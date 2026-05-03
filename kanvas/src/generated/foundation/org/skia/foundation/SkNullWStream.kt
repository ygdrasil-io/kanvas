package org.skia.foundation

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SK_API SkNullWStream : public SkWStream {
 * public:
 *     SkNullWStream() : fBytesWritten(0) {}
 *
 *     bool write(const void* , size_t n) override { fBytesWritten += n; return true; }
 *     void flush() override {}
 *     size_t bytesWritten() const override { return fBytesWritten; }
 *
 * private:
 *     size_t fBytesWritten;
 * }
 * ```
 */
public open class SkNullWStream public constructor() : SkWStream() {
  /**
   * C++ original:
   * ```cpp
   * size_t fBytesWritten
   * ```
   */
  private var fBytesWritten: ULong = TODO("Initialize fBytesWritten")

  /**
   * C++ original:
   * ```cpp
   * bool write(const void* , size_t n) override { fBytesWritten += n; return true; }
   * ```
   */
  public override fun write(param0: Unit?, n: ULong): Boolean {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void flush() override {}
   * ```
   */
  public override fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesWritten() const override { return fBytesWritten; }
   * ```
   */
  public override fun bytesWritten(): ULong {
    TODO("Implement bytesWritten")
  }
}
