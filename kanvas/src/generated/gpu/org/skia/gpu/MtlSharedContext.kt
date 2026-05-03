package org.skia.gpu

import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SingleOwner
import org.skia.core.SkEnumBitMask
import org.skia.core.SkExecutor
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class MtlSharedContext final : public SharedContext {
 * public:
 *     static sk_sp<SharedContext> Make(const MtlBackendContext&, const ContextOptions&);
 *     ~MtlSharedContext() override;
 *
 *     skgpu::MtlMemoryAllocator* memoryAllocator() const { return fMemoryAllocator.get(); }
 *
 *     id<MTLDevice> device() const { return fDevice.get(); }
 *
 *     const MtlCaps& mtlCaps() const { return static_cast<const MtlCaps&>(*this->caps()); }
 *
 *     MtlThreadSafeResourceProvider* threadSafeResourceProvider() const;
 *
 *     std::unique_ptr<ResourceProvider> makeResourceProvider(SingleOwner*,
 *                                                            uint32_t recorderID,
 *                                                            size_t resourceBudget) override;
 *
 *     sk_cfp<id<MTLDepthStencilState>> getCompatibleDepthStencilState(
 *         const DepthStencilSettings&) const;
 *
 *
 * private:
 *
 *     MtlSharedContext(sk_cfp<id<MTLDevice>>,
 *                      sk_sp<skgpu::MtlMemoryAllocator>,
 *                      std::unique_ptr<const MtlCaps>,
 *                      SkExecutor*,
 *                      SkSpan<sk_sp<SkRuntimeEffect>> userDefinedKnownRuntimeEffects);
 *
 *     void createCompatibleDepthStencilState(const DepthStencilSettings&);
 *
 *     sk_sp<GraphicsPipeline> createGraphicsPipeline(
 *             const RuntimeEffectDictionary*,
 *             const UniqueKey&,
 *             const GraphicsPipelineDesc&,
 *             const RenderPassDesc&,
 *             SkEnumBitMask<PipelineCreationFlags>,
 *             uint32_t compilationID) override;
 *
 *     sk_sp<skgpu::MtlMemoryAllocator> fMemoryAllocator;
 *
 *     sk_cfp<id<MTLDevice>> fDevice;
 *
 *     // In the current Graphite class structure 'fDepthStencilStates' would more appropriately
 *     // go in a new MtlGlobalCache class. However, GlobalCache may go away as a concept, in
 *     // which case, this would be a reasonable place for this cache.
 *     // TODO(robertphillips): Come up with a scheme to map from DepthStencilSettings to tightly
 *     // packed ints and switch this to be a std::array.
 *     skia_private::THashMap<DepthStencilSettings, sk_cfp<id<MTLDepthStencilState>>>
 *             fDepthStencilStates;
 * }
 * ```
 */
public class MtlSharedContext public constructor(
  memoryAllocator: SkSp<MtlMemoryAllocator>,
  caps: MtlCaps?,
  executor: SkExecutor?,
  userDefinedKnownRuntimeEffects: SkSpan<SkSp<SkRuntimeEffect>>,
) : SharedContext(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<skgpu::MtlMemoryAllocator> fMemoryAllocator
   * ```
   */
  private var fMemoryAllocator: Int = TODO("Initialize fMemoryAllocator")

  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLDevice>> fDevice
   * ```
   */
  private var fDevice: Int = TODO("Initialize fDevice")

  /**
   * C++ original:
   * ```cpp
   * skgpu::MtlMemoryAllocator* memoryAllocator() const { return fMemoryAllocator.get(); }
   * ```
   */
  public fun memoryAllocator(): MtlMemoryAllocator {
    TODO("Implement memoryAllocator")
  }

  /**
   * C++ original:
   * ```cpp
   * id<MTLDevice> device() const { return fDevice.get(); }
   * ```
   */
  public fun device(): Int {
    TODO("Implement device")
  }

  /**
   * C++ original:
   * ```cpp
   * const MtlCaps& mtlCaps() const { return static_cast<const MtlCaps&>(*this->caps()); }
   * ```
   */
  public fun mtlCaps(): Int {
    TODO("Implement mtlCaps")
  }

  /**
   * C++ original:
   * ```cpp
   * MtlThreadSafeResourceProvider* MtlSharedContext::threadSafeResourceProvider() const {
   *     return static_cast<MtlThreadSafeResourceProvider*>(fThreadSafeResourceProvider.get());
   * }
   * ```
   */
  public fun threadSafeResourceProvider(): MtlThreadSafeResourceProvider {
    TODO("Implement threadSafeResourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ResourceProvider> MtlSharedContext::makeResourceProvider(
   *         SingleOwner* singleOwner,
   *         uint32_t recorderID,
   *         size_t resourceBudget) {
   *     return std::unique_ptr<ResourceProvider>(new MtlResourceProvider(this,
   *                                                                      singleOwner,
   *                                                                      recorderID,
   *                                                                      resourceBudget));
   * }
   * ```
   */
  public override fun makeResourceProvider(
    singleOwner: SingleOwner?,
    recorderID: UInt,
    resourceBudget: ULong,
  ): Int {
    TODO("Implement makeResourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLDepthStencilState>> MtlSharedContext::getCompatibleDepthStencilState(
   *             const DepthStencilSettings& depthStencilSettings) const {
   *
   *     sk_cfp<id<MTLDepthStencilState>>* depthStencilState;
   *     depthStencilState = fDepthStencilStates.find(depthStencilSettings);
   *
   *     // We've explicitly initialized fDepthStencilStates with all the common depth stencil settings
   *     // in the ctor - since there are so few of them. This frees us from concurrency concerns (i.e.,
   *     // if we were to lazily create them and store them in a map). However, if a new one is
   *     // encountered we will need to either add its initialization to the ctor or reconsider this
   *     // approach.
   *     SkAssertResult(depthStencilState);
   *     return *depthStencilState;
   * }
   * ```
   */
  public fun getCompatibleDepthStencilState(depthStencilSettings: DepthStencilSettings): Int {
    TODO("Implement getCompatibleDepthStencilState")
  }

  /**
   * C++ original:
   * ```cpp
   * void MtlSharedContext::createCompatibleDepthStencilState(
   *                 const DepthStencilSettings& depthStencilSettings) {
   *
   *     MTLDepthStencilDescriptor* desc = [[MTLDepthStencilDescriptor alloc] init];
   *     SkASSERT(depthStencilSettings.fDepthTestEnabled ||
   *              depthStencilSettings.fDepthCompareOp == CompareOp::kAlways);
   *     desc.depthCompareFunction = compare_op_to_mtl(depthStencilSettings.fDepthCompareOp);
   *     if (depthStencilSettings.fDepthTestEnabled) {
   *         desc.depthWriteEnabled = depthStencilSettings.fDepthWriteEnabled;
   *     }
   *     if (depthStencilSettings.fStencilTestEnabled) {
   *         desc.frontFaceStencil = stencil_face_to_mtl(depthStencilSettings.fFrontStencil);
   *         desc.backFaceStencil = stencil_face_to_mtl(depthStencilSettings.fBackStencil);
   *     }
   *
   *     sk_cfp<id<MTLDepthStencilState>> dss(
   *             [this->device() newDepthStencilStateWithDescriptor: desc]);
   *
   *     fDepthStencilStates.set(depthStencilSettings, std::move(dss));
   * }
   * ```
   */
  private fun createCompatibleDepthStencilState(depthStencilSettings: DepthStencilSettings) {
    TODO("Implement createCompatibleDepthStencilState")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GraphicsPipeline> MtlSharedContext::createGraphicsPipeline(
   *         const RuntimeEffectDictionary* runtimeDict,
   *         const UniqueKey& pipelineKey,
   *         const GraphicsPipelineDesc& pipelineDesc,
   *         const RenderPassDesc& renderPassDesc,
   *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags,
   *         uint32_t compilationID) {
   *     return MtlGraphicsPipeline::Make(this,
   *                                      runtimeDict, pipelineKey, pipelineDesc, renderPassDesc,
   *                                      pipelineCreationFlags, compilationID);
   * }
   * ```
   */
  public override fun createGraphicsPipeline(
    runtimeDict: RuntimeEffectDictionary?,
    pipelineKey: UniqueKey,
    pipelineDesc: GraphicsPipelineDesc,
    renderPassDesc: RenderPassDesc,
    pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
    compilationID: UInt,
  ): Int {
    TODO("Implement createGraphicsPipeline")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SharedContext> MtlSharedContext::Make(const MtlBackendContext& context,
     *                                             const ContextOptions& options) {
     *     if (@available(macOS 10.15, iOS 13.0, tvOS 13.0, *)) {
     *         // no warning needed
     *     } else {
     *         SKGPU_LOG_E("Skia's Graphite backend no longer supports this OS version.");
     * #ifdef SK_BUILD_FOR_IOS
     *         SKGPU_LOG_E("Minimum supported version is iOS/tvOS 13.0.");
     * #else
     *         SKGPU_LOG_E("Minimum supported version is MacOS 10.15.");
     * #endif
     *         return nullptr;
     *     }
     *
     *     sk_cfp<id<MTLDevice>> device = sk_ret_cfp((id<MTLDevice>)(context.fDevice.get()));
     *
     *     std::unique_ptr<const MtlCaps> caps(new MtlCaps(device.get(), options));
     *
     *     // TODO: Add memory allocator to context once we figure out synchronization
     *     sk_sp<MtlMemoryAllocator> memoryAllocator = skgpu::MtlMemoryAllocatorImpl::Make(device.get());
     *     if (!memoryAllocator) {
     *         SkDEBUGFAIL("No supplied Metal memory allocator and unable to create one internally.");
     *         return nullptr;
     *     }
     *
     *     return sk_sp<SharedContext>(new MtlSharedContext(std::move(device),
     *                                                      std::move(memoryAllocator),
     *                                                      std::move(caps),
     *                                                      options.fExecutor,
     *                                                      options.fUserDefinedKnownRuntimeEffects));
     * }
     * ```
     */
    public fun make(context: MtlBackendContext, options: ContextOptions): Int {
      TODO("Implement make")
    }
  }
}
