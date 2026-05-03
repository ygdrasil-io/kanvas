package org.skia.effects

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRuntimeEffectBuilder {
 * public:
 *     explicit SkRuntimeEffectBuilder(sk_sp<SkRuntimeEffect> effect)
 *             : fEffect(std::move(effect))
 *             , fUniforms(SkData::MakeZeroInitialized(fEffect->uniformSize()))
 *             , fChildren(fEffect->children().size()) {}
 *     explicit SkRuntimeEffectBuilder(sk_sp<SkRuntimeEffect> effect, sk_sp<SkData> uniforms)
 *             : fEffect(std::move(effect))
 *             , fUniforms(std::move(uniforms))
 *             , fChildren(fEffect->children().size()) {}
 *
 *     // This is currently required by Android Framework but may go away if that dependency
 *     // can be removed.
 *     SkRuntimeEffectBuilder(const SkRuntimeEffectBuilder&) = default;
 *
 *     struct BuilderUniform {
 *         // Copy 'val' to this variable. No type conversion is performed - 'val' must be same
 *         // size as expected by the effect. Information about the variable can be queried by
 *         // looking at fVar. If the size is incorrect, no copy will be performed, and debug
 *         // builds will abort. If this is the result of querying a missing variable, fVar will
 *         // be nullptr, and assigning will also do nothing (and abort in debug builds).
 *         template <typename T>
 *         std::enable_if_t<std::is_trivially_copyable<T>::value, BuilderUniform&> operator=(
 *                 const T& val) {
 *             if (!fVar) {
 *                 SkDEBUGFAIL("Assigning to missing variable");
 *             } else if (sizeof(val) != fVar->sizeInBytes()) {
 *                 SkDEBUGFAIL("Incorrect value size");
 *             } else {
 *                 memcpy(SkTAddOffset<void>(fOwner->writableUniformData(), fVar->offset),
 *                        &val, sizeof(val));
 *             }
 *             return *this;
 *         }
 *
 *         BuilderUniform& operator=(const SkMatrix& val) {
 *             if (!fVar) {
 *                 SkDEBUGFAIL("Assigning to missing variable");
 *             } else if (fVar->sizeInBytes() != 9 * sizeof(float)) {
 *                 SkDEBUGFAIL("Incorrect value size");
 *             } else {
 *                 float* data = SkTAddOffset<float>(fOwner->writableUniformData(),
 *                                                   (ptrdiff_t)fVar->offset);
 *                 data[0] = val.get(0); data[1] = val.get(3); data[2] = val.get(6);
 *                 data[3] = val.get(1); data[4] = val.get(4); data[5] = val.get(7);
 *                 data[6] = val.get(2); data[7] = val.get(5); data[8] = val.get(8);
 *             }
 *             return *this;
 *         }
 *
 *         template <typename T>
 *         bool set(const T val[], const int count) {
 *             static_assert(std::is_trivially_copyable<T>::value, "Value must be trivial copyable");
 *             if (!fVar) {
 *                 SkDEBUGFAIL("Assigning to missing variable");
 *                 return false;
 *             } else if (sizeof(T) * count != fVar->sizeInBytes()) {
 *                 SkDEBUGFAIL("Incorrect value size");
 *                 return false;
 *             } else {
 *                 memcpy(SkTAddOffset<void>(fOwner->writableUniformData(), fVar->offset),
 *                        val, sizeof(T) * count);
 *             }
 *             return true;
 *         }
 *
 *         SkRuntimeEffectBuilder*         fOwner;
 *         const SkRuntimeEffect::Uniform* fVar;    // nullptr if the variable was not found
 *     };
 *
 *     struct BuilderChild {
 *         template <typename T> BuilderChild& operator=(sk_sp<T> val) {
 *             if (!fChild) {
 *                 SkDEBUGFAIL("Assigning to missing child");
 *             } else {
 *                 fOwner->fChildren[(size_t)fChild->index] =
 *                         SkRuntimeEffect::ChildPtr(std::move(val));
 *             }
 *             return *this;
 *         }
 *
 *         BuilderChild& operator=(std::nullptr_t) {
 *             if (!fChild) {
 *                 SkDEBUGFAIL("Assigning to missing child");
 *             } else {
 *                 fOwner->fChildren[(size_t)fChild->index] = SkRuntimeEffect::ChildPtr{};
 *             }
 *             return *this;
 *         }
 *
 *         SkRuntimeEffectBuilder*       fOwner;
 *         const SkRuntimeEffect::Child* fChild;  // nullptr if the child was not found
 *     };
 *
 *     const SkRuntimeEffect* effect() const { return fEffect.get(); }
 *
 *     BuilderUniform uniform(std::string_view name) { return { this, fEffect->findUniform(name) }; }
 *     BuilderChild child(std::string_view name) { return { this, fEffect->findChild(name) }; }
 *
 *     // Get access to the collated uniforms and children (in the order expected by APIs like
 *     // makeShader on the effect):
 *     sk_sp<const SkData> uniforms() const { return fUniforms; }
 *     SkSpan<const SkRuntimeEffect::ChildPtr> children() const { return fChildren; }
 *
 *     // Build methods, at this point checks are made to ensure the SkSL entry point `main` is correct
 *     sk_sp<SkShader> makeShader(const SkMatrix* localMatrix = nullptr) const;
 *     sk_sp<SkColorFilter> makeColorFilter() const;
 *     sk_sp<SkBlender> makeBlender() const;
 *
 *     ~SkRuntimeEffectBuilder() = default;
 *
 * protected:
 *     SkRuntimeEffectBuilder() = delete;
 *
 *     SkRuntimeEffectBuilder(SkRuntimeEffectBuilder&&) = default;
 *
 *     SkRuntimeEffectBuilder& operator=(SkRuntimeEffectBuilder&&) = delete;
 *     SkRuntimeEffectBuilder& operator=(const SkRuntimeEffectBuilder&) = delete;
 *
 * private:
 *     void* writableUniformData() {
 *         if (!fUniforms->unique()) {
 *             fUniforms = SkData::MakeWithCopy(fUniforms->data(), fUniforms->size());
 *         }
 *         return fUniforms->writable_data();
 *     }
 *
 *     sk_sp<SkRuntimeEffect>                 fEffect;
 *     sk_sp<SkData>                          fUniforms;
 *     std::vector<SkRuntimeEffect::ChildPtr> fChildren;
 *
 *     friend class SkRuntimeImageFilter;
 * }
 * ```
 */
public open class SkRuntimeEffectBuilder public constructor() {
  /**
   * C++ original:
   * ```cpp
   * explicit SkRuntimeEffectBuilder(sk_sp<SkRuntimeEffect> effect)
   * ```
   */
  public var skSp: SkRuntimeEffectBuilder = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect>                 fEffect
   * ```
   */
  private var fEffect: Int = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeEffectBuilder() = delete
   * ```
   */
  public constructor(param0: SkRuntimeEffectBuilder) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkRuntimeEffectBuilder(sk_sp<SkRuntimeEffect> effect)
   *             : fEffect(std::move(effect))
   *             , fUniforms(SkData::MakeZeroInitialized(fEffect->uniformSize()))
   * ```
   */
  public fun fUniforms(effect: SkSp<SkRuntimeEffect>): SkRuntimeEffectBuilder {
    TODO("Implement fUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkRuntimeEffectBuilder(sk_sp<SkRuntimeEffect> effect)
   *             : fEffect(std::move(effect))
   *             , fUniforms(SkData::MakeZeroInitialized(fEffect->uniformSize()))
   *             , fChildren(fEffect->children().size())
   * ```
   */
  public fun fChildren(effect: SkSp<SkRuntimeEffect>): SkRuntimeEffectBuilder {
    TODO("Implement fChildren")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRuntimeEffect* effect() const { return fEffect.get(); }
   * ```
   */
  public fun effect(): SkRuntimeEffect {
    TODO("Implement effect")
  }

  /**
   * C++ original:
   * ```cpp
   * BuilderUniform uniform(std::string_view name) { return { this, fEffect->findUniform(name) }; }
   * ```
   */
  public fun uniform(name: String): BuilderUniform {
    TODO("Implement uniform")
  }

  /**
   * C++ original:
   * ```cpp
   * BuilderChild child(std::string_view name) { return { this, fEffect->findChild(name) }; }
   * ```
   */
  public fun child(name: String): BuilderChild {
    TODO("Implement child")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> uniforms() const { return fUniforms; }
   * ```
   */
  public fun uniforms(): Int {
    TODO("Implement uniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkRuntimeEffect::ChildPtr> children() const { return fChildren; }
   * ```
   */
  public fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkRuntimeEffectBuilder::makeShader(const SkMatrix* localMatrix) const {
   *     return this->effect()->makeShader(this->uniforms(), this->children(), localMatrix);
   * }
   * ```
   */
  public fun makeShader(localMatrix: SkMatrix? = TODO()): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkRuntimeEffectBuilder::makeColorFilter() const {
   *     return this->effect()->makeColorFilter(this->uniforms(), this->children());
   * }
   * ```
   */
  public fun makeColorFilter(): Int {
    TODO("Implement makeColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBlender> SkRuntimeEffectBuilder::makeBlender() const {
   *     return this->effect()->makeBlender(this->uniforms(), this->children());
   * }
   * ```
   */
  public fun makeBlender(): Int {
    TODO("Implement makeBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeEffectBuilder& operator=(SkRuntimeEffectBuilder&&) = delete
   * ```
   */
  protected fun assign(param0: SkRuntimeEffectBuilder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRuntimeEffectBuilder& operator=(const SkRuntimeEffectBuilder&) = delete
   * ```
   */
  private fun writableUniformData() {
    TODO("Implement writableUniformData")
  }

  public data class BuilderUniform public constructor(
    public var fOwner: SkRuntimeEffectBuilder?,
    public val fVar: SkRuntimeEffect.Uniform?,
  ) {
    public fun <T> assign(`val`: T) {
      TODO("Implement assign")
    }

    public fun assign(`val`: SkMatrix) {
      TODO("Implement assign")
    }

    public fun <T> `set`(`val`: Array<T>, count: Int): Boolean {
      TODO("Implement set")
    }
  }

  public data class BuilderChild public constructor(
    public var fOwner: SkRuntimeEffectBuilder?,
    public val fChild: SkRuntimeEffect.Child?,
  ) {
    public fun assign(`val`: SkSp<T>) {
      TODO("Implement assign")
    }

    public fun assign(param0: Any?) {
      TODO("Implement assign")
    }
  }
}

public typealias SkRuntimeShaderBuilder = SkRuntimeEffectBuilder

public typealias SkRuntimeColorFilterBuilder = SkRuntimeEffectBuilder

public typealias SkRuntimeBlendBuilder = SkRuntimeEffectBuilder
