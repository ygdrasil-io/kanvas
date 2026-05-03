package org.skia.core

import kotlin.Char
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
 * class ThreeBoxApproxPass final : public Pass {
 * public:
 *     // NB 136 is the largest sigma that will not cause a buffer full of 255 mask values to overflow
 *     // using the Gauss filter. It also limits the size of buffers used hold intermediate values.
 *     // Explanation of maximums:
 *     //   sum0 = window * 255
 *     //   sum1 = window * sum0 -> window * window * 255
 *     //   sum2 = window * sum1 -> window * window * window * 255 -> window^3 * 255
 *     //
 *     //   The value window^3 * 255 must fit in a uint32_t. So,
 *     //      window^3 < 2^32. window = 255.
 *     //
 *     //   window = floor(sigma * 3 * sqrt(2 * kPi) / 4 + 0.5)
 *     //   For window <= 255, the largest value for sigma is 136.
 *     static PassMaker* MakeMaker(float sigma, SkArenaAlloc* alloc) {
 *         SkASSERT(0 <= sigma);
 *         int window = SkBlurEngine::BoxBlurWindow(sigma);
 *         if (255 <= window) {
 *             return nullptr;
 *         }
 *
 *         class Maker : public PassMaker {
 *         public:
 *             explicit Maker(int window, float sigma) : PassMaker{window, sigma} {}
 *             Pass* makePass(void* buffer, SkArenaAlloc* alloc) const override {
 *                 return ThreeBoxApproxPass::Make(this->window(), buffer, alloc);
 *             }
 *
 *             size_t bufferSizeBytes() const override {
 *                 int window = this->window();
 *                 size_t onePassSize = window - 1;
 *                 // If the window is odd, then there is an obvious middle element. For even sizes
 *                 // 2 passes are shifted, and the last pass has an extra element. Like this:
 *                 //       S
 *                 //    aaaAaa
 *                 //     bbBbbb
 *                 //    cccCccc
 *                 //       D
 *                 size_t bufferCount = (window & 1) == 1 ? 3 * onePassSize : 3 * onePassSize + 1;
 *                 return bufferCount * sizeof(skvx::Vec<4, uint32_t>);
 *             }
 *         };
 *
 *         return alloc->make<Maker>(window, sigma);
 *     }
 *
 *     static ThreeBoxApproxPass* Make(int window, void* buffers, SkArenaAlloc* alloc) {
 *         // We don't need to store the trailing edge pixel in the buffer;
 *         int passSize = window - 1;
 *         skvx::Vec<4, uint32_t>* buffer0 = static_cast<skvx::Vec<4, uint32_t>*>(buffers);
 *         skvx::Vec<4, uint32_t>* buffer1 = buffer0 + passSize;
 *         skvx::Vec<4, uint32_t>* buffer2 = buffer1 + passSize;
 *         // If the window is odd just one buffer is needed, but if it's even, then there is one
 *         // more element on that pass.
 *         skvx::Vec<4, uint32_t>* buffersEnd = buffer2 + ((window & 1) ? passSize : passSize + 1);
 *
 *         // Calculating the border is tricky. The border is the distance in pixels between the first
 *         // dst pixel and the first src pixel (or the last src pixel and the last dst pixel).
 *         // I will go through the odd case which is simpler, and then through the even case. Given a
 *         // stack of filters seven wide for the odd case of three passes.
 *         //
 *         //        S
 *         //     aaaAaaa
 *         //     bbbBbbb
 *         //     cccCccc
 *         //        D
 *         //
 *         // The furthest changed pixel is when the filters are in the following configuration.
 *         //
 *         //                 S
 *         //           aaaAaaa
 *         //        bbbBbbb
 *         //     cccCccc
 *         //        D
 *         //
 *         // The A pixel is calculated using the value S, the B uses A, and the C uses B, and
 *         // finally D is C. So, with a window size of seven the border is nine. In the odd case, the
 *         // border is 3*((window - 1)/2).
 *         //
 *         // For even cases the filter stack is more complicated. The spec specifies two passes
 *         // of even filters and a final pass of odd filters. A stack for a width of six looks like
 *         // this.
 *         //
 *         //       S
 *         //    aaaAaa
 *         //     bbBbbb
 *         //    cccCccc
 *         //       D
 *         //
 *         // The furthest pixel looks like this.
 *         //
 *         //               S
 *         //          aaaAaa
 *         //        bbBbbb
 *         //    cccCccc
 *         //       D
 *         //
 *         // For a window of six, the border value is eight. In the even case the border is 3 *
 *         // (window/2) - 1.
 *         int border = (window & 1) == 1 ? 3 * ((window - 1) / 2) : 3 * (window / 2) - 1;
 *
 *         // If the window is odd then the divisor is just window ^ 3 otherwise,
 *         // it is window * window * (window + 1) = window ^ 3 + window ^ 2;
 *         int window2 = window * window;
 *         int window3 = window2 * window;
 *         int divisor = (window & 1) == 1 ? window3 : window3 + window2;
 *         return alloc->make<ThreeBoxApproxPass>(buffer0, buffer1, buffer2,
 *                                                buffersEnd, border, divisor);
 *     }
 *
 *     ThreeBoxApproxPass(skvx::Vec<4, uint32_t>* buffer0,
 *               skvx::Vec<4, uint32_t>* buffer1,
 *               skvx::Vec<4, uint32_t>* buffer2,
 *               skvx::Vec<4, uint32_t>* buffersEnd,
 *               int border,
 *               int divisor)
 *         : Pass{border}
 *         , fBuffer0{buffer0}
 *         , fBuffer1{buffer1}
 *         , fBuffer2{buffer2}
 *         , fBuffersEnd{buffersEnd}
 *         , fDivider(divisor) {}
 *
 * private:
 *     void startBlur() override {
 *         skvx::Vec<4, uint32_t> zero = {0u, 0u, 0u, 0u};
 *         zero.store(fSum0);
 *         zero.store(fSum1);
 *         auto half = fDivider.half();
 *         skvx::Vec<4, uint32_t>{half, half, half, half}.store(fSum2);
 *         sk_bzero(fBuffer0, (fBuffersEnd - fBuffer0) * sizeof(skvx::Vec<4, uint32_t>));
 *
 *         fBuffer0Cursor = fBuffer0;
 *         fBuffer1Cursor = fBuffer1;
 *         fBuffer2Cursor = fBuffer2;
 *     }
 *
 *     // GaussPass implements the common three pass box filter approximation of Gaussian blur,
 *     // but combines all three passes into a single pass. This approach is facilitated by three
 *     // circular buffers the width of the window which track values for trailing edges of each of
 *     // the three passes. This allows the algorithm to use more precision in the calculation
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
 *     //    sum2_n+1 = sum2_n + sum1_n+1
 *     //
 *     //    sum2_n+1 / window^3 is the new value of the destination pixel.
 *     //
 *     // Reduce the sums by the trailing edges which were stored in the circular buffers for the
 *     // next go around. This is the case for odd sized windows, even windows the the third
 *     // circular buffer is one larger then the first two circular buffers.
 *     //
 *     //    sum2_n+2 = sum2_n+1 - buffer2[i];
 *     //    buffer2[i] = sum1;
 *     //    sum1_n+2 = sum1_n+1 - buffer1[i];
 *     //    buffer1[i] = sum0;
 *     //    sum0_n+2 = sum0_n+1 - buffer0[i];
 *     //    buffer0[i] = leading edge
 *     void blurSegment(
 *             int n, const void* src, int srcStride, void* dst, int dstStride) override {
 *         const uint32_t* src32 = reinterpret_cast<const uint32_t*>(src);
 *         uint32_t* dst32 = reinterpret_cast<uint32_t*>(dst);
 * #if SK_CPU_LSX_LEVEL >= SK_CPU_LSX_LEVEL_LSX
 *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
 *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
 *         skvx::Vec<4, uint32_t>* buffer2Cursor = fBuffer2Cursor;
 *         v4u32 sum0 = __lsx_vld(fSum0, 0); // same as skvx::Vec<4, uint32_t>::Load(fSum0);
 *         v4u32 sum1 = __lsx_vld(fSum1, 0);
 *         v4u32 sum2 = __lsx_vld(fSum2, 0);
 *
 *         auto processValue = [&](v4u32& vLeadingEdge){
 *           sum0 += vLeadingEdge;
 *           sum1 += sum0;
 *           sum2 += sum1;
 *
 *           v4u32 divisorFactor = __lsx_vreplgr2vr_w(fDivider.divisorFactor());
 *           v4u32 blurred = __lsx_vmuh_w(divisorFactor, sum2);
 *
 *           v4u32 buffer2Value = __lsx_vld(buffer2Cursor, 0); //Not fBuffer0Cursor, out of bounds.
 *           sum2 -= buffer2Value;
 *           __lsx_vst(sum1, (void *)buffer2Cursor, 0);
 *           buffer2Cursor = (buffer2Cursor + 1) < fBuffersEnd ? buffer2Cursor + 1 : fBuffer2;
 *           v4u32 buffer1Value = __lsx_vld(buffer1Cursor, 0);
 *           sum1 -= buffer1Value;
 *           __lsx_vst(sum0, (void *)buffer1Cursor, 0);
 *           buffer1Cursor = (buffer1Cursor + 1) < fBuffer2 ? buffer1Cursor + 1 : fBuffer1;
 *           v4u32 buffer0Value = __lsx_vld(buffer0Cursor, 0);
 *           sum0 -= buffer0Value;
 *           __lsx_vst(vLeadingEdge, (void *)buffer0Cursor, 0);
 *           buffer0Cursor = (buffer0Cursor + 1) < fBuffer1 ? buffer0Cursor + 1 : fBuffer0;
 *
 *           v16u8 shuf = {0x0,0x4,0x8,0xc,0x0};
 *           v16u8 ret = __lsx_vshuf_b(blurred, blurred, shuf);
 *           return ret;
 *         };
 *
 *         v4u32 zero = __lsx_vldi(0x0);
 *         if (!src32 && !dst32) {
 *             while (n --> 0) {
 *                 (void)processValue(zero);
 *             }
 *         } else if (src32 && !dst32) {
 *             while (n --> 0) {
 *                 v4u32 edge = __lsx_vinsgr2vr_w(zero, *src32, 0);
 *                 edge = __lsx_vilvl_b(zero, edge);
 *                 edge = __lsx_vilvl_h(zero, edge);
 *                 (void)processValue(edge);
 *                 src32 += srcStride;
 *             }
 *         } else if (!src32 && dst32) {
 *             while (n --> 0) {
 *                 v4u32 ret = processValue(zero);
 *                 __lsx_vstelm_w(ret, dst32, 0, 0); // 3rd is offset, 4th is idx.
 *                 dst32 += dstStride;
 *             }
 *         } else if (src32 && dst32) {
 *             while (n --> 0) {
 *                 v4u32 edge = __lsx_vinsgr2vr_w(zero, *src32, 0);
 *                 edge = __lsx_vilvl_b(zero, edge);
 *                 edge = __lsx_vilvl_h(zero, edge);
 *                 v4u32 ret = processValue(edge);
 *                 __lsx_vstelm_w(ret, dst32, 0, 0);
 *                 src32 += srcStride;
 *                 dst32 += dstStride;
 *             }
 *         }
 *
 *         // Store the state
 *         fBuffer0Cursor = buffer0Cursor;
 *         fBuffer1Cursor = buffer1Cursor;
 *         fBuffer2Cursor = buffer2Cursor;
 *
 *         __lsx_vst(sum0, fSum0, 0);
 *         __lsx_vst(sum1, fSum1, 0);
 *         __lsx_vst(sum2, fSum2, 0);
 * #else
 *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
 *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
 *         skvx::Vec<4, uint32_t>* buffer2Cursor = fBuffer2Cursor;
 *         skvx::Vec<4, uint32_t> sum0 = skvx::Vec<4, uint32_t>::Load(fSum0);
 *         skvx::Vec<4, uint32_t> sum1 = skvx::Vec<4, uint32_t>::Load(fSum1);
 *         skvx::Vec<4, uint32_t> sum2 = skvx::Vec<4, uint32_t>::Load(fSum2);
 *
 *         // Given an expanded input pixel, move the window ahead using the leadingEdge value.
 *         auto processValue = [&](const skvx::Vec<4, uint32_t>& leadingEdge) {
 *             sum0 += leadingEdge;
 *             sum1 += sum0;
 *             sum2 += sum1;
 *
 *             skvx::Vec<4, uint32_t> blurred = fDivider.divide(sum2);
 *
 *             sum2 -= *buffer2Cursor;
 *             *buffer2Cursor = sum1;
 *             buffer2Cursor = (buffer2Cursor + 1) < fBuffersEnd ? buffer2Cursor + 1 : fBuffer2;
 *             sum1 -= *buffer1Cursor;
 *             *buffer1Cursor = sum0;
 *             buffer1Cursor = (buffer1Cursor + 1) < fBuffer2 ? buffer1Cursor + 1 : fBuffer1;
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
 *         fBuffer2Cursor = buffer2Cursor;
 *
 *         sum0.store(fSum0);
 *         sum1.store(fSum1);
 *         sum2.store(fSum2);
 * #endif
 *     }
 *
 *     skvx::Vec<4, uint32_t>* const fBuffer0;
 *     skvx::Vec<4, uint32_t>* const fBuffer1;
 *     skvx::Vec<4, uint32_t>* const fBuffer2;
 *     skvx::Vec<4, uint32_t>* const fBuffersEnd;
 *     const skvx::ScaledDividerU32 fDivider;
 *
 *     // blur state
 *     char fSum0[sizeof(skvx::Vec<4, uint32_t>)];
 *     char fSum1[sizeof(skvx::Vec<4, uint32_t>)];
 *     char fSum2[sizeof(skvx::Vec<4, uint32_t>)];
 *     skvx::Vec<4, uint32_t>* fBuffer0Cursor;
 *     skvx::Vec<4, uint32_t>* fBuffer1Cursor;
 *     skvx::Vec<4, uint32_t>* fBuffer2Cursor;
 * }
 * ```
 */
public class ThreeBoxApproxPass public constructor(
  buffer0: Vec4<UInt>,
  buffer1: Vec4<UInt>,
  buffer2: Vec4<UInt>,
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
   * skvx::Vec<4, uint32_t>* const fBuffer2
   * ```
   */
  private val fBuffer2: Vec4<UInt> = TODO("Initialize fBuffer2")

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
   * char fSum0
   * ```
   */
  private var fSum0: Char = TODO("Initialize fSum0")

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
   * char fSum2[sizeof(skvx::Vec<4, uint32_t>)]
   * ```
   */
  private var fSum2: CharArray = TODO("Initialize fSum2")

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
   * skvx::Vec<4, uint32_t>* fBuffer2Cursor
   * ```
   */
  private var fBuffer2Cursor: Vec4<UInt> = TODO("Initialize fBuffer2Cursor")

  /**
   * C++ original:
   * ```cpp
   * void startBlur() override {
   *         skvx::Vec<4, uint32_t> zero = {0u, 0u, 0u, 0u};
   *         zero.store(fSum0);
   *         zero.store(fSum1);
   *         auto half = fDivider.half();
   *         skvx::Vec<4, uint32_t>{half, half, half, half}.store(fSum2);
   *         sk_bzero(fBuffer0, (fBuffersEnd - fBuffer0) * sizeof(skvx::Vec<4, uint32_t>));
   *
   *         fBuffer0Cursor = fBuffer0;
   *         fBuffer1Cursor = fBuffer1;
   *         fBuffer2Cursor = fBuffer2;
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
   * #if SK_CPU_LSX_LEVEL >= SK_CPU_LSX_LEVEL_LSX
   *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
   *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
   *         skvx::Vec<4, uint32_t>* buffer2Cursor = fBuffer2Cursor;
   *         v4u32 sum0 = __lsx_vld(fSum0, 0); // same as skvx::Vec<4, uint32_t>::Load(fSum0);
   *         v4u32 sum1 = __lsx_vld(fSum1, 0);
   *         v4u32 sum2 = __lsx_vld(fSum2, 0);
   *
   *         auto processValue = [&](v4u32& vLeadingEdge){
   *           sum0 += vLeadingEdge;
   *           sum1 += sum0;
   *           sum2 += sum1;
   *
   *           v4u32 divisorFactor = __lsx_vreplgr2vr_w(fDivider.divisorFactor());
   *           v4u32 blurred = __lsx_vmuh_w(divisorFactor, sum2);
   *
   *           v4u32 buffer2Value = __lsx_vld(buffer2Cursor, 0); //Not fBuffer0Cursor, out of bounds.
   *           sum2 -= buffer2Value;
   *           __lsx_vst(sum1, (void *)buffer2Cursor, 0);
   *           buffer2Cursor = (buffer2Cursor + 1) < fBuffersEnd ? buffer2Cursor + 1 : fBuffer2;
   *           v4u32 buffer1Value = __lsx_vld(buffer1Cursor, 0);
   *           sum1 -= buffer1Value;
   *           __lsx_vst(sum0, (void *)buffer1Cursor, 0);
   *           buffer1Cursor = (buffer1Cursor + 1) < fBuffer2 ? buffer1Cursor + 1 : fBuffer1;
   *           v4u32 buffer0Value = __lsx_vld(buffer0Cursor, 0);
   *           sum0 -= buffer0Value;
   *           __lsx_vst(vLeadingEdge, (void *)buffer0Cursor, 0);
   *           buffer0Cursor = (buffer0Cursor + 1) < fBuffer1 ? buffer0Cursor + 1 : fBuffer0;
   *
   *           v16u8 shuf = {0x0,0x4,0x8,0xc,0x0};
   *           v16u8 ret = __lsx_vshuf_b(blurred, blurred, shuf);
   *           return ret;
   *         };
   *
   *         v4u32 zero = __lsx_vldi(0x0);
   *         if (!src32 && !dst32) {
   *             while (n --> 0) {
   *                 (void)processValue(zero);
   *             }
   *         } else if (src32 && !dst32) {
   *             while (n --> 0) {
   *                 v4u32 edge = __lsx_vinsgr2vr_w(zero, *src32, 0);
   *                 edge = __lsx_vilvl_b(zero, edge);
   *                 edge = __lsx_vilvl_h(zero, edge);
   *                 (void)processValue(edge);
   *                 src32 += srcStride;
   *             }
   *         } else if (!src32 && dst32) {
   *             while (n --> 0) {
   *                 v4u32 ret = processValue(zero);
   *                 __lsx_vstelm_w(ret, dst32, 0, 0); // 3rd is offset, 4th is idx.
   *                 dst32 += dstStride;
   *             }
   *         } else if (src32 && dst32) {
   *             while (n --> 0) {
   *                 v4u32 edge = __lsx_vinsgr2vr_w(zero, *src32, 0);
   *                 edge = __lsx_vilvl_b(zero, edge);
   *                 edge = __lsx_vilvl_h(zero, edge);
   *                 v4u32 ret = processValue(edge);
   *                 __lsx_vstelm_w(ret, dst32, 0, 0);
   *                 src32 += srcStride;
   *                 dst32 += dstStride;
   *             }
   *         }
   *
   *         // Store the state
   *         fBuffer0Cursor = buffer0Cursor;
   *         fBuffer1Cursor = buffer1Cursor;
   *         fBuffer2Cursor = buffer2Cursor;
   *
   *         __lsx_vst(sum0, fSum0, 0);
   *         __lsx_vst(sum1, fSum1, 0);
   *         __lsx_vst(sum2, fSum2, 0);
   * #else
   *         skvx::Vec<4, uint32_t>* buffer0Cursor = fBuffer0Cursor;
   *         skvx::Vec<4, uint32_t>* buffer1Cursor = fBuffer1Cursor;
   *         skvx::Vec<4, uint32_t>* buffer2Cursor = fBuffer2Cursor;
   *         skvx::Vec<4, uint32_t> sum0 = skvx::Vec<4, uint32_t>::Load(fSum0);
   *         skvx::Vec<4, uint32_t> sum1 = skvx::Vec<4, uint32_t>::Load(fSum1);
   *         skvx::Vec<4, uint32_t> sum2 = skvx::Vec<4, uint32_t>::Load(fSum2);
   *
   *         // Given an expanded input pixel, move the window ahead using the leadingEdge value.
   *         auto processValue = [&](const skvx::Vec<4, uint32_t>& leadingEdge) {
   *             sum0 += leadingEdge;
   *             sum1 += sum0;
   *             sum2 += sum1;
   *
   *             skvx::Vec<4, uint32_t> blurred = fDivider.divide(sum2);
   *
   *             sum2 -= *buffer2Cursor;
   *             *buffer2Cursor = sum1;
   *             buffer2Cursor = (buffer2Cursor + 1) < fBuffersEnd ? buffer2Cursor + 1 : fBuffer2;
   *             sum1 -= *buffer1Cursor;
   *             *buffer1Cursor = sum0;
   *             buffer1Cursor = (buffer1Cursor + 1) < fBuffer2 ? buffer1Cursor + 1 : fBuffer1;
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
   *         fBuffer2Cursor = buffer2Cursor;
   *
   *         sum0.store(fSum0);
   *         sum1.store(fSum1);
   *         sum2.store(fSum2);
   * #endif
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
     *         int window = SkBlurEngine::BoxBlurWindow(sigma);
     *         if (255 <= window) {
     *             return nullptr;
     *         }
     *
     *         class Maker : public PassMaker {
     *         public:
     *             explicit Maker(int window, float sigma) : PassMaker{window, sigma} {}
     *             Pass* makePass(void* buffer, SkArenaAlloc* alloc) const override {
     *                 return ThreeBoxApproxPass::Make(this->window(), buffer, alloc);
     *             }
     *
     *             size_t bufferSizeBytes() const override {
     *                 int window = this->window();
     *                 size_t onePassSize = window - 1;
     *                 // If the window is odd, then there is an obvious middle element. For even sizes
     *                 // 2 passes are shifted, and the last pass has an extra element. Like this:
     *                 //       S
     *                 //    aaaAaa
     *                 //     bbBbbb
     *                 //    cccCccc
     *                 //       D
     *                 size_t bufferCount = (window & 1) == 1 ? 3 * onePassSize : 3 * onePassSize + 1;
     *                 return bufferCount * sizeof(skvx::Vec<4, uint32_t>);
     *             }
     *         };
     *
     *         return alloc->make<Maker>(window, sigma);
     *     }
     * ```
     */
    public fun makeMaker(sigma: Float, alloc: SkArenaAlloc?): PassMaker {
      TODO("Implement makeMaker")
    }

    /**
     * C++ original:
     * ```cpp
     * static ThreeBoxApproxPass* Make(int window, void* buffers, SkArenaAlloc* alloc) {
     *         // We don't need to store the trailing edge pixel in the buffer;
     *         int passSize = window - 1;
     *         skvx::Vec<4, uint32_t>* buffer0 = static_cast<skvx::Vec<4, uint32_t>*>(buffers);
     *         skvx::Vec<4, uint32_t>* buffer1 = buffer0 + passSize;
     *         skvx::Vec<4, uint32_t>* buffer2 = buffer1 + passSize;
     *         // If the window is odd just one buffer is needed, but if it's even, then there is one
     *         // more element on that pass.
     *         skvx::Vec<4, uint32_t>* buffersEnd = buffer2 + ((window & 1) ? passSize : passSize + 1);
     *
     *         // Calculating the border is tricky. The border is the distance in pixels between the first
     *         // dst pixel and the first src pixel (or the last src pixel and the last dst pixel).
     *         // I will go through the odd case which is simpler, and then through the even case. Given a
     *         // stack of filters seven wide for the odd case of three passes.
     *         //
     *         //        S
     *         //     aaaAaaa
     *         //     bbbBbbb
     *         //     cccCccc
     *         //        D
     *         //
     *         // The furthest changed pixel is when the filters are in the following configuration.
     *         //
     *         //                 S
     *         //           aaaAaaa
     *         //        bbbBbbb
     *         //     cccCccc
     *         //        D
     *         //
     *         // The A pixel is calculated using the value S, the B uses A, and the C uses B, and
     *         // finally D is C. So, with a window size of seven the border is nine. In the odd case, the
     *         // border is 3*((window - 1)/2).
     *         //
     *         // For even cases the filter stack is more complicated. The spec specifies two passes
     *         // of even filters and a final pass of odd filters. A stack for a width of six looks like
     *         // this.
     *         //
     *         //       S
     *         //    aaaAaa
     *         //     bbBbbb
     *         //    cccCccc
     *         //       D
     *         //
     *         // The furthest pixel looks like this.
     *         //
     *         //               S
     *         //          aaaAaa
     *         //        bbBbbb
     *         //    cccCccc
     *         //       D
     *         //
     *         // For a window of six, the border value is eight. In the even case the border is 3 *
     *         // (window/2) - 1.
     *         int border = (window & 1) == 1 ? 3 * ((window - 1) / 2) : 3 * (window / 2) - 1;
     *
     *         // If the window is odd then the divisor is just window ^ 3 otherwise,
     *         // it is window * window * (window + 1) = window ^ 3 + window ^ 2;
     *         int window2 = window * window;
     *         int window3 = window2 * window;
     *         int divisor = (window & 1) == 1 ? window3 : window3 + window2;
     *         return alloc->make<ThreeBoxApproxPass>(buffer0, buffer1, buffer2,
     *                                                buffersEnd, border, divisor);
     *     }
     * ```
     */
    public fun make(
      window: Int,
      buffers: Unit?,
      alloc: SkArenaAlloc?,
    ): ThreeBoxApproxPass {
      TODO("Implement make")
    }
  }
}
