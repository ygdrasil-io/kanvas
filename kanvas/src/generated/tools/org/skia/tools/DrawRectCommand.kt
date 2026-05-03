package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawRectCommand : public DrawCommand {
 * public:
 *     DrawRectCommand(const SkRect& rect, const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRect  fRect;
 *     SkPaint fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawRectCommand public constructor(
  rect: SkRect,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect  fRect
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

  /**
   * C++ original:
   * ```cpp
   * SkPaint fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawRectCommand::execute(SkCanvas* canvas) const { canvas->drawRect(fRect, fPaint); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawRectCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     MakeJsonRect(writer, fRect);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   *
   *     SkString desc;
   *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, *str_append(&desc, fRect));
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
