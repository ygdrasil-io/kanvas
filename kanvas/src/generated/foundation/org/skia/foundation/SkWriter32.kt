package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkData
import org.skia.core.SkNoncopyable
import org.skia.core.SkPath
import org.skia.core.SkRRect
import org.skia.core.SkRegion
import org.skia.core.SkSamplingOptions
import org.skia.core.SkStream
import org.skia.core.SkWStream
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkWriter32 : SkNoncopyable {
 * public:
 *     /**
 *      *  The caller can specify an initial block of storage, which the caller manages.
 *      *
 *      *  SkWriter32 will try to back reserve and write calls with this external storage until the
 *      *  first time an allocation doesn't fit.  From then it will use dynamically allocated storage.
 *      *  This used to be optional behavior, but pipe now relies on it.
 *      */
 *     SkWriter32(void* external = nullptr, size_t externalBytes = 0) {
 *         this->reset(external, externalBytes);
 *     }
 *
 *     // return the current offset (will always be a multiple of 4)
 *     size_t bytesWritten() const { return fUsed; }
 *
 *     // Returns true iff all of the bytes written so far are stored in the initial storage
 *     // buffer provided in the constructor or the most recent call to reset.
 *     bool usingInitialStorage() const { return fData == fExternal; }
 *
 *     void reset(void* external = nullptr, size_t externalBytes = 0) {
 *         // we cast this pointer to int* and float* at times, so assert that it is aligned.
 *         SkASSERT(SkIsAlign4((uintptr_t)external));
 *         // we always write multiples of 4-bytes, so truncate down the size to match that
 *         externalBytes &= ~3;
 *
 *         fData = (uint8_t*)external;
 *         fCapacity = externalBytes;
 *         fUsed = 0;
 *         fExternal = external;
 *     }
 *
 *     // size MUST be multiple of 4
 *     uint32_t* reserve(size_t size) {
 *         SkASSERT(SkAlign4(size) == size);
 *         size_t offset = fUsed;
 *         size_t totalRequired = fUsed + size;
 *         if (totalRequired > fCapacity) {
 *             this->growToAtLeast(totalRequired);
 *         }
 *         fUsed = totalRequired;
 *         return (uint32_t*)(fData + offset);
 *     }
 *
 *     /**
 *      *  Read a T record at offset, which must be a multiple of 4. Only legal if the record
 *      *  was written atomically using the write methods below.
 *      */
 *     template<typename T>
 *     const T& readTAt(size_t offset) const {
 *         SkASSERT(SkAlign4(offset) == offset);
 *         SkASSERT(offset < fUsed);
 *         return *(T*)(fData + offset);
 *     }
 *
 *     /**
 *      *  Overwrite a T record at offset, which must be a multiple of 4. Only legal if the record
 *      *  was written atomically using the write methods below.
 *      */
 *     template<typename T>
 *     void overwriteTAt(size_t offset, const T& value) {
 *         SkASSERT(SkAlign4(offset) == offset);
 *         SkASSERT(offset < fUsed);
 *         *(T*)(fData + offset) = value;
 *     }
 *
 *     bool writeBool(bool value) {
 *         this->write32(value);
 *         return value;
 *     }
 *
 *     void writeInt(int32_t value) {
 *         this->write32(value);
 *     }
 *
 *     void write8(int32_t value) {
 *         *(int32_t*)this->reserve(sizeof(value)) = value & 0xFF;
 *     }
 *
 *     void write16(int32_t value) {
 *         *(int32_t*)this->reserve(sizeof(value)) = value & 0xFFFF;
 *     }
 *
 *     void write32(int32_t value) {
 *         *(int32_t*)this->reserve(sizeof(value)) = value;
 *     }
 *
 *     void writeScalar(SkScalar value) {
 *         *(SkScalar*)this->reserve(sizeof(value)) = value;
 *     }
 *
 *     void writePoint(const SkPoint& pt) {
 *         *(SkPoint*)this->reserve(sizeof(pt)) = pt;
 *     }
 *
 *     void writePoint3(const SkPoint3& pt) {
 *         *(SkPoint3*)this->reserve(sizeof(pt)) = pt;
 *     }
 *
 *     void writeRect(const SkRect& rect) {
 *         *(SkRect*)this->reserve(sizeof(rect)) = rect;
 *     }
 *
 *     void writeIRect(const SkIRect& rect) {
 *         *(SkIRect*)this->reserve(sizeof(rect)) = rect;
 *     }
 *
 *     void writeRRect(const SkRRect& rrect) {
 *         rrect.writeToMemory(this->reserve(SkRRect::kSizeInMemory));
 *     }
 *
 *     void writePath(const SkPath& path) {
 *         size_t size = path.writeToMemory(nullptr);
 *         SkASSERT(SkAlign4(size) == size);
 *         path.writeToMemory(this->reserve(size));
 *     }
 *
 *     void writeMatrix(const SkMatrix& matrix);
 *
 *     void writeRegion(const SkRegion& rgn) {
 *         size_t size = rgn.writeToMemory(nullptr);
 *         SkASSERT(SkAlign4(size) == size);
 *         rgn.writeToMemory(this->reserve(size));
 *     }
 *
 *     void writeSampling(const SkSamplingOptions& sampling);
 *
 *     // write count bytes (must be a multiple of 4)
 *     void writeMul4(const void* values, size_t size) {
 *         this->write(values, size);
 *     }
 *
 *     /**
 *      *  Write size bytes from values. size must be a multiple of 4, though
 *      *  values need not be 4-byte aligned.
 *      */
 *     void write(const void* values, size_t size) {
 *         SkASSERT(SkAlign4(size) == size);
 *         sk_careful_memcpy(this->reserve(size), values, size);
 *     }
 *
 *     /**
 *      *  Reserve size bytes. Does not need to be 4 byte aligned. The remaining space (if any) will be
 *      *  filled in with zeroes.
 *      */
 *     uint32_t* reservePad(size_t size) {
 *         size_t alignedSize = SkAlign4(size);
 *         uint32_t* p = this->reserve(alignedSize);
 *         if (alignedSize != size) {
 *             SkASSERT(alignedSize >= 4);
 *             p[alignedSize / 4 - 1] = 0;
 *         }
 *         return p;
 *     }
 *
 *     /**
 *      *  Write size bytes from src, and pad to 4 byte alignment with zeroes.
 *      */
 *     void writePad(const void* src, size_t size) {
 *         sk_careful_memcpy(this->reservePad(size), src, size);
 *     }
 *
 *     /**
 *      *  Writes a string to the writer, which can be retrieved with SkReadBuffer::readString().
 *      *  The length can be specified, or if -1 is passed, it will be computed by calling strlen().
 *      *  The length must be < max size_t.
 *      *
 *      *  If you write NULL, it will be read as "".
 *      */
 *     void writeString(const char* str, size_t len = (size_t)-1);
 *
 *     /**
 *      *  Computes the size (aligned to multiple of 4) need to write the string
 *      *  in a call to writeString(). If the length is not specified, it will be
 *      *  computed by calling strlen().
 *      */
 *     static size_t WriteStringSize(const char* str, size_t len = (size_t)-1);
 *
 *     void writeData(const SkData* data) {
 *         uint32_t len = data ? SkToU32(data->size()) : 0;
 *         this->write32(len);
 *         if (data) {
 *             this->writePad(data->data(), len);
 *         }
 *     }
 *
 *     static size_t WriteDataSize(const SkData* data) {
 *         return 4 + SkAlign4(data ? data->size() : 0);
 *     }
 *
 *     /**
 *      *  Move the cursor back to offset bytes from the beginning.
 *      *  offset must be a multiple of 4 no greater than size().
 *      */
 *     void rewindToOffset(size_t offset) {
 *         SkASSERT(SkAlign4(offset) == offset);
 *         SkASSERT(offset <= bytesWritten());
 *         fUsed = offset;
 *     }
 *
 *     // copy into a single buffer (allocated by caller). Must be at least size()
 *     void flatten(void* dst) const {
 *         memcpy(dst, fData, fUsed);
 *     }
 *
 *     bool writeToStream(SkWStream* stream) const {
 *         return stream->write(fData, fUsed);
 *     }
 *
 *     // read from the stream, and write up to length bytes. Return the actual
 *     // number of bytes written.
 *     size_t readFromStream(SkStream* stream, size_t length) {
 *         return stream->read(this->reservePad(length), length);
 *     }
 *
 *     /**
 *      *  Captures a snapshot of the data as it is right now, and return it.
 *      */
 *     sk_sp<SkData> snapshotAsData() const;
 * private:
 *     void growToAtLeast(size_t size);
 *
 *     uint8_t* fData;                    // Points to either fInternal or fExternal.
 *     size_t fCapacity;                  // Number of bytes we can write to fData.
 *     size_t fUsed;                      // Number of bytes written.
 *     void* fExternal;                   // Unmanaged memory block.
 *     skia_private::AutoTMalloc<uint8_t> fInternal;  // Managed memory block.
 * }
 * ```
 */
public abstract class SkWriter32 public constructor(
  `external`: Unit? = null,
  externalBytes: ULong = 0u,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * uint8_t* fData
   * ```
   */
  private var fData: Int? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * size_t fCapacity
   * ```
   */
  private var fCapacity: Int = TODO("Initialize fCapacity")

  /**
   * C++ original:
   * ```cpp
   * size_t fUsed
   * ```
   */
  private var fUsed: Int = TODO("Initialize fUsed")

  /**
   * C++ original:
   * ```cpp
   * void* fExternal
   * ```
   */
  private var fExternal: Unit? = TODO("Initialize fExternal")

  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoTMalloc<uint8_t> fInternal
   * ```
   */
  private var fInternal: Int = TODO("Initialize fInternal")

  /**
   * C++ original:
   * ```cpp
   * size_t bytesWritten() const { return fUsed; }
   * ```
   */
  public fun bytesWritten(): Int {
    TODO("Implement bytesWritten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usingInitialStorage() const { return fData == fExternal; }
   * ```
   */
  public fun usingInitialStorage(): Boolean {
    TODO("Implement usingInitialStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset(void* external = nullptr, size_t externalBytes = 0) {
   *         // we cast this pointer to int* and float* at times, so assert that it is aligned.
   *         SkASSERT(SkIsAlign4((uintptr_t)external));
   *         // we always write multiples of 4-bytes, so truncate down the size to match that
   *         externalBytes &= ~3;
   *
   *         fData = (uint8_t*)external;
   *         fCapacity = externalBytes;
   *         fUsed = 0;
   *         fExternal = external;
   *     }
   * ```
   */
  public abstract fun reset(`external`: Unit? = null, externalBytes: ULong = 0u)

  /**
   * C++ original:
   * ```cpp
   * uint32_t* reserve(size_t size) {
   *         SkASSERT(SkAlign4(size) == size);
   *         size_t offset = fUsed;
   *         size_t totalRequired = fUsed + size;
   *         if (totalRequired > fCapacity) {
   *             this->growToAtLeast(totalRequired);
   *         }
   *         fUsed = totalRequired;
   *         return (uint32_t*)(fData + offset);
   *     }
   * ```
   */
  public fun reserve(size: ULong): Int {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     const T& readTAt(size_t offset) const {
   *         SkASSERT(SkAlign4(offset) == offset);
   *         SkASSERT(offset < fUsed);
   *         return *(T*)(fData + offset);
   *     }
   * ```
   */
  public fun <T> readTAt(offset: ULong): T {
    TODO("Implement readTAt")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename T>
   *     void overwriteTAt(size_t offset, const T& value) {
   *         SkASSERT(SkAlign4(offset) == offset);
   *         SkASSERT(offset < fUsed);
   *         *(T*)(fData + offset) = value;
   *     }
   * ```
   */
  public fun <T> overwriteTAt(offset: ULong, `value`: T) {
    TODO("Implement overwriteTAt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool writeBool(bool value) {
   *         this->write32(value);
   *         return value;
   *     }
   * ```
   */
  public fun writeBool(`value`: Boolean): Boolean {
    TODO("Implement writeBool")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeInt(int32_t value) {
   *         this->write32(value);
   *     }
   * ```
   */
  public fun writeInt(`value`: Int) {
    TODO("Implement writeInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void write8(int32_t value) {
   *         *(int32_t*)this->reserve(sizeof(value)) = value & 0xFF;
   *     }
   * ```
   */
  public fun write8(`value`: Int) {
    TODO("Implement write8")
  }

  /**
   * C++ original:
   * ```cpp
   * void write16(int32_t value) {
   *         *(int32_t*)this->reserve(sizeof(value)) = value & 0xFFFF;
   *     }
   * ```
   */
  public fun write16(`value`: Int) {
    TODO("Implement write16")
  }

  /**
   * C++ original:
   * ```cpp
   * void write32(int32_t value) {
   *         *(int32_t*)this->reserve(sizeof(value)) = value;
   *     }
   * ```
   */
  public fun write32(`value`: Int) {
    TODO("Implement write32")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeScalar(SkScalar value) {
   *         *(SkScalar*)this->reserve(sizeof(value)) = value;
   *     }
   * ```
   */
  public fun writeScalar(`value`: SkScalar) {
    TODO("Implement writeScalar")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePoint(const SkPoint& pt) {
   *         *(SkPoint*)this->reserve(sizeof(pt)) = pt;
   *     }
   * ```
   */
  public fun writePoint(pt: SkPoint) {
    TODO("Implement writePoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePoint3(const SkPoint3& pt) {
   *         *(SkPoint3*)this->reserve(sizeof(pt)) = pt;
   *     }
   * ```
   */
  public fun writePoint3(pt: SkPoint3) {
    TODO("Implement writePoint3")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeRect(const SkRect& rect) {
   *         *(SkRect*)this->reserve(sizeof(rect)) = rect;
   *     }
   * ```
   */
  public fun writeRect(rect: SkRect) {
    TODO("Implement writeRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeIRect(const SkIRect& rect) {
   *         *(SkIRect*)this->reserve(sizeof(rect)) = rect;
   *     }
   * ```
   */
  public fun writeIRect(rect: SkIRect) {
    TODO("Implement writeIRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeRRect(const SkRRect& rrect) {
   *         rrect.writeToMemory(this->reserve(SkRRect::kSizeInMemory));
   *     }
   * ```
   */
  public fun writeRRect(rrect: SkRRect) {
    TODO("Implement writeRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePath(const SkPath& path) {
   *         size_t size = path.writeToMemory(nullptr);
   *         SkASSERT(SkAlign4(size) == size);
   *         path.writeToMemory(this->reserve(size));
   *     }
   * ```
   */
  public fun writePath(path: SkPath) {
    TODO("Implement writePath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWriter32::writeMatrix(const SkMatrix& matrix) {
   *     size_t size = SkMatrixPriv::WriteToMemory(matrix, nullptr);
   *     SkASSERT(SkAlign4(size) == size);
   *     SkMatrixPriv::WriteToMemory(matrix, this->reserve(size));
   * }
   * ```
   */
  public fun writeMatrix(matrix: SkMatrix) {
    TODO("Implement writeMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeRegion(const SkRegion& rgn) {
   *         size_t size = rgn.writeToMemory(nullptr);
   *         SkASSERT(SkAlign4(size) == size);
   *         rgn.writeToMemory(this->reserve(size));
   *     }
   * ```
   */
  public fun writeRegion(rgn: SkRegion) {
    TODO("Implement writeRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWriter32::writeSampling(const SkSamplingOptions& sampling) {
   *     this->write32(sampling.maxAniso);
   *     if (!sampling.isAniso()) {
   *         this->writeBool(sampling.useCubic);
   *         if (sampling.useCubic) {
   *             this->writeScalar(sampling.cubic.B);
   *             this->writeScalar(sampling.cubic.C);
   *         } else {
   *             this->write32((unsigned)sampling.filter);
   *             this->write32((unsigned)sampling.mipmap);
   *         }
   *     }
   * }
   * ```
   */
  public fun writeSampling(sampling: SkSamplingOptions) {
    TODO("Implement writeSampling")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeMul4(const void* values, size_t size) {
   *         this->write(values, size);
   *     }
   * ```
   */
  public fun writeMul4(values: Unit?, size: ULong) {
    TODO("Implement writeMul4")
  }

  /**
   * C++ original:
   * ```cpp
   * void write(const void* values, size_t size) {
   *         SkASSERT(SkAlign4(size) == size);
   *         sk_careful_memcpy(this->reserve(size), values, size);
   *     }
   * ```
   */
  public fun write(values: Unit?, size: ULong) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t* reservePad(size_t size) {
   *         size_t alignedSize = SkAlign4(size);
   *         uint32_t* p = this->reserve(alignedSize);
   *         if (alignedSize != size) {
   *             SkASSERT(alignedSize >= 4);
   *             p[alignedSize / 4 - 1] = 0;
   *         }
   *         return p;
   *     }
   * ```
   */
  public fun reservePad(size: ULong): Int {
    TODO("Implement reservePad")
  }

  /**
   * C++ original:
   * ```cpp
   * void writePad(const void* src, size_t size) {
   *         sk_careful_memcpy(this->reservePad(size), src, size);
   *     }
   * ```
   */
  public fun writePad(src: Unit?, size: ULong) {
    TODO("Implement writePad")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWriter32::writeString(const char str[], size_t len) {
   *     if (nullptr == str) {
   *         str = "";
   *         len = 0;
   *     }
   *     if ((long)len < 0) {
   *         len = strlen(str);
   *     }
   *
   *     // [ 4 byte len ] [ str ... ] [1 - 4 \0s]
   *     uint32_t* ptr = this->reservePad(sizeof(uint32_t) + len + 1);
   *     *ptr = SkToU32(len);
   *     char* chars = (char*)(ptr + 1);
   *     memcpy(chars, str, len);
   *     chars[len] = '\0';
   * }
   * ```
   */
  public fun writeString(str: String?, len: ULong = TODO()) {
    TODO("Implement writeString")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeData(const SkData* data) {
   *         uint32_t len = data ? SkToU32(data->size()) : 0;
   *         this->write32(len);
   *         if (data) {
   *             this->writePad(data->data(), len);
   *         }
   *     }
   * ```
   */
  public fun writeData(`data`: SkData?) {
    TODO("Implement writeData")
  }

  /**
   * C++ original:
   * ```cpp
   * void rewindToOffset(size_t offset) {
   *         SkASSERT(SkAlign4(offset) == offset);
   *         SkASSERT(offset <= bytesWritten());
   *         fUsed = offset;
   *     }
   * ```
   */
  public fun rewindToOffset(offset: ULong) {
    TODO("Implement rewindToOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(void* dst) const {
   *         memcpy(dst, fData, fUsed);
   *     }
   * ```
   */
  public fun flatten(dst: Unit?) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool writeToStream(SkWStream* stream) const {
   *         return stream->write(fData, fUsed);
   *     }
   * ```
   */
  public fun writeToStream(stream: SkWStream?): Boolean {
    TODO("Implement writeToStream")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t readFromStream(SkStream* stream, size_t length) {
   *         return stream->read(this->reservePad(length), length);
   *     }
   * ```
   */
  public fun readFromStream(stream: SkStream?, length: ULong): Int {
    TODO("Implement readFromStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkWriter32::snapshotAsData() const {
   *     return SkData::MakeWithCopy(fData, fUsed);
   * }
   * ```
   */
  public fun snapshotAsData(): SkSp<SkData> {
    TODO("Implement snapshotAsData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkWriter32::growToAtLeast(size_t size) {
   *     const bool wasExternal = (fExternal != nullptr) && (fData == fExternal);
   *
   *     fCapacity = 4096 + std::max(size, fCapacity + (fCapacity / 2));
   *     fInternal.realloc(fCapacity);
   *     fData = fInternal.get();
   *
   *     if (wasExternal) {
   *         // we were external, so copy in the data
   *         memcpy(fData, fExternal, fUsed);
   *     }
   * }
   * ```
   */
  private fun growToAtLeast(size: ULong) {
    TODO("Implement growToAtLeast")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * size_t SkWriter32::WriteStringSize(const char* str, size_t len) {
     *     if ((long)len < 0) {
     *         SkASSERT(str);
     *         len = strlen(str);
     *     }
     *     const size_t lenBytes = 4;    // we use 4 bytes to record the length
     *     // add 1 since we also write a terminating 0
     *     return SkAlign4(lenBytes + len + 1);
     * }
     * ```
     */
    public fun writeStringSize(str: String?, len: ULong = TODO()): Int {
      TODO("Implement writeStringSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t WriteDataSize(const SkData* data) {
     *         return 4 + SkAlign4(data ? data->size() : 0);
     *     }
     * ```
     */
    public fun writeDataSize(`data`: SkData?): Int {
      TODO("Implement writeDataSize")
    }
  }
}

public typealias SkSWriter32INHERITED = SkWriter32
