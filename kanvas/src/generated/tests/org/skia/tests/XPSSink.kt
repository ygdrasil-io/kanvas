package org.skia.tests

import kotlin.Char
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class XPSSink : public Sink {
 * public:
 *     XPSSink();
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *     const char* fileExtension() const override { return "xps"; }
 *     SinkFlags flags() const override { return SinkFlags{ SinkFlags::kVector, SinkFlags::kDirect }; }
 * }
 * ```
 */
public open class XPSSink public constructor() : Sink() {
  /**
   * C++ original:
   * ```cpp
   * Result XPSSink::draw(const Src& src, SkBitmap*, SkWStream* dst, SkString*) const {
   *     return Result::Fatal("XPS not supported on this platform.");
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
   * const char* fileExtension() const override { return "xps"; }
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
