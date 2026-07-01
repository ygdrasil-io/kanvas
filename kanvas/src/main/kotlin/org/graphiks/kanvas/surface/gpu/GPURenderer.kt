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
            t.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                var cmdIdCounter = 0
                for (op in ops) {
                    val cmdId = GPUDrawCommandID(cmdIdCounter++)
                    when (op) {
                        is DisplayOp.DrawRect -> {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                        }
                        is DisplayOp.DrawPath -> {
                            val pathData = op.path.toPathTessellatorData()
                            val tessellator = PathTessellator(
                                tolerance = config.curveTolerance,
                                maxVertices = config.maxPathVertices.toInt(),
                            )
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
                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                        }
                        is DisplayOp.DrawRRect -> {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            dispatchFillRRect(cmd, dispatched, diagnostics, width, height, config)
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
            PathVerb.ARC_TO -> {
                val p0 = kanvasPoints[pi++]
                val p1 = kanvasPoints[pi++]
                val p2 = kanvasPoints[pi++]
                val p3 = kanvasPoints[pi++]
                val lastP = points.lastOrNull() ?: Point(0f, 0f)
                decomposeArcToCubics(lastP.x, lastP.y, p0.x, p0.y, p1.x, p1.y > 0f, p2.x > 0f, p3.x, p3.y, verbs, points)
            }
            PathVerb.CLOSE -> verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.Close)
        }
    }
    return PathData(verbs = verbs, points = points)
}

private fun decomposeArcToCubics(
    x1: Float, y1: Float, rx: Float, ry: Float, xAxisRotation: Float,
    largeArc: Boolean, sweep: Boolean, x2: Float, y2: Float,
    verbs: MutableList<org.graphiks.kanvas.gpu.renderer.geometry.PathVerb>,
    points: MutableList<Point>,
) {
    if (x1 == x2 && y1 == y2) return
    var erx = rx; var ery = ry
    if (erx <= 0f || ery <= 0f) {
        verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.LineTo(Point(x2, y2)))
        points.add(Point(x2, y2))
        return
    }
    val phi = Math.toRadians(xAxisRotation.toDouble()).toFloat()
    val cosPhi = kotlin.math.cos(phi); val sinPhi = kotlin.math.sin(phi)
    val dx2 = (x1 - x2) / 2f; val dy2 = (y1 - y2) / 2f
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2
    var rxs = erx * erx; var rys = ery * ery
    val lambda = x1p * x1p / rxs + y1p * y1p / rys
    if (lambda > 1f) { val s = kotlin.math.sqrt(lambda); erx *= s; ery *= s; rxs = erx * erx; rys = ery * ery }
    val sign = if (largeArc != sweep) 1f else -1f
    val num = rxs * rys - rxs * y1p * y1p - rys * x1p * x1p
    val den = rxs * y1p * y1p + rys * x1p * x1p
    val coeff = sign * kotlin.math.sqrt(kotlin.math.max(num / den, 0f))
    val cxp = coeff * ((erx * y1p) / ery)
    val cyp = coeff * (-(ery * x1p) / erx)
    val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2f
    val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2f
    val ux = (x1p - cxp) / erx; val uy = (y1p - cyp) / ery
    val vx = (-x1p - cxp) / erx; val vy = (-y1p - cyp) / ery
    var theta1 = kotlin.math.atan2(uy, ux)
    var dtheta = kotlin.math.atan2(vy, vx) - theta1
    if (!sweep && dtheta > 0f) dtheta -= (2f * Math.PI).toFloat()
    if (sweep && dtheta < 0f) dtheta += (2f * Math.PI).toFloat()
    val segments = kotlin.math.ceil(kotlin.math.abs(dtheta) / (Math.PI / 2.0).toFloat()).toInt().coerceAtLeast(1)
    val segAngle = dtheta / segments
    val kappa = (4f / 3f) * kotlin.math.tan(segAngle / 4f)
    var angle = theta1
    for (i in 0 until segments) {
        val a1 = angle; val a2 = angle + segAngle
        val ca1 = kotlin.math.cos(a1); val sa1 = kotlin.math.sin(a1)
        val ca2 = kotlin.math.cos(a2); val sa2 = kotlin.math.sin(a2)
        val p0x = cx + erx * ca1; val p0y = cy + ery * sa1
        val p3x = cx + erx * ca2; val p3y = cy + ery * sa2
        val cp1x = p0x - kappa * erx * sa1; val cp1y = p0y + kappa * ery * ca1
        val cp2x = p3x + kappa * erx * sa2; val cp2y = p3y - kappa * ery * ca2
        verbs.add(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.CubicTo(
            Point(cp1x, cp1y), Point(cp2x, cp2y), Point(p3x, p3y),
        ))
        points.add(Point(cp1x, cp1y)); points.add(Point(cp2x, cp2y)); points.add(Point(p3x, p3y))
        angle = a2
    }
}

