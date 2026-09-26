@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public W6e transactional witnesses.  Every expected pixel and budget term is derived before
 * constructing the Picture or Surface that consumes it; no planner, cache, or backend state is
 * observed directly.
 */
class W6eEffectsBudgetCacheRecoverySurfacePixelTest {
    @Test
    fun warmCacheRemainsPessimisticAtExactBudgetBoundary() {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val full = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val expected = ubyteArrayOf(0u, 0u, 255u, 255u, 0u, 0u, 0u, 0u)
        // B is hand-derived from this 2x1 public Picture fixture before a Picture or Surface
        // exists: root RGBA8 2x1 (8), coverage/shaded/Crop targets 3x1x1 RGBA8 (12),
        // frame and material rows 2x16 (32), then the aligned 2x1 RGBA8 readback row (256).
        // Thus B = 8 + 12 + 32 + 256 = 308.  A cached Picture result retains its frozen
        // targets and leases, so a warm B-1 frame is still terminal.
        val budgetB = listOf(
            Math.multiplyExact(2L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(3L, Math.multiplyExact(1L, 4L)),
            Math.multiplyExact(2L, 16L),
            256L,
        ).fold(0L, Math::addExact)
        val picture = cropPicture(unit)

        val admitted = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        admitted.canvas { drawPicture(picture) }
        assertRenderedExactly(admitted, expected)

        // Replaying the identical immutable Picture through the public Surface API is the only
        // cache priming operation.  No cache key, lease, or planner state is inspected.
        admitted.discardRecordedOperations()
        admitted.canvas { drawPicture(picture) }
        assertRenderedExactly(admitted, expected)

        val warmRefusal = Surface(2, 1, config = RenderConfig(
            frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L),
        ))
        warmRefusal.canvas { drawPicture(picture) }
        assertTerminalWithoutReadbackMutation(
            warmRefusal,
            full,
            "w6b.filter.frame_budget_exceeded:",
            UByteArray(expected.size) { 0x5au },
        )
    }

    @Test
    fun lateRefusalDoesNotPublishHealthySiblingAndSameSurfaceRecovers() {
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val healthySibling = ColorARGB.Blue
        val recovery = ColorARGB.Green
        val recoveryExpected = rgba(recovery)
        val lateUnsupported = ImageFilter.RuntimeEffect(
            requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
            UniformBlock { float1("alpha", .5f) },
            childShaderName = "child",
        )
        val source = Shader.Image(Image.fromPixels(
            1,
            1,
            ubyteArrayOf(85u, 45u, 24u, 255u).toByteArray(),
            alphaType = AlphaType.PREMUL,
        ))

        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(unit, Paint(healthySibling, antiAlias = false))
            drawRect(unit, Paint(shader = source, imageFilter = lateUnsupported, antiAlias = false))
        }
        assertTerminalWithoutReadbackMutation(
            surface,
            unit,
            "w6d.runtime_effect.abi_unsupported:",
            UByteArray(4) { 0x5au },
        )

        surface.discardRecordedOperations()
        surface.canvas { drawRect(unit, Paint(recovery, antiAlias = false)) }
        assertRenderedExactly(surface, recoveryExpected)
    }

    private fun cropPicture(unit: RectF32): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(unit).drawRect(unit, Paint(
            ColorARGB.Blue,
            imageFilter = ImageFilter.Crop(unit, TileMode.DECAL),
            antiAlias = false,
        ))
    }.finishRecordingAsPicture()

    private fun assertRenderedExactly(surface: Surface, expected: UByteArray) {
        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun assertTerminalWithoutReadbackMutation(
        surface: Surface,
        bounds: RectF32,
        diagnosticPrefix: String,
        sentinel: UByteArray,
    ) {
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun rgba(color: ColorARGB): UByteArray = ubyteArrayOf(
        color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte(),
    )

}
