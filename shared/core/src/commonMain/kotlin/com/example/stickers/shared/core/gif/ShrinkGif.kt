package com.example.stickers.shared.core.gif

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A GIF re-encoded at the stored size. */
class ShrunkGif(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * Shrinks a GIF so its longest side is [maxSide], one frame at a time, off the calling thread.
 * [onProgress] gets the share of frames done (0 to 1) after each frame. Cancellation is checked
 * between frames. Returns null when the GIF is broken or over [GifLimits].
 */
suspend fun shrinkGif(bytes: ByteArray, maxSide: Int, onProgress: (Float) -> Unit = {}): ShrunkGif? =
    withContext(Dispatchers.Default) {
        val reader = try {
            GifReader(bytes)
        } catch (e: GifException) {
            return@withContext null
        }
        val shrinker = GifShrinker(reader, maxSide)
        val writer = GifWriter(shrinker.width, shrinker.height, shrinker.loopCount)
        var done = 0
        for (frame in shrinker.frames()) {
            ensureActive()
            writer.add(frame)
            done++
            onProgress(done.toFloat() / shrinker.frameCount)
        }
        ShrunkGif(writer.finish(), shrinker.width, shrinker.height)
    }
