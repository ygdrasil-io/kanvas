package org.skia.gpu

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class FixedCountCurves {
 *     FixedCountCurves() = delete;
 * public:
 *     // A heuristic function for reserving instance attribute space before using a PatchWriter.
 *     static constexpr int PreallocCount(int totalCombinedPathVerbCnt) {
 *         // Over-allocate enough curves for 1 in 4 to chop. Every chop introduces 2 new patches:
 *         // another curve patch and a triangle patch that glues the two chops together,
 *         // i.e. + 2 * ((count + 3) / 4) == (count + 3) / 2
 *         return totalCombinedPathVerbCnt + (totalCombinedPathVerbCnt + 3) / 2;
 *     }
 *
 *     // Convert the accumulated worst-case tolerances into an index count passed into an instanced,
 *     // indexed draw function that uses FixedCountCurves static vertex and index buffers.
 *     static int VertexCount(const LinearTolerances& tolerances) {
 *         // We should already chopped curves to make sure none needed a higher resolveLevel than
 *         // kMaxResolveLevel.
 *         int resolveLevel = std::min(tolerances.requiredResolveLevel(), kMaxResolveLevel);
 *         return NumCurveTrianglesAtResolveLevel(resolveLevel) * 3;
 *     }
 *
 *     static constexpr size_t VertexBufferVertexCount() {
 *         return kMaxParametricSegments + 1;
 *     }
 *
 *     static constexpr size_t VertexBufferStride() {
 *         return 2 * sizeof(float);
 *     }
 *
 *     // Return the number of bytes to allocate for a buffer filled via WriteVertexBuffer, assuming
 *     // the shader and curve instances do require more than kMaxParametricSegments segments.
 *     static constexpr size_t VertexBufferSize() {
 *         return VertexBufferVertexCount() * VertexBufferStride();
 *     }
 *
 *     // As above but for the corresponding index buffer, written via WriteIndexBuffer.
 *     static constexpr size_t IndexBufferSize() {
 *         return NumCurveTrianglesAtResolveLevel(kMaxResolveLevel) * 3 * sizeof(uint16_t);
 *     }
 *
 *     static void WriteVertexBuffer(VertexWriter, size_t bufferSize);
 *
 *     static void WriteIndexBuffer(VertexWriter, size_t bufferSize);
 * }
 * ```
 */
public open class FixedCountCurves public constructor() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr int PreallocCount(int totalCombinedPathVerbCnt) {
     *         // Over-allocate enough curves for 1 in 4 to chop. Every chop introduces 2 new patches:
     *         // another curve patch and a triangle patch that glues the two chops together,
     *         // i.e. + 2 * ((count + 3) / 4) == (count + 3) / 2
     *         return totalCombinedPathVerbCnt + (totalCombinedPathVerbCnt + 3) / 2;
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
     *         // We should already chopped curves to make sure none needed a higher resolveLevel than
     *         // kMaxResolveLevel.
     *         int resolveLevel = std::min(tolerances.requiredResolveLevel(), kMaxResolveLevel);
     *         return NumCurveTrianglesAtResolveLevel(resolveLevel) * 3;
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
     *         return kMaxParametricSegments + 1;
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
     *         return NumCurveTrianglesAtResolveLevel(kMaxResolveLevel) * 3 * sizeof(uint16_t);
     *     }
     * ```
     */
    public fun indexBufferSize(): Int {
      TODO("Implement indexBufferSize")
    }

    /**
     * C++ original:
     * ```cpp
     * void FixedCountCurves::WriteVertexBuffer(VertexWriter vertexWriter, size_t bufferSize) {
     *     SkASSERT(bufferSize >= sizeof(SkPoint) * 2);
     *     int vertexCount = bufferSize / sizeof(SkPoint);
     *     SkASSERT(vertexCount > 3);
     *     SkDEBUGCODE(auto end = vertexWriter.mark(vertexCount * sizeof(SkPoint));)
     *
     *     // Lay out the vertices in "middle-out" order:
     *     //
     *     // T= 0/1, 1/1,              ; resolveLevel=0
     *     //    1/2,                   ; resolveLevel=1  (0/2 and 2/2 are already in resolveLevel 0)
     *     //    1/4, 3/4,              ; resolveLevel=2  (2/4 is already in resolveLevel 1)
     *     //    1/8, 3/8, 5/8, 7/8,    ; resolveLevel=3  (2/8 and 6/8 are already in resolveLevel 2)
     *     //    ...                    ; resolveLevel=...
     *     //
     *     // Resolve level 0 is just the beginning and ending vertices.
     *     vertexWriter << (float)0/*resolveLevel*/ << (float)0/*idx*/;
     *     vertexWriter << (float)0/*resolveLevel*/ << (float)1/*idx*/;
     *
     *     // Resolve levels 1..kMaxResolveLevel.
     *     int maxResolveLevel = SkPrevLog2(vertexCount - 1);
     *     SkASSERT((1 << maxResolveLevel) + 1 == vertexCount);
     *     for (int resolveLevel = 1; resolveLevel <= maxResolveLevel; ++resolveLevel) {
     *         int numSegmentsInResolveLevel = 1 << resolveLevel;
     *         // Write out the odd vertices in this resolveLevel. The even vertices were already written
     *         // out in previous resolveLevels and will be indexed from there.
     *         for (int i = 1; i < numSegmentsInResolveLevel; i += 2) {
     *             vertexWriter << (float)resolveLevel << (float)i;
     *         }
     *     }
     *
     *     SkASSERT(vertexWriter.mark() == end);
     * }
     * ```
     */
    public fun writeVertexBuffer(vertexWriter: VertexWriter, bufferSize: ULong) {
      TODO("Implement writeVertexBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void FixedCountCurves::WriteIndexBuffer(VertexWriter vertexWriter, size_t bufferSize) {
     *    write_curve_index_buffer_base_index(std::move(vertexWriter), bufferSize, /*baseIndex=*/0);
     * }
     * ```
     */
    public fun writeIndexBuffer(vertexWriter: VertexWriter, bufferSize: ULong) {
      TODO("Implement writeIndexBuffer")
    }
  }
}
