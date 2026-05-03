package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.json.SkJSONWriter
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SetMatrixCommand : public DrawCommand {
 * public:
 *     explicit SetMatrixCommand(const SkMatrix& matrix);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkMatrix fMatrix;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class SetMatrixCommand public constructor(
  matrix: SkMatrix,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fMatrix
   * ```
   */
  private var fMatrix: SkMatrix = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * void SetMatrixCommand::execute(SkCanvas* canvas) const { canvas->setMatrix(fMatrix); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void SetMatrixCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_MATRIX);
   *     MakeJsonMatrix(writer, fMatrix);
   *     writeMatrixType(writer, fMatrix);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
