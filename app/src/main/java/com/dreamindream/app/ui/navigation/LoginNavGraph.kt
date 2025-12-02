package com.dreamindream.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dreamindream.app.ui.login.LoginScreen

@Composable
fun LoginNavGraph(onLoginSuccess: () -> Unit) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = onLoginSuccess
            )
        }
    }
}
