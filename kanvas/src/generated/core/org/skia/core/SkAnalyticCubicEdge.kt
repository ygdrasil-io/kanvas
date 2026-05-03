package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkAnalyticCubicEdge : public SkAnalyticEdge {
 *     SkFixed fCx, fCy;
 *     SkFixed fCDx, fCDy;
 *     SkFixed fCDDx, fCDDy;
 *     SkFixed fCDDDx, fCDDDy;
 *     SkFixed fCLastX, fCLastY;
 *
 *     SkFixed fSnappedY; // to make sure that y is increasing with smooth jump and snapping
 *
 *     uint8_t fCubicDShift;   // applied to fCDx and fCDy
 *
 *     bool setCubicWithoutUpdate(const SkPoint pts[4], int shiftUp);
 *     bool setCubic(const SkPoint pts[4]);
 *     bool updateCubic();
 *     inline void keepContinuous() {
 *         SkASSERT(SkAbs32(fX - SkFixedMul(fDX, fY - SnapY(fCy)) - fCx) < SK_Fixed1);
 *         fCx = fX;
 *         fSnappedY = fY;
 *     }
 * }
 * ```
 */
public open class SkAnalyticCubicEdge public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCx
   * ```
   */
  public var fCx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCx, fCy
   * ```
   */
  public var fCy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDx
   * ```
   */
  public var fCDx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDx, fCDy
   * ```
   */
  public var fCDy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDDx
   * ```
   */
  public var fCDDx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDDx, fCDDy
   * ```
   */
  public var fCDDy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDDDx
   * ```
   */
  public var fCDDDx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDDDx, fCDDDy
   * ```
   */
  public var fCDDDy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCLastX
   * ```
   */
  public var fCLastX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCLastX, fCLastY
   * ```
   */
  public var fCLastY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fSnappedY
   * ```
   */
  public var fSnappedY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fCubicDShift
   * ```
   */
  public var fCubicDShift: Int,
) : SkAnalyticEdge(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticCubicEdge::setCubicWithoutUpdate(const SkPoint pts[4], int shift) {
   *     SkFDot6 x0, y0, x1, y1, x2, y2, x3, y3;
   *
   *     {
   * #ifdef SK_RASTERIZE_EVEN_ROUNDING
   *         x0 = SkScalarRoundToFDot6(pts[0].fX, shift);
   *         y0 = SkScalarRoundToFDot6(pts[0].fY, shift);
   *         x1 = SkScalarRoundToFDot6(pts[1].fX, shift);
   *         y1 = SkScalarRoundToFDot6(pts[1].fY, shift);
   *         x2 = SkScalarRoundToFDot6(pts[2].fX, shift);
   *         y2 = SkScalarRoundToFDot6(pts[2].fY, shift);
   *         x3 = SkScalarRoundToFDot6(pts[3].fX, shift);
   *         y3 = SkScalarRoundToFDot6(pts[3].fY, shift);
   * #else
   *         float scale = float(1 << (shift + 6));
   *         x0 = int(pts[0].fX * scale);
   *         y0 = int(pts[0].fY * scale);
   *         x1 = int(pts[1].fX * scale);
   *         y1 = int(pts[1].fY * scale);
   *         x2 = int(pts[2].fX * scale);
   *         y2 = int(pts[2].fY * scale);
   *         x3 = int(pts[3].fX * scale);
   *         y3 = int(pts[3].fY * scale);
   * #endif
   *     }
   *
   *     Winding winding = Winding::kCW;
   *     if (y0 > y3)
   *     {
   *         using std::swap;
   *         swap(x0, x3);
   *         swap(x1, x2);
   *         swap(y0, y3);
   *         swap(y1, y2);
   *         winding = Winding::kCCW;
   *     }
   *
   *     int top = SkFDot6Round(y0);
   *     int bot = SkFDot6Round(y3);
   *
   *     // are we a zero-height cubic (line)?
   *     if (top == bot)
   *         return 0;
   *
   *     // compute number of steps needed (1 << shift)
   *     {
   *         // Can't use (center of curve - center of baseline), since center-of-curve
   *         // need not be the max delta from the baseline (it could even be coincident)
   *         // so we try just looking at the two off-curve points
   *         SkFDot6 dx = cubic_delta_from_line(x0, x1, x2, x3);
   *         SkFDot6 dy = cubic_delta_from_line(y0, y1, y2, y3);
   *         // add 1 (by observation)
   *         shift = diff_to_shift(dx, dy, 2) + 1;
   *     }
   *     // need at least 1 subdivision for our bias trick
   *     SkASSERT(shift > 0);
   *     if (shift > MAX_COEFF_SHIFT) {
   *         shift = MAX_COEFF_SHIFT;
   *     }
   *
   *     /*  Since our in coming data is initially shifted down by 10 (or 8 in
   *         antialias). That means the most we can shift up is 8. However, we
   *         compute coefficients with a 3*, so the safest upshift is really 6
   *     */
   *     int upShift = 6;    // largest safe value
   *     int downShift = shift + upShift - 10;
   *     if (downShift < 0) {
   *         downShift = 0;
   *         upShift = 10 - shift;
   *     }
   *
   *     fWinding = winding;
   *     fEdgeType = Type::kCubic;
   *     fCurveCount = SkToS8(SkLeftShift(-1, shift));
   *     fCurveShift = SkToU8(shift);
   *     fCubicDShift = SkToU8(downShift);
   *
   *     SkFixed B = SkFDot6UpShift(3 * (x1 - x0), upShift);
   *     SkFixed C = SkFDot6UpShift(3 * (x0 - x1 - x1 + x2), upShift);
   *     SkFixed D = SkFDot6UpShift(x3 + 3 * (x1 - x2) - x0, upShift);
   *
   *     fCx     = SkFDot6ToFixed(x0);
   *     fCDx    = B + (C >> shift) + (D >> 2*shift);    // biased by shift
   *     fCDDx   = 2*C + (3*D >> (shift - 1));           // biased by 2*shift
   *     fCDDDx  = 3*D >> (shift - 1);                   // biased by 2*shift
   *
   *     B = SkFDot6UpShift(3 * (y1 - y0), upShift);
   *     C = SkFDot6UpShift(3 * (y0 - y1 - y1 + y2), upShift);
   *     D = SkFDot6UpShift(y3 + 3 * (y1 - y2) - y0, upShift);
   *
   *     fCy     = SkFDot6ToFixed(y0);
   *     fCDy    = B + (C >> shift) + (D >> 2*shift);    // biased by shift
   *     fCDDy   = 2*C + (3*D >> (shift - 1));           // biased by 2*shift
   *     fCDDDy  = 3*D >> (shift - 1);                   // biased by 2*shift
   *
   *     fCLastX = SkFDot6ToFixed(x3);
   *     fCLastY = SkFDot6ToFixed(y3);
   *
   *     return true;
   * }
   * ```
   */
  public fun setCubicWithoutUpdate(pts: Array<SkPoint>, shiftUp: Int): Boolean {
    TODO("Implement setCubicWithoutUpdate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticCubicEdge::setCubic(const SkPoint pts[4]) {
   *     if (!setCubicWithoutUpdate(pts, kDefaultAccuracy)) {
   *         return false;
   *     }
   *
   *     fCx >>= kDefaultAccuracy;
   *     fCy >>= kDefaultAccuracy;
   *     fCDx >>= kDefaultAccuracy;
   *     fCDy >>= kDefaultAccuracy;
   *     fCDDx >>= kDefaultAccuracy;
   *     fCDDy >>= kDefaultAccuracy;
   *     fCDDDx >>= kDefaultAccuracy;
   *     fCDDDy >>= kDefaultAccuracy;
   *     fCLastX >>= kDefaultAccuracy;
   *     fCLastY >>= kDefaultAccuracy;
   *     fCy = SnapY(fCy);
   *     fSnappedY = fCy;
   *     fCLastY = SnapY(fCLastY);
   *
   *     fEdgeType = Type::kCubic;
   *
   *     return this->updateCubic();
   * }
   * ```
   */
  public fun setCubic(pts: Array<SkPoint>): Boolean {
    TODO("Implement setCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticCubicEdge::updateCubic() {
   *     int     success;
   *     int     count = fCurveCount;
   *     SkFixed oldx = fCx;
   *     SkFixed oldy = fCy;
   *     SkFixed newx, newy;
   *     const int ddshift = fCurveShift;
   *     const int dshift = fCubicDShift;
   *
   *     SkASSERT(count < 0);
   *
   *     do {
   *         if (++count < 0) {
   *             newx    = oldx + (fCDx >> dshift);
   *             fCDx    += fCDDx >> ddshift;
   *             fCDDx   += fCDDDx;
   *
   *             newy    = oldy + (fCDy >> dshift);
   *             fCDy    += fCDDy >> ddshift;
   *             fCDDy   += fCDDDy;
   *         }
   *         else {    // last segment
   *             newx    = fCLastX;
   *             newy    = fCLastY;
   *         }
   *
   *         // we want to say SkASSERT(oldy <= newy), but our finite fixedpoint
   *         // doesn't always achieve that, so we have to explicitly pin it here.
   *         if (newy < oldy) {
   *             newy = oldy;
   *         }
   *
   *         SkFixed newSnappedY = SnapY(newy);
   *         // we want to SkASSERT(snappedNewY <= fCLastY), but our finite fixedpoint
   *         // doesn't always achieve that, so we have to explicitly pin it here.
   *         if (fCLastY < newSnappedY) {
   *             newSnappedY = fCLastY;
   *             count = 0;
   *         }
   *
   *         SkFixed slope = SkFixedToFDot6(newSnappedY - fSnappedY) == 0
   *                         ? SK_MaxS32
   *                         : SkFDot6Div(SkFixedToFDot6(newx - oldx),
   *                                      SkFixedToFDot6(newSnappedY - fSnappedY));
   *
   *         success = this->updateLine(oldx, fSnappedY, newx, newSnappedY, slope);
   *
   *         oldx = newx;
   *         oldy = newy;
   *         fSnappedY = newSnappedY;
   *     } while (count < 0 && !success);
   *
   *     fCx  = newx;
   *     fCy  = newy;
   *     fCurveCount = SkToS8(count);
   *     return success;
   * }
   * ```
   */
  public fun updateCubic(): Boolean {
    TODO("Implement updateCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void keepContinuous() {
   *         SkASSERT(SkAbs32(fX - SkFixedMul(fDX, fY - SnapY(fCy)) - fCx) < SK_Fixed1);
   *         fCx = fX;
   *         fSnappedY = fY;
   *     }
   * ```
   */
  public fun keepContinuous() {
    TODO("Implement keepContinuous")
  }
}
