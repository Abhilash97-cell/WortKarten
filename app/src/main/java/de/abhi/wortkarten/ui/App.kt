package de.abhi.wortkarten.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.abhi.wortkarten.data.Status
import de.abhi.wortkarten.data.Word

sealed interface Screen {
    data object Home : Screen
    data object Study : Screen
    data object Quiz : Screen
    data object Topics : Screen
    data class WordList(val category: String? = null, val status: Status? = null) : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object About : Screen
}

private val Light = lightColorScheme(primary = Color(0xFF1F4E79), secondary = Color(0xFF2E7D32), tertiary = Color(0xFFC62828))
private val Dark = darkColorScheme(primary = Color(0xFF8AB4F8), secondary = Color(0xFF81C784), tertiary = Color(0xFFEF9A9A))

@Composable
fun App(vm: MainViewModel, speak: (String) -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        Surface {
            val ready by vm.ready.collectAsState()
            if (!ready) return@Surface
            val ctx = LocalContext.current
            val msg by vm.message.collectAsState()
            LaunchedEffect(msg) {
                msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.message.value = null }
            }

            var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
            val go: (Screen) -> Unit = { stack = stack + it }
            val back: () -> Unit = {
                if (stack.last() == Screen.Study) vm.endSession()
                if (stack.size > 1) stack = stack.dropLast(1)
            }
            BackHandler(enabled = stack.size > 1) { back() }

            when (val s = stack.last()) {
                Screen.Home -> HomeScreen(vm, go)
                Screen.Study -> StudyScreen(vm, speak, back)
                Screen.Quiz -> QuizScreen(vm, speak, back)
                Screen.Topics -> TopicsScreen(vm, go, back)
                is Screen.WordList -> WordListScreen(vm, s.category, s.status, speak, back)
                Screen.Stats -> StatsScreen(vm, back)
                Screen.Settings -> SettingsScreen(vm, back, onAbout = { go(Screen.About) })
                Screen.About -> AboutScreen(onNavigateUp = back)
            }

            // Word shared from another app → add dialog
            val shared by vm.sharedText.collectAsState()
            shared?.let { t ->
                WordDialog(
                    word = null, initialGerman = t,
                    onDismiss = { vm.sharedText.value = null },
                    onSave = { w: Word -> vm.addWord(w); vm.sharedText.value = null }
                )
            }
        }
    }
}