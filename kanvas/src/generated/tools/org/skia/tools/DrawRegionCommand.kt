package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRegion
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class DrawRegionCommand : public DrawCommand {
 * public:
 *     DrawRegionCommand(const SkRegion& region, const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRegion fRegion;
 *     SkPaint  fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawRegionCommand public constructor(
  region: SkRegion,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRegion fRegion
   * ```
   */
  private var fRegion: SkRegion = TODO("Initialize fRegion")

  /**
   * C++ original:
   * ```cpp
   * SkPaint  fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawRegionCommand::execute(SkCanvas* canvas) const { canvas->drawRegion(fRegion, fPaint); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawRegionCommand::render(SkCanvas* canvas) const {
   *     render_region(canvas, fRegion);
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
   * void DrawRegionCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_REGION);
   *     MakeJsonRegion(writer, fRegion);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
