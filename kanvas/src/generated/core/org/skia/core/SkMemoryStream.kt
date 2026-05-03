package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkMemoryStream : public SkStreamMemory {
 * public:
 *     SkMemoryStream();
 *
 *     /** We allocate (and free) the memory. Write to it via getMemoryBase() */
 *     explicit SkMemoryStream(size_t length);
 *
 *     /** If copyData is true, the stream makes a private copy of the data. */
 *     SkMemoryStream(const void* data, size_t length, bool copyData = false);
 *
 *     /** Creates the stream to read from the specified data */
 *     explicit SkMemoryStream(sk_sp<const SkData> data);
 *
 *     /** Returns a stream with a copy of the input data. */
 *     static std::unique_ptr<SkMemoryStream> MakeCopy(const void* data, size_t length);
 *
 *     /** Returns a stream with a bare pointer reference to the input data. */
 *     static std::unique_ptr<SkMemoryStream> MakeDirect(const void* data, size_t length);
 *
 *     /** Returns a stream with a shared reference to the input data. */
 *     static std::unique_ptr<SkMemoryStream> Make(sk_sp<const SkData> data);
 *
 *     /** Resets the stream to the specified data and length,
 *         just like the constructor.
 *         if copyData is true, the stream makes a private copy of the data
 *     */
 *     virtual void setMemory(const void* data, size_t length,
 *                            bool copyData = false);
 *     /** Replace any memory buffer with the specified buffer. The caller
 *         must have allocated data with sk_malloc or sk_realloc, since it
 *         will be freed with sk_free.
 *     */
 *     void setMemoryOwned(const void* data, size_t length);
 *
 *     sk_sp<const SkData> getData() const override { return fData; }
 *
 *     void setData(sk_sp<const SkData> data);
 *
 *     const void* getAtPos();
 *
 *     size_t read(void* buffer, size_t size) override;
 *     bool isAtEnd() const override;
 *
 *     size_t peek(void* buffer, size_t size) const override;
 *
 *     bool rewind() override;
 *
 *     std::unique_ptr<SkMemoryStream> duplicate() const {
 *         return std::unique_ptr<SkMemoryStream>(this->onDuplicate());
 *     }
 *
 *     size_t getPosition() const override;
 *     bool seek(size_t position) override;
 *     bool move(long offset) override;
 *
 *     std::unique_ptr<SkMemoryStream> fork() const {
 *         return std::unique_ptr<SkMemoryStream>(this->onFork());
 *     }
 *
 *     size_t getLength() const override;
 *
 *     const void* getMemoryBase() override;
 *
 * private:
 *     SkMemoryStream* onDuplicate() const override;
 *     SkMemoryStream* onFork() const override;
 *
 *     sk_sp<const SkData> fData;
 *     size_t fOffset;
 *
 *     using INHERITED = SkStreamMemory;
 * }
 * ```
 */
public open class SkMemoryStream public constructor() : SkStreamMemory() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> fData
   * ```
   */
  private var fData: Int = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * size_t fOffset
   * ```
   */
  private var fOffset: ULong = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * SkMemoryStream::SkMemoryStream() {
   *     fData = SkData::MakeEmpty();
   *     fOffset = 0;
   * }
   * ```
   */
  public constructor(length: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkMemoryStream(size_t length)
   * ```
   */
  public constructor(
    `data`: Unit?,
    length: ULong,
    copyData: Boolean = false,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMemoryStream(const void* data, size_t length, bool copyData = false)
   * ```
   */
  public constructor(`data`: SkSp<SkData>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMemoryStream::setMemory(const void* src, size_t size, bool copyData) {
   *     fData = newFromParams(src, size, copyData);
   *     fOffset = 0;
   * }
   * ```
   */
  public open fun setMemory(
    `data`: Unit?,
    length: ULong,
    copyData: Boolean = false,
  ) {
    TODO("Implement setMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMemoryStream::setMemoryOwned(const void* src, size_t size) {
   *     fData = SkData::MakeFromMalloc(src, size);
   *     fOffset = 0;
   * }
   * ```
   */
  public fun setMemoryOwned(`data`: Unit?, length: ULong) {
    TODO("Implement setMemoryOwned")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> getData() const override { return fData; }
   * ```
   */
  public override fun getData(): Int {
    TODO("Implement getData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMemoryStream::setData(sk_sp<const SkData> data) {
   *     if (nullptr == data) {
   *         fData = SkData::MakeEmpty();
   *     } else {
   *         fData = data;
   *     }
   *     fOffset = 0;
   * }
   * ```
   */
  public fun setData(`data`: SkSp<SkData>) {
    TODO("Implement setData")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkMemoryStream::getAtPos() {
   *     return fData->bytes() + fOffset;
   * }
   * ```
   */
  public fun getAtPos() {
    TODO("Implement getAtPos")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMemoryStream::read(void* buffer, size_t size) {
   *     size_t dataSize = fData->size();
   *
   *     SkASSERT(fOffset <= dataSize);
   *     if (size > dataSize - fOffset) {
   *         size = dataSize - fOffset;
   *     }
   *     if (buffer) {
   *         sk_careful_memcpy(buffer, fData->bytes() + fOffset, size);
   *     }
   *     fOffset += size;
   *     return size;
   * }
   * ```
   */
  public override fun read(buffer: Unit?, size: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMemoryStream::isAtEnd() const {
   *     return fOffset == fData->size();
   * }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMemoryStream::peek(void* buffer, size_t size) const {
   *     SkASSERT(buffer != nullptr);
   *
   *     const size_t currentOffset = fOffset;
   *     SkMemoryStream* nonConstThis = const_cast<SkMemoryStream*>(this);
   *     const size_t bytesRead = nonConstThis->read(buffer, size);
   *     nonConstThis->fOffset = currentOffset;
   *     return bytesRead;
   * }
   * ```
   */
  public override fun peek(buffer: Unit?, size: ULong): ULong {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMemoryStream::rewind() {
   *     fOffset = 0;
   *     return true;
   * }
   * ```
   */
  public override fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkMemoryStream> duplicate() const {
   *         return std::unique_ptr<SkMemoryStream>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): Int {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMemoryStream::getPosition() const {
   *     return fOffset;
   * }
   * ```
   */
  public override fun getPosition(): ULong {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMemoryStream::seek(size_t position) {
   *     fOffset = position > fData->size()
   *             ? fData->size()
   *             : position;
   *     return true;
   * }
   * ```
   */
  public override fun seek(position: ULong): Boolean {
    TODO("Implement seek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMemoryStream::move(long offset) {
   *     return this->seek(fOffset + offset);
   * }
   * ```
   */
  public override fun move(offset: Long): Boolean {
    TODO("Implement move")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkMemoryStream> fork() const {
   *         return std::unique_ptr<SkMemoryStream>(this->onFork());
   *     }
   * ```
   */
  public override fun fork(): Int {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMemoryStream::getLength() const {
   *     return fData->size();
   * }
   * ```
   */
  public override fun getLength(): ULong {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkMemoryStream::getMemoryBase() {
   *     return fData->data();
   * }
   * ```
   */
  public override fun getMemoryBase() {
    TODO("Implement getMemoryBase")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMemoryStream* SkMemoryStream::onDuplicate() const {
   *     return new SkMemoryStream(fData);
   * }
   * ```
   */
  public override fun onDuplicate(): SkMemoryStream {
    TODO("Implement onDuplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMemoryStream* SkMemoryStream::onFork() const {
   *     std::unique_ptr<SkMemoryStream> that(this->duplicate());
   *     that->seek(fOffset);
   *     return that.release();
   * }
   * ```
   */
  public override fun onFork(): SkMemoryStream {
    TODO("Implement onFork")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkMemoryStream> SkMemoryStream::MakeCopy(const void* data, size_t length) {
     *     return std::make_unique<SkMemoryStream>(data, length, true);
     * }
     * ```
     */
    public fun makeCopy(`data`: Unit?, length: ULong): Int {
      TODO("Implement makeCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkMemoryStream> SkMemoryStream::MakeDirect(const void* data, size_t length) {
     *     return std::make_unique<SkMemoryStream>(data, length, false);
     * }
     * ```
     */
    public fun makeDirect(`data`: Unit?, length: ULong): Int {
      TODO("Implement makeDirect")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkMemoryStream> SkMemoryStream::Make(sk_sp<const SkData> data) {
     *     return std::make_unique<SkMemoryStream>(std::move(data));
     * }
     * ```
     */
    public fun make(`data`: SkSp<SkData>): Int {
      TODO("Implement make")
    }
  }
}
