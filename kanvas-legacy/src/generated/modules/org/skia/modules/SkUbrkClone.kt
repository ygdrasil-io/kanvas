package org.skia.modules

import kotlin.Any
import org.skia.`external`.UBreakIterator
import org.skia.`external`.UErrorCode

/**
 * C++ original:
 * ```cpp
 * template<typename T, typename = void>
 * struct SkUbrkClone {
 *     static UBreakIterator* clone(T bi, UErrorCode* status) {
 *         return ubrk_safeClone(bi, nullptr, nullptr, status);
 *     }
 * }
 * ```
 */
public open class SkUbrkClone<T, > {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static UBreakIterator* clone(T bi, UErrorCode* status) {
     *         return ubrk_safeClone(bi, nullptr, nullptr, status);
     *     }
     * ```
     */
    private fun clone(bi: Any, status: UErrorCode?): UBreakIterator {
      TODO("Implement clone")
    }
  }
}
