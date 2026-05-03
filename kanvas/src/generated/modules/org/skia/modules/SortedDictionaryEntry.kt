package org.skia.modules

import kotlin.String

/**
 * C++ original:
 * ```cpp
 * template<typename T>
 * struct SortedDictionaryEntry {
 *     const char* fKey;
 *     const T     fValue;
 * }
 * ```
 */
public data class SortedDictionaryEntry<T> public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fKey
   * ```
   */
  private val fKey: String?,
  /**
   * C++ original:
   * ```cpp
   * const T     fValue
   * ```
   */
  private val fValue: T,
)
