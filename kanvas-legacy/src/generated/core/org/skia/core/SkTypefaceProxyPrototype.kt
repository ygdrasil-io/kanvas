package org.skia.core

import `SkWriteBuffer&buffer`
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkReadBuffer

/**
 * C++ original:
 * ```cpp
 * class SkTypefaceProxyPrototype {
 * public:
 *     static std::optional<SkTypefaceProxyPrototype> MakeFromBuffer(SkReadBuffer& buffer);
 *     explicit SkTypefaceProxyPrototype(const SkTypeface& typeface);
 *     SkTypefaceProxyPrototype(SkTypefaceID typefaceID,
 *                              int glyphCount,
 *                              int32_t styleValue,
 *                              bool isFixedPitch,
 *                              bool glyphMaskNeedsCurrentColor);
 *
 *     void flatten(SkWriteBuffer&buffer) const;
 *     SkTypefaceID serverTypefaceID() const { return fServerTypefaceID; }
 *
 * private:
 *     friend class SkTypefaceProxy;
 *     SkFontStyle style() const {
 *         SkFontStyle style;
 *         style.fValue = fStyleValue;
 *         return style;
 *     }
 *     const SkTypefaceID fServerTypefaceID;
 *     const int fGlyphCount;
 *     const int32_t fStyleValue;
 *     const bool fIsFixedPitch;
 *     // Used for COLRv0 or COLRv1 fonts that may need the 0xFFFF special palette
 *     // index to represent foreground color. This information needs to be on here
 *     // to determine how this typeface can be cached.
 *     const bool fGlyphMaskNeedsCurrentColor;
 * }
 * ```
 */
public data class SkTypefaceProxyPrototype public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkTypefaceID fServerTypefaceID
   * ```
   */
  private val fServerTypefaceID: Int,
  /**
   * C++ original:
   * ```cpp
   * const int fGlyphCount
   * ```
   */
  private val fGlyphCount: Int,
  /**
   * C++ original:
   * ```cpp
   * const int32_t fStyleValue
   * ```
   */
  private val fStyleValue: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool fIsFixedPitch
   * ```
   */
  private val fIsFixedPitch: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const bool fGlyphMaskNeedsCurrentColor
   * ```
   */
  private val fGlyphMaskNeedsCurrentColor: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkTypefaceProxyPrototype::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeUInt(fServerTypefaceID);
   *     buffer.writeInt(fGlyphCount);
   *     buffer.write32(fStyleValue);
   *     buffer.writeBool(fIsFixedPitch);
   *     buffer.writeBool(fGlyphMaskNeedsCurrentColor);
   * }
   * ```
   */
  public fun flatten(buffer: `SkWriteBuffer&buffer`) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID serverTypefaceID() const { return fServerTypefaceID; }
   * ```
   */
  public fun serverTypefaceID(): Int {
    TODO("Implement serverTypefaceID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle style() const {
   *         SkFontStyle style;
   *         style.fValue = fStyleValue;
   *         return style;
   *     }
   * ```
   */
  private fun style(): SkFontStyle {
    TODO("Implement style")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::optional<SkTypefaceProxyPrototype>
     * SkTypefaceProxyPrototype::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkASSERT(buffer.isValid());
     *     const SkTypefaceID typefaceID = buffer.readUInt();
     *     const int glyphCount = buffer.readInt();
     *     const int32_t styleValue = buffer.read32();
     *     const bool isFixedPitch = buffer.readBool();
     *     const bool glyphMaskNeedsCurrentColor = buffer.readBool();
     *
     *     if (buffer.isValid()) {
     *         return SkTypefaceProxyPrototype{
     *             typefaceID, glyphCount, styleValue, isFixedPitch, glyphMaskNeedsCurrentColor};
     *     }
     *
     *     return std::nullopt;
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
