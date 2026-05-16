package org.skia.tools

import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.foundation.SkRegion
import org.skia.json.SkJSONWriter

/**
 * C++ original:
 * ```cpp
 * class ClipRegionCommand : public DrawCommand {
 * public:
 *     ClipRegionCommand(const SkRegion& region, SkClipOp op);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRegion fRegion;
 *     SkClipOp fOp;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class ClipRegionCommand public constructor(
  region: SkRegion,
  op: SkClipOp,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRegion fRegion
   * ```
   */
  private var fRegion: SkRegion = TODO("Initialize fRegion")

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
   * void ClipRegionCommand::execute(SkCanvas* canvas) const { canvas->clipRegion(fRegion, fOp); }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipRegionCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_REGION);
   *     MakeJsonRegion(writer, fRegion);
   *     writer.appendCString(DEBUGCANVAS_ATTRIBUTE_REGIONOP, clipop_name(fOp));
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
