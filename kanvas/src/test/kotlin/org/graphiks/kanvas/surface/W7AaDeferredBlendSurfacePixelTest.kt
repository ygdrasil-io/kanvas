@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Native W7 witness: resolved geometric coverage composes an original PLUS source at root. */
class W7AaDeferredBlendSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `root AA PLUS applies the bounded SourcePreScale witness at full coverage`() {
        // This Task-1 fixture uses black RGB, so the independently evaluated LINEAR envelope
        // remains bounded.  It proves the full covered result differs from the unchanged
        // destination before a Surface is constructed; no byte-wise sRGB addition is assumed.
        val fixture = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture()
        val covered = W5bBlendCpuOracle.point(fixture.center, fixture.destination, 1f)
        val uncovered = W5bBlendCpuOracle.point(fixture.center, fixture.destination, 0f)
        // Standard MSAA4 has two covered samples on x+y=6 at (3,2).
        // The resolved RGBA8 alpha is quantized once: round(255 * 2/4) / 255.
        val partial = W5bBlendCpuOracle.point(fixture.center, fixture.destination, 128f / 255f)
        val partialCodes = (partial as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)?.channels
            ?: error("Task-1 partial PLUS witness must remain bounded: $partial")
        val coveredCodes = (covered as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)?.channels
            ?: error("Task-1 full PLUS witness must remain bounded: $covered")
        val uncoveredCodes = (uncovered as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)?.channels
            ?: error("Task-1 C=0 witness must remain bounded: $uncovered")
        check(coveredCodes.zip(uncoveredCodes).any { (full, zero) -> full.intersect(zero).isEmpty() }) {
            "full coverage and C=0 PLUS witnesses must be disjoint"
        }
        check(partialCodes.zip(coveredCodes).any { (edge, full) -> edge.intersect(full).isEmpty() })
        check(partialCodes.zip(uncoveredCodes).any { (edge, zero) -> edge.intersect(zero).isEmpty() })
        val triangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f),
                Paint(shader = Shader.SolidColor(fixture.destination.color), antiAlias = false))
            drawPath(triangle, Paint(
                shader = Shader.Opacity(Shader.SolidColor(fixture.center.color), fixture.center.opacityF32),
                blendMode = BlendMode.PLUS,
                antiAlias = true,
            ))
        }

        val result = renderTwice(surface)
        assertAdmits(covered, result.pixels, 7, 2, 2)
        assertAdmits(uncovered, result.pixels, 7, 6, 6)
        assertAdmits(partial, result.pixels, 7, 3, 2)
    }

    private fun renderTwice(surface: Surface): RenderResult {
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "first native render must dispatch")
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertTrue(second.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            second.nativeEvidenceScopeKinds.toString())
        assertTrue(second.stats.opsDispatched > 0, "second native render must dispatch")
        assertContentEquals(first.pixels, second.pixels)
        return first
    }

    private fun assertAdmits(expected: WgslFloatEnvelopeV1Oracle.DrawResult, pixels: UByteArray,
        width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offset, offset + 4))
    }
}
