package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.isAffine
import org.graphiks.kanvas.types.isAxisAlignedAffine
import org.graphiks.kanvas.types.mapAxisAligned
import org.graphiks.kanvas.types.mapAxisAlignedRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

internal fun DisplayOp.DrawRect.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val paint = this.paint
    val material = paint.toMaterial()
    val gpRect = GPURect(this.rect.left, this.rect.top, this.rect.right, this.rect.bottom)
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRect"),
        stroke = paint.isStroke(),
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun DisplayOp.DrawPath.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
    tessellatedVertices: List<Float>,
    contourStarts: List<Int>,
    edgeCount: Int,
): NormalizedDrawCommand.FillPath {
    val paint = this.paint
    val material = paint.toMaterial()
    val bounds = computeBounds(tessellatedVertices)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    val maskFilter = paint.maskFilter.toNormalizedMaskFilter()
    val pathStencilConfig = stencilConfig(path.fillType)
    return NormalizedDrawCommand.FillPath(
        commandId = cmdId,
        pathKey = "path-${cmdId.value}",
        pathDescriptor = GPUPathFacts(
            pathKey = "path-${cmdId.value}",
            verbCount = 0,
            pointCount = tessellatedVertices.size / 2,
            fillRule = pathStencilConfig.fillRule.name,
            inverseFill = pathStencilConfig.inverse,
            finiteProof = if (tessellatedVertices.all(Float::isFinite)) "all_finite" else "non_finite",
            volatility = "static",
            transformClass = "identity",
            edgeCount = edgeCount,
        ),
        tessellatedVertices = tessellatedVertices,
        contourStarts = contourStarts,
        totalVertexCount = tessellatedVertices.size / 2,
        edgeCount = edgeCount,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawPath"),
        stroke = paint.isStroke(),
        strokeWidth = paint.strokeWidth,
        dashIntervals = (paint.pathEffect as? PathEffect.Dash)?.intervals,
        dashPhase = (paint.pathEffect as? PathEffect.Dash)?.phase ?: 0f,
        strokeCap = paint.strokeCap.name.lowercase(),
        strokeJoin = paint.strokeJoin.name.lowercase(),
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = maskFilter,
    )
}

/**
 * Converts a stroke-style [DisplayOp.DrawRect] into a [NormalizedDrawCommand.FillPath]
 * so the stroke can be dispatched through the tessellated-path pipeline.
 *
 * Generates a closed contour from the 4 rect corners and copies the paint's
 * stroke parameters (width, cap, join, dash) directly onto the path command.
 * Returns a fill-path command with [FillPath.stroke] set to `true`.
 */
internal fun DisplayOp.DrawRect.toStrokePathCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillPath {
    val r = this.rect
    val vertices = listOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)
    val edges = 4
    val bounds = computeBounds(vertices)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    val paint = this.paint
    return NormalizedDrawCommand.FillPath(
        commandId = cmdId,
        pathKey = "rect-stroke-${cmdId.value}",
        pathDescriptor = GPUPathFacts(
            pathKey = "rect-stroke-${cmdId.value}",
            verbCount = edges,
            pointCount = edges,
            fillRule = "winding",
            inverseFill = false,
            finiteProof = "all_finite",
            volatility = "static",
            transformClass = transform.type.name.lowercase(),
            edgeCount = edges,
        ),
        tessellatedVertices = vertices,
        contourStarts = listOf(0),
        totalVertexCount = edges,
        edgeCount = edges,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = paint.toMaterial(),
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRect.stroke"),
        stroke = true,
        strokeWidth = paint.strokeWidth,
        dashIntervals = (paint.pathEffect as? PathEffect.Dash)?.intervals,
        dashPhase = (paint.pathEffect as? PathEffect.Dash)?.phase ?: 0f,
        strokeCap = paint.strokeCap.name.lowercase(),
        strokeJoin = paint.strokeJoin.name.lowercase(),
        antiAlias = paint.antiAlias,
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun DisplayOp.DrawRRect.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRRect {
    val paint = this.paint
    val material = paint.toMaterial()
    val gpRect = GPURect(
        this.rrect.rect.left, this.rrect.rect.top,
        this.rrect.rect.right, this.rrect.rect.bottom,
    )
    val gpRRect = GPURRect(
        gpRect,
        topLeft = GPURRectCornerRadii(this.rrect.topLeft.x, this.rrect.topLeft.y),
        topRight = GPURRectCornerRadii(this.rrect.topRight.x, this.rrect.topRight.y),
        bottomRight = GPURRectCornerRadii(this.rrect.bottomRight.x, this.rrect.bottomRight.y),
        bottomLeft = GPURRectCornerRadii(this.rrect.bottomLeft.x, this.rrect.bottomLeft.y),
    )
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRRect(
        commandId = cmdId,
        rrect = gpRRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(
            paintOrder = 0,
            dependsOnDestination = false,
            requiresBarrier = false,
        ),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawRRect"),
        stroke = paint.isStroke(),
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
        maskFilter = paint.maskFilter.toNormalizedMaskFilter(),
    )
}

internal fun BlendMode.toGpuBlendFacts(): GPUBlendFacts {
    val mode = when (this) {
        BlendMode.CLEAR -> GPUBlendMode.CLEAR
        BlendMode.SRC_OVER -> GPUBlendMode.SRC_OVER
        BlendMode.SRC -> GPUBlendMode.SRC
        BlendMode.DST -> GPUBlendMode.DST
        BlendMode.DST_OVER -> GPUBlendMode.DST_OVER
        BlendMode.SRC_IN -> GPUBlendMode.SRC_IN
        BlendMode.DST_IN -> GPUBlendMode.DST_IN
        BlendMode.SRC_OUT -> GPUBlendMode.SRC_OUT
        BlendMode.DST_OUT -> GPUBlendMode.DST_OUT
        BlendMode.SRC_ATOP -> GPUBlendMode.SRC_ATOP
        BlendMode.DST_ATOP -> GPUBlendMode.DST_ATOP
        BlendMode.XOR -> GPUBlendMode.XOR
        BlendMode.PLUS -> GPUBlendMode.PLUS
        BlendMode.MODULATE -> GPUBlendMode.MODULATE
        BlendMode.MULTIPLY -> GPUBlendMode.MULTIPLY
        BlendMode.SCREEN -> GPUBlendMode.SCREEN
        BlendMode.OVERLAY -> GPUBlendMode.OVERLAY
        BlendMode.DARKEN -> GPUBlendMode.DARKEN
        BlendMode.LIGHTEN -> GPUBlendMode.LIGHTEN
        BlendMode.COLOR_DODGE -> GPUBlendMode.COLOR_DODGE
        BlendMode.COLOR_BURN -> GPUBlendMode.COLOR_BURN
        BlendMode.HARD_LIGHT -> GPUBlendMode.HARD_LIGHT
        BlendMode.SOFT_LIGHT -> GPUBlendMode.SOFT_LIGHT
        BlendMode.DIFFERENCE -> GPUBlendMode.DIFFERENCE
        BlendMode.EXCLUSION -> GPUBlendMode.EXCLUSION
        BlendMode.HUE -> GPUBlendMode.HUE
        BlendMode.SATURATION -> GPUBlendMode.SATURATION
        BlendMode.COLOR -> GPUBlendMode.COLOR
        BlendMode.LUMINOSITY -> GPUBlendMode.LUMINOSITY
    }
    return GPUBlendFacts(
        kind = GPUBlendKind.Custom,
        modeLabel = mode.gpuLabel,
        requiresDestinationRead = mode.requiresDestinationRead,
        blendMode = mode,
    )
}

internal fun Matrix33.toGPUTransformFacts(): GPUTransformFacts {
    if (!isAffine()) return GPUTransformFacts.perspective()
    if (this == Matrix33.identity()) return GPUTransformFacts.identity()
    return GPUTransformFacts.affine(
        scaleX = this.scaleX,
        skewX = this.skewX,
        skewY = this.skewY,
        scaleY = this.scaleY,
        translateX = this.transX,
        translateY = this.transY,
    )
}

internal fun MaskFilter?.toNormalizedMaskFilter(): NormalizedMaskFilter? = when (this) {
    is MaskFilter.Blur -> NormalizedMaskFilter.Blur(
        style = style.toNormalizedBlurStyle(),
        sigma = sigma,
    )
    is MaskFilter.Shader -> null
    is MaskFilter.Table -> null
    null -> null
}

internal fun BlurStyle.toNormalizedBlurStyle(): NormalizedBlurStyle = when (this) {
    BlurStyle.NORMAL -> NormalizedBlurStyle.NORMAL
    BlurStyle.SOLID -> NormalizedBlurStyle.SOLID
    BlurStyle.OUTER -> NormalizedBlurStyle.OUTER
    BlurStyle.INNER -> NormalizedBlurStyle.INNER
}

internal fun computeBounds(flatVertices: List<Float>): GPUBounds {
    if (flatVertices.isEmpty()) return GPUBounds(0f, 0f, 0f, 0f)
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
    for (i in flatVertices.indices step 2) {
        val x = flatVertices[i]; val y = flatVertices[i + 1]
        if (x < minX) minX = x; if (y < minY) minY = y
        if (x > maxX) maxX = x; if (y > maxY) maxY = y
    }
    return GPUBounds(minX, minY, maxX, maxY)
}

// ────────────────────────────────────────────────────────────────────────────
// DrawColor / Clear — full‑surface rect fills
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawColor.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val w = target.width.toFloat()
    val h = target.height.toFloat()
    val gpRect = GPURect(0f, 0f, w, h)
    val bounds = GPUBounds(0f, 0f, w, h)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = GPUMaterialDescriptor.SolidColor(
            r = this.color.r, g = this.color.g, b = this.color.b, a = this.color.a,
        ),
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawColor"),
        stroke = false,
        antiAlias = false,
        blend = this.mode.toGpuBlendFacts(),
    )
}

internal fun DisplayOp.Clear.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val w = target.width.toFloat()
    val h = target.height.toFloat()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = GPURect(0f, 0f, w, h),
        transform = GPUTransformFacts.identity(),
        clip = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, w, h)),
        layer = GPULayerFacts.root(target),
        material = GPUMaterialDescriptor.SolidColor(
            r = this.color.r, g = this.color.g, b = this.color.b, a = this.color.a,
        ),
        bounds = GPUBounds(0f, 0f, w, h),
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "clear"),
        stroke = false,
        antiAlias = false,
        blend = BlendMode.SRC.toGpuBlendFacts(),
    )
}

// ────────────────────────────────────────────────────────────────────────────
// DrawPoint — single pixel as 1×1 rect fill
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawPoint.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val paint = this.paint
    val gpRect = GPURect(this.x, this.y, this.x + 1f, this.y + 1f)
    val bounds = GPUBounds(this.x, this.y, this.x + 1f, this.y + 1f)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillRect(
        commandId = cmdId,
        rect = gpRect,
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = paint.toMaterial(),
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawPoint"),
        stroke = false,
        antiAlias = paint.antiAlias,
        blend = paint.blendMode.toGpuBlendFacts(),
    )
}

// ────────────────────────────────────────────────────────────────────────────
// DrawPoints — build a Path from the point list and the point mode.
// POINTS  → tiny rects for each point
// LINES   → moveTo/lineTo pairs
// POLYGON → closed polygon
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawPoints.toPath(): Path = when (this.mode) {
    PointMode.POINTS -> Path().also { path ->
        for (pt in this.points) {
            path.addRect(Rect.fromLTRB(pt.x, pt.y, pt.x + 1f, pt.y + 1f))
        }
    }
    PointMode.LINES -> Path().also { path ->
        var i = 0
        while (i + 1 < this.points.size) {
            path.moveTo(this.points[i].x, this.points[i].y)
            path.lineTo(this.points[i + 1].x, this.points[i + 1].y)
            i += 2
        }
    }
    PointMode.POLYGON -> Path().also { path ->
        if (this.points.isEmpty()) return@also
        path.moveTo(this.points[0].x, this.points[0].y)
        for (i in 1 until this.points.size) {
            path.lineTo(this.points[i].x, this.points[i].y)
        }
        path.close()
    }
}

// ────────────────────────────────────────────────────────────────────────────
// DrawDRRect — outer RRect contour (CW) + inner RRect contour (CCW) for hole
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawDRRect.toPath(): Path {
    val path = Path()
    path.addRRect(this.outer)
    // Inner contour: reverse the inner RRect path to produce CCW winding,
    // which punches a hole under non-zero winding fill.
    val innerPath = Path().addRRect(this.inner)
    path.reverseAddPath(innerPath)
    return path
}

// ────────────────────────────────────────────────────────────────────────────
// DrawImage → NormalizedDrawCommand.DrawImageRect
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawImage.toImageRectCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.DrawImageRect {
    val image = this.image
    val samplingFilterMode = this.paint?.let { p ->
        val sh = p.shader
        if (sh is org.graphiks.kanvas.paint.Shader.Image) {
            when (sh.sampling) {
                org.graphiks.kanvas.paint.SamplingOptions.NEAREST -> "nearest"
                org.graphiks.kanvas.paint.SamplingOptions.LINEAR -> "linear"
                is org.graphiks.kanvas.paint.SamplingOptions.Cubic -> "linear"
            }
        } else null
    } ?: "linear"
    val material = GPUMaterialDescriptor.ImageDraw(
        imageSourceId = image.sourceId,
        imageWidth = image.width,
        imageHeight = image.height,
        samplingFilterMode = samplingFilterMode,
        alphaOnly = image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8,
        tintR = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.r ?: 0f else 1f,
        tintG = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.g ?: 0f else 1f,
        tintB = if (image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8) this.paint?.color?.b ?: 0f else 1f,
        tintA = this.paint?.color?.a ?: 1f,
    )
    val src = GPURect(this.src.left, this.src.top, this.src.right, this.src.bottom)
    val dst = GPURect(this.dst.left, this.dst.top, this.dst.right, this.dst.bottom)
    val bounds = GPUBounds(dst.left, dst.top, dst.right, dst.bottom)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.DrawImageRect(
        commandId = cmdId,
        imageSourceId = image.sourceId,
        src = src,
        dst = dst,
        imageFilterPlan = toImageFilterPlan(transform, clip, target, dst),
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawImage"),
        blend = (this.paint?.blendMode ?: BlendMode.SRC_OVER).toGpuBlendFacts(),
        samplingFilterMode = when (val mat = material) {
            is GPUMaterialDescriptor.ImageDraw -> mat.samplingFilterMode
        },
        pixelsWidth = image.width,
        pixelsHeight = image.height,
        pixelsFormat = "RGBA8Unorm",
        pixelsAlphaType = "Premul",
    )
}

private fun DisplayOp.DrawImage.toImageFilterPlan(
    transform: GPUTransformFacts,
    clip: GPUClipFacts,
    target: GPUTargetFacts,
    dst: GPURect,
): GPUImageFilterPlan {
    val paint = paint ?: return GPUImageFilterPlan.None
    if (paint.maskFilter != null) return GPUImageFilterPlan.Refused("unsupported.mask-filter.image")
    val imageFilter = paint.imageFilter ?: return GPUImageFilterPlan.None

    val blur = imageFilter as? ImageFilter.Blur
        ?: return GPUImageFilterPlan.Refused("unsupported.image-filter.image.kind")
    if (blur.input != null) return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.input")
    if (blur.tileMode != TileMode.CLAMP) return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.tile-mode")
    if (
        !blur.sigmaX.isFinite() ||
        !blur.sigmaY.isFinite() ||
        blur.sigmaX < 0f ||
        blur.sigmaY < 0f ||
        blur.sigmaX > 12f ||
        blur.sigmaY > 12f
    ) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.sigma")
    }
    if (blur.sigmaX == 0f && blur.sigmaY == 0f) return GPUImageFilterPlan.Identity

    val haloX = kotlin.math.ceil(3f * blur.sigmaX).toInt()
    val haloY = kotlin.math.ceil(3f * blur.sigmaY).toInt()
    val targetBounds = GPURect(0f, 0f, target.width.toFloat(), target.height.toFloat())
    val clipBounds = when (clip.kind) {
        GPUClipKind.WideOpen -> targetBounds
        GPUClipKind.DeviceRect -> intersect(clip.bounds.toRect(), targetBounds)
        // A complex clip is applied once at the shared source-to-scene composite.
        // The filter source must therefore retain its full target-space halo.
        GPUClipKind.ComplexStack -> targetBounds
    }
    val outputBounds = intersect(
        GPURect(
            left = dst.left - haloX,
            top = dst.top - haloY,
            right = dst.right + haloX,
            bottom = dst.bottom + haloY,
        ),
        clipBounds,
    )
    val outputWidth = outputBounds.right - outputBounds.left
    val outputHeight = outputBounds.bottom - outputBounds.top
    if (
        outputWidth <= 0f || outputHeight <= 0f ||
        outputWidth > 2048f || outputHeight > 2048f
    ) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.intermediate-size")
    }
    if (transform.type != GPUTransformType.Identity) {
        return GPUImageFilterPlan.Refused("unsupported.image-filter.blur.transform")
    }
    return GPUImageFilterPlan.Blur(
        sigmaX = blur.sigmaX,
        sigmaY = blur.sigmaY,
        haloX = haloX,
        haloY = haloY,
        outputBounds = outputBounds,
    )
}

private fun GPUBounds.toRect(): GPURect = GPURect(left, top, right, bottom)

private fun intersect(first: GPURect, second: GPURect): GPURect = GPURect(
    left = maxOf(first.left, second.left),
    top = maxOf(first.top, second.top),
    right = minOf(first.right, second.right),
    bottom = minOf(first.bottom, second.bottom),
)

// ────────────────────────────────────────────────────────────────────────────
// DrawImageNine — decompose into 9 cells (src / dst pairs)
// ────────────────────────────────────────────────────────────────────────────

internal data class ImageCell(
    val src: Rect,
    val dst: Rect,
    val color: Color? = null,
)

internal fun DisplayOp.DrawImageNine.decompose(): List<ImageCell> {
    val iw = this.image.width.toFloat()
    val ih = this.image.height.toFloat()
    val c = this.center
    val d = this.dst
    val cells = mutableListOf<ImageCell>()

    // Column boundaries (source)
    val srcL = listOf(0f, c.left, c.right, iw)
    // Row boundaries (source)
    val srcT = listOf(0f, c.top, c.bottom, ih)
    // Column boundaries (destination)
    val dstL = listOf(
        d.left,
        d.left + c.left,
        d.right - (iw - c.right),
        d.right,
    )
    // Row boundaries (destination)
    val dstT = listOf(
        d.top,
        d.top + c.top,
        d.bottom - (ih - c.bottom),
        d.bottom,
    )

    for (row in 0 until 3) {
        for (col in 0 until 3) {
            val src = Rect.fromLTRB(srcL[col], srcT[row], srcL[col + 1], srcT[row + 1])
            val dst = Rect.fromLTRB(dstL[col], dstT[row], dstL[col + 1], dstT[row + 1])
            if (!src.isEmpty && !dst.isEmpty) {
                cells.add(ImageCell(src = src, dst = dst))
            }
        }
    }
    return cells
}

// ────────────────────────────────────────────────────────────────────────────
// DrawImageLattice — decompose into (xDivs+1)×(yDivs+1) cells
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawImageLattice.decompose(): List<ImageCell> {
    val iw = this.image.width.toFloat()
    val ih = this.image.height.toFloat()
    val lat = this.lattice
    val d = this.dst

    // Column boundaries from xDivs
    val cols = mutableListOf(0f)
    for (xv in lat.xDivs) cols.add(xv.toFloat())
    cols.add(iw)
    // Row boundaries from yDivs
    val rows = mutableListOf(0f)
    for (yv in lat.yDivs) rows.add(yv.toFloat())
    rows.add(ih)

    val numCols = cols.size - 1
    val numRows = rows.size - 1
    val cells = mutableListOf<ImageCell>()
    var cellIndex = 0

    for (r in 0 until numRows) {
        for (c in 0 until numCols) {
            val srcLeft = cols[c]
            val srcTop = rows[r]
            val srcRight = cols[c + 1]
            val srcBottom = rows[r + 1]

            val dstRect = if (lat.rects != null && cellIndex < lat.rects.size) {
                lat.rects[cellIndex]
            } else {
                // Proportional stretch to fill dst
                Rect.fromLTRB(
                    d.left + (srcLeft / iw) * d.width,
                    d.top + (srcTop / ih) * d.height,
                    d.left + (srcRight / iw) * d.width,
                    d.top + (srcBottom / ih) * d.height,
                )
            }

            val color = lat.colors?.getOrNull(cellIndex)
            cells.add(ImageCell(
                src = Rect.fromLTRB(srcLeft, srcTop, srcRight, srcBottom),
                dst = dstRect,
                color = color,
            ))
            cellIndex++
        }
    }
    return cells
}

// ────────────────────────────────────────────────────────────────────────────
// DisplayOp.withCombinedTransform — concatenate an outer transform into every
// drawing op that carries a transform field. Used for DrawPicture expansion.
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.withCombinedTransform(outer: Matrix33): DisplayOp = when (this) {
    is DisplayOp.DrawRect -> copy(transform = outer * transform)
    is DisplayOp.DrawRRect -> copy(transform = outer * transform)
    is DisplayOp.DrawPath -> copy(transform = outer * transform)
    is DisplayOp.DrawImage -> copy(transform = outer * transform)
    is DisplayOp.DrawText -> copy(transform = outer * transform)
    is DisplayOp.DrawColor -> copy(transform = outer * transform)
    is DisplayOp.DrawPoint -> copy(transform = outer * transform)
    is DisplayOp.DrawPoints -> copy(transform = outer * transform)
    is DisplayOp.DrawDRRect -> copy(transform = outer * transform)
    is DisplayOp.DrawImageNine -> copy(transform = outer * transform)
    is DisplayOp.DrawImageLattice -> copy(transform = outer * transform)
    is DisplayOp.DrawPicture -> copy(transform = outer * transform)
    is DisplayOp.DrawVertices -> copy(transform = outer * transform)
    is DisplayOp.DrawMesh -> copy(transform = outer * transform)
    is DisplayOp.DrawAtlas -> copy(transform = outer * transform)
    is DisplayOp.BeginLayer -> copy(transform = outer * transform)
    is DisplayOp.Clear,
    is DisplayOp.SetTransform,
    is DisplayOp.SetClip,
    DisplayOp.EndLayer,
    is DisplayOp.Annotation,
    is DisplayOp.FlushAndSnapshot -> this
}

/**
 * Replays an operation captured in a [Picture] under an outer picture transform.
 *
 * Display-list clips are already in the picture's device space at capture time, so they
 * must be transformed independently from the operation transform and then intersected
 * with the clip captured by each enclosing DrawPicture. This keeps a Picture child on the
 * same clip/S/G route it would have used if it had been recorded directly on the canvas.
 */
internal fun DisplayOp.withPictureReplayState(
    outerTransform: Matrix33,
    enclosingClip: ClipStack,
): DisplayOp {
    val replayClip = enclosingClip.intersectForPictureReplay(clipForPictureReplay(this)?.transformForPictureReplay(outerTransform))
    return when (val transformed = withCombinedTransform(outerTransform)) {
        is DisplayOp.DrawRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawRRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPath -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawImage -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawText -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawColor -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPoint -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPoints -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawDRRect -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawImageNine -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawImageLattice -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawPicture -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawVertices -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawMesh -> transformed.copy(clip = replayClip)
        is DisplayOp.DrawAtlas -> transformed.copy(clip = replayClip)
        else -> transformed
    }
}

/** Expands supported Pictures before clip-use accounting so every child gets its own S/G route. */
internal fun Iterable<DisplayOp>.expandPicturesForGpuReplay(): List<DisplayOp> {
    val expanded = mutableListOf<DisplayOp>()

    fun expandPicture(
        picture: org.graphiks.kanvas.picture.Picture,
        outerTransform: Matrix33,
        enclosingClip: ClipStack,
    ) {
        for (nested in picture.ops) {
            if (nested is DisplayOp.DrawPicture) {
                // Retain an explicitly unsupported Picture as one operation so its existing
                // preflight refusal remains atomic: no preceding picture child is encoded.
                if (nested.coreRoutePreflightRefusalReason() != null) {
                    expanded += nested.withPictureReplayState(outerTransform, enclosingClip)
                } else {
                    val nestedClip = enclosingClip.intersectForPictureReplay(
                        nested.clip.transformForPictureReplay(outerTransform),
                    )
                    expandPicture(nested.picture, outerTransform * nested.transform, nestedClip)
                }
            } else {
                expanded += nested.withPictureReplayState(outerTransform, enclosingClip)
            }
        }
    }

    for (operation in this) {
        if (operation is DisplayOp.DrawPicture && operation.coreRoutePreflightRefusalReason() == null) {
            expandPicture(operation.picture, operation.transform, operation.clip)
        } else {
            expanded += operation
        }
    }
    return expanded
}

private fun clipForPictureReplay(operation: DisplayOp): ClipStack? = when (operation) {
    is DisplayOp.DrawRect -> operation.clip
    is DisplayOp.DrawRRect -> operation.clip
    is DisplayOp.DrawPath -> operation.clip
    is DisplayOp.DrawImage -> operation.clip
    is DisplayOp.DrawText -> operation.clip
    is DisplayOp.DrawColor -> operation.clip
    is DisplayOp.DrawPoint -> operation.clip
    is DisplayOp.DrawPoints -> operation.clip
    is DisplayOp.DrawDRRect -> operation.clip
    is DisplayOp.DrawImageNine -> operation.clip
    is DisplayOp.DrawImageLattice -> operation.clip
    is DisplayOp.DrawPicture -> operation.clip
    is DisplayOp.DrawVertices -> operation.clip
    is DisplayOp.DrawMesh -> operation.clip
    is DisplayOp.DrawAtlas -> operation.clip
    else -> null
}

private fun ClipStack?.transformForPictureReplay(matrix: Matrix33): ClipStack? = this?.let { clip ->
    when (clip) {
        ClipStack.WideOpen -> ClipStack.WideOpen
        is ClipStack.DeviceRect -> clip.rectForPictureReplay(matrix, clip.antiAlias)
        is ClipStack.Complex -> clip.collapsedIntersectingRectOrNull()?.let {
            it.rectForPictureReplay(matrix, it.antiAlias)
        } ?: ClipStack.Complex(clip.ops.map { it.transformForPictureReplay(matrix) })
    }
}

/** The recorder's cull rect plus nested rectangular intersects remain a device rect. */
private fun ClipStack.Complex.collapsedIntersectingRectOrNull(): ClipStack.DeviceRect? {
    val rectOps = ops.map { it as? ClipStackOp.RectOp ?: return null }
    if (rectOps.any { it.op != org.graphiks.kanvas.pipeline.ClipOp.INTERSECT }) return null
    val intersection = rectOps.fold<ClipStackOp.RectOp, Rect?>(null) { current, op ->
        val rect = op.rect
        current?.let {
            Rect.fromLTRB(
                maxOf(it.left, rect.left),
                maxOf(it.top, rect.top),
                minOf(it.right, rect.right),
                minOf(it.bottom, rect.bottom),
            )
        } ?: rect
    } ?: return null
    return ClipStack.DeviceRect(intersection, antiAlias = rectOps.any { it.antiAlias })
}

private fun ClipStack.DeviceRect.rectForPictureReplay(matrix: Matrix33, antiAlias: Boolean): ClipStack = when {
    matrix.isAxisAlignedAffine() -> ClipStack.DeviceRect(matrix.mapAxisAlignedRect(rect), antiAlias)
    matrix.isAffine() -> ClipStack.Complex(
        listOf(ClipStackOp.PathOp(Path().addRect(rect).transform(matrix), org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias)),
    )
    else -> ClipStack.Complex(
        listOf(ClipStackOp.PathOp(Path().addRect(rect), org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias, perspectiveCaptureRefusal = true)),
    )
}

private fun ClipStackOp.transformForPictureReplay(matrix: Matrix33): ClipStackOp = when (this) {
    is ClipStackOp.RectOp -> when {
        matrix.isAxisAlignedAffine() -> copy(rect = matrix.mapAxisAlignedRect(rect))
        matrix.isAffine() -> ClipStackOp.PathOp(Path().addRect(rect).transform(matrix), op, antiAlias, perspectiveCaptureRefusal)
        else -> ClipStackOp.PathOp(Path().addRect(rect), op, antiAlias, perspectiveCaptureRefusal = true)
    }
    is ClipStackOp.RRectOp -> when {
        matrix.isAxisAlignedAffine() -> copy(rrect = rrect.mapAxisAligned(matrix))
        matrix.isAffine() -> ClipStackOp.PathOp(Path().addRRect(rrect).transform(matrix), op, antiAlias, perspectiveCaptureRefusal)
        else -> ClipStackOp.PathOp(Path().addRRect(rrect), op, antiAlias, perspectiveCaptureRefusal = true)
    }
    is ClipStackOp.PathOp -> copy(
        path = if (matrix.isAffine()) path.transform(matrix) else path,
        perspectiveCaptureRefusal = perspectiveCaptureRefusal || !matrix.isAffine(),
    )
}

private fun ClipStack.intersectForPictureReplay(other: ClipStack?): ClipStack = when (other) {
    null,
    ClipStack.WideOpen -> this
    else -> when (this) {
        ClipStack.WideOpen -> other
        is ClipStack.DeviceRect -> when (other) {
            is ClipStack.DeviceRect -> ClipStack.DeviceRect(
                Rect.fromLTRB(
                    maxOf(rect.left, other.rect.left),
                    maxOf(rect.top, other.rect.top),
                    minOf(rect.right, other.rect.right),
                    minOf(rect.bottom, other.rect.bottom),
                ),
                antiAlias || other.antiAlias,
            )
            is ClipStack.Complex -> ClipStack.Complex(
                listOf(ClipStackOp.RectOp(rect, org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias)) + other.ops,
            )
            ClipStack.WideOpen -> this
        }
        is ClipStack.Complex -> ClipStack.Complex(ops + other.asPictureReplayOps())
    }
}

private fun ClipStack.asPictureReplayOps(): List<ClipStackOp> = when (this) {
    ClipStack.WideOpen -> emptyList()
    is ClipStack.DeviceRect -> listOf(ClipStackOp.RectOp(rect, org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias))
    is ClipStack.Complex -> ops
}

// ────────────────────────────────────────────────────────────────────────────
// DrawText → NormalizedDrawCommand.DrawTextRun
// ────────────────────────────────────────────────────────────────────────────

internal fun DisplayOp.DrawText.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.DrawTextRun {
    val material = this.paint.toMaterial()
    val bounds = GPUBounds(this.x, this.y, this.x + this.blob.fontSize * 10f, this.y + this.blob.fontSize)
    val clip = this.clip.toGPUClipFacts(target)
    val transform = this.transform.toGPUTransformFacts()
    val blobId = "textblob-${this.blob.hashCode()}"
    return NormalizedDrawCommand.DrawTextRun(
        commandId = cmdId,
        textLayoutResultId = blobId,
        glyphRunId = blobId,
        glyphRunDescriptorRefs = emptyList(),
        glyphRunDescriptor = null,
        colorGlyphPlans = emptyList(),
        artifactRefs = emptyList(),
        artifactKeyHashes = emptyList(),
        atlasGenerationTokens = emptyList(),
        uploadDependencyFacts = emptyList(),
        routeDiagnostics = emptyList(),
        transform = transform,
        clip = clip,
        layer = GPULayerFacts.root(target),
        material = material,
        blend = this.paint.blendMode.toGpuBlendFacts(),
        bounds = bounds,
        ordering = GPUOrderingFacts(paintOrder = 0, dependsOnDestination = false, requiresBarrier = false),
        source = GPUCommandSource(adapter = "kanvas-surface", operation = "drawText"),
    )
}
