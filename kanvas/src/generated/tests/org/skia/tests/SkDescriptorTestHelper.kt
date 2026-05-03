package org.skia.tests

import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkDescriptor

/**
 * C++ original:
 * ```cpp
 * class SkDescriptorTestHelper {
 * public:
 *     static void SetLength(SkDescriptor* desc, size_t length) { desc->fLength = length; }
 *     static void SetCount(SkDescriptor* desc, uint32_t count) { desc->fCount = count; }
 * }
 * ```
 */
public open class SkDescriptorTestHelper {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void SetLength(SkDescriptor* desc, size_t length) { desc->fLength = length; }
     * ```
     */
    public fun setLength(desc: SkDescriptor?, length: ULong) {
      TODO("Implement setLength")
    }

    /**
     * C++ original:
     * ```cpp
     * static void SetCount(SkDescriptor* desc, uint32_t count) { desc->fCount = count; }
     * ```
     */
    public fun setCount(desc: SkDescriptor?, count: UInt) {
      TODO("Implement setCount")
    }
  }
}
