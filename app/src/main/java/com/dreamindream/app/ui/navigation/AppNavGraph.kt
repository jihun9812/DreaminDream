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
import com.dreamindream.app.ui.subscription.SubscriptionScreen

@Composable
fun AppNavGraph(
    onLogout: () -> Unit
) {
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
            DreamScreen(
                onRequestSubscription = {
                    navController.navigate(Routes.SUBSCRIPTION)
                }
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen()
        }

        composable(Routes.FORTUNE) {
            // 📌 [수정됨] 네비게이션 람다 연결
            // 기존에는 빈 람다여서 버튼을 눌러도 반응이 없었음.
            FortuneScreen(
                onNavigateToSubscription = {
                    navController.navigate(Routes.SUBSCRIPTION)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToSubscription = {
                    navController.navigate(Routes.SUBSCRIPTION)
                },
                onLogout = {
                    onLogout()
                }
            )
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
                onEmptyCta = { /* 필요시 구현 */ },
                onOpenDreamWrite = {
                    navController.navigate(Routes.DREAM)
                },
                onNavigateToSubscription = {
                    navController.navigate(Routes.SUBSCRIPTION)
                }
            )
        }

        composable(Routes.COMMUNITY) {
            SimpleTextScreen()
        }

        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(
                onClose = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SimpleTextScreen() {
    Text(
        text = "Community ready",
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    )
}