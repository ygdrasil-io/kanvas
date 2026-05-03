package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkImageInfo

/**
 * C++ original:
 * ```cpp
 * struct SkWritePixelsRec {
 *     SkWritePixelsRec(const SkImageInfo& info, const void* pixels, size_t rowBytes, int x, int y)
 *         : fPixels(pixels)
 *         , fRowBytes(rowBytes)
 *         , fInfo(info)
 *         , fX(x)
 *         , fY(y)
 *     {}
 *
 *     SkWritePixelsRec(const SkPixmap& pm, int x, int y)
 *         : fPixels(pm.addr())
 *         , fRowBytes(pm.rowBytes())
 *         , fInfo(pm.info())
 *         , fX(x)
 *         , fY(y)
 *     {}
 *
 *     const void* fPixels;
 *     size_t      fRowBytes;
 *     SkImageInfo fInfo;
 *     int         fX;
 *     int         fY;
 *
 *     /*
 *      *  On true, may have modified its fields (except fRowBytes) to make it a legal subset
 *      *  of the specified dst width/height.
 *      *
 *      *  On false, leaves self unchanged, but indicates that it does not overlap dst, or
 *      *  is not valid (e.g. bad fInfo) for writePixels().
 *      */
 *     bool trim(int dstWidth, int dstHeight);
 * }
 * ```
 */
public data class SkWritePixelsRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const void* fPixels
   * ```
   */
  public val fPixels: Unit?,
  /**
   * C++ original:
   * ```cpp
   * size_t      fRowBytes
   * ```
   */
  public var fRowBytes: Int,
  /**
   * C++ original:
   * ```cpp
   * SkImageInfo fInfo
   * ```
   */
  public var fInfo: SkImageInfo,
  /**
   * C++ original:
   * ```cpp
   * int         fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * int         fY
   * ```
   */
  public var fY: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkWritePixelsRec::trim(int dstWidth, int dstHeight) {
   *     if (nullptr == fPixels || fRowBytes < fInfo.minRowBytes()) {
   *         return false;
   *     }
   *     if (0 >= fInfo.width() || 0 >= fInfo.height()) {
   *         return false;
   *     }
   *
   *     int x = fX;
   *     int y = fY;
   *     SkIRect dstR = SkIRect::MakeXYWH(x, y, fInfo.width(), fInfo.height());
   *     if (!dstR.intersect({0, 0, dstWidth, dstHeight})) {
   *         return false;
   *     }
   *
   *     // if x or y are negative, then we have to adjust pixels
   *     if (x > 0) {
   *         x = 0;
   *     }
   *     if (y > 0) {
   *         y = 0;
   *     }
   *     // here x,y are either 0 or negative
   *     // we negate and add them so UBSAN (pointer-overflow) doesn't get confused.
   *     fPixels = ((const char*)fPixels + -y*fRowBytes + -x*fInfo.bytesPerPixel());
   *     // the intersect may have shrunk info's logical size
   *     fInfo = fInfo.makeDimensions(dstR.size());
   *     fX = dstR.x();
   *     fY = dstR.y();
   *
   *     return true;
   * }
   * ```
   */
  public fun trim(dstWidth: Int, dstHeight: Int): Boolean {
    TODO("Implement trim")
  }
}
