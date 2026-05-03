package org.skia.core

import kotlin.Int
import kotlinx.atomicfu.AtomicInt
import undefined.OSSemaphore

/**
 * C++ original:
 * ```cpp
 * class SkSemaphore {
 * public:
 *     constexpr SkSemaphore(int count = 0) : fCount(count), fOSSemaphore(nullptr) {}
 *
 *     // Cleanup the underlying OS semaphore.
 *     SK_SPI ~SkSemaphore();
 *
 *     // Increment the counter n times.
 *     // Generally it's better to call signal(n) instead of signal() n times.
 *     void signal(int n = 1);
 *
 *     // Decrement the counter by 1,
 *     // then if the counter is < 0, sleep this thread until the counter is >= 0.
 *     void wait();
 *
 *     // If the counter is positive, decrement it by 1 and return true, otherwise return false.
 *     SK_SPI bool try_wait();
 *
 * private:
 *     // This implementation follows the general strategy of
 *     //     'A Lightweight Semaphore with Partial Spinning'
 *     // found here
 *     //     http://preshing.com/20150316/semaphores-are-surprisingly-versatile/
 *     // That article (and entire blog) are very much worth reading.
 *     //
 *     // We wrap an OS-provided semaphore with a user-space atomic counter that
 *     // lets us avoid interacting with the OS semaphore unless strictly required:
 *     // moving the count from >=0 to <0 or vice-versa, i.e. sleeping or waking threads.
 *     struct OSSemaphore;
 *
 *     SK_SPI void osSignal(int n);
 *     SK_SPI void osWait();
 *
 *     std::atomic<int> fCount;
 *     SkOnce           fOSSemaphoreOnce;
 *     OSSemaphore*     fOSSemaphore;
 * }
 * ```
 */
public data class SkSemaphore public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::atomic<int> fCount
   * ```
   */
  private val fCount: AtomicInt,
  /**
   * C++ original:
   * ```cpp
   * SkOnce           fOSSemaphoreOnce
   * ```
   */
  private var fOSSemaphoreOnce: Int,
  /**
   * C++ original:
   * ```cpp
   * OSSemaphore*     fOSSemaphore
   * ```
   */
  private var fOSSemaphore: OSSemaphore?,
) {
  /**
   * C++ original:
   * ```cpp
   * inline void SkSemaphore::signal(int n) {
   *     int prev = fCount.fetch_add(n, std::memory_order_release);
   *
   *     // We only want to call the OS semaphore when our logical count crosses
   *     // from <0 to >=0 (when we need to wake sleeping threads).
   *     //
   *     // This is easiest to think about with specific examples of prev and n.
   *     // If n == 5 and prev == -3, there are 3 threads sleeping and we signal
   *     // std::min(-(-3), 5) == 3 times on the OS semaphore, leaving the count at 2.
   *     //
   *     // If prev >= 0, no threads are waiting, std::min(-prev, n) is always <= 0,
   *     // so we don't call the OS semaphore, leaving the count at (prev + n).
   *     int toSignal = std::min(-prev, n);
   *     if (toSignal > 0) {
   *         this->osSignal(toSignal);
   *     }
   * }
   * ```
   */
  public fun signal(n: Int = 1) {
    TODO("Implement signal")
  }

  /**
   * C++ original:
   * ```cpp
   * inline void SkSemaphore::wait() {
   *     // Since this fetches the value before the subtract, zero and below means that there are no
   *     // resources left, so the thread needs to wait.
   *     if (fCount.fetch_sub(1, std::memory_order_acquire) <= 0) {
   *         SK_POTENTIALLY_BLOCKING_REGION_BEGIN;
   *         this->osWait();
   *         SK_POTENTIALLY_BLOCKING_REGION_END;
   *     }
   * }
   * ```
   */
  public fun wait() {
    TODO("Implement wait")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSemaphore::try_wait() {
   *     int count = fCount.load(std::memory_order_relaxed);
   *     if (count > 0) {
   *         return fCount.compare_exchange_weak(count, count-1, std::memory_order_acquire);
   *     }
   *     return false;
   * }
   * ```
   */
  public fun tryWait(): Int {
    TODO("Implement tryWait")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSemaphore::osSignal(int n) {
   *     fOSSemaphoreOnce([this] { fOSSemaphore = new OSSemaphore; });
   *     fOSSemaphore->signal(n);
   * }
   * ```
   */
  private fun osSignal(n: Int): Int {
    TODO("Implement osSignal")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSemaphore::osWait() {
   *     fOSSemaphoreOnce([this] { fOSSemaphore = new OSSemaphore; });
   *     fOSSemaphore->wait();
   * }
   * ```
   */
  private fun osWait(): Int {
    TODO("Implement osWait")
  }
}
