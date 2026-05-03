package org.skia.gpu

import kotlin.Char

/**
 * C++ original:
 * ```cpp
 * class Sentinel : public Resource {
 * public:
 *     static Resource* Get() {
 *         static SkNoDestructor<Sentinel> kSentinel{};
 *         return kSentinel.get();
 *     }
 *
 * private:
 *     template <typename T>
 *     friend class ::SkNoDestructor;
 *
 *     // We can pass in a null shared context here because the only instance that is ever created is
 *     // wrapped in SkNoDestructor, and we never actually use it as a Resource.
 *     Sentinel() : Resource(/*sharedContext=*/nullptr, Ownership::kOwned, /*gpuMemorySize=*/0) {}
 *
 *     const char* getResourceType() const override { return "Sentinel"; }
 *
 *     void freeGpuData() override {}
 * }
 * ```
 */
public open class Sentinel public constructor() : Resource(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Sentinel"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeGpuData() override {}
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Resource* Get() {
     *         static SkNoDestructor<Sentinel> kSentinel{};
     *         return kSentinel.get();
     *     }
     * ```
     */
    public fun `get`(): Resource {
      TODO("Implement get")
    }
  }
}
