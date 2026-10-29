package app.efir

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val settings = Settings(app)

    var channels by mutableStateOf(repo.cached())
        private set
    var epg by mutableStateOf<Map<String, List<Programme>>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var maxHeight by mutableStateOf(settings.maxHeight)
        private set

    var current by mutableStateOf<Channel?>(null)
        private set
    var streamUrl by mutableStateOf<String?>(null)
        private set
    var streamHeaders by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var streamError by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
    var group by mutableStateOf<String?>(null)

    val source: String get() = settings.source
    val region: String get() = settings.region
    val tz: String get() = settings.tz
    val playlistUrl: String get() = settings.playlistUrl
    val epgUrl: String get() = settings.epgUrl

    private var retries = 0

    init { refresh() }

    private fun newApi() = LimeApi(settings.deviceId, settings.region, settings.tz)

    fun saveSettings(
        source: String, playlist: String, epgUrl: String,
        region: String, tz: String, maxH: Int,
    ) {
        settings.source = source
        settings.playlistUrl = playlist.trim()
        settings.epgUrl = epgUrl.trim()
        settings.region = region.trim().ifEmpty { "77" }
        settings.tz = tz.trim().ifEmpty { "3" }
        settings.maxHeight = maxH
        maxHeight = maxH
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                if (settings.source == "m3u") {
                    val url = settings.playlistUrl
                    if (url.isBlank()) {
                        error = "Bitte in den Einstellungen eine Playlist-URL (M3U) eintragen."
                    } else {
                        channels = repo.refreshM3u(url)
                    }
                } else {
                    channels = repo.refreshLime(newApi())
                }
            } catch (ex: Exception) {
                error = "Laden fehlgeschlagen: ${ex.message}"
            }
            loading = false
            val epgSource = settings.epgUrl
            if (settings.source == "m3u" && epgSource.isNotBlank()) {
                try { epg = repo.loadEpg(epgSource) } catch (_: Exception) { }
            }
        }
    }

    fun select(ch: Channel) {
        current = ch
        retries = 0
        loadStream()
    }

    /** Vom Player bei Fehlern aufgerufen. Abgelaufene Token werden neu geholt. */
    fun onPlayerError() {
        val ch = current ?: return
        if (ch.slug != null && retries < 2) {
            retries++
            loadStream()
        }
    }

    private fun loadStream() {
        val ch = current ?: return
        streamError = null
        val slug = ch.slug
        if (slug == null) {
            streamHeaders = emptyMap()
            streamUrl = ch.url
            return
        }
        val api = newApi()
        streamHeaders = api.headers
        streamUrl = null
        viewModelScope.launch {
            try {
                val u = api.streamUrl(slug)
                if (current == ch) streamUrl = u
            } catch (ex: Exception) {
                if (current == ch) streamError = ex.message
            }
        }
    }

    fun nowPlaying(c: Channel): String? {
        val now = System.currentTimeMillis()
        return epg[c.id]?.firstOrNull { now >= it.start && now < it.stop }?.title
    }
}
