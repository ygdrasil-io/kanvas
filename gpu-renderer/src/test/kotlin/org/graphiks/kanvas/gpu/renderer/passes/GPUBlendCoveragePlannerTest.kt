package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GPUBlendCoveragePlannerTest {
    private val planner = GPUBlendPlanner()

    @Test
    fun `canonical identity has exactly 29 unique labels`() {
        assertEquals(29, GPUBlendMode.entries.size)
        assertEquals(29, GPUBlendMode.entries.map(GPUBlendMode::gpuLabel).toSet().size)
    }

    @Test
    fun `canonical planner owns the target format gate`() {
        val plan = planner.plan(
            request(GPUBlendMode.SRC, GPUCoverageConsumption.FullOrScissor).copy(
                target = GPUTargetBlendFacts(
                    formatClass = "rgba32uint",
                    clampsNormalizedColorWrites = false,
                    premultipliedAlpha = true,
                ),
            ),
        )

        assertEquals(
            "unsupported.target.format_blend_incompatible",
            assertIs<GPUBlendPlan.UnsupportedBlend>(plan).diagnostic.code,
        )
    }

    @Test
    fun `canonical planner refuses active attachment sampling for fixed and destination-reading modes`() {
        listOf(GPUBlendMode.SRC_OVER, GPUBlendMode.MULTIPLY).forEach { mode ->
            val plan = planner.plan(
                request(mode, GPUCoverageConsumption.FullOrScissor).copy(
                    activeAttachmentSampled = true,
                ),
            )

            assertEquals(
                "unsupported.destination_read.active_attachment_sampled",
                assertIs<GPUBlendPlan.UnsupportedBlend>(plan).diagnostic.code,
                mode.gpuLabel,
            )
            assertEquals(GPUBlendDestinationReadRequirement.Refused, plan.destinationReadRequirement)
        }
    }

    @Test
    fun `every canonical mode follows the exhaustive coverage alpha target and sample matrix`() {
        GPUBlendMode.entries.forEach { mode ->
            coverageCases.forEach { case ->
                sourceAlphaCases.forEach { sourceAlpha ->
                    targetCases.forEach { target ->
                        val request = GPUBlendSpecializationRequest(
                            mode = mode,
                            coverage = case.coverage,
                            sourceAlpha = sourceAlpha,
                            target = target,
                            samplePlan = case.samplePlan,
                        )

                        val expected = expectedRoute(request)
                        val actual = planner.plan(request)
                        val context = "mode=${mode.gpuLabel},coverage=${case.coverage}," +
                            "alpha=$sourceAlpha,target=${target.formatClass},samplePlan=${case.samplePlan}"

                        assertEquals(expected.signature, actual.signature(), context)
                        assertEquals(expected.destinationReadRequirement, actual.destinationReadRequirement, context)
                        assertEquals(expected.coverageEncoding, actual.sourceCoverageEncoding, context)
                        if (expected.stateId != null) {
                            assertFixedFunctionState(expected.stateId, assertIs<GPUBlendPlan.FixedFunctionBlend>(actual).state, context)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `scalar opaque specialization is limited to the five approved upgrades`() {
        val expectedOpaqueUpgrades = mapOf(
            GPUBlendMode.SRC to "FF(modulate_one_isa)",
            GPUBlendMode.SRC_IN to "FF(modulate_da_isa)",
            GPUBlendMode.DST_IN to "N",
            GPUBlendMode.SRC_OUT to "FF(modulate_ida_isa)",
            GPUBlendMode.DST_ATOP to "FF(modulate_ida_one)",
        )

        val actualUpgrades = GPUBlendMode.entries.mapNotNull { mode ->
            val translucent = planner.plan(request(mode, GPUCoverageConsumption.ScalarCoverage)).signature()
            val opaque = planner.plan(
                request(
                    mode = mode,
                    coverage = GPUCoverageConsumption.ScalarCoverage,
                    sourceAlpha = GPUSourceAlphaClassification.ProvenOpaque,
                ),
            ).signature()
            if (opaque != translucent) mode to opaque else null
        }.toMap()

        assertEquals(expectedOpaqueUpgrades, actualUpgrades)
    }

    @Test
    fun `layer ordering wraps rather than reinterprets the canonical child plan`() {
        val childRequest = request(GPUBlendMode.MULTIPLY, GPUCoverageConsumption.ScalarCoverage)
        val child = planner.plan(childRequest)
        val wrapped = planner.plan(childRequest.copy(layerOrderingToken = "layer:restore:7"))

        val layer = assertIs<GPUBlendPlan.LayerCompositeBlend>(wrapped)
        assertEquals(child, layer.child)
        assertEquals("layer:restore:7", layer.layerOrderingToken)
        assertEquals(child.destinationReadRequirement, layer.destinationReadRequirement)
        assertEquals(child.sourceCoverageEncoding, layer.sourceCoverageEncoding)
    }

    @Test
    fun `only exact single sample frame accepts scalar destination read`() {
        val exact = planner.plan(
            request(GPUBlendMode.MULTIPLY, GPUCoverageConsumption.ScalarCoverage),
        )
        val continuations = listOf(
            GPUSamplePlan.MultisampleFrame(4),
            GPUSamplePlan.LocalResolveApproximation(sourceSampleCount = 4),
        )

        assertIs<GPUBlendPlan.ShaderBlendWithDstRead>(exact)
        continuations.forEach { samplePlan ->
            val refused = planner.plan(
                request(GPUBlendMode.MULTIPLY, GPUCoverageConsumption.ScalarCoverage).copy(
                    samplePlan = samplePlan,
                ),
            )
            assertEquals(
                "unsupported.blend.msaa_destination_read_exactness",
                assertIs<GPUBlendPlan.UnsupportedBlend>(refused).diagnostic.code,
            )
        }
    }
}

private data class CoverageCase(
    val coverage: GPUCoverageConsumption,
    val samplePlan: GPUSamplePlan,
)

private val coverageCases = listOf(
    CoverageCase(GPUCoverageConsumption.FullOrScissor, GPUSamplePlan.SingleSampleFrame),
    CoverageCase(GPUCoverageConsumption.FullOrScissor, GPUSamplePlan.MultisampleFrame(4)),
    CoverageCase(GPUCoverageConsumption.FullOrScissor, GPUSamplePlan.LocalResolveApproximation(4)),
    CoverageCase(GPUCoverageConsumption.ScalarCoverage, GPUSamplePlan.SingleSampleFrame),
    CoverageCase(GPUCoverageConsumption.ScalarCoverage, GPUSamplePlan.MultisampleFrame(4)),
    CoverageCase(GPUCoverageConsumption.ScalarCoverage, GPUSamplePlan.LocalResolveApproximation(4)),
    CoverageCase(GPUCoverageConsumption.StencilCoverage1x, GPUSamplePlan.SingleSampleFrame),
    CoverageCase(GPUCoverageConsumption.MultisampleAttachmentCoverage, GPUSamplePlan.MultisampleFrame(4)),
    CoverageCase(GPUCoverageConsumption.LCDCoverage, GPUSamplePlan.SingleSampleFrame),
    CoverageCase(GPUCoverageConsumption.LCDCoverage, GPUSamplePlan.MultisampleFrame(4)),
)

private val sourceAlphaCases = GPUSourceAlphaClassification.entries

private val targetCases = listOf(
    GPUTargetBlendFacts(
        formatClass = "rgba8unorm",
        clampsNormalizedColorWrites = true,
        premultipliedAlpha = true,
    ),
    GPUTargetBlendFacts(
        formatClass = "rgba16float",
        clampsNormalizedColorWrites = false,
        premultipliedAlpha = true,
    ),
)

private data class ExpectedRoute(
    val signature: String,
    val destinationReadRequirement: GPUBlendDestinationReadRequirement,
    val coverageEncoding: GPUSourceCoverageEncoding,
    val stateId: String? = null,
)

private fun expectedRoute(request: GPUBlendSpecializationRequest): ExpectedRoute {
    if (request.mode == GPUBlendMode.DST) return noOp()

    if (request.coverage == GPUCoverageConsumption.LCDCoverage) {
        return if (request.samplePlan is GPUSamplePlan.SingleSampleFrame) {
            shader("lcd.${request.mode.gpuLabel}@v1", GPUSourceCoverageEncoding.LCDCoverageInShader)
        } else {
            refused("unsupported.blend.lcd_msaa_exactness")
        }
    }

    val matrixCoverage = when (request.coverage) {
        GPUCoverageConsumption.FullOrScissor,
        GPUCoverageConsumption.StencilCoverage1x,
        GPUCoverageConsumption.MultisampleAttachmentCoverage,
        -> MatrixCoverage.Full
        GPUCoverageConsumption.ScalarCoverage -> MatrixCoverage.Scalar
        GPUCoverageConsumption.LCDCoverage -> error("handled above")
    }
    val matrixRoute = when (matrixCoverage) {
        MatrixCoverage.Full -> expectedFullRoute(request.mode, request.target.clampsNormalizedColorWrites)
        MatrixCoverage.Scalar -> expectedScalarRoute(request.mode, request.sourceAlpha)
    }

    if (request.samplePlan !is GPUSamplePlan.SingleSampleFrame && matrixRoute.destinationReadRequirement ==
        GPUBlendDestinationReadRequirement.DestinationTextureRequired
    ) {
        return refused("unsupported.blend.msaa_destination_read_exactness")
    }
    return matrixRoute
}

private fun expectedFullRoute(mode: GPUBlendMode, clampsNormalizedColorWrites: Boolean): ExpectedRoute =
    when (mode) {
        GPUBlendMode.CLEAR -> fixed("zero_zero")
        GPUBlendMode.SRC -> fixed("one_zero")
        GPUBlendMode.DST -> noOp()
        GPUBlendMode.SRC_OVER -> fixed("one_isa")
        GPUBlendMode.DST_OVER -> fixed("ida_one")
        GPUBlendMode.SRC_IN -> fixed("da_zero")
        GPUBlendMode.DST_IN -> fixed("zero_sa")
        GPUBlendMode.SRC_OUT -> fixed("ida_zero")
        GPUBlendMode.DST_OUT -> fixed("zero_isa")
        GPUBlendMode.SRC_ATOP -> fixed("da_isa")
        GPUBlendMode.DST_ATOP -> fixed("ida_sa")
        GPUBlendMode.XOR -> fixed("ida_isa")
        GPUBlendMode.PLUS -> if (clampsNormalizedColorWrites) fixed("one_one_clamped") else shader("plus_exact@v1")
        GPUBlendMode.MODULATE -> fixed("zero_sc")
        GPUBlendMode.SCREEN -> fixed("one_isc")
        else -> shader("${mode.gpuLabel}@v1")
    }

private fun expectedScalarRoute(
    mode: GPUBlendMode,
    sourceAlpha: GPUSourceAlphaClassification,
): ExpectedRoute {
    if (sourceAlpha == GPUSourceAlphaClassification.ProvenOpaque) {
        when (mode) {
            GPUBlendMode.SRC -> return fixed("modulate_one_isa")
            GPUBlendMode.SRC_IN -> return fixed("modulate_da_isa")
            GPUBlendMode.DST_IN -> return noOp()
            GPUBlendMode.SRC_OUT -> return fixed("modulate_ida_isa")
            GPUBlendMode.DST_ATOP -> return fixed("modulate_ida_one")
            else -> Unit
        }
    }

    return when (mode) {
        GPUBlendMode.CLEAR -> fixed("cov_reverse_subtract")
        GPUBlendMode.SRC -> scalarShader("src@v1")
        GPUBlendMode.DST -> noOp()
        GPUBlendMode.SRC_OVER -> fixed("modulate_one_isa")
        GPUBlendMode.DST_OVER -> fixed("modulate_ida_one")
        GPUBlendMode.SRC_IN -> scalarShader("src_in@v1")
        GPUBlendMode.DST_IN -> fixed("cov_reverse_subtract_isa")
        GPUBlendMode.SRC_OUT -> scalarShader("src_out@v1")
        GPUBlendMode.DST_OUT -> fixed("modulate_zero_isa")
        GPUBlendMode.SRC_ATOP -> fixed("modulate_da_isa")
        GPUBlendMode.DST_ATOP -> scalarShader("dst_atop@v1")
        GPUBlendMode.XOR -> fixed("modulate_ida_isa")
        GPUBlendMode.PLUS -> scalarShader("plus_exact@v1")
        GPUBlendMode.MODULATE -> fixed("cov_reverse_subtract_isc")
        GPUBlendMode.SCREEN -> fixed("modulate_one_isc")
        else -> scalarShader("${mode.gpuLabel}@v1")
    }
}

private fun fixed(stateId: String): ExpectedRoute = ExpectedRoute(
    signature = "FF($stateId)",
    destinationReadRequirement = GPUBlendDestinationReadRequirement.None,
    coverageEncoding = stateSpecs.getValue(stateId).coverageEncoding,
    stateId = stateId,
)

private fun shader(
    formulaId: String,
    coverageEncoding: GPUSourceCoverageEncoding = GPUSourceCoverageEncoding.None,
): ExpectedRoute = ExpectedRoute(
    signature = "SD($formulaId)",
    destinationReadRequirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
    coverageEncoding = coverageEncoding,
)

private fun scalarShader(formulaId: String): ExpectedRoute =
    shader(formulaId, GPUSourceCoverageEncoding.ScalarCoverageInShader)

private fun noOp(): ExpectedRoute = ExpectedRoute(
    signature = "N",
    destinationReadRequirement = GPUBlendDestinationReadRequirement.None,
    coverageEncoding = GPUSourceCoverageEncoding.None,
)

private fun refused(code: String): ExpectedRoute = ExpectedRoute(
    signature = "R($code)",
    destinationReadRequirement = GPUBlendDestinationReadRequirement.Refused,
    coverageEncoding = GPUSourceCoverageEncoding.None,
)

private fun GPUBlendPlan.signature(): String = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> "FF(${state.stateId})"
    is GPUBlendPlan.ShaderBlendNoDstRead -> "SN($formulaId)"
    is GPUBlendPlan.ShaderBlendWithDstRead -> "SD($formulaId)"
    is GPUBlendPlan.LayerCompositeBlend -> "L(${child.signature()})"
    is GPUBlendPlan.NoOp -> "N"
    is GPUBlendPlan.UnsupportedBlend -> "R(${diagnostic.code})"
}

private data class StateSpec(
    val colorSrcFactor: String,
    val colorDstFactor: String,
    val colorOperation: String,
    val alphaSrcFactor: String,
    val alphaDstFactor: String,
    val alphaOperation: String,
    val coverageEncoding: GPUSourceCoverageEncoding,
)

private val stateSpecs = mapOf(
    "zero_zero" to spec("zero", "zero"),
    "one_zero" to spec("one", "zero"),
    "one_isa" to spec("one", "one-minus-src-alpha"),
    "ida_one" to spec("one-minus-dst-alpha", "one"),
    "da_zero" to spec("dst-alpha", "zero"),
    "zero_sa" to spec("zero", "src-alpha"),
    "ida_zero" to spec("one-minus-dst-alpha", "zero"),
    "zero_isa" to spec("zero", "one-minus-src-alpha"),
    "da_isa" to spec("dst-alpha", "one-minus-src-alpha"),
    "ida_sa" to spec("one-minus-dst-alpha", "src-alpha"),
    "ida_isa" to spec("one-minus-dst-alpha", "one-minus-src-alpha"),
    "one_one_clamped" to spec("one", "one"),
    "zero_sc" to spec("zero", "src", alphaDst = "src-alpha"),
    "one_isc" to spec("one", "one-minus-src", alphaDst = "one-minus-src-alpha"),
    "cov_reverse_subtract" to spec(
        src = "dst",
        dst = "one",
        operation = "reverse-subtract",
        alphaSrc = "dst-alpha",
        alphaDst = "one",
        coverageEncoding = GPUSourceCoverageEncoding.Coverage,
    ),
    "modulate_one_isa" to spec("one", "one-minus-src-alpha", coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA),
    "modulate_ida_one" to spec("one-minus-dst-alpha", "one", coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA),
    "cov_reverse_subtract_isa" to spec(
        src = "dst",
        dst = "one",
        operation = "reverse-subtract",
        alphaSrc = "dst-alpha",
        alphaDst = "one",
        coverageEncoding = GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceAlpha,
    ),
    "modulate_zero_isa" to spec("zero", "one-minus-src-alpha", coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA),
    "modulate_da_isa" to spec("dst-alpha", "one-minus-src-alpha", coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA),
    "modulate_ida_isa" to spec("one-minus-dst-alpha", "one-minus-src-alpha", coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA),
    "cov_reverse_subtract_isc" to spec(
        src = "dst",
        dst = "one",
        operation = "reverse-subtract",
        alphaSrc = "dst-alpha",
        alphaDst = "one",
        coverageEncoding = GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceRGBA,
    ),
    "modulate_one_isc" to spec(
        src = "one",
        dst = "one-minus-src",
        alphaDst = "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
)

private fun spec(
    src: String,
    dst: String,
    operation: String = "add",
    alphaSrc: String = src,
    alphaDst: String = dst,
    alphaOperation: String = operation,
    coverageEncoding: GPUSourceCoverageEncoding = GPUSourceCoverageEncoding.None,
): StateSpec = StateSpec(src, dst, operation, alphaSrc, alphaDst, alphaOperation, coverageEncoding)

private fun assertFixedFunctionState(
    expectedStateId: String,
    actual: GPUFixedFunctionBlendState,
    context: String,
) {
    val expected = stateSpecs.getValue(expectedStateId)
    assertEquals(expectedStateId, actual.stateId, context)
    assertEquals(expected.colorSrcFactor, actual.color.sourceFactor, context)
    assertEquals(expected.colorDstFactor, actual.color.destinationFactor, context)
    assertEquals(expected.colorOperation, actual.color.operation, context)
    assertEquals(expected.alphaSrcFactor, actual.alpha.sourceFactor, context)
    assertEquals(expected.alphaDstFactor, actual.alpha.destinationFactor, context)
    assertEquals(expected.alphaOperation, actual.alpha.operation, context)
    assertEquals("rgba", actual.writeMask, context)
}

private fun request(
    mode: GPUBlendMode,
    coverage: GPUCoverageConsumption,
    sourceAlpha: GPUSourceAlphaClassification = GPUSourceAlphaClassification.Translucent,
): GPUBlendSpecializationRequest = GPUBlendSpecializationRequest(
    mode = mode,
    coverage = coverage,
    sourceAlpha = sourceAlpha,
    target = targetCases.first(),
    samplePlan = GPUSamplePlan.SingleSampleFrame,
)

private enum class MatrixCoverage { Full, Scalar }
