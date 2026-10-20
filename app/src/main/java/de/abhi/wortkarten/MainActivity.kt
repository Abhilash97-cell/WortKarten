package de.abhi.wortkarten

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.abhi.wortkarten.ui.App
import de.abhi.wortkarten.ui.MainViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Phone's built-in speech engine (offline once the German voice is installed).
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = (tts?.setLanguage(Locale.GERMANY) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
                tts?.setSpeechRate(0.9f)
            }
        }
        handleShare(intent)
        setContent { App(vm, speak = ::speak) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text shared from another app, or selected text via "Zu WortKarten". */
    private fun handleShare(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) vm.sharedText.value = text.trim().take(200)
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wk")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
