package org.skia.tests

import kotlin.Boolean
import kotlin.Long
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkStream

/**
 * C++ original:
 * ```cpp
 * class TestStream : public SkStream {
 * public:
 *     enum class Type {
 *         kUnseekable,    // SkJpegUnseekableSourceMgr
 *         kSeekable,      // SkJpegBufferedSourceMgr
 *         kMemoryMapped,  // SkJpegMemorySourceMgr
 *     };
 *     TestStream(Type type, SkStream* stream)
 *             : fStream(stream)
 *             , fSeekable(type != Type::kUnseekable)
 *             , fMemoryMapped(type == Type::kMemoryMapped) {}
 *     ~TestStream() override {}
 *
 *     size_t read(void* buffer, size_t size) override { return fStream->read(buffer, size); }
 *     size_t peek(void* buffer, size_t size) const override { return fStream->peek(buffer, size); }
 *     bool isAtEnd() const override { return fStream->isAtEnd(); }
 *     bool rewind() override {
 *         if (!fSeekable) {
 *             return false;
 *         }
 *         return fStream->rewind();
 *     }
 *     bool hasPosition() const override {
 *         if (!fSeekable) {
 *             return false;
 *         }
 *         return fStream->hasPosition();
 *     }
 *     size_t getPosition() const override {
 *         if (!fSeekable) {
 *             return 0;
 *         }
 *         return fStream->hasPosition();
 *     }
 *     bool seek(size_t position) override {
 *         if (!fSeekable) {
 *             return 0;
 *         }
 *         return fStream->seek(position);
 *     }
 *     bool move(long offset) override {
 *         if (!fSeekable) {
 *             return 0;
 *         }
 *         return fStream->move(offset);
 *     }
 *     bool hasLength() const override {
 *         if (!fMemoryMapped) {
 *             return false;
 *         }
 *         return fStream->hasLength();
 *     }
 *     size_t getLength() const override {
 *         if (!fMemoryMapped) {
 *             return 0;
 *         }
 *         return fStream->getLength();
 *     }
 *     const void* getMemoryBase() override {
 *         if (!fMemoryMapped) {
 *             return nullptr;
 *         }
 *         return fStream->getMemoryBase();
 *     }
 *
 * private:
 *     SkStream* const fStream;
 *     bool fSeekable = false;
 *     bool fMemoryMapped = false;
 * }
 * ```
 */
public open class TestStream public constructor(
  type: Type,
  stream: SkStream?,
) : SkStream() {
  /**
   * C++ original:
   * ```cpp
   * SkStream* const fStream
   * ```
   */
  private val fStream: SkStream? = TODO("Initialize fStream")

  /**
   * C++ original:
   * ```cpp
   * bool fSeekable = false
   * ```
   */
  private var fSeekable: Boolean = TODO("Initialize fSeekable")

  /**
   * C++ original:
   * ```cpp
   * bool fMemoryMapped = false
   * ```
   */
  private var fMemoryMapped: Boolean = TODO("Initialize fMemoryMapped")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buffer, size_t size) override { return fStream->read(buffer, size); }
   * ```
   */
  public override fun read(buffer: Unit?, size: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t peek(void* buffer, size_t size) const override { return fStream->peek(buffer, size); }
   * ```
   */
  public override fun peek(buffer: Unit?, size: ULong): ULong {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override { return fStream->isAtEnd(); }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rewind() override {
   *         if (!fSeekable) {
   *             return false;
   *         }
   *         return fStream->rewind();
   *     }
   * ```
   */
  public override fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPosition() const override {
   *         if (!fSeekable) {
   *             return false;
   *         }
   *         return fStream->hasPosition();
   *     }
   * ```
   */
  public override fun hasPosition(): Boolean {
    TODO("Implement hasPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getPosition() const override {
   *         if (!fSeekable) {
   *             return 0;
   *         }
   *         return fStream->hasPosition();
   *     }
   * ```
   */
  public override fun getPosition(): ULong {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * bool seek(size_t position) override {
   *         if (!fSeekable) {
   *             return 0;
   *         }
   *         return fStream->seek(position);
   *     }
   * ```
   */
  public override fun seek(position: ULong): Boolean {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool move(long offset) override {
   *         if (!fSeekable) {
   *             return 0;
   *         }
   *         return fStream->move(offset);
   *     }
   * ```
   */
  public override fun move(offset: Long): Boolean {
    TODO("Implement move")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasLength() const override {
   *         if (!fMemoryMapped) {
   *             return false;
   *         }
   *         return fStream->hasLength();
   *     }
   * ```
   */
  public override fun hasLength(): Boolean {
    TODO("Implement hasLength")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getLength() const override {
   *         if (!fMemoryMapped) {
   *             return 0;
   *         }
   *         return fStream->getLength();
   *     }
   * ```
   */
  public override fun getLength(): ULong {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* getMemoryBase() override {
   *         if (!fMemoryMapped) {
   *             return nullptr;
   *         }
   *         return fStream->getMemoryBase();
   *     }
   * ```
   */
  public override fun getMemoryBase() {
    TODO("Implement getMemoryBase")
  }

  public enum class Type {
    kUnseekable,
    kSeekable,
    kMemoryMapped,
  }
}
