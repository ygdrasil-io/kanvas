package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect

/**
 * Slice 3.2 — `SkPathFillType` helpers + immutable fill-type API on
 * `SkPath` + mutable convenience on `SkPathBuilder`.
 *
 * Mirrors the C++ static helpers in `include/core/SkPathTypes.h:28-42`
 * and the path/builder methods in `include/core/SkPath.h:266-279` /
 * `include/core/SkPathBuilder.h:80-958`.
 */
class SkPathFillTypeTest {

    // --- enum helpers ---------------------------------------------------

    @Test
    fun `isEvenOdd is true only for kEvenOdd and kInverseEvenOdd`() {
        assertFalse(SkPathFillType.kWinding.isEvenOdd())
        assertTrue(SkPathFillType.kEvenOdd.isEvenOdd())
        assertFalse(SkPathFillType.kInverseWinding.isEvenOdd())
        assertTrue(SkPathFillType.kInverseEvenOdd.isEvenOdd())
    }

    @Test
    fun `isInverse is true only for the two kInverse variants`() {
        assertFalse(SkPathFillType.kWinding.isInverse())
        assertFalse(SkPathFillType.kEvenOdd.isInverse())
        assertTrue(SkPathFillType.kInverseWinding.isInverse())
        assertTrue(SkPathFillType.kInverseEvenOdd.isInverse())
    }

    @Test
    fun `toggleInverse flips the inverse bit and is involutive`() {
        assertEquals(SkPathFillType.kInverseWinding, SkPathFillType.kWinding.toggleInverse())
        assertEquals(SkPathFillType.kInverseEvenOdd, SkPathFillType.kEvenOdd.toggleInverse())
        assertEquals(SkPathFillType.kWinding, SkPathFillType.kInverseWinding.toggleInverse())
        assertEquals(SkPathFillType.kEvenOdd, SkPathFillType.kInverseEvenOdd.toggleInverse())
        // Involutive.
        for (ft in SkPathFillType.entries) {
            assertEquals(ft, ft.toggleInverse().toggleInverse())
        }
    }

    @Test
    fun `convertToNonInverse drops the inverse bit but keeps winding choice`() {
        assertEquals(SkPathFillType.kWinding, SkPathFillType.kWinding.convertToNonInverse())
        assertEquals(SkPathFillType.kEvenOdd, SkPathFillType.kEvenOdd.convertToNonInverse())
        assertEquals(SkPathFillType.kWinding, SkPathFillType.kInverseWinding.convertToNonInverse())
        assertEquals(SkPathFillType.kEvenOdd, SkPathFillType.kInverseEvenOdd.convertToNonInverse())
        // Idempotent.
        for (ft in SkPathFillType.entries) {
            val once = ft.convertToNonInverse()
            assertEquals(once, once.convertToNonInverse())
        }
    }

    // --- SkPath.isInverseFillType ---------------------------------------

    @Test
    fun `SkPath isInverseFillType reflects the constructor fill type`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        for (ft in SkPathFillType.entries) {
            val p = SkPathBuilder().setFillType(ft).addRect(rect).detach()
            assertEquals(ft.isInverse(), p.isInverseFillType(), "fillType=$ft")
        }
    }

    // --- SkPath.makeFillType --------------------------------------------

    @Test
    fun `makeFillType returns same instance when fill type unchanged`() {
        val p = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f)).detach()
        assertSame(p, p.makeFillType(SkPathFillType.kEvenOdd))
    }

    @Test
    fun `makeFillType returns new instance with requested fill type`() {
        val src = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f)).detach()
        val dst = src.makeFillType(SkPathFillType.kInverseEvenOdd)
        assertEquals(SkPathFillType.kWinding, src.fillType)
        assertEquals(SkPathFillType.kInverseEvenOdd, dst.fillType)
        // Verb stream and coords are shared (no copy).
        assertSame(src.verbs, dst.verbs)
        assertSame(src.coords, dst.coords)
        assertSame(src.conicWeights, dst.conicWeights)
    }

    // --- SkPath.makeToggleInverseFillType -------------------------------

    @Test
    fun `makeToggleInverseFillType flips the inverse bit and shares storage`() {
        val src = SkPathBuilder().setFillType(SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f)).detach()
        val dst = src.makeToggleInverseFillType()
        assertEquals(SkPathFillType.kEvenOdd, src.fillType)
        assertEquals(SkPathFillType.kInverseEvenOdd, dst.fillType)
        assertSame(src.verbs, dst.verbs)
        assertSame(src.coords, dst.coords)
    }

    @Test
    fun `makeToggleInverseFillType is involutive over rendering state`() {
        val src = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f)).detach()
        val twice = src.makeToggleInverseFillType().makeToggleInverseFillType()
        assertEquals(src.fillType, twice.fillType)
        assertSame(src.verbs, twice.verbs)
    }

    // --- SkPathBuilder.fillType / isInverseFillType / toggleInverseFillType

    @Test
    fun `builder fillType reads back what setFillType wrote`() {
        val b = SkPathBuilder().setFillType(SkPathFillType.kInverseWinding)
        assertEquals(SkPathFillType.kInverseWinding, b.fillType())
    }

    @Test
    fun `builder default fillType is kWinding`() {
        assertEquals(SkPathFillType.kWinding, SkPathBuilder().fillType())
    }

    @Test
    fun `builder isInverseFillType matches the configured rule`() {
        val b = SkPathBuilder()
        assertFalse(b.isInverseFillType())
        b.setFillType(SkPathFillType.kInverseEvenOdd)
        assertTrue(b.isInverseFillType())
    }

    @Test
    fun `builder toggleInverseFillType flips the bit and is chainable`() {
        val p = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .toggleInverseFillType()
            .addRect(SkRect.MakeLTRB(0f, 0f, 5f, 5f))
            .detach()
        assertEquals(SkPathFillType.kInverseEvenOdd, p.fillType)
    }

    @Test
    fun `builder toggleInverseFillType twice restores the original`() {
        val b = SkPathBuilder().setFillType(SkPathFillType.kWinding)
        b.toggleInverseFillType().toggleInverseFillType()
        assertEquals(SkPathFillType.kWinding, b.fillType())
    }
}
