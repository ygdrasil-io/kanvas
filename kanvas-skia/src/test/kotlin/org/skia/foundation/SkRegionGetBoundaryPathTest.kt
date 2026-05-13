package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIRect

/**
 * R2.18 — covers `SkRegion.addBoundaryPath` and `getBoundaryPath`.
 *
 * The simple impl iterates over the region's rectangles and emits an
 * `addRect` per piece ; the resulting path's verb stream is therefore
 * `(kMove + 3×kLine + kClose) × N` where `N` is the rect count
 * (= `computeRegionComplexity`).
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
        assertEquals(SkPath.StorageVerb.kMove, path.verbs[0])
        for (i in 1..3) assertEquals(SkPath.StorageVerb.kLine, path.verbs[i])
        assertEquals(SkPath.StorageVerb.kClose, path.verbs[4])
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
        val moveCount = path.verbs.count { it == SkPath.StorageVerb.kMove }
        val lineCount = path.verbs.count { it == SkPath.StorageVerb.kLine }
        val closeCount = path.verbs.count { it == SkPath.StorageVerb.kClose }
        assertEquals(3, moveCount)
        assertEquals(9, lineCount)
        assertEquals(3, closeCount)
    }

    @Test
    fun `getBoundaryPath on empty region returns empty path`() {
        assertTrue(SkRegion().getBoundaryPath().isEmpty())
    }
}
