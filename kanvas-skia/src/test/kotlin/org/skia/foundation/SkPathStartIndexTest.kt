package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * Slice 3.5 — `startIndex` overloads on `addRect` / `addOval` /
 * `addRRect`, plus the matching `SkPath.Rect` / `Oval` / `RRect`
 * factories. The 9-verb `ConicStart` rrect variant is also covered by
 * an updated `isRRect` detector.
 *
 * Mirrors `include/core/SkPathBuilder.h:716, 747, 759` and
 * `include/core/SkPath.h:89-100` plus
 * `src/core/SkPathRawShapes.cpp:48-160`.
 */
class SkPathStartIndexTest {

    private val rect = SkRect.MakeLTRB(0f, 0f, 100f, 60f)

    // --- addRect with startIndex ----------------------------------------

    @Test
    fun `addRect default produces same output as 2-arg overload`() {
        val a = SkPathBuilder().addRect(rect).detach()
        val b = SkPathBuilder().addRect(rect, SkPathDirection.kCW, 0).detach()
        assertArrayEquals(a.verbs, b.verbs)
        assertArrayEquals(a.coords, b.coords)
    }

    @Test
    fun `addRect with each startIndex 0_3 starts at the matching corner (CW)`() {
        // CW corners in index order: TL, TR, BR, BL.
        val expected = arrayOf(
            0f to 0f,     // index 0 — top-left
            100f to 0f,   // index 1 — top-right
            100f to 60f,  // index 2 — bottom-right
            0f to 60f,    // index 3 — bottom-left
        )
        for (i in 0..3) {
            val p = SkPathBuilder().addRect(rect, SkPathDirection.kCW, i).detach()
            assertEquals(expected[i].first, p.coords[0], 1e-4f, "x for startIndex=$i")
            assertEquals(expected[i].second, p.coords[1], 1e-4f, "y for startIndex=$i")
        }
    }

    @Test
    fun `addRect with startIndex preserves the 5-verb shape`() {
        for (i in 0..3) {
            val p = SkPathBuilder().addRect(rect, SkPathDirection.kCW, i).detach()
            assertArrayEquals(
                arrayOf(
                    SkPath.StorageVerb.kMove, SkPath.StorageVerb.kLine, SkPath.StorageVerb.kLine,
                    SkPath.StorageVerb.kLine, SkPath.StorageVerb.kClose,
                ),
                p.verbs, "verbs for startIndex=$i",
            )
        }
    }

    @Test
    fun `isRect detects every startIndex variant`() {
        for (i in 0..3) {
            val p = SkPathBuilder().addRect(rect, SkPathDirection.kCW, i).detach()
            assertNotNull(p.isRect(), "startIndex=$i")
        }
    }

    // --- addOval with startIndex ----------------------------------------

    @Test
    fun `addOval default startIndex 1 starts at right cardinal`() {
        val p = SkPathBuilder().addOval(rect).detach()
        // (oval.right, centerY) = (100, 30).
        assertEquals(100f, p.coords[0], 1e-4f)
        assertEquals(30f, p.coords[1], 1e-4f)
    }

    @Test
    fun `addOval with each startIndex 0_3 starts at the matching cardinal (CW)`() {
        val expected = arrayOf(
            50f to 0f,    // index 0 — top
            100f to 30f,  // index 1 — right
            50f to 60f,   // index 2 — bottom
            0f to 30f,    // index 3 — left
        )
        for (i in 0..3) {
            val p = SkPathBuilder().addOval(rect, SkPathDirection.kCW, i).detach()
            assertEquals(expected[i].first, p.coords[0], 1e-4f, "x for startIndex=$i")
            assertEquals(expected[i].second, p.coords[1], 1e-4f, "y for startIndex=$i")
        }
    }

    @Test
    fun `addOval verb stream is invariant across startIndex values`() {
        val expectedVerbs = arrayOf(
            SkPath.StorageVerb.kMove,
            SkPath.StorageVerb.kConic, SkPath.StorageVerb.kConic, SkPath.StorageVerb.kConic, SkPath.StorageVerb.kConic,
            SkPath.StorageVerb.kClose,
        )
        for (i in 0..3) {
            val p = SkPathBuilder().addOval(rect, SkPathDirection.kCW, i).detach()
            assertArrayEquals(expectedVerbs, p.verbs, "verbs for startIndex=$i")
        }
    }

    @Test
    fun `isOval detects every startIndex variant`() {
        for (i in 0..3) {
            val p = SkPathBuilder().addOval(rect, SkPathDirection.kCW, i).detach()
            val bounds = p.isOval()
            assertNotNull(bounds, "startIndex=$i")
            assertEquals(0f, bounds!!.left, 1e-4f); assertEquals(100f, bounds.right, 1e-4f)
        }
    }

    // --- addRRect with startIndex (LineStart) ---------------------------

    @Test
    fun `addRRect default startIndex 0 produces 10-verb LineStart`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        assertEquals(10, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kMove, p.verbs[0])
        assertEquals(SkPath.StorageVerb.kLine, p.verbs[1])
        assertEquals(SkPath.StorageVerb.kConic, p.verbs[2])
        assertEquals(SkPath.StorageVerb.kClose, p.verbs[9])
    }

    @Test
    fun `addRRect with even startIndex (CW) emits LineStart`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        for (i in intArrayOf(0, 2, 4, 6)) {
            val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, i).detach()
            assertEquals(10, p.verbs.size, "verbs.size for startIndex=$i")
            assertEquals(SkPath.StorageVerb.kLine, p.verbs[1], "first segment after move for startIndex=$i")
        }
    }

    @Test
    fun `addRRect with odd startIndex (CW) emits ConicStart 9-verb stream`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        for (i in intArrayOf(1, 3, 5, 7)) {
            val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, i).detach()
            assertEquals(9, p.verbs.size, "verbs.size for startIndex=$i (ConicStart)")
            assertEquals(SkPath.StorageVerb.kMove, p.verbs[0])
            assertEquals(SkPath.StorageVerb.kConic, p.verbs[1], "first segment after move for startIndex=$i")
            // Verify alternating: kMove, kConic, kLine, kConic, kLine, kConic, kLine, kConic, kClose.
            val expected = arrayOf(
                SkPath.StorageVerb.kMove,
                SkPath.StorageVerb.kConic, SkPath.StorageVerb.kLine,
                SkPath.StorageVerb.kConic, SkPath.StorageVerb.kLine,
                SkPath.StorageVerb.kConic, SkPath.StorageVerb.kLine,
                SkPath.StorageVerb.kConic,
                SkPath.StorageVerb.kClose,
            )
            assertArrayEquals(expected, p.verbs, "verbs for startIndex=$i")
        }
    }

    @Test
    fun `addRRect direction CCW with startIndex 0 emits ConicStart`() {
        // CCW + even startIndex = ConicStart (per Skia: starts-with-conic
        // when (idx & 1) == (dir == kCW)).
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCCW, 0).detach()
        assertEquals(9, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kConic, p.verbs[1])
    }

    // --- isRRect detector — both LineStart and ConicStart ---------------

    @Test
    fun `isRRect detects LineStart with all CW startIndex even values`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 12f)
        for (i in intArrayOf(0, 2, 4, 6)) {
            val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, i).detach()
            val rr = p.isRRect()
            assertNotNull(rr, "startIndex=$i")
            for (corner in SkRRect.Corner.entries) {
                val r = rr!!.radii(corner)
                assertEquals(10f, r.fX, 1e-3f, "rx corner=$corner startIndex=$i")
                assertEquals(12f, r.fY, 1e-3f, "ry corner=$corner startIndex=$i")
            }
        }
    }

    @Test
    fun `isRRect detects ConicStart with all CW startIndex odd values`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 12f)
        for (i in intArrayOf(1, 3, 5, 7)) {
            val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, i).detach()
            val rr = p.isRRect()
            assertNotNull(rr, "startIndex=$i")
            for (corner in SkRRect.Corner.entries) {
                val r = rr!!.radii(corner)
                assertEquals(10f, r.fX, 1e-3f, "rx corner=$corner startIndex=$i")
                assertEquals(12f, r.fY, 1e-3f, "ry corner=$corner startIndex=$i")
            }
        }
    }

    @Test
    fun `isRRect detects per-corner radii from any startIndex`() {
        val radii = arrayOf(
            SkVector(5f,  5f),    // TL
            SkVector(10f, 10f),   // TR
            SkVector(15f, 15f),   // BR
            SkVector(20f, 20f),   // BL
        )
        val rrect = SkRRect.MakeRectRadii(SkRect.MakeLTRB(0f, 0f, 100f, 100f), radii)
        for (i in 0..7) {
            val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, i).detach()
            val rr = p.isRRect()
            assertNotNull(rr, "startIndex=$i")
            assertEquals(5f,  rr!!.radii(SkRRect.Corner.kUpperLeft_Corner).fX,  1e-3f, "TL.x i=$i")
            assertEquals(10f, rr.radii(SkRRect.Corner.kUpperRight_Corner).fX,  1e-3f, "TR.x i=$i")
            assertEquals(15f, rr.radii(SkRRect.Corner.kLowerRight_Corner).fX, 1e-3f, "BR.x i=$i")
            assertEquals(20f, rr.radii(SkRRect.Corner.kLowerLeft_Corner).fX,  1e-3f, "BL.x i=$i")
        }
    }

    // --- SkPath.Rect / Oval / RRect factories with startIndex -----------

    @Test
    fun `SkPath Rect with fillType + startIndex matches addRect`() {
        val p = SkPath.Rect(rect, SkPathFillType.kEvenOdd, SkPathDirection.kCW, 2)
        val q = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd)
            .addRect(rect, SkPathDirection.kCW, 2).detach()
        assertEquals(SkPathFillType.kEvenOdd, p.fillType)
        assertArrayEquals(q.verbs, p.verbs)
        assertArrayEquals(q.coords, p.coords)
    }

    @Test
    fun `SkPath Oval with startIndex matches addOval`() {
        val p = SkPath.Oval(rect, SkPathDirection.kCW, 2)
        val q = SkPathBuilder().addOval(rect, SkPathDirection.kCW, 2).detach()
        assertArrayEquals(q.verbs, p.verbs)
        assertArrayEquals(q.coords, p.coords)
    }

    @Test
    fun `SkPath RRect with startIndex matches addRRect`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        val p = SkPath.RRect(rrect, SkPathDirection.kCW, 6)
        val q = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW, 6).detach()
        assertArrayEquals(q.verbs, p.verbs)
        assertArrayEquals(q.coords, p.coords)
    }

    // --- Default-arg backward compatibility -----------------------------

    @Test
    fun `existing single-arg addOval calls produce same output (no shift)`() {
        // Verifies that adding the new (rect, dir, startIndex) overload
        // didn't shift the default starting cardinal (= 1 = right).
        val a = SkPathBuilder().addOval(rect).detach()
        assertEquals(100f, a.coords[0], 1e-4f)
        assertEquals(30f, a.coords[1], 1e-4f)
    }

    @Test
    fun `existing single-arg addRRect calls keep startIndex 0 default`() {
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        val a = SkPathBuilder().addRRect(rrect).detach()
        // Default = 0 → LineStart, move at (l + tlRx, t) = (10, 0).
        assertEquals(10f, a.coords[0], 1e-4f)
        assertEquals(0f, a.coords[1], 1e-4f)
        assertEquals(SkPath.StorageVerb.kLine, a.verbs[1])
    }

    @Test
    fun `every CW startIndex preserves convex-region containment`() {
        // The contour visits all 4 bbox corners and 4+ cardinals in order,
        // so the bounding rect of the produced points should still equal
        // the source bbox regardless of startIndex / direction.
        val rrect = SkRRect.MakeRectXY(rect, 10f, 10f)
        for (dir in SkPathDirection.entries) {
            for (i in 0..7) {
                val p = SkPathBuilder().addRRect(rrect, dir, i).detach()
                val bounds = p.computeBounds()
                assertEquals(rect.left, bounds.left, 1e-4f, "left dir=$dir i=$i")
                assertEquals(rect.top, bounds.top, 1e-4f, "top dir=$dir i=$i")
                assertEquals(rect.right, bounds.right, 1e-4f, "right dir=$dir i=$i")
                assertEquals(rect.bottom, bounds.bottom, 1e-4f, "bottom dir=$dir i=$i")
            }
        }
    }
}
