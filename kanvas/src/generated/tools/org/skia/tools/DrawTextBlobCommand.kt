package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkTextBlob
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DrawTextBlobCommand : public DrawCommand {
 * public:
 *     DrawTextBlobCommand(sk_sp<SkTextBlob> blob, SkScalar x, SkScalar y, const SkPaint& paint);
 *
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     sk_sp<SkTextBlob> fBlob;
 *     SkScalar          fXPos;
 *     SkScalar          fYPos;
 *     SkPaint           fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawTextBlobCommand public constructor(
  blob: SkSp<SkTextBlob>,
  x: SkScalar,
  y: SkScalar,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fBlob
   * ```
   */
  private var fBlob: SkSp<SkTextBlob> = TODO("Initialize fBlob")

  /**
   * C++ original:
   * ```cpp
   * SkScalar          fXPos
   * ```
   */
  private var fXPos: SkScalar = TODO("Initialize fXPos")

  /**
   * C++ original:
   * ```cpp
   * SkScalar          fYPos
   * ```
   */
  private var fYPos: SkScalar = TODO("Initialize fYPos")

  /**
   * C++ original:
   * ```cpp
   * SkPaint           fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawTextBlobCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawTextBlob(fBlob, fXPos, fYPos, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawTextBlobCommand::render(SkCanvas* canvas) const {
   *     canvas->clear(SK_ColorWHITE);
   *     canvas->save();
   *
   *     SkRect bounds = fBlob->bounds().makeOffset(fXPos, fYPos);
   *     xlate_and_scale_to_bounds(canvas, bounds);
   *
   *     canvas->drawTextBlob(fBlob, fXPos, fYPos, fPaint);
   *
   *     canvas->restore();
   *
   *     return true;
   * }
   * ```
   */
  public override fun render(canvas: SkCanvas?): Boolean {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawTextBlobCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_X, fXPos);
   *     writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_Y, fYPos);
   *     SkRect bounds = fBlob->bounds();
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_BOUNDS);
   *     MakeJsonRect(writer, bounds);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   *
   *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_RUNS);
   *     SkTextBlobRunIterator iter(fBlob.get());
   *     while (!iter.done()) {
   *         writer.beginObject();  // run
   *         if (iter.textSize()) {
   *             writer.appendString(DEBUGCANVAS_ATTRIBUTE_TEXT, iter.text(), iter.textSize());
   *         }
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_FONT);
   *         MakeJsonFont(iter.font(), writer, urlDataManager);
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *         MakeJsonPoint(writer, iter.offset());
   *         writer.beginArray(DEBUGCANVAS_ATTRIBUTE_GLYPHS);
   *         for (uint32_t i = 0; i < iter.glyphCount(); i++) {
   *             writer.appendU32(iter.glyphs()[i]);
   *         }
   *         writer.endArray();  // glyphs
   *         if (iter.positioning() != SkTextBlobRunIterator::kDefault_Positioning) {
   *             writer.beginArray(DEBUGCANVAS_ATTRIBUTE_POSITIONS);
   *             const SkScalar* iterPositions = iter.pos();
   *             for (uint32_t i = 0; i < iter.glyphCount(); i++) {
   *                 switch (iter.positioning()) {
   *                     case SkTextBlobRunIterator::kFull_Positioning:
   *                         MakeJsonPoint(writer, iterPositions[i * 2], iterPositions[i * 2 + 1]);
   *                         break;
   *                     case SkTextBlobRunIterator::kHorizontal_Positioning:
   *                         writer.appendFloat(iterPositions[i]);
   *                         break;
   *                     case SkTextBlobRunIterator::kDefault_Positioning: break;
   *                     case SkTextBlobRunIterator::kRSXform_Positioning:
   *                         // TODO_RSXFORM_BLOB
   *                         break;
   *                 }
   *             }
   *             writer.endArray();  // positions
   *         }
   *         if (iter.clusters()) {
   *             writer.beginArray(DEBUGCANVAS_ATTRIBUTE_CLUSTERS);
   *             for (uint32_t i = 0; i < iter.glyphCount(); i++) {
   *                 writer.appendU32(iter.clusters()[i]);
   *             }
   *             writer.endArray();  // clusters
   *         }
   *         writer.endObject();  // run
   *         iter.next();
   *     }
   *     writer.endArray();  // runs
   *
   *     SkString desc;
   *     // make the bounds local by applying the x,y
   *     bounds.offset(fXPos, fYPos);
   *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, *str_append(&desc, bounds));
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
