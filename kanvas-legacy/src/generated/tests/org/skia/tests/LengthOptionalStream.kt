package org.skia.tests

import kotlin.Boolean
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class LengthOptionalStream : public SkStream {
 * public:
 *     LengthOptionalStream(bool hasLength, bool hasPosition)
 *         : fHasLength(hasLength)
 *         , fHasPosition(hasPosition)
 *     {}
 *
 *     bool hasLength() const override {
 *         return fHasLength;
 *     }
 *
 *     bool hasPosition() const override {
 *         return fHasPosition;
 *     }
 *
 *     size_t read(void*, size_t) override {
 *         return 0;
 *     }
 *
 *     bool isAtEnd() const override {
 *         return true;
 *     }
 *
 * private:
 *     const bool fHasLength;
 *     const bool fHasPosition;
 * }
 * ```
 */
public open class LengthOptionalStream public constructor(
  hasLength: Boolean,
  hasPosition: Boolean,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * const bool fHasLength
   * ```
   */
  private val fHasLength: Boolean = TODO("Initialize fHasLength")

  /**
   * C++ original:
   * ```cpp
   * const bool fHasPosition
   * ```
   */
  private val fHasPosition: Boolean = TODO("Initialize fHasPosition")

  /**
   * C++ original:
   * ```cpp
   * bool hasLength() const override {
   *         return fHasLength;
   *     }
   * ```
   */
  public override fun hasLength(): Boolean {
    TODO("Implement hasLength")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPosition() const override {
   *         return fHasPosition;
   *     }
   * ```
   */
  public override fun hasPosition(): Boolean {
    TODO("Implement hasPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t read(void*, size_t) override {
   *         return 0;
   *     }
   * ```
   */
  public override fun read(param0: Unit?, param1: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return true;
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }
}
