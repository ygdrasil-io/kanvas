package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkStrikePromise
import org.skia.core.SkZip
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.utils.SkStrikeClient
import undefined.AtlasDrawDelegate
import undefined.SubRunStreamTag

/**
 * C++ original:
 * ```cpp
 * class PathSubRun final : public SubRun {
 * public:
 *     PathSubRun(PathOpSubmitter&& pathDrawing) : fPathDrawing(std::move(pathDrawing)) {}
 *
 *     static SubRunOwner Make(SkZip<const SkGlyphID, const SkPoint> accepted,
 *                             bool isAntiAliased,
 *                             SkScalar strikeToSourceScale,
 *                             SkStrikePromise&& strikePromise,
 *                             SubRunAllocator* alloc) {
 *         return alloc->makeUnique<PathSubRun>(
 *             PathOpSubmitter::Make(
 *                     accepted, isAntiAliased, strikeToSourceScale, std::move(strikePromise), alloc));
 *     }
 *
 *     void draw(SkCanvas* canvas,
 *               SkPoint drawOrigin,
 *               const SkPaint& paint,
 *               sk_sp<SkRefCnt>,
 *               const AtlasDrawDelegate&) const override {
 *         fPathDrawing.submitDraws(canvas, drawOrigin, paint);
 *     }
 *
 *     int unflattenSize() const override;
 *
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override {
 *         return true;
 *     }
 *     const AtlasSubRun* testingOnly_atlasSubRun() const override { return nullptr; }
 *     static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
 *                                       SubRunAllocator* alloc,
 *                                       const SkStrikeClient* client);
 *
 * protected:
 *     SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kPathStreamTag; }
 *     void doFlatten(SkWriteBuffer& buffer) const override;
 *
 * private:
 *     PathOpSubmitter fPathDrawing;
 * }
 * ```
 */
public class PathSubRun public constructor(
  pathDrawing: PathOpSubmitter,
) : SubRun() {
  /**
   * C++ original:
   * ```cpp
   * PathOpSubmitter fPathDrawing
   * ```
   */
  private var fPathDrawing: PathOpSubmitter = TODO("Initialize fPathDrawing")

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas,
   *               SkPoint drawOrigin,
   *               const SkPaint& paint,
   *               sk_sp<SkRefCnt>,
   *               const AtlasDrawDelegate&) const override {
   *         fPathDrawing.submitDraws(canvas, drawOrigin, paint);
   *     }
   * ```
   */
  public override fun draw(
    canvas: SkCanvas?,
    drawOrigin: SkPoint,
    paint: SkPaint,
    param3: SkSp<SkRefCnt>,
    param4: AtlasDrawDelegate,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * int PathSubRun::unflattenSize() const {
   *     return sizeof(PathSubRun) + fPathDrawing.unflattenSize();
   * }
   * ```
   */
  public override fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override {
   *         return true;
   *     }
   * ```
   */
  public override fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean {
    TODO("Implement canReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * const AtlasSubRun* testingOnly_atlasSubRun() const override { return nullptr; }
   * ```
   */
  public override fun testingOnlyAtlasSubRun(): AtlasSubRun {
    TODO("Implement testingOnlyAtlasSubRun")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kPathStreamTag; }
   * ```
   */
  protected override fun subRunStreamTag(): SubRunStreamTag {
    TODO("Implement subRunStreamTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void PathSubRun::doFlatten(SkWriteBuffer& buffer) const {
   *     fPathDrawing.flatten(buffer);
   * }
   * ```
   */
  protected override fun doFlatten(buffer: SkWriteBuffer) {
    TODO("Implement doFlatten")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SubRunOwner Make(SkZip<const SkGlyphID, const SkPoint> accepted,
     *                             bool isAntiAliased,
     *                             SkScalar strikeToSourceScale,
     *                             SkStrikePromise&& strikePromise,
     *                             SubRunAllocator* alloc) {
     *         return alloc->makeUnique<PathSubRun>(
     *             PathOpSubmitter::Make(
     *                     accepted, isAntiAliased, strikeToSourceScale, std::move(strikePromise), alloc));
     *     }
     * ```
     */
    public fun make(
      accepted: SkZip<SkGlyphID, SkPoint>,
      isAntiAliased: Boolean,
      strikeToSourceScale: SkScalar,
      strikePromise: SkStrikePromise,
      alloc: SubRunAllocator?,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SubRunOwner PathSubRun::MakeFromBuffer(SkReadBuffer& buffer,
     *                                        SubRunAllocator* alloc,
     *                                        const SkStrikeClient* client) {
     *     auto pathOpSubmitter = PathOpSubmitter::MakeFromBuffer(buffer, alloc, client);
     *     if (!buffer.validate(pathOpSubmitter.has_value())) { return nullptr; }
     *     return alloc->makeUnique<PathSubRun>(std::move(*pathOpSubmitter));
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
