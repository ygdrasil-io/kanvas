package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkSp
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class VectorExpressionAnimator final : public Animator {
 * public:
 *     VectorExpressionAnimator(sk_sp<ExpressionEvaluator<std::vector<float>>> expression_evaluator,
 *         std::vector<float>* target_value)
 *         : fExpressionEvaluator(std::move(expression_evaluator))
 *         , fTarget(target_value) {}
 *
 * private:
 *
 *     StateChanged onSeek(float t) override {
 *         std::vector<float> result = fExpressionEvaluator->evaluate(t);
 *         bool changed = false;
 *         for (size_t i = 0; i < fTarget->size(); i++) {
 *             // Use 0 as a default if the result is too small.
 *             float val = i >= result.size() ? 0 : result[i];
 *             if (!SkScalarNearlyEqual(val, (*fTarget)[i])) {
 *                 changed = true;
 *             }
 *             (*fTarget)[i] = val;
 *         }
 *
 *         return changed;
 *     }
 *
 *     sk_sp<ExpressionEvaluator<std::vector<float>>> fExpressionEvaluator;
 *     std::vector<float>* fTarget;
 * }
 * ```
 */
public class VectorExpressionAnimator public constructor(
  expressionEvaluator: SkSp<ExpressionEvaluator<List<Float>>>,
  targetValue: List<Float>?,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<float>* fTarget
   * ```
   */
  private var fTarget: Int? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         std::vector<float> result = fExpressionEvaluator->evaluate(t);
   *         bool changed = false;
   *         for (size_t i = 0; i < fTarget->size(); i++) {
   *             // Use 0 as a default if the result is too small.
   *             float val = i >= result.size() ? 0 : result[i];
   *             if (!SkScalarNearlyEqual(val, (*fTarget)[i])) {
   *                 changed = true;
   *             }
   *             (*fTarget)[i] = val;
   *         }
   *
   *         return changed;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
