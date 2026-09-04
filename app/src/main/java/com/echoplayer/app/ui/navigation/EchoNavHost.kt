package com.echoplayer.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echoplayer.app.EchoApp
import com.echoplayer.app.IncomingFile
import com.echoplayer.app.ui.history.HistoryScreen
import com.echoplayer.app.ui.library.LibraryScreen
import com.echoplayer.app.ui.practice.PracticeScreen
import com.echoplayer.app.ui.practice.PracticeSessionScreen
import com.echoplayer.app.ui.reader.ReaderScreen
import com.echoplayer.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map

/**
 * 四个区域，对应 Echo_player 的两种练习模式加上记录：
 * - **书架**：导入资源，自由听读、跟读、标记问题（第一种练习模式）；
 * - **练习**：把记录交给 AI 分析后生成的针对性练习（第二种练习模式）；
 * - **记录**：问题定位、生词、跟读评分三份记录，它们是练习的原料；
 * - **设置**。
 */
object Routes {
    const val LIBRARY = "library"
    const val PRACTICE = "practice"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val READER = "reader/{materialId}?unit={unit}&unitId={unitId}"
    const val SESSION = "practice/{setId}"

    fun reader(materialId: String, unitIndex: Int? = null) =
        "reader/$materialId" + (unitIndex?.let { "?unit=$it" } ?: "")

    fun readerUnit(materialId: String, unitId: String) =
        "reader/$materialId?unitId=" + java.net.URLEncoder.encode(unitId, "UTF-8")

    fun session(setId: String) = "practice/" + java.net.URLEncoder.encode(setId, "UTF-8")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, "书架", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks),
    Tab(Routes.PRACTICE, "练习", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    Tab(Routes.HISTORY, "记录", Icons.Outlined.History, Icons.Filled.History),
    Tab(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun EchoNavHost(incoming: IncomingFile?, onIncomingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    val app = LocalContext.current.applicationContext as EchoApp
    val pendingSets by app.practiceSets.pendingCount.collectAsStateWithLifecycle(0)
    val openIssues by app.issues.openCount.collectAsStateWithLifecycle(0)

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        val badge = when (tab.route) {
                            Routes.PRACTICE -> pendingSets
                            Routes.HISTORY -> openIssues
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(badge = { if (badge > 0) Badge { Text(if (badge > 99) "99+" else "$badge") } }) {
                                    Icon(if (selected) tab.iconSelected else tab.icon, contentDescription = tab.label)
                                }
                            },
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
            composable(Routes.PRACTICE) {
                PracticeScreen(onOpenSet = { setId -> nav.navigate(Routes.session(setId)) })
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
                ReaderScreen(
                    materialId = entry.arguments?.getString("materialId").orEmpty(),
                    initialUnitIndex = (entry.arguments?.getInt("unit") ?: -1).takeIf { it >= 0 },
                    initialUnitId = entry.arguments?.getString("unitId"),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = Routes.SESSION,
                arguments = listOf(navArgument("setId") { type = NavType.StringType }),
            ) { entry ->
                PracticeSessionScreen(
                    setId = entry.arguments?.getString("setId").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
