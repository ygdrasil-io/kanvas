package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkCubicClipper {
 * public:
 *     SkCubicClipper();
 *
 *     void setClip(const SkIRect& clip);
 *
 *     [[nodiscard]] bool clipCubic(const SkPoint src[4], SkPoint dst[4]);
 *
 *     [[nodiscard]] static bool ChopMonoAtY(const SkPoint pts[4], SkScalar y, SkScalar* t);
 * private:
 *     SkRect      fClip;
 * }
 * ```
 */
public data class SkCubicClipper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect      fClip
   * ```
   */
  private var fClip: SkRect,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkCubicClipper::setClip(const SkIRect& clip) {
   *     // conver to scalars, since that's where we'll see the points
   *     fClip.set(clip);
   * }
   * ```
   */
  public fun setClip(clip: SkIRect) {
    TODO("Implement setClip")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCubicClipper::clipCubic(const SkPoint srcPts[4], SkPoint dst[4]) {
   *     bool reverse;
   *
   *     // we need the data to be monotonically descending in Y
   *     if (srcPts[0].fY > srcPts[3].fY) {
   *         dst[0] = srcPts[3];
   *         dst[1] = srcPts[2];
   *         dst[2] = srcPts[1];
   *         dst[3] = srcPts[0];
   *         reverse = true;
   *     } else {
   *         memcpy(dst, srcPts, 4 * sizeof(SkPoint));
   *         reverse = false;
   *     }
   *
   *     // are we completely above or below
   *     const SkScalar ctop = fClip.fTop;
   *     const SkScalar cbot = fClip.fBottom;
   *     if (dst[3].fY <= ctop || dst[0].fY >= cbot) {
   *         return false;
   *     }
   *
   *     SkScalar t;
   *     SkPoint tmp[7]; // for SkChopCubicAt
   *
   *     // are we partially above
   *     if (dst[0].fY < ctop && ChopMonoAtY(dst, ctop, &t)) {
   *         SkChopCubicAt(dst, tmp, t);
   *         dst[0] = tmp[3];
   *         dst[1] = tmp[4];
   *         dst[2] = tmp[5];
   *     }
   *
   *     // are we partially below
   *     if (dst[3].fY > cbot && ChopMonoAtY(dst, cbot, &t)) {
   *         SkChopCubicAt(dst, tmp, t);
   *         dst[1] = tmp[1];
   *         dst[2] = tmp[2];
   *         dst[3] = tmp[3];
   *     }
   *
   *     if (reverse) {
   *         using std::swap;
   *         swap(dst[0], dst[3]);
   *         swap(dst[1], dst[2]);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun clipCubic(src: Array<SkPoint>, dst: Array<SkPoint>): Boolean {
    TODO("Implement clipCubic")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkCubicClipper::ChopMonoAtY(const SkPoint pts[4], SkScalar y, SkScalar* t) {
     *     SkScalar ycrv[4];
     *     ycrv[0] = pts[0].fY - y;
     *     ycrv[1] = pts[1].fY - y;
     *     ycrv[2] = pts[2].fY - y;
     *     ycrv[3] = pts[3].fY - y;
     *
     * #ifdef NEWTON_RAPHSON    // Quadratic convergence, typically <= 3 iterations.
     *     // Initial guess.
     *     // TODO(turk): Check for zero denominator? Shouldn't happen unless the curve
     *     // is not only monotonic but degenerate.
     *     SkScalar t1 = ycrv[0] / (ycrv[0] - ycrv[3]);
     *
     *     // Newton's iterations.
     *     const SkScalar tol = SK_Scalar1 / 16384;  // This leaves 2 fixed noise bits.
     *     SkScalar t0;
     *     const int maxiters = 5;
     *     int iters = 0;
     *     bool converged;
     *     do {
     *         t0 = t1;
     *         SkScalar y01   = SkScalarInterp(ycrv[0], ycrv[1], t0);
     *         SkScalar y12   = SkScalarInterp(ycrv[1], ycrv[2], t0);
     *         SkScalar y23   = SkScalarInterp(ycrv[2], ycrv[3], t0);
     *         SkScalar y012  = SkScalarInterp(y01,  y12,  t0);
     *         SkScalar y123  = SkScalarInterp(y12,  y23,  t0);
     *         SkScalar y0123 = SkScalarInterp(y012, y123, t0);
     *         SkScalar yder  = (y123 - y012) * 3;
     *         // TODO(turk): check for yder==0: horizontal.
     *         t1 -= y0123 / yder;
     *         converged = SkScalarAbs(t1 - t0) <= tol;  // NaN-safe
     *         ++iters;
     *     } while (!converged && (iters < maxiters));
     *     *t = t1;                  // Return the result.
     *
     *     // The result might be valid, even if outside of the range [0, 1], but
     *     // we never evaluate a Bezier outside this interval, so we return false.
     *     if (t1 < 0 || t1 > SK_Scalar1)
     *         return false;         // This shouldn't happen, but check anyway.
     *     return converged;
     *
     * #else  // BISECTION    // Linear convergence, typically 16 iterations.
     *
     *     // Check that the endpoints straddle zero.
     *     SkScalar tNeg, tPos;    // Negative and positive function parameters.
     *     if (ycrv[0] < 0) {
     *         if (ycrv[3] < 0)
     *             return false;
     *         tNeg = 0;
     *         tPos = SK_Scalar1;
     *     } else if (ycrv[0] > 0) {
     *         if (ycrv[3] > 0)
     *             return false;
     *         tNeg = SK_Scalar1;
     *         tPos = 0;
     *     } else {
     *         *t = 0;
     *         return true;
     *     }
     *
     *     const SkScalar tol = SK_Scalar1 / 65536;  // 1 for fixed, 1e-5 for float.
     *     do {
     *         SkScalar tMid = (tPos + tNeg) / 2;
     *         SkScalar y01   = SkScalarInterp(ycrv[0], ycrv[1], tMid);
     *         SkScalar y12   = SkScalarInterp(ycrv[1], ycrv[2], tMid);
     *         SkScalar y23   = SkScalarInterp(ycrv[2], ycrv[3], tMid);
     *         SkScalar y012  = SkScalarInterp(y01,     y12,     tMid);
     *         SkScalar y123  = SkScalarInterp(y12,     y23,     tMid);
     *         SkScalar y0123 = SkScalarInterp(y012,    y123,    tMid);
     *         if (y0123 == 0) {
     *             *t = tMid;
     *             return true;
     *         }
     *         if (y0123 < 0)  tNeg = tMid;
     *         else            tPos = tMid;
     *     } while (!(SkScalarAbs(tPos - tNeg) <= tol));   // Nan-safe
     *
     *     *t = (tNeg + tPos) / 2;
     *     return true;
     * #endif  // BISECTION
     * }
     * ```
     */
    public fun chopMonoAtY(
      pts: Array<SkPoint>,
      y: SkScalar,
      t: SkScalar?,
    ): Boolean {
      TODO("Implement chopMonoAtY")
    }
  }
}
