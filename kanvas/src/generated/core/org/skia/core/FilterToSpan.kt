package org.skia.core

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct FilterToSpan {
 *     FilterToSpan(const SkImageFilter* filter) : fFilter(sk_ref_sp(filter)) {}
 *
 *     operator SkCanvas::FilterSpan() {
 *         return fFilter ? SkCanvas::FilterSpan{&fFilter, 1} : SkCanvas::FilterSpan{};
 *     }
 *
 *     sk_sp<SkImageFilter> fFilter;
 * }
 * ```
 */
public data class FilterToSpan public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> fFilter
   * ```
   */
  public var fFilter: SkSp<SkImageFilter>,
)
