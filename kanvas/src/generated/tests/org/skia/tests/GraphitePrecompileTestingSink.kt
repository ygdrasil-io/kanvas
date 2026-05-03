package org.skia.tests

import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream
import org.skia.gpu.Context
import org.skia.gpu.PrecompileContext
import org.skia.gpu.Recorder
import org.skia.gpu.UniqueKey
import org.skia.tools.GraphiteTestContext
import org.skia.tools.SkCommandLineConfigGraphite
import org.skia.tools.TestOptions

/**
 * C++ original:
 * ```cpp
 * class GraphitePrecompileTestingSink : public GraphiteSink {
 * public:
 *     GraphitePrecompileTestingSink(const SkCommandLineConfigGraphite*,
 *                                   const skiatest::graphite::TestOptions&);
 *     ~GraphitePrecompileTestingSink() override;
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *
 *     const char* fileExtension() const override {
 *         // Suppress writing out results from this config - we just want to check that
 *         // the precompilation API is expressive enough and prepopulates the cache.
 *         // If desired, this could be updated to save the result of the precompiled rendering.
 *         // However; if all the keys match, as is expected, the images should always match.
 *         return nullptr;
 *     }
 *
 * private:
 *
 *     Result drawSrc(const Src&,
 *                    skgpu::graphite::Context*,
 *                    skiatest::graphite::GraphiteTestContext*,
 *                    skgpu::graphite::Recorder*) const;
 *
 *     Result resetAndRecreatePipelines(skgpu::graphite::PrecompileContext*) const;
 *
 * #ifdef SK_DEBUG
 *     static void LogMissingKey(skgpu::graphite::PrecompileContext*,
 *                               const skgpu::UniqueKey& missingKey,
 *                               const char* missingKeyName,
 *                               const std::vector<skgpu::UniqueKey>& pool,
 *                               const char* poolName);
 * #endif
 *
 *     static void CompareKeys(skgpu::graphite::PrecompileContext*,
 *                             const std::vector<skgpu::UniqueKey>& vA, const char* aName,
 *                             const std::vector<skgpu::UniqueKey>& vB, const char* bName);
 *
 *     std::unique_ptr<skiatools::graphite::PipelineCallBackHandler> fPipelineHandler;
 * }
 * ```
 */
public open class GraphitePrecompileTestingSink public constructor(
  param0: SkCommandLineConfigGraphite,
  param1: TestOptions,
) : GraphiteSink() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skiatools::graphite::PipelineCallBackHandler> fPipelineHandler
   * ```
   */
  private var fPipelineHandler: Int = TODO("Initialize fPipelineHandler")

  /**
   * C++ original:
   * ```cpp
   * GraphitePrecompileTestingSink(const SkCommandLineConfigGraphite*,
   *                                   const skiatest::graphite::TestOptions&)
   * ```
   */
  public constructor(config: SkCommandLineConfigGraphite?, options: TestOptions) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result GraphitePrecompileTestingSink::draw(const Src& src,
   *                                            SkBitmap* dst,
   *                                            SkWStream* dstStream,
   *                                            SkString* log) const {
   *     using namespace skgpu::graphite;
   *     using namespace skiatest::graphite;
   *     using namespace skiatools::graphite;
   *
   *     fPipelineHandler->reset();
   *
   *     {
   *         TestOptions options = fOptions;
   *         // If we've copied context options from an external source we can't trust that the
   *         // priv pointer is still in scope, so assume it should be NULL and set our own up.
   *         SkASSERT(!options.fContextOptions.fOptionsPriv);
   *         ContextOptionsPriv optionsPriv;
   *         options.fContextOptions.fOptionsPriv = &optionsPriv;
   *
   *         // We don't expect the src to mess with the more esoteric options
   *         SkDEBUGCODE(auto cache = options.fContextOptions.fPersistentPipelineStorage);
   *         SkDEBUGCODE(auto exec = options.fContextOptions.fExecutor);
   *         SkDEBUGCODE(auto cbContext = options.fContextOptions.fPipelineCallbackContext);
   *         SkDEBUGCODE(auto cb1 = options.fContextOptions.fPipelineCallback);
   *         SkDEBUGCODE(auto cb2 = options.fContextOptions.fPipelineCachingCallback);
   *         src.modifyGraphiteContextOptions(&options.fContextOptions);
   *         SkASSERT(cache == options.fContextOptions.fPersistentPipelineStorage);
   *         SkASSERT(exec == options.fContextOptions.fExecutor);
   *         SkASSERT(cbContext == options.fContextOptions.fPipelineCallbackContext);
   *         SkASSERT(cb1 == options.fContextOptions.fPipelineCallback);
   *         SkASSERT(cb2 == options.fContextOptions.fPipelineCachingCallback);
   *
   *         ContextFactory factory(options);
   *         skiatest::graphite::ContextInfo ctxInfo = factory.getContextInfo(fContextType);
   *         Context* context = ctxInfo.fContext;
   *         if (!context) {
   *             return Result::Fatal("Could not create a context.");
   *         }
   *
   *         std::unique_ptr<skgpu::graphite::Recorder> recorder =
   *                 context->makeRecorder(ToolUtils::CreateTestingRecorderOptions());
   *         if (!recorder) {
   *             return Result::Fatal("Could not create a recorder.");
   *         }
   *         std::unique_ptr<PrecompileContext> precompileContext = context->makePrecompileContext();
   *         ShaderCodeDictionary* shaderCodeDictionary = context->priv().shaderCodeDictionary();
   *
   *         SkASSERT_RELEASE(!context->priv().globalCache()->numGraphicsPipelines());
   *         SkASSERT_RELEASE(!shaderCodeDictionary->numUserDefinedRuntimeEffects());
   *
   *         // Draw the Src for the first time, populating the global pipeline cache.
   *         Result result = this->drawSrc(src, context, ctxInfo.fTestContext, recorder.get());
   *         if (!result.isOk()) {
   *             return result;
   *         }
   *
   *         // Note: this is different than numUserDefinedKnownRuntimeEffects! Known user-defined
   *         // runtime effects are allowed while unknown ones are not.
   *         if (shaderCodeDictionary->numUserDefinedRuntimeEffects()) {
   *             return Result::Skip("User-defined runtime effects cannot be serialized");
   *         }
   *
   *         // Call resetAndRecreatePipelines to clear out all the Pipelines in the global cache and
   *         // then regenerate them using the Precompilation system.
   *         result = this->resetAndRecreatePipelines(precompileContext.get());
   *         if (!result.isOk()) {
   *             return result;
   *         }
   *
   *         GlobalCache* globalCache = precompileContext->priv().globalCache();
   *         int numBeforeSecondDraw = globalCache->numGraphicsPipelines();
   *
   *         // Draw the Src for the second time. This shouldn't create any new Pipelines since the ones
   *         // generated via Precompilation should be sufficient.
   *         result = this->drawSrc(src, context, ctxInfo.fTestContext, recorder.get());
   *         if (!result.isOk()) {
   *             return result;
   *         }
   *
   *         SkASSERT_RELEASE(numBeforeSecondDraw == globalCache->numGraphicsPipelines());
   *     }
   *
   *     fPipelineHandler->reset();
   *
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    dst: SkBitmap?,
    dstStream: SkWStream?,
    log: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override {
   *         // Suppress writing out results from this config - we just want to check that
   *         // the precompilation API is expressive enough and prepopulates the cache.
   *         // If desired, this could be updated to save the result of the precompiled rendering.
   *         // However; if all the keys match, as is expected, the images should always match.
   *         return nullptr;
   *     }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * Result GraphitePrecompileTestingSink::drawSrc(
   *         const Src& src,
   *         skgpu::graphite::Context* context,
   *         skiatest::graphite::GraphiteTestContext* testContext,
   *         skgpu::graphite::Recorder* recorder) const {
   *
   *     sk_sp<SkSurface> surface = this->makeSurface(recorder, src);
   *     if (!surface) {
   *         return Result::Fatal("Could not create a surface.");
   *     }
   *     Result result = src.draw(surface->getCanvas(), testContext);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *
   *     std::unique_ptr<skgpu::graphite::Recording> recording = recorder->snap();
   *     if (!recording) {
   *         return Result::Fatal("Could not create a recording.");
   *     }
   *
   *     skgpu::graphite::InsertRecordingInfo info;
   *     info.fRecording = recording.get();
   *     if (!context->insertRecording(info)) {
   *         return Result::Fatal("Context::insertRecording failed.");
   *     }
   *     if (!context->submit(skgpu::graphite::SyncToCpu::kYes)) {
   *         return Result::Fatal("Context::submit failed.");
   *     }
   *
   *     return Result::Ok();
   * }
   * ```
   */
  private fun drawSrc(
    src: Src,
    context: Context?,
    testContext: GraphiteTestContext?,
    recorder: Recorder?,
  ): Result {
    TODO("Implement drawSrc")
  }

  /**
   * C++ original:
   * ```cpp
   * Result GraphitePrecompileTestingSink::resetAndRecreatePipelines(
   *         skgpu::graphite::PrecompileContext* precompileContext) const {
   *     using namespace skgpu::graphite;
   *
   *     GlobalCache* globalCache = precompileContext->priv().globalCache();
   *
   *     std::vector<skgpu::UniqueKey> origKeys;
   *
   *     UniqueKeyUtils::FetchUniqueKeys(precompileContext, &origKeys);
   *
   *     std::vector<sk_sp<SkData>> androidStyleKeys;
   *
   *     fPipelineHandler->retrieveKeys(&androidStyleKeys);
   *     fPipelineHandler->reset();
   *
   *     SkASSERTF_RELEASE(origKeys.size() == androidStyleKeys.size(),
   *                       "orig %zu != new %zu", origKeys.size(), androidStyleKeys.size());
   *
   *     int numBeforeReset = globalCache->numGraphicsPipelines();
   *     SkASSERT_RELEASE(numBeforeReset == (int) origKeys.size());
   *
   *     globalCache->resetGraphicsPipelines();
   *
   *     SkASSERT_RELEASE(globalCache->numGraphicsPipelines() == 0);
   *
   *     for (sk_sp<SkData>& d : androidStyleKeys) {
   *         bool result = precompileContext->precompile(d);
   *         SkAssertResult(result);
   *     }
   *
   *     int postRecreate = globalCache->numGraphicsPipelines();
   *
   *     SkASSERTF_RELEASE(numBeforeReset == postRecreate,
   *                       "before %d after %d", numBeforeReset, postRecreate);
   *
   *     {
   *         std::vector<skgpu::UniqueKey> recreatedKeys;
   *
   *         UniqueKeyUtils::FetchUniqueKeys(precompileContext, &recreatedKeys);
   *
   *         CompareKeys(precompileContext,
   *                     origKeys, "original",
   *                     recreatedKeys, "recreated");
   *     }
   *
   *     return Result::Ok();
   * }
   * ```
   */
  private fun resetAndRecreatePipelines(precompileContext: PrecompileContext?): Result {
    TODO("Implement resetAndRecreatePipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * void GraphitePrecompileTestingSink::LogMissingKey(
   *         skgpu::graphite::PrecompileContext* precompileContext,
   *         const skgpu::UniqueKey& missingKey,
   *         const char* missingKeyName,
   *         const std::vector<skgpu::UniqueKey>& pool,
   *         const char* poolName) {
   *     using namespace skgpu::graphite;
   *
   *     {
   *         GraphicsPipelineDesc originalPipelineDesc;
   *         RenderPassDesc originalRenderPassDesc;
   *         bool extracted = UniqueKeyUtils::ExtractKeyDescs(precompileContext, missingKey,
   *                                                          &originalPipelineDesc,
   *                                                          &originalRenderPassDesc);
   *
   *         SkDebugf("------- Key missing from %s keys:\n", poolName);
   *         missingKey.dump(missingKeyName);
   *         if (extracted) {
   *             UniqueKeyUtils::DumpDescs(precompileContext,
   *                                       originalPipelineDesc,
   *                                       originalRenderPassDesc);
   *         }
   *     }
   *
   *     SkDebugf("Have %d %s keys -----------------\n", (int) pool.size(), poolName);
   *     int count = 0;
   *     for (const skgpu::UniqueKey& b : pool) {
   *
   *         GraphicsPipelineDesc recreatedPipelineDesc;
   *         RenderPassDesc recreatedRenderPassDesc;
   *         bool extracted = UniqueKeyUtils::ExtractKeyDescs(precompileContext, b,
   *                                                          &recreatedPipelineDesc,
   *                                                          &recreatedRenderPassDesc);
   *
   *         SkDebugf("%d: ----\n", count++);
   *         b.dump("recreated key:");
   *         if (extracted) {
   *             UniqueKeyUtils::DumpDescs(precompileContext,
   *                                       recreatedPipelineDesc,
   *                                       recreatedRenderPassDesc);
   *         }
   *     }
   * }
   * ```
   */
  public fun logMissingKey(
    precompileContext: PrecompileContext?,
    missingKey: UniqueKey,
    missingKeyName: String?,
    pool: List<UniqueKey>,
    poolName: String?,
  ) {
    TODO("Implement logMissingKey")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void GraphitePrecompileTestingSink::CompareKeys(
     *         skgpu::graphite::PrecompileContext* precompileContext,
     *         const std::vector<skgpu::UniqueKey>& vA,
     *         const char* aName,
     *         const std::vector<skgpu::UniqueKey>& vB,
     *         const char* bName) {
     *
     *     for (const skgpu::UniqueKey& a : vA) {
     *         if (std::find(vB.begin(), vB.end(), a) == vB.end()) {
     * #ifdef SK_DEBUG
     *             LogMissingKey(precompileContext, a, aName, vB, bName);
     * #endif
     *
     *             SK_ABORT("missing");
     *         }
     *     }
     * }
     * ```
     */
    private fun compareKeys(
      precompileContext: PrecompileContext?,
      vA: List<UniqueKey>,
      aName: String?,
      vB: List<UniqueKey>,
      bName: String?,
    ) {
      TODO("Implement compareKeys")
    }
  }
}
