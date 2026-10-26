package io.github.touya0.nimbe

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.touya0.nimbe.feature.apps.AppsRepository
import io.github.touya0.nimbe.feature.apps.InstalledApp
import io.github.touya0.nimbe.shell.NimbeApp
import io.github.touya0.nimbe.ui.theme.NimbeTheme

class MainActivity : ComponentActivity() {

    private val apps by lazy { AppsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NimbeTheme {
                NimbeApp(repository = apps, onOpen = ::open)
            }
        }
    }

    private fun open(app: InstalledApp) {
        val intent = apps.launchIntent(app.packageName)
        try {
            if (intent == null) throw ActivityNotFoundException()
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }
}
