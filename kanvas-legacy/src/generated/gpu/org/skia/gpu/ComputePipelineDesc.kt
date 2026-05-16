package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ComputePipelineDesc {
 * public:
 *     // `computeStep` must outlive this `ComputePipelineDesc`.
 *     explicit ComputePipelineDesc(const ComputeStep* computeStep) : fComputeStep(computeStep) {}
 *
 *     bool operator==(const ComputePipelineDesc& that) const {
 *         return fComputeStep->uniqueID() == that.fComputeStep->uniqueID();
 *     }
 *
 *     const ComputeStep* computeStep() const { return fComputeStep; }
 *
 *     uint32_t uniqueID() const { return fComputeStep->uniqueID(); }
 *
 * private:
 *     const ComputeStep* fComputeStep;
 * }
 * ```
 */
public data class ComputePipelineDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * const ComputeStep* fComputeStep
   * ```
   */
  private val fComputeStep: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const ComputePipelineDesc& that) const {
   *         return fComputeStep->uniqueID() == that.fComputeStep->uniqueID();
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * const ComputeStep* computeStep() const { return fComputeStep; }
   * ```
   */
  public fun computeStep(): Int {
    TODO("Implement computeStep")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fComputeStep->uniqueID(); }
   * ```
   */
  public fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }
}
