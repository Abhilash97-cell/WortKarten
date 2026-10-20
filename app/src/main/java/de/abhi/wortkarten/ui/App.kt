package de.abhi.wortkarten.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "Home")
    data object Categories : Screen("categories", "Themen")
    data object Settings : Screen("settings", "Einstellungen")
    data object Study : Screen("study/{mode}/{arg}", "Lernen") {
        fun createRoute(mode: String, arg: String = "none") = "study/$mode/$arg"
    }
    data object WordList : Screen("wordlist/{category}", "Wortliste") {
        fun createRoute(category: String) = "wordlist/$category"
    }
    data object About : Screen("about", "About WortKarten")
}

@Composable
fun WortKartenApp(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Categories,
        Screen.Settings
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, contentDescription = screen.title)
                                    Screen.Categories -> Icon(Icons.AutoMirrored.Filled.List, contentDescription = screen.title)
                                    Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = screen.title)
                                    else -> {}
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToStudy = { mode, arg ->
                        navController.navigate(Screen.Study.createRoute(mode, arg))
                    },
                    onNavigateToCategories = {
                        navController.navigate(Screen.Categories.route)
                    }
                )
            }

            composable(Screen.Categories.route) {
                CategoriesScreen(
                    viewModel = viewModel,
                    onCategoryClick = { category ->
                        navController.navigate(Screen.WordList.createRoute(category))
                    },
                    onStudyCategory = { category ->
                        navController.navigate(Screen.Study.createRoute("category", category))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    }
                )
            }

            composable(Screen.About.route) {
                AboutScreen(
                    onNavigateUp = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.Study.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("arg") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "due"
                val arg = backStackEntry.arguments?.getString("arg") ?: "none"
                StudyScreen(
                    viewModel = viewModel,
                    mode = mode,
                    arg = arg,
                    onNavigateUp = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.WordList.route,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category") ?: ""
                WordListScreen(
                    viewModel = viewModel,
                    category = category,
                    onNavigateUp = { navController.navigateUp() },
                    onStudyCategory = {
                        navController.navigate(Screen.Study.createRoute("category", category))
                    }
                )
            }
        }
    }
}