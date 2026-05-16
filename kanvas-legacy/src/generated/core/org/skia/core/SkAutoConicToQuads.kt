package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoConicToQuads {
 * public:
 *     SkAutoConicToQuads() : fQuadCount(0) {}
 *
 *     /**
 *      *  Given a conic and a tolerance, return the array of points for the
 *      *  approximating quad(s). Call countQuads() to know the number of quads
 *      *  represented in these points.
 *      *
 *      *  The quads are allocated to share end-points. e.g. if there are 4 quads,
 *      *  there will be 9 points allocated as follows
 *      *      quad[0] == pts[0..2]
 *      *      quad[1] == pts[2..4]
 *      *      quad[2] == pts[4..6]
 *      *      quad[3] == pts[6..8]
 *      */
 *     const SkPoint* computeQuads(const SkConic& conic, SkScalar tol) {
 *         int pow2 = conic.computeQuadPOW2(tol);
 *         fQuadCount = 1 << pow2;
 *         SkPoint* pts = fStorage.reset(1 + 2 * fQuadCount);
 *         fQuadCount = conic.chopIntoQuadsPOW2(pts, pow2);
 *         return pts;
 *     }
 *
 *     const SkPoint* computeQuads(SkSpan<const SkPoint> pts, SkScalar weight, SkScalar tol) {
 *         SkConic conic;
 *         conic.set(pts.data(), weight);
 *         return computeQuads(conic, tol);
 *     }
 *
 *     const SkPoint* computeQuads(const SkPoint pts[3], SkScalar weight, SkScalar tol) {
 *         return this->computeQuads({pts, 3}, weight, tol);
 *     }
 *
 *     int countQuads() const { return fQuadCount; }
 *
 * private:
 *     enum {
 *         kQuadCount = 8, // should handle most conics
 *         kPointCount = 1 + 2 * kQuadCount,
 *     };
 *     skia_private::AutoSTMalloc<kPointCount, SkPoint> fStorage;
 *     int fQuadCount; // #quads for current usage
 * }
 * ```
 */
public data class SkAutoConicToQuads public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTMalloc<kPointCount, SkPoint> fStorage
   * ```
   */
  private var fStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * int fQuadCount
   * ```
   */
  private var fQuadCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkPoint* computeQuads(const SkConic& conic, SkScalar tol) {
   *         int pow2 = conic.computeQuadPOW2(tol);
   *         fQuadCount = 1 << pow2;
   *         SkPoint* pts = fStorage.reset(1 + 2 * fQuadCount);
   *         fQuadCount = conic.chopIntoQuadsPOW2(pts, pow2);
   *         return pts;
   *     }
   * ```
   */
  public fun computeQuads(conic: SkConic, tol: SkScalar): SkPoint {
    TODO("Implement computeQuads")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* computeQuads(SkSpan<const SkPoint> pts, SkScalar weight, SkScalar tol) {
   *         SkConic conic;
   *         conic.set(pts.data(), weight);
   *         return computeQuads(conic, tol);
   *     }
   * ```
   */
  public fun computeQuads(
    pts: SkSpan<SkPoint>,
    weight: SkScalar,
    tol: SkScalar,
  ): SkPoint {
    TODO("Implement computeQuads")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* computeQuads(const SkPoint pts[3], SkScalar weight, SkScalar tol) {
   *         return this->computeQuads({pts, 3}, weight, tol);
   *     }
   * ```
   */
  public fun computeQuads(
    pts: Array<SkPoint>,
    weight: SkScalar,
    tol: SkScalar,
  ): SkPoint {
    TODO("Implement computeQuads")
  }

  /**
   * C++ original:
   * ```cpp
   * int countQuads() const { return fQuadCount; }
   * ```
   */
  public fun countQuads(): Int {
    TODO("Implement countQuads")
  }

  public companion object {
    public val kQuadCount: Int = TODO("Initialize kQuadCount")

    public val kPointCount: Int = TODO("Initialize kPointCount")
  }
}
