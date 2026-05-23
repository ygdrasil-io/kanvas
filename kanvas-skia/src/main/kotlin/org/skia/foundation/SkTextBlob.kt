package org.skia.foundation

import org.graphiks.math.SkRect

/**
 * Mirrors Skia's
 * [`SkTextBlob`](https://github.com/google/skia/blob/main/include/core/SkTextBlob.h).
 *
 * An immutable sequence of glyph **runs**, each carrying its own
 * [SkFont] + glyph-id array + positions. Built via
 * [SkTextBlobBuilder] and rendered via
 * [org.skia.core.SkCanvas.drawTextBlob].
 *
 * **Phase I1 scope** : `HorizontalSpread` (uniform x-advance per
 * glyph, constant baseline y) and `FullPositions` (per-glyph `(x, y)`)
 * runs. Phase R-RSX extended this with `RSXformPositions` — one
 * [SkRSXform] per glyph carrying rotation-scale-translate, used by
 * upstream's `rsxtext.cpp::RSXShaderGM` (text-on-path-style draws).
 *
 * @property runs immutable list of glyph runs ; each run has its own
 *   font and is drawn left-to-right inside the blob's local space.
 * @property cullRect a conservative bounding box of the blob's glyph
 *   ink ; matches Skia's `bounds()` accessor.
 */
public class SkTextBlob public constructor(
    public val runs: List<Run>,
    private val cullRect: SkRect,
) {
    /** Mirrors Skia's `SkTextBlob::bounds()`. */
    public fun bounds(): SkRect = cullRect

    /**
     * Mirrors Skia's
     * [`int SkTextBlob::getIntercepts(const SkScalar bounds[2], SkScalar intervals[],
     *  const SkPaint* paint)`](https://github.com/google/skia/blob/main/include/core/SkTextBlob.h)
     * (`SkTextBlob.h` ~line 76), simplified to return a freshly-allocated
     * `FloatArray` for clean Kotlin idiom (callers don't pre-size).
     *
     * Returns per-glyph `[entryX, exitX]` pairs for every glyph whose
     * outline (or stroked outline, when [paint] supplies a non-zero
     * `strokeWidth`) intersects the horizontal band defined by
     * `bounds[0]` (upper line, lower y in Skia's y-down system) and
     * `bounds[1]` (lower line, higher y). Used by upstream's underline /
     * strikethrough decorators to skip glyph descenders crossing the
     * decoration line (cf. `texteffects` GM).
     *
     * **Implementation note** — `:kanvas-skia`'s pure-Kotlin port follows
     * the prompt's recommended recipe: per glyph we fetch [SkFont.getPath]
     * (or compute its path bounds), translate by the glyph's draw position
     * derived from the run's positioning mode, intersect the resulting
     * tight bounds with the y-band, and report the bounding-box entry /
     * exit X. This is a conservative approximation of the upstream
     * "stroked-segment intersection" — it never misses a glyph that
     * crosses the band, but it can over-report by a fraction of a glyph
     * width on tall sloped strokes (e.g. `/`, `\`, italic stems). The
     * approximation is sufficient for the underline-skip use case it
     * powers (the gap is widened slightly on each side ; never narrowed).
     *
     * Runs whose [SkFont.typeface] does not produce glyph paths
     * (e.g. [SkTypeface.MakeEmpty]) contribute no intercepts.
     *
     * @param bounds two floats: `[upperY, lowerY]` (text-advance-parallel).
     *               Order is irrelevant — they're sorted internally.
     * @param paint  honoured for `strokeWidth` only when non-null and
     *               `style != kFill_Style` ; expands the conservative
     *               bbox by half the stroke width on each side.
     * @return interleaved `[entryX0, exitX0, entryX1, exitX1, …]` of even
     *         length ; empty when no glyph crosses.
     */
    public fun getIntercepts(bounds: FloatArray, paint: SkPaint? = null): FloatArray {
        require(bounds.size >= 2) { "getIntercepts: bounds must have ≥ 2 entries, got ${bounds.size}" }
        val yLo = kotlin.math.min(bounds[0], bounds[1])
        val yHi = kotlin.math.max(bounds[0], bounds[1])
        // Stroke expands the visible glyph bbox by halfWidth on each side,
        // matching `SkPaint::computeFastStrokeBounds`.
        val strokeRadius = if (paint != null &&
            paint.style != SkPaint.Style.kFill_Style &&
            paint.strokeWidth > 0f
        ) paint.strokeWidth * 0.5f else 0f

        // Use ArrayList<Float> so we don't pre-size — most blobs cross
        // sparsely, and 2 × glyphCount is a loose upper bound.
        val out = ArrayList<Float>()

        for (run in runs) {
            for (i in run.glyphIds.indices) {
                val glyphPath = run.font.getPath(run.glyphIds[i]) ?: continue
                if (glyphPath.isEmpty()) continue
                val (gx, gy) = glyphOriginAt(run, i)
                val pb = glyphPath.computeTightBounds()
                // Empty (zero-area) glyph bbox — skip.
                if (pb.right <= pb.left || pb.bottom <= pb.top) continue
                val gTop = pb.top + gy - strokeRadius
                val gBottom = pb.bottom + gy + strokeRadius
                // No vertical overlap with the band — glyph passes
                // entirely above or below.
                if (gBottom <= yLo || gTop >= yHi) continue
                val entryX = pb.left + gx - strokeRadius
                val exitX = pb.right + gx + strokeRadius
                out.add(entryX)
                out.add(exitX)
            }
        }
        return FloatArray(out.size) { out[it] }
    }

    /**
     * Per-glyph baseline origin in blob-local coords for the `i`-th
     * glyph of [run]. Encodes the three positioning modes:
     *
     *  - [Run.HorizontalSpread]    : `(x + Σ widths[0..i-1], y)`.
     *  - [Run.HorizontalPositions] : `(xs[i], constY)`.
     *  - [Run.FullPositions]       : `(positions[2i], positions[2i+1])`.
     */
    private fun glyphOriginAt(run: Run, i: Int): Pair<Float, Float> = when (run) {
        is Run.HorizontalSpread -> {
            // Recompute cumulative advance up to (but not including) glyph i.
            var x = run.x
            for (k in 0 until i) {
                x += run.font.getWidth(run.glyphIds[k])
            }
            x to run.y
        }
        is Run.HorizontalPositions -> run.xs[i] to run.constY
        is Run.FullPositions       -> run.positions[2 * i] to run.positions[2 * i + 1]
        // RSXform glyphs carry their own rotation/scale on top of a translate
        // — `getIntercepts` is undefined for non-affine-aligned runs upstream
        // too (the underline-skip pipeline never feeds RSXform blobs). Report
        // the translate as the conservative origin.
        is Run.RSXformPositions    -> run.xforms[i].fTx to run.xforms[i].fTy
    }

    /**
     * Internal run representation. The two variants cover the
     * `allocRun` (uniform x-spacing, constant y) and `allocRunPosH`
     * (per-glyph x, constant y) / `allocRunPos` (per-glyph x and y)
     * builder paths.
     */
    public sealed class Run {
        abstract val font: SkFont
        abstract val glyphIds: IntArray

        /**
         * `allocRun` : single origin, glyphs stride out via the font's
         * advance widths. Mirrors Skia's `RunRecord` + `kDefault_RunFlag`.
         */
        data class HorizontalSpread(
            override val font: SkFont,
            override val glyphIds: IntArray,
            val x: Float,
            val y: Float,
        ) : Run()

        /**
         * `allocRunPosH` : per-glyph `x` array, constant baseline `y`.
         * Mirrors Skia's `kHorizontal_Positioning`.
         */
        data class HorizontalPositions(
            override val font: SkFont,
            override val glyphIds: IntArray,
            val xs: FloatArray,
            val constY: Float,
        ) : Run()

        /**
         * `allocRunPos` : full `(x, y)` per glyph, interleaved.
         * Mirrors Skia's `kFull_Positioning`.
         */
        data class FullPositions(
            override val font: SkFont,
            override val glyphIds: IntArray,
            /** Interleaved `[x0, y0, x1, y1, …]`, length = `glyphIds.size * 2`. */
            val positions: FloatArray,
        ) : Run()

        /**
         * `allocRunRSXform` : per-glyph rotation-scale-translate transform
         * carried as an [SkRSXform]. Mirrors Skia's `kRSXform_Positioning`
         * — used by `rsxtext.cpp::RSXShaderGM` and the text-on-path
         * primitives where each glyph needs its own 2×2 affine + translate
         * (e.g. characters following a curve).
         *
         * Each `xforms[i]` maps the glyph's origin-relative outline into
         * blob-local space :
         * ```
         * dst.x = fSCos * srcX − fSSin * srcY + fTx
         * dst.y = fSSin * srcX + fSCos * srcY + fTy
         * ```
         * The translate `(fTx, fTy)` is the glyph's destination origin.
         */
        data class RSXformPositions(
            override val font: SkFont,
            override val glyphIds: IntArray,
            val xforms: Array<SkRSXform>,
        ) : Run() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is RSXformPositions) return false
                return font == other.font &&
                    glyphIds.contentEquals(other.glyphIds) &&
                    xforms.contentEquals(other.xforms)
            }
            override fun hashCode(): Int {
                var h = font.hashCode()
                h = 31 * h + glyphIds.contentHashCode()
                h = 31 * h + xforms.contentHashCode()
                return h
            }
        }
    }

    public companion object {
        /**
         * Mirrors Skia's `SkTextBlob::MakeFromRSXformGlyphs(glyphs, count,
         * xforms, font)`. Builds a single-run RSXform text blob from an
         * already-resolved glyph-ID array.
         *
         * **STUB.RSXBLOB** — per-glyph RSXform glyph-blob rendering is not
         * yet supported end-to-end (the [Run.RSXformPositions] run type
         * exists but [org.skia.core.SkCanvas.drawTextBlob] does not yet
         * apply the per-glyph affine transforms when rasterising). Returns a
         * structurally valid [SkTextBlob] so callers compile; the visual
         * output will fall back to the glyph origins at `(fTx, fTy)` only
         * (no rotation or scale).
         */
        public fun MakeFromRSXformGlyphs(
            glyphs: IntArray,
            xforms: Array<SkRSXform>,
            font: SkFont,
        ): SkTextBlob {
            TODO("STUB.RSXBLOB")
        }

        /**
         * Mirrors Skia's `SkTextBlob::MakeFromRSXform(text, byteLength,
         * xforms, font)`. Resolves [text] (UTF-8) to glyph IDs via [font],
         * then delegates to [MakeFromRSXformGlyphs].
         *
         * **STUB.RSXBLOB** — see [MakeFromRSXformGlyphs].
         */
        public fun MakeFromRSXform(
            text: String,
            xforms: Array<SkRSXform>,
            font: SkFont,
        ): SkTextBlob {
            TODO("STUB.RSXBLOB")
        }
    }
}
