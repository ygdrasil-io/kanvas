package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SkRBuffer : SkNoncopyable {
 * public:
 *     SkRBuffer() : fData(nullptr), fPos(nullptr), fStop(nullptr) {}
 *
 *     /** Initialize RBuffer with a data point and length.
 *     */
 *     SkRBuffer(const void* data, size_t size) {
 *         SkASSERT(data != nullptr || size == 0);
 *         fData = (const char*)data;
 *         fPos = (const char*)data;
 *         fStop = (const char*)data + size;
 *     }
 *
 *     /** Return the number of bytes that have been read from the beginning
 *         of the data pointer.
 *     */
 *     size_t pos() const { return fPos - fData; }
 *     /** Return the total size of the data pointer. Only defined if the length was
 *         specified in the constructor or in a call to reset().
 *     */
 *     size_t size() const { return fStop - fData; }
 *     /** Return true if the buffer has read to the end of the data pointer.
 *         Only defined if the length was specified in the constructor or in a call
 *         to reset(). Always returns true if the length was not specified.
 *     */
 *     bool eof() const { return fPos >= fStop; }
 *
 *     size_t available() const { return fStop - fPos; }
 *
 *     bool isValid() const { return fValid; }
 *
 *     /** Read the specified number of bytes from the data pointer. If buffer is not
 *         null, copy those bytes into buffer.
 *     */
 *     bool read(void* buffer, size_t size);
 *     bool skipToAlign4();
 *
 *     bool readU8(uint8_t* x)   { return this->read(x, 1); }
 *     bool readS32(int32_t* x)  { return this->read(x, 4); }
 *     bool readU32(uint32_t* x) { return this->read(x, 4); }
 *
 *     // returns nullptr on failure
 *     const void* skip(size_t bytes);
 *     template <typename T> const T* skipCount(size_t count) {
 *         return static_cast<const T*>(this->skip(SkSafeMath::Mul(count, sizeof(T))));
 *     }
 *
 * private:
 *     const char* fData;
 *     const char* fPos;
 *     const char* fStop;
 *     bool        fValid = true;
 * }
 * ```
 */
public open class SkRBuffer public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const char* fData
   * ```
   */
  private val fData: String? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * const char* fPos
   * ```
   */
  private val fPos: String? = TODO("Initialize fPos")

  /**
   * C++ original:
   * ```cpp
   * const char* fStop
   * ```
   */
  private val fStop: String? = TODO("Initialize fStop")

  /**
   * C++ original:
   * ```cpp
   * bool        fValid = true
   * ```
   */
  private var fValid: Boolean = TODO("Initialize fValid")

  /**
   * C++ original:
   * ```cpp
   * SkRBuffer() : fData(nullptr), fPos(nullptr), fStop(nullptr) {}
   * ```
   */
  public constructor(`data`: Unit?, size: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t pos() const { return fPos - fData; }
   * ```
   */
  public fun pos(): Int {
    TODO("Implement pos")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fStop - fData; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool eof() const { return fPos >= fStop; }
   * ```
   */
  public fun eof(): Boolean {
    TODO("Implement eof")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t available() const { return fStop - fPos; }
   * ```
   */
  public fun available(): Int {
    TODO("Implement available")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fValid; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRBuffer::read(void* buffer, size_t size) {
   *     if (const void* src = this->skip(size)) {
   *         sk_careful_memcpy(buffer, src, size);
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun read(buffer: Unit?, size: ULong): Boolean {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRBuffer::skipToAlign4() {
   *     intptr_t pos = reinterpret_cast<intptr_t>(fPos);
   *     size_t n = SkAlign4(pos) - pos;
   *     if (fValid && n <= this->available()) {
   *         fPos += n;
   *         return true;
   *     } else {
   *         fValid = false;
   *         return false;
   *     }
   * }
   * ```
   */
  public fun skipToAlign4(): Boolean {
    TODO("Implement skipToAlign4")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU8(uint8_t* x)   { return this->read(x, 1); }
   * ```
   */
  public fun readU8(x: UByte?): Boolean {
    TODO("Implement readU8")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readS32(int32_t* x)  { return this->read(x, 4); }
   * ```
   */
  public fun readS32(x: Int?): Boolean {
    TODO("Implement readS32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU32(uint32_t* x) { return this->read(x, 4); }
   * ```
   */
  public fun readU32(x: UInt?): Boolean {
    TODO("Implement readU32")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkRBuffer::skip(size_t size) {
   *     if (fValid && size <= this->available()) {
   *         const void* pos = fPos;
   *         fPos += size;
   *         return pos;
   *     }
   *     fValid = false;
   *     return nullptr;
   * }
   * ```
   */
  public fun skip(bytes: ULong) {
    TODO("Implement skip")
  }

  /**
   * C++ original:
   * ```cpp
   * const T* skipCount(size_t count) {
   *         return static_cast<const T*>(this->skip(SkSafeMath::Mul(count, sizeof(T))));
   *     }
   * ```
   */
  public fun skipCount(count: ULong): T {
    TODO("Implement skipCount")
  }
}
