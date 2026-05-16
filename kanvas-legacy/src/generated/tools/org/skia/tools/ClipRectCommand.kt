package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class ClipRectCommand : public DrawCommand {
 * public:
 *     ClipRectCommand(const SkRect& rect, SkClipOp op, bool doAA);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRect   fRect;
 *     SkClipOp fOp;
 *     bool     fDoAA;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClipRectCommand public constructor(
  rect: SkRect,
  op: SkClipOp,
  doAA: Boolean,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect   fRect
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

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
   * void ClipRectCommand::execute(SkCanvas* canvas) const { canvas->clipRect(fRect, fOp, fDoAA); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipRectCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     MakeJsonRect(writer, fRect);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_REGIONOP, clipop_name(fOp));
   *     writer.appendBool(DEBUGCANVAS_ATTRIBUTE_ANTIALIAS, fDoAA);
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
