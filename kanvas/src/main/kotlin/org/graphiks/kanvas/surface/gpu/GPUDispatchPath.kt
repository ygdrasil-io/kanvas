package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
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
    target: GPUBackendOffscreenTarget,
) {
    fun refuse(reason: String) {
        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, reason)
    }

    cmd.fillGuardRefusalReasonOrNull()?.let { refuse(it); return }

    val blendMode = cmd.blend.blendMode
    val tessVertices = cmd.tessellatedVertices
    val vertexCount = cmd.totalVertexCount
    val minVertices = if (cmd.stroke) 2 else 3
    val minFloats = if (cmd.stroke) 4 else 6
    if (vertexCount < minVertices || tessVertices.size < minFloats) {
        refuse("insufficient_vertices:count=$vertexCount")
        return
    }

    val contourStarts = cmd.contourStarts

    // If stroke, convert to filled geometry
    val aaWidth = if (cmd.stroke) 2.0f else 0f  // 2px AA zone for stroke distance-field shader
    val (strokeVertices, strokeContours) = if (cmd.stroke) {
        val cap = when (cmd.strokeCap) { "round" -> StrokeCap.ROUND; "square" -> StrokeCap.SQUARE; else -> StrokeCap.BUTT }
        val join = when (cmd.strokeJoin) { "round" -> StrokeJoin.ROUND; "bevel" -> StrokeJoin.BEVEL; else -> StrokeJoin.MITER }
        val inflatedWidth = cmd.strokeWidth + 2f * aaWidth  // inflate body to cover AA zone
        val sg = strokeToFillGeometry(tessVertices, contourStarts, inflatedWidth, dashArray = cmd.dashIntervals, dashPhase = cmd.dashPhase, capStyle = cap, joinStyle = join)
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

    val clipBounds = cmd.clip.bounds
    val pathBounds = if (cmd.stroke) computeBounds(strokeVertices) else cmd.bounds
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
            if (cmd.stroke && cmd.tessellatedVertices.size >= 4) {
                // Coverage compute shader AA for strokes
                val p0x = cmd.tessellatedVertices[0]
                val p0y = cmd.tessellatedVertices[1]
                val p1x = cmd.tessellatedVertices[2]
                val p1y = cmd.tessellatedVertices[3]
                val halfW = cmd.strokeWidth / 2f
                val aaW = 2.0f

                // Compute coverage texture bounds (inflated by halfW + aaW for AA zone)
                val ox = halfW + aaW + 4f  // extra padding
                val minX = (minOf(p0x, p1x) - ox).toInt().coerceAtLeast(0)
                val minY = (minOf(p0y, p1y) - ox).toInt().coerceAtLeast(0)
                val maxX = (maxOf(p0x, p1x) + ox).toInt().coerceAtMost(surfaceWidth)
                val maxY = (maxOf(p0y, p1y) + ox).toInt().coerceAtMost(surfaceHeight)
                val covW = maxX - minX
                val covH = maxY - minY
                if (covW <= 0 || covH <= 0) {
                    diagnostics.fatal("refuse:coverage", "coverage", "zero_sized_coverage_texture")
                    return
                }

                // Create coverage texture (R8)
                val covLabel = target.createCoverageTexture(covW, covH)

                // Pack compute uniforms: p0, p1 (in coverage-local coords), halfW, aaW
                val compUniforms = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.nativeOrder())
                compUniforms.putFloat(p0x - minX); compUniforms.putFloat(p0y - minY)
                compUniforms.putFloat(p1x - minX); compUniforms.putFloat(p1y - minY)
                compUniforms.putFloat(halfW)
                compUniforms.putFloat(aaW)

                // Dispatch compute shader → writes coverage to texture
                val wgX = (covW + 7) / 8
                val wgY = (covH + 7) / 8
                target.recordCoverageStroke(COVERAGE_STROKE_WGSL, compUniforms.array(), covLabel, wgX, wgY)

                // Pack color uniforms (premultiplied linear)
                val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                colorBb.putFloat(srgbToLinear(material.r) * material.a)
                colorBb.putFloat(srgbToLinear(material.g) * material.a)
                colorBb.putFloat(srgbToLinear(material.b) * material.a)
                colorBb.putFloat(material.a)

                // Render coverage fill (fullscreen quad sampling coverage texture)
                recordCoverageFill(COVERAGE_FILL_WGSL, colorBb.array(), covLabel)
            } else {
                val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                colorBb.putFloat(srgbToLinear(material.r) * material.a)
                colorBb.putFloat(srgbToLinear(material.g) * material.a)
                colorBb.putFloat(srgbToLinear(material.b) * material.a)
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
                    blendMode = blendMode,
                )
            }
        }
        is GPUMaterialDescriptor.LinearGradient -> {
            val multiStop = material.allStopPositions != null && material.allStopPositions!!.size > 2
            if (multiStop) {
                val n = material.allStopPositions!!.size.coerceAtMost(256)
                val bb = java.nio.ByteBuffer.allocate(8224).order(java.nio.ByteOrder.nativeOrder())
                bb.putFloat(material.startX); bb.putFloat(material.startY)
                bb.putFloat(material.endX); bb.putFloat(material.endY)
                bb.putInt(n)
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
                    blendMode = blendMode,
                )
            }
        }
        else -> {
            refuse("unsupported_material:${material.kind.name}")
            return
        }
    }
    dispatched.add(cmd.commandId.toString())
    diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
}
