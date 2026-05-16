package org.skia.tests

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class LimitedRewindingStream : public SkStream {
 * public:
 *     static std::unique_ptr<SkStream> Make(const char path[], size_t limit) {
 *         auto stream = GetResourceAsStream(path);
 *         if (!stream) {
 *             return nullptr;
 *         }
 *         return std::unique_ptr<SkStream>(new LimitedRewindingStream(std::move(stream), limit));
 *     }
 *
 *     size_t read(void* buffer, size_t size) override {
 *         const size_t bytes = fStream->read(buffer, size);
 *         fPosition += bytes;
 *         return bytes;
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fStream->isAtEnd();
 *     }
 *
 *     bool rewind() override {
 *         if (fPosition <= fLimit && fStream->rewind()) {
 *             fPosition = 0;
 *             return true;
 *         }
 *
 *         return false;
 *     }
 *
 * private:
 *     std::unique_ptr<SkStream> fStream;
 *     const size_t              fLimit;
 *     size_t                    fPosition;
 *
 *     LimitedRewindingStream(std::unique_ptr<SkStream> stream, size_t limit)
 *         : fStream(std::move(stream))
 *         , fLimit(limit)
 *         , fPosition(0)
 *     {
 *         SkASSERT(fStream);
 *     }
 * }
 * ```
 */
public open class LimitedRewindingStream public constructor(
  stream: SkStream?,
  limit: ULong,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStream> fStream
   * ```
   */
  private var fStream: Int = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * const size_t              fLimit
   * ```
   */
  private val fLimit: ULong = TODO("Initialize fLimit")

  /**
   * C++ original:
   * ```cpp
   * size_t                    fPosition
   * ```
   */
  private var fPosition: ULong = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buffer, size_t size) override {
   *         const size_t bytes = fStream->read(buffer, size);
   *         fPosition += bytes;
   *         return bytes;
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
   *         return fStream->isAtEnd();
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rewind() override {
   *         if (fPosition <= fLimit && fStream->rewind()) {
   *             fPosition = 0;
   *             return true;
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  public override fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<SkStream> Make(const char path[], size_t limit) {
     *         auto stream = GetResourceAsStream(path);
     *         if (!stream) {
     *             return nullptr;
     *         }
     *         return std::unique_ptr<SkStream>(new LimitedRewindingStream(std::move(stream), limit));
     *     }
     * ```
     */
    public fun make(path: CharArray, limit: ULong): Int {
      TODO("Implement make")
    }
  }
}
