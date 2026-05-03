package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.ActionType
import org.skia.core.SkGlyph
import org.skia.core.SkGlyphDigest
import org.skia.core.SkGlyphPositionRoundingSpec
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrikePromise
import org.skia.core.SkStrikeSpec
import org.skia.core.StrikeForGPU
import org.skia.core.THashTable
import org.skia.foundation.SkAutoDescriptor
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkScalerContext
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkWriteBuffer
import org.skia.memory.SkArenaAllocWithReset
import org.skia.utils.SkDiscardableHandleId

/**
 * C++ original:
 * ```cpp
 * class RemoteStrike final : public sktext::StrikeForGPU {
 * public:
 *     // N.B. RemoteStrike is not valid until ensureScalerContext is called.
 *     RemoteStrike(const SkStrikeSpec& strikeSpec,
 *                  std::unique_ptr<SkScalerContext> context,
 *                  SkDiscardableHandleId discardableHandleId);
 *     ~RemoteStrike() override = default;
 *
 *     void lock() override {}
 *     void unlock() override {}
 *     SkGlyphDigest digestFor(skglyph::ActionType, SkPackedGlyphID) override;
 *     bool prepareForImage(SkGlyph* glyph) override {
 *         this->ensureScalerContext();
 *         glyph->setImage(&fAlloc, fContext.get());
 *         return glyph->image() != nullptr;
 *     }
 *     bool prepareForPath(SkGlyph* glyph) override {
 *         this->ensureScalerContext();
 *         glyph->setPath(&fAlloc, fContext.get());
 *         return glyph->path() != nullptr;
 *     }
 *     bool prepareForDrawable(SkGlyph* glyph) override {
 *         this->ensureScalerContext();
 *         glyph->setDrawable(&fAlloc, fContext.get());
 *         return glyph->drawable() != nullptr;
 *     }
 *
 *     void writePendingGlyphs(SkWriteBuffer& buffer);
 *
 *     SkDiscardableHandleId discardableHandleId() const { return fDiscardableHandleId; }
 *
 *     const SkDescriptor& getDescriptor() const override {
 *         return *fDescriptor.getDesc();
 *     }
 *
 *     void setStrikeSpec(const SkStrikeSpec& strikeSpec);
 *
 *     const SkGlyphPositionRoundingSpec& roundingSpec() const override {
 *         return fRoundingSpec;
 *     }
 *
 *     sktext::SkStrikePromise strikePromise() override;
 *
 *     bool hasPendingGlyphs() const {
 *         return !fMasksToSend.empty() || !fPathsToSend.empty() || !fDrawablesToSend.empty();
 *     }
 *
 *     void resetScalerContext();
 *
 * private:
 *     void ensureScalerContext();
 *
 *     const SkAutoDescriptor fDescriptor;
 *     const SkDiscardableHandleId fDiscardableHandleId;
 *
 *     const SkGlyphPositionRoundingSpec fRoundingSpec;
 *
 *     // The context built using fDescriptor
 *     std::unique_ptr<SkScalerContext> fContext;
 *     SkTypefaceID fStrikeSpecTypefaceId;
 *
 *     // fStrikeSpec is set every time getOrCreateCache is called. This allows the code to maintain
 *     // the fContext as lazy as possible.
 *     const SkStrikeSpec* fStrikeSpec;
 *
 *     // Have the metrics been sent for this strike. Only send them once.
 *     bool fHaveSentFontMetrics{false};
 *
 *     // The masks and paths that currently reside in the GPU process.
 *     THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest> fSentGlyphs;
 *
 *     // The Masks, SDFT Mask, and Paths that need to be sent to the GPU task for the processed
 *     // TextBlobs. Cleared after diffs are serialized.
 *     std::vector<SkGlyph> fMasksToSend;
 *     std::vector<SkGlyph> fPathsToSend;
 *     std::vector<SkGlyph> fDrawablesToSend;
 *
 *     // Alloc for storing bits and pieces of paths and drawables, Cleared after diffs are serialized.
 *     SkArenaAllocWithReset fAlloc{256};
 * }
 * ```
 */
public class RemoteStrike public constructor(
  strikeSpec: SkStrikeSpec,
  context: SkScalerContext?,
  discardableHandleId: SkDiscardableHandleId,
) : StrikeForGPU() {
  /**
   * C++ original:
   * ```cpp
   * const SkAutoDescriptor fDescriptor
   * ```
   */
  private val fDescriptor: SkAutoDescriptor = TODO("Initialize fDescriptor")

  /**
   * C++ original:
   * ```cpp
   * const SkDiscardableHandleId fDiscardableHandleId
   * ```
   */
  private val fDiscardableHandleId: Int = TODO("Initialize fDiscardableHandleId")

  /**
   * C++ original:
   * ```cpp
   * const SkGlyphPositionRoundingSpec fRoundingSpec
   * ```
   */
  private val fRoundingSpec: SkGlyphPositionRoundingSpec = TODO("Initialize fRoundingSpec")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> fContext
   * ```
   */
  private var fContext: Int = TODO("Initialize fContext")

  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID fStrikeSpecTypefaceId
   * ```
   */
  private var fStrikeSpecTypefaceId: SkTypeface = TODO("Initialize fStrikeSpecTypefaceId")

  /**
   * C++ original:
   * ```cpp
   * const SkStrikeSpec* fStrikeSpec
   * ```
   */
  private val fStrikeSpec: SkStrikeSpec? = TODO("Initialize fStrikeSpec")

  /**
   * C++ original:
   * ```cpp
   * bool fHaveSentFontMetrics{false}
   * ```
   */
  private var fHaveSentFontMetrics: Boolean = TODO("Initialize fHaveSentFontMetrics")

  /**
   * C++ original:
   * ```cpp
   * THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest> fSentGlyphs
   * ```
   */
  private var fSentGlyphs: THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest> =
      TODO("Initialize fSentGlyphs")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkGlyph> fMasksToSend
   * ```
   */
  private var fMasksToSend: Int = TODO("Initialize fMasksToSend")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkGlyph> fPathsToSend
   * ```
   */
  private var fPathsToSend: Int = TODO("Initialize fPathsToSend")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkGlyph> fDrawablesToSend
   * ```
   */
  private var fDrawablesToSend: Int = TODO("Initialize fDrawablesToSend")

  /**
   * C++ original:
   * ```cpp
   * SkArenaAllocWithReset fAlloc{256}
   * ```
   */
  private var fAlloc: SkArenaAllocWithReset = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * RemoteStrike(const SkStrikeSpec& strikeSpec,
   *                  std::unique_ptr<SkScalerContext> context,
   *                  SkDiscardableHandleId discardableHandleId)
   * ```
   */
  public constructor(
    strikeSpec: SkStrikeSpec,
    context: SkScalerContext?,
    discardableHandleId: UInt,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void lock() override {}
   * ```
   */
  public override fun lock() {
    TODO("Implement lock")
  }

  /**
   * C++ original:
   * ```cpp
   * void unlock() override {}
   * ```
   */
  public override fun unlock() {
    TODO("Implement unlock")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphDigest RemoteStrike::digestFor(ActionType actionType, SkPackedGlyphID packedGlyphID) {
   *     SkGlyphDigest* digestPtr = fSentGlyphs.find(packedGlyphID);
   *     if (digestPtr != nullptr && digestPtr->actionFor(actionType) != GlyphAction::kUnset) {
   *         return *digestPtr;
   *     }
   *
   *     SkGlyph* glyph;
   *     this->ensureScalerContext();
   *     switch (actionType) {
   *         case skglyph::kPath: {
   *             fPathsToSend.emplace_back(fContext->makeGlyph(packedGlyphID, &fAlloc));
   *             glyph = &fPathsToSend.back();
   *             break;
   *         }
   *         case skglyph::kDrawable: {
   *             fDrawablesToSend.emplace_back(fContext->makeGlyph(packedGlyphID, &fAlloc));
   *             glyph = &fDrawablesToSend.back();
   *             break;
   *         }
   *         default: {
   *             fMasksToSend.emplace_back(fContext->makeGlyph(packedGlyphID, &fAlloc));
   *             glyph = &fMasksToSend.back();
   *             break;
   *         }
   *     }
   *
   *     if (digestPtr == nullptr) {
   *         digestPtr = fSentGlyphs.set(SkGlyphDigest{0, *glyph});
   *     }
   *
   *     digestPtr->setActionFor(actionType, glyph, this);
   *
   *     return *digestPtr;
   * }
   * ```
   */
  public override fun digestFor(actionType: ActionType, packedGlyphID: SkPackedGlyphID): SkGlyphDigest {
    TODO("Implement digestFor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool prepareForImage(SkGlyph* glyph) override {
   *         this->ensureScalerContext();
   *         glyph->setImage(&fAlloc, fContext.get());
   *         return glyph->image() != nullptr;
   *     }
   * ```
   */
  public override fun prepareForImage(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool prepareForPath(SkGlyph* glyph) override {
   *         this->ensureScalerContext();
   *         glyph->setPath(&fAlloc, fContext.get());
   *         return glyph->path() != nullptr;
   *     }
   * ```
   */
  public override fun prepareForPath(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool prepareForDrawable(SkGlyph* glyph) override {
   *         this->ensureScalerContext();
   *         glyph->setDrawable(&fAlloc, fContext.get());
   *         return glyph->drawable() != nullptr;
   *     }
   * ```
   */
  public override fun prepareForDrawable(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void RemoteStrike::writePendingGlyphs(SkWriteBuffer& buffer) {
   *     SkASSERT(this->hasPendingGlyphs());
   *
   *     // ScalerContext should not hold to the typeface, so we should not use its ID.
   *     // We should use StrikeSpec typeface and its ID instead.
   *     buffer.writeUInt(fStrikeSpecTypefaceId);
   *     buffer.writeUInt(fDiscardableHandleId);
   *     fDescriptor.getDesc()->flatten(buffer);
   *
   *     buffer.writeBool(fHaveSentFontMetrics);
   *     if (!fHaveSentFontMetrics) {
   *         // Write FontMetrics if not sent before.
   *         SkFontMetrics fontMetrics;
   *         fContext->getFontMetrics(&fontMetrics);
   *         SkFontMetricsPriv::Flatten(buffer, fontMetrics);
   *         fHaveSentFontMetrics = true;
   *     }
   *
   *     // Make sure to install all the mask data into the glyphs before sending.
   *     for (SkGlyph& glyph: fMasksToSend) {
   *         this->prepareForImage(&glyph);
   *     }
   *
   *     // Make sure to install all the path data into the glyphs before sending.
   *     for (SkGlyph& glyph: fPathsToSend) {
   *         this->prepareForPath(&glyph);
   *     }
   *
   *     // Make sure to install all the drawable data into the glyphs before sending.
   *     for (SkGlyph& glyph: fDrawablesToSend) {
   *         this->prepareForDrawable(&glyph);
   *     }
   *
   *     // Send all the pending glyph information.
   *     SkStrike::FlattenGlyphsByType(buffer, fMasksToSend, fPathsToSend, fDrawablesToSend);
   *
   *     // Reset all the sending data.
   *     fMasksToSend.clear();
   *     fPathsToSend.clear();
   *     fDrawablesToSend.clear();
   *     fAlloc.reset();
   * }
   * ```
   */
  public fun writePendingGlyphs(buffer: SkWriteBuffer) {
    TODO("Implement writePendingGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId discardableHandleId() const { return fDiscardableHandleId; }
   * ```
   */
  public fun discardableHandleId(): Int {
    TODO("Implement discardableHandleId")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDescriptor& getDescriptor() const override {
   *         return *fDescriptor.getDesc();
   *     }
   * ```
   */
  public override fun getDescriptor(): SkDescriptor {
    TODO("Implement getDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void RemoteStrike::setStrikeSpec(const SkStrikeSpec& strikeSpec) {
   *     fStrikeSpec = &strikeSpec;
   * }
   * ```
   */
  public fun setStrikeSpec(strikeSpec: SkStrikeSpec) {
    TODO("Implement setStrikeSpec")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyphPositionRoundingSpec& roundingSpec() const override {
   *         return fRoundingSpec;
   *     }
   * ```
   */
  public override fun roundingSpec(): SkGlyphPositionRoundingSpec {
    TODO("Implement roundingSpec")
  }

  /**
   * C++ original:
   * ```cpp
   * sktext::SkStrikePromise RemoteStrike::strikePromise() {
   *     return sktext::SkStrikePromise{*this->fStrikeSpec};
   * }
   * ```
   */
  public override fun strikePromise(): SkStrikePromise {
    TODO("Implement strikePromise")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPendingGlyphs() const {
   *         return !fMasksToSend.empty() || !fPathsToSend.empty() || !fDrawablesToSend.empty();
   *     }
   * ```
   */
  public fun hasPendingGlyphs(): Boolean {
    TODO("Implement hasPendingGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void RemoteStrike::resetScalerContext() {
   *     fContext = nullptr;
   *     fStrikeSpec = nullptr;
   * }
   * ```
   */
  public fun resetScalerContext() {
    TODO("Implement resetScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void RemoteStrike::ensureScalerContext() {
   *     if (fContext == nullptr) {
   *         fContext = fStrikeSpec->createScalerContext();
   *     }
   * }
   * ```
   */
  private fun ensureScalerContext() {
    TODO("Implement ensureScalerContext")
  }
}
