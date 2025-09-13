package com.example.dreamindream

object ReportWarmup {
    fun warmUpThisWeek(uid: String) {
        val weekKey = WeekUtils.weekKey()
        FirestoreManager.countDreamEntriesForWeek(uid, weekKey) { count ->
            if (count >= 2) {
                // 리포트가 없으면 즉시 생성 (AIReportFragment에서 바로 로딩 완료 상태가 되도록)
                FirestoreManager.aggregateDreamsForWeek(uid, weekKey) { /* no-op */ }
            }
        }
    }
}
