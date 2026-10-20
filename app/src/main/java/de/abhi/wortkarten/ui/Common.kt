@file:OptIn(ExperimentalLayoutApi::class)

package de.abhi.wortkarten.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.abhi.wortkarten.data.Status
import de.abhi.wortkarten.data.Word

// der = blue, die = red, das = green
val DerColor = Color(0xFF1E6FD9)
val DieColor = Color(0xFFD93A3A)
val DasColor = Color(0xFF2E9A44)
val UmgColor = Color(0xFFE08A00)
val FormellColor = Color(0xFF7B5EA7)

fun articleColor(a: String?): Color? = when (a) {
    "der" -> DerColor
    "die" -> DieColor
    "das" -> DasColor
    else -> null
}

fun statusColor(s: Status): Color = when (s) {
    Status.NEW -> Color(0xFF607D8B)
    Status.KNOWN -> Color(0xFF00897B)
    Status.PRACTICE -> Color(0xFFE08A00)
    Status.LEARNED -> Color(0xFF2E9A44)
}

/** German word with the article coloured (der/die/das). */
@Composable
fun GermanText(w: Word, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val col = articleColor(w.article)
    val text = if (col == null) AnnotatedString(w.german) else buildAnnotatedString {
        withStyle(SpanStyle(color = col, fontWeight = FontWeight.Bold)) { append(w.german.substringBefore(' ')) }
        append(" ")
        withStyle(SpanStyle(color = col)) { append(w.noun) }
    }
    Text(text = text, modifier = modifier, style = style, textAlign = textAlign)
}

@Composable
fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color, style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun WordTags(w: Word, isNew: Boolean = false, showStatus: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isNew) Tag("NEU", MaterialTheme.colorScheme.tertiary)
        Tag(w.level, MaterialTheme.colorScheme.primary)
        w.registerLabel?.let { Tag(it, if (it == "formell") FormellColor else UmgColor) }
        if (w.subtopic.isNotBlank()) Tag(w.subtopic, MaterialTheme.colorScheme.outline)
        if (showStatus) Tag(w.status.emoji + " " + w.status.label, statusColor(w.status))
    }
}

/** Plural, grammar, example + translation, usage note. */
@Composable
fun WordDetails(w: Word, speak: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (w.plural.isNotBlank()) Text("Plural: ${w.plural}", style = MaterialTheme.typography.bodyMedium)
        if (w.grammar.isNotBlank()) Text(
            w.grammar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
        )
        if (w.example.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "🔊 " + w.example, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                modifier = Modifier.clickable { speak(w.example) }
            )
            if (w.exampleEn.isNotBlank()) Text(
                w.exampleEn, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (w.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("💡 " + w.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
