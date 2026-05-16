package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.json.SkJSONWriter
import org.skia.math.SkM44

/**
 * C++ original:
 * ```cpp
 * class SetM44Command : public DrawCommand {
 * public:
 *     explicit SetM44Command(const SkM44& matrix);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkM44 fMatrix;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class SetM44Command public constructor(
  matrix: SkM44,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkM44 fMatrix
   * ```
   */
  private var fMatrix: SkM44 = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * void SetM44Command::execute(SkCanvas* canvas) const { canvas->setMatrix(fMatrix); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void SetM44Command::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_MATRIX);
   *     MakeJsonMatrix44(writer, fMatrix);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
