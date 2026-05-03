package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Short
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import org.skia.foundation.SkMask

/**
 * C++ original:
 * ```cpp
 * class SkGlyphDigest {
 * public:
 *     // An atlas consists of plots, and plots hold glyphs. The minimum a plot can be is 256x256.
 *     // This means that the maximum size a glyph can be is 256x256.
 *     static constexpr uint16_t kSkSideTooBigForAtlas = 256;
 *
 *     // Default ctor is only needed for the hash table.
 *     SkGlyphDigest() = default;
 *     SkGlyphDigest(size_t index, const SkGlyph& glyph);
 *     int index()          const { return fIndex; }
 *     bool isEmpty()       const { return fIsEmpty; }
 *     bool isColor()       const { return fFormat == SkMask::kARGB32_Format; }
 *     SkMask::Format maskFormat() const { return static_cast<SkMask::Format>(fFormat); }
 *
 *     skglyph::GlyphAction actionFor(skglyph::ActionType actionType) const {
 *         return static_cast<skglyph::GlyphAction>((fActions >> actionType) & 0b11);
 *     }
 *
 *     void setActionFor(skglyph::ActionType, SkGlyph*, sktext::StrikeForGPU*);
 *
 *     uint16_t maxDimension() const {
 *         return std::max(fWidth, fHeight);
 *     }
 *
 *     bool fitsInAtlasDirect() const {
 *         return this->maxDimension() <= kSkSideTooBigForAtlas;
 *     }
 *
 *     bool fitsInAtlasInterpolated() const {
 *         // Include the padding needed for interpolating the glyph when drawing.
 *         return this->maxDimension() <= kSkSideTooBigForAtlas - 2;
 *     }
 *
 *     SkGlyphRect bounds() const {
 *         return SkGlyphRect(fLeft, fTop, (SkScalar)fLeft + fWidth, (SkScalar)fTop + fHeight);
 *     }
 *
 *     static bool FitsInAtlas(const SkGlyph& glyph);
 *
 *     // GetKey and Hash implement the required methods for THashTable.
 *     static SkPackedGlyphID GetKey(SkGlyphDigest digest) {
 *         return SkPackedGlyphID{SkTo<uint32_t>(digest.fPackedID)};
 *     }
 *     static uint32_t Hash(SkPackedGlyphID packedID) {
 *         return packedID.hash();
 *     }
 *     static bool ShouldGrow(int count, int capacity) {
 *         // Having the 50% load factor results in performance improvements and significantly reduces
 *         // the average number of probes on the Speedometer3 Editor-TipTap benchmark.
 *         return 2 * count >= capacity;
 *     }
 *     static bool ShouldShrink(int count, int capacity) {
 *         // Use 1/6 as the minimal load.
 *         return 6 * count <= capacity;
 *     }
 *
 * private:
 *     void setAction(skglyph::ActionType actionType, skglyph::GlyphAction action) {
 *         using namespace skglyph;
 *         SkASSERT(action != GlyphAction::kUnset);
 *         SkASSERT(this->actionFor(actionType) == GlyphAction::kUnset);
 *         const uint64_t mask = 0b11 << actionType;
 *         fActions &= ~mask;
 *         fActions |= SkTo<uint64_t>(action) << actionType;
 *     }
 *
 *     static_assert(SkPackedGlyphID::kEndData == 20);
 *     static_assert(SkMask::kCountMaskFormats <= 8);
 *     static_assert(SkTo<int>(skglyph::GlyphAction::kSize) <= 4);
 *     struct {
 *         uint64_t fPackedID : SkPackedGlyphID::kEndData;
 *         uint64_t fIndex    : SkPackedGlyphID::kEndData;
 *         uint64_t fIsEmpty  : 1;
 *         uint64_t fFormat   : 3;
 *         uint64_t fActions  : skglyph::ActionTypeSize::kTotalBits;
 *     };
 *     int16_t fLeft, fTop;
 *     uint16_t fWidth, fHeight;
 * }
 * ```
 */
public data class SkGlyphDigest public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr uint16_t kSkSideTooBigForAtlas = 256
   * ```
   */
  private var fLeft: Short,
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft
   * ```
   */
  private var fTop: Short,
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft, fTop
   * ```
   */
  private var fWidth: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fWidth
   * ```
   */
  private var fHeight: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t fWidth, fHeight
   * ```
   */
  public var fPackedID: ULong,
  public var fIndex: ULong,
  public var fIsEmpty: ULong,
  public var fFormat: ULong,
  public var fActions: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * int index()          const { return fIndex; }
   * ```
   */
  public fun index(): Int {
    TODO("Implement index")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty()       const { return fIsEmpty; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isColor()       const { return fFormat == SkMask::kARGB32_Format; }
   * ```
   */
  public fun isColor(): Boolean {
    TODO("Implement isColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format maskFormat() const { return static_cast<SkMask::Format>(fFormat); }
   * ```
   */
  public fun maskFormat(): SkMask.Format {
    TODO("Implement maskFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * skglyph::GlyphAction actionFor(skglyph::ActionType actionType) const {
   *         return static_cast<skglyph::GlyphAction>((fActions >> actionType) & 0b11);
   *     }
   * ```
   */
  public fun actionFor(actionType: ActionType): GlyphAction {
    TODO("Implement actionFor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkGlyphDigest::setActionFor(skglyph::ActionType actionType,
   *                                  SkGlyph* glyph,
   *                                  StrikeForGPU* strike) {
   *     // We don't have to do any more if the glyph is marked as kDrop because it was isEmpty().
   *     if (this->actionFor(actionType) == GlyphAction::kUnset) {
   *         GlyphAction action = GlyphAction::kReject;
   *         switch (actionType) {
   *             case kDirectMask: {
   *                 if (this->fitsInAtlasDirect()) {
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *             case kDirectMaskCPU: {
   *                 if (strike->prepareForImage(glyph)) {
   *                     SkASSERT(!glyph->isEmpty());
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *             case kMask: {
   *                 if (this->fitsInAtlasInterpolated()) {
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *             case kSDFT: {
   *                 if (this->fitsInAtlasDirect() &&
   *                     this->maskFormat() == SkMask::Format::kSDF_Format) {
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *             case kPath: {
   *                 if (strike->prepareForPath(glyph)) {
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *             case kDrawable: {
   *                 if (strike->prepareForDrawable(glyph)) {
   *                     action = GlyphAction::kAccept;
   *                 }
   *                 break;
   *             }
   *         }
   *         this->setAction(actionType, action);
   *     }
   * }
   * ```
   */
  public fun setActionFor(
    actionType: ActionType,
    glyph: SkGlyph?,
    strike: StrikeForGPU?,
  ) {
    TODO("Implement setActionFor")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t maxDimension() const {
   *         return std::max(fWidth, fHeight);
   *     }
   * ```
   */
  public fun maxDimension(): UShort {
    TODO("Implement maxDimension")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fitsInAtlasDirect() const {
   *         return this->maxDimension() <= kSkSideTooBigForAtlas;
   *     }
   * ```
   */
  public fun fitsInAtlasDirect(): Boolean {
    TODO("Implement fitsInAtlasDirect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fitsInAtlasInterpolated() const {
   *         // Include the padding needed for interpolating the glyph when drawing.
   *         return this->maxDimension() <= kSkSideTooBigForAtlas - 2;
   *     }
   * ```
   */
  public fun fitsInAtlasInterpolated(): Boolean {
    TODO("Implement fitsInAtlasInterpolated")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect bounds() const {
   *         return SkGlyphRect(fLeft, fTop, (SkScalar)fLeft + fWidth, (SkScalar)fTop + fHeight);
   *     }
   * ```
   */
  public fun bounds(): SkGlyphRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAction(skglyph::ActionType actionType, skglyph::GlyphAction action) {
   *         using namespace skglyph;
   *         SkASSERT(action != GlyphAction::kUnset);
   *         SkASSERT(this->actionFor(actionType) == GlyphAction::kUnset);
   *         const uint64_t mask = 0b11 << actionType;
   *         fActions &= ~mask;
   *         fActions |= SkTo<uint64_t>(action) << actionType;
   *     }
   * ```
   */
  private fun setAction(actionType: ActionType, action: GlyphAction) {
    TODO("Implement setAction")
  }

  public companion object {
    public val kSkSideTooBigForAtlas: UShort = TODO("Initialize kSkSideTooBigForAtlas")

    /**
     * C++ original:
     * ```cpp
     * bool SkGlyphDigest::FitsInAtlas(const SkGlyph& glyph) {
     *     return glyph.maxDimension() <= kSkSideTooBigForAtlas;
     * }
     * ```
     */
    public fun fitsInAtlas(glyph: SkGlyph): Boolean {
      TODO("Implement fitsInAtlas")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPackedGlyphID GetKey(SkGlyphDigest digest) {
     *         return SkPackedGlyphID{SkTo<uint32_t>(digest.fPackedID)};
     *     }
     * ```
     */
    public fun getKey(digest: SkGlyphDigest): SkPackedGlyphID {
      TODO("Implement getKey")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t Hash(SkPackedGlyphID packedID) {
     *         return packedID.hash();
     *     }
     * ```
     */
    public fun hash(packedID: SkPackedGlyphID): UInt {
      TODO("Implement hash")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool ShouldGrow(int count, int capacity) {
     *         // Having the 50% load factor results in performance improvements and significantly reduces
     *         // the average number of probes on the Speedometer3 Editor-TipTap benchmark.
     *         return 2 * count >= capacity;
     *     }
     * ```
     */
    public fun shouldGrow(count: Int, capacity: Int): Boolean {
      TODO("Implement shouldGrow")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool ShouldShrink(int count, int capacity) {
     *         // Use 1/6 as the minimal load.
     *         return 6 * count <= capacity;
     *     }
     * ```
     */
    public fun shouldShrink(count: Int, capacity: Int): Boolean {
      TODO("Implement shouldShrink")
    }
  }
}
