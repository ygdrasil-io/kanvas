package org.skia.gpu

import kotlin.Char

/**
 * C++ original:
 * ```cpp
 * class ComputePipeline : public Resource {
 * public:
 *     ~ComputePipeline() override = default;
 *
 *     // TODO(b/240615224): The pipeline should return an optional effective local workgroup
 *     // size if the value was statically assigned in the shader (when it's not possible to assign
 *     // them via specialization constants).
 *
 *     const char* getResourceType() const override { return "Compute Pipeline"; }
 *
 * protected:
 *     explicit ComputePipeline(const SharedContext*);
 * }
 * ```
 */
public open class ComputePipeline public constructor(
  sharedContext: SharedContext?,
) : Resource(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Compute Pipeline"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }
}
