package com.dreamindream.app

import android.app.Activity
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
object AdManager {
    private const val TAG = "AdManager"

    // TODO: keep ADS IDs here!!
    private const val REWARDED_ID = "ca-app-pub-1742201279182732/6656075788"
    private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712" // test id (replace for prod)

    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var isLoadingRewarded = false

    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var isLoadingInterstitial = false

    fun initialize(context: Context) {
        MobileAds.initialize(context)
        loadRewarded(context)
        loadInterstitial(context)
    }

    fun isRewardedReady(): Boolean = rewardedAd != null
    fun isInterstitialReady(): Boolean = interstitial != null

    /** Return true if this device is likely to fail on VP9 rewarded videos (OPPO/realme old builds). */
    fun shouldBypassVideoAds(): Boolean {
        val manu = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()
        val oppoLike = manu.contains("oppo") || brand.contains("oppo") || brand.contains("realme")
        val android11OrLower = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
        val lacksVp9Decoder = !deviceHasCodec("video/x-vnd.on2.vp9")
        return (oppoLike && android11OrLower) || lacksVp9Decoder
    }

    private fun deviceHasCodec(mime: String): Boolean {
        return try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) }
            }
        } catch (t: Throwable) {
            false
        }
    }

    @Synchronized
    fun loadRewarded(context: Context, adUnitId: String = REWARDED_ID) {
        if (isLoadingRewarded) return
        isLoadingRewarded = true
        RewardedAd.load(
            context, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadingRewarded = false
                    Log.d(TAG, "✅ Rewarded loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoadingRewarded = false
                    Log.w(TAG, "❌ Rewarded failed: ${error.code} / ${error.message}")
                }
            }
        )
    }

    @Synchronized
    fun loadInterstitial(context: Context, adUnitId: String = INTERSTITIAL_ID) {
        if (isLoadingInterstitial) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            context, adUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    isLoadingInterstitial = false
                    Log.d(TAG, "✅ Interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    isLoadingInterstitial = false
                    Log.w(TAG, "❌ Interstitial failed: ${error.code} / ${error.message}")
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (String) -> Unit,
        // optional: fallback to interstitial and still grant the reward (UX-friendly)
        allowBackupInterstitial: Boolean = true
    ) {
        // OPPO 등 문제 단말이면 바로 실패 메시지(호출부에서 우회 처리)
        if (shouldBypassVideoAds()) {
            onFailed("device_vp9_unsupported")
            // 옵션: 백업 전면 광고 시도
            if (allowBackupInterstitial && interstitial != null) {
                showBackupInterstitial(activity, grantReward = true, onClosed = onClosed)
            } else {
                loadInterstitial(activity)
            }
            // 다음 기회 대비 선로딩
            loadRewarded(activity)
            return
        }

        val ad = rewardedAd ?: run {
            onFailed("not_loaded")
            // 백업 interstitial 시도
            if (allowBackupInterstitial && interstitial != null) {
                showBackupInterstitial(activity, grantReward = true, onClosed = onClosed)
            } else {
                loadInterstitial(activity)
            }
            loadRewarded(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onClosed()
                loadRewarded(activity) // 다음 것 선로딩
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                rewardedAd = null
                onFailed("show_fail:${err.code}")
                // 백업 interstitial 시도
                if (allowBackupInterstitial && interstitial != null) {
                    showBackupInterstitial(activity, grantReward = true, onClosed = onClosed)
                } else {
                    loadInterstitial(activity)
                }
                loadRewarded(activity) // 재로딩
            }
            override fun onAdShowedFullScreenContent() {
                rewardedAd = null // 소모
            }
        }

        ad.show(activity) { _: RewardItem ->
            onRewardEarned()
        }
    }

    private fun showBackupInterstitial(activity: Activity, grantReward: Boolean, onClosed: () -> Unit) {
        val ad = interstitial
        if (ad == null) {
            onClosed()
            loadInterstitial(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                if (grantReward) {
                    // 보상동작은 호출부의 onRewardEarned에서 처리했을 가능성이 높으므로 여기선 단순히 닫기만
                }
                onClosed()
                loadInterstitial(activity)
            }
            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                interstitial = null
                onClosed()
                loadInterstitial(activity)
            }
        }
        ad.show(activity)
    }
}
