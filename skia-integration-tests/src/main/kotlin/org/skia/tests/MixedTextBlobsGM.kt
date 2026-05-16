package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.floor

/**
 * Port of Skia's `gm/mixedtextblobs.cpp::MixedTextBlobsGM`.
 *
 * Builds a single multi-run text blob mixing :
 *  1. a **giant `O`** at 385pt — large enough to fall through Skia's
 *     "draw glyph as path" threshold.
 *  2. an **LCD-style "LCD!!!!!"** at 32pt with `kSubpixelAntiAlias`
 *     edging (downgraded silently to `kAntiAlias` on raster).
 *  3. (skipped on raster — colour-emoji "♁♃" via `PlanetTypeface()`,
 *     which would need a CBDT/Sbix-capable scaler — see
 *     `STUB.EMOJI_TABLES` in `API_FINALIZATION_PLAN.md`).
 *  4. an **outline `aA`** at 12pt using `ReallyBigA.ttf` — a synthetic
 *     test font with a deliberately oversized "A" glyph.
 *
 * The blob is drawn 4 times, each clipped to a different sub-rect of
 * its bounds (full / upper-left / lower-right / interior). Each draw
 * pre-renders a faint (alpha 0.125) ghost of the full blob behind the
 * clipped main pass.
 *
 * Reference image : `mixedtextblobs.png` (1250 × 700).
 */
public class MixedTextBlobsGM : GM() {

    private companion object {
        const val K_WIDTH: Int = 1250
        const val K_HEIGHT: Int = 700
    }

    private var fBlob: SkTextBlob? = null
    // PlanetTypeface() is null on raster (colour emoji not supported by AWT) ;
    // we keep the field for parity with upstream but it stays null.
    private val fEmojiTypeface: SkTypeface? = ToolUtils.PlanetTypeface()
    private val fEmojiText: String = "♁♃"  // ♁♃ — Earth + Jupiter glyphs.
    private var fReallyBigATypeface: SkTypeface = SkTypeface.MakeEmpty()

    override fun getName(): String = "mixedtextblobs"

    override fun getISize(): SkISize = SkISize.Make(K_WIDTH, K_HEIGHT)

    override fun onOnceBeforeDraw() {
        fReallyBigATypeface = ToolUtils.CreateTypefaceFromResource("fonts/ReallyBigA.ttf")
            ?: ToolUtils.DefaultPortableTypeface()

        val builder = SkTextBlobBuilder()

        // 1. Giant "O" at 385pt.
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 385f).apply {
            edging = SkFont.Edging.kAlias
        }
        var text = "O"
        var bounds = SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        val yOffset = bounds.height()
        ToolUtils.addToTextBlob(builder, text, font, 10f, yOffset)
        val corruptedAx = bounds.width()
        val corruptedAy = yOffset

        val boundsHalfWidth = bounds.width() * 0.5f
        val boundsHalfHeight = bounds.height() * 0.5f

        val xOffset = boundsHalfWidth
        val yOffsetMid = boundsHalfHeight

        // 2. LCD "LCD!!!!!" at 32pt, subpixel AA.
        font.size = 32f
        font.edging = SkFont.Edging.kSubpixelAntiAlias
        font.isSubpixel = true
        text = "LCD!!!!!"
        bounds = SkRect.MakeWH(0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        ToolUtils.addToTextBlob(
            builder, text, font,
            xOffset - bounds.width() * 0.25f,
            yOffsetMid - bounds.height() * 0.5f,
        )

        // 3. Colour-emoji block — skipped on raster (fEmojiTypeface is null).
        val emoji = fEmojiTypeface
        if (emoji != null) {
            font.edging = SkFont.Edging.kAlias
            font.isSubpixel = false
            font.typeface = emoji
            bounds = SkRect.MakeWH(0f, 0f)
            font.measureText(fEmojiText, fEmojiText.length, SkTextEncoding.kUTF8, bounds)
            ToolUtils.addToTextBlob(builder, fEmojiText, font, xOffset, yOffsetMid)
        }

        // 4. Outline "aA" at 12pt with ReallyBigA.
        font.size = 12f
        text = "aA"
        font.typeface = fReallyBigATypeface
        ToolUtils.addToTextBlob(builder, text, font, corruptedAx, corruptedAy)

        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = fBlob ?: return

        c.drawColor(SK_ColorGRAY)

        val paint = SkPaint().apply { color = SK_ColorBLACK }
        c.translate(10f, 40f)

        val bounds = blob.bounds()
        val boundsHalfWidth = bounds.width() * 0.5f
        val boundsHalfHeight = bounds.height() * 0.5f
        val boundsQuarterWidth = boundsHalfWidth * 0.5f
        val boundsQuarterHeight = boundsHalfHeight * 0.5f

        val upperLeftClip = SkRect.MakeXYWH(
            bounds.left, bounds.top,
            boundsHalfWidth, boundsHalfHeight,
        )
        val lowerRightClip = SkRect.MakeXYWH(
            bounds.centerX(), bounds.centerY(),
            boundsHalfWidth, boundsHalfHeight,
        )
        val interiorClip = SkRect.MakeLTRB(
            bounds.left + boundsQuarterWidth,
            bounds.top + boundsQuarterHeight,
            bounds.right - boundsQuarterWidth,
            bounds.bottom - boundsQuarterHeight,
        )

        val clipRects = arrayOf(bounds, upperLeftClip, lowerRightClip, interiorClip)
        val count = clipRects.size

        for (x in 0 until count) {
            drawBlob(c, blob, paint, clipRects[x])
            if (x == (count shr 1) - 1) {
                c.translate(
                    floor(bounds.width() + 25f),
                    -(x * floor(bounds.height() + 25f)),
                )
            } else {
                c.translate(0f, floor(bounds.height() + 25f))
            }
        }
    }

    private fun drawBlob(
        c: SkCanvas,
        blob: SkTextBlob,
        skPaint: SkPaint,
        clipRect: SkRect,
    ) {
        val clipHairline = SkPaint().apply {
            color = SK_ColorWHITE
            style = SkPaint.Style.kStroke_Style
        }
        val paint = skPaint.copy()
        c.save()
        c.drawRect(clipRect, clipHairline)
        paint.alphaf = 0.125f
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.clipRect(clipRect)
        paint.alphaf = 1.0f
        c.drawTextBlob(blob, 0f, 0f, paint)
        c.restore()
    }
}

