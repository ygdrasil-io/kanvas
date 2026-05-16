package org.skia.core

import kotlin.CharArray
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import kotlin.Unit
import org.skia.memory.SkArenaAlloc
import skvx.Vec4

/**
 * C++ original:
 * ```cpp
 * class TentPass final : public Pass {
 * public:
 *     // NB 2183 is the largest sigma that will not cause a buffer full of 255 mask values to overflow
 *     // using the Tent filter. It also limits the size of buffers used hold intermediate values.
 *     // Explanation of maximums:
 *     //   sum0 = window * 255
 *     //   sum1 = window * sum0 -> window * window * 255
 *     //
 *     //   The value window^2 * 255 must fit in a uint32_t. So,
 *     //      window^2 < 2^32. window = 4104.
 *     //
 *     //   window = floor(sigma * 3 * sqrt(2 * kPi) / 4 + 0.5)
 *     //   For window <= 4104, the largest value for sigma is 2183.
 *     static PassMaker* MakeMaker(float sigma, SkArenaAlloc* alloc) {
 *         SkASSERT(0 <= sigma);
 *         int gaussianWindow = SkBlurEngine::BoxBlurWindow(sigma);
 *         // This is a naive method of using the window size for the Gaussian blur to calculate the
 *         // window size for the Tent blur. This seems to work well in practice.
 *         //
 *         // We can use a single pixel to generate the effective blur area given a window size. For
 *         // the Gaussian blur this is 3 * window size. For the Tent filter this is 2 * window size.
 *         int tentWindow = 3 * gaussianWindow / 2;
 *         if (tentWindow >= 4104) {
 *             return nullptr;
 *         }
 *
 *         class Maker : public PassMaker {
 *         public:
 *             explicit Maker(int window, float sigma) : PassMaker{window, sigma} {}
 *             Pass* makePass(void* buffer, SkArenaAlloc* alloc) const override {
 *                 return TentPass::Make(this->window(), buffer, alloc);
 *             }
 *
 *             size_t bufferSizeBytes() const override {
 *                 size_t onePassSize = this->window() - 1;
 *                 // If the window is odd, then there is an obvious middle element. For even sizes 2
 *                 // passes are shifted, and the last pass has an extra element. Like this:
 *                 //       S
 *                 //    aaaAaa
 *                 //     bbBbbb
 *                 //       D
 *                 size_t bufferCount = 2 * onePassSize;
 *                 return bufferCount * sizeof(skvx::Vec<4, uint32_t>);
 *             }
 *         };
 *
 *         return alloc->make<Maker>(tentWindow, sigma);
 *     }
 *
 *     static TentPass* Make(int window, void* buffers, SkArenaAlloc* alloc) {
 *         if (window > 4104) {
 *             return nullptr;
 *         }
 *
 *         // We don't need to store the trailing edge pixel in the buffer;
 *         int passSize = window - 1;
 *         skvx::Vec<4, uint32_t>* buffer0 = static_cast<skvx::Vec<4, uint32_t>*>(buffers);
 *         skvx::Vec<4, uint32_t>* buffer1 = buffer0 + passSize;
 *         skvx::Vec<4, uint32_t>* buffersEnd = buffer1 + passSize;
 *
 *         // Calculating the border is tricky. The border is the distance in pixels between the first
 *         // dst pixel and the first src pixel (or the last src pixel and the last dst pixel).
 *         // I will go through the odd case which is simpler, and then through the even case. Given a
 *         // stack of filters seven wide for the odd case of three passes.
 *         //
 *         //        S
 *         //     aaaAaaa
 *         //     bbbBbbb
 *         //        D
 *         //
 *         // The furthest changed pixel is when the filters are in the following configuration.
 *         //
 *         //              S
 *         //        aaaAaaa
 *         //     bbbBbbb
 *         //        D
 *         //
 *         // The A pixel is calculated using the value S, the B uses A, and the D uses B.
 *         // So, with a window size of seven the border is nine. In the odd case, the border is
 *         // window - 1.
 *         //
 *         // For even cases the filter stack is more complicated. It uses two passes
 *         // of even filters offset from each other. A stack for a width of six looks like
 *         // this.
 *         //
 *         //       S
 *         //    aaaAaa
 *         //     bbBbbb
 *         //       D
 *         //
 *         // The furthest pixel looks like this.
 *         //
 *         //            S
 *         //       aaaAaa
 *         //     bbBbbb
 *         //       D
 *         //
 *         // For a window of six, the border value is 5. In the even case the border is
 *         // window - 1.
 *         int border = window - 1;
 *
 *         int divisor = window * window;
 *         return alloc->make<TentPass>(buffer0, buffer1, buffersEnd, border, divisor);
 *     }
 *
 *     TentPass(skvx::Vec<4, uint32_t>* buffer0,
 *              skvx::Vec<4, uint32_t>* buffer1,
 *              skvx::Vec<4, uint32_t>* buffersEnd,
 *              int border,
 *              int divisor)
 *          : Pass{border}
 *          , fBuffer0{buffer0}
 *          , fBuffer1{buffer1}
 *          , fBuffersEnd{buffersEnd}
 *          , fDivider(divisor) {}
 *
 * private:
 *     void startBlur() override {
 *         skvx::Vec<4, uint32_t>{0u, 0u, 0u, 0u}.store(fSum0);
 *         auto half = fDivider.half();
 *         skvx::Vec<4, uint32_t>{half, half, half, half}.store(fSum1);
 *         sk_bzero(fBuffer0, (fBuffersEnd - fBuffer0) * sizeof(skvx::Vec<4, uint32_t>));
 *
 *         fBuffer0Cursor = fBuffer0;
 *         fBuffer1Cursor = fBuffer1;
 *     }
 *
 *     // TentPass implements the common two pass box filter approximation of Tent filter,
 *     // but combines all both passes into a single pass. This approach is facilitated by two
 *     // circular buffers the width of the window which track values for trailing edges of each of
 *     // both passes. This allows the algorithm to use more precision in the calculation
 *     // because the values are not rounded each pass. And this implementation also avoids a trap
 *     // that's easy to fall into resulting in blending in too many zeroes near the edge.
 *     //
 *     // In general, a window sum has the form:
 *     //     sum_n+1 = sum_n + leading_edge - trailing_edge.
 *     // If instead we do the subtraction at the end of the previous iteration, we can just
 *     // calculate the sums instead of having to do the subtractions too.
 *     //
 *     //      In previous iteration:
 *     //      sum_n+1 = sum_n - trailing_edge.
 *     //
 *     //      In this iteration:
 *     //      sum_n+1 = sum_n + leading_edge.
 *     //
 *     // Now we can stack all three sums and do them at once. Sum0 gets its leading edge from the
 *     // actual data. Sum1's leading edge is just Sum0, and Sum2's leading edge is Sum1. So, doing the
 *     // three passes at the same time has the form:
 *     //
 *     //    sum0_n+1 = sum0_n + leading edge
 *     //    sum1_n+1 = sum1_n + sum0_n+1
 *     //
 *     //    sum1_n+1 / window^2 is the new value of the destination pixel.
 *     //
 *     // Reduce the sums by the trailing edges which were stored in the circular buffers for the
 *     // next go around.
 *     //
 *     //    sum1_n+2 = sum1_n+1 - buffer1[i];
 *     //    buffer1[i] = sum0;
 *     //    sum0_n+2 = sum0_n+1 - buffer0[i];
 *     //    buffer0[i] = leading edge
 *     void blurSegment(
 *             int n, const void* src, int srcStride, void* dst, int dstStride) override {
 *         const uint32_t* src32 = reinterpret_cast<const uint32_t*>(src);
 *         uint32_t* dst32 = reinterpret_cast<uint32_t*>(dst);
 *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
 *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
 *         skvx::Vec<4, uint32_t> sum0 = skvx::Vec<4, uint32_t>::Load(fSum0);
 *         skvx::Vec<4, uint32_t> sum1 = skvx::Vec<4, uint32_t>::Load(fSum1);
 *
 *         // Given an expanded input pixel, move the window ahead using the leadingEdge value.
 *         auto processValue = [&](const skvx::Vec<4, uint32_t>& leadingEdge) {
 *             sum0 += leadingEdge;
 *             sum1 += sum0;
 *
 *             skvx::Vec<4, uint32_t> blurred = fDivider.divide(sum1);
 *
 *             sum1 -= *buffer1Cursor;
 *             *buffer1Cursor = sum0;
 *             buffer1Cursor = (buffer1Cursor + 1) < fBuffersEnd ? buffer1Cursor + 1 : fBuffer1;
 *             sum0 -= *buffer0Cursor;
 *             *buffer0Cursor = leadingEdge;
 *             buffer0Cursor = (buffer0Cursor + 1) < fBuffer1 ? buffer0Cursor + 1 : fBuffer0;
 *
 *             return skvx::cast<uint8_t>(blurred);
 *         };
 *
 *         auto loadEdge = [&](const uint32_t* srcCursor) {
 *             return skvx::cast<uint32_t>(skvx::Vec<4, uint8_t>::Load(srcCursor));
 *         };
 *
 *         if (!src32 && !dst32) {
 *             while (n --> 0) {
 *                 (void)processValue(0);
 *             }
 *         } else if (src32 && !dst32) {
 *             while (n --> 0) {
 *                 (void)processValue(loadEdge(src32));
 *                 src32 += srcStride;
 *             }
 *         } else if (!src32 && dst32) {
 *             while (n --> 0) {
 *                 processValue(0u).store(dst32);
 *                 dst32 += dstStride;
 *             }
 *         } else if (src32 && dst32) {
 *             while (n --> 0) {
 *                 processValue(loadEdge(src32)).store(dst32);
 *                 src32 += srcStride;
 *                 dst32 += dstStride;
 *             }
 *         }
 *
 *         // Store the state
 *         fBuffer0Cursor = buffer0Cursor;
 *         fBuffer1Cursor = buffer1Cursor;
 *         sum0.store(fSum0);
 *         sum1.store(fSum1);
 *     }
 *
 *     skvx::Vec<4, uint32_t>* const fBuffer0;
 *     skvx::Vec<4, uint32_t>* const fBuffer1;
 *     skvx::Vec<4, uint32_t>* const fBuffersEnd;
 *     const skvx::ScaledDividerU32 fDivider;
 *
 *     // blur state
 *     char fSum0[sizeof(skvx::Vec<4, uint32_t>)];
 *     char fSum1[sizeof(skvx::Vec<4, uint32_t>)];
 *     skvx::Vec<4, uint32_t>* fBuffer0Cursor;
 *     skvx::Vec<4, uint32_t>* fBuffer1Cursor;
 * }
 * ```
 */
public class TentPass public constructor(
  buffer0: Vec4<UInt>,
  buffer1: Vec4<UInt>,
  buffersEnd: Vec4<UInt>,
  border: Int,
  divisor: Int,
) : Pass(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skvx::Vec<4, uint32_t>* const fBuffer0
   * ```
   */
  private val fBuffer0: Vec4<UInt> = TODO("Initialize fBuffer0")

  /**
   * C++ original:
   * ```cpp
   * skvx::Vec<4, uint32_t>* const fBuffer1
   * ```
   */
  private val fBuffer1: Vec4<UInt> = TODO("Initialize fBuffer1")

  /**
   * C++ original:
   * ```cpp
   * skvx::Vec<4, uint32_t>* const fBuffersEnd
   * ```
   */
  private val fBuffersEnd: Vec4<UInt> = TODO("Initialize fBuffersEnd")

  /**
   * C++ original:
   * ```cpp
   * const skvx::ScaledDividerU32 fDivider
   * ```
   */
  private val fDivider: ScaledDividerU32 = TODO("Initialize fDivider")

  /**
   * C++ original:
   * ```cpp
   * char fSum0[sizeof(skvx::Vec<4, uint32_t>)]
   * ```
   */
  private var fSum0: CharArray = TODO("Initialize fSum0")

  /**
   * C++ original:
   * ```cpp
   * char fSum1[sizeof(skvx::Vec<4, uint32_t>)]
   * ```
   */
  private var fSum1: CharArray = TODO("Initialize fSum1")

  /**
   * C++ original:
   * ```cpp
   * skvx::Vec<4, uint32_t>* fBuffer0Cursor
   * ```
   */
  private var fBuffer0Cursor: Vec4<UInt> = TODO("Initialize fBuffer0Cursor")

  /**
   * C++ original:
   * ```cpp
   * skvx::Vec<4, uint32_t>* fBuffer1Cursor
   * ```
   */
  private var fBuffer1Cursor: Vec4<UInt> = TODO("Initialize fBuffer1Cursor")

  /**
   * C++ original:
   * ```cpp
   * void startBlur() override {
   *         skvx::Vec<4, uint32_t>{0u, 0u, 0u, 0u}.store(fSum0);
   *         auto half = fDivider.half();
   *         skvx::Vec<4, uint32_t>{half, half, half, half}.store(fSum1);
   *         sk_bzero(fBuffer0, (fBuffersEnd - fBuffer0) * sizeof(skvx::Vec<4, uint32_t>));
   *
   *         fBuffer0Cursor = fBuffer0;
   *         fBuffer1Cursor = fBuffer1;
   *     }
   * ```
   */
  public override fun startBlur() {
    TODO("Implement startBlur")
  }

  /**
   * C++ original:
   * ```cpp
   * void blurSegment(
   *             int n, const void* src, int srcStride, void* dst, int dstStride) override {
   *         const uint32_t* src32 = reinterpret_cast<const uint32_t*>(src);
   *         uint32_t* dst32 = reinterpret_cast<uint32_t*>(dst);
   *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
   *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
   *         skvx::Vec<4, uint32_t> sum0 = skvx::Vec<4, uint32_t>::Load(fSum0);
   *         skvx::Vec<4, uint32_t> sum1 = skvx::Vec<4, uint32_t>::Load(fSum1);
   *
   *         // Given an expanded input pixel, move the window ahead using the leadingEdge value.
   *         auto processValue = [&](const skvx::Vec<4, uint32_t>& leadingEdge) {
   *             sum0 += leadingEdge;
   *             sum1 += sum0;
   *
   *             skvx::Vec<4, uint32_t> blurred = fDivider.divide(sum1);
   *
   *             sum1 -= *buffer1Cursor;
   *             *buffer1Cursor = sum0;
   *             buffer1Cursor = (buffer1Cursor + 1) < fBuffersEnd ? buffer1Cursor + 1 : fBuffer1;
   *             sum0 -= *buffer0Cursor;
   *             *buffer0Cursor = leadingEdge;
   *             buffer0Cursor = (buffer0Cursor + 1) < fBuffer1 ? buffer0Cursor + 1 : fBuffer0;
   *
   *             return skvx::cast<uint8_t>(blurred);
   *         };
   *
   *         auto loadEdge = [&](const uint32_t* srcCursor) {
   *             return skvx::cast<uint32_t>(skvx::Vec<4, uint8_t>::Load(srcCursor));
   *         };
   *
   *         if (!src32 && !dst32) {
   *             while (n --> 0) {
   *                 (void)processValue(0);
   *             }
   *         } else if (src32 && !dst32) {
   *             while (n --> 0) {
   *                 (void)processValue(loadEdge(src32));
   *                 src32 += srcStride;
   *             }
   *         } else if (!src32 && dst32) {
   *             while (n --> 0) {
   *                 processValue(0u).store(dst32);
   *                 dst32 += dstStride;
   *             }
   *         } else if (src32 && dst32) {
   *             while (n --> 0) {
   *                 processValue(loadEdge(src32)).store(dst32);
   *                 src32 += srcStride;
   *                 dst32 += dstStride;
   *             }
   *         }
   *
   *         // Store the state
   *         fBuffer0Cursor = buffer0Cursor;
   *         fBuffer1Cursor = buffer1Cursor;
   *         sum0.store(fSum0);
   *         sum1.store(fSum1);
   *     }
   * ```
   */
  public override fun blurSegment(
    n: Int,
    src: Unit?,
    srcStride: Int,
    dst: Unit?,
    dstStride: Int,
  ) {
    TODO("Implement blurSegment")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static PassMaker* MakeMaker(float sigma, SkArenaAlloc* alloc) {
     *         SkASSERT(0 <= sigma);
     *         int gaussianWindow = SkBlurEngine::BoxBlurWindow(sigma);
     *         // This is a naive method of using the window size for the Gaussian blur to calculate the
     *         // window size for the Tent blur. This seems to work well in practice.
     *         //
     *         // We can use a single pixel to generate the effective blur area given a window size. For
     *         // the Gaussian blur this is 3 * window size. For the Tent filter this is 2 * window size.
     *         int tentWindow = 3 * gaussianWindow / 2;
     *         if (tentWindow >= 4104) {
     *             return nullptr;
     *         }
     *
     *         class Maker : public PassMaker {
     *         public:
     *             explicit Maker(int window, float sigma) : PassMaker{window, sigma} {}
     *             Pass* makePass(void* buffer, SkArenaAlloc* alloc) const override {
     *                 return TentPass::Make(this->window(), buffer, alloc);
     *             }
     *
     *             size_t bufferSizeBytes() const override {
     *                 size_t onePassSize = this->window() - 1;
     *                 // If the window is odd, then there is an obvious middle element. For even sizes 2
     *                 // passes are shifted, and the last pass has an extra element. Like this:
     *                 //       S
     *                 //    aaaAaa
     *                 //     bbBbbb
     *                 //       D
     *                 size_t bufferCount = 2 * onePassSize;
     *                 return bufferCount * sizeof(skvx::Vec<4, uint32_t>);
     *             }
     *         };
     *
     *         return alloc->make<Maker>(tentWindow, sigma);
     *     }
     * ```
     */
    public fun makeMaker(sigma: Float, alloc: SkArenaAlloc?): PassMaker {
      TODO("Implement makeMaker")
    }

    /**
     * C++ original:
     * ```cpp
     * static TentPass* Make(int window, void* buffers, SkArenaAlloc* alloc) {
     *         if (window > 4104) {
     *             return nullptr;
     *         }
     *
     *         // We don't need to store the trailing edge pixel in the buffer;
     *         int passSize = window - 1;
     *         skvx::Vec<4, uint32_t>* buffer0 = static_cast<skvx::Vec<4, uint32_t>*>(buffers);
     *         skvx::Vec<4, uint32_t>* buffer1 = buffer0 + passSize;
     *         skvx::Vec<4, uint32_t>* buffersEnd = buffer1 + passSize;
     *
     *         // Calculating the border is tricky. The border is the distance in pixels between the first
     *         // dst pixel and the first src pixel (or the last src pixel and the last dst pixel).
     *         // I will go through the odd case which is simpler, and then through the even case. Given a
     *         // stack of filters seven wide for the odd case of three passes.
     *         //
     *         //        S
     *         //     aaaAaaa
     *         //     bbbBbbb
     *         //        D
     *         //
     *         // The furthest changed pixel is when the filters are in the following configuration.
     *         //
     *         //              S
     *         //        aaaAaaa
     *         //     bbbBbbb
     *         //        D
     *         //
     *         // The A pixel is calculated using the value S, the B uses A, and the D uses B.
     *         // So, with a window size of seven the border is nine. In the odd case, the border is
     *         // window - 1.
     *         //
     *         // For even cases the filter stack is more complicated. It uses two passes
     *         // of even filters offset from each other. A stack for a width of six looks like
     *         // this.
     *         //
     *         //       S
     *         //    aaaAaa
     *         //     bbBbbb
     *         //       D
     *         //
     *         // The furthest pixel looks like this.
     *         //
     *         //            S
     *         //       aaaAaa
     *         //     bbBbbb
     *         //       D
     *         //
     *         // For a window of six, the border value is 5. In the even case the border is
     *         // window - 1.
     *         int border = window - 1;
     *
     *         int divisor = window * window;
     *         return alloc->make<TentPass>(buffer0, buffer1, buffersEnd, border, divisor);
     *     }
     * ```
     */
    public fun make(
      window: Int,
      buffers: Unit?,
      alloc: SkArenaAlloc?,
    ): TentPass {
      TODO("Implement make")
    }
  }
}
