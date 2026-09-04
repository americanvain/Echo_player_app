package com.echoplayer.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echoplayer.app.IncomingFile
import com.echoplayer.app.ui.history.HistoryScreen
import com.echoplayer.app.ui.library.LibraryScreen
import com.echoplayer.app.ui.reader.ReaderScreen
import com.echoplayer.app.ui.settings.SettingsScreen
import com.echoplayer.app.ui.vocab.VocabScreen

object Routes {
    const val LIBRARY = "library"
    const val VOCAB = "vocab"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val READER = "reader/{materialId}?unit={unit}&unitId={unitId}"
    fun reader(materialId: String, unitIndex: Int? = null) =
        "reader/$materialId" + (unitIndex?.let { "?unit=$it" } ?: "")
    fun readerUnit(materialId: String, unitId: String) =
        "reader/$materialId?unitId=" + java.net.URLEncoder.encode(unitId, "UTF-8")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, "书架", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks),
    Tab(Routes.VOCAB, "生词本", Icons.Outlined.Style, Icons.Filled.Style),
    Tab(Routes.HISTORY, "记录", Icons.Outlined.History, Icons.Filled.History),
    Tab(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun EchoNavHost(incoming: IncomingFile?, onIncomingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.iconSelected else tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    incoming = incoming,
                    onIncomingConsumed = onIncomingConsumed,
                    onOpen = { id, unit -> nav.navigate(Routes.reader(id, unit)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.VOCAB) {
                VocabScreen(onOpenUnit = { materialId, unitId -> nav.navigate(Routes.readerUnit(materialId, unitId)) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenUnit = { materialId, unitId -> nav.navigate(Routes.readerUnit(materialId, unitId)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("materialId") { type = NavType.StringType },
                    navArgument("unit") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("unitId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val materialId = entry.arguments?.getString("materialId").orEmpty()
                val unit = entry.arguments?.getInt("unit") ?: -1
                val unitId = entry.arguments?.getString("unitId")
                ReaderScreen(
                    materialId = materialId,
                    initialUnitIndex = unit.takeIf { it >= 0 },
                    initialUnitId = unitId,
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
        }
    }
}
