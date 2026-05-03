package org.skia.tools

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import kotlin.collections.List
import org.skia.core.SkSpinlock
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.gpu.ContextOptions

/**
 * C++ original:
 * ```cpp
 * class PipelineCallBackHandler {
 * public:
 *     static void CallBack(void* context,
 *                          skgpu::graphite::ContextOptions::PipelineCacheOp op,
 *                          const std::string& label,
 *                          uint32_t uniqueKeyHash,
 *                          bool fromPrecompile,
 *                          sk_sp<SkData> androidStyleKey) {
 *         PipelineCallBackHandler* handler = reinterpret_cast<PipelineCallBackHandler*>(context);
 *
 *         handler->add(op, label, uniqueKeyHash, fromPrecompile, std::move(androidStyleKey));
 *     }
 *
 *     void add(skgpu::graphite::ContextOptions::PipelineCacheOp op,
 *              const std::string& label,
 *              uint32_t uniqueKeyHash,
 *              bool fromPrecompile,
 *              sk_sp<SkData> androidStyleKey) SK_EXCLUDES(fSpinLock);
 *
 *     void retrieveKeys(std::vector<sk_sp<SkData>>* result) SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{ fSpinLock };
 *
 *         result->reserve(fMap.count());
 *
 *         fMap.foreach([result](std::unique_ptr<PipelineData>* data) {
 *             // Because not all Pipelines are serializable we need to check for nulls here
 *             if ((*data)->fAndroidStyleKey) {
 *                 result->push_back((*data)->fAndroidStyleKey);
 *             }
 *         });
 *     }
 *
 *     void reset() SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{ fSpinLock };
 *
 *         fMap.reset();
 *     }
 *
 *     void report() SK_EXCLUDES(fSpinLock);
 *
 * private:
 *     mutable SkSpinlock fSpinLock;
 *
 *     struct PipelineData {
 *         PipelineData(const std::string& label,
 *                      uint32_t uniqueKeyHash,
 *                      bool fromPrecompile,
 *                      sk_sp<SkData> androidStyleKey) :
 *                fLabel(label),
 *                fAndroidStyleKey(std::move(androidStyleKey)),
 *                fUniqueKeyHash(uniqueKeyHash),
 *                fUses(fromPrecompile ? 0 : 1),
 *                fFromPrecompile(fromPrecompile) {
 *         }
 *
 *         std::string   fLabel;
 *         sk_sp<SkData> fAndroidStyleKey;
 *         uint32_t      fUniqueKeyHash;
 *         uint32_t      fUses;
 *         bool          fFromPrecompile;
 *     };
 *
 *     struct PipelineKey {
 *         const std::string* fLabel;
 *         uint32_t fUniqueKeyHash;
 *
 *         static PipelineKey GetKey(const std::unique_ptr<PipelineData>& v) {
 *             return { &v->fLabel, v->fUniqueKeyHash };
 *         }
 *         static uint32_t Hash(const PipelineKey& k) { return k.fUniqueKeyHash; }
 *
 *         bool operator==(const PipelineKey& other) const {
 *             return fUniqueKeyHash == other.fUniqueKeyHash && *fLabel == *other.fLabel;
 *         }
 *     };
 *
 *     using Map = skia_private::THashTable<std::unique_ptr<PipelineData>, PipelineKey, PipelineKey>;
 *     Map fMap SK_GUARDED_BY(fSpinLock);
 * }
 * ```
 */
public data class PipelineCallBackHandler public constructor(
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fSpinLock: SkSpinlock,
  /**
   * C++ original:
   * ```cpp
   * Map fMap
   * ```
   */
  private var fMap: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void PipelineCallBackHandler::add(skgpu::graphite::ContextOptions::PipelineCacheOp op,
   *                                   const std::string& label,
   *                                   uint32_t uniqueKeyHash,
   *                                   bool fromPrecompile,
   *                                   sk_sp<SkData> androidStyleKey) {
   *     SkAutoSpinlock lock{ fSpinLock };
   *
   *     std::unique_ptr<PipelineData>* foundData = fMap.find({ &label, uniqueKeyHash });
   *     if (foundData) {
   *         if (op == skgpu::graphite::ContextOptions::PipelineCacheOp::kPipelineFound) {
   *             (*foundData)->fUses++;
   *         }
   *     } else {
   *         SkASSERT(op == skgpu::graphite::ContextOptions::PipelineCacheOp::kAddingPipeline);
   *
   *         std::unique_ptr<PipelineData> newData = std::make_unique<PipelineData>(
   *             label, uniqueKeyHash, fromPrecompile, std::move(androidStyleKey));
   *
   *         fMap.set(std::move(newData));
   *     }
   * }
   * ```
   */
  public fun add(
    op: ContextOptions.PipelineCacheOp,
    label: String,
    uniqueKeyHash: UInt,
    fromPrecompile: Boolean,
    androidStyleKey: SkSp<SkData>,
  ) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void retrieveKeys(std::vector<sk_sp<SkData>>* result) SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{ fSpinLock };
   *
   *         result->reserve(fMap.count());
   *
   *         fMap.foreach([result](std::unique_ptr<PipelineData>* data) {
   *             // Because not all Pipelines are serializable we need to check for nulls here
   *             if ((*data)->fAndroidStyleKey) {
   *                 result->push_back((*data)->fAndroidStyleKey);
   *             }
   *         });
   *     }
   * ```
   */
  public fun retrieveKeys(result: List<SkSp<SkData>>?) {
    TODO("Implement retrieveKeys")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{ fSpinLock };
   *
   *         fMap.reset();
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void PipelineCallBackHandler::report() {
   *     // The assumption is that we're just doing this once at the end so we just lock for the
   *     // entire method.
   *     SkAutoSpinlock lock{ fSpinLock };
   *
   *     std::vector<const PipelineData*> tmp;
   *
   *     tmp.reserve(fMap.count());
   *     fMap.foreach([&tmp](std::unique_ptr<PipelineData>* data) -> void {
   *         tmp.push_back((*data).get());
   *     });
   *
   *     std::sort(tmp.begin(), tmp.end(), [](const PipelineData* a, const PipelineData* b) {
   *                                             if (a->fUses != b->fUses) {
   *                                                 return a->fUses > b->fUses;
   *                                             }
   *                                             return a->fLabel < b->fLabel;
   *                                         });
   *
   *     for (const PipelineData* data : tmp) {
   *         if (data->fFromPrecompile && !data->fUses) {
   *             SkDebugf("!! ");   // A needless precompiled Pipeline
   *         }
   *         SkDebugf("%u %s\n", data->fUses, data->fLabel.c_str());
   *     }
   * }
   * ```
   */
  public fun report() {
    TODO("Implement report")
  }

  public data class PipelineData public constructor(
    public var fLabel: Int,
    public var fAndroidStyleKey: SkSp<SkData>,
    public var fUniqueKeyHash: Int,
    public var fUses: Int,
    public var fFromPrecompile: Boolean,
  )

  public data class PipelineKey public constructor(
    public val fLabel: Int?,
    public var fUniqueKeyHash: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public companion object {
      public fun getKey(v: PipelineData?): PipelineKey {
        TODO("Implement getKey")
      }

      public fun hash(k: PipelineKey): Int {
        TODO("Implement hash")
      }
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void CallBack(void* context,
     *                          skgpu::graphite::ContextOptions::PipelineCacheOp op,
     *                          const std::string& label,
     *                          uint32_t uniqueKeyHash,
     *                          bool fromPrecompile,
     *                          sk_sp<SkData> androidStyleKey) {
     *         PipelineCallBackHandler* handler = reinterpret_cast<PipelineCallBackHandler*>(context);
     *
     *         handler->add(op, label, uniqueKeyHash, fromPrecompile, std::move(androidStyleKey));
     *     }
     * ```
     */
    public fun callBack(
      context: Unit?,
      op: ContextOptions.PipelineCacheOp,
      label: String,
      uniqueKeyHash: UInt,
      fromPrecompile: Boolean,
      androidStyleKey: SkSp<SkData>,
    ) {
      TODO("Implement callBack")
    }
  }
}
