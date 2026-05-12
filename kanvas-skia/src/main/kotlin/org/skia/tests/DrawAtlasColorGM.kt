package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/drawatlascolor.cpp::DrawAtlasColorsGM`
 * (DEF_GM, name `draw-atlas-colors`).
 *
 * Tests `drawAtlas` with per-quad colours, all 29 blend modes
 * (kClear → kLuminosity), and an atlas containing transparency
 * (one of the four quadrants is `SK_ColorTRANSPARENT`).
 *
 * Layout : 29 columns × (1 text label + 2 atlas-rows-of-4-quads),
 * each panel `kAtlasSize = 30` px with `kPad = 2` between, plus a
 * `kTextPad = 8` text gutter at the top.
 *
 * **Adaptation** — `:kanvas-skia`'s [SkCanvas.drawAtlas] currently
 * ignores the per-sprite `colors` array and the `blendMode` /
 * tinting it implies (Phase I5.3 status). All 29×2 panels will
 * therefore render the raw 30×30 atlas without colour modulation,
 * so similarity against the reference will be low. Shipped per the
 * "if similarity is honest but low (<40%) ship anyway and call it
 * out" instruction in the porting plan.
 */
public class DrawAtlasColorGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    override fun getName(): String = "draw-atlas-colors"

    override fun getISize(): SkISize = SkISize.Make(
        kNumXferModes * (kAtlasSize + kPad) + kPad,
        2 * kNumColors * (kAtlasSize + kPad) + kTextPad + kPad,
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val target = SkRect.MakeWH(kAtlasSize.toFloat(), kAtlasSize.toFloat())
        val atlas = makeAtlas(kAtlasSize)

        val gModes = arrayOf(
            SkBlendMode.kClear, SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver, SkBlendMode.kSrcIn, SkBlendMode.kDstIn, SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut, SkBlendMode.kSrcATop, SkBlendMode.kDstATop, SkBlendMode.kXor,
            SkBlendMode.kPlus, SkBlendMode.kModulate, SkBlendMode.kScreen, SkBlendMode.kOverlay,
            SkBlendMode.kDarken, SkBlendMode.kLighten, SkBlendMode.kColorDodge,
            SkBlendMode.kColorBurn, SkBlendMode.kHardLight, SkBlendMode.kSoftLight,
            SkBlendMode.kDifference, SkBlendMode.kExclusion, SkBlendMode.kMultiply,
            SkBlendMode.kHue, SkBlendMode.kSaturation, SkBlendMode.kColor, SkBlendMode.kLuminosity,
        )
        val gColors = intArrayOf(
            SK_ColorWHITE,
            SK_ColorRED,
            0x88888888.toInt(),
            0x88000088.toInt(),
        )

        val numModes = gModes.size
        val numColors = gColors.size
        val xforms = Array(numColors) {
            SkRSXform.Make(1f, 0f, kPad.toFloat(), it * (target.width() + kPad))
        }
        val rects = Array(numColors) { target }
        val quadColors = gColors.copyOf()

        val paint = SkPaint().apply { isAntiAlias = true }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), kTextPad.toFloat())
        for (i in 0 until numModes) {
            val label = SkBlendMode_Name(gModes[i])
            c.drawString(label, i * (target.width() + kPad) + kPad, kTextPad.toFloat(), font, paint)
        }

        for (i in 0 until numModes) {
            c.save()
            c.translate(i * (target.height() + kPad), (kTextPad + kPad).toFloat())
            // w/o a paint
            c.drawAtlas(
                image = atlas, xform = xforms, src = rects, colors = quadColors,
                blendMode = gModes[i], sampling = SkSamplingOptions.Default, cullRect = null, paint = null,
            )
            c.translate(0f, numColors * (target.height() + kPad))
            // w/ a paint
            c.drawAtlas(
                image = atlas, xform = xforms, src = rects, colors = quadColors,
                blendMode = gModes[i], sampling = SkSamplingOptions.Default, cullRect = null, paint = paint,
            )
            c.restore()
        }
    }

    /** White | Red ／ Green | Transparent atlas, split 2×2 into quarters. */
    private fun makeAtlas(atlasSize: Int): SkImage {
        val kBlockSize = atlasSize / 2
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(atlasSize, atlasSize))
        val canvas = surface.canvas
        val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }

        paint.color = SK_ColorWHITE
        canvas.drawRect(SkRect.MakeXYWH(0f, 0f, kBlockSize.toFloat(), kBlockSize.toFloat()), paint)
        paint.color = SK_ColorRED
        canvas.drawRect(SkRect.MakeXYWH(kBlockSize.toFloat(), 0f, kBlockSize.toFloat(), kBlockSize.toFloat()), paint)
        paint.color = SK_ColorGREEN
        canvas.drawRect(SkRect.MakeXYWH(0f, kBlockSize.toFloat(), kBlockSize.toFloat(), kBlockSize.toFloat()), paint)
        paint.color = SK_ColorTRANSPARENT
        canvas.drawRect(SkRect.MakeXYWH(kBlockSize.toFloat(), kBlockSize.toFloat(), kBlockSize.toFloat(), kBlockSize.toFloat()), paint)
        return surface.makeImageSnapshot()
    }

    private companion object {
        private const val kNumXferModes = 29
        private const val kNumColors = 4
        private const val kAtlasSize = 30
        private const val kPad = 2
        private const val kTextPad = 8
    }
}
