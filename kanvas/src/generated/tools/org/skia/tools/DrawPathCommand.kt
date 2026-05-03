package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class DrawPathCommand : public DrawCommand {
 * public:
 *     DrawPathCommand(const SkPath& path, const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkPath  fPath;
 *     SkPaint fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawPathCommand public constructor(
  path: SkPath,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPath  fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

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
   * void DrawPathCommand::execute(SkCanvas* canvas) const { canvas->drawPath(fPath, fPaint); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawPathCommand::render(SkCanvas* canvas) const {
   *     render_path(canvas, fPath);
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
   * void DrawPathCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PATH);
   *     MakeJsonPath(writer, fPath);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
