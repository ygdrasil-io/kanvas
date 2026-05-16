package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class UnowningStream : public SkStream {
 * public:
 *     explicit UnowningStream(SkStream* stream)
 *         : fStream(stream)
 *     {}
 *
 *     size_t read(void* buf, size_t bytes) override {
 *         return fStream->read(buf, bytes);
 *     }
 *
 *     bool rewind() override {
 *         return fStream->rewind();
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fStream->isAtEnd();
 *     }
 * private:
 *     SkStream* fStream; // Unowned.
 * }
 * ```
 */
public open class UnowningStream public constructor(
  stream: SkStream?,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * SkStream* fStream
   * ```
   */
  private var fStream: SkStream? = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buf, size_t bytes) override {
   *         return fStream->read(buf, bytes);
   *     }
   * ```
   */
  public override fun read(buf: Unit?, bytes: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rewind() override {
   *         return fStream->rewind();
   *     }
   * ```
   */
  public override fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return fStream->isAtEnd();
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
