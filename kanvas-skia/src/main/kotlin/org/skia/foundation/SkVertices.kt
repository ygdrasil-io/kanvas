package org.skia.foundation

import org.skia.math.SkPoint

/**
 * Iso-aligned port of Skia's
 * [`SkVertices`](https://github.com/google/skia/blob/main/include/core/SkVertices.h).
 *
 * Immutable triangle-mesh data : per-vertex positions, optional
 * texture coordinates, optional per-vertex colors, optional triangle
 * indices, plus a [VertexMode] selecting how the vertex array is
 * partitioned into triangles ([VertexMode.kTriangles] : every 3
 * vertices ; [VertexMode.kTriangleStrip] : sliding window ;
 * [VertexMode.kTriangleFan] : all triangles share `positions[0]`).
 *
 * Consumed by [org.skia.core.SkCanvas.drawVertices] for shaded
 * triangle-mesh rendering with per-vertex color/UV interpolation.
 *
 * @property mode      How [positions] / [indices] are partitioned into
 *                     triangles.
 * @property positions Vertex positions in source coordinates.
 * @property texCoords Per-vertex texture coordinates ; same length as
 *                     [positions]. `null` ⇒ texture sampling is
 *                     disabled (paint shader is sampled at the
 *                     rasterised pixel's *destination* coords instead).
 * @property colors    Per-vertex colors ; same length as [positions].
 *                     `null` ⇒ all vertices share `paint.color` /
 *                     `paint.shader`.
 * @property indices   Triangle indirection : if non-null, triangles
 *                     reference `positions[indices[i]]` rather than
 *                     `positions[i]`. Length must be a multiple of 3
 *                     for [VertexMode.kTriangles].
 */
public class SkVertices internal constructor(
    public val mode: VertexMode,
    public val positions: Array<SkPoint>,
    public val texCoords: Array<SkPoint>?,
    public val colors: IntArray?,
    public val indices: ShortArray?,
) {

    /** Mirrors `SkVertices::VertexMode`. */
    public enum class VertexMode {
        /** Every consecutive triple `positions[3*i..3*i+2]` is a triangle. */
        kTriangles,

        /** Sliding 3-vertex window : `(p[0], p[1], p[2]), (p[1], p[2], p[3]), …`. Winding flips per step. */
        kTriangleStrip,

        /** All triangles share `positions[0]` : `(p[0], p[i], p[i+1])` for `i ≥ 1`. */
        kTriangleFan,
    }

    init {
        require(positions.isNotEmpty()) { "SkVertices requires at least one position" }
        if (texCoords != null) require(texCoords.size == positions.size) {
            "texCoords.size (${texCoords.size}) != positions.size (${positions.size})"
        }
        if (colors != null) require(colors.size == positions.size) {
            "colors.size (${colors.size}) != positions.size (${positions.size})"
        }
    }

    /** Total triangle count under the current [mode] / [indices] partition. */
    public fun triangleCount(): Int {
        val n = indices?.size ?: positions.size
        return when (mode) {
            VertexMode.kTriangles -> n / 3
            VertexMode.kTriangleStrip -> if (n >= 3) n - 2 else 0
            VertexMode.kTriangleFan -> if (n >= 3) n - 2 else 0
        }
    }

    /**
     * Returns the three vertex *indices* (into [positions] /
     * [colors] / [texCoords]) of the i-th triangle, honouring
     * [mode] and any [indices] indirection.
     *
     * @return [IntArray] of length 3.
     * @throws IndexOutOfBoundsException if `i ∉ [0, triangleCount)`.
     */
    public fun triangleAt(i: Int): IntArray {
        val n = indices?.size ?: positions.size
        val (a, b, c) = when (mode) {
            VertexMode.kTriangles -> {
                val base = i * 3
                Triple(base, base + 1, base + 2)
            }
            VertexMode.kTriangleStrip -> {
                // Triangle i uses vertices i, i+1, i+2. Winding flips
                // every other triangle so back-faces remain consistent
                // (matches Skia / OpenGL convention).
                if (i and 1 == 0) Triple(i, i + 1, i + 2)
                else Triple(i + 1, i, i + 2)
            }
            VertexMode.kTriangleFan -> {
                // Triangle i uses vertices 0, i+1, i+2.
                Triple(0, i + 1, i + 2)
            }
        }
        if (a < 0 || c >= n) throw IndexOutOfBoundsException("triangle $i out of range [0, ${triangleCount()})")
        return intArrayOf(
            indexOf(a), indexOf(b), indexOf(c),
        )
    }

    private fun indexOf(i: Int): Int =
        indices?.let { it[i].toInt() and 0xFFFF } ?: i

    public companion object {
        /**
         * Mirrors `SkVertices::MakeCopy(mode, vertexCount, positions,
         * texs, colors, indexCount, indices)`. The supplied arrays are
         * defensively copied — the resulting [SkVertices] is fully
         * decoupled from the caller's storage.
         */
        public fun MakeCopy(
            mode: VertexMode,
            positions: Array<SkPoint>,
            texCoords: Array<SkPoint>? = null,
            colors: IntArray? = null,
            indices: ShortArray? = null,
        ): SkVertices = SkVertices(
            mode = mode,
            positions = Array(positions.size) { SkPoint(positions[it].fX, positions[it].fY) },
            texCoords = texCoords?.let { tc -> Array(tc.size) { SkPoint(tc[it].fX, tc[it].fY) } },
            colors = colors?.copyOf(),
            indices = indices?.copyOf(),
        )
    }
}
