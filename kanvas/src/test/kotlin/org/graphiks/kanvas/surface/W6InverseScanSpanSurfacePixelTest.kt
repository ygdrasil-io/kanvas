@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public W6 admission boundaries for inverse direct paths whose finite interior is a scan-span
 * producer.  These tests intentionally observe only Surface terminal behavior: no planner,
 * graph, or native infrastructure detail supplies either expected diagnostic or budget.
 */
class W6InverseScanSpanSurfacePixelTest {
    @Test
    fun `inverse scan span 4097 draws refuse before readback`() {
        val sentinel = UByteArray(4_097 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val fullClip = targetClipPath(1, 4_097)
        val inverseTriangle = inverseTriangle(0f, 0f, 10_000f, 0f, 0f, 4_097f)

        val surface = Surface(1, 4_097)
        surface.canvas {
            clipPath(fullClip, antiAlias = false)
            drawPath(inverseTriangle, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 4_097f), sentinel)
        }
        assertTrue(failure.message?.startsWith("w4e.clip.scan-span-draw-limit:") == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    @Test
    fun `inverse scan span aggregate budget refuses B minus one before readback`() {
        // B is the aggregate W6 frame peak.  The W4e lane cannot see the parent root target or
        // the layer restore uniform, so this separate scope witness intentionally expects the
        // final W6a aggregate gate rather than relabelling that refusal as W4e.
        val widthI32 = 700
        val heightI32 = 700
        val rgba8Bytes = rgba8Bytes(widthI32, heightI32)
        val readbackBytes = readbackBytes(widthI32, heightI32)
        val finalBudgetBytes = checkedAdd(
            rgba8Bytes, // root target
            rgba8Bytes, // live W6 layer
            readbackBytes,
            checkedMultiply(rgba8Bytes, 3L), // two mask accumulators and scratch
            rgba8Bytes, // hard clip D24S8
            rgba8Bytes, // inverse scan-span producer D24S8
            16L * 1_024L, // VertexData physical minimum; zero useful bytes for this producer
            4L * 1_024L, // IndexData physical minimum; zero useful bytes for this producer
            4L * 1_024L, // W4e UniformData physical minimum
            16L, // W6 layer restore uniform
        )
        val sentinel = UByteArray(widthI32 * heightI32 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val fullClip = targetClipPath(widthI32, heightI32)
        val inverseTriangle = inverseTriangle(-700f, -700f, 3_500f, -700f, -700f, 3_500f)

        val surface = Surface(widthI32, heightI32, config = RenderConfig(frameLocalBudgetBytes = finalBudgetBytes - 1L))
        surface.canvas {
            saveLayer()
            clipPath(fullClip, antiAlias = false)
            drawPath(inverseTriangle, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat()), sentinel)
        }
        assertTrue(failure.message?.startsWith("w6a.layer.frame_budget_exceeded:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    @Test
    fun `inverse scan span lane budget refuses B minus one before readback`() {
        // B_lane is the final W4e-owned peak before insertClips publishes anything.  The
        // checked liveness alternatives include target/readback, masks, both D24S8 roles, and
        // the sole V/I/U policy reservation (with zero useful V/I for this producer).  The
        // prefix's target + three simultaneously-live masks is the maximum.
        val widthI32 = 700
        val heightI32 = 700
        val rgba8Bytes = rgba8Bytes(widthI32, heightI32)
        val payloadReservationsBytes = checkedAdd(
            16L * 1_024L, // VertexData physical minimum
            4L * 1_024L, // IndexData physical minimum
            4L * 1_024L, // W4e UniformData physical minimum
        )
        val laneBudgetBytes = maxOf(
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 3L)), // fold: target + two accumulators + scratch
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 2L), rgba8Bytes), // hard producer: target + two masks + D24S8
            checkedAdd(rgba8Bytes, rgba8Bytes, rgba8Bytes, payloadReservationsBytes), // inverse producer: target + mask + D24S8 + V/I/U
            checkedAdd(rgba8Bytes, readbackBytes(widthI32, heightI32), payloadReservationsBytes),
        )
        val sentinel = UByteArray(widthI32 * heightI32 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val fullClip = targetClipPath(widthI32, heightI32)
        val inverseTriangle = inverseTriangle(-700f, -700f, 3_500f, -700f, -700f, 3_500f)

        val surface = Surface(widthI32, heightI32, config = RenderConfig(frameLocalBudgetBytes = laneBudgetBytes - 1L))
        surface.canvas {
            clipPath(fullClip, antiAlias = false)
            drawPath(inverseTriangle, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat()), sentinel)
        }
        assertTrue(failure.message?.startsWith("w4e.clip.budget.frame-local-exceeded:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun targetClipPath(widthI32: Int, heightI32: Int): Path = Path().apply {
        moveTo(0f, 0f); lineTo(widthI32.toFloat(), 0f); lineTo(widthI32.toFloat(), heightI32.toFloat())
        lineTo(0f, heightI32.toFloat()); close()
    }

    private fun inverseTriangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float): Path = Path().apply {
        moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close(); fillType = FillType.INVERSE_WINDING
    }

    private fun checkedAdd(vararg values: Long): Long = values.fold(0L, Math::addExact)

    private fun checkedMultiply(left: Long, right: Long): Long = Math.multiplyExact(left, right)

    private fun rgba8Bytes(widthI32: Int, heightI32: Int): Long =
        checkedMultiply(checkedMultiply(widthI32.toLong(), heightI32.toLong()), 4L)

    private fun readbackBytes(widthI32: Int, heightI32: Int): Long =
        checkedMultiply(alignUp(checkedMultiply(widthI32.toLong(), 4L), 256L), heightI32.toLong())

    private fun alignUp(value: Long, alignment: Long): Long {
        val remainder = value % alignment
        return if (remainder == 0L) value else checkedAdd(value, alignment - remainder)
    }
}
