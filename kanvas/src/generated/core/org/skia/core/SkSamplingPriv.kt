package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSamplingOptions

/**
 * C++ original:
 * ```cpp
 * class SkSamplingPriv {
 * public:
 *     static size_t FlatSize(const SkSamplingOptions& options) {
 *         size_t size = sizeof(uint32_t);  // maxAniso
 *         if (!options.isAniso()) {
 *             size += 3 * sizeof(uint32_t);  // bool32 + [2 floats | 2 ints]
 *         }
 *         return size;
 *     }
 *
 *     // Returns true if the sampling can be ignored when the CTM is identity.
 *     static bool NoChangeWithIdentityMatrix(const SkSamplingOptions& sampling) {
 *         // If B == 0, the cubic resampler should have no effect for identity matrices
 *         // https://entropymine.com/imageworsener/bicubic/
 *         // We assume aniso has no effect with an identity transform.
 *         return !sampling.useCubic || sampling.cubic.B == 0;
 *     }
 *
 *     // Makes a fallback SkSamplingOptions for cases where anisotropic filtering is not allowed.
 *     // anisotropic filtering can access mip levels if present, but we don't add mipmaps to non-
 *     // mipmapped images when the user requests anisotropic. So we shouldn't fall back to a
 *     // sampling that would trigger mip map creation.
 *     static SkSamplingOptions AnisoFallback(bool imageIsMipped) {
 *         auto mm = imageIsMipped ? SkMipmapMode::kLinear : SkMipmapMode::kNone;
 *         return SkSamplingOptions(SkFilterMode::kLinear, mm);
 *     }
 *
 *     static SkSamplingOptions FromFQ(SkLegacyFQ fq, SkMediumAs behavior = kNearest_SkMediumAs) {
 *         switch (fq) {
 *             case kHigh_SkLegacyFQ:
 *                 return SkSamplingOptions(SkCubicResampler{1/3.0f, 1/3.0f});
 *             case kMedium_SkLegacyFQ:
 *                 return SkSamplingOptions(SkFilterMode::kLinear,
 *                                           behavior == kNearest_SkMediumAs ? SkMipmapMode::kNearest
 *                                                                           : SkMipmapMode::kLinear);
 *             case kLow_SkLegacyFQ:
 *                 return SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone);
 *             case kNone_SkLegacyFQ:
 *                 break;
 *         }
 *         return SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone);
 *     }
 * }
 * ```
 */
public open class SkSamplingPriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static size_t FlatSize(const SkSamplingOptions& options) {
     *         size_t size = sizeof(uint32_t);  // maxAniso
     *         if (!options.isAniso()) {
     *             size += 3 * sizeof(uint32_t);  // bool32 + [2 floats | 2 ints]
     *         }
     *         return size;
     *     }
     * ```
     */
    public fun flatSize(options: SkSamplingOptions): Int {
      TODO("Implement flatSize")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool NoChangeWithIdentityMatrix(const SkSamplingOptions& sampling) {
     *         // If B == 0, the cubic resampler should have no effect for identity matrices
     *         // https://entropymine.com/imageworsener/bicubic/
     *         // We assume aniso has no effect with an identity transform.
     *         return !sampling.useCubic || sampling.cubic.B == 0;
     *     }
     * ```
     */
    public fun noChangeWithIdentityMatrix(sampling: SkSamplingOptions): Boolean {
      TODO("Implement noChangeWithIdentityMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSamplingOptions AnisoFallback(bool imageIsMipped) {
     *         auto mm = imageIsMipped ? SkMipmapMode::kLinear : SkMipmapMode::kNone;
     *         return SkSamplingOptions(SkFilterMode::kLinear, mm);
     *     }
     * ```
     */
    public fun anisoFallback(imageIsMipped: Boolean): SkSamplingOptions {
      TODO("Implement anisoFallback")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkSamplingOptions FromFQ(SkLegacyFQ fq, SkMediumAs behavior = kNearest_SkMediumAs) {
     *         switch (fq) {
     *             case kHigh_SkLegacyFQ:
     *                 return SkSamplingOptions(SkCubicResampler{1/3.0f, 1/3.0f});
     *             case kMedium_SkLegacyFQ:
     *                 return SkSamplingOptions(SkFilterMode::kLinear,
     *                                           behavior == kNearest_SkMediumAs ? SkMipmapMode::kNearest
     *                                                                           : SkMipmapMode::kLinear);
     *             case kLow_SkLegacyFQ:
     *                 return SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone);
     *             case kNone_SkLegacyFQ:
     *                 break;
     *         }
     *         return SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone);
     *     }
     * ```
     */
    public fun fromFQ(fq: SkLegacyFQ, behavior: SkMediumAs = TODO()): SkSamplingOptions {
      TODO("Implement fromFQ")
    }
  }
}
