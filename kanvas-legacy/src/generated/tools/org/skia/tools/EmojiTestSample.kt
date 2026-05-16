package org.skia.tools

import kotlin.String
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface

/**
 * C++ original:
 * ```cpp
 * struct EmojiTestSample {
 *     sk_sp<SkTypeface> typeface = nullptr;
 *     const char* sampleText = "";
 * }
 * ```
 */
public data class EmojiTestSample public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> typeface
   * ```
   */
  public var typeface: SkSp<SkTypeface>,
  /**
   * C++ original:
   * ```cpp
   * const char* sampleText = ""
   * ```
   */
  public val sampleText: String?,
)
