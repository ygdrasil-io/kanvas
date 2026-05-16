package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class SKPSink : public Sink {
 * public:
 *     SKPSink();
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "skp"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
 * }
 * ```
 */
public open class SKPSink public constructor() : Sink() {
  /**
   * C++ original:
   * ```cpp
   * Result SKPSink::draw(const Src& src, SkBitmap*, SkWStream* dst, SkString*) const {
   *     auto size = SkSize::Make(src.size());
   *     SkPictureRecorder recorder;
   *     Result result = src.draw(recorder.beginRecording(size.width(), size.height()),
   *                              /*GraphiteTestContext=*/nullptr);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *     SkSerialProcs procs = serial_procs_using_png();
   *     recorder.finishRecordingAsPicture()->serialize(dst, &procs);
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    param1: SkBitmap?,
    dst: SkWStream?,
    param3: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override { return "skp"; }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
   * ```
   */
  public override fun flags(): SinkFlags {
    TODO("Implement flags")
  }
}
