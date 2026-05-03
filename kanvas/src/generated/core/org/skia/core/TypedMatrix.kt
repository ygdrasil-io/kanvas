package org.skia.core

import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * struct TypedMatrix : public SkMatrix {
 *     TypedMatrix() {}
 *     TypedMatrix(const SkMatrix& matrix);
 * }
 * ```
 */
public open class TypedMatrix public constructor() : SkMatrix() {
  /**
   * C++ original:
   * ```cpp
   * TypedMatrix() {}
   * ```
   */
  public constructor(matrix: SkMatrix) : this(TODO()) {
    TODO("Implement constructor")
  }
}
