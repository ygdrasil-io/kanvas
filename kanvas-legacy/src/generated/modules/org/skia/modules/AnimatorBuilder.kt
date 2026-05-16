package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkNoncopyable

public typealias ScalarAnimatorBuilderINHERITED = AnimatorBuilder

public typealias TextAnimatorBuilderINHERITED = AnimatorBuilder

public typealias Vec2AnimatorBuilderINHERITED = AnimatorBuilder

/**
 * C++ original:
 * ```cpp
 * class AnimatorBuilder : public SkNoncopyable {
 * public:
 *     virtual ~AnimatorBuilder();
 *
 *     virtual sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder&,
 *                                                       const skjson::ArrayValue&) = 0;
 *
 *     virtual sk_sp<Animator> makeFromExpression(ExpressionManager&, const char*) = 0;
 *
 *     virtual bool parseValue(const AnimationBuilder&, const skjson::Value&) const = 0;
 *
 * protected:
 *     explicit AnimatorBuilder(Keyframe::Value::Type ty)
 *         : keyframe_type(ty) {}
 *
 *     virtual bool parseKFValue(const AnimationBuilder&,
 *                               const skjson::ObjectValue&,
 *                               const skjson::Value&,
 *                               Keyframe::Value*) = 0;
 *
 *     bool parseKeyframes(const AnimationBuilder&, const skjson::ArrayValue&);
 *
 *     std::vector<Keyframe>   fKFs; // Keyframe records, one per AE/Lottie keyframe.
 *     std::vector<SkCubicMap> fCMs; // Optional cubic mappers (Bezier interpolation).
 *
 * private:
 *     uint32_t parseMapping(const skjson::ObjectValue&);
 *
 *     const Keyframe::Value::Type keyframe_type;
 *
 *     // Track previous cubic map parameters (for deduping).
 *     SkPoint                     prev_c0 = { 0, 0 },
 *                                 prev_c1 = { 0, 0 };
 * }
 * ```
 */
public abstract class AnimatorBuilder public constructor(
  ty: Value.Type,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<Keyframe>   fKFs
   * ```
   */
  protected var fKFs: Int = TODO("Initialize fKFs")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkCubicMap> fCMs
   * ```
   */
  protected var fCMs: Int = TODO("Initialize fCMs")

  /**
   * C++ original:
   * ```cpp
   * const Keyframe::Value::Type keyframe_type
   * ```
   */
  private val keyframeType: Value.Type = TODO("Initialize keyframeType")

  /**
   * C++ original:
   * ```cpp
   * SkPoint                     prev_c0
   * ```
   */
  private var prevC0: Int = TODO("Initialize prevC0")

  /**
   * C++ original:
   * ```cpp
   * SkPoint                     prev_c0 = { 0, 0 },
   *                                 prev_c1
   * ```
   */
  private var prevC1: Int = TODO("Initialize prevC1")

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<KeyframeAnimator> makeFromKeyframes(const AnimationBuilder&,
   *                                                       const skjson::ArrayValue&) = 0
   * ```
   */
  public abstract fun makeFromKeyframes(param0: AnimationBuilder, param1: ArrayValue): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<Animator> makeFromExpression(ExpressionManager&, const char*) = 0
   * ```
   */
  public abstract fun makeFromExpression(param0: ExpressionManager, param1: String?): Int

  /**
   * C++ original:
   * ```cpp
   * virtual bool parseValue(const AnimationBuilder&, const skjson::Value&) const = 0
   * ```
   */
  public abstract fun parseValue(param0: AnimationBuilder, param1: Value): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool parseKFValue(const AnimationBuilder&,
   *                               const skjson::ObjectValue&,
   *                               const skjson::Value&,
   *                               Keyframe::Value*) = 0
   * ```
   */
  protected abstract fun parseKFValue(
    param0: AnimationBuilder,
    param1: ObjectValue,
    param2: Value,
    param3: Keyframe.Value?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool AnimatorBuilder::parseKeyframes(const AnimationBuilder& abuilder,
   *                                      const skjson::ArrayValue& jkfs) {
   *     // Keyframe format:
   *     //
   *     // [                        // array of
   *     //   {
   *     //     "t": <float>         // keyframe time
   *     //     "s": <T>             // keyframe value
   *     //     "h": <bool>          // optional constant/hold keyframe marker
   *     //     "i": [<float,float>] // optional "in" Bezier control point
   *     //     "o": [<float,float>] // optional "out" Bezier control point
   *     //   },
   *     //   ...
   *     // ]
   *     //
   *     // Legacy keyframe format:
   *     //
   *     // [                        // array of
   *     //   {
   *     //     "t": <float>         // keyframe time
   *     //     "s": <T>             // keyframe start value
   *     //     "e": <T>             // keyframe end value
   *     //     "h": <bool>          // optional constant/hold keyframe marker (constant mapping)
   *     //     "i": [<float,float>] // optional "in" Bezier control point (cubic mapping)
   *     //     "o": [<float,float>] // optional "out" Bezier control point (cubic mapping)
   *     //   },
   *     //   ...
   *     //   {
   *     //     "t": <float>         // last keyframe only specifies a t
   *     //                          // the value is prev. keyframe end value
   *     //   }
   *     // ]
   *     //
   *     // Note: the legacy format contains duplicates, as normal frames are contiguous:
   *     //       frame(n).e == frame(n+1).s
   *
   *     const auto parse_value = [&](const skjson::ObjectValue& jkf, size_t i, Keyframe::Value* v) {
   *         auto parsed = this->parseKFValue(abuilder, jkf, jkf["s"], v);
   *
   *         // A missing value is only OK for the last legacy KF
   *         // (where it is pulled from prev KF 'end' value).
   *         if (!parsed && i > 0 && i == jkfs.size() - 1) {
   *             const skjson::ObjectValue* prev_kf = jkfs[i - 1];
   *             SkASSERT(prev_kf);
   *             parsed = this->parseKFValue(abuilder, jkf, (*prev_kf)["e"], v);
   *         }
   *
   *         return parsed;
   *     };
   *
   *     bool constant_value = true;
   *
   *     fKFs.reserve(jkfs.size());
   *
   *     for (size_t i = 0; i < jkfs.size(); ++i) {
   *         const skjson::ObjectValue* jkf = jkfs[i];
   *         if (!jkf) {
   *             return false;
   *         }
   *
   *         float t;
   *         if (!Parse<float>((*jkf)["t"], &t)) {
   *             return false;
   *         }
   *
   *         Keyframe::Value v;
   *         if (!parse_value(*jkf, i, &v)) {
   *             return false;
   *         }
   *
   *         if (i > 0) {
   *             auto& prev_kf = fKFs.back();
   *
   *             // Ts must be monotonic.
   *             if (t < prev_kf.t) {
   *                 return false;
   *             }
   *
   *             // We can power-reduce the mapping of repeated values (implicitly constant).
   *             if (v.equals(prev_kf.v, keyframe_type)) {
   *                 prev_kf.mapping = Keyframe::kConstantMapping;
   *             }
   *         }
   *
   *         fKFs.push_back({t, v, this->parseMapping(*jkf)});
   *
   *         constant_value = constant_value && (v.equals(fKFs.front().v, keyframe_type));
   *     }
   *
   *     SkASSERT(fKFs.size() == jkfs.size());
   *     fCMs.shrink_to_fit();
   *
   *     if (constant_value) {
   *         // When all keyframes hold the same value, we can discard all but one
   *         // (interpolation has no effect).
   *         fKFs.resize(1);
   *     }
   *
   * #if(DUMP_KF_RECORDS)
   *     SkDEBUGF("Animator[%p], values: %lu, KF records: %zu\n",
   *              this, fKFs.back().v_idx + 1, fKFs.size());
   *     for (const auto& kf : fKFs) {
   *         SkDEBUGF("  { t: %1.3f, v_idx: %lu, mapping: %lu }\n", kf.t, kf.v_idx, kf.mapping);
   *     }
   * #endif
   *     return true;
   * }
   * ```
   */
  protected fun parseKeyframes(abuilder: AnimationBuilder, jkfs: ArrayValue): Boolean {
    TODO("Implement parseKeyframes")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t AnimatorBuilder::parseMapping(const skjson::ObjectValue& jkf) {
   *     if (ParseDefault(jkf["h"], false)) {
   *         return Keyframe::kConstantMapping;
   *     }
   *
   *     SkPoint c0, c1;
   *     if (!Parse(jkf["o"], &c0) ||
   *         !Parse(jkf["i"], &c1) ||
   *         SkCubicMap::IsLinear(c0, c1)) {
   *         return Keyframe::kLinearMapping;
   *     }
   *
   *     // De-dupe sequential cubic mappers.
   *     if (c0 != prev_c0 || c1 != prev_c1 || fCMs.empty()) {
   *         fCMs.emplace_back(c0, c1);
   *         prev_c0 = c0;
   *         prev_c1 = c1;
   *     }
   *
   *     SkASSERT(!fCMs.empty());
   *     return SkToU32(fCMs.size()) - 1 + Keyframe::kCubicIndexOffset;
   * }
   * ```
   */
  private fun parseMapping(jkf: ObjectValue): Int {
    TODO("Implement parseMapping")
  }
}

public typealias VectorAnimatorBuilderINHERITED = AnimatorBuilder
