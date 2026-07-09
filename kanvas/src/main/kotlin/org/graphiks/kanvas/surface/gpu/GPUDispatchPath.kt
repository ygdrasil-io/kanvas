package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

internal fun offsetForAA(vertices: List<Float>, pixelStep: Float = 0.5f): List<Float> {
    if (vertices.size < 6) return vertices
    val n = vertices.size / 2
    val dx = FloatArray(n)
    val dy = FloatArray(n)
    for (i in 0 until n - 2) {
        val ax = vertices[i * 2]; val ay = vertices[i * 2 + 1]
        val bx = vertices[(i + 1) * 2]; val by = vertices[(i + 1) * 2 + 1]
        val cx = vertices[(i + 2) * 2]; val cy = vertices[(i + 2) * 2 + 1]
        val ex1 = bx - ax; val ey1 = by - ay
        val ex2 = cx - ax; val ey2 = cy - ay
        var nx = ey1 - ey2
        var ny = ex2 - ex1
        val len = kotlin.math.sqrt(nx * nx + ny * ny)
        if (len < 1e-6f) continue
        nx = nx / len * pixelStep
        ny = ny / len * pixelStep
        dx[i] += nx; dy[i] += ny
        dx[i + 1] += nx; dy[i + 1] += ny
        dx[i + 2] += nx; dy[i + 2] += ny
    }
    val result = vertices.toMutableList()
    for (i in 0 until n) {
        result[i * 2] = vertices[i * 2] + dx[i]
        result[i * 2 + 1] = vertices[i * 2 + 1] + dy[i]
    }
    return result
}

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

    val blendMode = cmd.blend.blendMode
    val tessVertices = cmd.tessellatedVertices
    val vertexCount = cmd.totalVertexCount
    val allowsDegenerateRoundStroke = cmd.stroke &&
        cmd.strokeCap == "round"
    val minVertices = if (cmd.stroke) {
        if (allowsDegenerateRoundStroke) 1 else 2
    } else {
        3
    }
    val minFloats = if (cmd.stroke) {
        if (allowsDegenerateRoundStroke) 2 else 4
    } else {
        6
    }
    if (vertexCount < minVertices || tessVertices.size < minFloats) {
        refuse("insufficient_vertices:count=$vertexCount")
        return
    }

    val contourStarts = cmd.contourStarts

    // If stroke, convert to filled geometry
    val (strokeVertices, strokeContours) = if (cmd.stroke) {
        val cap = when (cmd.strokeCap) { "round" -> StrokeCap.ROUND; "square" -> StrokeCap.SQUARE; else -> StrokeCap.BUTT }
        val join = when (cmd.strokeJoin) { "round" -> StrokeJoin.ROUND; "bevel" -> StrokeJoin.BEVEL; else -> StrokeJoin.MITER }
        val sg = strokeToFillGeometry(tessVertices, contourStarts, cmd.strokeWidth, dashArray = cmd.dashIntervals, dashPhase = cmd.dashPhase, capStyle = cap, joinStyle = join)
        Pair(sg.vertices, sg.contourStarts)
    } else {
        Pair(tessVertices, contourStarts)
    }

    val indices = mutableListOf<Int>()
    for (ci in strokeContours.indices) {
        val start = strokeContours[ci]
        val end = if (ci + 1 < strokeContours.size) strokeContours[ci + 1] else strokeVertices.size / 2
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

    val finalVertices = if (cmd.antiAlias) offsetForAA(strokeVertices) else strokeVertices
    val triangleData = GPUBackendTriangleData(
        vertices = finalVertices.toFloatArray(),
        indices = indices.toIntArray(),
    )

    val pathBounds = if (cmd.stroke) computeBounds(finalVertices) else cmd.bounds
    val clipBounds = if (cmd.stroke && cmd.clip.kind == GPUClipKind.WideOpen) {
        pathBounds
    } else {
        cmd.clip.bounds
    }
    val sx = maxOf(pathBounds.left, clipBounds.left).toInt().coerceIn(0, surfaceWidth - 1)
    val sy = maxOf(pathBounds.top, clipBounds.top).toInt().coerceIn(0, surfaceHeight - 1)
    val sw = (minOf(pathBounds.right, clipBounds.right).toInt() - sx).coerceIn(1, surfaceWidth - sx)
    val sh = (minOf(pathBounds.bottom, clipBounds.bottom).toInt() - sy).coerceIn(1, surfaceHeight - sy)

    val writeWgsl = stencilWriteWgsl(surfaceWidth, surfaceHeight)

    drawFullscreenStencilPass(
        wgsl = writeWgsl,
        colorFormat = config.gpuColorFormat.gpuLabel,
        stencilMode = GPUBackendStencilMode.Write,
        triangleData = triangleData,
        draws = emptyList(),
    )

    when (val material = cmd.material) {
        is GPUMaterialDescriptor.SolidColor -> {
            val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
            colorBb.putFloat(srgbToLinear(material.r) * material.a)
            colorBb.putFloat(srgbToLinear(material.g) * material.a)
            colorBb.putFloat(srgbToLinear(material.b) * material.a)
            colorBb.putFloat(material.a)
            drawFullscreenStencilPass(
                wgsl = SOLID_RECT_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = colorBb.array(),
                        scissorX = sx, scissorY = sy,
                        scissorWidth = sw, scissorHeight = sh,
                    ),
                ),
                blendMode = blendMode,
            )
        }
        is GPUMaterialDescriptor.LinearGradient -> {
            val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
            if (multiStop) {
                val n = material.allStopPositions!!.size.coerceAtMost(256)
                val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.startX); bb.putFloat(material.startY)
                bb.putFloat(material.endX); bb.putFloat(material.endY)
                bb.putInt(n)
                bb.alignUniformArray()
                for (i in 0 until 256) {
                    if (i < n) {
                        val pos = material.allStopPositions!!.getOrElse(i) { i.toFloat() / (n - 1).coerceAtLeast(1) }
                        bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        if (material.allStopColors != null && i * 4 + 3 < material.allStopColors!!.size) {
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 1]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 2]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(material.allStopColors!![i * 4 + 3])
                        } else {
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        }
                    } else {
                        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    }
                }
                drawFullscreenStencilPass(
                    wgsl = LINEAR_GRADIENT_MULTI_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            } else {
                val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.startX); bb.putFloat(material.startY)
                bb.putFloat(material.endX); bb.putFloat(material.endY)
                bb.putFloat(srgbToLinear(material.startR) * material.startA)
                bb.putFloat(srgbToLinear(material.startG) * material.startA)
                bb.putFloat(srgbToLinear(material.startB) * material.startA)
                bb.putFloat(material.startA)
                bb.putFloat(srgbToLinear(material.endR) * material.endA)
                bb.putFloat(srgbToLinear(material.endG) * material.endA)
                bb.putFloat(srgbToLinear(material.endB) * material.endA)
                bb.putFloat(material.endA)
                drawFullscreenStencilPass(
                    wgsl = LINEAR_GRADIENT_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            }
        }
        is GPUMaterialDescriptor.RadialGradient -> {
            val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
            if (multiStop) {
                val n = material.allStopPositions!!.size.coerceAtMost(256)
                val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                bb.putFloat(material.radius)
                bb.putInt(n)
                bb.alignUniformArray()
                for (i in 0 until 256) {
                    if (i >= n) { bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); continue }
                    val pos = material.allStopPositions!!.getOrElse(i) { i.toFloat() / (n - 1).coerceAtLeast(1) }
                    bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    if (material.allStopColors != null && i * 4 + 3 < material.allStopColors!!.size) {
                        bb.putFloat(srgbToLinear(material.allStopColors!![i * 4]) * material.allStopColors!![i * 4 + 3])
                        bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 1]) * material.allStopColors!![i * 4 + 3])
                        bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 2]) * material.allStopColors!![i * 4 + 3])
                        bb.putFloat(material.allStopColors!![i * 4 + 3])
                    } else {
                        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    }
                }
                drawFullscreenStencilPass(
                    wgsl = RADIAL_GRADIENT_MULTI_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            } else {
                val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                bb.putFloat(material.radius)
                bb.putFloat(0f) // padding — vec4f alignment at offset 16
                bb.putFloat(srgbToLinear(material.startR) * material.startA)
                bb.putFloat(srgbToLinear(material.startG) * material.startA)
                bb.putFloat(srgbToLinear(material.startB) * material.startA)
                bb.putFloat(material.startA)
                bb.putFloat(srgbToLinear(material.endR) * material.endA)
                bb.putFloat(srgbToLinear(material.endG) * material.endA)
                bb.putFloat(srgbToLinear(material.endB) * material.endA)
                bb.putFloat(material.endA)
                drawFullscreenStencilPass(
                    wgsl = RADIAL_GRADIENT_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            }
        }
        is GPUMaterialDescriptor.SweepGradient -> {
            val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
            if (multiStop) {
                val n = material.allStopPositions!!.size.coerceAtMost(256)
                val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                bb.putFloat(material.startAngle); bb.putFloat(material.endAngle)
                bb.putInt(n)
                bb.alignUniformArray()
                for (i in 0 until 256) {
                    if (i < n) {
                        val pos = material.allStopPositions!!.getOrElse(i) { i.toFloat() / (n - 1).coerceAtLeast(1) }
                        bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        if (material.allStopColors != null && i * 4 + 3 < material.allStopColors!!.size) {
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 1]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(srgbToLinear(material.allStopColors!![i * 4 + 2]) * material.allStopColors!![i * 4 + 3])
                            bb.putFloat(material.allStopColors!![i * 4 + 3])
                        } else {
                            bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        }
                    } else {
                        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                    }
                }
                drawFullscreenStencilPass(
                    wgsl = SWEEP_GRADIENT_MULTI_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            } else {
                val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.centerX); bb.putFloat(material.centerY)
                bb.putFloat(material.startAngle); bb.putFloat(material.endAngle)
                bb.putFloat(srgbToLinear(material.startR) * material.startA)
                bb.putFloat(srgbToLinear(material.startG) * material.startA)
                bb.putFloat(srgbToLinear(material.startB) * material.startA)
                bb.putFloat(material.startA)
                bb.putFloat(srgbToLinear(material.endR) * material.endA)
                bb.putFloat(srgbToLinear(material.endG) * material.endA)
                bb.putFloat(srgbToLinear(material.endB) * material.endA)
                bb.putFloat(material.endA)
                drawFullscreenStencilPass(
                    wgsl = SWEEP_GRADIENT_WGSL,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = bb.array(),
                            scissorX = sx, scissorY = sy,
                            scissorWidth = sw, scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            }
        }
        is GPUMaterialDescriptor.ConicalGradient -> {
            if (material.snippetSourceHash != null) {
                val shader = org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider.shaderFor(material)!!
                val uniformBytes = org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider.uniformBytesFor(material)!!
                drawFullscreenStencilPass(
                    wgsl = shader.wgslSource,
                    colorFormat = config.gpuColorFormat.gpuLabel,
                    stencilMode = GPUBackendStencilMode.Test,
                    triangleData = null,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = uniformBytes,
                            scissorX = sx,
                            scissorY = sy,
                            scissorWidth = sw,
                            scissorHeight = sh,
                        ),
                    ),
                    blendMode = blendMode,
                )
            } else {
                refuse("unsupported_material:conical_gradient_fallback")
                return
            }
        }
        is GPUMaterialDescriptor.ImageDraw -> {
            if (material.rgbaPixels.isEmpty()) {
                refuse("unsupported_material:image_draw_missing_pixels")
                return
            }
            textureDimensionsRefusalReasonOrNull(material.imageWidth, material.imageHeight)?.let { reason ->
                refuse(reason)
                return
            }
            val iw = material.imageWidth.toFloat().coerceAtLeast(1f)
            val ih = material.imageHeight.toFloat().coerceAtLeast(1f)
            val uvScaleX = (pathBounds.right - pathBounds.left) / iw
            val uvScaleY = (pathBounds.bottom - pathBounds.top) / ih
            val bb = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(pathBounds.left).putFloat(pathBounds.top).putFloat(pathBounds.right).putFloat(pathBounds.bottom)
            bb.putFloat(uvScaleX).putFloat(uvScaleY)
            bb.putFloat(0f).putFloat(0f)
            bb.putFloat(material.tintR)
            bb.putFloat(material.tintG)
            bb.putFloat(material.tintB)
            bb.putFloat(material.tintA)
            drawFullscreenTextureUniformPass(
                wgsl = IMAGE_TEXTURE_WGSL,
                colorFormat = config.gpuColorFormat.gpuLabel,
                textureRgba = material.rgbaPixels,
                textureWidth = material.imageWidth,
                textureHeight = material.imageHeight,
                textureFormat = "rgba8unorm",
                stencilMode = GPUBackendStencilMode.Test,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = bb.array(),
                        scissorX = sx, scissorY = sy,
                        scissorWidth = sw, scissorHeight = sh,
                    ),
                ),
                blendMode = blendMode,
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

private fun java.nio.ByteBuffer.alignUniformArray() {
    while (position() % 16 != 0) {
        putInt(0)
    }
}
