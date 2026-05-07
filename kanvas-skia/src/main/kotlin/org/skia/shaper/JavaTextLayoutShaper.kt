package org.skia.shaper

import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.awt.AwtTypeface
import java.text.Bidi

/**
 * Phase I4.2 — bidi-aware [SkShaper] backed by the JDK's
 * `java.awt.Font.layoutGlyphVector` (kerning, ligatures, glyph
 * reordering) and `java.text.Bidi` (UAX #9 bidirectional algorithm).
 *
 * **Pipeline** :
 *  1. Decode the input UTF-8 string into Java's UTF-16 char buffer.
 *  2. Compute UTF-8 byte offsets per UTF-16 char (so the [SkShaper]
 *     contract — clusters as UTF-8 byte indices — is honoured).
 *  3. Run [Bidi] with the requested base direction to obtain a list
 *     of `(start, limit, level)` level runs in *logical* order.
 *  4. Reorder those runs into *visual* order via [Bidi.reorderVisually]
 *     (mirror the runs whose level is odd-aligned with the base).
 *  5. For each visual run :
 *     - delegate to [AwtTypeface.shapeAwtRun] which returns shaped
 *       glyph IDs + run-local positions + glyph→UTF-16-char map ;
 *     - emit one [SkShaper.RunInfo] / [SkShaper.Buffer] pair, with
 *       glyphs anchored at the supplied [SkShaper.Buffer.point]
 *       origin plus the cumulative advance of preceding runs ;
 *     - translate the per-glyph char-cluster indices to UTF-8 byte
 *       offsets via the prefix-sum table from step 2.
 *
 * **Fallback** : when [SkFont.typeface] is not an [AwtTypeface] (for
 * example [org.skia.foundation.SkTypeface.MakeEmpty]) the shaper
 * defers to [PrimitiveShaper] — the AWT entry-point requires a real
 * font.
 *
 * **Out of scope** :
 *  - line wrapping (Phase I4.3 via ICU `BreakIterator`) — `width` is
 *    accepted for API parity but ignored ;
 *  - complex Indic / Khmer / Thai shaping (HarfBuzz-grade). Latin /
 *    CJK / Arabic / Hebrew render correctly for the common cases via
 *    AWT's built-in shapers.
 */
internal class JavaTextLayoutShaper : SkShaper() {

    override fun shape(
        utf8: String,
        font: SkFont,
        leftToRight: Boolean,
        @Suppress("UNUSED_PARAMETER") width: Float,
        runHandler: RunHandler,
    ) {
        runHandler.beginLine()

        if (utf8.isEmpty()) {
            runHandler.commitLine()
            return
        }

        val typeface = font.typeface
        if (typeface !is AwtTypeface) {
            // No AWT backend → punt to the primitive shaper. We've
            // already started the line ; close it and re-shape via
            // PrimitiveShaper which manages its own line bracketing.
            runHandler.commitLine()
            PrimitiveShaper().shape(utf8, font, leftToRight, width, runHandler)
            return
        }

        val chars: CharArray = utf8.toCharArray()
        val charsLen = chars.size

        // --- UTF-16 char index → UTF-8 byte offset (prefix-sum) ---
        // Skia's RunHandler clusters are UTF-8 byte offsets ; we walk
        // the input once in UTF-16 order and accumulate.
        val byteOffsetByChar = IntArray(charsLen + 1)
        run {
            var byteIdx = 0
            var i = 0
            while (i < charsLen) {
                byteOffsetByChar[i] = byteIdx
                val c = chars[i]
                val cp: Int = if (Character.isHighSurrogate(c) && i + 1 < charsLen) {
                    val low = chars[i + 1]
                    if (Character.isLowSurrogate(low)) {
                        byteOffsetByChar[i + 1] = byteIdx
                        Character.toCodePoint(c, low).also { i++ }
                    } else c.code
                } else c.code
                byteIdx += when {
                    cp < 0x80 -> 1
                    cp < 0x800 -> 2
                    cp < 0x10000 -> 3
                    else -> 4
                }
                i++
            }
            byteOffsetByChar[charsLen] = byteIdx
        }

        // --- Bidi resolution ---
        val baseLevel = if (leftToRight) Bidi.DIRECTION_LEFT_TO_RIGHT else Bidi.DIRECTION_RIGHT_TO_LEFT
        val bidi = Bidi(chars, 0, null, 0, charsLen, baseLevel)

        val nRuns = bidi.runCount
        val runStarts = IntArray(nRuns) { bidi.getRunStart(it) }
        val runLimits = IntArray(nRuns) { bidi.getRunLimit(it) }
        val runLevels = ByteArray(nRuns) { bidi.getRunLevel(it).toByte() }

        // Reorder logical runs into visual order. AWT's
        // Bidi.reorderVisually mutates an `objects[]` array in place.
        val visualOrder: Array<Int> = Array(nRuns) { it }
        Bidi.reorderVisually(runLevels, 0, visualOrder, 0, nRuns)

        // --- Font metrics, line height ---
        val metrics = SkFontMetrics()
        val lineHeight = font.getMetrics(metrics)

        var penX = 0f
        for (visualIdx in 0 until nRuns) {
            val logicalRun = visualOrder[visualIdx]
            val start = runStarts[logicalRun]
            val limit = runLimits[logicalRun]
            if (start >= limit) continue
            val runIsLtr = (runLevels[logicalRun].toInt() and 1) == 0

            val shaped = typeface.shapeAwtRun(
                chars = chars,
                start = start,
                limit = limit,
                leftToRight = runIsLtr,
                size = font.size,
                scaleX = font.scaleX,
                skewX = font.skewX,
            )
            val n = shaped.glyphIds.size
            if (n == 0) continue

            val info = RunInfo(
                font = SkFont(font),
                bidiLevel = runLevels[logicalRun].toInt(),
                advanceX = shaped.advanceX,
                advanceY = 0f,
                glyphCount = n,
                utf8Range = byteOffsetByChar[start]..byteOffsetByChar[limit],
                lineHeight = lineHeight,
                ascent = metrics.fAscent,
            )
            runHandler.runInfo(info)
            runHandler.commitRunInfo()

            val buffer = runHandler.runBuffer(info)
            require(buffer.glyphs.size >= n) { "buffer.glyphs too small : ${buffer.glyphs.size} < $n" }
            require(buffer.positions.size >= n * 2) {
                "buffer.positions too small : ${buffer.positions.size} < ${n * 2}"
            }
            require(buffer.clusters.size >= n) { "buffer.clusters too small : ${buffer.clusters.size} < $n" }

            val originX = buffer.point.getOrElse(0) { 0f }
            val originY = buffer.point.getOrElse(1) { 0f }
            for (i in 0 until n) {
                buffer.glyphs[i] = shaped.glyphIds[i]
                buffer.positions[i * 2] = originX + penX + shaped.positions[i * 2]
                buffer.positions[i * 2 + 1] = originY + shaped.positions[i * 2 + 1]
                // shaped.charClusters[i] is *relative to start*, in UTF-16 chars.
                val charIdx = (start + shaped.charClusters[i]).coerceIn(0, charsLen)
                buffer.clusters[i] = byteOffsetByChar[charIdx]
            }
            runHandler.commitRunBuffer(info)

            penX += shaped.advanceX
        }
        runHandler.commitLine()
    }
}
