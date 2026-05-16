package org.skia.tools

import kotlin.Array
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.json.SkJSONWriter
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class DrawPatchCommand : public DrawCommand {
 * public:
 *     DrawPatchCommand(const SkPoint  cubics[12],
 *                      const SkColor  colors[4],
 *                      const SkPoint  texCoords[4],
 *                      SkBlendMode    bmode,
 *                      const SkPaint& paint);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkPoint     fCubics[12];
 *     SkColor*    fColorsPtr;
 *     SkColor     fColors[4];
 *     SkPoint*    fTexCoordsPtr;
 *     SkPoint     fTexCoords[4];
 *     SkBlendMode fBlendMode;
 *     SkPaint     fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawPatchCommand public constructor(
  cubics: Array<SkPoint>,
  colors: Array<SkColor>,
  texCoords: Array<SkPoint>,
  bmode: SkBlendMode,
  paint: SkPaint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fCubics[12]
   * ```
   */
  private var fCubics: Array<SkPoint> = TODO("Initialize fCubics")

  /**
   * C++ original:
   * ```cpp
   * SkColor*    fColorsPtr
   * ```
   */
  private var fColorsPtr: SkColor? = TODO("Initialize fColorsPtr")

  /**
   * C++ original:
   * ```cpp
   * SkColor     fColors[4]
   * ```
   */
  private var fColors: Array<SkColor> = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkPoint*    fTexCoordsPtr
   * ```
   */
  private var fTexCoordsPtr: SkPoint? = TODO("Initialize fTexCoordsPtr")

  /**
   * C++ original:
   * ```cpp
   * SkPoint     fTexCoords[4]
   * ```
   */
  private var fTexCoords: Array<SkPoint> = TODO("Initialize fTexCoords")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fBlendMode
   * ```
   */
  private var fBlendMode: SkBlendMode = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * SkPaint     fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawPatchCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawPatch(fCubics, fColorsPtr, fTexCoordsPtr, fBlendMode, fPaint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawPatchCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     writer.beginArray(DEBUGCANVAS_ATTRIBUTE_CUBICS);
   *     for (int i = 0; i < 12; i++) {
   *         MakeJsonPoint(writer, fCubics[i]);
   *     }
   *     writer.endArray();  // cubics
   *     if (fColorsPtr != nullptr) {
   *         writer.beginArray(DEBUGCANVAS_ATTRIBUTE_COLORS);
   *         for (int i = 0; i < 4; i++) {
   *             MakeJsonColor(writer, fColorsPtr[i]);
   *         }
   *         writer.endArray();  // colors
   *     }
   *     if (fTexCoordsPtr != nullptr) {
   *         writer.beginArray(DEBUGCANVAS_ATTRIBUTE_TEXTURECOORDS);
   *         for (int i = 0; i < 4; i++) {
   *             MakeJsonPoint(writer, fTexCoords[i]);
   *         }
   *         writer.endArray();  // texCoords
   *     }
   *     // fBlendMode
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
