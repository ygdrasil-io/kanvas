package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds

internal data class GPUCoverageScissor(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun coverageScissor(
    bounds: GPUBounds,
    clipBounds: GPUBounds,
    surfaceWidth: Int,
    surfaceHeight: Int,
): GPUCoverageScissor? {
    if (surfaceWidth <= 0 || surfaceHeight <= 0) return null

    val left = maxOf(bounds.left, clipBounds.left, 0f)
    val top = maxOf(bounds.top, clipBounds.top, 0f)
    val right = minOf(bounds.right, clipBounds.right, surfaceWidth.toFloat())
    val bottom = minOf(bounds.bottom, clipBounds.bottom, surfaceHeight.toFloat())
    if (left >= right || top >= bottom) return null

    val x = floor(left).toInt()
    val y = floor(top).toInt()
    val endX = ceil(right).toInt()
    val endY = ceil(bottom).toInt()

    return GPUCoverageScissor(
        x = x,
        y = y,
        width = endX - x,
        height = endY - y,
    )
}

internal fun truncatedScissor(
    bounds: GPUBounds,
    clipBounds: GPUBounds,
    surfaceWidth: Int,
    surfaceHeight: Int,
): GPUCoverageScissor? {
    if (surfaceWidth <= 0 || surfaceHeight <= 0) return null

    val left = maxOf(bounds.left, clipBounds.left, 0f)
    val top = maxOf(bounds.top, clipBounds.top, 0f)
    val right = minOf(bounds.right, clipBounds.right, surfaceWidth.toFloat())
    val bottom = minOf(bounds.bottom, clipBounds.bottom, surfaceHeight.toFloat())
    if (left >= right || top >= bottom) return null

    val x = left.toInt()
    val y = top.toInt()
    val endX = right.toInt()
    val endY = bottom.toInt()
    return GPUCoverageScissor(
        x = x,
        y = y,
        width = (endX - x).coerceIn(1, surfaceWidth - x),
        height = (endY - y).coerceIn(1, surfaceHeight - y),
    )
}
