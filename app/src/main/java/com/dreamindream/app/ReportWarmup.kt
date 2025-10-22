package com.dreamindream.app

import android.content.Context
import android.os.Handler
import android.os.Looper

object ReportWarmup {
    private val main by lazy { Handler(Looper.getMainLooper()) }


    fun warmUpThisWeek(ctx: Context, uid: String) {
        val weekKey = WeekUtils.weekKey()

        FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }

        main.postDelayed({
            FirestoreManager.aggregateDreamsForWeek(uid, weekKey, ctx) { /* no-op */ }
        }, 1200)
    }
}