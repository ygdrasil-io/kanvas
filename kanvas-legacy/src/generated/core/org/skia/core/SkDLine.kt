package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkDLine {
 *     SkDPoint fPts[2];
 *
 *     const SkDPoint& operator[](int n) const { SkASSERT(n >= 0 && n < 2); return fPts[n]; }
 *     SkDPoint& operator[](int n) { SkASSERT(n >= 0 && n < 2); return fPts[n]; }
 *
 *     const SkDLine& set(const SkPoint pts[2]) {
 *         fPts[0] = pts[0];
 *         fPts[1] = pts[1];
 *         return *this;
 *     }
 *
 *     double exactPoint(const SkDPoint& xy) const;
 *     static double ExactPointH(const SkDPoint& xy, double left, double right, double y);
 *     static double ExactPointV(const SkDPoint& xy, double top, double bottom, double x);
 *
 *     double nearPoint(const SkDPoint& xy, bool* unequal) const;
 *     bool nearRay(const SkDPoint& xy) const;
 *     static double NearPointH(const SkDPoint& xy, double left, double right, double y);
 *     static double NearPointV(const SkDPoint& xy, double top, double bottom, double x);
 *     SkDPoint ptAtT(double t) const;
 *
 *     void dump() const;
 *     void dumpID(int ) const;
 *     void dumpInner() const;
 * }
 * ```
 */
public data class SkDLine public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDPoint fPts[2]
   * ```
   */
  public var fPts: Array<SkDPoint>,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& operator[](int n) const { SkASSERT(n >= 0 && n < 2); return fPts[n]; }
   * ```
   */
  public operator fun `get`(n: Int): SkDPoint {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint& operator[](int n) { SkASSERT(n >= 0 && n < 2); return fPts[n]; }
   * ```
   */
  public fun `set`(pts: Array<SkPoint>): SkDLine {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDLine& set(const SkPoint pts[2]) {
   *         fPts[0] = pts[0];
   *         fPts[1] = pts[1];
   *         return *this;
   *     }
   * ```
   */
  public fun exactPoint(xy: SkDPoint): Double {
    TODO("Implement exactPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDLine::exactPoint(const SkDPoint& xy) const {
   *     if (xy == fPts[0]) {  // do cheapest test first
   *         return 0;
   *     }
   *     if (xy == fPts[1]) {
   *         return 1;
   *     }
   *     return -1;
   * }
   * ```
   */
  public fun nearPoint(xy: SkDPoint, unequal: Boolean?): Double {
    TODO("Implement nearPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * double SkDLine::nearPoint(const SkDPoint& xy, bool* unequal) const {
   *     if (!AlmostBetweenUlps(fPts[0].fX, xy.fX, fPts[1].fX)
   *             || !AlmostBetweenUlps(fPts[0].fY, xy.fY, fPts[1].fY)) {
   *         return -1;
   *     }
   *     // project a perpendicular ray from the point to the line; find the T on the line
   *     SkDVector len = fPts[1] - fPts[0]; // the x/y magnitudes of the line
   *     double denom = len.fX * len.fX + len.fY * len.fY;  // see DLine intersectRay
   *     SkDVector ab0 = xy - fPts[0];
   *     double numer = len.fX * ab0.fX + ab0.fY * len.fY;
   *     if (!between(0, numer, denom)) {
   *         return -1;
   *     }
   *     if (!denom) {
   *         return 0;
   *     }
   *     double t = numer / denom;
   *     SkDPoint realPt = ptAtT(t);
   *     double dist = realPt.distance(xy);   // OPTIMIZATION: can we compare against distSq instead ?
   *     // find the ordinal in the original line with the largest unsigned exponent
   *     double tiniest = std::min(std::min(std::min(fPts[0].fX, fPts[0].fY), fPts[1].fX), fPts[1].fY);
   *     double largest = std::max(std::max(std::max(fPts[0].fX, fPts[0].fY), fPts[1].fX), fPts[1].fY);
   *     largest = std::max(largest, -tiniest);
   *     if (!AlmostEqualUlps_Pin(largest, largest + dist)) { // is the dist within ULPS tolerance?
   *         return -1;
   *     }
   *     if (unequal) {
   *         *unequal = (float) largest != (float) (largest + dist);
   *     }
   *     t = SkPinT(t);  // a looser pin breaks skpwww_lptemp_com_3
   *     SkASSERT(between(0, t, 1));
   *     return t;
   * }
   * ```
   */
  public fun nearRay(xy: SkDPoint): Boolean {
    TODO("Implement nearRay")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDLine::nearRay(const SkDPoint& xy) const {
   *     // project a perpendicular ray from the point to the line; find the T on the line
   *     SkDVector len = fPts[1] - fPts[0]; // the x/y magnitudes of the line
   *     double denom = len.fX * len.fX + len.fY * len.fY;  // see DLine intersectRay
   *     SkDVector ab0 = xy - fPts[0];
   *     double numer = len.fX * ab0.fX + ab0.fY * len.fY;
   *     double t = numer / denom;
   *     SkDPoint realPt = ptAtT(t);
   *     double dist = realPt.distance(xy);   // OPTIMIZATION: can we compare against distSq instead ?
   *     // find the ordinal in the original line with the largest unsigned exponent
   *     double tiniest = std::min(std::min(std::min(fPts[0].fX, fPts[0].fY), fPts[1].fX), fPts[1].fY);
   *     double largest = std::max(std::max(std::max(fPts[0].fX, fPts[0].fY), fPts[1].fX), fPts[1].fY);
   *     largest = std::max(largest, -tiniest);
   *     return RoughlyEqualUlps(largest, largest + dist); // is the dist within ULPS tolerance?
   * }
   * ```
   */
  public fun ptAtT(t: Double): SkDPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint SkDLine::ptAtT(double t) const {
   *     if (0 == t) {
   *         return fPts[0];
   *     }
   *     if (1 == t) {
   *         return fPts[1];
   *     }
   *     double one_t = 1 - t;
   *     SkDPoint result = { one_t * fPts[0].fX + t * fPts[1].fX, one_t * fPts[0].fY + t * fPts[1].fY };
   *     return result;
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDLine::dump() const {
   *     this->dumpInner();
   *     SkDebugf("}},\n");
   * }
   * ```
   */
  public fun dumpID(id: Int) {
    TODO("Implement dumpID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDLine::dumpID(int id) const {
   *     this->dumpInner();
   *     SkDebugf("}");
   *     DumpID(id);
   * }
   * ```
   */
  public fun dumpInner() {
    TODO("Implement dumpInner")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * double SkDLine::ExactPointH(const SkDPoint& xy, double left, double right, double y) {
     *     if (xy.fY == y) {
     *         if (xy.fX == left) {
     *             return 0;
     *         }
     *         if (xy.fX == right) {
     *             return 1;
     *         }
     *     }
     *     return -1;
     * }
     * ```
     */
    public fun exactPointH(
      xy: SkDPoint,
      left: Double,
      right: Double,
      y: Double,
    ): Double {
      TODO("Implement exactPointH")
    }

    /**
     * C++ original:
     * ```cpp
     * double SkDLine::ExactPointV(const SkDPoint& xy, double top, double bottom, double x) {
     *     if (xy.fX == x) {
     *         if (xy.fY == top) {
     *             return 0;
     *         }
     *         if (xy.fY == bottom) {
     *             return 1;
     *         }
     *     }
     *     return -1;
     * }
     * ```
     */
    public fun exactPointV(
      xy: SkDPoint,
      top: Double,
      bottom: Double,
      x: Double,
    ): Double {
      TODO("Implement exactPointV")
    }

    /**
     * C++ original:
     * ```cpp
     * double SkDLine::NearPointH(const SkDPoint& xy, double left, double right, double y) {
     *     if (!AlmostBequalUlps(xy.fY, y)) {
     *         return -1;
     *     }
     *     if (!AlmostBetweenUlps(left, xy.fX, right)) {
     *         return -1;
     *     }
     *     double t = (xy.fX - left) / (right - left);
     *     t = SkPinT(t);
     *     SkASSERT(between(0, t, 1));
     *     double realPtX = (1 - t) * left + t * right;
     *     SkDVector distU = {xy.fY - y, xy.fX - realPtX};
     *     double distSq = distU.fX * distU.fX + distU.fY * distU.fY;
     *     double dist = sqrt(distSq); // OPTIMIZATION: can we compare against distSq instead ?
     *     double tiniest = std::min(std::min(y, left), right);
     *     double largest = std::max(std::max(y, left), right);
     *     largest = std::max(largest, -tiniest);
     *     if (!AlmostEqualUlps(largest, largest + dist)) { // is the dist within ULPS tolerance?
     *         return -1;
     *     }
     *     return t;
     * }
     * ```
     */
    public fun nearPointH(
      xy: SkDPoint,
      left: Double,
      right: Double,
      y: Double,
    ): Double {
      TODO("Implement nearPointH")
    }

    /**
     * C++ original:
     * ```cpp
     * double SkDLine::NearPointV(const SkDPoint& xy, double top, double bottom, double x) {
     *     if (!AlmostBequalUlps(xy.fX, x)) {
     *         return -1;
     *     }
     *     if (!AlmostBetweenUlps(top, xy.fY, bottom)) {
     *         return -1;
     *     }
     *     double t = (xy.fY - top) / (bottom - top);
     *     t = SkPinT(t);
     *     SkASSERT(between(0, t, 1));
     *     double realPtY = (1 - t) * top + t * bottom;
     *     SkDVector distU = {xy.fX - x, xy.fY - realPtY};
     *     double distSq = distU.fX * distU.fX + distU.fY * distU.fY;
     *     double dist = sqrt(distSq); // OPTIMIZATION: can we compare against distSq instead ?
     *     double tiniest = std::min(std::min(x, top), bottom);
     *     double largest = std::max(std::max(x, top), bottom);
     *     largest = std::max(largest, -tiniest);
     *     if (!AlmostEqualUlps(largest, largest + dist)) { // is the dist within ULPS tolerance?
     *         return -1;
     *     }
     *     return t;
     * }
     * ```
     */
    public fun nearPointV(
      xy: SkDPoint,
      top: Double,
      bottom: Double,
      x: Double,
    ): Double {
      TODO("Implement nearPointV")
    }
  }
}
