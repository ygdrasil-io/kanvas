@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Native W7 witness: resolved geometric coverage composes an original PLUS source at root. */
class W7AaDeferredBlendSurfacePixelTest {
    enum class Geometry { PATH, RECT }
    enum class StencilGeometry { CONCAVE_WINDING, EVEN_ODD_HOLE }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic
        fun basicCells(): List<Array<Any>> = listOf(BlendMode.PLUS, BlendMode.SRC_OVER).flatMap { mode ->
            listOf(Geometry.PATH, Geometry.RECT).flatMap { geometry ->
                listOf(false, true).map { layer ->
                    arrayOf("$mode/${geometry.name.lowercase()}/${if (layer) "layer" else "root"}", mode, geometry, layer)
                }
            }
        }

        @JvmStatic
        fun stencilCells(): List<Array<Any>> = StencilGeometry.entries.flatMap { geometry ->
            listOf(false, true).map { layer ->
                arrayOf("PLUS/${geometry.name.lowercase()}/${if (layer) "layer" else "root"}", geometry, layer)
            }
        }
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

        val result = renderTwice(surface).first()
        assertAdmits(covered, result.pixels, 7, 2, 2)
        assertAdmits(uncovered, result.pixels, 7, 6, 6)
        assertAdmits(partial, result.pixels, 7, 3, 2)
    }

    @Test
    fun `ordinary Rect and deferred Path share one Solid Opacity source identity`() {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val expectedOrdinary = W5bBlendCpuOracle.point(source, destination, 1f)
        val expected = List(7 * 7) { index ->
            val x = index % 7
            val y = index / 7
            if (x == 5 && y == 5) expectedOrdinary
            else W5bBlendCpuOracle.point(source, destination, coverage(BlendMode.PLUS, Geometry.PATH, x, y))
        }
        expected.forEachIndexed { index, pixel -> requireBounded("shared source pixel ${index % 7},${index / 7}", pixel) }
        requireDisjoint("shared source ordinary/path exterior", expected[5 + 5 * 7], expected[6 + 6 * 7])

        val sourceShader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32)
        val path = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f),
                Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
            drawRect(RectF32.ofLTRB(5f, 5f, 6f, 6f),
                Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = false))
            drawPath(path, Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = true))
        }

        renderTwice(surface).forEachIndexed { renderIndex, result ->
            expected.forEachIndexed { index, pixel ->
                try {
                    assertAdmits(pixel, result.pixels, 7, index % 7, index / 7)
                } catch (failure: Throwable) {
                    throw AssertionError("shared source render $renderIndex pixel ${index % 7},${index / 7}", failure)
                }
            }
        }
    }

    @Test
    fun `shared source exact budget admits then B minus one recovers`() {
        // B is derived before native qualification: root/coverage/snapshot 3*196, AA4 784,
        // aligned readback 7*256, W6 uniform cursor 16, W4d V/I/U floors, D=16 and S=32.
        val budgetB = listOf(196L, 196L, 196L, 784L, 1_792L, 16L,
            16_384L, 4_096L, 4_096L, 16L, 32L).fold(0L, Math::addExact)
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val expectedOrdinary = W5bBlendCpuOracle.point(source, destination, 1f)
        val expected = List(7 * 7) { index ->
            val x = index % 7
            val y = index / 7
            if (x == 5 && y == 5) expectedOrdinary
            else W5bBlendCpuOracle.point(source, destination, coverage(BlendMode.PLUS, Geometry.PATH, x, y))
        }
        expected.forEachIndexed { index, pixel -> requireBounded("budget B pixel ${index % 7},${index / 7}", pixel) }
        val sourceShader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32)
        val recovery = ColorARGB.of(255, 0, 0, 0)
        val recoveryExpected = W5aSolidOpacityCpuOracle.draw(recovery, 1f)
        requireBounded("budget recovery", recoveryExpected)
        val path = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }

        val admitted = Surface(7, 7, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        admitted.canvas {
            drawRect(bounds, Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
            drawRect(RectF32.ofLTRB(5f, 5f, 6f, 6f), Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = false))
            drawPath(path, Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = true))
        }
        renderTwice(admitted).forEach { result ->
            expected.forEachIndexed { index, pixel ->
                assertAdmits(pixel, result.pixels, 7, index % 7, index / 7)
            }
        }

        val refused = Surface(7, 7, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L))
        refused.canvas {
            drawRect(bounds, Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
            drawRect(RectF32.ofLTRB(5f, 5f, 6f, 6f), Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = false))
            drawPath(path, Paint(shader = sourceShader, blendMode = BlendMode.PLUS, antiAlias = true))
        }
        val sentinel = UByteArray(7 * 7 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6a.layer.frame_budget_exceeded:") == true, failure.message)
        assertContentEquals(before, sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(bounds, Paint(recovery, antiAlias = false)) }
        renderTwice(refused).forEach { recovered ->
            assertAdmits(recoveryExpected, recovered.pixels, 7, 3, 3)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("basicCells")
    fun `AA deferred public matrix admits every pixel twice`(
        label: String,
        mode: BlendMode,
        geometry: Geometry,
        layer: Boolean,
    ) {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = if (mode == BlendMode.PLUS)
            W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        else W5bBlendCpuOracle.Draw(ColorARGB.Black, 1f, BlendMode.SRC_OVER)

        // Construct the complete independent oracle before recording the public scene.  The
        // transparent-root restore of the layer variant preserves the black-RGB attachment;
        // attachment UNORM stages are represented by point's envelope at each pixel.
        val expected = List(7 * 7) { index ->
            val x = index % 7
            val y = index / 7
            expectedPixel(source, destination, mode, coverage(mode, geometry, x, y))
        }
        expected.forEachIndexed { index, pixel -> requireBounded("$label pixel ${index % 7},${index / 7}", pixel) }
        val interior = expected[2 + 2 * 7]
        val edge = expected[(if (geometry == Geometry.PATH) 3 else 1) + 2 * 7]
        val exterior = expected[6 + 6 * 7]
        requireDisjoint("$label interior/exterior", interior, exterior)
        if (mode == BlendMode.PLUS) {
            requireDisjoint("$label interior/edge", interior, edge)
            requireDisjoint("$label edge/exterior", edge, exterior)
        }

        val path = Path().apply {
            if (mode == BlendMode.PLUS) {
                moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
            } else {
                addRect(RectF32.ofLTRB(1f, 1f, 5f, 5f))
            }
        }
        val sourcePaint = if (mode == BlendMode.SRC_OVER) {
            // The historical route admits its canonical SrcOver plan, not an explicitly
            // requested fixed-function equivalent.  Keep the Solid/Opacity fixture, which is
            // outside that historical source subset and must take the typed W7 fallback.
            Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), antiAlias = true)
        } else {
            Paint(
                shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32),
                blendMode = mode,
                antiAlias = true,
            )
        }
        val destinationPaint = Paint(shader = Shader.SolidColor(destination.color), antiAlias = false)
        val surface = Surface(7, 7)
        surface.canvas {
            fun Canvas.drawCell() {
                drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), destinationPaint)
                when (geometry) {
                    Geometry.PATH -> drawPath(path, sourcePaint)
                    Geometry.RECT -> drawRect(
                        if (mode == BlendMode.PLUS) RectF32.ofLTRB(1.5f, 1f, 4.5f, 5f)
                        else RectF32.ofLTRB(1f, 1f, 5f, 5f),
                        sourcePaint,
                    )
                }
            }
            if (layer) {
                saveLayer()
                drawCell()
                restore()
            } else {
                drawCell()
            }
        }

        renderTwice(surface).forEachIndexed { renderIndex, result ->
            expected.forEachIndexed { index, pixel ->
                try {
                    assertAdmits(pixel, result.pixels, 7, index % 7, index / 7)
                } catch (failure: Throwable) {
                    throw AssertionError("$label render $renderIndex pixel ${index % 7},${index / 7}", failure)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stencilCells")
    fun `AA deferred stencil matrix preserves winding and even odd coverage twice`(
        label: String,
        geometry: StencilGeometry,
        layer: Boolean,
    ) {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val expected = List(7 * 7) { index ->
            val x = index % 7
            val y = index / 7
            W5bBlendCpuOracle.point(source, destination, stencilCoverage(geometry, x, y))
        }
        expected.forEachIndexed { index, pixel -> requireBounded("$label pixel ${index % 7},${index / 7}", pixel) }
        requireDisjoint("$label interior/notch", expected[1 + 1 * 7], expected[2 + 2 * 7])
        requireDisjoint("$label interior/exterior", expected[1 + 1 * 7], expected[6 + 6 * 7])

        val path = when (geometry) {
            StencilGeometry.CONCAVE_WINDING -> Path().apply {
                moveTo(1f, 1f); lineTo(5f, 1f); lineTo(5f, 2f); lineTo(2f, 2f)
                lineTo(2f, 5f); lineTo(1f, 5f); close()
            }
            StencilGeometry.EVEN_ODD_HOLE -> Path().apply {
                fillType = FillType.EVEN_ODD
                addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f))
                addRect(RectF32.ofLTRB(2f, 2f, 5f, 5f))
            }
        }
        val surface = Surface(7, 7)
        surface.canvas {
            fun Canvas.drawCell() {
                drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
                drawPath(path, Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32),
                    blendMode = BlendMode.PLUS, antiAlias = true))
            }
            if (layer) {
                saveLayer()
                drawCell()
                restore()
            } else {
                drawCell()
            }
        }
        renderTwice(surface).forEachIndexed { renderIndex, result ->
            expected.forEachIndexed { index, pixel ->
                try {
                    assertAdmits(pixel, result.pixels, 7, index % 7, index / 7)
                } catch (failure: Throwable) {
                    throw AssertionError("$label render $renderIndex pixel ${index % 7},${index / 7}", failure)
                }
            }
        }
    }

    private fun coverage(mode: BlendMode, geometry: Geometry, x: Int, y: Int): Float = when (mode) {
        BlendMode.SRC_OVER -> if (x in 1..4 && y in 1..4) 1f else 0f
        BlendMode.PLUS -> when (geometry) {
        Geometry.PATH -> when {
            x >= 1 && y >= 1 && x + y < 5 -> 1f
            x >= 1 && y >= 1 && x + y == 5 -> 128f / 255f
            else -> 0f
        }
        Geometry.RECT -> when {
            y in 1..4 && x in 2..3 -> 1f
            y in 1..4 && x in 1..4 -> .5f
            else -> 0f
        }
        }
        else -> error("Matrix only admits PLUS and SRC_OVER")
    }

    private fun stencilCoverage(geometry: StencilGeometry, x: Int, y: Int): Float = when (geometry) {
        StencilGeometry.CONCAVE_WINDING -> if (y == 1 && x in 1..4 || x == 1 && y in 1..4) 1f else 0f
        StencilGeometry.EVEN_ODD_HOLE -> if (x in 1..5 && y in 1..5 && !(x in 2..4 && y in 2..4)) 1f else 0f
    }

    private fun expectedPixel(
        source: W5bBlendCpuOracle.Draw,
        destination: W5bBlendCpuOracle.Draw,
        mode: BlendMode,
        coverage: Float,
    ): WgslFloatEnvelopeV1Oracle.DrawResult = when (mode) {
        BlendMode.PLUS -> W5bBlendCpuOracle.point(source, destination, coverage)
        BlendMode.SRC_OVER -> W5aSolidOpacityCpuOracle.draw(
            color = source.color,
            // Historical resolved-color SRC_OVER materializes AA coverage in the source alpha
            // before the final full-coverage composite (the diagonal is 128/255, not .5).
            shaderOpacityOuterF32 = source.opacityF32 * coverage,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
                W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32),
            )),
            coverageF32 = 1f,
        )
        else -> error("Matrix only admits PLUS and SRC_OVER")
    }

    private fun requireBounded(label: String, result: WgslFloatEnvelopeV1Oracle.DrawResult):
        WgslFloatEnvelopeV1Oracle.DrawResult.Bounded =
        result as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
            ?: error("$label must be bounded: $result")

    private fun requireDisjoint(
        label: String,
        first: WgslFloatEnvelopeV1Oracle.DrawResult,
        second: WgslFloatEnvelopeV1Oracle.DrawResult,
    ) {
        val firstCodes = requireBounded(label, first).channels
        val secondCodes = requireBounded(label, second).channels
        check(firstCodes.zip(secondCodes).any { (left, right) -> left.intersect(right).isEmpty() }) {
            "$label must have disjoint output-code envelopes"
        }
    }

    private fun renderTwice(surface: Surface): List<RenderResult> {
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
        return listOf(first, second)
    }

    private fun assertAdmits(expected: WgslFloatEnvelopeV1Oracle.DrawResult, pixels: UByteArray,
        width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offset, offset + 4))
    }
}
