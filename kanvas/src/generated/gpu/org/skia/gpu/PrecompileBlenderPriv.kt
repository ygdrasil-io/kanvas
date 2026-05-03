package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlenderPriv {
 * public:
 *     std::optional<SkBlendMode> asBlendMode() const { return fPrecompileBlender->asBlendMode(); }
 *
 *     // The remaining methods make this a viable standin for PrecompileBasePriv
 *     int numChildCombinations() const { return fPrecompileBlender->numChildCombinations(); }
 *
 *     int numCombinations() const { return fPrecompileBlender->numCombinations(); }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const {
 *         fPrecompileBlender->addToKey(keyContext, desiredCombination);
 *     }
 *
 * private:
 *     friend class PrecompileBlender; // to construct/copy this type.
 *
 *     explicit PrecompileBlenderPriv(PrecompileBlender* precompileBlender)
 *             : fPrecompileBlender(precompileBlender) {}
 *
 *     PrecompileBlenderPriv& operator=(const PrecompileBlenderPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PrecompileBlenderPriv* operator&() const;
 *     PrecompileBlenderPriv *operator&();
 *
 *     PrecompileBlender* fPrecompileBlender;
 * }
 * ```
 */
public data class PrecompileBlenderPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PrecompileBlender* fPrecompileBlender
   * ```
   */
  private var fPrecompileBlender: PrecompileBlenderPriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkBlendMode> asBlendMode() const { return fPrecompileBlender->asBlendMode(); }
   * ```
   */
  public fun asBlendMode(): Int {
    TODO("Implement asBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const { return fPrecompileBlender->numChildCombinations(); }
   * ```
   */
  public fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCombinations() const { return fPrecompileBlender->numCombinations(); }
   * ```
   */
  public fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const {
   *         fPrecompileBlender->addToKey(keyContext, desiredCombination);
   *     }
   * ```
   */
  public fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileBlenderPriv& operator=(const PrecompileBlenderPriv&) = delete
   * ```
   */
  private fun assign(param0: PrecompileBlenderPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileBlenderPriv* operator&() const
   * ```
   */
  private fun addressOf(): PrecompileBlenderPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileBlenderPriv *operator&()
   * ```
   */
  public fun priv(): PrecompileBlenderPriv {
    TODO("Implement priv")
  }
}
