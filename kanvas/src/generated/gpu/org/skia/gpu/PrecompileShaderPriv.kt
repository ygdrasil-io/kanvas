package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileShaderPriv {
 * public:
 *     bool isConstant(int desiredCombination) const {
 *         return fPrecompileShader->isConstant(desiredCombination);
 *     }
 *
 *     bool isALocalMatrixShader() const {
 *         return fPrecompileShader->isALocalMatrixShader();
 *     }
 *
 *     // The remaining methods make this a viable standin for PrecompileBasePriv
 *     int numChildCombinations() const { return fPrecompileShader->numChildCombinations(); }
 *
 *     int numCombinations() const { return fPrecompileShader->numCombinations(); }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const {
 *         fPrecompileShader->addToKey(keyContext, desiredCombination);
 *     }
 *
 * private:
 *     friend class PrecompileShader; // to construct/copy this type.
 *
 *     explicit PrecompileShaderPriv(PrecompileShader* precompileShader)
 *             : fPrecompileShader(precompileShader) {}
 *
 *     PrecompileShaderPriv& operator=(const PrecompileShaderPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PrecompileShaderPriv* operator&() const;
 *     PrecompileShaderPriv *operator&();
 *
 *     PrecompileShader* fPrecompileShader;
 * }
 * ```
 */
public data class PrecompileShaderPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PrecompileShader* fPrecompileShader
   * ```
   */
  private var fPrecompileShader: PrecompileShaderPriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isConstant(int desiredCombination) const {
   *         return fPrecompileShader->isConstant(desiredCombination);
   *     }
   * ```
   */
  public fun isConstant(desiredCombination: Int): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isALocalMatrixShader() const {
   *         return fPrecompileShader->isALocalMatrixShader();
   *     }
   * ```
   */
  public fun isALocalMatrixShader(): Boolean {
    TODO("Implement isALocalMatrixShader")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const { return fPrecompileShader->numChildCombinations(); }
   * ```
   */
  public fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int numCombinations() const { return fPrecompileShader->numCombinations(); }
   * ```
   */
  public fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const {
   *         fPrecompileShader->addToKey(keyContext, desiredCombination);
   *     }
   * ```
   */
  public fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileShaderPriv& operator=(const PrecompileShaderPriv&) = delete
   * ```
   */
  private fun assign(param0: PrecompileShaderPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileShaderPriv* operator&() const
   * ```
   */
  private fun addressOf(): PrecompileShaderPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileShaderPriv *operator&()
   * ```
   */
  public fun priv(): PrecompileShaderPriv {
    TODO("Implement priv")
  }
}
