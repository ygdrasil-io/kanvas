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

class DrawimagerectFilterGm : SkiaGm {
    override val name = "drawimagerect_filter"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 180
    override val height = 60

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = checkerboardImage(50, 50, 0xFFFFFFFFu.toInt(), 0xFF000000u.toInt(), 1)
        canvas.translate(5f, 5f)
        canvas.drawImage(img, Rect(0.5f, 0.5f, 50.5f, 50.5f))
        canvas.translate(60f, 0f)
        canvas.drawImageRect(img, Rect(0f, 0f, 50f, 50f), Rect(0.5f, 0.5f, 50.5f, 50.5f))
        canvas.translate(60f, 0f)
        val lm = Matrix33.translate(0.5f, 0.5f)
        val shader = Shader.WithLocalMatrix(img.makeShader(TileMode.CLAMP, TileMode.CLAMP), lm)
        canvas.drawRect(Rect(0f, 0f, 50f, 50f), Paint(shader = shader))
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
