package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkData
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMask
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoPixmapStorage : public SkPixmap {
 * public:
 *     SkAutoPixmapStorage();
 *     ~SkAutoPixmapStorage();
 *
 *     SkAutoPixmapStorage(SkAutoPixmapStorage&& other);
 *
 *     /**
 *     * Leave the moved-from object in a free-but-valid state.
 *     */
 *     SkAutoPixmapStorage& operator=(SkAutoPixmapStorage&& other);
 *
 *     /**
 *     *  Try to allocate memory for the pixels needed to match the specified Info. On success
 *     *  return true and fill out the pixmap to point to that memory. The storage will be freed
 *     *  when this object is destroyed, or if another call to tryAlloc() or alloc() is made.
 *     *
 *     *  On failure, return false and reset() the pixmap to empty.
 *     */
 *     bool tryAlloc(const SkImageInfo&);
 *
 *     /**
 *     *  Allocate memory for the pixels needed to match the specified Info and fill out the pixmap
 *     *  to point to that memory. The storage will be freed when this object is destroyed,
 *     *  or if another call to tryAlloc() or alloc() is made.
 *     *
 *     *  If the memory cannot be allocated, calls SK_ABORT().
 *     */
 *     void alloc(const SkImageInfo&);
 *
 *     /**
 *     * Gets the size and optionally the rowBytes that would be allocated by SkAutoPixmapStorage if
 *     * alloc/tryAlloc was called.
 *     */
 *     static size_t AllocSize(const SkImageInfo& info, size_t* rowBytes);
 *
 *     /**
 *     * Returns a void* of the allocated pixel memory and resets the pixmap. If the storage hasn't
 *     * been allocated, the result is NULL. The caller is responsible for calling sk_free to free
 *     * the returned memory.
 *     */
 *     [[nodiscard]] void* detachPixels();
 *
 *     /**
 *     *  Returns an SkData object wrapping the allocated pixels memory, and resets the pixmap.
 *     *  If the storage hasn't been allocated, the result is NULL.
 *     */
 *     [[nodiscard]] sk_sp<SkData> detachPixelsAsData();
 *
 *     // We wrap these so we can clear our internal storage
 *
 *     void reset() {
 *         this->freeStorage();
 *         this->INHERITED::reset();
 *     }
 *     void reset(const SkImageInfo& info, const void* addr, size_t rb) {
 *         this->freeStorage();
 *         this->INHERITED::reset(info, addr, rb);
 *     }
 *
 *     [[nodiscard]] bool reset(const SkMask& mask) {
 *         this->freeStorage();
 *         return this->INHERITED::reset(mask);
 *     }
 *
 * private:
 *     void*   fStorage;
 *
 *     void freeStorage() {
 *         sk_free(fStorage);
 *         fStorage = nullptr;
 *     }
 *
 *     using INHERITED = SkPixmap;
 * }
 * ```
 */
public open class SkAutoPixmapStorage public constructor() : SkPixmap() {
  /**
   * C++ original:
   * ```cpp
   * void*   fStorage
   * ```
   */
  private var fStorage: Unit? = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * SkAutoPixmapStorage::SkAutoPixmapStorage() : fStorage(nullptr) {}
   * ```
   */
  public constructor(other: SkAutoPixmapStorage) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAutoPixmapStorage& SkAutoPixmapStorage::operator=(SkAutoPixmapStorage&& other) {
   *     this->fStorage = other.fStorage;
   *     this->INHERITED::reset(other.info(), this->fStorage, other.rowBytes());
   *
   *     other.fStorage = nullptr;
   *     other.INHERITED::reset();
   *
   *     return *this;
   * }
   * ```
   */
  public fun assign(other: SkAutoPixmapStorage) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkAutoPixmapStorage::tryAlloc(const SkImageInfo& info) {
   *     this->freeStorage();
   *
   *     size_t rb;
   *     size_t size = AllocSize(info, &rb);
   *     if (SkImageInfo::ByteSizeOverflowed(size)) {
   *         return false;
   *     }
   *     void* pixels = sk_malloc_canfail(size);
   *     if (nullptr == pixels) {
   *         return false;
   *     }
   *     this->reset(info, pixels, rb);
   *     fStorage = pixels;
   *     return true;
   * }
   * ```
   */
  public fun tryAlloc(info: SkImageInfo): Boolean {
    TODO("Implement tryAlloc")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAutoPixmapStorage::alloc(const SkImageInfo& info) {
   *     SkASSERT_RELEASE(this->tryAlloc(info));
   * }
   * ```
   */
  public fun alloc(info: SkImageInfo) {
    TODO("Implement alloc")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkAutoPixmapStorage::detachPixels() {
   *     if (!fStorage) {
   *         return nullptr;
   *     }
   *
   *     void* data = fStorage;
   *     fStorage = nullptr;
   *     this->INHERITED::reset();
   *
   *     return data;
   * }
   * ```
   */
  public fun detachPixels() {
    TODO("Implement detachPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkAutoPixmapStorage::detachPixelsAsData() {
   *     if (!fStorage) {
   *         return nullptr;
   *     }
   *
   *     sk_sp<SkData> data = SkData::MakeFromMalloc(fStorage, this->computeByteSize());
   *     fStorage = nullptr;
   *     this->INHERITED::reset();
   *
   *     return data;
   * }
   * ```
   */
  public fun detachPixelsAsData(): SkSp<SkData> {
    TODO("Implement detachPixelsAsData")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         this->freeStorage();
   *         this->INHERITED::reset();
   *     }
   * ```
   */
  public override fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset(const SkImageInfo& info, const void* addr, size_t rb) {
   *         this->freeStorage();
   *         this->INHERITED::reset(info, addr, rb);
   *     }
   * ```
   */
  public fun reset(
    info: SkImageInfo,
    addr: Unit?,
    rb: ULong,
  ) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool reset(const SkMask& mask) {
   *         this->freeStorage();
   *         return this->INHERITED::reset(mask);
   *     }
   * ```
   */
  public override fun reset(mask: SkMask): Boolean {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeStorage() {
   *         sk_free(fStorage);
   *         fStorage = nullptr;
   *     }
   * ```
   */
  private fun freeStorage() {
    TODO("Implement freeStorage")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * size_t SkAutoPixmapStorage::AllocSize(const SkImageInfo& info, size_t* rowBytes) {
     *     size_t rb = info.minRowBytes();
     *     if (rowBytes) {
     *         *rowBytes = rb;
     *     }
     *     return info.computeByteSize(rb);
     * }
     * ```
     */
    public fun allocSize(info: SkImageInfo, rowBytes: ULong?): Int {
      TODO("Implement allocSize")
    }
  }
}
