package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.utils.SkEventTracer
import org.skia.utils.SkEventTracerHandle

/**
 * C++ original:
 * ```cpp
 * class SkATrace : public SkEventTracer {
 * public:
 *     SkATrace();
 *
 *     SkEventTracer::Handle addTraceEvent(char phase,
 *                                         const uint8_t* categoryEnabledFlag,
 *                                         const char* name,
 *                                         uint64_t id,
 *                                         int numArgs,
 *                                         const char** argNames,
 *                                         const uint8_t* argTypes,
 *                                         const uint64_t* argValues,
 *                                         uint8_t flags) override;
 *
 *
 *     void updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
 *                                   const char* name,
 *                                   SkEventTracer::Handle handle) override;
 *
 *     const uint8_t* getCategoryGroupEnabled(const char* name) override;
 *
 *     const char* getCategoryGroupName(const uint8_t* categoryEnabledFlag) override {
 *         static const char* const category = "skiaATrace";
 *         return category;
 *     }
 *
 *     // Atrace does not yet support splitting up trace output into sections.
 *     void newTracingSection(const char* name) override {}
 *
 * private:
 *     SkATrace(const SkATrace&) = delete;
 *     SkATrace& operator=(const SkATrace&) = delete;
 *
 *     void (*fBeginSection)(const char*);
 *     void (*fEndSection)(void);
 *     bool (*fIsEnabled)(void);
 * }
 * ```
 */
public open class SkATrace public constructor() : SkEventTracer() {
  /**
   * C++ original:
   * ```cpp
   * void (*fBeginSection)(const char*)
   * ```
   */
  private val fBeginSection: (String?) -> Unit = TODO("Initialize fBeginSection")

  /**
   * C++ original:
   * ```cpp
   * void (*fEndSection)(void)
   * ```
   */
  private var fEndSection: () -> Unit = TODO("Initialize fEndSection")

  /**
   * C++ original:
   * ```cpp
   * bool (*fIsEnabled)(void)
   * ```
   */
  private var fIsEnabled: () -> Boolean = TODO("Initialize fIsEnabled")

  /**
   * C++ original:
   * ```cpp
   * SkATrace::SkATrace() : fBeginSection(nullptr), fEndSection(nullptr), fIsEnabled(nullptr) {
   * #if defined(SK_BUILD_FOR_ANDROID_FRAMEWORK)
   *     fIsEnabled = []{ return static_cast<bool>(CC_UNLIKELY(ATRACE_ENABLED())); };
   *     fBeginSection = [](const char* name){ ATRACE_BEGIN(name); };
   *     fEndSection = []{ ATRACE_END(); };
   * #elif defined(SK_BUILD_FOR_ANDROID)
   *     if (void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL)) {
   *         fBeginSection = (decltype(fBeginSection))dlsym(lib, "ATrace_beginSection");
   *         fEndSection = (decltype(fEndSection))dlsym(lib, "ATrace_endSection");
   *         fIsEnabled = (decltype(fIsEnabled))dlsym(lib, "ATrace_isEnabled");
   *     }
   * #endif
   *
   *     if (!fIsEnabled) {
   *         fIsEnabled = []{ return false; };
   *     }
   * }
   * ```
   */
  public constructor(param0: SkATrace) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEventTracer::Handle SkATrace::addTraceEvent(char phase,
   *                                               const uint8_t* categoryEnabledFlag,
   *                                               const char* name,
   *                                               uint64_t id,
   *                                               int numArgs,
   *                                               const char** argNames,
   *                                               const uint8_t* argTypes,
   *                                               const uint64_t* argValues,
   *                                               uint8_t flags) {
   *     if (fIsEnabled()) {
   *         if (TRACE_EVENT_PHASE_COMPLETE == phase ||
   *             TRACE_EVENT_PHASE_INSTANT == phase) {
   *             fBeginSection(name);
   *         }
   *
   *         if (TRACE_EVENT_PHASE_INSTANT == phase) {
   *             fEndSection();
   *         }
   *     }
   *     return 0;
   * }
   * ```
   */
  public override fun addTraceEvent(
    phase: Char,
    categoryEnabledFlag: UByte?,
    name: String?,
    id: ULong,
    numArgs: Int,
    argNames: Int?,
    argTypes: UByte?,
    argValues: ULong?,
    flags: UByte,
  ): SkEventTracerHandle {
    TODO("Implement addTraceEvent")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkATrace::updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
   *                                         const char* name,
   *                                         SkEventTracer::Handle handle) {
   *     // This is only ever called from a scoped trace event so we will just end the ATrace section.
   *     if (fIsEnabled()) {
   *         fEndSection();
   *     }
   * }
   * ```
   */
  public override fun updateTraceEventDuration(
    categoryEnabledFlag: UByte?,
    name: String?,
    handle: SkEventTracerHandle,
  ) {
    TODO("Implement updateTraceEventDuration")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* SkATrace::getCategoryGroupEnabled(const char* name) {
   *     // Chrome tracing is setup to not repeatly call this function once it has been initialized. So
   *     // we can't use this to do a check for ATrace isEnabled(). Thus we will always return yes here
   *     // and then check to see if ATrace is enabled when beginning and ending a section.
   *     static uint8_t yes = SkEventTracer::kEnabledForRecording_CategoryGroupEnabledFlags;
   *     return &yes;
   * }
   * ```
   */
  public override fun getCategoryGroupEnabled(name: String?): Int {
    TODO("Implement getCategoryGroupEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategoryGroupName(const uint8_t* categoryEnabledFlag) override {
   *         static const char* const category = "skiaATrace";
   *         return category;
   *     }
   * ```
   */
  public override fun getCategoryGroupName(categoryEnabledFlag: UByte?): Char {
    TODO("Implement getCategoryGroupName")
  }

  /**
   * C++ original:
   * ```cpp
   * void newTracingSection(const char* name) override {}
   * ```
   */
  public override fun newTracingSection(name: String?) {
    TODO("Implement newTracingSection")
  }

  /**
   * C++ original:
   * ```cpp
   * SkATrace& operator=(const SkATrace&) = delete
   * ```
   */
  private fun assign(param0: SkATrace) {
    TODO("Implement assign")
  }
}
