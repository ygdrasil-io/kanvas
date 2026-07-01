package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

internal fun renderViaGpu(
    buffer: DisplayListBuffer,
    width: Int,
    height: Int,
    format: PixelFormat,
    config: RenderConfig,
): RenderResult {
    val ops = buffer.ops()
    val diagnostics = Diagnostics()
    val dispatched = mutableListOf<String>()
    val targets = GPUTargetFacts(width = width, height = height, colorFormat = config.gpuColorFormat.wgpuLabel)

    val session = GPUBackendRuntimeFactory.createOrNull()
        ?: error("webgpu-context-unavailable")

    session.use { s ->
        val target = s.createOffscreenTarget(
            GPUOffscreenTargetRequest(
                width = width,
                height = height,
                colorFormat = config.gpuColorFormat.wgpuLabel,
            ),
        )
        target.use { t ->
            // Use offscreen textures as the primary scene buffer to support
            // destination-read sampling for advanced blend modes.
            val texFormat = config.gpuColorFormat.wgpuLabel
            val sceneLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
            val srcLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
            val snapLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
            var sceneHasContent = false

            fun blendModeIndex(mode: org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode): Int = when (mode) {
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.MULTIPLY -> 0
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.SCREEN -> 1
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.OVERLAY -> 2
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.DARKEN -> 3
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.LIGHTEN -> 4
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.DIFFERENCE -> 5
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.EXCLUSION -> 6
                else -> 0
            }

            fun renderAdvancedBlend(cmdId: GPUDrawCommandID, op: DisplayOp.DrawRect) {
                val cmd = op.toNormalizedCommand(cmdId, targets)
                // 1. Snapshot scene → snap texture
                t.encodeOffscreenTexture(snapLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawCompositePass(
                        wgsl = COPY_WGSL,
                        colorFormat = texFormat,
                        textureLabel = sceneLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(uniformBytes = ByteArray(16), scissorX = 0, scissorY = 0, scissorWidth = width, scissorHeight = height),
                        ),
                    )
                }
                // 2. Render source shape → src texture
                t.encodeOffscreenTexture(srcLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                }
                // 3. Blend src over snap into scene
                val blendMode = cmd.blend.blendMode
                val blendIdx = if (blendMode != null) blendModeIndex(blendMode) else 0
                val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                bb.putInt(blendIdx)
                t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawBlendPass(
                        wgsl = BLEND_FORMULA_WGSL,
                        colorFormat = texFormat,
                        srcTextureLabel = srcLabel,
                        dstTextureLabel = snapLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(uniformBytes = bb.array(), scissorX = 0, scissorY = 0, scissorWidth = width, scissorHeight = height),
                        ),
                    )
                }
                dispatched.add(cmdId.toString())
                diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
                sceneHasContent = true
            }

            // Phase 1: process ops, render directly to scene offscreen texture
            for (op in ops) {
                val cmdId = GPUDrawCommandID(dispatched.size)
                when (op) {
                    is DisplayOp.DrawRect -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            renderAdvancedBlend(cmdId, op)
                        } else {
                            if (!sceneHasContent) {
                                t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                }
                            } else {
                                t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                }
                            }
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.DrawPath -> {
                        val pathData = op.path.toPathTessellatorData()
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flat = tessellator.flatten(pathData)
                        if (flat.size < 3) {
                            diagnostics.fatal("refuse:${op.hashCode()}", "drawPath", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val tri = tessellator.triangulate(flat)
                        val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
                        val contourStarts = listOf(0)
                        val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawPath:${cmdId.value}", "drawPath", "unsupported_blend:advanced")
                            continue
                        }
                        if (!sceneHasContent) {
                            t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                            }
                        } else {
                            t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                            }
                        }
                        dispatched.add(cmdId.toString())
                        diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawRRect -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (!sceneHasContent) {
                            t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                dispatchFillRRect(cmd, dispatched, diagnostics, width, height, config)
                            }
                        } else {
                            t.encodeOffscreenTexture(sceneLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                                dispatchFillRRect(cmd, dispatched, diagnostics, width, height, config)
                            }
                        }
                        dispatched.add(cmdId.toString())
                        diagnostics.degrade("dispatch:${cmd.diagnosticName}", cmd.diagnosticName, "dispatched")
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawImage -> {
                        diagnostics.fatal("refuse:drawImage:${cmdId.value}", "drawImage", "unsupported_operation")
                    }
                    is DisplayOp.DrawText -> {
                        diagnostics.fatal("refuse:drawText:${cmdId.value}", "drawText", "unsupported_operation")
                    }
                    is DisplayOp.SetTransform,
                    is DisplayOp.SetClip,
                    is DisplayOp.BeginLayer,
                    is DisplayOp.EndLayer -> { /* state ops */ }
                }
            }

            // Phase 2: composite scene → main target for readback
            t.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                drawCompositePass(
                    wgsl = COPY_WGSL,
                    colorFormat = texFormat,
                    textureLabel = sceneLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(uniformBytes = ByteArray(16), scissorX = 0, scissorY = 0, scissorWidth = width, scissorHeight = height),
                    ),
                )
            }

            val rgba = t.readRgba()
            return RenderResult(
                pixels = rgba.toUByteArray(),
                width = width,
                height = height,
                format = format,
                diagnostics = diagnostics,
                stats = RenderStats(
                    opsDispatched = dispatched.size,
                    opsRefused = diagnostics.entries.size - dispatched.size,
                    pipelineCount = 1,
                    drawCallCount = dispatched.size,
                    coverage = if (dispatched.isNotEmpty()) 1f else 0f,
                ),
            )
        }
    }
}

internal fun org.graphiks.kanvas.geometry.Path.toPathTessellatorData(): PathData {
    val verbs = mutableListOf<org.graphiks.kanvas.gpu.renderer.geometry.PathVerb>()
    val points = mutableListOf<Point>()
    val kanvasVerbs = this.verbs()
    val kanvasPoints = this.points()
    var pi = 0
    for (verb in kanvasVerbs) {
        when (verb) {
            PathVerb.MOVE -> {
                val p = kanvasPoints[pi++]
                verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.MoveTo(Point(p.x, p.y)))
            }
            PathVerb.LINE -> {
                val p = kanvasPoints[pi++]
                verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.LineTo(Point(p.x, p.y)))
            }
            PathVerb.QUAD -> {
                val c = kanvasPoints[pi++]; val p = kanvasPoints[pi++]
                verbs.add(
                    org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.QuadTo(
                        Point(c.x, c.y), Point(p.x, p.y),
                    ),
                )
            }
            PathVerb.CUBIC -> {
                val c1 = kanvasPoints[pi++]; val c2 = kanvasPoints[pi++]; val p = kanvasPoints[pi++]
                verbs.add(
                    org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.CubicTo(
                        Point(c1.x, c1.y), Point(c2.x, c2.y), Point(p.x, p.y),
                    ),
                )
            }
            PathVerb.ARC_TO -> { pi += 4 }
            PathVerb.CLOSE -> verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.Close)
        }
    }
    return PathData(verbs = verbs, points = points)
}
