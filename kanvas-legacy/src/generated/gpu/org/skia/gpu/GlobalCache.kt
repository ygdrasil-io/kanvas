package org.skia.gpu

import StdSteadyClock.time_point
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.Unit
import org.skia.core.SkEnumBitMask
import org.skia.core.TArray
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import undefined.DeprecatedPipelineCallback
import undefined.PipelineCallback
import undefined.PipelineCallbackContext

/**
 * C++ original:
 * ```cpp
 * class GlobalCache {
 * public:
 *     GlobalCache();
 *     ~GlobalCache();
 *
 *     void deleteResources();
 *
 *     // Find a cached GraphicsPipeline that matches the associated key.
 *     sk_sp<GraphicsPipeline> findGraphicsPipeline(
 *         const UniqueKey&,
 *         SkEnumBitMask<PipelineCreationFlags> = PipelineCreationFlags::kNone,
 *         uint32_t* compilationID = nullptr) SK_EXCLUDES(fSpinLock);
 *
 *     // Associate the given pipeline with the key. If the key has already had a separate pipeline
 *     // associated with the key, that pipeline is returned and the passed-in pipeline is discarded.
 *     // Otherwise, the passed-in pipeline is held by the GlobalCache and also returned back.
 *     std::pair<sk_sp<GraphicsPipeline>, bool> addGraphicsPipeline(
 *             const UniqueKey&, sk_sp<GraphicsPipeline>) SK_EXCLUDES(fSpinLock);
 *
 *     void purgePipelinesNotUsedSince(
 *             StdSteadyClock::time_point purgeTime) SK_EXCLUDES(fSpinLock);
 *
 *     void reportPrecompileStats() SK_EXCLUDES(fSpinLock);
 *     void reportCacheStats() SK_EXCLUDES(fSpinLock);
 *
 * #if defined(GPU_TEST_UTILS)
 *     int numGraphicsPipelines() const SK_EXCLUDES(fSpinLock);
 *     void resetGraphicsPipelines() SK_EXCLUDES(fSpinLock);
 *     void forEachGraphicsPipeline(
 *             const std::function<void(const UniqueKey&, const GraphicsPipeline*)>& fn)
 *             SK_EXCLUDES(fSpinLock);
 *     uint16_t getEpoch() const SK_EXCLUDES(fSpinLock);
 *     void forceNextEpochOverflow() SK_EXCLUDES(fSpinLock);
 * #endif
 *
 *     struct PipelineStats {
 * #if defined(GPU_TEST_UTILS)
 *         int fGraphicsCacheHits = 0;
 *         int fGraphicsCacheMisses = 0;
 *         int fGraphicsCacheAdditions = 0;
 *         int fGraphicsRaces = 0;
 *         int fGraphicsPurges = 0;
 * #endif
 *         // Normally compiled Pipelines that were skipped bc of a preexisting Precompiled Pipeline
 *         uint32_t fNormalPreemptedByPrecompile = 0;
 *         // Precompiled Pipelines that made it into the cache
 *         uint32_t fUnpreemptedPrecompilePipelines = 0;
 *         // Precompiled Pipelines that were purged from the cache prior to use
 *         uint32_t fPurgedUnusedPrecompiledPipelines = 0;
 *         // The number of Pipelines requested since the last call to reportCacheStats
 *         uint32_t fPipelineUsesInEpoch = 0;
 *     };
 *
 *     PipelineStats getStats() const SK_EXCLUDES(fSpinLock);
 *
 *     // Find and add operations for ComputePipelines, with the same pattern as GraphicsPipelines.
 *     sk_sp<ComputePipeline> findComputePipeline(const UniqueKey&) SK_EXCLUDES(fSpinLock);
 *     sk_sp<ComputePipeline> addComputePipeline(const UniqueKey&,
 *                                               sk_sp<ComputePipeline>) SK_EXCLUDES(fSpinLock);
 *
 *     // The GlobalCache holds a ref on the given Resource until the cache is destroyed, keeping it
 *     // alive for the lifetime of the SharedContext. This should be used only for Resources that are
 *     // immutable after initialization so that anyone can use the resource without synchronization
 *     // or reference tracking.
 *     void addStaticResource(sk_sp<Resource>) SK_EXCLUDES(fSpinLock);
 *
 *     // Note: we change the names here to better reflect the internal view of the Callbacks
 *     using PipelineCallbackContext = ContextOptions::PipelineCallbackContext;
 *     using PipelineCallback = ContextOptions::PipelineCachingCallback;
 *     using DeprecatedPipelineCallback = ContextOptions::PipelineCallback;
 *
 *     void setPipelineCallback(PipelineCallbackContext,
 *                              PipelineCallback,
 *                              DeprecatedPipelineCallback);
 *
 *     bool hasPipelineCallback() const { return fPipelineCallback || fDeprecatedPipelineCallback; }
 *
 *     void invokePipelineCallback(ContextOptions::PipelineCacheOp,
 *                                 const GraphicsPipeline*,
 *                                 sk_sp<SkData> serializedKey = nullptr);
 *
 *     // Returns a cached Sampler matching the sampler description, assuming that `desc` is a dynamic
 *     // sampler and does not have any immutable sampler information. The number of dynamic samplers
 *     // is small enough that they are created once during context initialization and then shared.
 *     //
 *     // The returned Sampler is kept alive by the GlobalCache's static resource collection for the
 *     // lifetime of the GlobalCache.
 *     const Sampler* getDynamicSampler(SamplerDesc desc) const SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{fSpinLock};
 *         SkASSERT(!desc.isImmutable() && desc.asSpan().size() == 1 && fDynamicSamplers[desc.desc()]);
 *         return fDynamicSamplers[desc.desc()];
 *     }
 *
 *     bool initializeDynamicSamplers(ResourceProvider*, const Caps*) SK_EXCLUDES(fSpinLock);
 *
 * #if defined(GPU_TEST_UTILS)
 *     struct StaticVertexCopyRanges {
 *         uint32_t fOffset;
 *         uint32_t fUnalignedSize;
 *         uint32_t fSize;
 *         uint32_t fRequiredAlignment;
 *     };
 *     void testingOnly_SetStaticVertexInfo(skia_private::TArray<StaticVertexCopyRanges>,
 *                                          const Buffer*) SK_EXCLUDES(fSpinLock);
 *     SkSpan<const StaticVertexCopyRanges> getStaticVertexCopyRanges() const SK_EXCLUDES(fSpinLock);
 *     sk_sp<Buffer> getStaticVertexBuffer() SK_EXCLUDES(fSpinLock);
 * #endif
 *
 * private:
 *     static constexpr int kNumDynamicSamplers = 1 << SamplerDesc::kImmutableSamplerInfoShift;
 *
 *     // Remove the GraphicsPipeline from the cache, if possible. This does nothing if the pipeline
 *     // is not held in the cache. This removes based on actual pipeline object, not by key. When
 *     // pipeline compilation has transient failures, it is possible for multiple GraphicsPipelines to
 *     // be created that have the same key.
 *     void removeGraphicsPipeline(const GraphicsPipeline*) SK_REQUIRES(fSpinLock);
 *
 *     struct KeyHash {
 *         uint32_t operator()(const UniqueKey& key) const { return key.hash(); }
 *     };
 *
 *     static void LogPurge(void* context, const UniqueKey& key, sk_sp<GraphicsPipeline>* p);
 *     struct PurgeCB {
 *         void operator()(void* context, const UniqueKey& k, sk_sp<GraphicsPipeline>* p) const {
 *             LogPurge(context, k, p);
 *         }
 *     };
 *
 *     using GraphicsPipelineCache = SkLRUCache<UniqueKey, sk_sp<GraphicsPipeline>, KeyHash, PurgeCB>;
 *     using ComputePipelineCache  = SkLRUCache<UniqueKey, sk_sp<ComputePipeline>,  KeyHash>;
 *
 *     // TODO: can we do something better given this should have write-seldom/read-often behavior?
 *     mutable SkSpinlock fSpinLock;
 *
 *     // GraphicsPipelines and ComputePipelines are expensive to create, likely to be used by multiple
 *     // Recorders, and are ideally pre-compiled on process startup so thread write-contention is
 *     // expected to be low. For these reasons we store pipelines globally instead of per-Recorder.
 *     GraphicsPipelineCache fGraphicsPipelineCache SK_GUARDED_BY(fSpinLock);
 *     ComputePipelineCache  fComputePipelineCache  SK_GUARDED_BY(fSpinLock);
 *
 *     skia_private::TArray<sk_sp<Resource>> fStaticResource SK_GUARDED_BY(fSpinLock);
 *
 *     PipelineCallbackContext fPipelineCallbackContext = nullptr;
 *
 *     PipelineCallback fPipelineCallback = nullptr;
 *     DeprecatedPipelineCallback fDeprecatedPipelineCallback = nullptr;
 *
 *     PipelineStats fStats SK_GUARDED_BY(fSpinLock);
 *
 *     // An epoch is the span of time between calls to PrecompileContext::reportPipelineStats.
 *     // Every Pipeline will be marked with the epoch in which it was created and then updated
 *     // for each epoch in which it was used.
 *     uint16_t fEpochCounter SK_GUARDED_BY(fSpinLock) = 1;
 *
 *     // Lookup table for dynamically created samplers. Each sampler represents a specific combination
 *     // of tile modes and sampling options. The array is indexed by a bitmask generated from these
 *     // properties. The actual Sampler objects are owned by `fStaticResource`.
 *     std::array<const Sampler*, kNumDynamicSamplers> fDynamicSamplers SK_GUARDED_BY(fSpinLock);
 *
 * #if defined(GPU_TEST_UTILS)
 *     skia_private::TArray<StaticVertexCopyRanges> fStaticVertexInfo SK_GUARDED_BY(fSpinLock);
 *     const Buffer* fStaticVertexBuffer SK_GUARDED_BY(fSpinLock);
 * #endif
 * }
 * ```
 */
public data class GlobalCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNumDynamicSamplers
   * ```
   */
  private var fSpinLock: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fGraphicsPipelineCache: Int,
  /**
   * C++ original:
   * ```cpp
   * GraphicsPipelineCache fGraphicsPipelineCache
   * ```
   */
  private var fComputePipelineCache: Int,
  /**
   * C++ original:
   * ```cpp
   * ComputePipelineCache  fComputePipelineCache
   * ```
   */
  private var fStaticResource: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<Resource>> fStaticResource
   * ```
   */
  private var fPipelineCallbackContext: Int,
  /**
   * C++ original:
   * ```cpp
   * PipelineCallbackContext fPipelineCallbackContext
   * ```
   */
  private var fPipelineCallback: Int,
  /**
   * C++ original:
   * ```cpp
   * PipelineCallback fPipelineCallback
   * ```
   */
  private var fDeprecatedPipelineCallback: Int,
  /**
   * C++ original:
   * ```cpp
   * DeprecatedPipelineCallback fDeprecatedPipelineCallback
   * ```
   */
  private var fStats: PipelineStats,
  /**
   * C++ original:
   * ```cpp
   * PipelineStats fStats
   * ```
   */
  private var fEpochCounter: Int,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fEpochCounter
   * ```
   */
  private var fDynamicSamplers: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<const Sampler*, kNumDynamicSamplers> fDynamicSamplers
   * ```
   */
  private var fStaticVertexInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<StaticVertexCopyRanges> fStaticVertexInfo
   * ```
   */
  private val fStaticVertexBuffer: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::deleteResources() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     for (int i = 0; i < kNumDynamicSamplers; ++i) {
   *         fDynamicSamplers[i] = nullptr;
   *     }
   *
   *     fGraphicsPipelineCache.reset();
   *     fComputePipelineCache.reset();
   *     fStaticResource.clear();
   * }
   * ```
   */
  public fun deleteResources() {
    TODO("Implement deleteResources")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GraphicsPipeline> GlobalCache::findGraphicsPipeline(
   *         const UniqueKey& key,
   *         SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags,
   *         uint32_t *compilationID) {
   *
   *     [[maybe_unused]] bool forPrecompile =
   *             SkToBool(pipelineCreationFlags & PipelineCreationFlags::kForPrecompilation);
   *
   *     sk_sp<GraphicsPipeline>* entry = nullptr;
   *     {
   *         SkAutoSpinlock lock{fSpinLock};
   *
   *         entry = fGraphicsPipelineCache.find(key);
   *         if (entry) {
   *             if ((*entry)->didAsyncCompilationFail()) SK_UNLIKELY {
   *                 // If the pipeline failed, remove it from the cache and let it be regenerated.
   *                 this->removeGraphicsPipeline((*entry).get());
   *                 return nullptr;
   *             }
   * #if defined(GPU_TEST_UTILS)
   *             ++fStats.fGraphicsCacheHits;
   * #endif
   *
   *             if ((*entry)->epoch() != fEpochCounter) {
   *                 (*entry)->markEpoch(fEpochCounter);   // update epoch due to use in a new epoch
   *                 ++fStats.fPipelineUsesInEpoch;
   *             }
   *             if (!forPrecompile && (*entry)->fromPrecompile() && !(*entry)->wasUsed()) {
   *                 ++fStats.fNormalPreemptedByPrecompile;
   *             }
   *
   *             (*entry)->updateAccessTime();
   *             (*entry)->markUsed();
   *
   * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
   *             static const char* kNames[2] = { "CacheHitForN", "CacheHitForP" };
   *             TRACE_EVENT_INSTANT2("skia.gpu",
   *                                  TRACE_STR_STATIC(kNames[forPrecompile]),
   *                                  TRACE_EVENT_SCOPE_THREAD,
   *                                  "key", key.hash(),
   *                                  "compilationID", (*entry)->getPipelineInfo().fCompilationID);
   * #endif
   *         } else {
   * #if defined(GPU_TEST_UTILS)
   *             ++fStats.fGraphicsCacheMisses;
   * #endif
   *
   *             if (compilationID) {
   *                 // This is a cache miss so we know the next step is going to be a Pipeline
   *                 // creation. Create the compilationID here so we can use it in the "CacheMissFor"
   *                 // trace event.
   *                 *compilationID = next_compilation_id();
   *
   * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
   *                 static const char* kNames[2] = { "CacheMissForN", "CacheMissForP" };
   *                 TRACE_EVENT_INSTANT2("skia.gpu",
   *                                      TRACE_STR_STATIC(kNames[forPrecompile]),
   *                                      TRACE_EVENT_SCOPE_THREAD,
   *                                      "key", key.hash(),
   *                                      "compilationID", *compilationID);
   * #endif
   *             }
   *         }
   *     }
   *
   *     if (entry) {
   *         this->invokePipelineCallback(ContextOptions::PipelineCacheOp::kPipelineFound, entry->get());
   *         return *entry;
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  public fun findGraphicsPipeline(
    key: UniqueKey,
    pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags> = TODO(),
    compilationID: UInt? = TODO(),
  ): Int {
    TODO("Implement findGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<GraphicsPipeline>, bool> GlobalCache::addGraphicsPipeline(
   *         const UniqueKey& key,
   *         sk_sp<GraphicsPipeline> pipeline) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<GraphicsPipeline>* entry = fGraphicsPipelineCache.find(key);
   *     if (!entry) {
   *         // No equivalent pipeline was stored in the cache between a previous call to
   *         // findGraphicsPipeline() that returned null (triggering the pipeline creation) and this
   *         // later adding to the cache.
   *         entry = fGraphicsPipelineCache.insert(key, std::move(pipeline));
   *
   * #if defined(GPU_TEST_UTILS)
   *         ++fStats.fGraphicsCacheAdditions;
   * #endif
   *
   *         SkASSERT((*entry)->epoch() == 0);
   *         (*entry)->markEpoch(fEpochCounter);      // mark w/ epoch in which it was created
   *         ++fStats.fPipelineUsesInEpoch;
   *
   *         if ((*entry)->fromPrecompile()) {
   *             ++fStats.fUnpreemptedPrecompilePipelines;
   *         }
   *
   *         // Precompile Pipelines are only marked as used when they get a cache hit in
   *         // findGraphicsPipeline
   *         if (!(*entry)->fromPrecompile()) {
   *             (*entry)->updateAccessTime();
   *             (*entry)->markUsed();
   *         }
   *
   * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
   *         static const char* kNames[2] = { "AddedN", "AddedP" };
   *         TRACE_EVENT_INSTANT2("skia.gpu",
   *                              TRACE_STR_STATIC(kNames[(*entry)->fromPrecompile()]),
   *                              TRACE_EVENT_SCOPE_THREAD,
   *                              "key", key.hash(),
   *                              "compilationID", (*entry)->getPipelineInfo().fCompilationID);
   * #endif
   *
   *         return {*entry, true};
   *     } else {
   * #if defined(GPU_TEST_UTILS)
   *         // else there was a race creating the same pipeline and this thread lost, so return
   *         // the winner
   *         ++fStats.fGraphicsRaces;
   * #endif
   *
   *         [[maybe_unused]] int race = (*entry)->fromPrecompile() * 2 + pipeline->fromPrecompile();
   *
   * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
   *         static const char* kNames[4] = {
   *                 "NWonRaceOverN",
   *                 "NWonRaceOverP",
   *                 "PWonRaceOverN",
   *                 "PWonRaceOverP"
   *         };
   *         TRACE_EVENT_INSTANT2("skia.gpu",
   *                              TRACE_STR_STATIC(kNames[race]),
   *                              TRACE_EVENT_SCOPE_THREAD,
   *                              "key", key.hash(),
   *                              // The losing compilation
   *                              "compilationID", pipeline->getPipelineInfo().fCompilationID);
   * #endif
   *
   *         SK_HISTOGRAM_ENUMERATION("Graphite.PipelineCreationRace",
   *                                  race,
   *                                  kPipelineCreationRaceCount);
   *
   *         return {*entry, false};
   *     }
   * }
   * ```
   */
  public fun addGraphicsPipeline(key: UniqueKey, pipeline: SkSp<GraphicsPipeline>): Int {
    TODO("Implement addGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::purgePipelinesNotUsedSince(StdSteadyClock::time_point purgeTime) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     skia_private::TArray<skgpu::UniqueKey> toRemove;
   *
   *     // This is probably fine for now but is looping from most-recently-used to least-recently-used.
   *     // It seems like a reverse loop with an early out could be more efficient.
   *     fGraphicsPipelineCache.foreach([&toRemove, purgeTime](const UniqueKey* key,
   *                                                           const sk_sp<GraphicsPipeline>* pipeline) {
   *         if ((*pipeline)->lastAccessTime() < purgeTime) {
   *             toRemove.push_back(*key);
   *         }
   *     });
   *
   *     for (const skgpu::UniqueKey& k : toRemove) {
   * #if defined(GPU_TEST_UTILS)
   *         ++fStats.fGraphicsPurges;
   * #endif
   *         fGraphicsPipelineCache.remove(k);
   *     }
   *
   *     // TODO: add purging of Compute Pipelines (b/389073204)
   * }
   * ```
   */
  public fun purgePipelinesNotUsedSince(purgeTime: time_point) {
    TODO("Implement purgePipelinesNotUsedSince")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::reportPrecompileStats() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     uint32_t numUnusedInCache = 0;
   *
   *     fGraphicsPipelineCache.foreach([&numUnusedInCache](const UniqueKey* key,
   *                                                        const sk_sp<GraphicsPipeline>* pipeline) {
   *         if (!(*pipeline)->wasUsed()) {
   *             SkASSERT((*pipeline)->fromPrecompile());
   *             ++numUnusedInCache;
   *         }
   *     });
   *
   *     // From local testing we expect these UMA stats to comfortably fit in the specified ranges.
   *     // If we see a lot of the counts hitting the over and under-flow buckets something
   *     // unexpected is happening and we would need to figure it out and, possibly, create
   *     // new UMA statistics for the observed range.
   *     SK_HISTOGRAM_CUSTOM_EXACT_LINEAR("Graphite.Precompile.NormalPreemptedByPrecompile",
   *                                      fStats.fNormalPreemptedByPrecompile,
   *                                      /* countMin= */ 1,
   *                                      /* countMax= */ 51,
   *                                      /* bucketCount= */ 52);
   *     SK_HISTOGRAM_CUSTOM_EXACT_LINEAR("Graphite.Precompile.UnpreemptedPrecompilePipelines",
   *                                      fStats.fUnpreemptedPrecompilePipelines,
   *                                      /* countMin= */ 100,
   *                                      /* countMax= */ 150,
   *                                      /* bucketCount= */ 52);
   *     SK_HISTOGRAM_CUSTOM_EXACT_LINEAR("Graphite.Precompile.UnusedPrecompiledPipelines",
   *                                      fStats.fPurgedUnusedPrecompiledPipelines + numUnusedInCache,
   *                                      /* countMin= */ 50,
   *                                      /* countMax= */ 100,
   *                                      /* bucketCount= */ 52);
   * }
   * ```
   */
  public fun reportPrecompileStats() {
    TODO("Implement reportPrecompileStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::reportCacheStats() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     SK_HISTOGRAM_CUSTOM_EXACT_LINEAR("Graphite.PipelineCache.PipelineUsesInEpoch",
   *                                      fStats.fPipelineUsesInEpoch,
   *                                      /* countMin= */ 1,
   *                                      /* countMax= */ 1001,
   *                                      /* bucketCount= */ 102); // 10/bucket
   *
   *     // Set up for a new epoch
   *     fStats.fPipelineUsesInEpoch = 0;
   *     ++fEpochCounter;
   *     if (!fEpochCounter) {
   *         // The epoch counter has wrapped around - this should be *very* rare. Reset the cache.
   *         fGraphicsPipelineCache.foreach([](const UniqueKey* key,
   *                                           const sk_sp<GraphicsPipeline>* pipeline) {
   *             (*pipeline)->markEpoch(0);
   *         });
   *         fEpochCounter = 1;
   *     }
   * }
   * ```
   */
  public fun reportCacheStats() {
    TODO("Implement reportCacheStats")
  }

  /**
   * C++ original:
   * ```cpp
   * int GlobalCache::numGraphicsPipelines() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return fGraphicsPipelineCache.count();
   * }
   * ```
   */
  public fun numGraphicsPipelines(): Int {
    TODO("Implement numGraphicsPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::resetGraphicsPipelines() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     fGraphicsPipelineCache.reset();
   * }
   * ```
   */
  public fun resetGraphicsPipelines() {
    TODO("Implement resetGraphicsPipelines")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::forEachGraphicsPipeline(
   *         const std::function<void(const UniqueKey&, const GraphicsPipeline*)>& fn) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     fGraphicsPipelineCache.foreach([&](const UniqueKey* k, const sk_sp<GraphicsPipeline>* v) {
   *         fn(*k, v->get());
   *     });
   * }
   * ```
   */
  public fun forEachGraphicsPipeline(fn: (UniqueKey, GraphicsPipeline?) -> Unit) {
    TODO("Implement forEachGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t GlobalCache::getEpoch() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return fEpochCounter;
   * }
   * ```
   */
  public fun getEpoch(): Int {
    TODO("Implement getEpoch")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::forceNextEpochOverflow() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     fEpochCounter = std::numeric_limits<uint16_t>::max();
   * }
   * ```
   */
  public fun forceNextEpochOverflow() {
    TODO("Implement forceNextEpochOverflow")
  }

  /**
   * C++ original:
   * ```cpp
   * GlobalCache::PipelineStats GlobalCache::getStats() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return fStats;
   * }
   * ```
   */
  public fun getStats(): PipelineStats {
    TODO("Implement getStats")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ComputePipeline> GlobalCache::findComputePipeline(const UniqueKey& key) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<ComputePipeline>* entry = fComputePipelineCache.find(key);
   *     return entry ? *entry : nullptr;
   * }
   * ```
   */
  public fun findComputePipeline(key: UniqueKey): Int {
    TODO("Implement findComputePipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ComputePipeline> GlobalCache::addComputePipeline(const UniqueKey& key,
   *                                                        sk_sp<ComputePipeline> pipeline) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<ComputePipeline>* entry = fComputePipelineCache.find(key);
   *     if (!entry) {
   *         entry = fComputePipelineCache.insert(key, std::move(pipeline));
   *     }
   *     return *entry;
   * }
   * ```
   */
  public fun addComputePipeline(key: UniqueKey, pipeline: SkSp<ComputePipeline>): Int {
    TODO("Implement addComputePipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::addStaticResource(sk_sp<Resource> resource) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     fStaticResource.push_back(std::move(resource));
   * }
   * ```
   */
  public fun addStaticResource(resource: SkSp<Resource>) {
    TODO("Implement addStaticResource")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::setPipelineCallback(PipelineCallbackContext context,
   *                                       PipelineCallback callback,
   *                                       DeprecatedPipelineCallback deprecatedCallback) {
   *     // This should only ever be called once (in Context's constructor)
   *     SkASSERT(!fPipelineCallbackContext && !fPipelineCallback && !fDeprecatedPipelineCallback);
   *
   *     fPipelineCallbackContext = context;
   *     fPipelineCallback = callback;
   *     fDeprecatedPipelineCallback = deprecatedCallback;
   * }
   * ```
   */
  public fun setPipelineCallback(
    context: PipelineCallbackContext,
    callback: PipelineCallback,
    deprecatedCallback: DeprecatedPipelineCallback,
  ) {
    TODO("Implement setPipelineCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPipelineCallback() const { return fPipelineCallback || fDeprecatedPipelineCallback; }
   * ```
   */
  public fun hasPipelineCallback(): Boolean {
    TODO("Implement hasPipelineCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::invokePipelineCallback(ContextOptions::PipelineCacheOp op,
   *                                          const GraphicsPipeline* pipeline,
   *                                          sk_sp<SkData> serializedKey) {
   *     // If both callbacks are provided the new version preempts the old version
   *     if (fPipelineCallback) {
   *         (*fPipelineCallback)(fPipelineCallbackContext,
   *                              op,
   *                              pipeline->getLabel(),
   *                              pipeline->getPipelineInfo().fUniqueKeyHash,
   *                              pipeline->fromPrecompile(),
   *                              std::move(serializedKey));
   *     } else if (fDeprecatedPipelineCallback && serializedKey) {
   *         (*fDeprecatedPipelineCallback)(fPipelineCallbackContext, std::move(serializedKey));
   *     }
   * }
   * ```
   */
  public fun invokePipelineCallback(
    op: ContextOptions.PipelineCacheOp,
    pipeline: GraphicsPipeline?,
    serializedKey: SkSp<SkData> = TODO(),
  ) {
    TODO("Implement invokePipelineCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * const Sampler* getDynamicSampler(SamplerDesc desc) const
   * ```
   */
  public fun getDynamicSampler(desc: SamplerDesc): Sampler {
    TODO("Implement getDynamicSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::testingOnly_SetStaticVertexInfo(
   *         skia_private::TArray<StaticVertexCopyRanges> vertBufferInfo, const Buffer* vertBuffer) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     fStaticVertexInfo = vertBufferInfo;
   *     fStaticVertexBuffer = vertBuffer;
   * }
   * ```
   */
  public fun testingOnlySetStaticVertexInfo(vertBufferInfo: TArray<StaticVertexCopyRanges>, vertBuffer: Buffer?) {
    TODO("Implement testingOnlySetStaticVertexInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const GlobalCache::StaticVertexCopyRanges> GlobalCache::getStaticVertexCopyRanges() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return SkSpan<const GlobalCache::StaticVertexCopyRanges>(fStaticVertexInfo);
   * }
   * ```
   */
  public fun getStaticVertexCopyRanges(): Int {
    TODO("Implement getStaticVertexCopyRanges")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Buffer> GlobalCache::getStaticVertexBuffer() {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     return sk_ref_sp(fStaticVertexBuffer);
   * }
   * ```
   */
  public fun getStaticVertexBuffer(): Int {
    TODO("Implement getStaticVertexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlobalCache::removeGraphicsPipeline(const GraphicsPipeline* pipeline) {
   *
   *     skia_private::STArray<1, skgpu::UniqueKey> toRemove;
   *     // This is only called when a pipeline failed to compile, so it is not performance critical.
   *     fGraphicsPipelineCache.foreach([&toRemove, pipeline](const UniqueKey* key,
   *                                                          const sk_sp<GraphicsPipeline>* inCache) {
   *         // Since inCache is ref'ed by GlobalCache, we can safely compare direct addresses and not
   *         // worry about a new GraphicsPipeline being allocated at an address that was still here.
   *         if ((*inCache).get() == pipeline) {
   *             toRemove.push_back(*key);
   *         }
   *     });
   *
   *     // The pipeline shouldn't have multiple unique keys, but this is structured to clean up every
   *     // occurrence of pipeline in fGraphicsPipelineCache in release builds.
   *     SkASSERT(toRemove.size() <= 1);
   *
   *     for (const skgpu::UniqueKey& k : toRemove) {
   *         fGraphicsPipelineCache.remove(k);
   *     }
   * }
   * ```
   */
  private fun removeGraphicsPipeline(pipeline: GraphicsPipeline?) {
    TODO("Implement removeGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GlobalCache::initializeDynamicSamplers(ResourceProvider* resourceProvider, const Caps* caps) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     SkASSERT(fDynamicSamplers[0] == nullptr); // Must not already be initialized
   *
   *     static constexpr SkTileMode kTileModes[] = {SkTileMode::kClamp,
   *                                                 SkTileMode::kRepeat,
   *                                                 SkTileMode::kMirror,
   *                                                 SkTileMode::kDecal};
   *     // Manually unroll the SkSamplingOptions that can be dynamic samplers to avoid nesting so many
   *     // loops to create SamplerDescs. Cubic SkSamplingOptions do not need to contribute to this list.
   *     // TODO: Support anisotropic filters
   *     static constexpr SkSamplingOptions kSamplingOptions[] = {
   *         {SkFilterMode::kNearest, SkMipmapMode::kNone},
   *         {SkFilterMode::kLinear,  SkMipmapMode::kNone},
   *         {SkFilterMode::kNearest, SkMipmapMode::kNearest},
   *         {SkFilterMode::kLinear,  SkMipmapMode::kNearest},
   *         {SkFilterMode::kNearest, SkMipmapMode::kLinear},
   *         {SkFilterMode::kLinear,  SkMipmapMode::kLinear}
   *     };
   *
   *     const bool supportsClampToBorder = caps->clampToBorderSupport();
   *     for (auto samplingOption : kSamplingOptions) {
   *         for (auto tileX : kTileModes) {
   *             for (auto tileY : kTileModes) {
   *                 if (!supportsClampToBorder && (tileX == SkTileMode::kDecal ||
   *                                                tileY == SkTileMode::kDecal)) {
   *                     continue;
   *                 }
   *
   *                 SamplerDesc dynamicDesc{samplingOption, {tileX, tileY}};
   *                 SkASSERT(!dynamicDesc.isImmutable() && dynamicDesc.asSpan().size() == 1);
   *                 sk_sp<Sampler> sampler =
   *                         resourceProvider->findOrCreateCompatibleSampler(dynamicDesc);
   *                 if (!sampler) {
   *                     return false;
   *                 }
   *
   *                 // We already hold the spin lock, so add directly to fStaticResource
   *                 fStaticResource.emplace_back(sampler);
   *                 fDynamicSamplers[dynamicDesc.desc()] = sampler.get();
   *             }
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun initializeDynamicSamplers(resourceProvider: ResourceProvider?, caps: Caps?): Boolean {
    TODO("Implement initializeDynamicSamplers")
  }

  public data class PipelineStats public constructor(
    public var fGraphicsCacheHits: Int,
    public var fGraphicsCacheMisses: Int,
    public var fGraphicsCacheAdditions: Int,
    public var fGraphicsRaces: Int,
    public var fGraphicsPurges: Int,
    public var fNormalPreemptedByPrecompile: Int,
    public var fUnpreemptedPrecompilePipelines: Int,
    public var fPurgedUnusedPrecompiledPipelines: Int,
    public var fPipelineUsesInEpoch: Int,
  )

  public data class StaticVertexCopyRanges public constructor(
    public var fOffset: Int,
    public var fUnalignedSize: Int,
    public var fSize: Int,
    public var fRequiredAlignment: Int,
  )

  public open class KeyHash {
    public operator fun invoke(key: UniqueKey): Int {
      TODO("Implement invoke")
    }
  }

  public open class PurgeCB {
    public operator fun invoke(
      context: Unit?,
      k: UniqueKey,
      p: SkSp<GraphicsPipeline>?,
    ) {
      TODO("Implement invoke")
    }
  }

  public companion object {
    private val kNumDynamicSamplers: Int = TODO("Initialize kNumDynamicSamplers")

    /**
     * C++ original:
     * ```cpp
     * void GlobalCache::LogPurge(void* context, const UniqueKey& key, sk_sp<GraphicsPipeline>* p) {
     *     PipelineStats* stats = static_cast<PipelineStats*>(context);
     *
     *     if ((*p)->fromPrecompile() && !(*p)->wasUsed()) {
     *         ++stats->fPurgedUnusedPrecompiledPipelines;
     *     }
     *
     * #if defined(SK_PIPELINE_LIFETIME_LOGGING)
     *     // A "Bad" Purge is one where the Pipeline was never retrieved from the Cache (i.e., unused
     *     // overgeneration).
     *     static const char* kNames[2][2] = { { "BadPurgedN", "BadPurgedP" },
     *                                         { "PurgedN",    "PurgedP"} };
     *
     *     TRACE_EVENT_INSTANT2("skia.gpu",
     *                          TRACE_STR_STATIC(kNames[(*p)->wasUsed()][(*p)->fromPrecompile()]),
     *                          TRACE_EVENT_SCOPE_THREAD,
     *                          "key", key.hash(),
     *                          "compilationID", (*p)->getPipelineInfo().fCompilationID);
     * #endif
     * }
     * ```
     */
    private fun logPurge(
      context: Unit?,
      key: UniqueKey,
      p: SkSp<GraphicsPipeline>?,
    ) {
      TODO("Implement logPurge")
    }
  }
}
