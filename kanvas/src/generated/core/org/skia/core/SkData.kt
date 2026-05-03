package org.skia.core

import FILE
import kotlin.Any
import kotlin.Boolean
import kotlin.Byte
import kotlin.CharArray
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SK_API SkData final : public SkNVRefCnt<SkData> {
 * public:
 *     /**
 *      *  Returns true if this and rhs are the same size, and contain the same contents.
 *      *  All empty objects compare as equal.
 *      */
 *     bool operator==(const SkData& rhs) const;
 *     bool operator!=(const SkData& rhs) const { return !(*this == rhs); }
 *
 *     /**
 *      * Calls == operator, but first checks if other is null (in which case it returns false)
 *      */
 *     bool equals(const SkData* other) const {
 *         return (other != nullptr) && *this == *other;
 *     }
 *
 *     /**
 *      * Return true if both arguments are the same size and contain the same bytes, or if both
 *      * arguments are null.
 *      */
 *     static bool Equals(const SkData* a, const SkData* b) {
 *         return (a == nullptr) ? (b == nullptr) : a->equals(b);
 *     }
 *
 *     /**
 *      *  Returns the number of bytes stored.
 *      */
 *     size_t size() const { return fSpan.size(); }
 *
 *     /**
 *      *  Returns the ptr to the data.
 *      */
 *     const void* data() const { return fSpan.data(); }
 *
 *     bool empty() const { return fSpan.empty(); }
 *
 *     const uint8_t* bytes() const { return reinterpret_cast<const uint8_t*>(this->data()); }
 *
 *     SkSpan<const uint8_t> byteSpan() const { return {this->bytes(), this->size()}; }
 *
 *     /**
 *      *  USE WITH CAUTION.
 *      *  Be sure other 'owners' of this object are not accessing it in aother thread.
 *      */
 *     void* writable_data() {
 *         return fSpan.data();
 *     }
 *
 *     /** Attempt to create a deep copy of the original data, using the default allocator.
 *      *
 *      *  If  offset+length > this->size(), then this returns nullptr.
 *      */
 *     sk_sp<SkData> copySubset(size_t offset, size_t length) const;
 *
 *     /** Attempt to return a data that is a reference to a subset of the original data,
 *      *  This will never make a deep copy of the contents, but will retain a reference
 *      *  to the original data object.
 *      *
 *      *  If  offset+length > this->size(), then this returns nullptr.
 *      */
 *     sk_sp<SkData> shareSubset(size_t offset, size_t length);
 *     sk_sp<const SkData> shareSubset(size_t offset, size_t length) const;
 *
 *     /**
 *      *  Helper to copy a range of the data into a caller-provided buffer.
 *      *  Returns the actual number of bytes copied, after clamping offset and
 *      *  length to the size of this data. If buffer is NULL, it is ignored, and
 *      *  only the computed number of bytes is returned.
 *      */
 *     size_t copyRange(size_t offset, size_t length, void* buffer) const;
 *
 *     /**
 *      *  Function that, if provided, will be called when the SkData goes out
 *      *  of scope, allowing for custom allocation/freeing of the data's contents.
 *      */
 *     typedef void (*ReleaseProc)(const void* ptr, void* context);
 *
 *     /**
 *      *  Create a new dataref by copying the specified data
 *      */
 *     static sk_sp<SkData> MakeWithCopy(const void* data, size_t length);
 *
 *
 *     /**
 *      *  Create a new data with uninitialized contents. The caller should call writable_data()
 *      *  to write into the buffer, but this must be done before another ref() is made.
 *      */
 *     static sk_sp<SkData> MakeUninitialized(size_t length);
 *
 *     /**
 *      *  Create a new data with zero-initialized contents. The caller should call writable_data()
 *      *  to write into the buffer, but this must be done before another ref() is made.
 *      */
 *     static sk_sp<SkData> MakeZeroInitialized(size_t length);
 *
 *     /**
 *      *  Create a new dataref by copying the specified c-string
 *      *  (a null-terminated array of bytes). The returned SkData will have size()
 *      *  equal to strlen(cstr) + 1. If cstr is NULL, it will be treated the same
 *      *  as "".
 *      */
 *     static sk_sp<SkData> MakeWithCString(const char cstr[]);
 *
 *     /**
 *      *  Create a new dataref, taking the ptr as is, and using the
 *      *  releaseproc to free it. The proc may be NULL.
 *      */
 *     static sk_sp<SkData> MakeWithProc(const void* ptr, size_t length, ReleaseProc proc, void* ctx);
 *
 *     /**
 *      *  Call this when the data parameter is already const and will outlive the lifetime of the
 *      *  SkData. Suitable for globals.
 *      */
 *     static sk_sp<SkData> MakeWithoutCopy(const void* data, size_t length) {
 *         return MakeWithProc(data, length, NoopReleaseProc, nullptr);
 *     }
 *
 *     /**
 *      *  Create a new dataref from a pointer allocated by malloc. The Data object
 *      *  takes ownership of that allocation, and will handling calling sk_free.
 *      */
 *     static sk_sp<SkData> MakeFromMalloc(const void* data, size_t length);
 *
 *     /**
 *      *  Create a new dataref the file with the specified path.
 *      *  If the file cannot be opened, this returns NULL.
 *      */
 *     static sk_sp<SkData> MakeFromFileName(const char path[]);
 *
 *     /**
 *      *  Create a new dataref from a stdio FILE.
 *      *  This does not take ownership of the FILE, nor close it.
 *      *  The caller is free to close the FILE at its convenience.
 *      *  The FILE must be open for reading only.
 *      *  Returns NULL on failure.
 *      */
 *     static sk_sp<SkData> MakeFromFILE(FILE* f);
 *
 *     /**
 *      *  Create a new dataref from a file descriptor.
 *      *  This does not take ownership of the file descriptor, nor close it.
 *      *  The caller is free to close the file descriptor at its convenience.
 *      *  The file descriptor must be open for reading only.
 *      *  Returns NULL on failure.
 *      */
 *     static sk_sp<SkData> MakeFromFD(int fd);
 *
 *     /**
 *      *  Attempt to read size bytes into a SkData. If the read succeeds, return the data,
 *      *  else return NULL. Either way the stream's cursor may have been changed as a result
 *      *  of calling read().
 *      */
 *     static sk_sp<SkData> MakeFromStream(SkStream*, size_t size);
 *
 *     /**
 *      *  DEPRECATED variant of src->shareSubset(offset, length)
 *      *
 *      *  This variant checks if shaerSubset() returned null (because offset or length were out-of-range)
 *      *  and returns an empty SkData, rather than returning null.
 *      */
 *     static sk_sp<SkData> MakeSubset(const SkData* src, size_t offset, size_t length) {
 *         if (sk_sp<SkData> dst = const_cast<SkData*>(src)->shareSubset(offset, length)) {
 *             return dst;
 *         }
 *         return SkData::MakeEmpty();
 *     }
 *
 *     /**
 *      *  Returns a new empty dataref (or a reference to a shared empty dataref).
 *      *  New or shared, the caller must see that unref() is eventually called.
 *      */
 *     static sk_sp<SkData> MakeEmpty();
 *
 *     /**
 *      *  DEPRECATED -- use empty()
 *      */
 *     bool isEmpty() const { return fSpan.empty(); }
 *
 * private:
 *     friend class SkNVRefCnt<SkData>;
 *     ReleaseProc         fReleaseProc;
 *     void*               fReleaseProcContext;
 *     SkSpan<std::byte>   fSpan;
 *
 *     SkData(SkSpan<std::byte>, ReleaseProc, void* context);
 *     explicit SkData(size_t size);   // inplace new/delete
 *     ~SkData();
 *
 *     // Ensure the unsized delete is called.
 *     void operator delete(void* p);
 *
 *     // shared internal factory
 *     static sk_sp<SkData> PrivateNewWithCopy(const void* srcOrNull, size_t length);
 *
 *     static void NoopReleaseProc(const void*, void*); // {}
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public class SkData public constructor(
  size: ULong,
) : SkNVRefCnt(),
    SkData {
  /**
   * C++ original:
   * ```cpp
   * ReleaseProc         fReleaseProc
   * ```
   */
  private var fReleaseProc: SkDataReleaseProc = TODO("Initialize fReleaseProc")

  /**
   * C++ original:
   * ```cpp
   * void*               fReleaseProcContext
   * ```
   */
  private var fReleaseProcContext: Unit? = TODO("Initialize fReleaseProcContext")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<std::byte>   fSpan
   * ```
   */
  private var fSpan: Int = TODO("Initialize fSpan")

  /**
   * C++ original:
   * ```cpp
   * SkData(SkSpan<std::byte>, ReleaseProc, void* context)
   * ```
   */
  private var skSpan: SkData = TODO("Initialize skSpan")

  /**
   * C++ original:
   * ```cpp
   * explicit SkData(size_t size)
   * ```
   */
  public constructor(
    span: SkSpan<Byte>,
    proc: SkDataReleaseProc,
    context: Unit?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkData::operator==(const SkData& other) const {
   *     if (this == &other) {
   *         return true;
   *     }
   *     return size() == other.size() && !sk_careful_memcmp(data(), other.data(), size());
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkData& rhs) const { return !(*this == rhs); }
   * ```
   */
  public override fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equals(const SkData* other) const {
   *         return (other != nullptr) && *this == *other;
   *     }
   * ```
   */
  public override fun `data`() {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fSpan.size(); }
   * ```
   */
  public override fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* data() const { return fSpan.data(); }
   * ```
   */
  public override fun bytes(): UByte {
    TODO("Implement bytes")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fSpan.empty(); }
   * ```
   */
  public override fun byteSpan(): Int {
    TODO("Implement byteSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* bytes() const { return reinterpret_cast<const uint8_t*>(this->data()); }
   * ```
   */
  public override fun writableData() {
    TODO("Implement writableData")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const uint8_t> byteSpan() const { return {this->bytes(), this->size()}; }
   * ```
   */
  public override fun copySubset(offset: ULong, length: ULong): Int {
    TODO("Implement copySubset")
  }

  /**
   * C++ original:
   * ```cpp
   * void* writable_data() {
   *         return fSpan.data();
   *     }
   * ```
   */
  public override fun shareSubset(offset: ULong, length: ULong): Int {
    TODO("Implement shareSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkData::copySubset(size_t offset, size_t length) const {
   *     VALIDATE_SUBSET(this->size(), offset, length);
   *
   *     return SkData::MakeWithCopy(this->bytes() + offset, length);
   * }
   * ```
   */
  public override fun copyRange(
    offset: ULong,
    length: ULong,
    buffer: Unit?,
  ): ULong {
    TODO("Implement copyRange")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> shareSubset(size_t offset, size_t length)
   * ```
   */
  public override fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> shareSubset(size_t offset, size_t length) const
   * ```
   */
  public override fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Equals(const SkData* a, const SkData* b) {
     *         return (a == nullptr) ? (b == nullptr) : a->equals(b);
     *     }
     * ```
     */
    public override fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeWithCopy(const void* src, size_t length) {
     *     SkASSERT(src);
     *     return PrivateNewWithCopy(src, length);
     * }
     * ```
     */
    public override fun makeWithCopy(`data`: Unit?, length: ULong): Int {
      TODO("Implement makeWithCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeUninitialized(size_t length) {
     *     return PrivateNewWithCopy(nullptr, length);
     * }
     * ```
     */
    public override fun makeUninitialized(length: ULong): Int {
      TODO("Implement makeUninitialized")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeZeroInitialized(size_t length) {
     *     auto data = MakeUninitialized(length);
     *     if (length != 0) {
     *         memset(data->writable_data(), 0, data->size());
     *     }
     *     return data;
     * }
     * ```
     */
    public override fun makeZeroInitialized(length: ULong): Int {
      TODO("Implement makeZeroInitialized")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeWithCString(const char cstr[]) {
     *     size_t size;
     *     if (nullptr == cstr) {
     *         cstr = "";
     *         size = 1;
     *     } else {
     *         size = strlen(cstr) + 1;
     *     }
     *     return MakeWithCopy(cstr, size);
     * }
     * ```
     */
    public override fun makeWithCString(cstr: CharArray): Int {
      TODO("Implement makeWithCString")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeWithProc(const void* data, size_t length, ReleaseProc proc, void* ctx) {
     *     std::byte* ptr = static_cast<std::byte*>(const_cast<void*>(data));
     *     return sk_sp<SkData>(new SkData({ptr, length}, proc, ctx));
     * }
     * ```
     */
    public override fun makeWithProc(
      ptr: Unit?,
      length: ULong,
      proc: SkDataReleaseProc,
      ctx: Unit?,
    ): Int {
      TODO("Implement makeWithProc")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkData> MakeWithoutCopy(const void* data, size_t length) {
     *         return MakeWithProc(data, length, NoopReleaseProc, nullptr);
     *     }
     * ```
     */
    public override fun makeWithoutCopy(`data`: Unit?, length: ULong): Int {
      TODO("Implement makeWithoutCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeFromMalloc(const void* data, size_t length) {
     *     std::byte* ptr = static_cast<std::byte*>(const_cast<void*>(data));
     *     return sk_sp<SkData>(new SkData({ptr, length}, sk_free_releaseproc, nullptr));
     * }
     * ```
     */
    public override fun makeFromMalloc(`data`: Unit?, length: ULong): Int {
      TODO("Implement makeFromMalloc")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeFromFileName(const char path[]) {
     *     FILE* f = path ? sk_fopen(path, kRead_SkFILE_Flag) : nullptr;
     *     if (nullptr == f) {
     *         return nullptr;
     *     }
     *     auto data = MakeFromFILE(f);
     *     sk_fclose(f);
     *     return data;
     * }
     * ```
     */
    public override fun makeFromFileName(path: CharArray): Int {
      TODO("Implement makeFromFileName")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeFromFILE(FILE* f) {
     *     size_t size;
     *     void* addr = sk_fmmap(f, &size);
     *     if (nullptr == addr) {
     *         return nullptr;
     *     }
     *
     *     return SkData::MakeWithProc(addr, size, sk_mmap_releaseproc, reinterpret_cast<void*>(size));
     * }
     * ```
     */
    public override fun makeFromFILE(f: FILE?): Int {
      TODO("Implement makeFromFILE")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeFromFD(int fd) {
     *     size_t size;
     *     void* addr = sk_fdmmap(fd, &size);
     *     if (nullptr == addr) {
     *         return nullptr;
     *     }
     *     return SkData::MakeWithProc(addr, size, sk_mmap_releaseproc, reinterpret_cast<void*>(size));
     * }
     * ```
     */
    public override fun makeFromFD(fd: Int): Int {
      TODO("Implement makeFromFD")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeFromStream(SkStream* stream, size_t size) {
     *     // reduce the chance of OOM by checking that the stream has enough bytes to read from before
     *     // allocating that potentially large buffer.
     *     if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
     *         return nullptr;
     *     }
     *     sk_sp<SkData> data(SkData::MakeUninitialized(size));
     *     if (stream->read(data->writable_data(), size) != size) {
     *         return nullptr;
     *     }
     *     return data;
     * }
     * ```
     */
    public override fun makeFromStream(stream: SkStream?, size: ULong): Int {
      TODO("Implement makeFromStream")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkData> MakeSubset(const SkData* src, size_t offset, size_t length) {
     *         if (sk_sp<SkData> dst = const_cast<SkData*>(src)->shareSubset(offset, length)) {
     *             return dst;
     *         }
     *         return SkData::MakeEmpty();
     *     }
     * ```
     */
    public override fun makeSubset(
      src: SkData?,
      offset: ULong,
      length: ULong,
    ): Int {
      TODO("Implement makeSubset")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::MakeEmpty() {
     *     static SkData* empty = new SkData({}, nullptr, nullptr);
     *     return sk_ref_sp(empty);
     * }
     * ```
     */
    public override fun makeEmpty(): Int {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkData::PrivateNewWithCopy(const void* srcOrNull, size_t length) {
     *     if (0 == length) {
     *         return SkData::MakeEmpty();
     *     }
     *
     *     const size_t actualLength = length + sizeof(SkData);
     *     SkASSERT_RELEASE(length < actualLength);  // Check for overflow.
     *
     *     void* storage = ::operator new (actualLength);
     *     sk_sp<SkData> data(new (storage) SkData(length));
     *     if (srcOrNull) {
     *         memcpy(data->writable_data(), srcOrNull, length);
     *     }
     *     return data;
     * }
     * ```
     */
    public override fun privateNewWithCopy(srcOrNull: Unit?, length: ULong): Int {
      TODO("Implement privateNewWithCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkData::NoopReleaseProc(const void*, void*) {}
     * ```
     */
    public override fun noopReleaseProc(param0: Unit?, param1: Unit?) {
      TODO("Implement noopReleaseProc")
    }
  }
}
