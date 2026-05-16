package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkMemoryStream

/**
 * C++ original:
 * ```cpp
 * class AndroidLikeMemoryStream : public SkMemoryStream {
 * public:
 *     AndroidLikeMemoryStream(void* data, size_t size, bool ownMemory)
 *         : INHERITED(data, size, ownMemory)
 *         , fIsAtEnd(false) {}
 *
 *     size_t read(void* dst, size_t requested) override {
 *         size_t bytesRead = this->INHERITED::read(dst, requested);
 *         if (bytesRead < requested) {
 *             fIsAtEnd = true;
 *         }
 *         return bytesRead;
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fIsAtEnd;
 *     }
 *
 * private:
 *     bool fIsAtEnd;
 *     using INHERITED = SkMemoryStream;
 * }
 * ```
 */
public open class AndroidLikeMemoryStream public constructor(
  `data`: Unit?,
  size: ULong,
  ownMemory: Boolean,
) : SkMemoryStream(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool fIsAtEnd
   * ```
   */
  private var fIsAtEnd: Boolean = TODO("Initialize fIsAtEnd")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* dst, size_t requested) override {
   *         size_t bytesRead = this->INHERITED::read(dst, requested);
   *         if (bytesRead < requested) {
   *             fIsAtEnd = true;
   *         }
   *         return bytesRead;
   *     }
   * ```
   */
  public override fun read(dst: Unit?, requested: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return fIsAtEnd;
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
