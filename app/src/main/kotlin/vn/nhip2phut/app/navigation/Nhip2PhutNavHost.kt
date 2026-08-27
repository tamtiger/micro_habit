package vn.nhip2phut.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vn.nhip2phut.app.AppContainer
import vn.nhip2phut.ui.FoundationScreen

@Composable
fun Nhip2PhutNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Start,
) {
    check(appContainer.appContext === appContainer.appContext.applicationContext) {
        "AppContainer must retain only the application context"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(route = AppDestination.Foundation.route) {
            FoundationScreen()
        }
    }
}
