package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class BeginDrawPictureCommand : public DrawCommand {
 * public:
 *     BeginDrawPictureCommand(const SkPicture* picture, const SkMatrix* matrix, const SkPaint* paint);
 *
 *     void execute(SkCanvas* canvas) const override;
 *     bool render(SkCanvas* canvas) const override;
 *
 * private:
 *     sk_sp<const SkPicture>  fPicture;
 *     std::optional<SkMatrix> fMatrix;
 *     std::optional<SkPaint>  fPaint;
 *
 *     using INHERITED = DrawCommand;
 * }
 * ```
 */
public open class BeginDrawPictureCommand public constructor(
  picture: SkPicture?,
  matrix: SkMatrix?,
  paint: SkPaint?,
) : DrawCommand(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkPicture>  fPicture
   * ```
   */
  private val fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMatrix> fMatrix
   * ```
   */
  private var fMatrix: Int = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint>  fPaint
   * ```
   */
  private var fPaint: Int = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * void BeginDrawPictureCommand::execute(SkCanvas* canvas) const {
   *     if (fPaint.has_value()) {
   *         SkRect bounds = fPicture->cullRect();
   *         if (fMatrix.has_value()) {
   *             fMatrix->mapRect(&bounds);
   *         }
   *         canvas->saveLayer(&bounds, &fPaint.value());
   *     }
   *
   *     if (fMatrix.has_value()) {
   *         if (!fPaint.has_value()) {
   *             canvas->save();
   *         }
   *         canvas->concat(*fMatrix);
   *     }
   * }
   * ```
   */
  public override fun execute(canvas: SkCanvas?) {
    TODO("Implement execute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool BeginDrawPictureCommand::render(SkCanvas* canvas) const {
   *     canvas->clear(0xFFFFFFFF);
   *     canvas->save();
   *
   *     xlate_and_scale_to_bounds(canvas, fPicture->cullRect());
   *
   *     canvas->drawPicture(fPicture.get());
   *
   *     canvas->restore();
   *
   *     return true;
   * }
   * ```
   */
  public override fun render(canvas: SkCanvas?): Boolean {
    TODO("Implement render")
  }
}
