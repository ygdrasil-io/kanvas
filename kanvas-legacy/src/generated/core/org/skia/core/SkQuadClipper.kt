package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkIRect
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkQuadClipper {
 * public:
 *     SkQuadClipper();
 *
 *     void setClip(const SkIRect& clip);
 *
 *     bool clipQuad(const SkPoint src[3], SkPoint dst[3]);
 *
 * private:
 *     SkRect      fClip;
 * }
 * ```
 */
public data class SkQuadClipper public constructor(
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
   * void SkQuadClipper::setClip(const SkIRect& clip) {
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
   * bool SkQuadClipper::clipQuad(const SkPoint srcPts[3], SkPoint dst[3]) {
   *     bool reverse;
   *
   *     // we need the data to be monotonically increasing in Y
   *     if (srcPts[0].fY > srcPts[2].fY) {
   *         dst[0] = srcPts[2];
   *         dst[1] = srcPts[1];
   *         dst[2] = srcPts[0];
   *         reverse = true;
   *     } else {
   *         memcpy(dst, srcPts, 3 * sizeof(SkPoint));
   *         reverse = false;
   *     }
   *
   *     // are we completely above or below
   *     const SkScalar ctop = fClip.fTop;
   *     const SkScalar cbot = fClip.fBottom;
   *     if (dst[2].fY <= ctop || dst[0].fY >= cbot) {
   *         return false;
   *     }
   *
   *     SkScalar t;
   *     SkPoint tmp[5]; // for SkChopQuadAt
   *
   *     // are we partially above
   *     if (dst[0].fY < ctop) {
   *         if (chopMonoQuadAtY(dst, ctop, &t)) {
   *             // take the 2nd chopped quad
   *             SkChopQuadAt(dst, tmp, t);
   *             dst[0] = tmp[2];
   *             dst[1] = tmp[3];
   *         } else {
   *             // if chopMonoQuadAtY failed, then we may have hit inexact numerics
   *             // so we just clamp against the top
   *             for (int i = 0; i < 3; i++) {
   *                 if (dst[i].fY < ctop) {
   *                     dst[i].fY = ctop;
   *                 }
   *             }
   *         }
   *     }
   *
   *     // are we partially below
   *     if (dst[2].fY > cbot) {
   *         if (chopMonoQuadAtY(dst, cbot, &t)) {
   *             SkChopQuadAt(dst, tmp, t);
   *             dst[1] = tmp[1];
   *             dst[2] = tmp[2];
   *         } else {
   *             // if chopMonoQuadAtY failed, then we may have hit inexact numerics
   *             // so we just clamp against the bottom
   *             for (int i = 0; i < 3; i++) {
   *                 if (dst[i].fY > cbot) {
   *                     dst[i].fY = cbot;
   *                 }
   *             }
   *         }
   *     }
   *
   *     if (reverse) {
   *         using std::swap;
   *         swap(dst[0], dst[2]);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun clipQuad(src: Array<SkPoint>, dst: Array<SkPoint>): Boolean {
    TODO("Implement clipQuad")
  }
}
