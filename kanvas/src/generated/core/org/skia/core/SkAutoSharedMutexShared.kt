package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SK_SCOPED_CAPABILITY SkAutoSharedMutexShared {
 * public:
 *     explicit SkAutoSharedMutexShared(SkSharedMutex& lock) SK_ACQUIRE_SHARED(lock)
 *             : fLock(lock)  {
 *         lock.acquireShared();
 *     }
 *
 *     // You would think this should be SK_RELEASE_SHARED_CAPABILITY, but SK_SCOPED_CAPABILITY
 *     // doesn't fully understand the difference between shared and exclusive.
 *     // Please review https://reviews.llvm.org/D52578 for more information.
 *     ~SkAutoSharedMutexShared() SK_RELEASE_CAPABILITY() { fLock.releaseShared(); }
 *
 * private:
 *     SkSharedMutex& fLock;
 * }
 * ```
 */
public data class SkAutoSharedMutexShared public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSharedMutex& fLock
   * ```
   */
  private var fLock: SkSharedMutex,
)
