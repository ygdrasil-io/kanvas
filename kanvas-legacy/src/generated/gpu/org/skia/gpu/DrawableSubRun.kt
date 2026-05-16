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
 * class DrawableSubRun : public SubRun {
 * public:
 *     DrawableSubRun(DrawableOpSubmitter&& drawingDrawing)
 *             : fDrawingDrawing(std::move(drawingDrawing)) {}
 *
 *     static SubRunOwner Make(SkZip<const SkGlyphID, const SkPoint> drawables,
 *                             SkScalar strikeToSourceScale,
 *                             SkStrikePromise&& strikePromise,
 *                             SubRunAllocator* alloc) {
 *         return alloc->makeUnique<DrawableSubRun>(
 *                 DrawableOpSubmitter::Make(drawables,
 *                                           strikeToSourceScale,
 *                                           std::move(strikePromise),
 *                                           alloc));
 *     }
 *
 *     static SubRunOwner MakeFromBuffer(SkReadBuffer& buffer,
 *                                       SubRunAllocator* alloc,
 *                                       const SkStrikeClient* client);
 *
 *     void draw(SkCanvas* canvas,
 *               SkPoint drawOrigin,
 *               const SkPaint& paint,
 *               sk_sp<SkRefCnt>,
 *               const AtlasDrawDelegate&) const override {
 *         fDrawingDrawing.submitDraws(canvas, drawOrigin, paint);
 *     }
 *
 *     int unflattenSize() const override;
 *
 *     bool canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const override;
 *
 *     const AtlasSubRun* testingOnly_atlasSubRun() const override;
 *
 * protected:
 *     SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kDrawableStreamTag; }
 *     void doFlatten(SkWriteBuffer& buffer) const override;
 *
 * private:
 *     DrawableOpSubmitter fDrawingDrawing;
 * }
 * ```
 */
public open class DrawableSubRun public constructor(
  drawingDrawing: DrawableOpSubmitter,
) : SubRun() {
  /**
   * C++ original:
   * ```cpp
   * DrawableOpSubmitter fDrawingDrawing
   * ```
   */
  private var fDrawingDrawing: DrawableOpSubmitter = TODO("Initialize fDrawingDrawing")

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas,
   *               SkPoint drawOrigin,
   *               const SkPaint& paint,
   *               sk_sp<SkRefCnt>,
   *               const AtlasDrawDelegate&) const override {
   *         fDrawingDrawing.submitDraws(canvas, drawOrigin, paint);
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
   * int DrawableSubRun::unflattenSize() const {
   *     return sizeof(DrawableSubRun) + fDrawingDrawing.unflattenSize();
   * }
   * ```
   */
  public override fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawableSubRun::canReuse(const SkPaint& paint, const SkMatrix& positionMatrix) const {
   *     return true;
   * }
   * ```
   */
  public override fun canReuse(paint: SkPaint, positionMatrix: SkMatrix): Boolean {
    TODO("Implement canReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * const AtlasSubRun* DrawableSubRun::testingOnly_atlasSubRun() const {
   *     return nullptr;
   * }
   * ```
   */
  public override fun testingOnlyAtlasSubRun(): AtlasSubRun {
    TODO("Implement testingOnlyAtlasSubRun")
  }

  /**
   * C++ original:
   * ```cpp
   * SubRunStreamTag subRunStreamTag() const override { return SubRunStreamTag::kDrawableStreamTag; }
   * ```
   */
  protected override fun subRunStreamTag(): SubRunStreamTag {
    TODO("Implement subRunStreamTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawableSubRun::doFlatten(SkWriteBuffer& buffer) const {
   *     fDrawingDrawing.flatten(buffer);
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
     * static SubRunOwner Make(SkZip<const SkGlyphID, const SkPoint> drawables,
     *                             SkScalar strikeToSourceScale,
     *                             SkStrikePromise&& strikePromise,
     *                             SubRunAllocator* alloc) {
     *         return alloc->makeUnique<DrawableSubRun>(
     *                 DrawableOpSubmitter::Make(drawables,
     *                                           strikeToSourceScale,
     *                                           std::move(strikePromise),
     *                                           alloc));
     *     }
     * ```
     */
    public fun make(
      drawables: SkZip<SkGlyphID, SkPoint>,
      strikeToSourceScale: SkScalar,
      strikePromise: SkStrikePromise,
      alloc: SubRunAllocator?,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SubRunOwner DrawableSubRun::MakeFromBuffer(SkReadBuffer& buffer,
     *                                            SubRunAllocator* alloc,
     *                                            const SkStrikeClient* client) {
     *     auto drawableOpSubmitter = DrawableOpSubmitter::MakeFromBuffer(buffer, alloc, client);
     *     if (!buffer.validate(drawableOpSubmitter.has_value())) { return nullptr; }
     *     return alloc->makeUnique<DrawableSubRun>(std::move(*drawableOpSubmitter));
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
