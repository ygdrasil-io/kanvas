package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
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
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedDraw
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.surface.RenderConfig

const val GPU_FRAME_PROVENANCE_ANNOTATION_KEY: String = "kanvas.frame.provenance"

enum class GPUFramePathStateKind {
    Transform,
    Clip,
    Annotation,
    FlushSnapshot,
}

enum class GPUFrameClipExecution {
    Analytic,
    Mask,
    Stencil,
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
    val clipExecution: List<GPUFrameClipExecution>,
    val blendPlan: GPUBlendPlan,
    val provenance: GPUFrameProvenance,
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

/** Read-only inventory over the production [GPUOpMapper] → recorder → planner route. */
object GPUFramePathApiInventory {
    fun plan(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities = GPUProductFlagConfig.fromSystemProperties().buildCapabilities(),
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(0),
    ): GPUFramePathInventoryPlan {
        val mapping = GPUOpMapper.mapOperations(operations, target, config)
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
        val gatherer = GPUCorePrimitivePayloadGatherer()
        val semantics = inventory.visualCommands.associate { visual ->
            val semantic = gatherer.gatherSemantic(visual.toCorePrimitiveInput(targetBounds))
            visual.normalized.commandId.value to semantic
        }
        return GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = inventory.recording.taskList,
                capabilities = capabilities,
                target = GPUFrameTargetRef("frame.scene"),
                targetBounds = targetBounds,
                draws = inventory.visualCommands.map { visual ->
                    GPUCorePrimitivePreparedDraw(
                        commandIdValue = visual.normalized.commandId.value,
                        paintOrder = visual.normalized.ordering.paintOrder,
                        blendPlan = visual.blendPlan,
                        frameProvenance = visual.provenance,
                        clipCoveragePlan = visual.clipCoverage,
                    )
                },
                semanticsByCommandId = semantics,
                readbackRequestId = readbackRequestId,
            ),
        )
    }
}

private fun GPUFramePathVisualCommand.toCorePrimitiveInput(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitivePayloadInput {
    val material = normalized.material as? GPUMaterialDescriptor.SolidColor
        ?: error("Slice 12A CorePrimitive currently requires a solid material")
    val alpha = material.a
    val geometry = normalized.toDeviceGeometry()
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
    )
}

private fun NormalizedDrawCommand.toDeviceGeometry(): GPUCorePrimitiveGeometryInput = when (this) {
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
                contourStarts = listOf(0),
            )
        }
    }
    is NormalizedDrawCommand.FillRRect -> {
        require(transform.skewX == 0f && transform.skewY == 0f) {
            "Affine-skew RRects require path lowering before CorePrimitive gathering"
        }
        val first = transform.map(rrect.rect.left, rrect.rect.top)
        val second = transform.map(rrect.rect.right, rrect.rect.bottom)
        val scaleX = abs(transform.scaleX)
        val scaleY = abs(transform.scaleY)
        GPUCorePrimitiveGeometryInput.RRect(
            minOf(first.first, second.first),
            minOf(first.second, second.second),
            maxOf(first.first, second.first),
            maxOf(first.second, second.second),
            listOf(
                rrect.topLeft.x * scaleX, rrect.topLeft.y * scaleY,
                rrect.topRight.x * scaleX, rrect.topRight.y * scaleY,
                rrect.bottomRight.x * scaleX, rrect.bottomRight.y * scaleY,
                rrect.bottomLeft.x * scaleX, rrect.bottomLeft.y * scaleY,
            ),
        )
    }
    is NormalizedDrawCommand.FillPath -> pathDeviceGeometry()
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.FillPath.pathDeviceGeometry(): GPUCorePrimitiveGeometryInput {
    val transformed = tessellatedVertices.chunked(2).map { pair -> transform.map(pair[0], pair[1]) }
    if (stroke) return strokeDeviceGeometry(transformed)
    val indices = mutableListOf<Int>()
    contourStarts.forEachIndexed { contourIndex, start ->
        var end = contourStarts.getOrElse(contourIndex + 1) { transformed.size }
        if (end - start > 2 && transformed[end - 1] == transformed[start]) end--
        for (index in start + 1 until end - 1) {
            indices += start
            indices += index
            indices += index + 1
        }
    }
    require(indices.isNotEmpty()) { "Filled CorePrimitive paths require at least one complete triangle" }
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = transformed.flatMap { listOf(it.first, it.second) },
        indices = indices,
        contourStarts = contourStarts,
    )
}

private fun NormalizedDrawCommand.FillPath.strokeDeviceGeometry(
    transformed: List<Pair<Float, Float>>,
): GPUCorePrimitiveGeometryInput {
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Int>()
    val starts = mutableListOf<Int>()
    val halfWidth = maxOf(strokeWidth * (abs(transform.scaleX) + abs(transform.scaleY)) * 0.25f, 0.5f)
    contourStarts.forEachIndexed { contourIndex, start ->
        val end = contourStarts.getOrElse(contourIndex + 1) { transformed.size }
        for (index in start until end - 1) {
            val (x0, y0) = transformed[index]
            val (x1, y1) = transformed[index + 1]
            val length = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            if (length == 0f) continue
            val nx = -(y1 - y0) / length * halfWidth
            val ny = (x1 - x0) / length * halfWidth
            val base = vertices.size / 2
            starts += base
            vertices += listOf(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny)
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }
    }
    require(indices.isNotEmpty()) { "Stroked CorePrimitive paths require at least one non-degenerate segment" }
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(vertices, indices, starts)
}

private fun GPUTransformFacts.map(x: Float, y: Float): Pair<Float, Float> {
    require(type != GPUTransformType.Perspective && type != GPUTransformType.Singular) {
        "CorePrimitive geometry requires a finite affine transform"
    }
    return Pair(
        scaleX * x + skewX * y + translateX,
        skewY * x + scaleY * y + translateY,
    )
}

private fun String.toCoreSourceFamily(): GPUCorePrimitiveSourceFamily = when {
    startsWith("drawColor") || startsWith("clear") -> GPUCorePrimitiveSourceFamily.Color
    startsWith("drawPoint") || startsWith("drawPoints") -> GPUCorePrimitiveSourceFamily.PointLine
    startsWith("drawRect") -> GPUCorePrimitiveSourceFamily.Rect
    startsWith("drawRRect") -> GPUCorePrimitiveSourceFamily.RRect
    startsWith("drawDRRect") -> GPUCorePrimitiveSourceFamily.DRRect
    startsWith("drawPath") -> GPUCorePrimitiveSourceFamily.Path
    else -> error("Unknown Slice 12A source operation $this")
}
