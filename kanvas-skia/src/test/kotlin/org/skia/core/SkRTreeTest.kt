package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect
import kotlin.random.Random

/**
 * Q3.1 verification suite for the bounding-box hierarchy ports
 * [SkBBoxHierarchy] / [SkRTree] / [SkRTreeFactory].
 *
 * **Parity contract** with upstream's
 * [`RTreeTest.cpp`](https://github.com/google/skia/blob/main/tests/RTreeTest.cpp) :
 *  - 200 random non-empty rects in `[0, 1000]²`, bulk-inserted at once.
 *  - 50 random query rects ; for each query, the tree's hit set must
 *    equal the brute-force "intersect every input rect" answer
 *    (sorted ascending by insertion order).
 *  - Tree depth must lie in `[depthMin, depthMax]` where the bounds
 *    are derived from `kMinChildren` / `kMaxChildren`.
 *  - 100 iterations to catch flaky bulk-load bugs.
 *
 * Plus targeted tests for the API surface that upstream tests in
 * other suites :
 *  - empty insert → empty search
 *  - single-rect insert → exactly that op-index returns
 *  - exhaustively empty rects skipped → count matches non-empty
 *  - factory parity → `SkRTreeFactory.create()` returns a fresh
 *    [SkRTree] each time, and `SkRTreeFactory()` syntax compiles
 *    via the SAM-interface companion (sanity check).
 */
class SkRTreeTest {

    // ─── Random-rect parity with upstream RTreeTest ───────────────────

    private fun randomRect(rng: Random): SkRect {
        // Same domain as upstream : `[0, 1000]²`, non-empty (sort the
        // four corners and reject zero-area rects).
        while (true) {
            val a = rng.nextFloat() * 1000f
            val b = rng.nextFloat() * 1000f
            val c = rng.nextFloat() * 1000f
            val d = rng.nextFloat() * 1000f
            val l = minOf(a, b)
            val r = maxOf(a, b)
            val t = minOf(c, d)
            val bot = maxOf(c, d)
            if (l < r && t < bot) return SkRect.MakeLTRB(l, t, r, bot)
        }
    }

    private fun bruteForceQuery(query: SkRect, rects: Array<SkRect>): IntArray {
        val out = ArrayList<Int>()
        for (i in rects.indices) {
            if (query.intersects(rects[i])) out.add(i)
        }
        return out.toIntArray()
    }

    @Test
    fun `100 iterations of 200 random rects with 50 queries each match brute force`() {
        val n = 200
        val nQueries = 50
        // Stable seed so flakes are reproducible.
        val rng = Random(0xC0FFEEL)
        for (iter in 0 until 100) {
            val rects = Array(n) { randomRect(rng) }
            val tree = SkRTree()
            tree.insert(rects, n)

            assertEquals(n, tree.countInserted, "iter $iter: every random rect was non-empty")
            for (q in 0 until nQueries) {
                val query = randomRect(rng)
                val hits = tree.search(query)
                val expected = bruteForceQuery(query, rects)
                assertTrue(
                    hits.contentEquals(expected),
                    "iter $iter, query $q : RTree hits ${hits.toList()} ≠ brute ${expected.toList()}",
                )
            }
        }
    }

    @Test
    fun `tree depth lands inside the kMinChildren-kMaxChildren envelope`() {
        // For N inserts and branch factor b, depth ∈ [⌈log_kMax N⌉, ⌈log_kMin N⌉].
        val n = 200
        val rng = Random(0xDEADBEEFL)
        val rects = Array(n) { randomRect(rng) }
        val tree = SkRTree()
        tree.insert(rects, n)

        // Compute the upstream-style depth bounds.
        var minDepth = -1
        var rem = n
        while (rem > 0) {
            rem -= Math.pow(SkRTree.kMaxChildren.toDouble(), (minDepth + 1).toDouble()).toInt()
            minDepth++
        }
        var maxDepth = -1
        rem = n
        while (rem > 0) {
            rem -= Math.pow(SkRTree.kMinChildren.toDouble(), (maxDepth + 1).toDouble()).toInt()
            maxDepth++
        }
        val d = tree.depth
        assertTrue(d in minDepth..maxDepth, "depth $d out of [$minDepth, $maxDepth]")
    }

    // ─── Edge cases ────────────────────────────────────────────────────

    @Test
    fun `empty insert yields empty search and depth 0`() {
        val tree = SkRTree()
        tree.insert(emptyArray(), 0)
        assertEquals(0, tree.countInserted)
        assertEquals(0, tree.depth)
        assertEquals(
            0,
            tree.search(SkRect.MakeLTRB(0f, 0f, 100f, 100f)).size,
            "empty tree must produce no hits",
        )
    }

    @Test
    fun `single-rect insert returns op-index 0 on hit, empty on miss`() {
        val tree = SkRTree()
        val rect = SkRect.MakeLTRB(10f, 10f, 50f, 50f)
        tree.insert(arrayOf(rect), 1)

        assertEquals(1, tree.countInserted)
        // Hit
        val hitsInside = tree.search(SkRect.MakeLTRB(20f, 20f, 30f, 30f))
        assertTrue(hitsInside.contentEquals(intArrayOf(0)))
        // Miss — no overlap
        val hitsMiss = tree.search(SkRect.MakeLTRB(100f, 100f, 200f, 200f))
        assertEquals(0, hitsMiss.size)
    }

    @Test
    fun `empty rects are silently skipped from the index`() {
        // 4 rects, 2 of which are empty (zero-area). The tree must
        // index only the 2 real ones and getCount must reflect that.
        val rects = arrayOf(
            SkRect.MakeLTRB(0f, 0f, 0f, 0f),       // empty (zero-area)
            SkRect.MakeLTRB(10f, 10f, 50f, 50f),   // real
            SkRect.MakeLTRB(100f, 100f, 100f, 200f), // empty (zero width)
            SkRect.MakeLTRB(200f, 200f, 300f, 300f), // real
        )
        val tree = SkRTree()
        tree.insert(rects, 4)
        assertEquals(2, tree.countInserted)

        // Query covering both real rects.
        val hits = tree.search(SkRect.MakeLTRB(0f, 0f, 1000f, 1000f))
        assertTrue(
            hits.contentEquals(intArrayOf(1, 3)),
            "expected indices 1, 3 (the non-empty rects), got ${hits.toList()}",
        )
    }

    @Test
    fun `search results are sorted ascending by insertion order`() {
        // 30 rects in a tight grid — every query overlaps many. The tree
        // must return them sorted by insertion index, never permuted by
        // bulk-load grouping.
        val rects = Array(30) { i ->
            SkRect.MakeLTRB(
                i.toFloat(), 0f,
                i.toFloat() + 10f, 100f,
            )
        }
        val tree = SkRTree()
        tree.insert(rects, 30)

        // Query covering all 30.
        val all = tree.search(SkRect.MakeLTRB(0f, 0f, 1000f, 100f))
        assertEquals(30, all.size)
        for (i in 0 until 30) assertEquals(i, all[i], "sorted-order parity at $i")
    }

    @Test
    fun `double-insert is rejected`() {
        val tree = SkRTree()
        tree.insert(arrayOf(SkRect.MakeLTRB(0f, 0f, 10f, 10f)), 1)
        var caught = false
        try {
            tree.insert(arrayOf(SkRect.MakeLTRB(0f, 0f, 10f, 10f)), 1)
        } catch (e: IllegalStateException) {
            caught = true
        }
        assertTrue(caught, "second insert must throw IllegalStateException")
    }

    @Test
    fun `bytesUsed grows with insertion count`() {
        val small = SkRTree().also {
            it.insert(arrayOf(SkRect.MakeLTRB(0f, 0f, 10f, 10f)), 1)
        }
        val rng = Random(42)
        val big = SkRTree().also {
            it.insert(Array(200) { _ -> randomRect(rng) }, 200)
        }
        assertTrue(big.bytesUsed > small.bytesUsed, "big tree must use more bytes")
    }

    // ─── Factory ───────────────────────────────────────────────────────

    @Test
    fun `SkRTreeFactory yields a fresh SkRTree each call`() {
        val a = SkRTreeFactory.create()
        val b = SkRTreeFactory.create()
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a is SkRTree)
        assertTrue(b is SkRTree)
        assertFalse(a === b, "factory must yield independent instances")
    }

    @Test
    fun `SkBBHFactory SAM lambda compiles and works`() {
        // The fun-interface allows lambda use ; verify a custom
        // factory that always returns the same global tree compiles
        // and is callable.
        val shared = SkRTree()
        val factory: SkBBHFactory = SkBBHFactory { shared }
        assertTrue(factory.create() === shared, "lambda factory must return the captured instance")
    }
}
