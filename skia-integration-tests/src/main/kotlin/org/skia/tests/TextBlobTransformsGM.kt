package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGRAY
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.ceil

/**
 * Port of Skia's `gm/textblobtransforms.cpp::TextBlobTransforms`
 * (1000 × 1200).
 *
 * Validates that text blobs survive translate / rotate / scale CTM
 * concats. A 3-glyph blob (`A`-162pt + `B`-72pt + `C`-32pt — sized
 * to stress the GPU distance-field thresholds upstream) is rendered
 * a few times under translate-only transforms, four times under
 * rotates, and a long tail of 8 mixed rotate+scale+translate
 * transforms. The reference doesn't really care about subpixel
 * exactness — it's a "does each transformed copy land where it
 * should" smoke test.
 */
public class TextBlobTransformsGM : GM() {

    private var fBlob: SkTextBlob? = null

    override fun getName(): String = "textblobtransforms"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onOnceBeforeDraw() {
        val builder = SkTextBlobBuilder()

        // Make textblob. To stress distance fields, we choose sizes appropriately.
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 162f).apply {
            edging = SkFont.Edging.kAlias
        }
        var text = "A"

        val bounds = SkRect.MakeEmpty()
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        ToolUtils.addToTextBlob(builder, text, font, 0f, 0f)

        // Medium "B".
        val xOffset = bounds.width() + 5f
        font.size = 72f
        text = "B"
        ToolUtils.addToTextBlob(builder, text, font, xOffset, 0f)

        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        val yOffset = bounds.height()

        // Small "C".
        font.size = 32f
        text = "C"
        ToolUtils.addToTextBlob(builder, text, font, xOffset, -yOffset - 10f)

        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = fBlob ?: return

        c.drawColor(SK_ColorGRAY)

        val paint = SkPaint()

        val bounds = blob.bounds()
        c.translate(20f, 20f)

        val xOffset = ceil(bounds.width())
        val yOffset = ceil(bounds.height())

        // First : translates.
        c.translate(xOffset, 2f * yOffset)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(-xOffset, 0f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(2f * xOffset, 0f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(-xOffset, -yOffset)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(0f, 2f * yOffset)
        c.drawTextBlob(blob, 0f, 0f, paint)

        // Now : rotates.
        c.translate(4f * xOffset, -yOffset)
        c.rotate(180f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.rotate(-180f)
        c.translate(0f, -yOffset)
        c.rotate(-180f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.rotate(270f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.rotate(-90f)
        c.translate(-xOffset, yOffset)
        c.rotate(-90f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.rotate(90f)

        // And scales.
        c.translate(-3f * xOffset, 3f * yOffset)
        c.scale(1.5f, 1.5f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(xOffset, 0f)
        c.scale(.25f, .25f)
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.translate(xOffset, 0f)
        c.scale(3f, 2f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        // Finally : mix of rotate / scale / translate.
        c.translate(xOffset, 0f)
        c.rotate(23f)
        c.scale(.33f, .5f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(-46f)
        c.translate(xOffset, 0f)
        c.scale(1.2f, 1.1f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(46f)
        c.translate(xOffset, 0f)
        c.scale(1.1f, 1.2f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(46f)
        c.translate(xOffset, 0f)
        c.scale(.95f, 1.1f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(46f)
        c.translate(xOffset, 0f)
        c.scale(1.3f, .7f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(46f)
        c.translate(xOffset, 0f)
        c.scale(.8f, 1.1f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(10f)
        c.translate(xOffset, 0f)
        c.scale(1f, 5f)
        c.drawTextBlob(blob, 0f, 0f, paint)

        c.rotate(5f)
        c.translate(xOffset, 0f)
        c.scale(5f, 1f)
        c.drawTextBlob(blob, 0f, 0f, paint)
    }

    private companion object {
        const val kWidth: Int = 1000
        const val kHeight: Int = 1200
    }
}
