package org.skia.core

import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.modules.SkcmsMatrix3x3
import org.skia.modules.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * class SkColorFilterPriv {
 * public:
 *     static sk_sp<SkColorFilter> MakeGaussian();
 *
 *     // Make a color filter that will convert from src to dst.
 *     static sk_sp<SkColorFilter> MakeColorSpaceXform(sk_sp<SkColorSpace> src,
 *                                                     sk_sp<SkColorSpace> dst);
 *
 *     // Runs the child filter in a different working color format than usual (premul in
 *     // destination surface's color space), with all inputs and outputs expressed in this format.
 *     // Each non-null {tf,gamut,at} parameter overrides that particular aspect of the color format.
 *     static sk_sp<SkColorFilter> WithWorkingFormat(sk_sp<SkColorFilter> child,
 *                                                   const skcms_TransferFunction* tf,
 *                                                   const skcms_Matrix3x3* gamut,
 *                                                   const SkAlphaType* at);
 * }
 * ```
 */
public open class SkColorFilterPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilterPriv::MakeGaussian() {
     *     return sk_sp<SkColorFilter>(new SkGaussianColorFilter);
     * }
     * ```
     */
    public fun makeGaussian(): SkSp<SkColorFilter> {
      TODO("Implement makeGaussian")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilterPriv::MakeColorSpaceXform(sk_sp<SkColorSpace> src,
     *                                                             sk_sp<SkColorSpace> dst) {
     *     return sk_make_sp<SkColorSpaceXformColorFilter>(std::move(src), std::move(dst));
     * }
     * ```
     */
    public fun makeColorSpaceXform(src: SkSp<SkColorSpace>, dst: SkSp<SkColorSpace>): SkSp<SkColorFilter> {
      TODO("Implement makeColorSpaceXform")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilterPriv::WithWorkingFormat(sk_sp<SkColorFilter> child,
     *                                                           const skcms_TransferFunction* tf,
     *                                                           const skcms_Matrix3x3* gamut,
     *                                                           const SkAlphaType* at) {
     *     if (!child) {
     *         // This color filter applies a conversion from the 'dst' color space to the working format,
     *         // invokes the child, and then converts back to 'dst'. If `child` is null, it is the
     *         // identity color filter, so the conversion from 'dst' to working format and back to 'dst'
     *         // is also the identity.
     *         return nullptr;
     *     }
     *     return sk_make_sp<SkWorkingFormatColorFilter>(std::move(child), tf, gamut, at);
     * }
     * ```
     */
    public fun withWorkingFormat(
      child: SkSp<SkColorFilter>,
      tf: SkcmsTransferFunction?,
      gamut: SkcmsMatrix3x3?,
      at: SkAlphaType?,
    ): SkSp<SkColorFilter> {
      TODO("Implement withWorkingFormat")
    }
  }
}
