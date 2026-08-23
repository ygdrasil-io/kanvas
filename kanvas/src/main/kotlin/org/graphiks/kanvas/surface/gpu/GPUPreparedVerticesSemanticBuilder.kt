package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFrameIdentityAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesTopologyIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask

internal sealed interface GPUPreparedVerticesSemanticGatherResult {
    data class Gathered(
        val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload.Vertices>,
    ) : GPUPreparedVerticesSemanticGatherResult

    class Refused internal constructor(
        val code: String,
        val message: String,
        facts: Map<String, String>,
    ) : GPUPreparedVerticesSemanticGatherResult {
        val facts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

/** Authenticates the inventory/normalized/recording bijection before publishing vertices semantics. */
internal object GPUPreparedVerticesSemanticBuilder {
    fun gather(
        normalizedCommands: List<NormalizedDrawCommand>,
        inventory: PreparedVerticesFrameInventory,
        recording: GPURecording,
        target: GPUTargetFacts,
        targetBounds: GPUPixelBounds,
    ): GPUPreparedVerticesSemanticGatherResult {
        val normalizedIds = normalizedCommands.map { it.commandId.value }
        val verticesCommands = normalizedCommands.filterIsInstance<NormalizedDrawCommand.DrawPreparedVertices>()
        val verticesIds = verticesCommands.map { it.commandId.value }
        val inventoryIds = inventory.mappedCommands.map { it.commandId }
        val mappedByCommandId = inventory.mappedCommands.associateBy { it.commandId }
        val analysisIds = recording.analysis.records.map { record -> record.commandIdValue }
        val analysis = recording.analysis.records.associateBy { record -> record.commandIdValue }
        val orderedPackets = recording.taskList.tasks.flatMap { task ->
            when (task) {
                is GPUTask.Render -> task.drawPackets
                is GPUTask.SemanticOnly -> listOf(task.draw.packet)
                else -> emptyList()
            }
        }
        val packetIds = orderedPackets.map(GPUDrawPacket::commandIdValue)
        val packets = orderedPackets.associateBy(GPUDrawPacket::commandIdValue)
        val semanticOnlyIds = recording.semanticOnlyDraws.map { draw -> draw.packet.commandIdValue }
        val semanticOnlyDraws = recording.semanticOnlyDraws.associateBy { draw -> draw.packet.commandIdValue }
        val recordedIds = recording.recordedCommands.map { it.commandId.value }
        val decisionLineByCommandId = analysisIds
            .zip(recording.analysisDecisionDump.lines)
            .toMap(LinkedHashMap())
        val mappedOperationIds = inventory.mappedCommands.map { it.operationIndex }
        val expectedArtifactKeysByCommandId = inventory.mappedCommands.associate { mapped ->
            mapped.commandId to mapped.artifactKey
        }
        val mappedInventoryIsBijective =
            inventoryIds.distinct().size == inventoryIds.size &&
                mappedOperationIds.distinct().size == mappedOperationIds.size &&
                mappedOperationIds.toSet() == inventory.commandsByOperationIndex.keys &&
                inventory.artifactKeyByCommandId == expectedArtifactKeysByCommandId &&
                inventory.mappedCommands.all { mapped ->
                    inventory.commandsByOperationIndex[mapped.operationIndex]?.artifactKey == mapped.artifactKey
                }
        if (normalizedIds.distinct().size != normalizedIds.size ||
            normalizedIds != recordedIds ||
            normalizedIds != analysisIds ||
            normalizedIds != packetIds ||
            verticesIds != semanticOnlyIds ||
            analysis.size != analysisIds.size ||
            packets.size != packetIds.size ||
            semanticOnlyDraws.size != semanticOnlyIds.size ||
            recording.analysisDecisionDump.lines.size != normalizedIds.size ||
            decisionLineByCommandId.size != normalizedIds.size ||
            verticesIds != inventoryIds || !mappedInventoryIsBijective ||
            mappedByCommandId.size != inventoryIds.size ||
            verticesIds.toSet() != inventory.artifactKeyByCommandId.keys
        ) {
            return refused(
                "invalid.surface.prepared.semantic-command-bijection",
                "Prepared-surface normalized commands, vertices inventory, analysis records, packets, and semantics must be bijective.",
                mapOf(
                    "normalizedIds" to normalizedIds.joinToString(","),
                    "recordedIds" to recordedIds.joinToString(","),
                    "verticesIds" to verticesIds.joinToString(","),
                    "inventoryIds" to inventoryIds.joinToString(","),
                ),
            )
        }

        val result = linkedMapOf<Int, GPUDrawSemanticPayload.Vertices>()
        verticesCommands.forEach { normalized ->
            val mapped = mappedByCommandId.getValue(normalized.commandId.value)
            val inventoryCommand = inventory.commandsByOperationIndex.getValue(mapped.operationIndex)
            val draw = inventoryCommand.draw
            val packet = packets.getValue(mapped.commandId)
            val semanticOnlyDraw = semanticOnlyDraws.getValue(mapped.commandId)
            val record = analysis.getValue(mapped.commandId)
            val expectedTransform = draw.transform.let { matrix ->
                listOf(
                    matrix.sx, matrix.kx, matrix.tx,
                    matrix.ky, matrix.sy, matrix.ty,
                    matrix.persp0, matrix.persp1, matrix.persp2,
                ).map(Float::toRawBits)
            }
            val materialIdentity = inventoryCommand.materialFrameSnapshot.identity
            val expectedTransformFacts = normalizedTransformFacts(draw.transform)
            val expectedClipKind = when (draw.clipSnapshot.coveragePlan) {
                GPUClipCoveragePlan.NoClip -> org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind.WideOpen
                is GPUClipCoveragePlan.Scissor -> org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind.DeviceRect
                else -> org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind.ComplexStack
            }
            val expectedClipBounds = draw.clipSnapshot.scissorBounds ?: draw.deviceBounds
            val expectedBounds = draw.clippedBounds ?: draw.deviceBounds
            val expectedOperation = when (draw.operationKind) {
                GPUPreparedVerticesOperationKind.DrawVertices -> "drawVertices.prepared"
                GPUPreparedVerticesOperationKind.DrawMesh -> "drawMesh.prepared"
            }
            val expectedBoundsHash = expectedBounds.preparedVerticesBoundsHash()
            val expectedTargetHash = "target.${target.colorFormat}.${target.width}x${target.height}"
            val expectedRecordId = "analysis.draw_prepared_vertices.${mapped.commandId}"
            val expectedDecisionLine =
                "decision:discard:$expectedRecordId:prepared_vertices_unmaterialized"
            val expectedScissorHash = draw.clipSnapshot.coveragePlan.preparedVerticesSemanticIdentity()
            if (normalized.artifactKey != inventoryCommand.artifactKey ||
                normalized.artifactKey != draw.artifact.key ||
                normalized.artifactKey != mapped.artifactKey ||
                normalized.topologyIdentity != inventoryCommand.artifact.topology.sourceLabel ||
                normalized.layoutIdentity != inventoryCommand.artifact.normalizedLayoutIdentity() ||
                normalized.material !== null ||
                !GPUPreparedMaterialFrameIdentityAuthority.exactlyMatches(
                    inventoryCommand.material,
                    draw.material,
                ) ||
                materialIdentity.bucketKey != normalized.materialIdentity ||
                expectedTransform != normalized.transformBytes ||
                normalized.transform != expectedTransformFacts ||
                normalized.clip.kind != expectedClipKind ||
                normalized.clip.bounds != expectedClipBounds ||
                normalized.clip.coverageRequest != null ||
                normalized.clip.coveragePlan != draw.clipSnapshot.coveragePlan ||
                normalized.clip.executionPlan != null ||
                normalized.clip.perspectiveCaptureRefusal ||
                normalized.layer != org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts.root(target) ||
                normalized.blend != draw.finalBlend ||
                normalized.bounds != expectedBounds ||
                normalized.ordering.paintOrder != mapped.commandId ||
                normalized.ordering.dependsOnDestination !=
                    (draw.blendPlan.destinationReadRequirement ==
                        org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement.DestinationTextureRequired) ||
                normalized.ordering.requiresBarrier ||
                normalized.source.adapter != "kanvas-surface" ||
                normalized.source.operation != expectedOperation ||
                normalized.clipIdentity != draw.clipSnapshot.identity ||
                normalized.clipCoverageIdentity != draw.clipSnapshot.coveragePlan.preparedVerticesSemanticIdentity() ||
                normalized.primitiveColorPresent != draw.primitiveColorPresent ||
                normalized.primitiveBlendIdentity != draw.primitiveBlendPlan?.plan?.canonicalIdentity() ||
                normalized.preparedBlendPlan != draw.blendPlan ||
                normalized.drawProvenance != draw.provenance ||
                normalized.capabilitySnapshotHash != inventory.capabilitySnapshotHash ||
                normalized.source.frameProvenance != mapped.frameProvenance ||
                targetBounds.width != target.width || targetBounds.height != target.height ||
                record.recordId != expectedRecordId ||
                record.commandFamily != "DrawPreparedVertices" ||
                record.boundsHash != expectedBoundsHash ||
                record.routeDecisionLabel != "prepared.vertices.semantic" ||
                record.materialKeyHash != normalized.materialIdentity ||
                record.renderStepCandidates != listOf(PREPARED_VERTICES_RENDER_STEP_IDENTITY) ||
                record.sortKey.value != normalized.ordering.paintOrder.toLong() ||
                record.diagnostics.isNotEmpty() ||
                record.corePrimitiveRectRouteAuthority != null ||
                record.corePrimitiveRectGeometryAuthority != null ||
                record.corePrimitiveRRectGeometryAuthority != null ||
                decisionLineByCommandId.getValue(mapped.commandId) != expectedDecisionLine ||
                semanticOnlyDraw.stateLabel != "prepared_vertices_unmaterialized" ||
                !packet.exactlyMatchesPreparedVerticesSemanticPacket(semanticOnlyDraw.packet) ||
                packet.packetId.value != "packet.${mapped.commandId}.0" ||
                packet.analysisRecordId != expectedRecordId ||
                packet.passId != "semantic-only.prepared_vertices.${mapped.commandId}" ||
                packet.layerId != "root" || packet.bindingListId != "bindings.${mapped.commandId}" ||
                packet.insertionReasonCode != "paint-order" ||
                packet.sortKey != normalized.ordering.paintOrder.toLong() ||
                packet.sortKeyPreimage != "paint-order:${normalized.ordering.paintOrder}" ||
                packet.renderStepId.value != PREPARED_VERTICES_RENDER_STEP_IDENTITY ||
                packet.renderStepVersion != 1 || packet.role != GPUDrawPacketRole.Discard ||
                packet.blendPlan != draw.blendPlan ||
                packet.renderPipelineKey != null || packet.computePipelineKey != null ||
                packet.bindingLayoutHash != "unmaterialized" ||
                packet.uniformSlot != null || packet.resourceSlot != null || packet.semanticPayload != null ||
                packet.vertexSourceLabel != "unmaterialized" ||
                packet.scissorBoundsHash != expectedScissorHash ||
                packet.targetStateHash != expectedTargetHash ||
                packet.originalPaintOrder != normalized.ordering.paintOrder || packet.resourceGeneration != 0L ||
                packet.frameProvenance != mapped.frameProvenance ||
                packet.clipCoveragePlan != draw.clipSnapshot.coveragePlan ||
                packet.clipExecutionPlan != null || packet.diagnostics.isNotEmpty() ||
                packet.clipProducerAuthority != null
            ) {
                return refusedAuthority(
                    mapped.commandId,
                    "normalized_recording_fact_mismatch",
                    mapOf(
                        "artifact" to (normalized.artifactKey == inventoryCommand.artifactKey && normalized.artifactKey == draw.artifact.key).toString(),
                        "material" to (materialIdentity.bucketKey == normalized.materialIdentity).toString(),
                        "transform" to (expectedTransform == normalized.transformBytes).toString(),
                        "clip" to (normalized.clipIdentity == draw.clipSnapshot.identity && packet.clipCoveragePlan == draw.clipSnapshot.coveragePlan).toString(),
                        "primitive" to (normalized.primitiveColorPresent == draw.primitiveColorPresent && normalized.primitiveBlendIdentity == draw.primitiveBlendPlan?.plan?.canonicalIdentity()).toString(),
                        "blend" to (packet.blendPlan == draw.blendPlan).toString(),
                        "provenance" to (packet.frameProvenance == mapped.frameProvenance).toString(),
                        "recordFamily" to record.commandFamily,
                        "renderSteps" to record.renderStepCandidates.joinToString(","),
                    ),
                )
            }
            val scissor = draw.clipSnapshot.scissorBounds?.let { bounds ->
                val left = floor(bounds.left.toDouble()).toInt().coerceAtLeast(targetBounds.left)
                val top = floor(bounds.top.toDouble()).toInt().coerceAtLeast(targetBounds.top)
                val right = ceil(bounds.right.toDouble()).toInt().coerceAtMost(targetBounds.right)
                val bottom = ceil(bounds.bottom.toDouble()).toInt().coerceAtMost(targetBounds.bottom)
                if (left < right && top < bottom) GPUPixelBounds(left, top, right, bottom) else null
            } ?: if (draw.clipSnapshot.scissorBounds == null) {
                targetBounds
            } else {
                return refusedAuthority(mapped.commandId, "invalid_scissor_bounds")
            }
            val payload = when (val gathered = GPUPreparedVerticesPayloadGatherer.gather(
                GPUPreparedVerticesPayloadInput(
                    payloadRef = GPUDrawPayloadRef(mapped.commandId, PREPARED_VERTICES_RENDER_STEP_IDENTITY),
                    artifact = inventoryCommand.artifact,
                    material = inventoryCommand.material,
                    materialFrameSnapshot = inventoryCommand.materialFrameSnapshot,
                    topologyIdentity = when (inventoryCommand.artifact.topology.sourceLabel) {
                        "Triangles" -> GPUPreparedVerticesTopologyIdentity.Triangles
                        "TriangleStrip" -> GPUPreparedVerticesTopologyIdentity.TriangleStrip
                        else -> return refusedAuthority(mapped.commandId, "unsupported_topology")
                    },
                    transformBytes = normalized.transformBytes,
                    targetBounds = targetBounds,
                    scissorBounds = scissor,
                    targetFormat = normalized.layer.target.colorFormat,
                    clipIdentity = normalized.clipIdentity,
                    clipCoverageIdentity = normalized.clipCoverageIdentity,
                    primitiveColorPresent = normalized.primitiveColorPresent,
                    primitiveBlendIdentity = normalized.primitiveBlendIdentity,
                    finalBlendIdentity = draw.blendPlan.canonicalIdentity(),
                    capabilitySnapshotHash = normalized.capabilitySnapshotHash,
                    drawProvenance = normalized.drawProvenance,
                    frameProvenance = mapped.frameProvenance,
                ),
            )) {
                is GPUPreparedVerticesPayloadResult.Ready -> gathered.payload
                is GPUPreparedVerticesPayloadResult.Refused -> return GPUPreparedVerticesSemanticGatherResult.Refused(
                    gathered.code,
                    "Prepared vertices payload validation failed.",
                    gathered.facts + ("commandId" to mapped.commandId.toString()),
                )
            }
            result[mapped.commandId] = payload
        }
        return GPUPreparedVerticesSemanticGatherResult.Gathered(
            Collections.unmodifiableMap(result),
        )
    }

    private fun refusedAuthority(
        commandId: Int,
        reason: String,
        extraFacts: Map<String, String> = emptyMap(),
    ) = refused(
        "invalid.surface.prepared.vertices-semantic-authority",
        "Normalized prepared vertices facts must exactly match inventory and recording authority.",
        mapOf("commandId" to commandId.toString(), "reason" to reason) + extraFacts,
    )

    private fun refused(code: String, message: String, facts: Map<String, String>) =
        GPUPreparedVerticesSemanticGatherResult.Refused(code, message, facts)
}

private fun GPUDrawPacket.exactlyMatchesPreparedVerticesSemanticPacket(
    other: GPUDrawPacket,
): Boolean =
    packetId == other.packetId &&
        commandIdValue == other.commandIdValue &&
        analysisRecordId == other.analysisRecordId &&
        passId == other.passId &&
        layerId == other.layerId &&
        bindingListId == other.bindingListId &&
        insertionReasonCode == other.insertionReasonCode &&
        sortKey == other.sortKey &&
        sortKeyPreimage == other.sortKeyPreimage &&
        renderStepId == other.renderStepId &&
        renderStepVersion == other.renderStepVersion &&
        role == other.role &&
        blendPlan == other.blendPlan &&
        renderPipelineKey == other.renderPipelineKey &&
        computePipelineKey == other.computePipelineKey &&
        bindingLayoutHash == other.bindingLayoutHash &&
        uniformSlot == other.uniformSlot &&
        resourceSlot == other.resourceSlot &&
        semanticPayload == other.semanticPayload &&
        vertexSourceLabel == other.vertexSourceLabel &&
        scissorBoundsHash == other.scissorBoundsHash &&
        targetStateHash == other.targetStateHash &&
        originalPaintOrder == other.originalPaintOrder &&
        resourceGeneration == other.resourceGeneration &&
        frameProvenance == other.frameProvenance &&
        clipCoveragePlan == other.clipCoveragePlan &&
        clipExecutionPlan == other.clipExecutionPlan &&
        diagnostics == other.diagnostics &&
        clipProducerAuthority == other.clipProducerAuthority

private fun normalizedTransformFacts(matrix: org.graphiks.math.matrix.Matrix3x3F32) =
    org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts(
        type = when {
            matrix.persp0 != 0f || matrix.persp1 != 0f || matrix.persp2 != 1f -> GPUTransformType.Perspective
            matrix.sx == 1f && matrix.sy == 1f && matrix.kx == 0f && matrix.ky == 0f &&
                matrix.tx == 0f && matrix.ty == 0f -> GPUTransformType.Identity
            matrix.sx == 1f && matrix.sy == 1f && matrix.kx == 0f && matrix.ky == 0f ->
                GPUTransformType.Translate
            matrix.kx == 0f && matrix.ky == 0f -> GPUTransformType.Scale
            else -> GPUTransformType.Affine
        },
        translateX = matrix.tx,
        translateY = matrix.ty,
        scaleX = matrix.sx,
        scaleY = matrix.sy,
        skewX = matrix.kx,
        skewY = matrix.ky,
    )

private fun org.graphiks.kanvas.gpu.renderer.commands.GPUBounds.preparedVerticesBoundsHash(): String =
    "bounds:$left,$top,$right,$bottom"

internal fun GPUPreparedVerticesUploadArtifact.normalizedLayoutIdentity(): String = buildString {
    append("stride=").append(layout.strideBytes)
    append(";attributes=").append(layout.attributes.joinToString(","))
    append(";formats=").append(layout.attributeFormats.joinToString(","))
    append(";offsets=").append(layout.offsets.toSortedMap().entries.joinToString(","))
    append(";locations=").append(layout.shaderLocations.toSortedMap().entries.joinToString(","))
}

internal fun GPUClipCoveragePlan.preparedVerticesSemanticIdentity(): String = when (this) {
    GPUClipCoveragePlan.NoClip -> "none"
    is GPUClipCoveragePlan.Scissor ->
        "scissor:${bounds.left.toRawBits()}:${bounds.top.toRawBits()}:" +
            "${bounds.right.toRawBits()}:${bounds.bottom.toRawBits()}"
    is GPUClipCoveragePlan.AnalyticIntersection ->
        "analytic:${elements.preparedVerticesElementIdentity()}"
    is GPUClipCoveragePlan.Mask ->
        "mask:$contentKey:${width}x$height:$sampleCount:$resolvedBytes:$requiredBytes:" +
            elements.preparedVerticesElementIdentity()
    is GPUClipCoveragePlan.Refused -> "refused:$code"
}

private fun List<org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement>
    .preparedVerticesElementIdentity(): String = joinToString(";") { element ->
        "${element.operation.name}/${element.kind.name}/${element.antiAlias}/" +
            "${element.fillRule.name}/${element.inverseFill}/${element.vertexCount}/" +
            element.values.joinToString(",") { value -> value.toRawBits().toString() }
    }
