package org.skia.modules

import kotlin.Float
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class FakeStringExpressionEvaluator : public ExpressionEvaluator<SkString> {
 * public:
 *     SkString evaluate(float t) override { return SkString("Hello, world!"); }
 * }
 * ```
 */
public open class FakeStringExpressionEvaluator : ExpressionEvaluator(), String {
  /**
   * C++ original:
   * ```cpp
   * SkString evaluate(float t) override { return SkString("Hello, world!"); }
   * ```
   */
  public override fun evaluate(t: Float): String {
    TODO("Implement evaluate")
  }
}
