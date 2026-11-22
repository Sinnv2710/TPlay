package app.tplay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.tplay.ui.nav.AppNav
import app.tplay.ui.theme.TPlayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = magnetFrom(intent)
        setContent {
            TPlayTheme {
                AppNav(incomingMagnet = incoming.value, onIncomingUsed = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        magnetFrom(intent)?.let { incoming.value = it }
    }

    private fun magnetFrom(intent: Intent?): String? {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        return text?.trim()?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
    }
}
