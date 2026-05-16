package org.skia.modules

import kotlin.Boolean
import kotlin.String
import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class ScalarAnimatorBuilder final : public AnimatorBuilder {
 *     public:
 *         explicit ScalarAnimatorBuilder(ScalarValue* target)
 *             : INHERITED(Keyframe::Value::Type::kScalar)
 *             , fTarget(target) {}
 *
 *         sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
 *                                      const skjson::ArrayValue& jkfs) override {
 *             SkASSERT(jkfs.size() > 0);
 *             if (!this->parseKeyframes(abuilder, jkfs)) {
 *                 return nullptr;
 *             }
 *
 *             return sk_sp<ScalarKeyframeAnimator>(
 *                         new ScalarKeyframeAnimator(std::move(fKFs), std::move(fCMs), fTarget));
 *         }
 *
 *         sk_sp<Animator> makeFromExpression(ExpressionManager& em, const char* expr) override {
 *             sk_sp<ExpressionEvaluator<ScalarValue>> expression_evaluator =
 *                 em.createNumberExpressionEvaluator(expr);
 *             return sk_make_sp<ScalarExpressionAnimator>(expression_evaluator, fTarget);
 *         }
 *
 *
 *         bool parseValue(const AnimationBuilder&, const skjson::Value& jv) const override {
 *             return ::skottie::Parse(jv, fTarget);
 *         }
 *
 *     private:
 *         bool parseKFValue(const AnimationBuilder&,
 *                           const skjson::ObjectValue&,
 *                           const skjson::Value& jv,
 *                           Keyframe::Value* v) override {
 *             return ::skottie::Parse(jv, &v->flt);
 *         }
 *
 *         ScalarValue* fTarget;
 *
 *         using INHERITED = AnimatorBuilder;
 *     }
 * ```
 */
public class ScalarAnimatorBuilder public constructor(
  target: ScalarValue?,
) : AnimatorBuilder(TODO()) {
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
   * sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
   *                                      const skjson::ArrayValue& jkfs) override {
   *             SkASSERT(jkfs.size() > 0);
   *             if (!this->parseKeyframes(abuilder, jkfs)) {
   *                 return nullptr;
   *             }
   *
   *             return sk_sp<ScalarKeyframeAnimator>(
   *                         new ScalarKeyframeAnimator(std::move(fKFs), std::move(fCMs), fTarget));
   *         }
   * ```
   */
  public override fun makeFromKeyframes(abuilder: AnimationBuilder, jkfs: ArrayValue): SkSp<KeyframeAnimator> {
    TODO("Implement makeFromKeyframes")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Animator> makeFromExpression(ExpressionManager& em, const char* expr) override {
   *             sk_sp<ExpressionEvaluator<ScalarValue>> expression_evaluator =
   *                 em.createNumberExpressionEvaluator(expr);
   *             return sk_make_sp<ScalarExpressionAnimator>(expression_evaluator, fTarget);
   *         }
   * ```
   */
  public override fun makeFromExpression(em: ExpressionManager, expr: String?): SkSp<Animator> {
    TODO("Implement makeFromExpression")
  }

  /**
   * C++ original:
   * ```cpp
   * bool parseValue(const AnimationBuilder&, const skjson::Value& jv) const override {
   *             return ::skottie::Parse(jv, fTarget);
   *         }
   * ```
   */
  public override fun parseValue(param0: AnimationBuilder, jv: Value): Boolean {
    TODO("Implement parseValue")
  }

  /**
   * C++ original:
   * ```cpp
   * bool parseKFValue(const AnimationBuilder&,
   *                           const skjson::ObjectValue&,
   *                           const skjson::Value& jv,
   *                           Keyframe::Value* v) override {
   *             return ::skottie::Parse(jv, &v->flt);
   *         }
   * ```
   */
  public override fun parseKFValue(
    param0: AnimationBuilder,
    param1: ObjectValue,
    jv: Value,
    v: Keyframe.Value?,
  ): Boolean {
    TODO("Implement parseKFValue")
  }
}
