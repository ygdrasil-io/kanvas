package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts as CoreGPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind as CoreGPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor as CoreMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point

class Canvas(private val surface: Surface) {
    private var commandCounter = 0
    private val source = GPUCommandSource(adapter = "kanvas-api", operation = "draw")

    private fun nextCommandId(): GPUDrawCommandID {
        val id = GPUDrawCommandID(commandCounter)
        commandCounter++
        return id
    }

    private fun lowerPaint(paint: Paint): Pair<CoreMaterialDescriptor, CoreGPUBlendFacts> {
        val material: CoreMaterialDescriptor = when (val shader = paint.shader) {
            null -> CoreMaterialDescriptor.SolidColor(r = paint.r, g = paint.g, b = paint.b, a = paint.a)
            is Shader.SolidColor -> CoreMaterialDescriptor.SolidColor(r = shader.r, g = shader.g, b = shader.b, a = shader.a)
            is Shader.LinearGradient -> {
                val s = shader
                val startColor = s.stops.firstOrNull() ?: Triple(0f, 0f, 0f)
                val endColor = s.stops.lastOrNull() ?: Triple(0f, 0f, 0f)
                CoreMaterialDescriptor.LinearGradient(
                    startX = s.start.x, startY = s.start.y,
                    endX = s.end.x, endY = s.end.y,
                    startR = startColor.first, startG = startColor.second, startB = startColor.third, startA = 1f,
                    endR = endColor.first, endG = endColor.second, endB = endColor.third, endA = 1f,
                )
            }
            is Shader.RadialGradient -> {
                val s = shader
                val startColor = s.stops.firstOrNull() ?: Triple(0f, 0f, 0f)
                val endColor = s.stops.lastOrNull() ?: Triple(0f, 0f, 0f)
                CoreMaterialDescriptor.RadialGradient(
                    centerX = s.center.x, centerY = s.center.y, radius = s.radius,
                    startR = startColor.first, startG = startColor.second, startB = startColor.third, startA = 1f,
                    endR = endColor.first, endG = endColor.second, endB = endColor.third, endA = 1f,
                )
            }
            is Shader.SweepGradient -> {
                val s = shader
                val startColor = s.stops.firstOrNull() ?: Triple(0f, 0f, 0f)
                val endColor = s.stops.lastOrNull() ?: Triple(0f, 0f, 0f)
                CoreMaterialDescriptor.SweepGradient(
                    centerX = s.center.x, centerY = s.center.y,
                    startAngle = s.startAngle, endAngle = s.endAngle,
                    startR = startColor.first, startG = startColor.second, startB = startColor.third, startA = 1f,
                    endR = endColor.first, endG = endColor.second, endB = endColor.third, endA = 1f,
                )
            }
            else -> CoreMaterialDescriptor.SolidColor(r = paint.r, g = paint.g, b = paint.b, a = paint.a)
        }
        val blend = blendFromMode(paint.blendMode)
        return Pair(material, blend)
    }

    private fun blendFromMode(mode: BlendMode): CoreGPUBlendFacts = when (mode) {
        BlendMode.SRC_OVER -> CoreGPUBlendFacts.srcOver()
        else -> CoreGPUBlendFacts.unsupported(modeLabel = mode.label)
    }

    fun drawRect(rect: Rect, paint: Paint) {
        val (material, blend) = lowerPaint(paint)
        val command = GPUFillRectCommandBuilder.build(
            commandId = nextCommandId(),
            rect = GPURect(rect.left, rect.top, rect.right, rect.bottom),
            target = surface.targetFacts,
            material = material,
            blend = blend,
            source = source,
            stroke = paint.style == PaintStyle.STROKE,
        )
        surface.recorder.record(command)
    }

    fun drawRRect(rect: Rect, radii: RRectCornerRadii, paint: Paint) {
        val (material, blend) = lowerPaint(paint)
        val command = GPUFillRRectCommandBuilder.build(
            commandId = nextCommandId(),
            rrect = GPURRect(
                rect = GPURect(rect.left, rect.top, rect.right, rect.bottom),
                topLeft = GPURRectCornerRadii(radii.x, radii.y),
                topRight = GPURRectCornerRadii(radii.x, radii.y),
                bottomRight = GPURRectCornerRadii(radii.x, radii.y),
                bottomLeft = GPURRectCornerRadii(radii.x, radii.y),
            ),
            target = surface.targetFacts,
            material = material,
            blend = blend,
            source = source,
            stroke = paint.style == PaintStyle.STROKE,
        )
        surface.recorder.record(command)
    }

    fun drawRRect(rrect: RRect, paint: Paint) {
        val (material, blend) = lowerPaint(paint)
        val command = GPUFillRRectCommandBuilder.build(
            commandId = nextCommandId(),
            rrect = GPURRect(
                rect = GPURect(rrect.rect.left, rrect.rect.top, rrect.rect.right, rrect.rect.bottom),
                topLeft = GPURRectCornerRadii(rrect.topLeft.x, rrect.topLeft.y),
                topRight = GPURRectCornerRadii(rrect.topRight.x, rrect.topRight.y),
                bottomRight = GPURRectCornerRadii(rrect.bottomRight.x, rrect.bottomRight.y),
                bottomLeft = GPURRectCornerRadii(rrect.bottomLeft.x, rrect.bottomLeft.y),
            ),
            target = surface.targetFacts,
            material = material,
            blend = blend,
            source = source,
            stroke = paint.style == PaintStyle.STROKE,
        )
        surface.recorder.record(command)
    }

    fun drawPath(path: Path, paint: Paint) {
        val (material, blend) = lowerPaint(paint)
        val pathDescriptor = path.lower()
        val pathData = path.toPathData()
        val tessellator = PathTessellator()
        val flattened = tessellator.flatten(pathData)
        val triangleResult = tessellator.triangulate(flattened)
        val vertices = triangleResult.vertices.flatMap { listOf(it.x, it.y) }

        val pathFacts = GPUPathFacts(
            pathKey = pathDescriptor.pathKey,
            verbCount = pathDescriptor.verbCount,
            pointCount = pathDescriptor.pointCount,
            fillRule = pathDescriptor.fillRule,
            inverseFill = pathDescriptor.inverseFill,
            finiteProof = pathDescriptor.finiteProof,
            volatility = pathDescriptor.volatility,
            transformClass = pathDescriptor.transformClass,
            edgeCount = pathDescriptor.edgeCount,
        )

        val command = GPUFillPathCommandBuilder.build(
            commandId = nextCommandId(),
            pathKey = pathDescriptor.pathKey,
            pathDescriptor = pathFacts,
            tessellatedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = pathDescriptor.edgeCount,
            target = surface.targetFacts,
            material = material,
            blend = blend,
            source = source,
            stroke = paint.style == PaintStyle.STROKE,
        )
        surface.recorder.record(command)
    }

    fun drawImage(image: Image, rect: Rect, paint: Paint? = null) {
        val effectivePaint = paint ?: Paint()
        val (_, blend) = lowerPaint(effectivePaint)
        // Use ImageDraw material so dispatchFillRect refuses (no silent solid-rect fallback)
        val command = GPUFillRectCommandBuilder.build(
            commandId = nextCommandId(),
            rect = GPURect(rect.left, rect.top, rect.right, rect.bottom),
            target = surface.targetFacts,
            material = CoreMaterialDescriptor.ImageDraw(
                imageSourceId = image.sourceId,
                imageWidth = image.width,
                imageHeight = image.height,
            ),
            blend = blend,
            source = source,
        )
        surface.recorder.record(command)
    }

    fun drawTextBlob(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        val (material, blend) = lowerPaint(paint)
        val loweredDescriptor = blob.lower()
        // Bake the absolute draw origin into each glyph so the command carries
        // device-space glyph positions (the typeface stays on TextBlob, out of the command).
        val glyphRunDescriptor = loweredDescriptor.copy(
            glyphs = loweredDescriptor.glyphs.map { it.copy(drawX = it.drawX + x, drawY = it.drawY + y) },
        )
        val command = NormalizedDrawCommand.DrawTextRun(
            commandId = nextCommandId(),
            textLayoutResultId = "kanvas-text-${System.identityHashCode(blob)}",
            glyphRunId = "glyph-run-${System.identityHashCode(blob)}",
            glyphRunDescriptorRefs = listOf("kanvas-glyph-run"),
            glyphRunDescriptor = glyphRunDescriptor,
            artifactRefs = emptyList(),
            artifactKeyHashes = emptyList(),
            atlasGenerationTokens = emptyList(),
            uploadDependencyFacts = emptyList(),
            routeDiagnostics = emptyList(),
            transform = GPUTransformFacts.identity(),
            clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 0f, 0f)),
            layer = GPULayerFacts.root(target = surface.targetFacts),
            material = material,
            blend = blend,
            bounds = computeTextRunBounds(glyphRunDescriptor, 0f, 0f),
            ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
            source = source,
        )
        surface.recorder.record(command)
    }

    private fun computeTextRunBounds(descriptor: GlyphRunDescriptor, originX: Float, originY: Float): GPUBounds {
        if (descriptor.glyphs.isEmpty()) return GPUBounds(originX, originY, originX, originY)
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (glyph in descriptor.glyphs) {
            val glyphX = originX + glyph.drawX
            val glyphY = originY + glyph.drawY
            val width = glyph.placement.region.width.toFloat()
            val height = glyph.placement.region.height.toFloat()
            left = minOf(left, glyphX)
            top = minOf(top, glyphY)
            right = maxOf(right, glyphX + width)
            bottom = maxOf(bottom, glyphY + height)
        }
        return GPUBounds(left, top, right, bottom)
    }
}
