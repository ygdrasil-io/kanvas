package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkFlattenable

public typealias SkBlendModeBlenderINHERITED = SkBlenderBase

/**
 * C++ original:
 * ```cpp
 * class SkBlenderBase : public SkBlender {
 * public:
 *     /**
 *      * Returns true if this SkBlender represents any SkBlendMode, and returns the blender's
 *      * SkBlendMode in `mode`. Returns false for other types of blends.
 *      */
 *     virtual std::optional<SkBlendMode> asBlendMode() const { return {}; }
 *
 *     bool affectsTransparentBlack() const;
 *
 *     [[nodiscard]] bool appendStages(const SkStageRec& rec) const {
 *         return this->onAppendStages(rec);
 *     }
 *
 *     [[nodiscard]] virtual bool onAppendStages(const SkStageRec& rec) const = 0;
 *
 *     virtual SkRuntimeEffect* asRuntimeEffect() const { return nullptr; }
 *
 *     static SkFlattenable::Type GetFlattenableType() { return kSkBlender_Type; }
 *     SkFlattenable::Type getFlattenableType() const override { return GetFlattenableType(); }
 *
 *     enum class BlenderType {
 *     #define M(type) k ## type,
 *         SK_ALL_BLENDERS(M)
 *     #undef M
 *     };
 *
 *     virtual BlenderType type() const = 0;
 * }
 * ```
 */
public abstract class SkBlenderBase : SkBlender() {
  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<SkBlendMode> asBlendMode() const { return {}; }
   * ```
   */
  public open fun asBlendMode(): Int {
    TODO("Implement asBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlenderBase::affectsTransparentBlack() const {
   *     if (auto blendMode = this->asBlendMode()) {
   *         SkBlendModeCoeff src, dst;
   *         if (SkBlendMode_AsCoeff(*blendMode, &src, &dst)) {
   *             // If the source is (0,0,0,0), then dst is preserved as long as its coefficient
   *             // evaluates to 1.0. This is true for kOne, kISA, and kISC. Anything else means the
   *             // blend mode affects transparent black.
   *             return dst != SkBlendModeCoeff::kOne &&
   *                    dst != SkBlendModeCoeff::kISA &&
   *                    dst != SkBlendModeCoeff::kISC;
   *         } else {
   *             // An advanced blend mode, which do not affect transparent black
   *             return false;
   *         }
   *     } else {
   *         // Blenders that aren't blend modes are assumed to modify transparent black.
   *        return true;
   *     }
   * }
   * ```
   */
  public fun affectsTransparentBlack(): Boolean {
    TODO("Implement affectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * bool appendStages(const SkStageRec& rec) const {
   *         return this->onAppendStages(rec);
   *     }
   * ```
   */
  public fun appendStages(rec: SkStageRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAppendStages(const SkStageRec& rec) const = 0
   * ```
   */
  public abstract fun onAppendStages(rec: SkStageRec): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual SkRuntimeEffect* asRuntimeEffect() const { return nullptr; }
   * ```
   */
  public open fun asRuntimeEffect(): SkRuntimeEffect {
    TODO("Implement asRuntimeEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override { return GetFlattenableType(); }
   * ```
   */
  public override fun getFlattenableType(): SkFlattenable.Type {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual BlenderType type() const = 0
   * ```
   */
  public abstract fun type(): BlenderType

  public enum class BlenderType

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() { return kSkBlender_Type; }
     * ```
     */
    public fun getFlattenableType(): SkFlattenable.Type {
      TODO("Implement getFlattenableType")
    }
  }
}
