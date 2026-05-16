package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/tilemodes_scaled.cpp::ScaledTilingGM`
 * (`scaled_tilemodes` / `scaled_tilemodes_npot`, 880 × 880).
 *
 * 3 × 3 tile-mode matrix × 2 colour types × 5 sampling options.
 * Each cell is a `2 × 2 × size` rect (tiny `size = 4` for POT) filled
 * by an image shader sampling a `size × size` linear-gradient bitmap
 * (red → green → blue) under a 8× zoom (`canvas.scale(scale, scale)`).
 *
 * The 5 sampling options exercise the kanvas-skia sampling matrix :
 *
 *  - `nearest` (point)
 *  - `linear` (bilinear)
 *  - `linear + mipmap.linear` (trilinear)
 *  - `Mitchell` cubic resampler
 *  - `Aniso(16)`
 *
 * Header row mirrors [TilemodesGM] : `[KX,KY]` per column. Per-row
 * tag : `"<colorType>, <samplingName>"`.
 *
 * Two upstream factories (POT / NPOT) — we focus on the POT variant
 * (`scaled_tilemodes.png`).
 */
public class ScaledTilemodesGM(
    private val powerOfTwoSize: Boolean = true,
) : GM() {

    override fun getName(): String = if (powerOfTwoSize) "scaled_tilemodes" else "scaled_tilemodes_npot"
    override fun getISize(): SkISize = SkISize.Make(880, 880)

    private val textures: Array<SkBitmap?> = arrayOfNulls(gColorTypes.size)

    override fun onOnceBeforeDraw() {
        val size = if (powerOfTwoSize) kPOTSize else kNPOTSize
        for (i in gColorTypes.indices) {
            textures[i] = makebm(gColorTypes[i], size, size)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val font = SkFont12()

        val scale = 32f / kPOTSize.toFloat()

        val size = if (powerOfTwoSize) kPOTSize else kNPOTSize
        val r = SkRect.MakeLTRB(0f, 0f, (size * 2).toFloat(), (size * 2).toFloat())

        val colorTypeNames = arrayOf("8888", "565")
        val filterNames = arrayOf("Nearest", "Linear", "Trilinear", "Mitchell", "Aniso")
        val modes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)
        val modeNames = arrayOf("C", "R", "M")

        var y = 24f
        var x = 10f / scale

        // Header row : tile-mode pair labels.
        for (kx in modes.indices) {
            for (ky in modes.indices) {
                val str = "[${modeNames[kx]},${modeNames[ky]}]"
                SkTextUtils.DrawString(
                    c, str, scale * (x + r.width() / 2f), y,
                    font, SkPaint(), SkTextUtils.Align.kCenter_Align,
                )
                x += r.width() * 4f / 3f
            }
        }
        y = 40f / scale

        for (i in gColorTypes.indices) {
            for (j in gSamplings.indices) {
                x = 10f / scale
                for (kx in modes.indices) {
                    for (ky in modes.indices) {
                        if (!powerOfTwoSize) {
                            textures[i] = makebm(gColorTypes[i], size, size)
                        }
                        val bm = textures[i]!!
                        val paint = SkPaint().apply {
                            shader = bm.makeShader(
                                tileX = modes[kx],
                                tileY = modes[ky],
                                sampling = gSamplings[j],
                            )
                            isDither = true
                        }
                        c.save()
                        c.scale(scale, scale)
                        c.translate(x, y)
                        c.drawRect(r, paint)
                        c.restore()
                        x += r.width() * 4f / 3f
                    }
                }
                val tag = "${colorTypeNames[i]}, ${filterNames[j]}"
                c.drawString(
                    tag,
                    scale * x,
                    scale * (y + r.height() * 2f / 3f),
                    font, SkPaint(),
                )
                y += r.height() * 4f / 3f
            }
        }
    }

    private companion object {
        const val kPOTSize: Int = 4
        const val kNPOTSize: Int = 3

        val gColorTypes: Array<SkColorType> = arrayOf(
            SkColorType.kRGBA_8888,
            SkColorType.kRGB_565,
        )

        val gSamplings: Array<SkSamplingOptions> = arrayOf(
            SkSamplingOptions(SkFilterMode.kNearest),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
            SkSamplingOptions(SkCubicResampler.Mitchell),
            SkSamplingOptions.Aniso(16),
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

        /** Upstream uses `SkFont(DefaultPortableTypeface(), 12)`. */
        fun SkFont12() = org.skia.foundation.SkFont(ToolUtils.DefaultPortableTypeface(), 12f)
    }
}
