package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.json.SkJSONWriter
import org.skia.math.SkPoint
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class DrawPointsCommand : public DrawCommand {
 * public:
 *     DrawPointsCommand(SkCanvas::PointMode mode,
 *                       size_t              count,
 *                       const SkPoint       pts[],
 *                       const SkPaint&      paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkCanvas::PointMode fMode;
 *     SkTDArray<SkPoint>  fPts;
 *     SkPaint             fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawPointsCommand public constructor(
  mode: SkCanvas.PointMode,
  count: ULong,
  pts: Array<SkPoint>,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkCanvas::PointMode fMode
   * ```
   */
  private var fMode: SkCanvas.PointMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<SkPoint>  fPts
   * ```
   */
  private var fPts: SkTDArray<SkPoint> = TODO("Initialize fPts")

  /**
   * C++ original:
   * ```cpp
   * SkPaint             fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawPointsCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawPoints(fMode, fPts, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawPointsCommand::render(SkCanvas* canvas) const {
   *     canvas->clear(0xFFFFFFFF);
   *     canvas->save();
   *
   *     SkRect bounds;
   *
   *     bounds.setEmpty();
   *     for (int i = 0; i < fPts.size(); ++i) {
   *         SkRectPriv::GrowToInclude(&bounds, fPts[i]);
   *     }
   *
   *     xlate_and_scale_to_bounds(canvas, bounds);
   *
   *     SkPaint p;
   *     p.setColor(SK_ColorBLACK);
   *     p.setStyle(SkPaint::kStroke_Style);
   *
   *     canvas->drawPoints(fMode, fPts, p);
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
   * void DrawPointsCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_MODE, pointmode_name(fMode));
   *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_POINTS);
   *     for (int i = 0; i < fPts.size(); i++) {
   *         MakeJsonPoint(writer, fPts[i]);
   *     }
   *     writer.endArray();  // points
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
