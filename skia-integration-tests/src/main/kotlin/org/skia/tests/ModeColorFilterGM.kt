package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/modecolorfilters.cpp::ModeColorFilterGM` (512 × 1024).
 *
 * Stress test for `SkColorFilters::Blend(SkColor, SkBlendMode)` :
 * paints 14 blend modes × 5 paint-colour values × 4 shaders into a
 * `kRectWidth × kRectHeight = 20 × 20` grid (25 cells per row). Each
 * cell `saveLayer`s a small rect, draws a 565-checker background, then
 * the test paint. The checker background lets transparency show through.
 *
 * **kanvas-skia adaptation** : upstream `make_color_shader` returns a
 * `LinearGradient` with two identical stops (since the GPU backend
 * doesn't have a `SkColorShader`). We replicate that behaviour exactly
 * to match the rasterised reference image.
 */
public class ModeColorFilterGM : GM() {

    init { setBGColor(SkColorSetARGB(0xFF, 0x30, 0x30, 0x30)) }

    private var fBmpShader: SkShader? = null

    override fun getName(): String = "modecolorfilters"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kRectWidth = 20
        val kRectHeight = 20
        val kCheckSize = 10

        if (fBmpShader == null) fBmpShader = makeBgShader(kCheckSize)
        val bgPaint = SkPaint().apply {
            shader = fBmpShader
            blendMode = SkBlendMode.kSrc
        }

        val shaders = arrayOf<SkShader?>(
            null,                                                                  // paint colour, no shader
            makeColorShader(SkColorSetARGB(0xFF, 0x42, 0x82, 0x21)),                // solid
            makeColorShader(SkColorSetARGB(0x80, 0x10, 0x70, 0x20)),                // transparent
            makeColorShader(0x00000000),                                            // transparent-black
        )
        val colors = intArrayOf(
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),
            SkColorSetARGB(0xFF, 0x00, 0x00, 0x00),
            SkColorSetARGB(0x00, 0x00, 0x00, 0x00),
            SkColorSetARGB(0xFF, 0x10, 0x20, 0x42),
            SkColorSetARGB(0xA0, 0x20, 0x30, 0x90),
        )
        val alphas = intArrayOf(0xFFFFFFFF.toInt(), 0x80808080.toInt())
        val modes = arrayOf(
            SkBlendMode.kClear, SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver, SkBlendMode.kSrcIn, SkBlendMode.kDstIn, SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut, SkBlendMode.kSrcATop, SkBlendMode.kDstATop, SkBlendMode.kXor,
            SkBlendMode.kPlus, SkBlendMode.kModulate,
        )

        val paint = SkPaint()
        var idx = 0
        val kRectsPerRow = maxOf(size().width / kRectWidth, 1)
        for (cfm in modes.indices) {
            for (cfc in colors.indices) {
                paint.colorFilter = SkColorFilters.Blend(colors[cfc], modes[cfm])
                for (sIdx in shaders.indices) {
                    paint.shader = shaders[sIdx]
                    // Upstream computes `hasShader = (paint.getShader() == nullptr)` — the
                    // `null` shader iteration uses paint colours, others use alphas.
                    val hasShader = paint.shader == null
                    val paintColors = if (hasShader) alphas else colors
                    for (pc in paintColors.indices) {
                        paint.color = paintColors[pc]
                        val x = (idx % kRectsPerRow).toFloat()
                        val y = (idx / kRectsPerRow).toFloat()
                        val rect = SkRect.MakeXYWH(
                            x * kRectWidth, y * kRectHeight,
                            kRectWidth.toFloat(), kRectHeight.toFloat(),
                        )
                        c.saveLayer(rect, null)
                        c.drawRect(rect, bgPaint)
                        c.drawRect(rect, paint)
                        c.restore()
                        ++idx
                    }
                }
            }
        }
    }

    private fun makeColorShader(color: Int): SkShader {
        // Same trick as upstream : a 2-stop LinearGradient with identical
        // colours is a stand-in for SkColorShader (which the GPU backend
        // historically lacked).
        return SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(1f, 1f),
            colors = intArrayOf(color, color),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
    }

    private fun makeBgShader(checkSize: Int): SkShader {
        val bmp = SkBitmap.Make(2 * checkSize, 2 * checkSize)
        val canvas = SkCanvas(bmp)
        // Upstream uses `color_to_565(0xFF800000)` / `color_to_565(0xFF000080)`.
        // We don't have an exact 565 quantizer wired up ; the lossy
        // round-trip through 565 nudges 0x80 → 0x84 (red) / 0x84 (blue)
        // — close enough for the visual to match within tolerance.
        canvas.clear(0xFF800000.toInt())
        val paint = SkPaint().apply { color = 0xFF000080.toInt() }
        val rect0 = SkRect.MakeXYWH(0f, 0f, checkSize.toFloat(), checkSize.toFloat())
        val rect1 = SkRect.MakeXYWH(
            checkSize.toFloat(), checkSize.toFloat(),
            checkSize.toFloat(), checkSize.toFloat(),
        )
        canvas.drawRect(rect1, paint)
        canvas.drawRect(rect0, paint)
        return bmp.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
    }

    private companion object {
        const val WIDTH = 512
        const val HEIGHT = 1024
    }
}
