package org.skia.core

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.U16CPU
import org.skia.foundation.U8CPU
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkWStream {
 * public:
 *     virtual ~SkWStream();
 *     SkWStream() {}
 *
 *     /** Called to write bytes to a SkWStream. Returns true on success
 *         @param buffer the address of at least size bytes to be written to the stream
 *         @param size The number of bytes in buffer to write to the stream
 *         @return true on success
 *     */
 *     virtual bool write(const void* buffer, size_t size) = 0;
 *     virtual void flush();
 *
 *     virtual size_t bytesWritten() const = 0;
 *
 *     // helpers
 *
 *     bool write8(U8CPU value)   {
 *         uint8_t v = SkToU8(value);
 *         return this->write(&v, 1);
 *     }
 *     bool write16(U16CPU value) {
 *         uint16_t v = SkToU16(value);
 *         return this->write(&v, 2);
 *     }
 *     bool write32(uint32_t value) {
 *         return this->write(&value, 4);
 *     }
 *     bool write64(uint64_t value) {
 *         return this->write(&value, 8);
 *     }
 *
 *     bool writeText(const char text[]) {
 *         SkASSERT(text);
 *         return this->write(text, std::strlen(text));
 *     }
 *
 *     bool newline() { return this->write("\n", std::strlen("\n")); }
 *
 *     bool writeDecAsText(int32_t);
 *     bool writeBigDecAsText(int64_t, int minDigits = 0);
 *     bool writeHexAsText(uint32_t, int minDigits = 0);
 *     bool writeScalarAsText(SkScalar);
 *
 *     bool writeBool(bool v) { return this->write8(v); }
 *     bool writeScalar(SkScalar);
 *     bool writePackedUInt(size_t);
 *
 *     bool writeStream(SkStream* input, size_t length);
 *
 *     /**
 *      * This returns the number of bytes in the stream required to store
 *      * 'value'.
 *      */
 *     static int SizeOfPackedUInt(size_t value);
 *
 * private:
 *     SkWStream(const SkWStream&) = delete;
 *     SkWStream& operator=(const SkWStream&) = delete;
 * }
 * ```
 */
public abstract class SkWStream public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkWStream() {}
   * ```
   */
  public constructor(param0: SkWStream) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool write(const void* buffer, size_t size) = 0
   * ```
   */
  public abstract fun write(buffer: Unit?, size: ULong): Boolean

  /**
   * C++ original:
   * ```cpp
   * void SkWStream::flush()
   * {
   * }
   * ```
   */
  public open fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t bytesWritten() const = 0
   * ```
   */
  public abstract fun bytesWritten(): ULong

  /**
   * C++ original:
   * ```cpp
   * bool write8(U8CPU value)   {
   *         uint8_t v = SkToU8(value);
   *         return this->write(&v, 1);
   *     }
   * ```
   */
  public fun write8(`value`: U8CPU): Boolean {
    TODO("Implement write8")
  }

  /**
   * C++ original:
   * ```cpp
   * bool write16(U16CPU value) {
   *         uint16_t v = SkToU16(value);
   *         return this->write(&v, 2);
   *     }
   * ```
   */
  public fun write16(`value`: U16CPU): Boolean {
    TODO("Implement write16")
  }

  /**
   * C++ original:
   * ```cpp
   * bool write32(uint32_t value) {
   *         return this->write(&value, 4);
   *     }
   * ```
   */
  public fun write32(`value`: UInt): Boolean {
    TODO("Implement write32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool write64(uint64_t value) {
   *         return this->write(&value, 8);
   *     }
   * ```
   */
  public fun write64(`value`: ULong): Boolean {
    TODO("Implement write64")
  }

  /**
   * C++ original:
   * ```cpp
   * bool writeText(const char text[]) {
   *         SkASSERT(text);
   *         return this->write(text, std::strlen(text));
   *     }
   * ```
   */
  public fun writeText(text: CharArray): Boolean {
    TODO("Implement writeText")
  }

  /**
   * C++ original:
   * ```cpp
   * bool newline() { return this->write("\n", std::strlen("\n")); }
   * ```
   */
  public fun newline(): Boolean {
    TODO("Implement newline")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeDecAsText(int32_t dec)
   * {
   *     char buffer[kSkStrAppendS32_MaxSize];
   *     char* stop = SkStrAppendS32(buffer, dec);
   *     return this->write(buffer, stop - buffer);
   * }
   * ```
   */
  public fun writeDecAsText(dec: Int): Boolean {
    TODO("Implement writeDecAsText")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeBigDecAsText(int64_t dec, int minDigits)
   * {
   *     char buffer[kSkStrAppendU64_MaxSize];
   *     char* stop = SkStrAppendU64(buffer, dec, minDigits);
   *     return this->write(buffer, stop - buffer);
   * }
   * ```
   */
  public abstract fun writeBigDecAsText(dec: Long, minDigits: Int = 0): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeHexAsText(uint32_t hex, int digits)
   * {
   *     SkString    tmp;
   *     tmp.appendHex(hex, digits);
   *     return this->write(tmp.c_str(), tmp.size());
   * }
   * ```
   */
  public abstract fun writeHexAsText(hex: UInt, minDigits: Int = 0): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeScalarAsText(SkScalar value)
   * {
   *     char buffer[kSkStrAppendScalar_MaxSize];
   *     char* stop = SkStrAppendScalar(buffer, value);
   *     return this->write(buffer, stop - buffer);
   * }
   * ```
   */
  public fun writeScalarAsText(`value`: SkScalar): Boolean {
    TODO("Implement writeScalarAsText")
  }

  /**
   * C++ original:
   * ```cpp
   * bool writeBool(bool v) { return this->write8(v); }
   * ```
   */
  public fun writeBool(v: Boolean): Boolean {
    TODO("Implement writeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeScalar(SkScalar value) {
   *     return this->write(&value, sizeof(value));
   * }
   * ```
   */
  public fun writeScalar(`value`: SkScalar): Boolean {
    TODO("Implement writeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writePackedUInt(size_t value) {
   *     uint8_t data[5];
   *     size_t len = 1;
   *     if (value <= SK_MAX_BYTE_FOR_U8) {
   *         data[0] = value;
   *         len = 1;
   *     } else if (value <= 0xFFFF) {
   *         uint16_t value16 = value;
   *         data[0] = SK_BYTE_SENTINEL_FOR_U16;
   *         memcpy(&data[1], &value16, 2);
   *         len = 3;
   *     } else {
   *         uint32_t value32 = SkToU32(value);
   *         data[0] = SK_BYTE_SENTINEL_FOR_U32;
   *         memcpy(&data[1], &value32, 4);
   *         len = 5;
   *     }
   *     return this->write(data, len);
   * }
   * ```
   */
  public fun writePackedUInt(`value`: ULong): Boolean {
    TODO("Implement writePackedUInt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWStream::writeStream(SkStream* stream, size_t length) {
   *     char scratch[1024];
   *     const size_t MAX = sizeof(scratch);
   *
   *     while (length != 0) {
   *         size_t n = length;
   *         if (n > MAX) {
   *             n = MAX;
   *         }
   *         stream->read(scratch, n);
   *         if (!this->write(scratch, n)) {
   *             return false;
   *         }
   *         length -= n;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun writeStream(input: SkStream?, length: ULong): Boolean {
    TODO("Implement writeStream")
  }

  /**
   * C++ original:
   * ```cpp
   * SkWStream& operator=(const SkWStream&) = delete
   * ```
   */
  private fun assign(param0: SkWStream) {
    TODO("Implement assign")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * int SkWStream::SizeOfPackedUInt(size_t value) {
     *     if (value <= SK_MAX_BYTE_FOR_U8) {
     *         return 1;
     *     } else if (value <= 0xFFFF) {
     *         return 3;
     *     }
     *     return 5;
     * }
     * ```
     */
    public fun sizeOfPackedUInt(`value`: ULong): Int {
      TODO("Implement sizeOfPackedUInt")
    }
  }
}

public typealias SkFILEWStreamINHERITED = SkWStream

public typealias SkDynamicMemoryWStreamINHERITED = SkWStream
