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
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DrawImageCommand : public DrawCommand {
 * public:
 *     DrawImageCommand(const SkImage* image, SkScalar left, SkScalar top,
 *                      const SkSamplingOptions&, const SkPaint* paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *     uint64_t imageId(UrlDataManager& udb) const;
 *
 * private:
 *     sk_sp<const SkImage> fImage;
 *     SkScalar             fLeft;
 *     SkScalar             fTop;
 *     SkSamplingOptions    fSampling;
 *     std::optional<SkPaint> fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawImageCommand public constructor(
  image: SkImage?,
  left: SkScalar,
  top: SkScalar,
  sampling: SkSamplingOptions,
  paint: SkPaint?,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkImage> fImage
   * ```
   */
  private val fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkScalar             fLeft
   * ```
   */
  private var fLeft: SkScalar = TODO("Initialize fLeft")

  /**
   * C++ original:
   * ```cpp
   * SkScalar             fTop
   * ```
   */
  private var fTop: SkScalar = TODO("Initialize fTop")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions    fSampling
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint> fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void DrawImageCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawImage(fImage.get(), fLeft, fTop, fSampling, SkOptAddressOrNull(fPaint));
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawImageCommand::render(SkCanvas* canvas) const {
   *     SkAutoCanvasRestore acr(canvas, true);
   *     canvas->clear(0xFFFFFFFF);
   *
   *     xlate_and_scale_to_bounds(
   *             canvas,
   *             SkRect::MakeXYWH(
   *                     fLeft, fTop, SkIntToScalar(fImage->width()), SkIntToScalar(fImage->height())));
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
   * void DrawImageCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     flatten(*fImage, writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_COORDS);
   *     MakeJsonPoint(writer, fLeft, fTop);
   *     if (fPaint.has_value()) {
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *         MakeJsonPaint(writer, *fPaint, urlDataManager);
   *     }
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_SAMPLING);
   *     MakeJsonSampling(writer, fSampling);
   *
   *     writer.appendU32(DEBUGCANVAS_ATTRIBUTE_UNIQUE_ID, fImage->uniqueID());
   *     writer.appendS32(DEBUGCANVAS_ATTRIBUTE_WIDTH, fImage->width());
   *     writer.appendS32(DEBUGCANVAS_ATTRIBUTE_HEIGHT, fImage->height());
   *     switch (fImage->alphaType()) {
   *         case kOpaque_SkAlphaType:
   *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_ALPHA, DEBUGCANVAS_ALPHATYPE_OPAQUE);
   *             break;
   *         case kPremul_SkAlphaType:
   *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_ALPHA, DEBUGCANVAS_ALPHATYPE_PREMUL);
   *             break;
   *         case kUnpremul_SkAlphaType:
   *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_ALPHA, DEBUGCANVAS_ALPHATYPE_UNPREMUL);
   *             break;
   *         default:
   *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_ALPHA, DEBUGCANVAS_ALPHATYPE_UNKNOWN);
   *             break;
   *     }
   * }
   * ```
   */
  public override fun toJSON(writer: SkJSONWriter, urlDataManager: UrlDataManager) {
    TODO("Implement toJSON")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t DrawImageCommand::imageId(UrlDataManager& udm) const {
   *     return udm.lookupImage(fImage.get());
   * }
   * ```
   */
  public fun imageId(udb: UrlDataManager): ULong {
    TODO("Implement imageId")
  }
}
