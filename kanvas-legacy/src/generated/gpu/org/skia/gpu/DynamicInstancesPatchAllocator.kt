package org.skia.gpu

import kotlin.Int
import tess.LinearTolerances

/**
 * C++ original:
 * ```cpp
 * template <typename FixedCountVariant>
 * class DynamicInstancesPatchAllocator {
 * public:
 *     // 'stride' is provided by PatchWriter.
 *     // 'writer' is the DrawWriter that the RenderStep can append instance data to,
 *     // 'fixedVertexBuffer' and 'fixedIndexBuffer' are the bindings for the instance template that
 *     // is passed to DrawWriter::DynamicInstances.
 *     DynamicInstancesPatchAllocator(size_t stride,
 *                                    DrawWriter& writer,
 *                                    BindBufferInfo fixedVertexBuffer,
 *                                    BindBufferInfo fixedIndexBuffer,
 *                                    unsigned int reserveCount)
 *             : fInstances(writer, fixedVertexBuffer, fixedIndexBuffer) {
 *         SkASSERT(stride == writer.appendStride());
 *         // TODO: Is it worth re-reserving large chunks after this preallocation is used up? Or will
 *         // appending 1 at a time be fine since it's coming from a large vertex buffer alloc anyway?
 *         fInstances.reserve(reserveCount);
 *     }
 *
 *     VertexWriter append(const tess::LinearTolerances& tolerances) {
 *         return fInstances.append(tolerances, 1);
 *     }
 *
 * private:
 *     struct LinearToleranceProxy {
 *         operator uint32_t() const { return FixedCountVariant::VertexCount(fTolerances); }
 *         void operator <<(const tess::LinearTolerances& t) { fTolerances.accumulate(t); }
 *
 *         tess::LinearTolerances fTolerances;
 *     };
 *
 *     DrawWriter::DynamicInstances<LinearToleranceProxy> fInstances;
 * }
 * ```
 */
public data class DynamicInstancesPatchAllocator<FixedCountVariant> public constructor(
  /**
   * C++ original:
   * ```cpp
   * DrawWriter::DynamicInstances<LinearToleranceProxy> fInstances
   * ```
   */
  private var fInstances: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * VertexWriter append(const tess::LinearTolerances& tolerances) {
   *         return fInstances.append(tolerances, 1);
   *     }
   * ```
   */
  public fun append(tolerances: LinearTolerances): Int {
    TODO("Implement append")
  }

  public open class LinearToleranceProxy
}
