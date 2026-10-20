@file:OptIn(ExperimentalMaterial3Api::class)

package de.abhi.wortkarten.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.abhi.wortkarten.data.LEVELS
import de.abhi.wortkarten.data.Status
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(vm: MainViewModel, back: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val words by vm.words.collectAsState()
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val values = days.map { stats[it.toString()]?.reviewed ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Statistik") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } })
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Wiederholungen – letzte 7 Tage", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom
            ) {
                days.zip(values).forEach { (d, v) ->
                    Column(
                        Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text("$v", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.width(24.dp).fillMaxHeight(0.75f * v / max + 0.01f)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Serie: ${vm.streak()} Tage 🔥   •   Schwierige Wörter (≥ 3× vergessen): ${words.count { it.lapses >= 3 }}")

            Text("Fortschritt nach Niveau", style = MaterialTheme.typography.titleMedium)
            Row { listOf("Niveau") .plus(Status.entries.map { it.emoji }).plus("Σ").forEach {
                Text(it, Modifier.weight(1f), fontWeight = FontWeight.Bold) } }
            LEVELS.forEach { lv ->
                val ws = words.filter { it.level == lv }
                val c = ws.groupingBy { it.status }.eachCount()
                Row {
                    Text(lv, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Status.entries.forEach { s -> Text("${c[s] ?: 0}", Modifier.weight(1f), color = statusColor(s)) }
                    Text("${ws.size}", Modifier.weight(1f))
                }
            }
            Text("🆕 Neu · ✅ Kenne ich schon · 🔁 Üben · 🟢 Gelernt", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, back: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val csvImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            toast(if (text == null) "Datei konnte nicht gelesen werden" else "${vm.importCsv(text)} Wörter importiert")
        }
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportCsv().toByteArray(Charsets.UTF_8)) } }
            .onSuccess { toast("Wortliste exportiert") }
    }
    val backupSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.backupJson().toByteArray(Charsets.UTF_8)) } }
            .onSuccess { toast("Backup gespeichert") }
    }
    val backupLoad = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            toast(if (text != null && vm.restoreBackup(text)) "Backup wiederhergestellt" else "Ungültige Backup-Datei")
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.setReminder(true) else toast("Ohne Erlaubnis keine Erinnerung möglich")
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Einstellungen") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } })
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Lernen", style = MaterialTheme.typography.titleMedium)
            Text("Tagesziel: ${settings.dailyGoal} neue Wörter")
            Slider(settings.dailyGoal.toFloat(), { vm.setGoal(it.toInt()) }, valueRange = 5f..50f, steps = 8)

            Text("Anteil A1–A2 bei neuen Wörtern: ${settings.basicShare} %")
            Slider(settings.basicShare.toFloat(), { vm.setBasicShare(it.toInt()) }, valueRange = 0f..50f, steps = 9)
            Text("Der Rest kommt aus B1 → B2 → C1.", style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Beide Richtungen abfragen")
                    Text("Deutsch → Übersetzung und Übersetzung → Deutsch. 🟢 Gelernt erst, wenn beides sitzt.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(settings.bothDirections, { vm.setBothDirections(it) })
            }

            HorizontalDivider()
            Text("Erinnerung", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tägliche Erinnerung", Modifier.weight(1f))
                Switch(settings.reminderOn, { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.setReminder(on)
                })
            }
            if (settings.reminderOn) OutlinedButton({
                TimePickerDialog(ctx, { _, h, m -> vm.setReminderTime(h, m) },
                    settings.reminderHour, settings.reminderMinute, true).show()
            }) { Text("Uhrzeit: %02d:%02d".format(settings.reminderHour, settings.reminderMinute)) }

            HorizontalDivider()
            Text("Wortlisten & Backup", style = MaterialTheme.typography.titleMedium)
            Text("CSV (UTF-8, Semikolon):\ngerman;plural;grammar;translation;category;subtopic;level;register;example;example_en;note",
                style = MaterialTheme.typography.bodySmall)
            Button({ csvImport.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) },
                Modifier.fillMaxWidth()) { Text("CSV importieren") }
            OutlinedButton({ csvExport.launch("wortkarten_woerter.csv") }, Modifier.fillMaxWidth()) { Text("Wortliste als CSV exportieren") }
            Button({ backupSave.launch("wortkarten_backup_${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) {
                Text("Backup speichern (inkl. Fortschritt)")
            }
            OutlinedButton({ backupLoad.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth()) {
                Text("Backup wiederherstellen")
            }
            Text("Vor dem Deinstallieren oder Handywechsel immer ein Backup speichern.",
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            OutlinedButton({ confirmReset = true }, Modifier.fillMaxWidth()) {
                Text("Gesamten Lernfortschritt zurücksetzen", color = MaterialTheme.colorScheme.error)
            }
            Text("Aussprache nutzt die Sprachausgabe deines Handys. Falls stumm: Android-Einstellungen → Text-in-Sprache → deutsche Stimme installieren.",
                style = MaterialTheme.typography.bodySmall)
            Text("Wörter hinzufügen aus anderen Apps: Text markieren → „Zu WortKarten“, oder Teilen → WortKarten.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Wirklich zurücksetzen?") },
        text = { Text("Alle Wörter werden wieder 🆕 Neu. Die Wörter selbst bleiben erhalten.") },
        confirmButton = { TextButton({ vm.resetAll(); confirmReset = false }) { Text("Zurücksetzen") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Abbrechen") } }
    )
}
