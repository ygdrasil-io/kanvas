package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class Plane final : public GeometryNode {
 * public:
 *     static sk_sp<Plane> Make() { return sk_sp<Plane>(new Plane()); }
 *
 * protected:
 *     void onClip(SkCanvas*, bool antiAlias) const override;
 *     void onDraw(SkCanvas*, const SkPaint&) const override;
 *     bool onContains(const SkPoint&)        const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *     SkPath onAsPath() const override;
 *
 * private:
 *     Plane();
 *
 *     using INHERITED = GeometryNode;
 * }
 * ```
 */
public class Plane public constructor() : GeometryNode() {
  /**
   * C++ original:
   * ```cpp
   * void Plane::onClip(SkCanvas*, bool) const {}
   * ```
   */
  protected override fun onClip(param0: SkCanvas?, antiAlias: Boolean) {
    TODO("Implement onClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void Plane::onDraw(SkCanvas* canvas, const SkPaint& paint) const {
   *     canvas->drawPaint(paint);
   * }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, paint: SkPaint) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Plane::onContains(const SkPoint&) const { return true; }
   * ```
   */
  protected override fun onContains(param0: SkPoint): Boolean {
    TODO("Implement onContains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Plane::onRevalidate(InvalidationController*, const SkMatrix&) {
   *     SkASSERT(this->hasInval());
   *
   *     return SkRect::MakeLTRB(SK_ScalarMin, SK_ScalarMin, SK_ScalarMax, SK_ScalarMax);
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, param1: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath Plane::onAsPath() const {
   *     SkPath path;
   *     path.setFillType(SkPathFillType::kInverseWinding);
   *
   *     return path;
   * }
   * ```
   */
  protected override fun onAsPath(): Int {
    TODO("Implement onAsPath")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Plane> Make() { return sk_sp<Plane>(new Plane()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
