package org.skia.tests

import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class ViaRuntimeBlend : public Via {
 * public:
 *     explicit ViaRuntimeBlend(Sink* sink) : Via(sink) {}
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 * }
 * ```
 */
public open class ViaRuntimeBlend public constructor(
  sink: Sink?,
) : Via(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Result ViaRuntimeBlend::draw(const Src& src,
   *                              SkBitmap* bitmap,
   *                              SkWStream* stream,
   *                              SkString* log) const {
   *     class RuntimeBlendFilterCanvas : public SkPaintFilterCanvas {
   *     public:
   *         RuntimeBlendFilterCanvas(SkCanvas* canvas) : INHERITED(canvas) { }
   *
   *     protected:
   *         bool onFilter(SkPaint& paint) const override {
   *             if (std::optional<SkBlendMode> mode = paint.asBlendMode()) {
   *                 paint.setBlender(GetRuntimeBlendForBlendMode(*mode));
   *             }
   *             return true;
   *         }
   *
   *     private:
   *         using INHERITED = SkPaintFilterCanvas;
   *     };
   *
   *     return draw_to_canvas(fSink.get(), bitmap, stream, log, src.size(),
   *                           [&](SkCanvas* canvas, Src::GraphiteTestContext* testContext) {
   *         RuntimeBlendFilterCanvas runtimeBlendCanvas{canvas};
   *         return src.draw(&runtimeBlendCanvas, testContext);
   *     });
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
