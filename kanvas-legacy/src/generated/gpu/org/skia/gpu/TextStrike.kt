package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrikeSpec
import org.skia.foundation.SkNVRefCnt

/**
 * C++ original:
 * ```cpp
 * class TextStrike : public SkNVRefCnt<TextStrike> {
 * public:
 *     TextStrike(StrikeCache* strikeCache,
 *                const SkStrikeSpec& strikeSpec);
 *
 *     Glyph* getGlyph(SkPackedGlyphID);
 *     const SkStrikeSpec& strikeSpec() const { return fStrikeSpec; }
 *     const SkDescriptor& getDescriptor() const { return fStrikeSpec.descriptor(); }
 *
 * private:
 *     StrikeCache* const fStrikeCache;
 *
 *     // Key for retrieving the SkStrike for creating new atlas data.
 *     const SkStrikeSpec fStrikeSpec;
 *
 *     struct HashTraits {
 *         static const SkPackedGlyphID& GetKey(const Glyph* glyph);
 *         static uint32_t Hash(SkPackedGlyphID key);
 *     };
 *     // Map SkPackedGlyphID -> Glyph*.
 *     skia_private::THashTable<Glyph*, SkPackedGlyphID, HashTraits> fCache;
 *
 *     // Store for the glyph information.
 *     SkArenaAlloc fAlloc{512};
 *
 *     TextStrike*  fNext{nullptr};
 *     TextStrike*  fPrev{nullptr};
 *     size_t       fMemoryUsed{sizeof(TextStrike)};
 *     bool         fRemoved{false};
 *
 *     friend class StrikeCache;
 * }
 * ```
 */
public open class TextStrike public constructor(
  strikeCache: StrikeCache?,
  strikeSpec: SkStrikeSpec,
) : SkNVRefCnt(),
    TextStrike {
  /**
   * C++ original:
   * ```cpp
   * StrikeCache* const fStrikeCache
   * ```
   */
  private val fStrikeCache: StrikeCache? = TODO("Initialize fStrikeCache")

  /**
   * C++ original:
   * ```cpp
   * const SkStrikeSpec fStrikeSpec
   * ```
   */
  private val fStrikeSpec: Int = TODO("Initialize fStrikeSpec")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashTable<Glyph*, SkPackedGlyphID, HashTraits> fCache
   * ```
   */
  private var fCache: Int = TODO("Initialize fCache")

  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc fAlloc
   * ```
   */
  private var fAlloc: Int = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * TextStrike*  fNext{nullptr}
   * ```
   */
  private var fNext: TextStrike? = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * TextStrike*  fPrev{nullptr}
   * ```
   */
  private var fPrev: TextStrike? = TODO("Initialize fPrev")

  /**
   * C++ original:
   * ```cpp
   * size_t       fMemoryUsed
   * ```
   */
  private var fMemoryUsed: Int = TODO("Initialize fMemoryUsed")

  /**
   * C++ original:
   * ```cpp
   * bool         fRemoved{false}
   * ```
   */
  private var fRemoved: Boolean = TODO("Initialize fRemoved")

  /**
   * C++ original:
   * ```cpp
   * Glyph* TextStrike::getGlyph(SkPackedGlyphID packedGlyphID) {
   *     Glyph* glyph = fCache.findOrNull(packedGlyphID);
   *     if (glyph == nullptr) {
   *         glyph = fAlloc.make<Glyph>(packedGlyphID);
   *         fCache.set(glyph);
   *         fMemoryUsed += sizeof(Glyph);
   *         if (!fRemoved) {
   *             fStrikeCache->fTotalMemoryUsed += sizeof(Glyph);
   *         }
   *     }
   *     return glyph;
   * }
   * ```
   */
  public override fun getGlyph(packedGlyphID: SkPackedGlyphID): Glyph {
    TODO("Implement getGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkStrikeSpec& strikeSpec() const { return fStrikeSpec; }
   * ```
   */
  public override fun strikeSpec(): Int {
    TODO("Implement strikeSpec")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDescriptor& getDescriptor() const { return fStrikeSpec.descriptor(); }
   * ```
   */
  public override fun getDescriptor(): Int {
    TODO("Implement getDescriptor")
  }

  public open class HashTraits {
    public companion object {
      public fun getKey(glyph: Glyph?): SkPackedGlyphID {
        TODO("Implement getKey")
      }

      public fun hash(key: SkPackedGlyphID): Int {
        TODO("Implement hash")
      }
    }
  }
}
