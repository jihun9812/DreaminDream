package com.dreamindream.app

import android.content.Context
import android.os.Handler
import android.os.Looper

object ReportWarmup {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun warmUpThisWeek(ctx: Context, uid: String) {
        val weekKey = WeekUtils.weekKey()

        // 1) 즉시 집계 (저장 직후 빠르게 반영)
        FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }

        // 2) 0.8초 후 재집계 (쓰기 전파 지연 보정)
        main.postDelayed({
            FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }
        }, 800)

        // 3) 1.3초 후 추가 재집계 (네트워크/모바일 환경 변동성 보정)
        main.postDelayed({
            FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }
        }, 1300)
    }
}
