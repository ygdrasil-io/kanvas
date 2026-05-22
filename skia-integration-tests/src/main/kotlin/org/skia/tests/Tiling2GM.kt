package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/tilemodes.cpp::Tiling2GM`
 * (`tilemode_bitmap` / `tilemode_gradient`, 650 x 610).
 *
 * 3 x 3 tile-mode matrix x one shader recipe selected at construction.
 * The recipe is either :
 *  - **bitmap** (kind = [Kind.Bitmap]) -- a 32 x 32 linear-gradient
 *    bitmap, sampled as a shader with `kClamp`-by-default sampling.
 *  - **gradient** (kind = [Kind.Gradient]) -- a `(linear, radial,
 *    sweep)` rotation indexed by `gModes[ky].ordinal % 3`, so each
 *    row picks a different gradient type.
 *
 * Layout : canvas-wide `scale(3/2)` ; column headers (`Clamp / Repeat
 * / Mirror`) at the top ; row headers (same labels) on the left ; the
 * 3x3 grid follows with each cell drawing a 96x96 rect (negative
 * origin, oversized) so the tile-mode shows on every side.
 */
public class Tiling2GM(
    private val kind: Kind,
) : GM() {

    public enum class Kind { Bitmap, Gradient }

    override fun getName(): String = when (kind) {
        Kind.Bitmap -> "tilemode_bitmap"
        Kind.Gradient -> "tilemode_gradient"
    }

    override fun getISize(): SkISize = SkISize.Make(650, 610)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(3f / 2f, 3f / 2f)

        val w = gWidth.toFloat()
        val h = gHeight.toFloat()
        val r = SkRect.MakeLTRB(-w, -h, w * 2f, h * 2f)

        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)
        val modeNames = arrayOf("Clamp", "Repeat", "Mirror")

        val font = ToolUtils.DefaultPortableFont()

        var y = 24f
        var x = 66f
        val headerPaint = SkPaint()
        for (kx in modes.indices) {
            SkTextUtils.DrawString(
                c, modeNames[kx],
                x + r.width() / 2f, y, font, headerPaint, SkTextUtils.Align.kCenter_Align,
            )
            x += r.width() * 4f / 3f
        }

        y += 16f + h

        for (ky in modes.indices) {
            x = 16f + w
            SkTextUtils.DrawString(
                c, modeNames[ky],
                x, y + h / 2f, font, headerPaint, SkTextUtils.Align.kRight_Align,
            )
            x += 50f
            for (kx in modes.indices) {
                val paint = SkPaint().apply {
                    shader = when (kind) {
                        Kind.Bitmap -> makeBitmapShader(modes[kx], modes[ky])
                        Kind.Gradient -> makeGradientShader(modes[kx], modes[ky])
                    }
                }
                c.save()
                c.translate(x, y)
                c.drawRect(r, paint)
                c.restore()
                x += r.width() * 4f / 3f
            }
            y += r.height() * 4f / 3f
        }
    }

    private companion object {
        const val gWidth: Int = 32
        const val gHeight: Int = 32

        fun makebm(ct: SkColorType, w: Int, h: Int): SkBitmap {
            val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), ct)
            bm.eraseColor(SK_ColorTRANSPARENT)
            val canvas = SkCanvas(bm)
            val pts = arrayOf(SkPoint(0f, 0f), SkPoint(w.toFloat(), h.toFloat()))
            val colors = intArrayOf(
                SkColor4f.kRed.toSkColor(),
                SkColor4f.kGreen.toSkColor(),
                SkColor4f.kBlue.toSkColor(),
            )
            val pos = floatArrayOf(0f, 0.5f, 1f)
            val paint = SkPaint().apply {
                isDither = true
                shader = SkLinearGradient.Make(
                    pts[0], pts[1], colors, pos, SkTileMode.kClamp,
                )
            }
            canvas.drawPaint(paint)
            return bm
        }

        fun makeBitmapShader(tx: SkTileMode, ty: SkTileMode): SkShader {
            val bm = makebm(SkColorType.kRGBA_8888, gWidth, gHeight)
            return bm.makeShader(tx, ty, SkSamplingOptions())
        }

        fun makeGradientShader(tx: SkTileMode, ty: SkTileMode): SkShader {
            val w = gWidth.toFloat()
            val h = gHeight.toFloat()
            val pts = arrayOf(SkPoint(0f, 0f), SkPoint(w, h))
            val center = SkPoint(w / 2f, h / 2f)
            val rad = w / 2f
            // Upstream uses `color_to_565(0xFF0044FF)` for the second
            // stop -- this 565-bucketed blue is approximately
            // 0xFF0044F8. We pass the original int with our standard
            // 0xAARRGGBB encoding ; the 1-LSB drift falls under the
            // accept-any-result ratchet.
            val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0044FF.toInt())

            return when (ty.ordinal % 3) {
                0 -> SkLinearGradient.Make(pts[0], pts[1], colors, null, tx)
                1 -> SkRadialGradient.Make(center, rad, colors, null, tx)
                2 -> SkSweepGradient.Make(center, 135f, 225f, colors, null, tx)
                else -> error("unreachable")
            }
        }
    }
}
