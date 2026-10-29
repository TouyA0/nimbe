package io.github.touya0.nimbe.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.touya0.nimbe.data.nextcloud.NextcloudAuth
import io.github.touya0.nimbe.data.nextcloud.NextcloudGallery
import io.github.touya0.nimbe.data.profiles.Profile
import io.github.touya0.nimbe.data.profiles.ProfileKind
import io.github.touya0.nimbe.ui.components.bleed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * Rangée « Souvenirs » de l'accueil : les photos prises autour de cette date les années passées, puis
 * celles du mois. OK ouvre directement le plein écran. Rien du tout (pas même le titre) pour un profil
 * sans galerie reliée, ou s'il n'y a aucun souvenir à montrer.
 */
@Composable
fun GalleryHomeRow(gallery: NextcloudGallery, profile: Profile?, onOpen: (GalleryViewerRequest) -> Unit) {
    if (profile == null || profile.kind != ProfileKind.Adult) return
    val today = remember { LocalDate.now() }
    val memories by remember(profile.id) {
        gallery.all(profile.id).combine(gallery.link(profile.id)) { items, link ->
            if (link == null) emptyList()
            else GalleryMemories.onThisDay(items, today) + listOfNotNull(GalleryMemories.thisMonth(items, today))
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val userId by remember(profile.id) { gallery.link(profile.id) }.collectAsStateWithLifecycle(initialValue = null)
    val auth by produceState<NextcloudAuth?>(null, profile.id, userId) { value = gallery.auth(profile.id) }
    val current = auth ?: return
    val id = userId?.userId ?: return
    if (memories.isEmpty()) return

    Column {
        Text("Souvenirs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
        LazyRow(
            modifier = Modifier.bleed(48.dp),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(memories, key = { "souvenir:" + it.kicker + it.title }) { memory ->
                CollectionCard(
                    current,
                    cover = memory.cover,
                    kicker = memory.kicker,
                    title = memory.title,
                    detail = GalleryText.monthCounts(memory.photos, memory.videos),
                    width = 300,
                    onClick = { onOpen(GalleryViewerRequest(current, id, memory.items, 0)) },
                )
            }
        }
    }
}
