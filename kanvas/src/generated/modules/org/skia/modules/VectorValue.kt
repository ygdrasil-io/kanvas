package org.skia.modules

import kotlin.Any
import kotlin.Float
import kotlin.collections.List
import kotlin.initializer_list
import org.skia.math.SkV3

/**
 * C++ original:
 * ```cpp
 * class VectorValue : public std::vector<float> {
 * public:
 *     VectorValue() = default;
 *
 *     VectorValue(std::initializer_list<float> l) : INHERITED(l) {}
 *
 *     operator SkV3()      const;
 * private:
 *     using INHERITED = std::vector<float>;
 * }
 * ```
 */
public open class VectorValue public constructor() : List<Any>(), Float {
  /**
   * C++ original:
   * ```cpp
   * VectorValue() = default
   * ```
   */
  public constructor(l: initializer_list<Float>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * VectorValue::operator SkV3() const {
   *     // best effort to turn this into a 3D point
   *     return SkV3 {
   *         this->size() > 0 ? (*this)[0] : 0,
   *         this->size() > 1 ? (*this)[1] : 0,
   *         this->size() > 2 ? (*this)[2] : 0,
   *     };
   * }
   * ```
   */
  public fun toSkV3(): SkV3 {
    TODO("Implement toSkV3")
  }
}

public typealias ColorValueINHERITED = VectorValue
