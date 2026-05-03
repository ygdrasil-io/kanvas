package org.skia.tests

import kotlin.Int
import org.skia.core.SkOpAngle

/**
 * C++ original:
 * ```cpp
 * class PathOpsAngleTester {
 * public:
 *     static int After(SkOpAngle& lh, SkOpAngle& rh) {
 *         return lh.after(&rh);
 *     }
 *
 *     static int AllOnOneSide(SkOpAngle& lh, SkOpAngle& rh) {
 *         return lh.lineOnOneSide(&rh, false);
 *     }
 *
 *     static int ConvexHullOverlaps(SkOpAngle& lh, SkOpAngle& rh) {
 *         return lh.convexHullOverlaps(&rh);
 *     }
 *
 *     static int Orderable(SkOpAngle& lh, SkOpAngle& rh) {
 *         return lh.orderable(&rh);
 *     }
 *
 *     static int EndsIntersect(SkOpAngle& lh, SkOpAngle& rh) {
 *         return lh.endsIntersect(&rh);
 *     }
 *
 *     static void SetNext(SkOpAngle& lh, SkOpAngle& rh) {
 *         lh.fNext = &rh;
 *     }
 * }
 * ```
 */
public open class PathOpsAngleTester {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static int After(SkOpAngle& lh, SkOpAngle& rh) {
     *         return lh.after(&rh);
     *     }
     * ```
     */
    public fun after(lh: SkOpAngle, rh: SkOpAngle): Int {
      TODO("Implement after")
    }

    /**
     * C++ original:
     * ```cpp
     * static int AllOnOneSide(SkOpAngle& lh, SkOpAngle& rh) {
     *         return lh.lineOnOneSide(&rh, false);
     *     }
     * ```
     */
    public fun allOnOneSide(lh: SkOpAngle, rh: SkOpAngle): Int {
      TODO("Implement allOnOneSide")
    }

    /**
     * C++ original:
     * ```cpp
     * static int ConvexHullOverlaps(SkOpAngle& lh, SkOpAngle& rh) {
     *         return lh.convexHullOverlaps(&rh);
     *     }
     * ```
     */
    public fun convexHullOverlaps(lh: SkOpAngle, rh: SkOpAngle): Int {
      TODO("Implement convexHullOverlaps")
    }

    /**
     * C++ original:
     * ```cpp
     * static int Orderable(SkOpAngle& lh, SkOpAngle& rh) {
     *         return lh.orderable(&rh);
     *     }
     * ```
     */
    public fun orderable(lh: SkOpAngle, rh: SkOpAngle): Int {
      TODO("Implement orderable")
    }

    /**
     * C++ original:
     * ```cpp
     * static int EndsIntersect(SkOpAngle& lh, SkOpAngle& rh) {
     *         return lh.endsIntersect(&rh);
     *     }
     * ```
     */
    public fun endsIntersect(lh: SkOpAngle, rh: SkOpAngle): Int {
      TODO("Implement endsIntersect")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetNext(SkOpAngle& lh, SkOpAngle& rh) {
     *         lh.fNext = &rh;
     *     }
     * ```
     */
    public fun setNext(lh: SkOpAngle, rh: SkOpAngle) {
      TODO("Implement setNext")
    }
  }
}
