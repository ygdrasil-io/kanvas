package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.time.Duration
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileContext {
 * public:
 *     ~PrecompileContext();
 *
 *     /**
 *      * Purge Pipelines that haven't been used in the past 'msNotUsed' milliseconds
 *      * regardless of whether the pipeline cache is under budget.
 *      *
 *      * @param msNotUsed   Pipelines not used in these last milliseconds will be cleaned up.
 *      */
 *     void purgePipelinesNotUsedInMs(std::chrono::milliseconds msNotUsed);
 *
 *     enum class StatOptions {
 *         // Emit histograms (using the SK_HISTOGRAM* macros) for Skia's Precompiled Pipeline
 *         // usage:
 *         //    Skia.Graphite.Precompile.NormalPreemptedByPrecompile
 *         //    Skia.Graphite.Precompile.UnpreemptedPrecompilePipelines
 *         //    Skia.Graphite.Precompile.UnusedPrecompiledPipelines
 *         kPrecompile,
 *         // Emit histograms (using the SK_HISTOGRAM* macros) for Skia's Pipeline cache usage:
 *         //    Skia.Graphite.PipelineCache.PipelineUsesInEpoch
 *         kPipelineCache,
 *     };
 *
 *     /**
 *      * Emit histograms histograms related to Skia's Pipelines (c.f. the StatOptions enum).
 *      */
 *     void reportPipelineStats(StatOptions option = StatOptions::kPrecompile);
 *
 *     /**
 *      * Precompile one specific Pipeline that has been previously serialized. Serialized pipeline
 *      * keys can be acquired via the ContextOptions::PipelineCallback.
 *      *
 *      * @param serializedPipelineKey   serialized Pipeline key.
 *      * @return                        true if a Pipeline was created from the key; false otherwise
 *      */
 *     bool precompile(sk_sp<SkData> serializedPipelineKey);
 *
 *     /**
 *      * Get a human-readable version of a serialized pipeline key.
 *      *
 *      * @param serializedPipelineKey   serialized Pipeline key.
 *      * @return                        A human-readable version of the provided key; "" on failure.
 *      */
 *     std::string getPipelineLabel(sk_sp<SkData> serializedPipelineKey);
 *
 *     // Provides access to functions that aren't part of the public API.
 *     PrecompileContextPriv priv();
 *     const PrecompileContextPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * private:
 *     friend class PrecompileContextPriv;
 *     friend class Context; // for ctor
 *
 *     explicit PrecompileContext(sk_sp<SharedContext>);
 *
 *     mutable SingleOwner fSingleOwner;
 *     sk_sp<SharedContext> fSharedContext;
 *     std::unique_ptr<ResourceProvider> fResourceProvider;
 * }
 * ```
 */
public data class PrecompileContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * explicit PrecompileContext(sk_sp<SharedContext>)
   * ```
   */
  private var skSp: PrecompileContext,
  /**
   * C++ original:
   * ```cpp
   * mutable SingleOwner fSingleOwner
   * ```
   */
  private var fSingleOwner: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SharedContext> fSharedContext
   * ```
   */
  private var fSharedContext: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ResourceProvider> fResourceProvider
   * ```
   */
  private var fResourceProvider: ResourceProvider?,
) {
  /**
   * C++ original:
   * ```cpp
   * void PrecompileContext::purgePipelinesNotUsedInMs(std::chrono::milliseconds msNotUsed) {
   *     ASSERT_SINGLE_OWNER
   *
   *     auto purgeTime = skgpu::StdSteadyClock::now() - msNotUsed;
   *
   *     fSharedContext->globalCache()->purgePipelinesNotUsedSince(purgeTime);
   * }
   * ```
   */
  public fun purgePipelinesNotUsedInMs(msNotUsed: Duration) {
    TODO("Implement purgePipelinesNotUsedInMs")
  }

  /**
   * C++ original:
   * ```cpp
   * void PrecompileContext::reportPipelineStats(StatOptions option) {
   *     ASSERT_SINGLE_OWNER
   *
   *     if (option == StatOptions::kPrecompile) {
   *         fSharedContext->globalCache()->reportPrecompileStats();
   *     } else {
   *         fSharedContext->globalCache()->reportCacheStats();
   *     }
   * }
   * ```
   */
  public fun reportPipelineStats(option: StatOptions = TODO()) {
    TODO("Implement reportPipelineStats")
  }

  /**
   * C++ original:
   * ```cpp
   * bool PrecompileContext::precompile(sk_sp<SkData> serializedPipelineKey) {
   * #if defined(SK_ENABLE_PRECOMPILE)
   *     sk_sp<RuntimeEffectDictionary> rtEffectDict = sk_make_sp<RuntimeEffectDictionary>();
   *     const Caps* caps = fSharedContext->caps();
   *
   *     GraphicsPipelineDesc pipelineDesc;
   *     RenderPassDesc renderPassDesc;
   *
   *     if (!DataToPipelineDesc(caps,
   *                             fSharedContext->shaderCodeDictionary(),
   *                             serializedPipelineKey.get(),
   *                             &pipelineDesc,
   *                             &renderPassDesc)) {
   *         return false;
   *     }
   *
   *     GraphicsPipelineHandle handle = fResourceProvider->createGraphicsPipelineHandle(
   *             pipelineDesc,
   *             renderPassDesc,
   *             PipelineCreationFlags::kForPrecompilation);
   *     fResourceProvider->startPipelineCreationTask(rtEffectDict, handle);
   *
   *     return true;
   * #else
   *     return false;
   * #endif
   * }
   * ```
   */
  public fun precompile(serializedPipelineKey: SkSp<SkData>): Boolean {
    TODO("Implement precompile")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string PrecompileContext::getPipelineLabel(sk_sp<SkData> serializedPipelineKey) {
   * #if defined(SK_ENABLE_PRECOMPILE)
   *     GraphicsPipelineDesc pipelineDesc;
   *     RenderPassDesc renderPassDesc;
   *
   *     if (!DataToPipelineDesc(fSharedContext->caps(),
   *                             fSharedContext->shaderCodeDictionary(),
   *                             serializedPipelineKey.get(),
   *                             &pipelineDesc,
   *                             &renderPassDesc)) {
   *         return "";
   *     }
   *
   *     const RendererProvider* rendererProvider = fSharedContext->rendererProvider();
   *
   *     const RenderStep* renderStep = rendererProvider->lookup(pipelineDesc.renderStepID());
   *     if (!renderStep) {
   *         return "";
   *     }
   *
   *     return GetPipelineLabel(fSharedContext->caps(),
   *                             fSharedContext->shaderCodeDictionary(),
   *                             renderPassDesc,
   *                             renderStep,
   *                             pipelineDesc.paintParamsID());
   * #else
   *     return "";
   * #endif
   * }
   * ```
   */
  public fun getPipelineLabel(serializedPipelineKey: SkSp<SkData>): Int {
    TODO("Implement getPipelineLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileContextPriv priv()
   * ```
   */
  public fun priv(): PrecompileContextPriv {
    TODO("Implement priv")
  }

  public enum class StatOptions {
    kPrecompile,
    kPipelineCache,
  }
}
