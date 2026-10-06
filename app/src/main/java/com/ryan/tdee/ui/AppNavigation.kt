package com.ryan.tdee.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryan.tdee.ui.home.HomeScreen
import com.ryan.tdee.ui.progress.ProgressScreen
import com.ryan.tdee.ui.settings.GraphSettingsScreen
import com.ryan.tdee.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val GRAPH_SETTINGS = "graph_settings"
}

/** Material shared-axis style transitions: pages slide a little and cross-fade. */
@Composable
fun TdeeNavHost(viewModel: TdeeViewModel) {
    val nav = rememberNavController()
    val duration = 350
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(duration)) { it / 5 } + fadeIn(tween(duration)) },
        exitTransition = { slideOutHorizontally(tween(duration)) { -it / 5 } + fadeOut(tween(duration / 2)) },
        popEnterTransition = { slideInHorizontally(tween(duration)) { -it / 5 } + fadeIn(tween(duration)) },
        popExitTransition = { slideOutHorizontally(tween(duration)) { it / 5 } + fadeOut(tween(duration / 2)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenProgress = { nav.navigate(Routes.PROGRESS) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(
                viewModel = viewModel,
                onBack = nav::popIfResumed,
                onOpenGraphSettings = { nav.navigate(Routes.GRAPH_SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = nav::popIfResumed,
                onOpenGraphSettings = { nav.navigate(Routes.GRAPH_SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.GRAPH_SETTINGS) {
            GraphSettingsScreen(viewModel = viewModel, onBack = nav::popIfResumed)
        }
    }
}

/** Ignores repeated back taps while a transition is still running. */
private fun NavController.popIfResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}
