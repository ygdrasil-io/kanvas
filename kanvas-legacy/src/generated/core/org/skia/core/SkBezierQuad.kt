package org.skia.core

import kotlin.Double
import kotlin.Float
import kotlin.FloatArray
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkBezierQuad {
 * public:
 *     static SkSpan<const float> IntersectWithHorizontalLine(
 *             SkSpan<const SkPoint> controlPoints, float yIntercept,
 *             float intersectionStorage[2]);
 *
 *     /**
 *      * Given
 *      *    AY*t^2 -2*BY*t + CY = 0 and AX*t^2 - 2*BX*t + CX = 0,
 *      *
 *      * Find the t where AY*t^2 - 2*BY*t + CY - y = 0, then return AX*t^2 + - 2*BX*t + CX
 *      * where t is on [0, 1].
 *      *
 *      * - y - is the height of the line which intersects the quadratic.
 *      * - intersectionStorage - is the array to hold the return data pointed to in the span.
 *      *
 *      * Returns a span with the intersections of yIntercept, and the quadratic formed by A, B,
 *      * and C.
 *      */
 *     static SkSpan<const float> Intersect(
 *             double AX, double BX, double CX,
 *             double AY, double BY, double CY,
 *             double yIntercept,
 *             float intersectionStorage[2]);
 * }
 * ```
 */
public open class SkBezierQuad {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkSpan<const float>
     * SkBezierQuad::IntersectWithHorizontalLine(SkSpan<const SkPoint> controlPoints, float yIntercept,
     *                                           float intersectionStorage[2]) {
     *     SkASSERT(controlPoints.size() >= 3);
     *     const DPoint p0 = controlPoints[0],
     *                  p1 = controlPoints[1],
     *                  p2 = controlPoints[2];
     *
     *     // Calculate A, B, C using doubles to reduce round-off error.
     *     const DPoint A = p0 - 2 * p1 + p2,
     *     // Remember we are generating the polynomial in the form A*t^2 -2*B*t + C, so the factor
     *     // of 2 is not needed and the term is negated. This term for a Bézier curve is usually
     *     // 2(p1-p0).
     *                  B = p0 - p1,
     *                  C = p0;
     *
     *     return Intersect(A.x, B.x, C.x, A.y, B.y, C.y, yIntercept, intersectionStorage);
     * }
     * ```
     */
    public fun intersectWithHorizontalLine(
      controlPoints: SkSpan<SkPoint>,
      yIntercept: Float,
      intersectionStorage: FloatArray,
    ): SkSpan<Float> {
      TODO("Implement intersectWithHorizontalLine")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSpan<const float> SkBezierQuad::Intersect(
     *         double AX, double BX, double CX, double AY, double BY, double CY,
     *         double yIntercept, float intersectionStorage[2]) {
     *     auto [discriminant, r0, r1] = SkQuads::Roots(AY, BY, CY - yIntercept);
     *
     *     size_t intersectionCount = 0;
     *     // Round the roots to the nearest float to generate the values t. Valid t's are on the
     *     // domain [0, 1].
     *     const double t0 = pinTRange(r0);
     *     if (0 <= t0 && t0 <= 1) {
     *         intersectionStorage[intersectionCount++] = SkQuads::EvalAt(AX, -2 * BX, CX, t0);
     *     }
     *
     *     const double t1 = pinTRange(r1);
     *     if (0 <= t1 && t1 <= 1 && t1 != t0) {
     *         intersectionStorage[intersectionCount++] = SkQuads::EvalAt(AX, -2 * BX, CX, t1);
     *     }
     *
     *     return {intersectionStorage, intersectionCount};
     * }
     * ```
     */
    public fun intersect(
      ax: Double,
      bx: Double,
      cx: Double,
      ay: Double,
      `by`: Double,
      cy: Double,
      yIntercept: Double,
      intersectionStorage: FloatArray,
    ): SkSpan<Float> {
      TODO("Implement intersect")
    }
  }
}
