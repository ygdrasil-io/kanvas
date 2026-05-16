package org.skia.modules

import kotlin.Boolean
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct AttrParseInfo {
 *     SkSVGAttribute fAttr;
 *     bool (*fSetter)(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr, const char* stringValue);
 * }
 * ```
 */
public data class AttrParseInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSVGAttribute fAttr
   * ```
   */
  public var fAttr: SkSVGAttribute,
  /**
   * C++ original:
   * ```cpp
   * bool (*fSetter)(const sk_sp<SkSVGNode>& node, SkSVGAttribute attr, const char* stringValue)
   * ```
   */
  public val fSetter: (
    SkSp<SkSVGNode>,
    SkSVGAttribute,
    String?,
  ) -> Boolean,
)
