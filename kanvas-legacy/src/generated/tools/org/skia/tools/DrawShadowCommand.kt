package org.skia.tools

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkDrawShadowRec
import org.skia.foundation.SkPath
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class DrawShadowCommand : public DrawCommand {
 * public:
 *     DrawShadowCommand(const SkPath& path, const SkDrawShadowRec& rec);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkPath          fPath;
 *     SkDrawShadowRec fShadowRec;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawShadowCommand public constructor(
  path: SkPath,
  rec: SkDrawShadowRec,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPath          fPath
   * ```
   */
  private var fPath: SkPath = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SkDrawShadowRec fShadowRec
   * ```
   */
  private var fShadowRec: SkDrawShadowRec = TODO("Initialize fShadowRec")

  /**
   * C++ original:
   * ```cpp
   * void DrawShadowCommand::execute(SkCanvas* canvas) const {
   *     canvas->private_draw_shadow_rec(fPath, fShadowRec);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawShadowCommand::render(SkCanvas* canvas) const {
   *     render_shadow(canvas, fPath, fShadowRec);
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
   * void DrawShadowCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *
   *     bool geometricOnly = SkToBool(fShadowRec.fFlags & SkShadowFlags::kGeometricOnly_ShadowFlag);
   *     bool transparentOccluder =
   *             SkToBool(fShadowRec.fFlags & SkShadowFlags::kTransparentOccluder_ShadowFlag);
   *
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_PATH);
   *     MakeJsonPath(writer, fPath);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_ZPLANE);
   *     MakeJsonPoint3(writer, fShadowRec.fZPlaneParams);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_LIGHTPOSITION);
   *     MakeJsonPoint3(writer, fShadowRec.fLightPos);
   *     writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_LIGHTRADIUS, fShadowRec.fLightRadius);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_AMBIENTCOLOR);
   *     MakeJsonColor(writer, fShadowRec.fAmbientColor);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_SPOTCOLOR);
   *     MakeJsonColor(writer, fShadowRec.fSpotColor);
   *     store_bool(writer, DEBUGCANVAS_SHADOWFLAG_TRANSPARENT_OCC, transparentOccluder, false);
   *     store_bool(writer, DEBUGCANVAS_SHADOWFLAG_GEOMETRIC_ONLY, geometricOnly, false);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
