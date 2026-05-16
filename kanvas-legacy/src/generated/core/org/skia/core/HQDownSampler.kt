package org.skia.core

import org.skia.foundation.SkMipmapDownSampler
import org.skia.foundation.SkPixmap

/**
 * C++ original:
 * ```cpp
 * struct HQDownSampler : SkMipmapDownSampler {
 *     FilterProc* proc_1_2 = nullptr;
 *     FilterProc* proc_1_3 = nullptr;
 *     FilterProc* proc_2_1 = nullptr;
 *     FilterProc* proc_2_2 = nullptr;
 *     FilterProc* proc_2_3 = nullptr;
 *     FilterProc* proc_3_1 = nullptr;
 *     FilterProc* proc_3_2 = nullptr;
 *     FilterProc* proc_3_3 = nullptr;
 *
 *     void buildLevel(const SkPixmap& dst, const SkPixmap& src) override;
 * }
 * ```
 */
public open class HQDownSampler public constructor(
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_1_2 = nullptr
   * ```
   */
  public var proc12: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_1_3 = nullptr
   * ```
   */
  public var proc13: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_2_1 = nullptr
   * ```
   */
  public var proc21: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_2_2 = nullptr
   * ```
   */
  public var proc22: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_2_3 = nullptr
   * ```
   */
  public var proc23: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_3_1 = nullptr
   * ```
   */
  public var proc31: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_3_2 = nullptr
   * ```
   */
  public var proc32: FilterProc?,
  /**
   * C++ original:
   * ```cpp
   * FilterProc* proc_3_3 = nullptr
   * ```
   */
  public var proc33: FilterProc?,
) : SkMipmapDownSampler(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void HQDownSampler::buildLevel(const SkPixmap& dst, const SkPixmap& src) {
   *     const int width = src.width();
   *     const int height = src.height();
   *
   *     FilterProc* proc;
   *     if (height & 1) {
   *         if (height == 1) {        // src-height is 1
   *             if (width & 1) {      // src-width is 3
   *                 proc = proc_3_1;
   *             } else {              // src-width is 2
   *                 proc = proc_2_1;
   *             }
   *         } else {                  // src-height is 3
   *             if (width & 1) {
   *                 if (width == 1) { // src-width is 1
   *                     proc = proc_1_3;
   *                 } else {          // src-width is 3
   *                     proc = proc_3_3;
   *                 }
   *             } else {              // src-width is 2
   *                 proc = proc_2_3;
   *             }
   *         }
   *     } else {                      // src-height is 2
   *         if (width & 1) {
   *             if (width == 1) {     // src-width is 1
   *                 proc = proc_1_2;
   *             } else {              // src-width is 3
   *                 proc = proc_3_2;
   *             }
   *         } else {                  // src-width is 2
   *             proc = proc_2_2;
   *         }
   *     }
   *
   *     const void* srcBasePtr = src.addr();
   *     const size_t srcRB = src.rowBytes();
   *     void* dstBasePtr = dst.writable_addr();
   *
   *     for (int y = 0; y < dst.height(); y++) {
   *         proc(dstBasePtr, srcBasePtr, srcRB, dst.width());
   *         srcBasePtr = (const char*)srcBasePtr + srcRB * 2; // jump two rows
   *         dstBasePtr = (      char*)dstBasePtr + dst.rowBytes();
   *     }
   * }
   * ```
   */
  public override fun buildLevel(dst: SkPixmap, src: SkPixmap) {
    TODO("Implement buildLevel")
  }
}
