package dev.rafael.app.screens.exercise

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Cache de disco ÚNICO do ExoPlayer. O SimpleCache exige uma só instância por diretório —
 * por isso singleton de processo.
 *
 * Sem cache, o REPEAT_MODE_ONE re-baixa o mp4 a cada volta (uma GET por loop ~6s). Com cache,
 * baixa uma vez e os loops tocam do disco. É também o cache de vídeo no aparelho (teto 250MB, LRU).
 */
@OptIn(UnstableApi::class)
object ExoCache {
    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(app.cacheDir, "exo-media"),
                LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024),
                StandaloneDatabaseProvider(app),
            ).also { instance = it }
        }
    }
}
