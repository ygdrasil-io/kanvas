package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkPath
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class ClipPathCommand : public DrawCommand {
 * public:
 *     ClipPathCommand(const SkPath& path, SkClipOp op, bool doAA);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkPath   fPath;
 *     SkClipOp fOp;
 *     bool     fDoAA;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClipPathCommand public constructor(
  path: SkPath,
  op: SkClipOp,
  doAA: Boolean,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPath   fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

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
   * void ClipPathCommand::execute(SkCanvas* canvas) const { canvas->clipPath(fPath, fOp, fDoAA); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ClipPathCommand::render(SkCanvas* canvas) const {
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
   * void ClipPathCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PATH);
   *     MakeJsonPath(writer, fPath);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_REGIONOP, clipop_name(fOp));
   *     writer.appendBool(DEBUGCANVAS_ATTRIBUTE_ANTIALIAS, fDoAA);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
