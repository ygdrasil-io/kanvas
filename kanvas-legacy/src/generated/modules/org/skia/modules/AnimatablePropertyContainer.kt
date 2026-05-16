package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkV2
import undefined.ScalarValue
import undefined.StateChanged
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class AnimatablePropertyContainer : public Animator {
 * public:
 *     // This is the workhorse for property binding: depending on whether the property is animated,
 *     // it will either apply immediately or instantiate and attach a keyframe animator, scoped to
 *     // this container.
 *     template <typename T>
 *     bool bind(const AnimationBuilder&, const skjson::ObjectValue*, T*);
 *
 *     template <typename T>
 *     bool bind(const AnimationBuilder& abuilder, const skjson::ObjectValue* jobject, T& v) {
 *         return this->bind<T>(abuilder, jobject, &v);
 *     }
 *
 *     // A flavor of bind<Vec2Value> which drives an additional/optional orientation target
 *     // (rotation in degrees), when bound to a motion path property.
 *     bool bindAutoOrientable(const AnimationBuilder& abuilder,
 *                             const skjson::ObjectValue* jobject,
 *                             SkV2* v, float* orientation);
 *
 *     bool isStatic() const { return fAnimators.empty() && !fHasSlotID; }
 *
 * protected:
 *     friend class skottie::SlotManager;
 *     virtual void onSync() = 0;
 *
 *     void shrink_to_fit();
 *
 *     void attachDiscardableAdapter(sk_sp<AnimatablePropertyContainer>);
 *
 * private:
 *     StateChanged onSeek(float) final;
 *
 *     bool bindImpl(const AnimationBuilder&, const skjson::ObjectValue*, AnimatorBuilder&);
 *
 *     std::vector<sk_sp<Animator>> fAnimators;
 *     bool                         fHasSynced = false;
 *     bool                         fHasSlotID = false;
 * }
 * ```
 */
public abstract class AnimatablePropertyContainer : Animator() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<Animator>> fAnimators
   * ```
   */
  private var fAnimators: Int = TODO("Initialize fAnimators")

  /**
   * C++ original:
   * ```cpp
   * bool                         fHasSynced = false
   * ```
   */
  private var fHasSynced: Boolean = TODO("Initialize fHasSynced")

  /**
   * C++ original:
   * ```cpp
   * bool                         fHasSlotID = false
   * ```
   */
  private var fHasSlotID: Boolean = TODO("Initialize fHasSlotID")

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool bind(const AnimationBuilder&, const skjson::ObjectValue*, T*)
   * ```
   */
  public fun <T> bind(
    param0: AnimationBuilder,
    param1: ObjectValue?,
    param2: T?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     bool bind(const AnimationBuilder& abuilder, const skjson::ObjectValue* jobject, T& v) {
   *         return this->bind<T>(abuilder, jobject, &v);
   *     }
   * ```
   */
  public fun <T> bind(
    abuilder: AnimationBuilder,
    jobject: ObjectValue?,
    v: T,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimatablePropertyContainer::bindAutoOrientable(const AnimationBuilder& abuilder,
   *                                                      const skjson::ObjectValue* jprop,
   *                                                      Vec2Value* v, float* orientation) {
   *     if (!jprop) {
   *         return false;
   *     }
   *
   *     if (const auto* sid = ParseSlotID(jprop)) {
   *         fHasSlotID = true;
   *         abuilder.fSlotManager->trackVec2Value(SkString(sid->begin()), v, sk_ref_sp(this));
   *     }
   *
   *     if (!ParseDefault<bool>((*jprop)["s"], false)) {
   *         // Regular (static or keyframed) 2D value.
   *         Vec2AnimatorBuilder builder(v, orientation);
   *         return this->bindImpl(abuilder, jprop, builder);
   *     }
   *
   *     // Separate-dimensions vector value: each component is animated independently.
   *     bool boundX = this->bind(abuilder, (*jprop)["x"], &v->x);
   *     bool boundY = this->bind(abuilder, (*jprop)["y"], &v->y);
   *     return boundX || boundY;
   * }
   * ```
   */
  public fun bindAutoOrientable(
    abuilder: AnimationBuilder,
    jobject: ObjectValue?,
    v: SkV2?,
    orientation: Float?,
  ): Boolean {
    TODO("Implement bindAutoOrientable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isStatic() const { return fAnimators.empty() && !fHasSlotID; }
   * ```
   */
  public fun isStatic(): Boolean {
    TODO("Implement isStatic")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onSync() = 0
   * ```
   */
  protected abstract fun onSync()

  /**
   * C++ original:
   * ```cpp
   * void AnimatablePropertyContainer::shrink_to_fit() {
   *     fAnimators.shrink_to_fit();
   * }
   * ```
   */
  protected fun shrinkToFit() {
    TODO("Implement shrinkToFit")
  }

  /**
   * C++ original:
   * ```cpp
   * void AnimatablePropertyContainer::attachDiscardableAdapter(
   *         sk_sp<AnimatablePropertyContainer> child) {
   *     if (!child) {
   *         return;
   *     }
   *
   *     if (child->isStatic()) {
   *         child->seek(0);
   *         return;
   *     }
   *
   *     fAnimators.push_back(std::move(child));
   * }
   * ```
   */
  protected fun attachDiscardableAdapter(child: SkSp<AnimatablePropertyContainer>) {
    TODO("Implement attachDiscardableAdapter")
  }

  /**
   * C++ original:
   * ```cpp
   * Animator::StateChanged AnimatablePropertyContainer::onSeek(float t) {
   *     // The very first seek must trigger a sync, to ensure proper SG setup.
   *     bool changed = !fHasSynced;
   *
   *     for (const auto& animator : fAnimators) {
   *         changed |= animator->seek(t);
   *     }
   *
   *     if (changed) {
   *         this->onSync();
   *         fHasSynced = true;
   *     }
   *
   *     return changed;
   * }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool AnimatablePropertyContainer::bindImpl(const AnimationBuilder& abuilder,
   *                                            const skjson::ObjectValue* jprop,
   *                                            AnimatorBuilder& builder) {
   *     if (!jprop) {
   *         return false;
   *     }
   *
   *     if (const skjson::StringValue* jpropSlotID = (*jprop)["sid"] ) {
   *         if (!abuilder.getSlotsRoot()) {
   *             abuilder.log(Logger::Level::kWarning, jprop,
   *                          "Slotid found but no slots were found in the json. Using default values.");
   *         } else {
   *             const skjson::ObjectValue* slot = (*(abuilder.getSlotsRoot()))[jpropSlotID->begin()];
   *             if (!slot) {
   *                 abuilder.log(Logger::Level::kWarning, jprop,
   *                              "Specified slotID not found in 'slots'. Using default values.");
   *             } else {
   *                 jprop = (*slot)["p"];
   *             }
   *         }
   *     }
   *
   *     const auto& jpropA = (*jprop)["a"];
   *     const auto& jpropK = (*jprop)["k"];
   *
   *     // Handle expressions on the property.
   *     if (const skjson::StringValue* expr = (*jprop)["x"]) {
   *         if (!abuilder.expression_manager()) {
   *             abuilder.log(Logger::Level::kWarning, jprop,
   *                          "Expression encountered but ExpressionManager not provided.");
   *         } else {
   *             builder.parseValue(abuilder, jpropK);
   *             sk_sp<Animator> expression_animator = builder.makeFromExpression(
   *                                                     *abuilder.expression_manager(),
   *                                                     expr->begin());
   *             if (expression_animator) {
   *                 fAnimators.push_back(std::move(expression_animator));
   *                 return true;
   *             }
   *         }
   *     }
   *
   *     // Older Json versions don't have an "a" animation marker.
   *     // For those, we attempt to parse both ways.
   *     if (!ParseDefault<bool>(jpropA, false)) {
   *         if (builder.parseValue(abuilder, jpropK)) {
   *             // Static property.
   *             return true;
   *         }
   *
   *         if (!jpropA.is<skjson::NullValue>()) {
   *             abuilder.log(Logger::Level::kError, jprop,
   *                          "Could not parse (explicit) static property.");
   *             return false;
   *         }
   *     }
   *
   *     // Keyframed property.
   *     sk_sp<KeyframeAnimator> animator;
   *     const skjson::ArrayValue* jkfs = jpropK;
   *     if (jkfs && jkfs->size() > 0) {
   *         animator = builder.makeFromKeyframes(abuilder, *jkfs);
   *     }
   *
   *     if (!animator) {
   *         abuilder.log(Logger::Level::kError, jprop, "Could not parse keyframed property.");
   *         return false;
   *     }
   *
   *     if (animator->isConstant()) {
   *         // If all keyframes are constant, there is no reason to treat this
   *         // as an animated property - apply immediately and discard the animator.
   *         animator->seek(0);
   *     } else {
   *         fAnimators.push_back(std::move(animator));
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun bindImpl(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    builder: AnimatorBuilder,
  ): Boolean {
    TODO("Implement bindImpl")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<ScalarValue>(const AnimationBuilder& abuilder,
   *                                                     const skjson::ObjectValue* jprop,
   *                                                     ScalarValue* v) {
   *     if (const auto* sid = ParseSlotID(jprop)) {
   *         fHasSlotID = true;
   *         abuilder.fSlotManager->trackScalarValue(SkString(sid->begin()), v, sk_ref_sp(this));
   *     }
   *     ScalarAnimatorBuilder builder(v);
   *
   *     return this->bindImpl(abuilder, jprop, builder);
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: ScalarValue?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<ShapeValue>(const AnimationBuilder& abuilder,
   *                                                   const skjson::ObjectValue* jprop,
   *                                                   ShapeValue* v) {
   *     VectorAnimatorBuilder builder(v, parse_encoding_len, parse_encoding_data);
   *
   *     return this->bindImpl(abuilder, jprop, builder);
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: ShapeValue?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<TextValue>(const AnimationBuilder& abuilder,
   *                                                   const skjson::ObjectValue* jprop,
   *                                                   TextValue* v) {
   *     TextAnimatorBuilder builder(v);
   *     return this->bindImpl(abuilder, jprop, builder);
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: TextValue?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<Vec2Value>(const AnimationBuilder& abuilder,
   *                                                   const skjson::ObjectValue* jprop,
   *                                                   Vec2Value* v) {
   *     return this->bindAutoOrientable(abuilder, jprop, v, nullptr);
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: Vec2Value?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<VectorValue>(const AnimationBuilder& abuilder,
   *                                                     const skjson::ObjectValue* jprop,
   *                                                     VectorValue* v) {
   *     if (!jprop) {
   *         return false;
   *     }
   *
   *     if (!ParseDefault<bool>((*jprop)["s"], false)) {
   *         // Regular (static or keyframed) vector value.
   *         VectorAnimatorBuilder builder(
   *                     v,
   *                     // Len parser.
   *                     [](const skjson::Value& jv, size_t* len) -> bool {
   *                         if (const skjson::ArrayValue* ja = jv) {
   *                             *len = ja->size();
   *                             return true;
   *                         }
   *                         return false;
   *                     },
   *                     // Data parser.
   *                     [](const skjson::Value& jv, size_t len, float* data) {
   *                         return parse_array(jv, data, len);
   *                     });
   *
   *         return this->bindImpl(abuilder, jprop, builder);
   *     }
   *
   *     // Separate-dimensions vector value: each component is animated independently.
   *     *v = { 0, 0, 0 };
   *     bool boundX = this->bind(abuilder, (*jprop)["x"], v->data() + 0);
   *     bool boundY = this->bind(abuilder, (*jprop)["y"], v->data() + 1);
   *     bool boundZ = this->bind(abuilder, (*jprop)["z"], v->data() + 2);
   *     return boundX || boundY || boundZ;
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: VectorValue?,
  ): Boolean {
    TODO("Implement bind")
  }

  /**
   * C++ original:
   * ```cpp
   * template <>
   * bool AnimatablePropertyContainer::bind<ColorValue>(const AnimationBuilder& abuilder,
   *                                                     const skjson::ObjectValue* jprop,
   *                                                     ColorValue* v) {
   *     if (const auto* sid = ParseSlotID(jprop)) {
   *         fHasSlotID = true;
   *         abuilder.fSlotManager->trackColorValue(SkString(sid->begin()), v, sk_ref_sp(this));
   *     }
   *     return this->bind(abuilder, jprop, static_cast<VectorValue*>(v));
   * }
   * ```
   */
  public fun bind(
    abuilder: AnimationBuilder,
    jprop: ObjectValue?,
    v: ColorValue?,
  ): Boolean {
    TODO("Implement bind")
  }
}
