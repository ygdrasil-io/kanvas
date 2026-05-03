package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkStrike final : public sktext::StrikeForGPU {
 * public:
 *     SkStrike(SkStrikeCache* strikeCache,
 *              const SkStrikeSpec& strikeSpec,
 *              std::unique_ptr<SkScalerContext> scaler,
 *              const SkFontMetrics* metrics,
 *              std::unique_ptr<SkStrikePinner> pinner);
 *
 *     void lock() override SK_ACQUIRE(fStrikeLock);
 *     void unlock() override SK_RELEASE_CAPABILITY(fStrikeLock);
 *     SkGlyphDigest digestFor(skglyph::ActionType, SkPackedGlyphID) override SK_REQUIRES(fStrikeLock);
 *     bool prepareForImage(SkGlyph* glyph) override SK_REQUIRES(fStrikeLock);
 *     bool prepareForPath(SkGlyph*) override SK_REQUIRES(fStrikeLock);
 *     bool prepareForDrawable(SkGlyph*) override SK_REQUIRES(fStrikeLock);
 *
 *     bool mergeFromBuffer(SkReadBuffer& buffer) SK_EXCLUDES(fStrikeLock);
 *     static void FlattenGlyphsByType(SkWriteBuffer& buffer,
 *                                     SkSpan<SkGlyph> images,
 *                                     SkSpan<SkGlyph> paths,
 *                                     SkSpan<SkGlyph> drawables);
 *
 *     // Lookup (or create if needed) the returned glyph using toID. If that glyph is not initialized
 *     // with an image, then use the information in fromGlyph to initialize the width, height top,
 *     // left, format and image of the glyph. This is mainly used preserving the glyph if it was
 *     // created by a search of desperation. This is deprecated.
 *     SkGlyph* mergeGlyphAndImage(
 *             SkPackedGlyphID toID, const SkGlyph& fromGlyph) SK_EXCLUDES(fStrikeLock);
 *
 *     // If the path has never been set, then add a path to glyph. This is deprecated.
 *     const SkPath* mergePath(
 *             SkGlyph* glyph, const SkPath* path, bool hairline, bool modified) SK_EXCLUDES(fStrikeLock);
 *
 *     // If the drawable has never been set, then add a drawable to glyph. This is deprecated.
 *     const SkDrawable* mergeDrawable(
 *             SkGlyph* glyph, sk_sp<SkDrawable> drawable) SK_EXCLUDES(fStrikeLock);
 *
 *     // If the advance axis intersects the glyph's path, append the positions scaled and offset
 *     // to the array (if non-null), and set the count to the updated array length.
 *     // TODO: track memory usage.
 *     void findIntercepts(const SkScalar bounds[2], SkScalar scale, SkScalar xPos,
 *                         SkGlyph*, SkScalar* array, int* count) SK_EXCLUDES(fStrikeLock);
 *
 *     const SkFontMetrics& getFontMetrics() const {
 *         return fFontMetrics;
 *     }
 *
 *     SkSpan<const SkGlyph*> metrics(
 *             SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) SK_EXCLUDES(fStrikeLock);
 *
 *     SkSpan<const SkGlyph*> preparePaths(
 *             SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) SK_EXCLUDES(fStrikeLock);
 *
 *     SkSpan<const SkGlyph*> prepareImages(SkSpan<const SkPackedGlyphID> glyphIDs,
 *                                          const SkGlyph* results[]) SK_EXCLUDES(fStrikeLock);
 *
 *     SkSpan<const SkGlyph*> prepareDrawables(
 *             SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) SK_EXCLUDES(fStrikeLock);
 *
 *     // SkStrikeForGPU APIs
 *     const SkDescriptor& getDescriptor() const override {
 *         return fStrikeSpec.descriptor();
 *     }
 *
 *     const SkGlyphPositionRoundingSpec& roundingSpec() const override {
 *         return fRoundingSpec;
 *     }
 *
 *     sktext::SkStrikePromise strikePromise() override {
 *         return sktext::SkStrikePromise(sk_ref_sp<SkStrike>(this));
 *     }
 *
 *     // Convert all the IDs into SkPaths in the span.
 *     void glyphIDsToPaths(SkSpan<sktext::IDOrPath> idsOrPaths) SK_EXCLUDES(fStrikeLock);
 *
 *     // Convert all the IDs into SkDrawables in the span.
 *     void glyphIDsToDrawables(SkSpan<sktext::IDOrDrawable> idsOrDrawables) SK_EXCLUDES(fStrikeLock);
 *
 *     const SkStrikeSpec& strikeSpec() const {
 *         return fStrikeSpec;
 *     }
 *
 *     void verifyPinnedStrike() const {
 *         if (fPinner != nullptr) {
 *             fPinner->assertValid();
 *         }
 *     }
 *
 *     void dump() const SK_EXCLUDES(fStrikeLock);
 *     void dumpMemoryStatistics(SkTraceMemoryDump* dump) const SK_EXCLUDES(fStrikeLock);
 *
 *     SkGlyph* glyph(SkGlyphDigest) SK_REQUIRES(fStrikeLock);
 *
 * private:
 *     friend class SkStrikeCache;
 *     friend class SkStrikeTestingPeer;
 *     class Monitor;
 *
 *     // Return a glyph. Create it if it doesn't exist, and initialize the glyph with metrics and
 *     // advances using a scaler.
 *     SkGlyph* glyph(SkPackedGlyphID) SK_REQUIRES(fStrikeLock);
 *
 *     // Generate the glyph digest information and update structures to add the glyph.
 *     SkGlyphDigest* addGlyphAndDigest(SkGlyph* glyph) SK_REQUIRES(fStrikeLock);
 *
 *     SkGlyph* mergeGlyphFromBuffer(SkReadBuffer& buffer) SK_REQUIRES(fStrikeLock);
 *     bool mergeGlyphAndImageFromBuffer(SkReadBuffer& buffer) SK_REQUIRES(fStrikeLock);
 *     bool mergeGlyphAndPathFromBuffer(SkReadBuffer& buffer) SK_REQUIRES(fStrikeLock);
 *     bool mergeGlyphAndDrawableFromBuffer(SkReadBuffer& buffer) SK_REQUIRES(fStrikeLock);
 *
 *     // Maintain memory use statistics.
 *     void updateMemoryUsage(size_t increase) SK_EXCLUDES(fStrikeLock);
 *
 *     enum PathDetail {
 *         kMetricsOnly,
 *         kMetricsAndPath
 *     };
 *
 *     // internalPrepare will only be called with a mutex already held.
 *     SkSpan<const SkGlyph*> internalPrepare(
 *             SkSpan<const SkGlyphID> glyphIDs,
 *             PathDetail pathDetail,
 *             const SkGlyph** results) SK_REQUIRES(fStrikeLock);
 *
 *     // The following are const and need no mutex protection.
 *     const SkFontMetrics               fFontMetrics;
 *     const SkGlyphPositionRoundingSpec fRoundingSpec;
 *     const SkStrikeSpec                fStrikeSpec;
 *     SkStrikeCache* const              fStrikeCache;
 *
 *     // This mutex provides protection for this specific SkStrike.
 *     mutable SkMutex fStrikeLock;
 *
 *     // Maps from a combined GlyphID and sub-pixel position to a SkGlyphDigest. The actual glyph is
 *     // stored in the fAlloc. The pointer to the glyph is stored fGlyphForIndex. The
 *     // SkGlyphDigest's fIndex field stores the index. This pointer provides an unchanging
 *     // reference to the SkGlyph as long as the strike is alive, and fGlyphForIndex
 *     // provides a dense index for glyphs.
 *     skia_private::THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest>
 *             fDigestForPackedGlyphID SK_GUARDED_BY(fStrikeLock);
 *
 *     // Maps from a glyphIndex to a glyph
 *     std::vector<SkGlyph*> fGlyphForIndex SK_GUARDED_BY(fStrikeLock);
 *
 *     // Context that corresponds to the glyph information in this strike.
 *     const std::unique_ptr<SkScalerContext> fScalerContext SK_GUARDED_BY(fStrikeLock);
 *
 *     // Used while changing the strike to track memory increase.
 *     size_t fMemoryIncrease SK_GUARDED_BY(fStrikeLock) {0};
 *
 *     // So, we don't grow our arrays a lot.
 *     inline static constexpr size_t kMinGlyphCount = 8;
 *     inline static constexpr size_t kMinGlyphImageSize = 16 /* height */ * 8 /* width */;
 *     inline static constexpr size_t kMinAllocAmount = kMinGlyphImageSize * kMinGlyphCount;
 *
 *     SkArenaAlloc            fAlloc SK_GUARDED_BY(fStrikeLock) {kMinAllocAmount};
 *
 *     // The following are protected by the SkStrikeCache's mutex.
 *     SkStrike*                       fNext{nullptr};
 *     SkStrike*                       fPrev{nullptr};
 *     std::unique_ptr<SkStrikePinner> fPinner;
 *     size_t                          fMemoryUsed{sizeof(SkStrike)};
 *     bool                            fRemoved{false};
 * }
 * ```
 */
public class SkStrike public constructor(
  strikeCache: SkStrikeCache?,
  strikeSpec: SkStrikeSpec,
  scaler: SkScalerContext?,
  metrics: SkFontMetrics?,
  pinner: SkStrikePinner?,
) : StrikeForGPU() {
  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics               fFontMetrics
   * ```
   */
  private val fFontMetrics: SkFontMetrics = TODO("Initialize fFontMetrics")

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
   * const SkStrikeSpec                fStrikeSpec
   * ```
   */
  private val fStrikeSpec: SkStrikeSpec = TODO("Initialize fStrikeSpec")

  /**
   * C++ original:
   * ```cpp
   * SkStrikeCache* const              fStrikeCache
   * ```
   */
  private val fStrikeCache: SkStrikeCache? = TODO("Initialize fStrikeCache")

  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex fStrikeLock
   * ```
   */
  private var fStrikeLock: SkMutex = TODO("Initialize fStrikeLock")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest>
   *             fDigestForPackedGlyphID
   * ```
   */
  private var fDigestForPackedGlyphID: THashTable<SkGlyphDigest, SkPackedGlyphID, SkGlyphDigest> =
      TODO("Initialize fDigestForPackedGlyphID")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkGlyph*> fGlyphForIndex
   * ```
   */
  private var fGlyphForIndex: Int = TODO("Initialize fGlyphForIndex")

  /**
   * C++ original:
   * ```cpp
   * const std::unique_ptr<SkScalerContext> fScalerContext
   * ```
   */
  private val fScalerContext: Int = TODO("Initialize fScalerContext")

  /**
   * C++ original:
   * ```cpp
   * size_t fMemoryIncrease SK_GUARDED_BY(fStrikeLock) {0}
   * ```
   */
  private var fMemoryIncrease: ULong = TODO("Initialize fMemoryIncrease")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kMinGlyphCount = 8
   * ```
   */
  private var fAlloc: SkArenaAlloc = TODO("Initialize fAlloc")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kMinGlyphImageSize = 16 /* height */ * 8
   * ```
   */
  private var fNext: SkStrike? = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kMinAllocAmount = kMinGlyphImageSize * kMinGlyphCount
   * ```
   */
  private var fPrev: SkStrike? = TODO("Initialize fPrev")

  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc            fAlloc
   * ```
   */
  private var fPinner: Int = TODO("Initialize fPinner")

  /**
   * C++ original:
   * ```cpp
   * SkStrike*                       fNext{nullptr}
   * ```
   */
  private var fMemoryUsed: ULong = TODO("Initialize fMemoryUsed")

  /**
   * C++ original:
   * ```cpp
   * SkStrike*                       fPrev{nullptr}
   * ```
   */
  private var fRemoved: Boolean = TODO("Initialize fRemoved")

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::lock() {
   *     fStrikeLock.acquire();
   *     fMemoryIncrease = 0;
   * }
   * ```
   */
  public override fun lock() {
    TODO("Implement lock")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::unlock() {
   *     const size_t memoryIncrease = fMemoryIncrease;
   *     fStrikeLock.release();
   *     this->updateMemoryUsage(memoryIncrease);
   * }
   * ```
   */
  public override fun unlock() {
    TODO("Implement unlock")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphDigest SkStrike::digestFor(ActionType actionType, SkPackedGlyphID packedGlyphID) {
   *     SkGlyphDigest* digestPtr = fDigestForPackedGlyphID.find(packedGlyphID);
   *     if (digestPtr != nullptr && digestPtr->actionFor(actionType) != GlyphAction::kUnset) {
   *         return *digestPtr;
   *     }
   *
   *     SkGlyph* glyph;
   *     if (digestPtr != nullptr) {
   *         glyph = fGlyphForIndex[digestPtr->index()];
   *     } else {
   *         glyph = fAlloc.make<SkGlyph>(fScalerContext->makeGlyph(packedGlyphID, &fAlloc));
   *         fMemoryIncrease += sizeof(SkGlyph);
   *         digestPtr = this->addGlyphAndDigest(glyph);
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
   * bool SkStrike::prepareForImage(SkGlyph* glyph) {
   *     if (glyph->setImage(&fAlloc, fScalerContext.get())) {
   *         fMemoryIncrease += glyph->imageSize();
   *     }
   *     return glyph->image() != nullptr;
   * }
   * ```
   */
  public override fun prepareForImage(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::prepareForPath(SkGlyph* glyph) {
   *     if (glyph->setPath(&fAlloc, fScalerContext.get())) {
   *         fMemoryIncrease += glyph->path()->approximateBytesUsed();
   *     }
   *     return glyph->path() !=nullptr;
   * }
   * ```
   */
  public override fun prepareForPath(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::prepareForDrawable(SkGlyph* glyph) {
   *     if (glyph->setDrawable(&fAlloc, fScalerContext.get())) {
   *         size_t increase = glyph->drawable()->approximateBytesUsed();
   *         SkASSERT(increase > 0);
   *         fMemoryIncrease += increase;
   *     }
   *     return glyph->drawable() != nullptr;
   * }
   * ```
   */
  public override fun prepareForDrawable(glyph: SkGlyph?): Boolean {
    TODO("Implement prepareForDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::mergeFromBuffer(SkReadBuffer& buffer) {
   *     // Read glyphs with images for the current strike.
   *     const int imagesCount = buffer.readInt();
   *     if (imagesCount == 0 && !buffer.isValid()) {
   *         return false;
   *     }
   *
   *     {
   *         Monitor m{this};
   *         for (int curImage = 0; curImage < imagesCount; ++curImage) {
   *             if (!this->mergeGlyphAndImageFromBuffer(buffer)) {
   *                 return false;
   *             }
   *         }
   *     }
   *
   *     // Read glyphs with paths for the current strike.
   *     const int pathsCount = buffer.readInt();
   *     if (pathsCount == 0 && !buffer.isValid()) {
   *         return false;
   *     }
   *     {
   *         Monitor m{this};
   *         for (int curPath = 0; curPath < pathsCount; ++curPath) {
   *             if (!this->mergeGlyphAndPathFromBuffer(buffer)) {
   *                 return false;
   *             }
   *         }
   *     }
   *
   *     // Read glyphs with drawables for the current strike.
   *     const int drawablesCount = buffer.readInt();
   *     if (drawablesCount == 0 && !buffer.isValid()) {
   *         return false;
   *     }
   *     {
   *         Monitor m{this};
   *         for (int curDrawable = 0; curDrawable < drawablesCount; ++curDrawable) {
   *             if (!this->mergeGlyphAndDrawableFromBuffer(buffer)) {
   *                 return false;
   *             }
   *         }
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun mergeFromBuffer(buffer: SkReadBuffer): Boolean {
    TODO("Implement mergeFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph* SkStrike::mergeGlyphAndImage(SkPackedGlyphID toID, const SkGlyph& fromGlyph) {
   *     Monitor m{this};
   *     // TODO(herb): remove finding the glyph when setting the metrics and image are separated
   *     SkGlyphDigest* digest = fDigestForPackedGlyphID.find(toID);
   *     if (digest != nullptr) {
   *         SkGlyph* glyph = fGlyphForIndex[digest->index()];
   *         if (fromGlyph.setImageHasBeenCalled()) {
   *             if (glyph->setImageHasBeenCalled()) {
   *                 // Should never set an image on a glyph which already has an image.
   *                 SkDEBUGFAIL("Re-adding image to existing glyph. This should not happen.");
   *             }
   *             // TODO: assert that any metrics on fromGlyph are the same.
   *             fMemoryIncrease += glyph->setMetricsAndImage(&fAlloc, fromGlyph);
   *         }
   *         return glyph;
   *     } else {
   *         SkGlyph* glyph = fAlloc.make<SkGlyph>(toID);
   *         fMemoryIncrease += glyph->setMetricsAndImage(&fAlloc, fromGlyph) + sizeof(SkGlyph);
   *         (void)this->addGlyphAndDigest(glyph);
   *         return glyph;
   *     }
   * }
   * ```
   */
  public fun mergeGlyphAndImage(toID: SkPackedGlyphID, fromGlyph: SkGlyph): SkGlyph {
    TODO("Implement mergeGlyphAndImage")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPath* SkStrike::mergePath(SkGlyph* glyph, const SkPath* path, bool hairline, bool modified) {
   *     Monitor m{this};
   *     if (glyph->setPathHasBeenCalled()) {
   *         SkDEBUGFAIL("Re-adding path to existing glyph. This should not happen.");
   *     }
   *     if (glyph->setPath(&fAlloc, path, hairline, modified)) {
   *         fMemoryIncrease += glyph->path()->approximateBytesUsed();
   *     }
   *
   *     return glyph->path();
   * }
   * ```
   */
  public fun mergePath(
    glyph: SkGlyph?,
    path: SkPath?,
    hairline: Boolean,
    modified: Boolean,
  ): SkPath {
    TODO("Implement mergePath")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDrawable* SkStrike::mergeDrawable(SkGlyph* glyph, sk_sp<SkDrawable> drawable) {
   *     Monitor m{this};
   *     if (glyph->setDrawableHasBeenCalled()) {
   *         SkDEBUGFAIL("Re-adding drawable to existing glyph. This should not happen.");
   *     }
   *     if (glyph->setDrawable(&fAlloc, std::move(drawable))) {
   *         fMemoryIncrease += glyph->drawable()->approximateBytesUsed();
   *         SkASSERT(fMemoryIncrease > 0);
   *     }
   *
   *     return glyph->drawable();
   * }
   * ```
   */
  public fun mergeDrawable(glyph: SkGlyph?, drawable: SkSp<SkDrawable>): SkDrawable {
    TODO("Implement mergeDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::findIntercepts(const SkScalar bounds[2], SkScalar scale, SkScalar xPos,
   *                               SkGlyph* glyph, SkScalar* array, int* count) {
   *     SkAutoMutexExclusive lock{fStrikeLock};
   *     glyph->ensureIntercepts(bounds, scale, xPos, array, count, &fAlloc);
   * }
   * ```
   */
  public fun findIntercepts(
    bounds: Array<SkScalar>,
    scale: SkScalar,
    xPos: SkScalar,
    glyph: SkGlyph?,
    array: SkScalar?,
    count: Int?,
  ) {
    TODO("Implement findIntercepts")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics& getFontMetrics() const {
   *         return fFontMetrics;
   *     }
   * ```
   */
  public fun getFontMetrics(): SkFontMetrics {
    TODO("Implement getFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkStrike::metrics(
   *         SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) {
   *     Monitor m{this};
   *     return this->internalPrepare(glyphIDs, kMetricsOnly, results);
   * }
   * ```
   */
  public fun metrics(glyphIDs: SkSpan<SkGlyphID>, results: Int): SkSpan<SkGlyph?> {
    TODO("Implement metrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkStrike::preparePaths(
   *         SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) {
   *     Monitor m{this};
   *     return this->internalPrepare(glyphIDs, kMetricsAndPath, results);
   * }
   * ```
   */
  public fun preparePaths(glyphIDs: SkSpan<SkGlyphID>, results: Int): SkSpan<SkGlyph?> {
    TODO("Implement preparePaths")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkStrike::prepareImages(
   *         SkSpan<const SkPackedGlyphID> glyphIDs, const SkGlyph* results[]) {
   *     const SkGlyph** cursor = results;
   *     Monitor m{this};
   *     for (auto glyphID : glyphIDs) {
   *         SkGlyph* glyph = this->glyph(glyphID);
   *         this->prepareForImage(glyph);
   *         *cursor++ = glyph;
   *     }
   *
   *     return {results, glyphIDs.size()};
   * }
   * ```
   */
  public fun prepareImages(glyphIDs: SkSpan<SkPackedGlyphID>, results: Int): SkSpan<SkGlyph?> {
    TODO("Implement prepareImages")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkStrike::prepareDrawables(
   *         SkSpan<const SkGlyphID> glyphIDs, const SkGlyph* results[]) {
   *     const SkGlyph** cursor = results;
   *     {
   *         Monitor m{this};
   *         for (auto glyphID : glyphIDs) {
   *             SkGlyph* glyph = this->glyph(SkPackedGlyphID{glyphID});
   *             this->prepareForDrawable(glyph);
   *             *cursor++ = glyph;
   *         }
   *     }
   *
   *     return {results, glyphIDs.size()};
   * }
   * ```
   */
  public fun prepareDrawables(glyphIDs: SkSpan<SkGlyphID>, results: Int): SkSpan<SkGlyph?> {
    TODO("Implement prepareDrawables")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDescriptor& getDescriptor() const override {
   *         return fStrikeSpec.descriptor();
   *     }
   * ```
   */
  public override fun getDescriptor(): SkDescriptor {
    TODO("Implement getDescriptor")
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
   * sktext::SkStrikePromise strikePromise() override {
   *         return sktext::SkStrikePromise(sk_ref_sp<SkStrike>(this));
   *     }
   * ```
   */
  public override fun strikePromise(): SkStrikePromise {
    TODO("Implement strikePromise")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::glyphIDsToPaths(SkSpan<sktext::IDOrPath> idsOrPaths) {
   *     Monitor m{this};
   *     for (sktext::IDOrPath& idOrPath : idsOrPaths) {
   *         SkGlyph* glyph = this->glyph(SkPackedGlyphID{idOrPath.fGlyphID});
   *         this->prepareForPath(glyph);
   *         new (&idOrPath.fPath) SkPath{*glyph->path()};
   *     }
   * }
   * ```
   */
  public fun glyphIDsToPaths(idsOrPaths: SkSpan<IDOrPath>) {
    TODO("Implement glyphIDsToPaths")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::glyphIDsToDrawables(SkSpan<sktext::IDOrDrawable> idsOrDrawables) {
   *     Monitor m{this};
   *     for (sktext::IDOrDrawable& idOrDrawable : idsOrDrawables) {
   *         SkGlyph* glyph = this->glyph(SkPackedGlyphID{idOrDrawable.fGlyphID});
   *         this->prepareForDrawable(glyph);
   *         SkASSERT(glyph->drawable() != nullptr);
   *         idOrDrawable.fDrawable = glyph->drawable();
   *     }
   * }
   * ```
   */
  public fun glyphIDsToDrawables(idsOrDrawables: SkSpan<IDOrDrawable>) {
    TODO("Implement glyphIDsToDrawables")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkStrikeSpec& strikeSpec() const {
   *         return fStrikeSpec;
   *     }
   * ```
   */
  public fun strikeSpec(): SkStrikeSpec {
    TODO("Implement strikeSpec")
  }

  /**
   * C++ original:
   * ```cpp
   * void verifyPinnedStrike() const {
   *         if (fPinner != nullptr) {
   *             fPinner->assertValid();
   *         }
   *     }
   * ```
   */
  public fun verifyPinnedStrike() {
    TODO("Implement verifyPinnedStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::dump() const {
   *     SkAutoMutexExclusive lock{fStrikeLock};
   *     const SkTypeface* face = fScalerContext->getTypeface();
   *     const SkScalerContextRec& rec = fScalerContext->getRec();
   *     SkString name;
   *     face->getFamilyName(&name);
   *
   *     SkString msg;
   *     SkFontStyle style = face->fontStyle();
   *     msg.printf("cache typeface:%x %25s:(%d,%d,%d)\n %s glyphs:%3d",
   *                face->uniqueID(), name.c_str(), style.weight(), style.width(), style.slant(),
   *                rec.dump().c_str(), fDigestForPackedGlyphID.count());
   *     SkDebugf("%s\n", msg.c_str());
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::dumpMemoryStatistics(SkTraceMemoryDump* dump) const {
   *     SkAutoMutexExclusive lock{fStrikeLock};
   *     const SkTypeface* face = fScalerContext->getTypeface();
   *     const SkScalerContextRec& rec = fScalerContext->getRec();
   *
   *     SkString fontName;
   *     face->getFamilyName(&fontName);
   *     // Replace all special characters with '_'.
   *     for (size_t index = 0; index < fontName.size(); ++index) {
   *         if (!std::isalnum(fontName[index])) {
   *             fontName[index] = '_';
   *         }
   *     }
   *
   *     SkString dumpName = SkStringPrintf("%s/%s_%u/%p",
   *                                        SkStrikeCache::kGlyphCacheDumpName,
   *                                        fontName.c_str(),
   *                                        rec.fTypefaceID,
   *                                        this);
   *
   *     dump->dumpNumericValue(dumpName.c_str(), "size", "bytes", fMemoryUsed);
   *     dump->dumpNumericValue(dumpName.c_str(),
   *                            "glyph_count", "objects",
   *                            fDigestForPackedGlyphID.count());
   *     dump->setMemoryBacking(dumpName.c_str(), "malloc", nullptr);
   * }
   * ```
   */
  public fun dumpMemoryStatistics(dump: SkTraceMemoryDump?) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph* SkStrike::glyph(SkGlyphDigest digest) {
   *     return fGlyphForIndex[digest.index()];
   * }
   * ```
   */
  public fun glyph(digest: SkGlyphDigest): SkGlyph {
    TODO("Implement glyph")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph* SkStrike::glyph(SkPackedGlyphID packedGlyphID) {
   *     SkGlyphDigest digest = this->digestFor(kDirectMask, packedGlyphID);
   *     return this->glyph(digest);
   * }
   * ```
   */
  private fun glyph(packedGlyphID: SkPackedGlyphID): SkGlyph {
    TODO("Implement glyph")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphDigest* SkStrike::addGlyphAndDigest(SkGlyph* glyph) {
   *     size_t index = fGlyphForIndex.size();
   *     SkGlyphDigest digest = SkGlyphDigest{index, *glyph};
   *     SkGlyphDigest* newDigest = fDigestForPackedGlyphID.set(digest);
   *     fGlyphForIndex.push_back(glyph);
   *     return newDigest;
   * }
   * ```
   */
  private fun addGlyphAndDigest(glyph: SkGlyph?): SkGlyphDigest {
    TODO("Implement addGlyphAndDigest")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyph* SkStrike::mergeGlyphFromBuffer(SkReadBuffer& buffer) {
   *     SkASSERT(buffer.isValid());
   *     std::optional<SkGlyph> prototypeGlyph = SkGlyph::MakeFromBuffer(buffer);
   *     if (!buffer.validate(prototypeGlyph.has_value())) {
   *         return nullptr;
   *     }
   *
   *     // Check if this glyph has already been seen.
   *     SkGlyphDigest* digestPtr = fDigestForPackedGlyphID.find(prototypeGlyph->getPackedID());
   *     if (digestPtr != nullptr) {
   *         return fGlyphForIndex[digestPtr->index()];
   *     }
   *
   *     // This is the first time. Allocate a new glyph.
   *     SkGlyph* glyph = fAlloc.make<SkGlyph>(prototypeGlyph.value());
   *     fMemoryIncrease += sizeof(SkGlyph);
   *     this->addGlyphAndDigest(glyph);
   *     return glyph;
   * }
   * ```
   */
  private fun mergeGlyphFromBuffer(buffer: SkReadBuffer): SkGlyph {
    TODO("Implement mergeGlyphFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::mergeGlyphAndImageFromBuffer(SkReadBuffer& buffer) {
   *     SkASSERT(buffer.isValid());
   *     SkGlyph* glyph = this->mergeGlyphFromBuffer(buffer);
   *     if (!buffer.validate(glyph != nullptr)) {
   *         return false;
   *     }
   *     fMemoryIncrease += glyph->addImageFromBuffer(buffer, &fAlloc);
   *     return buffer.isValid();
   * }
   * ```
   */
  private fun mergeGlyphAndImageFromBuffer(buffer: SkReadBuffer): Boolean {
    TODO("Implement mergeGlyphAndImageFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::mergeGlyphAndPathFromBuffer(SkReadBuffer& buffer) {
   *     SkASSERT(buffer.isValid());
   *     SkGlyph* glyph = this->mergeGlyphFromBuffer(buffer);
   *     if (!buffer.validate(glyph != nullptr)) {
   *         return false;
   *     }
   *     fMemoryIncrease += glyph->addPathFromBuffer(buffer, &fAlloc);
   *     return buffer.isValid();
   * }
   * ```
   */
  private fun mergeGlyphAndPathFromBuffer(buffer: SkReadBuffer): Boolean {
    TODO("Implement mergeGlyphAndPathFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStrike::mergeGlyphAndDrawableFromBuffer(SkReadBuffer& buffer) {
   *     SkASSERT(buffer.isValid());
   *     SkGlyph* glyph = this->mergeGlyphFromBuffer(buffer);
   *     if (!buffer.validate(glyph != nullptr)) {
   *         return false;
   *     }
   *     fMemoryIncrease += glyph->addDrawableFromBuffer(buffer, &fAlloc);
   *     return buffer.isValid();
   * }
   * ```
   */
  private fun mergeGlyphAndDrawableFromBuffer(buffer: SkReadBuffer): Boolean {
    TODO("Implement mergeGlyphAndDrawableFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrike::updateMemoryUsage(size_t increase) {
   *     if (increase > 0) {
   *         // fRemoved and the cache's total memory are managed under the cache's lock. This allows
   *         // them to be accessed under LRU operation.
   *         SkAutoMutexExclusive lock{fStrikeCache->fLock};
   *         fMemoryUsed += increase;
   *         if (!fRemoved) {
   *             fStrikeCache->fTotalMemoryUsed += increase;
   *         }
   *     }
   * }
   * ```
   */
  private fun updateMemoryUsage(increase: ULong) {
    TODO("Implement updateMemoryUsage")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkStrike::internalPrepare(
   *         SkSpan<const SkGlyphID> glyphIDs, PathDetail pathDetail, const SkGlyph** results) {
   *     const SkGlyph** cursor = results;
   *     for (auto glyphID : glyphIDs) {
   *         SkGlyph* glyph = this->glyph(SkPackedGlyphID{glyphID});
   *         if (pathDetail == kMetricsAndPath) {
   *             this->prepareForPath(glyph);
   *         }
   *         *cursor++ = glyph;
   *     }
   *
   *     return {results, glyphIDs.size()};
   * }
   * ```
   */
  private fun internalPrepare(
    glyphIDs: SkSpan<SkGlyphID>,
    pathDetail: PathDetail,
    results: Int?,
  ): SkSpan<SkGlyph?> {
    TODO("Implement internalPrepare")
  }

  public enum class PathDetail {
    kMetricsOnly,
    kMetricsAndPath,
  }

  public companion object {
    private val kMinGlyphCount: ULong = TODO("Initialize kMinGlyphCount")

    private val kMinGlyphImageSize: ULong = TODO("Initialize kMinGlyphImageSize")

    private val kMinAllocAmount: ULong = TODO("Initialize kMinAllocAmount")

    /**
     * C++ original:
     * ```cpp
     * void
     * SkStrike::FlattenGlyphsByType(SkWriteBuffer& buffer,
     *                               SkSpan<SkGlyph> images,
     *                               SkSpan<SkGlyph> paths,
     *                               SkSpan<SkGlyph> drawables) {
     *     SkASSERT_RELEASE(SkTFitsIn<int>(images.size()) &&
     *                      SkTFitsIn<int>(paths.size()) &&
     *                      SkTFitsIn<int>(drawables.size()));
     *
     *     buffer.writeInt(images.size());
     *     for (SkGlyph& glyph : images) {
     *         SkASSERT(SkMask::IsValidFormat(glyph.maskFormat()));
     *         glyph.flattenMetrics(buffer);
     *         glyph.flattenImage(buffer);
     *     }
     *
     *     buffer.writeInt(paths.size());
     *     for (SkGlyph& glyph : paths) {
     *         SkASSERT(SkMask::IsValidFormat(glyph.maskFormat()));
     *         glyph.flattenMetrics(buffer);
     *         glyph.flattenPath(buffer);
     *     }
     *
     *     buffer.writeInt(drawables.size());
     *     for (SkGlyph& glyph : drawables) {
     *         SkASSERT(SkMask::IsValidFormat(glyph.maskFormat()));
     *         glyph.flattenMetrics(buffer);
     *         glyph.flattenDrawable(buffer);
     *     }
     * }
     * ```
     */
    public fun flattenGlyphsByType(
      buffer: SkWriteBuffer,
      images: SkSpan<SkGlyph>,
      paths: SkSpan<SkGlyph>,
      drawables: SkSpan<SkGlyph>,
    ) {
      TODO("Implement flattenGlyphsByType")
    }
  }
}
