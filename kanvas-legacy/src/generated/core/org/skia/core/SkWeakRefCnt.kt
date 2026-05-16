package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlinx.atomicfu.AtomicInt
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SK_API SkWeakRefCnt : public SkRefCnt {
 * public:
 *     /** Default construct, initializing the reference counts to 1.
 *         The strong references collectively hold one weak reference. When the
 *         strong reference count goes to zero, the collectively held weak
 *         reference is released.
 *     */
 *     SkWeakRefCnt() : SkRefCnt(), fWeakCnt(1) {}
 *
 *     /** Destruct, asserting that the weak reference count is 1.
 *     */
 *     ~SkWeakRefCnt() override {
 * #ifdef SK_DEBUG
 *         SkASSERT(getWeakCnt() == 1);
 *         fWeakCnt.store(0, std::memory_order_relaxed);
 * #endif
 *     }
 *
 * #ifdef SK_DEBUG
 *     /** Return the weak reference count. */
 *     int32_t getWeakCnt() const {
 *         return fWeakCnt.load(std::memory_order_relaxed);
 *     }
 * #endif
 *
 * private:
 *     /** If fRefCnt is 0, returns 0.
 *      *  Otherwise increments fRefCnt, acquires, and returns the old value.
 *      */
 *     int32_t atomic_conditional_acquire_strong_ref() const {
 *         int32_t prev = fRefCnt.load(std::memory_order_relaxed);
 *         do {
 *             if (0 == prev) {
 *                 break;
 *             }
 *         } while(!fRefCnt.compare_exchange_weak(prev, prev+1, std::memory_order_acquire,
 *                                                              std::memory_order_relaxed));
 *         return prev;
 *     }
 *
 * public:
 *     /** Creates a strong reference from a weak reference, if possible. The
 *         caller must already be an owner. If try_ref() returns true the owner
 *         is in posession of an additional strong reference. Both the original
 *         reference and new reference must be properly unreferenced. If try_ref()
 *         returns false, no strong reference could be created and the owner's
 *         reference is in the same state as before the call.
 *     */
 *     [[nodiscard]] bool try_ref() const {
 *         if (atomic_conditional_acquire_strong_ref() != 0) {
 *             // Acquire barrier (L/SL), if not provided above.
 *             // Prevents subsequent code from happening before the increment.
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /** Increment the weak reference count. Must be balanced by a call to
 *         weak_unref().
 *     */
 *     void weak_ref() const {
 *         SkASSERT(getRefCnt() > 0);
 *         SkASSERT(getWeakCnt() > 0);
 *         // No barrier required.
 *         (void)fWeakCnt.fetch_add(+1, std::memory_order_relaxed);
 *     }
 *
 *     /** Decrement the weak reference count. If the weak reference count is 1
 *         before the decrement, then call delete on the object. Note that if this
 *         is the case, then the object needs to have been allocated via new, and
 *         not on the stack.
 *     */
 *     void weak_unref() const {
 *         SkASSERT(getWeakCnt() > 0);
 *         // A release here acts in place of all releases we "should" have been doing in ref().
 *         if (1 == fWeakCnt.fetch_add(-1, std::memory_order_acq_rel)) {
 *             // Like try_ref(), the acquire is only needed on success, to make sure
 *             // code in internal_dispose() doesn't happen before the decrement.
 * #ifdef SK_DEBUG
 *             // so our destructor won't complain
 *             fWeakCnt.store(1, std::memory_order_relaxed);
 * #endif
 *             this->INHERITED::internal_dispose();
 *         }
 *     }
 *
 *     /** Returns true if there are no strong references to the object. When this
 *         is the case all future calls to try_ref() will return false.
 *     */
 *     bool weak_expired() const {
 *         return fRefCnt.load(std::memory_order_relaxed) == 0;
 *     }
 *
 * protected:
 *     /** Called when the strong reference count goes to zero. This allows the
 *         object to free any resources it may be holding. Weak references may
 *         still exist and their level of allowed access to the object is defined
 *         by the object's class.
 *     */
 *     virtual void weak_dispose() const {
 *     }
 *
 * private:
 *     /** Called when the strong reference count goes to zero. Calls weak_dispose
 *         on the object and releases the implicit weak reference held
 *         collectively by the strong references.
 *     */
 *     void internal_dispose() const override {
 *         weak_dispose();
 *         weak_unref();
 *     }
 *
 *     /* Invariant: fWeakCnt = #weak + (fRefCnt > 0 ? 1 : 0) */
 *     mutable std::atomic<int32_t> fWeakCnt;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkWeakRefCnt public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<int32_t> fWeakCnt
   * ```
   */
  private val fWeakCnt: AtomicInt = TODO("Initialize fWeakCnt")

  /**
   * C++ original:
   * ```cpp
   * int32_t atomic_conditional_acquire_strong_ref() const {
   *         int32_t prev = fRefCnt.load(std::memory_order_relaxed);
   *         do {
   *             if (0 == prev) {
   *                 break;
   *             }
   *         } while(!fRefCnt.compare_exchange_weak(prev, prev+1, std::memory_order_acquire,
   *                                                              std::memory_order_relaxed));
   *         return prev;
   *     }
   * ```
   */
  private fun atomicConditionalAcquireStrongRef(): Int {
    TODO("Implement atomicConditionalAcquireStrongRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool try_ref() const {
   *         if (atomic_conditional_acquire_strong_ref() != 0) {
   *             // Acquire barrier (L/SL), if not provided above.
   *             // Prevents subsequent code from happening before the increment.
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun tryRef(): Boolean {
    TODO("Implement tryRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void weak_ref() const {
   *         SkASSERT(getRefCnt() > 0);
   *         SkASSERT(getWeakCnt() > 0);
   *         // No barrier required.
   *         (void)fWeakCnt.fetch_add(+1, std::memory_order_relaxed);
   *     }
   * ```
   */
  public fun weakRef() {
    TODO("Implement weakRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void weak_unref() const {
   *         SkASSERT(getWeakCnt() > 0);
   *         // A release here acts in place of all releases we "should" have been doing in ref().
   *         if (1 == fWeakCnt.fetch_add(-1, std::memory_order_acq_rel)) {
   *             // Like try_ref(), the acquire is only needed on success, to make sure
   *             // code in internal_dispose() doesn't happen before the decrement.
   * #ifdef SK_DEBUG
   *             // so our destructor won't complain
   *             fWeakCnt.store(1, std::memory_order_relaxed);
   * #endif
   *             this->INHERITED::internal_dispose();
   *         }
   *     }
   * ```
   */
  public fun weakUnref() {
    TODO("Implement weakUnref")
  }

  /**
   * C++ original:
   * ```cpp
   * bool weak_expired() const {
   *         return fRefCnt.load(std::memory_order_relaxed) == 0;
   *     }
   * ```
   */
  public fun weakExpired(): Boolean {
    TODO("Implement weakExpired")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void weak_dispose() const {
   *     }
   * ```
   */
  protected open fun weakDispose() {
    TODO("Implement weakDispose")
  }

  /**
   * C++ original:
   * ```cpp
   * void internal_dispose() const override {
   *         weak_dispose();
   *         weak_unref();
   *     }
   * ```
   */
  public override fun internalDispose() {
    TODO("Implement internalDispose")
  }
}
