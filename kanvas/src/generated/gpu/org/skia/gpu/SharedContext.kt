package org.skia.gpu

import StdSteadyClock.time_point
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SingleOwner
import org.skia.core.SkEnumBitMask
import org.skia.core.SkExecutor
import org.skia.core.SkTraceMemoryDump
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.tools.SkCaptureManager

/**
 * C++ original:
 * ```cpp
 * class SharedContext : public SkRefCnt {
 * public:
 *     ~SharedContext() override;
 *
 *     /**
 *      * Gets the capabilities of the draw target.
 *      */
 *     const Caps* caps() const { return fCaps.get(); }
 *
 *     BackendApi backend() const { return fBackend; }
 *     Protected isProtected() const;
 *
 *     GlobalCache* globalCache() { return &fGlobalCache; }
 *     const GlobalCache* globalCache() const { return &fGlobalCache; }
 *
 *     PipelineManager* pipelineManager() { return &fPipelineManager; }
 *
 *     const RendererProvider* rendererProvider() const { return fRendererProvider.get(); }
 *
 *     ShaderCodeDictionary* shaderCodeDictionary() { return &fShaderDictionary; }
 *     const ShaderCodeDictionary* shaderCodeDictionary() const { return &fShaderDictionary; }
 *
 *     virtual std::unique_ptr<ResourceProvider> makeResourceProvider(SingleOwner*,
 *                                                                    uint32_t recorderID,
 *                                                                    size_t resourceBudget) = 0;
 *
 *     // The runtime effect dictionary provides a link between SkCodeSnippetIds referenced in the
 *     // paint key and the current SkRuntimeEffect that provides the SkSL for that id.
 *     sk_sp<GraphicsPipeline> findOrCreateGraphicsPipeline(
 *             const RuntimeEffectDictionary*,
 *             const UniqueKey& pipelineKey,
 *             const GraphicsPipelineDesc&,
 *             const RenderPassDesc&,
 *             SkEnumBitMask<PipelineCreationFlags>);
 *
 *     // Called by Context::isContextLost(). Returns true if the backend-specific SharedContext has
 *     // gotten into an unrecoverable, lost state.
 *     virtual bool isDeviceLost() const { return false; }
 *
 *     virtual void deviceTick(Context*) {}
 *
 *     virtual void syncPipelineData(PersistentPipelineStorage*, size_t maxSize) {}
 *
 *     SkCaptureManager* captureManager() { return fCaptureManager.get(); }
 *
 * #if defined(SK_DEBUG)
 *     size_t getResourceCacheLimit() const;
 *     size_t getResourceCacheCurrentBudgetedBytes() const;
 *     size_t getResourceCacheCurrentPurgeableBytes() const;
 * #endif
 *
 *     void dumpMemoryStatistics(SkTraceMemoryDump*) const;
 *     void freeGpuResources();
 *     void purgeResourcesNotUsedSince(StdSteadyClock::time_point purgeTime);
 *     void forceProcessReturnedResources();
 *
 * protected:
 *     SharedContext(std::unique_ptr<const Caps>,
 *                   BackendApi,
 *                   SkExecutor* executor,
 *                   SkSpan<sk_sp<SkRuntimeEffect>> userDefinedKnownRuntimeEffects);
 *
 *     // All the resources in the ThreadSafeResourceProvider should be 0-sized.
 *     static constexpr size_t kThreadedSafeResourceBudget = 256;
 *
 *     // This SingleOwner is for ResourceProvider wrapped by the fThreadSafeResourceProvider.
 *     // It can't be in that class bc each backend-specific ShareContext must make its own
 *     // backend-specific wrapped ResourceProvider in its constructor.
 *     mutable SingleOwner fSingleOwner;
 *     std::unique_ptr<ThreadSafeResourceProvider> fThreadSafeResourceProvider;
 *
 * private:
 *     friend class Context; // for setRendererProvider() and setCaptureManager()
 *
 *     virtual sk_sp<GraphicsPipeline> createGraphicsPipeline(
 *             const RuntimeEffectDictionary*,
 *             const UniqueKey&,
 *             const GraphicsPipelineDesc&,
 *             const RenderPassDesc&,
 *             SkEnumBitMask<PipelineCreationFlags>,
 *             uint32_t compilationID) = 0;
 *
 *     // Must be created out-of-band to allow RenderSteps to use a QueueManager.
 *     void setRendererProvider(std::unique_ptr<RendererProvider> rendererProvider);
 *
 *     void setCaptureManager(sk_sp<SkCaptureManager> captureManager);
 *
 *     std::unique_ptr<const Caps> fCaps; // Provided by backend subclass
 *
 *     BackendApi fBackend;
 *     GlobalCache fGlobalCache;
 *     PipelineManager fPipelineManager;
 *     std::unique_ptr<RendererProvider> fRendererProvider;
 *     ShaderCodeDictionary fShaderDictionary;
 *     sk_sp<SkCaptureManager> fCaptureManager;
 * }
 * ```
 */
public abstract class SharedContext public constructor(
  caps: Caps?,
  backend: BackendApi,
  executor: SkExecutor?,
  userDefinedKnownRuntimeEffects: SkSpan<SkSp<SkRuntimeEffect>>,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kThreadedSafeResourceBudget = 256
   * ```
   */
  protected var fSingleOwner: SingleOwner = TODO("Initialize fSingleOwner")

  /**
   * C++ original:
   * ```cpp
   * mutable SingleOwner fSingleOwner
   * ```
   */
  protected var fThreadSafeResourceProvider: Int = TODO("Initialize fThreadSafeResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ThreadSafeResourceProvider> fThreadSafeResourceProvider
   * ```
   */
  private var fCaps: Int = TODO("Initialize fCaps")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<const Caps> fCaps
   * ```
   */
  private var fBackend: Int = TODO("Initialize fBackend")

  /**
   * C++ original:
   * ```cpp
   * BackendApi fBackend
   * ```
   */
  private var fGlobalCache: Int = TODO("Initialize fGlobalCache")

  /**
   * C++ original:
   * ```cpp
   * GlobalCache fGlobalCache
   * ```
   */
  private var fPipelineManager: Int = TODO("Initialize fPipelineManager")

  /**
   * C++ original:
   * ```cpp
   * PipelineManager fPipelineManager
   * ```
   */
  private var fRendererProvider: Int = TODO("Initialize fRendererProvider")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<RendererProvider> fRendererProvider
   * ```
   */
  private var fShaderDictionary: Int = TODO("Initialize fShaderDictionary")

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary fShaderDictionary
   * ```
   */
  private var fCaptureManager: Int = TODO("Initialize fCaptureManager")

  /**
   * C++ original:
   * ```cpp
   * const Caps* caps() const { return fCaps.get(); }
   * ```
   */
  public fun caps(): Caps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi backend() const { return fBackend; }
   * ```
   */
  public fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * Protected SharedContext::isProtected() const { return Protected(fCaps->protectedSupport()); }
   * ```
   */
  public fun isProtected(): Int {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * GlobalCache* globalCache() { return &fGlobalCache; }
   * ```
   */
  public fun globalCache(): Int {
    TODO("Implement globalCache")
  }

  /**
   * C++ original:
   * ```cpp
   * const GlobalCache* globalCache() const { return &fGlobalCache; }
   * ```
   */
  public fun pipelineManager(): Int {
    TODO("Implement pipelineManager")
  }

  /**
   * C++ original:
   * ```cpp
   * PipelineManager* pipelineManager() { return &fPipelineManager; }
   * ```
   */
  public fun rendererProvider(): RendererProvider {
    TODO("Implement rendererProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * const RendererProvider* rendererProvider() const { return fRendererProvider.get(); }
   * ```
   */
  public fun shaderCodeDictionary(): Int {
    TODO("Implement shaderCodeDictionary")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderCodeDictionary* shaderCodeDictionary() { return &fShaderDictionary; }
   * ```
   */
  public abstract fun makeResourceProvider(
    param0: SingleOwner?,
    recorderID: UInt,
    resourceBudget: ULong,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * const ShaderCodeDictionary* shaderCodeDictionary() const { return &fShaderDictionary; }
   * ```
   */
  public fun findOrCreateGraphicsPipeline(
    runtimeDict: RuntimeEffectDictionary?,
    pipelineKey: UniqueKey,
    pipelineDesc: GraphicsPipelineDesc,
    renderPassDesc: RenderPassDesc,
    pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
  ): Int {
    TODO("Implement findOrCreateGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<ResourceProvider> makeResourceProvider(SingleOwner*,
   *                                                                    uint32_t recorderID,
   *                                                                    size_t resourceBudget) = 0
   * ```
   */
  public open fun isDeviceLost(): Boolean {
    TODO("Implement isDeviceLost")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GraphicsPipeline> SharedContext::findOrCreateGraphicsPipeline(
   *         const RuntimeEffectDictionary* runtimeDict,
   *         const UniqueKey& pipelineKey,
   *         const GraphicsPipelineDesc& pipelineDesc,
   *         const RenderPassDesc& renderPassDesc,
   *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags) {
   *     auto globalCache = this->globalCache();
   *
   *     uint32_t compilationID = 0;
   *     sk_sp<GraphicsPipeline> pipeline = globalCache->findGraphicsPipeline(pipelineKey,
   *                                                                          pipelineCreationFlags,
   *                                                                          &compilationID);
   *     if (!pipeline) {
   *         // Haven't encountered this pipeline, so create a new one. Since pipelines are shared
   *         // across Recorders, we could theoretically create equivalent pipelines on different
   *         // threads. If this happens, GlobalCache returns the first-through-gate pipeline and we
   *         // discard the redundant pipeline. While this is wasted effort in the rare event of a race,
   *         // it allows pipeline creation to be performed without locking the global cache.
   *         // NOTE: The parameters to TRACE_EVENT are only evaluated inside an if-block when the
   *         // category is enabled.
   *         TRACE_EVENT1_ALWAYS(
   *                 "skia.shaders", "createGraphicsPipeline", "desc",
   *                 TRACE_STR_COPY(to_str(this, pipelineDesc, renderPassDesc).c_str()));
   *
   * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
   *         bool forPrecompile =
   *                 SkToBool(pipelineCreationFlags & PipelineCreationFlags::kForPrecompilation);
   *
   *         static const char* kNames[2] = { "BeginBuildN", "BeginBuildP" };
   *         TRACE_EVENT_INSTANT2("skia.gpu",
   *                              TRACE_STR_STATIC(kNames[forPrecompile]),
   *                              TRACE_EVENT_SCOPE_THREAD,
   *                              "key", pipelineKey.hash(),
   *                              "compilationID", compilationID);
   * #endif
   *
   *         pipeline = this->createGraphicsPipeline(runtimeDict, pipelineKey,
   *                                                 pipelineDesc, renderPassDesc,
   *                                                 pipelineCreationFlags,
   *                                                 compilationID);
   *         if (pipeline) {
   *             // TODO: Should we store a null pipeline if we failed to create one so that subsequent
   *             // usage immediately sees that the pipeline cannot be created, vs. retrying every time?
   *             bool addedToCache;
   *             std::tie(pipeline, addedToCache) = globalCache->addGraphicsPipeline(pipelineKey,
   *                                                                                 pipeline);
   *
   *             if (addedToCache && globalCache->hasPipelineCallback()) {
   *                 sk_sp<SkData> data = PipelineDescToData(this->caps(),
   *                                                         this->shaderCodeDictionary(),
   *                                                         pipelineDesc,
   *                                                         renderPassDesc);
   *                 globalCache->invokePipelineCallback(
   *                     ContextOptions::PipelineCacheOp::kAddingPipeline,
   *                     pipeline.get(),
   *                     std::move(data));
   *             }
   *         }
   *     }
   *     return pipeline;
   * }
   * ```
   */
  public open fun deviceTick(param0: Context?) {
    TODO("Implement deviceTick")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isDeviceLost() const { return false; }
   * ```
   */
  public open fun syncPipelineData(param0: PersistentPipelineStorage?, maxSize: ULong) {
    TODO("Implement syncPipelineData")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void deviceTick(Context*) {}
   * ```
   */
  public fun captureManager(): SkCaptureManager {
    TODO("Implement captureManager")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void syncPipelineData(PersistentPipelineStorage*, size_t maxSize) {}
   * ```
   */
  public fun dumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCaptureManager* captureManager() { return fCaptureManager.get(); }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void SharedContext::dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const {
   *     fThreadSafeResourceProvider->dumpMemoryStatistics(traceMemoryDump);
   * }
   * ```
   */
  public fun purgeResourcesNotUsedSince(purgeTime: time_point) {
    TODO("Implement purgeResourcesNotUsedSince")
  }

  /**
   * C++ original:
   * ```cpp
   * void SharedContext::freeGpuResources() {
   *     fThreadSafeResourceProvider->freeGpuResources();
   * }
   * ```
   */
  public fun forceProcessReturnedResources() {
    TODO("Implement forceProcessReturnedResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void SharedContext::purgeResourcesNotUsedSince(StdSteadyClock::time_point purgeTime) {
   *     fThreadSafeResourceProvider->purgeResourcesNotUsedSince(purgeTime);
   * }
   * ```
   */
  private abstract fun createGraphicsPipeline(
    param0: RuntimeEffectDictionary?,
    param1: UniqueKey,
    param2: GraphicsPipelineDesc,
    param3: RenderPassDesc,
    param4: SkEnumBitMask<PipelineCreationFlags>,
    compilationID: UInt,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * void SharedContext::forceProcessReturnedResources() {
   *     fThreadSafeResourceProvider->forceProcessReturnedResources();
   * }
   * ```
   */
  private fun setRendererProvider(rendererProvider: RendererProvider?) {
    TODO("Implement setRendererProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<GraphicsPipeline> createGraphicsPipeline(
   *             const RuntimeEffectDictionary*,
   *             const UniqueKey&,
   *             const GraphicsPipelineDesc&,
   *             const RenderPassDesc&,
   *             SkEnumBitMask<PipelineCreationFlags>,
   *             uint32_t compilationID) = 0
   * ```
   */
  private fun setCaptureManager(captureManager: SkSp<SkCaptureManager>) {
    TODO("Implement setCaptureManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRendererProvider(std::unique_ptr<RendererProvider> rendererProvider)
   * ```
   */
  public fun getResourceCacheLimit(): ULong {
    TODO("Implement getResourceCacheLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SharedContext::setCaptureManager(sk_sp<SkCaptureManager> captureManager) {
   *     // Should only be called once and be non-null
   *     SkASSERT(captureManager && !fCaptureManager);
   *     fCaptureManager = captureManager;
   * }
   * ```
   */
  public fun getResourceCacheCurrentBudgetedBytes(): ULong {
    TODO("Implement getResourceCacheCurrentBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SharedContext::getResourceCacheLimit() const {
   *     return fThreadSafeResourceProvider->getResourceCacheLimit();
   * }
   * ```
   */
  public fun getResourceCacheCurrentPurgeableBytes(): ULong {
    TODO("Implement getResourceCacheCurrentPurgeableBytes")
  }

  public companion object {
    protected val kThreadedSafeResourceBudget: Int = TODO("Initialize kThreadedSafeResourceBudget")
  }
}
