package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import org.skia.math.SkV2
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class Vec2AnimatorBuilder final : public AnimatorBuilder {
 *     public:
 *         Vec2AnimatorBuilder(Vec2Value* vec_target, float* rot_target)
 *             : INHERITED(Keyframe::Value::Type::kIndex)
 *             , fVecTarget(vec_target)
 *             , fRotTarget(rot_target) {}
 *
 *         sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
 *                                      const skjson::ArrayValue& jkfs) override {
 *             SkASSERT(jkfs.size() > 0);
 *
 *             fValues.reserve(jkfs.size());
 *             if (!this->parseKeyframes(abuilder, jkfs)) {
 *                 return nullptr;
 *             }
 *             fValues.shrink_to_fit();
 *
 *             return sk_sp<Vec2KeyframeAnimator>(
 *                         new Vec2KeyframeAnimator(std::move(fKFs),
 *                                                  std::move(fCMs),
 *                                                  std::move(fValues),
 *                                                  fVecTarget,
 *                                                  fRotTarget));
 *         }
 *
 *         sk_sp<Animator> makeFromExpression(ExpressionManager& em, const char* expr) override {
 *             sk_sp<ExpressionEvaluator<std::vector<SkScalar>>> expression_evaluator =
 *                 em.createArrayExpressionEvaluator(expr);
 *             return sk_make_sp<Vec2ExpressionAnimator>(expression_evaluator, fVecTarget);
 *         }
 *
 *         bool parseValue(const AnimationBuilder&, const skjson::Value& jv) const override {
 *             return ::skottie::Parse(jv, fVecTarget);
 *         }
 *
 *     private:
 *         void backfill_spatial(const Vec2KeyframeAnimator::SpatialValue& val) {
 *             SkASSERT(!fValues.empty());
 *             auto& prev_val = fValues.back();
 *             SkASSERT(!prev_val.cmeasure);
 *
 *             if (val.v2 == prev_val.v2) {
 *                 // spatial interpolation only make sense for noncoincident values
 *                 return;
 *             }
 *
 *             // Check whether v0 and v1 have the same direction AND ||v0||>=||v1||
 *             auto check_vecs = [](const SkV2& v0, const SkV2& v1) {
 *                 const auto v0_len2 = v0.lengthSquared(),
 *                            v1_len2 = v1.lengthSquared();
 *
 *                 // check magnitude
 *                 if (v0_len2 < v1_len2) {
 *                     return false;
 *                 }
 *
 *                 // v0, v1 have the same direction iff dot(v0,v1) = ||v0||*||v1||
 *                 // <=>    dot(v0,v1)^2 = ||v0||^2 * ||v1||^2
 *                 const auto dot = v0.dot(v1);
 *                 return SkScalarNearlyEqual(dot * dot, v0_len2 * v1_len2);
 *             };
 *
 *             if (check_vecs(val.v2 - prev_val.v2, fTo) &&
 *                 check_vecs(prev_val.v2 - val.v2, fTi)) {
 *                 // Both control points lie on the [prev_val..val] segment
 *                 //   => we can power-reduce the Bezier "curve" to a straight line.
 *                 return;
 *             }
 *
 *             // Finally, this looks like a legitimate spatial keyframe.
 *             SkPathBuilder p;
 *             p.moveTo (prev_val.v2.x        , prev_val.v2.y);
 *             p.cubicTo(prev_val.v2.x + fTo.x, prev_val.v2.y + fTo.y,
 *                            val.v2.x + fTi.x,      val.v2.y + fTi.y,
 *                            val.v2.x,              val.v2.y);
 *             prev_val.cmeasure = SkContourMeasureIter(p.detach(), false).next();
 *         }
 *
 *         bool parseKFValue(const AnimationBuilder&,
 *                           const skjson::ObjectValue& jkf,
 *                           const skjson::Value& jv,
 *                           Keyframe::Value* v) override {
 *             Vec2KeyframeAnimator::SpatialValue val;
 *             if (!::skottie::Parse(jv, &val.v2)) {
 *                 return false;
 *             }
 *
 *             if (fPendingSpatial) {
 *                 this->backfill_spatial(val);
 *             }
 *
 *             // Track the last keyframe spatial tangents (checked on next parseValue).
 *             fTi             = ParseDefault<SkV2>(jkf["ti"], {0,0});
 *             fTo             = ParseDefault<SkV2>(jkf["to"], {0,0});
 *             fPendingSpatial = fTi != SkV2{0,0} || fTo != SkV2{0,0};
 *
 *             if (fValues.empty() || val.v2 != fValues.back().v2 || fPendingSpatial) {
 *                 fValues.push_back(std::move(val));
 *             }
 *
 *             v->idx = SkToU32(fValues.size() - 1);
 *
 *             return true;
 *         }
 *
 *         std::vector<Vec2KeyframeAnimator::SpatialValue> fValues;
 *         Vec2Value*                fVecTarget; // required
 *         float*                    fRotTarget; // optional
 *         SkV2                      fTi{0,0},
 *                                   fTo{0,0};
 *         bool                      fPendingSpatial = false;
 *
 *         using INHERITED = AnimatorBuilder;
 *     }
 * ```
 */
public class Vec2AnimatorBuilder public constructor(
  vecTarget: Vec2Value?,
  rotTarget: Float?,
) : AnimatorBuilder(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::vector<Vec2KeyframeAnimator::SpatialValue> fValues
   * ```
   */
  private var fValues: Int = TODO("Initialize fValues")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value*                fVecTarget
   * ```
   */
  private var fVecTarget: Vec2Value? = TODO("Initialize fVecTarget")

  /**
   * C++ original:
   * ```cpp
   * float*                    fRotTarget
   * ```
   */
  private var fRotTarget: Float? = TODO("Initialize fRotTarget")

  /**
   * C++ original:
   * ```cpp
   * SkV2                      fTi{0,0}
   * ```
   */
  private var fTi: SkV2 = TODO("Initialize fTi")

  /**
   * C++ original:
   * ```cpp
   * SkV2                      fTi{0,0},
   *                                   fTo{0,0}
   * ```
   */
  private var fTo: SkV2 = TODO("Initialize fTo")

  /**
   * C++ original:
   * ```cpp
   * bool                      fPendingSpatial = false
   * ```
   */
  private var fPendingSpatial: Boolean = TODO("Initialize fPendingSpatial")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder& abuilder,
   *                                      const skjson::ArrayValue& jkfs) override {
   *             SkASSERT(jkfs.size() > 0);
   *
   *             fValues.reserve(jkfs.size());
   *             if (!this->parseKeyframes(abuilder, jkfs)) {
   *                 return nullptr;
   *             }
   *             fValues.shrink_to_fit();
   *
   *             return sk_sp<Vec2KeyframeAnimator>(
   *                         new Vec2KeyframeAnimator(std::move(fKFs),
   *                                                  std::move(fCMs),
   *                                                  std::move(fValues),
   *                                                  fVecTarget,
   *                                                  fRotTarget));
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
   *             sk_sp<ExpressionEvaluator<std::vector<SkScalar>>> expression_evaluator =
   *                 em.createArrayExpressionEvaluator(expr);
   *             return sk_make_sp<Vec2ExpressionAnimator>(expression_evaluator, fVecTarget);
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
   *             return ::skottie::Parse(jv, fVecTarget);
   *         }
   * ```
   */
  public override fun parseValue(param0: AnimationBuilder, jv: Value): Boolean {
    TODO("Implement parseValue")
  }

  /**
   * C++ original:
   * ```cpp
   * void backfill_spatial(const Vec2KeyframeAnimator::SpatialValue& val) {
   *             SkASSERT(!fValues.empty());
   *             auto& prev_val = fValues.back();
   *             SkASSERT(!prev_val.cmeasure);
   *
   *             if (val.v2 == prev_val.v2) {
   *                 // spatial interpolation only make sense for noncoincident values
   *                 return;
   *             }
   *
   *             // Check whether v0 and v1 have the same direction AND ||v0||>=||v1||
   *             auto check_vecs = [](const SkV2& v0, const SkV2& v1) {
   *                 const auto v0_len2 = v0.lengthSquared(),
   *                            v1_len2 = v1.lengthSquared();
   *
   *                 // check magnitude
   *                 if (v0_len2 < v1_len2) {
   *                     return false;
   *                 }
   *
   *                 // v0, v1 have the same direction iff dot(v0,v1) = ||v0||*||v1||
   *                 // <=>    dot(v0,v1)^2 = ||v0||^2 * ||v1||^2
   *                 const auto dot = v0.dot(v1);
   *                 return SkScalarNearlyEqual(dot * dot, v0_len2 * v1_len2);
   *             };
   *
   *             if (check_vecs(val.v2 - prev_val.v2, fTo) &&
   *                 check_vecs(prev_val.v2 - val.v2, fTi)) {
   *                 // Both control points lie on the [prev_val..val] segment
   *                 //   => we can power-reduce the Bezier "curve" to a straight line.
   *                 return;
   *             }
   *
   *             // Finally, this looks like a legitimate spatial keyframe.
   *             SkPathBuilder p;
   *             p.moveTo (prev_val.v2.x        , prev_val.v2.y);
   *             p.cubicTo(prev_val.v2.x + fTo.x, prev_val.v2.y + fTo.y,
   *                            val.v2.x + fTi.x,      val.v2.y + fTi.y,
   *                            val.v2.x,              val.v2.y);
   *             prev_val.cmeasure = SkContourMeasureIter(p.detach(), false).next();
   *         }
   * ```
   */
  private fun backfillSpatial(`val`: Vec2KeyframeAnimator.SpatialValue) {
    TODO("Implement backfillSpatial")
  }

  /**
   * C++ original:
   * ```cpp
   * bool parseKFValue(const AnimationBuilder&,
   *                           const skjson::ObjectValue& jkf,
   *                           const skjson::Value& jv,
   *                           Keyframe::Value* v) override {
   *             Vec2KeyframeAnimator::SpatialValue val;
   *             if (!::skottie::Parse(jv, &val.v2)) {
   *                 return false;
   *             }
   *
   *             if (fPendingSpatial) {
   *                 this->backfill_spatial(val);
   *             }
   *
   *             // Track the last keyframe spatial tangents (checked on next parseValue).
   *             fTi             = ParseDefault<SkV2>(jkf["ti"], {0,0});
   *             fTo             = ParseDefault<SkV2>(jkf["to"], {0,0});
   *             fPendingSpatial = fTi != SkV2{0,0} || fTo != SkV2{0,0};
   *
   *             if (fValues.empty() || val.v2 != fValues.back().v2 || fPendingSpatial) {
   *                 fValues.push_back(std::move(val));
   *             }
   *
   *             v->idx = SkToU32(fValues.size() - 1);
   *
   *             return true;
   *         }
   * ```
   */
  public override fun parseKFValue(
    param0: AnimationBuilder,
    jkf: ObjectValue,
    jv: Value,
    v: Keyframe.Value?,
  ): Boolean {
    TODO("Implement parseKFValue")
  }
}
