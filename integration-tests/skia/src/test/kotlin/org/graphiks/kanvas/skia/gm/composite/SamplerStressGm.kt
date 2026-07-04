package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/samplerstress.cpp::SamplerStressGM` (640 × 480).
 * Renders a textured glyph clipped by a small AA round-rect with a blur mask filter.
 * @see https://github.com/google/skia/blob/main/gm/samplerstress.cpp
 */
class SamplerStressGm : SkiaGm {
    override val name = "gpusamplerstress"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Build a 16x16 striped texture shader
        val texPixels = ByteArray(16 * 16 * 4)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val off = (y * 16 + x) * 4
                val c = when {
                    y % 5 == 0 -> byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte()) // RED
                    x % 7 == 0 -> byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()) // GREEN
                    else -> byteArrayOf(0, 0, 0, 0xFF.toByte()) // BLACK
                }
                c.copyInto(texPixels, off)
            }
        }
        val texture = Image.fromPixels(16, 16, texPixels, ColorType.RGBA_8888, "stripes")
        val shader = texture.makeShader(TileMode.REPEAT, TileMode.REPEAT)

        canvas.save()
        val paint = Paint(
            shader = shader,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f),
        )
        val font = Font(typeface, size = 72f)
        val temp = Rect.fromLTRB(115f, 75f, 144f, 110f)
        val path = Path { }.apply { addRRect(RRect(temp, 5f)) }
        canvas.clipPath(path, antiAlias = true)
        canvas.drawString("M", 100f, 100f, font, paint)
        canvas.restore()

        val outline = Paint(color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 1f)
        canvas.drawString("M", 100f, 100f, font, outline)
    }
}
