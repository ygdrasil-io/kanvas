package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.tools.ToolUtils
import kotlin.math.min

/**
 * Port of Skia's `gm/shadertext3.cpp::ShaderText3GM` (820 × 930).
 *
 * Exercises bitmap-shader-based text fills with the four combinations of
 * `(SkTileMode.kRepeat, kMirror)` for X and Y. A small `kPointSize/4 ×
 * kPointSize/4` (75×75) gradient bitmap (`makebm`) is constructed at
 * `onOnceBeforeDraw`. The canvas is set up with a scale of 2× and the
 * baseline of the first run translated to `0.75 * kPointSize`. For each
 * `(tm0, tm1)` pair we build a local matrix `T(5,5) · R(20°) ·
 * S(1.15,.85)` and draw the letter `"B"` filled by the bitmap shader and
 * outlined by a zero-width stroke. Every two glyphs the canvas resets to
 * `(0, 0)` and moves down a line.
 *
 * **kanvas-skia adaptation** :
 *  - `SkShaders::LinearGradient` is mirrored by [SkLinearGradient.Make]
 *    — we issue two consecutive `drawPaint` passes through it
 *    (matching upstream's diagonal + vertical gradient overlay) into a
 *    fresh raster surface to seed the bitmap.
 *  - The translucent `SkColor4f` palette (`0x80F00080`, etc.) is fed
 *    directly as ARGB ints — Skia's `SkColorConverter` is just a
 *    constexpr-friendly wrapper around the same byte layout.
 *  - `bm.makeShader(tmX, tmY, sampling, localM)` maps 1-for-1 to
 *    [SkBitmap.makeShader].
 *  - `SkFont(DefaultPortableTypeface(), kPointSize)` is the same as
 *    upstream; `setEdging(kAlias)` is omitted (matches default in
 *    upstream too — it doesn't set edging here).
 *
 * C++ source : see `gm/shadertext3.cpp`. Reference: `shadertext3.png`.
 */
public class ShaderText3GM : GM() {

    init {
        setBGColor(0xFFDDDDDD.toInt())
    }

    private lateinit var fBmp: SkBitmap

    override fun getName(): String = "shadertext3"
    override fun getISize(): SkISize = SkISize.Make(820, 930)

    override fun onOnceBeforeDraw() {
        fBmp = makebm(kPointSize / 4, kPointSize / 4)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val bmpPaint = SkPaint().apply {
            isAntiAlias = true
            alphaf = 0.5f
        }
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        c.drawImage(fBmp.asImage(), 5f, 5f, sampling, bmpPaint)

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), kPointSize.toFloat())
        val outlinePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        c.translate(15f, 15f)
        c.scale(2f, 2f)

        val tileModes = arrayOf(SkTileMode.kRepeat, SkTileMode.kMirror)

        // position the baseline of the first run
        c.translate(0f, 0.75f * kPointSize)

        c.save()
        var i = 0
        for (tm0 in tileModes.indices) {
            for (tm1 in tileModes.indices) {
                // localM = T(5,5) · R(20°) · S(1.15, .85), built post-order to match Skia's
                // setTranslate → postRotate → postScale chain.
                val localM = SkMatrix.MakeTrans(5f, 5f)
                    .postRotate(20f)
                    .postScale(1.15f, 0.85f)

                val fillPaint = SkPaint().apply {
                    isAntiAlias = true
                    shader = fBmp.makeShader(tileModes[tm0], tileModes[tm1], sampling, localM)
                }

                val text = "B"
                c.drawString(text, 0f, 0f, font, fillPaint)
                c.drawString(text, 0f, 0f, font, outlinePaint)
                val w = font.measureText(text)
                c.translate(w + 10f, 0f)
                i++
                if (i % 2 == 0) {
                    c.restore()
                    c.translate(0f, 0.75f * kPointSize)
                    c.save()
                }
            }
        }
        c.restore()
    }

    // Mirrors `makebm(SkBitmap*, int w, int h)` — two diagonal/vertical
    // linear gradient passes overlaid on a transparent N32 raster.
    private fun makebm(w: Int, h: Int): SkBitmap {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        val s = min(w, h).toFloat()

        val kPos = floatArrayOf(0f, 0.5f, 1f)

        // pass 1 — diagonal (0,0) → (s, s) with colours
        // {0x80F00080, 0xF0F08000, 0x800080F0}
        val pts0a = SkPoint.Make(0f, 0f)
        val pts0b = SkPoint.Make(s, s)
        val colors0 = intArrayOf(
            0x80F00080.toInt(),
            0xF0F08000.toInt(),
            0x800080F0.toInt(),
        )
        val paint = SkPaint().apply {
            shader = SkLinearGradient.Make(pts0a, pts0b, colors0, kPos, SkTileMode.kClamp)
        }
        canvas.drawPaint(paint)

        // pass 2 — vertical (s/2, 0) → (s/2, s) with colours
        // {0xF08000F0, 0x8080F000, 0xF000F080}
        val pts1a = SkPoint.Make(s / 2f, 0f)
        val pts1b = SkPoint.Make(s / 2f, s)
        val colors1 = intArrayOf(
            0xF08000F0.toInt(),
            0x8080F000.toInt(),
            0xF000F080.toInt(),
        )
        paint.shader = SkLinearGradient.Make(pts1a, pts1b, colors1, kPos, SkTileMode.kClamp)
        canvas.drawPaint(paint)

        // Snapshot back into an SkBitmap so we can call `bm.makeShader`.
        val image = surface.makeImageSnapshot()
        val out = SkBitmap(w, h)
        out.eraseColor(SK_ColorTRANSPARENT)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out.setPixel(x, y, image.peekPixel(x, y))
            }
        }
        return out
    }

    private companion object {
        const val kPointSize: Int = 300
    }
}
