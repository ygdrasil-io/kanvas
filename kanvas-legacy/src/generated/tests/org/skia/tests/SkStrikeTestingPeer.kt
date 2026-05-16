package org.skia.tests

import org.skia.core.SkGlyph
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrike

/**
 * C++ original:
 * ```cpp
 * class SkStrikeTestingPeer {
 * public:
 *     static SkGlyph* GetGlyph(SkStrike* strike, SkPackedGlyphID packedID) {
 *         SkAutoMutexExclusive m{strike->fStrikeLock};
 *         return strike->glyph(packedID);
 *     }
 * }
 * ```
 */
public open class SkStrikeTestingPeer {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkGlyph* GetGlyph(SkStrike* strike, SkPackedGlyphID packedID) {
     *         SkAutoMutexExclusive m{strike->fStrikeLock};
     *         return strike->glyph(packedID);
     *     }
     * ```
     */
    public fun getGlyph(strike: SkStrike?, packedID: SkPackedGlyphID): SkGlyph {
      TODO("Implement getGlyph")
    }
  }
}
