package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.ui.components.ContentRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Rangée « Souvenirs » de l'accueil : les photos prises autour de cette date les années passées, puis
 * celles du mois. OK ouvre directement le plein écran. Rien du tout (pas même le titre) pour un profil
 * sans galerie reliée, ou s'il n'y a aucun souvenir à montrer. [onFocus] reçoit l'accroche, le titre,
 * le détail et la couverture du souvenir focalisé ; [first] s'applique à la première carte.
 */
@Composable
fun GalleryHomeRow(
    gallery: NextcloudGallery,
    profile: Profile?,
    onOpen: (GalleryViewerRequest) -> Unit,
    onFocus: (kicker: String, title: String, detail: String, image: Any?) -> Unit = { _, _, _, _ -> },
    state: LazyListState = rememberLazyListState(),
    first: Modifier = Modifier,
) {
    if (profile == null || profile.kind != ProfileKind.Adult) return
    val today = remember { LocalDate.now() }
    val context = LocalPlatformContext.current
    val memories by remember(profile.id) {
        combine(gallery.all(profile.id), gallery.link(profile.id), gallery.prefs.prefs(profile.id)) { items, link, prefs ->
            // Souvenirs coupés dans les réglages de la galerie : pas de rangée du tout.
            if (link == null || !prefs.memories) {
                emptyList()
            } else {
                val shown = prefs.filter(items)
                GalleryMemories.onThisDay(shown, today) + listOfNotNull(GalleryMemories.thisMonth(shown, today))
            }
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val slideSeconds by remember(profile.id) { gallery.prefs.prefs(profile.id).map { it.slideSeconds } }.collectAsStateWithLifecycle(initialValue = 8)
    val userId by remember(profile.id) { gallery.link(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val auth by produceState<NextcloudAuth?>(null, profile.id, userId) { value = gallery.auth(profile.id) }
    val current = auth ?: return
    val id = userId?.userId ?: return
    if (memories.isEmpty()) return

    ContentRow("Souvenirs", state = state) {
        itemsIndexed(memories, key = { _, it -> "souvenir:" + it.kicker + it.title }) { index, memory ->
            val detail = GalleryText.monthCounts(memory.photos, memory.videos)
            CollectionCard(
                current,
                cover = memory.cover,
                kicker = memory.kicker,
                title = memory.title,
                detail = detail,
                width = 240,
                onClick = { onOpen(GalleryViewerRequest(current, id, memory.items, 0, slideMs = slideSeconds * 1000L)) },
                modifier = if (index == 0) first else Modifier,
                onFocus = {
                    onFocus(memory.kicker, memory.title, detail, memory.cover?.let { previewRequest(context, current, it, BACKDROP_WIDTH, BACKDROP_HEIGHT, crop = true) })
                },
            )
        }
    }
}

/** Assez pour un fond fondu, sans demander l'original au serveur. */
private const val BACKDROP_WIDTH = 960
private const val BACKDROP_HEIGHT = 540
