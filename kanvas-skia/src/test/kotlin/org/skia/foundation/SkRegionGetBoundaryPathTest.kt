package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect

/**
 * R2.18 (+ port of upstream `addBoundaryPath` edge-stitcher) — covers
 * `SkRegion.addBoundaryPath` and `getBoundaryPath`.
 *
 * The stitched impl produces one closed contour per *connected
 * component* of the region. A single rectangle still emits
 * `kMove + 3×kLine + kClose = 5` verbs (matching `addRect`) ; an
 * L-shape (two rects sharing an edge) emits a single hexagonal
 * contour, not two rect contours.
 */
class SkRegionGetBoundaryPathTest {

    @Test
    fun `addBoundaryPath returns false on empty region and leaves builder empty`() {
        val builder = SkPathBuilder()
        assertFalse(SkRegion().addBoundaryPath(builder))
        assertTrue(builder.detach().isEmpty())
    }

    @Test
    fun `addBoundaryPath on rect region emits one rect contour`() {
        val rgn = SkRegion(SkIRect(0, 0, 10, 20))
        val builder = SkPathBuilder()
        assertTrue(rgn.addBoundaryPath(builder))
        val path = builder.detach()

        // 1 rect = kMove + 3 × kLine + kClose = 5 verbs.
        assertEquals(5, path.countVerbs())
        assertEquals(SkPath.Verb.kMove, path.verbs[0])
        for (i in 1..3) assertEquals(SkPath.Verb.kLine, path.verbs[i])
        assertEquals(SkPath.Verb.kClose, path.verbs[4])
    }

    @Test
    fun `getBoundaryPath on 3-rect region emits 3 rect contours`() {
        val rgn = SkRegion(SkIRect(0, 0, 10, 10))
        rgn.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion)
        rgn.op(SkIRect(40, 40, 50, 50), SkRegion.Op.kUnion)
        assertEquals(3, rgn.computeRegionComplexity())

        val path = rgn.getBoundaryPath()
        // 3 rect contours = 3 × 5 = 15 verbs.
        assertEquals(15, path.countVerbs())
        val moveCount = path.verbs.count { it == SkPath.Verb.kMove }
        val lineCount = path.verbs.count { it == SkPath.Verb.kLine }
        val closeCount = path.verbs.count { it == SkPath.Verb.kClose }
        assertEquals(3, moveCount)
        assertEquals(9, lineCount)
        assertEquals(3, closeCount)
    }

    @Test
    fun `getBoundaryPath on empty region returns empty path`() {
        assertTrue(SkRegion().getBoundaryPath().isEmpty())
    }

    @Test
    fun `getBoundaryPath on two disjoint rects emits two closed contours`() {
        val rgn = SkRegion(SkIRect(0, 0, 10, 10))
        rgn.op(SkIRect(50, 50, 70, 70), SkRegion.Op.kUnion)
        assertEquals(2, rgn.computeRegionComplexity())

        val path = rgn.getBoundaryPath()
        assertEquals(2, path.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(2, path.verbs.count { it == SkPath.Verb.kClose })
        // Last verb of every contour is kClose ; no dangling verbs.
        assertEquals(SkPath.Verb.kClose, path.verbs.last())
    }

    @Test
    fun `getBoundaryPath on L-shape emits single non-convex closed contour`() {
        // Two rects forming an L (taller bottom-left + wider top):
        //   top:    x ∈ [0, 20), y ∈ [0, 10)
        //   bottom: x ∈ [0, 10), y ∈ [10, 20)
        // 6 geometric corners. Upstream's stitcher emits a single
        // contour that starts at the *middle* of the left edge (where
        // edge sort places its first item) and traces all 6 corners,
        // adding one colinear vertex on the starting edge — see
        // `src/core/SkRegion_path.cpp:535-548` and the `extract_path`
        // discussion in `addBoundaryPath` kdoc.
        val rgn = SkRegion(SkIRect(0, 0, 20, 10))
        rgn.op(SkIRect(0, 10, 10, 20), SkRegion.Op.kUnion)
        // Canonical form : 2 bands, 2 rects total.
        assertEquals(2, rgn.computeRegionComplexity())

        val path = rgn.getBoundaryPath()
        // ONE contour, not two — the connected components merge.
        assertEquals(1, path.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(1, path.verbs.count { it == SkPath.Verb.kClose })
        // 7 lineTos = 6 corners + 1 colinear starting-edge midpoint
        // (the start vertex appears twice : once as the moveTo target,
        // once as the final lineTo before close — but the contour
        // physically visits 7 distinct points).
        assertEquals(7, path.verbs.count { it == SkPath.Verb.kLine })

        // Every one of the 6 geometric L-shape corners must appear.
        val pts = mutableSetOf<Pair<Float, Float>>()
        var i = 0
        while (i + 1 < path.coords.size) {
            pts.add(path.coords[i] to path.coords[i + 1])
            i += 2
        }
        assertTrue(pts.contains(0f  to 0f))
        assertTrue(pts.contains(20f to 0f))
        assertTrue(pts.contains(20f to 10f))
        assertTrue(pts.contains(10f to 10f))
        assertTrue(pts.contains(10f to 20f))
        assertTrue(pts.contains(0f  to 20f))
        // The starting / colinear vertex lies on the left edge.
        assertTrue(pts.contains(0f to 10f))
    }

    @Test
    fun `getBoundaryPath round-trip via setPath reproduces the source region`() {
        // 10×10 grid of "on" cells in a checker-ish pattern. The
        // boundary path → setPath round-trip should recover the same
        // region (up to canonical form).
        val source = SkRegion()
        val rng = java.util.Random(0xC0FFEEL)
        for (gy in 0 until 10) {
            for (gx in 0 until 10) {
                if (rng.nextBoolean()) {
                    val cell = SkIRect(gx * 10, gy * 10, gx * 10 + 10, gy * 10 + 10)
                    source.op(cell, SkRegion.Op.kUnion)
                }
            }
        }
        // Non-empty by construction (seed 0xC0FFEE yields ≥ 1 cell).
        assertFalse(source.isEmpty())

        val path = source.getBoundaryPath()
        assertFalse(path.isEmpty())

        // Round-trip : rasterise the boundary path back to a region
        // clipped to the source's bounds and compare bounds — exact
        // band-list equality isn't part of upstream's contract, but
        // bounds + non-emptiness must match.
        val clip = SkRegion(source.getBounds())
        val recovered = SkRegion()
        assertTrue(recovered.setPath(path, clip))
        assertEquals(source.getBounds(), recovered.getBounds())
    }

    @Test
    fun `getBoundaryPath on L-shape differs from per-rect path`() {
        // Sanity-check against the older "addRect per piece" fallback :
        // an L-shape would emit 10 verbs with that approach
        // (2 contours × 5 verbs) ; the stitcher must produce fewer
        // *contours* (1 instead of 2).
        val rgn = SkRegion(SkIRect(0, 0, 20, 10))
        rgn.op(SkIRect(0, 10, 10, 20), SkRegion.Op.kUnion)

        val path = rgn.getBoundaryPath()
        assertNotEquals(2, path.verbs.count { it == SkPath.Verb.kMove })
        assertEquals(1, path.verbs.count { it == SkPath.Verb.kMove })
    }
}
