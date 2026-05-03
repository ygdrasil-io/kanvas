package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.core.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.json.SkJSONWriter
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class DrawImageLatticeCommand : public DrawCommand {
 * public:
 *     DrawImageLatticeCommand(const SkImage*           image,
 *                             const SkCanvas::Lattice& lattice,
 *                             const SkRect&            dst,
 *                             SkFilterMode,
 *                             const SkPaint*           paint);
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *     void toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const override;
 *     uint64_t imageId(UrlDataManager& udb) const;
 *
 * private:
 *     sk_sp<const SkImage> fImage;
 *     SkCanvas::Lattice    fLattice;
 *     SkRect               fDst;
 *     SkFilterMode         fFilter;
 *     std::optional<SkPaint> fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class DrawImageLatticeCommand public constructor(
  image: SkImage?,
  lattice: SkCanvas.Lattice,
  dst: SkRect,
  filter: SkFilterMode,
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
   * SkCanvas::Lattice    fLattice
   * ```
   */
  private var fLattice: SkCanvas.Lattice = TODO("Initialize fLattice")

  /**
   * C++ original:
   * ```cpp
   * SkRect               fDst
   * ```
   */
  private var fDst: SkRect = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * SkFilterMode         fFilter
   * ```
   */
  private var fFilter: SkFilterMode = TODO("Initialize fFilter")

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
   * void DrawImageLatticeCommand::execute(SkCanvas* canvas) const {
   *     canvas->drawImageLattice(fImage.get(), fLattice, fDst, fFilter, SkOptAddressOrNull(fPaint));
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawImageLatticeCommand::render(SkCanvas* canvas) const {
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
   * void DrawImageLatticeCommand::toJSON(SkJSONWriter& writer, UrlDataManager& urlDataManager) const {
   *     INHERITED::toJSON(writer, urlDataManager);
   *     flatten(*fImage, writer, urlDataManager);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_LATTICE);
   *     MakeJsonLattice(writer, fLattice);
   *     writer.appendName(DEBUGCANVAS_ATTRIBUTE_DST);
   *     MakeJsonRect(writer, fDst);
   *     if (fPaint.has_value()) {
   *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_PAINT);
   *         MakeJsonPaint(writer, *fPaint, urlDataManager);
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
   * uint64_t DrawImageLatticeCommand::imageId(UrlDataManager& udm) const {
   *     return udm.lookupImage(fImage.get());
   * }
   * ```
   */
  public fun imageId(udb: UrlDataManager): ULong {
    TODO("Implement imageId")
  }
}
