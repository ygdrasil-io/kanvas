package org.skia.utils

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SK_API SkEventTracer {
 * public:
 *
 *     typedef uint64_t Handle;
 *
 *     /**
 *      * If this is the first call to SetInstance or GetInstance then the passed instance is
 *      * installed and true is returned. Otherwise, false is returned. In either case ownership of the
 *      * tracer is transferred and it will be deleted when no longer needed.
 *      *
 *      * Not deleting the tracer on process exit should not cause problems as
 *      * the whole heap is about to go away with the process. This can also
 *      * improve performance by reducing the amount of work needed.
 *      *
 *      * @param leakTracer Do not delete tracer on process exit.
 *      */
 *     static bool SetInstance(SkEventTracer*, bool leakTracer = false);
 *
 *     /**
 *      * Gets the event tracer. If this is the first call to SetInstance or GetIntance then a default
 *      * event tracer is installed and returned.
 *      */
 *     static SkEventTracer* GetInstance();
 *
 *     virtual ~SkEventTracer() = default;
 *
 *     // The pointer returned from GetCategoryGroupEnabled() points to a
 *     // value with zero or more of the following bits. Used in this class only.
 *     // The TRACE_EVENT macros should only use the value as a bool.
 *     // These values must be in sync with macro values in trace_event.h in chromium.
 *     enum CategoryGroupEnabledFlags {
 *         // Category group enabled for the recording mode.
 *         kEnabledForRecording_CategoryGroupEnabledFlags = 1 << 0,
 *         // Category group enabled for the monitoring mode.
 *         kEnabledForMonitoring_CategoryGroupEnabledFlags = 1 << 1,
 *         // Category group enabled by SetEventCallbackEnabled().
 *         kEnabledForEventCallback_CategoryGroupEnabledFlags = 1 << 2,
 *     };
 *
 *     virtual const uint8_t* getCategoryGroupEnabled(const char* name) = 0;
 *     virtual const char* getCategoryGroupName(const uint8_t* categoryEnabledFlag) = 0;
 *
 *     virtual SkEventTracer::Handle
 *         addTraceEvent(char phase,
 *                       const uint8_t* categoryEnabledFlag,
 *                       const char* name,
 *                       uint64_t id,
 *                       int32_t numArgs,
 *                       const char** argNames,
 *                       const uint8_t* argTypes,
 *                       const uint64_t* argValues,
 *                       uint8_t flags) = 0;
 *
 *     virtual void
 *         updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
 *                                  const char* name,
 *                                  SkEventTracer::Handle handle) = 0;
 *
 *     // Optional method that can be implemented to allow splitting up traces into different sections.
 *     virtual void newTracingSection(const char*) {}
 *
 * protected:
 *     SkEventTracer() = default;
 *     SkEventTracer(const SkEventTracer&) = delete;
 *     SkEventTracer& operator=(const SkEventTracer&) = delete;
 * }
 * ```
 */
public abstract class SkEventTracer public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkEventTracer() = default
   * ```
   */
  public constructor(param0: SkEventTracer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const uint8_t* getCategoryGroupEnabled(const char* name) = 0
   * ```
   */
  public abstract fun getCategoryGroupEnabled(name: String?): UByte

  /**
   * C++ original:
   * ```cpp
   * virtual const char* getCategoryGroupName(const uint8_t* categoryEnabledFlag) = 0
   * ```
   */
  public abstract fun getCategoryGroupName(categoryEnabledFlag: UByte?): Char

  /**
   * C++ original:
   * ```cpp
   * virtual SkEventTracer::Handle
   *         addTraceEvent(char phase,
   *                       const uint8_t* categoryEnabledFlag,
   *                       const char* name,
   *                       uint64_t id,
   *                       int32_t numArgs,
   *                       const char** argNames,
   *                       const uint8_t* argTypes,
   *                       const uint64_t* argValues,
   *                       uint8_t flags) = 0
   * ```
   */
  public abstract fun addTraceEvent(
    phase: Char,
    categoryEnabledFlag: UByte?,
    name: String?,
    id: ULong,
    numArgs: Int,
    argNames: Int?,
    argTypes: UByte?,
    argValues: ULong?,
    flags: UByte,
  ): SkEventTracer.SkEventTracerHandle

  /**
   * C++ original:
   * ```cpp
   * virtual void
   *         updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
   *                                  const char* name,
   *                                  SkEventTracer::Handle handle) = 0
   * ```
   */
  public abstract fun updateTraceEventDuration(
    categoryEnabledFlag: UByte?,
    name: String?,
    handle: SkEventTracer.SkEventTracerHandle,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void newTracingSection(const char*) {}
   * ```
   */
  public open fun newTracingSection(param0: String?) {
    TODO("Implement newTracingSection")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEventTracer& operator=(const SkEventTracer&) = delete
   * ```
   */
  protected fun assign(param0: SkEventTracer) {
    TODO("Implement assign")
  }

  public enum class CategoryGroupEnabledFlags {
    kEnabledForRecording_CategoryGroupEnabledFlags,
    kEnabledForMonitoring_CategoryGroupEnabledFlags,
    kEnabledForEventCallback_CategoryGroupEnabledFlags,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkEventTracer::SetInstance(SkEventTracer* tracer, bool leakTracer) {
     *     SkEventTracer* expected = nullptr;
     *     if (!gUserTracer.compare_exchange_strong(expected, tracer)) {
     *         delete tracer;
     *         return false;
     *     }
     *     // If leaking the tracer is accepted then there is no need to install
     *     // the atexit.
     *     if (!leakTracer) {
     *         atexit([]() { delete gUserTracer.load(); });
     *     }
     *     return true;
     * }
     * ```
     */
    public fun setInstance(tracer: SkEventTracer?, leakTracer: Boolean = TODO()): Boolean {
      TODO("Implement setInstance")
    }

    /**
     * C++ original:
     * ```cpp
     * SkEventTracer* SkEventTracer::GetInstance() {
     *     if (auto tracer = gUserTracer.load(std::memory_order_acquire)) {
     *         return tracer;
     *     }
     *     static SkDefaultEventTracer* defaultTracer = new SkDefaultEventTracer;
     *     return defaultTracer;
     * }
     * ```
     */
    public fun getInstance(): SkEventTracer {
      TODO("Implement getInstance")
    }
  }
}
