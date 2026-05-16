package org.skia.modules

import kotlin.Float
import kotlin.collections.List
import org.skia.foundation.SkSp
import undefined.StateChanged
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class Vec2ExpressionAnimator final : public Animator {
 * public:
 *     Vec2ExpressionAnimator(sk_sp<ExpressionEvaluator<std::vector<float>>> expression_evaluator,
 *         Vec2Value* target_value)
 *         : fExpressionEvaluator(std::move(expression_evaluator))
 *         , fTarget(target_value) {}
 *
 * private:
 *
 *     StateChanged onSeek(float t) override {
 *         auto old_value = *fTarget;
 *
 *         std::vector<float> result = fExpressionEvaluator->evaluate(t);
 *         fTarget->x = result.size() > 0 ? result[0] : 0;
 *         fTarget->y = result.size() > 1 ? result[1] : 0;
 *
 *         return *fTarget != old_value;
 *     }
 *
 *     sk_sp<ExpressionEvaluator<std::vector<float>>> fExpressionEvaluator;
 *     Vec2Value* fTarget;
 * }
 * ```
 */
public class Vec2ExpressionAnimator public constructor(
  expressionEvaluator: SkSp<ExpressionEvaluator<List<Float>>>,
  targetValue: Vec2Value?,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value* fTarget
   * ```
   */
  private var fTarget: Vec2Value? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         auto old_value = *fTarget;
   *
   *         std::vector<float> result = fExpressionEvaluator->evaluate(t);
   *         fTarget->x = result.size() > 0 ? result[0] : 0;
   *         fTarget->y = result.size() > 1 ? result[1] : 0;
   *
   *         return *fTarget != old_value;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
