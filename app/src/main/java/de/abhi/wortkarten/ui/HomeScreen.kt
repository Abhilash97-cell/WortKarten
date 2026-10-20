@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.abhi.wortkarten.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.abhi.wortkarten.data.LEVELS
import de.abhi.wortkarten.data.Status

@Composable
fun HomeScreen(vm: MainViewModel, go: (Screen) -> Unit) {
    val words by vm.words.collectAsState()
    val settings by vm.settings.collectAsState()
    val stats by vm.stats.collectAsState()
    val counts = remember(words, settings) { vm.counts() }
    val due = remember(words, settings) { vm.dueCount() }
    val today = remember(stats) { vm.today() }
    val streak = remember(stats) { vm.streak() }
    val newLeft = (settings.dailyGoal - today.learnedNew).coerceAtLeast(0)
    val newAvail = counts[Status.NEW] ?: 0
    var pickLevels by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("WortKarten") }, actions = {
            IconButton({ go(Screen.Stats) }) { Icon(Icons.Default.BarChart, "Statistik") }
            IconButton({ go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Einstellungen") }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Heute", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${today.learnedNew} / ${settings.dailyGoal} neue Wörter",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { (today.learnedNew.toFloat() / settings.dailyGoal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Text("Wiederholungen fällig: $due   •   Serie: $streak 🔥")
                }
            }

            val hasWork = due > 0 || (newLeft > 0 && newAvail > 0)
            Button(
                onClick = { vm.startSession(Mode.ALL); go(Screen.Study) },
                enabled = hasWork, modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                Text(if (hasWork) "Lernen starten" else "Für heute fertig ✓")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.startSession(Mode.PRACTICE); go(Screen.Study) },
                    enabled = (counts[Status.PRACTICE] ?: 0) > 0, modifier = Modifier.weight(1f)
                ) { Text("🔁 Nur Üben") }
                OutlinedButton(
                    onClick = { pickLevels = true }, enabled = newAvail > 0, modifier = Modifier.weight(1f)
                ) { Text("🆕 Nur Neu…") }
            }

            Text("Meine Karten (aktive Themen)", style = MaterialTheme.typography.titleSmall)
            Status.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { s ->
                        OutlinedCard(Modifier.weight(1f).clickable { go(Screen.WordList(status = s)) }) {
                            Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${counts[s] ?: 0}", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = statusColor(s))
                                Text(s.emoji + " " + s.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            OutlinedButton({ vm.nextQuiz(); go(Screen.Quiz) }, Modifier.fillMaxWidth()) {
                Text("der · die · das  –  Artikel-Quiz")
            }
            OutlinedButton({ go(Screen.Topics) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Category, null); Spacer(Modifier.width(8.dp)); Text("Themen")
            }
            OutlinedButton({ go(Screen.WordList()) }, Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(8.dp)); Text("Alle Wörter / Wort hinzufügen")
            }
        }
    }

    if (pickLevels) {
        var sel by remember { mutableStateOf(setOf("B1", "B2", "C1")) }
        AlertDialog(
            onDismissRequest = { pickLevels = false },
            title = { Text("Neue Wörter – welche Niveaus?") },
            text = {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LEVELS.forEach { lv ->
                            FilterChip(selected = lv in sel, onClick = { sel = if (lv in sel) sel - lv else sel + lv },
                                label = { Text(lv) })
                        }
                    }
                    Text("${vm.newAvailable(sel)} neue Wörter verfügbar", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pickLevels = false; vm.startSession(Mode.NEW, sel); go(Screen.Study) },
                    enabled = sel.isNotEmpty() && vm.newAvailable(sel) > 0
                ) { Text("Starten") }
            },
            dismissButton = { TextButton({ pickLevels = false }) { Text("Abbrechen") } }
        )
    }
}
