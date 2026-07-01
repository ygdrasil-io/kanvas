package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.surface.Diagnostics

private const val GPU_COLOR_FORMAT: String = "rgba8unorm"

internal fun GPUBackendRenderRecorder.dispatchFillRect(
    cmd: NormalizedDrawCommand.FillRect,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, reason)
    }

    cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }

    val rect = cmd.rect
    val clipBounds = cmd.clip.bounds
    val sx = maxOf(rect.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(rect.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(rect.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(rect.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    when (val material = cmd.material) {
        is GPUMaterialDescriptor.SolidColor -> {
            val rgba = floatArrayOf(
                material.r * material.a,
                material.g * material.a,
                material.b * material.a,
                material.a,
            )
            drawFullscreenPass(
                wgsl = SOLID_RECT_WGSL,
                colorFormat = GPU_COLOR_FORMAT,
                draws = listOf(
                    GPUBackendRectDraw(
                        rgbaPremul = rgba,
                        scissorX = sx, scissorY = sy,
                        scissorWidth = sw, scissorHeight = sh,
                    ),
                ),
            )
        }
        is GPUMaterialDescriptor.LinearGradient -> {
            val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
            bb.putFloat(material.startX); bb.putFloat(material.startY)
            bb.putFloat(material.endX); bb.putFloat(material.endY)
            bb.putFloat(material.startR * material.startA)
            bb.putFloat(material.startG * material.startA)
            bb.putFloat(material.startB * material.startA)
            bb.putFloat(material.startA)
            bb.putFloat(material.endR * material.endA)
            bb.putFloat(material.endG * material.endA)
            bb.putFloat(material.endB * material.endA)
            bb.putFloat(material.endA)
            drawFullscreenRawUniformPass(
                wgsl = LINEAR_GRADIENT_WGSL,
                colorFormat = GPU_COLOR_FORMAT,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = bb.array(),
                        scissorX = sx, scissorY = sy,
                        scissorWidth = sw, scissorHeight = sh,
                    ),
                ),
            )
        }
        else -> {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
    }
    dispatched.add(cmd.commandId.toString())
    diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
}
