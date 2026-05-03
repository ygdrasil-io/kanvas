package org.skia.modules

import kotlin.Float
import kotlin.String
import org.skia.foundation.SkSp
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class TextExpressionAnimator final : public Animator {
 * public:
 *     TextExpressionAnimator(sk_sp<ExpressionEvaluator<SkString>> expression_evaluator,
 *         TextValue* target_value)
 *         : fExpressionEvaluator(std::move(expression_evaluator))
 *         , fTarget(target_value) {}
 *
 * private:
 *
 *     StateChanged onSeek(float t) override {
 *         SkString old_value = fTarget->fText;
 *
 *         fTarget->fText = fExpressionEvaluator->evaluate(t);
 *
 *         return fTarget->fText != old_value;
 *     }
 *
 *     sk_sp<ExpressionEvaluator<SkString>> fExpressionEvaluator;
 *     TextValue* fTarget;
 * }
 * ```
 */
public class TextExpressionAnimator public constructor(
  expressionEvaluator: SkSp<ExpressionEvaluator<String>>,
  targetValue: TextValue?,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExpressionEvaluator<SkString>> fExpressionEvaluator
   * ```
   */
  private var fExpressionEvaluator: SkSp<ExpressionEvaluator<String>> =
      TODO("Initialize fExpressionEvaluator")

  /**
   * C++ original:
   * ```cpp
   * TextValue* fTarget
   * ```
   */
  private var fTarget: TextValue? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         SkString old_value = fTarget->fText;
   *
   *         fTarget->fText = fExpressionEvaluator->evaluate(t);
   *
   *         return fTarget->fText != old_value;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
