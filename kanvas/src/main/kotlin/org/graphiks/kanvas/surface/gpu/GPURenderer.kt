package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Rect

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
            val texFormat = config.gpuColorFormat.wgpuLabel
            val sceneLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
            val srcLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
            val snapLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))

            var sceneHasContent = false
            val clearTransparent = GPUClearColor(0.0, 0.0, 0.0, 0.0)
            fun sceneClear() = if (sceneHasContent) null else clearTransparent

            fun blendModeIndex(mode: GPUBlendMode): Int = when (mode) {
                GPUBlendMode.MULTIPLY -> 0
                GPUBlendMode.SCREEN -> 1
                GPUBlendMode.OVERLAY -> 2
                GPUBlendMode.DARKEN -> 3
                GPUBlendMode.LIGHTEN -> 4
                GPUBlendMode.DIFFERENCE -> 5
                GPUBlendMode.EXCLUSION -> 6
                else -> 0
            }

            fun renderAdvancedBlend(cmd: NormalizedDrawCommand.FillRect) {
                if (sceneHasContent) {
                    // 1a. Snapshot existing scene -> snap
                    t.encodeOffscreenTexture(snapLabel, null) {
                        drawCompositePass(
                            wgsl = COPY_WGSL,
                            colorFormat = texFormat,
                            textureLabel = sceneLabel,
                            draws = listOf(
                                GPUBackendRawUniformDraw(
                                    uniformBytes = ByteArray(16),
                                    scissorX = 0, scissorY = 0,
                                    scissorWidth = width, scissorHeight = height,
                                ),
                            ),
                        )
                    }
                } else {
                    // 1b. No scene yet -> clear snap to transparent (identity dst for blend)
                    t.encodeOffscreenTexture(snapLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }
                // 2. Render source shape -> src
                t.encodeOffscreenTexture(srcLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                }
                // 3. Blend src over snap -> scene
                val blendMode = cmd.blend.blendMode
                val blendIdx = if (blendMode != null) blendModeIndex(blendMode) else 0
                val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
                bb.putInt(blendIdx)
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    drawBlendPass(
                        wgsl = BLEND_FORMULA_WGSL,
                        colorFormat = texFormat,
                        srcTextureLabel = srcLabel,
                        dstTextureLabel = snapLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = bb.array(),
                                scissorX = 0, scissorY = 0,
                                scissorWidth = width, scissorHeight = height,
                            ),
                        ),
                    )
                }
            }



            val textureCache = mutableMapOf<String, ByteArray>()
            fun cachePixels(image: org.graphiks.kanvas.image.Image) {
                if (image.sourceId !in textureCache) {
                    val px = image.pixels
                    if (px != null) {
                        textureCache[image.sourceId] = px
                    } else {
                        diagnostics.warn("no_pixels:${image.sourceId}", "cachePixels", "cpu_pixels_unavailable")
                    }
                }
            }
            fun scanImages(scanOps: List<DisplayOp>) {
                for (op in scanOps) {
                    when (op) {
                        is DisplayOp.DrawImage -> cachePixels(op.image)
                        is DisplayOp.DrawImageNine -> cachePixels(op.image)
                        is DisplayOp.DrawImageLattice -> cachePixels(op.image)
                        is DisplayOp.DrawAtlas -> cachePixels(op.atlas)
                        is DisplayOp.DrawPicture -> scanImages(op.picture.ops)
                        else -> {}
                    }
                }
            }
            scanImages(ops)

            for (op in ops) {
                val cmdId = GPUDrawCommandID(dispatched.size)
                when (op) {
                    is DisplayOp.DrawRect -> {
                        if (op.paint.isStroke()) {
                            val cmd = op.toStrokePathCommand(cmdId, targets)
                            if (cmd.blend.requiresDestinationRead) {
                                diagnostics.fatal("refuse:drawRect:${cmdId.value}", "drawRect", "unsupported_blend:advanced")
                                continue
                            } else {
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                }
                            }
                        } else {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            if (cmd.blend.requiresDestinationRead) {
                                renderAdvancedBlend(cmd)
                            } else {
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                }
                            }
                        }
                        sceneHasContent = true
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
                        } else {
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                            }
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawRRect -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawRRect:${cmdId.value}", "drawRRect", "stroke_rrect_unimplemented")
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillRRect(cmd, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawImage -> {
                        val cmd = op.toImageRectCommand(cmdId, targets)
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchImageRect(cmd, textureCache, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawText -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        // Text route requires glyphRunDescriptor populated by the font module.
                        // planDrawTextRun() in RecordingContracts will refuse gracefully
                        // until the ShapingResult→TextBlob bridge provides real glyph data.
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            drawTextAtlasPassOrDegrade(cmd, dispatched, diagnostics)
                        }
                    }
                    is DisplayOp.SetTransform,
                    is DisplayOp.SetClip,
                    is DisplayOp.BeginLayer,
                    is DisplayOp.EndLayer -> { /* state ops */ }
                    is DisplayOp.DrawColor -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawColor:${cmdId.value}", "drawColor", "unsupported_blend:advanced")
                            continue
                        }
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.Clear -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawPoint -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawPoint:${cmdId.value}", "drawPoint", "stroke_point_unimplemented")
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawPoint:${cmdId.value}", "drawPoint", "unsupported_blend:advanced")
                            continue
                        }
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawPoints -> {
                        when (op.mode) {
                            PointMode.POINTS -> {
                                for (pt in op.points) {
                                    val subCmdId = GPUDrawCommandID(dispatched.size)
                                    val subOp = DisplayOp.DrawPoint(pt.x, pt.y, op.paint, op.transform, op.clip)
                                    val cmd = subOp.toNormalizedCommand(subCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                    }
                                    sceneHasContent = true
                                }
                            }
                            else -> {
                                val path = op.toPath()
                                val pathData = path.toPathTessellatorData()
                                val tessellator = PathTessellator(
                                    tolerance = config.curveTolerance,
                                    maxVertices = config.maxPathVertices.toInt(),
                                )
                                val flat = tessellator.flatten(pathData)
                                if (flat.size < 3) {
                                    diagnostics.fatal("refuse:drawPoints:${cmdId.value}", "drawPoints", "insufficient_vertices:${flat.size}")
                                    continue
                                }
                                val tri = tessellator.triangulate(flat)
                                val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
                                val contourStarts = listOf(0)
                                val isStroke = op.mode == PointMode.LINES
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(
                                    cmdId, targets, vertices, contourStarts, flat.size,
                                ).copy(stroke = isStroke)
                                if (cmd.blend.requiresDestinationRead) {
                                    diagnostics.fatal("refuse:drawPoints:${cmdId.value}", "drawPoints", "unsupported_blend:advanced")
                                    continue
                                }
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                }
                                sceneHasContent = true
                            }
                        }
                    }
                    is DisplayOp.DrawDRRect -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "stroke_drrect_unimplemented")
                            continue
                        }
                        val path = op.toPath()
                        val pathData = path.toPathTessellatorData()
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flat = tessellator.flatten(pathData)
                        if (flat.size < 3) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val tri = tessellator.triangulate(flat)
                        val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
                        val contourStarts = listOf(0)
                        val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                        val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "unsupported_blend:advanced")
                            continue
                        }
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                        }
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawImageNine -> {
                        val cells = op.decompose()
                        for (cell in cells) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val subOp = DisplayOp.DrawImage(
                                image = op.image,
                                src = cell.src,
                                dst = cell.dst,
                                paint = op.paint,
                                transform = op.transform,
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                dispatchImageRect(cmd, textureCache, dispatched, diagnostics, width, height, config)
                            }
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.DrawImageLattice -> {
                        val cells = op.decompose()
                        for (cell in cells) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val subPaint = if (cell.color != null) {
                                val c = cell.color
                                (op.paint ?: org.graphiks.kanvas.paint.Paint()).copy(
                                    color = c,
                                    blendMode = op.paint?.blendMode ?: org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                                )
                            } else op.paint
                            val subOp = DisplayOp.DrawImage(
                                image = op.image,
                                src = cell.src,
                                dst = cell.dst,
                                paint = subPaint,
                                transform = op.transform,
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                dispatchImageRect(cmd, textureCache, dispatched, diagnostics, width, height, config)
                            }
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.DrawPicture -> {
                        val expanded = mutableListOf<DisplayOp>()
                        fun expand(pic: org.graphiks.kanvas.picture.Picture, outerXform: Matrix33) {
                            for (nested in pic.ops) {
                                val combined = when (nested) {
                                    is DisplayOp.DrawPicture -> {
                                        expand(nested.picture, outerXform * nested.transform)
                                        continue
                                    }
                                    else -> nested.withCombinedTransform(outerXform)
                                }
                                expanded.add(combined)
                            }
                        }
                        expand(op.picture, op.transform)
                        for (nestedOp in expanded) {
                            val nestedCmdId = GPUDrawCommandID(dispatched.size)
                            when (nestedOp) {
                                is DisplayOp.DrawRect -> {
                                    if (nestedOp.paint.isStroke()) {
                                        val cmd = nestedOp.toStrokePathCommand(nestedCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                    } else {
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawRRect -> {
                                    if (!nestedOp.paint.isStroke()) {
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillRRect(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawPath -> {
                                    val pd = nestedOp.path.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val fl = tess.flatten(pd)
                                    if (fl.size >= 3) {
                                        val tri = tess.triangulate(fl)
                                        val verts = tri.vertices.flatMap { listOf(it.x, it.y) }
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets, verts, listOf(0), fl.size)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawColor -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.Clear -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawPoint -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawPoints -> {
                                    val p = nestedOp.toPath()
                                    val pd = p.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val fl = tess.flatten(pd)
                                    if (fl.size >= 3) {
                                        val tri = tess.triangulate(fl)
                                        val verts = tri.vertices.flatMap { listOf(it.x, it.y) }
                                        val isStroke = nestedOp.mode == PointMode.LINES
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, listOf(0), fl.size).copy(stroke = isStroke)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawDRRect -> {
                                    val p = nestedOp.toPath()
                                    val pd = p.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val fl = tess.flatten(pd)
                                    if (fl.size >= 3) {
                                        val tri = tess.triangulate(fl)
                                        val verts = tri.vertices.flatMap { listOf(it.x, it.y) }
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, listOf(0), fl.size)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawImage -> {
                                    val imgCmd = nestedOp.toImageRectCommand(nestedCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchImageRect(imgCmd, textureCache, dispatched, diagnostics, width, height, config)
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawImageNine -> {
                                    val cells = nestedOp.decompose()
                                    for (cell in cells) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.image,
                                            src = cell.src,
                                            dst = cell.dst,
                                            paint = nestedOp.paint,
                                            transform = nestedOp.transform,
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchImageRect(imgCmd, textureCache, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawImageLattice -> {
                                    val cells = nestedOp.decompose()
                                    for (cell in cells) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val subPaint = if (cell.color != null) {
                                            val c = cell.color
                                            (nestedOp.paint ?: org.graphiks.kanvas.paint.Paint()).copy(
                                                color = c,
                                                blendMode = nestedOp.paint?.blendMode ?: org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                                            )
                                        } else nestedOp.paint
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.image,
                                            src = cell.src,
                                            dst = cell.dst,
                                            paint = subPaint,
                                            transform = nestedOp.transform,
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchImageRect(imgCmd, textureCache, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawAtlas -> {
                                    val numSprites = minOf(nestedOp.transforms.size, nestedOp.texRects.size)
                                    for (i in 0 until numSprites) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val spriteXform = nestedOp.transform * nestedOp.transforms[i]
                                        val texRect = nestedOp.texRects[i]
                                        val tint = nestedOp.colors?.getOrNull(i)
                                        val subPaint = when {
                                            tint != null && nestedOp.paint != null -> nestedOp.paint.copy(color = tint)
                                            tint != null -> org.graphiks.kanvas.paint.Paint.fill(tint)
                                            else -> nestedOp.paint
                                        }
                                        val screenDst = computeAtlasDst(texRect, spriteXform)
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.atlas,
                                            src = texRect,
                                            dst = screenDst,
                                            paint = subPaint,
                                            transform = Matrix33.identity(),
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchImageRect(imgCmd, textureCache, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawVertices -> {
                                    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_vertices_unimplemented")
                                }
                                is DisplayOp.DrawText -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        drawTextAtlasPassOrDegrade(cmd, dispatched, diagnostics)
                                    }
                                }
                                is DisplayOp.SetTransform,
                                is DisplayOp.SetClip,
                                is DisplayOp.BeginLayer,
                                is DisplayOp.EndLayer,
                                is DisplayOp.Annotation -> { /* state / metadata ops */ }
                                is DisplayOp.DrawPicture -> {
                                    /* already flattened; should not occur */
                                }
                            }
                        }
                    }
                    is DisplayOp.DrawVertices -> {
                        val verts = op.vertices
                        if (verts.texCoords != null) {
                            diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_unimplemented")
                        }
                        if (verts.positions.size >= 3) {
                            val path = Path().also { p ->
                                when (verts.mode) {
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLES -> {
                                        var i = 0
                                        while (i + 2 < verts.positions.size) {
                                            p.moveTo(verts.positions[i].x, verts.positions[i].y)
                                            p.lineTo(verts.positions[i + 1].x, verts.positions[i + 1].y)
                                            p.lineTo(verts.positions[i + 2].x, verts.positions[i + 2].y)
                                            p.close()
                                            i += 3
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_STRIP -> {
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(verts.positions[j - 2].x, verts.positions[j - 2].y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_FAN -> {
                                        val first = verts.positions.first()
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(first.x, first.y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                }
                            }
                            val pathData = path.toPathTessellatorData()
                            val tessellator = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                            val flat = tessellator.flatten(pathData)
                            if (flat.size >= 3) {
                                val tri = tessellator.triangulate(flat)
                                val vertices = tri.vertices.flatMap { listOf(it.x, it.y) }
                                val contourStarts = listOf(0)
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                }
                                sceneHasContent = true
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:insufficient:${cmdId.value}", "drawVertices", "insufficient_vertices:${flat.size}")
                            }
                        }
                    }
                    is DisplayOp.DrawAtlas -> {
                        val numSprites = minOf(op.transforms.size, op.texRects.size)
                        for (i in 0 until numSprites) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val spriteXform = op.transform * op.transforms[i]
                            val texRect = op.texRects[i]
                            val tint = op.colors?.getOrNull(i)
                            val subPaint = when {
                                tint != null && op.paint != null -> op.paint.copy(color = tint)
                                tint != null -> org.graphiks.kanvas.paint.Paint.fill(tint)
                                else -> op.paint
                            }
                            val screenDst = computeAtlasDst(texRect, spriteXform)
                            val subOp = DisplayOp.DrawImage(
                                image = op.atlas,
                                src = texRect,
                                dst = screenDst,
                                paint = subPaint,
                                transform = Matrix33.identity(),
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                dispatchImageRect(cmd, textureCache, dispatched, diagnostics, width, height, config)
                            }
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.Annotation -> { /* no visual output */ }
                }
            }

            // Composite final: scene -> main target for readback
            t.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                drawCompositePass(
                    wgsl = COPY_WGSL,
                    colorFormat = texFormat,
                    textureLabel = sceneLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = ByteArray(16),
                            scissorX = 0, scissorY = 0,
                            scissorWidth = width, scissorHeight = height,
                        ),
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

/** Transform a source rectangle through an affine matrix to obtain screen-space destination bounds. */
private fun computeAtlasDst(texRect: Rect, xform: Matrix33): Rect {
    val x0 = xform.scaleX * texRect.left + xform.skewX * texRect.top + xform.transX
    val y0 = xform.skewY * texRect.left + xform.scaleY * texRect.top + xform.transY
    val x1 = xform.scaleX * texRect.right + xform.skewX * texRect.top + xform.transX
    val y1 = xform.skewY * texRect.right + xform.scaleY * texRect.top + xform.transY
    val x2 = xform.scaleX * texRect.right + xform.skewX * texRect.bottom + xform.transX
    val y2 = xform.skewY * texRect.right + xform.scaleY * texRect.bottom + xform.transY
    val x3 = xform.scaleX * texRect.left + xform.skewX * texRect.bottom + xform.transX
    val y3 = xform.skewY * texRect.left + xform.scaleY * texRect.bottom + xform.transY
    val l = minOf(x0, x1, x2, x3)
    val t = minOf(y0, y1, y2, y3)
    val r = maxOf(x0, x1, x2, x3)
    val b = maxOf(y0, y1, y2, y3)
    return Rect.fromLTRB(l, t, r, b)
}

/** Bridge: DrawText → GPU text atlas pass. Degrades gracefully until font module provides real glyph data and atlas. */
private fun drawTextAtlasPassOrDegrade(
    cmd: NormalizedDrawCommand.DrawTextRun,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
) {
    // Text GPU dispatch requires a populated GlyphRunDescriptor with atlas plan,
    // vertex data (glyph quad positions), and a built A8 glyph atlas texture.
    // Until the font module provides these via the ShapingResult→TextBlob bridge,
    // we degrade gracefully instead of hard-fataling.
    if (cmd.glyphRunDescriptor == null) {
        diagnostics.degrade("degrade:drawText:${cmd.commandId.value}", "drawText", "no_glyph_descriptor")
        return
    }
    // Full GPU text dispatch would call: t.drawTextAtlasPass(atlasRgba, ...)
    diagnostics.degrade("degrade:drawText:${cmd.commandId.value}", "drawText", "text_atlas_not_implemented")
}
