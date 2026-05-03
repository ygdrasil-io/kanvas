package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class Color : public PaintNode {
 * public:
 *     static sk_sp<Color> Make(SkColor c);
 *
 *     SG_ATTRIBUTE(Color, SkColor, fColor)
 *
 * protected:
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 *     void onApplyToPaint(SkPaint*) const override;
 *
 * private:
 *     explicit Color(SkColor);
 *
 *     SkColor fColor;
 *
 *     friend class skottie::internal::AnimationBuilder;
 * }
 * ```
 */
public open class Color public constructor(
  c: SkColor,
) : PaintNode() {
  /**
   * C++ original:
   * ```cpp
   * SkColor fColor
   * ```
   */
  private var fColor: Int = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Color, SkColor, fColor)
   * ```
   */
  public fun sgATTRIBUTE(param0: Color, param1: SkColor): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void Color::onApplyToPaint(SkPaint* paint) const {
   *     paint->setColor(fColor);
   * }
   * ```
   */
  protected override fun onApplyToPaint(paint: SkPaint?) {
    TODO("Implement onApplyToPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Color::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     return SkRect::MakeEmpty();
   * }
   * ```
   */
  public fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): SkRect {
    TODO("Implement onRevalidate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<Color> Color::Make(SkColor c) {
     *     return sk_sp<Color>(new Color(c));
     * }
     * ```
     */
    public fun make(c: SkColor): Int {
      TODO("Implement make")
    }
  }
}
