package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlinx.atomicfu.AtomicInt

/**
 * C++ original:
 * ```cpp
 * template <typename Derived>
 * class SkNVRefCnt {
 * public:
 *     SkNVRefCnt() : fRefCnt(1) {}
 *     ~SkNVRefCnt() {
 *     #ifdef SK_DEBUG
 *         int rc = fRefCnt.load(std::memory_order_relaxed);
 *         SkASSERTF(rc == 1, "NVRefCnt was %d", rc);
 *     #endif
 *     }
 *
 *     // Implementation is pretty much the same as SkRefCntBase. All required barriers are the same:
 *     //   - unique() needs acquire when it returns true, and no barrier if it returns false;
 *     //   - ref() doesn't need any barrier;
 *     //   - unref() needs a release barrier, and an acquire if it's going to call delete.
 *
 *     bool unique() const { return 1 == fRefCnt.load(std::memory_order_acquire); }
 *     void ref() const { (void)fRefCnt.fetch_add(+1, std::memory_order_relaxed); }
 *     void unref() const {
 *         if (1 == fRefCnt.fetch_add(-1, std::memory_order_acq_rel)) {
 *             // restore the 1 for our destructor's assert
 *             SkDEBUGCODE(fRefCnt.store(1, std::memory_order_relaxed));
 *             delete (const Derived*)this;
 *         }
 *     }
 *     void  deref() const { this->unref(); }
 *
 *     // This must be used with caution. It is only valid to call this when 'threadIsolatedTestCnt'
 *     // refs are known to be isolated to the current thread. That is, it is known that there are at
 *     // least 'threadIsolatedTestCnt' refs for which no other thread may make a balancing unref()
 *     // call. Assuming the contract is followed, if this returns false then no other thread has
 *     // ownership of this. If it returns true then another thread *may* have ownership.
 *     bool refCntGreaterThan(int32_t threadIsolatedTestCnt) const {
 *         int cnt = fRefCnt.load(std::memory_order_acquire);
 *         // If this fails then the above contract has been violated.
 *         SkASSERT(cnt >= threadIsolatedTestCnt);
 *         return cnt > threadIsolatedTestCnt;
 *     }
 *
 * private:
 *     mutable std::atomic<int32_t> fRefCnt;
 *
 *     SkNVRefCnt(SkNVRefCnt&&) = delete;
 *     SkNVRefCnt(const SkNVRefCnt&) = delete;
 *     SkNVRefCnt& operator=(SkNVRefCnt&&) = delete;
 *     SkNVRefCnt& operator=(const SkNVRefCnt&) = delete;
 * }
 * ```
 */
public open class SkNVRefCnt<Derived> public constructor() {
  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<int32_t> fRefCnt
   * ```
   */
  private val fRefCnt: AtomicInt = TODO("Initialize fRefCnt")

  public constructor(param0: SkNVRefCnt<Derived>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unique() const { return 1 == fRefCnt.load(std::memory_order_acquire); }
   * ```
   */
  public fun unique(): Boolean {
    TODO("Implement unique")
  }

  /**
   * C++ original:
   * ```cpp
   * void ref() const { (void)fRefCnt.fetch_add(+1, std::memory_order_relaxed); }
   * ```
   */
  public fun ref() {
    TODO("Implement ref")
  }

  /**
   * C++ original:
   * ```cpp
   * void unref() const {
   *         if (1 == fRefCnt.fetch_add(-1, std::memory_order_acq_rel)) {
   *             // restore the 1 for our destructor's assert
   *             SkDEBUGCODE(fRefCnt.store(1, std::memory_order_relaxed));
   *             delete (const Derived*)this;
   *         }
   *     }
   * ```
   */
  public fun unref() {
    TODO("Implement unref")
  }

  /**
   * C++ original:
   * ```cpp
   * void  deref() const { this->unref(); }
   * ```
   */
  public fun deref() {
    TODO("Implement deref")
  }

  /**
   * C++ original:
   * ```cpp
   * bool refCntGreaterThan(int32_t threadIsolatedTestCnt) const {
   *         int cnt = fRefCnt.load(std::memory_order_acquire);
   *         // If this fails then the above contract has been violated.
   *         SkASSERT(cnt >= threadIsolatedTestCnt);
   *         return cnt > threadIsolatedTestCnt;
   *     }
   * ```
   */
  public fun refCntGreaterThan(threadIsolatedTestCnt: Int): Boolean {
    TODO("Implement refCntGreaterThan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkNVRefCnt& operator=(SkNVRefCnt&&) = delete
   * ```
   */
  private fun assign(param0: SkNVRefCnt<Derived>) {
    TODO("Implement assign")
  }
}
