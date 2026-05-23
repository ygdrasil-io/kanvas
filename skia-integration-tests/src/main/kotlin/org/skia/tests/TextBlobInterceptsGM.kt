package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/texteffects.cpp::textblob_intercepts`
 * (DEF_SIMPLE_GM, 940 × 800).
 *
 * Renders the string `"Hyjay {worlp}."` in three positioning modes
 * (`make_text` / `make_posh` / `make_pos`) with two spacing variants
 * (0 and 20 px extra inter-glyph spacing), and for each draws a thick
 * horizontal underline band with gaps wherever a glyph descender
 * crosses the band — computed via [SkTextBlob.getIntercepts].
 *
 * The `trim_with_halo` logic widens each gap by 1.5× the band's
 * half-height on each side, merging or eliminating gaps that shrink
 * away.
 *
 * **Dependency** : [SkTextBlob.getIntercepts] is implemented in
 * `:kanvas-skia` as a conservative bbox-based approximation (see its
 * KDoc). Rendering similarity with the upstream reference will be
 * lower than for fully-outline-intersecting GMs.
 */
public class TextBlobInterceptsGM : GM() {

    override fun getName(): String = "textblob_intercepts"
    override fun getISize(): SkISize = SkISize.Make(940, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val text = "Hyjay {worlp}."
        val font = ToolUtils.DefaultPortableFont(100f).apply {
            edging = SkFont.Edging.kAntiAlias
        }

        // Convert text → glyph ids (reuse textToGlyphs for UTF-8 → glyph ids).
        val glyphs = font.textToGlyphs(text)

        val b0 = makeText(font, glyphs)

        c.translate(20f, 120f)
        drawBlobAdorned(c, b0)

        for (spacing in floatArrayOf(0f, 20f)) {
            val b1 = makePosH(font, glyphs, spacing)
            val b2 = makePos(font, glyphs, spacing)
            c.translate(0f, 150f)
            drawBlobAdorned(c, b1)
            c.translate(0f, 150f)
            drawBlobAdorned(c, b2)
        }
    }

    // -------------------------------------------------------------------------
    // Blob factories — mirror upstream's three static helpers.
    // -------------------------------------------------------------------------

    /**
     * Mirrors `make_text`: single-origin `allocRun` blob, glyphs
     * advance from x=0, y=0 using the font's per-glyph widths.
     */
    private fun makeText(font: SkFont, glyphs: IntArray): SkTextBlob {
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, glyphs.size, 0f, 0f)
        for (i in glyphs.indices) rec.glyphs[i] = glyphs[i]
        return builder.make()!!
    }

    /**
     * Mirrors `make_posh`: `allocRunPosH` blob with extra [spacing]
     * added between glyphs (cumulative), constant y=0.
     */
    private fun makePosH(font: SkFont, glyphs: IntArray, spacing: Float): SkTextBlob {
        val xpos = font.getXPos(glyphs)
        for (i in 1 until glyphs.size) {
            xpos[i] += spacing * i
        }
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPosH(font, glyphs.size, 0f)
        for (i in glyphs.indices) {
            rec.glyphs[i] = glyphs[i]
            rec.pos[i] = xpos[i]
        }
        return builder.make()!!
    }

    /**
     * Mirrors `make_pos`: `allocRunPos` blob with extra [spacing]
     * added between glyphs (cumulative), y=0 for all glyphs.
     */
    private fun makePos(font: SkFont, glyphs: IntArray, spacing: Float): SkTextBlob {
        val pts = font.getPos(glyphs)
        for (i in 1 until glyphs.size) {
            pts[i] = SkPoint(pts[i].fX + spacing * i, pts[i].fY)
        }
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPos(font, glyphs.size)
        for (i in glyphs.indices) {
            rec.glyphs[i] = glyphs[i]
            rec.pos[i * 2]     = pts[i].fX
            rec.pos[i * 2 + 1] = pts[i].fY
        }
        return builder.make()!!
    }

    // -------------------------------------------------------------------------
    // Underline rendering helpers.
    // -------------------------------------------------------------------------

    /**
     * Mirrors upstream's `draw_blob_adorned`: draws the blob, then
     * computes intercepts for the y-band [8, 16], trims them with a
     * 1.5× halo, and draws a thick stroked underline with the gaps.
     */
    private fun drawBlobAdorned(canvas: SkCanvas, blob: SkTextBlob) {
        val paint = SkPaint()
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        val yminmax = floatArrayOf(8f, 16f)
        val rawIntervals = blob.getIntercepts(yminmax)
        var count = rawIntervals.size
        if (count == 0) return

        val intervals = rawIntervals.copyOf()
        val halo = (yminmax[1] - yminmax[0]) * 0.5f * 1.5f
        count = trimWithHalo(intervals, count, halo)
        if (count < 2) return

        val y = (yminmax[0] + yminmax[1]) * 0.5f
        val end = 900f
        val builder = SkPathBuilder()
        builder.moveTo(0f, y)
        var i = 0
        while (i < count) {
            builder.lineTo(intervals[i], y).moveTo(intervals[i + 1], y)
            i += 2
        }
        if (intervals[count - 1] < end) {
            builder.lineTo(end, y)
        }

        paint.isAntiAlias = true
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = yminmax[1] - yminmax[0]
        canvas.drawPath(builder.detach(), paint)
    }

    /**
     * Mirrors upstream's `trim_with_halo`: widens each gap on both
     * sides by [margin], removing gaps that collapse (entry >= exit).
     * Operates in-place on [intervals] (only the first [count] entries
     * are live). Returns the new count of live entries.
     */
    private fun trimWithHalo(intervals: FloatArray, count: Int, margin: Float): Int {
        require(count > 0 && count % 2 == 0)
        var n = count
        var idx = 0

        // Extend the very first entry's left edge outward.
        intervals[idx] -= margin
        idx++

        while (idx < n - 1) {
            intervals[idx] += margin       // close the right side of left gap
            intervals[idx + 1] -= margin   // open the left side of right gap
            if (intervals[idx] >= intervals[idx + 1]) {
                // Gap vanished — compact the array.
                val remaining = n - idx - 2
                if (remaining > 0) {
                    System.arraycopy(intervals, idx + 2, intervals, idx, remaining)
                }
                n -= 2
                // Don't advance idx; it now points at the next pair.
            } else {
                idx += 2
            }
        }

        // Extend the very last entry's right edge outward.
        intervals[n - 1] += margin
        return n
    }
}
