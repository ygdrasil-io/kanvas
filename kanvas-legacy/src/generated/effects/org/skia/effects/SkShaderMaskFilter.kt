package org.skia.effects

import kotlin.Int
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkShaderMaskFilter {
 * public:
 *     static sk_sp<SkMaskFilter> Make(sk_sp<SkShader> shader);
 *
 * private:
 *     static void RegisterFlattenables();
 *     friend class SkFlattenable;
 * }
 * ```
 */
public open class SkShaderMaskFilter {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkMaskFilter> SkShaderMaskFilter::Make(sk_sp<SkShader> shader) {
     *     return shader ? sk_sp<SkMaskFilter>(new SkShaderMaskFilterImpl(std::move(shader))) : nullptr;
     * }
     * ```
     */
    public fun make(shader: SkSp<SkShader>): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkShaderMaskFilter::RegisterFlattenables() {
     *     SK_REGISTER_FLATTENABLE(SkShaderMaskFilterImpl);
     *     // Previous name
     *     SkFlattenable::Register("SkShaderMF", SkShaderMaskFilterImpl::CreateProc);
     * }
     * ```
     */
    private fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
