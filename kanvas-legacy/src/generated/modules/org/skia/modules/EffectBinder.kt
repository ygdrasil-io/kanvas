package org.skia.modules

import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class EffectBinder {
 * public:
 *     EffectBinder(const skjson::ArrayValue& jprops,
 *                  const AnimationBuilder& abuilder,
 *                  AnimatablePropertyContainer* acontainer)
 *         : fProps(jprops)
 *         , fBuilder(abuilder)
 *         , fContainer(acontainer) {}
 *
 *     template <typename T>
 *     const EffectBinder& bind(size_t prop_index, T& value) const {
 *         fContainer->bind(fBuilder, EffectBuilder::GetPropValue(fProps, prop_index), value);
 *
 *         return *this;
 *     }
 *
 * private:
 *     const skjson::ArrayValue&    fProps;
 *     const AnimationBuilder&      fBuilder;
 *     AnimatablePropertyContainer* fContainer;
 * }
 * ```
 */
public data class EffectBinder public constructor(
  /**
   * C++ original:
   * ```cpp
   * const skjson::ArrayValue&    fProps
   * ```
   */
  private val fProps: Int,
  /**
   * C++ original:
   * ```cpp
   * const AnimationBuilder&      fBuilder
   * ```
   */
  private val fBuilder: AnimationBuilder,
  /**
   * C++ original:
   * ```cpp
   * AnimatablePropertyContainer* fContainer
   * ```
   */
  private var fContainer: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     const EffectBinder& bind(size_t prop_index, T& value) const {
   *         fContainer->bind(fBuilder, EffectBuilder::GetPropValue(fProps, prop_index), value);
   *
   *         return *this;
   *     }
   * ```
   */
  public fun <T> bind(propIndex: ULong, `value`: T): EffectBinder {
    TODO("Implement bind")
  }
}
