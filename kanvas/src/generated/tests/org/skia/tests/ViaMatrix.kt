package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class ViaMatrix : public Via {
 * public:
 *     ViaMatrix(SkMatrix, Sink*);
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *
 * private:
 *     const SkMatrix fMatrix;
 * }
 * ```
 */
public open class ViaMatrix public constructor(
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
   * ViaMatrix(SkMatrix, Sink*)
   * ```
   */
  public constructor(matrix: SkMatrix, sink: Sink?) : this(TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result ViaMatrix::draw(const Src& src, SkBitmap* bitmap, SkWStream* stream, SkString* log) const {
   *     SkMatrix matrix = fMatrix;
   *     SkISize size = auto_compute_translate(&matrix, src.size().width(), src.size().height());
   *     return draw_to_canvas(fSink.get(), bitmap, stream, log, size,
   *                           [&](SkCanvas* canvas,
   *                               Src::GraphiteTestContext* testContext) {
   *                               canvas->concat(matrix);
   *                               return src.draw(canvas, testContext);
   *                           });
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
