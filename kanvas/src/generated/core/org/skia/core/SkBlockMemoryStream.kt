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
 * class SkBlockMemoryStream : public SkStreamAsset {
 * public:
 *     SkBlockMemoryStream(sk_sp<SkBlockMemoryRefCnt> headRef, size_t size)
 *         : fBlockMemory(std::move(headRef)), fCurrent(fBlockMemory->fHead)
 *         , fSize(size) , fOffset(0), fCurrentOffset(0) { }
 *
 *     size_t read(void* buffer, size_t rawCount) override {
 *         size_t count = rawCount;
 *         if (fOffset + count > fSize) {
 *             count = fSize - fOffset;
 *         }
 *         size_t bytesLeftToRead = count;
 *         while (fCurrent != nullptr) {
 *             size_t bytesLeftInCurrent = fCurrent->written() - fCurrentOffset;
 *             size_t bytesFromCurrent = std::min(bytesLeftToRead, bytesLeftInCurrent);
 *             if (buffer) {
 *                 memcpy(buffer, fCurrent->start() + fCurrentOffset, bytesFromCurrent);
 *                 buffer = SkTAddOffset<void>(buffer, bytesFromCurrent);
 *             }
 *             if (bytesLeftToRead <= bytesFromCurrent) {
 *                 fCurrentOffset += bytesFromCurrent;
 *                 fOffset += count;
 *                 return count;
 *             }
 *             bytesLeftToRead -= bytesFromCurrent;
 *             fCurrent = fCurrent->fNext;
 *             fCurrentOffset = 0;
 *         }
 *         SkASSERT(false);
 *         return 0;
 *     }
 *
 *     bool isAtEnd() const override {
 *         return fOffset == fSize;
 *     }
 *
 *     size_t peek(void* buff, size_t bytesToPeek) const override {
 *         SkASSERT(buff != nullptr);
 *
 *         bytesToPeek = std::min(bytesToPeek, fSize - fOffset);
 *
 *         size_t bytesLeftToPeek = bytesToPeek;
 *         char* buffer = static_cast<char*>(buff);
 *         const SkDynamicMemoryWStream::Block* current = fCurrent;
 *         size_t currentOffset = fCurrentOffset;
 *         while (bytesLeftToPeek) {
 *             SkASSERT(current);
 *             size_t bytesFromCurrent = std::min(current->written() - currentOffset, bytesLeftToPeek);
 *             memcpy(buffer, current->start() + currentOffset, bytesFromCurrent);
 *             bytesLeftToPeek -= bytesFromCurrent;
 *             buffer += bytesFromCurrent;
 *             current = current->fNext;
 *             currentOffset = 0;
 *         }
 *         return bytesToPeek;
 *     }
 *
 *     bool rewind() override {
 *         fCurrent = fBlockMemory->fHead;
 *         fOffset = 0;
 *         fCurrentOffset = 0;
 *         return true;
 *     }
 *
 *     SkBlockMemoryStream* onDuplicate() const override {
 *         return new SkBlockMemoryStream(fBlockMemory, fSize);
 *     }
 *
 *     size_t getPosition() const override {
 *         return fOffset;
 *     }
 *
 *     bool seek(size_t position) override {
 *         // If possible, skip forward.
 *         if (position >= fOffset) {
 *             size_t skipAmount = position - fOffset;
 *             return this->skip(skipAmount) == skipAmount;
 *         }
 *         // If possible, move backward within the current block.
 *         size_t moveBackAmount = fOffset - position;
 *         if (moveBackAmount <= fCurrentOffset) {
 *             fCurrentOffset -= moveBackAmount;
 *             fOffset -= moveBackAmount;
 *             return true;
 *         }
 *         // Otherwise rewind and move forward.
 *         return this->rewind() && this->skip(position) == position;
 *     }
 *
 *     bool move(long offset) override {
 *         return seek(fOffset + offset);
 *     }
 *
 *     SkBlockMemoryStream* onFork() const override {
 *         SkBlockMemoryStream* that = this->onDuplicate();
 *         that->fCurrent = this->fCurrent;
 *         that->fOffset = this->fOffset;
 *         that->fCurrentOffset = this->fCurrentOffset;
 *         return that;
 *     }
 *
 *     size_t getLength() const override {
 *         return fSize;
 *     }
 *
 *     const void* getMemoryBase() override {
 *         if (fBlockMemory->fHead && !fBlockMemory->fHead->fNext) {
 *             return fBlockMemory->fHead->start();
 *         }
 *         return nullptr;
 *     }
 *
 * private:
 *     sk_sp<SkBlockMemoryRefCnt> const fBlockMemory;
 *     SkDynamicMemoryWStream::Block const * fCurrent;
 *     size_t const fSize;
 *     size_t fOffset;
 *     size_t fCurrentOffset;
 * }
 * ```
 */
public open class SkBlockMemoryStream public constructor(
  headRef: SkSp<SkBlockMemoryRefCnt>,
  size: ULong,
) : SkStreamAsset() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkBlockMemoryRefCnt> const fBlockMemory
   * ```
   */
  private val fBlockMemory: SkSp<SkBlockMemoryRefCnt> = TODO("Initialize fBlockMemory")

  /**
   * C++ original:
   * ```cpp
   * SkDynamicMemoryWStream::Block const * fCurrent
   * ```
   */
  private val fCurrent: Block? = TODO("Initialize fCurrent")

  /**
   * C++ original:
   * ```cpp
   * size_t const fSize
   * ```
   */
  private val fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * size_t fOffset
   * ```
   */
  private var fOffset: Int = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * size_t fCurrentOffset
   * ```
   */
  private var fCurrentOffset: Int = TODO("Initialize fCurrentOffset")

  /**
   * C++ original:
   * ```cpp
   * size_t read(void* buffer, size_t rawCount) override {
   *         size_t count = rawCount;
   *         if (fOffset + count > fSize) {
   *             count = fSize - fOffset;
   *         }
   *         size_t bytesLeftToRead = count;
   *         while (fCurrent != nullptr) {
   *             size_t bytesLeftInCurrent = fCurrent->written() - fCurrentOffset;
   *             size_t bytesFromCurrent = std::min(bytesLeftToRead, bytesLeftInCurrent);
   *             if (buffer) {
   *                 memcpy(buffer, fCurrent->start() + fCurrentOffset, bytesFromCurrent);
   *                 buffer = SkTAddOffset<void>(buffer, bytesFromCurrent);
   *             }
   *             if (bytesLeftToRead <= bytesFromCurrent) {
   *                 fCurrentOffset += bytesFromCurrent;
   *                 fOffset += count;
   *                 return count;
   *             }
   *             bytesLeftToRead -= bytesFromCurrent;
   *             fCurrent = fCurrent->fNext;
   *             fCurrentOffset = 0;
   *         }
   *         SkASSERT(false);
   *         return 0;
   *     }
   * ```
   */
  public override fun read(buffer: Unit?, rawCount: ULong): Int {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAtEnd() const override {
   *         return fOffset == fSize;
   *     }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t peek(void* buff, size_t bytesToPeek) const override {
   *         SkASSERT(buff != nullptr);
   *
   *         bytesToPeek = std::min(bytesToPeek, fSize - fOffset);
   *
   *         size_t bytesLeftToPeek = bytesToPeek;
   *         char* buffer = static_cast<char*>(buff);
   *         const SkDynamicMemoryWStream::Block* current = fCurrent;
   *         size_t currentOffset = fCurrentOffset;
   *         while (bytesLeftToPeek) {
   *             SkASSERT(current);
   *             size_t bytesFromCurrent = std::min(current->written() - currentOffset, bytesLeftToPeek);
   *             memcpy(buffer, current->start() + currentOffset, bytesFromCurrent);
   *             bytesLeftToPeek -= bytesFromCurrent;
   *             buffer += bytesFromCurrent;
   *             current = current->fNext;
   *             currentOffset = 0;
   *         }
   *         return bytesToPeek;
   *     }
   * ```
   */
  public override fun peek(buff: Unit?, bytesToPeek: ULong): Int {
    TODO("Implement peek")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rewind() override {
   *         fCurrent = fBlockMemory->fHead;
   *         fOffset = 0;
   *         fCurrentOffset = 0;
   *         return true;
   *     }
   * ```
   */
  public override fun rewind(): Boolean {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlockMemoryStream* onDuplicate() const override {
   *         return new SkBlockMemoryStream(fBlockMemory, fSize);
   *     }
   * ```
   */
  public override fun onDuplicate(): SkBlockMemoryStream {
    TODO("Implement onDuplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getPosition() const override {
   *         return fOffset;
   *     }
   * ```
   */
  public override fun getPosition(): Int {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * bool seek(size_t position) override {
   *         // If possible, skip forward.
   *         if (position >= fOffset) {
   *             size_t skipAmount = position - fOffset;
   *             return this->skip(skipAmount) == skipAmount;
   *         }
   *         // If possible, move backward within the current block.
   *         size_t moveBackAmount = fOffset - position;
   *         if (moveBackAmount <= fCurrentOffset) {
   *             fCurrentOffset -= moveBackAmount;
   *             fOffset -= moveBackAmount;
   *             return true;
   *         }
   *         // Otherwise rewind and move forward.
   *         return this->rewind() && this->skip(position) == position;
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
   *         return seek(fOffset + offset);
   *     }
   * ```
   */
  public override fun move(offset: Long): Boolean {
    TODO("Implement move")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlockMemoryStream* onFork() const override {
   *         SkBlockMemoryStream* that = this->onDuplicate();
   *         that->fCurrent = this->fCurrent;
   *         that->fOffset = this->fOffset;
   *         that->fCurrentOffset = this->fCurrentOffset;
   *         return that;
   *     }
   * ```
   */
  public override fun onFork(): SkBlockMemoryStream {
    TODO("Implement onFork")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getLength() const override {
   *         return fSize;
   *     }
   * ```
   */
  public override fun getLength(): Int {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* getMemoryBase() override {
   *         if (fBlockMemory->fHead && !fBlockMemory->fHead->fNext) {
   *             return fBlockMemory->fHead->start();
   *         }
   *         return nullptr;
   *     }
   * ```
   */
  public override fun getMemoryBase() {
    TODO("Implement getMemoryBase")
  }
}
