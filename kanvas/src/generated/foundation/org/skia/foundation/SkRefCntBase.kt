package org.skia.foundation

import kotlin.Boolean
import kotlinx.atomicfu.AtomicInt

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRefCntBase {
 * public:
 *     /** Default construct, initializing the reference count to 1.
 *     */
 *     SkRefCntBase() : fRefCnt(1) {}
 *
 *     /** Destruct, asserting that the reference count is 1.
 *     */
 *     virtual ~SkRefCntBase() {
 *     #ifdef SK_DEBUG
 *         SkASSERTF(this->getRefCnt() == 1, "fRefCnt was %d", this->getRefCnt());
 *         // illegal value, to catch us if we reuse after delete
 *         fRefCnt.store(0, std::memory_order_relaxed);
 *     #endif
 *     }
 *
 *     /** May return true if the caller is the only owner.
 *      *  Ensures that all previous owner's actions are complete.
 *      */
 *     bool unique() const {
 *         if (1 == fRefCnt.load(std::memory_order_acquire)) {
 *             // The acquire barrier is only really needed if we return true.  It
 *             // prevents code conditioned on the result of unique() from running
 *             // until previous owners are all totally done calling unref().
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     /** Increment the reference count. Must be balanced by a call to unref().
 *     */
 *     void ref() const {
 *         SkASSERT(this->getRefCnt() > 0);
 *         // No barrier required.
 *         (void)fRefCnt.fetch_add(+1, std::memory_order_relaxed);
 *     }
 *
 *     /** Decrement the reference count. If the reference count is 1 before the
 *         decrement, then delete the object. Note that if this is the case, then
 *         the object needs to have been allocated via new, and not on the stack.
 *     */
 *     void unref() const {
 *         SkASSERT(this->getRefCnt() > 0);
 *         // A release here acts in place of all releases we "should" have been doing in ref().
 *         if (1 == fRefCnt.fetch_add(-1, std::memory_order_acq_rel)) {
 *             // Like unique(), the acquire is only needed on success, to make sure
 *             // code in internal_dispose() doesn't happen before the decrement.
 *             this->internal_dispose();
 *         }
 *     }
 *
 * private:
 *
 * #ifdef SK_DEBUG
 *     /** Return the reference count. Use only for debugging. */
 *     int32_t getRefCnt() const {
 *         return fRefCnt.load(std::memory_order_relaxed);
 *     }
 * #endif
 *
 *     /**
 *      *  Called when the ref count goes to 0.
 *      */
 *     virtual void internal_dispose() const {
 *     #ifdef SK_DEBUG
 *         SkASSERT(0 == this->getRefCnt());
 *         fRefCnt.store(1, std::memory_order_relaxed);
 *     #endif
 *         delete this;
 *     }
 *
 *     // The following friends are those which override internal_dispose()
 *     // and conditionally call SkRefCnt::internal_dispose().
 *     friend class SkWeakRefCnt;
 *
 *     mutable std::atomic<int32_t> fRefCnt;
 *
 *     SkRefCntBase(SkRefCntBase&&) = delete;
 *     SkRefCntBase(const SkRefCntBase&) = delete;
 *     SkRefCntBase& operator=(SkRefCntBase&&) = delete;
 *     SkRefCntBase& operator=(const SkRefCntBase&) = delete;
 * }
 * ```
 */
public open class SkRefCntBase public constructor() {
  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<int32_t> fRefCnt
   * ```
   */
  private var fRefCnt: AtomicInt = TODO("Initialize fRefCnt")

  /**
   * C++ original:
   * ```cpp
   * SkRefCntBase() : fRefCnt(1) {}
   * ```
   */
  public constructor(param0: SkRefCntBase) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unique() const {
   *         if (1 == fRefCnt.load(std::memory_order_acquire)) {
   *             // The acquire barrier is only really needed if we return true.  It
   *             // prevents code conditioned on the result of unique() from running
   *             // until previous owners are all totally done calling unref().
   *             return true;
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun unique(): Boolean {
    TODO("Implement unique")
  }

  /**
   * C++ original:
   * ```cpp
   * void ref() const {
   *         SkASSERT(this->getRefCnt() > 0);
   *         // No barrier required.
   *         (void)fRefCnt.fetch_add(+1, std::memory_order_relaxed);
   *     }
   * ```
   */
  public fun ref() {
    TODO("Implement ref")
  }

  /**
   * C++ original:
   * ```cpp
   * void unref() const {
   *         SkASSERT(this->getRefCnt() > 0);
   *         // A release here acts in place of all releases we "should" have been doing in ref().
   *         if (1 == fRefCnt.fetch_add(-1, std::memory_order_acq_rel)) {
   *             // Like unique(), the acquire is only needed on success, to make sure
   *             // code in internal_dispose() doesn't happen before the decrement.
   *             this->internal_dispose();
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
   * virtual void internal_dispose() const {
   *     #ifdef SK_DEBUG
   *         SkASSERT(0 == this->getRefCnt());
   *         fRefCnt.store(1, std::memory_order_relaxed);
   *     #endif
   *         delete this;
   *     }
   * ```
   */
  public open fun internalDispose() {
    TODO("Implement internalDispose")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRefCntBase& operator=(SkRefCntBase&&) = delete
   * ```
   */
  private fun assign(param0: SkRefCntBase) {
    TODO("Implement assign")
  }
}
