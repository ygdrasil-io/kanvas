package org.skia.gpu

import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SingleOwner
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class MtlResourceProvider final : public ResourceProvider {
 * public:
 *     MtlResourceProvider(SharedContext* sharedContext,
 *                         SingleOwner*,
 *                         uint32_t recorderID,
 *                         size_t resourceBudget);
 *     ~MtlResourceProvider() override {}
 *
 *     sk_sp<MtlGraphicsPipeline> findOrCreateLoadMSAAPipeline(const RenderPassDesc&);
 *
 * private:
 *     const MtlSharedContext* mtlSharedContext();
 *
 *     sk_sp<ComputePipeline> createComputePipeline(const ComputePipelineDesc&) override;
 *
 *     sk_sp<Texture> createTexture(SkISize, const TextureInfo&) override;
 *     sk_sp<Texture> onCreateWrappedTexture(const BackendTexture&) override;
 *     sk_sp<Buffer> createBuffer(size_t size, BufferType type, AccessPattern) override;
 *     sk_sp<Sampler> createSampler(const SamplerDesc&) override;
 *
 *     BackendTexture onCreateBackendTexture(SkISize dimensions, const TextureInfo&) override;
 *     void onDeleteBackendTexture(const BackendTexture&) override;
 *
 *     skia_private::THashMap<uint32_t, sk_sp<MtlGraphicsPipeline>> fLoadMSAAPipelines;
 * }
 * ```
 */
public class MtlResourceProvider public constructor(
  sharedContext: SharedContext?,
  param1: SingleOwner,
  recorderID: UInt,
  resourceBudget: ULong,
) : ResourceProvider() {
  /**
   * C++ original:
   * ```cpp
   * MtlResourceProvider(SharedContext* sharedContext,
   *                         SingleOwner*,
   *                         uint32_t recorderID,
   *                         size_t resourceBudget)
   * ```
   */
  public constructor(
    sharedContext: SharedContext?,
    singleOwner: SingleOwner?,
    recorderID: UInt,
    resourceBudget: ULong,
  ) : this(TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<MtlGraphicsPipeline> MtlResourceProvider::findOrCreateLoadMSAAPipeline(
   *         const RenderPassDesc& renderPassDesc) {
   *     uint32_t renderPassKey =
   *             this->mtlSharedContext()->mtlCaps().getRenderPassDescKey(renderPassDesc);
   *     sk_sp<MtlGraphicsPipeline> pipeline = fLoadMSAAPipelines[renderPassKey];
   *     if (!pipeline) {
   *         pipeline  = MtlGraphicsPipeline::MakeLoadMSAAPipeline(this->mtlSharedContext(),
   *                                                               renderPassDesc);
   *         if (pipeline) {
   *             fLoadMSAAPipelines.set(renderPassKey, pipeline);
   *         }
   *     }
   *
   *     return pipeline;
   * }
   * ```
   */
  public fun findOrCreateLoadMSAAPipeline(renderPassDesc: RenderPassDesc): Int {
    TODO("Implement findOrCreateLoadMSAAPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * const MtlSharedContext* MtlResourceProvider::mtlSharedContext() {
   *     return static_cast<const MtlSharedContext*>(fSharedContext);
   * }
   * ```
   */
  private fun mtlSharedContext(): MtlSharedContext {
    TODO("Implement mtlSharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ComputePipeline> MtlResourceProvider::createComputePipeline(
   *         const ComputePipelineDesc& pipelineDesc) {
   *     return MtlComputePipeline::Make(this->mtlSharedContext(), pipelineDesc);
   * }
   * ```
   */
  public override fun createComputePipeline(pipelineDesc: ComputePipelineDesc): Int {
    TODO("Implement createComputePipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Texture> MtlResourceProvider::createTexture(SkISize dimensions,
   *                                                   const TextureInfo& info) {
   *     return MtlTexture::Make(this->mtlSharedContext(), dimensions, info);
   * }
   * ```
   */
  public override fun createTexture(dimensions: SkISize, info: TextureInfo): Int {
    TODO("Implement createTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Texture> MtlResourceProvider::onCreateWrappedTexture(const BackendTexture& texture) {
   *     CFTypeRef mtlHandleTexture = BackendTextures::GetMtlTexture(texture);
   *     if (!mtlHandleTexture) {
   *         return nullptr;
   *     }
   *     sk_cfp<id<MTLTexture>> mtlTexture = sk_ret_cfp((id<MTLTexture>)mtlHandleTexture);
   *     return MtlTexture::MakeWrapped(this->mtlSharedContext(), texture.dimensions(), texture.info(),
   *                                    std::move(mtlTexture));
   * }
   * ```
   */
  public override fun onCreateWrappedTexture(texture: BackendTexture): Int {
    TODO("Implement onCreateWrappedTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Buffer> MtlResourceProvider::createBuffer(size_t size,
   *                                                 BufferType type,
   *                                                 AccessPattern accessPattern) {
   *     return MtlBuffer::Make(this->mtlSharedContext(), size, type, accessPattern);
   * }
   * ```
   */
  public override fun createBuffer(
    size: ULong,
    type: BufferType,
    accessPattern: AccessPattern,
  ): Int {
    TODO("Implement createBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Sampler> MtlResourceProvider::createSampler(const SamplerDesc& samplerDesc) {
   *     return MtlSampler::Make(this->mtlSharedContext(),
   *                             samplerDesc.samplingOptions(),
   *                             samplerDesc.tileModeX(),
   *                             samplerDesc.tileModeY());
   * }
   * ```
   */
  public override fun createSampler(samplerDesc: SamplerDesc): Int {
    TODO("Implement createSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendTexture MtlResourceProvider::onCreateBackendTexture(SkISize dimensions,
   *                                                            const TextureInfo& info) {
   *     sk_cfp<id<MTLTexture>> texture = MtlTexture::MakeMtlTexture(this->mtlSharedContext(),
   *                                                                 dimensions,
   *                                                                 info);
   *     if (!texture) {
   *         return {};
   *     }
   *     return BackendTextures::MakeMetal(dimensions, (CFTypeRef)texture.release());
   * }
   * ```
   */
  public override fun onCreateBackendTexture(dimensions: SkISize, info: TextureInfo): Int {
    TODO("Implement onCreateBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlResourceProvider::onDeleteBackendTexture(const BackendTexture& texture) {
   *     SkASSERT(texture.backend() == BackendApi::kMetal);
   *     CFTypeRef texHandle = BackendTextures::GetMtlTexture(texture);
   *     SkCFSafeRelease(texHandle);
   * }
   * ```
   */
  public override fun onDeleteBackendTexture(texture: BackendTexture) {
    TODO("Implement onDeleteBackendTexture")
  }
}
