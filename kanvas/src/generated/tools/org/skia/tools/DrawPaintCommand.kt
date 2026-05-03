package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class DrawPaintCommand : public DrawCommand {
 * public:
 *     explicit DrawPaintCommand(const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkPaint fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawPaintCommand public constructor(
  paint: SkPaint,
) : DrawCommand(TODO()) {
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
   * void DrawPaintCommand::execute(SkCanvas* canvas) const { canvas->drawPaint(fPaint); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawPaintCommand::render(SkCanvas* canvas) const {
   *     canvas->clear(0xFFFFFFFF);
   *     canvas->drawPaint(fPaint);
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
   * void DrawPaintCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
