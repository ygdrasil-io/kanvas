package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.floor

/**
 * Port of Skia's `gm/textblobcolortrans.cpp::TextBlobColorTrans`
 * (675 × 1600).
 *
 * Validates that text blobs can be translated and have their colors
 * regenerated correctly. Builds a 2-run blob (1 large 256pt
 * "AB" + 1 small 28pt pangram), then draws it repeatedly down the
 * canvas, cycling 4 colors per row. Upstream uses this to trigger
 * the GPU backend's A8 atlas-blob cache invalidation under colour
 * change ; our raster pipeline doesn't have that cache so the
 * reordering is a no-op, but the output should match.
 */
public class TextBlobColorTransGM : GM() {

    private var fBlob: SkTextBlob? = null

    override fun getName(): String = "textblobcolortrans"
    override fun getISize(): SkISize = SkISize.Make(675, 1600)

    override fun onOnceBeforeDraw() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 256f).apply {
            edging = SkFont.Edging.kAlias
        }

        var text = "AB"
        var bounds = SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        val yOffset = bounds.height()
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset - 30f)

        // A8
        font.size = 28f
        text = "The quick brown fox jumps over the lazy dog."
        bounds = SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset - 8f)

        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = fBlob ?: return
        c.drawColor(SK_ColorGRAY)

        val paint = SkPaint()
        c.translate(10f, 40f)

        val bounds = blob.bounds()
        val colors = intArrayOf(SK_ColorCYAN, SK_ColorLTGRAY, SK_ColorYELLOW, SK_ColorWHITE)
        var colorIndex = 0
        var y = 0
        while (y + floor(bounds.height()).toInt() < kHeight) {
            paint.color = colors[colorIndex++ % colors.size]
            c.save()
            c.translate(0f, y.toFloat())
            c.drawTextBlob(blob, 0f, 0f, paint)
            c.restore()
            y += floor(bounds.height()).toInt()
        }
    }

    private companion object {
        const val kWidth: Int = 675
        const val kHeight: Int = 1600
    }
}
