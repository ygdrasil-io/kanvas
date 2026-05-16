package org.skia.core

import kotlin.Array
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class RunArray {
 * public:
 *     RunArray() { fPtr = fStack; }
 *     #ifdef SK_DEBUG
 *     int count() const { return fCount; }
 *     #endif
 *     SkRegionPriv::RunType& operator[](int i) {
 *         SkASSERT((unsigned)i < (unsigned)fCount);
 *         return fPtr[i];
 *     }
 *     /** Resize the array to a size greater-than-or-equal-to count. */
 *     void resizeToAtLeast(int count) {
 *         if (count > fCount) {
 *             // leave at least 50% extra space for future growth (unless adding would overflow)
 *             SkSafeMath safe;
 *             int newCount = safe.addInt(count, count >> 1);
 *             count = safe ? newCount : SK_MaxS32;
 *             fMalloc.realloc(count);
 *             if (fPtr == fStack) {
 *                 memcpy(fMalloc.get(), fStack, fCount * sizeof(SkRegionPriv::RunType));
 *             }
 *             fPtr = fMalloc.get();
 *             fCount = count;
 *         }
 *     }
 * private:
 *     SkRegionPriv::RunType fStack[kRunArrayStackCount];
 *     AutoTMalloc<SkRegionPriv::RunType> fMalloc;
 *     int fCount = kRunArrayStackCount;
 *     SkRegionPriv::RunType* fPtr;  // non-owning pointer
 * }
 * ```
 */
public data class RunArray public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType fStack[kRunArrayStackCount]
   * ```
   */
  private var fStack: Array<SkRegionPrivRunType>,
  /**
   * C++ original:
   * ```cpp
   * AutoTMalloc<SkRegionPriv::RunType> fMalloc
   * ```
   */
  private var fMalloc: Int,
  /**
   * C++ original:
   * ```cpp
   * int fCount = kRunArrayStackCount
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType* fPtr
   * ```
   */
  private var fPtr: SkRegionPrivRunType?,
) {
  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCount; }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType& operator[](int i) {
   *         SkASSERT((unsigned)i < (unsigned)fCount);
   *         return fPtr[i];
   *     }
   * ```
   */
  public operator fun `get`(i: Int): SkRegionPrivRunType {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * void resizeToAtLeast(int count) {
   *         if (count > fCount) {
   *             // leave at least 50% extra space for future growth (unless adding would overflow)
   *             SkSafeMath safe;
   *             int newCount = safe.addInt(count, count >> 1);
   *             count = safe ? newCount : SK_MaxS32;
   *             fMalloc.realloc(count);
   *             if (fPtr == fStack) {
   *                 memcpy(fMalloc.get(), fStack, fCount * sizeof(SkRegionPriv::RunType));
   *             }
   *             fPtr = fMalloc.get();
   *             fCount = count;
   *         }
   *     }
   * ```
   */
  public fun resizeToAtLeast(count: Int) {
    TODO("Implement resizeToAtLeast")
  }
}
