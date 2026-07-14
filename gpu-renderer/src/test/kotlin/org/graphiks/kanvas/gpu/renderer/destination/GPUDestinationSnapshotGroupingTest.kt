package org.graphiks.kanvas.gpu.renderer.destination

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUDestinationSnapshotGroupingTest {
    @Test
    fun `group key equality covers every typed destination and continuation identity axis`() {
        val base = groupKey()

        assertEquals(base, groupKey())
        assertNotEquals(base, base.copy(target = GPUTargetIdentity("target:other")))
        assertNotEquals(base, base.copy(targetGeneration = 12))
        assertNotEquals(base, base.copy(deviceGeneration = GPUDeviceGenerationID(8)))
        assertNotEquals(base, base.copy(format = GPUColorFormat("bgra8unorm")))
        assertNotEquals(
            base,
            base.copy(colorInterpretation = GPUColorInterpretation("linear-premul")),
        )
        assertNotEquals(
            base,
            base.copy(
                sampleContinuation = continuationKey(
                    colorAttachment = GPUTargetIdentity("msaa-color:other"),
                ),
            ),
        )
        assertNotEquals(
            base,
            base.copy(sourceIntermediate = GPUIntermediateIdentity("intermediate:other")),
        )
    }

    @Test
    fun `default uncalibrated policy emits one bounded group per destination reading draw`() {
        val result = uncalibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
            ),
        )

        assertEquals(listOf(listOf("draw:a"), listOf("draw:b")), result.groups.memberIds())
        assertEquals(
            listOf(
                GPUPixelBounds(0, 0, 16, 16),
                GPUPixelBounds(16, 0, 32, 16),
            ),
            result.groups.map { group -> group.logicalBounds },
        )
        assertTrue(result.refusals.isEmpty())
        assertTrue(result.decisionDump.any { line -> line.contains("calibration=missing") })
        assertTrue(result.decisionDump.none { line -> line.contains("Refuse") })
    }

    @Test
    fun `intersecting intervening target write breaks calibrated snapshot sharing`() {
        val result = calibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                directWrite("draw:intervening", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
            ),
        )

        assertEquals(listOf(listOf("draw:a"), listOf("draw:b")), result.groups.memberIds())
        assertTrue(result.decisionDump.any { line -> line.contains("hazard=intersecting-write") })
    }

    @Test
    fun `calibrated cost model accepts local union but rejects inflation above two`() {
        val local = calibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
            ),
        )
        val distant = calibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(64.0, 0.0, 80.0, 16.0)),
            ),
        )

        assertEquals(listOf(listOf("draw:a", "draw:b")), local.groups.memberIds())
        assertEquals(GPUPixelBounds(0, 0, 32, 16), local.groups.single().logicalBounds)
        assertEquals(listOf(listOf("draw:a"), listOf("draw:b")), distant.groups.memberIds())
        assertTrue(distant.decisionDump.any { line -> line.contains("unionInflation=2.500000") })
        assertTrue(distant.decisionDump.any { line -> line.contains("decision=separate") })
    }

    @Test
    fun `target generation sample source layer and filter boundaries never share`() {
        val base = destinationRead("draw:a", bounds = footprint(0.0, 0.0, 8.0, 8.0))
        val boundaries = listOf(
            base.copy(
                commandId = "draw:target",
                key = groupKey(target = GPUTargetIdentity("target:other")),
            ),
            base.copy(commandId = "draw:generation", key = groupKey(targetGeneration = 12)),
            base.copy(
                commandId = "draw:sample",
                key = groupKey(
                    sampleContinuation = continuationKey(
                        colorAttachment = GPUTargetIdentity("msaa-color:other"),
                    ),
                ),
            ),
            base.copy(
                commandId = "draw:source",
                key = groupKey(sourceIntermediate = GPUIntermediateIdentity("intermediate:other")),
            ),
            base.copy(commandId = "draw:layer", layerId = "layer:isolated"),
            base.copy(commandId = "draw:filter", filterId = "filter:blur"),
        )

        boundaries.forEach { changed ->
            val result = calibratedGrouper().group(listOf(base, changed))
            assertEquals(
                listOf(listOf("draw:a"), listOf(changed.commandId)),
                result.groups.memberIds(),
                "boundary ${changed.commandId}",
            )
        }
    }

    @Test
    fun `overlapping destination write and direct intervening draw are ordered hazards`() {
        val overlapping = calibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(8.0, 0.0, 24.0, 16.0)),
            ),
        )
        val direct = calibratedGrouper().group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                directWrite("draw:direct", bounds = footprint(96.0, 96.0, 104.0, 104.0)),
                destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
            ),
        )

        assertEquals(2, overlapping.groups.size)
        assertTrue(overlapping.decisionDump.any { it.contains("hazard=intersecting-write") })
        assertEquals(2, direct.groups.size)
        assertTrue(direct.decisionDump.any { it.contains("hazard=direct-intervening-draw") })
    }

    @Test
    fun `AA and filter outsets round align then intersect clip and target bounds`() {
        val result = uncalibratedGrouper().group(
            listOf(
                destinationRead(
                    commandId = "draw:expanded",
                    bounds = GPUDestinationReadFootprint(
                        left = 5.2,
                        top = 6.8,
                        right = 10.1,
                        bottom = 11.1,
                        aaOutsetPixels = 0.5,
                        filterOutsetPixels = 1.0,
                        alignmentPixels = 4,
                    ),
                    clipBounds = GPUPixelBounds(2, 3, 13, 14),
                    targetBounds = GPUPixelBounds(0, 0, 12, 13),
                ),
            ),
        )

        assertEquals(GPUPixelBounds(2, 4, 12, 13), result.groups.single().logicalBounds)
    }

    @Test
    fun `empty clip intersection produces no copy and stays anchored inside the intersection`() {
        val result = uncalibratedGrouper().group(
            listOf(
                destinationRead(
                    commandId = "draw:outside",
                    bounds = footprint(50.0, 50.0, 60.0, 60.0),
                    clipBounds = GPUPixelBounds(0, 0, 10, 10),
                ),
            ),
        )

        assertTrue(result.groups.isEmpty())
        assertTrue(result.refusals.isEmpty())
        assertTrue(result.decisionDump.any { it.contains("decision=skip-empty bounds=10,10,10,10") })
        assertEquals(0L, result.totalCopiedBytes)
    }

    @Test
    fun `row bytes area and aggregate snapshot budget use checked aligned arithmetic`() {
        val exact = grouper(frameBudgetBytes = 512).group(
            listOf(
                destinationRead(
                    "draw:exact",
                    bounds = footprint(0.0, 0.0, 3.0, 2.0),
                ),
            ),
        )
        val overBudget = grouper(frameBudgetBytes = 511).group(
            listOf(
                destinationRead(
                    "draw:budget",
                    bounds = footprint(0.0, 0.0, 3.0, 2.0),
                ),
            ),
        )
        val aggregate = grouper(frameBudgetBytes = 768).group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 3.0, 2.0)),
                destinationRead("draw:b", bounds = footprint(8.0, 0.0, 11.0, 2.0)),
            ),
        )
        val overflow = grouper(frameBudgetBytes = Long.MAX_VALUE).group(
            listOf(
                destinationRead(
                    commandId = "draw:overflow",
                    bounds = footprint(
                        0.0,
                        0.0,
                        Int.MAX_VALUE.toDouble(),
                        Int.MAX_VALUE.toDouble(),
                    ),
                    targetBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    clipBounds = GPUPixelBounds(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    bytesPerPixel = Int.MAX_VALUE,
                ),
            ),
        )
        val baselineConsumesBudget = grouper(
            frameBudgetBytes = 1_024,
            baselineAllocations = listOf(
                GPUFrameMemoryAllocation(
                    label = "target:canonical",
                    category = GPUFrameMemoryCategory.CanonicalTarget,
                    bytes = 768,
                    resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                    extent = GPUPixelBounds(0, 0, 1, 1),
                ),
            ),
        ).group(
            listOf(
                destinationRead("draw:budget-after-baseline", bounds = footprint(0.0, 0.0, 3.0, 2.0)),
            ),
        )

        assertEquals(512L, exact.groups.single().copiedBytes)
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", overBudget.refusals.single().code)
        assertEquals(listOf("draw:a"), aggregate.groups.single().members.map { it.commandId })
        assertEquals("unsupported.frame_memory.aggregate_budget_exceeded", aggregate.refusals.single().code)
        assertEquals("unsupported.destination_snapshot.byte_accounting_overflow", overflow.refusals.single().code)
        assertEquals(
            "unsupported.frame_memory.aggregate_budget_exceeded",
            baselineConsumesBudget.refusals.single().code,
        )
    }

    @Test
    fun `copy as draw is a materialization only for typed noncanonical texturable source`() {
        val external = destinationRead(
            commandId = "draw:external",
            bounds = footprint(0.0, 0.0, 8.0, 8.0),
            key = groupKey(sourceIntermediate = GPUIntermediateIdentity("intermediate:texturable")),
            sourceKind = GPUDestinationSnapshotSourceKind.ExternalTexturableIntermediate,
            sourceUsageLabels = setOf("texture_binding"),
        )
        val result = uncalibratedGrouper().group(listOf(external))

        assertIs<CopyAsDrawMaterialization>(result.materializations.single())
        assertTrue(GPUDestinationReadStrategy.entries.none { it.name == "CopyAsDrawMaterialization" })
        assertFailsWith<IllegalArgumentException> {
            external.copy(
                sourceKind = GPUDestinationSnapshotSourceKind.CanonicalLayerTarget,
                sourceUsageLabels = setOf("render_attachment"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            external.copy(sourceUsageLabels = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            external.copy(
                key = groupKey(sourceIntermediate = null),
                sourceUsageLabels = setOf("texture_binding"),
            )
        }
    }

    @Test
    fun `non finite calibrated cost never authorizes snapshot sharing`() {
        val grouper = GPUDestinationSnapshotGrouper(
            SnapshotGroupingCostModel(
                frameMemoryBudgetRequest = GPUFrameMemoryBudgetRequest(
                    allocations = emptyList(),
                    configuredAggregateBudgetBytes = 16L * 1024L * 1024L,
                    deviceLimits = deviceLimits,
                ),
                calibration = SnapshotGroupingCalibration(
                    version = "overflow-calibration-fixture",
                    copyCostPerByte = Double.MAX_VALUE,
                    passBreakCost = Double.MAX_VALUE,
                    scratchCostPerByte = Double.MAX_VALUE,
                ),
            ),
        )

        val result = grouper.group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
            ),
        )

        assertEquals(2, result.groups.size)
        assertTrue(result.decisionDump.any { it.contains("reason=calibrated-cost-overflow") })
    }

    @Test
    fun `decision dumps are deterministic handle free and never cite CPU readback`() {
        val accesses = listOf(
            destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
            destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0)),
        )

        val first = calibratedGrouper().group(accesses).decisionDump
        val second = calibratedGrouper().group(accesses.map { it.copy() }).decisionDump

        assertContentEquals(first, second)
        val dump = first.joinToString("\n")
        assertFalse(dump.contains("@0x"))
        assertFalse(dump.contains("identityHashCode"))
        assertFalse(dump.contains("nanoTime"))
        assertFalse(dump.contains("currentTimeMillis"))
        assertFalse(dump.contains("cpuReadback", ignoreCase = true))
        assertTrue(dump.contains("sampleCount=4"))
    }

    private fun uncalibratedGrouper(): GPUDestinationSnapshotGrouper =
        grouper(frameBudgetBytes = 16L * 1024L * 1024L)

    private fun grouper(
        frameBudgetBytes: Long,
        baselineAllocations: List<GPUFrameMemoryAllocation> = emptyList(),
    ): GPUDestinationSnapshotGrouper =
        GPUDestinationSnapshotGrouper(
            SnapshotGroupingCostModel(
                frameMemoryBudgetRequest = GPUFrameMemoryBudgetRequest(
                    allocations = baselineAllocations,
                    configuredAggregateBudgetBytes = frameBudgetBytes,
                    deviceLimits = deviceLimits,
                ),
                calibration = null,
            ),
        )

    private fun calibratedGrouper(): GPUDestinationSnapshotGrouper =
        GPUDestinationSnapshotGrouper(
            SnapshotGroupingCostModel(
                frameMemoryBudgetRequest = GPUFrameMemoryBudgetRequest(
                    allocations = emptyList(),
                    configuredAggregateBudgetBytes = 16L * 1024L * 1024L,
                    deviceLimits = deviceLimits,
                ),
                calibration = SnapshotGroupingCalibration(
                    version = "snapshot-copy-v1",
                    copyCostPerByte = 1.0,
                    passBreakCost = 4_096.0,
                    scratchCostPerByte = 0.0,
                ),
            ),
        )

    private fun destinationRead(
        commandId: String,
        bounds: GPUDestinationReadFootprint,
        key: GPUDestinationSnapshotGroupKey = groupKey(),
        clipBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        bytesPerPixel: Int = 4,
        sourceKind: GPUDestinationSnapshotSourceKind =
            GPUDestinationSnapshotSourceKind.CanonicalSceneTarget,
        sourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    ): GPUTargetAccess = access(
        commandId = commandId,
        requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
        bounds = bounds,
        key = key,
        clipBounds = clipBounds,
        targetBounds = targetBounds,
        bytesPerPixel = bytesPerPixel,
        sourceKind = sourceKind,
        sourceUsageLabels = sourceUsageLabels,
    )

    private fun directWrite(
        commandId: String,
        bounds: GPUDestinationReadFootprint,
    ): GPUTargetAccess = access(
        commandId = commandId,
        requirement = GPUBlendDestinationReadRequirement.None,
        bounds = bounds,
    )

    private fun access(
        commandId: String,
        requirement: GPUBlendDestinationReadRequirement,
        bounds: GPUDestinationReadFootprint,
        key: GPUDestinationSnapshotGroupKey = groupKey(),
        clipBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        bytesPerPixel: Int = 4,
        sourceKind: GPUDestinationSnapshotSourceKind =
            GPUDestinationSnapshotSourceKind.CanonicalSceneTarget,
        sourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
    ): GPUTargetAccess = GPUTargetAccess(
        commandId = commandId,
        requirement = requirement,
        key = key,
        drawBounds = bounds,
        clipBounds = clipBounds,
        targetBounds = targetBounds,
        layerId = "layer:root",
        filterId = null,
        bytesPerPixel = bytesPerPixel,
        sourceKind = sourceKind,
        sourceUsageLabels = sourceUsageLabels,
    )

    private fun footprint(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
    ): GPUDestinationReadFootprint = GPUDestinationReadFootprint(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        aaOutsetPixels = 0.0,
        filterOutsetPixels = 0.0,
        alignmentPixels = 1,
    )

    private fun groupKey(
        target: GPUTargetIdentity = GPUTargetIdentity("target:main"),
        targetGeneration: Long = 11,
        sampleContinuation: GPUSampleContinuationKey = continuationKey(),
        sourceIntermediate: GPUIntermediateIdentity? = GPUIntermediateIdentity("intermediate:source"),
    ): GPUDestinationSnapshotGroupKey = GPUDestinationSnapshotGroupKey(
        target = target,
        targetGeneration = targetGeneration,
        deviceGeneration = GPUDeviceGenerationID(7),
        format = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("encoded-premul-srgb"),
        sampleContinuation = sampleContinuation,
        sourceIntermediate = sourceIntermediate,
    )

    private fun continuationKey(
        colorAttachment: GPUTargetIdentity = GPUTargetIdentity("msaa-color:main"),
    ): GPUSampleContinuationKey = GPUSampleContinuationKey(
        target = GPUTargetIdentity("target:main"),
        targetGeneration = 11,
        deviceGeneration = GPUDeviceGenerationID(7),
        colorFormat = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("encoded-premul-srgb"),
        samplePlan = GPUSamplePlan.MultisampleFrame(4),
        colorAttachment = colorAttachment,
        depthStencilAttachment = GPUTargetIdentity("msaa-depth-stencil:main"),
    )

    private fun List<GPUDestinationSnapshotGroup>.memberIds(): List<List<String>> =
        map { group -> group.members.map(GPUDestinationReadMember::commandId) }

    private val deviceLimits = GPULimits(
        maxTextureDimension2D = Int.MAX_VALUE.toLong(),
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        source = "snapshot-grouping-test",
    )
}
