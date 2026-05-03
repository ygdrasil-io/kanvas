package org.skia.modules

import kotlin.Float

/**
 * C++ original:
 * ```cpp
 * class FakeScalarExpressionEvaluator : public ExpressionEvaluator<float> {
 * public:
 *     float evaluate(float t) override { return 7.0f; }
 * }
 * ```
 */
public open class FakeScalarExpressionEvaluator : ExpressionEvaluator(), Float {
  /**
   * C++ original:
   * ```cpp
   * float evaluate(float t) override { return 7.0f; }
   * ```
   */
  public override fun evaluate(t: Float): Float {
    TODO("Implement evaluate")
  }
}
