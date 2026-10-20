@file:OptIn(ExperimentalMaterial3Api::class)

package de.abhi.wortkarten.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.abhi.wortkarten.data.Dir
import de.abhi.wortkarten.data.Word

@Composable
fun StudyScreen(vm: MainViewModel, speak: (String) -> Unit, onClose: () -> Unit) {
    val cur by vm.current.collectAsState()
    val remaining by vm.remaining.collectAsState()
    val done by vm.sessionDone.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (cur != null) "Noch $remaining" else "Fertig") },
            navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
        )
    }) { pad ->
        val pair = cur
        if (pair == null) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎉", style = MaterialTheme.typography.displayLarge)
                Text("Sitzung abgeschlossen", style = MaterialTheme.typography.headlineSmall)
                Text("$done Karten wiederholt")
                Spacer(Modifier.height(24.dp))
                Button(onClose) { Text("Zurück") }
            }
            return@Scaffold
        }
        val (w, item) = pair
        var flipped by remember(item, w.reps) { mutableStateOf(item.intro) }
        var dragX by remember(item, w.reps) { mutableFloatStateOf(0f) }
        val animX by animateFloatAsState(dragX, label = "drag")
        LaunchedEffect(item, w.reps) { if (item.dir == Dir.REC) speak(w.speakText) }
        val reveal = { flipped = true; if (item.dir == Dir.PROD) speak(w.speakText) }

        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                w.category + if (item.dir == Dir.PROD && !item.intro) "  •  Übersetzung → Deutsch" else "",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
            ElevatedCard(
                Modifier.fillMaxWidth().weight(1f)
                    .graphicsLayer { translationX = animX; rotationZ = animX / 40f }
                    .pointerInput(item, w.reps, flipped) {
                        if (!flipped || item.intro) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragX > 250f -> vm.answer(true)
                                    dragX < -250f -> vm.answer(false)
                                }
                                dragX = 0f
                            },
                            onDragCancel = { dragX = 0f }
                        ) { _, d -> dragX += d }
                    }
                    .clickable(enabled = !flipped) { reveal() }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CardBody(w, item, flipped, speak)
                }
            }

            when {
                item.intro -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ vm.markKnown() }, Modifier.weight(1f).height(56.dp)) { Text("✅ Kenne ich schon") }
                    Button({ vm.startLearning() }, Modifier.weight(1f).height(56.dp)) { Text("🔁 Lernen") }
                }
                !flipped -> Button(reveal, Modifier.fillMaxWidth().height(56.dp)) { Text("Antwort zeigen") }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        { vm.answer(false) }, Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("← Nochmal") }
                    Button(
                        { vm.answer(true) }, Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("Gewusst →") }
                }
            }
        }
    }
}

@Composable
private fun CardBody(w: Word, item: Item, flipped: Boolean, speak: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WordTags(w, isNew = item.intro)
        Spacer(Modifier.height(8.dp))
        if (item.dir == Dir.PROD && !item.intro) {
            Text("Wie sagt man auf Deutsch?", style = MaterialTheme.typography.labelLarge)
            Text(w.translation, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            if (!flipped && w.exampleEn.isNotBlank()) Text(
                "„${w.exampleEn}“", style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center
            )
            if (flipped) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                GermanText(w, MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                IconButton({ speak(w.speakText) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Aussprechen") }
                WordDetails(w, speak)
            }
        } else {
            GermanText(w, MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
            IconButton({ speak(w.speakText) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Aussprechen") }
            if (flipped) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(w.translation, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                WordDetails(w, speak)
            }
        }
        if (!flipped) {
            Spacer(Modifier.height(16.dp))
            Text("Tippen zum Aufdecken", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** der / die / das drill. Doesn't change your learning progress. */
@Composable
fun QuizScreen(vm: MainViewModel, speak: (String) -> Unit, back: () -> Unit) {
    val w by vm.quizWord.collectAsState()
    val round by vm.quizRound.collectAsState()
    val score by vm.quizScore.collectAsState()
    var picked by remember(round) { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Artikel-Quiz   ${score.first} / ${score.second}") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
        )
    }) { pad ->
        val word = w
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (word == null) {
                Text("Noch keine Nomen vorhanden. Aktiviere Themen mit Nomen oder füge Wörter hinzu.")
                return@Column
            }
            Spacer(Modifier.height(32.dp))
            Text("___ ${word.noun}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(word.translation, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("der", "die", "das").forEach { a ->
                    Button(
                        onClick = {
                            if (picked == null) { picked = a; vm.answerQuiz(a == word.article); speak(word.speakText) }
                        },
                        modifier = Modifier.weight(1f).height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = articleColor(a)!!)
                    ) { Text(a, style = MaterialTheme.typography.titleLarge) }
                }
            }
            picked?.let { p ->
                Text(
                    if (p == word.article) "✓ Richtig!" else "✗ Falsch",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (p == word.article) DasColor else DieColor
                )
                GermanText(word, MaterialTheme.typography.headlineSmall)
                if (word.plural.isNotBlank()) Text("Plural: ${word.plural}")
                if (word.example.isNotBlank()) Text(word.example, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                Button({ vm.nextQuiz() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Weiter") }
            }
        }
    }
}
