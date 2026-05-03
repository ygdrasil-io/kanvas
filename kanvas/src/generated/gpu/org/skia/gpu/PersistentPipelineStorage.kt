package org.skia.gpu

import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API PersistentPipelineStorage {
 * public:
 *     virtual ~PersistentPipelineStorage() = default;
 *
 *     /**
 *      * Should return the data that had been previously stored. It should return null if there
 *      * is no prior data.
 *      */
 *     virtual sk_sp<SkData> load() = 0;
 *
 *     /**
 *      * Should persist the provided Pipeline data.
 *      */
 *     virtual void store(const SkData& data) = 0;
 *
 * protected:
 *     PersistentPipelineStorage() = default;
 *     PersistentPipelineStorage(const PersistentPipelineStorage&) = delete;
 *     PersistentPipelineStorage& operator=(const PersistentPipelineStorage&) = delete;
 * }
 * ```
 */
public abstract class PersistentPipelineStorage public constructor() {
  /**
   * C++ original:
   * ```cpp
   * PersistentPipelineStorage() = default
   * ```
   */
  public constructor(param0: PersistentPipelineStorage) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkData> load() = 0
   * ```
   */
  public abstract fun load(): SkSp<SkData>

  /**
   * C++ original:
   * ```cpp
   * virtual void store(const SkData& data) = 0
   * ```
   */
  public abstract fun store(`data`: SkData)

  /**
   * C++ original:
   * ```cpp
   * PersistentPipelineStorage& operator=(const PersistentPipelineStorage&) = delete
   * ```
   */
  protected fun assign(param0: PersistentPipelineStorage) {
    TODO("Implement assign")
  }
}
