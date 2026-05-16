package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class ClipShaderCommand : public DrawCommand {
 * public:
 *     ClipShaderCommand(sk_sp<SkShader>, SkClipOp);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     sk_sp<SkShader> fShader;
 *     SkClipOp fOp;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClipShaderCommand public constructor(
  cs: SkSp<SkShader>,
  op: SkClipOp,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

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
   * void ClipShaderCommand::execute(SkCanvas* canvas) const { canvas->clipShader(fShader, fOp); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ClipShaderCommand::render(SkCanvas* canvas) const {
   *     SkPaint paint;
   *     paint.setShader(fShader);
   *     canvas->drawPaint(paint);
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
   * void ClipShaderCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     apply_flattenable(DEBUGCANVAS_ATTRIBUTE_SHADER, fShader.get(), writer, urlDataManager);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_REGIONOP, clipop_name(fOp));
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
