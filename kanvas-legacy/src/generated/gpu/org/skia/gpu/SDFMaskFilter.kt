package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkMaskFilter

/**
 * C++ original:
 * ```cpp
 * class SDFMaskFilter : public SkMaskFilter {
 * public:
 *     static sk_sp<SkMaskFilter> Make();
 * }
 * ```
 */
public open class SDFMaskFilter : SkMaskFilter() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkMaskFilter> SDFMaskFilter::Make() {
     *     return sk_sp<SkMaskFilter>(new SDFMaskFilterImpl());
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
