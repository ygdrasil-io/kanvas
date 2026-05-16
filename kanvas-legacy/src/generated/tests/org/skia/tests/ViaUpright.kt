package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class ViaUpright : public Via {
 * public:
 *     ViaUpright(SkMatrix, Sink*);
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *
 * private:
 *     const SkMatrix fMatrix;
 * }
 * ```
 */
public open class ViaUpright public constructor(
  param0: SkMatrix,
  param1: Sink,
) : Via() {
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fMatrix
   * ```
   */
  private val fMatrix: Int = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * ViaUpright(SkMatrix, Sink*)
   * ```
   */
  public constructor(matrix: SkMatrix, sink: Sink?) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result ViaUpright::draw(const Src& src, SkBitmap* bitmap, SkWStream* stream, SkString* log) const {
   *     Result result = fSink->draw(src, bitmap, stream, log);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *
   *     SkMatrix inverse;
   *     if (!fMatrix.rectStaysRect() || !fMatrix.invert(&inverse)) {
   *         return Result::Fatal("Cannot upright --matrix.");
   *     }
   *     SkMatrix upright = SkMatrix::I();
   *     upright.setScaleX(SkScalarSignAsScalar(inverse.getScaleX()));
   *     upright.setScaleY(SkScalarSignAsScalar(inverse.getScaleY()));
   *     upright.setSkewX(SkScalarSignAsScalar(inverse.getSkewX()));
   *     upright.setSkewY(SkScalarSignAsScalar(inverse.getSkewY()));
   *
   *     SkBitmap uprighted;
   *     SkISize size = auto_compute_translate(&upright, bitmap->width(), bitmap->height());
   *     uprighted.allocPixels(bitmap->info().makeDimensions(size));
   *
   *     SkCanvas canvas(uprighted);
   *     canvas.concat(upright);
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kSrc);
   *     canvas.drawImage(bitmap->asImage(), 0, 0, SkSamplingOptions(), &paint);
   *
   *     *bitmap = uprighted;
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    bitmap: SkBitmap?,
    stream: SkWStream?,
    log: String?,
  ): Result {
    TODO("Implement draw")
  }
}
