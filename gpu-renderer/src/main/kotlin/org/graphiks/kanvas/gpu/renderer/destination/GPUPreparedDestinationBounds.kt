package org.graphiks.kanvas.gpu.renderer.destination

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveGeometrySnapshot

/** Exact conservative projection of already-admitted prepared geometry; no geometry is rebuilt. */
internal fun GPUDrawSemanticPayload.preparedDestinationBounds(target: GPUPixelBounds): GPUPixelBounds {
    val clip = when (this) {
        is GPUDrawSemanticPayload.Vertices -> scissorBounds
        is GPUDrawSemanticPayload.TextA8 -> scissorBounds
        is GPUDrawSemanticPayload.CorePrimitive -> scissorBounds
        else -> return target // Unpromoted family: explicit unknown-footprint fallback.
    }
    val bounds = when (this) {
        is GPUDrawSemanticPayload.Vertices -> conservativeDrawBounds ?: target
        is GPUDrawSemanticPayload.CorePrimitive -> coreDestinationBounds(geometry, coverageMode, scissorBounds, target)
        is GPUDrawSemanticPayload.TextA8 -> preparedTextDestinationBounds(instances, target)
        else -> target
    }
    return preparedDestinationIntersection(bounds, clip, target)
}

internal fun GPUCorePrimitiveGeometryAuthority.preparedDestinationBounds(target: GPUPixelBounds): GPUPixelBounds =
    snapshot.preparedDestinationBounds(target)

internal fun GPUCorePrimitiveGeometrySnapshot.preparedDestinationBounds(target: GPUPixelBounds): GPUPixelBounds =
    preparedDestinationIntersection(coreDestinationBounds(geometry, coverageMode, scissorBounds, target), scissorBounds, target)

/** One bounds owner for pre-publication geometry and its post-bind semantic. */
private fun coreDestinationBounds(geometry: GPUCorePrimitiveGeometry, coverageMode: GPUCorePrimitiveCoverageMode,
    scissorBounds: GPUPixelBounds, target: GPUPixelBounds): GPUPixelBounds {
    // An unpromoted scalar-AA geometry lacks a bounded hard-edge proof and keeps full target.
    if (coverageMode !in setOf(GPUCorePrimitiveCoverageMode.FullOrScissor, GPUCorePrimitiveCoverageMode.Stencil1x)) return target
    return when (geometry) {
        is GPUCorePrimitiveGeometry.TriangulatedPath -> if (geometry.inverseFill) scissorBounds else geometry.coverBounds
        else -> {
            val edges = when (geometry) {
                is GPUCorePrimitiveGeometry.Rect -> listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
                is GPUCorePrimitiveGeometry.RRect -> listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
                is GPUCorePrimitiveGeometry.DRRect -> geometry.outerBounds
                else -> error("Unreachable retained geometry")
            }
            GPUPixelBounds(
                kotlin.math.floor(edges[0].toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
                kotlin.math.floor(edges[1].toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt(),
                kotlin.math.ceil(edges[2].toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
                kotlin.math.ceil(edges[3].toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt())
        }
    }
}

/** Existing Text coverage bounds, usable by the material-free inventory as well. */
fun preparedTextDestinationBounds(instances: List<org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance>,
    target: GPUPixelBounds): GPUPixelBounds {
    val quads = instances.map { it.deviceQuad }
    require(quads.isNotEmpty() && quads.all { it.size == 8 && it.all(Float::isFinite) })
    val xs = quads.flatMap { listOf(it[0], it[2], it[4], it[6]) }
    val ys = quads.flatMap { listOf(it[1], it[3], it[5], it[7]) }
    return GPUPixelBounds(
        kotlin.math.floor(xs.min().toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
        kotlin.math.floor(ys.min().toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt(),
        kotlin.math.ceil(xs.max().toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
        kotlin.math.ceil(ys.max().toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt())
}

/** One intersection owner for all already-admitted prepared destination consumers. */
fun preparedDestinationIntersection(bounds: GPUPixelBounds, clip: GPUPixelBounds,
    target: GPUPixelBounds): GPUPixelBounds =
    GPUPixelBounds(maxOf(target.left, clip.left, bounds.left), maxOf(target.top, clip.top, bounds.top),
        minOf(target.right, clip.right, bounds.right), minOf(target.bottom, clip.bottom, bounds.bottom)).also {
        require(!it.isEmpty) { "Prepared destination consumer has no visible bounds" }
    }
