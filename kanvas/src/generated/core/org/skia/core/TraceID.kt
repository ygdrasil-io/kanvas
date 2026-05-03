package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TraceID {
 * public:
 *     TraceID(const void* id, unsigned char* flags)
 *             : data_(static_cast<uint64_t>(reinterpret_cast<uintptr_t>(id))) {
 *         *flags |= TRACE_EVENT_FLAG_MANGLE_ID;
 *     }
 *     TraceID(uint64_t id, unsigned char* flags)
 *         : data_(id) { (void)flags; }
 *     TraceID(unsigned int id, unsigned char* flags)
 *         : data_(id) { (void)flags; }
 *     TraceID(unsigned short id, unsigned char* flags)
 *         : data_(id) { (void)flags; }
 *     TraceID(unsigned char id, unsigned char* flags)
 *         : data_(id) { (void)flags; }
 *     TraceID(long long id, unsigned char* flags)
 *         : data_(static_cast<uint64_t>(id)) { (void)flags; }
 *     TraceID(long id, unsigned char* flags)
 *         : data_(static_cast<uint64_t>(id)) { (void)flags; }
 *     TraceID(int id, unsigned char* flags)
 *         : data_(static_cast<uint64_t>(id)) { (void)flags; }
 *     TraceID(short id, unsigned char* flags)
 *         : data_(static_cast<uint64_t>(id)) { (void)flags; }
 *     TraceID(signed char id, unsigned char* flags)
 *         : data_(static_cast<uint64_t>(id)) { (void)flags; }
 *
 *     uint64_t data() const { return data_; }
 *
 * private:
 *     uint64_t data_;
 * }
 * ```
 */
public data class TraceID public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint64_t data_
   * ```
   */
  private var `data`: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * uint64_t data() const { return data_; }
   * ```
   */
  public fun `data`(): Int {
    TODO("Implement data")
  }
}
