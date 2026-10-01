@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
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
        fun basicCells(): List<Array<Any>> = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
            BlendMode.PLUS,
        ).flatMap { mode ->
            listOf(Geometry.PATH, Geometry.RECT).flatMap { geometry ->
                listOf(false, true).map { layer ->
                    arrayOf<Any>("$mode/${geometry.name.lowercase()}/${if (layer) "layer" else "root"}", mode, geometry, layer, false)
                }
            }
        } + listOf(Geometry.PATH, Geometry.RECT).flatMap { geometry ->
            listOf(false, true).map { layer ->
                arrayOf<Any>("SRC_OVER opaque/${geometry.name.lowercase()}/${if (layer) "layer" else "root"}", BlendMode.SRC_OVER, geometry, layer, true)
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

    @Test
    fun `deferred PLUS saturates a fractional edge before storage`() {
        val destinationColor = ColorARGB.of(192, 0, 0, 0)
        val background = requireBounded("saturation background", W5aSolidOpacityCpuOracle.draw(destinationColor, 1f))
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val edge = requireBounded("saturation edge", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(.75f), destination, 128f / 255f,
        ))
        val full = requireBounded("saturation full", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(.75f), destination, 1f,
        ))
        val zero = requireBounded("saturation C=0", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(.75f), destination, 0f,
        ))
        val oldPostLerp = WgslFloatEnvelopeV1Oracle.destinationExclusion(
            blackTable(.75f), MaterialPlanRef(1), destination, BlendMode.PLUS, 128f / 255f,
        )
        requireAlphaDisjoint("saturation edge/V1", edge, oldPostLerp.channels)
        requireAlphaDisjoint("saturation edge/C=0", edge, zero.channels)
        check(full.channels[3].intersect(edge.channels[3]).isNotEmpty()) {
            "saturated full and fractional edges may share alpha code 255"
        }

        val triangle = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destinationColor), antiAlias = false))
            drawPath(triangle, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), .75f),
                blendMode = BlendMode.PLUS, antiAlias = true))
        }
        renderTwice(surface).forEach { result ->
            assertAdmits(full, result.pixels, 7, 2, 2)
            assertAdmits(edge, result.pixels, 7, 3, 2)
            assertAdmits(zero, result.pixels, 7, 6, 6)
        }
    }

    @Test
    fun `deferred PLUS source alpha zero preserves a genuinely rendered background`() {
        val destinationColor = ColorARGB.of(64, 0, 0, 0)
        val background = requireBounded("alpha zero background", W5aSolidOpacityCpuOracle.draw(destinationColor, 1f))
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val zeroSource = requireBounded("alpha zero PLUS", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(0f), destination, 128f / 255f,
        ))
        val nonzeroSource = requireBounded("alpha nonzero PLUS", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(.75f), destination, 128f / 255f,
        ))
        check(zeroSource.channels.zip(background.channels).all { (zero, stored) -> zero.intersect(stored).isNotEmpty() }) {
            "zero source oracle must retain the stored-background codes"
        }
        requireAlphaDisjoint("alpha zero/nonzero source", nonzeroSource, background.channels)

        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destinationColor), antiAlias = false))
            drawPath(triangle(1f, 1f, 5f, 1f, 1f, 5f),
                Paint(shader = Shader.SolidColor(ColorARGB.of(0, 0, 0, 0)), blendMode = BlendMode.PLUS, antiAlias = true))
        }
        renderTwice(surface).forEach { result ->
            assertAdmits(background, result.pixels, 7, 3, 2)
            assertAdmits(background, result.pixels, 7, 6, 6)
        }
    }

    @Test
    fun `deferred PLUS transparent opacity source preserves a genuinely rendered background`() {
        val destinationColor = ColorARGB.of(64, 0, 0, 0)
        val background = requireBounded("transparent opacity background", W5aSolidOpacityCpuOracle.draw(destinationColor, 1f))
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val zeroSource = requireBounded("transparent opacity PLUS", WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(0f), destination, 128f / 255f,
        ))
        check(zeroSource.channels.zip(background.channels).all { (zero, stored) -> zero.intersect(stored).isNotEmpty() }) {
            "transparent opacity source oracle must retain the stored-background codes"
        }
        // The shader is transparently normalized by material planning, but the already-recorded
        // root background must still reach Render/Readback even if this identity draw is elided.
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destinationColor), antiAlias = false))
            drawPath(triangle(1f, 1f, 5f, 1f, 1f, 5f), Paint(
                shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), 0f), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
        }
        renderTwice(surface).forEach { result ->
            assertAdmits(background, result.pixels, 7, 3, 2)
            assertAdmits(background, result.pixels, 7, 6, 6)
        }
    }

    @Test
    fun `two deferred PLUS consumers use the stored intervening destination in recorded order`() {
        val destinationColor = ColorARGB.Black
        val forward = requireBounded("chronology forward overlap", chronologyExpected(3, 1, destinationColor, forward = true))
        val reverse = requireBounded("chronology reverse overlap", chronologyExpected(3, 1, destinationColor, forward = false))
        val stale = chronologyExpected(3, 1, destinationColor, forward = true, includeMiddle = false)
        // At these C=1 coordinates the first triangle is the only draw: exact 0/1 source and
        // opaque black D make sat(C*S+D) an exact UNORM singleton, independent of the broader
        // transfer envelope used for colored fractional coverage.
        val forwardFirst = listOf(255, 0, 0, 255)
        val reverseFirst = listOf(0, 255, 0, 255)
        val omittedFirst = listOf(0, 0, 0, 255)
        requireDisjoint("chronology forward/reverse overlap", forward, reverse)
        requireDisjointFromConservativeExclusion("chronology stale destination", forward, stale)
        check(forwardFirst != omittedFirst)
        check(reverseFirst != omittedFirst)
        val outside = requireBounded("chronology exterior", W5aSolidOpacityCpuOracle.draw(destinationColor, 1f))

        fun record(forwardOrder: Boolean): Surface = Surface(7, 7).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destinationColor), antiAlias = false))
            val first = if (forwardOrder) firstTriangle() else secondTriangle()
            val second = if (forwardOrder) secondTriangle() else firstTriangle()
            drawPath(first, Paint(shader = Shader.SolidColor(if (forwardOrder) ColorARGB.Red else ColorARGB.Green),
                blendMode = BlendMode.PLUS, antiAlias = true))
            drawRect(RectF32.ofLTRB(3f, 1f, 4f, 2f), Paint(shader = Shader.SolidColor(ColorARGB.Black), antiAlias = false))
            drawPath(second, Paint(shader = Shader.SolidColor(if (forwardOrder) ColorARGB.Green else ColorARGB.Red),
                blendMode = BlendMode.PLUS, antiAlias = true))
        } }
        renderTwice(record(forwardOrder = true)).forEach { result ->
            assertAdmits(forward, result.pixels, 7, 3, 1)
            assertExactPixel(forwardFirst, result.pixels, 7, 1, 1)
            assertAdmits(outside, result.pixels, 7, 6, 6)
        }
        renderTwice(record(forwardOrder = false)).forEach { result ->
            assertAdmits(reverse, result.pixels, 7, 3, 1)
            assertExactPixel(reverseFirst, result.pixels, 7, 5, 1)
            assertAdmits(outside, result.pixels, 7, 6, 6)
        }
    }

    @Test
    fun `deferred PLUS preserves translated hard-scissor mapping`() {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val full = requireBounded("mapping full", W5bBlendCpuOracle.point(source, destination, 1f))
        val outside = requireBounded("mapping outside", W5bBlendCpuOracle.point(source, destination, 0f))
        requireDisjoint("mapping full/outside", full, outside)

        val surface = Surface(9, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 9f, 8f), Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
            save()
            clipRect(RectF32.ofLTRB(3f, 2f, 5f, 4f), antiAlias = false)
            translate(2f, 1f)
            drawPath(triangle(0f, 0f, 4f, 0f, 0f, 4f), Paint(
                shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
            restore()
        }
        renderTwice(surface).forEach { result ->
            assertAdmits(full, result.pixels, 9, 3, 2)
            assertAdmits(outside, result.pixels, 9, 2, 2)
            assertAdmits(outside, result.pixels, 9, 5, 2)
        }
    }

    @Test
    fun `deferred PLUS preserves a nonzero plain-layer origin at its true AA edge`() {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        val source = W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
        val full = requireBounded("layer mapping full", W5bBlendCpuOracle.point(source, destination, 1f))
        val edge = requireBounded("layer mapping C128", W5bBlendCpuOracle.point(source, destination, 128f / 255f))
        val doubleCovered = requireBounded("layer mapping C128 squared", W5bBlendCpuOracle.point(source, destination,
            (128f / 255f) * (128f / 255f)))
        val untouchedLayer = requireBounded("layer mapping C0", W5bBlendCpuOracle.point(source, destination, 0f))
        val transparent = requireBounded("layer mapping root exterior", W5aSolidOpacityCpuOracle.draw(ColorARGB.of(0, 0, 0, 0), 1f))
        requireDisjoint("layer mapping full/C0", full, untouchedLayer)
        requireDisjoint("layer mapping C128/C128 squared", edge, doubleCovered)
        requireDisjoint("layer mapping C0/root exterior", untouchedLayer, transparent)

        val surface = Surface(9, 8)
        surface.canvas {
            saveLayer(RectF32.ofLTRB(5f, 4f, 9f, 8f))
            drawRect(RectF32.ofLTRB(5f, 4f, 9f, 8f), Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
            drawPath(triangle(5f, 4f, 9f, 4f, 5f, 8f), Paint(
                shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
            restore()
        }
        renderTwice(surface).forEach { result ->
            assertAdmits(full, result.pixels, 9, 6, 5)
            assertAdmits(edge, result.pixels, 9, 7, 5)
            assertAdmits(untouchedLayer, result.pixels, 9, 8, 7)
            assertAdmits(transparent, result.pixels, 9, 4, 4)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("basicCells")
    fun `AA deferred public matrix admits every pixel twice`(
        label: String,
        mode: BlendMode,
        geometry: Geometry,
        layer: Boolean,
        historicalOpaqueSrcOver: Boolean,
    ) {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.of(64, 0, 0, 0), 1f, BlendMode.SRC_OVER)
        // CLEAR/SRC intentionally use alpha-zero material: C remains semantically observable
        // through the destination interpolation and must not be paint-culled.
        val source = when (mode) {
            BlendMode.CLEAR, BlendMode.SRC -> W5bBlendCpuOracle.Draw(ColorARGB.Transparent, 1f, mode)
            BlendMode.DST -> W5bBlendCpuOracle.Draw(ColorARGB.of(192, 0, 0, 0), 1f, mode)
            BlendMode.SRC_ATOP -> W5bBlendCpuOracle.Draw(ColorARGB.of(192, 255, 0, 0), 1f, mode)
            BlendMode.PLUS -> W5bBlendCpuOracle.coveredPlusPrescaleV2PointFixture().center
            BlendMode.SRC_OVER -> W5bBlendCpuOracle.Draw(
                if (historicalOpaqueSrcOver) ColorARGB.Black else ColorARGB.of(192, 0, 0, 0), 1f, mode,
            )
            else -> W5bBlendCpuOracle.Draw(ColorARGB.of(192, 0, 0, 0), 1f, mode)
        }

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
        if (mode != BlendMode.DST) requireDisjoint("$label interior/exterior", interior, exterior)
        if (mode !in setOf(BlendMode.DST, BlendMode.SRC_OVER, BlendMode.SRC_ATOP)) {
            requireDisjoint("$label interior/edge", interior, edge)
            requireDisjoint("$label edge/exterior", edge, exterior)
        }

        val path = Path().apply {
            if (mode in setOf(BlendMode.SRC_OVER, BlendMode.SRC_ATOP)) addRect(RectF32.ofLTRB(1f, 1f, 5f, 5f))
            else { moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close() }
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
                        if (mode in setOf(BlendMode.SRC_OVER, BlendMode.SRC_ATOP)) RectF32.ofLTRB(1f, 1f, 5f, 5f)
                        else RectF32.ofLTRB(1.5f, 1f, 4.5f, 5f),
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

    @Test
    fun `fractional destination alpha distinguishes full covered SRC IN from DST IN`() {
        val destinationColor = ColorARGB.of(64, 0, 0, 0)
        val sourceColor = ColorARGB.of(192, 255, 0, 0)
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(destinationColor, 1f),
        ))
        fun expected(mode: BlendMode) = requireBounded("$mode distinguishing full pixel",
            WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
                WgslFloatEnvelopeV1Oracle.solidLinearPremul(sourceColor), destination, mode, 1f,
            ))
        val srcIn = expected(BlendMode.SRC_IN)
        val dstIn = expected(BlendMode.DST_IN)
        requireDisjoint("fractional destination SRC_IN/DST_IN", srcIn, dstIn)
        val path = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        fun record(mode: BlendMode): Surface = Surface(7, 7).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(destinationColor), antiAlias = false))
            drawPath(path, Paint(shader = Shader.SolidColor(sourceColor), blendMode = mode, antiAlias = true))
        } }
        renderTwice(record(BlendMode.SRC_IN)).forEach { result -> assertAdmits(srcIn, result.pixels, 7, 2, 2) }
        renderTwice(record(BlendMode.DST_IN)).forEach { result -> assertAdmits(dstIn, result.pixels, 7, 2, 2) }
    }

    @Test
    fun `fixed SRC OVER destination read and AA consume their immediate destination in either order`() {
        val backgroundColor = ColorARGB.of(64, 0, 0, 0)
        val fixedColor = ColorARGB.of(255, 0, 0, 0)
        val deferredColor = ColorARGB.of(192, 0, 0, 0)
        val finalAaColor = ColorARGB.of(192, 255, 0, 0)
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(backgroundColor, 1f),
        ))
        val fixedAfterBackground = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(fixedColor, 1f, destination = background),
        ))
        val destinationReadAfterFixed = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
                WgslFloatEnvelopeV1Oracle.solidLinearPremul(deferredColor), fixedAfterBackground, BlendMode.DST_OUT, 1f,
            ),
        ))
        val forward = requireBounded("fixed then DST_OUT then SRC_IN", WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
            WgslFloatEnvelopeV1Oracle.solidLinearPremul(finalAaColor), destinationReadAfterFixed, BlendMode.SRC_IN, 1f,
        ))
        val deferredAfterBackground = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
                WgslFloatEnvelopeV1Oracle.solidLinearPremul(deferredColor), background, BlendMode.DST_OUT, 1f,
            ),
        ))
        val fixedAfterDeferred = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(fixedColor, 1f, destination = deferredAfterBackground),
        ))
        val reverse = requireBounded("DST_OUT then fixed then SRC_IN", WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
            WgslFloatEnvelopeV1Oracle.solidLinearPremul(finalAaColor), fixedAfterDeferred, BlendMode.SRC_IN, 1f,
        ))
        requireDisjoint("fixed/deferred order", forward, reverse)
        val triangle = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        fun record(forwardOrder: Boolean): Surface = Surface(7, 7).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(shader = Shader.SolidColor(backgroundColor), antiAlias = false))
            if (forwardOrder) drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f),
                Paint(shader = Shader.SolidColor(fixedColor), blendMode = BlendMode.SRC_OVER, antiAlias = false))
            drawPath(triangle, Paint(shader = Shader.SolidColor(deferredColor), blendMode = BlendMode.DST_OUT, antiAlias = true))
            if (!forwardOrder) drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f),
                Paint(shader = Shader.SolidColor(fixedColor), blendMode = BlendMode.SRC_OVER, antiAlias = false))
            drawPath(triangle, Paint(shader = Shader.SolidColor(finalAaColor), blendMode = BlendMode.SRC_IN, antiAlias = true))
        } }
        renderTwice(record(forwardOrder = true)).forEach { result -> assertAdmits(forward, result.pixels, 7, 2, 2) }
        renderTwice(record(forwardOrder = false)).forEach { result -> assertAdmits(reverse, result.pixels, 7, 2, 2) }
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

    private fun coverage(mode: BlendMode, geometry: Geometry, x: Int, y: Int): Float {
        if (mode in setOf(BlendMode.SRC_OVER, BlendMode.SRC_ATOP)) return if (x in 1..4 && y in 1..4) 1f else 0f
        return when (geometry) {
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
    }

    private fun stencilCoverage(geometry: StencilGeometry, x: Int, y: Int): Float = when (geometry) {
        StencilGeometry.CONCAVE_WINDING -> if (y == 1 && x in 1..4 || x == 1 && y in 1..4) 1f else 0f
        StencilGeometry.EVEN_ODD_HOLE -> if (x in 1..5 && y in 1..5 && !(x in 2..4 && y in 2..4)) 1f else 0f
    }

    private fun chronologyExpected(
        x: Int,
        y: Int,
        destinationColor: ColorARGB,
        forward: Boolean,
        includeMiddle: Boolean = true,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        var result = W5aSolidOpacityCpuOracle.draw(destinationColor, 1f)
        fun plus(color: Array<WgslFloatEnvelopeV1Oracle.Interval>, coverage: Float) {
            result = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
                color, requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(result)), coverage,
            )
        }
        fun middle() {
            result = W5aSolidOpacityCpuOracle.draw(ColorARGB.Black, 1f,
                destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(result)))
        }
        val firstCoverage = firstTriangleCoverage(x, y)
        val secondCoverage = secondTriangleCoverage(x, y)
        if (forward) {
            plus(redIntervals(), firstCoverage)
            if (includeMiddle && x == 3 && y == 1) middle()
            plus(greenIntervals(), secondCoverage)
        } else {
            plus(greenIntervals(), secondCoverage)
            if (includeMiddle && x == 3 && y == 1) middle()
            plus(redIntervals(), firstCoverage)
        }
        return result
    }

    private fun firstTriangle(): Path = triangle(1f, 1f, 5f, 1f, 1f, 5f)

    private fun secondTriangle(): Path = triangle(3f, 1f, 7f, 1f, 3f, 5f)

    private fun triangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Path = Path().apply {
        moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); close()
    }

    private fun firstTriangleCoverage(x: Int, y: Int): Float = when {
        x >= 1 && y >= 1 && x + y < 5 -> 1f
        x >= 1 && y >= 1 && x + y == 5 -> 128f / 255f
        else -> 0f
    }

    private fun secondTriangleCoverage(x: Int, y: Int): Float = when {
        x >= 3 && y >= 1 && x + y < 7 -> 1f
        x >= 3 && y >= 1 && x + y == 7 -> 128f / 255f
        else -> 0f
    }

    private fun blackIntervals(opacityF32: Float) = arrayOf(
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
    )

    private fun greenIntervals(opacityF32: Float = 1f) = arrayOf(
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
    )

    private fun redIntervals() = arrayOf(
        WgslFloatEnvelopeV1Oracle.Interval.input(1f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(1f),
    )


    private fun blackTable(opacityF32: Float) = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(0f, 0f, 0f, 1f))),
        MaterialPlanEntry(MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(opacityF32)),
    ))

    private fun requireAlphaDisjoint(
        label: String,
        expected: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        counterfactual: List<Set<Int>>,
    ) {
        check(expected.channels[3].intersect(counterfactual[3]).isEmpty()) {
            "$label must have disjoint alpha-code envelopes: ${expected.channels[3]} versus ${counterfactual[3]}"
        }
    }

    private fun expectedPixel(
        source: W5bBlendCpuOracle.Draw,
        destination: W5bBlendCpuOracle.Draw,
        mode: BlendMode,
        coverage: Float,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        if (mode == BlendMode.PLUS) return W5bBlendCpuOracle.point(source, destination, coverage)
        val destinationAttachment = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32),
        ))
        return WgslFloatEnvelopeV1Oracle.coveredPorterDuffV1(
            WgslFloatEnvelopeV1Oracle.solidLinearPremul(source.color, source.opacityF32),
            destinationAttachment, mode, coverage,
        )
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

    /**
     * A stale-destination calculation is intentionally a non-emitted counterfactual.  Its
     * envelope can span more than two adjacent stored codes, but the oracle still supplies the
     * conservative codes it excludes; do not pretend it is a bounded render expectation.
     */
    private fun requireDisjointFromConservativeExclusion(
        label: String,
        emitted: WgslFloatEnvelopeV1Oracle.DrawResult,
        counterfactual: WgslFloatEnvelopeV1Oracle.DrawResult,
    ) {
        val emittedCodes = requireBounded(label, emitted).channels
        val excludedCodes = when (counterfactual) {
            is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded -> counterfactual.channels
            is WgslFloatEnvelopeV1Oracle.DrawResult.Unbounded ->
                requireNotNull(counterfactual.exclusionOnlyChannels) { "$label lacks conservative exclusions" }
            is WgslFloatEnvelopeV1Oracle.DrawResult.DomainUnbounded ->
                error("$label counterfactual has unbounded domain: ${counterfactual.reason}")
            is WgslFloatEnvelopeV1Oracle.DrawResult.FixtureUnbounded ->
                error("$label counterfactual is not a finite fixture: ${counterfactual.reason}")
        }
        check(emittedCodes.zip(excludedCodes).any { (actual, stale) -> actual.intersect(stale).isEmpty() }) {
            "$label must exclude the stale-destination code envelope"
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

    private fun assertExactPixel(expected: List<Int>, pixels: UByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        val actual = (0 until 4).map { pixels[offset + it].toInt() and 0xff }
        assertContentEquals(expected, actual)
    }
}
