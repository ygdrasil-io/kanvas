package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API SkDataTable : public SkRefCnt {
 * public:
 *     /**
 *      *  Returns true if the table is empty (i.e. has no entries).
 *      */
 *     bool isEmpty() const { return 0 == fCount; }
 *
 *     /**
 *      *  Return the number of entries in the table. 0 for an empty table
 *      */
 *     int count() const { return fCount; }
 *
 *     /**
 *      *  Return the size of the index'th entry in the table. The caller must
 *      *  ensure that index is valid for this table.
 *      */
 *     size_t atSize(int index) const;
 *
 *     /**
 *      *  Return a pointer to the data of the index'th entry in the table.
 *      *  The caller must ensure that index is valid for this table.
 *      *
 *      *  @param size If non-null, this returns the byte size of this entry. This
 *      *              will be the same value that atSize(index) would return.
 *      */
 *     const void* at(int index, size_t* size = nullptr) const;
 *
 *     template <typename T>
 *     const T* atT(int index, size_t* size = nullptr) const {
 *         return reinterpret_cast<const T*>(this->at(index, size));
 *     }
 *
 *     /**
 *      *  Returns the index'th entry as a c-string, and assumes that the trailing
 *      *  null byte had been copied into the table as well.
 *      */
 *     const char* atStr(int index) const {
 *         size_t size;
 *         const char* str = this->atT<const char>(index, &size);
 *         SkASSERT(strlen(str) + 1 == size);
 *         return str;
 *     }
 *
 *     typedef void (*FreeProc)(void* context);
 *
 *     static sk_sp<SkDataTable> MakeEmpty();
 *
 *     /**
 *      *  Return a new DataTable that contains a copy of the data stored in each
 *      *  "array".
 *      *
 *      *  @param ptrs array of points to each element to be copied into the table.
 *      *  @param sizes array of byte-lengths for each entry in the corresponding
 *      *               ptrs[] array.
 *      *  @param count the number of array elements in ptrs[] and sizes[] to copy.
 *      */
 *     static sk_sp<SkDataTable> MakeCopyArrays(const void * const * ptrs,
 *                                              const size_t sizes[], int count);
 *
 *     /**
 *      *  Return a new table that contains a copy of the data in array.
 *      *
 *      *  @param array contiguous array of data for all elements to be copied.
 *      *  @param elemSize byte-length for a given element.
 *      *  @param count the number of entries to be copied out of array. The number
 *      *               of bytes that will be copied is count * elemSize.
 *      */
 *     static sk_sp<SkDataTable> MakeCopyArray(const void* array, size_t elemSize, int count);
 *
 *     static sk_sp<SkDataTable> MakeArrayProc(const void* array, size_t elemSize, int count,
 *                                             FreeProc proc, void* context);
 *
 * private:
 *     struct Dir {
 *         const void* fPtr;
 *         uintptr_t   fSize;
 *     };
 *
 *     int         fCount;
 *     size_t      fElemSize;
 *     union {
 *         const Dir*  fDir;
 *         const char* fElems;
 *     } fU;
 *
 *     FreeProc    fFreeProc;
 *     void*       fFreeProcContext;
 *
 *     SkDataTable();
 *     SkDataTable(const void* array, size_t elemSize, int count,
 *                 FreeProc, void* context);
 *     SkDataTable(const Dir*, int count, FreeProc, void* context);
 *     ~SkDataTable() override;
 *
 *     friend class SkDataTableBuilder;    // access to Dir
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkDataTable public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int         fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * size_t      fElemSize
   * ```
   */
  private var fElemSize: ULong = TODO("Initialize fElemSize")

  /**
   * C++ original:
   * ```cpp
   * union {
   *         const Dir*  fDir;
   *         const char* fElems;
   *     } fU
   * ```
   */
  private var fU: Any = TODO("Initialize fU")

  /**
   * C++ original:
   * ```cpp
   * FreeProc    fFreeProc
   * ```
   */
  private var fFreeProc: SkDataTableFreeProc = TODO("Initialize fFreeProc")

  /**
   * C++ original:
   * ```cpp
   * void*       fFreeProcContext
   * ```
   */
  private var fFreeProcContext: Unit? = TODO("Initialize fFreeProcContext")

  private val fDir: Dir? = TODO("Initialize fDir")

  private val fElems: String? = TODO("Initialize fElems")

  /**
   * C++ original:
   * ```cpp
   * SkDataTable::SkDataTable() {
   *     fCount = 0;
   *     fElemSize = 0;   // 0 signals that we use fDir instead of fElems
   *     fU.fDir = nullptr;
   *     fFreeProc = nullptr;
   *     fFreeProcContext = nullptr;
   * }
   * ```
   */
  public constructor(
    array: Unit?,
    elemSize: ULong,
    count: Int,
    proc: SkDataTableFreeProc,
    context: Unit?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDataTable(const void* array, size_t elemSize, int count,
   *                 FreeProc, void* context)
   * ```
   */
  public constructor(
    dir: Dir?,
    count: Int,
    proc: SkDataTableFreeProc,
    context: Unit?,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return 0 == fCount; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCount; }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkDataTable::atSize(int index) const {
   *     SkASSERT((unsigned)index < (unsigned)fCount);
   *
   *     if (fElemSize) {
   *         return fElemSize;
   *     } else {
   *         return fU.fDir[index].fSize;
   *     }
   * }
   * ```
   */
  public fun atSize(index: Int): ULong {
    TODO("Implement atSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkDataTable::at(int index, size_t* size) const {
   *     SkASSERT((unsigned)index < (unsigned)fCount);
   *
   *     if (fElemSize) {
   *         if (size) {
   *             *size = fElemSize;
   *         }
   *         return fU.fElems + index * fElemSize;
   *     } else {
   *         if (size) {
   *             *size = fU.fDir[index].fSize;
   *         }
   *         return fU.fDir[index].fPtr;
   *     }
   * }
   * ```
   */
  public fun at(index: Int, size: ULong? = TODO()) {
    TODO("Implement at")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     const T* atT(int index, size_t* size = nullptr) const {
   *         return reinterpret_cast<const T*>(this->at(index, size));
   *     }
   * ```
   */
  public fun <T> atT(index: Int, size: ULong? = TODO()): T {
    TODO("Implement atT")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* atStr(int index) const {
   *         size_t size;
   *         const char* str = this->atT<const char>(index, &size);
   *         SkASSERT(strlen(str) + 1 == size);
   *         return str;
   *     }
   * ```
   */
  public fun atStr(index: Int): Char {
    TODO("Implement atStr")
  }

  public data class Dir public constructor(
    public val fPtr: Unit?,
    public var fSize: ULong,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkDataTable> SkDataTable::MakeEmpty() {
     *     static SkDataTable* singleton = new SkDataTable();
     *     return sk_ref_sp(singleton);
     * }
     * ```
     */
    public fun makeEmpty(): Int {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkDataTable> SkDataTable::MakeCopyArrays(const void * const * ptrs,
     *                                                const size_t sizes[], int count) {
     *     if (count <= 0) {
     *         return SkDataTable::MakeEmpty();
     *     }
     *
     *     size_t dataSize = 0;
     *     for (int i = 0; i < count; ++i) {
     *         dataSize += sizes[i];
     *     }
     *
     *     size_t bufferSize = count * sizeof(Dir) + dataSize;
     *     void* buffer = sk_malloc_throw(bufferSize);
     *
     *     Dir* dir = (Dir*)buffer;
     *     char* elem = (char*)(dir + count);
     *     for (int i = 0; i < count; ++i) {
     *         dir[i].fPtr = elem;
     *         dir[i].fSize = sizes[i];
     *         memcpy(elem, ptrs[i], sizes[i]);
     *         elem += sizes[i];
     *     }
     *
     *     return sk_sp<SkDataTable>(new SkDataTable(dir, count, malloc_freeproc, buffer));
     * }
     * ```
     */
    public fun makeCopyArrays(
      ptrs: Int?,
      sizes: Array<ULong>,
      count: Int,
    ): Int {
      TODO("Implement makeCopyArrays")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkDataTable> SkDataTable::MakeCopyArray(const void* array, size_t elemSize, int count) {
     *     if (count <= 0) {
     *         return SkDataTable::MakeEmpty();
     *     }
     *
     *     size_t bufferSize = elemSize * count;
     *     void* buffer = sk_malloc_throw(bufferSize);
     *     memcpy(buffer, array, bufferSize);
     *
     *     return sk_sp<SkDataTable>(new SkDataTable(buffer, elemSize, count, malloc_freeproc, buffer));
     * }
     * ```
     */
    public fun makeCopyArray(
      array: Unit?,
      elemSize: ULong,
      count: Int,
    ): Int {
      TODO("Implement makeCopyArray")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkDataTable> SkDataTable::MakeArrayProc(const void* array, size_t elemSize, int count,
     *                                               FreeProc proc, void* ctx) {
     *     if (count <= 0) {
     *         return SkDataTable::MakeEmpty();
     *     }
     *     return sk_sp<SkDataTable>(new SkDataTable(array, elemSize, count, proc, ctx));
     * }
     * ```
     */
    public fun makeArrayProc(
      array: Unit?,
      elemSize: ULong,
      count: Int,
      proc: SkDataTableFreeProc,
      context: Unit?,
    ): Int {
      TODO("Implement makeArrayProc")
    }
  }
}
