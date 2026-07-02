package org.skia.codec

import org.graphiks.kanvas.codec.AnimatedImage
import org.graphiks.kanvas.codec.AndroidCodec
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import java.nio.ByteBuffer

class SkAnimCodecPlayer(codec: Codec) {
    private var animImage: AnimatedImage? = null
    private val frameDurations: List<Int>
    val totalDuration: Int
    private var repCount: Int = Codec.kRepetitionCountInfinite
    private var pxWidth: Int = 0
    private var pxHeight: Int = 0

    init {
        val androidCodec = AndroidCodec.MakeFromCodec(codec)
        animImage = AnimatedImage.Make(androidCodec)
        frameDurations = codec.getFrameInfo().map { it.durationMs }
        totalDuration = frameDurations.sum()
        pxWidth = codec.dimensions().width
        pxHeight = codec.dimensions().height
    }

    fun getFrame(): SkImage? = animImage?.getCurrentFrame()

    fun getFrameAsImage(): Image? {
        val skImg = getFrame() ?: return null
        val w = skImg.width
        val h = skImg.height
        val info = SkImageInfo.MakeN32Premul(w, h)
        val rowBytes = info.minRowBytes()
        val dst = ByteBuffer.allocate(h * rowBytes)
        skImg.readPixels(info, dst, rowBytes, 0, 0)
        val bytes = ByteArray(dst.remaining()).also { dst.get(it) }
        return Image.fromPixels(w, h, bytes)
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
        animImage?.let { anim ->
            anim.reset()
            for (i in 0 until frame) {
                val duration = anim.decodeNextFrame()
                if (duration == AnimatedImage.kFinished) break
            }
        }
    }
}
