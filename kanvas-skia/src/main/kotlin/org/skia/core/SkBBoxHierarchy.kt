package org.skia.core

import org.graphiks.math.SkRect

/**
 * Mirrors Skia's
 * [`SkBBoxHierarchy`](https://github.com/google/skia/blob/main/include/core/SkBBHFactory.h) :
 * a spatial index that stores N axis-aligned bounding rectangles
 * (one per recorded picture op) and answers "give me the indices of
 * the rects that intersect this query rect" in `O(log N + K)` time
 * (where `K` is the number of hits).
 *
 * **Use case** : during [SkPicture.playback], the picture asks the
 * hierarchy which recorded ops touch the playback canvas's current
 * clip and skips ops that don't. For a 1000-op picture replayed
 * under a tiny clip, the cull turns an O(N) walk into O(log N + K).
 *
 * **Lifecycle** : a hierarchy is **single-shot** — built via one
 * [insert] call holding the full bounds list, then queried via
 * [search] / [bytesUsed]. Callers cannot mutate the index after
 * construction (matches Skia's contract — pictures are immutable
 * after `finishRecordingAsPicture()`).
 *
 * Concrete implementations :
 *  - [SkRTree] — bottom-up bulk-loaded R-tree with branch factor
 *    `[6, 11]`. Default for [SkRTreeFactory].
 *
 * Implementations are **not** thread-safe ; build / query a hierarchy
 * from a single thread.
 */
public abstract class SkBBoxHierarchy {

    /**
     * Per-rect tag used by Skia to distinguish draw ops from pure
     * state ops (`save` / `restore` / `setMatrix` / …). Currently
     * unused by [SkRTree] — the metadata-aware [insert] overload
     * defaults to forwarding to the rect-only one — but kept on the
     * API surface to mirror upstream and to support future
     * scope-aware culling (Skia's `FillBounds` collapses save / restore
     * blocks when the union of inner draws doesn't intersect Q).
     */
    public class Metadata(public val isDraw: Boolean)

    /**
     * Bulk-insert `n` bounding rectangles into the hierarchy. The
     * rectangles are indexed `0..n-1` ; [search] returns indices
     * into this exact ordering. Empty rects are skipped silently
     * (they can never intersect a non-empty query — matches upstream).
     *
     * Must be called **once** ; subsequent calls throw.
     */
    public abstract fun insert(rects: Array<SkRect>, n: Int)

    /**
     * Metadata-aware overload. Default impl ignores [metadata] and
     * forwards to the rect-only overload — subclasses can override
     * to honour `isDraw` flags (e.g. drop pure state-op bounds from
     * the index, or use them for save / restore scope culling).
     */
    public open fun insert(
        rects: Array<SkRect>,
        metadata: Array<Metadata>,
        n: Int,
    ) {
        insert(rects, n)
    }

    /**
     * Return the indices (into the `insert`-time ordering) of every
     * rectangle that intersects [query], in **insertion order**.
     * The list is always sorted ascending — callers that need to
     * replay records in original order can use it directly.
     *
     * An empty result is valid and means "no recorded op touches
     * [query]" ; callers can skip the playback walk entirely.
     */
    public abstract fun search(query: SkRect): IntArray

    /** Approximate heap footprint of the hierarchy in bytes. */
    public abstract val bytesUsed: Long
}
