package io.github.touya0.nimbe.feature.notifications

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.notifications.Banner
import io.github.touya0.nimbe.data.notifications.NotificationCenter
import io.github.touya0.nimbe.data.notifications.NotificationEntry
import io.github.touya0.nimbe.data.notifications.NotificationLevel
import io.github.touya0.nimbe.data.notifications.Presence
import io.github.touya0.nimbe.ui.theme.Alerte
import io.github.touya0.nimbe.ui.theme.EnLigne
import io.github.touya0.nimbe.ui.theme.Focus
import io.github.touya0.nimbe.ui.theme.Lune
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.LuneAttenueeBase
import io.github.touya0.nimbe.ui.theme.Panneau
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun NotificationEntry.color(): Color = when {
    kicker == "SERVICE RÉTABLI" -> EnLigne
    notificationLevel == NotificationLevel.Critical -> Alerte
    notificationLevel == NotificationLevel.Normal -> Focus
    else -> LuneAttenueeBase
}

/**
 * Le bandeau par-dessus Nimbe, lecteur compris. Il ne prend jamais le focus : le film continue et la
 * télécommande reste à l'écran en dessous. Retour ferme un bandeau critique (voir MainActivity).
 */
@Composable
fun BannerHost(center: NotificationCenter) {
    val banner by center.banner.collectAsStateWithLifecycle()
    val current = banner ?: return
    Box(Modifier.fillMaxSize().padding(top = 84.dp, end = 48.dp), contentAlignment = Alignment.TopEnd) {
        BannerCard(current)
    }
}

@Composable
private fun BannerCard(banner: Banner) {
    val entry = banner.entry
    val accent = entry.color()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(420.dp)
            .background(Panneau, shape)
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(entry.kicker, style = MaterialTheme.typography.labelMedium, color = accent)
        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Lune, maxLines = 2)
        entry.body?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = LuneAttenuee, maxLines = 2) }
        if (banner.persistent) Text("Retour pour fermer", style = MaterialTheme.typography.labelMedium, color = LuneAttenuee)
    }
}

/**
 * Le même bandeau quand une autre appli est à l'écran, si la permission « afficher par-dessus les autres
 * applis » est accordée (par adb). Sans focus possible, on ne peut pas le fermer depuis l'autre appli :
 * il s'efface après [OTHER_APP_MS], et une alerte critique attend au retour dans Nimbe.
 * Des vues Android simples : hors de l'activité, Compose n'a pas de cycle de vie sur lequel s'appuyer.
 */
class BannerOverlay(private val context: Context, private val center: NotificationCenter, private val scope: CoroutineScope) {

    fun start() {
        scope.launch {
            combine(center.banner, center.presence) { banner, presence -> banner.takeIf { presence == Presence.OtherApp } }
                .collectLatest { banner ->
                    if (banner == null || !Settings.canDrawOverlays(context)) return@collectLatest
                    withContext(Dispatchers.Main) {
                        val manager = context.getSystemService(WindowManager::class.java)
                        val view = build(banner)
                        val added = runCatching { manager.addView(view, params()) }.isSuccess
                        try {
                            delay(OTHER_APP_MS)
                        } finally {
                            if (added) runCatching { manager.removeView(view) }
                        }
                    }
                }
        }
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(48)
        y = dp(48)
    }

    private fun build(banner: Banner): View {
        val entry = banner.entry
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            minimumWidth = dp(420)
            background = GradientDrawable().apply {
                setColor(Panneau.toArgb())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), entry.color().toArgb())
            }
            addView(line(entry.kicker, 12f, entry.color().toArgb()))
            addView(line(entry.title, 18f, AndroidColor.WHITE))
            entry.body?.let { addView(line(it, 13f, LuneAttenueeBase.toArgb())) }
        }
    }

    private fun line(text: String, size: Float, color: Int) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        maxLines = 2
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val OTHER_APP_MS = 12_000L
    }
}
