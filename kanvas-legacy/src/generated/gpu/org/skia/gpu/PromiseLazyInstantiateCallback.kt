package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PromiseLazyInstantiateCallback {
 * public:
 *     PromiseLazyInstantiateCallback(sk_sp<RefCntedCallback> releaseHelper,
 *                                    GraphitePromiseTextureFulfillProc fulfillProc,
 *                                    GraphitePromiseTextureFulfillContext fulfillContext,
 *                                    GraphitePromiseTextureReleaseProc textureReleaseProc,
 *                                    std::string_view label)
 *             : fReleaseHelper(std::move(releaseHelper))
 *             , fFulfillProc(fulfillProc)
 *             , fFulfillContext(fulfillContext)
 *             , fTextureReleaseProc(textureReleaseProc)
 *             , fLabel(label) {
 *     }
 *     PromiseLazyInstantiateCallback(PromiseLazyInstantiateCallback&&) = default;
 *     PromiseLazyInstantiateCallback(const PromiseLazyInstantiateCallback&) {
 *         // Because we get wrapped in std::function we must be copyable. But we should never
 *         // be copied.
 *         SkASSERT(false);
 *     }
 *     PromiseLazyInstantiateCallback& operator=(PromiseLazyInstantiateCallback&&) = default;
 *     PromiseLazyInstantiateCallback& operator=(const PromiseLazyInstantiateCallback&) {
 *         SkASSERT(false);
 *         return *this;
 *     }
 *
 *     sk_sp<Texture> operator()(ResourceProvider* resourceProvider) {
 *         // Invoke the fulfill proc to get the promised backend texture.
 *         auto [ backendTexture, textureReleaseCtx ] = fFulfillProc(fFulfillContext);
 *         if (!backendTexture.isValid()) {
 *             SKGPU_LOG_W("FulfillProc returned an invalid backend texture");
 *             return nullptr;
 *         }
 *
 *         sk_sp<RefCntedCallback> textureReleaseCB = RefCntedCallback::Make(fTextureReleaseProc,
 *                                                                           textureReleaseCtx);
 *
 *         sk_sp<Texture> texture = resourceProvider->createWrappedTexture(backendTexture,
 *                                                                         std::move(fLabel));
 *         if (!texture) {
 *             SKGPU_LOG_W("Failed to wrap BackendTexture returned by fulfill proc");
 *             return nullptr;
 *         }
 *         texture->setReleaseCallback(std::move(textureReleaseCB));
 *         return texture;
 *     }
 *
 * private:
 *     sk_sp<RefCntedCallback> fReleaseHelper;
 *     GraphitePromiseTextureFulfillProc fFulfillProc;
 *     GraphitePromiseTextureFulfillContext fFulfillContext;
 *     GraphitePromiseTextureReleaseProc fTextureReleaseProc;
 *     std::string fLabel;
 * }
 * ```
 */
public data class PromiseLazyInstantiateCallback public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<RefCntedCallback> fReleaseHelper
   * ```
   */
  private var fReleaseHelper: SkSp<RefCntedCallback>,
  /**
   * C++ original:
   * ```cpp
   * GraphitePromiseTextureFulfillProc fFulfillProc
   * ```
   */
  private var fFulfillProc: GraphitePromiseTextureReleaseProc,
  /**
   * C++ original:
   * ```cpp
   * GraphitePromiseTextureFulfillContext fFulfillContext
   * ```
   */
  private var fFulfillContext: GraphitePromiseTextureFulfillContext,
  /**
   * C++ original:
   * ```cpp
   * GraphitePromiseTextureReleaseProc fTextureReleaseProc
   * ```
   */
  private var fTextureReleaseProc: GraphitePromiseTextureReleaseProc,
  /**
   * C++ original:
   * ```cpp
   * std::string fLabel
   * ```
   */
  private var fLabel: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * PromiseLazyInstantiateCallback& operator=(PromiseLazyInstantiateCallback&&) = default
   * ```
   */
  public fun assign(param0: PromiseLazyInstantiateCallback) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * PromiseLazyInstantiateCallback& operator=(const PromiseLazyInstantiateCallback&) {
   *         SkASSERT(false);
   *         return *this;
   *     }
   * ```
   */
  public operator fun invoke(resourceProvider: ResourceProvider?): SkSp<Texture> {
    TODO("Implement invoke")
  }
}
