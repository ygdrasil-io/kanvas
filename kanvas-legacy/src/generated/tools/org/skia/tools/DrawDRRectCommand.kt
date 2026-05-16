package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class DrawDRRectCommand : public DrawCommand {
 * public:
 *     DrawDRRectCommand(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRRect fOuter;
 *     SkRRect fInner;
 *     SkPaint fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawDRRectCommand public constructor(
  outer: SkRRect,
  `inner`: SkRRect,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRRect fOuter
   * ```
   */
  private var fOuter: SkRRect = TODO("Initialize fOuter")

  /**
   * C++ original:
   * ```cpp
   * SkRRect fInner
   * ```
   */
  private var fInner: SkRRect = TODO("Initialize fInner")

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
   * void DrawDRRectCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawDRRect(fOuter, fInner, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawDRRectCommand::render(SkCanvas* canvas) const {
   *     render_drrect(canvas, fOuter, fInner);
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
   * void DrawDRRectCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_OUTER);
   *     make_json_rrect(writer, fOuter);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_INNER);
   *     make_json_rrect(writer, fInner);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
