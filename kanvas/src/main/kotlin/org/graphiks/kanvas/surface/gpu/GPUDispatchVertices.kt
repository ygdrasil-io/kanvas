package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun GPUBackendRenderRecorder.dispatchTexturedVertices(
    positions: FloatArray,
    uvs: FloatArray,
    uvs2: FloatArray?,
    indices: IntArray,
    paint: Paint,
    textureBytes: ByteArray?,
    textureWidth: Int,
    textureHeight: Int,
    textureSourceId: String?,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
    diagnosticName: String,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:texturedVertices:$diagnosticName", diagnosticName, reason)
    }

    if (positions.size < 6 || positions.size % 2 != 0) {
        refuse("invalid_positions:${positions.size}")
        return
    }
    if (uvs.size != positions.size) {
        refuse("uv_position_mismatch:${uvs.size}vs${positions.size}")
        return
    }
    if (textureBytes == null) {
        refuse("no_texture_bytes")
        return
    }
    val vertexCount = positions.size / 2

    val hasColorFilter = paint.colorFilter is ColorFilter.Matrix
    val hasDualUV = uvs2 != null && uvs2.size == uvs.size

    val blendModeIndex = paint.blendMode.ordinal
    val alpha = paint.color.a

    val vertexData: FloatArray

    if (hasDualUV) {
        val dualUVs = uvs2
        vertexData = FloatArray(vertexCount * 6)
        for (i in 0 until vertexCount) {
            vertexData[i * 6 + 0] = positions[i * 2]
            vertexData[i * 6 + 1] = positions[i * 2 + 1]
            vertexData[i * 6 + 2] = uvs[i * 2]
            vertexData[i * 6 + 3] = uvs[i * 2 + 1]
            vertexData[i * 6 + 4] = dualUVs[i * 2]
            vertexData[i * 6 + 5] = dualUVs[i * 2 + 1]
        }
    } else {
        vertexData = FloatArray(vertexCount * 4)
        for (i in 0 until vertexCount) {
            vertexData[i * 4 + 0] = positions[i * 2]
            vertexData[i * 4 + 1] = positions[i * 2 + 1]
            vertexData[i * 4 + 2] = uvs[i * 2]
            vertexData[i * 4 + 3] = uvs[i * 2 + 1]
        }
    }

    val vertexBuffer = GPUBackendVertexPositionUVData(vertexData, indices)
    val label = createVertexPositionUVBuffer(vertexBuffer)

    val uniformBytes: ByteArray
    if (hasDualUV) {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bb.putFloat(alpha)
        bb.putInt(blendModeIndex)
        uniformBytes = bb.array()
    } else if (hasColorFilter) {
        val matrix = paint.colorFilter.values
        val bb = ByteBuffer.allocate(4 + 64).order(ByteOrder.LITTLE_ENDIAN)
        bb.putFloat(alpha)
        for (f in matrix.take(16)) bb.putFloat(f)
        for (i in matrix.size until 16) bb.putFloat(0f)
        uniformBytes = bb.array()
    } else {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putFloat(alpha)
        uniformBytes = bb.array()
    }

    val uniformDraw = GPUBackendRawUniformDraw(
        uniformBytes = uniformBytes,
        scissorX = 0,
        scissorY = 0,
        scissorWidth = surfaceWidth,
        scissorHeight = surfaceHeight,
    )

    val blend = org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.values()
        .firstOrNull { it.ordinal == paint.blendMode.ordinal }

    if (hasDualUV) {
        drawVertexPositionDualUVIndexed(
            vertexBufferLabel = label,
            indexCount = indices.size,
            uniformDraw = uniformDraw,
            texture1Rgba = textureBytes,
            texture1Width = textureWidth, texture1Height = textureHeight,
            texture2Rgba = textureBytes,
            texture2Width = textureWidth, texture2Height = textureHeight,
            textureFormat = "rgba8unorm",
            blendMode = blend,
        )
    } else {
        drawVertexPositionUVIndexed(
            vertexBufferLabel = label,
            indexCount = indices.size,
            uniformDraw = uniformDraw,
            textureRgba = textureBytes,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            textureFormat = "rgba8unorm",
            blendMode = blend,
        )
    }
}
