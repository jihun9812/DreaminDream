package com.example.dreamindream.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.dreamindream.BuildConfig
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    private const val TAG = "Ads"

    // 디버그=테스트 리워드, 릴리스=실유닛
    private val AD_UNIT_REWARDED =
        if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/5224354917"
        else
            "ca-app-pub-1742201279182732/9046955585"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var lastError: LoadAdError? = null

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    private val onLoadedWaiters = mutableListOf<() -> Unit>()
    private val onFailedWaiters = mutableListOf<(LoadAdError?) -> Unit>()

    fun initialize(context: Context) {
        MobileAds.initialize(context)
        loadAd(context)
    }

    fun isReady(): Boolean = rewardedAd != null

    fun addOnLoadedListener(l: () -> Unit) {
        if (rewardedAd != null) l() else onLoadedWaiters.add(l)
    }

    fun addOnFailedListener(l: (LoadAdError?) -> Unit) {
        if (lastError != null && rewardedAd == null) l(lastError) else onFailedWaiters.add(l)
    }

    fun loadAd(context: Context) {
        if (isLoading) return
        isLoading = true
        lastError = null
        Log.d(TAG, "🔄 Rewarded 로드 시작 (retry=$retryCount)")

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_REWARDED, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                isLoading = false
                rewardedAd = ad
                retryCount = 0
                Log.d(TAG, "✅ Rewarded 로드 완료")
                val loaded = onLoadedWaiters.toList()
                onLoadedWaiters.clear()
                onFailedWaiters.clear()
                loaded.forEach { it() }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading = false
                rewardedAd = null
                lastError = error
                Log.e(TAG, "❌ Rewarded 로드 실패: code=${error.code}, domain=${error.domain}, msg=${error.message}")
                val failed = onFailedWaiters.toList()
                onFailedWaiters.clear()
                failed.forEach { it(error) }
                // 지수 백오프 (최대 30초)
                retryCount++
                val delayMs = (3000L * retryCount).coerceAtMost(30_000L)
                handler.postDelayed({ loadAd(context.applicationContext) }, delayMs)
            }
        })

        // 하드 타임아웃(8초) 방어
        handler.postDelayed({
            if (isLoading && rewardedAd == null) {
                isLoading = false
                Log.w(TAG, "⏳ Rewarded 로드 타임아웃")
                val failed = onFailedWaiters.toList()
                onFailedWaiters.clear()
                failed.forEach { it(lastError) }
                retryCount++
                val delayMs = (3000L * retryCount).coerceAtMost(30_000L)
                handler.postDelayed({ loadAd(context.applicationContext) }, delayMs)
            }
        }, 8000L)
    }

    fun showAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "showAd() 시점에 광고 미준비 → 재로드")
            loadAd(activity)
            onFailed()
            return
        }

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "🧹 광고 닫힘 → 다음 로드")
                rewardedAd = null
                loadAd(activity)
                if (earned) onRewardEarned() else onFailed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "❌ 표시 실패: ${adError.code}, ${adError.message}")
                rewardedAd = null
                loadAd(activity)
                onFailed()
            }
        }
        ad.show(activity) { _: RewardItem -> earned = true }
    }
}
