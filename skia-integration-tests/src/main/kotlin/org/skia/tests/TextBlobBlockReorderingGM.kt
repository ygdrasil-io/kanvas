package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGRAY
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.floor

/**
 * Port of Skia's `gm/textblobblockreordering.cpp::TextBlobBlockReordering`
 * (275 × 200).
 *
 * Validates the GPU textblob cache's atlas-eviction handling : the
 * same `SkTextBlob` ("AB" at 56 pt, kAlias edging) is drawn 3× — the
 * middle draw under `kSrcIn` so its op doesn't get combined with the
 * surrounding `kSrcOver` draws. Upstream flushes them in the order
 * 1st / 3rd / 2nd, exposing reordering bugs when atlas slots are
 * recycled. Our raster pipeline doesn't have an atlas cache so the
 * reordering is a no-op ; the visual output should still match.
 *
 * Layout :
 *  - Top : black "AB" textblob.
 *  - Middle : red rectangle at the textblob's bbox, then "AB"
 *    drawn through `kSrcIn` so only the glyph alpha shows red.
 *  - Bottom : black "AB" again.
 */
public class TextBlobBlockReorderingGM : GM() {

    private var fBlob: SkTextBlob? = null

    override fun getName(): String = "textblobblockreordering"
    override fun getISize(): SkISize = SkISize.Make(275, 200)

    override fun onOnceBeforeDraw() {
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 56f).apply {
            edging = SkFont.Edging.kAlias
        }
        val text = "AB"

        val bounds = SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        val yOffset = bounds.height()

        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, text, font, 0f, yOffset - 30f)
        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = fBlob ?: return

        c.drawColor(SK_ColorGRAY)

        val paint = SkPaint()
        c.translate(10f, 40f)

        val bounds = blob.bounds()
        val yDelta = floor(bounds.height()).toInt() + 20
        val xDelta = floor(bounds.width()).toInt()

        c.drawTextBlob(blob, 0f, 0f, paint)

        c.translate(xDelta.toFloat(), yDelta.toFloat())

        // Red rect at textblob bbox + draw blob with kSrcIn so the
        // glyph alpha mask intersects the red rect.
        val redPaint = SkPaint().apply { color = SK_ColorRED }
        c.drawRect(bounds, redPaint)
        val srcInPaint = paint.copy().apply { blendMode = SkBlendMode.kSrcIn }
        c.drawTextBlob(blob, 0f, 0f, srcInPaint)

        c.translate(xDelta.toFloat(), yDelta.toFloat())
        c.drawTextBlob(blob, 0f, 0f, paint)
    }
}
