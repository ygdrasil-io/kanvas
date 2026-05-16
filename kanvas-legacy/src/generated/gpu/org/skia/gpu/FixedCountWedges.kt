package org.skia.gpu

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class FixedCountWedges {
 *     FixedCountWedges() = delete;
 * public:
 *     // These functions provide equivalent functionality to the matching ones in FixedCountCurves,
 *     // but are intended for use with a shader and PatchWriter that has enabled the kFanPoint attrib.
 *
 *     static constexpr int PreallocCount(int totalCombinedPathVerbCnt)  {
 *         // Over-allocate enough wedges for 1 in 4 to chop, i.e., ceil(maxWedges * 5/4)
 *         return (totalCombinedPathVerbCnt * 5 + 3) / 4;
 *     }
 *
 *     static int VertexCount(const LinearTolerances& tolerances) {
 *         // Emit 3 vertices per curve triangle, plus 3 more for the wedge fan triangle.
 *         int resolveLevel = std::min(tolerances.requiredResolveLevel(), kMaxResolveLevel);
 *         return (NumCurveTrianglesAtResolveLevel(resolveLevel) + 1) * 3;
 *     }
 *
 *     static constexpr size_t VertexBufferVertexCount() {
 *         return (kMaxParametricSegments + 1) + 1/*fan vertex*/;
 *     }
 *
 *     static constexpr size_t VertexBufferStride() {
 *         return 2 * sizeof(float);
 *     }
 *
 *     static constexpr size_t VertexBufferSize() {
 *         return VertexBufferVertexCount() * VertexBufferStride();
 *     }
 *
 *     static constexpr size_t IndexBufferSize() {
 *         return (NumCurveTrianglesAtResolveLevel(kMaxResolveLevel) + 1/*fan triangle*/) *
 *                3 * sizeof(uint16_t);
 *     }
 *
 *     static void WriteVertexBuffer(VertexWriter, size_t bufferSize);
 *
 *     static void WriteIndexBuffer(VertexWriter, size_t bufferSize);
 * }
 * ```
 */
public open class FixedCountWedges public constructor() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr int PreallocCount(int totalCombinedPathVerbCnt)  {
     *         // Over-allocate enough wedges for 1 in 4 to chop, i.e., ceil(maxWedges * 5/4)
     *         return (totalCombinedPathVerbCnt * 5 + 3) / 4;
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
     *         // Emit 3 vertices per curve triangle, plus 3 more for the wedge fan triangle.
     *         int resolveLevel = std::min(tolerances.requiredResolveLevel(), kMaxResolveLevel);
     *         return (NumCurveTrianglesAtResolveLevel(resolveLevel) + 1) * 3;
     *     }
     * ```
     */
    public fun vertexCount(tolerances: LinearTolerances): Int {
      TODO("Implement vertexCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t VertexBufferVertexCount() {
     *         return (kMaxParametricSegments + 1) + 1/*fan vertex*/;
     *     }
     * ```
     */
    public fun vertexBufferVertexCount(): Int {
      TODO("Implement vertexBufferVertexCount")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t VertexBufferStride() {
     *         return 2 * sizeof(float);
     *     }
     * ```
     */
    public fun vertexBufferStride(): Int {
      TODO("Implement vertexBufferStride")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t VertexBufferSize() {
     *         return VertexBufferVertexCount() * VertexBufferStride();
     *     }
     * ```
     */
    public fun vertexBufferSize(): Int {
      TODO("Implement vertexBufferSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr size_t IndexBufferSize() {
     *         return (NumCurveTrianglesAtResolveLevel(kMaxResolveLevel) + 1/*fan triangle*/) *
     *                3 * sizeof(uint16_t);
     *     }
     * ```
     */
    public fun indexBufferSize(): Int {
      TODO("Implement indexBufferSize")
    }

    /**
     * C++ original:
     * ```cpp
     * void FixedCountWedges::WriteVertexBuffer(VertexWriter vertexWriter, size_t bufferSize) {
     *     SkASSERT(bufferSize >= sizeof(SkPoint));
     *
     *     // Start out with the fan point. A negative resolve level indicates the fan point.
     *     vertexWriter << -1.f/*resolveLevel*/ << -1.f/*idx*/;
     *
     *     // The rest is the same as for curves.
     *     FixedCountCurves::WriteVertexBuffer(std::move(vertexWriter), bufferSize - sizeof(SkPoint));
     * }
     * ```
     */
    public fun writeVertexBuffer(vertexWriter: VertexWriter, bufferSize: ULong) {
      TODO("Implement writeVertexBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void FixedCountWedges::WriteIndexBuffer(VertexWriter vertexWriter, size_t bufferSize) {
     *     SkASSERT(bufferSize >= sizeof(uint16_t) * 3);
     *
     *     // Start out with the fan triangle.
     *     vertexWriter << (uint16_t)0 << (uint16_t)1 << (uint16_t)2;
     *
     *     // The rest is the same as for curves, with a baseIndex of 1.
     *     write_curve_index_buffer_base_index(std::move(vertexWriter),
     *                                         bufferSize - sizeof(uint16_t) * 3,
     *                                         /*baseIndex=*/1);
     * }
     * ```
     */
    public fun writeIndexBuffer(vertexWriter: VertexWriter, bufferSize: ULong) {
      TODO("Implement writeIndexBuffer")
    }
  }
}
