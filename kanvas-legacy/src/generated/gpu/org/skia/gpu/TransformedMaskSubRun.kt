package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkStrikePromise
import org.skia.core.SkZip
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
 * class TransformedMaskSubRun final : public AtlasSubRun {
 * public:
 *     TransformedMaskSubRun(bool isBigEnough,
 *                           VertexFiller&& vertexFiller,
 *                           GlyphVector&& glyphs)
 *             : AtlasSubRun(std::move(vertexFiller), std::move(glyphs))
 *             , fIsBigEnough{isBigEnough} {}
 *
 *     static SubRunOwner Make(SkZip<const SkPackedGlyphID, const SkPoint> accepted,
 *                             const SkMatrix& initialPositionMatrix,
 *                             SkStrikePromise&& strikePromise,
 *                             SkMatrix creationMatrix,
 *                             SkRect creationBounds,
 *                             MaskFormat maskType,
 *                             SubRunAllocator* alloc) {
 *         auto vertexFiller = VertexFiller::Make(maskType,
 *                                                creationMatrix,
 *                                                creationBounds,
 *                                                get_positions(accepted),
 *                                                alloc,
 *                                                kIsTransformed);
 *
 *         auto glyphVector = GlyphVector::Make(
 *                 std::move(strikePromise), get_packedIDs(accepted), alloc);
 *
 *         return alloc->makeUnique<TransformedMaskSubRun>(
 *                 initialPositionMatrix.getMaxScale() >= 1,
 *                 std::move(vertexFiller),
 *                 std::move(glyphVector));
 *     }
 *
 *     static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
 *                                       SubRunAllocator* alloc,
 *                                       const SkStrikeClient* client) {
 *         auto vertexFiller = VertexFiller::MakeFromBuffer(buffer, alloc);
 *         if (!buffer.validate(vertexFiller.has_value())) { return nullptr; }
 *
 *         auto glyphVector = GlyphVector::MakeFromBuffer(buffer, client, alloc);
 *         if (!buffer.validate(glyphVector.has_value())) { return nullptr; }
 *         if (!buffer.validate(SkCount(glyphVector->glyphs()) == vertexFiller->count())) {
 *             return nullptr;
 *         }
 *         const bool isBigEnough = buffer.readBool();
 *         return alloc->makeUnique<TransformedMaskSubRun>(
 *                 isBigEnough, std::move(*vertexFiller), std::move(*glyphVector));
 *     }
 *
 *     int unflattenSize() const override {
 *         return sizeof(TransformedMaskSubRun) +
 *                fGlyphs.unflattenSize() +
 *                fVertexFiller.unflattenSize();
 *     }
 *
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override {
 *         // If we are not scaling the cache entry to be larger, than a cache with smaller glyphs may
 *         // be better.
 *         return fIsBigEnough;
 *     }
 *
 *     const AtlasSubRun* testingOnly_atlasSubRun() const override { return this; }
 *
 *     void testingOnly_packedGlyphIDToGlyph(StrikeCache *cache) const override {
 *         fGlyphs.packedGlyphIDToGlyph(cache);
 *     }
 *
 *     int glyphSrcPadding() const override { return 1; }
 *
 *     void draw(SkCanvas*,
 *               SkPoint drawOrigin,
 *               const SkPaint& paint,
 *               sk_sp<SkRefCnt> subRunStorage,
 *               const AtlasDrawDelegate& drawAtlas) const override {
 *         drawAtlas(this, drawOrigin, paint, std::move(subRunStorage),
 *                   {/* isSDF = */false, fVertexFiller.isLCD(), fVertexFiller.grMaskType()});
 *     }
 *
 *     std::tuple<bool, SkRect> deviceRectAndNeedsTransform(
 *             const SkMatrix &positionMatrix) const override {
 *         auto [_, deviceRect] = fVertexFiller.deviceRectAndCheckTransform(positionMatrix);
 *         return {true, deviceRect};
 *     }
 *
 *     GlyphParams glyphParams() const override {
 *         // Since this is non-SDF, isAA will be ignored so we just pass true
 *         return { /*isSDF=*/false, fVertexFiller.isLCD(), /*isAA=*/true };
 *     }
 *
 *     std::tuple<bool, int> regenerateAtlas(int begin, int end,
 *                                           RegenerateAtlasDelegate regenerateAtlas) const override {
 *         return regenerateAtlas(
 *                 &fGlyphs, begin, end, fVertexFiller.grMaskType(), this->glyphSrcPadding());
 *     }
 *
 * protected:
 *     SubRunStreamTag subRunStreamTag() const override {
 *         return SubRunStreamTag::kTransformMaskStreamTag;
 *     }
 *
 *     void doFlatten(SkWriteBuffer& buffer) const override {
 *         fVertexFiller.flatten(buffer);
 *         fGlyphs.flatten(buffer);
 *         buffer.writeBool(fIsBigEnough);
 *     }
 *
 * private:
 *     const bool fIsBigEnough;
 * }
 * ```
 */
public class TransformedMaskSubRun public constructor(
  isBigEnough: Boolean,
  vertexFiller: VertexFiller,
  glyphs: GlyphVector,
) : AtlasSubRun(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const bool fIsBigEnough
   * ```
   */
  private val fIsBigEnough: Boolean = TODO("Initialize fIsBigEnough")

  /**
   * C++ original:
   * ```cpp
   * int unflattenSize() const override {
   *         return sizeof(TransformedMaskSubRun) +
   *                fGlyphs.unflattenSize() +
   *                fVertexFiller.unflattenSize();
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
   *         // If we are not scaling the cache entry to be larger, than a cache with smaller glyphs may
   *         // be better.
   *         return fIsBigEnough;
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
   * int glyphSrcPadding() const override { return 1; }
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
   *                   {/* isSDF = */false, fVertexFiller.isLCD(), fVertexFiller.grMaskType()});
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
   *         // Since this is non-SDF, isAA will be ignored so we just pass true
   *         return { /*isSDF=*/false, fVertexFiller.isLCD(), /*isAA=*/true };
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
   *         return regenerateAtlas(
   *                 &fGlyphs, begin, end, fVertexFiller.grMaskType(), this->glyphSrcPadding());
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
   * SubRunStreamTag subRunStreamTag() const override {
   *         return SubRunStreamTag::kTransformMaskStreamTag;
   *     }
   * ```
   */
  protected override fun subRunStreamTag(): SubRunStreamTag {
    TODO("Implement subRunStreamTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void doFlatten(SkWriteBuffer& buffer) const override {
   *         fVertexFiller.flatten(buffer);
   *         fGlyphs.flatten(buffer);
   *         buffer.writeBool(fIsBigEnough);
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
     *                             const SkMatrix& initialPositionMatrix,
     *                             SkStrikePromise&& strikePromise,
     *                             SkMatrix creationMatrix,
     *                             SkRect creationBounds,
     *                             MaskFormat maskType,
     *                             SubRunAllocator* alloc) {
     *         auto vertexFiller = VertexFiller::Make(maskType,
     *                                                creationMatrix,
     *                                                creationBounds,
     *                                                get_positions(accepted),
     *                                                alloc,
     *                                                kIsTransformed);
     *
     *         auto glyphVector = GlyphVector::Make(
     *                 std::move(strikePromise), get_packedIDs(accepted), alloc);
     *
     *         return alloc->makeUnique<TransformedMaskSubRun>(
     *                 initialPositionMatrix.getMaxScale() >= 1,
     *                 std::move(vertexFiller),
     *                 std::move(glyphVector));
     *     }
     * ```
     */
    public fun make(
      accepted: SkZip<SkPackedGlyphID, SkPoint>,
      initialPositionMatrix: SkMatrix,
      strikePromise: SkStrikePromise,
      creationMatrix: SkMatrix,
      creationBounds: SkRect,
      maskType: MaskFormat,
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
     *         auto vertexFiller = VertexFiller::MakeFromBuffer(buffer, alloc);
     *         if (!buffer.validate(vertexFiller.has_value())) { return nullptr; }
     *
     *         auto glyphVector = GlyphVector::MakeFromBuffer(buffer, client, alloc);
     *         if (!buffer.validate(glyphVector.has_value())) { return nullptr; }
     *         if (!buffer.validate(SkCount(glyphVector->glyphs()) == vertexFiller->count())) {
     *             return nullptr;
     *         }
     *         const bool isBigEnough = buffer.readBool();
     *         return alloc->makeUnique<TransformedMaskSubRun>(
     *                 isBigEnough, std::move(*vertexFiller), std::move(*glyphVector));
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
