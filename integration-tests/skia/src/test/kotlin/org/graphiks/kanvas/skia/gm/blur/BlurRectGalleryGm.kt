package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurrect.cpp::blurrect_gallery` (1200 x 1024).
 * Exercises blurred rect masks across sizes, blur radii, and blur styles.
 * @see https://github.com/google/skia/blob/main/gm/blurrect.cpp
 */
class BlurRectGalleryGm : SkiaGm {
    override val name = "blurrect_gallery"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val fGMWidth = 1200
        val fPadding = 10
        val fMargin = 100

        val widths = intArrayOf(25, 5, 5, 100, 150, 25)
        val heights = intArrayOf(100, 100, 5, 25, 150, 25)
        val styles = arrayOf(BlurStyle.NORMAL, BlurStyle.INNER, BlurStyle.OUTER)
        val radii = floatArrayOf(20f, 5f, 10f)

        canvas.translate(50f, 20f)

        var curX = 0
        var curY = 0
        var maxHeight = 0

        for (i in widths.indices) {
            val rectW = widths[i]
            val rectH = heights[i]
            val r = Rect.fromXYWH(0f, 0f, rectW.toFloat(), rectH.toFloat())

            canvas.save()

            for (radius in radii) {
                val sigma = convertRadiusToSigma(radius)

                for (style in styles) {
                    val paint = Paint(
                        maskFilter = MaskFilter.Blur(style, sigma),
                    )

                    val blurPad = ceil(sigma * 3f).toInt()
                    val maskW = rectW + 2 * blurPad
                    val maskH = rectH + 2 * blurPad

                    val pixels = ByteArray(maskW * maskH)
                    for (by in 0 until maskH) {
                        for (bx in 0 until maskW) {
                            val inside = if (bx in blurPad until (blurPad + rectW) &&
                                by in blurPad until (blurPad + rectH)
                            ) 1f else 0f

                            var alpha = inside
                            if (sigma > 0f && inside > 0f) {
                                var total = 0f
                                var weightSum = 0f
                                val k = ceil(sigma * 3f).toInt()
                                for (ky in -k..k) {
                                    for (kx in -k..k) {
                                        val gx = bx + kx
                                        val gy = by + ky
                                        if (gx in 0 until maskW && gy in 0 until maskH) {
                                            val insideG = if (gx in blurPad until (blurPad + rectW) &&
                                                gy in blurPad until (blurPad + rectH)
                                            ) 1f else 0f
                                            val w = kotlin.math.exp(-(kx * kx + ky * ky) / (2f * sigma * sigma))
                                            total += insideG * w
                                            weightSum += w
                                        }
                                    }
                                }
                                alpha = total / weightSum
                            }

                            if (style == BlurStyle.OUTER) {
                                alpha = (alpha - if (inside > 0f) 1f else 0f).coerceAtLeast(0f)
                            } else if (style == BlurStyle.INNER) {
                                alpha = if (inside > 0f) 1f - (1f - alpha) * 2f else 0f
                                alpha = alpha.coerceIn(0f, 1f)
                            }

                            val alphaByte = (alpha * 255f).toInt().coerceIn(0, 255)
                            pixels[by * maskW + bx] = alphaByte.toByte()
                        }
                    }

                    val bm = Image.fromPixels(maskW, maskH, pixels, ColorType.ALPHA_8, "blur-$rectW-$rectH-$radius-$style")

                    if (curX + bm.width >= fGMWidth - fMargin) {
                        curX = 0
                        curY += maxHeight + fPadding
                        maxHeight = 0
                    }

                    canvas.save()
                    canvas.translate(curX.toFloat(), curY.toFloat())
                    canvas.translate(
                        -(bm.width - r.width) / 2f,
                        -(bm.height - r.height) / 2f,
                    )
                    canvas.drawImage(bm, Rect.fromXYWH(0f, 0f, bm.width.toFloat(), bm.height.toFloat()))
                    canvas.restore()

                    curX += bm.width + fPadding
                    if (bm.height > maxHeight) maxHeight = bm.height
                }
            }

            canvas.restore()
        }
    }

    private fun convertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f

    private fun ceil(v: Float): Float = kotlin.math.ceil(v.toDouble()).toFloat()
}
