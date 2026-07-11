package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory

import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.types.Color

import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb as GpuPathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.font.colr.COLRPaintNode
import org.graphiks.kanvas.font.colr.COLRV1ColorLineExtend
import org.graphiks.kanvas.font.colr.COLR_FOREGROUND_PALETTE_INDEX
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.GlyphCoordinateMapper
import org.graphiks.kanvas.text.GpuTextBlob
import org.graphiks.kanvas.text.MappedGlyph
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.TextBridge
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun productIntermediatePlannerScopeDiagnostics(): List<String> =
    listOf(
        "gpu.product.phase5 phase5PlannerActivation=false " +
            "reason=product-display-list-route-not-yet-planner-backed " +
            "route=kanvas:scene-src-snap advancedBlend=local-procedural " +
            "scenePlannerActivation=out-of-scope",
    )

internal fun selectPathVerticesForCommand(
    isStroke: Boolean,
    flattened: List<Point>,
    triangulated: List<Point>,
): List<Point> =
    if (isStroke) flattened else triangulated

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
    val targets = GPUTargetFacts(width = width, height = height, colorFormat = config.gpuColorFormat.gpuLabel)

    val session = GPUBackendRuntimeFactory.createOrNull()
        ?: error("webgpu-context-unavailable")

    session.use { s ->
        val target = s.createOffscreenTarget(
            GPUOffscreenTargetRequest(
                width = width,
                height = height,
                colorFormat = config.gpuColorFormat.gpuLabel,
            ),
        )
        target.use { t ->
            val texFormat = config.gpuColorFormat.gpuLabel
            var sceneLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:scene", width = width, height = height, format = texFormat),
            )
            val srcLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:src", width = width, height = height, format = texFormat),
            )
            val snapLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:snap", width = width, height = height, format = texFormat),
            )

            data class SceneTargetFrame(val label: String, val hasContent: Boolean)

            var sceneHasContent = false
            val layerStack = java.util.ArrayDeque<SceneTargetFrame>()
            var layerOrdinal = 0
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
                productIntermediatePlannerScopeDiagnostics().forEach { line ->
                    diagnostics.warn(
                        code = "phase5:product-destination-read:not-planner-backed",
                        operation = "drawRect",
                        reason = line,
                    )
                }
                if (sceneHasContent) {
                    // 1a. Snapshot existing scene -> snap
                    t.encodeOffscreenTexture(snapLabel, clearTransparent) {
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
                // The shader already returns the complete source/destination result. Start from
                // transparent so the fixed-function SrcOver state does not compose that result a
                // second time with the pre-blend scene.
                t.encodeOffscreenTexture(sceneLabel, clearTransparent) {
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

            fun planMaskBlur(command: NormalizedDrawCommand): MaskBlurPlan =
                MaskBlurPlanner.plan(
                    command.toMaskBlurRequest(width, height, t.maxTextureDimension2D, config),
                )

            fun refuseMaskBlur(command: NormalizedDrawCommand, plan: MaskBlurPlan.Refused) {
                diagnostics.fatal(
                    "refuse:${command.diagnosticName}",
                    command.diagnosticName,
                    plan.code,
                )
            }

            fun renderMaskBlur(command: NormalizedDrawCommand, plan: MaskBlurPlan.Ready): Boolean =
                t.renderMaskBlurCommand(
                    sceneLabel,
                    command,
                    plan,
                    sceneClear(),
                    dispatched,
                    diagnostics,
                    texFormat,
                ).rendered

            fun dispatchRectDirect(command: NormalizedDrawCommand.FillRect): Boolean {
                if (command.blend.requiresDestinationRead) {
                    renderAdvancedBlend(command)
                } else {
                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                        dispatchFillRect(command, dispatched, diagnostics, width, height, config)
                    }
                }
                return true
            }

            fun dispatchPathDirect(command: NormalizedDrawCommand.FillPath): Boolean {
                if (command.blend.requiresDestinationRead) {
                    diagnostics.fatal("refuse:drawPath:${command.commandId.value}", "drawPath", "unsupported_blend:advanced")
                    return false
                }
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillPath(command, dispatched, diagnostics, width, height, config)
                }
                return true
            }

            fun dispatchRRectDirect(command: NormalizedDrawCommand.FillRRect): Boolean {
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillRRect(command, dispatched, diagnostics, width, height, config)
                }
                return true
            }

            fun drawGlyphPath(
                commands: List<OutlineCommand>,
                offsetX: Float,
                offsetY: Float,
                color: Color,
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ) {
                val tx = op.transform
                val sx = tx.scaleX; val kx = tx.skewX; val txx = tx.transX
                val ky = tx.skewY; val sy = tx.scaleY; val ty = tx.transY
                val verbs = mutableListOf<GpuPathVerb>()
                for (cmd in commands) {
                    when (cmd) {
                        is OutlineCommand.MoveTo -> {
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.MoveTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                        }
                        is OutlineCommand.LineTo -> {
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.LineTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                        }
                        is OutlineCommand.QuadraticTo -> {
                            val cx = cmd.controlX.toFloat() + offsetX
                            val cy = cmd.controlY.toFloat() + offsetY
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.QuadTo(
                                Point(sx * cx + kx * cy + txx, ky * cx + sy * cy + ty),
                                Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                            ))
                        }
                        is OutlineCommand.CubicTo -> {
                            val c1x = cmd.controlX1.toFloat() + offsetX
                            val c1y = cmd.controlY1.toFloat() + offsetY
                            val c2x = cmd.controlX2.toFloat() + offsetX
                            val c2y = cmd.controlY2.toFloat() + offsetY
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.CubicTo(
                                Point(sx * c1x + kx * c1y + txx, ky * c1x + sy * c1y + ty),
                                Point(sx * c2x + kx * c2y + txx, ky * c2x + sy * c2y + ty),
                                Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                            ))
                        }
                        is OutlineCommand.Close -> verbs.add(GpuPathVerb.Close)
                    }
                }
                val pathData = PathData(verbs, emptyList())
                val tessellator = PathTessellator(
                    tolerance = config.curveTolerance,
                    maxVertices = config.maxPathVertices.toInt(),
                )
                val flattened = tessellator.flattenWithContours(pathData)
                val flat = flattened.points
                if (flat.size < 3) return
                val vertices = flat.flatMap { listOf(it.x, it.y) }
                val syntheticOp = DisplayOp.DrawPath(
                    path = Path { },
                    paint = org.graphiks.kanvas.paint.Paint(color = color),
                    transform = Matrix33.identity(),
                    clip = op.clip,
                )
                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                val cmd = syntheticOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                }
            }

            fun renderShaderText(
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ) {
                val tf = op.blob.typeface as? FontTypeface ?: return
                val scaler = tf.scaler ?: return
                val tx = op.transform
                val sx = tx.scaleX; val kx = tx.skewX; val txx = tx.transX
                val ky = tx.skewY; val sy = tx.scaleY; val ty = tx.transY

                for (run in op.blob.glyphRuns) {
                    for ((idx, gid) in run.glyphs.withIndex()) {
                        val pos = run.positions[idx]
                        val scaled = scaler.scaleGlyph(gid.toInt(), run.fontSize)
                        val mapped = GlyphCoordinateMapper.map(scaled)
                        if (mapped !is MappedGlyph.Drawn) continue
                        val baselineX = pos.x + op.x
                        val baselineY = pos.y + op.y

                        val verbs = mutableListOf<GpuPathVerb>()
                        for (cmd in mapped.outlineCommands) {
                            when (cmd) {
                                is OutlineCommand.MoveTo -> {
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.MoveTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                                }
                                is OutlineCommand.LineTo -> {
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.LineTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                                }
                                is OutlineCommand.QuadraticTo -> {
                                    val cx = cmd.controlX.toFloat() + baselineX
                                    val cy = cmd.controlY.toFloat() + baselineY
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.QuadTo(
                                        Point(sx * cx + kx * cy + txx, ky * cx + sy * cy + ty),
                                        Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                                    ))
                                }
                                is OutlineCommand.CubicTo -> {
                                    val c1x = cmd.controlX1.toFloat() + baselineX
                                    val c1y = cmd.controlY1.toFloat() + baselineY
                                    val c2x = cmd.controlX2.toFloat() + baselineX
                                    val c2y = cmd.controlY2.toFloat() + baselineY
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.CubicTo(
                                        Point(sx * c1x + kx * c1y + txx, ky * c1x + sy * c1y + ty),
                                        Point(sx * c2x + kx * c2y + txx, ky * c2x + sy * c2y + ty),
                                        Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                                    ))
                                }
                                is OutlineCommand.Close -> verbs.add(GpuPathVerb.Close)
                            }
                        }
                        val pathData = PathData(verbs, emptyList())
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        if (flat.size < 3) continue
                        val vertices = flat.flatMap { listOf(it.x, it.y) }
                        val syntheticOp = DisplayOp.DrawPath(
                            path = Path { },
                            paint = op.paint,
                            transform = Matrix33.identity(),
                            clip = op.clip,
                        )
                        val glyphCmdId = GPUDrawCommandID(dispatched.size)
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                        val cmd = syntheticOp.toNormalizedCommand(glyphCmdId, targets, vertices, contourStarts, flat.size)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawText:shader:${glyphCmdId.value}", "drawText", "unsupported_blend:advanced")
                            continue
                        }
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                        }
                    }
                }
            }

            fun dispatchColrV1Node(
                node: COLRPaintNode,
                scaler: GlyphScaler,
                fontSize: Float,
                posX: Float,
                posY: Float,
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
                solidColors: Map<Int, Color>,
            ) {
                val kind = node.kind
                when {
                    kind == "colr-v1-paint-glyph" -> {
                        val refGlyphId = node.glyphId
                        if (refGlyphId == null) {
                            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_glyph_no_ref")
                            return
                        }
                        val scaled = scaler.scaleGlyph(refGlyphId, fontSize)
                        val glyphColor = node.children.firstNotNullOfOrNull { solidColors[it] }
                            ?: op.paint.color
                        val mapped = GlyphCoordinateMapper.map(scaled)
                        if (mapped is MappedGlyph.Drawn) {
                            drawGlyphPath(mapped.outlineCommands, posX + op.x, posY + op.y, glyphColor, op, cmdId)
                        }
                    }
                    kind == "colr-v1-paint-solid" -> { /* color resolved by parent glyph via solidColors */ }
                    kind == "colr-v1-paint-linear-gradient" ||
                    kind == "colr-v1-paint-radial-gradient" -> {
                        diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_gradient_needs_paint_tree")
                    }
                    kind == "colr-v1-paint-sweep-gradient" -> {
                        diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_sweep_not_routed")
                    }
                    kind.startsWith("colr-v1-paint-translate") ||
                    kind.startsWith("colr-v1-paint-scale") ||
                    kind.startsWith("colr-v1-paint-rotate") ||
                    kind.startsWith("colr-v1-paint-skew") ||
                    kind == "colr-v1-paint-transform" ||
                    kind.startsWith("colr-v1-paint-composite") ||
                    kind == "colr-v1-paint-layers" ||
                    kind == "colr-v1-paint-colr-glyph" ||
                    kind == "colr-v1-glyph" -> { /* passthrough */ }
                }
            }

            fun renderColorText(
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ) {
                val tf = op.blob.typeface as? FontTypeface ?: return
                val scaler = tf.scaler ?: return
                val foregroundColor = op.paint.color

                fun resolveSolidColors(nodes: List<COLRPaintNode>): Map<Int, Color> {
                    val result = mutableMapOf<Int, Color>()
                    for (n in nodes) {
                        if (n.kind != "colr-v1-paint-solid") continue
                        val pi = n.paletteIndex ?: continue
                        if (pi == COLR_FOREGROUND_PALETTE_INDEX) {
                            result[n.id] = foregroundColor
                            continue
                        }
                        val argb = scaler.resolveCpalColor(pi) ?: continue
                        result[n.id] = Color.fromRGBA(
                            ((argb shr 16) and 0xFF) / 255f,
                            ((argb shr 8) and 0xFF) / 255f,
                            (argb and 0xFF) / 255f,
                            ((argb shr 24) and 0xFF) / 255f,
                        )
                    }
                    return result
                }

                for (run in op.blob.glyphRuns) {
                    for ((idx, gid) in run.glyphs.withIndex()) {
                        val pos = run.positions[idx]
                        val rep = scaler.getGlyphRepresentation(gid.toInt(), op.blob.fontSize) ?: continue

                        when (rep) {
                            is GlyphRepresentation.ColorLayersV1 -> {
                                val solidColors = resolveSolidColors(rep.paintGraph.nodes)
                                for (node in rep.paintGraph.nodes) {
                                    dispatchColrV1Node(
                                        node, scaler, op.blob.fontSize, pos.x, pos.y, op, cmdId, solidColors,
                                    )
                                }
                            }
                            is GlyphRepresentation.ColorLayers -> {
                                for (layer in rep.layers) {
                                    val scaled = scaler.scaleGlyph(layer.glyphId, op.blob.fontSize)
                                    val color = Color.fromRGBA(
                                        ((layer.paletteColorArgb shr 16) and 0xFF) / 255f,
                                        ((layer.paletteColorArgb shr 8) and 0xFF) / 255f,
                                        (layer.paletteColorArgb and 0xFF) / 255f,
                                        ((layer.paletteColorArgb shr 24) and 0xFF) / 255f,
                                    )
                                    val mapped = GlyphCoordinateMapper.map(scaled)
                                    if (mapped is MappedGlyph.Drawn) {
                                        drawGlyphPath(mapped.outlineCommands, pos.x + op.x, pos.y + op.y, color, op, cmdId)
                                    }
                                }
                            }
                            is GlyphRepresentation.Bitmap -> {
                                val x = pos.x + op.x + rep.originX
                                val y = pos.y + op.y + rep.originY
                                val w = rep.pixelWidth.toFloat().coerceAtLeast(1f)
                                val h = rep.pixelHeight.toFloat().coerceAtLeast(1f)
                                val syntheticOp = DisplayOp.DrawRect(
                                    rect = Rect.fromLTRB(x, y, x + w, y + h),
                                    paint = org.graphiks.kanvas.paint.Paint(
                                        color = Color.fromRGBA(1f, 0.078f, 0.576f, 0.5f),
                                    ),
                                    transform = op.transform,
                                    clip = op.clip,
                                )
                                val cmd = syntheticOp.toNormalizedCommand(
                                    GPUDrawCommandID(dispatched.size), targets,
                                )
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillRect(cmd, dispatched, diagnostics, width, height, config)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            fun extendToTileMode(extend: COLRV1ColorLineExtend?): TileMode = when (extend) {
                COLRV1ColorLineExtend.PAD -> TileMode.CLAMP
                COLRV1ColorLineExtend.REPEAT -> TileMode.REPEAT
                COLRV1ColorLineExtend.REFLECT -> TileMode.MIRROR
                null -> TileMode.CLAMP
            }

            val textureCache = mutableMapOf<String, ByteArray>()
            fun cachePixels(image: org.graphiks.kanvas.image.Image) {
                if (image.sourceId !in textureCache) {
                    val px = image.pixels
                    if (px != null) {
                        textureCache[image.sourceId] = image.expandToRgbaForGpu()
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

            fun renderImageCommand(cmd: NormalizedDrawCommand.DrawImageRect) {
                when (val plan = cmd.imageFilterPlan) {
                    GPUImageFilterPlan.None, GPUImageFilterPlan.Identity -> {
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchImageRect(cmd, textureCache, dispatched, diagnostics, width, height, config)
                        }
                    }
                    is GPUImageFilterPlan.Refused -> {
                        diagnostics.fatal("refuse:${cmd.diagnosticName}", cmd.diagnosticName, plan.code)
                    }
                    is GPUImageFilterPlan.Blur -> {
                        t.renderImageCommand(
                            sceneTextureLabel = sceneLabel,
                            command = cmd,
                            textureCache = textureCache,
                            sceneClearColor = sceneClear(),
                            dispatched = dispatched,
                            diagnostics = diagnostics,
                            colorFormat = texFormat,
                        )
                    }
                }
            }

            var suppressedLayerDepth = 0
            val trivialLayerPaint = Paint()
            for (op in ops) {
                val cmdId = GPUDrawCommandID(dispatched.size)
                if (op is DisplayOp.BeginLayer) {
                    if (suppressedLayerDepth > 0) {
                        suppressedLayerDepth++
                        continue
                    }
                    val refusalReason = when {
                        op.rec.backdrop != null -> "unsupported.layer.backdrop_filter"
                        op.rec.bounds != null -> "unsupported.layer.bounds"
                        op.rec.paint != null && op.rec.paint != trivialLayerPaint -> "unsupported.layer.paint"
                        else -> null
                    }
                    if (refusalReason != null) {
                        diagnostics.fatal(
                            "refuse:saveLayer:${cmdId.value}",
                            "saveLayer",
                            refusalReason,
                        )
                        suppressedLayerDepth = 1
                    } else {
                        layerStack.addLast(SceneTargetFrame(sceneLabel, sceneHasContent))
                        sceneLabel = t.createOffscreenTexture(
                            GPUBackendOffscreenTexture(
                                label = "kanvas:saveLayer:${layerOrdinal++}",
                                width = width,
                                height = height,
                                format = texFormat,
                            ),
                        )
                        sceneHasContent = false
                    }
                    continue
                }
                if (op is DisplayOp.EndLayer) {
                    if (suppressedLayerDepth > 0) {
                        suppressedLayerDepth--
                    } else if (layerStack.isEmpty()) {
                        diagnostics.fatal(
                            "refuse:saveLayer:${cmdId.value}",
                            "saveLayer",
                            "unsupported.layer.unbalanced_end",
                        )
                    } else {
                        val childLabel = sceneLabel
                        val childHasContent = sceneHasContent
                        val parent = layerStack.removeLast()
                        sceneLabel = parent.label
                        sceneHasContent = parent.hasContent
                        if (childHasContent) {
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                drawCompositePass(
                                    wgsl = COPY_WGSL,
                                    colorFormat = texFormat,
                                    textureLabel = childLabel,
                                    draws = listOf(
                                        GPUBackendRawUniformDraw(
                                            uniformBytes = ByteArray(16),
                                            scissorX = 0, scissorY = 0,
                                            scissorWidth = width, scissorHeight = height,
                                        ),
                                    ),
                                    blendMode = GPUBlendMode.SRC_OVER,
                                )
                            }
                            sceneHasContent = true
                        }
                    }
                    continue
                }
                if (suppressedLayerDepth > 0) continue
                when (op) {
                    is DisplayOp.DrawRect -> {
                        val rendered = if (op.paint.isStroke()) {
                            val cmd = op.toStrokePathCommand(cmdId, targets)
                            if (cmd.maskFilter == null) {
                                if (cmd.blend.requiresDestinationRead) {
                                    diagnostics.fatal("refuse:drawRect:${cmdId.value}", "drawRect", "unsupported_blend:advanced")
                                    false
                                } else {
                                    dispatchPathDirect(cmd)
                                }
                            } else when (val plan = planMaskBlur(cmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(cmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> {
                                    if (cmd.blend.requiresDestinationRead) {
                                        diagnostics.fatal("refuse:drawRect:${cmdId.value}", "drawRect", "unsupported_blend:advanced")
                                        false
                                    } else {
                                        dispatchPathDirect(cmd)
                                    }
                                }
                            }
                        } else {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            if (cmd.maskFilter == null) {
                                dispatchRectDirect(cmd)
                            } else when (val plan = planMaskBlur(cmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(cmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> dispatchRectDirect(cmd)
                            }
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawPath -> {
                        val paint = op.paint
                        val isStroke = paint.isStroke()
                        val pathRect = Rect(0f, 0f, 0f, 0f)
                        if (!isStroke &&
                            op.transform == Matrix33.identity() &&
                            op.path.fillType in setOf(FillType.WINDING, FillType.EVEN_ODD) &&
                            op.path.isRect(pathRect)
                        ) {
                            val rectCmd = DisplayOp.DrawRect(pathRect, paint, op.transform, op.clip)
                                .toNormalizedCommand(cmdId, targets)
                            val rendered = if (rectCmd.maskFilter == null) {
                                dispatchRectDirect(rectCmd)
                            } else when (val plan = planMaskBlur(rectCmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(rectCmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(rectCmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> dispatchRectDirect(rectCmd)
                            }
                            sceneHasContent = sceneHasContent || rendered
                            continue
                        }
                        val pathData = op.path.toPathTessellatorData()
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        val allowsDegenerateRoundStroke = isStroke && paint.strokeCap.name.lowercase() == "round"
                        val minVertices = if (isStroke) {
                            if (allowsDegenerateRoundStroke) 1 else 2
                        } else {
                            3
                        }
                        if (flat.size < minVertices) {
                            diagnostics.fatal("refuse:${op.hashCode()}", "drawPath", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val vertices = selectPathVerticesForCommand(
                            isStroke = isStroke,
                            flattened = flat,
                            triangulated = flat,
                        ).flatMap { listOf(it.x, it.y) }
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                        val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                        val rendered = if (cmd.maskFilter == null) {
                            dispatchPathDirect(cmd)
                        } else when (val plan = planMaskBlur(cmd)) {
                            is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                            is MaskBlurPlan.Refused -> {
                                refuseMaskBlur(cmd, plan)
                                false
                            }
                            MaskBlurPlan.Identity -> dispatchPathDirect(cmd)
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawRRect -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawRRect:${cmdId.value}", "drawRRect", "stroke_rrect_unimplemented")
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        val rendered = if (cmd.maskFilter == null) {
                            dispatchRRectDirect(cmd)
                        } else when (val plan = planMaskBlur(cmd)) {
                            is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                            is MaskBlurPlan.Refused -> {
                                refuseMaskBlur(cmd, plan)
                                false
                            }
                            MaskBlurPlan.Identity -> dispatchRRectDirect(cmd)
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawImage -> {
                        val cmd = op.toImageRectCommand(cmdId, targets)
                        renderImageCommand(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawText -> {
                        if (op.paint.shader != null && extractSolidShaderColor(op.paint.shader) == null) {
                            renderShaderText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        if (hasColorGlyphs(op.blob)) {
                            renderColorText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        if (op.paint.isStroke()) {
                            renderShaderText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawText:${cmdId.value}", "drawText", "unsupported_blend:advanced")
                            continue
                        }
                        val ctmScale = ctmEffectiveScale(op.transform)
                        val rasterBlob = op.blob.scaledForRasterization(ctmScale)
                        var gpuBlob = TextBridge.rasterize(rasterBlob)
                        if (gpuBlob != null) {
                            gpuBlob = gpuBlob.normalizeGlyphRects(ctmScale)
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                drawTextAtlasPass(
                                    gpuBlob,
                                    cmd.blend.blendMode,
                                    dispatched,
                                    diagnostics,
                                    textColor = resolveTextColor(op.paint),
                                    targetWidth = width,
                                    targetHeight = height,
                                    drawOriginX = op.x,
                                    drawOriginY = op.y,
                                    transform = op.transform,
                                )
                            }
                            sceneHasContent = true
                        } else {
                            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "rasterize_failed")
                        }
                    }
                    is DisplayOp.SetTransform,
                    is DisplayOp.SetClip -> { /* state ops */ }
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
                                val flattened = tessellator.flattenWithContours(pathData)
                                val flat = flattened.points
                                if (flat.size < 3) {
                                    diagnostics.fatal("refuse:drawPoints:${cmdId.value}", "drawPoints", "insufficient_vertices:${flat.size}")
                                    continue
                                }
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
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
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        if (flat.size < 3) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val vertices = flat.flatMap { listOf(it.x, it.y) }
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
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
                            renderImageCommand(cmd)
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
                            renderImageCommand(cmd)
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
                        if (expanded.any { it is DisplayOp.BeginLayer || it is DisplayOp.EndLayer }) {
                            diagnostics.fatal(
                                "refuse:drawPicture:${cmdId.value}",
                                "drawPicture",
                                "unsupported.picture.save_layer",
                            )
                            continue
                        }
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
                                    val paint = nestedOp.paint
                                    val isStroke = paint.isStroke()
                                    val pd = nestedOp.path.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    val allowsDegenerateRoundStroke = isStroke && paint.strokeCap.name.lowercase() == "round"
                                    val minVertices = if (isStroke) {
                                        if (allowsDegenerateRoundStroke) 1 else 2
                                    } else {
                                        3
                                    }
                                    if (fl.size >= minVertices) {
                                        val verts = selectPathVerticesForCommand(
                                            isStroke = isStroke,
                                            flattened = fl,
                                            triangulated = fl,
                                        ).flatMap { listOf(it.x, it.y) }
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size)
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
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    if (fl.size >= 3) {
                                        val verts = fl.flatMap { listOf(it.x, it.y) }
                                        val isStroke = nestedOp.mode == PointMode.LINES
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size).copy(stroke = isStroke)
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
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    if (fl.size >= 3) {
                                        val verts = fl.flatMap { listOf(it.x, it.y) }
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                        }
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawImage -> {
                                    val imgCmd = nestedOp.toImageRectCommand(nestedCmdId, targets)
                                    renderImageCommand(imgCmd)
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
                                        renderImageCommand(imgCmd)
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
                                        renderImageCommand(imgCmd)
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
                                        renderImageCommand(imgCmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawVertices -> {
                                    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_vertices_unimplemented")
                                }
                                is DisplayOp.DrawMesh -> {
                                    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_mesh_unimplemented")
                                }
                                is DisplayOp.DrawText -> {
                                    if (nestedOp.paint.shader != null && extractSolidShaderColor(nestedOp.paint.shader) == null) {
                                        renderShaderText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    if (hasColorGlyphs(nestedOp.blob)) {
                                        renderColorText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    if (nestedOp.paint.isStroke()) {
                                        renderShaderText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    if (cmd.blend.requiresDestinationRead) {
                                        diagnostics.fatal("refuse:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "unsupported_blend:advanced")
                                        continue
                                    }
                                    val ctmScale = ctmEffectiveScale(nestedOp.transform)
                                    val rasterBlob = nestedOp.blob.scaledForRasterization(ctmScale)
                                    var gpuBlob = TextBridge.rasterize(rasterBlob)
                                    if (gpuBlob != null) {
                                        gpuBlob = gpuBlob.normalizeGlyphRects(ctmScale)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            drawTextAtlasPass(
                                                gpuBlob,
                                                cmd.blend.blendMode,
                                                dispatched,
                                                diagnostics,
                                                textColor = resolveTextColor(nestedOp.paint),
                                                targetWidth = width,
                                                targetHeight = height,
                                                drawOriginX = nestedOp.x,
                                                drawOriginY = nestedOp.y,
                                                transform = nestedOp.transform,
                                            )
                                        }
                                        sceneHasContent = true
                                    } else {
                                        diagnostics.degrade("degrade:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "nested_text_rasterize_failed")
                                    }
                                }
                                is DisplayOp.SetTransform,
                                is DisplayOp.SetClip,
                                is DisplayOp.BeginLayer,
                                is DisplayOp.EndLayer,
                                is DisplayOp.Annotation,
                                is DisplayOp.FlushAndSnapshot -> { /* state / metadata ops */ }
                                is DisplayOp.DrawPicture -> {
                                    /* already flattened; should not occur */
                                }
                            }
                        }
                    }
                    is DisplayOp.DrawVertices -> {
                        val verts = op.vertices
                        if (verts.texCoords != null) {
                            val tex = verts.texCoords
                            val idx = verts.indices?.toIntArray()
                                ?: IntArray(verts.positions.size) { it }
                            val posFlat = FloatArray(verts.positions.size * 2) {
                                if (it % 2 == 0) verts.positions[it / 2].x else verts.positions[it / 2].y
                            }
                            val uvFlat = FloatArray(tex.size * 2) {
                                if (it % 2 == 0) tex[it / 2].x else tex[it / 2].y
                            }
                            val paint = op.paint

                            if (paint.shader is Shader.Image) {
                                val img = paint.shader.image
                                val texBytes = img.pixels
                                val texW = img.width
                                val texH = img.height
                                if (texBytes != null) {
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchTexturedVertices(
                                            positions = posFlat, uvs = uvFlat, uvs2 = null,
                                            indices = idx, paint = paint,
                                            textureBytes = texBytes,
                                            textureWidth = texW, textureHeight = texH,
                                            textureSourceId = img.sourceId,
                                            diagnostics = diagnostics,
                                            surfaceWidth = width, surfaceHeight = height,
                                            config = config, diagnosticName = op.paint.toString(),
                                        )
                                    }
                                    sceneHasContent = true
                                    continue
                                } else {
                                    diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_pixels")
                                }
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_image_shader")
                            }
                            continue
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
                            val flattened = tessellator.flattenWithContours(pathData)
                            val flat = flattened.points
                            if (flat.size >= 3) {
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
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
                    is DisplayOp.DrawMesh -> {
                        val verts = op.mesh.vertices
                        if (verts.texCoords != null) {
                            val tex = verts.texCoords
                            val idx = verts.indices?.toIntArray()
                                ?: IntArray(verts.positions.size) { it }
                            val posFlat = FloatArray(verts.positions.size * 2) {
                                if (it % 2 == 0) verts.positions[it / 2].x else verts.positions[it / 2].y
                            }
                            val uvFlat = FloatArray(tex.size * 2) {
                                if (it % 2 == 0) tex[it / 2].x else tex[it / 2].y
                            }
                            val paint = op.paint

                            if (paint.shader is Shader.Image) {
                                val img = paint.shader.image
                                val texBytes = img.pixels
                                val texW = img.width
                                val texH = img.height
                                if (texBytes != null) {
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchTexturedVertices(
                                            positions = posFlat, uvs = uvFlat, uvs2 = null,
                                            indices = idx, paint = paint,
                                            textureBytes = texBytes,
                                            textureWidth = texW, textureHeight = texH,
                                            textureSourceId = img.sourceId,
                                            diagnostics = diagnostics,
                                            surfaceWidth = width, surfaceHeight = height,
                                            config = config, diagnosticName = op.paint.toString(),
                                        )
                                    }
                                    sceneHasContent = true
                                    continue
                                } else {
                                    diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_pixels")
                                }
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_image_shader")
                            }
                            continue
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
                            val flattened = tessellator.flattenWithContours(pathData)
                            val flat = flattened.points
                            if (flat.size >= 3) {
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    dispatchFillPath(cmd, dispatched, diagnostics, width, height, config)
                                }
                                sceneHasContent = true
                            } else {
                                diagnostics.degrade("unimplemented:drawMesh:insufficient:${cmdId.value}", "drawMesh", "insufficient_vertices:${flat.size}")
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
                            renderImageCommand(cmd)
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.Annotation -> { /* no visual output */ }
                    is DisplayOp.FlushAndSnapshot -> { /* deferred to render-backend; no-op in CPU path */ }
                }
            }

            if (suppressedLayerDepth > 0) {
                diagnostics.fatal(
                    "refuse:saveLayer:suppressed-unbalanced",
                    "saveLayer",
                    "unsupported.layer.unbalanced_begin",
                )
            }
            while (layerStack.isNotEmpty()) {
                diagnostics.fatal(
                    "refuse:saveLayer:unbalanced:${layerStack.size}",
                    "saveLayer",
                    "unsupported.layer.unbalanced_begin",
                )
                val parent = layerStack.removeLast()
                sceneLabel = parent.label
                sceneHasContent = parent.hasContent
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
                    opsRefused = diagnostics.fatalCount,
                    pipelineCount = 1,
                    drawCallCount = dispatched.size,
                    coverage = if (dispatched.isNotEmpty()) 1f else 0f,
                ),
            )
        }
    }
}

internal fun org.graphiks.kanvas.geometry.Path.toPathTessellatorData(): PathData {
    val verbs = mutableListOf<GpuPathVerb>()
    val points = mutableListOf<Point>()
    val kanvasVerbs = this.verbs()
    val kanvasPoints = this.points()
    var pi = 0
    var currentPoint = Point(0f, 0f)
    var contourStart = currentPoint
    for (verb in kanvasVerbs) {
        when (verb) {
            PathVerb.MOVE -> {
                val p = kanvasPoints[pi++]
                currentPoint = Point(p.x, p.y)
                contourStart = currentPoint
                verbs.add(GpuPathVerb.MoveTo(currentPoint))
            }
            PathVerb.LINE -> {
                val p = kanvasPoints[pi++]
                currentPoint = Point(p.x, p.y)
                verbs.add(GpuPathVerb.LineTo(currentPoint))
            }
            PathVerb.QUAD -> {
                val c = kanvasPoints[pi++]; val p = kanvasPoints[pi++]
                currentPoint = Point(p.x, p.y)
                verbs.add(
                    GpuPathVerb.QuadTo(
                        Point(c.x, c.y), currentPoint,
                    ),
                )
            }
            PathVerb.CUBIC -> {
                val c1 = kanvasPoints[pi++]; val c2 = kanvasPoints[pi++]; val p = kanvasPoints[pi++]
                currentPoint = Point(p.x, p.y)
                verbs.add(
                    GpuPathVerb.CubicTo(
                        Point(c1.x, c1.y), Point(c2.x, c2.y), currentPoint,
                    ),
                )
            }
            PathVerb.ARC_TO -> {
                val radius = kanvasPoints[pi++]
                val rotationAndLargeArc = kanvasPoints[pi++]
                val sweep = kanvasPoints[pi++]
                val endpoint = kanvasPoints[pi++]
                val arcEndpoint = Point(endpoint.x, endpoint.y)
                flattenSvgArc(
                    start = currentPoint,
                    radius = Point(radius.x, radius.y),
                    xAxisRotation = rotationAndLargeArc.x,
                    largeArc = rotationAndLargeArc.y > 0f,
                    sweep = sweep.x > 0f,
                    endpoint = arcEndpoint,
                ).forEach { verbs.add(GpuPathVerb.LineTo(it)) }
                currentPoint = arcEndpoint
            }
            PathVerb.CLOSE -> {
                verbs.add(GpuPathVerb.Close)
                currentPoint = contourStart
            }
        }
    }
    return PathData(verbs = verbs, points = points)
}

private fun flattenSvgArc(
    start: Point,
    radius: Point,
    xAxisRotation: Float,
    largeArc: Boolean,
    sweep: Boolean,
    endpoint: Point,
): List<Point> {
    if (!endpoint.x.isFinite() || !endpoint.y.isFinite()) return emptyList()
    if (
        !start.x.isFinite() || !start.y.isFinite() ||
        !radius.x.isFinite() || !radius.y.isFinite() ||
        !xAxisRotation.isFinite()
    ) {
        return listOf(endpoint)
    }

    val startX = start.x.toDouble()
    val startY = start.y.toDouble()
    val endX = endpoint.x.toDouble()
    val endY = endpoint.y.toDouble()
    var rx = abs(radius.x.toDouble())
    var ry = abs(radius.y.toDouble())
    if (rx == 0.0 || ry == 0.0 || (startX == endX && startY == endY)) {
        return listOf(endpoint)
    }

    val rotation = (xAxisRotation.toDouble() % 360.0) * PI / 180.0
    val cosRotation = cos(rotation)
    val sinRotation = sin(rotation)
    val halfDx = (startX - endX) / 2.0
    val halfDy = (startY - endY) / 2.0
    val transformedX = cosRotation * halfDx + sinRotation * halfDy
    val transformedY = -sinRotation * halfDx + cosRotation * halfDy

    val radiiScale = transformedX * transformedX / (rx * rx) +
        transformedY * transformedY / (ry * ry)
    if (!radiiScale.isFinite()) return listOf(endpoint)
    if (radiiScale > 1.0) {
        val scale = sqrt(radiiScale)
        rx *= scale
        ry *= scale
    }

    val rxSquared = rx * rx
    val rySquared = ry * ry
    val transformedXSquared = transformedX * transformedX
    val transformedYSquared = transformedY * transformedY
    val centerDenominator = rxSquared * transformedYSquared + rySquared * transformedXSquared
    if (centerDenominator <= 0.0 || !centerDenominator.isFinite()) return listOf(endpoint)
    val centerNumerator = maxOf(
        0.0,
        rxSquared * rySquared - rxSquared * transformedYSquared - rySquared * transformedXSquared,
    )
    val centerSign = if (largeArc == sweep) -1.0 else 1.0
    val centerScale = centerSign * sqrt(centerNumerator / centerDenominator)
    if (!centerScale.isFinite()) return listOf(endpoint)

    val centerTransformedX = centerScale * rx * transformedY / ry
    val centerTransformedY = -centerScale * ry * transformedX / rx
    val centerX = cosRotation * centerTransformedX - sinRotation * centerTransformedY +
        (startX + endX) / 2.0
    val centerY = sinRotation * centerTransformedX + cosRotation * centerTransformedY +
        (startY + endY) / 2.0

    val startVectorX = (transformedX - centerTransformedX) / rx
    val startVectorY = (transformedY - centerTransformedY) / ry
    val endVectorX = (-transformedX - centerTransformedX) / rx
    val endVectorY = (-transformedY - centerTransformedY) / ry
    val startAngle = atan2(startVectorY, startVectorX)
    var sweepAngle = atan2(
        startVectorX * endVectorY - startVectorY * endVectorX,
        startVectorX * endVectorX + startVectorY * endVectorY,
    )
    if (!sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
    if (sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI
    if (!startAngle.isFinite() || !sweepAngle.isFinite()) return listOf(endpoint)

    val flatnessTolerance = 0.25
    val subdivisionRadius = maxOf(rx, ry)
    val maxSegmentAngle = if (subdivisionRadius <= flatnessTolerance) {
        PI
    } else {
        val cosine = ((subdivisionRadius - flatnessTolerance).coerceAtLeast(0.0) / subdivisionRadius)
            .coerceIn(0.0, 1.0)
        2.0 * acos(cosine)
    }
    val segmentCount = if (maxSegmentAngle.isFinite() && maxSegmentAngle > 0.0) {
        ceil(abs(sweepAngle) / maxSegmentAngle).coerceIn(1.0, 64.0).toInt()
    } else {
        64
    }
    val flattened = ArrayList<Point>(segmentCount)
    for (segment in 1..segmentCount) {
        if (segment == segmentCount) {
            flattened.add(endpoint)
            continue
        }
        val angle = startAngle + sweepAngle * segment / segmentCount
        val ellipseX = rx * cos(angle)
        val ellipseY = ry * sin(angle)
        val x = centerX + cosRotation * ellipseX - sinRotation * ellipseY
        val y = centerY + sinRotation * ellipseX + cosRotation * ellipseY
        val floatX = x.toFloat()
        val floatY = y.toFloat()
        if (!floatX.isFinite() || !floatY.isFinite()) return listOf(endpoint)
        flattened.add(Point(floatX, floatY))
    }
    return flattened
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

private fun hasColorGlyphs(blob: TextBlob): Boolean {
    val tf = blob.typeface as? FontTypeface ?: return false
    val scaler = tf.scaler ?: return false
    // Shortcut: check table presence instead of scaling every glyph
    if (!scaler.hasAnyColorTable) return false
    for (run in blob.glyphRuns) {
        for (gid in run.glyphs) {
            val rep = scaler.getGlyphRepresentation(gid.toInt(), blob.fontSize)
            if (rep is GlyphRepresentation.ColorLayers || rep is GlyphRepresentation.Bitmap || rep is GlyphRepresentation.ColorLayersV1) return true
        }
    }
    return false
}

internal data class TextAtlasMesh(
    val vertexData: FloatArray,
    val indexData: IntArray,
)

internal fun buildTextAtlasMesh(
    gpuBlob: GpuTextBlob,
    drawOriginX: Float = 0f,
    drawOriginY: Float = 0f,
    transform: Matrix33? = null,
): TextAtlasMesh {
    val uvs = gpuBlob.glyphUvs
    val vertexData = mutableListOf<Float>()
    val indexData = mutableListOf<Int>()
    var glyphIndex = 0
    var quadIndex = 0
    val hasXform = transform != null
    val sx = transform?.scaleX ?: 1f
    val kx = transform?.skewX ?: 0f
    val tx = transform?.transX ?: 0f
    val ky = transform?.skewY ?: 0f
    val sy = transform?.scaleY ?: 1f
    val ty = transform?.transY ?: 0f

    for (run in gpuBlob.textBlob.glyphRuns) {
        for (pos in run.positions) {
            val uv = uvs.getOrNull(glyphIndex) ?: Rect.fromLTRB(0f, 0f, 1f, 1f)
            val glyphRect = gpuBlob.glyphRects.getOrNull(glyphIndex) ?: Rect(0f, 0f, 10f, 10f)
            glyphIndex++

            val left = drawOriginX + pos.x + glyphRect.left
            val top = drawOriginY + pos.y + glyphRect.top
            val right = drawOriginX + pos.x + glyphRect.right
            val bottom = drawOriginY + pos.y + glyphRect.bottom
            val w = right - left
            val h = bottom - top
            if (w <= 0f || h <= 0f) continue

            if (hasXform) {
                val x0 = sx * left + kx * top + tx
                val y0 = ky * left + sy * top + ty
                val x1 = sx * right + kx * top + tx
                val y1 = ky * right + sy * top + ty
                val x2 = sx * right + kx * bottom + tx
                val y2 = ky * right + sy * bottom + ty
                val x3 = sx * left + kx * bottom + tx
                val y3 = ky * left + sy * bottom + ty
                vertexData.addAll(listOf(x0, y0, uv.left, uv.top))
                vertexData.addAll(listOf(x1, y1, uv.right, uv.top))
                vertexData.addAll(listOf(x2, y2, uv.right, uv.bottom))
                vertexData.addAll(listOf(x3, y3, uv.left, uv.bottom))
            } else {
                vertexData.addAll(listOf(left, top, uv.left, uv.top))
                vertexData.addAll(listOf(right, top, uv.right, uv.top))
                vertexData.addAll(listOf(right, bottom, uv.right, uv.bottom))
                vertexData.addAll(listOf(left, bottom, uv.left, uv.bottom))
            }
            val base = quadIndex * 4
            indexData.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
            quadIndex++
        }
    }

    return TextAtlasMesh(vertexData.toFloatArray(), indexData.toIntArray())
}

/** Dispatch text atlas pass from a rasterized [GpuTextBlob]. */
private fun GPUBackendRenderRecorder.drawTextAtlasPass(
    gpuBlob: GpuTextBlob,
    blendMode: GPUBlendMode?,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    textColor: Color = Color.BLACK,
    targetWidth: Int = 0,
    targetHeight: Int = 0,
    drawOriginX: Float = 0f,
    drawOriginY: Float = 0f,
    transform: Matrix33? = null,
) {
    val blob = gpuBlob.textBlob
    val mesh = buildTextAtlasMesh(gpuBlob, drawOriginX, drawOriginY, transform)
    val vertexData = mesh.vertexData
    val indexData = mesh.indexData

    if (vertexData.isEmpty() || indexData.isEmpty()) return
    if (gpuBlob.atlasRgba.isEmpty() || gpuBlob.atlasWidth == 0 || gpuBlob.atlasHeight == 0) {
        diagnostics.degrade("degrade:drawText:empty_atlas", "drawText", "empty_atlas")
        return
    }


    // Populate uniforms matching TEXT_ATLAS_A8_WGSL struct:
    //   struct Uniforms {
    //       targetWidth: f32,     // offset 0,  size 4
    //       targetHeight: f32,    // offset 4,  size 4
    //       color: vec4<f32>,     // offset 16, size 16 (vec4 requires 16-byte alignment)
    //   };  // total size: 32 bytes
    val tw = if (targetWidth > 0) targetWidth.toFloat() else gpuBlob.atlasWidth.toFloat()
    val th = if (targetHeight > 0) targetHeight.toFloat() else gpuBlob.atlasHeight.toFloat()
    val uniformBytes = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    uniformBytes.putFloat(tw)           // targetWidth
    uniformBytes.putFloat(th)           // targetHeight
    uniformBytes.putFloat(0f)           // padding (vec4 alignment)
    uniformBytes.putFloat(0f)           // padding
    val cr = ((textColor.packed shr 16) and 0xFFu).toFloat() / 255f
    val cg = ((textColor.packed shr 8) and 0xFFu).toFloat() / 255f
    val cb = ((textColor.packed shr 0) and 0xFFu).toFloat() / 255f
    val ca = ((textColor.packed shr 24) and 0xFFu).toFloat() / 255f
    uniformBytes.putFloat(cr)           // color.r
    uniformBytes.putFloat(cg)           // color.g
    uniformBytes.putFloat(cb)           // color.b
    uniformBytes.putFloat(ca)           // color.a

    drawTextAtlasPass(
        atlasRgba = gpuBlob.atlasRgba,
        atlasWidth = gpuBlob.atlasWidth,
        atlasHeight = gpuBlob.atlasHeight,
        atlasFormat = "a8unorm",
        vertexData = vertexData,
        indexData = indexData,
        draws = listOf(
            GPUBackendRawUniformDraw(
                uniformBytes = uniformBytes.array(),
                scissorX = 0,
                scissorY = 0,
                scissorWidth = tw.toInt(),
                scissorHeight = th.toInt(),
            ),
        ),
        blendMode = blendMode,
    )
    dispatched.add("text:${blob.hashCode()}")
}

private fun resolveTextColor(paint: org.graphiks.kanvas.paint.Paint): Color {
    val shader = paint.shader ?: return paint.color
    return extractSolidShaderColor(shader) ?: paint.color
}

private fun extractSolidShaderColor(shader: Shader): Color? = when (shader) {
    is Shader.SolidColor -> shader.color
    is Shader.WithLocalMatrix -> extractSolidShaderColor(shader.shader)
    is Shader.WithColorFilter -> extractSolidShaderColor(shader.shader)
    is Shader.WithWorkingColorSpace -> extractSolidShaderColor(shader.shader)
    is Shader.CoordClamp -> extractSolidShaderColor(shader.shader)
    else -> null
}

/** Extract the effective scale for text rasterization from a CTM. */
private fun ctmEffectiveScale(transform: Matrix33): Float {
    return maxOf(abs(transform.scaleX), abs(transform.scaleY), 1f)
}

/** Create a [TextBlob] with fontSize scaled by [scale] for higher-resolution rasterization. */
private fun TextBlob.scaledForRasterization(scale: Float): TextBlob {
    if (scale <= 1f || fontSize <= 0f) return this
    val efSize = maxOf(fontSize * scale, 1f)
    return copy(
        fontSize = efSize,
        glyphRuns = glyphRuns.map { it.copy(fontSize = efSize) },
    )
}

/**
 * Rescale glyphRects back to design-space dimensions.
 *
 * When glyphs are rasterized at a CTM-scaled font size, the resulting bitmap rects
 * are proportionally larger. This function reverses that so [drawTextAtlasPass]
 * produces correct screen-space quads when it applies the CTM to the vertices.
 */
internal fun GpuTextBlob.normalizeGlyphRects(scale: Float): GpuTextBlob {
    if (scale <= 1f) return this
    return copy(
        glyphRects = glyphRects.map {
            Rect.fromLTRB(it.left / scale, it.top / scale, it.right / scale, it.bottom / scale)
        },
    )
}
