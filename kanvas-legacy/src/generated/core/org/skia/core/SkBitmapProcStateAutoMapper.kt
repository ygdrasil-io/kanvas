package org.skia.core

import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkFixed3232

/**
 * C++ original:
 * ```cpp
 * class SkBitmapProcStateAutoMapper {
 * public:
 *     SkBitmapProcStateAutoMapper(const SkBitmapProcState& s, int x, int y,
 *                                 SkPoint* scalarPoint = nullptr) {
 *         SkPoint pt = s.fInvMatrix.mapPoint({
 *             SkIntToScalar(x) + SK_ScalarHalf,
 *             SkIntToScalar(y) + SK_ScalarHalf,
 *         });
 *
 *         SkFixed biasX = 0, biasY = 0;
 *         if (s.fBilerp) {
 *             biasX = s.fFilterOneX >> 1;
 *             biasY = s.fFilterOneY >> 1;
 *         } else {
 *             // Our rasterizer biases upward. That is a rect from 0.5...1.5 fills pixel 1 and not
 *             // pixel 0. To make an image that is mapped 1:1 with device pixels but at a half pixel
 *             // offset select every pixel from the src image once we make exact integer pixel sample
 *             // values round down not up. Note that a mirror mapping will not have this property.
 *             biasX = 1;
 *             biasY = 1;
 *         }
 *
 *         // punt to unsigned for defined underflow behavior
 *         fX = (SkFixed3232)((uint64_t)SkScalarToFixed3232(pt.x()) -
 *                            (uint64_t)SkFixedToFixed3232(biasX));
 *         fY = (SkFixed3232)((uint64_t)SkScalarToFixed3232(pt.y()) -
 *                            (uint64_t)SkFixedToFixed3232(biasY));
 *
 *         if (scalarPoint) {
 *             scalarPoint->set(pt.x() - SkFixedToScalar(biasX),
 *                              pt.y() - SkFixedToScalar(biasY));
 *         }
 *     }
 *
 *     SkFixed3232 fixed3232X() const { return fX; }
 *     SkFixed3232 fixed3232Y() const { return fY; }
 *
 *     SkFixed fixedX() const { return SkFixed3232ToFixed(fX); }
 *     SkFixed fixedY() const { return SkFixed3232ToFixed(fY); }
 *
 *     int intX() const { return SkFixed3232ToInt(fX); }
 *     int intY() const { return SkFixed3232ToInt(fY); }
 *
 * private:
 *     SkFixed3232 fX, fY;
 * }
 * ```
 */
public data class SkBitmapProcStateAutoMapper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFixed3232 fX
   * ```
   */
  private var fX: SkFixed3232,
  /**
   * C++ original:
   * ```cpp
   * SkFixed3232 fX, fY
   * ```
   */
  private var fY: SkFixed3232,
) {
  /**
   * C++ original:
   * ```cpp
   * SkFixed3232 fixed3232X() const { return fX; }
   * ```
   */
  public fun fixed3232X(): SkFixed3232 {
    TODO("Implement fixed3232X")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed3232 fixed3232Y() const { return fY; }
   * ```
   */
  public fun fixed3232Y(): SkFixed3232 {
    TODO("Implement fixed3232Y")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed fixedX() const { return SkFixed3232ToFixed(fX); }
   * ```
   */
  public fun fixedX(): SkFixed {
    TODO("Implement fixedX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed fixedY() const { return SkFixed3232ToFixed(fY); }
   * ```
   */
  public fun fixedY(): SkFixed {
    TODO("Implement fixedY")
  }

  /**
   * C++ original:
   * ```cpp
   * int intX() const { return SkFixed3232ToInt(fX); }
   * ```
   */
  public fun intX(): Int {
    TODO("Implement intX")
  }

  /**
   * C++ original:
   * ```cpp
   * int intY() const { return SkFixed3232ToInt(fY); }
   * ```
   */
  public fun intY(): Int {
    TODO("Implement intY")
  }
}
