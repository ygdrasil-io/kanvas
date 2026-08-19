package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance

/** Sole Surface authority for materializing exact integral scissor bounds. */
internal fun GPUClipCoveragePlan.toPreparedScissorBounds(
    targetBounds: GPUPixelBounds,
    nonScissorClipRetainedSeparately: Boolean = false,
): GPUPixelBounds? = when (this) {
    is GPUClipCoveragePlan.Refused -> null
    is GPUClipCoveragePlan.Scissor -> GPUPixelBounds(
        left = floor(bounds.left).toInt().coerceIn(targetBounds.left, targetBounds.right),
        top = floor(bounds.top).toInt().coerceIn(targetBounds.top, targetBounds.bottom),
        right = ceil(bounds.right).toInt().coerceIn(targetBounds.left, targetBounds.right),
        bottom = ceil(bounds.bottom).toInt().coerceIn(targetBounds.top, targetBounds.bottom),
    ).takeUnless(GPUPixelBounds::isEmpty)
    GPUClipCoveragePlan.NoClip -> targetBounds
    else -> targetBounds.takeIf { nonScissorClipRetainedSeparately }
}

internal fun List<GPUTextA8Instance>.preparedTextBounds(
    target: GPUTargetFacts,
): GPUBounds? {
    val coordinates = flatMap(GPUTextA8Instance::deviceQuad)
    if (coordinates.size != size * 8 || coordinates.any { coordinate -> !coordinate.isFinite() }) return null
    val xs = coordinates.filterIndexed { index, _ -> index % 2 == 0 }
    val ys = coordinates.filterIndexed { index, _ -> index % 2 == 1 }
    val left = xs.minOrNull()?.coerceIn(0f, target.width.toFloat()) ?: return null
    val top = ys.minOrNull()?.coerceIn(0f, target.height.toFloat()) ?: return null
    val right = xs.maxOrNull()?.coerceIn(0f, target.width.toFloat()) ?: return null
    val bottom = ys.maxOrNull()?.coerceIn(0f, target.height.toFloat()) ?: return null
    return GPUBounds(left, top, right, bottom).takeIf { left < right && top < bottom }
}

internal fun GPUTextA8Instance.preparedTextPixelBounds(
    target: GPUPixelBounds,
): GPUPixelBounds? {
    if (deviceQuad.size != 8 || deviceQuad.any { coordinate -> !coordinate.isFinite() }) return null
    val xs = deviceQuad.filterIndexed { index, _ -> index % 2 == 0 }
    val ys = deviceQuad.filterIndexed { index, _ -> index % 2 == 1 }
    return GPUPixelBounds(
        left = floor(xs.min()).toInt().coerceIn(target.left, target.right),
        top = floor(ys.min()).toInt().coerceIn(target.top, target.bottom),
        right = ceil(xs.max()).toInt().coerceIn(target.left, target.right),
        bottom = ceil(ys.max()).toInt().coerceIn(target.top, target.bottom),
    ).takeUnless(GPUPixelBounds::isEmpty)
}
