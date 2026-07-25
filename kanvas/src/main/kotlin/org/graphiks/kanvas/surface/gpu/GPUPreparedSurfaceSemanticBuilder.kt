package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact
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
import org.graphiks.kanvas.gpu.renderer.recording.buildPreparedImageGeometry

sealed interface GPUPreparedSurfaceSemanticGatherResult {
    data class Gathered(
        val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    ) : GPUPreparedSurfaceSemanticGatherResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceSemanticGatherResult
}

/** Gathers the complete ordered semantic map before any frame task can be created. */
internal object GPUPreparedSurfaceSemanticBuilder {
    fun gather(
        visualCommands: List<GPUFramePathVisualCommand>,
        recording: GPURecording,
        targetBounds: GPUPixelBounds,
        imageArtifactsByCommandId: Map<Int, GPUPreparedImageUploadArtifact>,
        blendAuthorityPolicy: GPUCorePrimitiveBlendAuthorityPolicy =
            GPUCorePrimitiveBlendAuthorityPolicy.Required,
    ): GPUPreparedSurfaceSemanticGatherResult {
        val visualIds = visualCommands.map { visual -> visual.normalized.commandId.value }
        val imageVisuals = visualCommands.filter { visual ->
            visual.normalized is NormalizedDrawCommand.DrawImageRect
        }
        val imageIds = imageVisuals.map { visual -> visual.normalized.commandId.value }.toSet()
        val packetsByCommandId = recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .groupBy(GPUDrawPacket::commandIdValue)
        if (visualIds.distinct().size != visualIds.size ||
            imageArtifactsByCommandId.keys != imageIds ||
            recording.analysis.records.groupBy { it.commandIdValue }.let { records ->
                visualIds.any { commandId -> records[commandId].orEmpty().size != 1 }
            } ||
            visualIds.any { commandId -> packetsByCommandId[commandId].orEmpty().size != 1 }
        ) {
            return refused(
                "invalid.surface.prepared.semantic-command-bijection",
                "Prepared-surface visuals, analysis records, packets, and image artifacts must be bijective.",
                mapOf(
                    "visualIds" to visualIds.joinToString(","),
                    "imageIds" to imageIds.sorted().joinToString(","),
                    "artifactIds" to imageArtifactsByCommandId.keys.sorted().joinToString(","),
                    "analysisCounts" to visualIds.joinToString(",") { commandId ->
                        "$commandId:${recording.analysis.records.count { it.commandIdValue == commandId }}"
                    },
                    "packetCounts" to visualIds.joinToString(",") { commandId ->
                        "$commandId:${packetsByCommandId[commandId].orEmpty().size}"
                    },
                ),
            )
        }

        val coreVisuals = visualCommands.filterNot { visual ->
            visual.normalized is NormalizedDrawCommand.DrawImageRect
        }
        val coreSemantics = when (
            val gathered = GPUCorePrimitiveSemanticBuilder.gather(
                visualCommands = coreVisuals,
                recording = recording,
                targetBounds = targetBounds,
                blendAuthorityPolicy = blendAuthorityPolicy,
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
            val command = visual.normalized as? NormalizedDrawCommand.DrawImageRect
            if (command == null) {
                result[commandId] = coreSemantics.getValue(commandId)
                continue
            }
            val packet = packetsByCommandId.getValue(commandId).single()
            if (packet.renderStepId.value !in setOf(
                    "image.draw.texture_upload",
                    "image.draw.bitmap_shader",
                ) ||
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
            val scissor = when (val clip = visual.clipCoverage) {
                GPUClipCoveragePlan.NoClip -> targetBounds
                is GPUClipCoveragePlan.Scissor -> GPUPixelBounds(
                    left = floor(clip.bounds.left).toInt().coerceAtLeast(targetBounds.left),
                    top = floor(clip.bounds.top).toInt().coerceAtLeast(targetBounds.top),
                    right = ceil(clip.bounds.right).toInt().coerceAtMost(targetBounds.right),
                    bottom = ceil(clip.bounds.bottom).toInt().coerceAtMost(targetBounds.bottom),
                )
                else -> return refused(
                    "unsupported.surface.prepared.image-clip",
                    "Prepared image semantics currently require no clip or one exact scissor.",
                    mapOf("commandId" to commandId.toString()),
                )
            }
            val artifact = imageArtifactsByCommandId.getValue(commandId)
            val vertices = command.preparedVertices(artifact)
                ?: return refused(
                    "unsupported.surface.prepared.image-transform",
                    "Prepared image semantics require one finite non-singular affine transform.",
                    mapOf("commandId" to commandId.toString()),
                )
            val alpha = material.tintA
            val semantic = try {
                imageGatherer.gatherSemantic(
                    GPUPreparedImagePayloadInput(
                        payloadRef = GPUDrawPayloadRef(
                            commandIdValue = commandId,
                            renderStepIdentity = "image.draw.texture_upload",
                        ),
                        artifact = artifact,
                        geometry = buildPreparedImageGeometry(
                            geometryClass = if (
                                command.transform.type == GPUTransformType.Affine &&
                                (command.transform.skewX != 0f || command.transform.skewY != 0f)
                            ) {
                                GPUPreparedImageGeometryClass.Quad
                            } else {
                                GPUPreparedImageGeometryClass.Rect
                            },
                            vertices = vertices,
                        ),
                        sampling = when (command.samplingFilterMode) {
                            "nearest" -> GPUPreparedImageSampling.Nearest
                            else -> GPUPreparedImageSampling.Linear
                        },
                        tintPremultipliedRgba = listOf(
                            material.tintR * alpha,
                            material.tintG * alpha,
                            material.tintB * alpha,
                            alpha,
                        ),
                        atlasColorPremultipliedRgba = null,
                        atlasSourceBlend = null,
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
        return GPUPreparedSurfaceSemanticGatherResult.Gathered(result)
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
