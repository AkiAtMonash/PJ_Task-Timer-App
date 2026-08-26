package com.aki.tasktimer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aki.tasktimer.ui.home.HomeScreen
import com.aki.tasktimer.ui.switch.SwitchMode
import com.aki.tasktimer.ui.switch.SwitchScreen

object Routes {
    const val HOME = "home"
    const val SWITCH = "switch/{mode}"
    fun switch(mode: SwitchMode): String = "switch/${mode.name.lowercase()}"
}

/**
 * 画面遷移の 1 本化（docs/02_ARCHITECTURE.md 4 章）。
 * ホームと切り替えフローの 2 ルートのみ。履歴・設定は Phase 6/7 で追加する。
 */
@Composable
fun TaskTimerNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStart = { navController.navigate(Routes.switch(SwitchMode.SWITCH)) },
                onInterrupt = { navController.navigate(Routes.switch(SwitchMode.STOP)) },
            )
        }
        composable(
            route = Routes.SWITCH,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val mode = when (entry.arguments?.getString("mode")) {
                SwitchMode.STOP.name.lowercase() -> SwitchMode.STOP
                else -> SwitchMode.SWITCH
            }
            SwitchScreen(
                mode = mode,
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
