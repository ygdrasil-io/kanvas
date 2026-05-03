package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SkMD5 : public SkWStream {
 * public:
 *     SkMD5();
 *
 *     /** Processes input, adding it to the digest.
 *         Calling this after finish is undefined.  */
 *     bool write(const void* buffer, size_t size) final;
 *
 *     size_t bytesWritten() const final { return SkToSizeT(this->byteCount); }
 *
 *     struct Digest {
 *         SkString toHexString() const;
 *         SkString toLowercaseHexString() const;
 *         bool operator==(Digest const& other) const {
 *             return 0 == memcmp(data, other.data, sizeof(data));
 *         }
 *         bool operator!=(Digest const& other) const {
 *             return !(*this == other);
 *         }
 *
 *         uint8_t data[16];
 *     };
 *
 *     /** Computes and returns the digest. */
 *     Digest finish();
 *
 * private:
 *     uint64_t byteCount;  // number of bytes, modulo 2^64
 *     uint32_t state[4];   // state (ABCD)
 *     uint8_t buffer[64];  // input buffer
 * }
 * ```
 */
public open class SkMD5 public constructor() : SkWStream() {
  /**
   * C++ original:
   * ```cpp
   * uint64_t byteCount
   * ```
   */
  private var byteCount: Int = TODO("Initialize byteCount")

  /**
   * C++ original:
   * ```cpp
   * uint32_t state[4]
   * ```
   */
  private var state: IntArray = TODO("Initialize state")

  /**
   * C++ original:
   * ```cpp
   * uint8_t buffer[64]
   * ```
   */
  private var buffer: IntArray = TODO("Initialize buffer")

  /**
   * C++ original:
   * ```cpp
   * bool SkMD5::write(const void* buf, size_t inputLength) {
   *     const uint8_t* input = reinterpret_cast<const uint8_t*>(buf);
   *     unsigned int bufferIndex = (unsigned int)(this->byteCount & 0x3F);
   *     unsigned int bufferAvailable = 64 - bufferIndex;
   *
   *     unsigned int inputIndex;
   *     if (inputLength >= bufferAvailable) {
   *         if (bufferIndex) {
   *             sk_careful_memcpy(&this->buffer[bufferIndex], input, bufferAvailable);
   *             transform(this->state, this->buffer);
   *             inputIndex = bufferAvailable;
   *         } else {
   *             inputIndex = 0;
   *         }
   *
   *         for (; inputIndex + 63 < inputLength; inputIndex += 64) {
   *             transform(this->state, &input[inputIndex]);
   *         }
   *
   *         bufferIndex = 0;
   *     } else {
   *         inputIndex = 0;
   *     }
   *
   *     sk_careful_memcpy(&this->buffer[bufferIndex], &input[inputIndex], inputLength - inputIndex);
   *
   *     this->byteCount += inputLength;
   *     return true;
   * }
   * ```
   */
  public fun write(buffer: Unit?, size: ULong): Boolean {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesWritten() const final { return SkToSizeT(this->byteCount); }
   * ```
   */
  public override fun bytesWritten(): Int {
    TODO("Implement bytesWritten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMD5::Digest SkMD5::finish() {
   *     SkMD5::Digest digest;
   *     // Get the number of bits before padding.
   *     uint8_t bits[8];
   *     encode(bits, this->byteCount << 3);
   *
   *     // Pad out to 56 mod 64.
   *     unsigned int bufferIndex = (unsigned int)(this->byteCount & 0x3F);
   *     unsigned int paddingLength = (bufferIndex < 56) ? (56 - bufferIndex) : (120 - bufferIndex);
   *     static const uint8_t PADDING[64] = {
   *         0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
   *            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
   *            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
   *            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
   *     };
   *     (void)this->write(PADDING, paddingLength);
   *
   *     // Append length (length before padding, will cause final update).
   *     (void)this->write(bits, 8);
   *
   *     // Write out digest.
   *     encode(digest.data, this->state);
   *
   * #if defined(SK_MD5_CLEAR_DATA)
   *     // Clear state.
   *     memset(this, 0, sizeof(*this));
   * #endif
   *     return digest;
   * }
   * ```
   */
  public fun finish(): Digest {
    TODO("Implement finish")
  }

  public data class Digest public constructor(
    public var `data`: IntArray,
  ) {
    public fun toHexString(): String {
      TODO("Implement toHexString")
    }

    public fun toLowercaseHexString(): String {
      TODO("Implement toLowercaseHexString")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}
