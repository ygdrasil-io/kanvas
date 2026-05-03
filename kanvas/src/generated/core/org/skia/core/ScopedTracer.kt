package org.skia.core

import kotlin.Int
import kotlin.String
import kotlin.UByte
import org.skia.utils.SkEventTracerHandle

/**
 * C++ original:
 * ```cpp
 * class TRACE_EVENT_API_CLASS_EXPORT ScopedTracer {
 *  public:
 *   // Note: members of data_ intentionally left uninitialized. See Initialize.
 *   ScopedTracer() : p_data_(nullptr) {}
 *
 *   ~ScopedTracer() {
 *     if (p_data_ && *data_.category_group_enabled)
 *       TRACE_EVENT_API_UPDATE_TRACE_EVENT_DURATION(
 *           data_.category_group_enabled, data_.name, data_.event_handle);
 *   }
 *
 *   void Initialize(const uint8_t* category_group_enabled,
 *                   const char* name,
 *                   SkEventTracer::Handle event_handle) {
 *     data_.category_group_enabled = category_group_enabled;
 *     data_.name = name;
 *     data_.event_handle = event_handle;
 *     p_data_ = &data_;
 *   }
 *
 *  private:
 *     ScopedTracer(const ScopedTracer&) = delete;
 *     ScopedTracer& operator=(const ScopedTracer&) = delete;
 *
 *   // This Data struct workaround is to avoid initializing all the members in Data during
 *   // construction of this object, since this object is always constructed, even when tracing is
 *   // disabled. If the members of Data were members of this class instead, compiler warnings occur
 *   // about potential uninitialized accesses.
 *   struct Data {
 *     const uint8_t* category_group_enabled;
 *     const char* name;
 *     SkEventTracer::Handle event_handle;
 *   };
 *   Data* p_data_;
 *   Data data_;
 * }
 * ```
 */
public data class ScopedTracer public constructor(
  /**
   * C++ original:
   * ```cpp
   * Data* p_data_
   * ```
   */
  private var pData: Data?,
  /**
   * C++ original:
   * ```cpp
   * Data data_
   * ```
   */
  private var `data`: Data,
) {
  /**
   * C++ original:
   * ```cpp
   * void Initialize(const uint8_t* category_group_enabled,
   *                   const char* name,
   *                   SkEventTracer::Handle event_handle) {
   *     data_.category_group_enabled = category_group_enabled;
   *     data_.name = name;
   *     data_.event_handle = event_handle;
   *     p_data_ = &data_;
   *   }
   * ```
   */
  public fun initialize(
    categoryGroupEnabled: UByte?,
    name: String?,
    eventHandle: SkEventTracerHandle,
  ) {
    TODO("Implement initialize")
  }

  /**
   * C++ original:
   * ```cpp
   * ScopedTracer& operator=(const ScopedTracer&) = delete
   * ```
   */
  private fun assign(param0: ScopedTracer) {
    TODO("Implement assign")
  }

  public open class Data public constructor(
    public val categoryGroupEnabled: Int?,
    public val name: String?,
    public var eventHandle: SkEventTracerHandle,
  )
}
