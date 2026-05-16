package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class DebugfStream final : public SkWStream {
 * public:
 *     bool write(const void* buffer, size_t size) override;
 *     size_t bytesWritten() const override;
 *
 * private:
 *     size_t fBytesWritten = 0;
 * }
 * ```
 */
public class DebugfStream : SkWStream() {
  /**
   * C++ original:
   * ```cpp
   * size_t fBytesWritten
   * ```
   */
  private var fBytesWritten: Int = TODO("Initialize fBytesWritten")

  /**
   * C++ original:
   * ```cpp
   * bool DebugfStream::write(const void* buffer, size_t size) {
   *     SkDebugf("%.*s", (int)size, (const char*)buffer);
   *     fBytesWritten += size;
   *     return true;
   * }
   * ```
   */
  public override fun write(buffer: Unit?, size: ULong): Boolean {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t DebugfStream::bytesWritten() const { return fBytesWritten; }
   * ```
   */
  public override fun bytesWritten(): Int {
    TODO("Implement bytesWritten")
  }
}
