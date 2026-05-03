package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class AutoDeinstantiateTextureProxy {
 * public:
 *     AutoDeinstantiateTextureProxy(TextureProxy* textureProxy) : fTextureProxy(textureProxy) {}
 *
 *     ~AutoDeinstantiateTextureProxy() {
 *         if (fTextureProxy) {
 *             fTextureProxy->deinstantiate();
 *         }
 *     }
 *
 * private:
 *     TextureProxy* const fTextureProxy;
 * }
 * ```
 */
public data class AutoDeinstantiateTextureProxy public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextureProxy* const fTextureProxy
   * ```
   */
  private val fTextureProxy: TextureProxy?,
)
