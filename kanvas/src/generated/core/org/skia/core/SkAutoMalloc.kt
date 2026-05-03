package org.skia.core

import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkNoncopyable

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoMalloc : SkNoncopyable {
 * public:
 *     explicit SkAutoMalloc(size_t size = 0)
 *         : fPtr(size ? sk_malloc_throw(size) : nullptr), fSize(size) {}
 *
 *     /**
 *      *  Passed to reset to specify what happens if the requested size is smaller
 *      *  than the current size (and the current block was dynamically allocated).
 *      */
 *     enum OnShrink {
 *         /**
 *          *  If the requested size is smaller than the current size, and the
 *          *  current block is dynamically allocated, free the old block and
 *          *  malloc a new block of the smaller size.
 *          */
 *         kAlloc_OnShrink,
 *
 *         /**
 *          *  If the requested size is smaller than the current size, and the
 *          *  current block is dynamically allocated, just return the old
 *          *  block.
 *          */
 *         kReuse_OnShrink
 *     };
 *
 *     /**
 *      *  Reallocates the block to a new size. The ptr may or may not change.
 *      */
 *     void* reset(size_t size = 0, OnShrink shrink = kAlloc_OnShrink) {
 *         if (size != fSize && (size > fSize || kReuse_OnShrink != shrink)) {
 *             fPtr.reset(size ? sk_malloc_throw(size) : nullptr);
 *             fSize = size;
 *         }
 *         return fPtr.get();
 *     }
 *
 *     /**
 *      *  Return the allocated block.
 *      */
 *     void* get() { return fPtr.get(); }
 *     const void* get() const { return fPtr.get(); }
 *
 *    /** Transfer ownership of the current ptr to the caller, setting the
 *        internal reference to null. Note the caller is reponsible for calling
 *        sk_free on the returned address.
 *     */
 *     void* release() {
 *         fSize = 0;
 *         return fPtr.release();
 *     }
 *
 * private:
 *     struct WrapFree {
 *         void operator()(void* p) { sk_free(p); }
 *     };
 *     std::unique_ptr<void, WrapFree> fPtr;
 *     size_t fSize;  // can be larger than the requested size (see kReuse)
 * }
 * ```
 */
public abstract class SkAutoMalloc public constructor(
  size: ULong = 0u,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<void, WrapFree> fPtr
   * ```
   */
  private var fPtr: Int = TODO("Initialize fPtr")

  /**
   * C++ original:
   * ```cpp
   * size_t fSize
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * void* reset(size_t size = 0, OnShrink shrink = kAlloc_OnShrink) {
   *         if (size != fSize && (size > fSize || kReuse_OnShrink != shrink)) {
   *             fPtr.reset(size ? sk_malloc_throw(size) : nullptr);
   *             fSize = size;
   *         }
   *         return fPtr.get();
   *     }
   * ```
   */
  public abstract fun reset(size: ULong = 0u, shrink: OnShrink = TODO())

  /**
   * C++ original:
   * ```cpp
   * void* get() { return fPtr.get(); }
   * ```
   */
  public fun `get`() {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* get() const { return fPtr.get(); }
   * ```
   */
  public fun release() {
    TODO("Implement release")
  }

  public open class WrapFree {
    public operator fun invoke(p: Unit?) {
      TODO("Implement invoke")
    }
  }

  public enum class OnShrink {
    kAlloc_OnShrink,
    kReuse_OnShrink,
  }
}
