package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawImageRectLayerCommand : public DrawCommand {
 * public:
 *     DrawImageRectLayerCommand(DebugLayerManager*          layerManager,
 *                               const int                   nodeId,
 *                               const int                   frame,
 *                               const SkRect&               src,
 *                               const SkRect&               dst,
 *                               const SkSamplingOptions&    sampling,
 *                               const SkPaint*              paint,
 *                               SkCanvas::SrcRectConstraint constraint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *
 * private:
 *     DebugLayerManager*          fLayerManager;
 *     int                         fNodeId;
 *     int                         fFrame;
 *     SkRect                      fSrc;
 *     SkRect                      fDst;
 *     SkSamplingOptions           fSampling;
 *     std::optional<SkPaint>      fPaint;
 *     SkCanvas::SrcRectConstraint fConstraint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawImageRectLayerCommand public constructor(
  layerManager: DebugLayerManager?,
  nodeId: Int,
  frame: Int,
  src: SkRect,
  dst: SkRect,
  sampling: SkSamplingOptions,
  paint: SkPaint?,
  constraint: SkCanvas.SrcRectConstraint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * DebugLayerManager*          fLayerManager
   * ```
   */
  private var fLayerManager: DebugLayerManager? = TODO("Initialize fLayerManager")

  /**
   * C++ original:
   * ```cpp
   * int                         fNodeId
   * ```
   */
  private var fNodeId: Int = TODO("Initialize fNodeId")

  /**
   * C++ original:
   * ```cpp
   * int                         fFrame
   * ```
   */
  private var fFrame: Int = TODO("Initialize fFrame")

  /**
   * C++ original:
   * ```cpp
   * SkRect                      fSrc
   * ```
   */
  private var fSrc: SkRect = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * SkRect                      fDst
   * ```
   */
  private var fDst: SkRect = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions           fSampling
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>      fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * SkCanvas::SrcRectConstraint fConstraint
   * ```
   */
  private var fConstraint: SkCanvas.SrcRectConstraint = TODO("Initialize fConstraint")

  /**
   * C++ original:
   * ```cpp
   * void DrawImageRectLayerCommand::execute(SkCanvas* canvas) const {
   *     sk_sp<SkImage> snapshot = fLayerManager->getLayerAsImage(fNodeId, fFrame);
   *     canvas->drawImageRect(snapshot.get(), fSrc, fDst, SkSamplingOptions(),
   *                           SkOptAddressOrNull(fPaint), fConstraint);
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawImageRectLayerCommand::render(SkCanvas* canvas) const {
   *     SkAutoCanvasRestore acr(canvas, true);
   *     canvas->clear(0xFFFFFFFF);
   *
   *     xlate_and_scale_to_bounds(canvas, fDst);
   *
   *     this->execute(canvas);
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
   * void DrawImageRectLayerCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *
   *     // Don't append an image attribute here, the image can be rendered in as many different ways
   *     // as there are commands in the layer, at least. the urlDataManager would save each one under
   *     // a different URL.
   *     // Append the node id, and the layer inspector of the debugger will know what to do with it.
   *     writer.appendS64(DEBUGCANVAS_ATTRIBUTE_LAYERNODEID, fNodeId);
   *
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_SRC);
   *     MakeJsonRect(writer, fSrc);
   *
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_DST);
   *     MakeJsonRect(writer, fDst);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_SAMPLING);
   *     MakeJsonSampling(writer, fSampling);
   *     if (fPaint.has_value()) {
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *         MakeJsonPaint(writer, *fPaint, urlDataManager);
   *     }
   *     if (fConstraint == SkCanvas::kStrict_SrcRectConstraint) {
   *         writer.appendBool(DEBUGCANVAS_ATTRIBUTE_STRICT, true);
   *     }
   *
   *     SkString desc;
   *     writer.appendString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, *str_append(&desc, fDst));
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }
}
