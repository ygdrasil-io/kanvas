package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkPoint
import kotlin.math.abs

/**
 * Exercises [SkCubicMap] against the upstream parametric-bezier ease
 * semantics : `computeFromT(0) = (0, 0)`, `computeFromT(1) = (1, 1)`,
 * linear specialisation matches `y = x`, and `computeYFromX` is the
 * inverse of `computeFromT.x` to within solver tolerance.
 */
class SkCubicMapTest {

    private fun near(a: Float, b: Float, eps: Float = 1e-3f): Boolean = abs(a - b) <= eps

    @Test
    fun `endpoints map to corners of the unit square`() {
        val map = SkCubicMap(SkPoint(0.25f, 0.1f), SkPoint(0.25f, 1f))
        val start = map.computeFromT(0f)
        val end = map.computeFromT(1f)
        assertTrue(near(start.fX, 0f) && near(start.fY, 0f), "start=$start")
        assertTrue(near(end.fX, 1f) && near(end.fY, 1f), "end=$end")
    }

    @Test
    fun `linear control points produce identity y = x`() {
        // p1 = p2 on the diagonal → kLine specialisation.
        val map = SkCubicMap(SkPoint(0.25f, 0.25f), SkPoint(0.75f, 0.75f))
        for (i in 0..10) {
            val x = i / 10f
            val y = map.computeYFromX(x)
            assertTrue(near(y, x, 1e-4f), "x=$x → y=$y (expected $x)")
        }
        assertTrue(SkCubicMap.IsLinear(SkPoint(0.25f, 0.25f), SkPoint(0.75f, 0.75f)))
    }

    @Test
    fun `computeYFromX is the inverse of computeFromT for ease-out curve`() {
        // Standard CSS "ease-out" bezier.
        val map = SkCubicMap(SkPoint(0f, 0f), SkPoint(0.58f, 1f))
        for (i in 0..10) {
            val t = i / 10f
            val pt = map.computeFromT(t)
            val y = map.computeYFromX(pt.fX)
            assertTrue(near(y, pt.fY, 5e-3f), "t=$t pt=$pt y=$y")
        }
    }

    @Test
    fun `out-of-range x is clamped`() {
        val map = SkCubicMap(SkPoint(0.25f, 0.1f), SkPoint(0.25f, 1f))
        assertEquals(map.computeYFromX(0f), map.computeYFromX(-1f))
        assertEquals(map.computeYFromX(1f), map.computeYFromX(2f))
    }

    @Test
    fun `cube-root specialisation produces a sane ease curve`() {
        // p1.x = 0, p2.x = 0 → kCubeRoot specialisation (At^3 = x).
        val map = SkCubicMap(SkPoint(0f, 0.5f), SkPoint(0f, 1f))
        // Mid-point should land somewhere between the linear estimate and 1.
        val mid = map.computeYFromX(0.5f)
        assertTrue(mid in 0f..1f, "y(0.5)=$mid out of unit interval")
    }

    @Test
    fun `IsLinear returns false for non-diagonal control points`() {
        assertTrue(!SkCubicMap.IsLinear(SkPoint(0.42f, 0f), SkPoint(0.58f, 1f)))
    }
}
