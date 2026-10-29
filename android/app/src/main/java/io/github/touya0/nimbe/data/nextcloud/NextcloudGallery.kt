package io.github.touya0.nimbe.data.nextcloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Où en est la synchronisation d'une galerie. [error] : dernier échec, en français ; la grille
 * continue d'afficher l'index déjà enregistré. [lost] : le serveur refuse l'accès, il faut relier à nouveau.
 */
data class GallerySyncState(val syncing: Boolean = false, val error: String? = null, val lost: Boolean = false)

/**
 * Les galeries Nextcloud des profils : un index local (Room) rempli par WebDAV SEARCH, que les écrans
 * lisent directement. On resynchronise au plus toutes les [MIN_INTERVAL_MS], ou à la demande.
 * L'index entier est remplacé d'un coup : les suppressions sur le serveur disparaissent aussi de la TV.
 */
class NextcloudGallery(
    private val accounts: NextcloudAccounts,
    private val dao: GalleryDao,
    /** Pour les synchronisations qui doivent survivre à l'écran qui les lance. */
    private val scope: CoroutineScope,
) {

    private val states = ConcurrentHashMap<String, MutableStateFlow<GallerySyncState>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun state(profileId: String): StateFlow<GallerySyncState> = stateFlow(profileId).asStateFlow()

    /** Le profil a-t-il une galerie reliée ? */
    fun linked(profileId: String): Flow<Boolean> = accounts.links.map { profileId in it }

    fun counts(profileId: String): Flow<GalleryCounts> = dao.counts(profileId)

    fun syncedAt(profileId: String): Flow<Long?> = dao.syncedAt(profileId)

    fun all(profileId: String): Flow<List<GalleryItem>> = dao.all(profileId)

    fun recent(profileId: String, limit: Int): Flow<List<GalleryItem>> = dao.recent(profileId, limit)

    suspend fun page(profileId: String, offset: Int, limit: Int): List<GalleryItem> = dao.page(profileId, offset, limit)

    /** De quoi demander les aperçus de ce profil ; null s'il n'est pas relié. */
    suspend fun auth(profileId: String): NextcloudAuth? = accounts.auth(profileId)

    /**
     * Met l'index à jour s'il date de plus de [MIN_INTERVAL_MS] (ou toujours avec [force]).
     * Une seule synchronisation à la fois par profil : un deuxième appel attend la première, puis la trouve fraîche.
     */
    suspend fun sync(profileId: String, force: Boolean = false) {
        val lock = locks.getOrPut(profileId) { Mutex() }
        lock.withLock {
            val last = dao.syncedAt(profileId).first()
            if (!force && last != null && System.currentTimeMillis() - last < MIN_INTERVAL_MS) return
            val auth = accounts.auth(profileId) ?: return
            val link = accounts.links.first()[profileId] ?: return
            val state = stateFlow(profileId)
            state.update { it.copy(syncing = true) }
            try {
                val items = fetchAll(auth, link.userId, link.folders).map { it.toItem(profileId) }
                dao.replace(profileId, items, System.currentTimeMillis())
                state.value = GallerySyncState()
            } catch (e: NextcloudException) {
                val lost = e.kind == NextcloudException.Kind.Unauthorized
                state.value = GallerySyncState(
                    error = if (lost) "Accès retiré sur Nextcloud : relie à nouveau la galerie dans Réglages" else e.message,
                    lost = lost,
                )
            }
        }
    }

    /** Galerie déconnectée ou profil supprimé : on oublie son index et son état. */
    suspend fun forget(profileId: String) {
        dao.forget(profileId)
        states.remove(profileId)
    }

    /** Tout le compte, ou chaque dossier choisi l'un après l'autre. */
    private suspend fun fetchAll(auth: NextcloudAuth, userId: String, folders: List<String>): List<GalleryFile> {
        val all = LinkedHashMap<Long, GalleryFile>()
        for (folder in folders.ifEmpty { listOf(null) }) {
            var offset = 0
            while (all.size < MAX_ITEMS) {
                val (files, read) = accounts.api.searchMedia(auth, userId, folder, offset, PAGE_SIZE)
                // Un fichier modifié pendant la lecture peut changer de page : l'identifiant évite les doublons.
                files.forEach { all[it.fileId] = it }
                if (read < PAGE_SIZE) break
                offset += read
            }
        }
        return all.values.toList()
    }

    /**
     * Change les dossiers montrés : l'index actuel ne correspond plus, on le resynchronise tout de suite
     * (il reste affiché pendant ce temps, puis il est remplacé d'un coup).
     */
    suspend fun setFolders(profileId: String, folders: List<String>) {
        accounts.setFolders(profileId, folders)
        scope.launch { sync(profileId, force = true) }
    }

    /** Sous-dossiers de [folder] (null : racine du compte) ; null si le profil n'est pas relié. */
    suspend fun folders(profileId: String, folder: String?): List<String>? {
        val auth = accounts.auth(profileId) ?: return null
        val userId = accounts.links.first()[profileId]?.userId ?: return null
        return accounts.api.folders(auth, userId, folder)
    }

    private fun stateFlow(profileId: String) = states.getOrPut(profileId) { MutableStateFlow(GallerySyncState()) }

    private fun GalleryFile.toItem(profileId: String) =
        GalleryItem(profileId, fileId, path, mime, takenAt, size, width, height, hasPreview)

    companion object {
        const val MIN_INTERVAL_MS = 10 * 60_000L
        private const val PAGE_SIZE = 500
        /** Garde-fou : au-delà, la TV n'en ferait rien d'utile et la base grossirait sans fin. */
        private const val MAX_ITEMS = 100_000
    }
}
