package org.graphiks.kanvas.gpu.plan

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.mapPathFillInputF64
import org.junit.jupiter.api.Test

class PathFillPlanBudgetTest {
    @Test
    fun byteExactBudgetCountsDirectAndStencilGeometry() {
        val direct = assertIs<PathFillPlanBudgetResult.WithinBudget>(
            PathFillPlanBudget.calculate(
                targetExtent = SizeI32(4, 3),
                geometriesF32 = listOf(directGeometry()),
                capabilities = capabilities(
                    policy = PlanBufferAllocationPolicy.of(64, 64, 512),
                ),
                budget = PlanBudget(1_456),
            ),
        ).footprint

        assertEquals(48L, direct.targetBytes)
        assertEquals(256L, direct.readbackBytesPerRow)
        assertEquals(768L, direct.readbackBytes)
        assertEquals(24L, direct.vertexUsefulBytes)
        assertEquals(12L, direct.indexUsefulBytes)
        assertEquals(256L, direct.uniformStrideBytes)
        assertEquals(32L, direct.uniformUsefulBytes)
        assertEquals(64L, direct.vertexCapacityBytes)
        assertEquals(64L, direct.indexCapacityBytes)
        assertEquals(512L, direct.uniformCapacityBytes)
        assertEquals(0L, direct.depthStencilBytes)
        assertEquals(1_456L, direct.peakBytes)

        val stencil = assertIs<PathFillPlanBudgetResult.WithinBudget>(
            PathFillPlanBudget.calculate(
                targetExtent = SizeI32(4, 3),
                geometriesF32 = listOf(concaveGeometry()),
                capabilities = capabilities(
                    policy = PlanBufferAllocationPolicy.of(256, 128, 512),
                ),
                budget = PlanBudget(1_760),
            ),
        ).footprint

        assertEquals(152L, stencil.vertexUsefulBytes)
        assertEquals(84L, stencil.indexUsefulBytes)
        assertEquals(256L, stencil.vertexCapacityBytes)
        assertEquals(128L, stencil.indexCapacityBytes)
        assertEquals(48L, stencil.depthStencilBytes)
        assertEquals(1_760L, stencil.peakBytes)
    }

    @Test
    fun budgetAcceptsItsExactPeakAndRejectsOneByteLess() {
        val atLimit = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            geometriesF32 = listOf(directGeometry()),
            capabilities = capabilities(
                policy = PlanBufferAllocationPolicy.of(128, 64, 512),
            ),
            budget = PlanBudget(1_520),
        )
        val belowLimit = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(4, 3),
            geometriesF32 = listOf(directGeometry()),
            capabilities = capabilities(
                policy = PlanBufferAllocationPolicy.of(128, 64, 512),
            ),
            budget = PlanBudget(1_519),
        )

        assertEquals(1_520L, assertIs<PathFillPlanBudgetResult.WithinBudget>(atLimit).footprint.peakBytes)
        assertEquals(
            PathFillPlanBudgetResult.Exceeded(requiredBytes = 1_520L, limitBytes = 1_519L),
            belowLimit,
        )
    }

    @Test
    fun budgetReportsCheckedSizeOverflowInsteadOfWrapping() {
        val result = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(Int.MAX_VALUE, Int.MAX_VALUE),
            geometriesF32 = listOf(directGeometry()),
            capabilities = capabilities(maxTextureDimension2D = Int.MAX_VALUE),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(PathFillPlanBudgetResult.Invalid("size-overflow"), result)
    }

    @Test
    fun budgetRejectsUniformStorageThatCannotBeHostAddressed() {
        val result = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(1, 1),
            geometriesF32 = List(512) { directGeometry() },
            capabilities = capabilities(
                uniformAlignment = 1 shl 22,
                policy = PlanBufferAllocationPolicy.of(1L shl 22, 1L shl 22, 1L shl 22),
                maxBufferSizeBytes = Long.MAX_VALUE,
            ),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(PathFillPlanBudgetResult.Invalid("uniform-host-size-overflow"), result)
    }

    @Test
    fun budgetRejectsReadbackAndPoolCapacitiesThatCannotBeHostAddressed() {
        val readback = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(1, Int.MAX_VALUE),
            geometriesF32 = listOf(directGeometry()),
            capabilities = capabilities(maxTextureDimension2D = Int.MAX_VALUE),
            budget = PlanBudget(Long.MAX_VALUE),
        )
        val pool = PathFillPlanBudget.calculate(
            targetExtent = SizeI32(1, 1),
            geometriesF32 = listOf(directGeometry()),
            capabilities = capabilities(
                policy = PlanBufferAllocationPolicy.of(1L shl 31, 4_096, 4_096),
                maxBufferSizeBytes = Long.MAX_VALUE,
            ),
            budget = PlanBudget(Long.MAX_VALUE),
        )

        assertEquals(PathFillPlanBudgetResult.Invalid("readback-host-size-overflow"), readback)
        assertEquals(PathFillPlanBudgetResult.Invalid("pool-host-size-overflow"), pool)
    }

    private fun directGeometry(): PathFillGeometryF32 = prepared(
        PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    )

    private fun concaveGeometry(): PathFillGeometryF32 = prepared(
        PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 3f)
            .lineTo(2f, 1f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    )

    private fun prepared(path: PathF32): PathFillGeometryF32 =
        assertIs<PathFillPreparationResult.Ready>(
            preparePathFillGeometryF32(Matrix3x3F32.Identity.mapPathFillInputF64(path)),
        ).geometryF32

    private fun capabilities(
        uniformAlignment: Int = 256,
        policy: PlanBufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        maxTextureDimension2D: Int = 64,
        maxBufferSizeBytes: Long = 1L shl 20,
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = maxTextureDimension2D,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = uniformAlignment,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = policy,
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )
}
