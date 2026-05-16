package org.skia.tests

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class CopyCounter {
 * public:
 *     CopyCounter() : fID(0), fCounter(nullptr) {}
 *
 *     CopyCounter(uint32_t id, uint32_t* counter) : fID(id), fCounter(counter) {}
 *
 *     CopyCounter(const CopyCounter& other)
 *         : fID(other.fID)
 *         , fCounter(other.fCounter) {
 *         SkASSERT(fCounter);
 *         *fCounter += 1;
 *     }
 *
 *     void operator=(const CopyCounter& other) {
 *         fID = other.fID;
 *         fCounter = other.fCounter;
 *         *fCounter += 1;
 *     }
 *
 *     CopyCounter(CopyCounter&& other) { *this = std::move(other); }
 *     void operator=(CopyCounter&& other) {
 *         fID = other.fID;
 *         fCounter = other.fCounter;
 *     }
 *
 *
 *     bool operator==(const CopyCounter& other) const {
 *         return fID == other.fID;
 *     }
 *
 * private:
 *     uint32_t  fID;
 *     uint32_t* fCounter;
 * }
 * ```
 */
public data class CopyCounter public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t  fID
   * ```
   */
  private var fID: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t* fCounter
   * ```
   */
  private var fCounter: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * void operator=(const CopyCounter& other) {
   *         fID = other.fID;
   *         fCounter = other.fCounter;
   *         *fCounter += 1;
   *     }
   * ```
   */
  public fun assign(other: CopyCounter) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator=(CopyCounter&& other) {
   *         fID = other.fID;
   *         fCounter = other.fCounter;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
