package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.math.geometry.SizeI32
import org.junit.jupiter.api.Test

class AnalyticRRectPlanBudgetTest {
    @Test
    fun `analytic rrect budget counts exact five resource footprint`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            drawCount = 2,
            capabilities = supportedCapabilities(),
            budget = PlanBudget(25_392),
        )

        val ready = assertIs<AnalyticRRectPlanBudgetResult.WithinBudget>(result)
        val footprint = ready.footprint
        assertEquals(48L, footprint.targetBytes)
        assertEquals(768L, footprint.readbackBytes)
        assertEquals(64L, footprint.vertexUsefulBytes)
        assertEquals(48L, footprint.indexUsefulBytes)
        assertEquals(256L, footprint.uniformStrideBytes)
        assertEquals(512L, footprint.uniformUsefulBytes)
        assertEquals(16_384L, footprint.vertexCapacityBytes)
        assertEquals(4_096L, footprint.indexCapacityBytes)
        assertEquals(4_096L, footprint.uniformCapacityBytes)
        assertEquals(25_392L, footprint.peakBytes)
    }

    @Test
    fun `analytic rrect budget rejects empty primitive collections`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            drawCount = 0,
            capabilities = supportedCapabilities(),
            budget = PlanBudget(25_392),
        )

        assertEquals(AnalyticRRectPlanBudgetResult.Invalid("invalid-input"), result)
    }

    @Test
    fun `analytic rrect budget reports exact frame local excess`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            drawCount = 2,
            capabilities = supportedCapabilities(),
            budget = PlanBudget(25_391),
        )

        assertEquals(AnalyticRRectPlanBudgetResult.Exceeded(25_392L, 25_391L), result)
    }

    @Test
    fun `analytic rrect budget rejects Uniform80 host storage beyond I32`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(1, 1),
            drawCount = 512,
            capabilities = rendererRepresentabilityCapabilities(),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(
            AnalyticRRectPlanBudgetResult.Invalid("uniform-host-size-overflow"),
            result,
        )
    }

    @Test
    fun `analytic rrect budget keeps the largest I32 representable Uniform80 batch`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(1, 1),
            drawCount = 511,
            capabilities = rendererRepresentabilityCapabilities(),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        val ready = assertIs<AnalyticRRectPlanBudgetResult.WithinBudget>(result)
        assertEquals(2_143_289_344L, ready.footprint.uniformUsefulBytes)
    }

    @Test
    fun `analytic rrect budget rejects a Uniform80 dynamic offset beyond UInt`() {
        val result = AnalyticRRectPlanBudget.calculate(
            targetExtent = SizeI32(1, 1),
            drawCount = 1_025,
            capabilities = rendererRepresentabilityCapabilities(),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(
            AnalyticRRectPlanBudgetResult.Invalid("uniform-dynamic-offset-overflow"),
            result,
        )
    }

    private fun supportedCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
    )

    private fun rendererRepresentabilityCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = Long.MAX_VALUE,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 1 shl 22,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(1L shl 22, 1L shl 22, 1L shl 22),
    )
}
