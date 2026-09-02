package org.graphiks.kanvas.gpu.renderer.geometry

import org.graphiks.kanvas.render.ir.RenderPathFanLimits

/**
 * Immutable ABI capacity for one non-AA stencil edge-fan payload.
 *
 * The public limits are owned by [RenderPathFanLimits]. This adapter keeps the
 * payload validator on that same authority so a mapped path cannot be accepted
 * by one side and refused by the other after recording begins.
 */
object GPUPathEdgeFanPayloadContract {
    /** One emitted edge fan triangle contains three two-float positions and three indices. */
    const val BYTES_PER_TRIANGLE: UInt = RenderPathFanLimits.BYTES_PER_TRIANGLE

    /** Maximum edge fan triangle count representable by the payload ABI. */
    const val MAX_TRIANGLES: UInt = RenderPathFanLimits.MAX_TRIANGLES

    /** Maximum position/index payload size for [MAX_TRIANGLES]. */
    const val MAX_GEOMETRY_BYTES: UInt = RenderPathFanLimits.MAX_GEOMETRY_BYTES
}
