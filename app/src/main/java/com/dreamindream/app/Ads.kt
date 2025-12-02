package com.dreamindream.app

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

@Suppress("unused")
object Ads {

    @Suppress("unused")
    fun bindBanner(root: View, @IdRes adViewId: Int, owner: LifecycleOwner) {
        val adView = root.findViewById<AdView>(adViewId) ?: return

        // ✅ 프리미엄 개념 없이 항상 광고 노출
        adView.visibility = View.VISIBLE
        try {
            adView.loadAd(AdRequest.Builder().build())
        } catch (_: Exception) {}
    }

    @Suppress("unused")
    fun maybeBypassDeepGate(
        context: Context,
        onProceedDeep: () -> Unit,   // 심화분석 실행
        onShowGate: () -> Unit       // 무료 유저 게이트 (리워드 광고 등)
    ) {
        // ✅ 구독 개념 제거 → 항상 게이트 타게 (보상형 광고/팝업)
        onShowGate()
    }
}
