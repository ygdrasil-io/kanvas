package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API ExpressionManager : public SkRefCnt {
 * public:
 *     virtual sk_sp<ExpressionEvaluator<float>> createNumberExpressionEvaluator(
 *         const char expression[]) = 0;
 *
 *     virtual sk_sp<ExpressionEvaluator<SkString>> createStringExpressionEvaluator(
 *         const char expression[]) = 0;
 *
 *     virtual sk_sp<ExpressionEvaluator<std::vector<float>>> createArrayExpressionEvaluator(
 *         const char expression[]) = 0;
 * }
 * ```
 */
public abstract class ExpressionManager : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ExpressionEvaluator<float>> createNumberExpressionEvaluator(
   *         const char expression[]) = 0
   * ```
   */
  public abstract fun createNumberExpressionEvaluator(expression: CharArray): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ExpressionEvaluator<SkString>> createStringExpressionEvaluator(
   *         const char expression[]) = 0
   * ```
   */
  public abstract fun createStringExpressionEvaluator(expression: CharArray): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ExpressionEvaluator<std::vector<float>>> createArrayExpressionEvaluator(
   *         const char expression[]) = 0
   * ```
   */
  public abstract fun createArrayExpressionEvaluator(expression: CharArray): Int
}
