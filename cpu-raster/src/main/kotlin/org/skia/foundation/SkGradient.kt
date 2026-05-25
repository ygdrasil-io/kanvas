package org.skia.foundation

import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Small public mirror of Skia's `SkGradient` aggregate.
 *
 * This slice supports RGB interpolation spaces that can be expressed with the
 * existing `SkShader.makeWithWorkingColorSpace` wrapper, plus a bounded HSL
 * dedicated sampler. Remaining perceptual CSS spaces stay explicit
 * `STUB.GRADIENT_INTERPOLATION` failures.
 */
public class SkGradient(
    colors: IntArray,
    positions: FloatArray? = null,
    public val tileMode: SkTileMode = SkTileMode.kClamp,
    public val interpolation: Interpolation = Interpolation(),
) {
    public val colors: IntArray = colors.copyOf()
    public val positions: FloatArray? = positions?.copyOf()

    init {
        require(colors.isNotEmpty()) { "SkGradient requires at least one colour" }
        require(positions == null || positions.size == colors.size) {
            "positions.size (${positions?.size}) must match colors.size (${colors.size})"
        }
    }

    public class Interpolation(
        public val colorSpace: ColorSpace = ColorSpace.kDestination,
        public val hueMethod: HueMethod = HueMethod.kShorter,
        public val inPremul: InPremul = InPremul.kNo,
    ) {
        public enum class ColorSpace {
            kDestination,
            kSRGB,
            kSRGBLinear,
            kLab,
            kOKLab,
            kLCH,
            kOKLCH,
            kHSL,
            kHWB,
            kA98RGB,
            kProPhotoRGB,
            kDisplayP3,
            kRec2020,
        }

        public enum class HueMethod {
            kShorter,
            kLonger,
            kIncreasing,
            kDecreasing,
        }

        public enum class InPremul {
            kNo,
            kYes,
        }
    }

    internal fun workingColorSpaceOrNull(): SkColorSpace? {
        if (interpolation.hueMethod != Interpolation.HueMethod.kShorter) {
            unsupported("HueMethod.${interpolation.hueMethod}")
        }

        return when (interpolation.colorSpace) {
            Interpolation.ColorSpace.kDestination -> null
            Interpolation.ColorSpace.kSRGB -> SkColorSpace.makeSRGB()
            Interpolation.ColorSpace.kSRGBLinear -> SkColorSpace.makeSRGBLinear()
            Interpolation.ColorSpace.kA98RGB -> SkColorSpace.makeRGB(
                SkNamedTransferFn.kA98RGB,
                SkNamedGamut.kAdobeRGB,
            )
            Interpolation.ColorSpace.kProPhotoRGB -> SkColorSpace.MakeRGB(
                SkColorSpaceTransferFn.fromSkcms(SkNamedTransferFn.kProPhotoRGB),
                SkNamedPrimaries.kProPhotoRGB,
            )
            Interpolation.ColorSpace.kDisplayP3 -> SkColorSpace.makeRGB(
                SkNamedTransferFn.kSRGB,
                SkNamedGamut.kDisplayP3,
            )
            Interpolation.ColorSpace.kRec2020 -> SkColorSpace.makeRGB(
                SkNamedTransferFn.kRec2020,
                SkNamedGamut.kRec2020,
            )
            Interpolation.ColorSpace.kLab,
            Interpolation.ColorSpace.kOKLab,
            Interpolation.ColorSpace.kLCH,
            Interpolation.ColorSpace.kOKLCH,
            Interpolation.ColorSpace.kHSL,
            Interpolation.ColorSpace.kHWB -> unsupported("ColorSpace.${interpolation.colorSpace}")
        }
    }

    internal fun requiresDedicatedSampler(): Boolean =
        interpolation.colorSpace == Interpolation.ColorSpace.kHSL ||
            interpolation.colorSpace == Interpolation.ColorSpace.kLCH

    internal fun validateDedicatedSampler() {
        if (!requiresDedicatedSampler()) {
            unsupported("ColorSpace.${interpolation.colorSpace}")
        }
    }

    private fun unsupported(feature: String): Nothing =
        throw UnsupportedOperationException("STUB.GRADIENT_INTERPOLATION: $feature is not implemented")
}
