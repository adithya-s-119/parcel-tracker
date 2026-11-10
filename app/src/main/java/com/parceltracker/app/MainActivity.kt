package com.parceltracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parceltracker.app.data.HtmlText
import com.parceltracker.app.ui.MainViewModel
import com.parceltracker.app.ui.ParcelAppRoot
import com.parceltracker.app.ui.components.ParcelWordmark
import com.parceltracker.app.ui.theme.ParcelTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            ParcelTheme {
                val ready by viewModel.ready.collectAsStateWithLifecycle()
                if (ready) {
                    ParcelAppRoot(viewModel = viewModel)
                } else {
                    LoadingScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: html?.let(HtmlText::toPlain)
            ?: return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()

        viewModel.handleSharedText(subject, text)
        intent.action = null
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ParcelWordmark()
            Spacer(modifier = Modifier.height(22.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
