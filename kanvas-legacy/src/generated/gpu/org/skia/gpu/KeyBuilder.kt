package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.Unit
import skia_private.TArraytrue

/**
 * C++ original:
 * ```cpp
 * class KeyBuilder {
 * public:
 *     KeyBuilder(skia_private::TArray<uint32_t, true>* data) : fData(data) {}
 *
 *     virtual ~KeyBuilder() {
 *         // Ensure that flush was called before we went out of scope
 *         SkASSERT(fBitsUsed == 0);
 *     }
 *
 *     virtual void addBits(uint32_t numBits, uint32_t val, std::string_view label) {
 *         SkASSERT(numBits > 0 && numBits <= 32);
 *         SkASSERT(numBits == 32 || (val < (1u << numBits)));
 *
 *         fCurValue |= (val << fBitsUsed);
 *         fBitsUsed += numBits;
 *
 *         if (fBitsUsed >= 32) {
 *             // Overflow, start a new working value
 *             fData->push_back(fCurValue);
 *             uint32_t excess = fBitsUsed - 32;
 *             fCurValue = excess ? (val >> (numBits - excess)) : 0;
 *             fBitsUsed = excess;
 *         }
 *
 *         SkASSERT(fCurValue < (1u << fBitsUsed));
 *     }
 *
 *     void addBytes(uint32_t numBytes, const void* data, std::string_view label) {
 *         const uint8_t* bytes = reinterpret_cast<const uint8_t*>(data);
 *         for (; numBytes --> 0; bytes++) {
 *             this->addBits(8, *bytes, label);
 *         }
 *     }
 *
 *     void addBool(bool b, std::string_view label) {
 *         this->addBits(1, b, label);
 *     }
 *
 *     void add32(uint32_t v, std::string_view label = "unknown") {
 *         this->addBits(32, v, label);
 *     }
 *
 *     virtual void appendComment(const char* comment) {}
 *
 *     // Introduces a word-boundary in the key. Must be called before using the key with any cache,
 *     // but can also be called to create a break between generic data and backend-specific data.
 *     void flush() {
 *         if (fBitsUsed) {
 *             fData->push_back(fCurValue);
 *             fCurValue = 0;
 *             fBitsUsed = 0;
 *         }
 *     }
 *
 * private:
 *     skia_private::TArray<uint32_t, true>* fData;
 *     uint32_t fCurValue = 0;
 *     uint32_t fBitsUsed = 0;  // ... in current value
 * }
 * ```
 */
public open class KeyBuilder public constructor(
  `data`: TArraytrue<UInt>,
) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<uint32_t, true>* fData
   * ```
   */
  private var fData: Int? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fCurValue
   * ```
   */
  private var fCurValue: Int = TODO("Initialize fCurValue")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fBitsUsed
   * ```
   */
  private var fBitsUsed: Int = TODO("Initialize fBitsUsed")

  /**
   * C++ original:
   * ```cpp
   * virtual void addBits(uint32_t numBits, uint32_t val, std::string_view label) {
   *         SkASSERT(numBits > 0 && numBits <= 32);
   *         SkASSERT(numBits == 32 || (val < (1u << numBits)));
   *
   *         fCurValue |= (val << fBitsUsed);
   *         fBitsUsed += numBits;
   *
   *         if (fBitsUsed >= 32) {
   *             // Overflow, start a new working value
   *             fData->push_back(fCurValue);
   *             uint32_t excess = fBitsUsed - 32;
   *             fCurValue = excess ? (val >> (numBits - excess)) : 0;
   *             fBitsUsed = excess;
   *         }
   *
   *         SkASSERT(fCurValue < (1u << fBitsUsed));
   *     }
   * ```
   */
  public open fun addBits(
    numBits: UInt,
    `val`: UInt,
    label: String,
  ) {
    TODO("Implement addBits")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBytes(uint32_t numBytes, const void* data, std::string_view label) {
   *         const uint8_t* bytes = reinterpret_cast<const uint8_t*>(data);
   *         for (; numBytes --> 0; bytes++) {
   *             this->addBits(8, *bytes, label);
   *         }
   *     }
   * ```
   */
  public fun addBytes(
    numBytes: UInt,
    `data`: Unit?,
    label: String,
  ) {
    TODO("Implement addBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBool(bool b, std::string_view label) {
   *         this->addBits(1, b, label);
   *     }
   * ```
   */
  public fun addBool(b: Boolean, label: String) {
    TODO("Implement addBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void add32(uint32_t v, std::string_view label = "unknown") {
   *         this->addBits(32, v, label);
   *     }
   * ```
   */
  public fun add32(v: UInt, label: String = TODO()) {
    TODO("Implement add32")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void appendComment(const char* comment) {}
   * ```
   */
  public open fun appendComment(comment: String?) {
    TODO("Implement appendComment")
  }

  /**
   * C++ original:
   * ```cpp
   * void flush() {
   *         if (fBitsUsed) {
   *             fData->push_back(fCurValue);
   *             fCurValue = 0;
   *             fBitsUsed = 0;
   *         }
   *     }
   * ```
   */
  public fun flush() {
    TODO("Implement flush")
  }
}
