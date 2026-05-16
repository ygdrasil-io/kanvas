package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SK_SCOPED_CAPABILITY SkAutoSharedMutexExclusive {
 * public:
 *     explicit SkAutoSharedMutexExclusive(SkSharedMutex& lock) SK_ACQUIRE(lock)
 *             : fLock(lock) {
 *         lock.acquire();
 *     }
 *     ~SkAutoSharedMutexExclusive() SK_RELEASE_CAPABILITY() { fLock.release(); }
 *
 * private:
 *     SkSharedMutex& fLock;
 * }
 * ```
 */
public data class SkAutoSharedMutexExclusive public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSharedMutex& fLock
   * ```
   */
  private var fLock: SkSharedMutex,
)
