package org.skia.gpu

import kotlin.Char

/**
 * C++ original:
 * ```cpp
 * class Sampler : public Resource {
 * public:
 *     ~Sampler() override;
 *
 *     const char* getResourceType() const override { return "Sampler"; }
 *
 * protected:
 *     explicit Sampler(const SharedContext*);
 *
 * private:
 * }
 * ```
 */
public open class Sampler public constructor(
  param0: SharedContext,
) : Resource() {
  /**
   * C++ original:
   * ```cpp
   * explicit Sampler(const SharedContext*)
   * ```
   */
  public constructor(sharedContext: SharedContext?) : this(TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Sampler"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }
}
