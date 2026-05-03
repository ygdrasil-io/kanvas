package org.skia.utils

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename RawType>
 * struct SkNumericLimits {
 *     static const int digits = 0;
 * }
 * ```
 */
public open class SkNumericLimits<RawType> {
  public companion object {
    private val digits: Int = TODO("Initialize digits")
  }
}
