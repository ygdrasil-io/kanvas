package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.PI

/**
 * Port of Skia's `gm/anisotropic.cpp` — anisotropic sampling variant.
 *
 * Same radiating-line image as the linear/mip variants, drawn into a grid
 * of progressively squashed rects using `SkSamplingOptions::Aniso(16)`.
 * @see https://github.com/google/skia/blob/main/gm/anisotropic.cpp
 */
class AnisotropicImageScaleAnisoGm : SkiaGm {
    override val name = "anisotropic_image_scale_aniso"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2 * kImageSize + 3 * kSpacer
    override val height = kNumVertImages * kImageSize + (kNumVertImages + 1) * kSpacer

    private val fImage: Image by lazy { makeSourceImage() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = fImage
        val gScales = floatArrayOf(0.9f, 0.8f, 0.75f, 0.6f, 0.5f, 0.4f, 0.25f, 0.2f, 0.1f)

        for (i in gScales.indices) {
            val h = floor(img.height * gScales[i]).toInt()
            val yOff: Int = if (i <= gScales.size / 2) {
                kSpacer + i * (img.height + kSpacer)
            } else {
                (gScales.size - i) * (img.height + kSpacer) - h
            }
            val dst = Rect(
                kSpacer.toFloat(), yOff.toFloat(),
                (kSpacer + img.width).toFloat(), (yOff + h).toFloat(),
            )
            canvas.drawImageRect(img, Rect(0f, 0f, img.width.toFloat(), img.height.toFloat()), dst)
        }

        for (i in gScales.indices) {
            val w = floor(img.width * gScales[i]).toInt()
            val xOff: Int
            val yOff: Int
            if (i <= gScales.size / 2) {
                xOff = img.width + 2 * kSpacer
                yOff = kSpacer + i * (img.height + kSpacer)
            } else {
                xOff = img.width + 2 * kSpacer + img.width - w
                yOff = kSpacer + (gScales.size - i - 1) * (img.height + kSpacer)
            }
            val dst = Rect(
                xOff.toFloat(), yOff.toFloat(),
                (xOff + w).toFloat(), (yOff + img.height).toFloat(),
            )
            canvas.drawImageRect(img, Rect(0f, 0f, img.width.toFloat(), img.height.toFloat()), dst)
        }
    }

    private fun makeSourceImage(): Image {
        val surf = Surface(kImageSize, kImageSize)
        surf.canvas {
            val paint = Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f)
            val kNumLines = 100
            val kAngleStep = 360f / kNumLines
            val kInnerOffset = 10
            translate(kImageSize / 2f, kImageSize / 2f)
            var angleDeg = 0f
            for (i in 0 until kNumLines) {
                val angleRad = angleDeg * (PI.toFloat() / 180f)
                val s = sin(angleRad)
                val c = cos(angleRad)
                drawLine(
                    c * kInnerOffset, s * kInnerOffset,
                    c * kImageSize / 2f, s * kImageSize / 2f,
                    paint,
                )
                angleDeg += kAngleStep
            }
        }
        return surf.makeImageSnapshot()
    }

    private companion object {
        const val kImageSize: Int = 256
        const val kSpacer: Int = 10
        const val kNumVertImages: Int = 5
    }
}
