package com.dreamindream.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dreamindream.app.ui.aireport.AIReportRoute
import com.dreamindream.app.ui.calendar.CalendarScreen
import com.dreamindream.app.ui.dream.DreamScreen
import com.dreamindream.app.ui.fortune.FortuneScreen
import com.dreamindream.app.ui.screens.HomeScreen
import com.dreamindream.app.ui.settings.SettingsScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToDream = { navController.navigate(Routes.DREAM) },
                onNavigateToCalendar = { navController.navigate(Routes.CALENDAR) },
                onNavigateToFortune = { navController.navigate(Routes.FORTUNE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToAIReport = { weekKey ->
                    navController.navigate(Routes.aiReport(weekKey))
                },
                onNavigateToCommunity = {
                    navController.navigate(Routes.COMMUNITY)
                }
            )
        }

        composable(Routes.DREAM) {
            DreamScreen()
        }

        composable(Routes.CALENDAR) {
            CalendarScreen()
        }

        composable(Routes.FORTUNE) {
            FortuneScreen()
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(
            route = Routes.AI_REPORT,
            arguments = listOf(
                navArgument("weekKey") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val weekKey = backStackEntry.arguments?.getString("weekKey")
            AIReportRoute(
                weekKeyArg = weekKey,
                onEmptyCta = { /* TODO: 필요한 경우 작성 */ },
                onOpenDreamWrite = {
                    // 예시: Dream 화면으로 이동
                    navController.navigate(Routes.DREAM)
                }
            )
        }


        composable(Routes.COMMUNITY) {
            SimpleTextScreen()
        }
    }
}

@Composable
private fun SimpleTextScreen() {
    Text(
        text = "Community 화면 준비중",
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    )
}
