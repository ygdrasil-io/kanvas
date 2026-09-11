package org.graphiks.kanvas.gpu.renderer.destination

import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode

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
        is GPUDrawSemanticPayload.CorePrimitive -> {
            // Mixed W5b admits only the retained hard-edge geometry domain here.
            // An unpromoted scalar-AA geometry lacks that proof and keeps full target.
            if (coverageMode !in setOf(GPUCorePrimitiveCoverageMode.FullOrScissor,
                    GPUCorePrimitiveCoverageMode.Stencil1x)) target
            else when (val geometry = geometry) {
                is GPUCorePrimitiveGeometry.TriangulatedPath ->
                    if (geometry.inverseFill) scissorBounds else geometry.coverBounds
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
        is GPUDrawSemanticPayload.TextA8 -> {
            val quads = instances.map { it.deviceQuad }
            require(quads.isNotEmpty() && quads.all { it.size == 8 && it.all(Float::isFinite) })
            val xs = quads.flatMap { listOf(it[0], it[2], it[4], it[6]) }
            val ys = quads.flatMap { listOf(it[1], it[3], it[5], it[7]) }
            GPUPixelBounds(
                kotlin.math.floor(xs.min().toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
                kotlin.math.floor(ys.min().toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt(),
                kotlin.math.ceil(xs.max().toDouble()).coerceIn(target.left.toDouble(), target.right.toDouble()).toInt(),
                kotlin.math.ceil(ys.max().toDouble()).coerceIn(target.top.toDouble(), target.bottom.toDouble()).toInt())
        }
        else -> target
    }
    return GPUPixelBounds(maxOf(target.left, clip.left, bounds.left), maxOf(target.top, clip.top, bounds.top),
        minOf(target.right, clip.right, bounds.right), minOf(target.bottom, clip.bottom, bounds.bottom)).also {
        require(!it.isEmpty) { "Prepared destination consumer has no visible bounds" }
    }
}
