package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkAnalyticQuadraticEdge : public SkAnalyticEdge {
 *     SkFixed fQx, fQy;
 *     SkFixed fQDx, fQDy;
 *     SkFixed fQDDx, fQDDy;
 *     SkFixed fQLastX, fQLastY;
 *
 *     // snap y to integer points in the middle of the curve to accelerate AAA path filling
 *     SkFixed fSnappedX, fSnappedY;
 *
 *     bool setQuadraticWithoutUpdate(const SkPoint pts[3], int shiftUp);
 *     bool setQuadratic(const SkPoint pts[3]);
 *     bool updateQuadratic();
 *     inline void keepContinuous() {
 *         // We use fX as the starting x to ensure the continuouty.
 *         // Without it, we may break the sorted edge list.
 *         SkASSERT(SkAbs32(fX - SkFixedMul(fY - fSnappedY, fDX) - fSnappedX) < SK_Fixed1);
 *         SkASSERT(SkAbs32(fY - fSnappedY) < SK_Fixed1); // This may differ due to smooth jump
 *         fSnappedX = fX;
 *         fSnappedY = fY;
 *     }
 * }
 * ```
 */
public open class SkAnalyticQuadraticEdge public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQx
   * ```
   */
  public var fQx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQx, fQy
   * ```
   */
  public var fQy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDx
   * ```
   */
  public var fQDx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDx, fQDy
   * ```
   */
  public var fQDy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDDx
   * ```
   */
  public var fQDDx: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDDx, fQDDy
   * ```
   */
  public var fQDDy: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQLastX
   * ```
   */
  public var fQLastX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQLastX, fQLastY
   * ```
   */
  public var fQLastY: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fSnappedX
   * ```
   */
  public var fSnappedX: SkFixed,
  /**
   * C++ original:
   * ```cpp
   * SkFixed fSnappedX, fSnappedY
   * ```
   */
  public var fSnappedY: SkFixed,
) : SkAnalyticEdge(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticQuadraticEdge::setQuadraticWithoutUpdate(const SkPoint pts[3], int shift) {
   *     SkFDot6 x0, y0, x1, y1, x2, y2;
   *
   *     {
   * #ifdef SK_RASTERIZE_EVEN_ROUNDING
   *         x0 = SkScalarRoundToFDot6(pts[0].fX, shift);
   *         y0 = SkScalarRoundToFDot6(pts[0].fY, shift);
   *         x1 = SkScalarRoundToFDot6(pts[1].fX, shift);
   *         y1 = SkScalarRoundToFDot6(pts[1].fY, shift);
   *         x2 = SkScalarRoundToFDot6(pts[2].fX, shift);
   *         y2 = SkScalarRoundToFDot6(pts[2].fY, shift);
   * #else
   *         float scale = float(1 << (shift + 6));
   *         x0 = int(pts[0].fX * scale);
   *         y0 = int(pts[0].fY * scale);
   *         x1 = int(pts[1].fX * scale);
   *         y1 = int(pts[1].fY * scale);
   *         x2 = int(pts[2].fX * scale);
   *         y2 = int(pts[2].fY * scale);
   * #endif
   *     }
   *
   *     Winding winding = Winding::kCW;
   *     if (y0 > y2)
   *     {
   *         using std::swap;
   *         swap(x0, x2);
   *         swap(y0, y2);
   *         winding = Winding::kCCW;
   *     }
   *     SkASSERT(y0 <= y1 && y1 <= y2);
   *
   *     int top = SkFDot6Round(y0);
   *     int bot = SkFDot6Round(y2);
   *
   *     // are we a zero-height quad (line)?
   *     if (top == bot) {
   *         return 0;
   *     }
   *
   *     // compute number of steps needed (1 << shift)
   *     {
   *         SkFDot6 dx = (SkLeftShift(x1, 1) - x0 - x2) >> 2;
   *         SkFDot6 dy = (SkLeftShift(y1, 1) - y0 - y2) >> 2;
   *         // This is a little confusing:
   *         // before this line, shift is the scale up factor for AA;
   *         // after this line, shift is the fCurveShift.
   *         shift = diff_to_shift(dx, dy, shift);
   *         SkASSERT(shift >= 0);
   *     }
   *     // need at least 1 subdivision for our bias trick
   *     if (shift == 0) {
   *         shift = 1;
   *     } else if (shift > MAX_COEFF_SHIFT) {
   *         shift = MAX_COEFF_SHIFT;
   *     }
   *
   *     fWinding = winding;
   *     //fCubicDShift only set for cubics
   *     fEdgeType = Type::kQuad;
   *     fCurveCount = SkToS8(1 << shift);
   *
   *     /*
   *      *  We want to reformulate into polynomial form, to make it clear how we
   *      *  should forward-difference.
   *      *
   *      *  p0 (1 - t)^2 + p1 t(1 - t) + p2 t^2 ==> At^2 + Bt + C
   *      *
   *      *  A = p0 - 2p1 + p2
   *      *  B = 2(p1 - p0)
   *      *  C = p0
   *      *
   *      *  Our caller must have constrained our inputs (p0..p2) to all fit into
   *      *  16.16. However, as seen above, we sometimes compute values that can be
   *      *  larger (e.g. B = 2*(p1 - p0)). To guard against overflow, we will store
   *      *  A and B at 1/2 of their actual value, and just apply a 2x scale during
   *      *  application in updateQuadratic(). Hence we store (shift - 1) in
   *      *  fCurveShift.
   *      */
   *
   *     fCurveShift = SkToU8(shift - 1);
   *
   *     SkFixed A = SkFDot6ToFixedDiv2(x0 - x1 - x1 + x2);  // 1/2 the real value
   *     SkFixed B = SkFDot6ToFixed(x1 - x0);                // 1/2 the real value
   *
   *     fQx     = SkFDot6ToFixed(x0);
   *     fQDx    = B + (A >> shift);     // biased by shift
   *     fQDDx   = A >> (shift - 1);     // biased by shift
   *
   *     A = SkFDot6ToFixedDiv2(y0 - y1 - y1 + y2);  // 1/2 the real value
   *     B = SkFDot6ToFixed(y1 - y0);                // 1/2 the real value
   *
   *     fQy     = SkFDot6ToFixed(y0);
   *     fQDy    = B + (A >> shift);     // biased by shift
   *     fQDDy   = A >> (shift - 1);     // biased by shift
   *
   *     fQLastX = SkFDot6ToFixed(x2);
   *     fQLastY = SkFDot6ToFixed(y2);
   *
   *     return true;
   * }
   * ```
   */
  public fun setQuadraticWithoutUpdate(pts: Array<SkPoint>, shiftUp: Int): Boolean {
    TODO("Implement setQuadraticWithoutUpdate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticQuadraticEdge::setQuadratic(const SkPoint pts[3]) {
   *     if (!setQuadraticWithoutUpdate(pts, kDefaultAccuracy)) {
   *         return false;
   *     }
   *     fQx >>= kDefaultAccuracy;
   *     fQy >>= kDefaultAccuracy;
   *     fQDx >>= kDefaultAccuracy;
   *     fQDy >>= kDefaultAccuracy;
   *     fQDDx >>= kDefaultAccuracy;
   *     fQDDy >>= kDefaultAccuracy;
   *     fQLastX >>= kDefaultAccuracy;
   *     fQLastY >>= kDefaultAccuracy;
   *     fQy = SnapY(fQy);
   *     fQLastY = SnapY(fQLastY);
   *
   *     fEdgeType = Type::kQuad;
   *
   *     fSnappedX = fQx;
   *     fSnappedY = fQy;
   *
   *     return this->updateQuadratic();
   * }
   * ```
   */
  public fun setQuadratic(pts: Array<SkPoint>): Boolean {
    TODO("Implement setQuadratic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAnalyticQuadraticEdge::updateQuadratic() {
   *     int     success = 0; // initialize to fail!
   *     int     count = fCurveCount;
   *     SkFixed oldx = fQx;
   *     SkFixed oldy = fQy;
   *     SkFixed dx = fQDx;
   *     SkFixed dy = fQDy;
   *     SkFixed newx, newy, newSnappedX, newSnappedY;
   *     int     shift = fCurveShift;
   *
   *     SkASSERT(count > 0);
   *
   *     do {
   *         SkFixed slope;
   *         if (--count > 0)
   *         {
   *             newx    = oldx + (dx >> shift);
   *             newy    = oldy + (dy >> shift);
   *             // only snap when dy is large enough and dx/dy isn't too large
   *             if (SkAbs32(dy >> shift) >= SK_Fixed1 * 2 &&
   *                 SkLeftShift((int64_t) SkAbs32(dy), 6) > SkAbs32(dx)) {
   *                 SkFDot6 diffY = SkFixedToFDot6(newy - fSnappedY);
   *                 slope = diffY ? quick_div(SkFixedToFDot6(newx - fSnappedX), diffY)
   *                               : SK_MaxS32;
   *                 newSnappedY = std::min<SkFixed>(fQLastY, SkFixedRoundToFixed(newy));
   *                 newSnappedX = newx - SkFixedMul(slope, newy - newSnappedY);
   *             } else {
   *                 newSnappedY = std::min(fQLastY, SnapY(newy));
   *                 newSnappedX = newx;
   *                 SkFDot6 diffY = SkFixedToFDot6(newSnappedY - fSnappedY);
   *                 slope = diffY ? quick_div(SkFixedToFDot6(newx - fSnappedX), diffY)
   *                               : SK_MaxS32;
   *             }
   *             dx += fQDDx;
   *             dy += fQDDy;
   *         }
   *         else    // last segment
   *         {
   *             newx    = fQLastX;
   *             newy    = fQLastY;
   *             newSnappedY = newy;
   *             newSnappedX = newx;
   *             SkFDot6 diffY = SkFixedToFDot6(newy - fSnappedY);
   *             slope = diffY ? quick_div(SkFixedToFDot6(newx - fSnappedX), diffY) : SK_MaxS32;
   *         }
   *         if (slope < SK_MaxS32) {
   *             success = this->updateLine(fSnappedX, fSnappedY, newSnappedX, newSnappedY, slope);
   *         }
   *         oldx = newx;
   *         oldy = newy;
   *     } while (count > 0 && !success);
   *
   *     SkASSERT(newSnappedY <= fQLastY);
   *
   *     fQx  = newx;
   *     fQy  = newy;
   *     fQDx = dx;
   *     fQDy = dy;
   *     fSnappedX   = newSnappedX;
   *     fSnappedY   = newSnappedY;
   *     fCurveCount = SkToS8(count);
   *     return success;
   * }
   * ```
   */
  public fun updateQuadratic(): Boolean {
    TODO("Implement updateQuadratic")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void keepContinuous() {
   *         // We use fX as the starting x to ensure the continuouty.
   *         // Without it, we may break the sorted edge list.
   *         SkASSERT(SkAbs32(fX - SkFixedMul(fY - fSnappedY, fDX) - fSnappedX) < SK_Fixed1);
   *         SkASSERT(SkAbs32(fY - fSnappedY) < SK_Fixed1); // This may differ due to smooth jump
   *         fSnappedX = fX;
   *         fSnappedY = fY;
   *     }
   * ```
   */
  public fun keepContinuous() {
    TODO("Implement keepContinuous")
  }
}
