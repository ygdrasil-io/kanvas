package org.skia.gpu

import kotlin.Int
import org.skia.core.SkPackedGlyphID
import org.skia.foundation.SkMask

public typealias Glyph = Glyph

/**
 * C++ original:
 * ```cpp
 * class Glyph {
 * public:
 *     static skgpu::MaskFormat FormatFromSkGlyph(SkMask::Format format) {
 *         switch (format) {
 *             case SkMask::kBW_Format:
 *             case SkMask::kSDF_Format:
 *                 // fall through to kA8 -- we store BW and SDF glyphs in our 8-bit cache
 *             case SkMask::kA8_Format:
 *                 return skgpu::MaskFormat::kA8;
 *             case SkMask::k3D_Format:
 *                 return skgpu::MaskFormat::kA8; // ignore the mul and add planes, just use the mask
 *             case SkMask::kLCD16_Format:
 *                 return skgpu::MaskFormat::kA565;
 *             case SkMask::kARGB32_Format:
 *                 return skgpu::MaskFormat::kARGB;
 *         }
 *
 *         SkUNREACHABLE;
 *     }
 *
 *     explicit Glyph(SkPackedGlyphID packedGlyphID) : fPackedID(packedGlyphID) {}
 *
 *     const SkPackedGlyphID       fPackedID;
 *     skgpu::AtlasLocator         fAtlasLocator;
 * }
 * ```
 */
public open class Glyph public constructor(
  packedGlyphID: SkPackedGlyphID,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkPackedGlyphID       fPackedID
   * ```
   */
  public val fPackedID: Int = TODO("Initialize fPackedID")

  /**
   * C++ original:
   * ```cpp
   * skgpu::AtlasLocator         fAtlasLocator
   * ```
   */
  public var fAtlasLocator: Int = TODO("Initialize fAtlasLocator")

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static skgpu::MaskFormat FormatFromSkGlyph(SkMask::Format format) {
     *         switch (format) {
     *             case SkMask::kBW_Format:
     *             case SkMask::kSDF_Format:
     *                 // fall through to kA8 -- we store BW and SDF glyphs in our 8-bit cache
     *             case SkMask::kA8_Format:
     *                 return skgpu::MaskFormat::kA8;
     *             case SkMask::k3D_Format:
     *                 return skgpu::MaskFormat::kA8; // ignore the mul and add planes, just use the mask
     *             case SkMask::kLCD16_Format:
     *                 return skgpu::MaskFormat::kA565;
     *             case SkMask::kARGB32_Format:
     *                 return skgpu::MaskFormat::kARGB;
     *         }
     *
     *         SkUNREACHABLE;
     *     }
     * ```
     */
    public fun formatFromSkGlyph(format: SkMask.Format): Int {
      TODO("Implement formatFromSkGlyph")
    }
  }
}
