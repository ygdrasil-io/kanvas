package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/tilemodes.cpp::tilemode_decal`.
 * Demonstrates kDecal tile mode combined with other tile modes on image and gradient shaders.
 * @see https://github.com/google/skia/blob/main/gm/tilemodes.cpp
 */
class TilemodeDecalGm : SkiaGm {
    override val name = "tilemode_decal"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 720
    override val height = 1100

    private val sourceImage: Image = run {
        val w = 64; val h = 64
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                pixels[i] = ((x * 255 / (w - 1)) and 0xFF).toByte()
                pixels[i + 1] = ((y * 255 / (h - 1)) and 0xFF).toByte()
                pixels[i + 2] = (((x + y) * 255 / (w + h - 2)) and 0xFF).toByte()
                pixels[i + 3] = 0xFF.toByte()
            }
        }
        Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "mandrill-standin")
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = sourceImage
        val bgPaint = Paint(color = Color(0xFFFFFF00u))

        val imgW = img.width.toFloat()
        val imgH = img.height.toFloat()
        val r = Rect(-20f, -20f, imgW + 20f, imgH + 20f)

        canvas.translate(45f, 45f)

        data class XY(val tx: TileMode, val ty: TileMode)
        val pairs = listOf(
            XY(TileMode.CLAMP, TileMode.CLAMP),
            XY(TileMode.CLAMP, TileMode.DECAL),
            XY(TileMode.DECAL, TileMode.CLAMP),
            XY(TileMode.DECAL, TileMode.DECAL),
        )

        for (pair in pairs) {
            canvas.save()
            // 1. Nearest
            run {
                canvas.save(); canvas.rotate(4f)
                canvas.drawRect(r, bgPaint)
                canvas.drawRect(r, Paint(shader = img.makeShader(pair.tx, pair.ty, SamplingOptions.NEAREST)))
                canvas.restore(); canvas.translate(0f, r.height + 20f)
            }
            // 2. Linear
            run {
                canvas.save(); canvas.rotate(4f)
                canvas.drawRect(r, bgPaint)
                canvas.drawRect(r, Paint(shader = img.makeShader(pair.tx, pair.ty, SamplingOptions.LINEAR)))
                canvas.restore(); canvas.translate(0f, r.height + 20f)
            }
            // 3. Bicubic
            run {
                canvas.save(); canvas.rotate(4f)
                canvas.drawRect(r, bgPaint)
                canvas.drawRect(r, Paint(shader = img.makeShader(pair.tx, pair.ty, SamplingOptions.Cubic.Mitchell)))
                canvas.restore(); canvas.translate(0f, r.height + 20f)
            }
            // 4. Linear gradient
            run {
                canvas.save(); canvas.rotate(4f)
                canvas.drawRect(r, bgPaint)
                val grad = Shader.LinearGradient(
                    Point(0f, 0f), Point(imgW, imgH),
                    listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
                    pair.tx,
                )
                canvas.drawRect(r, Paint(shader = grad))
                canvas.restore(); canvas.translate(0f, r.height + 20f)
            }
            // 5. Radial gradient
            run {
                canvas.save(); canvas.rotate(4f)
                canvas.drawRect(r, bgPaint)
                val center = Point(imgW * 0.5f, imgW * 0.5f)
                val rad = imgW * 0.5f
                val grad = Shader.RadialGradient(
                    center, rad,
                    listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
                    pair.tx,
                )
                canvas.drawRect(r, Paint(shader = grad))
                canvas.restore(); canvas.translate(0f, r.height + 20f)
            }
            canvas.restore()
            canvas.translate(r.width + 10f, 0f)
        }
    }
}
