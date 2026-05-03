package org.skia.effects

import kotlin.Boolean
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkPathMeasure
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkPath
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class Sk1DPathEffect : public SkPathEffectBase {
 * public:
 * protected:
 *     bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec*, const SkRect*,
 *                       const SkMatrix&) const override {
 *         SkPathMeasure   meas(src, false);
 *         do {
 *             int governor = MAX_REASONABLE_ITERATIONS;
 *             SkScalar    length = meas.getLength();
 *             SkScalar    distance = this->begin(length);
 *             while (distance < length && --governor >= 0) {
 *                 SkScalar delta = this->next(builder, distance, meas);
 *                 if (delta <= 0) {
 *                     break;
 *                 }
 *                 distance += delta;
 *             }
 *             if (governor < 0) {
 *                 return false;
 *             }
 *         } while (meas.nextContour());
 *         return true;
 *     }
 *
 *     /** Called at the start of each contour, returns the initial offset
 *         into that contour.
 *     */
 *     virtual SkScalar begin(SkScalar contourLength) const = 0;
 *     /** Called with the current distance along the path, with the current matrix
 *         for the point/tangent at the specified distance.
 *         Return the distance to travel for the next call. If return <= 0, then that
 *         contour is done.
 *     */
 *     virtual SkScalar next(SkPathBuilder* dst, SkScalar dist, SkPathMeasure&) const = 0;
 *
 * private:
 *     // For simplicity, assume fast bounds cannot be computed
 *     bool computeFastBounds(SkRect*) const override { return false; }
 * }
 * ```
 */
public abstract class Sk1DPathEffect : SkPathEffectBase() {
  /**
   * C++ original:
   * ```cpp
   * bool onFilterPath(SkPathBuilder* builder, const SkPath& src, SkStrokeRec*, const SkRect*,
   *                       const SkMatrix&) const override {
   *         SkPathMeasure   meas(src, false);
   *         do {
   *             int governor = MAX_REASONABLE_ITERATIONS;
   *             SkScalar    length = meas.getLength();
   *             SkScalar    distance = this->begin(length);
   *             while (distance < length && --governor >= 0) {
   *                 SkScalar delta = this->next(builder, distance, meas);
   *                 if (delta <= 0) {
   *                     break;
   *                 }
   *                 distance += delta;
   *             }
   *             if (governor < 0) {
   *                 return false;
   *             }
   *         } while (meas.nextContour());
   *         return true;
   *     }
   * ```
   */
  protected override fun onFilterPath(
    builder: SkPathBuilder?,
    src: SkPath,
    param2: SkStrokeRec?,
    param3: SkRect?,
    param4: SkMatrix,
  ): Boolean {
    TODO("Implement onFilterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkScalar begin(SkScalar contourLength) const = 0
   * ```
   */
  protected abstract fun begin(contourLength: SkScalar): SkScalar

  /**
   * C++ original:
   * ```cpp
   * virtual SkScalar next(SkPathBuilder* dst, SkScalar dist, SkPathMeasure&) const = 0
   * ```
   */
  protected abstract fun next(
    dst: SkPathBuilder?,
    dist: SkScalar,
    param2: SkPathMeasure,
  ): SkScalar

  /**
   * C++ original:
   * ```cpp
   * bool computeFastBounds(SkRect*) const override { return false; }
   * ```
   */
  public override fun computeFastBounds(param0: SkRect?): Boolean {
    TODO("Implement computeFastBounds")
  }
}

public typealias SkPath1DPathEffectImplINHERITED = Sk1DPathEffect
