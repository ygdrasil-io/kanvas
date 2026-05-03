package org.skia.tools

import kotlin.CharArray
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawAnnotationCommand : public DrawCommand {
 * public:
 *     DrawAnnotationCommand(const SkRect&, const char key[], sk_sp<SkData> value);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     SkRect        fRect;
 *     SkString      fKey;
 *     sk_sp<SkData> fValue;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawAnnotationCommand public constructor(
  rect: SkRect,
  key: CharArray,
  `value`: SkSp<SkData>,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect        fRect
   * ```
   */
  private var fRect: SkRect = TODO("Initialize fRect")

  /**
   * C++ original:
   * ```cpp
   * SkString      fKey
   * ```
   */
  private var fKey: String = TODO("Initialize fKey")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fValue
   * ```
   */
  private var fValue: SkSp<SkData> = TODO("Initialize fValue")

  /**
   * C++ original:
   * ```cpp
   * void DrawAnnotationCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawAnnotation(fRect, fKey.c_str(), fValue);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawAnnotationCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     MakeJsonRect(writer, fRect);
   *     writer.appendString("key", fKey);
   *     if (fValue) {
   *         writer.appendString("value", static_cast<const char*>(fValue->data()), fValue->size());
   *     }
   *
   *     SkString desc;
   *     str_append(&desc, fRect)->appendf(" %s", fKey.c_str());
   *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, desc);
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
