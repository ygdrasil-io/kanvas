package org.skia.gpu

import kotlin.Int
import org.skia.core.IDOrDrawable
import org.skia.core.SkCanvas
import org.skia.core.SkOnce
import org.skia.core.SkStrikePromise
import org.skia.core.SkZip
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.utils.SkStrikeClient

/**
 * C++ original:
 * ```cpp
 * class DrawableOpSubmitter {
 * public:
 *     DrawableOpSubmitter() = delete;
 *     DrawableOpSubmitter(const DrawableOpSubmitter&) = delete;
 *     const DrawableOpSubmitter& operator=(const DrawableOpSubmitter&) = delete;
 *     DrawableOpSubmitter(DrawableOpSubmitter&& that)
 *         : fStrikeToSourceScale{that.fStrikeToSourceScale}
 *         , fPositions{that.fPositions}
 *         , fIDsOrDrawables{that.fIDsOrDrawables}
 *         , fStrikePromise{std::move(that.fStrikePromise)} {}
 *     DrawableOpSubmitter& operator=(DrawableOpSubmitter&& that) {
 *         this->~DrawableOpSubmitter();
 *         new (this) DrawableOpSubmitter{std::move(that)};
 *         return *this;
 *     }
 *     DrawableOpSubmitter(SkScalar strikeToSourceScale,
 *                         SkSpan<SkPoint> positions,
 *                         SkSpan<IDOrDrawable> idsOrDrawables,
 *                         SkStrikePromise&& strikePromise);
 *
 *     static DrawableOpSubmitter Make(SkZip<const SkGlyphID, const SkPoint> accepted,
 *                                     SkScalar strikeToSourceScale,
 *                                     SkStrikePromise&& strikePromise,
 *                                     SubRunAllocator* alloc) {
 *         auto mapToIDOrDrawable = [](const SkGlyphID glyphID) { return IDOrDrawable{glyphID}; };
 *
 *         return DrawableOpSubmitter{
 *             strikeToSourceScale,
 *             alloc->makePODSpan(get_positions(accepted)),
 *             alloc->makePODArray<IDOrDrawable>(get_glyphIDs(accepted), mapToIDOrDrawable),
 *             std::move(strikePromise)};
 *     }
 *
 *     int unflattenSize() const;
 *     void flatten(SkWriteBuffer& buffer) const;
 *     static std::optional<DrawableOpSubmitter> MakeFromBuffer(SkReadBuffer& buffer,
 *                                                              SubRunAllocator* alloc,
 *                                                              const SkStrikeClient* client);
 *     void submitDraws(SkCanvas* canvas, SkPoint drawOrigin, const SkPaint& paint) const;
 *
 * private:
 *     const SkScalar fStrikeToSourceScale;
 *     const SkSpan<SkPoint> fPositions;
 *     const SkSpan<IDOrDrawable> fIDsOrDrawables;
 *     // When the promise is converted to a strike it acts as the ref on the strike to keep the
 *     // SkDrawable data alive.
 *     mutable SkStrikePromise fStrikePromise;
 *     mutable SkOnce fConvertIDsToDrawables;
 * }
 * ```
 */
public data class DrawableOpSubmitter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fStrikeToSourceScale
   * ```
   */
  private val fStrikeToSourceScale: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<SkPoint> fPositions
   * ```
   */
  private val fPositions: SkSpan<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<IDOrDrawable> fIDsOrDrawables
   * ```
   */
  private val fIDsOrDrawables: SkSpan<IDOrDrawable>,
  /**
   * C++ original:
   * ```cpp
   * mutable SkStrikePromise fStrikePromise
   * ```
   */
  private var fStrikePromise: SkStrikePromise,
  /**
   * C++ original:
   * ```cpp
   * mutable SkOnce fConvertIDsToDrawables
   * ```
   */
  private var fConvertIDsToDrawables: SkOnce,
) {
  /**
   * C++ original:
   * ```cpp
   * const DrawableOpSubmitter& operator=(const DrawableOpSubmitter&) = delete
   * ```
   */
  public fun assign(param0: DrawableOpSubmitter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawableOpSubmitter& operator=(DrawableOpSubmitter&& that) {
   *         this->~DrawableOpSubmitter();
   *         new (this) DrawableOpSubmitter{std::move(that)};
   *         return *this;
   *     }
   * ```
   */
  public fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * int DrawableOpSubmitter::unflattenSize() const {
   *     return fPositions.size_bytes() + fIDsOrDrawables.size_bytes();
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawableOpSubmitter::flatten(SkWriteBuffer& buffer) const {
   *     fStrikePromise.flatten(buffer);
   *
   *     buffer.writeScalar(fStrikeToSourceScale);
   *     buffer.writePointArray(fPositions);
   *     for (IDOrDrawable idOrDrawable : fIDsOrDrawables) {
   *         buffer.writeInt(idOrDrawable.fGlyphID);
   *     }
   * }
   * ```
   */
  public fun submitDraws(
    canvas: SkCanvas?,
    drawOrigin: SkPoint,
    paint: SkPaint,
  ) {
    TODO("Implement submitDraws")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static DrawableOpSubmitter Make(SkZip<const SkGlyphID, const SkPoint> accepted,
     *                                     SkScalar strikeToSourceScale,
     *                                     SkStrikePromise&& strikePromise,
     *                                     SubRunAllocator* alloc) {
     *         auto mapToIDOrDrawable = [](const SkGlyphID glyphID) { return IDOrDrawable{glyphID}; };
     *
     *         return DrawableOpSubmitter{
     *             strikeToSourceScale,
     *             alloc->makePODSpan(get_positions(accepted)),
     *             alloc->makePODArray<IDOrDrawable>(get_glyphIDs(accepted), mapToIDOrDrawable),
     *             std::move(strikePromise)};
     *     }
     * ```
     */
    public fun make(
      accepted: SkZip<SkGlyphID, SkPoint>,
      strikeToSourceScale: SkScalar,
      strikePromise: SkStrikePromise,
      alloc: SubRunAllocator?,
    ): DrawableOpSubmitter {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<DrawableOpSubmitter> DrawableOpSubmitter::MakeFromBuffer(
     *         SkReadBuffer& buffer, SubRunAllocator* alloc, const SkStrikeClient* client) {
     *     std::optional<SkStrikePromise> strikePromise =
     *             SkStrikePromise::MakeFromBuffer(buffer, client, SkStrikeCache::GlobalStrikeCache());
     *     if (!buffer.validate(strikePromise.has_value())) {
     *         return std::nullopt;
     *     }
     *
     *     SkScalar strikeToSourceScale = buffer.readScalar();
     *     if (!buffer.validate(0 < strikeToSourceScale)) { return std::nullopt; }
     *
     *     SkSpan<SkPoint> positions = MakePointsFromBuffer(buffer, alloc);
     *     if (positions.empty()) { return std::nullopt; }
     *     const int glyphCount = SkCount(positions);
     *
     *     if (!buffer.validateCanReadN<int>(glyphCount)) { return std::nullopt; }
     *     auto idsOrDrawables = alloc->makePODArray<IDOrDrawable>(glyphCount);
     *     for (int i = 0; i < SkToInt(glyphCount); ++i) {
     *         // Remember, we stored an int for glyph id.
     *         idsOrDrawables[i].fGlyphID = SkTo<SkGlyphID>(buffer.readInt());
     *     }
     *
     *     if (!buffer.isValid()) {
     *         return std::nullopt;
     *     }
     *
     *     return DrawableOpSubmitter{strikeToSourceScale,
     *                                positions,
     *                                SkSpan(idsOrDrawables, glyphCount),
     *                                std::move(strikePromise.value())};
     * }
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
