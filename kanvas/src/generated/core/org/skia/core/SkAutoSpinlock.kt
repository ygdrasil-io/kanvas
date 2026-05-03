package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SK_SCOPED_CAPABILITY SkAutoSpinlock {
 * public:
 *     explicit SkAutoSpinlock(SkSpinlock& mutex) SK_ACQUIRE(mutex) : fSpinlock(mutex) {
 *         fSpinlock.acquire();
 *     }
 *     ~SkAutoSpinlock() SK_RELEASE_CAPABILITY() { fSpinlock.release(); }
 *
 * private:
 *     SkSpinlock& fSpinlock;
 * }
 * ```
 */
public data class SkAutoSpinlock public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSpinlock& fSpinlock
   * ```
   */
  private var fSpinlock: SkSpinlock,
)
