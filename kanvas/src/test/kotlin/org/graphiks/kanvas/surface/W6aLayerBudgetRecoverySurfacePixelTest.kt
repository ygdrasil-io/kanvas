@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/**
 * Public W6a budget contract.  The byte expectations are hand-derived before a Surface exists:
 * a 1x1 RGBA8 root (4), each 1x1 layer/snapshot (4), one 256-byte readback row, and the
 * 16-byte W6 geometry/restore uniform block.  They never consult a planner or diagnostics
 * implementation to calculate B.
 */
class W6aLayerBudgetRecoverySurfacePixelTest {
    @Test
    fun `vertices physical gap preserves its owner diagnostic and same surface recovers`() {
        val expected = ubyteArrayOf(17u, 61u, 211u, 255u)
        val triangle = org.graphiks.kanvas.types.Vertices(org.graphiks.kanvas.types.VertexMode.TRIANGLES,
            listOf(org.graphiks.math.geometry.Point2F32(0f, 0f), org.graphiks.math.geometry.Point2F32(2f, 0f),
                org.graphiks.math.geometry.Point2F32(0f, 2f)))
        for (layered in listOf(false, true)) {
            val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
            val before = sentinel.copyOf()
            val surface = Surface(1, 1)
            surface.canvas {
                if (layered) saveLayer()
                save()
                concat(org.graphiks.math.matrix.Matrix3x3F32(persp0 = .25f))
                drawVertices(triangle, BlendMode.SRC_OVER, Paint(ColorARGB.Red, antiAlias = false))
                restore()
                if (layered) restore()
            }
            val failure = assertFailsWith<IllegalStateException> { surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel) }
            assertTrue(failure.message?.startsWith("unsupported.vertices.transform:") == true, failure.message ?: "missing diagnostic")
            assertContentEquals(before, sentinel)
            surface.discardRecordedOperations()
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
            assertContentEquals(expected, surface.render().pixels)
        }
    }

    @Test
    fun `point physical gap preserves its exact diagnostic and same surface recovers`() {
        val expected = ubyteArrayOf(17u, 61u, 211u, 255u)
        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val before = sentinel.copyOf()
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 239, 51, 73), antiAlias = false))
            drawPoint(.5f, .5f, Paint(strokeWidth = 1f, strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND, antiAlias = false))
            restore()
        }
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel) }
        assertTrue(failure.message?.startsWith("unsupported.core_primitive.point.round_cap_exact_lowering:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `exactBudgetBoundaryAcceptsBAndRefusesBMinusOne`() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val exactBudgetBytes = 4L + 4L + 256L + 16L

        val admitted = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes))
        admitted.canvas { saveLayer(); restore() }
        assertContentEquals(transparent, admitted.render().pixels)

        val refused = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes - 1L))
        refused.canvas { saveLayer(); restore() }
        assertTerminalWithoutReadbackMutation(refused)
    }

    @Test
    fun `nestedLiveTargetsRemainBudgeted`() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val exactBudgetBytes = 4L + 4L + 4L + 256L + 16L

        val admitted = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes))
        admitted.canvas { saveLayer(); saveLayer(); restore(); restore() }
        assertContentEquals(transparent, admitted.render().pixels)

        val refused = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes - 1L))
        refused.canvas { saveLayer(); saveLayer(); restore(); restore() }
        assertTerminalWithoutReadbackMutation(refused)
    }

    @Test
    fun `successiveSiblingTargetsChargeDistinctPreparedAllocations`() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val exactBudgetBytes = 4L + 4L + 4L + 256L + 16L

        val admitted = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes))
        admitted.canvas { saveLayer(); restore(); saveLayer(); restore() }
        assertContentEquals(transparent, admitted.render().pixels)

        val refused = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes - 1L))
        refused.canvas { saveLayer(); restore(); saveLayer(); restore() }
        assertTerminalWithoutReadbackMutation(refused)
    }

    @Test
    fun `previousCopyAndDestinationSnapshotAreBudgeted`() {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val exactBudgetBytes = 4L + 4L + 4L + 256L + 16L
        val previousDifference = SaveLayerRec(
            paint = Paint(blendMode = BlendMode.DIFFERENCE, antiAlias = false),
            initWithPrevious = true,
        )

        val admitted = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes))
        admitted.canvas { saveLayer(previousDifference); restore() }
        assertContentEquals(transparent, admitted.render().pixels)

        val refused = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes - 1L))
        refused.canvas { saveLayer(previousDifference); restore() }
        assertTerminalWithoutReadbackMutation(refused)
    }

    @Test
    fun `warmReplayKeepsPessimisticBudget`() {
        val expected = ubyteArrayOf(17u, 61u, 211u, 255u)
        // Root + live layer + readback + W6 uniform, then the published 112-byte
        // direct-image header, its 48-byte identity coordinate matrix and 16-byte
        // composed tail-alpha field, one RGBA8 texel and its pessimistic upload row.
        val exactBudgetBytes = 4L + 4L + 256L + 16L + 112L + 48L + 16L + 4L + 256L
        val image = org.graphiks.kanvas.image.Image.fromPixels(1, 1, byteArrayOf(17, 61, -45, -1),
            alphaType = org.graphiks.kanvas.image.AlphaType.UNPREMUL)
        val draw: org.graphiks.kanvas.canvas.Canvas.() -> Unit = {
            saveLayer()
            drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
                Paint(antiAlias = false))
            restore()
        }
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes))
        surface.canvas(draw)
        assertContentEquals(expected, surface.render().pixels)
        surface.discardRecordedOperations()
        surface.canvas(draw)
        assertContentEquals(expected, surface.render().pixels)

        val refused = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = exactBudgetBytes - 1L))
        refused.canvas(draw)
        assertTerminalWithoutReadbackMutation(refused, "budget.w5g.composed-uniform:")
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun `lateSiblingRefusalLeavesReadbackSentinelAndSameSurfaceRecovers`() {
        val recovered = ubyteArrayOf(17u, 61u, 211u, 255u)
        val oneLayerBudgetBytes = 4L + 4L + 256L + 16L
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = oneLayerBudgetBytes))
        surface.canvas { saveLayer(); restore(); saveLayer(); restore() }
        assertTerminalWithoutReadbackMutation(surface)

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(recovered, surface.render().pixels)
    }

    @Test
    fun `late material refusal keeps its W5 diagnostic and same surface recovers`() {
        val recovered = ubyteArrayOf(17u, 61u, 211u, 255u)
        val unregistered = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }.makeShader(UniformBlock.EMPTY)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(shader = unregistered, antiAlias = false))
            restore()
        }

        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith("unsupported.material.runtime_effect.unregistered_semantics:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(recovered, surface.render().pixels)
    }

    @Test
    fun `image owner budget refusal keeps its diagnostic and same surface recovers`() {
        // The direct image source requires 540 bytes before W6 can construct its aggregate
        // graph. The literal 500 is a public fixture, not a derived W6 budget, so the code
        // belongs to the image owner rather than to the later W6 peak checker.
        val image = org.graphiks.kanvas.image.Image.fromPixels(1, 1, byteArrayOf(17, 61, -45, -1),
            alphaType = org.graphiks.kanvas.image.AlphaType.UNPREMUL)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = 500L))
        surface.canvas {
            saveLayer()
            drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
                Paint(antiAlias = false))
            restore()
        }

        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith("resource-limit.w5g.composed-binding:") == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(ubyteArrayOf(17u, 61u, 211u, 255u), surface.render().pixels)
    }

    private fun assertTerminalWithoutReadbackMutation(
        surface: Surface,
        diagnosticPrefix: String = "w6a.layer.frame_budget_exceeded:",
    ) {
        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 1f, 1f), sentinel)
        }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true,
            failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }
}
