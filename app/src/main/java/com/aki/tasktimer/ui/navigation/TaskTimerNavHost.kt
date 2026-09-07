package com.aki.tasktimer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aki.tasktimer.ui.home.HomeScreen
import com.aki.tasktimer.ui.settings.SettingsScreen
import com.aki.tasktimer.ui.switch.SwitchScreen

object Routes {
    const val HOME = "home"
    const val SWITCH = "switch"
    const val SETTINGS = "settings"
}

/**
 * 画面遷移の 1 本化（docs/02_ARCHITECTURE.md 4 章）。
 * ホーム・切り替えフロー・設定の 3 ルート。履歴は Phase 6 で追加する。
 *
 * @param openSwitchRequest 超過画面や覆いから「切り替えフローを開いて」と頼まれた回数。
 *        増えるたびに切り替え画面へ進む（すでに開いていれば何もしない）。
 */
@Composable
fun TaskTimerNavHost(
    openSwitchRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    LaunchedEffect(openSwitchRequest) {
        if (openSwitchRequest > 0 && navController.currentDestination?.route != Routes.SWITCH) {
            navController.navigate(Routes.SWITCH) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStart = { navController.navigate(Routes.SWITCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SWITCH) {
            SwitchScreen(
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
