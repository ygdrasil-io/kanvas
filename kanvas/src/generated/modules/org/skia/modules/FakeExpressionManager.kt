package org.skia.modules

import kotlin.CharArray
import kotlin.Float
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class FakeExpressionManager : public ExpressionManager {
 * public:
 *     sk_sp<ExpressionEvaluator<float>> createNumberExpressionEvaluator(
 *             const char expression[]) override {
 *         return sk_make_sp<FakeScalarExpressionEvaluator>();
 *     }
 *
 *     sk_sp<ExpressionEvaluator<SkString>> createStringExpressionEvaluator(
 *             const char expression[]) override {
 *         return sk_make_sp<FakeStringExpressionEvaluator>();
 *     }
 *
 *     sk_sp<ExpressionEvaluator<std::vector<float>>> createArrayExpressionEvaluator(
 *             const char expression[]) override {
 *         return sk_make_sp<FakeVectorExpressionEvaluator>();
 *     }
 * }
 * ```
 */
public open class FakeExpressionManager : ExpressionManager() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionEvaluator<float>> createNumberExpressionEvaluator(
   *             const char expression[]) override {
   *         return sk_make_sp<FakeScalarExpressionEvaluator>();
   *     }
   * ```
   */
  public override fun createNumberExpressionEvaluator(expression: CharArray): SkSp<ExpressionEvaluator<Float>> {
    TODO("Implement createNumberExpressionEvaluator")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionEvaluator<SkString>> createStringExpressionEvaluator(
   *             const char expression[]) override {
   *         return sk_make_sp<FakeStringExpressionEvaluator>();
   *     }
   * ```
   */
  public override fun createStringExpressionEvaluator(expression: CharArray): SkSp<ExpressionEvaluator<String>> {
    TODO("Implement createStringExpressionEvaluator")
  }
}
