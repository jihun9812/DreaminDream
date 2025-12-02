package com.dreamindream.app

import android.app.Activity
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AdManager {

    private const val TAG = "AdManager"
    private const val REWARDED_ID = "ca-app-pub-4715784452595618/6748580055"

    // ★ 구독 여부
    var isPremium: Boolean = false

    // ★ 보상형 게이트 상태 (모든 Compose 화면 공용)
    private val _showGate = MutableStateFlow(false)
    val showGate: StateFlow<Boolean> get() = _showGate

    private var rewardCallback: (() -> Unit)? = null

    fun openGate(onReward: () -> Unit) {
        if (isPremium) {
            onReward()
            return
        }
        rewardCallback = onReward
        _showGate.value = true
    }

    fun hideGate() { _showGate.value = false }

    fun onRewardFinished() {
        rewardCallback?.invoke()
        rewardCallback = null
        hideGate()
    }

    // -----------------------
    // ★ Rewarded Ad 관리
    // -----------------------

    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var isLoadingRewarded = false

    fun initialize(context: Context) {
        MobileAds.initialize(context)
        loadRewarded(context)
    }

    fun loadRewarded(context: Context) {
        if (isLoadingRewarded) return
        isLoadingRewarded = true

        RewardedAd.load(
            context,
            REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadingRewarded = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoadingRewarded = false
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onClosed: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        val ad = rewardedAd ?: return onFailed("not_loaded")

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onClosed()
                loadRewarded(activity)
            }

            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                rewardedAd = null
                onFailed(err.message)
                loadRewarded(activity)
            }
        }

        ad.show(activity) { _: RewardItem ->
            onRewardEarned()
        }
    }
}
