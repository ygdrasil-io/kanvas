package org.skia.core

import kotlin.Array
import kotlin.Int
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SK_CAPABILITY("mutex") SkSharedMutex {
 * public:
 *     SkSharedMutex();
 *     ~SkSharedMutex();
 *     // Acquire lock for exclusive use.
 *     void acquire() SK_ACQUIRE();
 *
 *     // Release lock for exclusive use.
 *     void release() SK_RELEASE_CAPABILITY();
 *
 *     // Fail if exclusive is not held.
 *     void assertHeld() const SK_ASSERT_CAPABILITY(this);
 *
 *     // Acquire lock for shared use.
 *     void acquireShared() SK_ACQUIRE_SHARED();
 *
 *     // Release lock for shared use.
 *     void releaseShared() SK_RELEASE_SHARED_CAPABILITY();
 *
 *     // Fail if shared lock not held.
 *     void assertHeldShared() const SK_ASSERT_SHARED_CAPABILITY(this);
 *
 * private:
 * #ifdef SK_DEBUG
 *     class ThreadIDSet;
 *     std::unique_ptr<ThreadIDSet> fCurrentShared;
 *     std::unique_ptr<ThreadIDSet> fWaitingExclusive;
 *     std::unique_ptr<ThreadIDSet> fWaitingShared;
 *     int fSharedQueueSelect{0};
 *     mutable SkMutex fMu;
 *     SkSemaphore fSharedQueue[2];
 *     SkSemaphore fExclusiveQueue;
 * #else
 *     std::atomic<int32_t> fQueueCounts;
 *     SkSemaphore          fSharedQueue;
 *     SkSemaphore          fExclusiveQueue;
 * #endif  // SK_DEBUG
 * }
 * ```
 */
public data class SkSharedMutex public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ThreadIDSet> fCurrentShared
   * ```
   */
  private var fCurrentShared: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ThreadIDSet> fWaitingExclusive
   * ```
   */
  private var fWaitingExclusive: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ThreadIDSet> fWaitingShared
   * ```
   */
  private var fWaitingShared: Int,
  /**
   * C++ original:
   * ```cpp
   * int fSharedQueueSelect{0}
   * ```
   */
  private var fSharedQueueSelect: Int,
  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex fMu
   * ```
   */
  private var fMu: SkMutex,
  /**
   * C++ original:
   * ```cpp
   * SkSemaphore fSharedQueue[2]
   * ```
   */
  private var fSharedQueue: Array<SkSemaphore>,
  /**
   * C++ original:
   * ```cpp
   * SkSemaphore fExclusiveQueue
   * ```
   */
  private var fExclusiveQueue: SkSemaphore,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::acquire() {
   *         SkThreadID threadID(SkGetThreadID());
   *         int currentSharedCount;
   *         int waitingExclusiveCount;
   *         {
   *             SkAutoMutexExclusive l(fMu);
   *
   *             SkASSERTF(!fCurrentShared->find(threadID),
   *                       "Thread %" PRIx64 " already has an shared lock\n", (uint64_t)threadID);
   *
   *             if (!fWaitingExclusive->tryAdd(threadID)) {
   *                 SkDEBUGFAILF("Thread %" PRIx64 " already has an exclusive lock\n",
   *                              (uint64_t)threadID);
   *             }
   *
   *             currentSharedCount = fCurrentShared->count();
   *             waitingExclusiveCount = fWaitingExclusive->count();
   *         }
   *
   *         if (currentSharedCount > 0 || waitingExclusiveCount > 1) {
   *             fExclusiveQueue.wait();
   *         }
   *
   *         ANNOTATE_RWLOCK_ACQUIRED(this, 1);
   *     }
   * ```
   */
  public fun acquire() {
    TODO("Implement acquire")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::release() {
   *         ANNOTATE_RWLOCK_RELEASED(this, 1);
   *         SkThreadID threadID(SkGetThreadID());
   *         int sharedWaitingCount;
   *         int exclusiveWaitingCount;
   *         int sharedQueueSelect;
   *         {
   *             SkAutoMutexExclusive l(fMu);
   *             SkASSERT(0 == fCurrentShared->count());
   *             if (!fWaitingExclusive->tryRemove(threadID)) {
   *                 SkDEBUGFAILF("Thread %" PRIx64 " did not have the lock held.\n",
   *                              (uint64_t)threadID);
   *             }
   *             exclusiveWaitingCount = fWaitingExclusive->count();
   *             sharedWaitingCount = fWaitingShared->count();
   *             fWaitingShared.swap(fCurrentShared);
   *             sharedQueueSelect = fSharedQueueSelect;
   *             if (sharedWaitingCount > 0) {
   *                 fSharedQueueSelect = 1 - fSharedQueueSelect;
   *             }
   *         }
   *
   *         if (sharedWaitingCount > 0) {
   *             fSharedQueue[sharedQueueSelect].signal(sharedWaitingCount);
   *         } else if (exclusiveWaitingCount > 0) {
   *             fExclusiveQueue.signal();
   *         }
   *     }
   * ```
   */
  public fun release() {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::assertHeld() const {
   *         SkThreadID threadID(SkGetThreadID());
   *         SkAutoMutexExclusive l(fMu);
   *         SkASSERT(0 == fCurrentShared->count());
   *         SkASSERT(fWaitingExclusive->find(threadID));
   *     }
   * ```
   */
  public fun assertHeld() {
    TODO("Implement assertHeld")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::acquireShared() {
   *         SkThreadID threadID(SkGetThreadID());
   *         int exclusiveWaitingCount;
   *         int sharedQueueSelect;
   *         {
   *             SkAutoMutexExclusive l(fMu);
   *             exclusiveWaitingCount = fWaitingExclusive->count();
   *             if (exclusiveWaitingCount > 0) {
   *                 if (!fWaitingShared->tryAdd(threadID)) {
   *                     SkDEBUGFAILF("Thread %" PRIx64 " was already waiting!\n", (uint64_t)threadID);
   *                 }
   *             } else {
   *                 if (!fCurrentShared->tryAdd(threadID)) {
   *                     SkDEBUGFAILF("Thread %" PRIx64 " already holds a shared lock!\n",
   *                                  (uint64_t)threadID);
   *                 }
   *             }
   *             sharedQueueSelect = fSharedQueueSelect;
   *         }
   *
   *         if (exclusiveWaitingCount > 0) {
   *             fSharedQueue[sharedQueueSelect].wait();
   *         }
   *
   *         ANNOTATE_RWLOCK_ACQUIRED(this, 0);
   *     }
   * ```
   */
  public fun acquireShared() {
    TODO("Implement acquireShared")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::releaseShared() {
   *         ANNOTATE_RWLOCK_RELEASED(this, 0);
   *         SkThreadID threadID(SkGetThreadID());
   *
   *         int currentSharedCount;
   *         int waitingExclusiveCount;
   *         {
   *             SkAutoMutexExclusive l(fMu);
   *             if (!fCurrentShared->tryRemove(threadID)) {
   *                 SkDEBUGFAILF("Thread %" PRIx64 " does not hold a shared lock.\n",
   *                              (uint64_t)threadID);
   *             }
   *             currentSharedCount = fCurrentShared->count();
   *             waitingExclusiveCount = fWaitingExclusive->count();
   *         }
   *
   *         if (0 == currentSharedCount && waitingExclusiveCount > 0) {
   *             fExclusiveQueue.signal();
   *         }
   *     }
   * ```
   */
  public fun releaseShared() {
    TODO("Implement releaseShared")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSharedMutex::assertHeldShared() const {
   *         SkThreadID threadID(SkGetThreadID());
   *         SkAutoMutexExclusive l(fMu);
   *         SkASSERT(fCurrentShared->find(threadID));
   *     }
   * ```
   */
  public fun assertHeldShared() {
    TODO("Implement assertHeldShared")
  }
}
