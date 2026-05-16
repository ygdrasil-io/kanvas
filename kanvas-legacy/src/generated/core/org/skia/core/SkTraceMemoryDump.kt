package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * class SK_API SkTraceMemoryDump {
 * public:
 *     /**
 *      * Enum to specify the level of the requested details for the dump from the Skia objects.
 *      */
 *     enum LevelOfDetail {
 *         // Dump only the minimal details to get the total memory usage (Usually just the totals).
 *         kLight_LevelOfDetail,
 *
 *         // Dump the detailed breakdown of the objects in the caches.
 *         kObjectsBreakdowns_LevelOfDetail
 *     };
 *
 *     /**
 *      *  Appends a new memory dump (i.e. a row) to the trace memory infrastructure.
 *      *  If dumpName does not exist yet, a new one is created. Otherwise, a new column is appended to
 *      *  the previously created dump.
 *      *  Arguments:
 *      *    dumpName: an absolute, slash-separated, name for the item being dumped
 *      *        e.g., "skia/CacheX/EntryY".
 *      *    valueName: a string indicating the name of the column.
 *      *        e.g., "size", "active_size", "number_of_objects".
 *      *        This string is supposed to be long lived and is NOT copied.
 *      *    units: a string indicating the units for the value.
 *      *        e.g., "bytes", "objects".
 *      *        This string is supposed to be long lived and is NOT copied.
 *      *    value: the actual value being dumped.
 *      */
 *     virtual void dumpNumericValue(const char* dumpName,
 *                                   const char* valueName,
 *                                   const char* units,
 *                                   uint64_t value) = 0;
 *
 *     virtual void dumpStringValue(const char* /*dumpName*/,
 *                                  const char* /*valueName*/,
 *                                  const char* /*value*/) { }
 *
 *     /**
 *      * Sets the memory backing for an existing dump.
 *      * backingType and backingObjectId are used by the embedder to associate the memory dumped via
 *      * dumpNumericValue with the corresponding dump that backs the memory.
 *      */
 *     virtual void setMemoryBacking(const char* dumpName,
 *                                   const char* backingType,
 *                                   const char* backingObjectId) = 0;
 *
 *     /**
 *      * Specialization for memory backed by discardable memory.
 *      */
 *     virtual void setDiscardableMemoryBacking(
 *         const char* dumpName,
 *         const SkDiscardableMemory& discardableMemoryObject) = 0;
 *
 *     /**
 *      * Returns the type of details requested in the dump. The granularity of the dump is supposed to
 *      * match the LevelOfDetail argument. The level of detail must not affect the total size
 *      * reported, but only granularity of the child entries.
 *      */
 *     virtual LevelOfDetail getRequestedDetails() const = 0;
 *
 *     /**
 *      * Returns true if we should dump wrapped objects. Wrapped objects come from outside Skia, and
 *      * may be independently tracked there.
 *      */
 *     virtual bool shouldDumpWrappedObjects() const { return true; }
 *
 *     /**
 *      * If shouldDumpWrappedObjects() returns true then this function will be called to populate
 *      * the output with information on whether the item being dumped is a wrapped object.
 *      */
 *     virtual void dumpWrappedState(const char* /*dumpName*/, bool /*isWrappedObject*/) {}
 *
 *     /**
 *      * Returns true if we should dump unbudgeted objects. Unbudgeted objects can either come from
 *      * wrapped objects passed into Skia from the client or from Skia created objects currently held
 *      * by the client in a public Skia object (e.g. SkSurface or SkImage). This call is only used
 *      * when dumping Graphite memory statistics.
 *      */
 *     virtual bool shouldDumpUnbudgetedObjects() const { return true; }
 *
 *     /**
 *      * If shouldDumpUnbudgetedObjects() returns true then this function will be called to populate
 *      * the output with information on whether the item being dumped is budgeted. This call is only
 *      * used when dumping Graphite memory statistics.
 *      */
 *     virtual void dumpBudgetedState(const char* /*dumpName*/, bool /*isBudgeted*/) {}
 *
 *     /**
 *      * Returns true if we should dump sizeless non-texture objects (e.g. Samplers, pipelines, etc).
 *      * Memoryless textures are always dumped. This call is only used when dumping Graphite memory
 *      * statistics.
 *      */
 *     virtual bool shouldDumpSizelessObjects() const { return false; }
 *
 * protected:
 *     virtual ~SkTraceMemoryDump() = default;
 *     SkTraceMemoryDump() = default;
 *     SkTraceMemoryDump(const SkTraceMemoryDump&) = delete;
 *     SkTraceMemoryDump& operator=(const SkTraceMemoryDump&) = delete;
 * }
 * ```
 */
public abstract class SkTraceMemoryDump public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkTraceMemoryDump() = default
   * ```
   */
  public constructor(param0: SkTraceMemoryDump) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void dumpNumericValue(const char* dumpName,
   *                                   const char* valueName,
   *                                   const char* units,
   *                                   uint64_t value) = 0
   * ```
   */
  public abstract fun dumpNumericValue(
    dumpName: String?,
    valueName: String?,
    units: String?,
    `value`: ULong,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void dumpStringValue(const char* /*dumpName*/,
   *                                  const char* /*valueName*/,
   *                                  const char* /*value*/) { }
   * ```
   */
  public open fun dumpStringValue(
    param0: Int,
    param1: Int,
    param2: Int,
  ) {
    TODO("Implement dumpStringValue")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void setMemoryBacking(const char* dumpName,
   *                                   const char* backingType,
   *                                   const char* backingObjectId) = 0
   * ```
   */
  public abstract fun setMemoryBacking(
    dumpName: String?,
    backingType: String?,
    backingObjectId: String?,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void setDiscardableMemoryBacking(
   *         const char* dumpName,
   *         const SkDiscardableMemory& discardableMemoryObject) = 0
   * ```
   */
  public abstract fun setDiscardableMemoryBacking(dumpName: String?, discardableMemoryObject: SkDiscardableMemory)

  /**
   * C++ original:
   * ```cpp
   * virtual LevelOfDetail getRequestedDetails() const = 0
   * ```
   */
  public abstract fun getRequestedDetails(): LevelOfDetail

  /**
   * C++ original:
   * ```cpp
   * virtual bool shouldDumpWrappedObjects() const { return true; }
   * ```
   */
  public open fun shouldDumpWrappedObjects(): Boolean {
    TODO("Implement shouldDumpWrappedObjects")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void dumpWrappedState(const char* /*dumpName*/, bool /*isWrappedObject*/) {}
   * ```
   */
  public open fun dumpWrappedState(param0: Int, param1: Int) {
    TODO("Implement dumpWrappedState")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool shouldDumpUnbudgetedObjects() const { return true; }
   * ```
   */
  public open fun shouldDumpUnbudgetedObjects(): Boolean {
    TODO("Implement shouldDumpUnbudgetedObjects")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void dumpBudgetedState(const char* /*dumpName*/, bool /*isBudgeted*/) {}
   * ```
   */
  public open fun dumpBudgetedState(param0: Int, param1: Int) {
    TODO("Implement dumpBudgetedState")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool shouldDumpSizelessObjects() const { return false; }
   * ```
   */
  public open fun shouldDumpSizelessObjects(): Boolean {
    TODO("Implement shouldDumpSizelessObjects")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTraceMemoryDump& operator=(const SkTraceMemoryDump&) = delete
   * ```
   */
  protected fun assign(param0: SkTraceMemoryDump) {
    TODO("Implement assign")
  }

  public enum class LevelOfDetail {
    kLight_LevelOfDetail,
    kObjectsBreakdowns_LevelOfDetail,
  }
}
