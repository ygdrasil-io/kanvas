package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/lcdblendmodes.cpp::LcdBlendGM` (720 × 750).
 *
 * Four-column matrix of blend-mode samples. Each column starts with a
 * solid background rect (black / white / green / cyan) and overlays
 * 29 rows of `BlendMode_Name(mode)` text (25 px) at the matching
 * blend mode against a foreground colour (white / black / magenta /
 * magenta). The fourth column uses a vertical linear-gradient shader
 * on the text instead of a solid colour.
 *
 * Upstream renders the four columns into an offscreen `SkSurface`
 * configured with `kRGB_H_SkPixelGeometry` (LCD-RGB stripe geometry)
 * and composites the surface onto the canvas with [SkBlendMode.kSrcOver].
 *
 * **kanvas-skia adaptations** :
 *  - `SkSurfaceProps` is not yet on the kanvas-skia surface — the
 *    offscreen [SkSurface.MakeRaster] is allocated with the default
 *    pixel geometry. The fonts already silently downgrade
 *    `kSubpixelAntiAlias` to plain antialiased (cf. `SkFont.kt`), so
 *    the LCD-stripe expectation that lcdblendmodes was designed to
 *    showcase isn't reproducible at this slice — the structural
 *    content (column backgrounds, mode names, blend mode pairings)
 *    is preserved.
 *  - `ToolUtils::create_checkerboard_shader` doesn't yet exist on the
 *    kanvas-skia `ToolUtils` ; the background checker is painted by
 *    inlining the same `2*size × 2*size` repeat-bitmap-shader recipe
 *    as `ToolUtils.draw_checkerboard`, which is what upstream
 *    `create_checkerboard_shader` builds internally.
 *
 * C++ source : see `gm/lcdblendmodes.cpp`. Reference: `lcdblendmodes.png`.
 */
public class LcdBlendGM : GM() {

    private val fTextHeight: SkScalar = kPointSize.toFloat()
    private var fCheckerboardCheckerSize: Int = 4

    override fun getName(): String = "lcdblendmodes"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Background checker — black + white, 4px cells, full canvas.
        val checker = SkPaint().apply {
            isAntiAlias = false
            style = SkPaint.Style.kFill_Style
            shader = checkerboardShader(SK_ColorBLACK, SK_ColorWHITE, fCheckerboardCheckerSize)
        }
        val r = SkRect.MakeWH(kWidth.toFloat(), kHeight.toFloat())
        c.drawRect(r, checker)

        // Render the four columns into an offscreen surface, then
        // composite with srcOver — matches upstream's recipe.
        val info = SkImageInfo.MakeN32Premul(kWidth, kHeight)
        val surface = SkSurface.MakeRaster(info)

        val surfCanvas = surface.canvas
        drawColumn(surfCanvas, SK_ColorBLACK, SK_ColorWHITE, useGrad = false)
        surfCanvas.translate(kColWidth.toFloat(), 0f)
        drawColumn(surfCanvas, SK_ColorWHITE, SK_ColorBLACK, useGrad = false)
        surfCanvas.translate(kColWidth.toFloat(), 0f)
        drawColumn(surfCanvas, SK_ColorGREEN, SK_ColorMAGENTA, useGrad = false)
        surfCanvas.translate(kColWidth.toFloat(), 0f)
        drawColumn(surfCanvas, SK_ColorCYAN, SK_ColorMAGENTA, useGrad = true)

        val surfPaint = SkPaint().apply { blendMode = SkBlendMode.kSrcOver }
        surface.draw(c, 0f, 0f, surfPaint)
    }

    private fun drawColumn(canvas: SkCanvas, backgroundColor: SkColor, textColor: SkColor, useGrad: Boolean) {
        val gModes = arrayOf(
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
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
            SkBlendMode.kScreen,
            SkBlendMode.kOverlay,
            SkBlendMode.kDarken,
            SkBlendMode.kLighten,
            SkBlendMode.kColorDodge,
            SkBlendMode.kColorBurn,
            SkBlendMode.kHardLight,
            SkBlendMode.kSoftLight,
            SkBlendMode.kDifference,
            SkBlendMode.kExclusion,
            SkBlendMode.kMultiply,
            SkBlendMode.kHue,
            SkBlendMode.kSaturation,
            SkBlendMode.kColor,
            SkBlendMode.kLuminosity,
        )

        // Background rect for this column.
        val backgroundPaint = SkPaint().apply { color = backgroundColor }
        canvas.drawRect(SkRect.MakeIWH(kColWidth, kHeight), backgroundPaint)

        var y = fTextHeight
        for (mode in gModes) {
            val paint = SkPaint().apply {
                color = textColor
                blendMode = mode
            }
            val font = SkFont(ToolUtils.DefaultPortableTypeface(), fTextHeight).apply {
                isSubpixel = true
                edging = SkFont.Edging.kSubpixelAntiAlias // downgraded silently to AA
            }
            if (useGrad) {
                val rr = SkRect.MakeXYWH(0f, y - fTextHeight, kColWidth.toFloat(), fTextHeight)
                paint.shader = makeShader(rr)
            }
            val s = SkBlendMode_Name(mode)
            canvas.drawString(s, 0f, y, font, paint)
            y += fTextHeight
        }
    }

    // Mirrors upstream `make_shader(bounds)` — a linear gradient from
    // top-left to bottom-right, red → green, kRepeat.
    private fun makeShader(bounds: SkRect): org.skia.foundation.SkShader {
        val pts0 = SkPoint(bounds.left, bounds.top)
        val pts1 = SkPoint(bounds.right, bounds.bottom)
        val colors = intArrayOf(
            SkColor4f.kRed.toSkColor(),
            SkColor4f.kGreen.toSkColor(),
        )
        return SkLinearGradient.Make(pts0, pts1, colors, null, SkTileMode.kRepeat)
    }

    private fun checkerboardShader(c1: SkColor, c2: SkColor, size: Int): org.skia.foundation.SkShader {
        val tile = org.skia.foundation.SkBitmap(2 * size, 2 * size)
        tile.eraseColor(c1)
        for (y in 0 until size) {
            for (x in 0 until size) {
                tile.setPixel(x, y, c2)
                tile.setPixel(x + size, y + size, c2)
            }
        }
        return tile.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
    }

    private companion object {
        const val kPointSize: Int = 25
        const val kColWidth: Int = 180
        const val kNumCols: Int = 4
        const val kWidth: Int = kColWidth * kNumCols
        const val kHeight: Int = 750
    }
}
