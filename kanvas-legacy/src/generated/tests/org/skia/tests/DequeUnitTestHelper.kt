package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DequeUnitTestHelper {
 * public:
 *     int fNumBlocksAllocated;
 *
 *     DequeUnitTestHelper(const SkDeque& deq) {
 *         fNumBlocksAllocated = deq.numBlocksAllocated();
 *     }
 * }
 * ```
 */
public data class DequeUnitTestHelper public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fNumBlocksAllocated
   * ```
   */
  public var fNumBlocksAllocated: Int,
)
