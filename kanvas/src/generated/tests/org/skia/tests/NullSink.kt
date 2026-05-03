package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class NullSink : public Sink {
 * public:
 *     NullSink() {}
 *
 *     Result draw(const Src& src, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return ""; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kNull, SinkFlags::kDirect }; }
 * }
 * ```
 */
public open class NullSink public constructor() : Sink() {
  /**
   * C++ original:
   * ```cpp
   * Result NullSink::draw(const Src& src, SkBitmap*, SkWStream*, SkString*) const {
   *     return src.draw(SkMakeNullCanvas().get(), /*GraphiteTestContext=*/nullptr);
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    param1: SkBitmap?,
    param2: SkWStream?,
    param3: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override { return ""; }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }

  /**
   * C++ original:
   * ```cpp
   * SinkFlags flags() const override { return SinkFlags{ SinkFlags::kNull, SinkFlags::kDirect }; }
   * ```
   */
  public override fun flags(): SinkFlags {
    TODO("Implement flags")
  }
}
