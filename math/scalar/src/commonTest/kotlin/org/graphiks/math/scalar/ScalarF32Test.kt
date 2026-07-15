package org.graphiks.math.scalar

import kotlin.math.PI
import kotlin.test.*

class ScalarF32Test {

    @Test
    fun testConstants() {
        assertEquals(0f, ScalarF32.Zero.value)
        assertEquals(1f, ScalarF32.One.value)
        assertEquals(0.5f, ScalarF32.Half.value)
    }

    @Test
    fun testNearlyZero() {
        assertTrue(nearlyZero(0f))
        assertTrue(nearlyZero(1e-8f))
        assertFalse(nearlyZero(0.001f))
    }

    @Test
    fun testNearlyEqual() {
        assertTrue(nearlyEqual(1f, 1.00000001f))
        assertFalse(nearlyEqual(1f, 2f))
    }

    @Test
    fun testClamp() {
        assertEquals(5f, clamp(5f, 0f, 10f))
        assertEquals(0f, clamp(-5f, 0f, 10f))
        assertEquals(10f, clamp(15f, 0f, 10f))
    }

    @Test
    fun testInterp() {
        assertEquals(0f, interp(0f, 10f, 0f))
        assertEquals(10f, interp(0f, 10f, 1f))
        assertEquals(5f, interp(0f, 10f, 0.5f))
    }

    @Test
    fun testSign() {
        assertEquals(1f, sign(5f))
        assertEquals(-1f, sign(-5f))
        assertEquals(0f, sign(0f))
    }

    @Test
    fun testSin() {
        assertEquals(0f, sin(0f))
        assertEquals(1f, sin(PI.toFloat() / 2f))
        assertEquals(0f, sin(PI.toFloat()))
    }

    @Test
    fun testCos() {
        assertEquals(1f, cos(0f))
        assertEquals(0f, cos(PI.toFloat() / 2f))
        assertEquals(-1f, cos(PI.toFloat()))
    }

    @Test
    fun testTan() {
        assertEquals(0f, tan(0f))
        assertEquals(0f, tan(PI.toFloat()))
    }

    @Test
    fun testSaturatingAdd32() {
        assertEquals(10, saturatingAdd32(7, 3))
        assertEquals(Int.MAX_VALUE, saturatingAdd32(Int.MAX_VALUE, 1))
        assertEquals(Int.MIN_VALUE, saturatingAdd32(Int.MIN_VALUE, -1))
    }

    @Test
    fun testSaturatingSub32() {
        assertEquals(4, saturatingSub32(7, 3))
        assertEquals(Int.MIN_VALUE, saturatingSub32(Int.MIN_VALUE, 1))
        assertEquals(Int.MAX_VALUE, saturatingSub32(Int.MAX_VALUE, -1))
    }

    @Test
    fun testInstanceIsNearlyZero() {
        assertTrue(ScalarF32.of(0f).isNearlyZero())
        assertFalse(ScalarF32.of(0.001f).isNearlyZero())
    }

    @Test
    fun testInstanceClamp() {
        assertEquals(5f, ScalarF32.of(5f).clamp(0f, 10f))
        assertEquals(0f, ScalarF32.of(-5f).clamp(0f, 10f))
    }

    @Test
    fun testInstanceFloorToInt() {
        assertEquals(3, ScalarF32.of(3.7f).floorToInt())
        assertEquals(-4, ScalarF32.of(-3.2f).floorToInt())
    }

    @Test
    fun testInstanceCeilToInt() {
        assertEquals(4, ScalarF32.of(3.2f).ceilToInt())
    }

    @Test
    fun testInstanceRoundToInt() {
        assertEquals(4, ScalarF32.of(3.7f).roundToInt())
        assertEquals(1, ScalarF32.of(0.5f).roundToInt())
        assertEquals(3, ScalarF32.of(2.5f).roundToInt())
        assertEquals(0, ScalarF32.of(-0.5f).roundToInt())
        assertEquals(-2, ScalarF32.of(-2.5f).roundToInt())
    }

    @Test
    fun testInstanceIsFinite() {
        assertTrue(ScalarF32.of(1f).isFinite())
        assertFalse(ScalarF32.of(Float.POSITIVE_INFINITY).isFinite())
        assertFalse(ScalarF32.of(Float.NaN).isFinite())
    }

    @Test
    fun testInstanceIsInteger() {
        assertTrue(ScalarF32.of(5f).isInteger())
        assertFalse(ScalarF32.of(5.3f).isInteger())
    }
}
