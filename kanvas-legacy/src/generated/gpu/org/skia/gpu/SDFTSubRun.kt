package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrikePromise
import org.skia.core.SkZip
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.utils.SkStrikeClient
import undefined.AtlasDrawDelegate
import undefined.GlyphParams
import undefined.RegenerateAtlasDelegate
import undefined.SubRunStreamTag

/**
 * C++ original:
 * ```cpp
 * class SDFTSubRun final : public AtlasSubRun {
 * public:
 *     SDFTSubRun(bool useLCDText,
 *                bool antiAliased,
 *                const SDFTMatrixRange& matrixRange,
 *                VertexFiller&& vertexFiller,
 *                GlyphVector&& glyphs)
 *             : AtlasSubRun(std::move(vertexFiller), std::move(glyphs))
 *             , fUseLCDText{useLCDText}
 *             , fAntiAliased{antiAliased}
 *             , fMatrixRange{matrixRange} {
 *         SkASSERT(fVertexFiller.grMaskType() == MaskFormat::kA8);
 *     }
 *
 *     static SubRunOwner Make(SkZip<const SkPackedGlyphID, const SkPoint> accepted,
 *                             const SkFont& runFont,
 *                             SkStrikePromise&& strikePromise,
 *                             const SkMatrix& creationMatrix,
 *                             SkRect creationBounds,
 *                             const SDFTMatrixRange& matrixRange,
 *                             SubRunAllocator* alloc) {
 *         auto vertexFiller = VertexFiller::Make(MaskFormat::kA8,
 *                                                creationMatrix,
 *                                                creationBounds,
 *                                                get_positions(accepted),
 *                                                alloc,
 *                                                kIsTransformed);
 *
 *         auto glyphVector = GlyphVector::Make(
 *                 std::move(strikePromise), get_packedIDs(accepted), alloc);
 *
 *         return alloc->makeUnique<SDFTSubRun>(
 *                 runFont.getEdging() == SkFont::Edging::kSubpixelAntiAlias,
 *                 has_some_antialiasing(runFont),
 *                 matrixRange,
 *                 std::move(vertexFiller),
 *                 std::move(glyphVector));
 *     }
 *
 *     static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
 *                                       SubRunAllocator* alloc,
 *                                       const SkStrikeClient* client) {
 *         int useLCD = buffer.readInt();
 *         int isAntiAliased = buffer.readInt();
 *         SDFTMatrixRange matrixRange = SDFTMatrixRange::MakeFromBuffer(buffer);
 *         auto vertexFiller = VertexFiller::MakeFromBuffer(buffer, alloc);
 *         if (!buffer.validate(vertexFiller.has_value())) { return nullptr; }
 *         if (!buffer.validate(vertexFiller.value().grMaskType() == MaskFormat::kA8)) {
 *             return nullptr;
 *         }
 *         auto glyphVector = GlyphVector::MakeFromBuffer(buffer, client, alloc);
 *         if (!buffer.validate(glyphVector.has_value())) { return nullptr; }
 *         if (!buffer.validate(SkCount(glyphVector->glyphs()) == vertexFiller->count())) {
 *             return nullptr;
 *         }
 *         return alloc->makeUnique<SDFTSubRun>(useLCD,
 *                                              isAntiAliased,
 *                                              matrixRange,
 *                                              std::move(*vertexFiller),
 *                                              std::move(*glyphVector));
 *     }
 *
 *     int unflattenSize() const override {
 *         return sizeof(SDFTSubRun) + fGlyphs.unflattenSize() + fVertexFiller.unflattenSize();
 *     }
 *
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override {
 *         return fMatrixRange.matrixInRange(positionMatrix);
 *     }
 *
 *     const AtlasSubRun* testingOnly_atlasSubRun() const override { return this; }
 *
 *     void testingOnly_packedGlyphIDToGlyph(StrikeCache *cache) const override {
 *         fGlyphs.packedGlyphIDToGlyph(cache);
 *     }
 *
 *     int glyphSrcPadding() const override { return SK_DistanceFieldInset; }
 *
 *     void draw(SkCanvas*,
 *               SkPoint drawOrigin,
 *               const SkPaint& paint,
 *               sk_sp<SkRefCnt> subRunStorage,
 *               const AtlasDrawDelegate& drawAtlas) const override {
 *         drawAtlas(this, drawOrigin, paint, std::move(subRunStorage),
 *                   {/* isSDF = */true, /* isLCD = */fUseLCDText, skgpu::MaskFormat::kA8});
 *     }
 *
 *     std::tuple<bool, SkRect> deviceRectAndNeedsTransform(
 *             const SkMatrix &positionMatrix) const override {
 *         auto [_, deviceRect] = fVertexFiller.deviceRectAndCheckTransform(positionMatrix);
 *         return {true, deviceRect};
 *     }
 *
 *     GlyphParams glyphParams() const override {
 *         return { /*isSDF=*/true, fUseLCDText, /*isAA=*/fAntiAliased };
 *     }
 *
 *     std::tuple<bool, int> regenerateAtlas(int begin, int end,
 *                                           RegenerateAtlasDelegate regenerateAtlas) const override {
 *         return regenerateAtlas(&fGlyphs, begin, end, MaskFormat::kA8, this->glyphSrcPadding());
 *     }
 *
 * protected:
 *     SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kSDFTStreamTag; }
 *     void doFlatten(SkWriteBuffer& buffer) const override {
 *         buffer.writeInt(fUseLCDText);
 *         buffer.writeInt(fAntiAliased);
 *         fMatrixRange.flatten(buffer);
 *         fVertexFiller.flatten(buffer);
 *         fGlyphs.flatten(buffer);
 *     }
 *
 * private:
 *     const bool fUseLCDText;
 *     const bool fAntiAliased;
 *     const SDFTMatrixRange fMatrixRange;
 * }
 * ```
 */
public class SDFTSubRun public constructor(
  useLCDText: Boolean,
  antiAliased: Boolean,
  matrixRange: SDFTMatrixRange,
  vertexFiller: VertexFiller,
  glyphs: GlyphVector,
) : AtlasSubRun(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const bool fUseLCDText
   * ```
   */
  private val fUseLCDText: Boolean = TODO("Initialize fUseLCDText")

  /**
   * C++ original:
   * ```cpp
   * const bool fAntiAliased
   * ```
   */
  private val fAntiAliased: Boolean = TODO("Initialize fAntiAliased")

  /**
   * C++ original:
   * ```cpp
   * const SDFTMatrixRange fMatrixRange
   * ```
   */
  private val fMatrixRange: SDFTMatrixRange = TODO("Initialize fMatrixRange")

  /**
   * C++ original:
   * ```cpp
   * int unflattenSize() const override {
   *         return sizeof(SDFTSubRun) + fGlyphs.unflattenSize() + fVertexFiller.unflattenSize();
   *     }
   * ```
   */
  public override fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override {
   *         return fMatrixRange.matrixInRange(positionMatrix);
   *     }
   * ```
   */
  public override fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean {
    TODO("Implement canReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * const AtlasSubRun* testingOnly_atlasSubRun() const override { return this; }
   * ```
   */
  public override fun testingOnlyAtlasSubRun(): AtlasSubRun {
    TODO("Implement testingOnlyAtlasSubRun")
  }

  /**
   * C++ original:
   * ```cpp
   * void testingOnly_packedGlyphIDToGlyph(StrikeCache *cache) const override {
   *         fGlyphs.packedGlyphIDToGlyph(cache);
   *     }
   * ```
   */
  public override fun testingOnlyPackedGlyphIDToGlyph(cache: StrikeCache?) {
    TODO("Implement testingOnlyPackedGlyphIDToGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphSrcPadding() const override { return SK_DistanceFieldInset; }
   * ```
   */
  public override fun glyphSrcPadding(): Int {
    TODO("Implement glyphSrcPadding")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas*,
   *               SkPoint drawOrigin,
   *               const SkPaint& paint,
   *               sk_sp<SkRefCnt> subRunStorage,
   *               const AtlasDrawDelegate& drawAtlas) const override {
   *         drawAtlas(this, drawOrigin, paint, std::move(subRunStorage),
   *                   {/* isSDF = */true, /* isLCD = */fUseLCDText, skgpu::MaskFormat::kA8});
   *     }
   * ```
   */
  public override fun draw(
    param0: SkCanvas?,
    drawOrigin: SkPoint,
    paint: SkPaint,
    subRunStorage: SkSp<SkRefCnt>,
    drawAtlas: AtlasDrawDelegate,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, SkRect> deviceRectAndNeedsTransform(
   *             const SkMatrix &positionMatrix) const override {
   *         auto [_, deviceRect] = fVertexFiller.deviceRectAndCheckTransform(positionMatrix);
   *         return {true, deviceRect};
   *     }
   * ```
   */
  public override fun deviceRectAndNeedsTransform(positionMatrix: SkMatrix): Int {
    TODO("Implement deviceRectAndNeedsTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * GlyphParams glyphParams() const override {
   *         return { /*isSDF=*/true, fUseLCDText, /*isAA=*/fAntiAliased };
   *     }
   * ```
   */
  public override fun glyphParams(): GlyphParams {
    TODO("Implement glyphParams")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, int> regenerateAtlas(int begin, int end,
   *                                           RegenerateAtlasDelegate regenerateAtlas) const override {
   *         return regenerateAtlas(&fGlyphs, begin, end, MaskFormat::kA8, this->glyphSrcPadding());
   *     }
   * ```
   */
  public override fun regenerateAtlas(
    begin: Int,
    end: Int,
    regenerateAtlas: RegenerateAtlasDelegate,
  ): Int {
    TODO("Implement regenerateAtlas")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kSDFTStreamTag; }
   * ```
   */
  protected override fun subRunStreamTag(): SubRunStreamTag {
    TODO("Implement subRunStreamTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void doFlatten(SkWriteBuffer& buffer) const override {
   *         buffer.writeInt(fUseLCDText);
   *         buffer.writeInt(fAntiAliased);
   *         fMatrixRange.flatten(buffer);
   *         fVertexFiller.flatten(buffer);
   *         fGlyphs.flatten(buffer);
   *     }
   * ```
   */
  protected override fun doFlatten(buffer: SkWriteBuffer) {
    TODO("Implement doFlatten")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SubRunOwner Make(SkZip<const SkPackedGlyphID, const SkPoint> accepted,
     *                             const SkFont& runFont,
     *                             SkStrikePromise&& strikePromise,
     *                             const SkMatrix& creationMatrix,
     *                             SkRect creationBounds,
     *                             const SDFTMatrixRange& matrixRange,
     *                             SubRunAllocator* alloc) {
     *         auto vertexFiller = VertexFiller::Make(MaskFormat::kA8,
     *                                                creationMatrix,
     *                                                creationBounds,
     *                                                get_positions(accepted),
     *                                                alloc,
     *                                                kIsTransformed);
     *
     *         auto glyphVector = GlyphVector::Make(
     *                 std::move(strikePromise), get_packedIDs(accepted), alloc);
     *
     *         return alloc->makeUnique<SDFTSubRun>(
     *                 runFont.getEdging() == SkFont::Edging::kSubpixelAntiAlias,
     *                 has_some_antialiasing(runFont),
     *                 matrixRange,
     *                 std::move(vertexFiller),
     *                 std::move(glyphVector));
     *     }
     * ```
     */
    public fun make(
      accepted: SkZip<SkPackedGlyphID, SkPoint>,
      runFont: SkFont,
      strikePromise: SkStrikePromise,
      creationMatrix: SkMatrix,
      creationBounds: SkRect,
      matrixRange: SDFTMatrixRange,
      alloc: SubRunAllocator?,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
     *                                       SubRunAllocator* alloc,
     *                                       const SkStrikeClient* client) {
     *         int useLCD = buffer.readInt();
     *         int isAntiAliased = buffer.readInt();
     *         SDFTMatrixRange matrixRange = SDFTMatrixRange::MakeFromBuffer(buffer);
     *         auto vertexFiller = VertexFiller::MakeFromBuffer(buffer, alloc);
     *         if (!buffer.validate(vertexFiller.has_value())) { return nullptr; }
     *         if (!buffer.validate(vertexFiller.value().grMaskType() == MaskFormat::kA8)) {
     *             return nullptr;
     *         }
     *         auto glyphVector = GlyphVector::MakeFromBuffer(buffer, client, alloc);
     *         if (!buffer.validate(glyphVector.has_value())) { return nullptr; }
     *         if (!buffer.validate(SkCount(glyphVector->glyphs()) == vertexFiller->count())) {
     *             return nullptr;
     *         }
     *         return alloc->makeUnique<SDFTSubRun>(useLCD,
     *                                              isAntiAliased,
     *                                              matrixRange,
     *                                              std::move(*vertexFiller),
     *                                              std::move(*glyphVector));
     *     }
     * ```
     */
    public fun makeFromBuffer(
      buffer: SkReadBuffer,
      alloc: SubRunAllocator?,
      client: SkStrikeClient?,
    ): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
