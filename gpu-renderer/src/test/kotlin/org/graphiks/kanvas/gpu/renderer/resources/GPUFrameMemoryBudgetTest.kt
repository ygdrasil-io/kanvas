package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement

class GPUFrameMemoryBudgetTest {
    @Test
    fun `budget accounts every frame memory category separately`() {
        val allocations = GPUFrameMemoryCategory.entries.mapIndexed { index, category ->
            val resourceKind = when (category) {
                GPUFrameMemoryCategory.ReadbackStaging,
                GPUFrameMemoryCategory.ReusableScratch,
                -> GPUFrameMemoryResourceKind.Buffer
                else -> GPUFrameMemoryResourceKind.Texture2D
            }
            GPUFrameMemoryAllocation(
                label = "allocation:$category",
                category = category,
                bytes = (index + 1L) * 1_024L,
                resourceKind = resourceKind,
                extent = if (resourceKind == GPUFrameMemoryResourceKind.Texture2D) {
                    GPUPixelBounds(left = 0, top = 0, right = 1, bottom = 1)
                } else {
                    null
                },
            )
        }

        val plan = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = allocations,
                configuredAggregateBudgetBytes = 1L shl 30,
                deviceLimits = deviceLimits,
            ),
        )

        assertEquals(GPUFrameMemoryCategory.entries.toSet(), plan.categoryTotals.keys)
        GPUFrameMemoryCategory.entries.forEachIndexed { index, category ->
            assertEquals((index + 1L) * 1_024L, plan.categoryTotals.getValue(category))
        }
        assertEquals(
            plan.categoryTotals.filterKeys { it.targetResident }.values.sum(),
            plan.targetResidentBytes,
        )
        assertEquals(
            plan.categoryTotals.filterKeys { !it.targetResident }.values.sum(),
            plan.peakFrameTransientBytes,
        )
        assertEquals(3, plan.deviceLimitFacts.size)
        assertNull(plan.diagnostic)
    }

    @Test
    fun `target owned 4k 4x attachments cannot bypass aggregate peak budget`() {
        val bounds = GPUPixelBounds(left = 0, top = 0, right = 3_840, bottom = 2_160)
        val canonicalBytes = bounds.checkedByteSize(bytesPerPixel = 4, sampleCount = 1)
        val multisampleBytes = bounds.checkedByteSize(bytesPerPixel = 4, sampleCount = 4)

        val plan = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "target:canonical",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = canonicalBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = bounds,
                    ),
                    GPUFrameMemoryAllocation(
                        label = "target:msaa-color",
                        category = GPUFrameMemoryCategory.RetainedMsaaColor,
                        bytes = multisampleBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = bounds,
                    ),
                    GPUFrameMemoryAllocation(
                        label = "target:msaa-depth-stencil",
                        category = GPUFrameMemoryCategory.RetainedMsaaDepthStencil,
                        bytes = multisampleBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = bounds,
                    ),
                ),
                configuredAggregateBudgetBytes = 256L * 1_024L * 1_024L,
                deviceLimits = deviceLimits,
            ),
        )

        assertEquals(canonicalBytes + multisampleBytes * 2L, plan.targetResidentBytes)
        assertEquals(0L, plan.peakFrameTransientBytes)
        assertEquals(
            "unsupported.frame_memory.aggregate_budget_exceeded",
            plan.diagnostic?.code?.value,
        )
    }

    @Test
    fun `canonical and msaa textures reject missing extents`() {
        listOf(
            GPUFrameMemoryCategory.CanonicalTarget,
            GPUFrameMemoryCategory.RetainedMsaaColor,
            GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
        ).forEach { category ->
            assertFailsWith<IllegalArgumentException> {
                GPUFrameMemoryAllocation(
                    label = "texture:$category",
                    category = category,
                    bytes = 4,
                    resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                    extent = null,
                )
            }
        }
    }

    @Test
    fun `readback buffer has no invented texture extent`() {
        val allocation = GPUFrameMemoryAllocation(
            label = "readback:staging",
            category = GPUFrameMemoryCategory.ReadbackStaging,
            bytes = 4_096,
            resourceKind = GPUFrameMemoryResourceKind.Buffer,
            extent = null,
        )
        val plan = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(allocation),
                configuredAggregateBudgetBytes = 1L shl 20,
                deviceLimits = deviceLimits,
            ),
        )

        assertNull(plan.diagnostic)
        assertFailsWith<IllegalArgumentException> {
            allocation.copy(
                extent = GPUPixelBounds(left = 0, top = 0, right = 1, bottom = 1),
            )
        }
    }

    @Test
    fun `device texture limits remain a hard preflight refusal`() {
        val plan = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "target:oversized",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = 9_000L * 4L,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = GPUPixelBounds(left = 0, top = 0, right = 9_000, bottom = 1),
                    ),
                ),
                configuredAggregateBudgetBytes = 1L shl 30,
                deviceLimits = deviceLimits,
            ),
        )

        assertEquals(
            "unsupported.frame_memory.device_limit_exceeded",
            plan.diagnostic?.code?.value,
        )
    }

    @Test
    fun `aggregate accounting reports signed long overflow before budget exhaustion`() {
        val plan = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "target:canonical",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = Long.MAX_VALUE,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = GPUPixelBounds(left = 0, top = 0, right = 1, bottom = 1),
                    ),
                    GPUFrameMemoryAllocation(
                        label = "scratch:one-byte",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = 1,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                ),
                configuredAggregateBudgetBytes = Long.MAX_VALUE,
                deviceLimits = deviceLimits,
            ),
        )

        assertEquals(Long.MAX_VALUE, plan.targetResidentBytes)
        assertEquals(1L, plan.peakFrameTransientBytes)
        assertEquals(
            "unsupported.frame_memory.accounting_overflow",
            plan.diagnostic?.code?.value,
        )
    }

    @Test
    fun `destination copies above historical sixteen mib ceiling rely on aggregate budget`() {
        val copyWidth = 2_560
        val copyHeight = 2_048
        val route = GPUDestinationReadStrategyPlanner().plan(
            GPUDestinationReadStrategyRequest(
                commandId = "blend:large-snapshot",
                requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
                bounds = GPUDestinationReadBounds(
                    boundsLabel = "bounds:large-snapshot",
                    conservative = true,
                    pixelAligned = true,
                    width = copyWidth,
                    height = copyHeight,
                    targetWidth = copyWidth,
                    targetHeight = copyHeight,
                ),
                sourceTargetLabel = "target:main",
                sourceUsageLabels = setOf("render_attachment", "copy_src"),
                copyUsageLabels = setOf("copy_dst", "texture_binding"),
                targetFormatClass = "rgba8unorm",
                targetGeneration = 3,
            ),
        )

        assertEquals(20L * 1_024L * 1_024L, route.copyDescriptor?.byteEstimate)
        assertEquals(GPUDestinationReadStrategy.CopyTarget, route.plan.strategy)
        assertNull(route.plan.diagnostic)
    }

    private val deviceLimits = GPULimits(
        maxTextureDimension2D = 8_192,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
    )
}
