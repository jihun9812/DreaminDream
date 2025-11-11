package com.dreamindream.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.*
import com.google.firebase.functions.FirebaseFunctions

object BillingManager : PurchasesUpdatedListener {

    private const val TAG = "BillingManager"

    // 콘솔의 실제 상품 ID
    private const val LEGACY_INAPP_ID = "premium_remove_ads"
    private const val SUBS_ID = "premium_remove_ads_sub"

    // ----- ✅ 호환용 enum (SubscriptionDialogFragment가 사용)
    enum class BillingPeriod { MONTHLY, YEARLY }

    // offerTags(콘솔 Base plan/Offer에 태그 부여 권장)
    private const val TAG_MONTHLY = "MONTHLY"
    private const val TAG_YEARLY  = "ANNUAL"

    private lateinit var appContext: Context
    private var billingClient: BillingClient? = null

    private var subsProductDetails: ProductDetails? = null
    private var inappProductDetails: ProductDetails? = null

    private const val TTL_MS = 12 * 60 * 60 * 1000L
    private var lastSyncMs: Long = 0L

    /* ───────────────── Init ───────────────── */

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            // 앱 포그라운드 복귀 시 주기 재동기화
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (System.currentTimeMillis() - lastSyncMs > TTL_MS) {
                        queryExistingPurchases(appContext) { /* noop */ }
                        lastSyncMs = System.currentTimeMillis()
                    }
                }
            })
        }

        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(appContext)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProductDetails()
                    queryExistingPurchases(appContext)
                    Log.d(TAG, "Billing setup OK")
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    /* ───────────────── UI: 구매 시작 ───────────────── */

    /** 기본(자동 선택) 플로우 — 기존 화면들이 호출할 수도 있어 유지 */
    fun launchPurchase(activity: Activity) {
        if (!SubscriptionManager.isEligibleForPurchase()) {
            val id = activity.resources.getIdentifier("signin_required", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "로그인이 필요합니다."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            try { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
            return
        }

        val client = billingClient ?: run {
            val id = activity.resources.getIdentifier("billing_err_connect", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "결제 서비스를 초기화하는 중입니다. 잠시 후 다시 시도하세요."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val product = subsProductDetails ?: inappProductDetails
        if (product == null) {
            val id = activity.resources.getIdentifier("billing_err_product_info", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "상품 정보를 불러올 수 없습니다. 잠시 후 다시 시도하세요."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            refreshProductDetails()
            return
        }

        val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)

        if (product.productType == BillingClient.ProductType.SUBS) {
            val offerToken = offerTokenFor(product)
            if (offerToken == null) {
                val id = activity.resources.getIdentifier("billing_err_offer_missing", "string", activity.packageName)
                val msg = if (id != 0) activity.getString(id) else "이 요금제는 현재 이용할 수 없습니다."
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                return
            }
            pdParams.setOfferToken(offerToken)
        }

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(pdParams.build()))
            .build()

        val res = client.launchBillingFlow(activity, flow)
        Log.d(TAG, "launchBillingFlow -> ${res.responseCode}")
    }

    /** ✅ 호환용 오버로드: 월/연 명시 플로우 */
    fun launchPurchase(activity: Activity, period: BillingPeriod) {
        if (!SubscriptionManager.isEligibleForPurchase()) {
            val id = activity.resources.getIdentifier("signin_required", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "로그인이 필요합니다."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            try { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
            return
        }

        val client = billingClient ?: run {
            val id = activity.resources.getIdentifier("billing_err_connect", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "결제 서비스를 초기화하는 중입니다. 잠시 후 다시 시도하세요."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val subs = subsProductDetails
        if (subs != null) {
            val offerToken = offerTokenFor(subs, period)
            if (offerToken == null) {
                val id = activity.resources.getIdentifier("billing_err_offer_missing", "string", activity.packageName)
                val msg = if (id != 0) activity.getString(id) else "이 요금제는 현재 이용할 수 없습니다."
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                return
            }
            val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(subs)
                .setOfferToken(offerToken)
                .build()
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(pdParams))
                .build()
            client.launchBillingFlow(activity, flow)
            return
        }

        // 폴백: 일회성 INAPP
        val inapp = inappProductDetails ?: run {
            val id = activity.resources.getIdentifier("billing_err_product_info", "string", activity.packageName)
            val msg = if (id != 0) activity.getString(id) else "상품 정보를 불러올 수 없습니다. 잠시 후 다시 시도하세요."
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            refreshProductDetails()
            return
        }
        val pdParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(inapp)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(pdParams))
            .build()
        client.launchBillingFlow(activity, flow)
    }

    /* ───────────────── 제품 정보 갱신 ───────────────── */

    private fun refreshProductDetails() {
        val client = billingClient ?: return
        val subs = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SUBS_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val inapp = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(LEGACY_INAPP_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val q = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(subs, inapp)).build()

        client.queryProductDetailsAsync(q) { res, list ->
            if (res.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetailsAsync fail: ${res.responseCode}")
                return@queryProductDetailsAsync
            }
            subsProductDetails = list.firstOrNull { it.productId == SUBS_ID && it.productType == BillingClient.ProductType.SUBS }
            inappProductDetails = list.firstOrNull { it.productId == LEGACY_INAPP_ID && it.productType == BillingClient.ProductType.INAPP }
            Log.d(TAG, "productDetails: subs=${subsProductDetails != null}, inapp=${inappProductDetails != null}")
        }
    }

    /* ───────────────── Offer 선택 ───────────────── */

    private fun offerTokenFor(details: ProductDetails): String? {
        val offers = details.subscriptionOfferDetails ?: return null
        val tagHit = offers.firstOrNull { it.offerTags.contains(TAG_MONTHLY) } ?: offers.firstOrNull { it.offerTags.contains(TAG_YEARLY) }
        if (tagHit != null) return tagHit.offerToken
        val ranked = offers.sortedBy { od ->
            val p = od.pricingPhases.pricingPhaseList.lastOrNull()
            when (p?.billingPeriod) {
                null -> 99
                else -> if (p.billingPeriod.contains("P1Y")) 12 else if (p.billingPeriod.contains("M")) 1 else 99
            }
        }
        return (ranked.firstOrNull() ?: offers.firstOrNull())?.offerToken
    }

    /** ✅ 호환용: 기간 지정 Offer 토큰 선택 */
    private fun offerTokenFor(details: ProductDetails, period: BillingPeriod): String? {
        val offers = details.subscriptionOfferDetails ?: return null
        // 1) 태그 우선
        val tag = if (period == BillingPeriod.MONTHLY) TAG_MONTHLY else TAG_YEARLY
        val byTag = offers.firstOrNull { it.offerTags.contains(tag) }?.offerToken
        if (byTag != null) return byTag
        // 2) 기간 파싱
        val want = if (period == BillingPeriod.MONTHLY) "M" else "Y"
        val byPeriod = offers.firstOrNull { od ->
            od.pricingPhases.pricingPhaseList.any { it.billingPeriod.contains(want) }
        }?.offerToken
        if (byPeriod != null) return byPeriod
        // 3) 폴백(첫번째)
        return offers.firstOrNull()?.offerToken
    }

    /* ───────────────── 구매 콜백 ───────────────── */

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            Log.w(TAG, "onPurchasesUpdated: ${result.responseCode}")
            return
        }
        purchases.forEach { handlePurchase(it) }
    }

    private fun handlePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val owns = purchase.products.any { it == SUBS_ID || it == LEGACY_INAPP_ID }
        if (!owns) return

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(ack) { res ->
                if (res.responseCode == BillingClient.BillingResponseCode.OK) afterOwned(purchase)
            }
        } else {
            afterOwned(purchase)
        }
    }

    private fun afterOwned(purchase: Purchase) {
        // UX 낙관 반영
        SubscriptionManager.setPremium(appContext, true)

        // 서버 검증 (최종 권한은 서버 스냅샷)
        try {
            FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyPlaySubscription")
                .call(mapOf("purchaseToken" to purchase.purchaseToken, "productId" to (purchase.products.firstOrNull() ?: SUBS_ID)))
                .addOnSuccessListener { r -> Log.d(TAG, "verifyPlaySubscription OK: ${r.data}") }
                .addOnFailureListener { e -> Log.w(TAG, "verifyPlaySubscription failed: ${e.message}") }
        } catch (t: Throwable) {
            Log.w(TAG, "verifyPlaySubscription call error: ${t.message}")
        }
    }

    /* ───────────────── 복원/동기화 ───────────────── */

    fun restorePurchases(context: Context, onResult: (Boolean) -> Unit = {}) {
        ensureReady(context) { _ -> queryExistingPurchases(context, onResult) }
    }

    fun queryExistingPurchases(context: Context, onResult: (Boolean) -> Unit = {}) {
        val client = billingClient ?: return onResult(false)
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { _, listSubs ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { _, listInapp ->
                val total = (listSubs + listInapp).orEmpty()
                val owned = total.any { p -> p.products.any { it == SUBS_ID || it == LEGACY_INAPP_ID } }
                if (owned) SubscriptionManager.setPremium(context, true)
                onResult(owned)
            }
        }
    }

    /* ───────────────── 가격 라벨(호환) ───────────────── */

    /** ✅ 호환용: 다이얼로그에서 사용 */
    fun formattedPriceForPeriodLabel(context: Context, period: BillingPeriod): String? {
        val pd = subsProductDetails ?: return null

        val offer = when (period) {
            BillingPeriod.MONTHLY -> pd.subscriptionOfferDetails?.firstOrNull { od ->
                od.offerTags.contains(TAG_MONTHLY) || od.pricingPhases.pricingPhaseList.any { it.billingPeriod.contains("M") }
            }
            BillingPeriod.YEARLY -> pd.subscriptionOfferDetails?.firstOrNull { od ->
                od.offerTags.contains(TAG_YEARLY) || od.pricingPhases.pricingPhaseList.any { it.billingPeriod.contains("Y") }
            }
        } ?: return null

        val phase = when (period) {
            BillingPeriod.MONTHLY -> offer.pricingPhases.pricingPhaseList.firstOrNull { it.billingPeriod.contains("M") }
                ?: offer.pricingPhases.pricingPhaseList.lastOrNull()
            BillingPeriod.YEARLY -> offer.pricingPhases.pricingPhaseList.firstOrNull { it.billingPeriod.contains("Y") }
                ?: offer.pricingPhases.pricingPhaseList.lastOrNull()
        } ?: return null

        val price = phase.formattedPrice
        // "price / unit" 형식 포맷(리소스가 없으면 기본 문자열)
        val fmtId = context.resources.getIdentifier("sub_price_format", "string", context.packageName)
        val unitId = context.resources.getIdentifier(
            if (period == BillingPeriod.MONTHLY) "sub_unit_month" else "sub_unit_year",
            "string", context.packageName
        )
        val unit = if (unitId != 0) context.getString(unitId) else if (period == BillingPeriod.MONTHLY) "per month" else "per year"
        return if (fmtId != 0) context.getString(fmtId, price, unit) else "$price / $unit"
    }

    /* ───────────────── 내부 유틸 ───────────────── */

    private fun ensureReady(context: Context, block: (BillingClient) -> Unit) {
        val bc = billingClient
        if (bc != null && bc.isReady) { block(bc); return }
        if (bc == null) init(context)
        val c = billingClient ?: return
        if (c.isReady) { block(c); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) block(c)
            }
            override fun onBillingServiceDisconnected() { Log.w(TAG, "ensureReady: disconnected") }
        })
    }
}
