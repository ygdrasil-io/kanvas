package org.skia.modules

import kotlin.Any
import kotlin.Char
import org.skia.`external`.UErrorCode
import org.skia.`external`.ULocDataLocaleType

/**
 * C++ original:
 * ```cpp
 * template<typename T, typename = void>
 * struct SkUbrkGetLocaleByType {
 *     static const char* getLocaleByType(T bi, ULocDataLocaleType type, UErrorCode* status) {
 *         *status = U_UNSUPPORTED_ERROR;
 *         return nullptr;
 *     }
 * }
 * ```
 */
public open class SkUbrkGetLocaleByType<T, > {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static const char* getLocaleByType(T bi, ULocDataLocaleType type, UErrorCode* status) {
     *         *status = U_UNSUPPORTED_ERROR;
     *         return nullptr;
     *     }
     * ```
     */
    private fun getLocaleByType(
      bi: Any,
      type: ULocDataLocaleType,
      status: UErrorCode?,
    ): Char {
      TODO("Implement getLocaleByType")
    }
  }
}
