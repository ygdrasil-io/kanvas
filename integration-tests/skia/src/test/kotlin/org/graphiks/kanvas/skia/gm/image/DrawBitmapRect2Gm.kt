package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprect.cpp` DrawBitmapRect2.
 * Draws a 64x64 image (red + white-to-blue gradient circle) four times
 * with different source rects. The FLOAT variant passes src as float rect;
 * the INT variant uses rects derived from the original pixel rect.
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
open class DrawBitmapRect2Gm(private val variant: Variant) : SkiaGm {
    enum class Variant(val suffix: String) {
        FLOAT("s"), INT("i"),
    }

    override val name = "bitmaprect_${variant.suffix}"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private var cachedImage: Image? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = cachedImage ?: makeImage().also { cachedImage = it }
        canvas.drawColor(r = 0.8f, g = 0.8f, b = 0.8f)

        val srcs = listOf(
            Rect.fromLTRB(0f, 0f, 32f, 32f),
            Rect.fromLTRB(0f, 0f, 80f, 80f),
            Rect.fromLTRB(32f, 32f, 96f, 96f),
            Rect.fromLTRB(-32f, -32f, 32f, 32f),
        )

        val stroke = Paint(style = PaintStyle.STROKE, color = Color.BLACK, strokeWidth = 1f)
        val dstR = Rect.fromLTRB(0f, 200f, 128f, 380f)

        canvas.save()
        canvas.translate(16f, 40f)
        for (src in srcs) {
            canvas.drawImage(image, Rect(0f, 0f, 64f, 64f), stroke)
            when (variant) {
                Variant.FLOAT -> canvas.drawImageRect(image, src, dstR, stroke)
                Variant.INT -> canvas.drawImageRect(
                    image,
                    Rect.fromLTRB(src.left, src.top, src.right, src.bottom),
                    dstR,
                    stroke,
                )
            }
            canvas.drawRect(dstR, stroke)
            canvas.drawRect(src, stroke)
            canvas.translate(160f, 0f)
        }
        canvas.restore()
    }

    private fun makeImage(): Image {
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(Color.RED)
            val path = Path { }.apply { addCircle(32f, 32f, 32f) }
            val shader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(64f, 64f),
                stops = listOf(
                    GradientStop(0f, Color.WHITE),
                    GradientStop(1f, Color.BLUE),
                ),
            )
            drawPath(path, Paint(shader = shader, antiAlias = true))
        }
        return surface.makeImageSnapshot()
    }
}

class DrawBitmapRect2FloatGm : DrawBitmapRect2Gm(DrawBitmapRect2Gm.Variant.FLOAT)
class DrawBitmapRect2IntGm : DrawBitmapRect2Gm(DrawBitmapRect2Gm.Variant.INT)
