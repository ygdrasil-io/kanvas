package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

internal fun GPUBackendRenderRecorder.dispatchFillRRect(
    cmd: NormalizedDrawCommand.FillRRect,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, reason)
    }

    cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }
    cmd.nonUniformRadiiRefusalReasonOrNull()?.let { refuse(it); return }

    val material = cmd.material as? GPUMaterialDescriptor.SolidColor ?: run {
        refuse("unsupported_material:${cmd.material.kind.name}")
        return
    }

    val rrect = cmd.rrect
    val rx = rrect.topLeft.x
    val ry = rrect.topLeft.y
    val rect = rrect.rect
    val clipBounds = cmd.clip.bounds
    val sx = maxOf(rect.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(rect.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(rect.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(rect.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
    bb.putFloat(rect.left); bb.putFloat(rect.top)
    bb.putFloat(rect.right); bb.putFloat(rect.bottom)
    bb.putFloat(rx); bb.putFloat(ry)
    bb.putFloat(0f); bb.putFloat(0f)
    bb.putFloat(material.r * material.a)
    bb.putFloat(material.g * material.a)
    bb.putFloat(material.b * material.a)
    bb.putFloat(material.a)

    drawFullscreenRawUniformPass(
        wgsl = RRECT_WGSL,
        colorFormat = config.gpuColorFormat.wgpuLabel,
        draws = listOf(
            GPUBackendRawUniformDraw(
                uniformBytes = bb.array(),
                scissorX = sx, scissorY = sy,
                scissorWidth = sw, scissorHeight = sh,
            ),
        ),
    )
    dispatched.add(cmd.commandId.toString())
    diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
}
