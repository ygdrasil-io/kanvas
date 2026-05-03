package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class FailingStream : public SkStream {
 * public:
 *     FailingStream()
 *     : fAtEnd(false)
 *     {}
 *
 *     size_t read(void* buffer, size_t size) override {
 *         SkASSERT(!fAtEnd);
 *         fAtEnd = true;
 *         return 0;
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fAtEnd;
 *     }
 *
 * private:
 *     bool fAtEnd;
 * }
 * ```
 */
public open class FailingStream public constructor() : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * bool fAtEnd
   * ```
   */
  private var fAtEnd: Boolean = TODO("Initialize fAtEnd")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buffer, size_t size) override {
   *         SkASSERT(!fAtEnd);
   *         fAtEnd = true;
   *         return 0;
   *     }
   * ```
   */
  public override fun read(buffer: Unit?, size: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return fAtEnd;
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
