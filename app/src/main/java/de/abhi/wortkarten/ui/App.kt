package de.abhi.wortkarten.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import de.abhi.wortkarten.data.Word

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFCC00),
    onPrimary = Color(0xFF1C1400),
    primaryContainer = Color(0xFF3A2E00),
    onPrimaryContainer = Color(0xFFFFE074),
    secondary = Color(0xFFFF6B5B),
    onSecondary = Color(0xFF2C0600),
    secondaryContainer = Color(0xFF4A1810),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFF66BB6A),
    background = Color(0xFF0F1116),
    onBackground = Color(0xFFECEEF4),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFECEEF4),
    surfaceVariant = Color(0xFF222631),
    onSurfaceVariant = Color(0xFFB8BeCC),
    outline = Color(0xFF3E4454)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB58900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEFA6),
    onPrimaryContainer = Color(0xFF2B2000),
    secondary = Color(0xFFD84315),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF14171F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171F),
    surfaceVariant = Color(0xFFECEEF4),
    onSurfaceVariant = Color(0xFF4C5364),
    outline = Color(0xFFC5CAD6)
)

enum class Tab(val label: String) {
    HOME("Start"),
    DECKS("Pakete"),
    BROWSE("Wörter"),
    STATS("Statistik"),
    SETTINGS("Mehr")
}

@Composable
fun WortKartenApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val dark = when (state.settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Surface
            }

            var tab by remember { mutableStateOf(Tab.HOME) }
            var activeRoute by remember { mutableStateOf<String?>(null) }
            var selectedWord by remember { mutableStateOf<Word?>(null) }

            when {
                state.study != null -> {
                    BackHandler { vm.endSession() }
                    StudyScreen(
                        session = state.study!!,
                        progress = state.progress,
                        autoplay = state.settings.autoplayAudio,
                        onAnswer = { vm.answerCurrent(it) },
                        onReveal = { vm.revealAnswer() },
                        onToggleStar = { vm.toggleFavorite(it) },
                        onSaveNote = { id, n -> vm.setNote(id, n) },
                        onRestart = { vm.startSession(state.study!!.config) },
                        onExit = { vm.endSession() }
                    )
                }

                selectedWord != null -> {
                    BackHandler { selectedWord = null }
                    val w = selectedWord!!
                    WordDetailSheet(
                        word = w,
                        progress = state.progress[w.id],
                        onBack = { selectedWord = null },
                        onToggleStar = { vm.toggleFavorite(w.id) },
                        onSaveNote = { n -> vm.setNote(w.id, n) }
                    )
                }

                activeRoute == "about" -> {
                    BackHandler { activeRoute = null }
                    AboutScreen(
                        onNavigateUp = { activeRoute = null }
                    )
                }

                else -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavItem(Tab.HOME, tab, Icons.Outlined.Home) { tab = it }
                                NavItem(Tab.DECKS, tab, Icons.Outlined.Style) { tab = it }
                                NavItem(Tab.BROWSE, tab, Icons.Outlined.MenuBook) { tab = it }
                                NavItem(Tab.STATS, tab, Icons.Outlined.BarChart) { tab = it }
                                NavItem(Tab.SETTINGS, tab, Icons.Outlined.Settings) { tab = it }
                            }
                        }
                    ) { pad ->
                        Box(Modifier.padding(pad)) {
                            when (tab) {
                                Tab.HOME -> HomeScreen(
                                    state = state,
                                    onStart = { vm.startSession(it) },
                                    onGoDecks = { tab = Tab.DECKS },
                                    onGoBrowse = { tab = Tab.BROWSE },
                                    onOpenWord = { selectedWord = it }
                                )

                                Tab.DECKS -> DecksScreen(
                                    state = state,
                                    onStart = { vm.startSession(it) }
                                )

                                Tab.BROWSE -> BrowseScreen(
                                    state = state,
                                    onSelectWord = { selectedWord = it },
                                    onToggleStar = { vm.toggleFavorite(it) }
                                )

                                Tab.STATS -> StatsScreen(state = state)

                                Tab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    onUpdate = { vm.updateSettings(it) },
                                    onExport = { uri, cb -> vm.exportBackup(uri, cb) },
                                    onImport = { uri, cb -> vm.importBackup(uri, cb) },
                                    onReset = { vm.resetAll() },
                                    onNavigateToAbout = { activeRoute = "about" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    t: Tab,
    current: Tab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (Tab) -> Unit
) {
    NavigationBarItem(
        selected = t == current,
        onClick = { onClick(t) },
        icon = { Icon(icon, contentDescription = t.label) },
        label = {
            Text(
                t.label,
                fontWeight = if (t == current) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}