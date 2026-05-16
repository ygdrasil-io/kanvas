package org.skia.modules

import kotlin.Float
import kotlin.initializer_list
import org.skia.foundation.SkColor
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class ColorValue final : public VectorValue {
 * public:
 *     ColorValue() = default;
 *
 *     ColorValue(std::initializer_list<float> l) : INHERITED(l) {}
 *
 *     operator SkColor()   const;
 *     operator SkColor4f() const;
 *
 * private:
 *     using INHERITED = VectorValue;
 * }
 * ```
 */
public class ColorValue public constructor() : VectorValue() {
  /**
   * C++ original:
   * ```cpp
   * ColorValue() = default
   * ```
   */
  public constructor(l: initializer_list<Float>) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ColorValue::operator SkColor() const {
   *     return static_cast<SkColor4f>(*this).toSkColor();
   * }
   * ```
   */
  public fun toInt(): SkColor {
    TODO("Implement toInt")
  }

  /**
   * C++ original:
   * ```cpp
   * ColorValue::operator SkColor4f() const {
   *     // best effort to turn a vector into a color
   *     const auto r = this->size() > 0 ? SkTPin((*this)[0], 0.0f, 1.0f) : 0,
   *                g = this->size() > 1 ? SkTPin((*this)[1], 0.0f, 1.0f) : 0,
   *                b = this->size() > 2 ? SkTPin((*this)[2], 0.0f, 1.0f) : 0,
   *                a = this->size() > 3 ? SkTPin((*this)[3], 0.0f, 1.0f) : 1;
   *
   *     return { r, g, b, a };
   * }
   * ```
   */
  public fun toSkRGBA4f(): SkColor4f {
    TODO("Implement toSkRGBA4f")
  }
}
