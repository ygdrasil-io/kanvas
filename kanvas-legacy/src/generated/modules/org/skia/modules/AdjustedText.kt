package org.skia.modules

import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class AdjustedText {
 * public:
 *     AdjustedText(const SkString& txt, const Shaper::TextDesc& desc, SkUnicode* unicode)
 *         : fText(txt) {
 *         switch (desc.fCapitalization) {
 *         case Shaper::Capitalization::kNone:
 *             break;
 *         case Shaper::Capitalization::kUpperCase:
 *             if (unicode) {
 *                 fText = unicode->toUpper(fText);
 *             }
 *             break;
 *         }
 *     }
 *
 *     operator const SkString&() const { return fText; }
 *
 * private:
 *     SkString fText;
 * }
 * ```
 */
public data class AdjustedText public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString fText
   * ```
   */
  private var fText: String,
)
