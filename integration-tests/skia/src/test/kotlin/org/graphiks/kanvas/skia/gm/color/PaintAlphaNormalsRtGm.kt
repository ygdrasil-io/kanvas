package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests paint alpha with normal-map shader rendering across four tiled quadrants. */
class PaintAlphaNormalsRtGm : SkiaGm {
    override val name = "paint_alpha_normals_rt"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val normalMapImage = makeNormalMapImage()
        val normalShader = normalMapImage.makeShader(sampling = SamplingOptions.LINEAR)

        val draw = { x: Int, y: Int, shader: Shader? ->
            val paint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 164f / 255f), shader = shader)
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.clipRect(Rect(0f, 0f, 256f, 256f))
            canvas.drawRect(Rect(0f, 0f, 256f, 256f), paint)
            canvas.restore()
        }

        draw(0, 0, normalShader)
        draw(0, 256, normalShader)
        draw(256, 0, normalShader)
        draw(256, 256, normalShader)
    }

    private fun makeNormalMapImage(): Image {
        val w = 256; val h = 256
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val nx = (x.toFloat() / w) * 2f - 1f
                val ny = (y.toFloat() / h) * 2f - 1f
                val len = kotlin.math.sqrt((nx * nx + ny * ny).coerceAtLeast(0f))
                val nz = if (len < 1f) kotlin.math.sqrt(1f - len * len) else 0f
                pixels[i] = ((nx * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i + 1] = ((ny * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i + 2] = ((nz * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i + 3] = (-1).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "normal-map")
    }
}
