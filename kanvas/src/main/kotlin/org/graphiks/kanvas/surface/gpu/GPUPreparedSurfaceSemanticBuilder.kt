package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask

internal sealed interface GPUPreparedSurfaceSemanticGatherResult {
    data class Gathered(
        val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    ) : GPUPreparedSurfaceSemanticGatherResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceSemanticGatherResult
}

/** Gathers the complete ordered semantic map before any frame task can be created. */
internal object GPUPreparedSurfaceSemanticBuilder {
    fun gather(
        visualCommands: List<GPUFramePathVisualCommand>,
        normalizedCommands: List<NormalizedDrawCommand> =
            visualCommands.map(GPUFramePathVisualCommand::normalized),
        recording: GPURecording,
        targetBounds: GPUPixelBounds,
        imageArtifactsByCommandId: Map<Int, GPUPreparedImageUploadArtifact>,
        textSemanticsByCommandId: Map<Int, GPUDrawSemanticPayload> = emptyMap(),
        verticesSemanticsByCommandId: Map<Int, GPUDrawSemanticPayload.Vertices> = emptyMap(),
        blendAuthorityPolicy: GPUCorePrimitiveBlendAuthorityPolicy =
            GPUCorePrimitiveBlendAuthorityPolicy.Required,
    ): GPUPreparedSurfaceSemanticGatherResult {
        val visualIds = visualCommands.map { visual -> visual.normalized.commandId.value }
        val normalizedIds = normalizedCommands.map { command -> command.commandId.value }
        val normalizedVisualIds = normalizedCommands.filterNot {
            it is NormalizedDrawCommand.DrawPreparedVertices
        }.map { command -> command.commandId.value }
        val verticesIds = normalizedCommands.filterIsInstance<NormalizedDrawCommand.DrawPreparedVertices>()
            .map { command -> command.commandId.value }.toSet()
        val imageVisuals = visualCommands.filter { visual ->
            visual.normalized is NormalizedDrawCommand.DrawImageRect
        }
        val imageIds = imageVisuals.map { visual -> visual.normalized.commandId.value }.toSet()
        val textIds = visualCommands.filter { visual -> visual.preparedText != null }
            .map { visual -> visual.normalized.commandId.value }
            .toSet()
        val packetsByCommandId = (
            recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets) +
                recording.semanticOnlyDraws.map { draw -> draw.packet }
            )
            .groupBy(GPUDrawPacket::commandIdValue)
        val analysisByCommandId = recording.analysis.records.groupBy { it.commandIdValue }
        val normalizedIdSet = normalizedIds.toSet()
        if (visualIds.distinct().size != visualIds.size ||
            normalizedIds.distinct().size != normalizedIds.size ||
            visualIds != normalizedVisualIds ||
            visualIds.toSet().intersect(verticesIds).isNotEmpty() ||
            visualIds.toSet() + verticesIds != normalizedIdSet ||
            imageArtifactsByCommandId.keys != imageIds ||
            textSemanticsByCommandId.keys != textIds ||
            verticesSemanticsByCommandId.keys != verticesIds ||
            textSemanticsByCommandId.any { (commandId, semantic) ->
                semantic.payloadRef.commandIdValue != commandId ||
                    semantic !is GPUDrawSemanticPayload.TextA8 &&
                    semantic !is GPUDrawSemanticPayload.ColorGlyph
            } ||
            verticesSemanticsByCommandId.any { (commandId, semantic) ->
                semantic.payloadRef.commandIdValue != commandId
            } ||
            analysisByCommandId.keys != normalizedIdSet ||
            packetsByCommandId.keys != normalizedIdSet ||
            analysisByCommandId.size != normalizedIds.size ||
            packetsByCommandId.size != normalizedIds.size ||
            normalizedIds.any { commandId -> analysisByCommandId[commandId].orEmpty().size != 1 } ||
            normalizedIds.any { commandId -> packetsByCommandId[commandId].orEmpty().size != 1 }
        ) {
            return refused(
                "invalid.surface.prepared.semantic-command-bijection",
                "Prepared-surface visuals, analysis records, packets, image artifacts, and text semantics " +
                    "must be bijective.",
                mapOf(
                    "visualIds" to visualIds.joinToString(","),
                    "normalizedIds" to normalizedIds.joinToString(","),
                    "verticesIds" to verticesIds.sorted().joinToString(","),
                    "imageIds" to imageIds.sorted().joinToString(","),
                    "artifactIds" to imageArtifactsByCommandId.keys.sorted().joinToString(","),
                    "analysisCounts" to normalizedIds.joinToString(",") { commandId ->
                        "$commandId:${recording.analysis.records.count { it.commandIdValue == commandId }}"
                    },
                    "packetCounts" to normalizedIds.joinToString(",") { commandId ->
                        "$commandId:${packetsByCommandId[commandId].orEmpty().size}"
                    },
                ),
            )
        }

        val coreVisuals = visualCommands.filterNot { visual ->
            visual.normalized is NormalizedDrawCommand.DrawImageRect ||
                visual.preparedText != null
        }
        val coreSemantics = when (
            val gathered = GPUCorePrimitiveSemanticBuilder.gather(
                visualCommands = coreVisuals,
                recording = recording,
                targetBounds = targetBounds,
                blendAuthorityPolicy = blendAuthorityPolicy,
                colorTransform = GPUCorePrimitiveColorTransform.SrgbToLinear,
            )
        ) {
            is GPUCorePrimitiveSemanticGatherResult.Gathered ->
                gathered.asPreparedSurfaceSemanticMap()
            is GPUCorePrimitiveSemanticGatherResult.Refused -> return GPUPreparedSurfaceSemanticGatherResult.Refused(
                GPUDiagnostic(
                    code = GPUDiagnosticCode(gathered.code),
                    domain = GPUDiagnosticDomain.Recording,
                    severity = GPUDiagnosticSeverity.Error,
                    message = gathered.message,
                    facts = gathered.facts,
                ),
            )
        }

        val imageGatherer = GPUPreparedImagePayloadGatherer()
        val result = linkedMapOf<Int, GPUDrawSemanticPayload>()
        for (visual in visualCommands) {
            val commandId = visual.normalized.commandId.value
            if (visual.preparedText != null) {
                if (visual.preparedImage != null ||
                    visual.normalized !is NormalizedDrawCommand.DrawTextRun
                ) {
                    return refused(
                        "invalid.surface.prepared.text-lowerer-authority",
                        "Prepared text facts must belong only to normalized text commands.",
                        mapOf("commandId" to commandId.toString()),
                    )
                }
                result[commandId] = textSemanticsByCommandId.getValue(commandId)
                continue
            }
            val command = visual.normalized as? NormalizedDrawCommand.DrawImageRect
            if (command == null) {
                if (visual.preparedImage != null) {
                    return refused(
                        "invalid.surface.prepared.image-lowerer-authority",
                        "Prepared image facts cannot be attached to a core command.",
                        mapOf("commandId" to commandId.toString()),
                    )
                }
                result[commandId] = coreSemantics.getValue(commandId)
                continue
            }
            val packet = packetsByCommandId.getValue(commandId).single()
            if (packet.renderStepId.value != "image.draw.texture_upload" ||
                packet.blendPlan == null ||
                command.pixelsFormat != "RGBA8Unorm"
            ) {
                return refused(
                    "invalid.surface.prepared.image-recording-authority",
                    "Prepared image recording authority must retain its exact render step, blend, and format.",
                    mapOf(
                        "commandId" to commandId.toString(),
                        "renderStep" to packet.renderStepId.value,
                        "blendPresent" to (packet.blendPlan != null).toString(),
                        "pixelsFormat" to command.pixelsFormat,
                    ),
                )
            }
            val material = command.material as? GPUMaterialDescriptor.ImageDraw
                ?: return refused(
                    "invalid.surface.prepared.image-material",
                    "Prepared image semantic requires the normalized image material descriptor.",
                    mapOf("commandId" to commandId.toString()),
                )
            val scissor = visual.clipCoverage.toPreparedScissorBounds(targetBounds)
                ?: return refused(
                    "unsupported.surface.prepared.image-clip",
                    "Prepared image semantics require valid canonical clip bounds.",
                    mapOf("commandId" to commandId.toString()),
                )
            val artifact = imageArtifactsByCommandId.getValue(commandId)
            val preparedImage = visual.preparedImage
                ?: return refused(
                    "invalid.surface.prepared.image-lowerer-authority",
                    "Prepared image command requires exact lowerer facts.",
                    mapOf("commandId" to commandId.toString()),
                )
            val expectedVertices = command.preparedVertices(artifact)
                ?: return refused(
                    "unsupported.surface.prepared.image-transform",
                    "Prepared image semantics require one finite non-singular affine transform.",
                    mapOf("commandId" to commandId.toString()),
                )
            val alpha = material.tintA
            val expectedGeometryClass = if (
                command.transform.type == GPUTransformType.Affine &&
                (command.transform.skewX != 0f || command.transform.skewY != 0f)
            ) {
                GPUPreparedImageGeometryClass.Quad
            } else {
                GPUPreparedImageGeometryClass.Rect
            }
            val expectedSampling = when (command.samplingFilterMode) {
                "nearest" -> GPUPreparedImageSampling.Nearest
                "linear" -> GPUPreparedImageSampling.Linear
                else -> return refused(
                    "invalid.surface.prepared.image-lowerer-authority",
                    "Prepared image command has an unknown sampler authority.",
                    mapOf(
                        "commandId" to commandId.toString(),
                        "sampling" to command.samplingFilterMode,
                    ),
                )
            }
            val expectedTint = listOf(
                material.tintR * alpha,
                material.tintG * alpha,
                material.tintB * alpha,
                alpha,
            )
            val atlasFactsPaired =
                (preparedImage.atlasColorPremultipliedRgba == null) ==
                    (preparedImage.atlasSourceBlend == null)
            if (preparedImage.artifact !== artifact ||
                preparedImage.geometry.geometryClass != expectedGeometryClass ||
                preparedImage.geometry.vertices != expectedVertices ||
                preparedImage.sampling != expectedSampling ||
                preparedImage.tintPremultipliedRgba != expectedTint ||
                !atlasFactsPaired ||
                command.pixelsWidth != artifact.width ||
                command.pixelsHeight != artifact.height ||
                command.pixelsRowBytes != artifact.pixelLayout.normalizedRgba8RowBytes ||
                command.pixelsGeneration != artifact.sourceGeneration ||
                command.pixelsContentHash != artifact.contentHash ||
                command.pixelsProvenance != "prepared-surface-artifact"
            ) {
                return refused(
                    "invalid.surface.prepared.image-lowerer-authority",
                    "Normalized image facts and prepared lowerer facts must remain identical.",
                    mapOf("commandId" to commandId.toString()),
                )
            }
            val semantic = try {
                imageGatherer.gatherSemantic(
                    GPUPreparedImagePayloadInput(
                        payloadRef = GPUDrawPayloadRef(
                            commandIdValue = commandId,
                            renderStepIdentity = "image.draw.texture_upload",
                        ),
                        artifact = artifact,
                        geometry = preparedImage.geometry,
                        sampling = preparedImage.sampling,
                        routeCapability = preparedImage.routeCapability,
                        tintPremultipliedRgba = preparedImage.tintPremultipliedRgba,
                        atlasColorPremultipliedRgba =
                            preparedImage.atlasColorPremultipliedRgba,
                        atlasSourceBlend = preparedImage.atlasSourceBlend,
                        targetBounds = targetBounds,
                        scissorBounds = scissor,
                        blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                        frameProvenance = packet.frameProvenance,
                    ),
                )
            } catch (failure: IllegalArgumentException) {
                return refused(
                    "invalid.surface.prepared.image-semantic",
                    "Prepared image semantic validation failed.",
                    mapOf(
                        "commandId" to commandId.toString(),
                        "reason" to (failure.message ?: "invalid"),
                    ),
                )
            }
            result[commandId] = semantic
        }
        val ordered = linkedMapOf<Int, GPUDrawSemanticPayload>()
        normalizedIds.forEach { commandId ->
            ordered[commandId] = verticesSemanticsByCommandId[commandId]
                ?: result[commandId]
                ?: return refused(
                    "invalid.surface.prepared.semantic-command-bijection",
                    "Prepared-surface semantic IDs must exactly match normalized command IDs.",
                    mapOf("commandId" to commandId.toString()),
                )
        }
        if (ordered.any { (commandId, semantic) -> semantic.payloadRef.commandIdValue != commandId }) {
            return refused(
                "invalid.surface.prepared.semantic-command-bijection",
                "Prepared-surface semantic payload identities must match their command keys.",
                emptyMap(),
            )
        }
        return GPUPreparedSurfaceSemanticGatherResult.Gathered(
            Collections.unmodifiableMap(ordered),
        )
    }
}

private fun NormalizedDrawCommand.DrawImageRect.preparedVertices(
    artifact: GPUPreparedImageUploadArtifact,
): List<GPUPreparedImageVertex>? {
    if (transform.type == GPUTransformType.Perspective ||
        transform.type == GPUTransformType.Singular ||
        listOf(
            transform.translateX,
            transform.translateY,
            transform.scaleX,
            transform.scaleY,
            transform.skewX,
            transform.skewY,
            src.left,
            src.top,
            src.right,
            src.bottom,
            dst.left,
            dst.top,
            dst.right,
            dst.bottom,
        ).any { !it.isFinite() }
    ) {
        return null
    }
    val determinant = transform.scaleX * transform.scaleY -
        transform.skewX * transform.skewY
    if (determinant == 0f) return null
    val local = listOf(
        dst.left to dst.top,
        dst.right to dst.top,
        dst.right to dst.bottom,
        dst.left to dst.bottom,
    )
    val u = listOf(src.left, src.right, src.right, src.left)
        .map { coordinate -> coordinate / artifact.width.toFloat() }
    val v = listOf(src.top, src.top, src.bottom, src.bottom)
        .map { coordinate -> coordinate / artifact.height.toFloat() }
    return local.mapIndexed { index, (x, y) ->
        GPUPreparedImageVertex(
            x = x * transform.scaleX + y * transform.skewX + transform.translateX,
            y = x * transform.skewY + y * transform.scaleY + transform.translateY,
            u = u[index],
            v = v[index],
        )
    }.takeIf { vertices ->
        vertices.all { vertex ->
            vertex.x.isFinite() && vertex.y.isFinite() &&
                vertex.u.isFinite() && vertex.v.isFinite()
        }
    }
}

private fun refused(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
) = GPUPreparedSurfaceSemanticGatherResult.Refused(
    GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Recording,
        severity = GPUDiagnosticSeverity.Error,
        message = message,
        facts = facts,
    ),
)
