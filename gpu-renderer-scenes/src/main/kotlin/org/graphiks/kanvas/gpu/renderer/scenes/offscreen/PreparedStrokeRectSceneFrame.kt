package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.math.roundToInt
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLowerer
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLoweringRequest
import org.graphiks.kanvas.gpu.renderer.geometry.GPUAxisAlignedStrokeRectLoweringResult
import org.graphiks.kanvas.gpu.renderer.geometry.GPUGeometryRoute
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

internal sealed interface PreparedStrokeRectSceneFrameResult {
    data class Recorded(
        val semantics: List<GPUDrawSemanticPayload.SolidRect>,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val cpuReferenceRgba: ByteArray,
        val diagnostics: List<String>,
    ) : PreparedStrokeRectSceneFrameResult

    data class Refused(val reason: String) : PreparedStrokeRectSceneFrameResult
}

/** Scene adapter for one exact axis-aligned stroke lowered by Geometry/Coverage. */
internal class PreparedStrokeRectSceneFrameRecorder(
    private val lowerer: GPUAxisAlignedStrokeRectLowerer = GPUAxisAlignedStrokeRectLowerer(),
    private val recorder: GPUSolidRectFrameRecorder = GPUSolidRectFrameRecorder(),
) {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedStrokeRectSceneFrameResult {
        if (!scene.usesPreparedStrokeRectPilot()) {
            return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect first slice accepts stroke-rect-outline only",
            )
        }
        val clear = scene.commands.filterIsInstance<SceneCommand.Clear>().singleOrNull()
            ?: return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect requires exactly one clear command",
            )
        val stroke = scene.commands.filterIsInstance<SceneCommand.Stroke>().singleOrNull()
            ?: return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect requires exactly one Stroke command",
            )
        if (scene.commands.any { it !== clear && it !== stroke }) {
            return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect accepts no additional draw commands",
            )
        }
        if (stroke.pathKind != "bounded-rect-path") {
            return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect requires bounded-rect-path geometry",
            )
        }
        val targetBounds = GPUPixelBounds(0, 0, scene.dimensions.width, scene.dimensions.height)
        val pathBounds = stroke.rect.toStrokeIntegralBoundsOrNull()
            ?: return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect requires integral path bounds",
            )
        val lowering = when (
            val result = lowerer.lower(
                GPUAxisAlignedStrokeRectLoweringRequest(
                    targetBounds = targetBounds,
                    pathBounds = pathBounds,
                    strokeWidth = stroke.strokeWidth,
                    pathKey = "path:scene:${scene.sceneId.value}:${stroke.label}:v1",
                    provenance = "gpu-renderer-scenes",
                ),
            )
        ) {
            is GPUAxisAlignedStrokeRectLoweringResult.Refused ->
                return PreparedStrokeRectSceneFrameResult.Refused(
                    "${result.diagnostic.code}: ${result.diagnostic.message}",
                )
            is GPUAxisAlignedStrokeRectLoweringResult.Lowered -> result
        }
        val route = lowering.geometryPlan.route as? GPUGeometryRoute.Analytic
            ?: return PreparedStrokeRectSceneFrameResult.Refused(
                "prepared stroke-rect lowering did not produce analytic coverage",
            )
        val draws = buildList {
            add(
                GPUSolidRectFrameResolvedDraw(
                    commandIdValue = 1,
                    bounds = targetBounds,
                    rgba = clear.color.rgba(),
                    paintOrder = 0,
                ),
            )
            lowering.coverageBands.forEachIndexed { index, band ->
                add(
                    GPUSolidRectFrameResolvedDraw(
                        commandIdValue = index + 2,
                        bounds = band,
                        rgba = stroke.strokeColor.rgba(),
                        paintOrder = stroke.paintOrder,
                    ),
                )
            }
        }
        val readbackRequestId = if (withReadback) {
            GPUReadbackRequestID("readback.scene.${scene.sceneId.value}.$frameOrdinal")
        } else {
            null
        }
        return when (
            val result = recorder.record(
                GPUSolidRectFrameRecordingRequest(
                    frameId = GPUFrameID(STROKE_RECT_SCENE_FRAME_ID_BASE + frameOrdinal),
                    recordingId = GPURecordingID("recording.scene.${scene.sceneId.value}.$frameOrdinal"),
                    capabilities = capabilities,
                    deviceGeneration = deviceGeneration,
                    target = GPUFrameTargetRef("target.scene.${scene.sceneId.value}"),
                    targetBounds = targetBounds,
                    draws = draws,
                    readbackRequestId = readbackRequestId,
                ),
            )
        ) {
            is GPUSolidRectFrameRecordingResult.Refused -> PreparedStrokeRectSceneFrameResult.Refused(
                "${result.diagnostic.code.value}: ${result.diagnostic.message}",
            )
            is GPUSolidRectFrameRecordingResult.Recorded -> PreparedStrokeRectSceneFrameResult.Recorded(
                semantics = result.semantics,
                taskList = result.taskList,
                readbackRequestId = readbackRequestId,
                cpuReferenceRgba = independentStrokeRectReference(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    clear.color,
                    stroke.strokeColor,
                    lowering.coverageBands,
                ),
                diagnostics = listOf(
                    "strokeRect:geometryRoute=${route.renderStepLabel}",
                    "strokeRect:geometryOwner=gpu-renderer.geometry",
                    "strokeRect:coverageBands=${lowering.coverageBands.size}",
                    "strokeRect:strokeWidth=${stroke.strokeWidth}",
                    "strokeRect:join=Miter",
                    "strokeRect:outerBounds=${lowering.outerBounds.stableLabel()}",
                    "strokeRect:innerBounds=${lowering.innerBounds.stableLabel()}",
                    "strokeRect:frameRoute=one-encoder-one-command-buffer-one-submit",
                    "strokeRect:legacyStrokeWgsl=false",
                    "strokeRect:reference=independent-cpu-analytic-bands",
                ),
            )
        }
    }
}

internal fun GPURendererScene<SceneCommand>.usesPreparedStrokeRectPilot(): Boolean =
    sceneId.value == "stroke-rect-outline"

private fun independentStrokeRectReference(
    width: Int,
    height: Int,
    clear: SceneColor,
    stroke: SceneColor,
    bands: List<GPUPixelBounds>,
): ByteArray {
    val pixels = FloatArray(width * height * 4)
    fun draw(bounds: GPUPixelBounds, color: SceneColor) {
        val source = color.premultiplied()
        repeat(bounds.height) { localY ->
            repeat(bounds.width) { localX ->
                val offset = ((bounds.top + localY) * width + bounds.left + localX) * 4
                repeat(4) { channel ->
                    pixels[offset + channel] = (
                        source[channel] + pixels[offset + channel] * (1f - source[3])
                    ).quantizedUnorm8()
                }
            }
        }
    }
    draw(GPUPixelBounds(0, 0, width, height), clear)
    bands.forEach { draw(it, stroke) }
    return ByteArray(pixels.size) { index ->
        (pixels[index].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }
}

private fun SceneRect.toStrokeIntegralBoundsOrNull(): GPUPixelBounds? {
    val values = listOf(left, top, right, bottom)
    if (values.any { !it.isFinite() || it.toInt().toFloat() != it }) return null
    return GPUPixelBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

private fun SceneColor.rgba(): List<Float> = listOf(r, g, b, a)

private fun SceneColor.premultiplied(): FloatArray = floatArrayOf(r * a, g * a, b * a, a)

private fun Float.quantizedUnorm8(): Float = (coerceIn(0f, 1f) * 255f).roundToInt() / 255f

private fun GPUPixelBounds.stableLabel(): String = "$left,$top,$right,$bottom"

private const val STROKE_RECT_SCENE_FRAME_ID_BASE = 11_000L
