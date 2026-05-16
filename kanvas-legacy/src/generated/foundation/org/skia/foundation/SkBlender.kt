package org.skia.foundation

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkBlender : public SkFlattenable {
 * public:
 *     /**
 *      * Create a blender that implements the specified BlendMode.
 *      */
 *     static sk_sp<SkBlender> Mode(SkBlendMode mode);
 *
 * private:
 *     SkBlender() = default;
 *     friend class SkBlenderBase;
 * }
 * ```
 */
public open class SkBlender public constructor() : SkFlattenable() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkBlender> SkBlender::Mode(SkBlendMode mode) {
     *     return sk_ref_sp(GetBlendModeSingleton(mode));
     * }
     * ```
     */
    public fun mode(mode: SkBlendMode): Int {
      TODO("Implement mode")
    }
  }
}
