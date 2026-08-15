package dev.rafael.app.screens.exercise

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.rafael.app.ui.shimmer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Toca um mp4 em LOOP e MUDO, sem controles — efeito "GIF" da demonstração do exercício.
 *
 * - CacheDataSource (ExoCache): baixa o mp4 uma vez e toca os loops do disco (sem re-baixar).
 * - Loading: enquanto o player não fica READY, sobrepõe um spinner e pinta o shutter com a cor
 *   de placeholder — em vez da tela preta padrão do PlayerView.
 * - O ExoPlayer é criado por URL e liberado no dispose (não vaza player).
 */
@OptIn(UnstableApi::class)
@Composable
fun ExerciseVideoLoop(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shutterColor = MaterialTheme.colorScheme.surfaceVariant
    var loading by remember(url) { mutableStateOf(true) }

    val player = remember(url) {
        val cacheFactory = CacheDataSource.Factory()
            .setCache(ExoCache.get(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE   // loop infinito
                volume = 0f                           // mudo
                playWhenReady = true                  // autoplay
                prepare()
            }
    }

    DisposableEffect(url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) loading = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(shutterColor.toArgb())   // não preto
                }
            },
        )
        if (loading) {
            Box(Modifier.matchParentSize().shimmer())
        }
    }
}
