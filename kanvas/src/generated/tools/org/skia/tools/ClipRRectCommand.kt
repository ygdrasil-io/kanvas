package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkRRect
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class ClipRRectCommand : public DrawCommand {
 * public:
 *     ClipRRectCommand(const SkRRect& rrect, SkClipOp op, bool doAA);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRRect  fRRect;
 *     SkClipOp fOp;
 *     bool     fDoAA;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClipRRectCommand public constructor(
  rrect: SkRRect,
  op: SkClipOp,
  doAA: Boolean,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRRect  fRRect
   * ```
   */
  private var fRRect: SkRRect = TODO("Initialize fRRect")

  /**
   * C++ original:
   * ```cpp
   * SkClipOp fOp
   * ```
   */
  private var fOp: SkClipOp = TODO("Initialize fOp")

  /**
   * C++ original:
   * ```cpp
   * bool     fDoAA
   * ```
   */
  private var fDoAA: Boolean = TODO("Initialize fDoAA")

  /**
   * C++ original:
   * ```cpp
   * void ClipRRectCommand::execute(SkCanvas* canvas) const { canvas->clipRRect(fRRect, fOp, fDoAA); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ClipRRectCommand::render(SkCanvas* canvas) const {
   *     render_rrect(canvas, fRRect);
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
   * void ClipRRectCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     make_json_rrect(writer, fRRect);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_REGIONOP, clipop_name(fOp));
   *     writer.appendBool(DEBUGCANVAS_ATTRIBUTE_ANTIALIAS, fDoAA);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
