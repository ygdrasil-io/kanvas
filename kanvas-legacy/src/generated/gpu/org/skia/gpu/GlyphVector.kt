package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrikePromise
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.utils.SkStrikeClient
import undefined.GrMeshDrawTarget

/**
 * C++ original:
 * ```cpp
 * class GlyphVector {
 * public:
 *     union Variant {
 *         // Initially, filled with packed id, but changed to Glyph* in the onPrepare stage.
 *         SkPackedGlyphID packedGlyphID;
 *         Glyph* glyph;
 *         // Add ctors to help SkArenaAlloc create arrays.
 *         Variant() : glyph{nullptr} {}
 *         Variant(SkPackedGlyphID id) : packedGlyphID{id} {}
 *     };
 *
 *     GlyphVector(SkStrikePromise&& strikePromise, SkSpan<Variant> glyphs);
 *
 *     static GlyphVector Make(SkStrikePromise&& promise,
 *                             SkSpan<const SkPackedGlyphID> glyphs,
 *                             SubRunAllocator* alloc);
 *
 *     SkSpan<const Glyph*> glyphs() const;
 *
 *     static std::optional<GlyphVector> MakeFromBuffer(SkReadBuffer& buffer,
 *                                                      const SkStrikeClient* strikeClient,
 *                                                      SubRunAllocator* alloc);
 *     void flatten(SkWriteBuffer& buffer) const;
 *
 *     // This doesn't need to include sizeof(GlyphVector) because this is embedded in each of
 *     // the sub runs.
 *     int unflattenSize() const { return GlyphVectorSize(fGlyphs.size()); }
 *
 *     void packedGlyphIDToGlyph(StrikeCache* cache);
 *
 *     static size_t GlyphVectorSize(size_t count) {
 *         return sizeof(Variant) * count;
 *     }
 *
 * private:
 *     friend class GlyphVectorTestingPeer;
 *     friend class ::skgpu::graphite::Device;
 *     friend class ::skgpu::ganesh::AtlasTextOp;
 *
 *     // This function is implemented in ganesh/text/GrAtlasManager.cpp, and should only be called
 *     // from AtlasTextOp or linking issues may occur.
 *     std::tuple<bool, int> regenerateAtlasForGanesh(
 *             int begin, int end,
 *             skgpu::MaskFormat maskFormat,
 *             int srcPadding,
 *             GrMeshDrawTarget*);
 *
 *     // This function is implemented in graphite/text/AtlasManager.cpp, and should only be called
 *     // from graphite::Device or linking issues may occur.
 *     std::tuple<bool, int> regenerateAtlasForGraphite(
 *             int begin, int end,
 *             skgpu::MaskFormat maskFormat,
 *             int srcPadding,
 *             skgpu::graphite::Recorder*);
 *
 *     SkStrikePromise fStrikePromise;
 *     SkSpan<Variant> fGlyphs;
 *     sk_sp<TextStrike> fTextStrike{nullptr};
 *     uint64_t fAtlasGeneration{skgpu::AtlasGenerationCounter::kInvalidGeneration};
 *     skgpu::BulkUsePlotUpdater fBulkUseUpdater;
 * }
 * ```
 */
public open class GlyphVector public constructor(
  strikePromise: SkStrikePromise,
  glyphs: SkSpan<org.skia.tests.Variant>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise fStrikePromise
   * ```
   */
  private var fStrikePromise: Int = TODO("Initialize fStrikePromise")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<Variant> fGlyphs
   * ```
   */
  private var fGlyphs: Int = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextStrike> fTextStrike
   * ```
   */
  private var fTextStrike: Int = TODO("Initialize fTextStrike")

  /**
   * C++ original:
   * ```cpp
   * uint64_t fAtlasGeneration
   * ```
   */
  private var fAtlasGeneration: Int = TODO("Initialize fAtlasGeneration")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BulkUsePlotUpdater fBulkUseUpdater
   * ```
   */
  private var fBulkUseUpdater: Int = TODO("Initialize fBulkUseUpdater")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Glyph*> GlyphVector::glyphs() const {
   *     return SkSpan(reinterpret_cast<const Glyph**>(fGlyphs.data()), fGlyphs.size());
   * }
   * ```
   */
  public fun glyphs(): Int {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphVector::flatten(SkWriteBuffer& buffer) const {
   *     // There should never be a glyph vector with zero glyphs.
   *     SkASSERT(!fGlyphs.empty());
   *     fStrikePromise.flatten(buffer);
   *
   *     // Write out the span of packedGlyphIDs.
   *     buffer.write32(SkTo<int32_t>(fGlyphs.size()));
   *     for (Variant variant : fGlyphs) {
   *         buffer.writeUInt(variant.packedGlyphID.value());
   *     }
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * int unflattenSize() const { return GlyphVectorSize(fGlyphs.size()); }
   * ```
   */
  public fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphVector::packedGlyphIDToGlyph(StrikeCache* cache) {
   *     if (fTextStrike == nullptr) {
   *         SkStrike* strike = fStrikePromise.strike();
   *         fTextStrike = cache->findOrCreateStrike(strike->strikeSpec());
   *
   *         // Get all the atlas locations for each glyph.
   *         for (Variant& variant : fGlyphs) {
   *             variant.glyph = fTextStrike->getGlyph(variant.packedGlyphID);
   *         }
   *
   *         // This must be pinned for the Atlas filling to work.
   *         strike->verifyPinnedStrike();
   *
   *         // Drop the ref to the strike so that it can be purged if needed.
   *         fStrikePromise.resetStrike();
   *     }
   * }
   * ```
   */
  public fun packedGlyphIDToGlyph(cache: StrikeCache?) {
    TODO("Implement packedGlyphIDToGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, int> regenerateAtlasForGanesh(
   *             int begin, int end,
   *             skgpu::MaskFormat maskFormat,
   *             int srcPadding,
   *             GrMeshDrawTarget*)
   * ```
   */
  private fun regenerateAtlasForGanesh(
    begin: Int,
    end: Int,
    maskFormat: MaskFormat,
    srcPadding: Int,
    param4: GrMeshDrawTarget?,
  ): Int {
    TODO("Implement regenerateAtlasForGanesh")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, int> GlyphVector::regenerateAtlasForGraphite(int begin,
   *                                                               int end,
   *                                                               skgpu::MaskFormat maskFormat,
   *                                                               int srcPadding,
   *                                                               skgpu::graphite::Recorder* recorder) {
   *     auto atlasManager = recorder->priv().atlasProvider()->textAtlasManager();
   *     auto tokenTracker = recorder->priv().tokenTracker();
   *
   *     // TODO: this is not a great place for this -- need a better way to init atlases when needed
   *     unsigned int numActiveProxies;
   *     const sk_sp<skgpu::graphite::TextureProxy>* proxies =
   *             atlasManager->getProxies(maskFormat, &numActiveProxies);
   *     if (!proxies) {
   *         SkDebugf("Could not allocate backing texture for atlas\n");
   *         return {false, 0};
   *     }
   *
   *     uint64_t currentAtlasGen = atlasManager->atlasGeneration(maskFormat);
   *
   *     this->packedGlyphIDToGlyph(recorder->priv().strikeCache());
   *
   *     if (fAtlasGeneration != currentAtlasGen) {
   *         // Calculate the texture coordinates for the vertexes during first use (fAtlasGeneration
   *         // is set to kInvalidAtlasGeneration) or the atlas has changed in subsequent calls..
   *         fBulkUseUpdater.reset();
   *
   *         SkBulkGlyphMetricsAndImages metricsAndImages{fTextStrike->strikeSpec()};
   *
   *         // Update the atlas information in the GrStrike.
   *         auto glyphs = fGlyphs.subspan(begin, end - begin);
   *         int glyphsPlacedInAtlas = 0;
   *         bool success = true;
   *         for (const Variant& variant : glyphs) {
   *             Glyph* gpuGlyph = variant.glyph;
   *             SkASSERT(gpuGlyph != nullptr);
   *
   *             if (!atlasManager->hasGlyph(maskFormat, gpuGlyph)) {
   *                 const SkGlyph& skGlyph = *metricsAndImages.glyph(gpuGlyph->fPackedID);
   *                 auto code = atlasManager->addGlyphToAtlas(skGlyph, gpuGlyph, srcPadding);
   *                 if (code != DrawAtlas::ErrorCode::kSucceeded) {
   *                     success = code != DrawAtlas::ErrorCode::kError;
   *                     break;
   *                 }
   *             }
   *             atlasManager->addGlyphToBulkAndSetUseToken(
   *                     &fBulkUseUpdater, maskFormat, gpuGlyph,
   *                     tokenTracker->nextFlushToken());
   *             glyphsPlacedInAtlas++;
   *         }
   *
   *         // Update atlas generation if there are no more glyphs to put in the atlas.
   *         if (success && begin + glyphsPlacedInAtlas == SkCount(fGlyphs)) {
   *             // Need to get the freshest value of the atlas' generation because
   *             // updateTextureCoordinates may have changed it.
   *             fAtlasGeneration = atlasManager->atlasGeneration(maskFormat);
   *         }
   *
   *         return {success, glyphsPlacedInAtlas};
   *     } else {
   *         // The atlas hasn't changed, so our texture coordinates are still valid.
   *         if (end == SkCount(fGlyphs)) {
   *             // The atlas hasn't changed and the texture coordinates are all still valid. Update
   *             // all the plots used to the new use token.
   *             atlasManager->setUseTokenBulk(fBulkUseUpdater,
   *                                           tokenTracker->nextFlushToken(),
   *                                           maskFormat);
   *         }
   *         return {true, end - begin};
   *     }
   * }
   * ```
   */
  private fun regenerateAtlasForGraphite(
    begin: Int,
    end: Int,
    maskFormat: MaskFormat,
    srcPadding: Int,
    recorder: Recorder?,
  ): Int {
    TODO("Implement regenerateAtlasForGraphite")
  }

  public data class Variant public constructor(
    private var packedGlyphID: Int,
    private var glyph: Glyph?,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * GlyphVector GlyphVector::Make(SkStrikePromise&& promise,
     *                               SkSpan<const SkPackedGlyphID> packedIDs,
     *                               SubRunAllocator* alloc) {
     *     SkASSERT(!packedIDs.empty());
     *     auto packedIDToVariant = [] (SkPackedGlyphID packedID) {
     *         return Variant{packedID};
     *     };
     *
     *     return GlyphVector{std::move(promise),
     *                        alloc->makePODArray<Variant>(packedIDs, packedIDToVariant)};
     * }
     * ```
     */
    public fun make(
      promise: SkStrikePromise,
      glyphs: SkSpan<SkPackedGlyphID>,
      alloc: SubRunAllocator?,
    ): GlyphVector {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<GlyphVector> GlyphVector::MakeFromBuffer(SkReadBuffer& buffer,
     *                                                        const SkStrikeClient* client,
     *                                                        SubRunAllocator* alloc) {
     *     std::optional<SkStrikePromise> promise =
     *             SkStrikePromise::MakeFromBuffer(buffer, client, SkStrikeCache::GlobalStrikeCache());
     *     if (!buffer.validate(promise.has_value())) {
     *         return std::nullopt;
     *     }
     *
     *     int32_t glyphCount = buffer.read32();
     *     // Since the glyph count can never be zero. There was a buffer reading problem.
     *     if (!buffer.validate(glyphCount > 0)) {
     *         return std::nullopt;
     *     }
     *
     *     // Make sure we can multiply without overflow in the check below.
     *     static constexpr int kMaxCount = (int)(INT_MAX / sizeof(uint32_t));
     *     if (!buffer.validate(glyphCount <= kMaxCount)) {
     *         return std::nullopt;
     *     }
     *
     *     // Check for enough bytes to populate the packedGlyphID array. If not enough something has
     *     // gone wrong.
     *     if (!buffer.validate(glyphCount * sizeof(uint32_t) <= buffer.available())) {
     *         return std::nullopt;
     *     }
     *
     *     Variant* variants = alloc->makePODArray<Variant>(glyphCount);
     *     for (int i = 0; i < glyphCount; i++) {
     *         variants[i].packedGlyphID = SkPackedGlyphID(buffer.readUInt());
     *     }
     *     return GlyphVector{std::move(promise.value()), SkSpan(variants, glyphCount)};
     * }
     * ```
     */
    public fun makeFromBuffer(
      buffer: SkReadBuffer,
      strikeClient: SkStrikeClient?,
      alloc: SubRunAllocator?,
    ): Int {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t GlyphVectorSize(size_t count) {
     *         return sizeof(Variant) * count;
     *     }
     * ```
     */
    public fun glyphVectorSize(count: ULong): Int {
      TODO("Implement glyphVectorSize")
    }
  }
}
