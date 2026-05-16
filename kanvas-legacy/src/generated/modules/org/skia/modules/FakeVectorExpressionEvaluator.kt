package org.skia.modules

import kotlin.Any
import kotlin.Float
import kotlin.Int
import kotlin.collections.List

/**
 * C++ original:
 * ```cpp
 * class FakeVectorExpressionEvaluator : public ExpressionEvaluator<std::vector<float>> {
 * public:
 *     std::vector<float> evaluate(float t) override { return {0.1f, 0.2f, 0.3f, 1.0f}; }
 * }
 * ```
 */
public open class FakeVectorExpressionEvaluator : ExpressionEvaluator(), List<Any>, Float {
  /**
   * C++ original:
   * ```cpp
   * std::vector<float> evaluate(float t) override { return {0.1f, 0.2f, 0.3f, 1.0f}; }
   * ```
   */
  public override fun evaluate(t: Float): Int {
    TODO("Implement evaluate")
  }
}
