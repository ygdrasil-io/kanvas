package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.GlyphRunList
import org.skia.core.SkCanvas
import org.skia.core.SkStrikeDeviceInfo
import org.skia.core.StrikeForGPUCacheInterface
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.utils.SkStrikeClient
import undefined.AtlasDrawDelegate

/**
 * C++ original:
 * ```cpp
 * class SubRunContainer {
 * public:
 *     explicit SubRunContainer(const SkMatrix& initialPositionMatrix);
 *     SubRunContainer() = delete;
 *     SubRunContainer(const SubRunContainer&) = delete;
 *     SubRunContainer& operator=(const SubRunContainer&) = delete;
 *
 *     // Delete the move operations because the SubRuns contain pointers to fInitialPositionMatrix.
 *     SubRunContainer(SubRunContainer&&) = delete;
 *     SubRunContainer& operator=(SubRunContainer&&) = delete;
 *
 *     void flattenAllocSizeHint(SkWriteBuffer& buffer) const;
 *     static int AllocSizeHintFromBuffer(SkReadBuffer& buffer);
 *
 *     void flattenRuns(SkWriteBuffer& buffer) const;
 *     static SubRunContainerOwner MakeFromBufferInAlloc(SkReadBuffer& buffer,
 *                                                       const SkStrikeClient* client,
 *                                                       SubRunAllocator* alloc);
 *
 *     enum SubRunCreationBehavior {kAddSubRuns, kStrikeCalculationsOnly};
 *     // The returned SubRunContainerOwner will never be null. If subRunCreation ==
 *     // kStrikeCalculationsOnly, then the returned container will be empty.
 *     [[nodiscard]] static SubRunContainerOwner MakeInAlloc(const GlyphRunList& glyphRunList,
 *                                                           const SkMatrix& positionMatrix,
 *                                                           const SkPaint& runPaint,
 *                                                           SkStrikeDeviceInfo strikeDeviceInfo,
 *                                                           StrikeForGPUCacheInterface* strikeCache,
 *                                                           sktext::gpu::SubRunAllocator* alloc,
 *                                                           SubRunCreationBehavior creationBehavior,
 *                                                           const char* tag);
 *
 *     static size_t EstimateAllocSize(const GlyphRunList& glyphRunList);
 *
 *     void draw(SkCanvas*, SkPoint drawOrigin, const SkPaint&, const SkRefCnt* subRunStorage,
 *               const AtlasDrawDelegate&) const;
 *
 *     const SkMatrix& initialPosition() const { return fInitialPositionMatrix; }
 *     bool isEmpty() const { return fSubRuns.isEmpty(); }
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const;
 *
 * private:
 *     friend class TextBlobTools;
 *     const SkMatrix fInitialPositionMatrix;
 *     SubRunList fSubRuns;
 * }
 * ```
 */
public data class SubRunContainer public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fInitialPositionMatrix
   * ```
   */
  private val fInitialPositionMatrix: Int,
  /**
   * C++ original:
   * ```cpp
   * SubRunList fSubRuns
   * ```
   */
  private var fSubRuns: SubRunList,
) {
  /**
   * C++ original:
   * ```cpp
   * SubRunContainer& operator=(const SubRunContainer&) = delete
   * ```
   */
  public fun assign(param0: SubRunContainer) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunContainer& operator=(SubRunContainer&&) = delete
   * ```
   */
  public fun flattenAllocSizeHint(buffer: SkWriteBuffer) {
    TODO("Implement flattenAllocSizeHint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SubRunContainer::flattenAllocSizeHint(SkWriteBuffer& buffer) const {
   *     int unflattenSizeHint = 0;
   *     for (auto& subrun : fSubRuns) {
   *         unflattenSizeHint += subrun.unflattenSize();
   *     }
   *     buffer.writeInt(unflattenSizeHint);
   * }
   * ```
   */
  public fun flattenRuns(buffer: SkWriteBuffer) {
    TODO("Implement flattenRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void SubRunContainer::flattenRuns(SkWriteBuffer& buffer) const {
   *     buffer.writeMatrix(fInitialPositionMatrix);
   *     int subRunCount = 0;
   *     for ([[maybe_unused]] auto& subRun : fSubRuns) {
   *         subRunCount += 1;
   *     }
   *     buffer.writeInt(subRunCount);
   *     for (auto& subRun : fSubRuns) {
   *         subRun.flatten(buffer);
   *     }
   * }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    drawOrigin: SkPoint,
    paint: SkPaint,
    subRunStorage: SkRefCnt?,
    atlasDelegate: AtlasDrawDelegate,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void SubRunContainer::draw(SkCanvas* canvas,
   *                            SkPoint drawOrigin,
   *                            const SkPaint& paint,
   *                            const SkRefCnt* subRunStorage,
   *                            const AtlasDrawDelegate& atlasDelegate) const {
   *     for (auto& subRun : fSubRuns) {
   *         subRun.draw(canvas, drawOrigin, paint, sk_ref_sp(subRunStorage), atlasDelegate);
   *     }
   * }
   * ```
   */
  public fun initialPosition(): Int {
    TODO("Implement initialPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& initialPosition() const { return fInitialPositionMatrix; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fSubRuns.isEmpty(); }
   * ```
   */
  public fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean {
    TODO("Implement canReuse")
  }

  public enum class SubRunCreationBehavior {
    kAddSubRuns,
    kStrikeCalculationsOnly,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * int SubRunContainer::AllocSizeHintFromBuffer(SkReadBuffer& buffer) {
     *     int subRunsSizeHint = buffer.readInt();
     *
     *     // Since the hint doesn't affect correctness, if it looks fishy just pick a reasonable
     *     // value.
     *     if (subRunsSizeHint < 0 || (1 << 16) < subRunsSizeHint) {
     *         subRunsSizeHint = 128;
     *     }
     *     return subRunsSizeHint;
     * }
     * ```
     */
    public fun allocSizeHintFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement allocSizeHintFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * SubRunContainerOwner SubRunContainer::MakeFromBufferInAlloc(SkReadBuffer& buffer,
     *                                                             const SkStrikeClient* client,
     *                                                             SubRunAllocator* alloc) {
     *     SkMatrix positionMatrix;
     *     buffer.readMatrix(&positionMatrix);
     *     if (!buffer.isValid()) { return nullptr; }
     *     SubRunContainerOwner container = alloc->makeUnique<SubRunContainer>(positionMatrix);
     *
     *     int subRunCount = buffer.readInt();
     *     if (!buffer.validate(subRunCount > 0)) { return nullptr; }
     *     for (int i = 0; i < subRunCount; ++i) {
     *         auto subRunOwner = SubRun::MakeFromBuffer(buffer, alloc, client);
     *         if (!buffer.validate(subRunOwner != nullptr)) { return nullptr; }
     *         if (subRunOwner != nullptr) {
     *             container->fSubRuns.append(std::move(subRunOwner));
     *         }
     *     }
     *     return container;
     * }
     * ```
     */
    public fun makeFromBufferInAlloc(
      buffer: SkReadBuffer,
      client: SkStrikeClient?,
      alloc: SubRunAllocator?,
    ): Int {
      TODO("Implement makeFromBufferInAlloc")
    }

    /**
     * C++ original:
     * ```cpp
     * SubRunContainerOwner SubRunContainer::MakeInAlloc(
     *         const GlyphRunList& glyphRunList,
     *         const SkMatrix& positionMatrix,
     *         const SkPaint& runPaint,
     *         SkStrikeDeviceInfo strikeDeviceInfo,
     *         StrikeForGPUCacheInterface* strikeCache,
     *         SubRunAllocator* alloc,
     *         SubRunCreationBehavior creationBehavior,
     *         const char* tag) {
     *     SkASSERT(alloc != nullptr);
     *     SkASSERT(strikeDeviceInfo.fSubRunControl != nullptr);
     *
     *     SubRunContainerOwner container = alloc->makeUnique<SubRunContainer>(positionMatrix);
     *     // If there is no SubRunControl description ignore all SubRuns.
     *     if (strikeDeviceInfo.fSubRunControl == nullptr) {
     *         return container;
     *     }
     *
     *     const SkSurfaceProps deviceProps = strikeDeviceInfo.fSurfaceProps;
     *     const SkScalerContextFlags scalerContextFlags = strikeDeviceInfo.fScalerContextFlags;
     *     const SubRunControl* subRunControl = strikeDeviceInfo.fSubRunControl;
     * #if !defined(SK_DISABLE_SDF_TEXT)
     *     const SkScalar maxMaskSize = subRunControl->maxSize();
     * #else
     *     const SkScalar maxMaskSize = 256;
     * #endif
     *
     *     // TODO: hoist the buffer structure to the GlyphRunBuilder. The buffer structure here is
     *     //  still begin tuned, and this is expected to be slower until tuned.
     *     const int maxGlyphRunSize = glyphRunList.maxGlyphRunSize();
     *
     *     // Accepted buffers.
     *     STArray<64, SkPackedGlyphID> acceptedPackedGlyphIDs;
     *     STArray<64, SkGlyphID> acceptedGlyphIDs;
     *     STArray<64, SkPoint> acceptedPositions;
     *     STArray<64, SkMask::Format> acceptedFormats;
     *     acceptedPackedGlyphIDs.resize(maxGlyphRunSize);
     *     acceptedGlyphIDs.resize(maxGlyphRunSize);
     *     acceptedPositions.resize(maxGlyphRunSize);
     *     acceptedFormats.resize(maxGlyphRunSize);
     *
     *     // Rejected buffers.
     *     STArray<64, SkGlyphID> rejectedGlyphIDs;
     *     STArray<64, SkPoint> rejectedPositions;
     *     rejectedGlyphIDs.resize(maxGlyphRunSize);
     *     rejectedPositions.resize(maxGlyphRunSize);
     *     const auto rejectedBuffer = SkMakeZip(rejectedGlyphIDs, rejectedPositions);
     *
     *     const SkPoint glyphRunListLocation = glyphRunList.sourceBounds().center();
     *
     *     // Handle all the runs in the glyphRunList
     *     for (auto& glyphRun : glyphRunList) {
     *         SkZip<const SkGlyphID, const SkPoint> source = glyphRun.source();
     *         const SkFont& runFont = glyphRun.font();
     *
     *         const SkScalar approximateDeviceTextSize =
     *                 // Since the positionMatrix has the origin prepended, use the plain
     *                 // sourceBounds from above.
     *                 SkFontPriv::ApproximateTransformedTextSize(runFont, positionMatrix,
     *                                                            glyphRunListLocation);
     *
     *         // Atlas mask cases - SDFT and direct mask
     *         // Only consider using direct or SDFT drawing if not drawing hairlines and not too big.
     *         if ((runPaint.getStyle() != SkPaint::kStroke_Style || runPaint.getStrokeWidth() != 0) &&
     *                 approximateDeviceTextSize < maxMaskSize) {
     *
     * #if !defined(SK_DISABLE_SDF_TEXT)
     *             // SDFT case
     *             if (subRunControl->isSDFT(approximateDeviceTextSize, runPaint, positionMatrix)) {
     *                 // Process SDFT - This should be the .009% case.
     *                 const auto& [strikeSpec, strikeToSourceScale, matrixRange] =
     *                         make_sdft_strike_spec(
     *                                 runFont, runPaint, deviceProps, positionMatrix,
     *                                 glyphRunListLocation, *subRunControl);
     *
     *                 if (!SkScalarNearlyZero(strikeToSourceScale)) {
     *                     sk_sp<StrikeForGPU> strike = strikeSpec.findOrCreateScopedStrike(strikeCache);
     *
     *                     // The creationMatrix needs to scale the strike data when inverted and
     *                     // multiplied by the positionMatrix. The final CTM should be:
     *                     //   [positionMatrix][scale by strikeToSourceScale],
     *                     // which should equal the following because of the transform during the vertex
     *                     // calculation,
     *                     //   [positionMatrix][creationMatrix]^-1.
     *                     // So, the creation matrix needs to be
     *                     //   [scale by 1/strikeToSourceScale].
     *                     SkMatrix creationMatrix =
     *                             SkMatrix::Scale(1.f/strikeToSourceScale, 1.f/strikeToSourceScale);
     *
     *                     auto acceptedBuffer = SkMakeZip(acceptedPackedGlyphIDs, acceptedPositions);
     *                     auto [accepted, rejected, creationBounds] = prepare_for_SDFT_drawing(
     *                             strike.get(), creationMatrix, source, acceptedBuffer, rejectedBuffer);
     *                     source = rejected;
     *
     *                     if (creationBehavior == kAddSubRuns && !accepted.empty()) {
     *                         container->fSubRuns.append(SDFTSubRun::Make(
     *                                 accepted,
     *                                 runFont,
     *                                 strike->strikePromise(),
     *                                 creationMatrix,
     *                                 creationBounds,
     *                                 matrixRange,
     *                                 alloc));
     *                     }
     *                 }
     *             }
     * #endif  // !defined(SK_DISABLE_SDF_TEXT)
     *             // Mask filters with 3D format (e.g. EmbossMaskFilter) need to be drawn as a path
     *             // in order to apply the filter through the Canvas AutoLayer system.
     *             const bool needsAutoLayer = runPaint.getMaskFilter() &&
     *                 as_MFB(runPaint.getMaskFilter())->getFormat() == SkMask::k3D_Format;
     *             // Direct Mask case
     *             // Handle all the directly mapped mask subruns.
     *             if (!source.empty() && !positionMatrix.hasPerspective() && !needsAutoLayer) {
     *                 // Process masks including ARGB - this should be the 99.99% case.
     *                 // This will handle medium size emoji that are sharing the run with SDFT drawn text.
     *                 // If things are too big they will be passed along to the drawing of last resort
     *                 // below.
     *                 SkStrikeSpec strikeSpec = SkStrikeSpec::MakeMask(
     *                         runFont, runPaint, deviceProps, scalerContextFlags, positionMatrix);
     *
     *                 sk_sp<StrikeForGPU> strike = strikeSpec.findOrCreateScopedStrike(strikeCache);
     *
     *                 auto acceptedBuffer = SkMakeZip(acceptedPackedGlyphIDs,
     *                                                 acceptedPositions,
     *                                                 acceptedFormats);
     *                 auto [accepted, rejected, creationBounds] = prepare_for_direct_mask_drawing(
     *                         strike.get(), positionMatrix, source, acceptedBuffer, rejectedBuffer);
     *                 source = rejected;
     *
     *                 if (creationBehavior == kAddSubRuns && !accepted.empty()) {
     *                     auto addGlyphsWithSameFormat =
     *                         [&, bounds = creationBounds](
     *                                 SkZip<const SkPackedGlyphID, const SkPoint> subrun,
     *                                 MaskFormat format) {
     *                             container->fSubRuns.append(
     *                                     DirectMaskSubRun::Make(bounds,
     *                                                            subrun,
     *                                                            container->initialPosition(),
     *                                                            strike->strikePromise(),
     *                                                            format,
     *                                                            alloc));
     *                         };
     *                     add_multi_mask_format(addGlyphsWithSameFormat, accepted);
     *                 }
     *             }
     *         }
     *
     *         // Drawable case
     *         // Handle all the drawable glyphs - usually large or perspective color glyphs.
     *         if (!source.empty()) {
     *             auto [strikeSpec, strikeToSourceScale] =
     *                     SkStrikeSpec::MakePath(runFont, runPaint, deviceProps, scalerContextFlags);
     *
     *             if (!SkScalarNearlyZero(strikeToSourceScale)) {
     *                 sk_sp<StrikeForGPU> strike = strikeSpec.findOrCreateScopedStrike(strikeCache);
     *
     *                 auto acceptedBuffer = SkMakeZip(acceptedGlyphIDs, acceptedPositions);
     *                 auto [accepted, rejected] =
     *                 prepare_for_drawable_drawing(strike.get(), source, acceptedBuffer, rejectedBuffer);
     *                 source = rejected;
     *
     *                 if (creationBehavior == kAddSubRuns && !accepted.empty()) {
     *                     container->fSubRuns.append(
     *                             DrawableSubRun::Make(
     *                                 accepted,
     *                                 strikeToSourceScale,
     *                                 strike->strikePromise(),
     *                                 alloc));
     *                 }
     *             }
     *         }
     *
     *         // Path case
     *         // Handle path subruns. Mainly, large or large perspective glyphs with no color.
     *         if (!source.empty()) {
     *             auto [strikeSpec, strikeToSourceScale] =
     *                     SkStrikeSpec::MakePath(runFont, runPaint, deviceProps, scalerContextFlags);
     *
     *             if (!SkScalarNearlyZero(strikeToSourceScale)) {
     *                 sk_sp<StrikeForGPU> strike = strikeSpec.findOrCreateScopedStrike(strikeCache);
     *
     *                 auto acceptedBuffer = SkMakeZip(acceptedGlyphIDs, acceptedPositions);
     *                 auto [accepted, rejected] =
     *                 prepare_for_path_drawing(strike.get(), source, acceptedBuffer, rejectedBuffer);
     *                 source = rejected;
     *
     *                 if (creationBehavior == kAddSubRuns && !accepted.empty()) {
     *                     const bool isAntiAliased =
     *                             subRunControl->forcePathAA() || has_some_antialiasing(runFont);
     *                     container->fSubRuns.append(
     *                             PathSubRun::Make(accepted,
     *                                              isAntiAliased,
     *                                              strikeToSourceScale,
     *                                              strike->strikePromise(),
     *                                              alloc));
     *                 }
     *             }
     *         }
     *
     *         // Drawing of last resort case
     *         // Draw all the rest of the rejected glyphs from above. This scales out of the atlas to
     *         // the screen, so quality will suffer. This mainly handles large color or perspective
     *         // color not handled by Drawables.
     *         if (!source.empty() && !SkScalarNearlyZero(approximateDeviceTextSize)) {
     *             // Creation matrix will be changed below to meet the following criteria:
     *             // * No perspective - the font scaler and the strikes can't handle perspective masks.
     *             // * Fits atlas - creationMatrix will be conditioned so that the maximum glyph
     *             //   dimension for this run will be <  kMaxBilerpAtlasDimension.
     *             SkMatrix creationMatrix = positionMatrix;
     *
     *             // Condition creationMatrix for perspective.
     *             if (creationMatrix.hasPerspective()) {
     *                 // Find a scale factor that reduces pixelation caused by keystoning.
     *                 SkPoint center = glyphRunList.sourceBounds().center();
     *                 SkScalar maxAreaScale = SkMatrixPriv::DifferentialAreaScale(creationMatrix, center);
     *                 SkScalar perspectiveFactor = 1;
     *                 if (SkIsFinite(maxAreaScale) && !SkScalarNearlyZero(maxAreaScale)) {
     *                     perspectiveFactor = SkScalarSqrt(maxAreaScale);
     *                 }
     *
     *                 // Masks can not be created in perspective. Create a non-perspective font with a
     *                 // scale that will support the perspective keystoning.
     *                 creationMatrix = SkMatrix::Scale(perspectiveFactor, perspectiveFactor);
     *             }
     *
     *             // Reduce to make a one pixel border for the bilerp padding.
     *             static const constexpr SkScalar kMaxBilerpAtlasDimension =
     *                     SkGlyphDigest::kSkSideTooBigForAtlas - 2;
     *
     *             // Get the raw glyph IDs to simulate device drawing to figure the maximum device
     *             // dimension.
     *             const SkSpan<const SkGlyphID> glyphs = get_glyphIDs(source);
     *
     *             // maxGlyphDimension always returns an integer even though the return type is SkScalar.
     *             auto maxGlyphDimension = [&](const SkMatrix& m) {
     *                 const SkStrikeSpec strikeSpec = SkStrikeSpec::MakeTransformMask(
     *                         runFont, runPaint, deviceProps, scalerContextFlags, m);
     *                 const sk_sp<StrikeForGPU> gaugingStrike =
     *                         strikeSpec.findOrCreateScopedStrike(strikeCache);
     *                 const SkScalar maxDimension =
     *                         find_maximum_glyph_dimension(gaugingStrike.get(), glyphs);
     *                 // TODO: There is a problem where a small character (say .) and a large
     *                 //  character (say M) are in the same run. If the run is scaled to be very
     *                 //  large, then the M may return 0 because its dimensions are > 65535, but
     *                 //  the small character produces regular result because its largest dimension
     *                 //  is < 65535. This will create an improper scale factor causing the M to be
     *                 //  too large to fit in the atlas. Tracked by skbug.com/40044801.
     *                 return maxDimension;
     *             };
     *
     *             // Condition the creationMatrix so that glyphs fit in the atlas.
     *             for (SkScalar maxDimension = maxGlyphDimension(creationMatrix);
     *                  kMaxBilerpAtlasDimension < maxDimension;
     *                  maxDimension = maxGlyphDimension(creationMatrix))
     *             {
     *                 // The SkScalerContext has a limit of 65536 maximum dimension.
     *                 // reductionFactor will always be < 1 because
     *                 // maxDimension > kMaxBilerpAtlasDimension, and because maxDimension will always
     *                 // be an integer the reduction factor will always be at most 254 / 255.
     *                 SkScalar reductionFactor = kMaxBilerpAtlasDimension / maxDimension;
     *                 creationMatrix.postScale(reductionFactor, reductionFactor);
     *             }
     *
     *             // Draw using the creationMatrix.
     *             SkStrikeSpec strikeSpec = SkStrikeSpec::MakeTransformMask(
     *                     runFont, runPaint, deviceProps, scalerContextFlags, creationMatrix);
     *
     *             sk_sp<StrikeForGPU> strike = strikeSpec.findOrCreateScopedStrike(strikeCache);
     *
     *             auto acceptedBuffer =
     *                     SkMakeZip(acceptedPackedGlyphIDs, acceptedPositions, acceptedFormats);
     *             auto [accepted, rejected, creationBounds] =
     *                 prepare_for_mask_drawing(
     *                         strike.get(), creationMatrix, source, acceptedBuffer, rejectedBuffer);
     *             source = rejected;
     *
     *             if (creationBehavior == kAddSubRuns && !accepted.empty()) {
     *
     *                 auto addGlyphsWithSameFormat =
     *                         [&, bounds = creationBounds](
     *                                 SkZip<const SkPackedGlyphID, const SkPoint> subrun,
     *                                 MaskFormat format) {
     *                             container->fSubRuns.append(
     *                                     TransformedMaskSubRun::Make(subrun,
     *                                                                 container->initialPosition(),
     *                                                                 strike->strikePromise(),
     *                                                                 creationMatrix,
     *                                                                 bounds,
     *                                                                 format,
     *                                                                 alloc));
     *                         };
     *                 add_multi_mask_format(addGlyphsWithSameFormat, accepted);
     *             }
     *         }
     *     }
     *
     *     return container;
     * }
     * ```
     */
    public fun makeInAlloc(
      glyphRunList: GlyphRunList,
      positionMatrix: SkMatrix,
      runPaint: SkPaint,
      strikeDeviceInfo: SkStrikeDeviceInfo,
      strikeCache: StrikeForGPUCacheInterface?,
      alloc: SubRunAllocator?,
      creationBehavior: SubRunCreationBehavior,
      tag: String?,
    ): Int {
      TODO("Implement makeInAlloc")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SubRunContainer::EstimateAllocSize(const GlyphRunList& glyphRunList) {
     *     // The difference in alignment from the per-glyph data to the SubRun;
     *     constexpr size_t alignDiff = alignof(DirectMaskSubRun) - alignof(SkPoint);
     *     constexpr size_t vertexDataToSubRunPadding = alignDiff > 0 ? alignDiff : 0;
     *     size_t totalGlyphCount = glyphRunList.totalGlyphCount();
     *     // This is optimized for DirectMaskSubRun which is by far the most common case.
     *     return totalGlyphCount * sizeof(SkPoint)
     *            + GlyphVector::GlyphVectorSize(totalGlyphCount)
     *            + glyphRunList.runCount() * (sizeof(DirectMaskSubRun) + vertexDataToSubRunPadding)
     *            + sizeof(SubRunContainer);
     * }
     * ```
     */
    public fun estimateAllocSize(glyphRunList: GlyphRunList): Int {
      TODO("Implement estimateAllocSize")
    }
  }
}
