package org.skia.tests

import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class ViaSerialization : public Via {
 * public:
 *     explicit ViaSerialization(Sink* sink) : Via(sink) {}
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 * }
 * ```
 */
public open class ViaSerialization public constructor(
  sink: Sink?,
) : Via(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Result ViaSerialization::draw(
   *         const Src& src, SkBitmap* bitmap, SkWStream* stream, SkString* log) const {
   *     // Record our Src into a picture.
   *     auto size = src.size();
   *     SkPictureRecorder recorder;
   *     Result result = src.draw(recorder.beginRecording(SkIntToScalar(size.width()),
   *                                                      SkIntToScalar(size.height())),
   *                                                      /*GraphiteTestContext=*/nullptr);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *     sk_sp<SkPicture> pic(recorder.finishRecordingAsPicture());
   *
   *     SkSerialProcs procs = serial_procs_using_png();
   *     SkDeserialProcs dProcs = deserial_procs_using_png();
   *     // Serialize it and then deserialize it.
   *     sk_sp<SkPicture> deserialized = SkPicture::MakeFromData(pic->serialize(&procs).get(), &dProcs);
   *
   *     result = draw_to_canvas(fSink.get(), bitmap, stream, log, size,
   *                             [&](SkCanvas* canvas, Src::GraphiteTestContext*) {
   *                                 canvas->drawPicture(deserialized);
   *                                 return Result::Ok();
   *                             });
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
