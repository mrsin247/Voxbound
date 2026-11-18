package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.MainViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsCogScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.VoxBoundTheme
import kotlinx.serialization.Serializable

@Serializable
object RouteLibrary

@Serializable
data class RouteStudio(val bookId: String)

@Serializable
data class RoutePlayer(val bookId: String)

@Serializable
object RouteSettingsCog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxBoundTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoxBoundNavApp()
                }
            }
        }
    }
}

@Composable
fun VoxBoundNavApp(
    viewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RouteLibrary
    ) {
        composable<RouteLibrary> {
            LibraryScreen(
                viewModel = viewModel,
                onOpenStudio = { bookId ->
                    navController.navigate(RouteStudio(bookId))
                },
                onOpenPlayer = { bookId ->
                    navController.navigate(RoutePlayer(bookId))
                },
                onOpenSettingsCog = {
                    navController.navigate(RouteSettingsCog)
                }
            )
        }

        composable<RouteStudio> { backStackEntry ->
            val route = backStackEntry.toRoute<RouteStudio>()
            StudioScreen(
                bookId = route.bookId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { bookId ->
                    navController.navigate(RoutePlayer(bookId)) {
                        popUpTo(RouteLibrary)
                    }
                },
                onOpenSettingsCog = {
                    navController.navigate(RouteSettingsCog)
                }
            )
        }

        composable<RoutePlayer> { backStackEntry ->
            val route = backStackEntry.toRoute<RoutePlayer>()
            PlayerScreen(
                bookId = route.bookId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSettingsCog = {
                    navController.navigate(RouteSettingsCog)
                },
                onOpenStudio = { bookId ->
                    navController.navigate(RouteStudio(bookId))
                }
            )
        }

        composable<RouteSettingsCog> {
            SettingsCogScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
