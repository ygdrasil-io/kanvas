package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/gammatext.cpp::GammaShaderTextGM`
 * (`DEF_GM(return new GammaShaderTextGM;)`, name `gammagradienttext`).
 *
 * 3-row 300×300 GM exercising text + shader/colour gamma interaction.
 * Each row repeats the same 3-string `draw_pair` block (colour fill /
 * colour shader / horizontal-fade gradient shader) for one of the
 * three base colours : black, red, blue.
 *
 * **Note** : kanvas-skia uses `kSubpixelAntiAlias` font edging
 * silently downgraded to `kAntiAlias` (see [SkFont.Edging] doc) —
 * within the textual tolerance.
 */
public class GammaShaderTextGM : GM() {

    override fun getName(): String = "gammagradienttext"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    // Three "row" base colours, mirroring upstream's `fColors[3]`.
    private val baseColors: IntArray = intArrayOf(SK_ColorBLACK, SK_ColorRED, SK_ColorBLUE)

    // Lazily-built per-base-colour horizontal fade gradients (built in
    // onOnceBeforeDraw to mirror upstream's `fShaders` cache).
    private var fShaders: Array<SkShader?> = arrayOfNulls(3)

    override fun onOnceBeforeDraw() {
        for (i in baseColors.indices) {
            fShaders[i] = makeGradient(baseColors[i])
        }
    }

    private fun makeGradient(color: Int): SkShader {
        val transparent = color and 0x00FFFFFF
        return SkLinearGradient.Make(
            p0 = SkPoint(0f, 0f),
            p1 = SkPoint(240f, 0f),
            colors = intArrayOf(color, transparent),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
    }

    private fun drawPair(canvas: SkCanvas, font: SkFont, color: Int, shader: SkShader?) {
        val text = "Now is the time for all good"
        val paint = SkPaint().apply { this.color = color }
        // 1) solid colour
        canvas.drawString(text, 10f, 20f, font, paint)
        // 2) `SkShaders::Color(paint.getColor())` — modelled as a no-op
        //    here ; the paint's colour already drives the fill identically
        //    on raster. (kanvas-skia has no `SkShaders.Color` factory
        //    exposed yet ; the visual difference is below text tolerance.)
        canvas.drawString(text, 10f, 40f, font, paint)
        // 3) horizontal fade shader.
        paint.shader = shader
        canvas.drawString(text, 10f, 60f, font, paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        // Upstream uses `ToolUtils::CreatePortableTypeface("serif",
        // SkFontStyle::Italic())` ; kanvas-skia's default portable
        // typeface stand-in is used here.
        val font = ToolUtils.DefaultPortableFont().apply {
            size = 18f
            edging = SkFont.Edging.kSubpixelAntiAlias
        }

        for (i in baseColors.indices) {
            drawPair(c, font, baseColors[i], fShaders[i])
            c.translate(0f, 80f)
        }
        // paint variable above is unused but kept for API parity / future
        // when SkShaders.Color lands. Suppress "unused" warning.
        @Suppress("UNUSED_VARIABLE")
        val unused = paint
    }
}
