package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.IDOrPath
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
 * class PathOpSubmitter {
 * public:
 *     PathOpSubmitter() = delete;
 *     PathOpSubmitter(const PathOpSubmitter&) = delete;
 *     const PathOpSubmitter& operator=(const PathOpSubmitter&) = delete;
 *     PathOpSubmitter(PathOpSubmitter&& that)
 *             // Transfer ownership of fIDsOrPaths from that to this.
 *             : fIDsOrPaths{std::exchange(
 *                       const_cast<SkSpan<IDOrPath>&>(that.fIDsOrPaths), SkSpan<IDOrPath>{})}
 *             , fPositions{that.fPositions}
 *             , fStrikeToSourceScale{that.fStrikeToSourceScale}
 *             , fIsAntiAliased{that.fIsAntiAliased}
 *             , fStrikePromise{std::move(that.fStrikePromise)} {}
 *     PathOpSubmitter& operator=(PathOpSubmitter&& that) {
 *         this->~PathOpSubmitter();
 *         new (this) PathOpSubmitter{std::move(that)};
 *         return *this;
 *     }
 *     PathOpSubmitter(bool isAntiAliased,
 *                     SkScalar strikeToSourceScale,
 *                     SkSpan<SkPoint> positions,
 *                     SkSpan<IDOrPath> idsOrPaths,
 *                     SkStrikePromise&& strikePromise);
 *
 *     ~PathOpSubmitter();
 *
 *     static PathOpSubmitter Make(SkZip<const SkGlyphID, const SkPoint> accepted,
 *                                 bool isAntiAliased,
 *                                 SkScalar strikeToSourceScale,
 *                                 SkStrikePromise&& strikePromise,
 *                                 SubRunAllocator* alloc);
 *
 *     int unflattenSize() const;
 *     void flatten(SkWriteBuffer& buffer) const;
 *     static std::optional<PathOpSubmitter> MakeFromBuffer(SkReadBuffer& buffer,
 *                                                          SubRunAllocator* alloc,
 *                                                          const SkStrikeClient* client);
 *
 *     // submitDraws is not thread safe. It only occurs the single thread drawing portion of the GPU
 *     // rendering.
 *     void submitDraws(SkCanvas*,
 *                      SkPoint drawOrigin,
 *                      const SkPaint& paint) const;
 *
 * private:
 *     // When PathOpSubmitter is created only the glyphIDs are needed, during the submitDraws call,
 *     // the glyphIDs are converted to SkPaths.
 *     const SkSpan<IDOrPath> fIDsOrPaths;
 *     const SkSpan<const SkPoint> fPositions;
 *     const SkScalar fStrikeToSourceScale;
 *     const bool fIsAntiAliased;
 *
 *     mutable SkStrikePromise fStrikePromise;
 *     mutable SkOnce fConvertIDsToPaths;
 *     mutable bool fPathsAreCreated{false};
 * }
 * ```
 */
public data class PathOpSubmitter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<IDOrPath> fIDsOrPaths
   * ```
   */
  private val fIDsOrPaths: SkSpan<IDOrPath>,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<const SkPoint> fPositions
   * ```
   */
  private val fPositions: SkSpan<SkPoint>,
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
   * const bool fIsAntiAliased
   * ```
   */
  private val fIsAntiAliased: Boolean,
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
   * mutable SkOnce fConvertIDsToPaths
   * ```
   */
  private var fConvertIDsToPaths: SkOnce,
  /**
   * C++ original:
   * ```cpp
   * mutable bool fPathsAreCreated{false}
   * ```
   */
  private var fPathsAreCreated: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const PathOpSubmitter& operator=(const PathOpSubmitter&) = delete
   * ```
   */
  public fun assign(param0: PathOpSubmitter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * PathOpSubmitter& operator=(PathOpSubmitter&& that) {
   *         this->~PathOpSubmitter();
   *         new (this) PathOpSubmitter{std::move(that)};
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
   * int PathOpSubmitter::unflattenSize() const {
   *     return fPositions.size_bytes() + fIDsOrPaths.size_bytes();
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void PathOpSubmitter::flatten(SkWriteBuffer& buffer) const {
   *     fStrikePromise.flatten(buffer);
   *
   *     buffer.writeInt(fIsAntiAliased);
   *     buffer.writeScalar(fStrikeToSourceScale);
   *     buffer.writePointArray(fPositions);
   *     for (IDOrPath& idOrPath : fIDsOrPaths) {
   *         buffer.writeInt(idOrPath.fGlyphID);
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
     * PathOpSubmitter PathOpSubmitter::Make(SkZip<const SkGlyphID, const SkPoint> accepted,
     *                                       bool isAntiAliased,
     *                                       SkScalar strikeToSourceScale,
     *                                       SkStrikePromise&& strikePromise,
     *                                       SubRunAllocator* alloc) {
     *     auto mapToIDOrPath = [](SkGlyphID glyphID) { return IDOrPath{glyphID}; };
     *
     *     IDOrPath* const rawIDsOrPaths =
     *             alloc->makeUniqueArray<IDOrPath>(get_glyphIDs(accepted), mapToIDOrPath).release();
     *
     *     return PathOpSubmitter{isAntiAliased,
     *                            strikeToSourceScale,
     *                            alloc->makePODSpan(get_positions(accepted)),
     *                            SkSpan(rawIDsOrPaths, accepted.size()),
     *                            std::move(strikePromise)};
     * }
     * ```
     */
    public fun make(
      accepted: SkZip<SkGlyphID, SkPoint>,
      isAntiAliased: Boolean,
      strikeToSourceScale: SkScalar,
      strikePromise: SkStrikePromise,
      alloc: SubRunAllocator?,
    ): PathOpSubmitter {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<PathOpSubmitter> PathOpSubmitter::MakeFromBuffer(SkReadBuffer& buffer,
     *                                                                SubRunAllocator* alloc,
     *                                                                const SkStrikeClient* client) {
     *     std::optional<SkStrikePromise> strikePromise =
     *             SkStrikePromise::MakeFromBuffer(buffer, client, SkStrikeCache::GlobalStrikeCache());
     *     if (!buffer.validate(strikePromise.has_value())) {
     *         return std::nullopt;
     *     }
     *
     *     bool isAntiAlias = buffer.readInt();
     *
     *     SkScalar strikeToSourceScale = buffer.readScalar();
     *     if (!buffer.validate(0 < strikeToSourceScale)) { return std::nullopt; }
     *
     *     SkSpan<SkPoint> positions = MakePointsFromBuffer(buffer, alloc);
     *     if (positions.empty()) { return std::nullopt; }
     *     const int glyphCount = SkCount(positions);
     *
     *     // Remember, we stored an int for glyph id.
     *     if (!buffer.validateCanReadN<int>(glyphCount)) { return std::nullopt; }
     *     auto idsOrPaths = SkSpan(alloc->makeUniqueArray<IDOrPath>(glyphCount).release(), glyphCount);
     *     for (auto& idOrPath : idsOrPaths) {
     *         idOrPath.fGlyphID = SkTo<SkGlyphID>(buffer.readInt());
     *     }
     *
     *     if (!buffer.isValid()) { return std::nullopt; }
     *
     *     return PathOpSubmitter{isAntiAlias,
     *                            strikeToSourceScale,
     *                            positions,
     *                            idsOrPaths,
     *                            std::move(strikePromise.value())};
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
