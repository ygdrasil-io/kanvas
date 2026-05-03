package org.skia.tests

import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class ViaPicture : public Via {
 * public:
 *     explicit ViaPicture(Sink* sink) : Via(sink) {}
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 * }
 * ```
 */
public open class ViaPicture public constructor(
  sink: Sink?,
) : Via(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Result ViaPicture::draw(const Src& src, SkBitmap* bitmap, SkWStream* stream, SkString* log) const {
   *     auto size = src.size();
   *     Result result = draw_to_canvas(fSink.get(), bitmap, stream, log, size,
   *                                    [&](SkCanvas* canvas, Src::GraphiteTestContext* testContext) {
   *         SkPictureRecorder recorder;
   *         sk_sp<SkPicture> pic;
   *         Result result = src.draw(recorder.beginRecording(SkIntToScalar(size.width()),
   *                                                          SkIntToScalar(size.height())),
   *                                  testContext);
   *         if (!result.isOk()) {
   *             return result;
   *         }
   *         pic = recorder.finishRecordingAsPicture();
   *         canvas->drawPicture(pic);
   *         return result;
   *     });
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *
   *     return check_against_reference(bitmap, src, fSink.get());
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
