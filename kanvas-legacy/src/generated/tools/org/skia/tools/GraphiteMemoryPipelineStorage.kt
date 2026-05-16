package org.skia.tools

import kotlin.Int
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.gpu.PersistentPipelineStorage

/**
 * C++ original:
 * ```cpp
 * class GraphiteMemoryPipelineStorage : public skgpu::graphite::PersistentPipelineStorage {
 * public:
 *     GraphiteMemoryPipelineStorage() = default;
 *     GraphiteMemoryPipelineStorage(const GraphiteMemoryPipelineStorage&) = delete;
 *     GraphiteMemoryPipelineStorage& operator=(const GraphiteMemoryPipelineStorage&) = delete;
 *
 *     sk_sp<SkData> load() override;
 *     void store(const SkData& data) override;
 *
 *     int numLoads() const { return fLoadCount; }
 *     int numStores() const { return fStoreCount; }
 *     void resetCacheStats() {
 *         fLoadCount = 0;
 *         fStoreCount = 0;
 *     }
 *     void reset() {
 *         this->resetCacheStats();
 *         fData.reset();
 *     }
 *
 * private:
 *     int fLoadCount = 0;
 *     int fStoreCount = 0;
 *     sk_sp<SkData> fData;
 * }
 * ```
 */
public open class GraphiteMemoryPipelineStorage public constructor() : PersistentPipelineStorage() {
  /**
   * C++ original:
   * ```cpp
   * int fLoadCount = 0
   * ```
   */
  private var fLoadCount: Int = TODO("Initialize fLoadCount")

  /**
   * C++ original:
   * ```cpp
   * int fStoreCount = 0
   * ```
   */
  private var fStoreCount: Int = TODO("Initialize fStoreCount")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  private var fData: SkSp<SkData> = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * GraphiteMemoryPipelineStorage() = default
   * ```
   */
  public constructor(param0: GraphiteMemoryPipelineStorage) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * GraphiteMemoryPipelineStorage& operator=(const GraphiteMemoryPipelineStorage&) = delete
   * ```
   */
  public fun assign(param0: GraphiteMemoryPipelineStorage) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> GraphiteMemoryPipelineStorage::load() {
   *     if (!fData) {
   * #if defined(LOG_MEMORY_CACHE)
   *         SkDebugf("No data to load\n");
   * #endif
   *         return nullptr;
   *     }
   *
   * #if defined(LOG_MEMORY_CACHE)
   *     SkDebugf("Loading data: %zu %s\n", fData->size(), data_to_str(*fData).c_str());
   * #endif
   *
   *     ++fLoadCount;
   *     return fData;
   * }
   * ```
   */
  public override fun load(): SkSp<SkData> {
    TODO("Implement load")
  }

  /**
   * C++ original:
   * ```cpp
   * void GraphiteMemoryPipelineStorage::store(const SkData& data) {
   * #if defined(LOG_MEMORY_CACHE)
   *     SkDebugf("Storing data: %zu %s\n", data.size(), data_to_str(data).c_str());
   * #endif
   *
   *     ++fStoreCount;
   *     fData = SkData::MakeWithCopy(data.data(), data.size());
   * }
   * ```
   */
  public override fun store(`data`: SkData) {
    TODO("Implement store")
  }

  /**
   * C++ original:
   * ```cpp
   * int numLoads() const { return fLoadCount; }
   * ```
   */
  public fun numLoads(): Int {
    TODO("Implement numLoads")
  }

  /**
   * C++ original:
   * ```cpp
   * int numStores() const { return fStoreCount; }
   * ```
   */
  public fun numStores(): Int {
    TODO("Implement numStores")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCacheStats() {
   *         fLoadCount = 0;
   *         fStoreCount = 0;
   *     }
   * ```
   */
  public fun resetCacheStats() {
    TODO("Implement resetCacheStats")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         this->resetCacheStats();
   *         fData.reset();
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }
}
