package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.CharArray
import kotlin.ULong
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkData
import org.skia.foundation.SkDrawable
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * template <typename Base>
 * class SkCanvasVirtualEnforcer : public Base {
 * public:
 *     using Base::Base;
 *
 * protected:
 *     void onDrawPaint(const SkPaint& paint) override = 0;
 *     void onDrawBehind(const SkPaint&) override {} // make zero after android updates
 *     void onDrawRect(const SkRect& rect, const SkPaint& paint) override = 0;
 *     void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override = 0;
 *     void onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
 *                       const SkPaint& paint) override = 0;
 *     void onDrawOval(const SkRect& rect, const SkPaint& paint) override = 0;
 *     void onDrawArc(const SkRect& rect, SkScalar startAngle, SkScalar sweepAngle, bool useCenter,
 *                    const SkPaint& paint) override = 0;
 *     void onDrawPath(const SkPath& path, const SkPaint& paint) override = 0;
 *     void onDrawRegion(const SkRegion& region, const SkPaint& paint) override = 0;
 *
 *     void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
 *                         const SkPaint& paint) override = 0;
 *
 *     void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
 *                      const SkPoint texCoords[4], SkBlendMode mode,
 *                      const SkPaint& paint) override = 0;
 *     void onDrawPoints(SkCanvas::PointMode mode, size_t count, const SkPoint pts[],
 *                       const SkPaint& paint) override = 0;
 *
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     // This is under active development for Chrome and not used in Android. Hold off on adding
 *     // implementations in Android's SkCanvas subclasses until this stabilizes.
 *     void onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
 *             SkCanvas::QuadAAFlags aaFlags, const SkColor4f& color, SkBlendMode mode) override {}
 * #else
 *     void onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
 *             SkCanvas::QuadAAFlags aaFlags, const SkColor4f& color, SkBlendMode mode) override = 0;
 * #endif
 *
 *     void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) override = 0;
 *     void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override = 0;
 *
 *     void onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) override = 0;
 *     void onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
 *                        const SkPaint* paint) override = 0;
 * }
 * ```
 */
public abstract class SkCanvasVirtualEnforcer<Base> : Base() {
  /**
   * C++ original:
   * ```cpp
   * void onDrawPaint(const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawPaint(paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawBehind(const SkPaint&) override {}
   * ```
   */
  protected override fun onDrawBehind(param0: SkPaint) {
    TODO("Implement onDrawBehind")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDrawRect(const SkRect& rect, const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawRect(rect: SkRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawRRect(const SkRRect& rrect, const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawRRect(rrect: SkRRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawDRRect(const SkRRect& outer, const SkRRect& inner,
   *                       const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawDRRect(
    outer: SkRRect,
    `inner`: SkRRect,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawOval(const SkRect& rect, const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawOval(rect: SkRect, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawArc(const SkRect& rect, SkScalar startAngle, SkScalar sweepAngle, bool useCenter,
   *                    const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawArc(
    rect: SkRect,
    startAngle: SkScalar,
    sweepAngle: SkScalar,
    useCenter: Boolean,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawPath(const SkPath& path, const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawPath(path: SkPath, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawRegion(const SkRegion& region, const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawRegion(region: SkRegion, paint: SkPaint)

  /**
   * C++ original:
   * ```cpp
   * void onDrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y,
   *                         const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawTextBlob(
    blob: SkTextBlob?,
    x: SkScalar,
    y: SkScalar,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawPatch(const SkPoint cubics[12], const SkColor colors[4],
   *                      const SkPoint texCoords[4], SkBlendMode mode,
   *                      const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawPatch(
    cubics: Array<SkPoint>,
    colors: Array<SkColor>,
    texCoords: Array<SkPoint>,
    mode: SkBlendMode,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawPoints(SkCanvas::PointMode mode, size_t count, const SkPoint pts[],
   *                       const SkPaint& paint) override = 0
   * ```
   */
  protected abstract override fun onDrawPoints(
    mode: SkCanvas.PointMode,
    count: ULong,
    pts: Array<SkPoint>,
    paint: SkPaint,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
   *             SkCanvas::QuadAAFlags aaFlags, const SkColor4f& color, SkBlendMode mode) override = 0
   * ```
   */
  protected abstract override fun onDrawEdgeAAQuad(
    rect: SkRect,
    clip: Array<SkPoint>,
    aaFlags: SkCanvas.QuadAAFlags,
    color: SkColor4f,
    mode: SkBlendMode,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawAnnotation(const SkRect& rect, const char key[], SkData* value) override = 0
   * ```
   */
  protected abstract override fun onDrawAnnotation(
    rect: SkRect,
    key: CharArray,
    `value`: SkData?,
  )

  /**
   * C++ original:
   * ```cpp
   * void onDrawShadowRec(const SkPath&, const SkDrawShadowRec&) override = 0
   * ```
   */
  protected abstract override fun onDrawShadowRec(param0: SkPath, param1: SkDrawShadowRec)

  /**
   * C++ original:
   * ```cpp
   * void onDrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) override = 0
   * ```
   */
  protected abstract override fun onDrawDrawable(drawable: SkDrawable?, matrix: SkMatrix?)

  /**
   * C++ original:
   * ```cpp
   * void onDrawPicture(const SkPicture* picture, const SkMatrix* matrix,
   *                        const SkPaint* paint) override = 0
   * ```
   */
  protected abstract override fun onDrawPicture(
    picture: SkPicture?,
    matrix: SkMatrix?,
    paint: SkPaint?,
  )
}
