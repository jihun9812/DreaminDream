package com.example.dreamindream.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    // Google 공식 테스트 보상형 ID
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }

    fun loadRewarded(context: Context, adUnitId: String = TEST_REWARDED_ID) {
        RewardedAd.load(
            context, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Rewarded failed: ${error.code} / ${error.message}")
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
                // 소모되므로 null
                rewardedAd = null
            }
        }
        ad.show(activity) { _: RewardItem ->
            // 사용자가 보상 조건을 충족했을 때 호출
            onRewardEarned()
        }
    }
}
