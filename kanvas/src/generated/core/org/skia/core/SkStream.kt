package org.skia.core

import kotlin.Boolean
import kotlin.Byte
import kotlin.CharArray
import kotlin.Int
import kotlin.Long
import kotlin.Short
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkStream {
 * public:
 *     virtual ~SkStream() {}
 *     SkStream() {}
 *
 *     /**
 *      *  Attempts to open the specified file as a stream, returns nullptr on failure.
 *      */
 *     static std::unique_ptr<SkStreamAsset> MakeFromFile(const char path[]);
 *
 *     /** Reads or skips size number of bytes.
 *      *  If buffer == NULL, skip size bytes, return how many were skipped.
 *      *  If buffer != NULL, copy size bytes into buffer, return how many were copied.
 *      *  @param buffer when NULL skip size bytes, otherwise copy size bytes into buffer
 *      *  @param size the number of bytes to skip or copy
 *      *  @return the number of bytes actually read.
 *      */
 *     virtual size_t read(void* buffer, size_t size) = 0;
 *
 *     /** Skip size number of bytes.
 *      *  @return the actual number bytes that could be skipped.
 *      */
 *     size_t skip(size_t size) {
 *         return this->read(nullptr, size);
 *     }
 *
 *     /**
 *      *  Attempt to peek at size bytes.
 *      *  If this stream supports peeking, copy min(size, peekable bytes) into
 *      *  buffer, and return the number of bytes copied.
 *      *  If the stream does not support peeking, or cannot peek any bytes,
 *      *  return 0 and leave buffer unchanged.
 *      *  The stream is guaranteed to be in the same visible state after this
 *      *  call, regardless of success or failure.
 *      *  @param buffer Must not be NULL, and must be at least size bytes. Destination
 *      *      to copy bytes.
 *      *  @param size Number of bytes to copy.
 *      *  @return The number of bytes peeked/copied.
 *      */
 *     virtual size_t peek(void* /*buffer*/, size_t /*size*/) const { return 0; }
 *
 *     /** Returns true when all the bytes in the stream have been read.
 *      *  As SkStream represents synchronous I/O, isAtEnd returns false when the
 *      *  final stream length isn't known yet, even when all the bytes available
 *      *  so far have been read.
 *      *  This may return true early (when there are no more bytes to be read)
 *      *  or late (after the first unsuccessful read).
 *      */
 *     virtual bool isAtEnd() const = 0;
 *
 *     [[nodiscard]] bool readS8(int8_t*);
 *     [[nodiscard]] bool readS16(int16_t*);
 *     [[nodiscard]] bool readS32(int32_t*);
 *     [[nodiscard]] bool readS64(int64_t*);
 *
 *     [[nodiscard]] bool readU8(uint8_t* i)   { return this->readS8((int8_t*)i); }
 *     [[nodiscard]] bool readU16(uint16_t* i) { return this->readS16((int16_t*)i); }
 *     [[nodiscard]] bool readU32(uint32_t* i) { return this->readS32((int32_t*)i); }
 *     [[nodiscard]] bool readU64(uint64_t* i) { return this->readS64((int64_t*)i); }
 *
 *     [[nodiscard]] bool readBool(bool* b) {
 *         uint8_t i;
 *         if (!this->readU8(&i)) { return false; }
 *         *b = (i != 0);
 *         return true;
 *     }
 *     [[nodiscard]] bool readScalar(SkScalar*);
 *     [[nodiscard]] bool readPackedUInt(size_t*);
 *
 * //SkStreamRewindable
 *     /** Rewinds to the beginning of the stream. Returns true if the stream is known
 *      *  to be at the beginning after this call returns.
 *      */
 *     virtual bool rewind() { return false; }
 *
 *     /** Duplicates this stream. If this cannot be done, returns NULL.
 *      *  The returned stream will be positioned at the beginning of its data.
 *      */
 *     std::unique_ptr<SkStream> duplicate() const {
 *         return std::unique_ptr<SkStream>(this->onDuplicate());
 *     }
 *     /** Duplicates this stream. If this cannot be done, returns NULL.
 *      *  The returned stream will be positioned the same as this stream.
 *      */
 *     std::unique_ptr<SkStream> fork() const {
 *         return std::unique_ptr<SkStream>(this->onFork());
 *     }
 *
 * //SkStreamSeekable
 *     /** Returns true if this stream can report its current position. */
 *     virtual bool hasPosition() const { return false; }
 *     /** Returns the current position in the stream. If this cannot be done, returns 0. */
 *     virtual size_t getPosition() const { return 0; }
 *
 *     /** Seeks to an absolute position in the stream. If this cannot be done, returns false.
 *      *  If an attempt is made to seek past the end of the stream, the position will be set
 *      *  to the end of the stream.
 *      */
 *     virtual bool seek(size_t /*position*/) { return false; }
 *
 *     /** Seeks to an relative offset in the stream. If this cannot be done, returns false.
 *      *  If an attempt is made to move to a position outside the stream, the position will be set
 *      *  to the closest point within the stream (beginning or end).
 *      */
 *     virtual bool move(long /*offset*/) { return false; }
 *
 * //SkStreamAsset
 *     /** Returns true if this stream can report its total length. */
 *     virtual bool hasLength() const { return false; }
 *     /** Returns the total length of the stream. If this cannot be done, returns 0. */
 *     virtual size_t getLength() const { return 0; }
 *
 * //SkStreamMemory
 *     /** Returns the starting address for the data. If this cannot be done, returns NULL. */
 *     virtual const void* getMemoryBase() { return nullptr; }
 *     virtual sk_sp<const SkData> getData() const { return nullptr; }
 *
 * private:
 *     virtual SkStream* onDuplicate() const { return nullptr; }
 *     virtual SkStream* onFork() const { return nullptr; }
 *
 *     SkStream(SkStream&&) = delete;
 *     SkStream(const SkStream&) = delete;
 *     SkStream& operator=(SkStream&&) = delete;
 *     SkStream& operator=(const SkStream&) = delete;
 * }
 * ```
 */
public abstract class SkStream public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkStream() {}
   * ```
   */
  public constructor(param0: SkStream) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t read(void* buffer, size_t size) = 0
   * ```
   */
  public abstract fun read(buffer: Unit?, size: ULong): ULong

  /**
   * C++ original:
   * ```cpp
   * size_t skip(size_t size) {
   *         return this->read(nullptr, size);
   *     }
   * ```
   */
  public fun skip(size: ULong): ULong {
    TODO("Implement skip")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t peek(void* /*buffer*/, size_t /*size*/) const { return 0; }
   * ```
   */
  public open fun peek(param0: Int, param1: Int): ULong {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isAtEnd() const = 0
   * ```
   */
  public abstract fun isAtEnd(): Boolean

  /**
   * C++ original:
   * ```cpp
   * bool SkStream::readS8(int8_t* i) {
   *     return this->read(i, sizeof(*i)) == sizeof(*i);
   * }
   * ```
   */
  public fun readS8(i: Byte?): Boolean {
    TODO("Implement readS8")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStream::readS16(int16_t* i) {
   *     return this->read(i, sizeof(*i)) == sizeof(*i);
   * }
   * ```
   */
  public fun readS16(i: Short?): Boolean {
    TODO("Implement readS16")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStream::readS32(int32_t* i) {
   *     return this->read(i, sizeof(*i)) == sizeof(*i);
   * }
   * ```
   */
  public fun readS32(i: Int?): Boolean {
    TODO("Implement readS32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStream::readS64(int64_t* i) {
   *     return this->read(i, sizeof(*i)) == sizeof(*i);
   * }
   * ```
   */
  public fun readS64(i: Long?): Boolean {
    TODO("Implement readS64")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU8(uint8_t* i)   { return this->readS8((int8_t*)i); }
   * ```
   */
  public fun readU8(i: UByte?): Boolean {
    TODO("Implement readU8")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU16(uint16_t* i) { return this->readS16((int16_t*)i); }
   * ```
   */
  public fun readU16(i: UShort?): Boolean {
    TODO("Implement readU16")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU32(uint32_t* i) { return this->readS32((int32_t*)i); }
   * ```
   */
  public fun readU32(i: UInt?): Boolean {
    TODO("Implement readU32")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readU64(uint64_t* i) { return this->readS64((int64_t*)i); }
   * ```
   */
  public fun readU64(i: ULong?): Boolean {
    TODO("Implement readU64")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readBool(bool* b) {
   *         uint8_t i;
   *         if (!this->readU8(&i)) { return false; }
   *         *b = (i != 0);
   *         return true;
   *     }
   * ```
   */
  public fun readBool(b: Boolean?): Boolean {
    TODO("Implement readBool")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkStream::readScalar(SkScalar* i) {
   *     return this->read(i, sizeof(*i)) == sizeof(*i);
   * }
   * ```
   */
  public fun readScalar(i: SkScalar?): Boolean {
    TODO("Implement readScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPackedUInt(size_t*)
   * ```
   */
  public fun readPackedUInt(param0: ULong?): Boolean {
    TODO("Implement readPackedUInt")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool rewind() { return false; }
   * ```
   */
  public open fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStream> duplicate() const {
   *         return std::unique_ptr<SkStream>(this->onDuplicate());
   *     }
   * ```
   */
  public fun duplicate(): SkStream? {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStream> fork() const {
   *         return std::unique_ptr<SkStream>(this->onFork());
   *     }
   * ```
   */
  public fun fork(): SkStream? {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool hasPosition() const { return false; }
   * ```
   */
  public open fun hasPosition(): Boolean {
    TODO("Implement hasPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getPosition() const { return 0; }
   * ```
   */
  public open fun getPosition(): ULong {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool seek(size_t /*position*/) { return false; }
   * ```
   */
  public open fun seek(param0: Int): Boolean {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool move(long /*offset*/) { return false; }
   * ```
   */
  public open fun move(param0: Int): Boolean {
    TODO("Implement move")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool hasLength() const { return false; }
   * ```
   */
  public open fun hasLength(): Boolean {
    TODO("Implement hasLength")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getLength() const { return 0; }
   * ```
   */
  public open fun getLength(): ULong {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const void* getMemoryBase() { return nullptr; }
   * ```
   */
  public open fun getMemoryBase() {
    TODO("Implement getMemoryBase")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<const SkData> getData() const { return nullptr; }
   * ```
   */
  public open fun getData(): Int {
    TODO("Implement getData")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkStream* onDuplicate() const { return nullptr; }
   * ```
   */
  public open fun onDuplicate(): SkStream {
    TODO("Implement onDuplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkStream* onFork() const { return nullptr; }
   * ```
   */
  public open fun onFork(): SkStream {
    TODO("Implement onFork")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStream& operator=(SkStream&&) = delete
   * ```
   */
  private fun assign(param0: SkStream) {
    TODO("Implement assign")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkStreamAsset> SkStream::MakeFromFile(const char path[]) {
     *     auto data(mmap_filename(path));
     *     if (data) {
     *         return std::make_unique<SkMemoryStream>(std::move(data));
     *     }
     *
     *     // If we get here, then our attempt at using mmap failed, so try normal file access.
     *     auto stream = std::make_unique<SkFILEStream>(path);
     *     if (!stream->isValid()) {
     *         return nullptr;
     *     }
     *     return stream;
     * }
     * ```
     */
    public fun makeFromFile(path: CharArray): SkStreamAsset? {
      TODO("Implement makeFromFile")
    }
  }
}
