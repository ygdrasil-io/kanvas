package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class DebugSink : public Sink {
 * public:
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "json"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
 * }
 * ```
 */
public open class DebugSink : Sink() {
  /**
   * C++ original:
   * ```cpp
   * Result DebugSink::draw(const Src& src, SkBitmap*, SkWStream* dst, SkString*) const {
   *     DebugCanvas debugCanvas(src.size().width(), src.size().height());
   *     Result result = src.draw(&debugCanvas, /*GraphiteTestContext=*/nullptr);
   *     if (!result.isOk()) {
   *         return result;
   *     }
   *     std::unique_ptr<SkCanvas> nullCanvas = SkMakeNullCanvas();
   *     UrlDataManager dataManager(SkString("data"));
   *     SkJSONWriter writer(dst, SkJSONWriter::Mode::kPretty);
   *     writer.beginObject(); // root
   *     debugCanvas.toJSON(writer, dataManager, nullCanvas.get());
   *     writer.endObject(); // root
   *     writer.flush();
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
   * const char* fileExtension() const override { return "json"; }
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
