package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawImageRectCommand : public DrawCommand {
 * public:
 *     DrawImageRectCommand(const SkImage*              image,
 *                          const SkRect&               src,
 *                          const SkRect&               dst,
 *                          const SkSamplingOptions&    sampling,
 *                          const SkPaint*              paint,
 *                          SkCanvas::SrcRectConstraint constraint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *     uint64_t imageId(UrlDataManager& udm) const;
 *
 * private:
 *     sk_sp<const SkImage>        fImage;
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
public open class DrawImageRectCommand public constructor(
  image: SkImage?,
  src: SkRect,
  dst: SkRect,
  sampling: SkSamplingOptions,
  paint: SkPaint?,
  constraint: SkCanvas.SrcRectConstraint,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkImage>        fImage
   * ```
   */
  private val fImage: SkSp<SkImage> = TODO("Initialize fImage")

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
   * void DrawImageRectCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawImageRect(fImage.get(), fSrc, fDst, fSampling,
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
   * bool DrawImageRectCommand::render(SkCanvas* canvas) const {
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
   * void DrawImageRectCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     flatten(*fImage, writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_SRC);
   *     MakeJsonRect(writer, fSrc);
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

  /**
   * C++ original:
   * ```cpp
   * uint64_t DrawImageRectCommand::imageId(UrlDataManager& udm) const {
   *     return udm.lookupImage(fImage.get());
   * }
   * ```
   */
  public fun imageId(udm: UrlDataManager): ULong {
    TODO("Implement imageId")
  }
}
