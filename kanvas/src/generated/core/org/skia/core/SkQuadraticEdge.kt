package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkFixed
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkQuadraticEdge final : public SkEdge {
 * public:
 *     // Sets up the line segments. Returns false if the line would be of height 0.
 *     bool setQuadratic(const SkPoint pts[3]);
 *     bool nextSegment() override;
 *
 * private:
 *     // These are the non-rounded points that the current line segment ends at.
 *     SkFixed fQx, fQy;
 *     // These represent the first derivatives of the quadratic curve evaluated
 *     // at the midpoint of the next line segment. To avoid overflows, we store them as half
 *     // their normal value. During the forward-difference step, instead of multiplying by
 *     // a deltaT of 1/N, we'll multiply by 2/N instead.
 *     SkFixed fQDxDt, fQDyDt;
 *     // These are the second derivatives of the quadratic curve pre-multiplied by 1/N.
 *     SkFixed fQD2xDt2, fQD2yDt2;
 *
 *     // The non-rounded end points for the entire curve. On the last segment, these
 *     // will be used instead of the results from our forward-differnce technique
 *     // to make sure cumulative error doesn't result in a dramatically different line.
 *     SkFixed fQLastX, fQLastY;
 *
 * #if defined(SK_DEBUG)
 * public:
 *     void dump() const override;
 * #endif
 * }
 * ```
 */
public class SkQuadraticEdge : SkEdge() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed fQx
   * ```
   */
  private var fQx: SkFixed = TODO("Initialize fQx")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQx, fQy
   * ```
   */
  private var fQy: SkFixed = TODO("Initialize fQy")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDxDt
   * ```
   */
  private var fQDxDt: SkFixed = TODO("Initialize fQDxDt")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQDxDt, fQDyDt
   * ```
   */
  private var fQDyDt: SkFixed = TODO("Initialize fQDyDt")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQD2xDt2
   * ```
   */
  private var fQD2xDt2: SkFixed = TODO("Initialize fQD2xDt2")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQD2xDt2, fQD2yDt2
   * ```
   */
  private var fQD2yDt2: SkFixed = TODO("Initialize fQD2yDt2")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQLastX
   * ```
   */
  private var fQLastX: SkFixed = TODO("Initialize fQLastX")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fQLastX, fQLastY
   * ```
   */
  private var fQLastY: SkFixed = TODO("Initialize fQLastY")

  /**
   * C++ original:
   * ```cpp
   * bool SkQuadraticEdge::setQuadratic(const SkPoint pts[3]) {
   *     SkFDot6 x0, y0, x1, y1, x2, y2;
   *
   * #if defined(SK_RASTERIZE_EVEN_ROUNDING)
   *     x0 = SkScalarRoundToFDot6(pts[0].fX, 0);
   *     y0 = SkScalarRoundToFDot6(pts[0].fY, 0);
   *     x1 = SkScalarRoundToFDot6(pts[1].fX, 0);
   *     y1 = SkScalarRoundToFDot6(pts[1].fY, 0);
   *     x2 = SkScalarRoundToFDot6(pts[2].fX, 0);
   *     y2 = SkScalarRoundToFDot6(pts[2].fY, 0);
   * #else
   *     x0 = SkFloatToFDot6(pts[0].fX);
   *     y0 = SkFloatToFDot6(pts[0].fY);
   *     x1 = SkFloatToFDot6(pts[1].fX);
   *     y1 = SkFloatToFDot6(pts[1].fY);
   *     x2 = SkFloatToFDot6(pts[2].fX);
   *     y2 = SkFloatToFDot6(pts[2].fY);
   * #endif
   *
   *     Winding winding = Winding::kCW;
   *     if (y0 > y2) {
   *         std::swap(x0, x2);
   *         std::swap(y0, y2);
   *         winding = Winding::kCCW;
   *     }
   *     SkASSERTF(y0 <= y1 && y1 <= y2, "curve must be monotonic");
   *
   *     const int top = SkFDot6Round(y0);
   *     const int bot = SkFDot6Round(y2);
   *
   *     // are we a zero-height quad (line)?
   *     if (top == bot) {
   *         return false;
   *     }
   *
   *     // compute number of steps needed (2^shift) based on the distance between
   *     // this curve at the half-way point (t=0.5) and the midpoint of a straight
   *     // line between p0 and p2.
   *     // B(1/2) = p0 (1-t)^2 + 2 p1 t(1-t) + p2 t^2; t = 1/2
   *     //        = p0 (1/2)^2 + 2 p1 (1/2)(1/2) + p2 (1/2)^2
   *     //        = 1/4 (p0 + 2 p1 + p2)
   *     // Midpoint of p0 and p2 is M(p0, p2) = (p2 + p0) / 2
   *     // Subtracting the two terms to get the vector representing the difference
   *     // distance = B(1/2) - M(p0, p2)
   *     //          = 1/4 (p0 + 2 p1 + p2) - (p2 + p0) / 2
   *     //          = 1/4 (p0 + 2 p1 + p2) - (2 p2 + 2 p0) / 4
   *     //          = 1/4 (-p0 + 2 p1 - p2)
   *     SkFDot6 deltaX = (2*x1 - x0 - x2) >> 2;
   *     SkFDot6 deltaY = (2*y1 - y0 - y2) >> 2;
   *     // We pass those points into this function which will find the total distance
   *     // and use a heuristic to reduce the error to some threshold.
   *     int shift = diff_to_shift(deltaX, deltaY, 0);
   *     SkASSERT(shift >= 0);
   *
   *     // We need at least 2 line segments for us to be able to save the derivatives as
   *     // half their values to avoid overflow.
   *     if (shift == 0) {
   *         shift = 1;
   *     } else if (shift > MAX_COEFF_SHIFT) {
   *         shift = MAX_COEFF_SHIFT;
   *     }
   *
   *     fWinding = winding;
   *     fEdgeType = Type::kQuad;
   *     fSegmentCount = SkToU8(1 << shift);
   *
   *     /*
   *      *  By re-arranging the Bezier curve in polynomial form, it is easier to
   *      *  find the derivatives and forward-differentiate from one segment to the next.
   *      *
   *      *  p0 (1-t)^2 + 2 p1 t(1-t) + p2 t^2 ==> At^2 + Bt + C
   *      *
   *      *  A = p0 - 2p1 + p2
   *      *  B = 2(p1 - p0)
   *      *  C = p0
   *      *
   *      *  Our caller must have constrained our inputs (p0..p2) to all fit into
   *      *  16.16. However, as seen above, we sometimes compute values that can be
   *      *  larger (e.g. B = 2*(p1 - p0)). To guard against overflow, we will store
   *      *  A and B at 1/2 of their actual value, and just apply a 2x scale during
   *      *  application in nextSegment(). Hence we store (shift - 1) in
   *      *  fCurveShift.
   *      */
   *
   *     fCurveShift = SkToU8(shift - 1);
   *     // TODO(kjlubick): Can we use SkVx and calculate both X and Y at once?
   *
   *     // The extra 1/2 factor avoids overflow
   *     SkFixed A_half = SkFDot6ToFixedDiv2(x0 - x1 - x1 + x2);
   *     SkFixed B_half = SkFDot6ToFixed(x1 - x0);
   *
   *     // We want to calculate the slope at the midpoint of our first segment. This means evaluating
   *     //   dx/dt = 2A*t + B
   *     //   dx^2/dt^2 = 2A
   *     // at t = 1/N * 1/2
   *     // There's an extra 1/2 on the whole expression to avoid overflows (as above).
   *     //  1/2 ( 2A*t + B) => 1/2 (2A*1/2N + B) => A/2*1/N + B/2 => A/2 * 1/2^shift + B/2
   *     fQDxDt = B_half + (A_half >> shift);
   *     // The second derivatives are constant, so we can pre-multiply them by 1/N to save having
   *     // to do it in nextSegment(). Since A_half was already calculated we can use a smaller shift.
   *     // 1/2 (2A * 1/N) => A * 1/N => A * 1/2^shift => A/2 * 1/2^(shift-1)
   *     fQD2xDt2 = A_half >> (shift - 1);
   *
   *     A_half = SkFDot6ToFixedDiv2(y0 - y1 - y1 + y2);
   *     B_half = SkFDot6ToFixed(y1 - y0);
   *
   *     fQDyDt = B_half + (A_half >> shift);
   *     fQD2yDt2 = A_half >> (shift - 1);
   *
   *     fQx     = SkFDot6ToFixed(x0);
   *     fQy     = SkFDot6ToFixed(y0);
   *     fQLastX = SkFDot6ToFixed(x2);
   *     fQLastY = SkFDot6ToFixed(y2);
   *
   *     return this->nextSegment();
   * }
   * ```
   */
  public fun setQuadratic(pts: Array<SkPoint>): Boolean {
    TODO("Implement setQuadratic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkQuadraticEdge::nextSegment() {
   *     bool    success;
   *     int     count = fSegmentCount;
   *     SkFixed oldx = fQx;
   *     SkFixed oldy = fQy;
   *     SkFixed dx = fQDxDt;
   *     SkFixed dy = fQDyDt;
   *     SkFixed newx, newy;
   *     int     shift = fCurveShift;
   *
   *     SkASSERT(count > 0);
   *
   *     do {
   *         if (--count > 0) {
   *             newx = oldx + (dx >> shift);
   *             dx += fQD2xDt2;
   *             newy = oldy + (dy >> shift);
   *             dy += fQD2yDt2;
   *         }
   *         else    // last segment
   *         {
   *             newx = fQLastX;
   *             newy = fQLastY;
   *         }
   *         success = this->updateLine(oldx, oldy, newx, newy);
   *         oldx = newx;
   *         oldy = newy;
   *     } while (count > 0 && !success);
   *
   *     fQx = newx;
   *     fQy = newy;
   *     fQDxDt = dx;
   *     fQDyDt = dy;
   *     fSegmentCount = SkToU8(count);
   *     return success;
   * }
   * ```
   */
  public override fun nextSegment(): Boolean {
    TODO("Implement nextSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkQuadraticEdge::dump() const {
   *     SkDebugf("quad edge; %u segment(s) left: firstY:%d lastY:%d x:%g dx/dy:%g\n"
   *              "\tqx:%g qy:%g dqx:%g dqy:%g ddqx:%g ddqy:%g qLastX:%g qLastY:%g\n"
   *              "\twinding:%d curveShift:%u\n",
   *              fSegmentCount,
   *              fFirstY,
   *              fLastY,
   *              SkFixedToFloat(fX),
   *              SkFixedToFloat(fDxDy),
   *              SkFixedToFloat(fQx),
   *              SkFixedToFloat(fQy),
   *              SkFixedToFloat(fQDxDt),
   *              SkFixedToFloat(fQDyDt),
   *              SkFixedToFloat(fQD2xDt2),
   *              SkFixedToFloat(fQD2yDt2),
   *              SkFixedToFloat(fQLastX),
   *              SkFixedToFloat(fQLastY),
   *              static_cast<int8_t>(fWinding),
   *              fCurveShift);
   * }
   * ```
   */
  public override fun dump() {
    TODO("Implement dump")
  }
}
