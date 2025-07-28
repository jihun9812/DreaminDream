package com.example.dreamindream.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback

object AdManager {

    private var rewardedAd: RewardedAd? = null
    private const val adUnitId = "ca-app-pub-1742201279182732/9046955585"
    private var hasEarnedReward = false

    fun initialize(context: Context) {
        MobileAds.initialize(context)
        loadAd(context)
    }

    fun loadAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                rewardedAd = null
            }
        })
    }

    fun showAd(activity: Activity, onRewardEarned: () -> Unit, onFailed: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed()
            return
        }

        hasEarnedReward = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadAd(activity)
                if (hasEarnedReward) {
                    onRewardEarned()
                } else {
                    onFailed() // 광고 닫기만 하고 보상 안 받았을 때
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                onFailed()
            }
        }

        ad.show(activity) { rewardItem: RewardItem ->
            hasEarnedReward = true
        }
    }
}
