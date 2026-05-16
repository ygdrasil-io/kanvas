package org.skia.core

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class Pass {
 * public:
 *     explicit Pass(int border) : fBorder(border) {}
 *     virtual ~Pass() = default;
 *
 *     // T is type of the pixel format for the color type.
 *     template <typename T>
 *     void blur(int srcLeft, int srcRight, int dstRight,
 *               const T* src, int srcStride,
 *               T* dst, int dstStride) {
 *         this->startBlur();
 *
 *         auto srcStart = srcLeft - fBorder,
 *                 srcEnd   = srcRight - fBorder,
 *                 dstEnd   = dstRight,
 *                 srcIdx   = srcStart,
 *                 dstIdx   = 0;
 *
 *         const T* srcCursor = src;
 *         T* dstCursor = dst;
 *
 *         if (dstIdx < srcIdx) {
 *             // The destination pixels are not effected by the src pixels,
 *             // change to zero as per the spec.
 *             // https://drafts.fxtf.org/filter-effects/#FilterPrimitivesOverviewIntro
 *             int commonEnd = std::min(srcIdx, dstEnd);
 *             while (dstIdx < commonEnd) {
 *                 *dstCursor = 0;
 *                 dstCursor += dstStride;
 *                 SK_PREFETCH(dstCursor);
 *                 dstIdx++;
 *             }
 *         } else if (srcIdx < dstIdx) {
 *             // The edge of the source is before the edge of the destination. Calculate the sums for
 *             // the pixels before the start of the destination.
 *             if (int commonEnd = std::min(dstIdx, srcEnd); srcIdx < commonEnd) {
 *                 // Preload the blur with values from src before dst is entered.
 *                 int n = commonEnd - srcIdx;
 *                 this->blurSegment(n, srcCursor, srcStride, nullptr, 0);
 *                 srcIdx += n;
 *                 srcCursor += n * srcStride;
 *             }
 *             if (srcIdx < dstIdx) {
 *                 // The weird case where src is out of pixels before dst is even started.
 *                 int n = dstIdx - srcIdx;
 *                 this->blurSegment(n, nullptr, 0, nullptr, 0);
 *                 srcIdx += n;
 *             }
 *         }
 *
 *         if (int commonEnd = std::min(dstEnd, srcEnd); dstIdx < commonEnd) {
 *             // Both srcIdx and dstIdx are in sync now, and can run in a 1:1 fashion. This is the
 *             // normal mode of operation.
 *             SkASSERT(srcIdx == dstIdx);
 *
 *             int n = commonEnd - dstIdx;
 *             this->blurSegment(n, srcCursor, srcStride, dstCursor, dstStride);
 *             srcCursor += n * srcStride;
 *             dstCursor += n * dstStride;
 *             dstIdx += n;
 *             srcIdx += n;
 *         }
 *
 *         // Drain the remaining blur values into dst assuming 0's for the leading edge.
 *         if (dstIdx < dstEnd) {
 *             int n = dstEnd - dstIdx;
 *             this->blurSegment(n, nullptr, 0, dstCursor, dstStride);
 *         }
 *     }
 *
 * protected:
 *     virtual void startBlur() = 0;
 *     virtual void blurSegment(
 *             int n, const void* src, int srcStride, void* dst, int dstStride) = 0;
 *
 * private:
 *     const int fBorder;
 * }
 * ```
 */
public abstract class Pass public constructor(
  border: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const int fBorder
   * ```
   */
  private val fBorder: Int = TODO("Initialize fBorder")

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void blur(int srcLeft, int srcRight, int dstRight,
   *               const T* src, int srcStride,
   *               T* dst, int dstStride) {
   *         this->startBlur();
   *
   *         auto srcStart = srcLeft - fBorder,
   *                 srcEnd   = srcRight - fBorder,
   *                 dstEnd   = dstRight,
   *                 srcIdx   = srcStart,
   *                 dstIdx   = 0;
   *
   *         const T* srcCursor = src;
   *         T* dstCursor = dst;
   *
   *         if (dstIdx < srcIdx) {
   *             // The destination pixels are not effected by the src pixels,
   *             // change to zero as per the spec.
   *             // https://drafts.fxtf.org/filter-effects/#FilterPrimitivesOverviewIntro
   *             int commonEnd = std::min(srcIdx, dstEnd);
   *             while (dstIdx < commonEnd) {
   *                 *dstCursor = 0;
   *                 dstCursor += dstStride;
   *                 SK_PREFETCH(dstCursor);
   *                 dstIdx++;
   *             }
   *         } else if (srcIdx < dstIdx) {
   *             // The edge of the source is before the edge of the destination. Calculate the sums for
   *             // the pixels before the start of the destination.
   *             if (int commonEnd = std::min(dstIdx, srcEnd); srcIdx < commonEnd) {
   *                 // Preload the blur with values from src before dst is entered.
   *                 int n = commonEnd - srcIdx;
   *                 this->blurSegment(n, srcCursor, srcStride, nullptr, 0);
   *                 srcIdx += n;
   *                 srcCursor += n * srcStride;
   *             }
   *             if (srcIdx < dstIdx) {
   *                 // The weird case where src is out of pixels before dst is even started.
   *                 int n = dstIdx - srcIdx;
   *                 this->blurSegment(n, nullptr, 0, nullptr, 0);
   *                 srcIdx += n;
   *             }
   *         }
   *
   *         if (int commonEnd = std::min(dstEnd, srcEnd); dstIdx < commonEnd) {
   *             // Both srcIdx and dstIdx are in sync now, and can run in a 1:1 fashion. This is the
   *             // normal mode of operation.
   *             SkASSERT(srcIdx == dstIdx);
   *
   *             int n = commonEnd - dstIdx;
   *             this->blurSegment(n, srcCursor, srcStride, dstCursor, dstStride);
   *             srcCursor += n * srcStride;
   *             dstCursor += n * dstStride;
   *             dstIdx += n;
   *             srcIdx += n;
   *         }
   *
   *         // Drain the remaining blur values into dst assuming 0's for the leading edge.
   *         if (dstIdx < dstEnd) {
   *             int n = dstEnd - dstIdx;
   *             this->blurSegment(n, nullptr, 0, dstCursor, dstStride);
   *         }
   *     }
   * ```
   */
  public fun <T> blur(
    srcLeft: Int,
    srcRight: Int,
    dstRight: Int,
    src: T?,
    srcStride: Int,
    dst: T?,
    dstStride: Int,
  ) {
    TODO("Implement blur")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void startBlur() = 0
   * ```
   */
  protected abstract fun startBlur()

  /**
   * C++ original:
   * ```cpp
   * virtual void blurSegment(
   *             int n, const void* src, int srcStride, void* dst, int dstStride) = 0
   * ```
   */
  protected abstract fun blurSegment(
    n: Int,
    src: Unit?,
    srcStride: Int,
    dst: Unit?,
    dstStride: Int,
  )
}
