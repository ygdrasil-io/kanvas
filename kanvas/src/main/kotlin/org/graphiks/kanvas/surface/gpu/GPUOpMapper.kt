package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.types.Matrix33

internal fun DisplayOp.DrawRect.toNormalizedCommand(
    cmdId: GPUDrawCommandID,
    target: GPUTargetFacts,
): NormalizedDrawCommand.FillRect {
    val paint = this.paint
    val material = paint.toMaterial()
    val gpRect = GPURect(this.rect.left, this.rect.top, this.rect.right, this.rect.bottom)
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(bounds)
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
    val clip = this.clip.toGPUClipFacts(bounds)
    val transform = this.transform.toGPUTransformFacts()
    return NormalizedDrawCommand.FillPath(
        commandId = cmdId,
        pathKey = "path-${cmdId.value}",
        pathDescriptor = GPUPathFacts(
            pathKey = "path-${cmdId.value}",
            verbCount = 0,
            pointCount = tessellatedVertices.size / 2,
            fillRule = "winding",
            inverseFill = false,
            finiteProof = "all_finite",
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
        antiAlias = paint.antiAlias,
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
    val gpRRect = GPURRect(gpRect, this.rrect.topLeft.x, this.rrect.topLeft.y)
    val bounds = GPUBounds(gpRect.left, gpRect.top, gpRect.right, gpRect.bottom)
    val clip = this.clip.toGPUClipFacts(bounds)
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
    )
}

internal fun ClipStack.toGPUClipFacts(bounds: GPUBounds): GPUClipFacts = when (this) {
    ClipStack.WideOpen -> GPUClipFacts.wideOpen(bounds)
    is ClipStack.DeviceRect -> GPUClipFacts.deviceRect(bounds)
    is ClipStack.Complex -> GPUClipFacts.complexStack(bounds)
}

internal fun Matrix33.toGPUTransformFacts(): GPUTransformFacts {
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
