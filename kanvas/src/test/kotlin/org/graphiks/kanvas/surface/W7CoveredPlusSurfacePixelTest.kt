@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.geometry.Path
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** Public native witnesses for the selected covered-PLUS law, sat(C*S + D). */
class W7CoveredPlusSurfacePixelTest {
    @AfterEach fun disposeGpuRuntime() = GPUBackendRuntimeFactory.dispose()

    @Test fun `analytic AA Rect pre-scales green PLUS source before saturation`() {
        val background = background()
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val source = greenSource()
        val edge = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(greenIntervals(), destination, .5f)
        val oldLaw = WgslFloatEnvelopeV1Oracle.destinationExclusion(
            source, MaterialPlanRef(1), destination, BlendMode.PLUS, .5f,
        )
        assertDisjoint(edge, oldLaw)
        val full = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(greenIntervals(), destination, 1f)
        val picture = PictureRecorder().also { recorder -> recorder.beginRecording(BOUNDS).apply {
            drawRect(BOUNDS, Paint(shader = Shader.SolidColor(BACKGROUND_COLOR), antiAlias = true))
            drawRect(RectF32.ofLTRB(.5f, 0f, 2f, 2f), Paint(
                shader = sourceShader(), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
        } }.finishRecordingAsPicture()
        val surface = Surface(5, 5).also { target -> target.canvas { picture.playback(this) } }
        val result = renderTwice(surface)
        assertAdmits(edge, result.pixels, 0, 1)
        assertAdmits(full, result.pixels, 1, 1)
        assertAdmits(background, result.pixels, 4, 4)
    }

    @Test fun `W4e encoded scalar mask pre-scales green PLUS source before saturation`() {
        val background = background()
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val source = greenSource()
        // The W4e producer stores the three-quarter difference mask as UNORM code 191.
        val encodedCoverage = 191f / 255f
        val edge = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            greenIntervals(), destination, encodedCoverage, scalarMask = true,
        )
        val full = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            greenIntervals(), destination, 1f, scalarMask = true,
        )
        val oldLaw = WgslFloatEnvelopeV1Oracle.destinationExclusion(
            source, MaterialPlanRef(1), destination, BlendMode.PLUS, encodedCoverage, scalarMask = true,
        )
        assertDisjoint(edge, oldLaw)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(W4E_BOUNDS).drawPath(Path().apply {
                addRect(RectF32.ofLTRB(-1f, -1f, 5f, 5f))
            }, Paint(
                shader = sourceShader(), blendMode = BlendMode.PLUS, antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val surface = Surface(4, 4).also { target -> target.canvas {
            drawPath(Path().apply { addRect(RectF32.ofLTRB(-1f, -1f, 5f, 5f)) },
                Paint(shader = Shader.SolidColor(BACKGROUND_COLOR), antiAlias = false))
            save()
            clipPath(Path().apply { moveTo(0f, 0f); lineTo(4f, 0f); lineTo(0f, 4f); close() }, antiAlias = false)
            clipRect(RectF32.ofLTRB(.5f, .5f, 4f, 4f), org.graphiks.kanvas.pipeline.ClipOp.DIFFERENCE, antiAlias = true)
            picture.playback(this)
            restore()
        } }
        val result = renderTwice(surface)
        WgslFloatEnvelopeV1Oracle.assertAdmits(edge, result.pixels.copyOfRange(0, 4))
        WgslFloatEnvelopeV1Oracle.assertAdmits(full, result.pixels.copyOfRange(4, 8))
        WgslFloatEnvelopeV1Oracle.assertAdmits(background, result.pixels.copyOfRange(60, 64))
    }

    @Test fun `covered PLUS controls retain zero source zero coverage full coverage and a nonsaturating edge`() {
        // Black RGB keeps the existing V2 EOTF envelope exact while fractional alpha
        // independently exercises the non-clamping C * S addition.
        val controlBackground = ColorARGB.of(64, 0, 0, 0)
        val controlSourceOpacity = .25f
        val background = W5aSolidOpacityCpuOracle.draw(controlBackground, 1f)
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val edge = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(controlSourceOpacity), destination, .5f,
        )
        val full = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(controlSourceOpacity), destination, 1f,
        )
        val nonSaturating = Surface(5, 5).also { target -> target.canvas {
            drawRect(BOUNDS, Paint(shader = Shader.SolidColor(controlBackground), antiAlias = true))
            drawRect(RectF32.ofLTRB(.5f, 0f, 2f, 2f), Paint(
                shader = blackSourceShader(controlSourceOpacity), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
        } }
        val nonSaturatingResult = renderTwice(nonSaturating)
        assertAdmits(edge, nonSaturatingResult.pixels, 0, 1)
        assertAdmits(full, nonSaturatingResult.pixels, 1, 1)
        assertAdmits(background, nonSaturatingResult.pixels, 4, 4)

        val zeroSource = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(blackIntervals(0f), destination, .5f)
        // The outside sample has the same result as a covered PLUS draw at C=0: no source
        // contribution reaches the attachment, independently of the zero-source control above.
        val zeroCoverage = WgslFloatEnvelopeV1Oracle.coveredPlusPrescaleV2(
            blackIntervals(controlSourceOpacity), destination, 0f,
        )
        val zero = Surface(5, 5).also { target -> target.canvas {
            drawRect(BOUNDS, Paint(shader = Shader.SolidColor(controlBackground), antiAlias = true))
            drawRect(RectF32.ofLTRB(.5f, 0f, 2f, 2f), Paint(
                shader = blackSourceShader(0f), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
        } }
        val zeroResult = renderTwice(zero)
        assertAdmits(zeroSource, zeroResult.pixels, 0, 1)
        assertAdmits(zeroCoverage, zeroResult.pixels, 4, 4)
    }

    @Test fun `covered PLUS low-budget refusal leaves the same Surface recoverable`() {
        // 128² target + aligned readback + full-width snapshot each reserve 64 KiB.
        // The existing W5b 180 KiB window admits a plain root draw (128 KiB plus
        // bounded geometry/material), while this covered destination read exceeds it.
        val bounds = RectF32.ofLTRB(0f, 0f, 128f, 128f)
        val surface = Surface(128, 128, config = RenderConfig(frameLocalBudgetBytes = 180L * 1024L))
        val rejected = PictureRecorder().also { recorder -> recorder.beginRecording(bounds).apply {
            drawRect(bounds, Paint(shader = Shader.SolidColor(BACKGROUND_COLOR), antiAlias = true))
            drawRect(RectF32.ofLTRB(.5f, 0f, 128f, 128f), Paint(
                shader = sourceShader(), blendMode = BlendMode.PLUS, antiAlias = true,
            ))
        } }.finishRecordingAsPicture()
        surface.canvas { rejected.playback(this) }
        val sentinel = UByteArray(128 * 128 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message.orEmpty().contains("resource-limit.w5b.destination-budget"), failure.message)
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
        val recovered = renderTwice(surface)
        assertContentEquals(UByteArray(128 * 128 * 4) { channel -> if (channel % 4 in 2..3) 255u else 0u }, recovered.pixels)
    }

    private fun renderTwice(surface: Surface): RenderResult {
        val first = surface.render()
        assertTrue(first.isClean, first.diagnostics.summary())
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertTrue(first.stats.opsDispatched > 0, "native render must dispatch the recorded draws")
        val second = surface.render()
        assertTrue(second.isClean, second.diagnostics.summary())
        assertTrue(second.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), second.nativeEvidenceScopeKinds.toString())
        assertTrue(second.stats.opsDispatched > 0, "repeated native render must dispatch the recorded draws")
        assertContentEquals(first.pixels, second.pixels)
        return first
    }

    private fun assertAdmits(expected: WgslFloatEnvelopeV1Oracle.DrawResult, pixels: UByteArray, x: Int, y: Int) {
        val offset = (y * 5 + x) * 4
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offset, offset + 4))
    }

    private fun assertDisjoint(expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        counterfactual: WgslFloatEnvelopeV1Oracle.ConservativeExclusion) {
        check(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "Expected: $expected" }
        check(expected.channels.zip(counterfactual.channels).any { (actual, old) -> actual.intersect(old).isEmpty() }) {
            "V2 covered PLUS must be disjoint from V1 post-lerp: ${expected.channels} versus ${counterfactual.channels}"
        }
    }

    private fun background() = W5aSolidOpacityCpuOracle.draw(BACKGROUND_COLOR, 1f)
    private fun greenSource(opacityF32: Float = SOURCE_OPACITY) = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(0f, 1f, 0f, 1f))),
        MaterialPlanEntry(MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(opacityF32)),
    ))
    private fun greenIntervals(opacityF32: Float = SOURCE_OPACITY) = arrayOf(
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
    )
    private fun sourceShader(opacityF32: Float = SOURCE_OPACITY) = Shader.Opacity(Shader.SolidColor(ColorARGB.Green), opacityF32)
    private fun blackSourceShader(opacityF32: Float) = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), opacityF32)
    private fun blackIntervals(opacityF32: Float) = arrayOf(
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(0f),
        WgslFloatEnvelopeV1Oracle.Interval.input(opacityF32),
    )

    private companion object {
        val BOUNDS: RectF32 = RectF32.ofLTRB(0f, 0f, 5f, 5f)
        val W4E_BOUNDS: RectF32 = RectF32.ofLTRB(0f, 0f, 4f, 4f)
        val BACKGROUND_COLOR: ColorARGB = ColorARGB.of(191, 0, 255, 0)
        const val SOURCE_OPACITY = .75f
    }
}
