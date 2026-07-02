package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import org.skia.codec.SkAnimCodecPlayer

private const val CELL_SIZE = 100

open class AnimCodecPlayerExifGm(
    private val path: String,
) : SkiaGm {
    final override val name = "AnimCodecPlayerExif_${path.substringAfterLast('/')}"
    final override val renderFamily = RenderFamily.IMAGE
    final override val minSimilarity = 0.0
    final override val width = 300
    final override val height = 300

    final override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val data = loadResource(path)
            ?: error("Resource not found: $path")
        val codec = Codec.MakeFromData(data)
            ?: error("Codec.MakeFromData returned null for $path")
        val player = SkAnimCodecPlayer(codec)
        val frameCount = player.getFrameCount()
        val totalDuration = player.duration()

        canvas.drawColor(1f, 1f, 1f)
        if (frameCount == 0) return

        val cols = kotlin.math.ceil(kotlin.math.sqrt(frameCount.toFloat())).toInt()

        for (i in 0 until frameCount) {
            val ms = if (totalDuration > 0) totalDuration * i / frameCount else 0
            player.seek(ms)
            val frameImage = player.getFrameAsImage() ?: continue

            val col = i % cols
            val row = i / cols
            val x = (col * CELL_SIZE).toFloat()
            val y = (row * CELL_SIZE).toFloat()
            val fw = frameImage.width.toFloat()
            val fh = frameImage.height.toFloat()

            canvas.drawImage(frameImage, Rect(x, y, x + fw, y + fh))
        }
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }
}

class AnimCodecPlayerExifRequiredWebpGm : AnimCodecPlayerExifGm("images/required.webp")
class AnimCodecPlayerExifRequiredGifGm : AnimCodecPlayerExifGm("images/required.gif")
class AnimCodecPlayerExifStoplightHWebpGm : AnimCodecPlayerExifGm("images/stoplight_h.webp")
