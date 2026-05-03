package org.skia.tests

import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class GlyphVectorTestingPeer {
 * public:
 *     static const SkDescriptor& GetDescriptor(const GlyphVector& v) {
 *         return v.fStrikePromise.descriptor();
 *     }
 *     static SkSpan<GlyphVector::Variant> GetGlyphs(const GlyphVector& v) {
 *         return v.fGlyphs;
 *     }
 * }
 * ```
 */
public open class GlyphVectorTestingPeer {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static const SkDescriptor& GetDescriptor(const GlyphVector& v) {
     *         return v.fStrikePromise.descriptor();
     *     }
     * ```
     */
    public fun getDescriptor(v: GlyphVector): SkDescriptor {
      TODO("Implement getDescriptor")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSpan<GlyphVector::Variant> GetGlyphs(const GlyphVector& v) {
     *         return v.fGlyphs;
     *     }
     * ```
     */
    public fun getGlyphs(v: GlyphVector): SkSpan<GlyphVector.Variant> {
      TODO("Implement getGlyphs")
    }
  }
}
