package org.skia.gpu

import StdSteadyClock.time_point
import kotlin.Int
import org.skia.core.SkTraceMemoryDump

/**
 * C++ original:
 * ```cpp
 * class ThreadSafeResourceProvider {
 * public:
 *     ThreadSafeResourceProvider(std::unique_ptr<ResourceProvider>);
 *
 *     sk_sp<Sampler> findOrCreateCompatibleSampler(const SamplerDesc&) SK_EXCLUDES(fSpinLock);
 *
 * #if defined(SK_DEBUG)
 *     size_t getResourceCacheLimit() const SK_EXCLUDES(fSpinLock);
 *     size_t getResourceCacheCurrentBudgetedBytes() const SK_EXCLUDES(fSpinLock);
 *     size_t getResourceCacheCurrentPurgeableBytes() const SK_EXCLUDES(fSpinLock);
 * #endif
 *
 *     void dumpMemoryStatistics(SkTraceMemoryDump*) const SK_EXCLUDES(fSpinLock);
 *     void freeGpuResources() SK_EXCLUDES(fSpinLock);
 *     void purgeResourcesNotUsedSince(StdSteadyClock::time_point purgeTime) SK_EXCLUDES(fSpinLock);
 *     void forceProcessReturnedResources() SK_EXCLUDES(fSpinLock);
 *
 * protected:
 *     mutable SkSpinlock fSpinLock;
 *
 *     std::unique_ptr<ResourceProvider> fWrappedProvider SK_GUARDED_BY(fSpinLock);
 * }
 * ```
 */
public open class ThreadSafeResourceProvider public constructor(
  resourceProvider: ResourceProvider?,
) {
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  protected var fSpinLock: Int = TODO("Initialize fSpinLock")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ResourceProvider> fWrappedProvider
   * ```
   */
  protected var fWrappedProvider: Int = TODO("Initialize fWrappedProvider")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<Sampler> ThreadSafeResourceProvider::findOrCreateCompatibleSampler(const SamplerDesc& desc) {
   *     SkAutoSpinlock lock{fSpinLock};
   *
   *     sk_sp<Sampler> sampler = fWrappedProvider->findOrCreateCompatibleSampler(desc);
   *     SkAssertResult(sampler->gpuMemorySize() == 0);
   *     return sampler;
   * }
   * ```
   */
  public fun findOrCreateCompatibleSampler(desc: SamplerDesc): Int {
    TODO("Implement findOrCreateCompatibleSampler")
  }

  /**
   * C++ original:
   * ```cpp
   * void ThreadSafeResourceProvider::dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump) const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     fWrappedProvider->dumpMemoryStatistics(traceMemoryDump);
   * }
   * ```
   */
  public fun dumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * void ThreadSafeResourceProvider::freeGpuResources() {
   *     SkAutoSpinlock lock{fSpinLock};
   *     fWrappedProvider->freeGpuResources();
   * }
   * ```
   */
  public fun freeGpuResources() {
    TODO("Implement freeGpuResources")
  }

  /**
   * C++ original:
   * ```cpp
   * void ThreadSafeResourceProvider::purgeResourcesNotUsedSince(StdSteadyClock::time_point purgeTime) {
   *     SkAutoSpinlock lock{fSpinLock};
   *     fWrappedProvider->purgeResourcesNotUsedSince(purgeTime);
   * }
   * ```
   */
  public fun purgeResourcesNotUsedSince(purgeTime: time_point) {
    TODO("Implement purgeResourcesNotUsedSince")
  }

  /**
   * C++ original:
   * ```cpp
   * void ThreadSafeResourceProvider::forceProcessReturnedResources() {
   *     SkAutoSpinlock lock{fSpinLock};
   *     fWrappedProvider->forceProcessReturnedResources();
   * }
   * ```
   */
  public fun forceProcessReturnedResources() {
    TODO("Implement forceProcessReturnedResources")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t ThreadSafeResourceProvider::getResourceCacheLimit() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     return fWrappedProvider->getResourceCacheLimit();
   * }
   * ```
   */
  public fun getResourceCacheLimit(): Int {
    TODO("Implement getResourceCacheLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t ThreadSafeResourceProvider::getResourceCacheCurrentBudgetedBytes() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     return fWrappedProvider->getResourceCacheCurrentBudgetedBytes();
   * }
   * ```
   */
  public fun getResourceCacheCurrentBudgetedBytes(): Int {
    TODO("Implement getResourceCacheCurrentBudgetedBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t ThreadSafeResourceProvider::getResourceCacheCurrentPurgeableBytes() const {
   *     SkAutoSpinlock lock{fSpinLock};
   *     return fWrappedProvider->getResourceCacheCurrentPurgeableBytes();
   * }
   * ```
   */
  public fun getResourceCacheCurrentPurgeableBytes(): Int {
    TODO("Implement getResourceCacheCurrentPurgeableBytes")
  }
}
