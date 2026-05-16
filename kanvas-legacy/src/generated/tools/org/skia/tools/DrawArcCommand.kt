package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DrawArcCommand : public DrawCommand {
 * public:
 *     DrawArcCommand(const SkRect&  oval,
 *                    SkScalar       startAngle,
 *                    SkScalar       sweepAngle,
 *                    bool           useCenter,
 *                    const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRect   fOval;
 *     SkScalar fStartAngle;
 *     SkScalar fSweepAngle;
 *     bool     fUseCenter;
 *     SkPaint  fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawArcCommand public constructor(
  oval: SkRect,
  startAngle: SkScalar,
  sweepAngle: SkScalar,
  useCenter: Boolean,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect   fOval
   * ```
   */
  private var fOval: SkRect = TODO("Initialize fOval")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fStartAngle
   * ```
   */
  private var fStartAngle: SkScalar = TODO("Initialize fStartAngle")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fSweepAngle
   * ```
   */
  private var fSweepAngle: SkScalar = TODO("Initialize fSweepAngle")

  /**
   * C++ original:
   * ```cpp
   * bool     fUseCenter
   * ```
   */
  private var fUseCenter: Boolean = TODO("Initialize fUseCenter")

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
   * void DrawArcCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawArc(fOval, fStartAngle, fSweepAngle, fUseCenter, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawArcCommand::render(SkCanvas* canvas) const {
   *     canvas->clear(0xFFFFFFFF);
   *     canvas->save();
   *
   *     xlate_and_scale_to_bounds(canvas, fOval);
   *
   *     SkPaint p;
   *     p.setColor(SK_ColorBLACK);
   *     p.setStyle(SkPaint::kStroke_Style);
   *
   *     canvas->drawArc(fOval, fStartAngle, fSweepAngle, fUseCenter, p);
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
   * void DrawArcCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     MakeJsonRect(writer, fOval);
   *     writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_STARTANGLE, fStartAngle);
   *     writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_SWEEPANGLE, fSweepAngle);
   *     writer.appendBool(DEBUGCANVAS_ATTRIBUTE_USECENTER, fUseCenter);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *     MakeJsonPaint(writer, fPaint, urlDataManager);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
