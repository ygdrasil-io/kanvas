package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/aarectmodes.cpp` (`aarectmodes` GM, 640 × 480).
 *
 * The 12 Porter-Duff coefficient blend modes drawn as a 2 × 2 grid of
 * 6 × 4 cells. Each cell:
 *  1. Fills the cell rect with a 12 × 12 light-grey/white checkerboard
 *     (via a 2 × 2 source bitmap put into a `kRepeat` × `kRepeat` shader,
 *     scaled 6×).
 *  2. `saveLayer(bounds, null)` opens a transparent offscreen layer.
 *  3. Draws an inset blue oval with alpha `a0`.
 *  4. Draws an inset red rect with alpha `a1` and blend mode `mode` —
 *     this is the actual mode under test, applied between the rect and
 *     the previously-drawn blue oval inside the layer.
 *  5. `restore()` flattens the layer (kSrcOver) onto the checkered BG.
 *
 * 4 alpha configurations (`{a0, a1} ∈ {(0xFF, 0xFF), (0x88, 0xFF),
 * (0xFF, 0x88), (0x88, 0x88)}`) cover both opaque-on-opaque and
 * fractional-alpha cases for each mode. The 12 modes split into two
 * vertical columns of 6 (the C++ `if (6 == i)` reset).
 *
 * Reference image: `aarectmodes.png`, 640 × 480, 16-bit RGBA.
 */
public class AaRectModesGM : GM() {

    override fun getName(): String = "aarectmodes"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // 2×2 source bitmap for the checkerboard BG: white + light grey
        // tiles, scaled 6× via a `kRepeat` × `kRepeat` image shader.
        val bgBitmap = SkBitmap(2, 2).apply {
            // (0,0) and (1,1) = white; (1,0) and (0,1) = light grey.
            setPixel(0, 0, 0xFFFFFFFF.toInt())
            setPixel(1, 1, 0xFFFFFFFF.toInt())
            setPixel(1, 0, SkColorSetARGB(0xFF, 0xCE, 0xCF, 0xCE))
            setPixel(0, 1, SkColorSetARGB(0xFF, 0xCE, 0xCF, 0xCE))
        }
        val bgPaint = SkPaint().apply {
            shader = bgBitmap.makeShader(
                SkTileMode.kRepeat,
                SkTileMode.kRepeat,
                SkSamplingOptions.Default,
                SkMatrix(sx = 6f, ky = 0f, kx = 0f, sy = 6f, tx = 0f, ty = 0f),
            )
        }

        val bounds = SkRect.MakeWH(W, H)
        val alphaValue = intArrayOf(0xFF, 0x88, 0x88)

        c.translate(4f, 4f)

        for (alpha in 0 until 4) {
            c.save()
            c.save()
            for (i in modes.indices) {
                if (i == 6) {
                    c.restore()
                    c.translate(W * 5, 0f)
                    c.save()
                }
                c.drawRect(bounds, bgPaint)
                c.saveLayer(bounds, null)
                val a0 = alphaValue[alpha and 1]
                val a1 = alphaValue[alpha and 2]
                val dy = drawCell(c, modes[i], a0, a1)
                c.restore()
                c.translate(0f, dy * 5f / 4f)
            }
            c.restore()
            c.restore()
            c.translate(W * 5f / 4f, 0f)
        }
    }

    /**
     * Draws one mode-under-test cell into the current layer:
     * blue oval (alpha `a0`) covered by a red rect (alpha `a1`) with
     * `paint.blendMode = mode`. Returns the cell height for the caller's
     * vertical advance.
     */
    private fun drawCell(canvas: SkCanvas, mode: SkBlendMode, a0: Int, a1: Int): Float {
        val paint = SkPaint().apply { isAntiAlias = true }

        val r = SkRect.MakeWH(W, H)
        r.inset(W / 10f, H / 10f)

        paint.color = SkColorSetARGB(a0, 0, 0, 0xFF)  // blue with alpha a0
        canvas.drawOval(r, paint)

        paint.color = SkColorSetARGB(a1, 0xFF, 0, 0)  // red with alpha a1
        paint.blendMode = mode

        val offset = 1f / 3f
        val rect = SkRect.MakeXYWH(
            W / 4f + offset,
            H / 4f + offset,
            W / 2f,
            H / 2f,
        )
        canvas.drawRect(rect, paint)

        return H
    }

    private companion object {
        const val GW: Int = 64
        const val GH: Int = 64
        const val W: Float = GW.toFloat()
        const val H: Float = GH.toFloat()

        val modes: Array<SkBlendMode> = arrayOf(
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
        )
    }
}

