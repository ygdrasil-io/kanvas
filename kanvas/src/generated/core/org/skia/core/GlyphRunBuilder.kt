package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class GlyphRunBuilder {
 * public:
 *     GlyphRunList makeGlyphRunList(const GlyphRun& run, const SkPaint& paint, SkPoint origin);
 *     const GlyphRunList& textToGlyphRunList(const SkFont& font,
 *                                            const SkPaint& paint,
 *                                            const void* bytes,
 *                                            size_t byteLength,
 *                                            SkPoint origin,
 *                                            SkTextEncoding encoding = SkTextEncoding::kUTF8);
 *     const GlyphRunList& blobToGlyphRunList(const SkTextBlob& blob, SkPoint origin);
 *     std::tuple<SkSpan<const SkPoint>, SkSpan<const SkVector>>
 *             convertRSXForm(SkSpan<const SkRSXform> xforms);
 *
 *     bool empty() const { return fGlyphRunListStorage.empty(); }
 *
 * private:
 *     void initialize(const SkTextBlob& blob);
 *     void prepareBuffers(int positionCount, int RSXFormCount);
 *
 *     SkSpan<const SkGlyphID> textToGlyphIDs(
 *             const SkFont& font, const void* bytes, size_t byteLength, SkTextEncoding);
 *
 *     void makeGlyphRun(
 *             const SkFont& font,
 *             SkSpan<const SkGlyphID> glyphIDs,
 *             SkSpan<const SkPoint> positions,
 *             SkSpan<const char> text,
 *             SkSpan<const uint32_t> clusters,
 *             SkSpan<const SkVector> scaledRotations);
 *
 *     const GlyphRunList& setGlyphRunList(
 *             const SkTextBlob* blob, const SkRect& bounds, SkPoint origin);
 *
 *     int fMaxTotalRunSize{0};
 *     skia_private::AutoTMalloc<SkPoint> fPositions;
 *     int fMaxScaledRotations{0};
 *     skia_private::AutoTMalloc<SkVector> fScaledRotations;
 *
 *     std::vector<GlyphRun> fGlyphRunListStorage;
 *     std::optional<GlyphRunList> fGlyphRunList;  // Defaults to no value;
 *
 *     // Used as a temporary for preparing using utfN text. This implies that only one run of
 *     // glyph ids will ever be needed because blobs are already glyph based.
 *     std::vector<SkGlyphID> fScratchGlyphIDs;
 *
 * }
 * ```
 */
public data class GlyphRunBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fMaxTotalRunSize{0}
   * ```
   */
  private var fMaxTotalRunSize: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<SkPoint> fPositions
   * ```
   */
  private var fPositions: Int,
  /**
   * C++ original:
   * ```cpp
   * int fMaxScaledRotations{0}
   * ```
   */
  private var fMaxScaledRotations: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<SkVector> fScaledRotations
   * ```
   */
  private var fScaledRotations: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<GlyphRun> fGlyphRunListStorage
   * ```
   */
  private var fGlyphRunListStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * std::optional<GlyphRunList> fGlyphRunList
   * ```
   */
  private var fGlyphRunList: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkGlyphID> fScratchGlyphIDs
   * ```
   */
  private var fScratchGlyphIDs: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * GlyphRunList GlyphRunBuilder::makeGlyphRunList(
   *         const GlyphRun& run, const SkPaint& paint, SkPoint origin) {
   *     const SkRect bounds =
   *             glyphrun_source_bounds(run.font(), paint, run.source(), run.scaledRotations());
   *     return GlyphRunList{run, bounds, origin, this};
   * }
   * ```
   */
  public fun makeGlyphRunList(
    run: GlyphRun,
    paint: SkPaint,
    origin: SkPoint,
  ): GlyphRunList {
    TODO("Implement makeGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * const GlyphRunList& GlyphRunBuilder::textToGlyphRunList(
   *         const SkFont& font, const SkPaint& paint,
   *         const void* bytes, size_t byteLength, SkPoint origin,
   *         SkTextEncoding encoding) {
   *     auto glyphIDs = textToGlyphIDs(font, bytes, byteLength, encoding);
   *     SkRect bounds = SkRect::MakeEmpty();
   *     this->prepareBuffers(glyphIDs.size(), 0);
   *     if (!glyphIDs.empty()) {
   *         SkSpan<const SkPoint> positions = draw_text_positions(font, glyphIDs, {0, 0}, fPositions);
   *         this->makeGlyphRun(font,
   *                            glyphIDs,
   *                            positions,
   *                            SkSpan<const char>{},
   *                            SkSpan<const uint32_t>{},
   *                            SkSpan<const SkVector>{});
   *         auto run = fGlyphRunListStorage.front();
   *         bounds = glyphrun_source_bounds(run.font(), paint, run.source(), run.scaledRotations());
   *     }
   *
   *     return this->setGlyphRunList(nullptr, bounds, origin);
   * }
   * ```
   */
  public fun textToGlyphRunList(
    font: SkFont,
    paint: SkPaint,
    bytes: Unit?,
    byteLength: ULong,
    origin: SkPoint,
    encoding: SkTextEncoding = TODO(),
  ): GlyphRunList {
    TODO("Implement textToGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * const GlyphRunList& sktext::GlyphRunBuilder::blobToGlyphRunList(
   *         const SkTextBlob& blob, SkPoint origin) {
   *     // Pre-size all the buffers, so they don't move during processing.
   *     this->initialize(blob);
   *
   *     SkPoint* positionCursor = fPositions;
   *     SkVector* scaledRotationsCursor = fScaledRotations;
   *     for (SkTextBlobRunIterator it(&blob); !it.done(); it.next()) {
   *         size_t runSize = it.glyphCount();
   *         if (runSize == 0 || !SkFontPriv::IsFinite(it.font())) {
   *             // If no glyphs or the font is not finite, don't add the run.
   *             continue;
   *         }
   *
   *         const SkFont& font = it.font();
   *         auto glyphIDs = SkSpan<const SkGlyphID>{it.glyphs(), runSize};
   *
   *         SkSpan<const SkPoint> positions;
   *         SkSpan<const SkVector> scaledRotations;
   *         switch (it.positioning()) {
   *             case SkTextBlobRunIterator::kDefault_Positioning: {
   *                 positions = draw_text_positions(font, glyphIDs, it.offset(), positionCursor);
   *                 positionCursor += positions.size();
   *                 break;
   *             }
   *             case SkTextBlobRunIterator::kHorizontal_Positioning: {
   *                 positions = SkSpan(positionCursor, runSize);
   *                 for (auto x : SkSpan<const SkScalar>{it.pos(), glyphIDs.size()}) {
   *                     *positionCursor++ = SkPoint::Make(x, it.offset().y());
   *                 }
   *                 break;
   *             }
   *             case SkTextBlobRunIterator::kFull_Positioning: {
   *                 positions = SkSpan(it.points(), runSize);
   *                 break;
   *             }
   *             case SkTextBlobRunIterator::kRSXform_Positioning: {
   *                 positions = SkSpan(positionCursor, runSize);
   *                 scaledRotations = SkSpan(scaledRotationsCursor, runSize);
   *                 for (const SkRSXform& xform : SkSpan(it.xforms(), runSize)) {
   *                     *positionCursor++ = {xform.fTx, xform.fTy};
   *                     *scaledRotationsCursor++ = {xform.fSCos, xform.fSSin};
   *                 }
   *                 break;
   *             }
   *         }
   *
   *         const uint32_t* clusters = it.clusters();
   *         this->makeGlyphRun(
   *                 font,
   *                 glyphIDs,
   *                 positions,
   *                 SkSpan<const char>(it.text(), it.textSize()),
   *                 SkSpan<const uint32_t>(clusters, clusters ? runSize : 0),
   *                 scaledRotations);
   *     }
   *
   *     return this->setGlyphRunList(&blob, blob.bounds(), origin);
   * }
   * ```
   */
  public fun blobToGlyphRunList(blob: SkTextBlob, origin: SkPoint): GlyphRunList {
    TODO("Implement blobToGlyphRunList")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkSpan<const SkPoint>, SkSpan<const SkVector>>
   * GlyphRunBuilder::convertRSXForm(SkSpan<const SkRSXform> xforms) {
   *     const int count = SkCount(xforms);
   *     this->prepareBuffers(count, count);
   *     auto positions = SkSpan(fPositions.get(), count);
   *     auto scaledRotations = SkSpan(fScaledRotations.get(), count);
   *     for (auto [pos, sr, xform] : SkMakeZip(positions, scaledRotations, xforms)) {
   *         auto [scos, ssin, tx, ty] = xform;
   *         pos = {tx, ty};
   *         sr = {scos, ssin};
   *     }
   *     return {positions, scaledRotations};
   * }
   * ```
   */
  public fun convertRSXForm(xforms: SkSpan<SkRSXform>): Int {
    TODO("Implement convertRSXForm")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fGlyphRunListStorage.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphRunBuilder::initialize(const SkTextBlob& blob) {
   *     int positionCount = 0;
   *     int rsxFormCount = 0;
   *     for (SkTextBlobRunIterator it(&blob); !it.done(); it.next()) {
   *         if (it.positioning() != SkTextBlobRunIterator::kFull_Positioning) {
   *             positionCount += it.glyphCount();
   *         }
   *         if (it.positioning() == SkTextBlobRunIterator::kRSXform_Positioning) {
   *             rsxFormCount += it.glyphCount();
   *         }
   *     }
   *
   *     prepareBuffers(positionCount, rsxFormCount);
   * }
   * ```
   */
  private fun initialize(blob: SkTextBlob) {
    TODO("Implement initialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphRunBuilder::prepareBuffers(int positionCount, int RSXFormCount) {
   *     if (positionCount > fMaxTotalRunSize) {
   *         fMaxTotalRunSize = positionCount;
   *         fPositions.reset(fMaxTotalRunSize);
   *     }
   *
   *     if (RSXFormCount > fMaxScaledRotations) {
   *         fMaxScaledRotations = RSXFormCount;
   *         fScaledRotations.reset(RSXFormCount);
   *     }
   *
   *     fGlyphRunListStorage.clear();
   * }
   * ```
   */
  private fun prepareBuffers(positionCount: Int, rSXFormCount: Int) {
    TODO("Implement prepareBuffers")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyphID> GlyphRunBuilder::textToGlyphIDs(
   *         const SkFont& font, const void* bytes, size_t byteLength, SkTextEncoding encoding) {
   *     if (encoding != SkTextEncoding::kGlyphID) {
   *         int count = font.countText(bytes, byteLength, encoding);
   *         if (count > 0) {
   *             fScratchGlyphIDs.resize(count);
   *             font.textToGlyphs(bytes, byteLength, encoding, fScratchGlyphIDs);
   *             return SkSpan(fScratchGlyphIDs);
   *         } else {
   *             return SkSpan<const SkGlyphID>();
   *         }
   *     } else {
   *         return SkSpan<const SkGlyphID>((const SkGlyphID*)bytes, byteLength / 2);
   *     }
   * }
   * ```
   */
  private fun textToGlyphIDs(
    font: SkFont,
    bytes: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
  ): SkSpan<SkGlyphID> {
    TODO("Implement textToGlyphIDs")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphRunBuilder::makeGlyphRun(
   *         const SkFont& font,
   *         SkSpan<const SkGlyphID> glyphIDs,
   *         SkSpan<const SkPoint> positions,
   *         SkSpan<const char> text,
   *         SkSpan<const uint32_t> clusters,
   *         SkSpan<const SkVector> scaledRotations) {
   *
   *     // Ignore empty runs.
   *     if (!glyphIDs.empty()) {
   *         fGlyphRunListStorage.emplace_back(
   *                 font,
   *                 positions,
   *                 glyphIDs,
   *                 text,
   *                 clusters,
   *                 scaledRotations);
   *     }
   * }
   * ```
   */
  private fun makeGlyphRun(
    font: SkFont,
    glyphIDs: SkSpan<SkGlyphID>,
    positions: SkSpan<SkPoint>,
    text: SkSpan<Char>,
    clusters: SkSpan<UInt>,
    scaledRotations: SkSpan<SkVector>,
  ) {
    TODO("Implement makeGlyphRun")
  }

  /**
   * C++ original:
   * ```cpp
   * const GlyphRunList& sktext::GlyphRunBuilder::setGlyphRunList(
   *         const SkTextBlob* blob, const SkRect& bounds, SkPoint origin) {
   *     fGlyphRunList.emplace(blob, bounds, origin, SkSpan(fGlyphRunListStorage), this);
   *     return fGlyphRunList.value();
   * }
   * ```
   */
  private fun setGlyphRunList(
    blob: SkTextBlob?,
    bounds: SkRect,
    origin: SkPoint,
  ): GlyphRunList {
    TODO("Implement setGlyphRunList")
  }
}
