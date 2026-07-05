package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class DrawimageSamplingGm : SkiaGm {
    override val name = "drawimage_sampling"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val n = 256
        val kScale = 1.0f / 6
        val dst = Rect(0f, 0f, kScale * n, kScale * n)
        val img = checkerboardImage(n, n, 0xFF000000u.toInt(), 0xFFFFFFFFu.toInt(), 7)
        val src = Rect(0f, 0f, img.width.toFloat(), img.height.toFloat())
        val mx = Matrix33.scale(dst.width / src.width, dst.height / src.height)

        for (mm in listOf(false, true)) {
            for (fm in listOf(false, true)) {
                canvas.save()
                canvas.concat(mx)
                canvas.drawImage(img, Rect(0f, 0f, img.width.toFloat(), img.height.toFloat()))
                canvas.restore()

                canvas.translate(dst.width + 4f, 0f)
                canvas.drawRect(dst, Paint(shader = img.makeShader(TileMode.CLAMP, TileMode.CLAMP)))
                canvas.translate(dst.width + 4f, 0f)

                canvas.drawImageRect(img, src, dst)
                canvas.restore()
                canvas.translate(0f, dst.height + 8f)
            }
        }
    }

    private fun checkerboardImage(w: Int, h: Int, c0: Int, c1: Int, size: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val on = ((x / size) + (y / size)) % 2 == 0
                val c = if (on) c0 else c1
                val i = (y * w + x) * 4
                pixels[i] = (c and 0xFF).toByte()
                pixels[i + 1] = ((c ushr 8) and 0xFF).toByte()
                pixels[i + 2] = ((c ushr 16) and 0xFF).toByte()
                pixels[i + 3] = ((c ushr 24) and 0xFF).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels)
    }
}
