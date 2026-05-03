package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.ContextImpl
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap

/**
 * C++ original:
 * ```cpp
 * class ContextPriv {
 * public:
 *     const Caps* caps() const { return fContext->fSharedContext->caps(); }
 *
 *     const ShaderCodeDictionary* shaderCodeDictionary() const {
 *         return fContext->fSharedContext->shaderCodeDictionary();
 *     }
 *     ShaderCodeDictionary* shaderCodeDictionary() {
 *         return fContext->fSharedContext->shaderCodeDictionary();
 *     }
 * #if defined(GPU_TEST_UTILS)
 *     const GlobalCache* globalCache() const {
 *         return fContext->fSharedContext->globalCache();
 *     }
 *     GlobalCache* globalCache() {
 *         return fContext->fSharedContext->globalCache();
 *     }
 * #endif
 *     const RendererProvider* rendererProvider() const {
 *         return fContext->fSharedContext->rendererProvider();
 *     }
 *     ResourceProvider* resourceProvider() const {
 *         return fContext->fResourceProvider.get();
 *     }
 *     SharedContext* sharedContext() {
 *         return fContext->fSharedContext.get();
 *     }
 *     const skcpu::ContextImpl* cpuContext() const { return fContext->fCPUContext.get(); }
 *
 * #if defined(GPU_TEST_UTILS)
 *     void startCapture() {
 *         fContext->fQueueManager->startCapture();
 *     }
 *     void stopCapture() {
 *         fContext->fQueueManager->stopCapture();
 *     }
 *
 *     void deregisterRecorder(const Recorder* recorder) {
 *         fContext->deregisterRecorder(recorder);
 *     }
 *
 *     bool readPixels(const SkPixmap&,
 *                     const TextureProxy*,
 *                     const SkImageInfo& srcImageInfo,
 *                     int srcX, int srcY);
 * #endif
 *
 * private:
 *     friend class Context; // to construct/copy this type.
 *
 *     explicit ContextPriv(Context* context) : fContext(context) {}
 *
 *     ContextPriv& operator=(const ContextPriv&) = delete;
 *
 *     // No taking addresses of this type.
 *     const ContextPriv* operator&() const;
 *     ContextPriv *operator&();
 *
 *     Context* fContext;
 * }
 * ```
 */
public data class ContextPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * Context* fContext
   * ```
   */
  private var fContext: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * const Caps* caps() const { return fContext->fSharedContext->caps(); }
   * ```
   */
  public fun caps(): Caps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* shaderCodeDictionary() const {
   *         return fContext->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun shaderCodeDictionary(): ShaderCodeDictionary {
    TODO("Implement shaderCodeDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* shaderCodeDictionary() {
   *         return fContext->fSharedContext->shaderCodeDictionary();
   *     }
   * ```
   */
  public fun globalCache(): GlobalCache {
    TODO("Implement globalCache")
  }

  /**
   * C++ original:
   * ```cpp
   * const GlobalCache* globalCache() const {
   *         return fContext->fSharedContext->globalCache();
   *     }
   * ```
   */
  public fun rendererProvider(): RendererProvider {
    TODO("Implement rendererProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * GlobalCache* globalCache() {
   *         return fContext->fSharedContext->globalCache();
   *     }
   * ```
   */
  public fun resourceProvider(): ResourceProvider {
    TODO("Implement resourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * const RendererProvider* rendererProvider() const {
   *         return fContext->fSharedContext->rendererProvider();
   *     }
   * ```
   */
  public fun sharedContext(): Int {
    TODO("Implement sharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* resourceProvider() const {
   *         return fContext->fResourceProvider.get();
   *     }
   * ```
   */
  public fun cpuContext(): ContextImpl {
    TODO("Implement cpuContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SharedContext* sharedContext() {
   *         return fContext->fSharedContext.get();
   *     }
   * ```
   */
  public fun startCapture() {
    TODO("Implement startCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * const skcpu::ContextImpl* cpuContext() const { return fContext->fCPUContext.get(); }
   * ```
   */
  public fun stopCapture() {
    TODO("Implement stopCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * void startCapture() {
   *         fContext->fQueueManager->startCapture();
   *     }
   * ```
   */
  public fun deregisterRecorder(recorder: Recorder?) {
    TODO("Implement deregisterRecorder")
  }

  /**
   * C++ original:
   * ```cpp
   * void stopCapture() {
   *         fContext->fQueueManager->stopCapture();
   *     }
   * ```
   */
  public fun readPixels(
    pm: SkPixmap,
    textureProxy: TextureProxy?,
    srcImageInfo: SkImageInfo,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * void deregisterRecorder(const Recorder* recorder) {
   *         fContext->deregisterRecorder(recorder);
   *     }
   * ```
   */
  private fun assign(param0: ContextPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ContextPriv::readPixels(const SkPixmap& pm,
   *                              const TextureProxy* textureProxy,
   *                              const SkImageInfo& srcImageInfo,
   *                              int srcX, int srcY) {
   *     auto rect = SkIRect::MakeXYWH(srcX, srcY, pm.width(), pm.height());
   *     struct AsyncContext {
   *         bool fCalled = false;
   *         std::unique_ptr<const SkImage::AsyncReadResult> fResult;
   *     } asyncContext;
   *
   *     auto asyncCallback = [](void* c, std::unique_ptr<const SkImage::AsyncReadResult> out) {
   *         auto context = static_cast<AsyncContext*>(c);
   *         context->fResult = std::move(out);
   *         context->fCalled = true;
   *     };
   *
   *     const SkColorInfo& srcColorInfo = srcImageInfo.colorInfo();
   *
   *     // This is roughly equivalent to the logic taken in asyncRescaleAndRead(SkSurface) to either
   *     // try the image-based readback (with copy-as-draw fallbacks) or read the texture directly
   *     // if it supports reading.
   *     if (!fContext->fSharedContext->caps()->supportsReadPixels(textureProxy->textureInfo())) {
   *         // Since this is a synchronous testing-only API, callers should have flushed any pending
   *         // work that modifies this texture proxy already. This means we don't have to worry about
   *         // re-wrapping the proxy in a new Image (that wouldn't tbe connected to any Device, etc.).
   *         sk_sp<SkImage> image{new Image(TextureProxyView(sk_ref_sp(textureProxy)), srcColorInfo)};
   *         Context::AsyncParams<SkImage> params {image.get(), rect, pm.info(),
   *                                               asyncCallback, &asyncContext};
   *         if (!params.validate()) {
   *             params.fail();
   *         } else {
   *             fContext->asyncReadPixels(/*recorder=*/nullptr, params);
   *         }
   *     } else {
   *         fContext->asyncReadTexture(/*recorder=*/nullptr,
   *                                    {textureProxy, rect, pm.info(), asyncCallback, &asyncContext},
   *                                    srcImageInfo.colorInfo());
   *     }
   *
   *     if (fContext->fSharedContext->caps()->allowCpuSync()) {
   *         fContext->submit(SyncToCpu::kYes);
   *     } else {
   *         fContext->submit(SyncToCpu::kNo);
   *         if (fContext->fSharedContext->backend() == BackendApi::kDawn) {
   *             while (!asyncContext.fCalled) {
   *                 fContext->fSharedContext->deviceTick(fContext);
   *             }
   *         } else {
   *             SK_ABORT("Only Dawn supports non-syncing contexts.");
   *         }
   *     }
   *     SkASSERT(asyncContext.fCalled);
   *     if (!asyncContext.fResult) {
   *         return false;
   *     }
   *     SkRectMemcpy(pm.writable_addr(), pm.rowBytes(), asyncContext.fResult->data(0),
   *                  asyncContext.fResult->rowBytes(0), pm.info().minRowBytes(),
   *                  pm.height());
   *     return true;
   * }
   * ```
   */
  private fun addressOf(): ContextPriv {
    TODO("Implement addressOf")
  }
}
