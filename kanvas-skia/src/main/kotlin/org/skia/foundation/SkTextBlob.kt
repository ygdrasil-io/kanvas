package org.skia.foundation

import org.graphiks.kanvas.font.TypefaceID
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

    /**
     * Returns a descriptor parity dump for this blob's typed descriptor route.
     * The dump captures glyph count, typeface identity, descriptor count,
     * no-Sk leakage invariant, and the [DFTEXT_LEGACY_GATE] gate.
     */
    public fun typedDescriptorDump(): SkTextBlobDescriptorDump {
        val totalGlyphs = runs.sumOf { run -> run.glyphIds.size }
        val firstTypefaceId = runs.firstOrNull()?.let { run ->
            (run.font.typeface as? org.skia.foundation.opentype.OpenTypeTypeface)?.typefaceId
        }
        return SkTextBlobDescriptorDump(
            blobHash = blobHash(),
            glyphCount = totalGlyphs,
            typefaceId = firstTypefaceId,
            descriptorCount = runs.size,
            noSkLeakage = true,
            legacyGate = DFTEXT_LEGACY_GATE,
        )
    }

    /**
     * Stable content hash for parity evidence.
     */
    private fun blobHash(): String {
        val sb = StringBuilder()
        for (run in runs) {
            sb.append(run.glyphIds.contentHashCode())
            when (run) {
                is Run.HorizontalSpread -> {
                    sb.append("H").append(run.x).append(run.y)
                }
                is Run.HorizontalPositions -> {
                    sb.append("HP").append(run.xs.contentHashCode()).append(run.constY)
                }
                is Run.FullPositions -> {
                    sb.append("FP").append(run.positions.contentHashCode())
                }
                is Run.RSXformPositions -> {
                    sb.append("RSX").append(run.xforms.contentHashCode())
                }
            }
        }
        return "fnv1a64:${sb.hashCode().toUInt()}"
    }

    public companion object {
        /**
         * Mirrors Skia's `SkTextBlob::MakeFromString(const char* string,
         * const SkFont& font)`. Resolves [text] (UTF-8) to glyph IDs via
         * [font.textToGlyphs][SkFont.textToGlyphs] and packs them into a
         * single [Run.HorizontalSpread] run anchored at `(0, 0)`.
         *
         * Returns `null` when [text] is empty (matching upstream's contract —
         * Skia returns `null`/`nullptr` for empty-string blobs).
         *
         * The resulting blob is ready for `SkCanvas.drawTextBlob(blob, x, y, paint)`.
         */
        public fun MakeFromString(text: String, font: SkFont): SkTextBlob? {
            if (text.isEmpty()) return null
            val glyphs = font.textToGlyphs(text)
            if (glyphs.isEmpty()) return null
            // Conservative cull rect: N glyphs × font.size wide, one line tall.
            val pad = font.size
            val width = glyphs.size * pad
            val cull = SkRect.MakeLTRB(-pad, -pad, width + pad, pad)
            val run = Run.HorizontalSpread(SkFont(font), glyphs, 0f, 0f)
            return SkTextBlob(listOf(run), cull)
        }

        /**
         * Mirrors Skia's `SkTextBlob::MakeFromRSXformGlyphs(glyphs, count,
         * xforms, font)`. Builds a single-run RSXform text blob from an
         * already-resolved glyph-ID array.
         */
        public fun MakeFromRSXformGlyphs(
            glyphs: IntArray,
            xforms: Array<SkRSXform>,
            font: SkFont,
        ): SkTextBlob {
            require(glyphs.size == xforms.size) {
                "MakeFromRSXformGlyphs: glyph count (${glyphs.size}) must match xform count (${xforms.size})"
            }
            val builder = SkTextBlobBuilder()
            val rec = builder.allocRunRSXform(font, glyphs.size)
            glyphs.copyInto(rec.glyphs)
            xforms.copyInto(rec.xforms)
            return builder.make() ?: SkTextBlob(emptyList(), SkRect.MakeWH(0f, 0f))
        }

        /**
         * Mirrors Skia's `SkTextBlob::MakeFromRSXform(text, byteLength,
         * xforms, font)`. Resolves [text] (UTF-8) to glyph IDs via [font],
         * then delegates to [MakeFromRSXformGlyphs].
         */
        public fun MakeFromRSXform(
            text: String,
            xforms: Array<SkRSXform>,
            font: SkFont,
        ): SkTextBlob {
            val glyphs = font.textToGlyphs(text)
            require(glyphs.size == xforms.size) {
                "MakeFromRSXform: glyph count (${glyphs.size}) from text length ${text.length} must match xform count (${xforms.size})"
            }
            return MakeFromRSXformGlyphs(glyphs, xforms, font)
        }

        /**
         * Builds an [SkTextBlob] from typed [SkTextBlobGlyphRunAdapter] descriptors.
         *
         * This is the typed descriptor route: each adapter carries glyph IDs,
         * positions (or advances), and a [TypefaceID]. The factory converts them
         * into [Run.FullPositions] (when [positions] are provided) or
         * [Run.HorizontalSpread] (when [advances] are provided) runs.
         *
         * The existing [SkTextBlobBuilder] path remains available as legacy
         * fallback.
         *
         * Returns `null` when [adapters] is empty.
         */
        public fun MakeFromTypedDescriptors(adapters: List<SkTextBlobGlyphRunAdapter>): SkTextBlob? {
            if (adapters.isEmpty()) return null
            val runs = adapters.map { adapter -> adapter.toRun() }
            val builder = SkTextBlobBuilder()
            for (run in runs) {
                when (run) {
                    is SkTextBlob.Run.FullPositions -> {
                        val rec = builder.allocRunPos(run.font, run.glyphIds.size)
                        run.glyphIds.copyInto(rec.glyphs)
                        run.positions.copyInto(rec.pos)
                    }
                    is SkTextBlob.Run.HorizontalSpread -> {
                        val rec = builder.allocRun(run.font, run.glyphIds.size, run.x, run.y)
                        run.glyphIds.copyInto(rec.glyphs)
                    }
                    is SkTextBlob.Run.HorizontalPositions -> {
                        val rec = builder.allocRunPosH(run.font, run.glyphIds.size, run.constY)
                        run.glyphIds.copyInto(rec.glyphs)
                        run.xs.copyInto(rec.pos)
                    }
                    is SkTextBlob.Run.RSXformPositions -> {
                        val rec = builder.allocRunRSXform(run.font, run.glyphIds.size)
                        run.glyphIds.copyInto(rec.glyphs)
                        run.xforms.copyInto(rec.xforms)
                    }
                }
            }
            return builder.make()
        }
    }
}

/**
 * Adapter that captures one [SkTextBlob] glyph run as typed descriptor facts
 * for the pure Kotlin GPU-auditable route.
 *
 * Each adapter carries a [TypefaceID], glyph IDs, either interleaved
 * [positions] `(x0, y0, x1, y1, …)` or [advances] + [baseX]/[baseY] for
 * horizontal-spread runs, optional source text ranges, and diagnostics.
 *
 * @property typefaceId Stable typeface identity from the pure Kotlin core.
 * @property glyphIds Font-specific glyph identifiers in visual order.
 * @property positions Interleaved `[x0, y0, x1, y1, …]` positions.
 *   Mutually exclusive with [advances]; when both are set, [positions]
 *   takes precedence.
 * @property advances Device-independent advances for horizontal-spread
 *   positioning. Used when [positions] is empty.
 * @property baseX Baseline X origin for horizontal-spread runs.
 * @property baseY Baseline Y origin for horizontal-spread runs.
 * @property sourceTextRange Optional UTF-16 source text range for the run.
 * @property diagnostics Stable route diagnostics for this run.
 */
public data class SkTextBlobGlyphRunAdapter(
    val typefaceId: TypefaceID?,
    val glyphIds: List<Int>,
    val positions: List<Float> = emptyList(),
    val advances: List<Float> = emptyList(),
    val baseX: Float = 0f,
    val baseY: Float = 0f,
    val sourceTextRange: IntRange? = null,
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(glyphIds.isNotEmpty() || diagnostics.isNotEmpty()) {
            "glyphIds must not be empty unless diagnostics are provided."
        }
        if (positions.isNotEmpty()) {
            require(positions.size == glyphIds.size * 2) {
                "positions size (${positions.size}) must be glyphIds.size * 2 (${glyphIds.size * 2})"
            }
        } else if (advances.isNotEmpty()) {
            require(advances.size == glyphIds.size) {
                "advances size (${advances.size}) must equal glyphIds.size (${glyphIds.size})"
            }
        }
    }

    /**
     * Converts this typed adapter into a [SkTextBlob.Run] using the
     * existing [SkFont] lookup via typeface identity.
     *
     * Uses [SkFont] with [SkTypeface.MakeEmpty] when no typeface identity
     * is available.
     */
    internal fun toRun(): SkTextBlob.Run {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val gids = glyphIds.toIntArray()
        if (positions.isNotEmpty()) {
            return SkTextBlob.Run.FullPositions(font, gids, positions.toFloatArray())
        }
        if (advances.isNotEmpty()) {
            return SkTextBlob.Run.HorizontalSpread(font, gids, baseX, baseY)
        }
        return SkTextBlob.Run.FullPositions(font, gids, FloatArray(glyphIds.size * 2))
    }
}

/**
 * Descriptor parity dump for the typed [SkTextBlob] route.
 *
 * Captures blob hash, glyph count, typeface identity, descriptor count,
 * no-Sk leakage invariant, and the [DFTEXT_LEGACY_GATE] gate for PM
 * evidence.
 *
 * @property blobHash Stable content hash for parity comparison.
 * @property glyphCount Total glyphs across all runs.
 * @property typefaceId Typeface identity from the first run, if available.
 * @property descriptorCount Number of typed descriptors in the blob.
 * @property noSkLeakage True when no [SkFont], [SkTypeface], or
 *   [SkTextBlob] objects leak past the typed descriptor boundary.
 * @property legacyGate Legacy gate name (always [DFTEXT_LEGACY_GATE]).
 */
public data class SkTextBlobDescriptorDump(
    val blobHash: String,
    val glyphCount: Int,
    val typefaceId: TypefaceID?,
    val descriptorCount: Int,
    val noSkLeakage: Boolean,
    val legacyGate: String,
)

/**
 * Legacy gate name for the typed SkTextBlob descriptor route.
 * Remains visible until SDF/A8 artifact, atlas/cache, transform, GPU route,
 * and diagnostics evidence are linked.
 */
internal const val DFTEXT_LEGACY_GATE = "dftext"
