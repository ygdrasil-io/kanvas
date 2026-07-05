package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/lighting.cpp::ImageLightingGM` (660 x 660).
 * 6-column x 6-row grid exercising C1.7 lighting filters
 * (PointLitDiffuse / DistantLitDiffuse / SpotLitDiffuse x specular variants)
 * on a 100x100 input bitmap.
 * @see https://github.com/google/skia/blob/main/gm/lighting.cpp
 */
class LightingGm : SkiaGm {
    override val name = "lighting"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = WIDTH
    override val height = HEIGHT

    private lateinit var fBitmap: Image
    private val fAzimuth = 225f

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val surf = Surface(100, 100)
        surf.canvas {
            drawRect(Rect(0f, 0f, 100f, 100f), Paint(color = Color.fromRGBA(0f, 0f, 0f, 0f)))
            drawRect(Rect.fromXYWH(20f, 20f, 60f, 60f), Paint(color = Color.WHITE))
        }
        fBitmap = surf.makeImageSnapshot()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0.063f, 0.063f, 0.063f, 1f)))

        drawChecker(canvas)

        val sinA = sin(Math.toRadians(fAzimuth.toDouble())).toFloat()
        val cosA = cos(Math.toRadians(fAzimuth.toDouble())).toFloat()

        val spotTarget = Point(40f, 40f)
        val spotLocation = Point(
            spotTarget.x + 70.7214f * cosA,
            spotTarget.y + 70.7214f * sinA,
        )
        val pointLocation = Point(
            spotTarget.x + 50f * cosA,
            spotTarget.y + 50f * sinA,
        )
        val elevationRad = Math.toRadians(5.0).toFloat()
        val cosEl = cos(elevationRad)
        val sinEl = sin(elevationRad)
        val distantDirection = Point(cosA * cosEl, sinA * cosEl)

        val kd = 2f
        val ks = 1f
        val shininess = 8f
        val surfaceScale = 1f
        val surfaceScaleSmall = 0.1f
        val greenYellow = Color.fromRGBA(173f / 255f, 255f / 255f, 47f / 255f)
        val spotExponent1 = 1f
        val spotExponent10 = 10f
        val cutoffAngleSmall = 15f
        val cutoffAngleNone = 180f

        var y = 0

        var paint = Paint(
            imageFilter = ImageFilter.PointLitDiffuse(pointLocation, Color.WHITE, surfaceScale, kd, null),
        )
        drawClipped(canvas, paint, 0, y)

        paint = paint.copy(
            imageFilter = ImageFilter.DistantLitDiffuse(distantDirection, Color.WHITE, surfaceScale, kd, null),
        )
        drawClipped(canvas, paint, 110, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitDiffuse(spotLocation, spotTarget, spotExponent1, cutoffAngleSmall, Color.WHITE, surfaceScale, kd, null),
        )
        drawClipped(canvas, paint, 220, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitDiffuse(spotLocation, spotTarget, spotExponent10, cutoffAngleNone, Color.WHITE, surfaceScale, kd, null),
        )
        drawClipped(canvas, paint, 330, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitDiffuse(spotLocation, spotTarget, spotExponent1, cutoffAngleNone, Color.WHITE, surfaceScaleSmall, kd, null),
        )
        drawClipped(canvas, paint, 440, y)

        paint = paint.copy(
            imageFilter = ImageFilter.DistantLitDiffuse(distantDirection, greenYellow, surfaceScale, 4f * kd, null),
        )
        drawClipped(canvas, paint, 550, y)

        y += 110

        paint = paint.copy(
            imageFilter = ImageFilter.PointLitSpecular(pointLocation, Color.WHITE, surfaceScale, ks, shininess, null),
        )
        drawClipped(canvas, paint, 0, y)

        paint = paint.copy(
            imageFilter = ImageFilter.DistantLitSpecular(distantDirection, Color.WHITE, surfaceScale, ks, shininess, null),
        )
        drawClipped(canvas, paint, 110, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitSpecular(spotLocation, spotTarget, spotExponent1, cutoffAngleSmall, Color.WHITE, surfaceScale, ks, shininess, null),
        )
        drawClipped(canvas, paint, 220, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitSpecular(spotLocation, spotTarget, spotExponent10, cutoffAngleNone, Color.WHITE, surfaceScale, ks, shininess, null),
        )
        drawClipped(canvas, paint, 330, y)

        paint = paint.copy(
            imageFilter = ImageFilter.SpotLitSpecular(spotLocation, spotTarget, spotExponent1, cutoffAngleNone, Color.WHITE, surfaceScaleSmall, ks, shininess, null),
        )
        drawClipped(canvas, paint, 440, y)

        paint = paint.copy(
            imageFilter = ImageFilter.DistantLitSpecular(distantDirection, greenYellow, surfaceScale, 4f * ks, shininess, null),
        )
        drawClipped(canvas, paint, 550, y)
    }

    private fun drawClipped(canvas: GmCanvas, paint: Paint, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(Rect.fromXYWH(0f, 0f, 100f, 100f))
        canvas.drawImage(fBitmap, Rect(0f, 0f, 100f, 100f), paint)
        canvas.restore()
    }

    private fun drawChecker(canvas: GmCanvas) {
        val checkPaint = Paint(color = Color.fromRGBA(0.125f, 0.125f, 0.125f, 1f))
        for (yy in 0 until HEIGHT step 16) {
            for (xx in 0 until WIDTH step 16) {
                canvas.save()
                canvas.translate(xx.toFloat(), yy.toFloat())
                canvas.drawRect(Rect.fromXYWH(8f, 0f, 8f, 8f), checkPaint)
                canvas.drawRect(Rect.fromXYWH(0f, 8f, 8f, 8f), checkPaint)
                canvas.restore()
            }
        }
    }

    companion object {
        const val WIDTH = 660
        const val HEIGHT = 660
    }
}
