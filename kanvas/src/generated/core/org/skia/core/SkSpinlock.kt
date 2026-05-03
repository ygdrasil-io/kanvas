package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_CAPABILITY("mutex") SkSpinlock {
 * public:
 *     constexpr SkSpinlock() = default;
 *
 *     void acquire() SK_ACQUIRE() {
 *         // To act as a mutex, we need an acquire barrier when we acquire the lock.
 *         if (fLocked.exchange(true, std::memory_order_acquire)) {
 *             // Lock was contended.  Fall back to an out-of-line spin loop.
 *             this->contendedAcquire();
 *         }
 *     }
 *
 *     // Acquire the lock or fail (quickly). Lets the caller decide to do something other than wait.
 *     bool tryAcquire() SK_TRY_ACQUIRE(true) {
 *         // To act as a mutex, we need an acquire barrier when we acquire the lock.
 *         if (fLocked.exchange(true, std::memory_order_acquire)) {
 *             // Lock was contended. Let the caller decide what to do.
 *             return false;
 *         }
 *         return true;
 *     }
 *
 *     void release() SK_RELEASE_CAPABILITY() {
 *         // To act as a mutex, we need a release barrier when we release the lock.
 *         fLocked.store(false, std::memory_order_release);
 *     }
 *
 * private:
 *     SK_API void contendedAcquire();
 *
 *     std::atomic<bool> fLocked{false};
 * }
 * ```
 */
public data class SkSpinlock public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::atomic<bool> fLocked
   * ```
   */
  private var fLocked: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void acquire() SK_ACQUIRE() {
   *         // To act as a mutex, we need an acquire barrier when we acquire the lock.
   *         if (fLocked.exchange(true, std::memory_order_acquire)) {
   *             // Lock was contended.  Fall back to an out-of-line spin loop.
   *             this->contendedAcquire();
   *         }
   *     }
   * ```
   */
  public fun acquire() {
    TODO("Implement acquire")
  }

  /**
   * C++ original:
   * ```cpp
   * bool tryAcquire() SK_TRY_ACQUIRE(true) {
   *         // To act as a mutex, we need an acquire barrier when we acquire the lock.
   *         if (fLocked.exchange(true, std::memory_order_acquire)) {
   *             // Lock was contended. Let the caller decide what to do.
   *             return false;
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun tryAcquire(): Boolean {
    TODO("Implement tryAcquire")
  }

  /**
   * C++ original:
   * ```cpp
   * void release() SK_RELEASE_CAPABILITY() {
   *         // To act as a mutex, we need a release barrier when we release the lock.
   *         fLocked.store(false, std::memory_order_release);
   *     }
   * ```
   */
  public fun release() {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpinlock::contendedAcquire() {
   *     debug_trace();
   *
   *     // To act as a mutex, we need an acquire barrier when we acquire the lock.
   *     SK_POTENTIALLY_BLOCKING_REGION_BEGIN;
   *     while (fLocked.exchange(true, std::memory_order_acquire)) {
   *         do_pause();
   *     }
   *     SK_POTENTIALLY_BLOCKING_REGION_END;
   * }
   * ```
   */
  private fun contendedAcquire() {
    TODO("Implement contendedAcquire")
  }
}
