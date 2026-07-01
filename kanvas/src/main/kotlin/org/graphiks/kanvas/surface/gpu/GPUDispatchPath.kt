package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

internal fun GPUBackendRenderRecorder.dispatchFillPath(
    cmd: NormalizedDrawCommand.FillPath,
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

    val tessVertices = cmd.tessellatedVertices
    val vertexCount = cmd.totalVertexCount
    if (vertexCount < 3 || tessVertices.size < 6) {
        refuse("insufficient_vertices:count=$vertexCount")
        return
    }

    val contourStarts = cmd.contourStarts
    val indices = mutableListOf<Int>()
    for (ci in contourStarts.indices) {
        val start = contourStarts[ci]
        val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else vertexCount
        val cvCount = end - start
        if (cvCount < 3) continue
        for (i in 1 until cvCount - 1) {
            indices.add(start)
            indices.add(start + i)
            indices.add(start + i + 1)
        }
    }

    if (indices.size < 3) {
        refuse("no_triangles_generated")
        return
    }

    val triangleData = GPUBackendTriangleData(
        vertices = tessVertices.toFloatArray(),
        indices = indices.toIntArray(),
    )

    val clipBounds = cmd.clip.bounds
    val pathBounds = cmd.bounds
    val sx = maxOf(pathBounds.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(pathBounds.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(pathBounds.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(pathBounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    val writeWgsl = stencilWriteWgsl(surfaceWidth, surfaceHeight)

    drawFullscreenStencilPass(
        wgsl = writeWgsl,
        colorFormat = config.gpuColorFormat.wgpuLabel,
        stencilMode = GPUBackendStencilMode.Write,
        triangleData = triangleData,
        draws = emptyList(),
    )

    when (val material = cmd.material) {
        is GPUMaterialDescriptor.SolidColor -> {
            val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
            colorBb.putFloat(material.r * material.a)
            colorBb.putFloat(material.g * material.a)
            colorBb.putFloat(material.b * material.a)
            colorBb.putFloat(material.a)
            drawFullscreenStencilPass(
                wgsl = SOLID_RECT_WGSL,
                colorFormat = config.gpuColorFormat.wgpuLabel,
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = colorBb.array(),
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
            drawFullscreenStencilPass(
                wgsl = LINEAR_GRADIENT_WGSL,
                colorFormat = config.gpuColorFormat.wgpuLabel,
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
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
