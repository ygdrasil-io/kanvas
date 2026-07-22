package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.sqrt
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPURegisteredUniformRectResolvedDraw
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

internal sealed interface PreparedRegisteredUniformRectSceneFrameResult {
    data class Recorded(
        val semantics: List<GPUDrawSemanticPayload.RegisteredUniformRect>,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val cpuReferenceRgba: ByteArray,
        val diagnostics: List<String>,
    ) : PreparedRegisteredUniformRectSceneFrameResult

    data class Refused(val reason: String) : PreparedRegisteredUniformRectSceneFrameResult
}

/** Scene adapter for the generic closed-program uniform rectangle frame route. */
internal class PreparedRegisteredUniformRectSceneFrameRecorder(
    private val recorder: GPURegisteredUniformRectFrameRecorder = GPURegisteredUniformRectFrameRecorder(),
) {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedRegisteredUniformRectSceneFrameResult {
        val acceptsColorMatrix = scene.sceneId.value in COLOR_MATRIX_SCENE_IDS
        val unsupported = scene.commands.filterNot {
            it is SceneCommand.Clear || it is SceneCommand.LinearGradientRect ||
                it is SceneCommand.RadialGradientRect || it is SceneCommand.SweepGradientRect ||
                (it is SceneCommand.RuntimeEffectTile && it.isRegisteredSimpleRt) ||
                (acceptsColorMatrix && it is SceneCommand.FillRect)
        }
        if (unsupported.isNotEmpty()) {
            return PreparedRegisteredUniformRectSceneFrameResult.Refused(
                "prepared registered uniform scene accepts registered uniform rectangle commands",
            )
        }
        val clear = scene.commands.filterIsInstance<SceneCommand.Clear>().singleOrNull()
            ?: return PreparedRegisteredUniformRectSceneFrameResult.Refused(
                "prepared registered uniform scene requires exactly one clear command",
            )
        val registeredDraws = scene.commands.filter { it.isRegisteredUniformRectCommand(acceptsColorMatrix) }
        if (registeredDraws.isEmpty()) {
            return PreparedRegisteredUniformRectSceneFrameResult.Refused(
                "prepared registered uniform scene requires at least one registered draw",
            )
        }
        val targetBounds = GPUPixelBounds(0, 0, scene.dimensions.width, scene.dimensions.height)
        val draws = buildList {
            add(
                GPURegisteredUniformRectResolvedDraw(
                    commandIdValue = 1,
                    bounds = targetBounds,
                    program = GPURegisteredUniformProgram.SolidColor,
                    uniformBytes = UniformPacker.solidColorBytes(clear.color),
                    paintOrder = 0,
                ),
            )
            registeredDraws.sortedBy { it.registeredUniformPaintOrder() }.forEachIndexed { index, command ->
                val rect = command.registeredUniformRect()
                val bounds = rect.toRegisteredIntegralBoundsOrNull()
                    ?: return PreparedRegisteredUniformRectSceneFrameResult.Refused(
                        "prepared registered uniform scene requires integral bounds: ${command.label}",
                    )
                val program = when (command) {
                    is SceneCommand.LinearGradientRect -> GPURegisteredUniformProgram.LinearGradient
                    is SceneCommand.RadialGradientRect -> GPURegisteredUniformProgram.RadialGradient
                    is SceneCommand.SweepGradientRect -> GPURegisteredUniformProgram.SweepGradient
                    is SceneCommand.FillRect -> GPURegisteredUniformProgram.ColorMatrix
                    is SceneCommand.RuntimeEffectTile -> GPURegisteredUniformProgram.SimpleRuntimeEffect
                    else -> error("unreachable registered uniform command")
                }
                val uniformBytes = when (command) {
                    is SceneCommand.LinearGradientRect -> UniformPacker.linearGradientBytes(
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        command.startColor,
                        command.endColor,
                    )
                    is SceneCommand.RadialGradientRect -> UniformPacker.radialGradientBytes(
                        command.centerX,
                        command.centerY,
                        command.radius,
                        command.startColor,
                        command.endColor,
                    )
                    is SceneCommand.SweepGradientRect -> UniformPacker.sweepGradientBytes(
                        command.centerX,
                        command.centerY,
                        command.startAngle,
                        command.endAngle,
                        command.startColor,
                        command.endColor,
                    )
                    is SceneCommand.RuntimeEffectTile ->
                        UniformPacker.simpleRtBytes(requireNotNull(command.uniformColor))
                    is SceneCommand.FillRect ->
                        UniformPacker.colorMatrixBytes(command.color, command.paintOrder)
                }
                add(
                    GPURegisteredUniformRectResolvedDraw(
                        commandIdValue = index + 2,
                        bounds = bounds,
                        program = program,
                        uniformBytes = uniformBytes,
                        paintOrder = command.registeredUniformPaintOrder() + 1,
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
                GPURegisteredUniformRectFrameRecordingRequest(
                    frameId = GPUFrameID(REGISTERED_UNIFORM_SCENE_FRAME_ID_BASE + frameOrdinal),
                    recordingId = GPURecordingID(
                        "recording.scene.${scene.sceneId.value}.$frameOrdinal",
                    ),
                    capabilities = capabilities,
                    deviceGeneration = deviceGeneration,
                    target = GPUFrameTargetRef("target.scene.${scene.sceneId.value}"),
                    targetBounds = targetBounds,
                    draws = draws,
                    readbackRequestId = readbackRequestId,
                ),
            )
        ) {
            is GPURegisteredUniformRectFrameRecordingResult.Refused ->
                PreparedRegisteredUniformRectSceneFrameResult.Refused(
                    "${result.diagnostic.code.value}: ${result.diagnostic.message}",
                )
            is GPURegisteredUniformRectFrameRecordingResult.Recorded ->
                PreparedRegisteredUniformRectSceneFrameResult.Recorded(
                    semantics = result.semantics,
                    taskList = result.taskList,
                    readbackRequestId = readbackRequestId,
                    cpuReferenceRgba = composeIndependentRegisteredUniformReference(
                        scene.dimensions.width,
                        scene.dimensions.height,
                        clear,
                        registeredDraws,
                    ),
                    diagnostics = listOf(
                        "registeredUniform:route=prepared-closed-program-batch",
                        "registeredUniform:programs=" + result.semantics
                            .map { it.program.wireId }
                            .distinct()
                            .joinToString(","),
                        "registeredUniform:packets=${draws.size}",
                        "registeredUniform:frameRoute=one-encoder-one-command-buffer-one-submit",
                        "registeredUniform:wgslSourceInFramePlan=false",
                    ),
                )
        }
    }

    private fun composeIndependentRegisteredUniformReference(
        width: Int,
        height: Int,
        clear: SceneCommand.Clear,
        draws: List<SceneCommand>,
    ): ByteArray {
        val rgba = FloatArray(width * height * 4)
        val clearPremul = clear.color.premultiplied()
        repeat(width * height) { pixel ->
            repeat(4) { channel ->
                rgba[pixel * 4 + channel] = clearPremul[channel].quantizedUnorm8()
            }
        }
        draws.sortedBy { it.registeredUniformPaintOrder() }.forEach { command ->
            val rect = command.registeredUniformRect()
            val bounds = requireNotNull(rect.toRegisteredIntegralBoundsOrNull())
            repeat(bounds.height) { localY ->
                repeat(bounds.width) { localX ->
                    val x = bounds.left + localX + 0.5f
                    val y = bounds.top + localY + 0.5f
                    val straight = when (command) {
                        is SceneCommand.RuntimeEffectTile -> {
                            val color = requireNotNull(command.uniformColor)
                            FloatArray(4) { channel -> color.channel(channel) }
                        }
                        is SceneCommand.FillRect -> command.color.applyIndependentColorMatrix(command.paintOrder)
                        else -> {
                            val t = command.registeredGradientT(x, y).coerceIn(0f, 1f)
                            val startColor = command.registeredGradientStartColor()
                            val endColor = command.registeredGradientEndColor()
                            FloatArray(4) { channel ->
                                startColor.channel(channel) * (1f - t) + endColor.channel(channel) * t
                            }
                        }
                    }
                    val sourceAlpha = straight[3]
                    val source = floatArrayOf(
                        straight[0] * sourceAlpha,
                        straight[1] * sourceAlpha,
                        straight[2] * sourceAlpha,
                        sourceAlpha,
                    )
                    val pixel = ((bounds.top + localY) * width + bounds.left + localX) * 4
                    repeat(4) { channel ->
                        rgba[pixel + channel] = (
                            source[channel] + rgba[pixel + channel] * (1f - sourceAlpha)
                        ).quantizedUnorm8()
                    }
                }
            }
        }
        return ByteArray(rgba.size) { index ->
            (rgba[index].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
    }
}

internal fun GPURendererScene<SceneCommand>.usesPreparedRegisteredUniformRectPilot(): Boolean =
    sceneId.value in setOf(
        "linear-gradient-lanes",
        "radial-swatch",
        "sweep-disk",
        "runtime-effect-uniform",
        "runtime-effect-child",
    ) + COLOR_MATRIX_SCENE_IDS

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect.toRegisteredIntegralBoundsOrNull(): GPUPixelBounds? {
    val values = listOf(left, top, right, bottom)
    if (values.any { !it.isFinite() || it.toInt().toFloat() != it }) return null
    return GPUPixelBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor.channel(index: Int): Float =
    when (index) {
        0 -> r
        1 -> g
        2 -> b
        else -> a
    }

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor.premultiplied(): FloatArray =
    floatArrayOf(r * a, g * a, b * a, a)

private fun Float.quantizedUnorm8(): Float =
    (coerceIn(0f, 1f) * 255f).roundToInt() / 255f

private fun SceneCommand.isRegisteredGradient(): Boolean =
    this is SceneCommand.LinearGradientRect || this is SceneCommand.RadialGradientRect ||
        this is SceneCommand.SweepGradientRect

private fun SceneCommand.isRegisteredUniformRectCommand(acceptsColorMatrix: Boolean): Boolean =
    isRegisteredGradient() || (this is SceneCommand.RuntimeEffectTile && isRegisteredSimpleRt) ||
        (acceptsColorMatrix && this is SceneCommand.FillRect)

private fun SceneCommand.registeredUniformRect() = when (this) {
    is SceneCommand.LinearGradientRect -> rect
    is SceneCommand.RadialGradientRect -> rect
    is SceneCommand.SweepGradientRect -> rect
    is SceneCommand.FillRect -> rect
    is SceneCommand.RuntimeEffectTile -> requireNotNull(rect)
    else -> error("not a registered uniform command")
}

private fun SceneCommand.registeredUniformPaintOrder() = when (this) {
    is SceneCommand.LinearGradientRect -> paintOrder
    is SceneCommand.RadialGradientRect -> paintOrder
    is SceneCommand.SweepGradientRect -> paintOrder
    is SceneCommand.FillRect -> paintOrder
    is SceneCommand.RuntimeEffectTile -> paintOrder
    else -> error("not a registered uniform command")
}

private fun SceneCommand.registeredGradientStartColor() = when (this) {
    is SceneCommand.LinearGradientRect -> startColor
    is SceneCommand.RadialGradientRect -> startColor
    is SceneCommand.SweepGradientRect -> startColor
    else -> error("not a registered gradient command")
}

private fun SceneCommand.registeredGradientEndColor() = when (this) {
    is SceneCommand.LinearGradientRect -> endColor
    is SceneCommand.RadialGradientRect -> endColor
    is SceneCommand.SweepGradientRect -> endColor
    else -> error("not a registered gradient command")
}

private fun SceneCommand.registeredGradientT(x: Float, y: Float): Float = when (this) {
    is SceneCommand.LinearGradientRect -> {
        val directionX = rect.right - rect.left
        val directionY = rect.bottom - rect.top
        val lengthSquared = directionX * directionX + directionY * directionY
        if (lengthSquared < 1.0e-12f) 0f else
            ((x - rect.left) * directionX + (y - rect.top) * directionY) / lengthSquared
    }
    is SceneCommand.RadialGradientRect -> {
        val dx = x - centerX
        val dy = y - centerY
        sqrt(dx * dx + dy * dy) / radius
    }
    is SceneCommand.SweepGradientRect -> {
        val dx = x - centerX
        val dy = y - centerY
        if (dx == 0f && dy == 0f) {
            0f
        } else {
            var u = (atan2(-dy.toDouble(), dx.toDouble()) / (Math.PI * 2.0)).toFloat()
            if (u < 0f) u += 1f
            val sweep = endAngle - startAngle
            if (sweep <= 0f) 0f else (u - startAngle / 360f) * (360f / sweep)
        }
    }
    else -> error("not a registered gradient command")
}

private fun org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor.applyIndependentColorMatrix(
    kind: Int,
): FloatArray {
    val transformed = when (kind) {
        1 -> floatArrayOf(r, g, b, a)
        2 -> {
            val luma = 0.213f * r + 0.715f * g + 0.072f * b
            floatArrayOf(luma, luma, luma, a)
        }
        3 -> floatArrayOf(g, b, r, a)
        else -> error("unsupported independent color matrix fixture kind: $kind")
    }
    return FloatArray(4) { index -> transformed[index].coerceIn(0f, 1f) }
}

private const val REGISTERED_UNIFORM_SCENE_FRAME_ID_BASE = 10_800L
private val COLOR_MATRIX_SCENE_IDS = setOf("color-matrix-filter", "color-matrix-tint")
