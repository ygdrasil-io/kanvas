package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

internal data class GPUPreparedSurfaceFrameBuildRequest(
    val candidate: GPUPreparedSurfaceEligibility.Candidate,
    val targetFacts: GPUTargetFacts,
    val targetBounds: GPUPixelBounds,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val recordingId: GPURecordingID,
    val frameId: GPUFrameID,
    val readbackRequestId: GPUReadbackRequestID,
)

internal sealed interface GPUPreparedSurfaceFrameBuildResult {
    data class Ready(
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID,
        val visualOperationCount: Int,
        val stateEventCount: Int,
    ) : GPUPreparedSurfaceFrameBuildResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameBuildResult
}

/** Builds one handle-free prepared Surface frame without creating or submitting GPU resources. */
internal object GPUPreparedSurfaceFrameBuilder {
    fun build(
        request: GPUPreparedSurfaceFrameBuildRequest,
    ): GPUPreparedSurfaceFrameBuildResult {
        validateTargetBounds(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateTargetFormat(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateFrameIdentities(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }

        return try {
            val mapping = GPUOpMapper.mapOperations(
                operations = request.candidate.operations,
                target = request.targetFacts,
                config = request.candidate.config,
                capabilities = request.capabilities,
            )
            val recorder = GPURecorder(
                recordingId = request.recordingId,
                frameId = request.frameId,
                capabilities = request.capabilities,
                deviceGeneration = request.deviceGeneration,
            )
            mapping.visualCommands.forEach { visual -> recorder.record(visual.normalized) }
            val recording = recorder.close()
            recording.taskList.diagnostics.firstOrNull { diagnostic -> diagnostic.isTerminal }?.let {
                return GPUPreparedSurfaceFrameBuildResult.Refused(it)
            }
            val semantics = when (val gathered = GPUCorePrimitiveSemanticBuilder.gather(
                visualCommands = mapping.visualCommands,
                recording = recording,
                targetBounds = request.targetBounds,
            )) {
                is GPUCorePrimitiveSemanticGatherResult.Gathered -> gathered.semantics
                is GPUCorePrimitiveSemanticGatherResult.Refused -> {
                    return GPUPreparedSurfaceFrameBuildResult.Refused(gathered.toDiagnostic())
                }
            }
            when (val prepared = GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = recording.taskList,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = semantics,
                    readbackRequestId = request.readbackRequestId,
                ),
            )) {
                is GPUCorePrimitivePreparedFrameResult.Recorded -> {
                    validateEncodedPremulSrgbOutput(request, mapping, semantics)?.let {
                        return GPUPreparedSurfaceFrameBuildResult.Refused(it)
                    }
                    GPUPreparedSurfaceFrameBuildResult.Ready(
                        taskList = prepared.taskList,
                        readbackRequestId = request.readbackRequestId,
                        visualOperationCount = mapping.visualCommands.size,
                        stateEventCount = mapping.stateEvents.count { event ->
                            event.kind == GPUFramePathStateKind.Transform ||
                                event.kind == GPUFramePathStateKind.Clip ||
                                event.kind == GPUFramePathStateKind.Annotation
                        },
                    )
                }
                is GPUCorePrimitivePreparedFrameResult.Refused ->
                    GPUPreparedSurfaceFrameBuildResult.Refused(prepared.diagnostic)
            }
        } catch (failure: Exception) {
            GPUPreparedSurfaceFrameBuildResult.Refused(
                diagnostic(
                    code = "invalid.surface.prepared.frame-build-contract",
                    message = "Prepared Surface frame construction violated an internal contract.",
                    facts = mapOf("failureClass" to failure.javaClass.name),
                ),
            )
        }
    }
}

/**
 * The prepared target is currently a physical UNORM texture carrying the named
 * encoded-premul-sRGB convention. Opaque solids retain the same stored bytes as
 * the legacy sRGB attachment. Translucent solids need the legacy attachment's
 * linear-premul-to-sRGB store conversion, which this lane cannot express yet.
 */
private fun validateEncodedPremulSrgbOutput(
    request: GPUPreparedSurfaceFrameBuildRequest,
    mapping: GPUOpMapping,
    semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
): GPUDiagnostic? {
    if (request.candidate.color.interpretation != GPUColorInterpretation.EncodedPremulSrgb) {
        return null
    }
    mapping.visualCommands.forEach { visual ->
        val commandId = visual.normalized.commandId.value
        val semantic = semantics[commandId] ?: return@forEach
        if (semantic.premultipliedRgba[3] != 1f) {
            return diagnostic(
                code = "unsupported.surface.prepared.encoded-premul-srgb.translucent-solid",
                message = "Prepared Surface requires an explicit sRGB store conversion for translucent solids.",
                facts = mapOf("commandId" to commandId.toString()),
            )
        }
        val fractionalCoverageAuthority = semantic.fractionalCoverageAuthority(visual.clipExecutionPlan)
            ?: return@forEach
        return diagnostic(
            code = "unsupported.surface.prepared.encoded-premul-srgb.fractional-coverage",
            message = "Prepared Surface requires an explicit sRGB store conversion for fractional coverage.",
            facts = mapOf(
                "commandId" to commandId.toString(),
                "authority" to fractionalCoverageAuthority,
            ),
        )
    }
    return null
}

private fun GPUDrawSemanticPayload.CorePrimitive.fractionalCoverageAuthority(
    clipExecutionPlan: GPUClipExecutionPlan,
): String? = when (coverageMode) {
    GPUCorePrimitiveCoverageMode.ScalarAA -> "geometry.ScalarAA"
    GPUCorePrimitiveCoverageMode.StencilAA -> "geometry.StencilAA"
    GPUCorePrimitiveCoverageMode.FullOrScissor,
    GPUCorePrimitiveCoverageMode.Stencil1x,
    -> clipExecutionPlan.fractionalCoverageAuthority()
}

private fun GPUClipExecutionPlan.fractionalCoverageAuthority(): String? = when (this) {
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    is GPUClipExecutionPlan.Refused,
    -> null
    is GPUClipExecutionPlan.AnalyticCoverage -> "clip.AnalyticCoverage.aa".takeIf { antiAlias }
    is GPUClipExecutionPlan.AnalyticIntersection ->
        "clip.AnalyticIntersection.aa".takeIf { elements.any { element -> element.antiAlias } }
    is GPUClipExecutionPlan.StencilCoverage -> "clip.StencilCoverage.msaa".takeIf { sampleCount > 1 }
    is GPUClipExecutionPlan.CoverageMask -> when {
        sampleCount > 1 -> "clip.CoverageMask.msaa"
        producers.any { producer -> producer.antiAlias } -> "clip.CoverageMask.aa"
        consumer.sampling != GPUClipMaskSampling.Nearest -> "clip.CoverageMask.filtered"
        else -> null
    }
}

private fun validateTargetBounds(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? =
    if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
        request.targetBounds.width != request.targetFacts.width ||
        request.targetBounds.height != request.targetFacts.height
    ) {
        diagnostic(
            code = "invalid.surface.prepared.target-bounds",
            message = "Prepared Surface target facts and pixel bounds must share zero origin and size.",
            facts = mapOf(
                "factsSize" to "${request.targetFacts.width}x${request.targetFacts.height}",
                "bounds" to request.targetBounds.toString(),
            ),
        )
    } else {
        null
    }

private fun validateTargetFormat(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? =
    if (request.targetFacts.colorFormat != request.candidate.color.physicalFormat.value) {
        diagnostic(
            code = "invalid.surface.prepared.target-format",
            message = "Prepared Surface target format must match the admitted physical color format.",
            facts = mapOf(
                "targetFormat" to request.targetFacts.colorFormat,
                "candidateFormat" to request.candidate.color.physicalFormat.value,
            ),
        )
    } else {
        null
    }

private fun validateFrameIdentities(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? {
    val identities = listOf(
        request.target.value,
        request.recordingId.value,
        request.readbackRequestId.value,
    )
    return if (identities.distinct().size != identities.size) {
        diagnostic(
            code = "invalid.surface.prepared.frame-identities",
            message = "Prepared Surface target, recording, and readback identities must be unambiguous.",
        )
    } else {
        null
    }
}

private fun GPUCorePrimitiveSemanticGatherResult.Refused.toDiagnostic(): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = facts,
)

private fun diagnostic(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = facts,
)
