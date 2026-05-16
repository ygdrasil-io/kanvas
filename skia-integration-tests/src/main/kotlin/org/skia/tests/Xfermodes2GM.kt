package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode.Companion.kSkBlendModeCount
import org.skia.foundation.SkBlendMode_Name
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/xfermodes2.cpp::Xfermodes2GM`
 * (`xfermodes2`, 455 × 475).
 *
 * 5-wide × 6-tall (29 cells, one per [SkBlendMode]) grid of
 * `kSize × kSize` cells (`kSize = 64`). Each cell composes three
 * shader layers :
 *
 *  1. A 2×2 checkerboard ARGB bg (`bg shader`).
 *  2. A horizontally-varying source-alpha "Dst" gradient
 *     (`fDst` — alpha sweeps `0..255` along Y).
 *  3. A vertically-varying source-alpha "Src" gradient (`fSrc`)
 *     composited under the cell's blend mode.
 *
 *  The mode-bearing draw happens inside a `saveLayer(rect, null)` to
 *  isolate the cell from its neighbours. A 0.5-px stroke outlines the
 *  cell with the bg shader rolled back ; the mode name is centred
 *  above each cell.
 *
 *  Has two upstream factories — colour (`fGrayscale = false`) and
 *  grayscale variant (`xfermodes2_gray`). This port covers the
 *  canonical colour variant.
 */
public class Xfermodes2GM(
    private val grayscale: Boolean = false,
) : GM() {

    override fun getName(): String = if (grayscale) "xfermodes2_gray" else "xfermodes2"
    override fun getISize(): SkISize = SkISize.Make(455, 475)

    private lateinit var bgShader: SkShader
    private lateinit var srcShader: SkShader
    private lateinit var dstShader: SkShader

    override fun onOnceBeforeDraw() {
        // Bg : 2 × 2 ARGB_8888 checker (deep / light grey).
        val bg = SkBitmap(2, 2)
        // Upstream layout: row-major {0xFF42 4142, 0xFFD6D3D6, 0xFFD6D3D6, 0xFF424142}
        // (alpha=FF; greys 0x42 4142 and 0xD6 D3D6 alternating).
        val dark = SkColorSetARGB(0xFF, 0x42, 0x41, 0x42)
        val light = SkColorSetARGB(0xFF, 0xD6, 0xD3, 0xD6)
        bg.setPixel(0, 0, dark)
        bg.setPixel(1, 0, light)
        bg.setPixel(0, 1, light)
        bg.setPixel(1, 1, dark)
        val lm = SkMatrix.MakeScale(16f, 16f)
        bgShader = bg.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default, lm)

        // Src : alpha gradient sweep along Y (each row uniform).
        // Premul-equivalent upstream is (c, c, 0, c/2) ↔ unpremul
        // (a=c, r=255, g=0, b=128). For grayscale variant : (a=c, r=g=b=255).
        srcShader = makeAlphaSweepBitmap(grayscale, vertical = true).makeShader(
            SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default,
        )
        // Dst : alpha gradient sweep along X (each column uniform).
        // Upstream pixel (c, 0, c, c/2) ↔ unpremul (a=c, r=0, g=255, b=128).
        dstShader = makeAlphaSweepBitmap(grayscale, vertical = false).makeShader(
            SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 20f)

        val w = kSize.toFloat()
        val h = kSize.toFloat()

        val font = ToolUtils.DefaultPortableFont()
        val kRowW = 6

        var x = 0f
        var y = 0f
        for (m in 0 until kSkBlendModeCount) {
            val mode = SkBlendMode.entries[m]
            c.save()
            c.translate(x, y)

            val p = SkPaint().apply {
                isAntiAlias = false
                style = SkPaint.Style.kFill_Style
                shader = bgShader
            }
            val r = SkRect.MakeWH(w, h)
            c.drawRect(r, p)

            c.saveLayer(r, null)

            p.shader = dstShader
            c.drawRect(r, p)
            p.shader = srcShader
            p.blendMode = mode
            c.drawRect(r, p)

            c.restore()

            // Stroke frame.
            val frame = SkRect.MakeLTRB(
                r.left - 0.5f, r.top - 0.5f,
                r.right + 0.5f, r.bottom + 0.5f,
            )
            val framePaint = SkPaint().apply {
                style = SkPaint.Style.kStroke_Style
                blendMode = SkBlendMode.kSrcOver
            }
            c.drawRect(frame, framePaint)

            c.restore()

            SkTextUtils.DrawString(
                c, SkBlendMode_Name(mode),
                x + w / 2f, y - font.size / 2f,
                font, SkPaint(),
                SkTextUtils.Align.kCenter_Align,
            )

            x += w + 10f
            if ((m % kRowW) == kRowW - 1) {
                x = 0f
                y += h + 30f
            }
        }
    }

    private companion object {
        const val kShift: Int = 2
        const val kSize: Int = 256 ushr kShift // 64

        /**
         * `kSize × kSize` ARGB bitmap with alpha sweeping `0..255` along the
         * selected axis. Each row (or column) is uniform unpremul colour
         * `(a=c, 255, 0, 128)` for non-gray, `(a=c, 255, 255, 255)` for gray.
         * Dst variant swaps R↔G : `(a=c, 0, 255, 128)`.
         *
         * @param vertical when true, alpha varies row-by-row (gradient along Y).
         */
        fun makeAlphaSweepBitmap(grayscale: Boolean, vertical: Boolean): SkBitmap {
            val bm = SkBitmap(kSize, kSize)
            if (vertical) {
                // Src: each row of equal alpha.
                for (yy in 0 until kSize) {
                    val a = yy shl kShift            // 0..255 stepped by 4
                    val color = if (grayscale) {
                        SkColorSetARGB(a, 255, 255, 255)
                    } else {
                        SkColorSetARGB(a, 255, 0, 128)   // unpremul of (c, c, 0, c/2)
                    }
                    for (xx in 0 until kSize) {
                        bm.setPixel(xx, yy, color)
                    }
                }
            } else {
                // Dst: each column of equal alpha.
                for (xx in 0 until kSize) {
                    val a = xx shl kShift
                    val color = if (grayscale) {
                        SkColorSetARGB(a, 255, 255, 255)
                    } else {
                        SkColorSetARGB(a, 0, 255, 128)   // unpremul of (c, 0, c, c/2)
                    }
                    for (yy in 0 until kSize) {
                        bm.setPixel(xx, yy, color)
                    }
                }
            }
            return bm
        }
    }
}
