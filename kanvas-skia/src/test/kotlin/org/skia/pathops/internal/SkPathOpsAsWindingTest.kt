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
