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
    fun `inverse scan span mirror edge keeps its fixed point boundary`() {
        // Removing the scan-span fixed-point rounding, or treating the mirror's right boundary
        // as exclusive at the wrong pixel centre, changes one of these three public pixels.
        val blue = rgba(17, 61, 211)
        val transparent = rgba(0, 0, 0, 0)
        val widthI32 = 6
        val mirror = inverseTriangle(1f, 1f, 4f, 1f, 4f, 4f)
        val surface = Surface(widthI32, 6)
        surface.canvas {
            saveLayer()
            clipPath(targetClipPath(5, 4), antiAlias = false)
            drawPath(mirror, opaqueBlue())
            restore()
        }

        val actual = surface.render()
        assertPixel(blue, actual.pixels, widthI32, 1, 1)
        assertPixel(transparent, actual.pixels, widthI32, 2, 1)
        assertPixel(transparent, actual.pixels, widthI32, 5, 1)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `inverse scan span reversed winding keeps the upper right pixel inside`() {
        // Losing edge normalization on the reversed direct triangle turns (4,1) blue through
        // the inverse cover.  The clipped-out pixel separately keeps clip ownership observable.
        val transparent = rgba(0, 0, 0, 0)
        val widthI32 = 6
        val reverse = inverseTriangle(2f, 1f, 5f, 1f, 2f, 4f)
        val surface = Surface(widthI32, 6)
        surface.canvas {
            saveLayer()
            clipPath(targetClipPath(5, 4), antiAlias = false)
            drawPath(reverse, opaqueBlue())
            restore()
        }

        val actual = surface.render()
        assertPixel(transparent, actual.pixels, widthI32, 4, 1)
        assertPixel(transparent, actual.pixels, widthI32, 5, 1)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `inverse scan span upper right edge distinguishes 4 point 98 from 5 point 02`() {
        // The two inputs straddle the centre (4.5,1.5).  Replacing Skia's FDot6 recurrence by
        // an epsilon or a direct GPU triangle makes these public results collapse together.
        val blue = rgba(17, 61, 211)
        val transparent = rgba(0, 0, 0, 0)
        val widthI32 = 6
        listOf(4.98f to blue, 5.02f to transparent).forEach { (rightF32, expected) ->
            val surface = Surface(widthI32, 6)
            surface.canvas {
                saveLayer()
                clipPath(targetClipPath(5, 4), antiAlias = false)
                drawPath(inverseTriangle(2f, 1f, rightF32, 1f, 2f, 4f), opaqueBlue())
                restore()
            }

            val actual = surface.render()
            assertPixel(expected, actual.pixels, widthI32, 4, 1)
            assertPixel(transparent, actual.pixels, widthI32, 5, 1)
            assertRenderReadback(actual.nativeEvidenceScopeKinds)
        }
    }

    @Test
    fun `inverse scan span 4096 draws remain admitted in one W6 layer`() {
        // The limit is inclusive.  A producer span at each target row leaves its inverse cover
        // transparent, while Render/Readback prove the admitted lane reaches execution.
        val transparent = rgba(0, 0, 0, 0)
        val heightI32 = 4_096
        val surface = Surface(1, heightI32)
        surface.canvas {
            saveLayer()
            clipPath(targetClipPath(1, heightI32), antiAlias = false)
            drawPath(inverseTriangle(0f, 0f, 10_000f, 0f, 0f, heightI32.toFloat()), opaqueBlue())
            restore()
        }

        val actual = surface.render()
        assertPixel(transparent, actual.pixels, 1, 0, 0)
        assertPixel(transparent, actual.pixels, 1, 0, heightI32 - 1)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `inverse scan span two W6 occurrences of one path refuse 4098 draws before readback`() {
        // Each source lane has 2,049 valid spans.  Reusing the identical Path instance must not
        // turn two final producer occurrences into one source-level admission count.
        val heightI32 = 2_049
        val sentinel = UByteArray(heightI32 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val samePath = inverseTriangle(0f, 0f, 10_000f, 0f, 0f, heightI32.toFloat())
        val surface = Surface(1, heightI32)
        surface.canvas {
            saveLayer()
            clipPath(targetClipPath(1, heightI32), antiAlias = false)
            drawPath(samePath, opaqueBlue())
            drawPath(samePath, opaqueBlue())
            restore()
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, heightI32.toFloat()), sentinel)
        }
        assertTrue(failure.message?.startsWith("w4e.clip.scan-span-draw-limit:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    @Test
    fun `inverse scan spans rebase once in a translated W6 layer`() {
        // This 8x8 literal is the 6x6 L witness translated once by (1,1).  A second rebase
        // would move the blue vertical arm away from x=2, while no rebase would leave it at x=1.
        val clear = rgba(0, 0, 0, 0)
        val blue = rgba(17, 61, 211)
        val expected = listOf(
            clear, clear, clear, clear, clear, clear, clear, clear,
            clear, clear, clear, clear, clear, clear, clear, clear,
            clear, clear, blue, clear, clear, clear, clear, clear,
            clear, clear, blue, clear, clear, clear, clear, clear,
            clear, clear, blue, clear, clear, clear, clear, clear,
            clear, clear, blue, clear, clear, clear, clear, clear,
            clear, clear, clear, clear, clear, clear, clear, clear,
            clear, clear, clear, clear, clear, clear, clear, clear,
        ).flatten().toUByteArray()
        val clip = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(5f, 2f)
            lineTo(2f, 2f); lineTo(2f, 5f); lineTo(1f, 5f); close()
        }
        val inverseTriangle = inverseTriangle(2f, 1f, 5f, 1f, 2f, 4f)

        val surface = Surface(8, 8)
        surface.canvas {
            saveLayer()
            translate(1f, 1f)
            clipPath(clip, antiAlias = false)
            drawPath(inverseTriangle, opaqueBlue())
            restore()
        }
        val actual = surface.render()
        assertContentEquals(expected, actual.pixels)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `subpixel inverse triangle keeps a clear only W6 producer`() {
        // The finite triangle covers no pixel centre, so Geometry remains selected but its
        // producer must only clear stencil before the inverse cover paints the literal L.
        val clear = rgba(0, 0, 0, 0)
        val blue = rgba(17, 61, 211)
        val expected = listOf(
            clear, clear, clear, clear, clear, clear,
            clear, blue, blue, blue, blue, clear,
            clear, blue, clear, clear, clear, clear,
            clear, blue, clear, clear, clear, clear,
            clear, blue, clear, clear, clear, clear,
            clear, clear, clear, clear, clear, clear,
        ).flatten().toUByteArray()
        val clip = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(5f, 2f)
            lineTo(2f, 2f); lineTo(2f, 5f); lineTo(1f, 5f); close()
        }
        val inverseTriangle = inverseTriangle(2.1f, 1.1f, 2.2f, 1.1f, 2.1f, 1.2f)

        val surface = Surface(6, 6)
        surface.canvas {
            saveLayer()
            clipPath(clip, antiAlias = false)
            drawPath(inverseTriangle, opaqueBlue())
            restore()
        }
        val actual = surface.render()
        assertContentEquals(expected, actual.pixels)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `inverse scan span fullscreen producer covers 700 corners`() {
        // All four literal corner oracles are inside the finite triangle.  The inverse cover
        // must therefore leave them transparent; (699,0) is near the fullscreen hypotenuse.
        val widthI32 = 700
        val heightI32 = 700
        val transparent = rgba(0, 0, 0, 0)
        val corners = listOf(0 to 0, 699 to 0, 0 to 699, 699 to 699)
        val surface = Surface(widthI32, heightI32)
        surface.canvas {
            saveLayer()
            clipPath(targetClipPath(widthI32, heightI32), antiAlias = false)
            drawPath(inverseTriangle(-700f, -700f, 3_500f, -700f, -700f, 3_500f), opaqueBlue())
            restore()
        }
        val actual = surface.render()
        corners.forEach { (xI32, yI32) -> assertPixel(transparent, actual.pixels, widthI32, xI32, yI32) }
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

    @Test
    fun `inverse scan span frame budget at B keeps its W6 pixels`() {
        // This positive side is intentionally a public W6 budget/pixel witness.  Its L clip
        // leaves (0,699) outside the finite triangle, while (1,1) is inside it and (699,699)
        // is outside the L.  All three values are fixed before Surface construction.
        val widthI32 = 700
        val heightI32 = 700
        // Exact final parent + layer inventory.  It includes both 16-byte SourceUniformData
        // rows; this witness intentionally does not relabel the child W4e lane budget as W6.
        val frameBudgetBytes = 15_715_808L
        val blue = rgba(17, 61, 211)
        val transparent = rgba(0, 0, 0, 0)
        val lClip = Path().apply {
            moveTo(0f, 0f); lineTo(700f, 0f); lineTo(700f, 2f)
            lineTo(2f, 2f); lineTo(2f, 700f); lineTo(0f, 700f); close()
        }
        val surface = Surface(widthI32, heightI32, config = RenderConfig(frameLocalBudgetBytes = frameBudgetBytes))
        surface.canvas {
            saveLayer()
            clipPath(lClip, antiAlias = false)
            drawPath(inverseTriangle(1f, 1f, 700f, 1f, 1f, 700f), opaqueBlue())
            restore()
        }
        val actual = surface.render()
        assertPixel(blue, actual.pixels, widthI32, 0, 699)
        assertPixel(transparent, actual.pixels, widthI32, 1, 1)
        assertPixel(transparent, actual.pixels, widthI32, 699, 699)
        assertRenderReadback(actual.nativeEvidenceScopeKinds)
    }

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
        // Exact same final frame peak as the positive B witness, including the second
        // 16-byte SourceUniformData row retained by the final W6 occurrence inventory.
        val finalBudgetBytes = 15_715_808L
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
        // B_lane is the final W4e-owned peak before W4e publishes anything. This clipPath is a
        // path producer, so the prefix binds the shared 16 KiB V + 4 KiB I pools. At its hard
        // producer every physical mask allocation starts at prefix pass zero, and the logical
        // target is frame-resident: target + three masks + D24S8 + V/I = 9,820,480 bytes.
        val widthI32 = 700
        val heightI32 = 700
        val rgba8Bytes = rgba8Bytes(widthI32, heightI32)
        val payloadReservationsBytes = checkedAdd(
            16L * 1_024L, // VertexData physical minimum
            4L * 1_024L, // IndexData physical minimum
            4L * 1_024L, // W4e UniformData physical minimum
        )
        val prefixNativeBytes = checkedAdd(16L * 1_024L, 4L * 1_024L)
        val laneBudgetBytes = maxOf(
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 3L), prefixNativeBytes), // fold: target + three masks + prefix V/I
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 3L), rgba8Bytes, prefixNativeBytes), // hard producer: target + three masks + D24S8 + prefix V/I
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
        assertTrue(failure.message?.startsWith(
            "w4e.clip.budget.frame-local-exceeded: W4e pooled clip resources require $laneBudgetBytes bytes",
        ) == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    @Test
    fun `inverse scan span lane budget at B reaches native submission`() {
        // This is the positive side of the Task 2 budget boundary.  Task 3 owns the native
        // scan-span execution, so at B the current public terminal may reach that later refusal,
        // but must not be rejected by W4e's frame-local budget gate.
        val widthI32 = 700
        val heightI32 = 700
        val rgba8Bytes = rgba8Bytes(widthI32, heightI32)
        val payloadReservationsBytes = checkedAdd(16L * 1_024L, 4L * 1_024L, 4L * 1_024L)
        val prefixNativeBytes = checkedAdd(16L * 1_024L, 4L * 1_024L)
        val laneBudgetBytes = maxOf(
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 3L), prefixNativeBytes),
            checkedAdd(rgba8Bytes, checkedMultiply(rgba8Bytes, 3L), rgba8Bytes, prefixNativeBytes),
            checkedAdd(rgba8Bytes, rgba8Bytes, rgba8Bytes, payloadReservationsBytes),
            checkedAdd(rgba8Bytes, readbackBytes(widthI32, heightI32), payloadReservationsBytes),
        )
        val sentinel = UByteArray(widthI32 * heightI32 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val surface = Surface(widthI32, heightI32, config = RenderConfig(frameLocalBudgetBytes = laneBudgetBytes))
        surface.canvas {
            clipPath(targetClipPath(widthI32, heightI32), antiAlias = false)
            drawPath(inverseTriangle(-700f, -700f, 3_500f, -700f, -700f, 3_500f),
                Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }

        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat()), sentinel)
        }
        assertTrue(failure.message ==
            "w3.execution.submit_failure: GPU submission was refused. " +
                "unsupported.frame_memory.aggregate_budget_exceeded: " +
                "Frame aggregate memory exceeds the configured budget.",
            failure.message ?: "missing native terminal refusal")
        assertContentEquals(before, sentinel)
    }

    private fun targetClipPath(widthI32: Int, heightI32: Int): Path = Path().apply {
        moveTo(0f, 0f); lineTo(widthI32.toFloat(), 0f); lineTo(widthI32.toFloat(), heightI32.toFloat())
        lineTo(0f, heightI32.toFloat()); close()
    }

    private fun inverseTriangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float): Path = Path().apply {
        moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close(); fillType = FillType.INVERSE_WINDING
    }

    private fun opaqueBlue(): Paint = Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): UByteArray =
        ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    private fun assertPixel(expected: UByteArray, pixels: UByteArray, widthI32: Int, xI32: Int, yI32: Int) {
        val offsetI32 = Math.multiplyExact(Math.addExact(Math.multiplyExact(yI32, widthI32), xI32), 4)
        assertContentEquals(expected, pixels.copyOfRange(offsetI32, Math.addExact(offsetI32, 4)), "($xI32,$yI32)")
    }

    private fun assertRenderReadback(scopes: Collection<String>) {
        assertTrue(scopes.containsAll(listOf("Render", "Readback")), scopes.toString())
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
