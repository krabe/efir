@file:OptIn(ExperimentalMaterial3Api::class)

package app.efir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private val qualities = listOf(0 to "Auto", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var fullscreen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val activity = LocalContext.current as ComponentActivity

    LaunchedEffect(fullscreen) {
        val c = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(fullscreen) { fullscreen = false }

    Column(Modifier.fillMaxSize()) {
        if (vm.current != null) {
            PlayerBox(
                url = vm.streamUrl,
                headers = vm.streamHeaders,
                loadError = vm.streamError,
                maxHeight = vm.maxHeight,
                onPlayerError = { vm.onPlayerError() },
                onToggleFullscreen = { fullscreen = !fullscreen },
                modifier = if (fullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16 / 9f),
            )
        }

        if (!fullscreen) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    singleLine = true,
                    placeholder = { Text("Sender suchen") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Einstellungen") }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }

            val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct() }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(vm.group == null, { vm.group = null }, { Text("Alle") }) }
                items(groups) { g -> FilterChip(vm.group == g, { vm.group = g }, { Text(g) }) }
            }

            val shown = remember(vm.channels, vm.query, vm.group) {
                vm.channels.filter {
                    (vm.group == null || it.group == vm.group) && it.name.contains(vm.query, ignoreCase = true)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown) { ch ->
                    val selected = vm.current == ch
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { vm.select(ch) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(ch.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            vm.nowPlaying(ch)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
fun PlayerBox(
    url: String?,
    headers: Map<String, String>,
    loadError: String?,
    maxHeight: Int,
    onPlayerError: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val onErr by rememberUpdatedState(onPlayerError)
    var err by remember { mutableStateOf<String?>(null) }

    val player = remember(headers) {
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        headers["User-Agent"]?.let { factory.setUserAgent(it) }
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                err = "Stream nicht verfügbar (${error.errorCodeName})"
                onErr()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) err = null
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(player, url) {
        err = null
        if (url != null) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }
    }

    LaunchedEffect(player, maxHeight) {
        val h = if (maxHeight > 0) maxHeight else Int.MAX_VALUE
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon().setMaxVideoSize(Int.MAX_VALUE, h).build()
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { keepScreenOn = true } },
            update = {
                it.player = player
                it.setFullscreenButtonClickListener { onToggleFullscreen() }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val message = loadError ?: err
        if (message != null) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        } else if (url == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(vm.source) }
    var playlist by remember { mutableStateOf(vm.playlistUrl) }
    var epg by remember { mutableStateOf(vm.epgUrl) }
    var region by remember { mutableStateOf(vm.region) }
    var tz by remember { mutableStateOf(vm.tz) }
    var quality by remember { mutableStateOf(vm.maxHeight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quelle")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(source == "limehd", { source = "limehd" }, { Text("LimeHD-API") })
                    FilterChip(source == "m3u", { source = "m3u" }, { Text("M3U-Playlist") })
                }
                if (source == "limehd") {
                    OutlinedTextField(region, { region = it }, label = { Text("Region-Code") }, singleLine = true)
                    OutlinedTextField(tz, { tz = it }, label = { Text("Zeitzone (UTC+)") }, singleLine = true)
                } else {
                    OutlinedTextField(playlist, { playlist = it }, label = { Text("Playlist-URL (M3U)") }, singleLine = true)
                    OutlinedTextField(epg, { epg = it }, label = { Text("Programmführer (XMLTV, optional)") }, singleLine = true)
                }
                Text("Maximale Qualität")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualities.forEach { (h, label) ->
                        FilterChip(quality == h, { quality = h }, { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ vm.saveSettings(source, playlist, epg, region, tz, quality); onDismiss() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}
