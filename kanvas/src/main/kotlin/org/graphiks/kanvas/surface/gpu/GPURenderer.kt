package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

private const val GPU_COLOR_FORMAT: String = "rgba8unorm"

internal fun renderViaGpu(
    buffer: DisplayListBuffer,
    width: Int,
    height: Int,
    format: PixelFormat,
): RenderResult {
    val ops = buffer.ops()
    val diagnostics = Diagnostics()
    val dispatched = mutableListOf<String>()
    val targets = GPUTargetFacts(width = width, height = height, colorFormat = format.toGpuColorFormat())

    val session = GPUBackendRuntimeFactory.createOrNull()
        ?: error("webgpu-context-unavailable")

    session.use { s ->
        val target = s.createOffscreenTarget(
            GPUOffscreenTargetRequest(
                width = width,
                height = height,
                colorFormat = GPU_COLOR_FORMAT,
            ),
        )
        target.use { t ->
            t.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                var cmdIdCounter = 0
                for (op in ops) {
                    val cmdId = GPUDrawCommandID(cmdIdCounter++)
                    when (op) {
                        is DisplayOp.DrawRect -> {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            dispatchFillRect(cmd, dispatched, diagnostics, width, height)
                        }
                        is DisplayOp.DrawPath -> {
                            val pathData = op.path.toPathTessellatorData()
                            val tessellator = PathTessellator(tolerance = 0.25f, maxVertices = 16384)
                            val flat = tessellator.flatten(pathData)
                            if (flat.size < 3) {
                                diagnostics.fatal(
                                    "refuse:${op.hashCode()}", "drawPath", "insufficient_vertices:${flat.size}",
                                )
                                continue
                            }
                            val tri = tessellator.triangulate(flat)
                            val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
                            val contourStarts = listOf(0)
                            val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                            dispatchFillPath(cmd, dispatched, diagnostics, width, height)
                        }
                        is DisplayOp.DrawRRect -> {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            dispatchFillRRect(cmd, dispatched, diagnostics, width, height)
                        }
                        is DisplayOp.DrawImage -> {
                            diagnostics.fatal(
                                "refuse:drawImage:${cmdId.value}", "drawImage", "unsupported_operation",
                            )
                        }
                        is DisplayOp.DrawText -> {
                            diagnostics.fatal(
                                "refuse:drawText:${cmdId.value}", "drawText", "unsupported_operation",
                            )
                        }
                        is DisplayOp.SetTransform,
                        is DisplayOp.SetClip,
                        is DisplayOp.BeginLayer,
                        is DisplayOp.EndLayer -> { /* state ops, no direct dispatch */ }
                    }
                }
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

internal fun PixelFormat.toGpuColorFormat(): String = when (this) {
    PixelFormat.RGBA8 -> "rgba8unorm"
    PixelFormat.BGRA8 -> "bgra8unorm"
}
