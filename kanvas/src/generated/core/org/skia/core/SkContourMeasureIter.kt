package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkContourMeasureIter {
 * public:
 *     SkContourMeasureIter();
 *     /**
 *      *  Initialize the Iter with a path.
 *      *  The parts of the path that are needed are copied, so the client is free to modify/delete
 *      *  the path after this call.
 *      *
 *      *  resScale controls the precision of the measure. values > 1 increase the
 *      *  precision (and possibly slow down the computation).
 *      */
 *     SkContourMeasureIter(const SkPath& path, bool forceClosed, SkScalar resScale = 1);
 *     ~SkContourMeasureIter();
 *
 *     SkContourMeasureIter(SkContourMeasureIter&&);
 *     SkContourMeasureIter& operator=(SkContourMeasureIter&&);
 *
 *     /**
 *      *  Reset the Iter with a path.
 *      *  The parts of the path that are needed are copied, so the client is free to modify/delete
 *      *  the path after this call.
 *      */
 *     void reset(const SkPath& path, bool forceClosed, SkScalar resScale = 1);
 *
 *     /**
 *      *  Iterates through contours in path, returning a contour-measure object for each contour
 *      *  in the path. Returns null when it is done.
 *      *
 *      *  This only returns non-zero length contours, where a contour is the segments between
 *      *  a kMove_Verb and either ...
 *      *      - the next kMove_Verb
 *      *      - kClose_Verb (1 or more)
 *      *      - kDone_Verb
 *      *  If it encounters a zero-length contour, it is skipped.
 *      */
 *     sk_sp<SkContourMeasure> next();
 *
 * private:
 *     class Impl;
 *
 *     std::unique_ptr<Impl> fImpl;
 * }
 * ```
 */
public data class SkContourMeasureIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Impl> fImpl
   * ```
   */
  private var fImpl: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkContourMeasureIter& SkContourMeasureIter::operator=(SkContourMeasureIter&&)
   * ```
   */
  public fun assign(param0: SkContourMeasureIter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkContourMeasureIter::reset(const SkPath& path, bool forceClosed, SkScalar resScale) {
   *     if (path.isFinite()) {
   *         fImpl = std::make_unique<Impl>(path, forceClosed, resScale);
   *     } else {
   *         fImpl.reset();
   *     }
   * }
   * ```
   */
  public fun reset(
    path: SkPath,
    forceClosed: Boolean,
    resScale: SkScalar = 1,
  ) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkContourMeasure> SkContourMeasureIter::next() {
   *     if (!fImpl) {
   *         return nullptr;
   *     }
   *     while (fImpl->hasNextSegments()) {
   *         auto cm = fImpl->buildSegments();
   *         if (cm) {
   *             return sk_sp<SkContourMeasure>(cm);
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun next(): Int {
    TODO("Implement next")
  }
}
