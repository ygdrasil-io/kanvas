@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Public causal witnesses for the closed W7 inverse-AA Picture coverage source. */
class W7InverseAaPictureSurfacePixelTest {
    companion object {
        private val pictureBounds = RectF32.ofLTRB(-1f, -1f, 8f, 8f)
        private val explicitClip = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        private val black128 = ColorARGB.of(128, 0, 0, 0)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @JvmStatic
        fun alphaOccurrenceCells(): List<Array<Any>> = inverseFillCells("alpha-once")

        @JvmStatic
        fun windingParityCells(): List<Array<Any>> = inverseFillCells("winding-parity")

        private fun inverseFillCells(prefix: String): List<Array<Any>> = listOf(false, true).flatMap { wire ->
            listOf(FillType.INVERSE_WINDING, FillType.INVERSE_EVEN_ODD).map { fillType ->
                arrayOf<Any>("$prefix/${if (wire) "wire" else "memory"}/$fillType", wire, fillType)
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("alphaOccurrenceCells")
    fun `inverse AA Picture applies alpha once at two translated occurrences`(
        label: String,
        wire: Boolean,
        fillType: FillType,
    ) {
        // The hard clip restricts the inverse AA source to exactly C0 and its two-of-four
        // sample C128 edge. Both complete-RGBA expectations are fixed before the Surface.
        val expectations = alphaExpectations(label)

        val picture = inverseTrianglePicture(wire, fillType)
        val surface = Surface(17, 9).also { target -> target.canvas {
            save(); translate(1f, 1f); drawPicture(picture); restore()
            save(); translate(9f, 1f); drawPicture(picture); restore()
        } }

        val first = surface.renderAndRepeat(label)
        for (yI32 in 0 until 9) for (xI32 in 0 until 17) {
            val localXI32 = if (xI32 >= 8) xI32 - 9 else xI32 - 1
            val localYI32 = yI32 - 1
            val expected = alphaExpectationAt(localXI32, localYI32, expectations)
            assertExpected("$label/$xI32,$yI32", expected, first.pixels, 17, xI32, yI32)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windingParityCells")
    fun `inverse AA Picture preserves winding versus parity`(
        label: String,
        wire: Boolean,
        fillType: FillType,
    ) {
        val opaqueStates = opaqueStates()
        val picture = inverseNestedRectsPicture(wire, fillType)
        val surface = Surface(17, 9).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 9f), Paint(ColorARGB.Blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(8f, 0f, 17f, 9f), Paint(ColorARGB.Red, antiAlias = false))
            save(); translate(1f, 1f); drawPicture(picture); restore()
            save(); translate(9f, 1f); drawPicture(picture); restore()
        } }

        val first = surface.renderAndRepeat(label)
        for (yI32 in 0 until 9) for (xI32 in 0 until 17) {
            val background = if (xI32 < 8) ColorARGB.Blue else ColorARGB.Red
            val localXI32 = if (xI32 < 8) xI32 - 1 else xI32 - 9
            val localYI32 = yI32 - 1
            val insideClip = localXI32 in 0..6 && localYI32 in 0..6
            val outerInterior = localXI32 in 1..5 && localYI32 in 1..5
            val innerInterior = localXI32 in 2..4 && localYI32 in 2..4
            val white = insideClip && (!outerInterior || (innerInterior && fillType == FillType.INVERSE_EVEN_ODD))
            val expectedColor = if (white) ColorARGB.White else background
            val expectedState = when (expectedColor) {
                ColorARGB.White -> opaqueStates.white
                ColorARGB.Blue -> opaqueStates.blue
                ColorARGB.Red -> opaqueStates.red
                else -> error("unexpected opaque expectation $expectedColor")
            }
            assertOpaquePixel(first.pixels, 17, xI32, yI32, expectedState, expectedColor, "$label/$xI32,$yI32")
        }
    }

    private fun inverseTrianglePicture(wire: Boolean, fillType: FillType): Picture {
        val recorded = PictureRecorder().also { recorder -> recorder.beginRecording(pictureBounds).apply {
            clipRect(RectF32.ofLTRB(1f, 1f, 2f, 5f), ClipOp.INTERSECT, antiAlias = false)
            drawPath(Path().apply {
                moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
                this.fillType = fillType
            }, Paint(black128, blendMode = BlendMode.SRC_OVER, antiAlias = true))
        } }.finishRecordingAsPicture()
        return if (wire) requireNotNull(Picture.fromByteArray(recorded.toByteArray())) else recorded
    }

    private fun inverseNestedRectsPicture(wire: Boolean, fillType: FillType): Picture {
        val recorded = PictureRecorder().also { recorder -> recorder.beginRecording(pictureBounds).apply {
            clipRect(explicitClip, ClipOp.INTERSECT, antiAlias = false)
            drawPath(Path().apply {
                // addRect is intentionally called in the same order for both contours.
                addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f))
                addRect(RectF32.ofLTRB(2f, 2f, 5f, 5f))
                this.fillType = fillType
            }, Paint(ColorARGB.White, blendMode = BlendMode.SRC_OVER, antiAlias = true))
        } }.finishRecordingAsPicture()
        return if (wire) requireNotNull(Picture.fromByteArray(recorded.toByteArray())) else recorded
    }

    private data class AlphaExpectations(
        val clear: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        val edge: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
    )

    private data class OpaqueStates(
        val blue: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        val red: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        val white: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
    )

    private fun alphaExpectations(label: String): AlphaExpectations {
        val clear = WgslFloatEnvelopeV1Oracle.clearAttachment()
        val coverageF32 = 128f / 255f
        val zero = requireBounded("$label/C0", W5aSolidOpacityCpuOracle.draw(
            black128, 1f, destination = clear, coverageF32 = 0f,
        ))
        val edge = requireBounded("$label/C128", W5aSolidOpacityCpuOracle.draw(
            black128, 1f, destination = clear, coverageF32 = coverageF32,
        ))
        requireAlphaDisjoint("$label/C128-C0", edge, zero.channels)

        val material = black128MaterialTable()
        val root0 = MaterialPlanRef(0)
        val root1 = MaterialPlanRef(1)
        requireAlphaDisjoint("$label/C128-hard-C1", edge,
            WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, root0, clear, 1f).channels)
        requireAlphaDisjoint("$label/C128-C2", edge,
            WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, root0, clear, coverageF32 * coverageF32).channels)
        requireAlphaDisjoint("$label/C128-extra-material-alpha", edge,
            WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, root1, clear, coverageF32).channels)
        requireAlphaDisjoint("$label/C128-second-edge-SrcOver", edge,
            WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, root0,
                requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(edge)), coverageF32).channels)
        return AlphaExpectations(zero, edge)
    }

    private fun opaqueStates(): OpaqueStates = OpaqueStates(
        blue = requireBounded("opaque blue", W5aSolidOpacityCpuOracle.draw(ColorARGB.Blue, 1f)),
        red = requireBounded("opaque red", W5aSolidOpacityCpuOracle.draw(ColorARGB.Red, 1f)),
        white = requireBounded("opaque white", W5aSolidOpacityCpuOracle.draw(ColorARGB.White, 1f)),
    )

    private fun alphaExpectationAt(
        localXI32: Int,
        localYI32: Int,
        expectations: AlphaExpectations,
    ): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded = if (localXI32 == 1 && localYI32 == 4)
        expectations.edge else expectations.clear

    private fun black128MaterialTable(): MaterialPlanTable = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(
            MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(0f, 0f, 0f, black128.alphaNormalized)),
        ),
        MaterialPlanEntry(
            MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(black128.alphaNormalized),
        ),
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

    private fun requireBounded(
        label: String,
        result: WgslFloatEnvelopeV1Oracle.DrawResult,
    ): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded = result as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        ?: error("$label must be bounded: $result")

    private fun Surface.renderAndRepeat(label: String): RenderResult {
        val first = render()
        assertNativeRenderAndReadback(label, first)
        val second = render()
        assertNativeRenderAndReadback("$label/repeat", second)
        assertContentEquals(first.pixels, second.pixels, "$label retained Surface frame must be byte-identical")
        return first
    }

    private fun assertNativeRenderAndReadback(label: String, result: RenderResult) {
        val trace = "$label diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.diagnostics.isEmpty, trace)
        assertEquals(setOf("Render", "LayerComposite", "Readback"), result.nativeEvidenceScopeKinds.toSet(), trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertEquals(0, result.stats.opsRefused, trace)
    }

    private fun assertExpected(
        label: String,
        expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        pixels: UByteArray,
        widthI32: Int,
        xI32: Int,
        yI32: Int,
    ) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        try {
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
        } catch (failure: Throwable) {
            throw AssertionError("$label pixel", failure)
        }
    }

    private fun assertPixel(
        pixels: UByteArray,
        widthI32: Int,
        xI32: Int,
        yI32: Int,
        color: ColorARGB,
        label: String,
    ) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertContentEquals(
            ubyteArrayOf(
                color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte(),
            ),
            pixels.copyOfRange(offsetI32, offsetI32 + 4),
            "$label pixel",
        )
    }

    private fun assertOpaquePixel(
        pixels: UByteArray,
        widthI32: Int,
        xI32: Int,
        yI32: Int,
        expected: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        color: ColorARGB,
        label: String,
    ) {
        assertExpected(label, expected, pixels, widthI32, xI32, yI32)
        assertPixel(pixels, widthI32, xI32, yI32, color, label)
    }
}
