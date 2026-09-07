package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertIs
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.graphiks.math.geometry.SizeI32
import org.junit.jupiter.api.Test

class PathStrokePlanBudgetTest {
    @Test
    fun rejectsAnUnrepresentableTargetFootprint() {
        val result = PathStrokePlanBudget.calculate(
            targetExtent = SizeI32(Int.MAX_VALUE, Int.MAX_VALUE),
            geometriesF32 = listOf(geometry()),
            capabilities = capabilities(),
            budget = PlanBudget(1L shl 20),
        )

        assertIs<PathStrokePlanBudgetResult.Invalid>(result)
    }

    private fun geometry() = assertIs<PathFillPreparationResult.Ready>(
        preparePathFillGeometryF32(PathFillInputF64.fromPathF32(
            PathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).lineTo(0f, 1f).close().build(),
        )),
    ).geometryF32

    private fun capabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )
}
