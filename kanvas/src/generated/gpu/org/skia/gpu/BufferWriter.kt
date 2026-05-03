package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct BufferWriter {
 * public:
 *     // Marks a read-only position in the underlying buffer
 *     struct Mark {
 *     public:
 *         Mark() : Mark(nullptr) {}
 *         Mark(void* ptr, size_t offset = 0)
 *                 : fMark(reinterpret_cast<uintptr_t>(ptr) + offset) {
 *             SkASSERT(ptr || offset == 0);
 *         }
 *
 *         bool operator< (const Mark& o) const { return fMark <  o.fMark; }
 *         bool operator<=(const Mark& o) const { return fMark <= o.fMark; }
 *         bool operator==(const Mark& o) const { return fMark == o.fMark; }
 *         bool operator!=(const Mark& o) const { return fMark != o.fMark; }
 *         bool operator>=(const Mark& o) const { return fMark >= o.fMark; }
 *         bool operator> (const Mark& o) const { return fMark >  o.fMark; }
 *
 *         ptrdiff_t operator-(const Mark& o) const { return fMark - o.fMark; }
 *
 *         explicit operator bool() const { return *this != Mark(); }
 *     private:
 *         uintptr_t fMark;
 *     };
 *
 *     BufferWriter() = default;
 *     BufferWriter(BufferWriter&& w) { *this = std::move(w); }
 *
 *     BufferWriter(void* ptr, size_t size) : fPtr(ptr) {
 *         SkDEBUGCODE(fEnd = Mark(ptr, ptr ? size : 0);)
 *     }
 *     BufferWriter(void* ptr, Mark end = {}) : fPtr(ptr) {
 *         SkDEBUGCODE(fEnd = end;)
 *     }
 *
 *     BufferWriter& operator=(const BufferWriter&) = delete;
 *     BufferWriter& operator=(BufferWriter&& that) {
 *         fPtr = that.fPtr;
 *         that.fPtr = nullptr;
 *         SkDEBUGCODE(fEnd = that.fEnd;)
 *         SkDEBUGCODE(that.fEnd = Mark();)
 *         return *this;
 *     }
 *
 *     explicit operator bool() const { return fPtr != nullptr; }
 *
 *     Mark mark(size_t offset=0) const {
 *         this->validate(offset);
 *         return Mark(fPtr, offset);
 *     }
 *
 *     void zeroBytes(size_t bytes) {
 *         auto s = this->slice(bytes);
 *         memset(s.data(), 0, s.size_bytes());
 *     }
 *
 *     void write(const void* src, size_t bytes) {
 *         auto s = this->slice(bytes);
 *         memcpy(s.data(), src, s.size_bytes());
 *     }
 *
 *     template <typename T>
 *     void write(SkSpan<const T> data) { this->write(data.data(), data.size_bytes()); }
 *     template <typename T>
 *     void write(T data) { this->write(&data, sizeof(T)); }
 *
 * protected:
 *     // For integration with Rust, to expose slice() directly
 *     friend class skgpu::graphite::VelloRenderer;
 *
 *     // makeOffset effectively splits the current writer from {fPtr, fEnd} into {fPtr, p} and
 *     // a new writer {p, fEnd}. The same data range is accessible, but each byte can only be
 *     // set by a single writer. Automatically validates that there is enough bytes remaining in this
 *     // writer to do such a split.
 *     //
 *     // This splitting and validation means that providers of BufferWriters to callers can easily
 *     // and correctly track everything in a single BufferWriter field and use
 *     //    return std::exchange(fCurrWriter, fCurrWriter.makeOffset(requestedBytes));
 *     // This exposes the current writer position to the caller and sets the provider's new current
 *     // position to be just after the requested bytes.
 *     //
 *     // Templated so that it can create subclasses directly.
 *     template<typename W>
 *     W makeOffset(size_t offsetInBytes) const {
 *         this->validate(offsetInBytes);
 *         void* p = SkTAddOffset<void>(fPtr, offsetInBytes);
 *         Mark end{SkDEBUGCODE(fEnd)};
 *         SkDEBUGCODE(fEnd = Mark(p);)
 *         return W{p, end};
 *     }
 *
 *     // The Writer's pointer is advanced by `bytes` just as though `write()` had been called. The
 *     // returned pointer should not be used for reading, and should only write to each index once, in
 *     // order, for optimal performance.
 *     SkSpan<uint8_t> slice(size_t bytes) {
 *         this->validate(bytes);
 *         SkSpan<uint8_t> slice{static_cast<uint8_t*>(fPtr), bytes};
 *         fPtr = SkTAddOffset<void>(fPtr, bytes);
 *         return slice;
 *     }
 *
 *     void validate(size_t bytesToWrite) const {
 *         // If the buffer writer had an end marked, make sure we're not crossing it.
 *         // Ideally, all creators of BufferWriters mark the end, but a lot of legacy code is not set
 *         // up to easily do this.
 *         SkASSERT(fPtr || bytesToWrite == 0);
 *         SkASSERT(!fEnd || Mark(fPtr, bytesToWrite) <= fEnd);
 *     }
 *
 *     void* fPtr = nullptr;
 *     SkDEBUGCODE(mutable Mark fEnd = {};)
 * }
 * ```
 */
public abstract class BufferWriter public constructor(
  /**
   * C++ original:
   * ```cpp
   * void* fPtr = nullptr
   * ```
   */
  protected var fPtr: Unit?,
) {
  /**
   * C++ original:
   * ```cpp
   * BufferWriter() = default
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter(BufferWriter&& w) { *this = std::move(w); }
   * ```
   */
  public constructor(w: BufferWriter) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter(void* ptr, size_t size) : fPtr(ptr) {
   *         SkDEBUGCODE(fEnd = Mark(ptr, ptr ? size : 0);)
   *     }
   * ```
   */
  public constructor(ptr: Unit?, size: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter(void* ptr, Mark end = {}) : fPtr(ptr) {
   *         SkDEBUGCODE(fEnd = end;)
   *     }
   * ```
   */
  public constructor(ptr: Unit?, end: Mark) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter& operator=(const BufferWriter&) = delete
   * ```
   */
  private fun assign(param0: BufferWriter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter& operator=(BufferWriter&& that) {
   *         fPtr = that.fPtr;
   *         that.fPtr = nullptr;
   *         SkDEBUGCODE(fEnd = that.fEnd;)
   *         SkDEBUGCODE(that.fEnd = Mark();)
   *         return *this;
   *     }
   * ```
   */
  private abstract fun mark(offset: ULong = TODO()): Mark

  /**
   * C++ original:
   * ```cpp
   * Mark mark(size_t offset=0) const {
   *         this->validate(offset);
   *         return Mark(fPtr, offset);
   *     }
   * ```
   */
  private fun zeroBytes(bytes: ULong) {
    TODO("Implement zeroBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void zeroBytes(size_t bytes) {
   *         auto s = this->slice(bytes);
   *         memset(s.data(), 0, s.size_bytes());
   *     }
   * ```
   */
  private fun write(src: Unit?, bytes: ULong) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const void* src, size_t bytes) {
   *         auto s = this->slice(bytes);
   *         memcpy(s.data(), src, s.size_bytes());
   *     }
   * ```
   */
  private fun <T> write(`data`: SkSpan<T>) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void write(SkSpan<const T> data) { this->write(data.data(), data.size_bytes()); }
   * ```
   */
  private fun <T> write(`data`: T) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void write(T data) { this->write(&data, sizeof(T)); }
   * ```
   */
  protected fun <W> makeOffset(offsetInBytes: ULong): W {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename W>
   *     W makeOffset(size_t offsetInBytes) const {
   *         this->validate(offsetInBytes);
   *         void* p = SkTAddOffset<void>(fPtr, offsetInBytes);
   *         Mark end{SkDEBUGCODE(fEnd)};
   *         SkDEBUGCODE(fEnd = Mark(p);)
   *         return W{p, end};
   *     }
   * ```
   */
  protected fun slice(bytes: ULong): Int {
    TODO("Implement slice")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<uint8_t> slice(size_t bytes) {
   *         this->validate(bytes);
   *         SkSpan<uint8_t> slice{static_cast<uint8_t*>(fPtr), bytes};
   *         fPtr = SkTAddOffset<void>(fPtr, bytes);
   *         return slice;
   *     }
   * ```
   */
  protected fun validate(bytesToWrite: ULong) {
    TODO("Implement validate")
  }

  public data class Mark public constructor(
    private var fMark: Int,
  ) {
    public operator fun compareTo(o: undefined.Mark): Int {
      TODO("Implement compareTo")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public operator fun minus(o: undefined.Mark): Int {
      TODO("Implement minus")
    }
  }
}
