package com.dreamindream.app

import android.content.Context
import android.os.Handler
import android.os.Looper

object ReportWarmup {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Fire-and-forget warmup for this week's aggregated AI report.
     * We assume aggregation is idempotent. We trigger immediately and once more after a short delay
     * to absorb write propagation lag so that navigation into AIReportFragment feels instant.
     */
    fun warmUpThisWeek(ctx: Context, uid: String) {
        val weekKey = WeekUtils.weekKey()
        // immediate
        FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }
        // small delayed retry
        main.postDelayed({
            FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }
        }, 1200)
    }
}
