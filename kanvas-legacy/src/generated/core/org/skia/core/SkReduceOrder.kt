package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * union SkReduceOrder {
 *     enum Quadratics {
 *         kNo_Quadratics,
 *         kAllow_Quadratics
 *     };
 *
 *     int reduce(const SkDCubic& cubic, Quadratics);
 *     int reduce(const SkDLine& line);
 *     int reduce(const SkDQuad& quad);
 *
 *     static SkPath::Verb Conic(const SkConic& conic, SkPoint* reducePts);
 *     static SkPath::Verb Cubic(const SkPoint pts[4], SkPoint* reducePts);
 *     static SkPath::Verb Quad(const SkPoint pts[3], SkPoint* reducePts);
 *
 *     SkDLine fLine;
 *     SkDQuad fQuad;
 *     SkDCubic fCubic;
 * }
 * ```
 */
public data class SkReduceOrder public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDLine fLine
   * ```
   */
  private var fLine: SkDLine,
  /**
   * C++ original:
   * ```cpp
   * SkDQuad fQuad
   * ```
   */
  private var fQuad: SkDQuad,
  /**
   * C++ original:
   * ```cpp
   * SkDCubic fCubic
   * ```
   */
  private var fCubic: SkDCubic,
) {
  /**
   * C++ original:
   * ```cpp
   * int SkReduceOrder::reduce(const SkDCubic& cubic, Quadratics allowQuadratics) {
   *     int index, minX, maxX, minY, maxY;
   *     int minXSet, minYSet;
   *     minX = maxX = minY = maxY = 0;
   *     minXSet = minYSet = 0;
   *     for (index = 1; index < 4; ++index) {
   *         if (cubic[minX].fX > cubic[index].fX) {
   *             minX = index;
   *         }
   *         if (cubic[minY].fY > cubic[index].fY) {
   *             minY = index;
   *         }
   *         if (cubic[maxX].fX < cubic[index].fX) {
   *             maxX = index;
   *         }
   *         if (cubic[maxY].fY < cubic[index].fY) {
   *             maxY = index;
   *         }
   *     }
   *     for (index = 0; index < 4; ++index) {
   *         double cx = cubic[index].fX;
   *         double cy = cubic[index].fY;
   *         double denom = std::max(fabs(cx), std::max(fabs(cy),
   *                 std::max(fabs(cubic[minX].fX), fabs(cubic[minY].fY))));
   *         if (denom == 0) {
   *             minXSet |= 1 << index;
   *             minYSet |= 1 << index;
   *             continue;
   *         }
   *         double inv = 1 / denom;
   *         if (approximately_equal_half(cx * inv, cubic[minX].fX * inv)) {
   *             minXSet |= 1 << index;
   *         }
   *         if (approximately_equal_half(cy * inv, cubic[minY].fY * inv)) {
   *             minYSet |= 1 << index;
   *         }
   *     }
   *     if (minXSet == 0xF) {  // test for vertical line
   *         if (minYSet == 0xF) {  // return 1 if all four are coincident
   *             return coincident_line(cubic, fCubic);
   *         }
   *         return vertical_line(cubic, fCubic);
   *     }
   *     if (minYSet == 0xF) {  // test for horizontal line
   *         return horizontal_line(cubic, fCubic);
   *     }
   *     int result = check_linear(cubic, minX, maxX, minY, maxY, fCubic);
   *     if (result) {
   *         return result;
   *     }
   *     if (allowQuadratics == SkReduceOrder::kAllow_Quadratics
   *             && (result = check_quadratic(cubic, fCubic))) {
   *         return result;
   *     }
   *     fCubic = cubic;
   *     return 4;
   * }
   * ```
   */
  private fun reduce(cubic: SkDCubic, allowQuadratics: Quadratics): Int {
    TODO("Implement reduce")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkReduceOrder::reduce(const SkDLine& line) {
   *     fLine[0] = line[0];
   *     int different = line[0] != line[1];
   *     fLine[1] = line[different];
   *     return 1 + different;
   * }
   * ```
   */
  private fun reduce(line: SkDLine): Int {
    TODO("Implement reduce")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkReduceOrder::reduce(const SkDQuad& quad) {
   *     int index, minX, maxX, minY, maxY;
   *     int minXSet, minYSet;
   *     minX = maxX = minY = maxY = 0;
   *     minXSet = minYSet = 0;
   *     for (index = 1; index < 3; ++index) {
   *         if (quad[minX].fX > quad[index].fX) {
   *             minX = index;
   *         }
   *         if (quad[minY].fY > quad[index].fY) {
   *             minY = index;
   *         }
   *         if (quad[maxX].fX < quad[index].fX) {
   *             maxX = index;
   *         }
   *         if (quad[maxY].fY < quad[index].fY) {
   *             maxY = index;
   *         }
   *     }
   *     for (index = 0; index < 3; ++index) {
   *         if (AlmostEqualUlps(quad[index].fX, quad[minX].fX)) {
   *             minXSet |= 1 << index;
   *         }
   *         if (AlmostEqualUlps(quad[index].fY, quad[minY].fY)) {
   *             minYSet |= 1 << index;
   *         }
   *     }
   *     if ((minXSet & 0x05) == 0x5 && (minYSet & 0x05) == 0x5) { // test for degenerate
   *         // this quad starts and ends at the same place, so never contributes
   *         // to the fill
   *         return coincident_line(quad, fQuad);
   *     }
   *     if (minXSet == 0x7) {  // test for vertical line
   *         return vertical_line(quad, fQuad);
   *     }
   *     if (minYSet == 0x7) {  // test for horizontal line
   *         return horizontal_line(quad, fQuad);
   *     }
   *     int result = check_linear(quad, minX, maxX, minY, maxY, fQuad);
   *     if (result) {
   *         return result;
   *     }
   *     fQuad = quad;
   *     return 3;
   * }
   * ```
   */
  private fun reduce(quad: SkDQuad): Int {
    TODO("Implement reduce")
  }

  public enum class Quadratics {
    kNo_Quadratics,
    kAllow_Quadratics,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkPath::Verb SkReduceOrder::Conic(const SkConic& c, SkPoint* reducePts) {
     *     SkPath::Verb verb = SkReduceOrder::Quad(c.fPts, reducePts);
     *     if (verb > SkPath::kLine_Verb && c.fW == 1) {
     *         return SkPath::kQuad_Verb;
     *     }
     *     return verb == SkPath::kQuad_Verb ? SkPath::kConic_Verb : verb;
     * }
     * ```
     */
    private fun conic(conic: SkConic, reducePts: SkPoint?): SkPathVerb {
      TODO("Implement conic")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath::Verb SkReduceOrder::Cubic(const SkPoint a[4], SkPoint* reducePts) {
     *     if (SkDPoint::ApproximatelyEqual(a[0], a[1]) && SkDPoint::ApproximatelyEqual(a[0], a[2])
     *             && SkDPoint::ApproximatelyEqual(a[0], a[3])) {
     *         reducePts[0] = a[0];
     *         return SkPath::kMove_Verb;
     *     }
     *     SkDCubic cubic;
     *     cubic.set(a);
     *     SkReduceOrder reducer;
     *     int order = reducer.reduce(cubic, kAllow_Quadratics);
     *     if (order == 2 || order == 3) {  // cubic became line or quad
     *         for (int index = 0; index < order; ++index) {
     *             *reducePts++ = reducer.fQuad[index].asSkPoint();
     *         }
     *     }
     *     return SkPathOpsPointsToVerb(order - 1);
     * }
     * ```
     */
    private fun cubic(pts: Array<SkPoint>, reducePts: SkPoint?): SkPathVerb {
      TODO("Implement cubic")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPath::Verb SkReduceOrder::Quad(const SkPoint a[3], SkPoint* reducePts) {
     *     SkDQuad quad;
     *     quad.set(a);
     *     SkReduceOrder reducer;
     *     int order = reducer.reduce(quad);
     *     if (order == 2) {  // quad became line
     *         for (int index = 0; index < order; ++index) {
     *             *reducePts++ = reducer.fLine[index].asSkPoint();
     *         }
     *     }
     *     return SkPathOpsPointsToVerb(order - 1);
     * }
     * ```
     */
    private fun quad(pts: Array<SkPoint>, reducePts: SkPoint?): SkPathVerb {
      TODO("Implement quad")
    }
  }
}
