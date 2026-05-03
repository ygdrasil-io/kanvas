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
 * shape (`moveTo` + 4×(`lineTo` + `cubicTo`) + `close`), and the
 * direction symmetry (CW / CCW share start point and final close).
 */
class SkPathBuilderRRectTest {

    private val OVAL_KAPPA: Float = 0.5522847498307933f

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
    fun `simple rrect emits moveTo plus 4 line-cubic pairs plus close`() {
        // Uniform 10-pixel corner radii on a 100×60 rect.
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 10f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        // 1 move + 4 (line + cubic) + 1 close = 10 verbs.
        val expected = arrayOf(
            SkPath.Verb.kMove,
            SkPath.Verb.kLine, SkPath.Verb.kCubic,   // top edge + TR corner
            SkPath.Verb.kLine, SkPath.Verb.kCubic,   // right edge + BR corner
            SkPath.Verb.kLine, SkPath.Verb.kCubic,   // bottom edge + BL corner
            SkPath.Verb.kLine, SkPath.Verb.kCubic,   // left edge + TL corner
            SkPath.Verb.kClose,
        )
        assertArrayEquals(expected, p.verbs)
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
        // CW: next verb is lineTo (top edge); CCW: next verb is cubicTo (TL corner reversed).
        assertEquals(SkPath.Verb.kLine, cw.verbs[1])
        assertEquals(SkPath.Verb.kCubic, ccw.verbs[1])
    }

    @Test
    fun `rrect cubics use the kappa approximation for the corner radii`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 60f), 10f, 20f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        // After moveTo(10, 0), top edge ends at (90, 0). Verify the first
        // cubic (top-right corner) emits the expected control points.
        // Cubic: P1 = (right - rx*(1-k), top)        = (100 - 10*(1-k), 0)
        //        P2 = (right, top + ry*(1-k))        = (100,         20*(1-k))
        //        P3 = (right, top + ry)              = (100, 20)
        val k = OVAL_KAPPA
        // Skip move (2 floats) + line (2 floats) → cubic starts at coord index 4.
        // Cubic consumes 6 floats: (P1.x, P1.y, P2.x, P2.y, P3.x, P3.y).
        val cubicStart = 4
        assertEquals(100f - 10f * (1f - k), p.coords[cubicStart],     1e-4f)
        assertEquals(0f,                     p.coords[cubicStart + 1], 1e-4f)
        assertEquals(100f,                   p.coords[cubicStart + 2], 1e-4f)
        assertEquals(20f * (1f - k),         p.coords[cubicStart + 3], 1e-4f)
        assertEquals(100f,                   p.coords[cubicStart + 4], 1e-4f)
        assertEquals(20f,                    p.coords[cubicStart + 5], 1e-4f)
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
        // First cubic ends at (right, top + trRy) = (100, 10).
        // Cubic emits 6 floats; P3 lives at offsets 8..9 from coord index 0.
        assertEquals(100f, p.coords[8], 1e-4f)
        assertEquals(10f,  p.coords[9], 1e-4f)
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
        assertEquals(SkPath.Verb.kMove, p.verbs[0])
        assertTrue(p.verbs.drop(1).take(3).all { it == SkPath.Verb.kLine })
        assertEquals(SkPath.Verb.kClose, p.verbs[4])
    }
}
