package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/filterbug.cpp::FilterBugGM`
 * (registered name `"filterbug"`, 150 × 150, BG = `SK_ColorRED`).
 *
 * Tiny regression GM tracking
 * [crbug.com/673261](https://crbug.com/673261). Three rectangles are
 * stacked along the y-axis on the right half of the canvas, the top
 * and bottom are filled by a [SkImage.makeShader] bitmap-shader with
 * a Mitchell-cubic sampler and a `[2× scale + translate]` local
 * matrix, while the middle is a plain white fill. The shader source
 * images are 25 × 27 (top : 5 black rows at the top, 22 white rows
 * below; bottom : 22 white rows at the top, 5 black rows below) —
 * the bug shipped a clamping issue that smeared the cubic-sampled
 * black row across the joint between the texture-fill rects and the
 * white-fill in the middle. The reference picks up that mis-clamping
 * (or absence of it) along the y-axis seams.
 *
 * `kDoAA = true`, sampler is `SkCubicResampler.Mitchell` and the
 * texture tile mode is `kRepeat` along both axes (chosen by upstream
 * specifically to expose the bug — under `kClamp` it wouldn't fire).
 */
public class FilterBugGM : GM() {

    init { setBGColor(SK_ColorRED) }

    override fun getName(): String = "filterbug"
    override fun getISize(): SkISize = SkISize.Make(150, 150)

    private lateinit var top: SkImage
    private lateinit var bot: SkImage

    override fun onOnceBeforeDraw() {
        // Top : 5 black rows at the top, 22 white rows below.
        top = makeImage(0, 5)
        // Bottom : 22 white rows at the top (firstBlackRow=22), 5 black rows below.
        bot = makeImage(22, 27)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sampling = SkSamplingOptions(SkCubicResampler.Mitchell)
        val doAA = true

        // Top rect: (50, 0, 50, 50) — scaled 2× and translated (50, 0).
        run {
            val r1 = SkRect.MakeXYWH(50f, 0f, 50f, 50f)
            val p1 = SkPaint().apply { isAntiAlias = doAA }
            val localMat = SkMatrix(sx = 2f, sy = 2f, tx = 50f, ty = 0f)
            p1.shader = top.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, sampling, localMat)
            c.drawRect(r1, p1)
        }

        // Middle rect: (50, 50, 50, 36) — solid white.
        run {
            val r2 = SkRect.MakeXYWH(50f, 50f, 50f, 36f)
            val p2 = SkPaint().apply {
                color = SK_ColorWHITE
                isAntiAlias = doAA
            }
            c.drawRect(r2, p2)
        }

        // Bottom rect: (50, 86, 50, 50) — scaled 2× and translated (50, 86).
        run {
            val r3 = SkRect.MakeXYWH(50f, 86f, 50f, 50f)
            val p3 = SkPaint().apply { isAntiAlias = doAA }
            val localMat = SkMatrix(sx = 2f, sy = 2f, tx = 50f, ty = 86f)
            p3.shader = bot.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, sampling, localMat)
            c.drawRect(r3, p3)
        }
    }

    /**
     * `make_image(firstBlackRow, lastBlackRow)` — 25 × 27 N32 bitmap,
     * pre-filled with white, then `[firstBlackRow, lastBlackRow)` rows
     * stamped to opaque black. Marked opaque + immutable so the image
     * snapshot is bit-iso to upstream.
     */
    private fun makeImage(firstBlackRow: Int, lastBlackRow: Int): SkImage {
        val bm = SkBitmap.Make(K_WIDTH, K_HEIGHT)
        bm.eraseColor(SK_ColorWHITE)
        val black = SkColorSetARGB(0xFF, 0x0, 0x0, 0x0)
        for (y in firstBlackRow until lastBlackRow) {
            for (x in 0 until K_WIDTH) {
                bm.setPixel(x, y, black)
            }
        }
        return bm.asImage()
    }

    private companion object {
        const val K_WIDTH = 25
        const val K_HEIGHT = 27
    }
}
