package org.graphiks.kanvas.render.ir

/**
 * Backend-neutral capacity of the indexed geometry emitted for one path edge fan.
 *
 * Renderers may choose their own payload ABI, but a renderer that consumes this
 * route must preserve these public admission limits so Scene IR and Surface
 * configuration make the same boundedness promise.
 */
public object RenderPathFanLimits {
    /** Maximum number of triangles in one path edge fan. */
    public const val MAX_TRIANGLES: UInt = 1_024u

    /** Encoded position and index bytes required by one fan triangle. */
    public const val BYTES_PER_TRIANGLE: UInt = 36u

    /** Maximum encoded bytes for [MAX_TRIANGLES] fan triangles. */
    public const val MAX_GEOMETRY_BYTES: UInt = 36_864u
}
