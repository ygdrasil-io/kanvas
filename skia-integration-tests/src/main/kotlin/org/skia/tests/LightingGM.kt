package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of upstream Skia's `gm/lighting.cpp::ImageLightingGM`
 * (`DEF_GM(return new ImageLightingGM;)`).
 *
 * 6-column × 6-row grid exercising the C1.7 lighting filters
 * (`PointLitDiffuse` / `DistantLitDiffuse` / `SpotLitDiffuse` ×
 * specular variants) on a 100×100 input bitmap, with rotating
 * azimuth angle (static azimuth = 225° for DM render).
 *
 * **Adaptations** :
 *  - The input bitmap upstream is built via
 *    `ToolUtils.CreateStringBitmap(100, 100, white, 20, 70, 96, "e")`
 *    — i.e. the letter 'e' rendered to a 100×100 surface.
 *    We substitute a simple white-filled rounded blob (a
 *    rectangle centred at (50, 50) of size 60×60) — the lighting
 *    interaction with the shape edges drives the visible output.
 *  - `cropRect` parameters in upstream are not exposed by our
 *    lighting factories. The 3-row outer loop reduces to a single
 *    iteration ; the GM renders only the no-crop variants. Output
 *    height is therefore ~220 instead of 660.
 */
public class LightingGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0, 0, 0))
    }

    override fun getName(): String = "lighting"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private lateinit var fBitmap: SkBitmap
    private val fAzimuth: Float = K_START_AZIMUTH

    override fun onOnceBeforeDraw() {
        fBitmap = SkBitmap(100, 100)
        // Black background, white centred rectangle as the lit shape.
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val onShape = x in 20..80 && y in 20..80
                fBitmap.setPixel(x, y, if (onShape) SK_ColorWHITE else SkColorSetARGB(0, 0, 0, 0))
            }
        }
    }

    private fun drawClippedBitmap(canvas: SkCanvas, paint: SkPaint, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(SkRect.Make(SkIRect.MakeWH(fBitmap.width, fBitmap.height)))
        canvas.drawImage(fBitmap.asImage(), 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SkColorSetARGB(0xFF, 0x10, 0x10, 0x10))

        // Checker background — only top-left of each 16×16 cell.
        val checkPaint = SkPaint().apply { color = SkColorSetARGB(0xFF, 0x20, 0x20, 0x20) }
        for (yy in 0 until HEIGHT step 16) {
            for (xx in 0 until WIDTH step 16) {
                c.save()
                c.translate(xx.toFloat(), yy.toFloat())
                c.drawRect(SkRect.MakeXYWH(8f, 0f, 8f, 8f), checkPaint)
                c.drawRect(SkRect.MakeXYWH(0f, 8f, 8f, 8f), checkPaint)
                c.restore()
            }
        }

        val sinA = sin(Math.toRadians(fAzimuth.toDouble())).toFloat()
        val cosA = cos(Math.toRadians(fAzimuth.toDouble())).toFloat()

        val spotTarget = floatArrayOf(40f, 40f, 0f)
        val spotLocation = floatArrayOf(
            spotTarget[0] + 70.7214f * cosA,
            spotTarget[1] + 70.7214f * sinA,
            spotTarget[2] + 20f,
        )
        val pointLocation = floatArrayOf(
            spotTarget[0] + 50f * cosA,
            spotTarget[1] + 50f * sinA,
            10f,
        )
        val elevationRad = Math.toRadians(5.0).toFloat()
        val cosEl = cos(elevationRad)
        val sinEl = sin(elevationRad)
        val distantDirection = floatArrayOf(cosA * cosEl, sinA * cosEl, sinEl)

        val kd = 2f
        val ks = 1f
        val shininess = 8f
        val surfaceScale = 1f
        val surfaceScaleSmall = 0.1f
        val greenYellow = SkColorSetARGB(255, 173, 255, 47)
        val spotExponent1 = 1f
        val spotExponent10 = 10f
        val cutoffAngleSmall = 15f
        val cutoffAngleNone = 180f

        var y = 0

        // Diffuse row.
        var paint = SkPaint().apply {
            imageFilter = SkImageFilters.PointLitDiffuse(pointLocation, SK_ColorWHITE, surfaceScale, kd, null)
        }
        drawClippedBitmap(c, paint, 0, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.DistantLitDiffuse(distantDirection, SK_ColorWHITE, surfaceScale, kd, null)
        }
        drawClippedBitmap(c, paint, 110, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitDiffuse(
                spotLocation, spotTarget, spotExponent1, cutoffAngleSmall, SK_ColorWHITE,
                surfaceScale, kd, null,
            )
        }
        drawClippedBitmap(c, paint, 220, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitDiffuse(
                spotLocation, spotTarget, spotExponent10, cutoffAngleNone, SK_ColorWHITE,
                surfaceScale, kd, null,
            )
        }
        drawClippedBitmap(c, paint, 330, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitDiffuse(
                spotLocation, spotTarget, spotExponent1, cutoffAngleNone, SK_ColorWHITE,
                surfaceScaleSmall, kd, null,
            )
        }
        drawClippedBitmap(c, paint, 440, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.DistantLitDiffuse(distantDirection, greenYellow, surfaceScale, 4f * kd, null)
        }
        drawClippedBitmap(c, paint, 550, y)

        y += 110

        // Specular row.
        paint = SkPaint().apply {
            imageFilter = SkImageFilters.PointLitSpecular(pointLocation, SK_ColorWHITE, surfaceScale, ks, shininess, null)
        }
        drawClippedBitmap(c, paint, 0, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.DistantLitSpecular(distantDirection, SK_ColorWHITE, surfaceScale, ks, shininess, null)
        }
        drawClippedBitmap(c, paint, 110, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitSpecular(
                spotLocation, spotTarget, spotExponent1, cutoffAngleSmall, SK_ColorWHITE,
                surfaceScale, ks, shininess, null,
            )
        }
        drawClippedBitmap(c, paint, 220, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitSpecular(
                spotLocation, spotTarget, spotExponent10, cutoffAngleNone, SK_ColorWHITE,
                surfaceScale, ks, shininess, null,
            )
        }
        drawClippedBitmap(c, paint, 330, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.SpotLitSpecular(
                spotLocation, spotTarget, spotExponent1, cutoffAngleNone, SK_ColorWHITE,
                surfaceScaleSmall, ks, shininess, null,
            )
        }
        drawClippedBitmap(c, paint, 440, y)

        paint = SkPaint().apply {
            imageFilter = SkImageFilters.DistantLitSpecular(distantDirection, greenYellow, surfaceScale, 4f * ks, shininess, null)
        }
        drawClippedBitmap(c, paint, 550, y)
    }

    private companion object {
        private const val WIDTH: Int = 660
        private const val HEIGHT: Int = 660
        private const val K_START_AZIMUTH: Float = 225f
    }
}
