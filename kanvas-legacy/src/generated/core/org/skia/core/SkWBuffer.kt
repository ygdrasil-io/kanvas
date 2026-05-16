package org.skia.core

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Short
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkNoncopyable
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkWBuffer : SkNoncopyable {
 * public:
 *     SkWBuffer() : fData(nullptr), fPos(nullptr), fStop(nullptr) {}
 *     explicit SkWBuffer(void* data) { reset(data); }
 *     SkWBuffer(void* data, size_t size) { reset(data, size); }
 *
 *     void reset(void* data) {
 *         fData = (char*)data;
 *         fPos = (char*)data;
 *         fStop = nullptr;  // no bounds checking
 *     }
 *
 *     void reset(void* data, size_t size) {
 *         SkASSERT(data != nullptr || size == 0);
 *         fData = (char*)data;
 *         fPos = (char*)data;
 *         fStop = (char*)data + size;
 *     }
 *
 *     size_t  pos() const { return fPos - fData; }
 *     void*   skip(size_t size); // return start of skipped data
 *
 *     void write(const void* buffer, size_t size) {
 *         if (size) {
 *             this->writeNoSizeCheck(buffer, size);
 *         }
 *     }
 *
 *     size_t  padToAlign4();
 *
 *     void    writePtr(const void* x) { this->writeNoSizeCheck(&x, sizeof(x)); }
 *     void    writeScalar(SkScalar x) { this->writeNoSizeCheck(&x, 4); }
 *     void    write32(int32_t x) { this->writeNoSizeCheck(&x, 4); }
 *     void    write16(int16_t x) { this->writeNoSizeCheck(&x, 2); }
 *     void    write8(int8_t x) { this->writeNoSizeCheck(&x, 1); }
 *     void    writeBool(bool x) { this->write8(x); }
 *
 * private:
 *     void    writeNoSizeCheck(const void* buffer, size_t size);
 *
 *     char* fData;
 *     char* fPos;
 *     char* fStop;
 * }
 * ```
 */
public open class SkWBuffer public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * char* fData
   * ```
   */
  private var fData: String? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * char* fPos
   * ```
   */
  private var fPos: String? = TODO("Initialize fPos")

  /**
   * C++ original:
   * ```cpp
   * char* fStop
   * ```
   */
  private var fStop: String? = TODO("Initialize fStop")

  /**
   * C++ original:
   * ```cpp
   * SkWBuffer() : fData(nullptr), fPos(nullptr), fStop(nullptr) {}
   * ```
   */
  public constructor(`data`: Unit?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkWBuffer(void* data) { reset(data); }
   * ```
   */
  public constructor(`data`: Unit?, size: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset(void* data) {
   *         fData = (char*)data;
   *         fPos = (char*)data;
   *         fStop = nullptr;  // no bounds checking
   *     }
   * ```
   */
  public fun reset(`data`: Unit?) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset(void* data, size_t size) {
   *         SkASSERT(data != nullptr || size == 0);
   *         fData = (char*)data;
   *         fPos = (char*)data;
   *         fStop = (char*)data + size;
   *     }
   * ```
   */
  public fun reset(`data`: Unit?, size: ULong) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t  pos() const { return fPos - fData; }
   * ```
   */
  public fun pos(): Int {
    TODO("Implement pos")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkWBuffer::skip(size_t size) {
   *     void* result = fPos;
   *     writeNoSizeCheck(nullptr, size);
   *     return fData == nullptr ? nullptr : result;
   * }
   * ```
   */
  public fun skip(size: ULong) {
    TODO("Implement skip")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const void* buffer, size_t size) {
   *         if (size) {
   *             this->writeNoSizeCheck(buffer, size);
   *         }
   *     }
   * ```
   */
  public fun write(buffer: Unit?, size: ULong) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkWBuffer::padToAlign4() {
   *     size_t pos = this->pos();
   *     size_t n = SkAlign4(pos) - pos;
   *
   *     if (n && fData)
   *     {
   *         char* p = fPos;
   *         char* stop = p + n;
   *         do {
   *             *p++ = 0;
   *         } while (p < stop);
   *     }
   *     fPos += n;
   *     return n;
   * }
   * ```
   */
  public fun padToAlign4(): Int {
    TODO("Implement padToAlign4")
  }

  /**
   * C++ original:
   * ```cpp
   * void    writePtr(const void* x) { this->writeNoSizeCheck(&x, sizeof(x)); }
   * ```
   */
  public fun writePtr(x: Unit?) {
    TODO("Implement writePtr")
  }

  /**
   * C++ original:
   * ```cpp
   * void    writeScalar(SkScalar x) { this->writeNoSizeCheck(&x, 4); }
   * ```
   */
  public fun writeScalar(x: SkScalar) {
    TODO("Implement writeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * void    write32(int32_t x) { this->writeNoSizeCheck(&x, 4); }
   * ```
   */
  public fun write32(x: Int) {
    TODO("Implement write32")
  }

  /**
   * C++ original:
   * ```cpp
   * void    write16(int16_t x) { this->writeNoSizeCheck(&x, 2); }
   * ```
   */
  public fun write16(x: Short) {
    TODO("Implement write16")
  }

  /**
   * C++ original:
   * ```cpp
   * void    write8(int8_t x) { this->writeNoSizeCheck(&x, 1); }
   * ```
   */
  public fun write8(x: Byte) {
    TODO("Implement write8")
  }

  /**
   * C++ original:
   * ```cpp
   * void    writeBool(bool x) { this->write8(x); }
   * ```
   */
  public fun writeBool(x: Boolean) {
    TODO("Implement writeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWBuffer::writeNoSizeCheck(const void* buffer, size_t size) {
   *     SkASSERT(fData == nullptr || fStop == nullptr || fPos + size <= fStop);
   *     if (fData && buffer) {
   *         sk_careful_memcpy(fPos, buffer, size);
   *     }
   *     fPos += size;
   * }
   * ```
   */
  private fun writeNoSizeCheck(buffer: Unit?, size: ULong) {
    TODO("Implement writeNoSizeCheck")
  }
}
