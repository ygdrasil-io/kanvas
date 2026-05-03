package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp
import undefined.SkDiscardableMemory
import undefined.UniqueVoidPtr

/**
 * C++ original:
 * ```cpp
 * class PoolDiscardableMemory : public SkDiscardableMemory {
 * public:
 *     PoolDiscardableMemory(sk_sp<DiscardableMemoryPool> pool, UniqueVoidPtr pointer, size_t bytes);
 *     ~PoolDiscardableMemory() override;
 *     bool lock() override;
 *     void* data() override;
 *     void unlock() override;
 *     friend class DiscardableMemoryPool;
 * private:
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(PoolDiscardableMemory);
 *     sk_sp<DiscardableMemoryPool> fPool;
 *     bool                         fLocked;
 *     UniqueVoidPtr                   fPointer;
 *     const size_t                 fBytes;
 * }
 * ```
 */
public open class PoolDiscardableMemory public constructor(
  pool: SkSp<DiscardableMemoryPool>,
  pointer: UniqueVoidPtr,
  bytes: ULong,
) : SkDiscardableMemory() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<DiscardableMemoryPool> fPool
   * ```
   */
  private var fPool: SkSp<DiscardableMemoryPool> = TODO("Initialize fPool")

  /**
   * C++ original:
   * ```cpp
   * bool                         fLocked
   * ```
   */
  private var fLocked: Boolean = TODO("Initialize fLocked")

  /**
   * C++ original:
   * ```cpp
   * UniqueVoidPtr                   fPointer
   * ```
   */
  private var fPointer: Int = TODO("Initialize fPointer")

  /**
   * C++ original:
   * ```cpp
   * const size_t                 fBytes
   * ```
   */
  private val fBytes: Int = TODO("Initialize fBytes")

  /**
   * C++ original:
   * ```cpp
   * bool PoolDiscardableMemory::lock() {
   *     SkASSERT(!fLocked); // contract for SkDiscardableMemory
   *     return fPool->lock(this);
   * }
   * ```
   */
  public override fun lock(): Boolean {
    TODO("Implement lock")
  }

  /**
   * C++ original:
   * ```cpp
   * void* PoolDiscardableMemory::data() {
   *     SkASSERT(fLocked); // contract for SkDiscardableMemory
   *     return fPointer.get();
   * }
   * ```
   */
  public override fun `data`() {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * void PoolDiscardableMemory::unlock() {
   *     SkASSERT(fLocked); // contract for SkDiscardableMemory
   *     fPool->unlock(this);
   * }
   * ```
   */
  public override fun unlock() {
    TODO("Implement unlock")
  }
}
