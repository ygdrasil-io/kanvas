package org.skia.gpu

import kotlin.Int
import kotlin.String
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class MtlTexture : public Texture {
 * public:
 *     static sk_cfp<id<MTLTexture>> MakeMtlTexture(const MtlSharedContext*,
 *                                                  SkISize dimensions,
 *                                                  const TextureInfo&);
 *
 *     static sk_sp<Texture> Make(const MtlSharedContext*,
 *                                SkISize dimensions,
 *                                const TextureInfo&);
 *
 *     static sk_sp<Texture> MakeWrapped(const MtlSharedContext*,
 *                                       SkISize dimensions,
 *                                       const TextureInfo&,
 *                                       sk_cfp<id<MTLTexture>>);
 *
 *     ~MtlTexture() override {}
 *
 *     const MtlTextureInfo& mtlTextureInfo() const {
 *         return TextureInfoPriv::Get<MtlTextureInfo>(this->textureInfo());
 *     }
 *     id<MTLTexture> mtlTexture() const { return fTexture.get(); }
 *
 * private:
 *     MtlTexture(const MtlSharedContext* sharedContext,
 *                SkISize dimensions,
 *                const TextureInfo& info,
 *                sk_cfp<id<MTLTexture>>,
 *                Ownership);
 *
 *     void freeGpuData() override;
 *
 *     void setBackendLabel(char const* label) override;
 *
 *     sk_cfp<id<MTLTexture>> fTexture;
 * }
 * ```
 */
public open class MtlTexture public constructor(
  sharedContext: MtlSharedContext?,
  dimensions: SkISize,
  info: TextureInfo,
  ownership: Ownership,
) : Texture(TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLTexture>> fTexture
   * ```
   */
  private var fTexture: Int = TODO("Initialize fTexture")

  /**
   * C++ original:
   * ```cpp
   * const MtlTextureInfo& mtlTextureInfo() const {
   *         return TextureInfoPriv::Get<MtlTextureInfo>(this->textureInfo());
   *     }
   * ```
   */
  public fun mtlTextureInfo(): MtlTexture {
    TODO("Implement mtlTextureInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * id<MTLTexture> mtlTexture() const { return fTexture.get(); }
   * ```
   */
  public fun mtlTexture(): Int {
    TODO("Implement mtlTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlTexture::freeGpuData() {
   *     fTexture.reset();
   * }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlTexture::setBackendLabel(char const* label) {
   *     SkASSERT(label);
   * #ifdef SK_ENABLE_MTL_DEBUG_INFO
   *     NSString* labelStr = @(label);
   *     this->mtlTexture().label = labelStr;
   * #endif
   * }
   * ```
   */
  public override fun setBackendLabel(label: String?) {
    TODO("Implement setBackendLabel")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_cfp<id<MTLTexture>> MtlTexture::MakeMtlTexture(const MtlSharedContext* sharedContext,
     *                                                   SkISize dimensions,
     *                                                   const TextureInfo& info) {
     *     const Caps* caps = sharedContext->caps();
     *     if (dimensions.width() > caps->maxTextureSize() ||
     *         dimensions.height() > caps->maxTextureSize()) {
     *         return nullptr;
     *     }
     *
     *     const auto& mtlInfo = TextureInfoPriv::Get<MtlTextureInfo>(info);
     *     SkASSERT(!mtlInfo.fFramebufferOnly);
     *
     *     if (mtlInfo.fUsage & MTLTextureUsageShaderRead && !caps->isTexturable(info)) {
     *         return nullptr;
     *     }
     *
     *     if (mtlInfo.fUsage & MTLTextureUsageRenderTarget && !caps->isRenderable(info)) {
     *         return nullptr;
     *     }
     *
     *     if (mtlInfo.fUsage & MTLTextureUsageShaderWrite && !caps->isStorage(info)) {
     *         return nullptr;
     *     }
     *
     *     int numMipLevels = 1;
     *     if (info.mipmapped() == Mipmapped::kYes) {
     *         numMipLevels = SkMipmap::ComputeLevelCount(dimensions) + 1;
     *     }
     *
     *     sk_cfp<MTLTextureDescriptor*> desc([[MTLTextureDescriptor alloc] init]);
     *     (*desc).textureType = (info.sampleCount() > SampleCount::k1) ? MTLTextureType2DMultisample
     *                                                                  : MTLTextureType2D;
     *     (*desc).pixelFormat = mtlInfo.fFormat;
     *     (*desc).width = dimensions.width();
     *     (*desc).height = dimensions.height();
     *     (*desc).depth = 1;
     *     (*desc).mipmapLevelCount = numMipLevels;
     *     (*desc).sampleCount = (uint8_t) info.sampleCount();
     *     (*desc).arrayLength = 1;
     *     (*desc).usage = mtlInfo.fUsage;
     *     (*desc).storageMode = mtlInfo.fStorageMode;
     *
     *     sk_cfp<id<MTLTexture>> texture([sharedContext->device() newTextureWithDescriptor:desc.get()]);
     *     return texture;
     * }
     * ```
     */
    public fun makeMtlTexture(
      sharedContext: MtlSharedContext?,
      dimensions: SkISize,
      info: TextureInfo,
    ): Int {
      TODO("Implement makeMtlTexture")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Texture> MtlTexture::Make(const MtlSharedContext* sharedContext,
     *                                 SkISize dimensions,
     *                                 const TextureInfo& info) {
     *     sk_cfp<id<MTLTexture>> texture = MakeMtlTexture(sharedContext, dimensions, info);
     *     if (!texture) {
     *         return nullptr;
     *     }
     *     return sk_sp<Texture>(new MtlTexture(sharedContext,
     *                                          dimensions,
     *                                          info,
     *                                          std::move(texture),
     *                                          Ownership::kOwned));
     * }
     * ```
     */
    public fun make(
      sharedContext: MtlSharedContext?,
      dimensions: SkISize,
      info: TextureInfo,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<Texture> MtlTexture::MakeWrapped(const MtlSharedContext* sharedContext,
     *                                        SkISize dimensions,
     *                                        const TextureInfo& info,
     *                                        sk_cfp<id<MTLTexture>> texture) {
     *     return sk_sp<Texture>(new MtlTexture(sharedContext,
     *                                          dimensions,
     *                                          info,
     *                                          std::move(texture),
     *                                          Ownership::kWrapped));
     * }
     * ```
     */
    public fun makeWrapped(
      sharedContext: MtlSharedContext?,
      dimensions: SkISize,
      info: TextureInfo,
    ): Int {
      TODO("Implement makeWrapped")
    }
  }
}
