package org.skia.foundation

import org.graphiks.math.SkRect

/**
 * Mirrors Skia's
 * [`SkTextBlobBuilder`](https://github.com/google/skia/blob/main/include/core/SkTextBlobBuilder.h).
 *
 * Mutable builder for an [SkTextBlob]. Three allocation styles, all
 * supported by Phase I1 :
 *
 *  - [allocRun] : uniform x-advance from a single origin (the most
 *    common case ; positions computed per-glyph from the font's
 *    advance widths). The returned [Allocation] exposes the
 *    `glyphs` array for the caller to populate.
 *  - [allocRunPosH] : per-glyph x, constant baseline y.
 *  - [allocRunPos] : full `(x, y)` per glyph (interleaved).
 *
 * Call [make] once finished — the builder can be reused after
 * `make` (matching Skia's
 * [`SkTextBlobBuilder::make`](https://github.com/google/skia/blob/main/src/core/SkTextBlobBuilder.cpp#L25)
 * which clears its internal `RunBuffer` list as a side-effect).
 *
 * @sample
 * ```
 * val builder = SkTextBlobBuilder()
 * val rec = builder.allocRun(font, glyphCount = 3, x = 20f, y = 50f)
 * rec.glyphs[0] = font.unicharsToGlyphs(...)[0]
 * // ...
 * val blob = builder.make() ?: return
 * canvas.drawTextBlob(blob, 0f, 0f, paint)
 * ```
 */
public class SkTextBlobBuilder {

    private val runs: MutableList<SkTextBlob.Run> = mutableListOf()

    /**
     * Allocation handle for [allocRun] — exposes the glyph-id array.
     * Caller fills [glyphs] before the next builder call ; the array
     * is captured by reference into the underlying run.
     */
    public class Allocation internal constructor(public val glyphs: IntArray)

    /** Allocation handle for [allocRunPosH]. */
    public class AllocationPosH internal constructor(
        public val glyphs: IntArray,
        public val pos: FloatArray,
    )

    /** Allocation handle for [allocRunPos]. */
    public class AllocationPos internal constructor(
        public val glyphs: IntArray,
        public val pos: FloatArray,
    )

    /**
     * Allocation handle for [allocRunRSXform]. The [xforms] array
     * exposes each glyph's [SkRSXform] slot in-place — the caller fills
     * it before the next builder call. Upstream Skia's
     * `RunBuffer::xforms()` returns a writable pointer ; we expose a
     * fixed-size [Array] so callers can `xforms[i] = SkRSXform(...)`.
     */
    public class AllocationRSXform internal constructor(
        public val glyphs: IntArray,
        public val xforms: Array<SkRSXform>,
    )

    /**
     * Mirrors Skia's `SkTextBlobBuilder::allocRun(font, count, x, y)`.
     * Reserves space for [count] glyphs with **uniform x-advance** (the
     * font's per-glyph advance widths drive positioning) and a single
     * baseline `(x, y)` origin. Returns an [Allocation] whose
     * [Allocation.glyphs] array the caller must populate before the
     * next builder operation.
     */
    public fun allocRun(font: SkFont, count: Int, x: Float, y: Float): Allocation {
        require(count >= 0) { "count must be ≥ 0, got $count" }
        val glyphs = IntArray(count)
        runs.add(SkTextBlob.Run.HorizontalSpread(SkFont(font), glyphs, x, y))
        return Allocation(glyphs)
    }

    /**
     * Mirrors Skia's `SkTextBlobBuilder::allocRunPosH(font, count, y)`.
     * Per-glyph X positions in [AllocationPosH.pos] (length [count]),
     * constant baseline Y.
     */
    public fun allocRunPosH(font: SkFont, count: Int, y: Float): AllocationPosH {
        require(count >= 0) { "count must be ≥ 0, got $count" }
        val glyphs = IntArray(count)
        val xs = FloatArray(count)
        runs.add(SkTextBlob.Run.HorizontalPositions(SkFont(font), glyphs, xs, y))
        return AllocationPosH(glyphs, xs)
    }

    /**
     * Mirrors Skia's `SkTextBlobBuilder::allocRunPos(font, count)`.
     * Full per-glyph `(x, y)` positions in [AllocationPos.pos]
     * (length `count * 2`, interleaved `[x0, y0, x1, y1, …]`).
     */
    public fun allocRunPos(font: SkFont, count: Int): AllocationPos {
        require(count >= 0) { "count must be ≥ 0, got $count" }
        val glyphs = IntArray(count)
        val positions = FloatArray(count * 2)
        runs.add(SkTextBlob.Run.FullPositions(SkFont(font), glyphs, positions))
        return AllocationPos(glyphs, positions)
    }

    /**
     * Mirrors Skia's `SkTextBlobBuilder::allocRunRSXform(font, count)`
     * (see [`SkTextBlobBuilder.h`](https://github.com/google/skia/blob/main/include/core/SkTextBlobBuilder.h)).
     * Reserves space for [count] glyphs whose per-glyph transform is
     * an [SkRSXform] (rotation + uniform-scale + translate). Returns
     * an [AllocationRSXform] handle exposing both the glyph-id array
     * (`glyphs`, length [count]) and the writable transform array
     * (`xforms`, length [count]) — caller fills both before the next
     * builder operation.
     *
     * Used by upstream's `rsxtext.cpp::RSXShaderGM` and any text-on-path
     * style draw where each glyph needs its own rotation + scale.
     */
    public fun allocRunRSXform(font: SkFont, count: Int): AllocationRSXform {
        require(count >= 0) { "count must be ≥ 0, got $count" }
        val glyphs = IntArray(count)
        val xforms = Array(count) { SkRSXform.Identity }
        runs.add(SkTextBlob.Run.RSXformPositions(SkFont(font), glyphs, xforms))
        return AllocationRSXform(glyphs, xforms)
    }

    /**
     * Mirrors Skia's `SkTextBlobBuilder::make()`. Returns the built
     * blob and clears the builder's run list (so the same builder can
     * be reused for the next blob). Returns `null` when no runs were
     * allocated since the last `make()` call.
     */
    public fun make(): SkTextBlob? {
        if (runs.isEmpty()) return null
        val cull = computeCullRect(runs)
        val blob = SkTextBlob(runs.toList(), cull)
        runs.clear()
        return blob
    }

    private companion object {
        /**
         * Conservative cull-rect computation — for each run we expand
         * the origin point by `font.size` on every side. Skia's
         * `SkTextBlobBuilder` queries the font's per-glyph bounds via
         * `SkGlyphRunPainter` ; that pipeline lands in I2's glyph mask
         * cache. For now the bbox is loose but always >= the visible
         * ink, which is what `bounds()` callers need (culling).
         */
        private fun computeCullRect(runs: List<SkTextBlob.Run>): SkRect {
            if (runs.isEmpty()) return SkRect.MakeWH(0f, 0f)
            var l = Float.POSITIVE_INFINITY
            var t = Float.POSITIVE_INFINITY
            var r = Float.NEGATIVE_INFINITY
            var b = Float.NEGATIVE_INFINITY
            for (run in runs) {
                val pad = run.font.size
                when (run) {
                    is SkTextBlob.Run.HorizontalSpread -> {
                        // Conservative : extend by N glyphs of `font.size` width
                        // each ; an empty run is a no-op.
                        if (run.glyphIds.isEmpty()) continue
                        val w = run.glyphIds.size * pad
                        l = minOf(l, run.x - pad)
                        t = minOf(t, run.y - pad)
                        r = maxOf(r, run.x + w + pad)
                        b = maxOf(b, run.y + pad)
                    }
                    is SkTextBlob.Run.HorizontalPositions -> {
                        for (px in run.xs) {
                            l = minOf(l, px - pad)
                            r = maxOf(r, px + pad)
                        }
                        t = minOf(t, run.constY - pad)
                        b = maxOf(b, run.constY + pad)
                    }
                    is SkTextBlob.Run.FullPositions -> {
                        var i = 0
                        while (i < run.positions.size) {
                            val px = run.positions[i]
                            val py = run.positions[i + 1]
                            l = minOf(l, px - pad)
                            t = minOf(t, py - pad)
                            r = maxOf(r, px + pad)
                            b = maxOf(b, py + pad)
                            i += 2
                        }
                    }
                    is SkTextBlob.Run.RSXformPositions -> {
                        // Conservative : per-glyph translate ± pad ; the
                        // RSXform rotation is bounded by the same `font.size`
                        // pad in any direction.
                        for (xf in run.xforms) {
                            l = minOf(l, xf.fTx - pad)
                            t = minOf(t, xf.fTy - pad)
                            r = maxOf(r, xf.fTx + pad)
                            b = maxOf(b, xf.fTy + pad)
                        }
                    }
                }
            }
            if (!l.isFinite() || !t.isFinite()) return SkRect.MakeWH(0f, 0f)
            return SkRect.MakeLTRB(l, t, r, b)
        }
    }
}
