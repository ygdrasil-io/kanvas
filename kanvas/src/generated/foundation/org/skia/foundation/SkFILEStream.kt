package org.skia.foundation

import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.Long
import kotlin.ULong
import kotlin.Unit
import undefined.FILE

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFILEStream : public SkStreamAsset {
 * public:
 *     /** Initialize the stream by calling sk_fopen on the specified path.
 *      *  This internal stream will be closed in the destructor.
 *      */
 *     explicit SkFILEStream(const char path[] = nullptr);
 *
 *     /** Initialize the stream with an existing C FILE stream.
 *      *  The current position of the C FILE stream will be considered the
 *      *  beginning of the SkFILEStream and the current seek end of the FILE will be the end.
 *      *  The C FILE stream will be closed in the destructor.
 *      */
 *     explicit SkFILEStream(FILE* file);
 *
 *     /** Initialize the stream with an existing C FILE stream.
 *      *  The current position of the C FILE stream will be considered the
 *      *  beginning of the SkFILEStream and size bytes later will be the end.
 *      *  The C FILE stream will be closed in the destructor.
 *      */
 *     explicit SkFILEStream(FILE* file, size_t size);
 *
 *     ~SkFILEStream() override;
 *
 *     static std::unique_ptr<SkFILEStream> Make(const char path[]) {
 *         std::unique_ptr<SkFILEStream> stream(new SkFILEStream(path));
 *         return stream->isValid() ? std::move(stream) : nullptr;
 *     }
 *
 *     /** Returns true if the current path could be opened. */
 *     bool isValid() const { return fFILE != nullptr; }
 *
 *     /** Close this SkFILEStream. */
 *     void close();
 *
 *     size_t read(void* buffer, size_t size) override;
 *     bool isAtEnd() const override;
 *
 *     bool rewind() override;
 *     std::unique_ptr<SkStreamAsset> duplicate() const {
 *         return std::unique_ptr<SkStreamAsset>(this->onDuplicate());
 *     }
 *
 *     size_t getPosition() const override;
 *     bool seek(size_t position) override;
 *     bool move(long offset) override;
 *
 *     std::unique_ptr<SkStreamAsset> fork() const {
 *         return std::unique_ptr<SkStreamAsset>(this->onFork());
 *     }
 *
 *     size_t getLength() const override;
 *
 * private:
 *     explicit SkFILEStream(FILE*, size_t size, size_t start);
 *     explicit SkFILEStream(std::shared_ptr<FILE>, size_t end, size_t start);
 *     explicit SkFILEStream(std::shared_ptr<FILE>, size_t end, size_t start, size_t current);
 *
 *     SkStreamAsset* onDuplicate() const override;
 *     SkStreamAsset* onFork() const override;
 *
 *     std::shared_ptr<FILE> fFILE;
 *     // My own council will I keep on sizes and offsets.
 *     // These are seek positions in the underling FILE, not offsets into the stream.
 *     size_t fEnd;
 *     size_t fStart;
 *     size_t fCurrent;
 *
 *     using INHERITED = SkStreamAsset;
 * }
 * ```
 */
public open class SkFILEStream public constructor(
  path: CharArray = null,
) : SkStreamAsset() {
  /**
   * C++ original:
   * ```cpp
   * std::shared_ptr<FILE> fFILE
   * ```
   */
  private var fFILE: FILE = TODO("Initialize fFILE")

  /**
   * C++ original:
   * ```cpp
   * size_t fEnd
   * ```
   */
  private var fEnd: ULong = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * size_t fStart
   * ```
   */
  private var fStart: ULong = TODO("Initialize fStart")

  /**
   * C++ original:
   * ```cpp
   * size_t fCurrent
   * ```
   */
  private var fCurrent: ULong = TODO("Initialize fCurrent")

  /**
   * C++ original:
   * ```cpp
   * SkFILEStream::SkFILEStream(const char path[])
   *     : SkFILEStream(path ? sk_fopen(path, kRead_SkFILE_Flag) : nullptr)
   * { }
   * ```
   */
  public constructor(`file`: FILE?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkFILEStream(FILE* file)
   * ```
   */
  public constructor(`file`: FILE?, size: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkFILEStream(FILE* file, size_t size)
   * ```
   */
  public constructor(
    `file`: FILE,
    end: ULong,
    start: ULong,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkFILEStream(FILE*, size_t size, size_t start)
   * ```
   */
  public constructor(
    `file`: FILE,
    end: ULong,
    start: ULong,
    current: ULong,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * explicit SkFILEStream(std::shared_ptr<FILE>, size_t end, size_t start)
   * ```
   */
  public constructor(
    `file`: FILE?,
    size: ULong,
    start: ULong,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fFILE != nullptr; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFILEStream::close() {
   *     fFILE.reset();
   *     fEnd = 0;
   *     fStart = 0;
   *     fCurrent = 0;
   * }
   * ```
   */
  public fun close() {
    TODO("Implement close")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFILEStream::read(void* buffer, size_t size) {
   *     if (size > fEnd - fCurrent) {
   *         size = fEnd - fCurrent;
   *     }
   *     size_t bytesRead = size;
   *     if (buffer) {
   *         bytesRead = sk_qread(fFILE.get(), buffer, size, fCurrent);
   *     }
   *     if (bytesRead == SIZE_MAX) {
   *         return 0;
   *     }
   *     fCurrent += bytesRead;
   *     return bytesRead;
   * }
   * ```
   */
  public override fun read(buffer: Unit?, size: ULong): ULong {
    TODO("Implement read")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkFILEStream::isAtEnd() const {
   *     if (fCurrent == fEnd) {
   *         return true;
   *     }
   *     return fCurrent >= sk_fgetsize(fFILE.get());
   * }
   * ```
   */
  public override fun isAtEnd(): Boolean {
    TODO("Implement isAtEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkFILEStream::rewind() {
   *     fCurrent = fStart;
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
   * std::unique_ptr<SkStreamAsset> duplicate() const {
   *         return std::unique_ptr<SkStreamAsset>(this->onDuplicate());
   *     }
   * ```
   */
  public override fun duplicate(): Int {
    TODO("Implement duplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFILEStream::getPosition() const {
   *     SkASSERT(fCurrent >= fStart);
   *     return fCurrent - fStart;
   * }
   * ```
   */
  public override fun getPosition(): ULong {
    TODO("Implement getPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkFILEStream::seek(size_t position) {
   *     fCurrent = std::min(SkSafeMath::Add(position, fStart), fEnd);
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
   * bool SkFILEStream::move(long offset) {
   *     if (offset < 0) {
   *         if (offset == std::numeric_limits<long>::min() ||
   *             !SkTFitsIn<size_t>(-offset) ||
   *             (size_t) (-offset) >= this->getPosition())
   *         {
   *             fCurrent = fStart;
   *         } else {
   *             fCurrent += offset;
   *         }
   *     } else if (!SkTFitsIn<size_t>(offset)) {
   *         fCurrent = fEnd;
   *     } else {
   *         fCurrent = std::min(SkSafeMath::Add(fCurrent, (size_t) offset), fEnd);
   *     }
   *
   *     SkASSERT(fCurrent >= fStart && fCurrent <= fEnd);
   *     return true;
   * }
   * ```
   */
  public override fun move(offset: Long): Boolean {
    TODO("Implement move")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> fork() const {
   *         return std::unique_ptr<SkStreamAsset>(this->onFork());
   *     }
   * ```
   */
  public override fun fork(): Int {
    TODO("Implement fork")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkFILEStream::getLength() const {
   *     return fEnd - fStart;
   * }
   * ```
   */
  public override fun getLength(): ULong {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset* SkFILEStream::onDuplicate() const {
   *     return new SkFILEStream(fFILE, fEnd, fStart, fStart);
   * }
   * ```
   */
  public override fun onDuplicate(): SkStreamAsset {
    TODO("Implement onDuplicate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset* SkFILEStream::onFork() const {
   *     return new SkFILEStream(fFILE, fEnd, fStart, fCurrent);
   * }
   * ```
   */
  public override fun onFork(): SkStreamAsset {
    TODO("Implement onFork")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<SkFILEStream> Make(const char path[]) {
     *         std::unique_ptr<SkFILEStream> stream(new SkFILEStream(path));
     *         return stream->isValid() ? std::move(stream) : nullptr;
     *     }
     * ```
     */
    public fun make(path: CharArray): Int {
      TODO("Implement make")
    }
  }
}
