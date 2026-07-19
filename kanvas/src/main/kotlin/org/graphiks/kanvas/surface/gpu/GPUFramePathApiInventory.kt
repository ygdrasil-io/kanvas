package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.analysis.matchesCorePrimitiveRectGeometry
import org.graphiks.kanvas.gpu.renderer.analysis.matchesCorePrimitiveRRectGeometry
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeStyle
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.gpu.renderer.geometry.FlattenedPath
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point as GPUPathPoint
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity

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
)

data class GPUFramePathInventoryPlan(
    val visualCommands: List<GPUFramePathVisualCommand>,
    val normalizedCommands: List<NormalizedDrawCommand>,
    val stateEvents: List<GPUFramePathStateEvent>,
    val telemetryInputs: List<GPUFramePathTelemetryInput>,
    val recording: GPURecording,
    val framePlan: GPUFramePlan,
    val legacyDump: GPULegacyImmediatePathDump,
)

internal sealed interface GPUCorePrimitiveSemanticGatherResult {
    data class Gathered(val semantics: Map<Int, GPUDrawSemanticPayload.CorePrimitive>) :
        GPUCorePrimitiveSemanticGatherResult

    data class Refused(
        val code: String,
        val message: String,
        val facts: Map<String, String>,
    ) : GPUCorePrimitiveSemanticGatherResult
}

data class GPUCorePrimitiveGeometryRefusal(
    val code: String,
    val refusalFacts: Map<String, String>,
)

private class GPUCorePrimitiveGeometryRefusalException(
    val refusal: GPUCorePrimitiveGeometryRefusal,
) : RuntimeException(refusal.code)

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
        val mapping = GPUOpMapper.mapOperations(operations, target, config, capabilities)
        val visual = mapping.visualCommands

        val recorder = GPURecorder(
            recordingId = GPURecordingID("kanvas.frame-path"),
            frameId = GPUFrameID(0),
            capabilities = capabilities,
            deviceGeneration = deviceGeneration,
        )
        visual.forEach { recorder.record(it.normalized) }
        val recording = recorder.close()
        val framePlan = GPUFramePlanner.plan(recording.taskList)
        return GPUFramePathInventoryPlan(
            visualCommands = visual.toList(),
            normalizedCommands = visual.map(GPUFramePathVisualCommand::normalized),
            stateEvents = mapping.stateEvents,
            telemetryInputs = visual.map { command ->
                GPUFramePathTelemetryInput(
                    commandId = command.normalized.commandId,
                    paintOrder = command.normalized.ordering.paintOrder,
                    provenance = command.provenance,
                )
            },
            recording = recording,
            framePlan = framePlan,
            legacyDump = mapping.legacyDump,
        )
    }

    /** Adds the canonical native frame envelope after the mapper and recorder have made route decisions. */
    fun prepareNativeTaskList(
        inventory: GPUFramePathInventoryPlan,
        capabilities: GPUCapabilities,
        targetBounds: GPUPixelBounds,
        readbackRequestId: GPUReadbackRequestID? = null,
    ): GPUCorePrimitivePreparedFrameResult {
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
            ),
        )
    }

    /** Gathers the exact handle-free semantics before any native task-envelope materialization. */
    internal fun gatherCorePrimitiveSemantics(
        inventory: GPUFramePathInventoryPlan,
        targetBounds: GPUPixelBounds,
    ): GPUCorePrimitiveSemanticGatherResult {
        val gatherer = GPUCorePrimitivePayloadGatherer()
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload.CorePrimitive>()
        val analysisRecordsByCommandId = inventory.recording.analysis.records
            .groupBy(GPUDrawAnalysisRecord::commandIdValue)
        inventory.visualCommands.forEach { visual ->
            visual.geometryRefusal?.let { refusal ->
                return refusal.toGatherRefusal(visual)
            }
            val commandIdValue = visual.normalized.commandId.value
            val matchingAnalysisRecords = analysisRecordsByCommandId[commandIdValue].orEmpty()
            if (matchingAnalysisRecords.size != 1) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_record_bijection",
                    refusalFacts = mapOf(
                        "matchingRecordCount" to matchingAnalysisRecords.size.toString(),
                    ),
                ).toGatherRefusal(visual)
            }
            val analysisRecord = matchingAnalysisRecords.single()
            val expectedAnalysisFamily = visual.normalized.analysisCommandFamily()
            if (analysisRecord.commandFamily != expectedAnalysisFamily) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_command_family_mismatch",
                    refusalFacts = mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "analysisCommandFamily" to analysisRecord.commandFamily,
                        "normalizedCommandFamily" to expectedAnalysisFamily,
                    ),
                ).toGatherRefusal(visual)
            }
            val expectedAnalysisRecordId = visual.normalized.analysisRecordId()
            if (analysisRecord.recordId != expectedAnalysisRecordId) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_record_id_mismatch",
                    refusalFacts = mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "expectedAnalysisRecordId" to expectedAnalysisRecordId,
                    ),
                ).toGatherRefusal(visual)
            }
            val semantic = try {
                gatherer.gatherSemantic(visual.toCorePrimitiveInput(targetBounds, analysisRecord))
            } catch (failure: GPUCorePrimitiveGeometryRefusalException) {
                return failure.refusal.toGatherRefusal(visual)
            } catch (failure: IllegalArgumentException) {
                val stableCode = failure.message
                    ?.takeIf { message -> message.startsWith("unsupported.core_primitive.") }
                    ?: "unsupported.core_primitive.geometry.invalid"
                return GPUCorePrimitiveGeometryRefusal(
                    code = stableCode,
                    refusalFacts = mapOf("reason" to (failure.message ?: "invalid_geometry")),
                ).toGatherRefusal(visual)
            }
            semantics[visual.normalized.commandId.value] = semantic
        }
        return GPUCorePrimitiveSemanticGatherResult.Gathered(semantics)
    }
}

private fun GPUCorePrimitiveGeometryRefusal.toGatherRefusal(
    visual: GPUFramePathVisualCommand,
): GPUCorePrimitiveSemanticGatherResult.Refused = GPUCorePrimitiveSemanticGatherResult.Refused(
    code = code,
    message = "Core primitive geometry cannot be lowered exactly by the current canonical route.",
    facts = refusalFacts + mapOf(
        "commandId" to visual.normalized.commandId.value.toString(),
        "source" to visual.normalized.source.operation,
    ),
)

private fun GPUFramePathVisualCommand.toCorePrimitiveInput(
    targetBounds: GPUPixelBounds,
    analysisRecord: GPUDrawAnalysisRecord,
): GPUCorePrimitivePayloadInput {
    val material = normalized.material as? GPUMaterialDescriptor.SolidColor
        ?: refuseGeometry(
            "unsupported.core_primitive.material.non_solid",
            mapOf("materialKind" to normalized.material::class.simpleName.orEmpty()),
        )
    val alpha = material.a
    val sourceFamily = normalized.toCoreSourceFamily()
    val rectRouteAuthority: GPUCorePrimitiveRectRouteAuthority?
    val rectGeometryAuthority: GPUCorePrimitiveRectGeometryAuthority?
    val rrectGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority?
    val sealedRRectGeometry: GPUCorePrimitiveGeometryInput.RRect?
    when (sourceFamily) {
        GPUCorePrimitiveSourceFamily.Rect -> {
            val fillRect = normalized as NormalizedDrawCommand.FillRect
            if (analysisRecord.corePrimitiveRRectGeometryAuthority != null) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            rectRouteAuthority = analysisRecord.corePrimitiveRectRouteAuthority
                ?: refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_missing",
                    mapOf("analysisRecordId" to analysisRecord.recordId),
                )
            rectGeometryAuthority = analysisRecord.corePrimitiveRectGeometryAuthority?.also { authority ->
                if (!authority.matchesCorePrimitiveRectGeometry(fillRect.rect, fillRect.transform)) {
                    refuseGeometry(
                        "unsupported.core_primitive.rect.geometry_authority_mismatch",
                        mapOf(
                            "analysisRecordId" to analysisRecord.recordId,
                            "analysisGeometryAuthority" to authority.toString(),
                        ),
                    )
                }
            } ?: refuseGeometry(
                "unsupported.core_primitive.rect.geometry_authority_mismatch",
                mapOf(
                    "analysisRecordId" to analysisRecord.recordId,
                    "analysisGeometryAuthority" to "missing",
                ),
            )
            rrectGeometryAuthority = null
            sealedRRectGeometry = null
        }
        GPUCorePrimitiveSourceFamily.RRect -> {
            val fillRRect = normalized as NormalizedDrawCommand.FillRRect
            if (analysisRecord.corePrimitiveRectRouteAuthority != null ||
                analysisRecord.corePrimitiveRectGeometryAuthority != null
            ) {
                refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            val authority = analysisRecord.corePrimitiveRRectGeometryAuthority
                ?: refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_missing",
                    mapOf("analysisRecordId" to analysisRecord.recordId),
                )
            if (!authority.matchesCorePrimitiveRRectGeometry(fillRRect.rrect, fillRRect.transform)) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.geometry_authority_mismatch",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "analysisGeometryAuthority" to authority.toString(),
                    ),
                )
            }
            rectRouteAuthority = null
            rectGeometryAuthority = null
            rrectGeometryAuthority = authority
            sealedRRectGeometry = authority.sealedDeviceGeometryInput()
        }
        else -> {
            if (analysisRecord.corePrimitiveRectRouteAuthority != null ||
                analysisRecord.corePrimitiveRectGeometryAuthority != null
            ) {
                refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            if (analysisRecord.corePrimitiveRRectGeometryAuthority != null) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            rectRouteAuthority = null
            rectGeometryAuthority = null
            rrectGeometryAuthority = null
            sealedRRectGeometry = null
        }
    }
    val geometry = sealedRRectGeometry ?: normalized.toDeviceGeometry(targetBounds)
    val scissor = when (val clip = clipCoverage) {
        is GPUClipCoveragePlan.Scissor -> GPUPixelBounds(
            floor(clip.bounds.left).toInt().coerceIn(targetBounds.left, targetBounds.right),
            floor(clip.bounds.top).toInt().coerceIn(targetBounds.top, targetBounds.bottom),
            ceil(clip.bounds.right).toInt().coerceIn(targetBounds.left, targetBounds.right),
            ceil(clip.bounds.bottom).toInt().coerceIn(targetBounds.top, targetBounds.bottom),
        )
        else -> targetBounds
    }
    return GPUCorePrimitivePayloadInput(
        commandIdValue = normalized.commandId.value,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = listOf(material.r * alpha, material.g * alpha, material.b * alpha, alpha),
        targetBounds = targetBounds,
        scissorBounds = scissor,
        clipCoveragePlan = clipCoverage,
        blendPlanIdentity = blendPlan.canonicalIdentity(),
        frameProvenance = provenance,
        coverageMode = coverageMode(),
        analysisRecordId = analysisRecord.recordId.takeIf {
            sourceFamily == GPUCorePrimitiveSourceFamily.Rect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.RRect
        },
        analysisCommandFamily = analysisRecord.commandFamily.takeIf {
            sourceFamily == GPUCorePrimitiveSourceFamily.Rect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.RRect
        },
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
    )
}

private fun GPUFramePathVisualCommand.coverageMode(): GPUCorePrimitiveCoverageMode = when (geometryCoverage) {
    GPUCoverageConsumption.FullOrScissor -> GPUCorePrimitiveCoverageMode.FullOrScissor
    GPUCoverageConsumption.ScalarCoverage -> GPUCorePrimitiveCoverageMode.ScalarAA
    GPUCoverageConsumption.StencilCoverage1x -> if (normalized.antiAlias()) {
        GPUCorePrimitiveCoverageMode.StencilAA
    } else {
        GPUCorePrimitiveCoverageMode.Stencil1x
    }
    GPUCoverageConsumption.MultisampleAttachmentCoverage,
    GPUCoverageConsumption.LCDCoverage,
    -> error("unsupported.core_primitive.coverage_mode:${geometryCoverage.name}")
}

private fun NormalizedDrawCommand.antiAlias(): Boolean = when (this) {
    is NormalizedDrawCommand.FillRect -> antiAlias
    is NormalizedDrawCommand.FillRRect -> antiAlias
    is NormalizedDrawCommand.FillPath -> antiAlias
    else -> false
}

private fun NormalizedDrawCommand.toDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput = when (this) {
    is NormalizedDrawCommand.FillRect -> {
        val corners = listOf(
            transform.map(rect.left, rect.top),
            transform.map(rect.right, rect.top),
            transform.map(rect.right, rect.bottom),
            transform.map(rect.left, rect.bottom),
        )
        if (transform.skewX == 0f && transform.skewY == 0f) {
            GPUCorePrimitiveGeometryInput.Rect(
                corners.minOf { it.first },
                corners.minOf { it.second },
                corners.maxOf { it.first },
                corners.maxOf { it.second },
            )
        } else {
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = corners.flatMap { listOf(it.first, it.second) },
                indices = listOf(0, 1, 2, 0, 2, 3),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 4,
                coverBounds = corners.toPixelCoverBounds(targetBounds),
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
            )
        }
    }
    is NormalizedDrawCommand.FillRRect -> error(
        "unsupported.core_primitive.rrect.analysis_authority_missing",
    )
    is NormalizedDrawCommand.FillPath -> pathDeviceGeometry(targetBounds)
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.FillPath.pathDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput {
    if (source.operation == "drawPoint" || source.operation == "drawPoints.points") {
        val refusalCode = when {
            dashIntervals?.isNotEmpty() == true -> "unsupported.core_primitive.point.path_effect_exact_lowering"
            strokeWidth == 0f -> "unsupported.core_primitive.point.hairline_exact_lowering"
            !strokeWidth.isFinite() || strokeWidth < 0f -> "unsupported.core_primitive.point.invalid_width"
            strokeCap == "round" -> "unsupported.core_primitive.point.round_cap_exact_lowering"
            else -> null
        }
        if (refusalCode != null) {
            refuseGeometry(
                refusalCode,
                mapOf(
                    "width" to strokeWidth.toString(),
                    "cap" to strokeCap,
                    "dashIntervals" to dashIntervals?.joinToString(",").orEmpty(),
                ),
            )
        }
    }
    if (stroke) return strokeDeviceGeometry(targetBounds)
    if (tessellatedVertices.isEmpty()) {
        refuseGeometry(
            code = if (pathDescriptor.inverseFill) {
                "unsupported.core_primitive.inverse_empty_path"
            } else {
                "unsupported.core_primitive.empty_path"
            },
            facts = mapOf(
                "fillRule" to pathDescriptor.fillRule,
                "inverseFill" to "true",
            ),
        )
    }
    val transformed = tessellatedVertices.chunked(2).map { pair -> transform.map(pair[0], pair[1]) }
    val edgeFan = PathTessellator().stencilEdgeFan(
        FlattenedPath(
            points = transformed.map { (x, y) -> GPUPathPoint(x, y) },
            contourStarts = contourStarts,
        ),
    )
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = edgeFan.vertices.toList(),
        indices = edgeFan.indices.toList(),
        sourceContourStarts = contourStarts,
        sourceVertexCount = transformed.size,
        coverBounds = if (pathDescriptor.inverseFill) targetBounds else bounds.toPixelCoverBounds(targetBounds),
        geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        fillRule = pathDescriptor.fillRule.toCoreFillRule(),
        inverseFill = pathDescriptor.inverseFill,
    )
}

private fun NormalizedDrawCommand.FillPath.strokeDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput {
    val pointCount = tessellatedVertices.size / 2
    val exactSingleSegment = contourStarts == listOf(0) && pointCount == 2
    val refusalCode = when {
        dashIntervals?.isNotEmpty() == true -> "unsupported.core_primitive.stroke.dash_exact_lowering"
        strokeCap == "round" -> "unsupported.core_primitive.stroke.round_cap_exact_lowering"
        strokeWidth == 0f -> "unsupported.core_primitive.stroke.hairline_exact_lowering"
        !exactSingleSegment -> "unsupported.core_primitive.stroke.complex_exact_lowering"
        else -> null
    }
    if (refusalCode != null) {
        refuseGeometry(
            code = refusalCode,
            facts = mapOf(
                "width" to strokeWidth.toString(),
                "cap" to strokeCap,
                "join" to strokeJoin,
                "miterLimit" to strokeMiterLimit.toString(),
                "dashIntervals" to dashIntervals?.joinToString(",").orEmpty(),
                "dashPhase" to dashPhase.toString(),
                "contourCount" to contourStarts.size.toString(),
                "pointCount" to pointCount.toString(),
            ),
        )
    }
    val cap = when (strokeCap) {
        "round" -> StrokeCap.ROUND
        "square" -> StrokeCap.SQUARE
        else -> StrokeCap.BUTT
    }
    val join = when (strokeJoin) {
        "round" -> StrokeJoin.ROUND
        "bevel" -> StrokeJoin.BEVEL
        else -> StrokeJoin.MITER
    }
    val outline = strokeToFillGeometry(
        contourVertices = tessellatedVertices,
        contourStarts = contourStarts,
        strokeWidth = strokeWidth,
        dashArray = dashIntervals,
        dashPhase = dashPhase,
        capStyle = cap,
        joinStyle = join,
    )
    val transformed = outline.vertices.chunked(2).map { pair -> transform.map(pair[0], pair[1]) }
    val transformedContourStarts = outline.contourStarts
        .filter { it < transformed.size }
        .distinct()
        .ifEmpty { listOf(0) }
    val edgeFan = PathTessellator().stencilEdgeFan(
        FlattenedPath(
            points = transformed.map { (x, y) -> GPUPathPoint(x, y) },
            contourStarts = transformedContourStarts,
        ),
    )
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = edgeFan.vertices.toList(),
        indices = edgeFan.indices.toList(),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 2,
        coverBounds = bounds.toPixelCoverBounds(targetBounds),
        geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        strokeStyle = GPUCorePrimitiveStrokeStyle(
            width = strokeWidth,
            cap = strokeCap,
            join = strokeJoin,
            miterLimit = strokeMiterLimit,
            dashIntervals = dashIntervals?.toList().orEmpty(),
            dashPhase = dashPhase,
            loweringProof = if (strokeCap == "square") {
                GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1
            } else {
                GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1
            },
        ),
    )
}

private fun org.graphiks.kanvas.gpu.renderer.commands.GPUBounds.toPixelCoverBounds(
    target: GPUPixelBounds,
): GPUPixelBounds = GPUPixelBounds(
    floor(left).toInt().coerceIn(target.left, target.right),
    floor(top).toInt().coerceIn(target.top, target.bottom),
    ceil(right).toInt().coerceIn(target.left, target.right),
    ceil(bottom).toInt().coerceIn(target.top, target.bottom),
)

private fun List<Pair<Float, Float>>.toPixelCoverBounds(target: GPUPixelBounds): GPUPixelBounds =
    GPUPixelBounds(
        floor(minOf { it.first }).toInt().coerceIn(target.left, target.right),
        floor(minOf { it.second }).toInt().coerceIn(target.top, target.bottom),
        ceil(maxOf { it.first }).toInt().coerceIn(target.left, target.right),
        ceil(maxOf { it.second }).toInt().coerceIn(target.top, target.bottom),
    )

private fun String.toCoreFillRule(): GPUCorePrimitiveFillRule = when (this) {
    "NonZero", "winding" -> GPUCorePrimitiveFillRule.Winding
    "EvenOdd", "even_odd" -> GPUCorePrimitiveFillRule.EvenOdd
    else -> refuseGeometry("unsupported.core_primitive.fill_rule", mapOf("fillRule" to this))
}

private fun GPUTransformFacts.map(x: Float, y: Float): Pair<Float, Float> {
    if (type == GPUTransformType.Perspective || type == GPUTransformType.Singular) {
        refuseGeometry(
            "unsupported.core_primitive.geometry.non_affine_transform",
            mapOf("transformType" to type.name),
        )
    }
    val mapped = Pair(
        scaleX * x + skewX * y + translateX,
        skewY * x + scaleY * y + translateY,
    )
    if (!mapped.first.isFinite() || !mapped.second.isFinite()) {
        refuseGeometry(
            "unsupported.core_primitive.geometry.non_finite_transform",
            mapOf("transformType" to type.name),
        )
    }
    return mapped
}

private fun refuseGeometry(code: String, facts: Map<String, String>): Nothing =
    throw GPUCorePrimitiveGeometryRefusalException(GPUCorePrimitiveGeometryRefusal(code, facts))

private fun NormalizedDrawCommand.analysisCommandFamily(): String = when (this) {
    is NormalizedDrawCommand.FillRect -> "FillRect"
    is NormalizedDrawCommand.FillRRect -> "FillRRect"
    is NormalizedDrawCommand.FillPath -> "FillPath"
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.analysisRecordId(): String = when (this) {
    is NormalizedDrawCommand.FillRect -> "analysis.fill_rect.${commandId.value}"
    is NormalizedDrawCommand.FillRRect -> "analysis.fill_rrect.${commandId.value}"
    is NormalizedDrawCommand.FillPath -> "analysis.fill_path.${commandId.value}"
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.toCoreSourceFamily(): GPUCorePrimitiveSourceFamily = when (this) {
    is NormalizedDrawCommand.FillRect -> GPUCorePrimitiveSourceFamily.Rect
    is NormalizedDrawCommand.FillRRect -> GPUCorePrimitiveSourceFamily.RRect
    is NormalizedDrawCommand.FillPath -> when {
        source.operation.startsWith("drawPoint") || source.operation.startsWith("drawPoints") ->
            GPUCorePrimitiveSourceFamily.PointLine
        source.operation.startsWith("drawDRRect") -> GPUCorePrimitiveSourceFamily.DRRect
        else -> GPUCorePrimitiveSourceFamily.Path
    }
    else -> error("Non-core command reached Slice 12A semantic gathering")
}
