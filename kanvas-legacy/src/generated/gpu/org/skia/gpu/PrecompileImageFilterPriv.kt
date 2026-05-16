package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileImageFilterPriv {
 * public:
 *     sk_sp<PrecompileColorFilter> isColorFilterNode() const {
 *         return fPrecompileImageFilter->isColorFilterNode();
 *     }
 *
 *     const PrecompileImageFilter* getInput(int index) const {
 *         return fPrecompileImageFilter->getInput(index);
 *     }
 *
 * private:
 *     friend class PrecompileImageFilter; // to construct/copy this type.
 *
 *     explicit PrecompileImageFilterPriv(PrecompileImageFilter* precompileImageFilter)
 *             : fPrecompileImageFilter(precompileImageFilter) {}
 *
 *     PrecompileImageFilterPriv& operator=(const PrecompileImageFilterPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PrecompileImageFilterPriv* operator&() const;
 *     PrecompileImageFilterPriv *operator&();
 *
 *     PrecompileImageFilter* fPrecompileImageFilter;
 * }
 * ```
 */
public data class PrecompileImageFilterPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PrecompileImageFilter* fPrecompileImageFilter
   * ```
   */
  private var fPrecompileImageFilter: PrecompileImageFilterPriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileColorFilter> isColorFilterNode() const {
   *         return fPrecompileImageFilter->isColorFilterNode();
   *     }
   * ```
   */
  public fun isColorFilterNode(): Int {
    TODO("Implement isColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileImageFilter* getInput(int index) const {
   *         return fPrecompileImageFilter->getInput(index);
   *     }
   * ```
   */
  public fun getInput(index: Int): PrecompileImageFilterPriv {
    TODO("Implement getInput")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileImageFilterPriv& operator=(const PrecompileImageFilterPriv&) = delete
   * ```
   */
  private fun assign(param0: PrecompileImageFilterPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileImageFilterPriv* operator&() const
   * ```
   */
  private fun addressOf(): PrecompileImageFilterPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileImageFilterPriv *operator&()
   * ```
   */
  public fun priv(): PrecompileImageFilterPriv {
    TODO("Implement priv")
  }
}
