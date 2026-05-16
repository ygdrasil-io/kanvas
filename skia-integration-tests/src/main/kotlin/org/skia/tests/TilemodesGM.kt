package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.math.SkColor4f
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/tilemodes.cpp::TilingGM`
 * (`tilemodes` / `tilemodes_npot`, 880 × 560).
 *
 * 3 × 3 tile-mode matrix × 2 colour types × 2 filter modes : 4 banks
 * of 9 cells. Each cell is `2 × 2 × size` filled by an image shader
 * sampling a `size × size` linear-gradient bitmap (red → green → blue,
 * top-left → bottom-right) with the tile mode varying per cell and the
 * filter mode varying per bank.
 *
 * The header row prints `[KX,KY]` per column (C/R/M for Clamp/Repeat/
 * Mirror) ; each row prints `"<colorType>, <filter>"` after its 3-cell
 * group (e.g. `"8888, point"` / `"565, bilinear"`).
 *
 * `:kanvas-skia` honours both the `8888` and `565` storage paths
 * (Phase R1-C / R-suivi.24) ; the rendered image is the colour-type
 * x filter-mode cartesian product, matching upstream's pixel layout.
 *
 * The `TilingGM` class has two upstream factories
 * (`new TilingGM(true)` / `new TilingGM(false)`) that swap `kPOTSize`
 * (32) for `kNPOTSize` (21) and rename the GM accordingly. This port
 * focuses on the POT variant — the canonical `tilemodes.png` ref.
 */
public class TilemodesGM(
    private val powerOfTwoSize: Boolean = true,
) : GM() {

    override fun getName(): String = if (powerOfTwoSize) "tilemodes" else "tilemodes_npot"
    override fun getISize(): SkISize = SkISize.Make(880, 560)

    private val textures: Array<SkBitmap?> = arrayOfNulls(gColorTypes.size)

    override fun onOnceBeforeDraw() {
        val size = if (powerOfTwoSize) kPOTSize else kNPOTSize
        for (i in gColorTypes.indices) {
            textures[i] = makebm(gColorTypes[i], size, size)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val font = ToolUtils.DefaultPortableFont()

        val size = if (powerOfTwoSize) kPOTSize else kNPOTSize
        val r = SkRect.MakeLTRB(0f, 0f, (size * 2).toFloat(), (size * 2).toFloat())

        val configNames = arrayOf("8888", "565")

        val filters = arrayOf(SkFilterMode.kNearest, SkFilterMode.kLinear)
        val filterNames = arrayOf("point", "bilinear")

        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)
        val modeNames = arrayOf("C", "R", "M")

        var y = 24f
        var x = 10f

        val labelPaint = SkPaint()
        for (kx in modes.indices) {
            for (ky in modes.indices) {
                val str = "[${modeNames[kx]},${modeNames[ky]}]"
                SkTextUtils.DrawString(
                    c, str, x + r.width() / 2f, y,
                    font, labelPaint, SkTextUtils.Align.kCenter_Align,
                )
                x += r.width() * 4f / 3f
            }
        }
        y += 16f

        for (i in gColorTypes.indices) {
            for (j in filters.indices) {
                x = 10f
                for (kx in modes.indices) {
                    for (ky in modes.indices) {
                        // Upstream regenerates the bitmap for NPOT every cell
                        // (a driver-bug workaround on a long-dead GPU). We do
                        // the same to keep pixel-identity.
                        if (!powerOfTwoSize) {
                            textures[i] = makebm(gColorTypes[i], size, size)
                        }
                        val bm = textures[i]!!
                        val paint = SkPaint().apply {
                            shader = bm.makeShader(
                                tileX = modes[kx],
                                tileY = modes[ky],
                                sampling = SkSamplingOptions(filters[j]),
                            )
                            isDither = true
                        }
                        c.save()
                        c.translate(x, y)
                        c.drawRect(r, paint)
                        c.restore()
                        x += r.width() * 4f / 3f
                    }
                }
                val tag = "${configNames[i]}, ${filterNames[j]}"
                c.drawString(tag, x, y + r.height() * 2f / 3f, font, SkPaint())
                y += r.height() * 4f / 3f
            }
        }
    }

    private companion object {
        const val kPOTSize: Int = 32
        const val kNPOTSize: Int = 21

        val gColorTypes: Array<SkColorType> = arrayOf(
            SkColorType.kRGBA_8888,
            SkColorType.kRGB_565,
        )

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
    }
}
