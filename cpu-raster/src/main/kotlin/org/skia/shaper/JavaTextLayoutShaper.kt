@file:Suppress("DEPRECATION")

package org.skia.shaper

import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.awt.AwtTypeface
import java.text.Bidi
import java.text.BreakIterator
import java.util.Locale

/**
 * Optional JVM/AWT shaper. Phase I4.2 + I4.3 — bidi-aware [SkShaper] backed by the JDK's
 * `java.awt.Font.layoutGlyphVector` (kerning, ligatures, glyph
 * reordering), `java.text.Bidi` (UAX #9 bidirectional algorithm),
 * and `java.text.BreakIterator` (UAX #14 line break opportunities).
 *
 * **Pipeline (per [shape] call)** :
 *  1. (I4.3) — split the input into lines using
 *     [BreakIterator.getLineInstance] + a greedy width fill : we keep
 *     extending the candidate line as long as the next break-segment
 *     fits in [width]. Single break-segments wider than [width]
 *     overflow alone (no mid-word breaks).
 *  2. (I4.2, per line) — decode UTF-16 chars + UTF-8 byte prefix-sum,
 *     then run [Bidi] with the requested base direction and
 *     [Bidi.reorderVisually] to obtain visual-order runs.
 *  3. Per visual run, delegate to [AwtTypeface.shapeAwtRun] to get
 *     shaped glyph IDs, run-local positions and a glyph→UTF-16-char
 *     map. Translate the cluster indices to UTF-8 byte offsets via
 *     the prefix-sum, and emit one [SkShaper.RunInfo] / [SkShaper.Buffer]
 *     pair anchored at the buffer's origin point + cumulative pen
 *     advance for the line.
 *  4. Surround each line with [SkShaper.RunHandler.beginLine] /
 *     [SkShaper.RunHandler.commitLine] so multi-line handlers (notably
 *     [SkTextBlobShaperRunHandler]) can advance the baseline cursor
 *     between lines.
 *
 * This class is deliberately confined to `:cpu-raster`; portable font paths
 * should use [SkShaper.MakePrimitive] until a pure Kotlin complex shaper is
 * introduced.
 *
 * **Fallback** — when [SkFont.typeface] is not an [AwtTypeface] (for
 * example [org.skia.foundation.SkTypeface.MakeEmpty]) the shaper
 * defers to [PrimitiveShaper] for the *whole* input ; line wrapping
 * relies on AWT measurement, so without the AWT engine we punt to
 * single-line primitive shaping.
 *
 * **Out of scope** :
 *  - mid-word breaks for ultra-narrow widths (a single break segment
 *    overflows alone — no character-level fallback) ;
 *  - HarfBuzz-grade complex Indic / Khmer / Thai shaping. Latin / CJK
 *    / Arabic / Hebrew render correctly via AWT's built-in shapers.
 */
internal class JavaTextLayoutShaper : SkShaper() {

    override fun shape(
        utf8: String,
        font: SkFont,
        leftToRight: Boolean,
        width: Float,
        runHandler: RunHandler,
    ) {
        if (utf8.isEmpty()) {
            runHandler.beginLine()
            runHandler.commitLine()
            return
        }

        val typeface = font.typeface
        if (typeface !is AwtTypeface) {
            // No AWT backend → punt to the primitive shaper for the
            // whole input. PrimitiveShaper manages its own beginLine /
            // commitLine bracketing.
            PrimitiveShaper().shape(utf8, font, leftToRight, width, runHandler)
            return
        }

        val lines = splitIntoLines(utf8, font, width)
        // Phase I4.3 splits at UTF-16 char boundaries — store each
        // line's start char index so we can lift cluster offsets to
        // the original UTF-8 byte stream.
        val originBytes = computeUtf8ByteOffsets(utf8.toCharArray())

        for ((lineStart, lineLimit) in lines) {
            runHandler.beginLine()
            shapeOneLine(
                chars = utf8.toCharArray(),
                start = lineStart,
                limit = lineLimit,
                font = font,
                typeface = typeface,
                leftToRight = leftToRight,
                originBytes = originBytes,
                runHandler = runHandler,
            )
            runHandler.commitLine()
        }
    }

    /**
     * Bidi + per-run shape for one line, restricted to
     * `chars[start..limit)`. Cluster offsets reported via
     * [SkShaper.Buffer.clusters] are absolute UTF-8 byte offsets into
     * the *original* input — looked up via [originBytes].
     */
    private fun shapeOneLine(
        chars: CharArray,
        start: Int,
        limit: Int,
        font: SkFont,
        typeface: AwtTypeface,
        leftToRight: Boolean,
        originBytes: IntArray,
        runHandler: RunHandler,
    ) {
        if (start >= limit) return

        val baseLevel = if (leftToRight) Bidi.DIRECTION_LEFT_TO_RIGHT else Bidi.DIRECTION_RIGHT_TO_LEFT
        val bidi = Bidi(chars, start, null, 0, limit - start, baseLevel)

        val nRuns = bidi.runCount
        val runStarts = IntArray(nRuns) { bidi.getRunStart(it) + start }
        val runLimits = IntArray(nRuns) { bidi.getRunLimit(it) + start }
        val runLevels = ByteArray(nRuns) { bidi.getRunLevel(it).toByte() }

        // Reorder logical runs into visual order. AWT's
        // Bidi.reorderVisually mutates an `objects[]` array in place.
        val visualOrder: Array<Int> = Array(nRuns) { it }
        Bidi.reorderVisually(runLevels, 0, visualOrder, 0, nRuns)

        val metrics = SkFontMetrics()
        val lineHeight = font.getMetrics(metrics)

        var penX = 0f
        for (visualIdx in 0 until nRuns) {
            val logicalRun = visualOrder[visualIdx]
            val runStart = runStarts[logicalRun]
            val runLimit = runLimits[logicalRun]
            if (runStart >= runLimit) continue
            val runIsLtr = (runLevels[logicalRun].toInt() and 1) == 0

            val shaped = typeface.shapeAwtRun(
                chars = chars,
                start = runStart,
                limit = runLimit,
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
                utf8Range = originBytes[runStart]..originBytes[runLimit],
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
                // shaped.charClusters[i] is *relative to runStart*, in UTF-16 chars.
                val charIdx = (runStart + shaped.charClusters[i]).coerceIn(0, originBytes.size - 1)
                buffer.clusters[i] = originBytes[charIdx]
            }
            runHandler.commitRunBuffer(info)

            penX += shaped.advanceX
        }
    }

    /**
     * Phase I4.3 — split [text] into greedy line ranges (start, limit)
     * such that each range fits in [width] when measured by [font].
     *
     * Uses [BreakIterator.getLineInstance] (UAX #14 line breaking
     * opportunities) — the locale is [Locale.ROOT] for deterministic
     * behaviour across environments. Trailing whitespace on each
     * line is *not* trimmed from the range — handlers that want
     * trimmed-bounds rendering can post-process via the cluster
     * indices.
     *
     * Single break-segments that exceed [width] alone are emitted
     * as their own line (overflow). Empty ranges are filtered.
     *
     * @return list of `(start, limit)` UTF-16 char index pairs into
     *         [text] ; the union covers the entire input.
     */
    private fun splitIntoLines(text: String, font: SkFont, width: Float): List<Pair<Int, Int>> {
        if (!width.isFinite() || width <= 0f) {
            return listOf(Pair(0, text.length))
        }

        val bi = BreakIterator.getLineInstance(Locale.ROOT)
        bi.setText(text)

        val lines = mutableListOf<Pair<Int, Int>>()
        var lineStart = 0
        while (lineStart < text.length) {
            var lastFit = lineStart
            var b = bi.following(lineStart)
            while (b != BreakIterator.DONE) {
                val advance = font.measureText(text.substring(lineStart, b))
                if (advance <= width) {
                    lastFit = b
                    b = bi.following(b)
                } else {
                    break
                }
            }
            val emit = when {
                lastFit > lineStart -> lastFit
                b == BreakIterator.DONE -> text.length
                // First candidate breaks past width — overflow it alone.
                else -> b
            }
            lines.add(Pair(lineStart, emit))
            lineStart = emit
        }
        return lines
    }

    /**
     * Phase I4.3 — UTF-16 char index → absolute UTF-8 byte offset
     * prefix-sum table. Length is `chars.size + 1`. Surrogate pairs
     * collapse into one code point at the high-surrogate index ; the
     * low-surrogate's slot mirrors the high one for convenience.
     */
    private fun computeUtf8ByteOffsets(chars: CharArray): IntArray {
        val n = chars.size
        val out = IntArray(n + 1)
        var byteIdx = 0
        var i = 0
        while (i < n) {
            out[i] = byteIdx
            val c = chars[i]
            val cp: Int = if (Character.isHighSurrogate(c) && i + 1 < n) {
                val low = chars[i + 1]
                if (Character.isLowSurrogate(low)) {
                    out[i + 1] = byteIdx
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
        out[n] = byteIdx
        return out
    }
}
