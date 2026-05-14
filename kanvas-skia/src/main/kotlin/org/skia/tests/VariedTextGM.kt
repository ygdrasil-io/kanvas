package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/variedtext.cpp::VariedTextGM` (640 × 480), four
 * registered variants : `varied_text_{clipped,ignorable_clip}_{lcd,no_lcd}`.
 *
 * Stress test for many small text draws that each get their own
 * `clipRect`. 30 random-string draws of random size / colour /
 * typeface are positioned so their measured glyph bbox fits inside
 * the canvas, then each draw is wrapped by a per-text clip rect
 * (effective when `fEffectiveClip = true`, ignorable — wider than the
 * text — when `false`). LCD vs non-LCD is mapped to the font edging.
 *
 * Each clip rect is then visualised by a hairline stroke on top, so
 * the reference shows both the text and its clipping bounds.
 *
 * Random-seeded with the default 0 seed so the layout / colours are
 * deterministic between runs and across the C++ reference (modulo the
 * [SkRandom] bit-compat port).
 *
 * Known fidelity caveats :
 *  - `SkFont.Edging.kSubpixelAntiAlias` collapses to `kAntiAlias` on
 *    the AWT-backed glyph path (T3), so the LCD variants will differ
 *    from upstream where subpixel AA was used.
 */
public class VariedTextGM public constructor(
    private val fEffectiveClip: Boolean,
    private val fLCD: Boolean,
) : GM() {

    private val fPaint: SkPaint = SkPaint()
    private val fFont: SkFont = SkFont()
    private val fTypefaces: Array<SkTypeface?> = arrayOfNulls(4)

    private val fStrings: Array<String> = Array(kCnt) { "" }
    private val fColors: IntArray = IntArray(kCnt)
    private val fPtSizes: FloatArray = FloatArray(kCnt)
    private val fTypefaceIndices: IntArray = IntArray(kCnt)
    private val fOffsets: Array<SkPoint> = Array(kCnt) { SkPoint(0f, 0f) }
    private val fClipRects: Array<SkRect> = Array(kCnt) { SkRect.MakeEmpty() }

    override fun getName(): String {
        val sb = StringBuilder("varied_text")
        sb.append(if (fEffectiveClip) "_clipped" else "_ignorable_clip")
        sb.append(if (fLCD) "_lcd" else "_no_lcd")
        return sb.toString()
    }

    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        fPaint.isAntiAlias = true
        fFont.edging = if (fLCD) SkFont.Edging.kSubpixelAntiAlias else SkFont.Edging.kAntiAlias

        val size = getISize()
        val w = size.width.toFloat()
        val h = size.height.toFloat()

        fTypefaces[0] = ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle())
        fTypefaces[1] = ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold())
        fTypefaces[2] = ToolUtils.CreatePortableTypeface("serif", SkFontStyle())
        fTypefaces[3] = ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Bold())

        val random = SkRandom()
        for (i in 0 until kCnt) {
            val length = random.nextRangeU(kMinLength, kMaxLength)
            val chars = CharArray(length)
            for (j in 0 until length) {
                chars[j] = random.nextRangeU('!'.code, 'z'.code).toChar()
            }
            fStrings[i] = String(chars)

            var color = random.nextU()
            color = color or 0xFF000000.toInt()
            fColors[i] = ToolUtils.colorTo565(color)

            fPtSizes[i] = random.nextRangeScalar(kMinPtSize, kMaxPtSize)

            fTypefaceIndices[i] = random.nextULessThan(fTypefaces.size)

            val r = SkRect.MakeEmpty()
            fPaint.color = fColors[i]
            fFont.typeface = fTypefaces[fTypefaceIndices[i]]!!
            fFont.size = fPtSizes[i]

            fFont.measureText(fStrings[i], fStrings[i].length, SkTextEncoding.kUTF8, r)

            // safeRect : offsets that keep the bbox inside the GM border.
            var safeRect = SkRect.MakeLTRB(-r.left, -r.top, w - r.right, h - r.bottom)
            if (safeRect.isEmpty) {
                safeRect = SkRect.MakeWH(w, h)
            }
            fOffsets[i] = SkPoint(
                random.nextRangeScalar(safeRect.left, safeRect.right),
                random.nextRangeScalar(safeRect.top, safeRect.bottom),
            )

            val clip = r.copy()
            clip.offset(fOffsets[i].fX, fOffsets[i].fY)
            clip.outset(2f, 2f)

            if (fEffectiveClip) {
                clip.right -= 0.25f * clip.width()
            }
            fClipRects[i] = clip
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        for (i in 0 until kCnt) {
            fPaint.color = fColors[i]
            fFont.size = fPtSizes[i]
            fFont.typeface = fTypefaces[fTypefaceIndices[i]]!!

            c.withSave {
                clipRect(fClipRects[i])
                translate(fOffsets[i].fX, fOffsets[i].fY)
                drawSimpleText(
                    fStrings[i], fStrings[i].length, SkTextEncoding.kUTF8,
                    0f, 0f, fFont, fPaint,
                )
            }
        }

        // Visualise the clips with a hairline stroke (upstream skips this
        // in bench mode — the kanvas runner is always "draw mode").
        val wirePaint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = 0f
            style = SkPaint.Style.kStroke_Style
        }
        for (i in 0 until kCnt) {
            c.drawRect(fClipRects[i], wirePaint)
        }
    }

    private companion object {
        const val kCnt: Int = 30
        const val kMinLength: Int = 15
        const val kMaxLength: Int = 40
        const val kMinPtSize: Float = 8f
        const val kMaxPtSize: Float = 32f
    }

    public class Variants {
        public companion object {
            public fun ignorableClipNoLcd(): VariedTextGM = VariedTextGM(false, false)
            public fun clippedNoLcd(): VariedTextGM = VariedTextGM(true, false)
            public fun ignorableClipLcd(): VariedTextGM = VariedTextGM(false, true)
            public fun clippedLcd(): VariedTextGM = VariedTextGM(true, true)
        }
    }
}
