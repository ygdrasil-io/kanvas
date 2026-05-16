package org.skia.core

import kotlin.UByte
import kotlinx.atomicfu.AtomicRef
import undefined.Args
import undefined.Fn

/**
 * C++ original:
 * ```cpp
 * class SkOnce {
 * public:
 *     constexpr SkOnce() = default;
 *
 *     template <typename Fn, typename... Args>
 *     void operator()(Fn&& fn, Args&&... args) {
 *         auto state = fState.load(std::memory_order_acquire);
 *
 *         if (state == Done) {
 *             return;
 *         }
 *
 *         // If it looks like no one has started calling fn(), try to claim that job.
 *         if (state == NotStarted && fState.compare_exchange_strong(state, Claimed,
 *                                                                   std::memory_order_relaxed,
 *                                                                   std::memory_order_relaxed)) {
 *             // Great!  We'll run fn() then notify the other threads by releasing Done into fState.
 *             fn(std::forward<Args>(args)...);
 *             return fState.store(Done, std::memory_order_release);
 *         }
 *
 *         // Some other thread is calling fn().
 *         // We'll just spin here acquiring until it releases Done into fState.
 *         SK_POTENTIALLY_BLOCKING_REGION_BEGIN;
 *         while (fState.load(std::memory_order_acquire) != Done) { /*spin*/ }
 *         SK_POTENTIALLY_BLOCKING_REGION_END;
 *     }
 *
 * private:
 *     enum State : uint8_t { NotStarted, Claimed, Done};
 *     std::atomic<uint8_t> fState{NotStarted};
 * }
 * ```
 */
public data class SkOnce public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::atomic<uint8_t> fState
   * ```
   */
  private val fState: AtomicRef<UByte>,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename Fn, typename... Args>
   *     void operator()(Fn&& fn, Args&&... args) {
   *         auto state = fState.load(std::memory_order_acquire);
   *
   *         if (state == Done) {
   *             return;
   *         }
   *
   *         // If it looks like no one has started calling fn(), try to claim that job.
   *         if (state == NotStarted && fState.compare_exchange_strong(state, Claimed,
   *                                                                   std::memory_order_relaxed,
   *                                                                   std::memory_order_relaxed)) {
   *             // Great!  We'll run fn() then notify the other threads by releasing Done into fState.
   *             fn(std::forward<Args>(args)...);
   *             return fState.store(Done, std::memory_order_release);
   *         }
   *
   *         // Some other thread is calling fn().
   *         // We'll just spin here acquiring until it releases Done into fState.
   *         SK_POTENTIALLY_BLOCKING_REGION_BEGIN;
   *         while (fState.load(std::memory_order_acquire) != Done) { /*spin*/ }
   *         SK_POTENTIALLY_BLOCKING_REGION_END;
   *     }
   * ```
   */
  public operator fun <Fn, Args> invoke(fn: Fn, args: Args) {
    TODO("Implement invoke")
  }

  public enum class State {
    NotStarted,
    Claimed,
    Done,
  }
}
