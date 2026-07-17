package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

/** One closed scene-to-task recording boundary for every product offscreen consumer. */
sealed interface PreparedSceneFrameResult {
    data class Recorded(
        val route: String,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val diagnostics: List<String>,
    ) : PreparedSceneFrameResult

    data class Refused(
        val code: String,
        val message: String,
    ) : PreparedSceneFrameResult
}

/**
 * Selects only typed prepared recorders. It never falls back to an immediate target encoder.
 * Unsupported families remain explicit until their Task 12 semantic payload is implemented.
 */
class PreparedSceneFrameRecorder {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedSceneFrameResult {
        require(frameOrdinal > 0L) { "prepared scene frame ordinal must be positive" }
        return when {
            scene.commands.any { it is SceneCommand.ColorTextRun } -> when (
                val result = PreparedColorGlyphSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    deviceGeneration,
                    frameOrdinal,
                    withReadback,
                )
            ) {
                is PreparedColorGlyphSceneFrameResult.Recorded -> result.recorded("color-glyph")
                is PreparedColorGlyphSceneFrameResult.Refused -> result.refused("color-glyph")
            }
            scene.usesPreparedSolidRectPilot() -> when (
                val result = PreparedSolidRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    deviceGeneration,
                    frameOrdinal,
                    withReadback,
                )
            ) {
                is PreparedSolidRectSceneFrameResult.Recorded -> result.recorded("solid-rect")
                is PreparedSolidRectSceneFrameResult.Refused -> result.refused("solid-rect")
            }
            scene.usesPreparedStrokeRectPilot() -> when (
                val result = PreparedStrokeRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    deviceGeneration,
                    frameOrdinal,
                    withReadback,
                )
            ) {
                is PreparedStrokeRectSceneFrameResult.Recorded -> result.recorded("stroke-rect")
                is PreparedStrokeRectSceneFrameResult.Refused -> result.refused("stroke-rect")
            }
            scene.usesPreparedRegisteredUniformRectPilot() -> when (
                val result = PreparedRegisteredUniformRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    deviceGeneration,
                    frameOrdinal,
                    withReadback,
                )
            ) {
                is PreparedRegisteredUniformRectSceneFrameResult.Recorded ->
                    result.recorded("registered-uniform-rect")
                is PreparedRegisteredUniformRectSceneFrameResult.Refused ->
                    result.refused("registered-uniform-rect")
            }
            scene.usesPreparedSeparableBlurRectPilot() -> when (
                val result = PreparedSeparableBlurRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    deviceGeneration,
                    frameOrdinal,
                    withReadback,
                )
            ) {
                is PreparedSeparableBlurRectSceneFrameResult.Recorded -> result.recorded("separable-blur-rect")
                is PreparedSeparableBlurRectSceneFrameResult.Refused -> result.refused("separable-blur-rect")
            }
            else -> PreparedSceneFrameResult.Refused(
                code = "unsupported.prepared-scene.family",
                message = "Scene ${scene.sceneId.value} has no typed prepared semantic route.",
            )
        }
    }
}

private fun PreparedColorGlyphSceneFrameResult.Recorded.recorded(route: String) =
    PreparedSceneFrameResult.Recorded(route, taskList, readbackRequestId, diagnostics)

private fun PreparedSolidRectSceneFrameResult.Recorded.recorded(route: String) =
    PreparedSceneFrameResult.Recorded(route, taskList, readbackRequestId, diagnostics)

private fun PreparedStrokeRectSceneFrameResult.Recorded.recorded(route: String) =
    PreparedSceneFrameResult.Recorded(route, taskList, readbackRequestId, diagnostics)

private fun PreparedRegisteredUniformRectSceneFrameResult.Recorded.recorded(route: String) =
    PreparedSceneFrameResult.Recorded(route, taskList, readbackRequestId, diagnostics)

private fun PreparedSeparableBlurRectSceneFrameResult.Recorded.recorded(route: String) =
    PreparedSceneFrameResult.Recorded(route, taskList, readbackRequestId, diagnostics)

private fun PreparedColorGlyphSceneFrameResult.Refused.refused(route: String) = refused(route, reason)
private fun PreparedSolidRectSceneFrameResult.Refused.refused(route: String) = refused(route, reason)
private fun PreparedStrokeRectSceneFrameResult.Refused.refused(route: String) = refused(route, reason)
private fun PreparedRegisteredUniformRectSceneFrameResult.Refused.refused(route: String) = refused(route, reason)
private fun PreparedSeparableBlurRectSceneFrameResult.Refused.refused(route: String) = refused(route, reason)

private fun refused(route: String, reason: String) = PreparedSceneFrameResult.Refused(
    code = "unsupported.prepared-scene.$route",
    message = reason,
)
