package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedVerticesSemanticBuilderTest {
    @Test
    fun `core and prepared vertices form one ordered command recording and semantic bijection`() {
        val inventory = plan(
            listOf(
                rect(),
                vertices(Color.RED, Matrix33.identity()),
                vertices(Color.BLUE, Matrix33.translate(3f, 4f)),
            ),
        )

        assertEquals(listOf(0, 1, 2), inventory.normalizedCommands.map { it.commandId.value })
        assertEquals(listOf(0, 1, 2), inventory.recording.recordedCommands.map { it.commandId.value })
        assertEquals(listOf(0, 1, 2), inventory.recording.analysis.records.map { it.commandIdValue })
        assertEquals(
            listOf(0, 1, 2),
            inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).map { it.commandIdValue },
        )

        val gatherResult = GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                inventory = inventory,
                targetBounds = BOUNDS,
                imageArtifactsByCommandId = emptyMap(),
            )
        val gathered = assertIs<GPUPreparedSurfaceSemanticGatherResult.Gathered>(
            gatherResult,
            (gatherResult as? GPUPreparedSurfaceSemanticGatherResult.Refused)?.diagnostic.toString(),
        )
        assertEquals(listOf(0, 1, 2), gathered.semanticsByCommandId.keys.toList())
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semanticsByCommandId.getValue(0))
        val first = assertIs<GPUDrawSemanticPayload.Vertices>(gathered.semanticsByCommandId.getValue(1))
        val second = assertIs<GPUDrawSemanticPayload.Vertices>(gathered.semanticsByCommandId.getValue(2))
        assertEquals(first.artifact.key, second.artifact.key)
        assertNotEquals(first.material, second.material)
        assertNotEquals(first.transformBytes, second.transformBytes)
        assertNotEquals(first.canonicalHash, second.canonicalHash)
        assertEquals("rgba8unorm", first.targetFormat)
        assertEquals(CAPABILITIES.canonicalSnapshotHash(), first.capabilitySnapshotHash)
        assertNotNull(inventory.preparedVerticesInventory)

        val swapped = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
            GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = inventory.visualCommands,
                normalizedCommands = inventory.normalizedCommands,
                recording = inventory.recording,
                targetBounds = BOUNDS,
                imageArtifactsByCommandId = emptyMap(),
                verticesSemanticsByCommandId = mapOf(1 to second, 2 to first),
                blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
            ),
        )
        assertEquals("invalid.surface.prepared.semantic-command-bijection", swapped.diagnostic.code.value)
    }

    @Test
    fun `missing duplicate reordered and type-confused vertices authorities refuse atomically`() {
        val inventory = plan(listOf(rect(), vertices(Color.RED), vertices(Color.BLUE)))
        val verticesIds = assertNotNull(inventory.preparedVerticesInventory).artifactKeyByCommandId.keys
        assertEquals(setOf(1, 2), verticesIds)

        listOf(
            inventory.copy(normalizedCommands = inventory.normalizedCommands.dropLast(1)),
            inventory.copy(normalizedCommands = inventory.normalizedCommands + inventory.normalizedCommands.last()),
            inventory.copy(normalizedCommands = inventory.normalizedCommands.reversed()),
            inventory.copy(
                preparedVerticesInventory = inventory.preparedVerticesInventory.testRebindCommandIds(
                    mapOf(1 to 0, 2 to 2),
                ),
            ),
        ).forEach { malformed ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = malformed,
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.semantic-command-bijection",
                refused.diagnostic.code.value,
            )
        }

        val vertices = assertIs<NormalizedDrawCommand.DrawPreparedVertices>(
            inventory.normalizedCommands[1],
        )
        val changedTransform = vertices.transformBytes.toMutableList().also {
            it[2] = 1f.toRawBits()
        }
        listOf(
            vertices.withAuthorities(transformBytes = changedTransform),
            vertices.withAuthorities(transform = vertices.transform.copy(translateX = 999f)),
            vertices.withAuthorities(capabilitySnapshotHash = "capability:forged"),
        ).forEach { changed ->
            val authorityRefusal = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = inventory.copy(
                        normalizedCommands = inventory.normalizedCommands.toMutableList().also {
                            it[1] = changed
                        },
                    ),
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.vertices-semantic-authority",
                authorityRefusal.diagnostic.code.value,
            )
        }
    }

    @Test
    fun `record packet and target authority mutations refuse before semantic publication`() {
        val inventory = plan(listOf(vertices(Color.RED)))
        val record = inventory.recording.analysis.records.single()
        val recordMutations = listOf(
            record.copy(recordId = "forged.record"),
            record.copy(commandFamily = "ForgedFamily"),
            record.copy(boundsHash = "forged.bounds"),
            record.copy(routeDecisionLabel = "forged.route"),
            record.copy(materialKeyHash = "forged.material"),
            record.copy(renderStepCandidates = listOf("forged.step")),
            record.copy(sortKey = org.graphiks.kanvas.gpu.renderer.analysis.SortKey(99)),
            record.copy(diagnostics = listOf(
                org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDiagnostic(
                    code = "forged.diagnostic",
                    terminal = true,
                ),
            )),
        )
        val malformed = recordMutations.map { changed ->
            inventory.copy(
                recording = inventory.recording.copy(
                    analysis = inventory.recording.analysis.copy(records = listOf(changed)),
                ),
            )
        } + listOf(
            inventory.copy(target = inventory.target.copy(width = 33)),
            inventory.copy(target = inventory.target.copy(height = 25)),
            inventory.copy(target = inventory.target.copy(colorFormat = "bgra8unorm")),
            inventory.copy(recording = inventory.recording.withForgedPacketTargetState()),
        )

        malformed.forEach { changed ->
            val refused = assertIs<GPUPreparedSurfaceSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherPreparedSurfaceSemantics(
                    inventory = changed,
                    targetBounds = BOUNDS,
                    imageArtifactsByCommandId = emptyMap(),
                ),
            )
            assertEquals(
                "invalid.surface.prepared.vertices-semantic-authority",
                refused.diagnostic.code.value,
            )
        }
    }

    private fun NormalizedDrawCommand.DrawPreparedVertices.withAuthorities(
        transformBytes: List<Int> = this.transformBytes,
        transform: org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts = this.transform,
        capabilitySnapshotHash: String = this.capabilitySnapshotHash,
    ) = NormalizedDrawCommand.DrawPreparedVertices(
        commandId = commandId,
        artifactKey = artifactKey,
        topologyIdentity = topologyIdentity,
        layoutIdentity = layoutIdentity,
        materialIdentity = materialIdentity,
        transformBytes = transformBytes,
        transform = transform,
        clip = clip,
        layer = layer,
        blend = blend,
        preparedBlendPlan = preparedBlendPlan,
        bounds = bounds,
        ordering = ordering,
        source = source,
        clipIdentity = clipIdentity,
        clipCoverageIdentity = clipCoverageIdentity,
        primitiveColorPresent = primitiveColorPresent,
        primitiveBlendIdentity = primitiveBlendIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        drawProvenance = drawProvenance,
    )

    private fun PreparedVerticesFrameInventory.testRebindCommandIds(
        commandIdByOperationIndex: Map<Int, Int>,
    ): PreparedVerticesFrameInventory? = when (val rebound = bindCommandIds(commandIdByOperationIndex)) {
        is PreparedVerticesCommandBindingResult.Ready -> rebound.inventory
        is PreparedVerticesCommandBindingResult.Refused -> null
    }

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPURecording
        .withForgedPacketTargetState(): org.graphiks.kanvas.gpu.renderer.recording.GPURecording {
        val changedTasks = taskList.tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val changedPackets = task.drawPackets.map { packet ->
                org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket(
                    packetId = packet.packetId,
                    commandIdValue = packet.commandIdValue,
                    analysisRecordId = packet.analysisRecordId,
                    passId = packet.passId,
                    layerId = packet.layerId,
                    bindingListId = packet.bindingListId,
                    insertionReasonCode = packet.insertionReasonCode,
                    sortKey = packet.sortKey,
                    sortKeyPreimage = packet.sortKeyPreimage,
                    renderStepId = packet.renderStepId,
                    renderStepVersion = packet.renderStepVersion,
                    role = packet.role,
                    blendPlan = packet.blendPlan,
                    renderPipelineKey = packet.renderPipelineKey,
                    computePipelineKey = packet.computePipelineKey,
                    bindingLayoutHash = packet.bindingLayoutHash,
                    uniformSlot = packet.uniformSlot,
                    resourceSlot = packet.resourceSlot,
                    semanticPayload = packet.semanticPayload,
                    vertexSourceLabel = packet.vertexSourceLabel,
                    scissorBoundsHash = packet.scissorBoundsHash,
                    targetStateHash = "forged.target-state",
                    originalPaintOrder = packet.originalPaintOrder,
                    resourceGeneration = packet.resourceGeneration,
                    frameProvenance = packet.frameProvenance,
                    clipCoveragePlan = packet.clipCoveragePlan,
                    clipExecutionPlan = packet.clipExecutionPlan,
                    diagnostics = packet.diagnostics,
                    clipProducerAuthority = packet.clipProducerAuthority,
                )
            }
            GPUTask.Render(
                taskId = task.taskId,
                recordingId = task.recordingId,
                phase = task.phase,
                target = task.target,
                loadStore = task.loadStore,
                samplePlan = task.samplePlan,
                resourceUses = task.resourceUses,
                provisionalSegmentKey = task.provisionalSegmentKey,
                drawPackets = changedPackets,
                batchEligibilityByPacketId = task.batchEligibilityByPacketId,
                sampleContinuationKey = task.sampleContinuationKey,
                compositeMembership = task.compositeMembership,
                depthStencilLoadStore = task.depthStencilLoadStore,
                preparedImageBindingsByPacketId = task.preparedImageBindingsByPacketId,
                preparedTextBindingsByPacketId = task.preparedTextBindingsByPacketId,
            )
        }
        val changedTaskList = org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList(
            frameId = taskList.frameId,
            capabilitySeal = taskList.capabilitySeal,
            recordingSeals = taskList.recordingSeals,
            expectedReplayKeyHash = taskList.expectedReplayKeyHash,
            tasks = changedTasks,
            dependencies = taskList.dependencies,
            phaseOrder = taskList.phaseOrder,
            memoryBudget = taskList.memoryBudget,
            diagnostics = taskList.diagnostics,
        )
        return copy(taskList = changedTaskList)
    }

    private fun plan(operations: List<DisplayOp>) = GPUFramePathApiInventory.plan(
        operations = operations,
        target = GPUTargetFacts(32, 24, "rgba8unorm"),
        config = RenderConfig.DEFAULT,
        capabilities = CAPABILITIES,
    )

    private fun rect() = DisplayOp.DrawRect(
        Rect.fromLTRB(1f, 1f, 7f, 7f),
        Paint.fill(Color.GREEN).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun vertices(
        color: Color,
        transform: Matrix33 = Matrix33.identity(),
    ) = DisplayOp.DrawVertices(
        Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(4f, 0f), Point(0f, 4f)),
        ),
        Paint.fill(color),
        transform,
        ClipStack.WideOpen,
    )

    private companion object {
        val BOUNDS = GPUPixelBounds(0, 0, 32, 24)
        val CAPABILITIES: GPUCapabilities = GPUProductFlagConfig().buildCapabilities().let { base ->
            GPUCapabilities(
                implementation = base.implementation,
                facts = base.facts + GPUCapabilityFact(
                    "first_slice.fill_rect.native", "test", "supported", true, "vertices-semantic",
                ),
                knownUnsupportedFacts = base.knownUnsupportedFacts,
                snapshotId = "fp06-task7-vertices-semantic",
                limits = base.limits,
            )
        }
    }
}
