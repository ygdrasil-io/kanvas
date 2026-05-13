package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * RRect-specific path-builder tests. Cover the type-dispatch table
 * (`kEmpty` / `kRect` / `kOval` / rounded variants), the verb stream
 * shape (`moveTo` + 4×(`lineTo` + `kConic`) + `close`), and the
 * direction symmetry (CW / CCW share start point and final close).
 */
class SkPathBuilderRRectTest {

    private val OVAL_CONIC_WEIGHT: Float = 0.707106781f

    @Test
    fun `empty rrect produces empty path`() {
        val rrect = SkRRect.MakeEmpty()
        val p = SkPathBuilder().addRRect(rrect).detach()
        assertTrue(p.isEmpty())
    }

    @Test
    fun `pure-rect rrect collapses to addRect`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val byRRect = SkPathBuilder().addRRect(SkRRect.MakeRect(rect)).detach()
        val byRect = SkPathBuilder().addRect(rect).detach()
        assertArrayEquals(byRect.verbs, byRRect.verbs)
        assertArrayEquals(byRect.coords, byRRect.coords)
    }

    @Test
    fun `pure-oval rrect collapses to addOval`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 50f)
        val byRRect = SkPathBuilder().addRRect(SkRRect.MakeOval(rect)).detach()
        val byOval = SkPathBuilder().addOval(rect).detach()
        assertArrayEquals(byOval.verbs, byRRect.verbs)
        assertArrayEquals(byOval.coords, byRRect.coords)
    }

    @Test
    fun `simple rrect emits moveTo plus 4 line-conic pairs plus close (Skia parity)`() {
        // Uniform 10-pixel corner radii on a 100×60 rect.
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 10f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        // Mirrors gRRectVerbs_LineStart in src/core/SkPathRawShapes.cpp:90-97
        // (1 move + 4 (line + conic) + 1 close = 10 verbs).
        val expected = arrayOf(
            SkPath.StorageVerb.kMove,
            SkPath.StorageVerb.kLine, SkPath.StorageVerb.kConic,   // top edge + TR corner
            SkPath.StorageVerb.kLine, SkPath.StorageVerb.kConic,   // right edge + BR corner
            SkPath.StorageVerb.kLine, SkPath.StorageVerb.kConic,   // bottom edge + BL corner
            SkPath.StorageVerb.kLine, SkPath.StorageVerb.kConic,   // left edge + TL corner
            SkPath.StorageVerb.kClose,
        )
        assertArrayEquals(expected, p.verbs)
        // 4 conic weights, all √2/2.
        assertEquals(4, p.conicWeights.size)
        for (w in p.conicWeights) assertEquals(OVAL_CONIC_WEIGHT, w, 1e-4f)
    }

    @Test
    fun `simple rrect CW starts at top-left corner end on top edge`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 10f)
        val p = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW).detach()
        // moveTo(left + tlRx, top) = (10, 0).
        assertEquals(10f, p.coords[0], 1e-4f)
        assertEquals(0f, p.coords[1], 1e-4f)
    }

    @Test
    fun `simple rrect CCW starts at the same point as CW`() {
        // The contour starts at the top-left corner's end-of-arc on the top
        // edge in both directions; the next verb is what differs.
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 10f)
        val cw = SkPathBuilder().addRRect(rrect, SkPathDirection.kCW).detach()
        val ccw = SkPathBuilder().addRRect(rrect, SkPathDirection.kCCW).detach()
        assertEquals(cw.coords[0], ccw.coords[0])
        assertEquals(cw.coords[1], ccw.coords[1])
        // CW: next verb is lineTo (top edge); CCW: next verb is conicTo (TL corner reversed).
        assertEquals(SkPath.StorageVerb.kLine, cw.verbs[1])
        assertEquals(SkPath.StorageVerb.kConic, ccw.verbs[1])
    }

    @Test
    fun `rrect conics carry the bbox corner as control and the next edge cardinal as end`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 20f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        // After moveTo(10, 0), top edge ends at (90, 0). Verify the first
        // conic (top-right corner): control at (right, top) = (100, 0),
        // end at (right, top + ry) = (100, 20). Per Skia's
        // SkPathRawShapes::set_as_rrect (LineStart variant).
        // Skip move (2 floats) + line (2 floats) → conic starts at coord index 4.
        // Conic consumes 4 floats: (Pc.x, Pc.y, Pe.x, Pe.y).
        val conicStart = 4
        assertEquals(100f, p.coords[conicStart],     1e-4f)
        assertEquals(0f,   p.coords[conicStart + 1], 1e-4f)
        assertEquals(100f, p.coords[conicStart + 2], 1e-4f)
        assertEquals(20f,  p.coords[conicStart + 3], 1e-4f)
        // Weight is √2/2.
        assertEquals(OVAL_CONIC_WEIGHT, p.conicWeights[0], 1e-4f)
    }

    @Test
    fun `complex rrect with per-corner radii uses each corner's own radii`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val radii = arrayOf(
            SkVector(5f,  5f),    // TL
            SkVector(10f, 10f),   // TR
            SkVector(15f, 15f),   // BR
            SkVector(20f, 20f),   // BL
        )
        val rrect = SkRRect.MakeRectRadii(rect, radii)
        val p = SkPathBuilder().addRRect(rrect).detach()
        // Start at (left + tlRx, top) = (5, 0).
        assertEquals(5f, p.coords[0], 1e-4f)
        assertEquals(0f, p.coords[1], 1e-4f)
        // First lineTo end is (right - trRx, top) = (90, 0).
        assertEquals(90f, p.coords[2], 1e-4f)
        assertEquals(0f,  p.coords[3], 1e-4f)
        // First conic: control = (right, top) = (100, 0), end = (right, top + trRy) = (100, 10).
        // Conic emits 4 floats (control then end); end lives at offsets 6..7.
        assertEquals(100f, p.coords[4], 1e-4f)
        assertEquals(0f,   p.coords[5], 1e-4f)
        assertEquals(100f, p.coords[6], 1e-4f)
        assertEquals(10f,  p.coords[7], 1e-4f)
    }

    @Test
    fun `SkPath RRect factory matches addRRect`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 50f, 50f), 8f, 8f)
        val a = SkPath.RRect(rrect)
        val b = SkPathBuilder().addRRect(rrect).detach()
        assertArrayEquals(a.verbs, b.verbs)
        assertArrayEquals(a.coords, b.coords)
    }

    @Test
    fun `addRRect on a Type-kRect input produces a 1-move-3-line-1-close polygon`() {
        // The Type kRect path skips emitRRectCorners and delegates to addRect,
        // which emits 1 move + 3 lines + 1 close (no cubics).
        val rrect = SkRRect.MakeRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val p = SkPathBuilder().addRRect(rrect).detach()
        assertEquals(5, p.verbs.size)
        assertEquals(SkPath.StorageVerb.kMove, p.verbs[0])
        assertTrue(p.verbs.drop(1).take(3).all { it == SkPath.StorageVerb.kLine })
        assertEquals(SkPath.StorageVerb.kClose, p.verbs[4])
    }
}
