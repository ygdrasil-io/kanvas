package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.math.geometry.SizeI32
import org.junit.jupiter.api.Test

class AnalyticRectPlanBudgetTest {
    @Test
    fun `analytic rect budget counts exact pooled capacities`() {
        val result = AnalyticRectPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            drawCount = 2,
            capabilities = supportedCapabilities(minUniformAlignment = 256),
            budget = PlanBudget(25_392),
        )

        val ready = assertIs<AnalyticRectPlanBudgetResult.WithinBudget>(result)
        assertEquals(48, ready.footprint.targetBytes)
        assertEquals(768, ready.footprint.readbackBytes)
        assertEquals(256, ready.footprint.uniformStrideBytes)
        assertEquals(512, ready.footprint.uniformUsefulBytes)
        assertEquals(16_384, ready.footprint.vertexCapacityBytes)
        assertEquals(4_096, ready.footprint.indexCapacityBytes)
        assertEquals(4_096, ready.footprint.uniformCapacityBytes)
        assertEquals(25_392, ready.footprint.peakBytes)
    }

    @Test
    fun `analytic rect budget aligns the 80 byte uniform payload before pooling`() {
        val result = AnalyticRectPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            drawCount = 2,
            capabilities = supportedCapabilities(
                minUniformAlignment = 64,
                vertexFloorBytes = 32,
                indexFloorBytes = 24,
                uniformFloorBytes = 128,
            ),
            budget = PlanBudget(1_184),
        )

        val ready = assertIs<AnalyticRectPlanBudgetResult.WithinBudget>(result)
        assertEquals(128, ready.footprint.uniformStrideBytes)
        assertEquals(256, ready.footprint.uniformUsefulBytes)
        assertEquals(256, ready.footprint.uniformCapacityBytes)
        assertEquals(1_184, ready.footprint.peakBytes)
    }

    @Test
    fun `pool policy doubles only after a floor is exceeded`() {
        val policy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096)

        assertEquals(16_384, policy.reserve(PlanScratchBufferKind.Vertex, 16_384))
        assertEquals(32_768, policy.reserve(PlanScratchBufferKind.Vertex, 16_385))
        assertNull(policy.reserve(PlanScratchBufferKind.Uniform, Long.MAX_VALUE))
    }

    private fun supportedCapabilities(
        minUniformAlignment: Int = 256,
        maxBufferSizeBytes: Long = 1L shl 20,
        vertexFloorBytes: Long = 16_384,
        indexFloorBytes: Long = 4_096,
        uniformFloorBytes: Long = 4_096,
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = minUniformAlignment,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(vertexFloorBytes, indexFloorBytes, uniformFloorBytes),
    )
}
