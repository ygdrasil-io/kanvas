package org.skia.modules

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct is_bitmask_enum<skia::textlayout::TextLine::TextAdjustment> : std::true_type {}
 * ```
 */
public open class IsBitmaskEnum : TextLine.TextAdjustment(), Boolean
