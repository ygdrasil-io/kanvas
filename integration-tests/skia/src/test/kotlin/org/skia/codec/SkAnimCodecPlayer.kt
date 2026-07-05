package org.skia.codec

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType

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
        val info = codec.getInfo().makeColorType(SkColorType.kRGBA_8888)
        val bitmap = SkBitmap(
            width = info.width,
            height = info.height,
            colorSpace = info.colorSpace,
            colorType = info.colorType,
        )
        val result = codec.getPixels(
            info = info,
            dst = bitmap,
            opts = Codec.Options(frameIndex = currentFrameIndex),
        )
        if (result != Codec.Result.kSuccess) return null
        return bitmap.toImage()
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

    private fun SkBitmap.toImage(): Image {
        val rgba = ByteArray(width * height * 4)
        var di = 0
        for (pixel in pixels8888) {
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            rgba[di] = b.toByte()
            rgba[di + 1] = g.toByte()
            rgba[di + 2] = r.toByte()
            rgba[di + 3] = a.toByte()
            di += 4
        }
        return Image.fromPixels(width, height, rgba)
    }
}
