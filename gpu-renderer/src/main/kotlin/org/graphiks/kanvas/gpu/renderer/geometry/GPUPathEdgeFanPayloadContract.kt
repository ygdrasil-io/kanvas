package org.graphiks.kanvas.gpu.renderer.geometry

/**
 * Immutable ABI capacity for one non-AA stencil edge-fan payload.
 *
 * This is owned by `gpu-renderer`: the payload validator and the public
 * Surface configuration must use the same bound so a mapped path cannot be
 * accepted by one side and refused by the other after recording begins.
 */
object GPUPathEdgeFanPayloadContract {
    /** One emitted edge fan triangle contains three two-float positions and three indices. */
    const val BYTES_PER_TRIANGLE: UInt = 36u

    /** Maximum edge fan triangle count representable by the payload ABI. */
    const val MAX_TRIANGLES: UInt = 1_024u

    /** Maximum source contour metadata retained by the semantic payload contract. */
    const val MAX_SOURCE_VERTICES: UInt = 256u

    /** Maximum position/index payload size for [MAX_TRIANGLES]. */
    const val MAX_GEOMETRY_BYTES: UInt = 36_864u
}
