package com.dreamindream.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val FORTUNE = "fortune"
    const val CALENDAR = "calendar"
    const val DREAM = "dream"
    const val SETTINGS = "settings"
    const val COMMUNITY = "community"
    const val AI_REPORT = "aiReport/{weekKey}"

    fun aiReport(weekKey: String) = "aiReport/$weekKey"
}
