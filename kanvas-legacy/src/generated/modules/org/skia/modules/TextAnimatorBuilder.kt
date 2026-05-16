package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class TextAnimatorBuilder final : public AnimatorBuilder {
 * public:
 *     explicit TextAnimatorBuilder(TextValue* target)
 *         : INHERITED(Keyframe::Value::Type::kIndex)
 *         , fTarget(target) {}
 *
 *     sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
 *                                     const skjson::ArrayValue& jkfs) override {
 *         SkASSERT(jkfs.size() > 0);
 *
 *         fValues.reserve(jkfs.size());
 *         if (!this->parseKeyframes(abuilder, jkfs)) {
 *             return nullptr;
 *         }
 *         fValues.shrink_to_fit();
 *
 *         return sk_sp<TextKeyframeAnimator>(
 *                     new TextKeyframeAnimator(std::move(fKFs),
 *                                                 std::move(fCMs),
 *                                                 std::move(fValues),
 *                                                 fTarget));
 *     }
 *
 *     sk_sp<Animator> makeFromExpression(ExpressionManager& em, const char* expr) override {
 *          sk_sp<ExpressionEvaluator<SkString>> expression_evaluator =
 *                 em.createStringExpressionEvaluator(expr);
 *             return sk_make_sp<TextExpressionAnimator>(expression_evaluator, fTarget);
 *     }
 *
 *     bool parseValue(const AnimationBuilder& abuilder, const skjson::Value& jv) const override {
 *         return Parse(jv, abuilder, fTarget);
 *     }
 *
 * private:
 *     bool parseKFValue(const AnimationBuilder& abuilder,
 *                         const skjson::ObjectValue&,
 *                         const skjson::Value& jv,
 *                         Keyframe::Value* v) override {
 *         TextValue val;
 *         if (!Parse(jv, abuilder, &val)) {
 *             return false;
 *         }
 *
 *         // TODO: full deduping?
 *         if (fValues.empty() || val != fValues.back()) {
 *             fValues.push_back(std::move(val));
 *         }
 *
 *         v->idx = SkToU32(fValues.size() - 1);
 *
 *         return true;
 *     }
 *
 *     std::vector<TextValue> fValues;
 *     TextValue*             fTarget;
 *
 *     using INHERITED = AnimatorBuilder;
 * }
 * ```
 */
public class TextAnimatorBuilder public constructor(
  target: TextValue?,
) : AnimatorBuilder(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::vector<TextValue> fValues
   * ```
   */
  private var fValues: Int = TODO("Initialize fValues")

  /**
   * C++ original:
   * ```cpp
   * TextValue*             fTarget
   * ```
   */
  private var fTarget: TextValue? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
   *                                     const skjson::ArrayValue& jkfs) override {
   *         SkASSERT(jkfs.size() > 0);
   *
   *         fValues.reserve(jkfs.size());
   *         if (!this->parseKeyframes(abuilder, jkfs)) {
   *             return nullptr;
   *         }
   *         fValues.shrink_to_fit();
   *
   *         return sk_sp<TextKeyframeAnimator>(
   *                     new TextKeyframeAnimator(std::move(fKFs),
   *                                                 std::move(fCMs),
   *                                                 std::move(fValues),
   *                                                 fTarget));
   *     }
   * ```
   */
  public override fun makeFromKeyframes(abuilder: AnimationBuilder, jkfs: ArrayValue): SkSp<KeyframeAnimator> {
    TODO("Implement makeFromKeyframes")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Animator> makeFromExpression(ExpressionManager& em, const char* expr) override {
   *          sk_sp<ExpressionEvaluator<SkString>> expression_evaluator =
   *                 em.createStringExpressionEvaluator(expr);
   *             return sk_make_sp<TextExpressionAnimator>(expression_evaluator, fTarget);
   *     }
   * ```
   */
  public override fun makeFromExpression(em: ExpressionManager, expr: String?): SkSp<Animator> {
    TODO("Implement makeFromExpression")
  }

  /**
   * C++ original:
   * ```cpp
   * bool parseValue(const AnimationBuilder& abuilder, const skjson::Value& jv) const override {
   *         return Parse(jv, abuilder, fTarget);
   *     }
   * ```
   */
  public override fun parseValue(abuilder: AnimationBuilder, jv: Value): Boolean {
    TODO("Implement parseValue")
  }

  /**
   * C++ original:
   * ```cpp
   * bool parseKFValue(const AnimationBuilder& abuilder,
   *                         const skjson::ObjectValue&,
   *                         const skjson::Value& jv,
   *                         Keyframe::Value* v) override {
   *         TextValue val;
   *         if (!Parse(jv, abuilder, &val)) {
   *             return false;
   *         }
   *
   *         // TODO: full deduping?
   *         if (fValues.empty() || val != fValues.back()) {
   *             fValues.push_back(std::move(val));
   *         }
   *
   *         v->idx = SkToU32(fValues.size() - 1);
   *
   *         return true;
   *     }
   * ```
   */
  public override fun parseKFValue(
    abuilder: AnimationBuilder,
    param1: ObjectValue,
    jv: Value,
    v: Keyframe.Value?,
  ): Boolean {
    TODO("Implement parseKFValue")
  }
}
