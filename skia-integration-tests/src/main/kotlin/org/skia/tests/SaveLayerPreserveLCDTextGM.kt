package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/lcdtext.cpp::SaveLayerPreserveLCDTextGM`
 * (`savelayerpreservelcdtext`, 620 × 300).
 *
 * Two rows of 36-px subpixel-AA text, each drawn inside its own
 * [SkCanvas.saveLayer] :
 *  - Top row : layer flagged with `kPreserveLCDText_SaveLayerFlag = 0x2`
 *    — upstream keeps LCD text intact through the layer round-trip.
 *  - Bottom row : default flags — upstream falls back to grayscale AA.
 *
 * Our raster path has no LCD-text support (glyph fills are 8-bit
 * coverage AA per `SkCanvas.drawString` Javadoc), so both rows render
 * with the same grayscale glyphs. The diff vs. the upstream PNG is
 * therefore concentrated on the LCD-fringed top row.
 */
public class SaveLayerPreserveLCDTextGM : GM() {

    override fun getName(): String = "savelayerpreservelcdtext"
    override fun getISize(): SkISize = SkISize.Make(620, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawText(c, "SaveLayer PreserveLCDText", 50, K_PRESERVE_LCDTEXT)
        drawText(c, "SaveLayer Default (LCDText not preserved)", 150, 0)
    }

    private fun drawText(canvas: SkCanvas, string: String, y: Int, saveLayerFlags: Int) {
        val rec = SaveLayerRec(bounds = null, paint = null, backdrop = null, flags = saveLayerFlags)
        canvas.saveLayer(rec)
        val paint = SkPaint().apply { color = SK_ColorWHITE }
        canvas.drawRect(SkRect.MakeXYWH(0f, y - 10f, 640f, K_TEXT_HEIGHT + 20f), paint)
        paint.color = SK_ColorBLACK
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), K_TEXT_HEIGHT)
        font.edging = SkFont.Edging.kSubpixelAntiAlias
        canvas.drawString(string, 10f, y.toFloat(), font, paint)
        canvas.restore()
    }

    private companion object {
        const val K_TEXT_HEIGHT: Float = 36f
        // Mirrors SkCanvas::kPreserveLCDText_SaveLayerFlag = 1 << 1.
        const val K_PRESERVE_LCDTEXT: Int = 0x2
    }
}
