package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkRect

/**
 * Unit tests for [contourBounds] / [inParent] / [isFlatTree]
 * (Phase D1.2.h.6.3).
 */
class SkPathOpsAsWindingTest {

    @Test
    fun `contourBounds emits one entry per contour`() {
        val p = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
            .addRect(SkRect.MakeLTRB(20f, 20f, 30f, 30f))
            .detach()
        val contours = contourBounds(p)
        assertEquals(2, contours.size)
        assertEquals(0f, contours[0].bounds.left)
        assertEquals(10f, contours[0].bounds.right)
        assertEquals(20f, contours[1].bounds.left)
        assertEquals(30f, contours[1].bounds.right)
    }

    @Test
    fun `contourBounds is empty on an empty path`() {
        assertTrue(contourBounds(SkPathBuilder().detach()).isEmpty())
    }

    @Test
    fun `inParent flat — disjoint contours stay siblings`() {
        val a = AsWindingContour(SkRect.MakeLTRB(0f, 0f, 10f, 10f), 0, 0)
        val b = AsWindingContour(SkRect.MakeLTRB(20f, 20f, 30f, 30f), 0, 0)
        val root = AsWindingContour(SkRect.MakeEmpty(), 0, 0)
        inParent(a, root)
        inParent(b, root)
        assertEquals(2, root.children.size)
        assertTrue(a.children.isEmpty())
        assertTrue(b.children.isEmpty())
        assertTrue(isFlatTree(root))
    }

    @Test
    fun `inParent nested — outer contains inner`() {
        val outer = AsWindingContour(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 0, 0)
        val inner = AsWindingContour(SkRect.MakeLTRB(20f, 20f, 80f, 80f), 0, 0)
        val root = AsWindingContour(SkRect.MakeEmpty(), 0, 0)
        inParent(outer, root)
        inParent(inner, root)
        // Tree : root → outer → inner.
        assertEquals(1, root.children.size)
        assertSame(outer, root.children[0])
        assertEquals(1, outer.children.size)
        assertSame(inner, outer.children[0])
        assertFalse(isFlatTree(root))
    }

    // ─── getDirection (D1.2.h.6.4) ──────────────────────────────
    //
    // Note : the upstream `Contour::Direction` enum is the
    // mathematical direction (Y-up convention), while
    // `SkPathDirection.kCW` describes the visual screen-coord
    // direction (Y-down). The two are flipped : a `addRect(kCW)`
    // tracing visually clockwise in screen coords plots
    // mathematically counter-clockwise on a Y-up plot, so
    // [getDirection] returns [AsWindingDirection.kCCW] for such a
    // rect. This matches `OpAsWinding::getDirection`'s
    // `total_signed_area < 0 ? kCCW : kCW` convention.

    @Test
    fun `getDirection on visual-CW rect returns kCCW (math direction)`() {
        val p = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), org.skia.foundation.SkPathDirection.kCW)
            .detach()
        val contours = contourBounds(p)
        assertEquals(1, contours.size)
        assertEquals(AsWindingDirection.kCCW, getDirection(p, contours[0]))
    }

    @Test
    fun `getDirection on visual-CCW rect returns kCW (math direction)`() {
        val p = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), org.skia.foundation.SkPathDirection.kCCW)
            .detach()
        val contours = contourBounds(p)
        assertEquals(1, contours.size)
        assertEquals(AsWindingDirection.kCW, getDirection(p, contours[0]))
    }

    @Test
    fun `getDirection ignores other contours via verbStart-verbEnd`() {
        // Two contours : first visual-CW (math kCCW), second visual-CCW (math kCW).
        val p = SkPathBuilder()
            .addRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), org.skia.foundation.SkPathDirection.kCW)
            .addRect(SkRect.MakeLTRB(20f, 20f, 80f, 80f), org.skia.foundation.SkPathDirection.kCCW)
            .detach()
        val contours = contourBounds(p)
        assertEquals(2, contours.size)
        assertEquals(AsWindingDirection.kCCW, getDirection(p, contours[0]))
        assertEquals(AsWindingDirection.kCW, getDirection(p, contours[1]))
    }

    // ─── containsEdge / leftEdge / nextEdge (D1.2.h.6.5) ─────────

    @Test
    fun `leftEdge on a horizontal line picks the smaller-X endpoint`() {
        val rec = AsWindingVerbRec(
            org.skia.foundation.SkPath.Verb.kLine,
            arrayOf(org.skia.math.SkPoint(10f, 5f), org.skia.math.SkPoint(0f, 5f)),
            1f, 0,
        )
        val lp = leftEdge(rec)
        assertEquals(0f, lp.fX)
    }

    @Test
    fun `nextEdge kInitial sets contour minXY to leftmost edge point`() {
        // 4-edge quadrilateral with all edges explicit.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f).lineTo(100f, 100f).lineTo(0f, 100f).lineTo(0f, 0f)
            .close()
            .detach()
        val contours = contourBounds(p)
        assertEquals(1, contours.size)
        // Reset minXY to ScalarMax so kInitial mode populates it.
        contours[0].minXY = org.skia.math.SkPoint(AsWindingContour.SK_ScalarMax,
                                                    AsWindingContour.SK_ScalarMax)
        nextEdge(p, contours[0], AsWindingEdge.kInitial)
        // Smallest X is 0 ; first non-horizontal edge with X=0 is the
        // (0,100) → (0,0) closing line, whose top point is (0, 0)
        // (after the smaller-X-then-Y tie-break).
        assertEquals(0f, contours[0].minXY.fX)
    }

    // ─── reverseAddPath / reverseMarkedContours (D1.2.h.6.6) ───────

    @Test
    fun `reverseAddPath flips a 4-line polygon's verb order`() {
        val src = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f).lineTo(10f, 10f).lineTo(0f, 10f).lineTo(0f, 0f)
            .close()
            .detach()
        val builder = SkPathBuilder()
        reverseAddPath(builder, src)
        val r = builder.detach()
        // Expect : moveTo(start) + 4 lineTo (in reverse order) + close.
        // First verb is kMove, second-to-last verb (before close) is the
        // line back to (0, 0).
        assertEquals(org.skia.foundation.SkPath.Verb.kMove, r.verbs[0])
        // Last point (in reversed walk) is the original moveTo (0, 0)
        // — which is the **first** lineTo target after the moveTo of the
        // reversed path. Concretely the reversed sequence is :
        // moveTo(0,0) → lineTo(0,10) → lineTo(10,10) → lineTo(10,0) → lineTo(0,0) → close.
        val moves = r.verbs.count { it == org.skia.foundation.SkPath.Verb.kMove }
        val lines = r.verbs.count { it == org.skia.foundation.SkPath.Verb.kLine }
        assertEquals(1, moves)
        assertEquals(4, lines)
    }

    @Test
    fun `reverseAddPath is a no-op on empty input`() {
        val empty = SkPathBuilder().detach()
        val builder = SkPathBuilder()
        reverseAddPath(builder, empty)
        val r = builder.detach()
        org.junit.jupiter.api.Assertions.assertTrue(r.isEmpty())
    }

    @Test
    fun `containerContains — outer 4-line rect contains inner rect`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(100f, 0f).lineTo(100f, 100f).lineTo(0f, 100f).lineTo(0f, 0f)
            .close()
            .moveTo(20f, 20f)
            .lineTo(80f, 20f).lineTo(80f, 80f).lineTo(20f, 80f).lineTo(20f, 20f)
            .close()
            .detach()
        val contours = contourBounds(p)
        assertEquals(2, contours.size)
        assertTrue(containerContains(p, contours[0], contours[1]))
        assertTrue(contours[1].contained)
    }

    @Test
    fun `inParent promotion — inner inserted before outer gets re-parented`() {
        // Same setup as nested, but insert inner first. inParent
        // should relocate inner under outer when outer is added.
        val outer = AsWindingContour(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 0, 0)
        val inner = AsWindingContour(SkRect.MakeLTRB(20f, 20f, 80f, 80f), 0, 0)
        val root = AsWindingContour(SkRect.MakeEmpty(), 0, 0)
        inParent(inner, root)
        inParent(outer, root)
        assertEquals(1, root.children.size)
        assertSame(outer, root.children[0])
        assertSame(inner, outer.children[0])
    }
}
