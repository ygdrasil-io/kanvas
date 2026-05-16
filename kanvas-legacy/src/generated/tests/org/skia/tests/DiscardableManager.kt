package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.UInt
import org.skia.core.THashSet
import org.skia.utils.SkDiscardableHandleId
import org.skia.utils.SkStrikeClient
import org.skia.utils.SkStrikeServer
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class DiscardableManager : public SkStrikeServer::DiscardableHandleManager,
 *                            public SkStrikeClient::DiscardableHandleManager {
 * public:
 *     DiscardableManager() { sk_bzero(&fCacheMissCount, sizeof(fCacheMissCount)); }
 *     ~DiscardableManager() override = default;
 *
 *     // Server implementation.
 *     SkDiscardableHandleId createHandle() override {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         // Handles starts as locked.
 *         fLockedHandles.add(++fNextHandleId);
 *         return fNextHandleId;
 *     }
 *     bool lockHandle(SkDiscardableHandleId id) override {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         if (id <= fLastDeletedHandleId) return false;
 *         fLockedHandles.add(id);
 *         return true;
 *     }
 *
 *     // Client implementation.
 *     bool deleteHandle(SkDiscardableHandleId id) override {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         return id <= fLastDeletedHandleId;
 *     }
 *
 *     void notifyCacheMiss(SkStrikeClient::CacheMissType type, int fontSize) override {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         fCacheMissCount[type]++;
 *     }
 *     bool isHandleDeleted(SkDiscardableHandleId id) override {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         return id <= fLastDeletedHandleId;
 *     }
 *
 *     void unlockAll() {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         fLockedHandles.reset();
 *     }
 *     void unlockAndDeleteAll() {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         fLockedHandles.reset();
 *         fLastDeletedHandleId = fNextHandleId;
 *     }
 *     const THashSet<SkDiscardableHandleId>& lockedHandles() const {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         return fLockedHandles;
 *     }
 *     SkDiscardableHandleId handleCount() {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         return fNextHandleId;
 *     }
 *     int cacheMissCount(uint32_t type) {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         return fCacheMissCount[type];
 *     }
 *     bool hasCacheMiss() const {
 *         SkAutoMutexExclusive l(fMutex);
 *
 *         for (uint32_t i = 0; i <= SkStrikeClient::CacheMissType::kLast; ++i) {
 *             if (fCacheMissCount[i] > 0) { return true; }
 *         }
 *         return false;
 *     }
 *     void resetCacheMissCounts() {
 *         SkAutoMutexExclusive l(fMutex);
 *         sk_bzero(&fCacheMissCount, sizeof(fCacheMissCount));
 *     }
 *
 * private:
 *     // The tests below run in parallel on multiple threads and use the same
 *     // process global SkStrikeCache. So the implementation needs to be
 *     // thread-safe.
 *     mutable SkMutex fMutex;
 *
 *     SkDiscardableHandleId fNextHandleId = 0u;
 *     SkDiscardableHandleId fLastDeletedHandleId = 0u;
 *     THashSet<SkDiscardableHandleId> fLockedHandles;
 *     int fCacheMissCount[SkStrikeClient::CacheMissType::kLast + 1u];
 * }
 * ```
 */
public open class DiscardableManager public constructor() : SkStrikeServer.DiscardableHandleManager(),
    SkStrikeClient.DiscardableHandleManager {
  /**
   * C++ original:
   * ```cpp
   * mutable SkMutex fMutex
   * ```
   */
  private var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId fNextHandleId = 0u
   * ```
   */
  private var fNextHandleId: SkDiscardableHandleId = TODO("Initialize fNextHandleId")

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId fLastDeletedHandleId = 0u
   * ```
   */
  private var fLastDeletedHandleId: SkDiscardableHandleId = TODO("Initialize fLastDeletedHandleId")

  /**
   * C++ original:
   * ```cpp
   * THashSet<SkDiscardableHandleId> fLockedHandles
   * ```
   */
  private var fLockedHandles: THashSet<SkDiscardableHandleId> = TODO("Initialize fLockedHandles")

  /**
   * C++ original:
   * ```cpp
   * int fCacheMissCount[SkStrikeClient::CacheMissType::kLast + 1u]
   * ```
   */
  private var fCacheMissCount: IntArray = TODO("Initialize fCacheMissCount")

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId createHandle() override {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         // Handles starts as locked.
   *         fLockedHandles.add(++fNextHandleId);
   *         return fNextHandleId;
   *     }
   * ```
   */
  public override fun createHandle(): SkDiscardableHandleId {
    TODO("Implement createHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool lockHandle(SkDiscardableHandleId id) override {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         if (id <= fLastDeletedHandleId) return false;
   *         fLockedHandles.add(id);
   *         return true;
   *     }
   * ```
   */
  public override fun lockHandle(id: SkDiscardableHandleId): Boolean {
    TODO("Implement lockHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool deleteHandle(SkDiscardableHandleId id) override {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         return id <= fLastDeletedHandleId;
   *     }
   * ```
   */
  public override fun deleteHandle(id: SkDiscardableHandleId): Boolean {
    TODO("Implement deleteHandle")
  }

  /**
   * C++ original:
   * ```cpp
   * void notifyCacheMiss(SkStrikeClient::CacheMissType type, int fontSize) override {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         fCacheMissCount[type]++;
   *     }
   * ```
   */
  public override fun notifyCacheMiss(type: SkStrikeClient.CacheMissType, fontSize: Int) {
    TODO("Implement notifyCacheMiss")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHandleDeleted(SkDiscardableHandleId id) override {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         return id <= fLastDeletedHandleId;
   *     }
   * ```
   */
  public override fun isHandleDeleted(id: SkDiscardableHandleId): Boolean {
    TODO("Implement isHandleDeleted")
  }

  /**
   * C++ original:
   * ```cpp
   * void unlockAll() {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         fLockedHandles.reset();
   *     }
   * ```
   */
  public fun unlockAll() {
    TODO("Implement unlockAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void unlockAndDeleteAll() {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         fLockedHandles.reset();
   *         fLastDeletedHandleId = fNextHandleId;
   *     }
   * ```
   */
  public fun unlockAndDeleteAll() {
    TODO("Implement unlockAndDeleteAll")
  }

  /**
   * C++ original:
   * ```cpp
   * const THashSet<SkDiscardableHandleId>& lockedHandles() const {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         return fLockedHandles;
   *     }
   * ```
   */
  public fun lockedHandles(): THashSet<SkDiscardableHandleId> {
    TODO("Implement lockedHandles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableHandleId handleCount() {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         return fNextHandleId;
   *     }
   * ```
   */
  public fun handleCount(): SkDiscardableHandleId {
    TODO("Implement handleCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int cacheMissCount(uint32_t type) {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         return fCacheMissCount[type];
   *     }
   * ```
   */
  public fun cacheMissCount(type: UInt): Int {
    TODO("Implement cacheMissCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasCacheMiss() const {
   *         SkAutoMutexExclusive l(fMutex);
   *
   *         for (uint32_t i = 0; i <= SkStrikeClient::CacheMissType::kLast; ++i) {
   *             if (fCacheMissCount[i] > 0) { return true; }
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun hasCacheMiss(): Boolean {
    TODO("Implement hasCacheMiss")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetCacheMissCounts() {
   *         SkAutoMutexExclusive l(fMutex);
   *         sk_bzero(&fCacheMissCount, sizeof(fCacheMissCount));
   *     }
   * ```
   */
  public fun resetCacheMissCounts() {
    TODO("Implement resetCacheMissCounts")
  }
}
