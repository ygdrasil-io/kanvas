package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.junit.jupiter.api.Test

class PathAaPlanBudgetTest {
    @Test
    fun `AA4 budget counts each pooled attachment once with exact bytes`() {
        val footprint = assertIs<PathAaPlanBudgetResult.WithinBudget>(
            PathAaPlanBudget.calculate(
                targetExtent = SizeI32(16, 16),
                geometriesF32 = listOf(directGeometry(), directGeometry()),
                requiresAa4DepthStencil = true,
                requiresHardMask = true,
                requiresHardEdgeDepthStencil = true,
                capabilities = capabilities(),
                budget = PlanBudget(12_000),
            ),
        ).footprint

        assertEquals(4_096L, footprint.multisampleColorBytes)
        assertEquals(4_096L, footprint.multisampleDepthStencilBytes)
        assertEquals(1_024L, footprint.hardEdgeMaskCapacityBytes)
        assertEquals(1_024L, footprint.hardEdgeDepthStencilCapacityBytes)
        assertEquals(11_904L, footprint.peakBytes)
    }

    @Test
    fun `AA4 budget accepts exact peak and rejects one byte short`() {
        val exact = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(16, 16),
            geometriesF32 = listOf(directGeometry()),
            requiresAa4DepthStencil = true,
            requiresHardMask = true,
            requiresHardEdgeDepthStencil = true,
            capabilities = capabilities(),
            budget = PlanBudget(11_904),
        )
        val short = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(16, 16),
            geometriesF32 = listOf(directGeometry()),
            requiresAa4DepthStencil = true,
            requiresHardMask = true,
            requiresHardEdgeDepthStencil = true,
            capabilities = capabilities(),
            budget = PlanBudget(11_903),
        )

        assertEquals(11_904L, assertIs<PathAaPlanBudgetResult.WithinBudget>(exact).footprint.peakBytes)
        assertEquals(
            PathAaPlanBudgetResult.Exceeded(requiredBytes = 11_904L, limitBytes = 11_903L),
            short,
        )
    }

    @Test
    fun `AA4 budget reports checked size overflow rather than wrapping`() {
        val result = PathAaPlanBudget.calculate(
            targetExtent = SizeI32(Int.MAX_VALUE, Int.MAX_VALUE),
            geometriesF32 = listOf(directGeometry()),
            requiresAa4DepthStencil = true,
            requiresHardMask = true,
            requiresHardEdgeDepthStencil = true,
            capabilities = capabilities(maxTextureDimension2D = Int.MAX_VALUE),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(PathAaPlanBudgetResult.Invalid("size-overflow"), result)
    }

    private fun directGeometry(): PathFillGeometryF32 = assertIs<PathFillPreparationResult.Ready>(
        preparePathFillGeometryF32(
            PathFillInputF64.fromPathF32(
                PathBuilder().moveTo(0f, 0f).lineTo(4f, 0f).lineTo(0f, 3f).close().build(),
            ),
        ),
    ).geometryF32

    private fun capabilities(maxTextureDimension2D: Int = 64): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = maxTextureDimension2D,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(64, 64, 512),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )
}
