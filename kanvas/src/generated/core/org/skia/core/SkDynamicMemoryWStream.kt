package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.modules.Block

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDynamicMemoryWStream : public SkWStream {
 * public:
 *     SkDynamicMemoryWStream() = default;
 *     SkDynamicMemoryWStream(SkDynamicMemoryWStream&&);
 *     SkDynamicMemoryWStream& operator=(SkDynamicMemoryWStream&&);
 *     ~SkDynamicMemoryWStream() override;
 *
 *     bool write(const void* buffer, size_t size) override;
 *     size_t bytesWritten() const override;
 *
 *     bool read(void* buffer, size_t offset, size_t size);
 *
 *     /** More efficient version of read(dst, 0, bytesWritten()). */
 *     void copyTo(void* dst) const;
 *     bool writeToStream(SkWStream* dst) const;
 *
 *     /** Equivalent to copyTo() followed by reset(), but may save memory use. */
 *     void copyToAndReset(void* dst);
 *
 *     /** Equivalent to writeToStream() followed by reset(), but may save memory use. */
 *     bool writeToAndReset(SkWStream* dst);
 *
 *     /** Equivalent to writeToStream() followed by reset(), but may save memory use.
 *         When the dst is also a SkDynamicMemoryWStream, the implementation is constant time. */
 *     bool writeToAndReset(SkDynamicMemoryWStream* dst);
 *
 *     /** Prepend this stream to dst, resetting this. */
 *     void prependToAndReset(SkDynamicMemoryWStream* dst);
 *
 *     /** Return the contents as SkData, and then reset the stream. */
 *     sk_sp<SkData> detachAsData();
 *
 *     /** Return the contents as vector, and then reset the stream. */
 *     std::vector<uint8_t> detachAsVector();
 *
 *     /** Reset, returning a reader stream with the current content. */
 *     std::unique_ptr<SkStreamAsset> detachAsStream();
 *
 *     /** Reset the stream to its original, empty, state. */
 *     void reset();
 *     void padToAlign4();
 * private:
 *     struct Block;
 *     Block*  fHead = nullptr;
 *     Block*  fTail = nullptr;
 *     size_t  fBytesWrittenBeforeTail = 0;
 *
 * #ifdef SK_DEBUG
 *     void validate() const;
 * #else
 *     void validate() const {}
 * #endif
 *
 *     // For access to the Block type.
 *     friend class SkBlockMemoryStream;
 *     friend class SkBlockMemoryRefCnt;
 *
 *     using INHERITED = SkWStream;
 * }
 * ```
 */
public open class SkDynamicMemoryWStream public constructor() : SkWStream() {
  /**
   * C++ original:
   * ```cpp
   * Block*  fHead = nullptr
   * ```
   */
  private var fHead: Block? = TODO("Initialize fHead")

  /**
   * C++ original:
   * ```cpp
   * Block*  fTail = nullptr
   * ```
   */
  private var fTail: Block? = TODO("Initialize fTail")

  /**
   * C++ original:
   * ```cpp
   * size_t  fBytesWrittenBeforeTail = 0
   * ```
   */
  private var fBytesWrittenBeforeTail: ULong = TODO("Initialize fBytesWrittenBeforeTail")

  /**
   * C++ original:
   * ```cpp
   * SkDynamicMemoryWStream() = default
   * ```
   */
  public constructor(other: SkDynamicMemoryWStream) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDynamicMemoryWStream& SkDynamicMemoryWStream::operator=(SkDynamicMemoryWStream&& other) {
   *     if (this != &other) {
   *         this->~SkDynamicMemoryWStream();
   *         new (this) SkDynamicMemoryWStream(std::move(other));
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(other: SkDynamicMemoryWStream) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDynamicMemoryWStream::write(const void* buffer, size_t count) {
   *     if (count > 0) {
   *         SkASSERT(buffer);
   *         size_t size;
   *
   *         if (fTail && fTail->avail() > 0) {
   *             size = std::min(fTail->avail(), count);
   *             buffer = fTail->append(buffer, size);
   *             SkASSERT(count >= size);
   *             count -= size;
   *             if (count == 0) {
   *                 return true;
   *             }
   *         }
   *
   *         size = std::max<size_t>(count, SkDynamicMemoryWStream_MinBlockSize - sizeof(Block));
   *         size = SkAlign4(size);  // ensure we're always a multiple of 4 (see padToAlign4())
   *
   *         Block* block = (Block*)sk_malloc_canfail(sizeof(Block) + size);
   *         if (!block) {
   *             this->validate();
   *             return false;
   *         }
   *         block->init(size);
   *         block->append(buffer, count);
   *
   *         if (fTail) {
   *             fBytesWrittenBeforeTail += fTail->written();
   *             fTail->fNext = block;
   *         } else {
   *             fHead = fTail = block;
   *         }
   *         fTail = block;
   *         this->validate();
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun write(buffer: Unit?, size: ULong): Boolean {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkDynamicMemoryWStream::bytesWritten() const {
   *     this->validate();
   *
   *     if (fTail) {
   *         return fBytesWrittenBeforeTail + fTail->written();
   *     }
   *     return 0;
   * }
   * ```
   */
  public override fun bytesWritten(): ULong {
    TODO("Implement bytesWritten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDynamicMemoryWStream::read(void* buffer, size_t offset, size_t count) {
   *     if (offset + count > this->bytesWritten()) {
   *         return false; // test does not partially modify
   *     }
   *     Block* block = fHead;
   *     while (block != nullptr) {
   *         size_t size = block->written();
   *         if (offset < size) {
   *             size_t part = offset + count > size ? size - offset : count;
   *             memcpy(buffer, block->start() + offset, part);
   *             if (count <= part) {
   *                 return true;
   *             }
   *             count -= part;
   *             buffer = (void*) ((char* ) buffer + part);
   *         }
   *         offset = offset > size ? offset - size : 0;
   *         block = block->fNext;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun read(
    buffer: Unit?,
    offset: ULong,
    size: ULong,
  ): Boolean {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::copyTo(void* dst) const {
   *     SkASSERT(dst);
   *     Block* block = fHead;
   *     while (block != nullptr) {
   *         size_t size = block->written();
   *         memcpy(dst, block->start(), size);
   *         dst = (void*)((char*)dst + size);
   *         block = block->fNext;
   *     }
   * }
   * ```
   */
  public fun copyTo(dst: Unit?) {
    TODO("Implement copyTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDynamicMemoryWStream::writeToStream(SkWStream* dst) const {
   *     SkASSERT(dst);
   *     for (Block* block = fHead; block != nullptr; block = block->fNext) {
   *         if (!dst->write(block->start(), block->written())) {
   *             return false;
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  public fun writeToStream(dst: SkWStream?): Boolean {
    TODO("Implement writeToStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::copyToAndReset(void* ptr) {
   *     if (!ptr) {
   *         this->reset();
   *         return;
   *     }
   *     // By looping through the source and freeing as we copy, we
   *     // can reduce real memory use with large streams.
   *     char* dst = reinterpret_cast<char*>(ptr);
   *     Block* block = fHead;
   *     while (block != nullptr) {
   *         size_t len = block->written();
   *         memcpy(dst, block->start(), len);
   *         dst += len;
   *         Block* next = block->fNext;
   *         sk_free(block);
   *         block = next;
   *     }
   *     fHead = fTail = nullptr;
   *     fBytesWrittenBeforeTail = 0;
   * }
   * ```
   */
  public fun copyToAndReset(dst: Unit?) {
    TODO("Implement copyToAndReset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDynamicMemoryWStream::writeToAndReset(SkWStream* dst) {
   *     SkASSERT(dst);
   *     // By looping through the source and freeing as we copy, we
   *     // can reduce real memory use with large streams.
   *     bool dstStreamGood = true;
   *     for (Block* block = fHead; block != nullptr; ) {
   *         if (dstStreamGood && !dst->write(block->start(), block->written())) {
   *             dstStreamGood = false;
   *         }
   *         Block* next = block->fNext;
   *         sk_free(block);
   *         block = next;
   *     }
   *     fHead = fTail = nullptr;
   *     fBytesWrittenBeforeTail = 0;
   *     return dstStreamGood;
   * }
   * ```
   */
  public fun writeToAndReset(dst: SkWStream?): Boolean {
    TODO("Implement writeToAndReset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDynamicMemoryWStream::writeToAndReset(SkDynamicMemoryWStream* dst) {
   *     SkASSERT(dst);
   *     SkASSERT(dst != this);
   *     if (0 == this->bytesWritten()) {
   *         return true;
   *     }
   *     if (0 == dst->bytesWritten()) {
   *         *dst = std::move(*this);
   *         return true;
   *     }
   *     dst->fTail->fNext = fHead;
   *     dst->fBytesWrittenBeforeTail += fBytesWrittenBeforeTail + dst->fTail->written();
   *     dst->fTail = fTail;
   *     fHead = fTail = nullptr;
   *     fBytesWrittenBeforeTail = 0;
   *     return true;
   * }
   * ```
   */
  public fun writeToAndReset(dst: SkDynamicMemoryWStream?): Boolean {
    TODO("Implement writeToAndReset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::prependToAndReset(SkDynamicMemoryWStream* dst) {
   *     SkASSERT(dst);
   *     SkASSERT(dst != this);
   *     if (0 == this->bytesWritten()) {
   *         return;
   *     }
   *     if (0 == dst->bytesWritten()) {
   *         *dst = std::move(*this);
   *         return;
   *     }
   *     fTail->fNext = dst->fHead;
   *     dst->fHead = fHead;
   *     dst->fBytesWrittenBeforeTail += fBytesWrittenBeforeTail + fTail->written();
   *     fHead = fTail = nullptr;
   *     fBytesWrittenBeforeTail = 0;
   * }
   * ```
   */
  public fun prependToAndReset(dst: SkDynamicMemoryWStream?) {
    TODO("Implement prependToAndReset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkDynamicMemoryWStream::detachAsData() {
   *     const size_t size = this->bytesWritten();
   *     if (0 == size) {
   *         return SkData::MakeEmpty();
   *     }
   *     sk_sp<SkData> data = SkData::MakeUninitialized(size);
   *     this->copyToAndReset(data->writable_data());
   *     return data;
   * }
   * ```
   */
  public fun detachAsData(): Int {
    TODO("Implement detachAsData")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<uint8_t> SkDynamicMemoryWStream::detachAsVector() {
   *     std::vector<uint8_t> result;
   *
   *     const size_t size = this->bytesWritten();
   *     if (0 == size) {
   *         return result;
   *     }
   *
   *     result.resize(size);
   *     this->copyToAndReset(result.data());
   *     return result;
   * }
   * ```
   */
  public fun detachAsVector(): Int {
    TODO("Implement detachAsVector")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkDynamicMemoryWStream::detachAsStream() {
   *     if (nullptr == fHead) {
   *         // no need to reset.
   *         return SkMemoryStream::Make(nullptr);
   *     }
   *     if (fHead == fTail) {  // one block, may be worth shrinking.
   *         ptrdiff_t used = fTail->fCurr - (char*)fTail;
   *         fHead = fTail = (SkDynamicMemoryWStream::Block*)sk_realloc_throw(fTail, SkToSizeT(used));
   *         fTail->fStop = fTail->fCurr = (char*)fTail + used;  // Update pointers.
   *         SkASSERT(nullptr == fTail->fNext);
   *         SkASSERT(0 == fBytesWrittenBeforeTail);
   *     }
   *     std::unique_ptr<SkStreamAsset> stream
   *             = std::make_unique<SkBlockMemoryStream>(sk_make_sp<SkBlockMemoryRefCnt>(fHead),
   *                                                       this->bytesWritten());
   *     fHead = nullptr;    // signal reset() to not free anything
   *     this->reset();
   *     return stream;
   * }
   * ```
   */
  public fun detachAsStream(): Int {
    TODO("Implement detachAsStream")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::reset() {
   *     Block* block = fHead;
   *     while (block != nullptr) {
   *         Block* next = block->fNext;
   *         sk_free(block);
   *         block = next;
   *     }
   *     fHead = fTail = nullptr;
   *     fBytesWrittenBeforeTail = 0;
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::padToAlign4() {
   *     // The contract is to write zeros until the entire stream has written a multiple of 4 bytes.
   *     // Our Blocks are guaranteed always be (a) full (except the tail) and (b) a multiple of 4
   *     // so it is sufficient to just examine the tail (if present).
   *
   *     if (fTail) {
   *         // cast to remove unary-minus warning
   *         int padBytes = -(int)fTail->written() & 0x03;
   *         if (padBytes) {
   *             int zero = 0;
   *             fTail->append(&zero, padBytes);
   *         }
   *     }
   * }
   * ```
   */
  public fun padToAlign4() {
    TODO("Implement padToAlign4")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDynamicMemoryWStream::validate() const {
   *     if (!fHead) {
   *         SkASSERT(!fTail);
   *         SkASSERT(fBytesWrittenBeforeTail == 0);
   *         return;
   *     }
   *     SkASSERT(fTail);
   *
   *     size_t bytes = 0;
   *     const Block* block = fHead;
   *     while (block) {
   *         if (block->fNext) {
   *             bytes += block->written();
   *         }
   *         block = block->fNext;
   *     }
   *     SkASSERT(bytes == fBytesWrittenBeforeTail);
   * }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }
}
