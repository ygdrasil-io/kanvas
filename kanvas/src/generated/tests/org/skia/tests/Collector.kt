package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkSpinlock

/**
 * C++ original:
 * ```cpp
 * class Collector {
 * public:
 *     static constexpr int kMaxCount = 200;
 *
 *     void insert(int i) SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{fSpinLock};
 *
 *         if (fCount >= kMaxCount) {
 *             return;
 *         }
 *
 *         fData[fCount++] = i;
 *     }
 *
 *     int count() const SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{fSpinLock};
 *         return fCount;
 *     }
 *
 * #if defined(SK_DEBUG)
 *     void dump() const SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{fSpinLock};
 *
 *         for (int i = 0; i < fCount; ++i) {
 *             SkDebugf("%d ", fData[i]);
 *         }
 *         SkDebugf("\n");
 *     }
 * #endif
 *
 *     // For this following tests we expect that all the high priority work is completed
 *     // before the low priority work. The length of the two task types is set up so,
 *     // even if low priority work is picked up while the high priority tasks are finishing,
 *     // all the high priority task should still finish before the low priority ones.
 *     bool dataIsInOrder() const SK_EXCLUDES(fSpinLock) {
 *         SkAutoSpinlock lock{fSpinLock};
 *
 *         for (int i = 0; i < fCount; ++i) {
 *             if (i < 100) {
 *                 if (fData[i] >= 100) { return false; }
 *             } else {
 *                 if (fData[i] < 100) { return false; }
 *             }
 *         }
 *         return true;
 *     }
 *
 * private:
 *     mutable SkSpinlock fSpinLock;
 *
 *     int fCount SK_GUARDED_BY(fSpinLock) = 0;
 *     int fData[kMaxCount] SK_GUARDED_BY(fSpinLock);
 * }
 * ```
 */
public data class Collector public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxCount = 200
   * ```
   */
  private var fSpinLock: SkSpinlock,
  /**
   * C++ original:
   * ```cpp
   * mutable SkSpinlock fSpinLock
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fCount SK_GUARDED_BY(fSpinLock) = 0
   * ```
   */
  private var fData: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * void insert(int i) SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{fSpinLock};
   *
   *         if (fCount >= kMaxCount) {
   *             return;
   *         }
   *
   *         fData[fCount++] = i;
   *     }
   * ```
   */
  public fun insert(i: Int) {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{fSpinLock};
   *         return fCount;
   *     }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void dump() const SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{fSpinLock};
   *
   *         for (int i = 0; i < fCount; ++i) {
   *             SkDebugf("%d ", fData[i]);
   *         }
   *         SkDebugf("\n");
   *     }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool dataIsInOrder() const SK_EXCLUDES(fSpinLock) {
   *         SkAutoSpinlock lock{fSpinLock};
   *
   *         for (int i = 0; i < fCount; ++i) {
   *             if (i < 100) {
   *                 if (fData[i] >= 100) { return false; }
   *             } else {
   *                 if (fData[i] < 100) { return false; }
   *             }
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun dataIsInOrder(): Boolean {
    TODO("Implement dataIsInOrder")
  }

  public companion object {
    public val kMaxCount: Int = TODO("Initialize kMaxCount")
  }
}
