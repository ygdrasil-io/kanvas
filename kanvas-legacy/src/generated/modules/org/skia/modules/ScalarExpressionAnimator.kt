package org.skia.modules

import kotlin.Float
import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class ScalarExpressionAnimator final : public Animator {
 * public:
 *     ScalarExpressionAnimator(sk_sp<ExpressionEvaluator<ScalarValue>> expression_evaluator,
 *         ScalarValue* target_value)
 *         : fExpressionEvaluator(std::move(expression_evaluator))
 *         , fTarget(target_value) {}
 *
 * private:
 *
 *     StateChanged onSeek(float t) override {
 *         auto old_value = *fTarget;
 *
 *         *fTarget = fExpressionEvaluator->evaluate(t);
 *
 *         return *fTarget != old_value;
 *     }
 *
 *     sk_sp<ExpressionEvaluator<ScalarValue>> fExpressionEvaluator;
 *     ScalarValue* fTarget;
 * }
 * ```
 */
public class ScalarExpressionAnimator public constructor(
  expressionEvaluator: SkSp<ExpressionEvaluator<ScalarValue>>,
  targetValue: ScalarValue?,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionEvaluator<ScalarValue>> fExpressionEvaluator
   * ```
   */
  private var fExpressionEvaluator: SkSp<ExpressionEvaluator<ScalarValue>> =
      TODO("Initialize fExpressionEvaluator")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue* fTarget
   * ```
   */
  private var fTarget: ScalarValue? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         auto old_value = *fTarget;
   *
   *         *fTarget = fExpressionEvaluator->evaluate(t);
   *
   *         return *fTarget != old_value;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
