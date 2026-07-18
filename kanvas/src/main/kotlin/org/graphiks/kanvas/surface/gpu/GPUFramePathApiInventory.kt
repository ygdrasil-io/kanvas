package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeStyle
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
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
        inventory.visualCommands.forEach { visual ->
            visual.geometryRefusal?.let { refusal ->
                return refusal.toGatherRefusal(visual)
            }
            val semantic = try {
                gatherer.gatherSemantic(visual.toCorePrimitiveInput(targetBounds))
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
): GPUCorePrimitivePayloadInput {
    val material = normalized.material as? GPUMaterialDescriptor.SolidColor
        ?: refuseGeometry(
            "unsupported.core_primitive.material.non_solid",
            mapOf("materialKind" to normalized.material::class.simpleName.orEmpty()),
        )
    val alpha = material.a
    val geometry = normalized.toDeviceGeometry(targetBounds)
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
        sourceFamily = normalized.source.operation.toCoreSourceFamily(),
        geometry = geometry,
        premultipliedRgba = listOf(material.r * alpha, material.g * alpha, material.b * alpha, alpha),
        targetBounds = targetBounds,
        scissorBounds = scissor,
        clipCoveragePlan = clipCoverage,
        blendPlanIdentity = blendPlan.canonicalIdentity(),
        frameProvenance = provenance,
        coverageMode = coverageMode(),
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
                coverBounds = bounds.toPixelCoverBounds(targetBounds),
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
            )
        }
    }
    is NormalizedDrawCommand.FillRRect -> {
        if (transform.skewX != 0f || transform.skewY != 0f) {
            refuseGeometry(
                "unsupported.core_primitive.rrect.non_axis_aligned_transform",
                mapOf("transformType" to transform.type.name),
            )
        }
        val first = transform.map(rrect.rect.left, rrect.rect.top)
        val second = transform.map(rrect.rect.right, rrect.rect.bottom)
        val scaleX = abs(transform.scaleX)
        val scaleY = abs(transform.scaleY)
        fun deviceCorner(deviceLeft: Boolean, deviceTop: Boolean) = when {
            (if (transform.scaleX < 0f) !deviceLeft else deviceLeft) &&
                (if (transform.scaleY < 0f) !deviceTop else deviceTop) -> rrect.topLeft
            !(if (transform.scaleX < 0f) !deviceLeft else deviceLeft) &&
                (if (transform.scaleY < 0f) !deviceTop else deviceTop) -> rrect.topRight
            !(if (transform.scaleX < 0f) !deviceLeft else deviceLeft) -> rrect.bottomRight
            else -> rrect.bottomLeft
        }
        val tl = deviceCorner(deviceLeft = true, deviceTop = true)
        val tr = deviceCorner(deviceLeft = false, deviceTop = true)
        val br = deviceCorner(deviceLeft = false, deviceTop = false)
        val bl = deviceCorner(deviceLeft = true, deviceTop = false)
        GPUCorePrimitiveGeometryInput.RRect(
            minOf(first.first, second.first),
            minOf(first.second, second.second),
            maxOf(first.first, second.first),
            maxOf(first.second, second.second),
            listOf(
                tl.x * scaleX, tl.y * scaleY,
                tr.x * scaleX, tr.y * scaleY,
                br.x * scaleX, br.y * scaleY,
                bl.x * scaleX, bl.y * scaleY,
            ),
        )
    }
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

private fun String.toCoreSourceFamily(): GPUCorePrimitiveSourceFamily = when {
    startsWith("drawColor") || startsWith("clear") -> GPUCorePrimitiveSourceFamily.Color
    startsWith("drawPoint") || startsWith("drawPoints") -> GPUCorePrimitiveSourceFamily.PointLine
    startsWith("drawRect") -> GPUCorePrimitiveSourceFamily.Rect
    startsWith("drawRRect") -> GPUCorePrimitiveSourceFamily.RRect
    startsWith("drawDRRect") -> GPUCorePrimitiveSourceFamily.DRRect
    startsWith("drawPath") -> GPUCorePrimitiveSourceFamily.Path
    else -> error("Unknown Slice 12A source operation $this")
}
