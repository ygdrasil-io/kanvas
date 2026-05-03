package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkMemoryStream
import org.skia.foundation.SkSp
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class LimitedPeekingMemStream : public SkStream {
 * public:
 *     LimitedPeekingMemStream(sk_sp<SkData> data, size_t limit)
 *         : fStream(std::move(data))
 *         , fLimit(limit) {}
 *
 *     size_t peek(void* buf, size_t bytes) const override {
 *         return fStream.peek(buf, std::min(bytes, fLimit));
 *     }
 *     size_t read(void* buf, size_t bytes) override {
 *         return fStream.read(buf, bytes);
 *     }
 *     bool rewind() override {
 *         return fStream.rewind();
 *     }
 *     bool isAtEnd() const override {
 *         return fStream.isAtEnd();
 *     }
 * private:
 *     SkMemoryStream fStream;
 *     const size_t   fLimit;
 * }
 * ```
 */
public open class LimitedPeekingMemStream public constructor(
  `data`: SkSp<SkData>,
  limit: ULong,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * SkMemoryStream fStream
   * ```
   */
  private var fStream: SkMemoryStream = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * const size_t   fLimit
   * ```
   */
  private val fLimit: ULong = TODO("Initialize fLimit")

  /**
   * C++ original:
   * ```cpp
   * size_t peek(void* buf, size_t bytes) const override {
   *         return fStream.peek(buf, std::min(bytes, fLimit));
   *     }
   * ```
   */
  public override fun peek(buf: Unit?, bytes: ULong): ULong {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buf, size_t bytes) override {
   *         return fStream.read(buf, bytes);
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
   *         return fStream.rewind();
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
   *         return fStream.isAtEnd();
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
