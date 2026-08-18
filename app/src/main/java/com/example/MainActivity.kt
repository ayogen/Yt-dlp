package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainApp
import com.example.ui.MainViewModel
import com.example.ui.theme.VideoDownloaderTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle shared URL from external apps
        handleIncomingIntent(intent)

        setContent {
            VideoDownloaderTheme(darkTheme = true) {
                MainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                val extractedUrl = extractUrl(sharedText)
                if (extractedUrl.isNotBlank()) {
                    viewModel.analyzeUrl(extractedUrl)
                }
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            intent.dataString?.let { uriString ->
                if (uriString.isNotBlank()) {
                    viewModel.analyzeUrl(uriString)
                }
            }
        }
    }

    private fun extractUrl(text: String): String {
        val parts = text.split("\\s+".toRegex())
        return parts.firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) } ?: text.trim()
    }
}
