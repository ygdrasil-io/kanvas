package org.skia.utils

import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SkDefaultEventTracer : public SkEventTracer {
 *     SkEventTracer::Handle
 *         addTraceEvent(char phase,
 *                       const uint8_t* categoryEnabledFlag,
 *                       const char* name,
 *                       uint64_t id,
 *                       int numArgs,
 *                       const char** argNames,
 *                       const uint8_t* argTypes,
 *                       const uint64_t* argValues,
 *                       uint8_t flags) override { return 0; }
 *
 *     void
 *         updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
 *                                  const char* name,
 *                                  SkEventTracer::Handle handle) override {}
 *
 *     const uint8_t* getCategoryGroupEnabled(const char* name) override {
 *         static uint8_t no = 0;
 *         return &no;
 *     }
 *     const char* getCategoryGroupName(
 *       const uint8_t* categoryEnabledFlag) override {
 *         static const char* stub = "stub";
 *         return stub;
 *     }
 *
 *     // The default tracer does not yet support splitting up trace output into sections.
 *     void newTracingSection(const char* name) override {}
 * }
 * ```
 */
public open class SkDefaultEventTracer : SkEventTracer() {
  /**
   * C++ original:
   * ```cpp
   * SkEventTracer::Handle
   *         addTraceEvent(char phase,
   *                       const uint8_t* categoryEnabledFlag,
   *                       const char* name,
   *                       uint64_t id,
   *                       int numArgs,
   *                       const char** argNames,
   *                       const uint8_t* argTypes,
   *                       const uint64_t* argValues,
   *                       uint8_t flags) override { return 0; }
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
   * void
   *         updateTraceEventDuration(const uint8_t* categoryEnabledFlag,
   *                                  const char* name,
   *                                  SkEventTracer::Handle handle) override {}
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
   * const uint8_t* getCategoryGroupEnabled(const char* name) override {
   *         static uint8_t no = 0;
   *         return &no;
   *     }
   * ```
   */
  public override fun getCategoryGroupEnabled(name: String?): Int {
    TODO("Implement getCategoryGroupEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategoryGroupName(
   *       const uint8_t* categoryEnabledFlag) override {
   *         static const char* stub = "stub";
   *         return stub;
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
}
