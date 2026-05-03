package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoSMalloc : SkNoncopyable {
 * public:
 *     /**
 *      *  Creates initially empty storage. get() returns a ptr, but it is to a zero-byte allocation.
 *      *  Must call reset(size) to return an allocated block.
 *      */
 *     SkAutoSMalloc() {
 *         fPtr = fStorage;
 *         fSize = kSize;
 *     }
 *
 *     /**
 *      *  Allocate a block of the specified size. If size <= kSizeRequested (or slightly more), then
 *      *  the allocation will come from the stack, otherwise it will be dynamically allocated.
 *      */
 *     explicit SkAutoSMalloc(size_t size) {
 *         fPtr = fStorage;
 *         fSize = kSize;
 *         this->reset(size);
 *     }
 *
 *     /**
 *      *  Free the allocated block (if any). If the block was small enough to have been allocated on
 *      *  the stack, then this does nothing.
 *      */
 *     ~SkAutoSMalloc() {
 *         if (fPtr != (void*)fStorage) {
 *             sk_free(fPtr);
 *         }
 *     }
 *
 *     /**
 *      *  Return the allocated block. May return non-null even if the block is of zero size. Since
 *      *  this may be on the stack or dynamically allocated, the caller must not call sk_free() on it,
 *      *  but must rely on SkAutoSMalloc to manage it.
 *      */
 *     void* get() const { return fPtr; }
 *
 *     /**
 *      *  Return a new block of the requested size, freeing (as necessary) any previously allocated
 *      *  block. As with the constructor, if size <= kSizeRequested (or slightly more) then the return
 *      *  block may be allocated locally, rather than from the heap.
 *      */
 *     void* reset(size_t size,
 *                 SkAutoMalloc::OnShrink shrink = SkAutoMalloc::kAlloc_OnShrink,
 *                 bool* didChangeAlloc = nullptr) {
 *         size = (size < kSize) ? kSize : size;
 *         bool alloc = size != fSize && (SkAutoMalloc::kAlloc_OnShrink == shrink || size > fSize);
 *         if (didChangeAlloc) {
 *             *didChangeAlloc = alloc;
 *         }
 *         if (alloc) {
 *             if (fPtr != (void*)fStorage) {
 *                 sk_free(fPtr);
 *             }
 *
 *             if (size == kSize) {
 *                 SkASSERT(fPtr != fStorage); // otherwise we lied when setting didChangeAlloc.
 *                 fPtr = fStorage;
 *             } else {
 *                 fPtr = sk_malloc_throw(size);
 *             }
 *
 *             fSize = size;
 *         }
 *         SkASSERT(fSize >= size && fSize >= kSize);
 *         SkASSERT((fPtr == fStorage) || fSize > kSize);
 *         return fPtr;
 *     }
 *
 * private:
 *     // Align up to 32 bits.
 *     static const size_t kSizeAlign4 = SkAlign4(kSizeRequested);
 * #if defined(SK_BUILD_FOR_GOOGLE3)
 *     // Stack frame size is limited for SK_BUILD_FOR_GOOGLE3. 4k is less than the actual max, but some functions
 *     // have multiple large stack allocations.
 *     static const size_t kMaxBytes = 4 * 1024;
 *     static const size_t kSize = kSizeRequested > kMaxBytes ? kMaxBytes : kSizeAlign4;
 * #else
 *     static const size_t kSize = kSizeAlign4;
 * #endif
 *
 *     void*       fPtr;
 *     size_t      fSize;  // can be larger than the requested size (see kReuse)
 *     uint32_t    fStorage[kSize >> 2];
 * }
 * ```
 */
public open class SkAutoSMallockSize public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * static const size_t kSizeAlign4 = SkAlign4(kSizeRequested)
   * ```
   */
  private var fPtr: Unit? = TODO("Initialize fPtr")

  /**
   * C++ original:
   * ```cpp
   * static const size_t kSize = kSizeAlign4
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * void*       fPtr
   * ```
   */
  private var fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * SkAutoSMalloc() {
   *         fPtr = fStorage;
   *         fSize = kSize;
   *     }
   * ```
   */
  public constructor(size: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void* get() const { return fPtr; }
   * ```
   */
  public fun `get`() {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void* reset(size_t size,
   *                 SkAutoMalloc::OnShrink shrink = SkAutoMalloc::kAlloc_OnShrink,
   *                 bool* didChangeAlloc = nullptr) {
   *         size = (size < kSize) ? kSize : size;
   *         bool alloc = size != fSize && (SkAutoMalloc::kAlloc_OnShrink == shrink || size > fSize);
   *         if (didChangeAlloc) {
   *             *didChangeAlloc = alloc;
   *         }
   *         if (alloc) {
   *             if (fPtr != (void*)fStorage) {
   *                 sk_free(fPtr);
   *             }
   *
   *             if (size == kSize) {
   *                 SkASSERT(fPtr != fStorage); // otherwise we lied when setting didChangeAlloc.
   *                 fPtr = fStorage;
   *             } else {
   *                 fPtr = sk_malloc_throw(size);
   *             }
   *
   *             fSize = size;
   *         }
   *         SkASSERT(fSize >= size && fSize >= kSize);
   *         SkASSERT((fPtr == fStorage) || fSize > kSize);
   *         return fPtr;
   *     }
   * ```
   */
  public fun reset(
    size: ULong,
    shrink: SkAutoMalloc.OnShrink = TODO(),
    didChangeAlloc: Boolean? = null,
  ) {
    TODO("Implement reset")
  }

  public companion object {
    private val kSizeAlign4: Int = TODO("Initialize kSizeAlign4")

    private val kSize: Int = TODO("Initialize kSize")
  }
}
