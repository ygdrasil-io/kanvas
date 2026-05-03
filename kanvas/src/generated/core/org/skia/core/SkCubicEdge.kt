package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkCubicEdge final : public SkEdge {
 * public:
 *     // Sets up the line segments. Returns false if the line would be of height 0.
 *     bool setCubic(const SkPoint pts[4]);
 *     bool nextSegment() override;
 *
 * private:
 *     // These are the non-rounded points that the current line segment ends at.
 *     SkFixed fCx, fCy;
 *     SkFixed fCDxDt, fCDyDt;
 *     SkFixed fCD2xDt2, fCD2yDt2;
 *     SkFixed fCD3xDt3, fCD3yDt3;
 *
 *     // The non-rounded end points for the entire curve. On the last segment, these
 *     // will be used instead of the results from our forward-difference technique
 *     // to make sure cumulative error doesn't result in a dramatically different line.
 *     SkFixed fCLastX, fCLastY;
 *
 *     uint8_t fCubicDShift;   // applied to fCDxDt and fCDyDt only in cubic
 *
 * #if defined(SK_DEBUG)
 * public:
 *     void dump() const override;
 * #endif
 * }
 * ```
 */
public class SkCubicEdge : SkEdge() {
  /**
   * C++ original:
   * ```cpp
   * SkFixed fCx
   * ```
   */
  private var fCx: SkFixed = TODO("Initialize fCx")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCx, fCy
   * ```
   */
  private var fCy: SkFixed = TODO("Initialize fCy")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDxDt
   * ```
   */
  private var fCDxDt: SkFixed = TODO("Initialize fCDxDt")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCDxDt, fCDyDt
   * ```
   */
  private var fCDyDt: SkFixed = TODO("Initialize fCDyDt")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCD2xDt2
   * ```
   */
  private var fCD2xDt2: SkFixed = TODO("Initialize fCD2xDt2")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCD2xDt2, fCD2yDt2
   * ```
   */
  private var fCD2yDt2: SkFixed = TODO("Initialize fCD2yDt2")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCD3xDt3
   * ```
   */
  private var fCD3xDt3: SkFixed = TODO("Initialize fCD3xDt3")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCD3xDt3, fCD3yDt3
   * ```
   */
  private var fCD3yDt3: SkFixed = TODO("Initialize fCD3yDt3")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCLastX
   * ```
   */
  private var fCLastX: SkFixed = TODO("Initialize fCLastX")

  /**
   * C++ original:
   * ```cpp
   * SkFixed fCLastX, fCLastY
   * ```
   */
  private var fCLastY: SkFixed = TODO("Initialize fCLastY")

  /**
   * C++ original:
   * ```cpp
   * uint8_t fCubicDShift
   * ```
   */
  private var fCubicDShift: Int = TODO("Initialize fCubicDShift")

  /**
   * C++ original:
   * ```cpp
   * bool SkCubicEdge::setCubic(const SkPoint pts[4]) {
   *     SkFDot6 x0, y0, x1, y1, x2, y2, x3, y3;
   *
   * #if defined(SK_RASTERIZE_EVEN_ROUNDING)
   *     x0 = SkScalarRoundToFDot6(pts[0].fX, 0);
   *     y0 = SkScalarRoundToFDot6(pts[0].fY, 0);
   *     x1 = SkScalarRoundToFDot6(pts[1].fX, 0);
   *     y1 = SkScalarRoundToFDot6(pts[1].fY, 0);
   *     x2 = SkScalarRoundToFDot6(pts[2].fX, 0);
   *     y2 = SkScalarRoundToFDot6(pts[2].fY, 0);
   *     x3 = SkScalarRoundToFDot6(pts[3].fX, 0);
   *     y3 = SkScalarRoundToFDot6(pts[3].fY, 0);
   * #else
   *     x0 = SkFloatToFDot6(pts[0].fX);
   *     y0 = SkFloatToFDot6(pts[0].fY);
   *     x1 = SkFloatToFDot6(pts[1].fX);
   *     y1 = SkFloatToFDot6(pts[1].fY);
   *     x2 = SkFloatToFDot6(pts[2].fX);
   *     y2 = SkFloatToFDot6(pts[2].fY);
   *     x3 = SkFloatToFDot6(pts[3].fX);
   *     y3 = SkFloatToFDot6(pts[3].fY);
   * #endif
   *
   *     Winding winding = Winding::kCW;
   *     if (y0 > y3) {
   *         std::swap(x0, x3);
   *         std::swap(x1, x2);
   *         std::swap(y0, y3);
   *         std::swap(y1, y2);
   *         winding = Winding::kCCW;
   *     }
   *
   *     int top = SkFDot6Round(y0);
   *     int bot = SkFDot6Round(y3);
   *
   *     // are we a zero-height cubic (line)?
   *     if (top == bot) {
   *         return false;
   *     }
   *
   *     // compute number of steps needed (1 << shift)
   *     // Can't use (center of curve - center of baseline), since center-of-curve
   *     // need not be the max delta from the baseline (it could even be coincident)
   *     // so we try just looking at the two off-curve points
   *     SkFDot6 dx = cubic_delta_from_line(x0, x1, x2, x3);
   *     SkFDot6 dy = cubic_delta_from_line(y0, y1, y2, y3);
   *     // add 1 (by observation)
   *     int shift = diff_to_shift(dx, dy, 2) + 1;
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
   *     fSegmentCount = SkToU8(SkLeftShift(1, shift));
   *     fCurveShift = SkToU8(shift);
   *     fCubicDShift = SkToU8(downShift);
   *
   *     /*
   *      *  By re-arranging the Bezier curve in polynomial form, it is easier to
   *      *  find the derivatives and forward-differentiate from one segment to the next.
   *      *
   *      *  p0 (1-t)^3 + 3 p1 t(1-t)^2 + 3 p2 t^2 (1-t) + p3 t^3 ==> At^3 + Bt^2 + Ct + D
   *      *  Where A = -p0 + 3p1 + -3p2 + p3
   *      *        B = 3p0 - 6p1 + 3p2
   *      *        C = -3p0 + 3p1
   *      *        D = p0
   *      */
   *     // TODO(kjlubick): Can we use SkVx and calculate both X and Y at once?
   *
   *     SkFixed A = SkFDot6UpShift(x3 + 3 * (x1 - x2) - x0, upShift);
   *     SkFixed B = SkFDot6UpShift(3 * (x0 - 2*x1 + x2), upShift);
   *     SkFixed C = SkFDot6UpShift(3 * (x1 - x0), upShift);
   *
   *     // We want to calculate the slope at the midpoint of our first segment. This means evaluating
   *     //   dx/dt = 3A*t^2 + 2B*t + C
   *     //   dx^2/dt^2 = 6A * t + 2B
   *     //   dx^3/dt^3 = 6A
   *     // at t = 1/N * 1/2
   *     // TODO(kjlubick): I'm not sure these cubic approximations are being done correctly. Some
   *     //                 coefficients seem to be missing. Maybe it's being done not at the midpoint
   *     //                 of the first line but just...somewhere in there?
   *     fCDxDt = (A >> 2*shift) + (B >> shift) + C;
   *     fCD2xDt2 = (3*A >> (shift - 1)) + 2*B;
   *
   *     // This may be attempting to precompute the third derivative times 1/N
   *     // 6A / N => 6A / 2^shift => 3A / 2^(shift-1)
   *     fCD3xDt3 = 3*A >> (shift - 1);
   *
   *     A = SkFDot6UpShift(y3 + 3 * (y1 - y2) - y0, upShift);
   *     B = SkFDot6UpShift(3 * (y0 - 2*y1 + y2), upShift);
   *     C = SkFDot6UpShift(3 * (y1 - y0), upShift);
   *
   *     fCDyDt = (A >> 2*shift) + (B >> shift) + C;
   *     fCD2yDt2 = (3*A >> (shift - 1)) + 2*B;
   *     fCD3yDt3 = 3*A >> (shift - 1);
   *
   *     fCx = SkFDot6ToFixed(x0);
   *     fCy = SkFDot6ToFixed(y0);
   *     fCLastX = SkFDot6ToFixed(x3);
   *     fCLastY = SkFDot6ToFixed(y3);
   *
   *     return this->nextSegment();
   * }
   * ```
   */
  public fun setCubic(pts: Array<SkPoint>): Boolean {
    TODO("Implement setCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCubicEdge::nextSegment() {
   *     bool    success;
   *     int     count = fSegmentCount;
   *     SkFixed oldx = fCx;
   *     SkFixed oldy = fCy;
   *     SkFixed newx, newy;
   *     const int ddshift = fCurveShift;
   *     const int dshift = fCubicDShift;
   *
   *     SkASSERT(count > 0);
   *
   *     do {
   *         if (--count > 0)
   *         {
   *             newx = oldx + (fCDxDt >> dshift);
   *             fCDxDt += fCD2xDt2 >> ddshift;
   *             fCD2xDt2 += fCD3xDt3;
   *
   *             newy = oldy + (fCDyDt >> dshift);
   *             fCDyDt += fCD2yDt2 >> ddshift;
   *             fCD2yDt2 += fCD3yDt3;
   *         }
   *         else    // last segment
   *         {
   *             newx = fCLastX;
   *             newy = fCLastY;
   *         }
   *
   *         // we want to say SkASSERT(oldy <= newy), but our finite fixedpoint
   *         // doesn't always achieve that, so we have to explicitly pin it here.
   *         if (newy < oldy) {
   *             newy = oldy;
   *         }
   *
   *         success = this->updateLine(oldx, oldy, newx, newy);
   *         oldx = newx;
   *         oldy = newy;
   *     } while (count > 0 && !success);
   *
   *     fCx = newx;
   *     fCy = newy;
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
   * void SkCubicEdge::dump() const {
   *     SkDebugf("cube edge; %u segment(s) left: firstY:%d lastY:%d x:%g dx/dy:%g\n"
   *              "qx:%g qy:%g dcx:%g dcy:%g ddcx:%g ddcy:%g dddcx:%g dddcy:%g cLastX:%g cLastY:%g\n"
   *              "\twinding:%d curveShift:%u dShift:%u\n",
   *              fSegmentCount,
   *              fFirstY,
   *              fLastY,
   *              SkFixedToFloat(fX),
   *              SkFixedToFloat(fDxDy),
   *              SkFixedToFloat(fCx),
   *              SkFixedToFloat(fCy),
   *              SkFixedToFloat(fCDxDt),
   *              SkFixedToFloat(fCDyDt),
   *              SkFixedToFloat(fCD2xDt2),
   *              SkFixedToFloat(fCD2yDt2),
   *              SkFixedToFloat(fCD3xDt3),
   *              SkFixedToFloat(fCD3yDt3),
   *              SkFixedToFloat(fCLastX),
   *              SkFixedToFloat(fCLastY),
   *              static_cast<int8_t>(fWinding),
   *              fCurveShift,
   *              fCubicDShift);
   * }
   * ```
   */
  public override fun dump() {
    TODO("Implement dump")
  }
}
