@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public render/readback regression for authenticated image opacity under AA. */
class W7ImageOpacitySurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun latticeImageOpacityAppliesOnceWithAaAndHardEdges() {
        // Removing image opacity propagation makes the AA cases refuse at the
        // V4 authority boundary; applying opacity twice changes both sources.
        // A Surface snapshot retains the public GPU-backed image route used by lattice2.
        val source = Surface(3, 1)
        source.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 1f), Paint(color = ColorARGB.Blue, blendMode = BlendMode.SRC)) }
        val blue = source.makeImageSnapshot()
        val lattice = Lattice(listOf(1, 2), emptyList(), colors = List(3) { ColorARGB.Red }, flags = listOf(
            LatticeFlags.DEFAULT, LatticeFlags.FIXED_COLOR, LatticeFlags.TRANSPARENT,
        ))
        val alphaPaint = Paint(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        val alpha = alphaPaint.color.alphaNormalized
        val retained = alpha * (1f - alpha)
        for (antiAlias in listOf(true, false)) for (mode in listOf(BlendMode.SRC_OVER, BlendMode.SRC_ATOP)) {
            // A translucent destination makes SRC_ATOP use the deferred image
            // source path, where an image V3 must retain its authenticated V4 proof.
            val alphaCells = when (mode) {
                BlendMode.SRC_OVER -> listOf(
                    expectedLinear(listOf(0f, retained, alpha, alpha + retained)),
                    expectedLinear(listOf(alpha, retained, 0f, alpha + retained)),
                    expectedLinear(listOf(0f, alpha, 0f, alpha)),
                )
                BlendMode.SRC_ATOP -> listOf(
                    expectedLinear(listOf(0f, retained, alpha * alpha, alpha)),
                    expectedLinear(listOf(alpha * alpha, retained, 0f, alpha)),
                    expectedLinear(listOf(0f, alpha, 0f, alpha)),
                )
                else -> error("unreachable")
            }
            val opaqueCells = when (mode) {
                BlendMode.SRC_OVER -> listOf(
                    expectedLinear(listOf(0f, 0f, 1f, 1f)),
                    expectedLinear(listOf(1f, 0f, 0f, 1f)),
                    expectedLinear(listOf(0f, alpha, 0f, alpha)),
                )
                BlendMode.SRC_ATOP -> listOf(
                    expectedLinear(listOf(0f, 0f, alpha, alpha)),
                    expectedLinear(listOf(alpha, 0f, 0f, alpha)),
                    expectedLinear(listOf(0f, alpha, 0f, alpha)),
                )
            }
            val expected = List(3) { expectedLinear(listOf(0f, 0f, 1f, 1f)) } + opaqueCells + alphaCells
            val surface = Surface(9, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), Paint(
                    color = alphaPaint.color, blendMode = BlendMode.SRC))
                drawImageRect(blue, RectF32.ofLTRB(0f, 0f, 3f, 1f), RectF32.ofLTRB(0f, 0f, 3f, 1f),
                    SamplingOptions.NEAREST, Paint(color = ColorARGB.White, antiAlias = antiAlias, blendMode = BlendMode.SRC_OVER))
                drawImageLattice(blue, lattice, RectF32.ofLTRB(3f, 0f, 6f, 1f),
                    Paint(color = ColorARGB.White, antiAlias = antiAlias, blendMode = mode), SamplingOptions.NEAREST)
                drawImageLattice(blue, lattice, RectF32.ofLTRB(6f, 0f, 9f, 1f),
                    alphaPaint.copy(antiAlias = antiAlias, blendMode = mode), SamplingOptions.NEAREST)
            }
            val first = surface.render()
            assertPixels(expected, first.pixels, "aa=$antiAlias mode=$mode")
            assertEquals(4, first.stats.opsDispatched, "aa=$antiAlias mode=$mode")
            assertEquals(0, first.stats.opsRefused, "aa=$antiAlias mode=$mode")
            assertContentEquals(first.pixels, surface.render().pixels, "aa=$antiAlias mode=$mode replay")
        }
    }

    private fun expectedLinear(values: List<Float>) = bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(
        values.map(WgslFloatEnvelopeV1Oracle.Interval::input).toTypedArray()))

    private fun bounded(value: WgslFloatEnvelopeV1Oracle.DrawResult): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        require(value is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { value.toString() }
        return value
    }

    private fun assertPixels(expected: List<WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>, pixels: UByteArray, label: String) {
        assertEquals(expected.size * 4, pixels.size, label)
        expected.forEachIndexed { index, value ->
            try { WgslFloatEnvelopeV1Oracle.assertAdmits(value, pixels.copyOfRange(index * 4, index * 4 + 4)) }
            catch (failure: IllegalArgumentException) { throw IllegalArgumentException("$label pixel=$index: ${failure.message}", failure) }
        }
    }
}
