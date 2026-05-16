package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Slice 3.6 — geometry helpers on `SkPath` (Skia parity):
 * `computeTightBounds`, `makeScale`, `tryMakeTransform` /
 * `tryMakeOffset` / `tryMakeScale`, plus the static
 * `IsLineDegenerate` / `IsQuadDegenerate` / `IsCubicDegenerate`
 * predicates. All read-only / immutable; no rendering paths touched.
 */
class SkPathGeometryHelpersTest {

    // --- computeTightBounds --------------------------------------------

    @Test
    fun `tight bounds on empty path is the origin rect`() {
        val empty = SkPathBuilder().detach()
        val b = empty.computeTightBounds()
        assertEquals(0f, b.left); assertEquals(0f, b.top)
        assertEquals(0f, b.right); assertEquals(0f, b.bottom)
    }

    @Test
    fun `tight bounds matches getBounds for line-only paths`() {
        val p = SkPath.Polygon(arrayOf(0f to 0f, 10f to 5f, 5f to 10f), isClosed = true)
        val tight = p.computeTightBounds()
        val fast = p.computeBounds()
        assertEquals(fast.left, tight.left, 1e-4f)
        assertEquals(fast.top, tight.top, 1e-4f)
        assertEquals(fast.right, tight.right, 1e-4f)
        assertEquals(fast.bottom, tight.bottom, 1e-4f)
    }

    @Test
    fun `tight bounds on a quadratic excludes the control point bulge`() {
        // Quad with control point (50, 1000) — peak of curve at t=0.5 is y=500,
        // not 1000. computeTightBounds must report 500, not 1000.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(50f, 1000f, 100f, 0f)
            .detach()
        val tight = p.computeTightBounds()
        assertEquals(0f, tight.left, 1e-4f)
        assertEquals(100f, tight.right, 1e-4f)
        assertEquals(0f, tight.top, 1e-4f)
        // Curve peak at t=0.5: y = (1−0.5)²·0 + 2·0.5·0.5·1000 + 0.5²·0 = 500.
        assertEquals(500f, tight.bottom, 1e-4f)
        // Conservative bounds should still go all the way up to 1000.
        assertEquals(1000f, p.computeBounds().bottom, 1e-4f)
    }

    @Test
    fun `tight bounds on a cubic excludes the control point bulge`() {
        // Cubic 0,0 → 100,0 with controls (0, 1000), (100, 1000).
        // Curve peak at t=0.5 is 3/4 · 1000 = 750.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, 1000f, 100f, 1000f, 100f, 0f)
            .detach()
        val tight = p.computeTightBounds()
        assertEquals(0f, tight.top, 1e-4f)
        assertEquals(750f, tight.bottom, 1e-3f)
        // Conservative bounds reach 1000.
        assertEquals(1000f, p.computeBounds().bottom, 1e-4f)
    }

    @Test
    fun `tight bounds on an oval matches the bounding rect`() {
        // For a quarter-arc conic with weight √2/2 and control at the bbox
        // corner, the curve stays within (start, end, control) bbox and
        // touches the bbox edges tangentially at endpoints — i.e. tight
        // bounds == control-point bounds == requested ellipse rect.
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 60f)
        val p = SkPathBuilder().addOval(rect).detach()
        val tight = p.computeTightBounds()
        assertEquals(0f, tight.left, 1e-3f)
        assertEquals(0f, tight.top, 1e-3f)
        assertEquals(100f, tight.right, 1e-3f)
        assertEquals(60f, tight.bottom, 1e-3f)
    }

    // --- makeScale ------------------------------------------------------

    @Test
    fun `makeScale stretches every coord`() {
        val src = SkPathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).detach()
        val scaled = src.makeScale(10f, 100f)
        assertEquals(10f, scaled.coords[0], 1e-4f)
        assertEquals(200f, scaled.coords[1], 1e-4f)
        assertEquals(30f, scaled.coords[2], 1e-4f)
        assertEquals(400f, scaled.coords[3], 1e-4f)
    }

    @Test
    fun `makeScale preserves verbs and conic weights`() {
        val src = SkPathBuilder()
            .moveTo(0f, 0f).conicTo(10f, 0f, 10f, 10f, 0.7f)
            .detach()
        val scaled = src.makeScale(2f, 3f)
        assertTrue(src.verbs contentEquals scaled.verbs)
        assertEquals(0.7f, scaled.conicWeights[0], 1e-4f)
    }

    // --- tryMakeTransform / tryMakeOffset / tryMakeScale ----------------

    @Test
    fun `tryMakeOffset on a finite path returns a finite copy`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        val moved = src.tryMakeOffset(5f, 7f)
        assertNotNull(moved)
        assertTrue(moved!!.isFinite())
        assertEquals(5f, moved.coords[0], 1e-4f)
    }

    @Test
    fun `tryMakeOffset returns null when the result would be non-finite`() {
        // Path coord = MAX_VALUE; offset by MAX_VALUE → +∞.
        val src = SkPathBuilder().moveTo(Float.MAX_VALUE, 0f).lineTo(0f, 0f).detach()
        assertNull(src.tryMakeOffset(Float.MAX_VALUE, 0f))
    }

    @Test
    fun `tryMakeScale returns null on overflow`() {
        val src = SkPathBuilder().moveTo(Float.MAX_VALUE, 0f).detach()
        assertNull(src.tryMakeScale(2f, 1f))
    }

    @Test
    fun `tryMakeTransform with NaN matrix returns null`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        val nanMatrix = SkMatrix(sx = Float.NaN)
        assertNull(src.tryMakeTransform(nanMatrix))
    }

    @Test
    fun `tryMakeTransform identity returns same instance`() {
        val src = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        // Identity short-circuits in makeTransform → returns `this`. tryMakeTransform
        // wraps that with a finite check, which passes for a finite source.
        val out = src.tryMakeTransform(SkMatrix.Identity)
        assertSame(src, out)
    }

    // --- Is*Degenerate static predicates --------------------------------

    @Test
    fun `IsLineDegenerate exact only matches bit-equal endpoints`() {
        val p = SkPoint(1f, 2f); val q = SkPoint(1f, 2f)
        val r = SkPoint(1.0001f, 2f)
        assertTrue(SkPath.IsLineDegenerate(p, q, exact = true))
        assertFalse(SkPath.IsLineDegenerate(p, r, exact = true))
    }

    @Test
    fun `IsLineDegenerate non-exact uses NearlyZero tolerance`() {
        val p = SkPoint(0f, 0f)
        val q = SkPoint(1f / 8192f, 0f)   // < 1/4096 → nearly-equal
        assertTrue(SkPath.IsLineDegenerate(p, q, exact = false))
        val r = SkPoint(1f, 0f)
        assertFalse(SkPath.IsLineDegenerate(p, r, exact = false))
    }

    @Test
    fun `IsQuadDegenerate detects collinear collapsed quads`() {
        val a = SkPoint(0f, 0f)
        val b = SkPoint(0f, 0f)
        val c = SkPoint(0f, 0f)
        assertTrue(SkPath.IsQuadDegenerate(a, b, c, exact = true))
        // Move third point apart — no longer degenerate.
        val d = SkPoint(10f, 0f)
        assertFalse(SkPath.IsQuadDegenerate(a, b, d, exact = true))
    }

    @Test
    fun `IsCubicDegenerate detects fully collapsed cubics`() {
        val z = SkPoint(0f, 0f)
        assertTrue(SkPath.IsCubicDegenerate(z, z, z, z, exact = true))
        val far = SkPoint(100f, 0f)
        assertFalse(SkPath.IsCubicDegenerate(z, z, z, far, exact = true))
    }

    @Test
    fun `IsQuadDegenerate non-exact tolerance handles sub-NearlyZero noise`() {
        val a = SkPoint(0f, 0f)
        val b = SkPoint(1f / 8192f, 0f)
        val c = SkPoint(2f / 8192f, 0f)
        assertTrue(SkPath.IsQuadDegenerate(a, b, c, exact = false))
    }
}
