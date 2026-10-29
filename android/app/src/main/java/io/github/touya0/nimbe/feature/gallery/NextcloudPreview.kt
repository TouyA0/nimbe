package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.ui.theme.LuneAttenuee
import io.github.touya0.nimbe.ui.theme.PanneauClair
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.touya0.nimbe.data.nextcloud.GalleryItem
import io.github.touya0.nimbe.data.nextcloud.NextcloudApi
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth

/**
 * Aperçu calculé par Nextcloud, jamais le fichier d'origine : [width]×[height] en pixels, recadré.
 * Le mot de passe d'application part dans l'en-tête Authorization ; l'adresse, qui sert de clé
 * au cache d'images, n'en contient rien. Sans aperçu (vidéo sans ffmpeg sur le serveur, photo trop
 * grande pour sa mémoire : il répond 404), on affiche le nom du fichier à la place.
 */
@Composable
fun NextcloudPreview(
    auth: NextcloudAuth,
    item: GalleryItem,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalPlatformContext.current
    val request = remember(auth, item.fileId, width, height) {
        ImageRequest.Builder(context)
            .data(NextcloudApi.previewUrl(auth.server, item.fileId, width, height, crop = true))
            .httpHeaders(NetworkHeaders.Builder().set("Authorization", NextcloudApi.basic(auth)).build())
            .build()
    }
    var failed by remember(request) { mutableStateOf(!item.hasPreview) }
    if (failed) {
        Box(modifier.background(PanneauClair), contentAlignment = Alignment.Center) {
            Text(
                item.path.substringAfterLast('/'),
                style = MaterialTheme.typography.labelMedium,
                color = LuneAttenuee,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        onError = { failed = true },
    )
}
