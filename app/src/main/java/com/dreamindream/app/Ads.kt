package com.dreamindream.app

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.LifecycleOwner
import com.dreamindream.app.billing.SubscriptionManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

object Ads {


    fun bindBanner(root: View, @IdRes adViewId: Int, owner: LifecycleOwner) {
        val adView = root.findViewById<AdView>(adViewId) ?: return

        fun apply(premium: Boolean) {
            if (premium) {
                try { adView.destroy() } catch (_: Exception) {}
                adView.visibility = View.GONE
            } else {
                adView.visibility = View.VISIBLE
                try { adView.loadAd(AdRequest.Builder().build()) } catch (_: Exception) {}
            }
        }

        apply(SubscriptionManager.isPremium(root.context))
        SubscriptionManager.observePremium(owner) { apply(it) }
    }

    fun maybeBypassDeepGate(
        context: Context,
        onProceedDeep: () -> Unit,   // 심화분석 실제 실행(다이얼로그 열기 등)
        onShowGate: () -> Unit       // 무료 유저용 기존 게이트(리워드/구독 안내)
    ) {
        if (SubscriptionManager.isPremium(context)) {
            onProceedDeep()
        } else {
            onShowGate()
        }
    }
}
