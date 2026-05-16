package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class ClearCommand : public DrawCommand {
 * public:
 *     explicit ClearCommand(SkColor color);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkColor fColor;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClearCommand public constructor(
  color: SkColor,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkColor fColor
   * ```
   */
  private var fColor: SkColor = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * void ClearCommand::execute(SkCanvas* canvas) const { canvas->clear(fColor); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClearCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COLOR);
   *     MakeJsonColor(writer, fColor);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
