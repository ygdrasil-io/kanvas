package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkBlurStyle
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkMaskFilter : public SkFlattenable {
 * public:
 *     /** Create a blur maskfilter.
 *      *  @param style      The SkBlurStyle to use
 *      *  @param sigma      Standard deviation of the Gaussian blur to apply. Must be > 0.
 *      *  @param respectCTM if true the blur's sigma is modified by the CTM.
 *      *  @return The new blur maskfilter
 *      */
 *     static sk_sp<SkMaskFilter> MakeBlur(SkBlurStyle style, SkScalar sigma,
 *                                         bool respectCTM = true);
 *
 *     static sk_sp<SkMaskFilter> Deserialize(const void* data, size_t size,
 *                                            const SkDeserialProcs* procs = nullptr);
 *
 * private:
 *     static void RegisterFlattenables();
 *     friend class SkFlattenable;
 * }
 * ```
 */
public open class SkMaskFilter : SkFlattenable() {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkMaskFilter> SkMaskFilter::MakeBlur(SkBlurStyle style, SkScalar sigma, bool respectCTM) {
     *     if (SkIsFinite(sigma) && sigma > 0) {
     *         return sk_sp<SkMaskFilter>(new SkBlurMaskFilterImpl(sigma, style, respectCTM));
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun makeBlur(
      style: SkBlurStyle,
      sigma: SkScalar,
      respectCTM: Boolean = TODO(),
    ): Int {
      TODO("Implement makeBlur")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkMaskFilter> SkMaskFilter::Deserialize(const void* data, size_t size,
     *                                               const SkDeserialProcs* procs) {
     *     return sk_sp<SkMaskFilter>(static_cast<SkMaskFilter*>(
     *                                SkFlattenable::Deserialize(
     *                                kSkMaskFilter_Type, data, size, procs).release()));
     * }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMaskFilter::RegisterFlattenables() {
     *     sk_register_blur_maskfilter_createproc();
     * }
     * ```
     */
    private fun registerFlattenables() {
      TODO("Implement registerFlattenables")
    }
  }
}
