package org.skia.tests

import SkContainerAllocator
import kotlin.Double
import kotlin.Int
import kotlin.Long

/**
 * C++ original:
 * ```cpp
 * struct SkContainerAllocatorTestingPeer {
 *     static size_t RoundUpCapacity(const SkContainerAllocator& a, int64_t capacity){
 *         return a.roundUpCapacity(capacity);
 *     }
 *
 *     static size_t GrowthFactorCapacity(
 *             const SkContainerAllocator& a, int capacity, double growthFactor) {
 *         return a.growthFactorCapacity(capacity, growthFactor);
 *     }
 *
 *     static constexpr int64_t kCapacityMultiple = SkContainerAllocator::kCapacityMultiple;
 * }
 * ```
 */
public open class SkContainerAllocatorTestingPeer {
  public companion object {
    public val kCapacityMultiple: Int = TODO("Initialize kCapacityMultiple")

    /**
     * C++ original:
     * ```cpp
     * static size_t RoundUpCapacity(const SkContainerAllocator& a, int64_t capacity){
     *         return a.roundUpCapacity(capacity);
     *     }
     * ```
     */
    public fun roundUpCapacity(a: SkContainerAllocator, capacity: Long): Int {
      TODO("Implement roundUpCapacity")
    }

    /**
     * C++ original:
     * ```cpp
     * static size_t GrowthFactorCapacity(
     *             const SkContainerAllocator& a, int capacity, double growthFactor) {
     *         return a.growthFactorCapacity(capacity, growthFactor);
     *     }
     * ```
     */
    public fun growthFactorCapacity(
      a: SkContainerAllocator,
      capacity: Int,
      growthFactor: Double,
    ): Int {
      TODO("Implement growthFactorCapacity")
    }
  }
}
