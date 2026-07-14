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
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor
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
    fun `only planner selected CopyTarget accesses become destination snapshots`() {
        val bindIntermediate = destinationRead(
            commandId = "draw:bind-intermediate",
            bounds = footprint(0.0, 0.0, 8.0, 8.0),
            strategyGatePlan = strategyPlan(
                commandId = "draw:bind-intermediate",
                eligibleIntermediate = exactIntermediate(),
            ),
        )
        val isolateLayer = destinationRead(
            commandId = "draw:isolate-layer",
            bounds = footprint(8.0, 0.0, 16.0, 8.0),
            strategyGatePlan = strategyPlan(
                commandId = "draw:isolate-layer",
                mandatoryIsolation = GPUDestinationReadMandatoryIsolation(
                    kind = GPUDestinationReadIsolationKind.Layer,
                    targetLabel = "layer:isolated",
                ),
            ),
        )
        val refused = destinationRead(
            commandId = "draw:refused",
            bounds = footprint(16.0, 0.0, 24.0, 8.0),
            strategyGatePlan = strategyPlan(
                commandId = "draw:refused",
                targetCopyAvailable = false,
            ),
        )

        val result = uncalibratedGrouper().group(listOf(bindIntermediate, isolateLayer, refused))

        assertTrue(result.groups.isEmpty())
        assertTrue(result.materializations.isEmpty())
        assertTrue(result.refusals.isEmpty())
        assertEquals(
            listOf(
                "destination-snapshot:member command=draw:bind-intermediate decision=skip " +
                    "selectedStrategy=BindIntermediate",
                "destination-snapshot:member command=draw:isolate-layer decision=skip " +
                    "selectedStrategy=IsolateLayer",
                "destination-snapshot:member command=draw:refused decision=skip selectedStrategy=Refuse",
            ),
            result.decisionDump.filter { line -> line.contains("selectedStrategy=") },
        )
    }

    @Test
    fun `planner CopyTarget provenance stays canonical while external materialization facts stay separate`() {
        val canonicalCopyTarget = strategyPlan(
            commandId = "draw:external-provenance",
            canonicalTarget = GPUTargetIdentity("target:main"),
            canonicalTargetGeneration = 11,
            canonicalTargetUsageLabels = setOf("render_attachment", "copy_src"),
        )
        val externalMaterialization = destinationRead(
            commandId = "draw:external-provenance",
            bounds = footprint(0.0, 0.0, 8.0, 8.0),
            key = groupKey(
                target = GPUTargetIdentity("target:main"),
                targetGeneration = 11,
                sourceIntermediate = GPUIntermediateIdentity("intermediate:texturable-provenance"),
            ),
            materializationSourceKind =
                GPUDestinationSnapshotMaterializationSourceKind.ExternalTexturableIntermediate,
            materializationSourceUsageLabels = setOf("texture_binding"),
            strategyGatePlan = canonicalCopyTarget,
        )

        listOf(
            strategyPlan(
                commandId = "draw:external-provenance",
                canonicalTarget = GPUTargetIdentity("target:other"),
            ),
            strategyPlan(
                commandId = "draw:external-provenance",
                canonicalTargetGeneration = 12,
            ),
            strategyPlan(commandId = "draw:other-command"),
            strategyPlan(
                commandId = "draw:external-provenance",
                canonicalTargetFormat = GPUColorFormat("bgra8unorm"),
            ),
        ).forEach { mismatchedPlan ->
            assertFailsWith<IllegalArgumentException> {
                externalMaterialization.copy(strategyGatePlan = mismatchedPlan)
            }
        }

        val result = uncalibratedGrouper().group(listOf(externalMaterialization))
        assertIs<CopyAsDrawMaterialization>(result.materializations.single())
        assertTrue(
            result.decisionDump.any { line ->
                line == "destination-snapshot:member command=draw:external-provenance " +
                    "selectedStrategy=CopyTarget selectedCommand=draw:external-provenance " +
                    "selectedTarget=target:main " +
                    "selectedTargetGeneration=11 selectedTargetUsage=copy_src,render_attachment " +
                    "selectedTargetFormat=rgba8unorm " +
                    "materializationSource=ExternalTexturableIntermediate " +
                    "materializationSourceUsage=texture_binding"
            },
        )

        val canonicalWithoutCopySrc = strategyPlan(
            commandId = "draw:canonical-without-copy-src",
            canonicalTargetUsageLabels = setOf("render_attachment"),
        )
        assertEquals(GPUDestinationReadStrategy.Refuse, canonicalWithoutCopySrc.plan.strategy)
    }

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
    fun `union budget rejection falls back to two separately admissible snapshots`() {
        val result = calibratedGrouper(frameBudgetBytes = 8_192).group(
            listOf(
                destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0)),
                destinationRead("draw:b", bounds = footprint(0.0, 32.0, 16.0, 48.0)),
            ),
        )

        assertEquals(listOf(listOf("draw:a"), listOf("draw:b")), result.groups.memberIds())
        assertEquals(listOf(4_096L, 4_096L), result.groups.map { group -> group.copiedBytes })
        assertEquals(8_192L, result.totalCopiedBytes)
        assertTrue(result.refusals.isEmpty())
        assertTrue(
            result.decisionDump.any { line ->
                line.contains("decision=separate") && line.contains("reason=union-budget")
            },
        )
    }

    @Test
    fun `target generation sample source layer and filter boundaries never share`() {
        val base = destinationRead("draw:a", bounds = footprint(0.0, 0.0, 8.0, 8.0))
        val boundaries = listOf(
            base.copy(
                commandId = "draw:target",
                key = groupKey(target = GPUTargetIdentity("target:other")),
                strategyGatePlan = strategyPlan(
                    commandId = "draw:target",
                    canonicalTarget = GPUTargetIdentity("target:other"),
                ),
            ),
            base.copy(
                commandId = "draw:generation",
                key = groupKey(targetGeneration = 12),
                strategyGatePlan = strategyPlan(
                    commandId = "draw:generation",
                    canonicalTargetGeneration = 12,
                ),
            ),
            base.copy(
                commandId = "draw:sample",
                key = groupKey(
                    sampleContinuation = continuationKey(
                        colorAttachment = GPUTargetIdentity("msaa-color:other"),
                    ),
                ),
                strategyGatePlan = strategyPlan("draw:sample"),
            ),
            base.copy(
                commandId = "draw:source",
                key = groupKey(sourceIntermediate = GPUIntermediateIdentity("intermediate:other")),
                strategyGatePlan = strategyPlan("draw:source"),
            ),
            base.copy(
                commandId = "draw:layer",
                layerId = "layer:isolated",
                strategyGatePlan = strategyPlan("draw:layer"),
            ),
            base.copy(
                commandId = "draw:filter",
                filterId = "filter:blur",
                strategyGatePlan = strategyPlan("draw:filter"),
            ),
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
    fun `intervening target generation layer and filter boundaries precede intersection filtering`() {
        val first = destinationRead("draw:a", bounds = footprint(0.0, 0.0, 16.0, 16.0))
        val second = destinationRead("draw:b", bounds = footprint(16.0, 0.0, 32.0, 16.0))
        val boundaries = listOf(
            directWrite(
                commandId = "draw:other-target",
                bounds = footprint(16.0, 0.0, 32.0, 16.0),
                key = groupKey(target = GPUTargetIdentity("target:other")),
            ) to "hazard=intervening-target-boundary",
            directWrite(
                commandId = "draw:other-generation",
                bounds = footprint(16.0, 0.0, 32.0, 16.0),
                key = groupKey(targetGeneration = 12),
            ) to "hazard=intervening-generation-boundary",
            directWrite(
                commandId = "draw:other-layer",
                bounds = footprint(16.0, 0.0, 32.0, 16.0),
                layerId = "layer:isolated",
            ) to "hazard=intervening-layer-boundary",
            directWrite(
                commandId = "draw:other-filter",
                bounds = footprint(16.0, 0.0, 32.0, 16.0),
                filterId = "filter:blur",
            ) to "hazard=intervening-filter-boundary",
        )

        boundaries.forEach { (boundary, expectedReason) ->
            val result = calibratedGrouper().group(listOf(first, boundary, second))

            assertEquals(
                listOf(listOf("draw:a"), listOf("draw:b")),
                result.groups.memberIds(),
                boundary.commandId,
            )
            assertTrue(
                result.decisionDump.any { line -> line.contains(expectedReason) },
                "$expectedReason for ${boundary.commandId}",
            )
        }
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
            materializationSourceKind =
                GPUDestinationSnapshotMaterializationSourceKind.ExternalTexturableIntermediate,
            materializationSourceUsageLabels = setOf("texture_binding"),
        )
        val result = uncalibratedGrouper().group(listOf(external))

        assertIs<CopyAsDrawMaterialization>(result.materializations.single())
        assertTrue(GPUDestinationReadStrategy.entries.none { it.name == "CopyAsDrawMaterialization" })
        assertFailsWith<IllegalArgumentException> {
            external.copy(
                materializationSourceKind =
                    GPUDestinationSnapshotMaterializationSourceKind.CanonicalLayerTarget,
                materializationSourceUsageLabels = setOf("render_attachment"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            external.copy(materializationSourceUsageLabels = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            external.copy(
                key = groupKey(sourceIntermediate = null),
                materializationSourceUsageLabels = setOf("texture_binding"),
            )
        }
        val externalCopy = external.copy(
            commandId = "draw:external-copy",
            key = groupKey(sourceIntermediate = GPUIntermediateIdentity("intermediate:copyable")),
            materializationSourceUsageLabels = setOf("texture_binding", "copy_src"),
            strategyGatePlan = strategyPlan("draw:external-copy"),
        )
        assertIs<GPUDestinationSnapshotMaterialization.TextureCopy>(
            uncalibratedGrouper().group(listOf(externalCopy)).materializations.single(),
        )
        assertFailsWith<IllegalArgumentException> {
            externalCopy.copy(key = groupKey(sourceIntermediate = null))
        }
        assertFailsWith<IllegalArgumentException> {
            externalCopy.copy(materializationSourceUsageLabels = setOf("copy_src"))
        }

        val otherExternal = external.copy(
            commandId = "draw:external-other",
            drawBounds = footprint(8.0, 0.0, 16.0, 8.0),
            key = groupKey(sourceIntermediate = GPUIntermediateIdentity("intermediate:other")),
            strategyGatePlan = strategyPlan("draw:external-other"),
        )
        val distinctSources = calibratedGrouper().group(listOf(external, otherExternal))

        assertEquals(listOf(listOf("draw:external"), listOf("draw:external-other")), distinctSources.groups.memberIds())
        assertEquals(
            listOf(
                GPUIntermediateIdentity("intermediate:texturable"),
                GPUIntermediateIdentity("intermediate:other"),
            ),
            distinctSources.materializations.map { materialization ->
                assertIs<CopyAsDrawMaterialization>(materialization).sourceIntermediate
            },
        )
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

    private fun calibratedGrouper(
        frameBudgetBytes: Long = 16L * 1024L * 1024L,
    ): GPUDestinationSnapshotGrouper =
        GPUDestinationSnapshotGrouper(
            SnapshotGroupingCostModel(
                frameMemoryBudgetRequest = GPUFrameMemoryBudgetRequest(
                    allocations = emptyList(),
                    configuredAggregateBudgetBytes = frameBudgetBytes,
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
        materializationSourceKind: GPUDestinationSnapshotMaterializationSourceKind =
            GPUDestinationSnapshotMaterializationSourceKind.CanonicalSceneTarget,
        materializationSourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
        strategyGatePlan: GPUDestinationReadStrategyGatePlan = strategyPlan(
            commandId = commandId,
            canonicalTarget = key.target,
            canonicalTargetGeneration = key.targetGeneration,
        ),
    ): GPUTargetAccess = access(
        commandId = commandId,
        requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
        bounds = bounds,
        key = key,
        clipBounds = clipBounds,
        targetBounds = targetBounds,
        bytesPerPixel = bytesPerPixel,
        materializationSourceKind = materializationSourceKind,
        materializationSourceUsageLabels = materializationSourceUsageLabels,
        strategyGatePlan = strategyGatePlan,
    )

    private fun directWrite(
        commandId: String,
        bounds: GPUDestinationReadFootprint,
        key: GPUDestinationSnapshotGroupKey = groupKey(),
        layerId: String = "layer:root",
        filterId: String? = null,
    ): GPUTargetAccess = access(
        commandId = commandId,
        requirement = GPUBlendDestinationReadRequirement.None,
        bounds = bounds,
        key = key,
        layerId = layerId,
        filterId = filterId,
    )

    private fun access(
        commandId: String,
        requirement: GPUBlendDestinationReadRequirement,
        bounds: GPUDestinationReadFootprint,
        key: GPUDestinationSnapshotGroupKey = groupKey(),
        clipBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 128, 128),
        bytesPerPixel: Int = 4,
        materializationSourceKind: GPUDestinationSnapshotMaterializationSourceKind =
            GPUDestinationSnapshotMaterializationSourceKind.CanonicalSceneTarget,
        materializationSourceUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
        strategyGatePlan: GPUDestinationReadStrategyGatePlan? = null,
        layerId: String = "layer:root",
        filterId: String? = null,
    ): GPUTargetAccess = GPUTargetAccess(
        commandId = commandId,
        requirement = requirement,
        key = key,
        drawBounds = bounds,
        clipBounds = clipBounds,
        targetBounds = targetBounds,
        layerId = layerId,
        filterId = filterId,
        bytesPerPixel = bytesPerPixel,
        materializationSourceKind = materializationSourceKind,
        materializationSourceUsageLabels = materializationSourceUsageLabels,
        strategyGatePlan = strategyGatePlan,
    )

    private fun strategyPlan(
        commandId: String,
        eligibleIntermediate: GPUDestinationReadEligibleIntermediate? = null,
        mandatoryIsolation: GPUDestinationReadMandatoryIsolation? = null,
        targetCopyAvailable: Boolean = true,
        canonicalTarget: GPUTargetIdentity = GPUTargetIdentity("target:main"),
        canonicalTargetGeneration: Long = 11,
        canonicalTargetUsageLabels: Set<String> = setOf("render_attachment", "copy_src"),
        canonicalTargetFormat: GPUColorFormat = GPUColorFormat("rgba8unorm"),
    ): GPUDestinationReadStrategyGatePlan = GPUDestinationReadStrategyPlanner().plan(
        GPUDestinationReadStrategyRequest(
            label = "snapshot:$commandId",
            commandId = commandId,
            requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
            bounds = GPUDestinationReadBounds(
                boundsLabel = "0,0,16,16",
                conservative = true,
                pixelAligned = true,
                width = 16,
                height = 16,
                targetWidth = 128,
                targetHeight = 128,
            ),
            sourceTargetLabel = canonicalTarget.value,
            sourceUsageLabels = canonicalTargetUsageLabels,
            copyUsageLabels = setOf("copy_dst", "texture_binding"),
            targetFormatClass = canonicalTargetFormat.value,
            targetGeneration = canonicalTargetGeneration,
            eligibleIntermediate = eligibleIntermediate,
            mandatoryIsolation = mandatoryIsolation,
            targetCopyAvailable = targetCopyAvailable,
        ),
    )

    private fun exactIntermediate(): GPUDestinationReadEligibleIntermediate =
        GPUDestinationReadEligibleIntermediate(
            GPUIntermediateTextureDescriptor(
                label = "intermediate:exact",
                purpose = GPUIntermediatePurpose.ExistingIntermediate,
                descriptorHash = "descriptor:exact",
                sourceTargetLabel = "target:main",
                boundsLabel = "0,0,16,16",
                width = 16,
                height = 16,
                formatClass = "rgba8unorm",
                usageLabels = listOf("texture_binding"),
                sampleCount = 1,
                generation = 11,
                lifetimeClass = "draw-local",
                ownerScope = "snapshot-test",
                byteEstimate = 1_024,
            ),
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
