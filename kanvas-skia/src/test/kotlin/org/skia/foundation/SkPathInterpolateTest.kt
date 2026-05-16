package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect

/**
 * Slice 3.9 — `SkPath` interpolation + dump helpers (Skia parity).
 * Mirrors `SkPath::isInterpolatable` / `makeInterpolate` / `interpolate`
 * (`include/core/SkPath.h:210-255`) and `dumpToString` / `dump`
 * (`include/core/SkPath.h:1071, 1085`).
 */
class SkPathInterpolateTest {

    private fun lineSeg(x0: Float, y0: Float, x1: Float, y1: Float) =
        SkPathBuilder().moveTo(x0, y0).lineTo(x1, y1).detach()

    // --- isInterpolatable ----------------------------------------------

    @Test
    fun `paths with the same verb stream are interpolatable`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(50f, 50f, 60f, 60f)
        assertTrue(a.isInterpolatable(b))
    }

    @Test
    fun `paths with different verb streams are not interpolatable`() {
        val line = lineSeg(0f, 0f, 10f, 10f)
        val rect = SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        assertFalse(line.isInterpolatable(rect))
    }

    @Test
    fun `paths with different conic weights are not interpolatable`() {
        val a = SkPathBuilder().moveTo(0f, 0f).conicTo(10f, 0f, 10f, 10f, 0.5f).detach()
        val b = SkPathBuilder().moveTo(0f, 0f).conicTo(10f, 0f, 10f, 10f, 0.7f).detach()
        assertFalse(a.isInterpolatable(b))
    }

    @Test
    fun `interpolatable empty paths produce an interpolatable empty result`() {
        val a = SkPathBuilder().detach()
        val b = SkPathBuilder().detach()
        assertTrue(a.isInterpolatable(b))
        assertNotNull(a.makeInterpolate(b, 0.5f))
    }

    // --- makeInterpolate -----------------------------------------------

    @Test
    fun `makeInterpolate at weight 1 returns this paths coords`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(50f, 50f, 60f, 60f)
        val out = a.makeInterpolate(b, 1f)!!
        assertEquals(0f, out.coords[0], 1e-4f)
        assertEquals(0f, out.coords[1], 1e-4f)
        assertEquals(10f, out.coords[2], 1e-4f)
        assertEquals(10f, out.coords[3], 1e-4f)
    }

    @Test
    fun `makeInterpolate at weight 0 returns ending paths coords`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(50f, 50f, 60f, 60f)
        val out = a.makeInterpolate(b, 0f)!!
        assertEquals(50f, out.coords[0], 1e-4f)
        assertEquals(50f, out.coords[1], 1e-4f)
        assertEquals(60f, out.coords[2], 1e-4f)
        assertEquals(60f, out.coords[3], 1e-4f)
    }

    @Test
    fun `makeInterpolate at weight 0_5 returns midpoints`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(40f, 40f, 50f, 50f)
        val out = a.makeInterpolate(b, 0.5f)!!
        // (0 * 0.5 + 40 * 0.5, 0 * 0.5 + 40 * 0.5) = (20, 20)
        assertEquals(20f, out.coords[0], 1e-4f)
        assertEquals(20f, out.coords[1], 1e-4f)
        // (10 * 0.5 + 50 * 0.5) = 30
        assertEquals(30f, out.coords[2], 1e-4f)
        assertEquals(30f, out.coords[3], 1e-4f)
    }

    @Test
    fun `makeInterpolate preserves verb stream + fillType from this`() {
        val a = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd)
            .moveTo(0f, 0f).lineTo(10f, 10f).detach()
        val b = SkPathBuilder().setFillType(SkPathFillType.kInverseWinding)
            .moveTo(50f, 50f).lineTo(60f, 60f).detach()
        val out = a.makeInterpolate(b, 0.5f)!!
        assertTrue(a.verbs contentEquals out.verbs)
        // fillType is inherited from `this`, not the ending path.
        assertEquals(SkPathFillType.kEvenOdd, out.fillType)
    }

    @Test
    fun `makeInterpolate preserves conic weights when verb streams match`() {
        val a = SkPathBuilder().moveTo(0f, 0f).conicTo(10f, 0f, 10f, 10f, 0.5f).detach()
        val b = SkPathBuilder().moveTo(20f, 20f).conicTo(30f, 20f, 30f, 30f, 0.5f).detach()
        val out = a.makeInterpolate(b, 0.5f)!!
        assertEquals(0.5f, out.conicWeights[0], 1e-4f)
    }

    @Test
    fun `makeInterpolate returns null when paths aren't interpolatable`() {
        val line = lineSeg(0f, 0f, 10f, 10f)
        val rect = SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        assertNull(line.makeInterpolate(rect, 0.5f))
    }

    @Test
    fun `interpolate is an alias of makeInterpolate`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(50f, 50f, 60f, 60f)
        val out1 = a.makeInterpolate(b, 0.5f)!!
        val out2 = a.interpolate(b, 0.5f)!!
        assertTrue(out1.coords contentEquals out2.coords)
        assertTrue(out1.verbs contentEquals out2.verbs)
    }

    // --- weights outside [0, 1] (extrapolation) ------------------------

    @Test
    fun `makeInterpolate with weight 2 extrapolates past this`() {
        val a = lineSeg(0f, 0f, 10f, 10f)
        val b = lineSeg(20f, 20f, 30f, 30f)
        // out = a * 2 + b * (-1) = (0*2 - 20, 0*2 - 20) = (-20, -20)
        val out = a.makeInterpolate(b, 2f)!!
        assertEquals(-20f, out.coords[0], 1e-4f)
        assertEquals(-20f, out.coords[1], 1e-4f)
    }

    // --- dumpToString --------------------------------------------------

    @Test
    fun `dumpToString labels fillType and lists each verb`() {
        val p = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .quadTo(15f, 5f, 10f, 10f)
            .conicTo(0f, 10f, 0f, 0f, 0.5f)
            .cubicTo(5f, -5f, 5f, 15f, 10f, 10f)
            .close()
            .detach()
        val s = p.dumpToString()
        assertTrue(s.contains("kEvenOdd"))
        assertTrue(s.contains("moveTo"))
        assertTrue(s.contains("lineTo"))
        assertTrue(s.contains("quadTo"))
        assertTrue(s.contains("conicTo"))
        assertTrue(s.contains("w=0.5"))
        assertTrue(s.contains("cubicTo"))
        assertTrue(s.contains("close"))
    }

    @Test
    fun `dumpToString of empty path lists zero verbs`() {
        val s = SkPathBuilder().detach().dumpToString()
        assertTrue(s.contains("verbs=0"))
    }
}
