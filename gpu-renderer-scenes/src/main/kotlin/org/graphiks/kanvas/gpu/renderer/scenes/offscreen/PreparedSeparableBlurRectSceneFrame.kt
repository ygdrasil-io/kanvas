package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.math.abs
import kotlin.math.roundToInt
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

internal sealed interface PreparedSeparableBlurRectSceneFrameResult {
    data class Recorded(
        val semantic: GPUDrawSemanticPayload.SeparableBlurRect,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val cpuReferenceRgba: ByteArray,
        val diagnostics: List<String>,
    ) : PreparedSeparableBlurRectSceneFrameResult

    data class Refused(val reason: String) : PreparedSeparableBlurRectSceneFrameResult
}

/** Scene adapter for the first real bounded two-pass Gaussian blur route. */
internal class PreparedSeparableBlurRectSceneFrameRecorder(
    private val recorder: GPUSeparableBlurRectFrameRecorder = GPUSeparableBlurRectFrameRecorder(),
) {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedSeparableBlurRectSceneFrameResult {
        if (!scene.usesPreparedSeparableBlurRectPilot()) {
            return PreparedSeparableBlurRectSceneFrameResult.Refused(
                "prepared separable blur first slice accepts gaussian-blur-photo only",
            )
        }
        val clear = scene.commands.filterIsInstance<SceneCommand.Clear>().singleOrNull()
            ?: return PreparedSeparableBlurRectSceneFrameResult.Refused(
                "prepared separable blur requires exactly one clear command",
            )
        val fill = scene.commands.filterIsInstance<SceneCommand.FillRect>().singleOrNull()
            ?: return PreparedSeparableBlurRectSceneFrameResult.Refused(
                "prepared separable blur requires exactly one FillRect source",
            )
        if (scene.commands.any { it !is SceneCommand.Clear && it !== fill }) {
            return PreparedSeparableBlurRectSceneFrameResult.Refused(
                "prepared separable blur first slice accepts no additional draw commands",
            )
        }
        val targetBounds = GPUPixelBounds(0, 0, scene.dimensions.width, scene.dimensions.height)
        val sourceBounds = fill.rect.toIntegralBoundsOrNull()
            ?: return PreparedSeparableBlurRectSceneFrameResult.Refused(
                "prepared separable blur requires integral source bounds",
            )
        val readbackRequestId = if (withReadback) {
            GPUReadbackRequestID("readback.scene.${scene.sceneId.value}.$frameOrdinal")
        } else {
            null
        }
        return when (
            val result = recorder.record(
                GPUSeparableBlurRectFrameRecordingRequest(
                    frameId = GPUFrameID(SEPARABLE_BLUR_SCENE_FRAME_ID_BASE + frameOrdinal),
                    recordingId = GPURecordingID("recording.scene.${scene.sceneId.value}.$frameOrdinal"),
                    capabilities = capabilities,
                    deviceGeneration = deviceGeneration,
                    target = GPUFrameTargetRef("target.scene.${scene.sceneId.value}"),
                    targetBounds = targetBounds,
                    sourceBounds = sourceBounds,
                    sourcePremultipliedRgba = fill.color.premultiplied(),
                    clearPremultipliedRgba = clear.color.premultiplied(),
                    sigma = GAUSSIAN_BLUR_PHOTO_SIGMA,
                    readbackRequestId = readbackRequestId,
                ),
            )
        ) {
            is GPUSeparableBlurRectFrameRecordingResult.Refused ->
                PreparedSeparableBlurRectSceneFrameResult.Refused(
                    "${result.diagnostic.code.value}: ${result.diagnostic.message}",
                )
            is GPUSeparableBlurRectFrameRecordingResult.Recorded ->
                PreparedSeparableBlurRectSceneFrameResult.Recorded(
                    semantic = result.semantic,
                    taskList = result.taskList,
                    readbackRequestId = readbackRequestId,
                    cpuReferenceRgba = independentSeparableBlurReference(result.semantic),
                    diagnostics = listOf(
                        "separableBlur:route=prepared-source-horizontal-vertical",
                        "separableBlur:sigma=${result.semantic.effectiveSigma}",
                        "separableBlur:taps=${result.semantic.tapCount}",
                        "separableBlur:intermediateTextures=2",
                        "separableBlur:intermediateBytes=${scene.dimensions.width.toLong() * scene.dimensions.height * 8L}",
                        "separableBlur:frameRoute=one-encoder-one-command-buffer-one-submit",
                        "separableBlur:wgslSourceInFramePlan=false",
                        "separableBlur:reference=independent-cpu-two-pass-unorm8",
                    ),
                )
        }
    }
}

internal fun GPURendererScene<SceneCommand>.usesPreparedSeparableBlurRectPilot(): Boolean =
    sceneId.value == "gaussian-blur-photo"

private fun independentSeparableBlurReference(
    semantic: GPUDrawSemanticPayload.SeparableBlurRect,
): ByteArray {
    val width = semantic.targetBounds.width
    val height = semantic.targetBounds.height
    val source = FloatArray(width * height * 4)
    for (y in semantic.sourceBounds.top until semantic.sourceBounds.bottom) {
        for (x in semantic.sourceBounds.left until semantic.sourceBounds.right) {
            val offset = (y * width + x) * 4
            repeat(4) { channel ->
                source[offset + channel] = semantic.sourcePremultipliedRgba[channel].quantizedUnorm8()
            }
        }
    }
    val horizontal = FloatArray(source.size)
    val half = semantic.tapCount / 2
    repeat(height) { y ->
        repeat(width) { x ->
            val output = (y * width + x) * 4
            repeat(semantic.tapCount) { tap ->
                val sampleX = x + tap - half
                if (sampleX in 0 until width) {
                    val input = (y * width + sampleX) * 4
                    val weight = semantic.weights[tap]
                    repeat(4) { channel -> horizontal[output + channel] += source[input + channel] * weight }
                }
            }
            repeat(4) { channel -> horizontal[output + channel] = horizontal[output + channel].quantizedUnorm8() }
        }
    }
    val clear = FloatArray(4) { channel -> semantic.clearPremultipliedRgba[channel].quantizedUnorm8() }
    val result = ByteArray(source.size)
    repeat(height) { y ->
        repeat(width) { x ->
            val output = (y * width + x) * 4
            val blurred = FloatArray(4)
            repeat(semantic.tapCount) { tap ->
                val sampleY = y + tap - half
                if (sampleY in 0 until height) {
                    val input = (sampleY * width + x) * 4
                    val weight = semantic.weights[tap]
                    repeat(4) { channel -> blurred[channel] += horizontal[input + channel] * weight }
                }
            }
            val inverseAlpha = 1f - blurred[3]
            repeat(4) { channel ->
                val composited = blurred[channel] + clear[channel] * inverseAlpha
                result[output + channel] = (composited.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            }
        }
    }
    return result
}

private fun SceneRect.toIntegralBoundsOrNull(): GPUPixelBounds? {
    val values = listOf(left, top, right, bottom)
    if (values.any { !it.isFinite() || abs(it - it.roundToInt()) > 0f }) return null
    return GPUPixelBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

private fun SceneColor.premultiplied(): FloatArray = floatArrayOf(r * a, g * a, b * a, a)

private fun Float.quantizedUnorm8(): Float = (coerceIn(0f, 1f) * 255f).roundToInt() / 255f

private const val GAUSSIAN_BLUR_PHOTO_SIGMA = 6f
private const val SEPARABLE_BLUR_SCENE_FRAME_ID_BASE = 10_900L
