package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun GPUBackendRenderRecorder.dispatchImageRect(
    cmd: NormalizedDrawCommand.DrawImageRect,
    textureCache: Map<String, ByteArray>,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, reason)
    }

    val pixels = textureCache[cmd.imageSourceId]
    if (pixels == null) {
        refuse("texture_not_found:${cmd.imageSourceId}")
        return
    }

    if (cmd.transform.type != GPUTransformType.Identity) {
        refuse("unsupported_transform:${cmd.transform.type.name}")
        return
    }

    val blendMode = cmd.blend.blendMode
    val dst = cmd.dst
    val src = cmd.src
    val clipBounds = cmd.clip.bounds

    val sx = maxOf(dst.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(dst.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(dst.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(dst.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    val iw = cmd.pixelsWidth.toFloat().coerceAtLeast(1f)
    val ih = cmd.pixelsHeight.toFloat().coerceAtLeast(1f)

    val uvScaleX = (src.right - src.left) / iw
    val uvScaleY = (src.bottom - src.top) / ih
    val uvOffsetX = src.left / iw
    val uvOffsetY = src.top / ih

    val bb = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
    bb.putFloat(dst.left).putFloat(dst.top).putFloat(dst.right).putFloat(dst.bottom)
    bb.putFloat(uvScaleX).putFloat(uvScaleY)
    bb.putFloat(uvOffsetX).putFloat(uvOffsetY)
    val material = cmd.material as? org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.ImageDraw
    bb.putFloat(material?.tintR ?: 1f)
    bb.putFloat(material?.tintG ?: 1f)
    bb.putFloat(material?.tintB ?: 1f)
    bb.putFloat(material?.tintA ?: 1f)

    // TODO: read cmd.samplingFilterMode and create an appropriate GPU sampler.
    // Currently hardcoded to Nearest/ClampToEdge in the native GPU runtime.
    drawFullscreenTextureUniformPass(
        wgsl = IMAGE_TEXTURE_WGSL,
        colorFormat = config.gpuColorFormat.gpuLabel,
        textureRgba = pixels,
        textureWidth = cmd.pixelsWidth,
        textureHeight = cmd.pixelsHeight,
        textureFormat = "rgba8unorm",
        draws = listOf(
            GPUBackendRawUniformDraw(
                uniformBytes = bb.array(),
                scissorX = sx,
                scissorY = sy,
                scissorWidth = sw,
                scissorHeight = sh,
            ),
        ),
        blendMode = blendMode,
    )

    dispatched.add(cmd.commandId.toString())
}
