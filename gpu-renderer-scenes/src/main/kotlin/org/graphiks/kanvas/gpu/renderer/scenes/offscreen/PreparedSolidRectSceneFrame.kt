package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUSolidRectFrameResolvedDraw
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBlendMode
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

internal sealed interface PreparedSolidRectSceneFrameResult {
    data class Recorded(
        val semantics: List<GPUDrawSemanticPayload.SolidRect>,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val diagnostics: List<String>,
    ) : PreparedSolidRectSceneFrameResult

    data class Refused(val reason: String) : PreparedSolidRectSceneFrameResult
}

internal class PreparedSolidRectSceneFrameRecorder(
    private val recorder: GPUSolidRectFrameRecorder = GPUSolidRectFrameRecorder(),
) {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedSolidRectSceneFrameResult {
        val unsupported = scene.commands.filterNot { it is SceneCommand.Clear || it is SceneCommand.FillRect }
        if (unsupported.isNotEmpty()) {
            return PreparedSolidRectSceneFrameResult.Refused(
                "prepared SolidRect scene requires only Clear and FillRect commands",
            )
        }
        val clear = scene.commands.filterIsInstance<SceneCommand.Clear>().singleOrNull()
            ?: return PreparedSolidRectSceneFrameResult.Refused(
                "prepared SolidRect scene requires exactly one clear command",
            )
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()
        if (fills.isEmpty() || fills.any { it.blendMode != SceneBlendMode.SrcOver }) {
            return PreparedSolidRectSceneFrameResult.Refused(
                "prepared SolidRect scene requires SrcOver FillRect commands",
            )
        }
        val targetBounds = GPUPixelBounds(0, 0, scene.dimensions.width, scene.dimensions.height)
        val draws = buildList {
            add(
                GPUSolidRectFrameResolvedDraw(
                    commandIdValue = 1,
                    bounds = targetBounds,
                    rgba = clear.color.toRgba(),
                    paintOrder = 0,
                ),
            )
            fills.sortedBy { it.paintOrder }.forEachIndexed { index, fill ->
                val bounds = fill.rect.toIntegralBoundsOrNull()
                    ?: return PreparedSolidRectSceneFrameResult.Refused(
                        "prepared SolidRect scene requires integral rectangle bounds: ${fill.label}",
                    )
                add(
                    GPUSolidRectFrameResolvedDraw(
                        commandIdValue = index + 2,
                        bounds = bounds,
                        rgba = fill.color.toRgba(),
                        paintOrder = fill.paintOrder,
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
                    frameId = GPUFrameID(SOLID_SCENE_FRAME_ID_BASE + frameOrdinal),
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
            is GPUSolidRectFrameRecordingResult.Refused -> PreparedSolidRectSceneFrameResult.Refused(
                "${result.diagnostic.code.value}: ${result.diagnostic.message}",
            )
            is GPUSolidRectFrameRecordingResult.Recorded -> PreparedSolidRectSceneFrameResult.Recorded(
                semantics = result.semantics,
                taskList = result.taskList,
                readbackRequestId = readbackRequestId,
                diagnostics = listOf(
                    "solidRect:route=prepared-homogeneous-batch",
                    "solidRect:packets=${draws.size} clearAsPacket=true",
                    "solidRect:blend=canonical-fixed-function-src-over",
                    "solidRect:frameRoute=one-encoder-one-command-buffer-one-submit",
                    "solidRect:readback=${if (withReadback) "final-only" else "none"}",
                ),
            )
        }
    }
}

internal fun GPURendererScene<SceneCommand>.usesPreparedSolidRectPilot(): Boolean =
    sceneId.value == "solid-card-stack"

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor.toRgba(): List<Float> =
    listOf(r, g, b, a)

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect.toIntegralBoundsOrNull(): GPUPixelBounds? {
    val values = listOf(left, top, right, bottom)
    if (values.any { !it.isFinite() || it.toInt().toFloat() != it }) return null
    return GPUPixelBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

private const val SOLID_SCENE_FRAME_ID_BASE = 10_700L
