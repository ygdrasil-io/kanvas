package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import LazyInstantiateCallback as LazyInstantiateCallback_
import undefined.LazyInstantiateCallback as UndefinedLazyInstantiateCallback

/**
 * C++ original:
 * ```cpp
 * class TextureProxy : public SkRefCnt {
 * public:
 *     TextureProxy() = delete;
 *
 *     ~TextureProxy() override;
 *
 *     SampleCount sampleCount() const { return fInfo.sampleCount(); }
 *     Mipmapped mipmapped() const { return fInfo.mipmapped(); }
 *
 *     SkISize dimensions() const;
 *     const TextureInfo& textureInfo() const { return fInfo; }
 *
 *     const char* label() const { return fLabel.c_str(); }
 *
 *     bool isLazy() const;
 *     bool isFullyLazy() const;
 *     bool isVolatile() const;
 *
 *     Protected isProtected() const { return fInfo.isProtected(); }
 *
 *     size_t uninstantiatedGpuMemorySize() const;
 *
 *     bool instantiate(ResourceProvider*);
 *     /*
 *      * We currently only instantiate lazy proxies at insertion-time. Snap-time 'instantiate'
 *      * calls should be wrapped in 'InstantiateIfNotLazy'.
 *      *
 *      * Unlike Ganesh, in Graphite we do not update the proxy's dimensions with the instantiating
 *      * texture's dimensions. This means that when a fully-lazy proxy is instantiated and
 *      * deinstantiated, it goes back to being fully-lazy and without dimensions, and can be
 *      * re-instantiated with a new texture with different dimensions than the first.
 *      */
 *     bool lazyInstantiate(ResourceProvider*);
 *     /*
 *      * For Lazy proxies this will return true. Otherwise, it will return the result of
 *      * calling instantiate on the texture proxy.
 *      *
 *      * DEPRECATED: Eventually all un-instantiated non-lazy proxies should use the
 *      *             ScratchResourceManager function instead of the ResourceProvider directly.
 *      */
 *     static bool InstantiateIfNotLazy(ResourceProvider*, TextureProxy*);
 *
 *     /*
 *      * Instantiate any scratch proxy (not already instantiated and not lazy) by using a texture
 *      * from the ScratchResourceManager. When possible, this will be a texture that has been returned
 *      * for reuse by a prior task. Lazy proxies and already instantiated proxies will return true.
 *      *
 *      * False is returned if instantiation fails.
 *      */
 *     static bool InstantiateIfNotLazy(ScratchResourceManager*, TextureProxy*);
 *
 *     bool isInstantiated() const { return SkToBool(fTexture); }
 *     void deinstantiate();
 *     sk_sp<Texture> refTexture() const;
 *     const Texture* texture() const;
 *     Texture* texture() { return fTexture.get(); }
 *
 *     // Make() will immediately instantiate non-budgeted proxies.
 *     static sk_sp<TextureProxy> Make(const Caps*,
 *                                     ResourceProvider*,
 *                                     SkISize dimensions,
 *                                     const TextureInfo&,
 *                                     std::string_view label,
 *                                     skgpu::Budgeted);
 *
 *     using LazyInstantiateCallback = std::function<sk_sp<Texture> (ResourceProvider*)>;
 *
 *     static sk_sp<TextureProxy> MakeLazy(const Caps*,
 *                                         SkISize dimensions,
 *                                         const TextureInfo&,
 *                                         skgpu::Budgeted,
 *                                         Volatile,
 *                                         LazyInstantiateCallback&&);
 *     static sk_sp<TextureProxy> MakeFullyLazy(const TextureInfo&,
 *                                              skgpu::Budgeted,
 *                                              Volatile,
 *                                              LazyInstantiateCallback&&);
 *
 *     static sk_sp<TextureProxy> Wrap(sk_sp<Texture>);
 *
 * private:
 *     TextureProxy(SkISize dimensions,
 *                  const TextureInfo& info,
 *                  std::string_view label,
 *                  skgpu::Budgeted budgeted);
 *     TextureProxy(SkISize dimensions,
 *                  const TextureInfo&,
 *                  skgpu::Budgeted,
 *                  Volatile,
 *                  LazyInstantiateCallback&&);
 *     TextureProxy(sk_sp<Texture>);
 *
 * #ifdef SK_DEBUG
 *     void validateTexture(const Texture*);
 * #endif
 *
 *     // In the following, 'fVolatile' and 'fLazyInstantiateCallback' can be accessed from
 *     // multiple threads so need to remain immutable.
 *     SkISize fDimensions;
 *     const TextureInfo fInfo;
 *
 *     // String used to describe the current use of this TextureProxy. It will be set on its
 *     // Texture object when the proxy gets instantiated.
 *     std::string fLabel;
 *
 *     skgpu::Budgeted fBudgeted;
 *     const Volatile fVolatile;
 *
 *     sk_sp<Texture> fTexture;
 *
 *     const LazyInstantiateCallback fLazyInstantiateCallback;
 * }
 * ```
 */
public open class TextureProxy public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * TextureProxy(sk_sp<Texture>)
   * ```
   */
  private var skSp: TextureProxy = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * SkISize fDimensions
   * ```
   */
  private var fDimensions: Int = TODO("Initialize fDimensions")

  /**
   * C++ original:
   * ```cpp
   * const TextureInfo fInfo
   * ```
   */
  private val fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * std::string fLabel
   * ```
   */
  private var fLabel: Int = TODO("Initialize fLabel")

  /**
   * C++ original:
   * ```cpp
   * skgpu::Budgeted fBudgeted
   * ```
   */
  private var fBudgeted: Int = TODO("Initialize fBudgeted")

  /**
   * C++ original:
   * ```cpp
   * const Volatile fVolatile
   * ```
   */
  private val fVolatile: Int = TODO("Initialize fVolatile")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Texture> fTexture
   * ```
   */
  private var fTexture: Int = TODO("Initialize fTexture")

  /**
   * C++ original:
   * ```cpp
   * const LazyInstantiateCallback fLazyInstantiateCallback
   * ```
   */
  private val fLazyInstantiateCallback: Int = TODO("Initialize fLazyInstantiateCallback")

  /**
   * C++ original:
   * ```cpp
   * TextureProxy() = delete
   * ```
   */
  public constructor(
    dimensions: SkISize,
    info: TextureInfo,
    label: String,
    budgeted: Budgeted,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy(SkISize dimensions,
   *                  const TextureInfo& info,
   *                  std::string_view label,
   *                  skgpu::Budgeted budgeted)
   * ```
   */
  public constructor(
    dimensions: SkISize,
    textureInfo: TextureInfo,
    budgeted: Budgeted,
    isVolatile: Volatile,
    callback: UndefinedLazyInstantiateCallback,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureProxy(SkISize dimensions,
   *                  const TextureInfo&,
   *                  skgpu::Budgeted,
   *                  Volatile,
   *                  LazyInstantiateCallback&&)
   * ```
   */
  public constructor(texture: SkSp<Texture>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SampleCount sampleCount() const { return fInfo.sampleCount(); }
   * ```
   */
  public fun sampleCount(): Int {
    TODO("Implement sampleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * Mipmapped mipmapped() const { return fInfo.mipmapped(); }
   * ```
   */
  public fun mipmapped(): Int {
    TODO("Implement mipmapped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize TextureProxy::dimensions() const {
   *     SkASSERT(!this->isFullyLazy() || this->isInstantiated());
   *     return this->isInstantiated() ? fTexture->dimensions() : fDimensions;
   * }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * const TextureInfo& textureInfo() const { return fInfo; }
   * ```
   */
  public fun textureInfo(): Int {
    TODO("Implement textureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* label() const { return fLabel.c_str(); }
   * ```
   */
  public fun label(): Char {
    TODO("Implement label")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextureProxy::isLazy() const {
   *     return SkToBool(fLazyInstantiateCallback);
   * }
   * ```
   */
  public fun isLazy(): Boolean {
    TODO("Implement isLazy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextureProxy::isFullyLazy() const {
   *     bool result = fDimensions.width() < 0;
   *     SkASSERT(result == (fDimensions.height() < 0));
   *     SkASSERT(!result || this->isLazy());
   *     return result;
   * }
   * ```
   */
  public fun isFullyLazy(): Boolean {
    TODO("Implement isFullyLazy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextureProxy::isVolatile() const {
   *     SkASSERT(fVolatile == Volatile::kNo || SkToBool(fLazyInstantiateCallback));
   *
   *     return fVolatile == Volatile::kYes;
   * }
   * ```
   */
  public fun isVolatile(): Boolean {
    TODO("Implement isVolatile")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected isProtected() const { return fInfo.isProtected(); }
   * ```
   */
  public fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t TextureProxy::uninstantiatedGpuMemorySize() const {
   *     return ComputeSize(fDimensions, fInfo);
   * }
   * ```
   */
  public fun uninstantiatedGpuMemorySize(): Int {
    TODO("Implement uninstantiatedGpuMemorySize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextureProxy::instantiate(ResourceProvider* resourceProvider) {
   *     SkASSERT(!this->isLazy());
   *
   *     if (fTexture) {
   *         return true;
   *     }
   *
   *     // TODO(389908374): Once all tasks use the ScratchResourceManager, this can be updated to just
   *     // finding and creating a non-shareable AND non-budgeted texture.
   *     fTexture = resourceProvider->findOrCreateNonShareableTexture(
   *             fDimensions, fInfo, fLabel, fBudgeted);
   *     if (!fTexture) {
   *         return false;
   *     }
   *     SkDEBUGCODE(this->validateTexture(fTexture.get()));
   *     return true;
   * }
   * ```
   */
  public fun instantiate(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement instantiate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TextureProxy::lazyInstantiate(ResourceProvider* resourceProvider) {
   *     SkASSERT(this->isLazy());
   *
   *     if (fTexture) {
   *         return true;
   *     }
   *
   *     fTexture = fLazyInstantiateCallback(resourceProvider);
   *     if (!fTexture) {
   *         return false;
   *     }
   *     SkDEBUGCODE(this->validateTexture(fTexture.get()));
   *     return true;
   * }
   * ```
   */
  public fun lazyInstantiate(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement lazyInstantiate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isInstantiated() const { return SkToBool(fTexture); }
   * ```
   */
  public fun isInstantiated(): Boolean {
    TODO("Implement isInstantiated")
  }

  /**
   * C++ original:
   * ```cpp
   * void TextureProxy::deinstantiate() {
   *     SkASSERT(fVolatile == Volatile::kYes && SkToBool(fLazyInstantiateCallback));
   *
   *     fTexture.reset();
   * }
   * ```
   */
  public fun deinstantiate() {
    TODO("Implement deinstantiate")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Texture> TextureProxy::refTexture() const {
   *     return fTexture;
   * }
   * ```
   */
  public fun refTexture(): Int {
    TODO("Implement refTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * const Texture* TextureProxy::texture() const {
   *     return fTexture.get();
   * }
   * ```
   */
  public fun texture(): Texture {
    TODO("Implement texture")
  }

  /**
   * C++ original:
   * ```cpp
   * Texture* texture() { return fTexture.get(); }
   * ```
   */
  public fun validateTexture(texture: Texture?) {
    TODO("Implement validateTexture")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool TextureProxy::InstantiateIfNotLazy(ResourceProvider* resourceProvider,
     *                                         TextureProxy* textureProxy) {
     *     if (textureProxy->isLazy()) {
     *         return true;
     *     }
     *
     *     return textureProxy->instantiate(resourceProvider);
     * }
     * ```
     */
    public fun instantiateIfNotLazy(resourceProvider: ResourceProvider?, textureProxy: TextureProxy?): Boolean {
      TODO("Implement instantiateIfNotLazy")
    }

    /**
     * C++ original:
     * ```cpp
     * bool TextureProxy::InstantiateIfNotLazy(ScratchResourceManager* scratchManager,
     *                                         TextureProxy* textureProxy) {
     *     if (textureProxy->isLazy() || textureProxy->isInstantiated()) {
     *         return true;
     *     }
     *
     *     textureProxy->fTexture = scratchManager->getScratchTexture(textureProxy->dimensions(),
     *                                                                textureProxy->textureInfo(),
     *                                                                textureProxy->fLabel);
     *     if (!textureProxy->fTexture) {
     *         return false;
     *     }
     *     SkDEBUGCODE(textureProxy->validateTexture(textureProxy->fTexture.get()));
     *     return true;
     * }
     * ```
     */
    public fun instantiateIfNotLazy(scratchManager: ScratchResourceManager?, textureProxy: TextureProxy?): Boolean {
      TODO("Implement instantiateIfNotLazy")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextureProxy> TextureProxy::Make(const Caps* caps,
     *                                        ResourceProvider* resourceProvider,
     *                                        SkISize dimensions,
     *                                        const TextureInfo& textureInfo,
     *                                        std::string_view label,
     *                                        skgpu::Budgeted budgeted) {
     *     if (dimensions.width() < 1 || dimensions.height() < 1 ||
     *         dimensions.width() > caps->maxTextureSize() ||
     *         dimensions.height() > caps->maxTextureSize() ||
     *         !textureInfo.isValid()) {
     *         return nullptr;
     *     }
     *
     *     sk_sp<TextureProxy> proxy{new TextureProxy(dimensions,
     *                                                textureInfo,
     *                                                std::move(label),
     *                                                budgeted)};
     *     if (budgeted == Budgeted::kNo) {
     *         // Instantiate immediately to avoid races later on if the client starts to use the wrapping
     *         // object on multiple threads.
     *         if (!proxy->instantiate(resourceProvider)) {
     *             return nullptr;
     *         }
     *     }
     *     return proxy;
     * }
     * ```
     */
    public fun make(
      caps: Caps?,
      resourceProvider: ResourceProvider?,
      dimensions: SkISize,
      textureInfo: TextureInfo,
      label: String,
      budgeted: Budgeted,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextureProxy> TextureProxy::MakeLazy(const Caps* caps,
     *                                            SkISize dimensions,
     *                                            const TextureInfo& textureInfo,
     *                                            skgpu::Budgeted budgeted,
     *                                            Volatile isVolatile,
     *                                            LazyInstantiateCallback&& callback) {
     *     SkASSERT(textureInfo.isValid());
     *     if (dimensions.width() < 1 || dimensions.height() < 1 ||
     *         dimensions.width() > caps->maxTextureSize() ||
     *         dimensions.height() > caps->maxTextureSize()) {
     *         return nullptr;
     *     }
     *
     *     return sk_sp<TextureProxy>(new TextureProxy(dimensions,
     *                                                 textureInfo,
     *                                                 budgeted,
     *                                                 isVolatile,
     *                                                 std::move(callback)));
     * }
     * ```
     */
    public fun makeLazy(
      caps: Caps?,
      dimensions: SkISize,
      textureInfo: TextureInfo,
      budgeted: Budgeted,
      isVolatile: Volatile,
      callback: LazyInstantiateCallback_,
    ): Int {
      TODO("Implement makeLazy")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextureProxy> TextureProxy::MakeFullyLazy(const TextureInfo& textureInfo,
     *                                                 skgpu::Budgeted budgeted,
     *                                                 Volatile isVolatile,
     *                                                 LazyInstantiateCallback&& callback) {
     *     SkASSERT(textureInfo.isValid());
     *
     *     return sk_sp<TextureProxy>(new TextureProxy(SkISize::Make(-1, -1),
     *                                                 textureInfo,
     *                                                 budgeted,
     *                                                 isVolatile,
     *                                                 std::move(callback)));
     * }
     * ```
     */
    public fun makeFullyLazy(
      textureInfo: TextureInfo,
      budgeted: Budgeted,
      isVolatile: Volatile,
      callback: LazyInstantiateCallback_,
    ): Int {
      TODO("Implement makeFullyLazy")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<TextureProxy> TextureProxy::Wrap(sk_sp<Texture> texture) {
     *     return sk_sp<TextureProxy>(new TextureProxy(std::move(texture)));
     * }
     * ```
     */
    public fun wrap(texture: SkSp<Texture>): Int {
      TODO("Implement wrap")
    }
  }
}
