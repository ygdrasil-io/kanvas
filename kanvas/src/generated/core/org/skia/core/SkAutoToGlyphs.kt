package org.skia.core

import kotlin.Int
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSpan
import org.skia.memory.AutoSTArray32

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoToGlyphs {
 * public:
 *     SkAutoToGlyphs(const SkFont& font, const void* text, size_t length, SkTextEncoding encoding) {
 *         if (encoding == SkTextEncoding::kGlyphID || length == 0) {
 *             fGlyphs = {reinterpret_cast<const uint16_t*>(text), length >> 1};
 *         } else {
 *             const size_t count = font.countText(text, length, encoding);
 *             fStorage.reset(count);
 *             SkSpan<SkGlyphID> glyphs = {fStorage.get(), count};
 *             (void)font.textToGlyphs(text, length, encoding, glyphs);
 *             fGlyphs = glyphs;
 *         }
 *     }
 *
 *     size_t size() const { return fGlyphs.size(); }
 *     const SkGlyphID* data() const { return fGlyphs.data(); }
 *
 *     size_t count() const { return fGlyphs.size(); }
 *     SkSpan<const SkGlyphID> glyphs() const { return fGlyphs; }
 *
 * private:
 *     skia_private::AutoSTArray<32, SkGlyphID> fStorage;
 *     SkSpan<const SkGlyphID> fGlyphs;
 * }
 * ```
 */
public data class SkAutoToGlyphs public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTArray<32, SkGlyphID> fStorage
   * ```
   */
  private var fStorage: AutoSTArray32<SkGlyphID>,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyphID> fGlyphs
   * ```
   */
  private val fGlyphs: SkSpan<SkGlyphID>,
) {
  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fGlyphs.size(); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyphID* data() const { return fGlyphs.data(); }
   * ```
   */
  public fun `data`(): SkGlyphID {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t count() const { return fGlyphs.size(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyphID> glyphs() const { return fGlyphs; }
   * ```
   */
  public fun glyphs(): SkSpan<SkGlyphID> {
    TODO("Implement glyphs")
  }
}
