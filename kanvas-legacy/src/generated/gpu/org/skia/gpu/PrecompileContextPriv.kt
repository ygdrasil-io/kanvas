package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileContextPriv {
 * public:
 *     const Caps* caps() const { return fPrecompileContext->fSharedContext->caps(); }
 *     const ShaderCodeDictionary* shaderCodeDictionary() const {
 *         return fPrecompileContext->fSharedContext->shaderCodeDictionary();
 *     }
 *     ShaderCodeDictionary* shaderCodeDictionary() {
 *         return fPrecompileContext->fSharedContext->shaderCodeDictionary();
 *     }
 *     const RendererProvider* rendererProvider() const {
 *         return fPrecompileContext->fSharedContext->rendererProvider();
 *     }
 *     SharedContext* sharedContext() {
 *         return fPrecompileContext->fSharedContext.get();
 *     }
 *     ResourceProvider* resourceProvider() {
 *         return fPrecompileContext->fResourceProvider.get();
 *     }
 * #if defined(GPU_TEST_UTILS)
 *     GlobalCache* globalCache() {
 *         return fPrecompileContext->fSharedContext->globalCache();
 *     }
 * #endif
 *
 * private:
 *     friend class PrecompileContext; // to construct/copy this type.
 *
 *     explicit PrecompileContextPriv(PrecompileContext* precompileContext)
 *             : fPrecompileContext(precompileContext) {}
 *
 *     PrecompileContextPriv& operator=(const PrecompileContextPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const PrecompileContextPriv* operator&() const;
 *     PrecompileContextPriv *operator&();
 *
 *     PrecompileContext* fPrecompileContext;
 * }
 * ```
 */
public data class PrecompileContextPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * PrecompileContext* fPrecompileContext
   * ```
   */
  private var fPrecompileContext: PrecompileContextPriv?,
) {
  /**
   * C++ original:
   * ```cpp
   * const Caps* caps() const { return fPrecompileContext->fSharedContext->caps(); }
   * ```
   */
  public fun caps(): Caps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* shaderCodeDictionary() const {
   *         return fPrecompileContext->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun shaderCodeDictionary(): Int {
    TODO("Implement shaderCodeDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* shaderCodeDictionary() {
   *         return fPrecompileContext->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun rendererProvider(): Int {
    TODO("Implement rendererProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * const RendererProvider* rendererProvider() const {
   *         return fPrecompileContext->fSharedContext->rendererProvider();
   *     }
   * ```
   */
  public fun sharedContext(): Int {
    TODO("Implement sharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SharedContext* sharedContext() {
   *         return fPrecompileContext->fSharedContext.get();
   *     }
   * ```
   */
  public fun resourceProvider(): Int {
    TODO("Implement resourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* resourceProvider() {
   *         return fPrecompileContext->fResourceProvider.get();
   *     }
   * ```
   */
  public fun globalCache(): Int {
    TODO("Implement globalCache")
  }

  /**
   * C++ original:
   * ```cpp
   * GlobalCache* globalCache() {
   *         return fPrecompileContext->fSharedContext->globalCache();
   *     }
   * ```
   */
  private fun assign(param0: PrecompileContextPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileContextPriv& operator=(const PrecompileContextPriv&) = delete
   * ```
   */
  private fun addressOf(): PrecompileContextPriv {
    TODO("Implement addressOf")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileContextPriv* operator&() const
   * ```
   */
  public fun priv(): PrecompileContextPriv {
    TODO("Implement priv")
  }
}
