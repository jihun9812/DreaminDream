package com.dreamindream.app.ui.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing v6
 * - 월간/연간 2종 구독을 대상으로 동작
 * - 서버 검증 없이 "로컬 영수증" 기준. (서버 연동이 있으면 검증을 붙이세요)
 */
object BillingManager : PurchasesUpdatedListener, BillingClientStateListener {

    private const val TAG = "BillingManager"

    /** Play Console의 실제 상품 ID로 바꾸세요 */
    const val PRODUCT_MONTHLY = "sub_premium_monthly"
    const val PRODUCT_YEARLY  = "sub_premium_yearly"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)

    private var billingClient: BillingClient? = null

    // 제품 메타
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails

    // 현재 구독 보유 여부
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    // 내부 상태
    private var isReady = false

    fun init(context: Context) {
        if (::appContext.isInitialized && isReady) return
        appContext = context.applicationContext
        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        connect()
    }

    private fun connect() {
        if (billingClient?.isReady == true) return
        billingClient?.startConnection(this)
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        isReady = result.responseCode == BillingClient.BillingResponseCode.OK
        if (!isReady) {
            Log.e(TAG, "onBillingSetupFinished: ${result.debugMessage}")
            return
        }
        scope.launch {
            queryProducts()
            refreshPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        isReady = false
        // 재시도
        connect()
    }

    /** UI에서 표시할 제품 정보 조회 */
    suspend fun queryProducts() {
        val client = billingClient ?: return
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY).map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            ).build()

        val res = client.queryProductDetails(query)
        if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = res.productDetailsList ?: emptyList()
        } else {
            Log.e(TAG, "queryProductDetails fail: ${res.billingResult.debugMessage}")
        }
    }

    /** 현재 구매 상태 조회 → 구독 여부 반영 */
    suspend fun refreshPurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val res = client.queryPurchasesAsync(params)
        val active = res.purchasesList.any { it.isPurchasedActive() }
        _isSubscribed.value = active
    }

    /** 구매 진행 */
    fun purchase(activity: Activity, target: ProductDetails) {
        val offerToken = target.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run {
                Log.e(TAG, "No offerToken in ProductDetails")
                return
            }
        val productParams = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(target)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient?.launchBillingFlow(activity, flowParams)
    }

    /** 복원(재인증) */
    suspend fun restore(): Boolean {
        val client = billingClient ?: return false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val res = client.queryPurchasesAsync(params)
        val active = res.purchasesList.any { it.isPurchasedActive() }
        _isSubscribed.value = active
        return active
    }

    /** Billing 콜백 */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            handlePurchaseList(purchases)
        } else {
            Log.w(TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun handlePurchaseList(purchases: List<Purchase>) {
        scope.launch {
            val client = billingClient ?: return@launch
            var active = false
            purchases.forEach { p ->
                if (p.isPurchasedActive()) {
                    // 미인증 시, acknowledge
                    if (!p.isAcknowledged) {
                        try {
                            client.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(p.purchaseToken)
                                    .build()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "acknowledgePurchase", e)
                        }
                    }
                    active = true
                }
            }
            _isSubscribed.value = active
        }
    }

    private fun Purchase.isPurchasedActive(): Boolean {
        return purchaseState == Purchase.PurchaseState.PURCHASED && !isAcknowledged && !isAutoRenewing.not() || // 결제 직후
                (purchaseState == Purchase.PurchaseState.PURCHASED && isAutoRenewing)                                  // 자동 갱신 중
    }
}