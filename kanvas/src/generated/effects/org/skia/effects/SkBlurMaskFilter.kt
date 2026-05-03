package org.skia.effects

/**
 * C++ original:
 * ```cpp
 * class SK_API SkBlurMaskFilter {
 * public:
 * #ifdef SK_SUPPORT_LEGACY_EMBOSSMASKFILTER
 *     /** Create an emboss maskfilter
 *         @param blurSigma    standard deviation of the Gaussian blur to apply
 *                             before applying lighting (e.g. 3)
 *         @param direction    array of 3 scalars [x, y, z] specifying the direction of the light source
 *         @param ambient      0...1 amount of ambient light
 *         @param specular     coefficient for specular highlights (e.g. 8)
 *         @return the emboss maskfilter
 *     */
 *     static sk_sp<SkMaskFilter> MakeEmboss(SkScalar blurSigma, const SkScalar direction[3],
 *                                           SkScalar ambient, SkScalar specular);
 * #endif
 * }
 * ```
 */
public open class SkBlurMaskFilter
