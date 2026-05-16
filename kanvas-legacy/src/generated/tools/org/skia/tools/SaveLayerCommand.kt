package org.skia.tools

import kotlin.Int
import kotlin.UInt
import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.json.SkJSONWriter
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SaveLayerCommand : public DrawCommand {
 * public:
 *     SaveLayerCommand(const SkCanvas::SaveLayerRec&);
 *     void execute(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     std::optional<SkRect>      fBounds;
 *     std::optional<SkPaint>     fPaint;
 *     sk_sp<const SkImageFilter> fBackdrop;
 *     uint32_t                   fSaveLayerFlags;
 *     SkScalar                   fBackdropScale;
 *     SkTileMode                 fBackdropTileMode;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class SaveLayerCommand public constructor(
  rec: SkCanvas.SaveLayerRec,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkRect>      fBounds
   * ```
   */
  private var fBounds: Int = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>     fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkImageFilter> fBackdrop
   * ```
   */
  private val fBackdrop: SkSp<SkImageFilter> = TODO("Initialize fBackdrop")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                   fSaveLayerFlags
   * ```
   */
  private var fSaveLayerFlags: UInt = TODO("Initialize fSaveLayerFlags")

  /**
   * C++ original:
   * ```cpp
   * SkScalar                   fBackdropScale
   * ```
   */
  private var fBackdropScale: SkScalar = TODO("Initialize fBackdropScale")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode                 fBackdropTileMode
   * ```
   */
  private var fBackdropTileMode: SkTileMode = TODO("Initialize fBackdropTileMode")

  /**
   * C++ original:
   * ```cpp
   * void SaveLayerCommand::execute(SkCanvas* canvas) const {
   *     // In the common case fBackdropScale == 1.f and then this is no different than a regular Rec
   *     canvas->saveLayer(SkCanvasPriv::ScaledBackdropLayer(SkOptAddressOrNull(fBounds),
   *                                                         SkOptAddressOrNull(fPaint),
   *                                                         fBackdrop.get(),
   *                                                         fBackdropScale,
   *                                                         fBackdropTileMode,
   *                                                         fSaveLayerFlags));
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * void SaveLayerCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     if (fBounds.has_value()) {
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_BOUNDS);
   *         MakeJsonRect(writer, *fBounds);
   *     }
   *     if (fPaint.has_value()) {
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *         MakeJsonPaint(writer, *fPaint, urlDataManager);
   *     }
   *     if (fBackdrop != nullptr) {
   *         writer.beginObject(DEBUGCANVAS_ATTRIBUTE_BACKDROP);
   *         flatten(fBackdrop.get(), writer, urlDataManager);
   *         writer.endObject();  // backdrop
   *     }
   *     if (fSaveLayerFlags != 0) {
   *         SkDebugf("unsupported: saveLayer flags\n");
   *         SkASSERT(false);
   *     }
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
