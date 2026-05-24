package org.skia.shaper

import org.skia.foundation.SkFont
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkRect

/**
 * Mirrors Skia's
 * [`SkShaper`](https://github.com/google/skia/blob/main/modules/skshaper/include/SkShaper.h).
 *
 * **Phase I4 (this slice — I4.1) ships [MakePrimitive]** — a
 * naive char-by-char shaper that maps each Unicode code point to its
 * font-local glyph ID via [SkFont.unicharsToGlyphs] and lays glyphs
 * out left-to-right using the font's per-glyph advance widths. No
 * bidi, no kerning, no ligatures, no complex-script support.
 *
 * Complex shaping is intentionally out of scope for this portable
 * implementation until a pure Kotlin shaper is introduced.
 *
 * **API surface** matches upstream's public façade :
 *  - [shape] — emit one or more `RunInfo` / `Buffer` pairs through
 *    the supplied [RunHandler].
 *  - [SkTextBlobShaperRunHandler] — convenience subclass that
 *    accumulates the shaped output into an [SkTextBlob] (the most
 *    common consumer).
 */
public abstract class SkShaper protected constructor() {

    /**
     * Per-run information emitted by [shape] (font + glyph count +
     * advance metrics + line bbox).
     */
    public data class RunInfo(
        public val font: SkFont,
        public val bidiLevel: Int,
        public val advanceX: Float,
        public val advanceY: Float,
        public val glyphCount: Int,
        public val utf8Range: IntRange,
        public val lineHeight: Float,
        public val ascent: Float,
    )

    /**
     * Mutable buffer the [RunHandler] populates with glyph IDs +
     * positions + cluster indices for the run described by the
     * preceding [RunInfo]. The arrays are owned by the handler ;
     * [SkShaper] writes into them directly.
     */
    public class Buffer(
        public val glyphs: IntArray,
        public val positions: FloatArray,  // interleaved (x, y) per glyph
        public val clusters: IntArray,     // utf8 byte offset per glyph
        public val point: FloatArray,      // (originX, originY) for the run
    )

    /**
     * Sink for shaped output. Mirrors Skia's `SkShaper::RunHandler`.
     * The [shape] call sequences :
     *
     *   beginLine                               // once per line
     *   ┌─ runInfo(RunInfo)
     *   │  commitRunInfo
     *   │  runBuffer(RunInfo) → Buffer          // shaper writes glyphs
     *   │  commitRunBuffer(RunInfo)
     *   └─ (repeat for each run on the line)
     *   commitLine
     */
    public interface RunHandler {
        public fun beginLine()
        public fun runInfo(info: RunInfo)
        public fun commitRunInfo()
        public fun runBuffer(info: RunInfo): Buffer
        public fun commitRunBuffer(info: RunInfo)
        public fun commitLine()
    }

    /**
     * Mirrors Skia's `SkShaper::shape(utf8, length, font, ltr, width, handler)`.
     * Resolves [utf8] into glyph runs given [font] and the
     * [leftToRight] direction hint. [width] caps the line width
     * before wrapping ; for I4.1 the primitive shaper ignores
     * wrapping (single-line output regardless of [width]) and the
     * full text becomes a single run.
     */
    public abstract fun shape(
        utf8: String,
        font: SkFont,
        leftToRight: Boolean,
        width: Float,
        runHandler: RunHandler,
    )

    public companion object {
        /**
         * Naive char-by-char shaper. No bidi, no ligatures, no
         * kerning. Glyph positions are computed via the font's
         * `getWidth(glyphId)` advance API.
         */
        public fun MakePrimitive(): SkShaper = PrimitiveShaper()

    }
}

/**
 * Phase I4.1 implementation : single-line, char-by-char glyph
 * mapping. Output is one `RunInfo` per call (text becomes a single
 * run). `bidiLevel = 0` (LTR) or `1` (RTL) per [SkShaper.shape]'s
 * `leftToRight` flag.
 */
internal class PrimitiveShaper : SkShaper() {

    override fun shape(
        utf8: String,
        font: SkFont,
        leftToRight: Boolean,
        width: Float,
        runHandler: RunHandler,
    ) {
        runHandler.beginLine()

        // Resolve code points → glyph IDs via the font.
        val codepoints: IntArray = utf8.codePoints().toArray()
        val n = codepoints.size
        if (n == 0) {
            runHandler.commitLine()
            return
        }
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codepoints, n, glyphsShort)
        val glyphsInt = IntArray(n) { glyphsShort[it].toInt() and 0xFFFF }

        // Compute advance widths + cluster indices (utf8 byte offset
        // per code point — for ASCII / Latin, byte offset = char
        // offset ; for non-Latin we fall back to char offset which is
        // still useful for caret placement at the codepoint
        // granularity).
        val utf8Bytes = utf8.toByteArray(Charsets.UTF_8)
        val clusters = IntArray(n)
        run {
            var byteIdx = 0
            for (i in 0 until n) {
                clusters[i] = byteIdx
                val cp = codepoints[i]
                byteIdx += when {
                    cp < 0x80 -> 1
                    cp < 0x800 -> 2
                    cp < 0x10000 -> 3
                    else -> 4
                }
            }
        }

        // Per-glyph advance widths via the font.
        val xAdvances = FloatArray(n) { font.getWidth(glyphsInt[it]) }
        val totalAdvance = xAdvances.sum()

        // Font metrics for ascent / line-height.
        val metrics = org.skia.foundation.SkFontMetrics()
        val lineHeight = font.getMetrics(metrics)

        val info = RunInfo(
            font = SkFont(font),
            bidiLevel = if (leftToRight) 0 else 1,
            advanceX = totalAdvance,
            advanceY = 0f,
            glyphCount = n,
            utf8Range = 0..utf8Bytes.size,
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

        // Lay glyphs out at the run's origin point. RTL flips the
        // direction (last glyph first).
        val originX = buffer.point.getOrElse(0) { 0f }
        val originY = buffer.point.getOrElse(1) { 0f }
        var penX = 0f
        if (leftToRight) {
            for (i in 0 until n) {
                buffer.glyphs[i] = glyphsInt[i]
                buffer.positions[i * 2] = originX + penX
                buffer.positions[i * 2 + 1] = originY
                buffer.clusters[i] = clusters[i]
                penX += xAdvances[i]
            }
        } else {
            // Lay out RTL : visually right-to-left. The last codepoint
            // anchors at originX ; preceding ones extend leftward.
            penX = totalAdvance
            for (i in 0 until n) {
                penX -= xAdvances[i]
                buffer.glyphs[i] = glyphsInt[i]
                buffer.positions[i * 2] = originX + penX
                buffer.positions[i * 2 + 1] = originY
                buffer.clusters[i] = clusters[i]
            }
        }
        runHandler.commitRunBuffer(info)
        runHandler.commitLine()
    }
}

/**
 * Convenience [SkShaper.RunHandler] that accumulates the shaped
 * output into an [SkTextBlob]. Mirrors Skia's
 * `SkTextBlobBuilderRunHandler`.
 *
 * Usage :
 * ```
 * val handler = SkTextBlobShaperRunHandler(text, originX = 0f, originY = 50f)
 * SkShaper.MakePrimitive().shape(text, font, leftToRight = true,
 *     width = 1000f, runHandler = handler)
 * val blob = handler.makeBlob()
 * canvas.drawTextBlob(blob, 0f, 0f, paint)
 * ```
 */
public class SkTextBlobShaperRunHandler(
    @Suppress("UNUSED_PARAMETER") utf8: String,
    private val originX: Float,
    private val originY: Float,
) : SkShaper.RunHandler {

    private val builder = SkTextBlobBuilder()
    private var lineLeft = Float.POSITIVE_INFINITY
    private var lineTop = Float.POSITIVE_INFINITY
    private var lineRight = Float.NEGATIVE_INFINITY
    private var lineBottom = Float.NEGATIVE_INFINITY
    private var pendingAlloc: SkTextBlobBuilder.AllocationPos? = null

    /**
     * Phase I4.3 — multi-line tracking. Each [beginLine] zeros the
     * "max line height" accumulator ; runs report their lineHeight via
     * [SkShaper.RunInfo.lineHeight] and we keep the largest. On
     * [commitLine] the baseline cursor advances by that amount so the
     * next line's runs anchor below the current one.
     */
    private var currentLineY: Float = originY
    private var currentLineMaxHeight: Float = 0f

    override fun beginLine() {
        currentLineMaxHeight = 0f
    }

    override fun runInfo(info: SkShaper.RunInfo) { /* no-op — buffer alloc happens in runBuffer */ }

    override fun commitRunInfo() { /* no-op */ }

    override fun runBuffer(info: SkShaper.RunInfo): SkShaper.Buffer {
        val alloc = builder.allocRunPos(info.font, info.glyphCount)
        pendingAlloc = alloc
        currentLineMaxHeight = maxOf(currentLineMaxHeight, info.lineHeight)
        // Track line bbox conservatively : extend by `font.size` on
        // every side around `(originX, currentLineY)`.
        val pad = info.font.size
        lineLeft = minOf(lineLeft, originX - pad)
        lineTop = minOf(lineTop, currentLineY + info.ascent)
        lineRight = maxOf(lineRight, originX + info.advanceX + pad)
        lineBottom = maxOf(lineBottom, currentLineY + info.lineHeight + info.ascent)
        return SkShaper.Buffer(
            glyphs = alloc.glyphs,
            positions = alloc.pos,
            clusters = IntArray(info.glyphCount),
            point = floatArrayOf(originX, currentLineY),
        )
    }

    override fun commitRunBuffer(info: SkShaper.RunInfo) {
        pendingAlloc = null
    }

    override fun commitLine() {
        // Advance the baseline cursor by the largest lineHeight seen
        // on this line. If no runs were emitted (empty line) the
        // cursor stays put — matches Skia's behaviour where empty
        // lines have zero height.
        currentLineY += currentLineMaxHeight
    }

    /**
     * Produce the accumulated [SkTextBlob], or `null` if no runs
     * were emitted. Builder is cleared after a successful call —
     * the handler can be reused but state from prior `shape` is
     * gone.
     */
    public fun makeBlob(): SkTextBlob? = builder.make()

    /** Conservative cull rect of the accumulated runs. */
    public fun bounds(): SkRect =
        if (lineLeft.isFinite()) SkRect.MakeLTRB(lineLeft, lineTop, lineRight, lineBottom)
        else SkRect.MakeWH(0f, 0f)
}
