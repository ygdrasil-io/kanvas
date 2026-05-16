package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorSetRGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gammatext.cpp::gammatext_color_shader`
 * (`DEF_SIMPLE_GM_BG`, 300 × 275, BG = `SK_ColorGRAY`).
 *
 * Three columns of "ABCDEFG" rendered with progressively-brightening
 * monochrome grey colours :
 *  - left column : paint colour only ;
 *  - middle column : `SkShaders.Color(SkColor)` constant-colour shader ;
 *  - right column : `SkShaders.Color(SkColor4f, SkColorSpace.MakeSRGB())`
 *    F32-component constant-colour shader with explicit sRGB input.
 *
 * The three columns are designed to match pixel-exactly — any
 * divergence between the rasteriser's plain-colour path and the
 * shader path shows up as banding here.
 */
public class GammatextColorShaderGM : GM() {

    init { setBGColor(0xFF888888.toInt()) } // SK_ColorGRAY upstream → 0xFF888888.

    override fun getName(): String = "gammatext_color_shader"
    override fun getISize(): SkISize = SkISize.Make(300, 275)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kText = "ABCDEFG"
        val tf = ToolUtils.CreatePortableTypeface("serif", SkFontStyle())
        val font = SkFont(tf, 18f).apply { edging = SkFont.Edging.kSubpixelAntiAlias }

        c.translate(10f, 30f)
        var i = 0
        while (i < 256) {
            val color = SkColorSetRGB(i, i, i)
            val paint = SkPaint().apply { this.color = color }
            c.drawString(kText, 0f, 0f, font, paint)
            paint.shader = SkShaders.Color(color)
            c.drawString(kText, 100f, 0f, font, paint)
            paint.shader = SkShaders.Color(SkColor4f.FromColor(color), SkColorSpace.makeSRGB())
            c.drawString(kText, 200f, 0f, font, paint)
            c.translate(0f, 20f)
            i += 20
        }
    }
}
