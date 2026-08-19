package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.surface.RenderConfig

const val GPU_FRAME_PROVENANCE_ANNOTATION_KEY: String = "kanvas.frame.provenance"

enum class GPUFramePathStateKind {
    Transform,
    Clip,
    Annotation,
    FlushSnapshot,
}

data class GPUFramePathStateEvent(
    val operationIndex: Int,
    val kind: GPUFramePathStateKind,
)

data class GPUFramePathTelemetryInput(
    val commandId: GPUDrawCommandID,
    val paintOrder: Int,
    val provenance: GPUFrameProvenance,
)

data class GPUFramePathVisualCommand(
    val normalized: NormalizedDrawCommand,
    val targetSpaceBounds: GPUBounds,
    val geometryCoverage: GPUCoverageConsumption,
    val clipCoverage: GPUClipCoveragePlan,
    val clipExecutionPlan: GPUClipExecutionPlan,
    val blendPlan: GPUBlendPlan,
    val provenance: GPUFrameProvenance,
    val geometryRefusal: GPUCorePrimitiveGeometryRefusal? = null,
    val preparedImage: GPUPreparedImageDrawFacts? = null,
    val preparedText: GPUPreparedTextSubRun? = null,
)

data class GPUFramePathInventoryPlan(
    val target: GPUTargetFacts,
    val visualCommands: List<GPUFramePathVisualCommand>,
    val normalizedCommands: List<NormalizedDrawCommand>,
    val stateEvents: List<GPUFramePathStateEvent>,
    val telemetryInputs: List<GPUFramePathTelemetryInput>,
    val recording: GPURecording,
    val framePlan: GPUFramePlan,
    val preparedRefusal: GPUPreparedOperationRefusal?,
    val preparedTextInventory: PreparedTextFrameInventory? = null,
    val preparedVerticesInventory: PreparedVerticesFrameInventory? = null,
    val allocatedCommandIds: Set<Int> = emptySet(),
)

/**
 * Read-only evidence harness over the production [GPUOpMapper] → recorder → planner contracts.
 *
 * Until the Task 12A-C Surface cutover, this inventory is neither the active Surface route nor
 * proof that a product frame reached one native queue submit. It only makes handle-free route
 * decisions and prepared-frame evidence inspectable by tests.
 */
object GPUFramePathApiInventory {
    fun plan(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities = GPUProductFlagConfig.fromSystemProperties().buildCapabilities(),
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(0),
    ): GPUFramePathInventoryPlan {
        val operationSnapshot = operations.toList()
        val textPreparation = GPUPreparedTextFramePreparer.prepareInventory(
            operations = operationSnapshot,
            target = target,
            capabilities = capabilities,
            generation = GPUTextArtifactGeneration(0),
        )
        var publishedTextInventory: PreparedTextFrameInventory? = null
        var publishedVerticesInventory: PreparedVerticesFrameInventory? = null
        val mapping = when (textPreparation) {
            is GPUPreparedTextFrameInventoryPreparation.Refused ->
                refusedMapping(textPreparation.refusal)
            is GPUPreparedTextFrameInventoryPreparation.Ready -> when (
                val verticesPreparation = GPUPreparedVerticesFramePreparer.prepare(
                    operations = operationSnapshot,
                    target = target,
                    config = config,
                    capabilities = capabilities,
                    preparedTextInventory = textPreparation.inventory,
                )
            ) {
                is GPUPreparedVerticesFramePreparation.Refused ->
                    refusedMapping(verticesPreparation.refusal)
                is GPUPreparedVerticesFramePreparation.Ready -> {
                    publishedTextInventory = textPreparation.inventory
                    publishedVerticesInventory = verticesPreparation.inventory
                    verticesPreparation.mapping
                }
            }
        }
        val visual = mapping.visualCommands

        val recorder = GPURecorder(
            recordingId = GPURecordingID("kanvas.frame-path"),
            frameId = GPUFrameID(0),
            capabilities = capabilities,
            deviceGeneration = deviceGeneration,
        )
        val preparedVerticesCommands = publishedVerticesInventory
            ?.normalizedCommands(target, capabilities)
            .orEmpty()
        val normalizedCommands = (visual.map(GPUFramePathVisualCommand::normalized) +
            preparedVerticesCommands).sortedBy { command -> command.commandId.value }
        normalizedCommands.forEach(recorder::record)
        val recording = recorder.close()
        val framePlan = GPUFramePlanner.plan(recording.taskList)
        return GPUFramePathInventoryPlan(
            target = target,
            visualCommands = visual.toList(),
            normalizedCommands = normalizedCommands,
            stateEvents = mapping.stateEvents,
            telemetryInputs = normalizedCommands.map { command ->
                GPUFramePathTelemetryInput(
                    commandId = command.commandId,
                    paintOrder = command.ordering.paintOrder,
                    provenance = command.source.frameProvenance,
                )
            },
            recording = recording,
            framePlan = framePlan,
            preparedRefusal = mapping.preparedRefusal,
            preparedTextInventory =
                publishedTextInventory,
            preparedVerticesInventory = publishedVerticesInventory,
            allocatedCommandIds = mapping.allocatedCommandIds,
        )
    }

    private fun refusedMapping(refusal: GPUPreparedOperationRefusal): GPUOpMapping =
        GPUOpMapping(
            visualCommands = emptyList(),
            stateEvents = emptyList(),
            preparedRefusal = refusal,
        )

    /** Adds the canonical native frame envelope after the mapper and recorder have made route decisions. */
    fun prepareNativeTaskList(
        inventory: GPUFramePathInventoryPlan,
        capabilities: GPUCapabilities,
        targetBounds: GPUPixelBounds,
        readbackRequestId: GPUReadbackRequestID? = null,
    ): GPUCorePrimitivePreparedFrameResult {
        preflightSemanticOnlyVertices(inventory, targetBounds)?.let { diagnostic ->
            return GPUCorePrimitivePreparedFrameResult.Refused(diagnostic)
        }
        val semantics = when (val gathered = gatherCorePrimitiveSemantics(inventory, targetBounds)) {
            is GPUCorePrimitiveSemanticGatherResult.Gathered -> gathered.semantics
            is GPUCorePrimitiveSemanticGatherResult.Refused -> return GPUCorePrimitivePreparedFrameResult.Refused(
                GPUDiagnostic(
                    code = GPUDiagnosticCode(gathered.code),
                    domain = GPUDiagnosticDomain.Recording,
                    severity = GPUDiagnosticSeverity.Error,
                    message = gathered.message,
                    facts = gathered.facts,
                ),
            )
        }
        return GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = inventory.recording.taskList,
                capabilities = capabilities,
                target = GPUFrameTargetRef("frame.scene"),
                targetBounds = targetBounds,
                semanticsByCommandId = semantics,
                readbackRequestId = readbackRequestId,
                targetFormat = GPUColorFormat(inventory.target.colorFormat),
            ),
        )
    }

    /** Builds the heterogeneous direct prepared seam without opening the Surface product gate. */
    fun preparePreparedNativeTaskList(
        inventory: GPUFramePathInventoryPlan,
        capabilities: GPUCapabilities,
        targetBounds: GPUPixelBounds,
        readbackRequestId: GPUReadbackRequestID? = null,
    ): GPUPreparedSurfaceFrameResult {
        inventory.preparedRefusal?.let { refusal ->
            return GPUPreparedSurfaceFrameResult.Refused(
                preparedRefusalDiagnostic(refusal),
            )
        }
        preflightSemanticOnlyVertices(inventory, targetBounds)?.let { diagnostic ->
            return GPUPreparedSurfaceFrameResult.Refused(diagnostic)
        }
        val artifacts = inventory.visualCommands.mapNotNull { visual ->
            visual.preparedImage?.artifact?.let { artifact ->
                visual.normalized.commandId.value to artifact
            }
        }.toMap()
        val semantics = when (
            val gathered = gatherPreparedSurfaceSemantics(
                inventory = inventory,
                targetBounds = targetBounds,
                imageArtifactsByCommandId = artifacts,
            )
        ) {
            is GPUPreparedSurfaceSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
            is GPUPreparedSurfaceSemanticGatherResult.Refused ->
                return GPUPreparedSurfaceFrameResult.Refused(gathered.diagnostic)
        }
        return GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = inventory.recording.taskList,
                capabilities = capabilities,
                target = GPUFrameTargetRef("frame.scene"),
                targetBounds = targetBounds,
                semanticsByCommandId = semantics,
                readbackRequestId = readbackRequestId,
                targetFormat = GPUColorFormat(inventory.target.colorFormat),
            ),
        )
    }

    /** Delegates exact handle-free semantics to the production builder. */
    internal fun gatherCorePrimitiveSemantics(
        inventory: GPUFramePathInventoryPlan,
        targetBounds: GPUPixelBounds,
    ): GPUCorePrimitiveSemanticGatherResult = GPUCorePrimitiveSemanticBuilder.gather(
        visualCommands = inventory.visualCommands,
        recording = inventory.recording,
        targetBounds = targetBounds,
        blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
    )

    /** Read-only heterogeneous semantic evidence; this does not open the Surface product gate. */
    internal fun gatherPreparedSurfaceSemantics(
        inventory: GPUFramePathInventoryPlan,
        targetBounds: GPUPixelBounds,
        imageArtifactsByCommandId: Map<Int, GPUPreparedImageUploadArtifact>,
    ): GPUPreparedSurfaceSemanticGatherResult {
        val textSemantics = inventory.preparedTextInventory?.let { preparedText ->
            when (
                val gathered = GPUPreparedTextSemanticBuilder.gather(
                    visualCommands = inventory.visualCommands,
                    inventory = preparedText,
                    targetBounds = targetBounds,
                )
            ) {
                is GPUPreparedTextSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                is GPUPreparedTextSemanticGatherResult.Refused ->
                    return GPUPreparedSurfaceSemanticGatherResult.Refused(
                        GPUDiagnostic(
                            code = GPUDiagnosticCode(gathered.code),
                            domain = GPUDiagnosticDomain.Recording,
                            severity = GPUDiagnosticSeverity.Error,
                            message = gathered.message,
                        ),
                    )
            }
        }.orEmpty()
        return GPUPreparedSurfaceSemanticBuilder.gather(
            visualCommands = inventory.visualCommands,
            normalizedCommands = inventory.normalizedCommands,
            recording = inventory.recording,
            targetBounds = targetBounds,
            imageArtifactsByCommandId = imageArtifactsByCommandId,
            textSemanticsByCommandId = textSemantics,
            verticesSemanticsByCommandId = inventory.preparedVerticesInventory?.let {
                when (val gathered = GPUPreparedVerticesSemanticBuilder.gather(
                    normalizedCommands = inventory.normalizedCommands,
                    inventory = it,
                    recording = inventory.recording,
                    target = inventory.target,
                    targetBounds = targetBounds,
                )) {
                    is GPUPreparedVerticesSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                    is GPUPreparedVerticesSemanticGatherResult.Refused -> return GPUPreparedSurfaceSemanticGatherResult.Refused(
                        GPUDiagnostic(
                            code = GPUDiagnosticCode(gathered.code),
                            domain = GPUDiagnosticDomain.Recording,
                            severity = GPUDiagnosticSeverity.Error,
                            message = gathered.message,
                            facts = gathered.facts,
                        ),
                    )
                }
            }.orEmpty(),
            blendAuthorityPolicy = GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness,
        )
    }

    private fun preflightSemanticOnlyVertices(
        inventory: GPUFramePathInventoryPlan,
        targetBounds: GPUPixelBounds,
    ): GPUDiagnostic? {
        val semanticOnlyCommandIds = inventory.recording.semanticOnlyDraws
            .map { draw -> draw.packet.commandIdValue }
        val verticesInventory = inventory.preparedVerticesInventory
        val inventoryCommandIds = verticesInventory?.mappedCommands
            ?.map { mapped -> mapped.commandId }
            .orEmpty()
        if (semanticOnlyCommandIds.isEmpty() && inventoryCommandIds.isEmpty()) return null
        if (verticesInventory == null) return GPUDiagnostic(
            code = GPUDiagnosticCode("invalid.surface.prepared.semantic-command-bijection"),
            domain = GPUDiagnosticDomain.Recording,
            severity = GPUDiagnosticSeverity.Error,
            message = "Semantic-only vertices recording evidence requires its exact frame inventory.",
        )
        return when (val gathered = GPUPreparedVerticesSemanticBuilder.gather(
            normalizedCommands = inventory.normalizedCommands,
            inventory = verticesInventory,
            recording = inventory.recording,
            target = inventory.target,
            targetBounds = targetBounds,
        )) {
            is GPUPreparedVerticesSemanticGatherResult.Refused -> GPUDiagnostic(
                code = GPUDiagnosticCode(gathered.code),
                domain = GPUDiagnosticDomain.Recording,
                severity = GPUDiagnosticSeverity.Error,
                message = gathered.message,
                facts = gathered.facts,
            )
            is GPUPreparedVerticesSemanticGatherResult.Gathered -> GPUDiagnostic(
                code = GPUDiagnosticCode(
                    PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE,
                ),
                domain = GPUDiagnosticDomain.Recording,
                severity = GPUDiagnosticSeverity.Error,
                message = "Prepared vertices semantics have no executable native materialization route.",
                facts = mapOf(
                    "semanticOnlyCommandIds" to semanticOnlyCommandIds.joinToString(","),
                    "verticesCommandIds" to gathered.semanticsByCommandId.keys.joinToString(","),
                    "state" to "prepared_vertices_unmaterialized",
                ),
            )
        }
    }

    private fun preparedRefusalDiagnostic(refusal: GPUPreparedOperationRefusal): GPUDiagnostic =
        GPUDiagnostic(
            code = GPUDiagnosticCode(refusal.code),
            domain = GPUDiagnosticDomain.Recording,
            severity = GPUDiagnosticSeverity.Error,
            message = "Prepared inventory operation could not be lowered.",
            facts = refusal.facts + mapOf(
                "boundary" to "inventory",
                "commandId" to refusal.commandId.toString(),
                "operationIndex" to refusal.operationIndex.toString(),
            ),
        )
}

internal fun PreparedVerticesFrameInventory.normalizedCommands(
    target: GPUTargetFacts,
    capabilities: GPUCapabilities,
): List<NormalizedDrawCommand.DrawPreparedVertices> = mappedCommands.map { mapped ->
    val command = commandsByOperationIndex.getValue(mapped.operationIndex)
    val draw = command.draw
    val matrix = draw.transform
    val transformType = when {
        matrix.persp0 != 0f || matrix.persp1 != 0f || matrix.persp2 != 1f -> GPUTransformType.Perspective
        matrix.scaleX == 1f && matrix.scaleY == 1f && matrix.skewX == 0f && matrix.skewY == 0f &&
            matrix.transX == 0f && matrix.transY == 0f -> GPUTransformType.Identity
        matrix.scaleX == 1f && matrix.scaleY == 1f && matrix.skewX == 0f && matrix.skewY == 0f ->
            GPUTransformType.Translate
        matrix.skewX == 0f && matrix.skewY == 0f -> GPUTransformType.Scale
        else -> GPUTransformType.Affine
    }
    val coverage = draw.clipSnapshot.coveragePlan
    val clipKind = when (coverage) {
        GPUClipCoveragePlan.NoClip -> GPUClipKind.WideOpen
        is GPUClipCoveragePlan.Scissor -> GPUClipKind.DeviceRect
        else -> GPUClipKind.ComplexStack
    }
    NormalizedDrawCommand.DrawPreparedVertices(
        commandId = GPUDrawCommandID(mapped.commandId),
        artifactKey = command.artifactKey,
        topologyIdentity = command.artifact.topology.sourceLabel,
        layoutIdentity = command.artifact.normalizedLayoutIdentity(),
        materialIdentity = command.materialFrameSnapshot.identity.bucketKey,
        transformBytes = listOf(
            matrix.scaleX, matrix.skewX, matrix.transX,
            matrix.skewY, matrix.scaleY, matrix.transY,
            matrix.persp0, matrix.persp1, matrix.persp2,
        ).map(Float::toRawBits),
        transform = GPUTransformFacts(
            type = transformType,
            translateX = matrix.transX,
            translateY = matrix.transY,
            scaleX = matrix.scaleX,
            scaleY = matrix.scaleY,
            skewX = matrix.skewX,
            skewY = matrix.skewY,
        ),
        clip = GPUClipFacts(
            kind = clipKind,
            bounds = draw.clipSnapshot.scissorBounds ?: draw.deviceBounds,
            coveragePlan = coverage,
        ),
        layer = GPULayerFacts.root(target),
        blend = draw.finalBlend,
        preparedBlendPlan = draw.blendPlan,
        bounds = draw.clippedBounds ?: draw.deviceBounds,
        ordering = GPUOrderingFacts(
            paintOrder = mapped.commandId,
            dependsOnDestination = draw.blendPlan.destinationReadRequirement ==
                GPUBlendDestinationReadRequirement.DestinationTextureRequired,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(
            adapter = "kanvas-surface",
            operation = when (draw.operationKind) {
                GPUPreparedVerticesOperationKind.DrawVertices -> "drawVertices.prepared"
                GPUPreparedVerticesOperationKind.DrawMesh -> "drawMesh.prepared"
            },
            frameProvenance = mapped.frameProvenance,
        ),
        clipIdentity = draw.clipSnapshot.identity,
        clipCoverageIdentity = coverage.preparedVerticesSemanticIdentity(),
        primitiveColorPresent = draw.primitiveColorPresent,
        primitiveBlendIdentity = draw.primitiveBlendPlan?.plan?.canonicalIdentity(),
        capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
        drawProvenance = draw.provenance,
    )
}
