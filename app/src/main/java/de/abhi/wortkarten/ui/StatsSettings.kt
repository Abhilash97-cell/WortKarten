package de.abhi.wortkarten.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.abhi.wortkarten.data.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(state: UiState) {
    val stats = remember(state.words, state.progress) {
        Srs.computeCounts(state.words, state.progress)
    }
    val total = state.words.size.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Statistik",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Dein Lernfortschritt im Überblick",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Streak & Today
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔥",
                    value = "${state.settings.streak}",
                    label = "Tage-Serie"
                )
                BigStatCard(
                    modifier = Modifier.weight(1f),
                    icon = "✅",
                    value = "${state.settings.todayReviewed}",
                    label = "Heute gelernt"
                )
            }
        }

        // Breakdown card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Wortschatz-Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    StatusRow("Gemeistert (21+ Tage)", stats.mastered, total, Color(0xFF66BB6A))
                    Spacer(Modifier.height(10.dp))
                    StatusRow("Im Lernen", stats.learning, total, Color(0xFFFFCC00))
                    Spacer(Modifier.height(10.dp))
                    StatusRow("Noch neu", stats.newCount, total, Color(0xFF42A5F5))
                    Spacer(Modifier.height(10.dp))
                    StatusRow("Aktuell fällig", stats.due, total, Color(0xFFFF7043))
                }
            }
        }

        // Last 14 days activity chart
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Letzte 14 Tage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
                    val counts = days.map { state.history[it.toString()] ?: 0 }
                    val max = (counts.maxOrNull() ?: 1).coerceAtLeast(10)

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        days.forEachIndexed { idx, d ->
                            val c = counts[idx]
                            val frac = (c.toFloat() / max.toFloat()).coerceIn(0.06f, 1f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    Modifier
                                        .width(12.dp)
                                        .fillMaxHeight(frac * 0.8f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (c > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.GERMAN),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Level progress
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Nach Niveau (GER)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    listOf("A1", "A2", "B1", "B2", "C1").forEach { lvl ->
                        val inLvl = state.words.filter { it.level.equals(lvl, true) }
                        if (inLvl.isNotEmpty()) {
                            val seen = inLvl.count { (state.progress[it.id]?.reps ?: 0) > 0 }
                            StatusRow(
                                "$lvl  ($seen / ${inLvl.size})",
                                seen,
                                inLvl.size,
                                levelColor(lvl)
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: Int, total: Int, color: Color) {
    val pct = (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$value (${(pct * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// =====================================================================
// SETTINGS SCREEN
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onExport: (Uri, (Boolean) -> Unit) -> Unit,
    onImport: (Uri, (Int) -> Unit) -> Unit,
    onReset: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val ctx = LocalContext.current
    val s = state.settings
    var confirmReset by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdate { it.copy(reminderEnabled = granted) }
        if (!granted) {
            Toast.makeText(ctx, "Benachrichtigungs-Berechtigung verweigert", Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExport(uri) { ok ->
                Toast.makeText(
                    ctx,
                    if (ok) "Backup gespeichert" else "Fehler beim Speichern",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImport(uri) { count ->
                Toast.makeText(
                    ctx,
                    if (count >= 0) "$count Einträge wiederhergestellt" else "Ungültige Datei",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Daily goal & session size
        item {
            SettingsCard("Lernen") {
                Text(
                    "Tagesziel: ${s.dailyGoal} Karten",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = s.dailyGoal.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(dailyGoal = v.toInt()) } },
                    valueRange = 5f..100f,
                    steps = 18
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sitzungsgröße: ${s.sessionSize} Karten",
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = s.sessionSize.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(sessionSize = v.toInt()) } },
                    valueRange = 5f..50f,
                    steps = 8
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Aussprache automatisch abspielen", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Spricht jedes Wort beim Öffnen der Karte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.autoplayAudio,
                        onCheckedChange = { v -> onUpdate { it.copy(autoplayAudio = v) } }
                    )
                }
            }
        }

        // Daily Reminder
        item {
            SettingsCard("Tägliche Erinnerung") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Erinnerung aktivieren", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Benachrichtigt dich einmal täglich mit fälligen Karten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.reminderEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdate { it.copy(reminderEnabled = true) }
                                }
                            } else {
                                onUpdate { it.copy(reminderEnabled = enable) }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Uhrzeit: %02d:%02d Uhr".format(s.reminderHour, s.reminderMinute),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Theme
        item {
            SettingsCard("Darstellung") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "dark" to "Dunkel",
                        "light" to "Hell",
                        "system" to "System"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = s.theme == key,
                            onClick = { onUpdate { it.copy(theme = key) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // Backup & Reset
        item {
            SettingsCard("Daten & Backup") {
                OutlinedButton(
                    onClick = { exportLauncher.launch("wortkarten_backup_${LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Fortschritt exportieren (JSON)")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Backup importieren")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lernfortschritt zurücksetzen")
                }
            }
        }

        // About WortKarten Menu Item
        item {
            SettingsCard("Info") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigateToAbout() }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "About WortKarten",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = s.reminderHour,
            initialMinute = s.reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate {
                        it.copy(
                            reminderHour = pickerState.hour,
                            reminderMinute = pickerState.minute
                        )
                    }
                    showTimePicker = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            },
            title = { Text("Erinnerungszeit") },
            text = { TimePicker(state = pickerState) }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    confirmReset = false
                }) { Text("Zurücksetzen", color = Color(0xFFEF5350)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") }
            },
            title = { Text("Alles zurücksetzen?") },
            text = { Text("Dein gesamter Lernfortschritt, Favoriten und Notizen werden gelöscht.") }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}