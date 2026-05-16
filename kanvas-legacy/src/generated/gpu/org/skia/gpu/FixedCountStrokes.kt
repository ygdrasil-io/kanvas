package org.skia.gpu

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class FixedCountStrokes {
 *     FixedCountStrokes() = delete;
 * public:
 *     // These functions provide equivalent functionality to the matching ones in FixedCountCurves,
 *     // but are intended for a shader that that strokes a path instead of filling, where vertices
 *     // are associated with joins, caps, radial segments, or parametric segments.
 *     //
 *     // NOTE: The fixed-count stroke buffer is only needed when vertex IDs are not available as an
 *     // SkSL built-in. And unlike the curve and wedge variants, stroke drawing never relies on an
 *     // index buffer so those functions are not provided.
 *
 *     // Don't draw more vertices than can be indexed by a signed short. We just have to draw the line
 *     // somewhere and this seems reasonable enough. (There are two vertices per edge, so 2^14 edges
 *     // make 2^15 vertices.)
 *     static constexpr int kMaxEdges = (1 << 14) - 1;
 *     static constexpr int kMaxEdgesNoVertexIDs = 1024;
 *
 *     static constexpr int PreallocCount(int totalCombinedPathVerbCnt) {
 *         // Over-allocate enough patches for each stroke to chop once, and for 8 extra caps. Since
 *         // we have to chop at inflections, points of 180 degree rotation, and anywhere a stroke
 *         // requires too many parametric segments, many strokes will end up getting choppped.
 *         return (totalCombinedPathVerbCnt * 2) + 8/* caps */;
 *     }
 *
 *     // Does not account for falling back to kMaxEdgesNoVertexIDs
 *     static int VertexCount(const LinearTolerances& tolerances) {
 *         return std::min(tolerances.requiredStrokeEdges(), kMaxEdges) * 2;
 *     }
 *
 *     static constexpr size_t VertexBufferSize() {
 *         // Each vertex is a single float (explicit id) and each edge is composed of two vertices.
 *         return 2 * kMaxEdgesNoVertexIDs * sizeof(float);
 *     }
 *
 *     // Initializes the fallback vertex buffer that should be bound when sk_VertexID is not supported
 *     static void WriteVertexBuffer(VertexWriter, size_t bufferSize);
 * }
 * ```
 */
public open class FixedCountStrokes public constructor() {
  public companion object {
    public val kMaxEdges: Int = TODO("Initialize kMaxEdges")

    public val kMaxEdgesNoVertexIDs: Int = TODO("Initialize kMaxEdgesNoVertexIDs")

    /**
     * C++ original:
     * ```cpp
     * static constexpr int PreallocCount(int totalCombinedPathVerbCnt) {
     *         // Over-allocate enough patches for each stroke to chop once, and for 8 extra caps. Since
     *         // we have to chop at inflections, points of 180 degree rotation, and anywhere a stroke
     *         // requires too many parametric segments, many strokes will end up getting choppped.
     *         return (totalCombinedPathVerbCnt * 2) + 8/* caps */;
     *     }
     * ```
     */
    public fun preallocCount(totalCombinedPathVerbCnt: Int): Int {
      TODO("Implement preallocCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static int VertexCount(const LinearTolerances& tolerances) {
     *         return std::min(tolerances.requiredStrokeEdges(), kMaxEdges) * 2;
     *     }
     * ```
     */
    public fun vertexCount(tolerances: LinearTolerances): Int {
      TODO("Implement vertexCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t VertexBufferSize() {
     *         // Each vertex is a single float (explicit id) and each edge is composed of two vertices.
     *         return 2 * kMaxEdgesNoVertexIDs * sizeof(float);
     *     }
     * ```
     */
    public fun vertexBufferSize(): Int {
      TODO("Implement vertexBufferSize")
    }

    /**
     * C++ original:
     * ```cpp
     * void FixedCountStrokes::WriteVertexBuffer(VertexWriter vertexWriter, size_t bufferSize) {
     *     int edgeCount = bufferSize / (sizeof(float) * 2);
     *     for (int i = 0; i < edgeCount; ++i) {
     *         vertexWriter << (float)i << (float)-i;
     *     }
     * }
     * ```
     */
    public fun writeVertexBuffer(vertexWriter: VertexWriter, bufferSize: ULong) {
      TODO("Implement writeVertexBuffer")
    }
  }
}
