package org.skia.codec

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image

class SkAnimCodecPlayer(
    private val codec: Codec,
) {
    private val frameDurations: List<Int>
    val totalDuration: Int
    private var repCount: Int = codec.getRepetitionCount()
    private var currentFrameIndex: Int = 0

    init {
        frameDurations = codec.getFrameInfo().map { it.durationMs }
        totalDuration = frameDurations.sum()
    }

    fun getFrameAsImage(): Image? {
        if (frameDurations.isEmpty()) return null
        val info = codec.getInfo().makeColorType(ColorType.RGBA_8888)
        val bitmap = Bitmap(info)
        val result = codec.getPixels(
            info = info,
            dst = bitmap,
            opts = Codec.Options(frameIndex = currentFrameIndex),
        )
        if (result != Codec.Result.kSuccess) return null
        return bitmap.toImageOrNull()
    }

    fun seek(ms: Int): Boolean {
        if (frameDurations.isEmpty()) return false
        val clamped = if (totalDuration > 0) ms % totalDuration else 0
        var accumulated = 0
        for (i in frameDurations.indices) {
            if (accumulated + frameDurations[i] > clamped) {
                seekToFrame(i)
                return true
            }
            accumulated += frameDurations[i]
        }
        seekToFrame(frameDurations.size - 1)
        return true
    }

    fun duration(): Int = totalDuration

    fun getFrameCount(): Int = frameDurations.size

    fun repetitionCount(): Int = repCount

    fun setRepetitionCount(count: Int) {
        repCount = count
    }

    private fun seekToFrame(frame: Int) {
        currentFrameIndex = if (frameDurations.isEmpty()) {
            0
        } else {
            frame.coerceIn(0, frameDurations.lastIndex)
        }
    }
}
