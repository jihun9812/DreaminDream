package com.dreamindream.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // ✅ 실제 보상형 광고 단위 ID
    private const val REWARDED_ID = "ca-app-pub-1742201279182732/6656075788"

    private var rewardedAd: RewardedAd? = null

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }

    fun loadRewarded(context: Context, adUnitId: String = REWARDED_ID) {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "✅ 보상형 광고 로드 성공")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "❌ 보상형 광고 로드 실패: ${error.code} / ${error.message}")
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onClosed: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed("not_loaded")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                onFailed("show_fail:${adError.code}")
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }
        }

        ad.show(activity) { _: RewardItem ->
            onRewardEarned()
        }
    }
}
