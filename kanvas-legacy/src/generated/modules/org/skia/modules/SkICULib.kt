package org.skia.modules

import kotlin.Int
import kotlin.String
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SkICULib {
 *     SKICU_EMIT_FUNCS
 *
 *     // ubrk_clone added as draft in ICU69 and Android API 31 (first ICU NDK).
 *     // ubrk_safeClone deprecated in ICU69 and not exposed by Android.
 *     UBreakIterator* (*f_ubrk_clone_)(const UBreakIterator*, UErrorCode*);
 *     UBreakIterator* (*f_ubrk_safeClone_)(const UBreakIterator*, void*, int32_t*, UErrorCode*);
 *
 *     // ubrk_getLocaleByType not exposed by Android.
 *     const char* (*f_ubrk_getLocaleByType)(const UBreakIterator*, ULocDataLocaleType, UErrorCode*);
 * }
 * ```
 */
public data class SkICULib public constructor(
  /**
   * C++ original:
   * ```cpp
   * UBreakIterator* (*f_ubrk_clone_)(const UBreakIterator*, UErrorCode*)
   * ```
   */
  public val fUbrkClone: (Int?, Int?) -> Int?,
  /**
   * C++ original:
   * ```cpp
   * UBreakIterator* (*f_ubrk_safeClone_)(const UBreakIterator*, void*, int32_t*, UErrorCode*)
   * ```
   */
  public val fUbrkSafeClone: (
    Int?,
    Unit?,
    Int?,
    Int?,
  ) -> Int?,
  /**
   * C++ original:
   * ```cpp
   * const char* (*f_ubrk_getLocaleByType)(const UBreakIterator*, ULocDataLocaleType, UErrorCode*)
   * ```
   */
  public val fUbrkGetLocaleByType: (
    Int?,
    Int,
    Int?,
  ) -> String?,
)
