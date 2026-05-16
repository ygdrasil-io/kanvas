package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileBasePriv {
 * public:
 *     int numChildCombinations() const {
 *         return fPrecompileBase->numChildCombinations();
 *     }
 *
 *     int numCombinations() const {
 *         return fPrecompileBase->numCombinations();
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const {
 *         fPrecompileBase->addToKey(keyContext, desiredCombination);
 *     }
 *
 * private:
 *     friend class PrecompileBase; // to construct/copy this type.
 *
 *     explicit PrecompileBasePriv(PrecompileBase* precompileBase)
 *             : fPrecompileBase(precompileBase) {
 *     }
 *
 *     PrecompileBasePriv& operator=(const PrecompileBasePriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PrecompileBasePriv* operator&() const;
 *     PrecompileBasePriv *operator&();
 *
 *     PrecompileBase* fPrecompileBase;
 * }
 * ```
 */
public data class PrecompileBasePriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PrecompileBase* fPrecompileBase
   * ```
   */
  private var fPrecompileBase: PrecompileBasePriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const {
   *         return fPrecompileBase->numChildCombinations();
   *     }
   * ```
   */
  public fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCombinations() const {
   *         return fPrecompileBase->numCombinations();
   *     }
   * ```
   */
  public fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const {
   *         fPrecompileBase->addToKey(keyContext, desiredCombination);
   *     }
   * ```
   */
  public fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileBasePriv& operator=(const PrecompileBasePriv&) = delete
   * ```
   */
  private fun assign(param0: PrecompileBasePriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileBasePriv* operator&() const
   * ```
   */
  private fun addressOf(): PrecompileBasePriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileBasePriv *operator&()
   * ```
   */
  public fun priv(): PrecompileBasePriv {
    TODO("Implement priv")
  }
}
