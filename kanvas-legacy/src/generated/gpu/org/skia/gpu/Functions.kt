package org.skia.gpu

import PrecompileChildOptions
import SkAlignedSTStorage1
import `*toBackend)(SkSL`.Program
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.Pair
import kotlin.String
import kotlin.Triple
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import kotlin.collections.List
import org.skia.core.Backend
import org.skia.core.Draw
import org.skia.core.ETC1Block
import org.skia.core.Float2
import org.skia.core.Float4
import org.skia.core.GlyphRunList
import org.skia.core.Half2
import org.skia.core.SkCanvas
import org.skia.core.SkColorSpaceXformSteps
import org.skia.core.SkEnumBitMask
import org.skia.core.SkGlyph
import org.skia.core.SkGradientBaseShader
import org.skia.core.SkIDChangeListener
import org.skia.core.SkImageLazy
import org.skia.core.SkImagePicture
import org.skia.core.SkPMColor4f
import org.skia.core.SkPackedGlyphID
import org.skia.core.SkRasterClip
import org.skia.core.SkSLType
import org.skia.core.SkSpecialImage
import org.skia.core.SkStrikeDeviceInfo
import org.skia.core.SkStrikeSpec
import org.skia.core.SkStrokeRec
import org.skia.core.SkSurface
import org.skia.core.SkSweepGradient
import org.skia.core.SkTextureCompressionType
import org.skia.core.SkYUVAInfo
import org.skia.core.SkYUVAPixmaps
import org.skia.core.SkZip
import org.skia.core.StableKey
import org.skia.core.StrikeForGPU
import org.skia.core.StrikeForGPUCacheInterface
import org.skia.core.TArray
import org.skia.effects.SkGradient
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkAlpha
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMask
import org.skia.foundation.SkMipmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRecorder
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkStream
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWStream
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.gpu.ganesh.TextureReleaseProc
import org.skia.gpu.vk.VulkanYcbcrConversionInfo
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPathFillType
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize
import org.skia.math.SkV4
import org.skia.math.SkVector
import org.skia.ports.ReleaseContext
import org.skia.ports.SkCfp
import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTransferFunction
import org.skia.sksl.NativeShader
import org.skia.sksl.ProgramInterface
import org.skia.sksl.ProgramSettings
import org.skia.sksl.ShaderCaps
import org.skia.utils.Slug
import undefined.CFTypeRef
import undefined.MTLBlendFactor
import undefined.MTLBlendOperation
import undefined.MTLCompareFunction
import undefined.MTLPixelFormat
import undefined.MTLPrimitiveType
import undefined.MTLSamplerAddressMode
import undefined.MTLStencilOperation
import undefined.MTLVertexFormat
import undefined.MTLVertexStepFunction
import undefined.MtlTextureInfo
import undefined.RawElement
import undefined.SkColor4f
import undefined.TransformedShape
import wgpu.Instance

/**
 * C++ original:
 * ```cpp
 * const char* BlendFuncName(SkBlendMode mode) {
 *     switch (mode) {
 *         case SkBlendMode::kClear:      return "blend_clear";
 *         case SkBlendMode::kSrc:        return "blend_src";
 *         case SkBlendMode::kDst:        return "blend_dst";
 *         case SkBlendMode::kSrcOver:    return "blend_src_over";
 *         case SkBlendMode::kDstOver:    return "blend_dst_over";
 *         case SkBlendMode::kSrcIn:      return "blend_src_in";
 *         case SkBlendMode::kDstIn:      return "blend_dst_in";
 *         case SkBlendMode::kSrcOut:     return "blend_src_out";
 *         case SkBlendMode::kDstOut:     return "blend_dst_out";
 *         case SkBlendMode::kSrcATop:    return "blend_src_atop";
 *         case SkBlendMode::kDstATop:    return "blend_dst_atop";
 *         case SkBlendMode::kXor:        return "blend_xor";
 *         case SkBlendMode::kPlus:       return "blend_plus";
 *         case SkBlendMode::kModulate:   return "blend_modulate";
 *         case SkBlendMode::kScreen:     return "blend_screen";
 *         case SkBlendMode::kOverlay:    return "blend_overlay";
 *         case SkBlendMode::kDarken:     return "blend_darken";
 *         case SkBlendMode::kLighten:    return "blend_lighten";
 *         case SkBlendMode::kColorDodge: return "blend_color_dodge";
 *         case SkBlendMode::kColorBurn:  return "blend_color_burn";
 *         case SkBlendMode::kHardLight:  return "blend_hard_light";
 *         case SkBlendMode::kSoftLight:  return "blend_soft_light";
 *         case SkBlendMode::kDifference: return "blend_difference";
 *         case SkBlendMode::kExclusion:  return "blend_exclusion";
 *         case SkBlendMode::kMultiply:   return "blend_multiply";
 *         case SkBlendMode::kHue:        return "blend_hue";
 *         case SkBlendMode::kSaturation: return "blend_saturation";
 *         case SkBlendMode::kColor:      return "blend_color";
 *         case SkBlendMode::kLuminosity: return "blend_luminosity";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun blendFuncName(mode: SkBlendMode): Char {
  TODO("Implement blendFuncName")
}

/**
 * C++ original:
 * ```cpp
 * SkSpan<const float> GetPorterDuffBlendConstants(SkBlendMode mode) {
 *     // See sksl_gpu.sksl's blend_porter_duff function for explanation of values
 *     static constexpr float kClear[]      = {0,  0,  0,  0};
 *     static constexpr float kSrc[]        = {1,  0,  0,  0};
 *     static constexpr float kDst[]        = {0,  1,  0,  0};
 *     static constexpr float kSrcOver[]    = {1,  1,  0, -1};
 *     static constexpr float kDstOver[]    = {1,  1, -1,  0};
 *     static constexpr float kSrcIn[]      = {0,  0,  1,  0};
 *     static constexpr float kDstIn[]      = {0,  0,  0,  1};
 *     static constexpr float kSrcOut[]     = {1,  0, -1,  0};
 *     static constexpr float kDstOut[]     = {0,  1,  0, -1};
 *     static constexpr float kSrcATop[]    = {0,  1,  1, -1};
 *     static constexpr float kDstATop[]    = {1,  0, -1,  1};
 *     static constexpr float kXor[]        = {1,  1, -1, -1};
 *
 *     switch (mode) {
 *         case SkBlendMode::kClear:      return SkSpan(kClear);
 *         case SkBlendMode::kSrc:        return SkSpan(kSrc);
 *         case SkBlendMode::kDst:        return SkSpan(kDst);
 *         case SkBlendMode::kSrcOver:    return SkSpan(kSrcOver);
 *         case SkBlendMode::kDstOver:    return SkSpan(kDstOver);
 *         case SkBlendMode::kSrcIn:      return SkSpan(kSrcIn);
 *         case SkBlendMode::kDstIn:      return SkSpan(kDstIn);
 *         case SkBlendMode::kSrcOut:     return SkSpan(kSrcOut);
 *         case SkBlendMode::kDstOut:     return SkSpan(kDstOut);
 *         case SkBlendMode::kSrcATop:    return SkSpan(kSrcATop);
 *         case SkBlendMode::kDstATop:    return SkSpan(kDstATop);
 *         case SkBlendMode::kXor:        return SkSpan(kXor);
 *         default:                       return {};
 *     }
 * }
 * ```
 */
public fun getPorterDuffBlendConstants(mode: SkBlendMode): SkSpan<Float> {
  TODO("Implement getPorterDuffBlendConstants")
}

/**
 * C++ original:
 * ```cpp
 * ReducedBlendModeInfo GetReducedBlendModeInfo(SkBlendMode mode) {
 *     static constexpr float kHue[]        = {0, 1};
 *     static constexpr float kSaturation[] = {1, 1};
 *     static constexpr float kColor[]      = {0, 0};
 *     static constexpr float kLuminosity[] = {1, 0};
 *
 *     static constexpr float kOverlay[]    = {0};
 *     static constexpr float kHardLight[]  = {1};
 *
 *     static constexpr float kDarken[]     = {1};
 *     static constexpr float kLighten[]    = {-1};
 *
 *     // This switch must be kept in sync with BlendKey() in src/ganesh/glsl/GrGLSLBlend.cpp.
 *     switch (mode) {
 *         // Clear/src/dst are intentionally omitted; using the built-in blend_xxxxx functions is
 *         // preferable, since that gives us an opportunity to eliminate the src/dst entirely.
 *
 *         case SkBlendMode::kSrcOver:
 *         case SkBlendMode::kDstOver:
 *         case SkBlendMode::kSrcIn:
 *         case SkBlendMode::kDstIn:
 *         case SkBlendMode::kSrcOut:
 *         case SkBlendMode::kDstOut:
 *         case SkBlendMode::kSrcATop:
 *         case SkBlendMode::kDstATop:
 *         case SkBlendMode::kXor:        return {"blend_porter_duff",
 *                                                GetPorterDuffBlendConstants(mode)};
 *
 *         case SkBlendMode::kHue:        return {"blend_hslc", SkSpan(kHue)};
 *         case SkBlendMode::kSaturation: return {"blend_hslc", SkSpan(kSaturation)};
 *         case SkBlendMode::kColor:      return {"blend_hslc", SkSpan(kColor)};
 *         case SkBlendMode::kLuminosity: return {"blend_hslc", SkSpan(kLuminosity)};
 *
 *         case SkBlendMode::kOverlay:    return {"blend_overlay", SkSpan(kOverlay)};
 *         case SkBlendMode::kHardLight:  return {"blend_overlay", SkSpan(kHardLight)};
 *
 *         case SkBlendMode::kDarken:     return {"blend_darken", SkSpan(kDarken)};
 *         case SkBlendMode::kLighten:    return {"blend_darken", SkSpan(kLighten)};
 *
 *         default:                       return {BlendFuncName(mode), {}};
 *     }
 * }
 * ```
 */
public fun getReducedBlendModeInfo(mode: SkBlendMode): ReducedBlendModeInfo {
  TODO("Implement getReducedBlendModeInfo")
}

/**
 * C++ original:
 * ```cpp
 * const char *equation_string(skgpu::BlendEquation eq) {
 *     switch (eq) {
 *         case skgpu::BlendEquation::kAdd:             return "add";
 *         case skgpu::BlendEquation::kSubtract:        return "subtract";
 *         case skgpu::BlendEquation::kReverseSubtract: return "reverse_subtract";
 *         case skgpu::BlendEquation::kScreen:          return "screen";
 *         case skgpu::BlendEquation::kOverlay:         return "overlay";
 *         case skgpu::BlendEquation::kDarken:          return "darken";
 *         case skgpu::BlendEquation::kLighten:         return "lighten";
 *         case skgpu::BlendEquation::kColorDodge:      return "color_dodge";
 *         case skgpu::BlendEquation::kColorBurn:       return "color_burn";
 *         case skgpu::BlendEquation::kHardLight:       return "hard_light";
 *         case skgpu::BlendEquation::kSoftLight:       return "soft_light";
 *         case skgpu::BlendEquation::kDifference:      return "difference";
 *         case skgpu::BlendEquation::kExclusion:       return "exclusion";
 *         case skgpu::BlendEquation::kMultiply:        return "multiply";
 *         case skgpu::BlendEquation::kHSLHue:          return "hsl_hue";
 *         case skgpu::BlendEquation::kHSLSaturation:   return "hsl_saturation";
 *         case skgpu::BlendEquation::kHSLColor:        return "hsl_color";
 *         case skgpu::BlendEquation::kHSLLuminosity:   return "hsl_luminosity";
 *         case skgpu::BlendEquation::kIllegal:
 *             SkASSERT(false);
 *             return "<illegal>";
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun equationString(eq: BlendEquation): Char {
  TODO("Implement equationString")
}

/**
 * C++ original:
 * ```cpp
 * const char *coeff_string(skgpu::BlendCoeff coeff) {
 *     switch (coeff) {
 *         case skgpu::BlendCoeff::kZero:    return "zero";
 *         case skgpu::BlendCoeff::kOne:     return "one";
 *         case skgpu::BlendCoeff::kSC:      return "src_color";
 *         case skgpu::BlendCoeff::kISC:     return "inv_src_color";
 *         case skgpu::BlendCoeff::kDC:      return "dst_color";
 *         case skgpu::BlendCoeff::kIDC:     return "inv_dst_color";
 *         case skgpu::BlendCoeff::kSA:      return "src_alpha";
 *         case skgpu::BlendCoeff::kISA:     return "inv_src_alpha";
 *         case skgpu::BlendCoeff::kDA:      return "dst_alpha";
 *         case skgpu::BlendCoeff::kIDA:     return "inv_dst_alpha";
 *         case skgpu::BlendCoeff::kConstC:  return "const_color";
 *         case skgpu::BlendCoeff::kIConstC: return "inv_const_color";
 *         case skgpu::BlendCoeff::kS2C:     return "src2_color";
 *         case skgpu::BlendCoeff::kIS2C:    return "inv_src2_color";
 *         case skgpu::BlendCoeff::kS2A:     return "src2_alpha";
 *         case skgpu::BlendCoeff::kIS2A:    return "inv_src2_alpha";
 *         case skgpu::BlendCoeff::kIllegal:
 *             SkASSERT(false);
 *             return "<illegal>";
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun coeffString(coeff: BlendCoeff): Char {
  TODO("Implement coeffString")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap CreateIntegralTable(int width) {
 *     SkBitmap table;
 *
 *     if (width <= 0) {
 *         return table;
 *     }
 *
 *     if (!table.tryAllocPixels(SkImageInfo::MakeA8(width, 1))) {
 *         return table;
 *     }
 *     *table.getAddr8(0, 0) = 255;
 *     const float invWidth = 1.f / width;
 *     for (int i = 1; i < width - 1; ++i) {
 *         float x = (i + 0.5f) * invWidth;
 *         x = (-6 * x + 3) * SK_ScalarRoot2Over2;
 *         float integral = 0.5f * (std::erf(x) + 1.f);
 *         *table.getAddr8(i, 0) = SkToU8(sk_float_round2int(255.f * integral));
 *     }
 *
 *     *table.getAddr8(width - 1, 0) = 0;
 *     table.setImmutable();
 *     return table;
 * }
 * ```
 */
public fun createIntegralTable(width: Int): SkBitmap {
  TODO("Implement createIntegralTable")
}

/**
 * C++ original:
 * ```cpp
 * int ComputeIntegralTableWidth(float sixSigma) {
 *     // Check for NaN/infinity
 *     if (!SkIsFinite(sixSigma)) {
 *         return 0;
 *     }
 *     // Avoid overflow, covers both multiplying by 2 and finding next power of 2:
 *     // 2*((2^31-1)/4 + 1) = 2*(2^29-1) + 2 = 2^30 and SkNextPow2(2^30) = 2^30
 *     if (sixSigma > SK_MaxS32 / 4 + 1) {
 *         return 0;
 *     }
 *     // The texture we're producing represents the integral of a normal distribution over a
 *     // six-sigma range centered at zero. We want enough resolution so that the linear
 *     // interpolation done in texture lookup doesn't introduce noticeable artifacts. We
 *     // conservatively choose to have 2 texels for each dst pixel.
 *     int minWidth = 2 * ((int)std::ceil(sixSigma));
 *     // Bin by powers of 2 with a minimum so we get good profile reuse.
 *     return std::max(SkNextPow2(minWidth), 32);
 * }
 * ```
 */
public fun computeIntegralTableWidth(sixSigma: Float): Int {
  TODO("Implement computeIntegralTableWidth")
}

/**
 * C++ original:
 * ```cpp
 * static float make_unnormalized_half_kernel(float* halfKernel, int halfKernelSize, float sigma) {
 *     const float invSigma = 1.0f / sigma;
 *     const float b = -0.5f * invSigma * invSigma;
 *     float tot = 0.0f;
 *     // Compute half kernel values at half pixel steps out from the center.
 *     float t = 0.5f;
 *     for (int i = 0; i < halfKernelSize; ++i) {
 *         float value = expf(t * t * b);
 *         tot += value;
 *         halfKernel[i] = value;
 *         t += 1.0f;
 *     }
 *     return tot;
 * }
 * ```
 */
public fun makeUnnormalizedHalfKernel(
  halfKernel: Float?,
  halfKernelSize: Int,
  sigma: Float,
): Float {
  TODO("Implement makeUnnormalizedHalfKernel")
}

/**
 * C++ original:
 * ```cpp
 * static void make_half_kernel_and_summed_table(float* halfKernel,
 *                                               float* summedHalfKernel,
 *                                               int halfKernelSize,
 *                                               float sigma) {
 *     // The half kernel should sum to 0.5 not 1.0.
 *     const float tot = 2.0f * make_unnormalized_half_kernel(halfKernel, halfKernelSize, sigma);
 *     float sum = 0.0f;
 *     for (int i = 0; i < halfKernelSize; ++i) {
 *         halfKernel[i] /= tot;
 *         sum += halfKernel[i];
 *         summedHalfKernel[i] = sum;
 *     }
 * }
 * ```
 */
public fun makeHalfKernelAndSummedTable(
  halfKernel: Float?,
  summedHalfKernel: Float?,
  halfKernelSize: Int,
  sigma: Float,
) {
  TODO("Implement makeHalfKernelAndSummedTable")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_kernel_in_y(float* results,
 *                               int numSteps,
 *                               float firstX,
 *                               float circleR,
 *                               int halfKernelSize,
 *                               const float* summedHalfKernelTable) {
 *     float x = firstX;
 *     for (int i = 0; i < numSteps; ++i, x += 1.0f) {
 *         if (x < -circleR || x > circleR) {
 *             results[i] = 0;
 *             continue;
 *         }
 *         float y = sqrtf(circleR * circleR - x * x);
 *         // In the column at x we exit the circle at +y and -y
 *         // The summed table entry j is actually reflects an offset of j + 0.5.
 *         y -= 0.5f;
 *         int yInt = SkScalarFloorToInt(y);
 *         SkASSERT(yInt >= -1);
 *         if (y < 0) {
 *             results[i] = (y + 0.5f) * summedHalfKernelTable[0];
 *         } else if (yInt >= halfKernelSize - 1) {
 *             results[i] = 0.5f;
 *         } else {
 *             float yFrac = y - yInt;
 *             results[i] = (1.0f - yFrac) * summedHalfKernelTable[yInt] +
 *                          yFrac * summedHalfKernelTable[yInt + 1];
 *         }
 *     }
 * }
 * ```
 */
public fun applyKernelInY(
  results: Float?,
  numSteps: Int,
  firstX: Float,
  circleR: Float,
  halfKernelSize: Int,
  summedHalfKernelTable: Float?,
) {
  TODO("Implement applyKernelInY")
}

/**
 * C++ original:
 * ```cpp
 * static uint8_t eval_at(float evalX,
 *                        float circleR,
 *                        const float* halfKernel,
 *                        int halfKernelSize,
 *                        const float* yKernelEvaluations) {
 *     float acc = 0;
 *
 *     float x = evalX - halfKernelSize;
 *     for (int i = 0; i < halfKernelSize; ++i, x += 1.0f) {
 *         if (x < -circleR || x > circleR) {
 *             continue;
 *         }
 *         float verticalEval = yKernelEvaluations[i];
 *         acc += verticalEval * halfKernel[halfKernelSize - i - 1];
 *     }
 *     for (int i = 0; i < halfKernelSize; ++i, x += 1.0f) {
 *         if (x < -circleR || x > circleR) {
 *             continue;
 *         }
 *         float verticalEval = yKernelEvaluations[i + halfKernelSize];
 *         acc += verticalEval * halfKernel[i];
 *     }
 *     // Since we applied a half kernel in y we multiply acc by 2 (the circle is symmetric about
 *     // the x axis).
 *     return SkUnitScalarClampToByte(2.0f * acc);
 * }
 * ```
 */
public fun evalAt(
  evalX: Float,
  circleR: Float,
  halfKernel: Float?,
  halfKernelSize: Int,
  yKernelEvaluations: Float?,
): UByte {
  TODO("Implement evalAt")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap CreateCircleProfile(float sigma, float radius, int profileWidth) {
 *     const int numSteps = profileWidth;
 *
 *     // The full kernel is 6 sigmas wide. SkScalarCeilToInt saturates to a number large enough to
 *     // still detect overflow in the kernel size calcutions.
 *     int halfKernelSize = SkScalarCeilToInt(6.0f * sigma);
 *     // and this is small enough that the rest of the `halfKernelSize` math won't overflow
 *     SkASSERT(halfKernelSize <= SK_MaxS32FitsInFloat &&
 *              SK_MaxS32FitsInFloat <= std::numeric_limits<int32_t>::max() - 1);
 *     // Round up to next multiple of 2 and then divide by 2.
 *     halfKernelSize = ((halfKernelSize + 1) & ~1) >> 1;
 *
 *     // The full internal allocations will be numSteps + 4*halfKernelSize, so if that would overflow
 *     // then return an empty bitmap.
 *     static constexpr int kAllocLimit = std::numeric_limits<int32_t>::max() >> 2;
 *
 *     SkBitmap bitmap;
 *     if (numSteps > kAllocLimit || halfKernelSize > (kAllocLimit - numSteps) ||
 *         !bitmap.tryAllocPixels(SkImageInfo::MakeA8(profileWidth, 1))) {
 *         return bitmap;
 *     }
 *
 *     // Number of x steps at which to apply kernel in y to cover all the profile samples in x.
 *     const int numYSteps = numSteps + 2 * halfKernelSize;
 *
 *     skia_private::AutoTArray<float> bulkAlloc(halfKernelSize + halfKernelSize + numYSteps);
 *     float* halfKernel = bulkAlloc.get();
 *     float* summedKernel = bulkAlloc.get() + halfKernelSize;
 *     float* yEvals = bulkAlloc.get() + 2 * halfKernelSize;
 *     make_half_kernel_and_summed_table(halfKernel, summedKernel, halfKernelSize, sigma);
 *
 *     float firstX = -halfKernelSize + 0.5f;
 *     apply_kernel_in_y(yEvals, numYSteps, firstX, radius, halfKernelSize, summedKernel);
 *
 *     uint8_t* profile = bitmap.getAddr8(0, 0);
 *     for (int i = 0; i < numSteps - 1; ++i) {
 *         float evalX = i + 0.5f;
 *         profile[i] = eval_at(evalX, radius, halfKernel, halfKernelSize, yEvals + i);
 *     }
 *     // Ensure the tail of the Gaussian goes to zero.
 *     profile[numSteps - 1] = 0;
 *
 *     bitmap.setImmutable();
 *     return bitmap;
 * }
 * ```
 */
public fun createCircleProfile(
  sigma: Float,
  radius: Float,
  profileWidth: Int,
): SkBitmap {
  TODO("Implement createCircleProfile")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap CreateHalfPlaneProfile(int profileWidth) {
 *     SkASSERT(!(profileWidth & 0x1));
 *
 *     SkBitmap bitmap;
 *     if (!bitmap.tryAllocPixels(SkImageInfo::MakeA8(profileWidth, 1))) {
 *         return bitmap;
 *     }
 *
 *     uint8_t* profile = bitmap.getAddr8(0, 0);
 *
 *     // The full kernel is 6 sigmas wide.
 *     const float sigma = profileWidth / 6.0f;
 *     const int halfKernelSize = profileWidth / 2;
 *
 *     skia_private::AutoTArray<float> halfKernel(halfKernelSize);
 *
 *     // The half kernel should sum to 0.5.
 *     const float tot = 2.0f * make_unnormalized_half_kernel(halfKernel.get(), halfKernelSize, sigma);
 *     float sum = 0.0f;
 *     // Populate the profile from the right edge to the middle.
 *     for (int i = 0; i < halfKernelSize; ++i) {
 *         halfKernel[halfKernelSize - i - 1] /= tot;
 *         sum += halfKernel[halfKernelSize - i - 1];
 *         profile[profileWidth - i - 1] = SkUnitScalarClampToByte(sum);
 *     }
 *     // Populate the profile from the middle to the left edge (by flipping the half kernel and
 *     // continuing the summation).
 *     for (int i = 0; i < halfKernelSize; ++i) {
 *         sum += halfKernel[i];
 *         profile[halfKernelSize - i - 1] = SkUnitScalarClampToByte(sum);
 *     }
 *     // Ensure the tail of the Gaussian goes to zero.
 *     profile[profileWidth - 1] = 0;
 *
 *     bitmap.setImmutable();
 *     return bitmap;
 * }
 * ```
 */
public fun createHalfPlaneProfile(profileWidth: Int): SkBitmap {
  TODO("Implement createHalfPlaneProfile")
}

/**
 * C++ original:
 * ```cpp
 * static uint8_t eval_V(float top, int y, const uint8_t* integral, int integralSize, float sixSigma) {
 *     if (top < 0) {
 *         return 0;  // an empty column
 *     }
 *
 *     float fT = (top - y - 0.5f) * (integralSize / sixSigma);
 *     if (fT < 0) {
 *         return 255;
 *     } else if (fT >= integralSize - 1) {
 *         return 0;
 *     }
 *
 *     int lower = (int)fT;
 *     float frac = fT - lower;
 *
 *     SkASSERT(lower + 1 < integralSize);
 *
 *     return integral[lower] * (1.0f - frac) + integral[lower + 1] * frac;
 * }
 * ```
 */
public fun evalV(
  top: Float,
  y: Int,
  integral: UByte?,
  integralSize: Int,
  sixSigma: Float,
): UByte {
  TODO("Implement evalV")
}

/**
 * C++ original:
 * ```cpp
 * static uint8_t eval_H(int x,
 *                       int y,
 *                       const std::vector<float>& topVec,
 *                       const float* kernel,
 *                       int kernelSize,
 *                       const uint8_t* integral,
 *                       int integralSize,
 *                       float sixSigma) {
 *     SkASSERT(0 <= x && x < (int)topVec.size());
 *     SkASSERT(kernelSize % 2);
 *
 *     float accum = 0.0f;
 *
 *     int xSampleLoc = x - (kernelSize / 2);
 *     for (int i = 0; i < kernelSize; ++i, ++xSampleLoc) {
 *         if (xSampleLoc < 0 || xSampleLoc >= (int)topVec.size()) {
 *             continue;
 *         }
 *
 *         accum += kernel[i] * eval_V(topVec[xSampleLoc], y, integral, integralSize, sixSigma);
 *     }
 *
 *     return accum + 0.5f;
 * }
 * ```
 */
public fun evalH(
  x: Int,
  y: Int,
  topVec: List<Float>,
  kernel: Float?,
  kernelSize: Int,
  integral: UByte?,
  integralSize: Int,
  sixSigma: Float,
): UByte {
  TODO("Implement evalH")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap CreateRRectBlurMask(const SkRRect& rrectToDraw, const SkISize& dimensions, float sigma) {
 *     SkASSERT(!skgpu::BlurIsEffectivelyIdentity(sigma));
 *     int radius = skgpu::BlurSigmaRadius(sigma);
 *     int kernelSize = skgpu::BlurKernelWidth(radius);
 *
 *     SkASSERT(kernelSize % 2);
 *     SkASSERT(dimensions.width() % 2);
 *     SkASSERT(dimensions.height() % 2);
 *
 *     SkVector radii = rrectToDraw.getSimpleRadii();
 *     SkASSERT(SkScalarNearlyEqual(radii.fX, radii.fY));
 *
 *     const int halfWidthPlus1 = (dimensions.width() / 2) + 1;
 *     const int halfHeightPlus1 = (dimensions.height() / 2) + 1;
 *
 *     std::unique_ptr<float[]> kernel(new float[kernelSize]);
 *     skgpu::Compute1DBlurKernel(sigma, radius, SkSpan<float>(kernel.get(), kernelSize));
 *
 *     const int tableWidth = ComputeIntegralTableWidth(6.0f * sigma);
 *     SkBitmap integral = CreateIntegralTable(tableWidth);
 *     if (integral.empty()) {
 *         return {};
 *     }
 *
 *     SkBitmap result;
 *     if (!result.tryAllocPixels(SkImageInfo::MakeA8(dimensions.width(), dimensions.height()))) {
 *         return {};
 *     }
 *
 *     std::vector<float> topVec;
 *     topVec.reserve(dimensions.width());
 *     for (int x = 0; x < dimensions.width(); ++x) {
 *         if (x < rrectToDraw.rect().fLeft || x > rrectToDraw.rect().fRight) {
 *             topVec.push_back(-1);
 *         } else {
 *             if (x + 0.5f < rrectToDraw.rect().fLeft + radii.fX) {  // in the circular section
 *                 float xDist = rrectToDraw.rect().fLeft + radii.fX - x - 0.5f;
 *                 float h = sqrtf(radii.fX * radii.fX - xDist * xDist);
 *                 SkASSERT(0 <= h && h < radii.fY);
 *                 topVec.push_back(rrectToDraw.rect().fTop + radii.fX - h + 3 * sigma);
 *             } else {
 *                 topVec.push_back(rrectToDraw.rect().fTop + 3 * sigma);
 *             }
 *         }
 *     }
 *
 *     for (int y = 0; y < halfHeightPlus1; ++y) {
 *         uint8_t* scanline = result.getAddr8(0, y);
 *
 *         for (int x = 0; x < halfWidthPlus1; ++x) {
 *             scanline[x] = eval_H(x,
 *                                  y,
 *                                  topVec,
 *                                  kernel.get(),
 *                                  kernelSize,
 *                                  integral.getAddr8(0, 0),
 *                                  integral.width(),
 *                                  6.0f * sigma);
 *             scanline[dimensions.width() - x - 1] = scanline[x];
 *         }
 *
 *         memcpy(result.getAddr8(0, dimensions.height() - y - 1), scanline, result.rowBytes());
 *     }
 *
 *     result.setImmutable();
 *     return result;
 * }
 * ```
 */
public fun createRRectBlurMask(
  rrectToDraw: SkRRect,
  dimensions: SkISize,
  sigma: Float,
): SkBitmap {
  TODO("Implement createRRectBlurMask")
}

/**
 * C++ original:
 * ```cpp
 * constexpr BlendFormula MakeCoeffFormula(skgpu::BlendCoeff srcCoeff, skgpu::BlendCoeff dstCoeff) {
 *     // When the coeffs are (Zero, Zero) or (Zero, One) we set the primary output to none.
 *     return (skgpu::BlendCoeff::kZero == srcCoeff &&
 *             (skgpu::BlendCoeff::kZero == dstCoeff || skgpu::BlendCoeff::kOne == dstCoeff))
 *            ? BlendFormula(BlendFormula::kNone_OutputType, BlendFormula::kNone_OutputType,
 *                           skgpu::BlendEquation::kAdd, skgpu::BlendCoeff::kZero, dstCoeff)
 *            : BlendFormula(BlendFormula::kModulate_OutputType, BlendFormula::kNone_OutputType,
 *                           skgpu::BlendEquation::kAdd, srcCoeff, dstCoeff);
 * }
 * ```
 */
public fun makeCoeffFormula(srcCoeff: BlendCoeff, dstCoeff: BlendCoeff): BlendFormula {
  TODO("Implement makeCoeffFormula")
}

/**
 * C++ original:
 * ```cpp
 * constexpr BlendFormula MakeSAModulateFormula(skgpu::BlendCoeff srcCoeff,
 *                                              skgpu::BlendCoeff dstCoeff) {
 *     return BlendFormula(BlendFormula::kSAModulate_OutputType, BlendFormula::kNone_OutputType,
 *                         skgpu::BlendEquation::kAdd, srcCoeff, dstCoeff);
 * }
 * ```
 */
public fun makeSAModulateFormula(srcCoeff: BlendCoeff, dstCoeff: BlendCoeff): BlendFormula {
  TODO("Implement makeSAModulateFormula")
}

/**
 * C++ original:
 * ```cpp
 * constexpr BlendFormula MakeCoverageFormula(BlendFormula::OutputType oneMinusDstCoeffModulateOutput,
 *                                            skgpu::BlendCoeff srcCoeff) {
 *     return BlendFormula(BlendFormula::kModulate_OutputType, oneMinusDstCoeffModulateOutput,
 *                         skgpu::BlendEquation::kAdd, srcCoeff, skgpu::BlendCoeff::kIS2C);
 * }
 * ```
 */
public fun makeCoverageFormula(oneMinusDstCoeffModulateOutput: BlendFormula.OutputType, srcCoeff: BlendCoeff): BlendFormula {
  TODO("Implement makeCoverageFormula")
}

/**
 * C++ original:
 * ```cpp
 * constexpr BlendFormula MakeCoverageSrcCoeffZeroFormula(
 *         BlendFormula::OutputType oneMinusDstCoeffModulateOutput) {
 *     return BlendFormula(oneMinusDstCoeffModulateOutput, BlendFormula::kNone_OutputType,
 *                         skgpu::BlendEquation::kReverseSubtract, skgpu::BlendCoeff::kDC,
 *                         skgpu::BlendCoeff::kOne);
 * }
 * ```
 */
public fun makeCoverageSrcCoeffZeroFormula(oneMinusDstCoeffModulateOutput: BlendFormula.OutputType): BlendFormula {
  TODO("Implement makeCoverageSrcCoeffZeroFormula")
}

/**
 * C++ original:
 * ```cpp
 * constexpr BlendFormula MakeCoverageDstCoeffZeroFormula(skgpu::BlendCoeff srcCoeff) {
 *     return BlendFormula(BlendFormula::kModulate_OutputType, BlendFormula::kCoverage_OutputType,
 *                         skgpu::BlendEquation::kAdd, srcCoeff, skgpu::BlendCoeff::kIS2A);
 * }
 * ```
 */
public fun makeCoverageDstCoeffZeroFormula(srcCoeff: BlendCoeff): BlendFormula {
  TODO("Implement makeCoverageDstCoeffZeroFormula")
}

/**
 * C++ original:
 * ```cpp
 * BlendFormula GetBlendFormula(bool isOpaque, bool hasCoverage, SkBlendMode xfermode) {
 *     SkASSERT((unsigned)xfermode <= (unsigned)SkBlendMode::kLastCoeffMode);
 *     return gBlendTable[isOpaque][hasCoverage][(int)xfermode];
 * }
 * ```
 */
public fun getBlendFormula(
  isOpaque: Boolean,
  hasCoverage: Boolean,
  xfermode: SkBlendMode,
): BlendFormula {
  TODO("Implement getBlendFormula")
}

/**
 * C++ original:
 * ```cpp
 * BlendFormula GetLCDBlendFormula(SkBlendMode xfermode) {
 *     SkASSERT((unsigned)xfermode <= (unsigned)SkBlendMode::kLastCoeffMode);
 *     return gLCDBlendTable[(int)xfermode];
 * }
 * ```
 */
public fun getLCDBlendFormula(xfermode: SkBlendMode): BlendFormula {
  TODO("Implement getLCDBlendFormula")
}

/**
 * C++ original:
 * ```cpp
 * static int test_table_entry(int rOrig, int gOrig, int bOrig,
 *                             int r8, int g8, int b8,
 *                             int table, int offset) {
 *     SkASSERT(0 <= table && table < 8);
 *     SkASSERT(0 <= offset && offset < 4);
 *
 *     r8 = SkTPin<int>(r8 + kETC1ModifierTables[table][offset], 0, 255);
 *     g8 = SkTPin<int>(g8 + kETC1ModifierTables[table][offset], 0, 255);
 *     b8 = SkTPin<int>(b8 + kETC1ModifierTables[table][offset], 0, 255);
 *
 *     return SkTAbs(rOrig - r8) + SkTAbs(gOrig - g8) + SkTAbs(bOrig - b8);
 * }
 * ```
 */
public fun testTableEntry(
  rOrig: Int,
  gOrig: Int,
  bOrig: Int,
  r8: Int,
  g8: Int,
  b8: Int,
  table: Int,
  offset: Int,
): Int {
  TODO("Implement testTableEntry")
}

/**
 * C++ original:
 * ```cpp
 * static void create_etc1_block(SkColor col, ETC1Block* block) {
 *     uint32_t high = 0;
 *     uint32_t low = 0;
 *
 *     int rOrig = SkColorGetR(col);
 *     int gOrig = SkColorGetG(col);
 *     int bOrig = SkColorGetB(col);
 *
 *     int r5 = SkMulDiv255Round(31, rOrig);
 *     int g5 = SkMulDiv255Round(31, gOrig);
 *     int b5 = SkMulDiv255Round(31, bOrig);
 *
 *     int r8 = extend_5To8bits(r5);
 *     int g8 = extend_5To8bits(g5);
 *     int b8 = extend_5To8bits(b5);
 *
 *     // We always encode solid color textures in differential mode (i.e., with a 555 base color) but
 *     // with zero diffs (i.e., bits 26-24, 18-16 and 10-8 are left 0).
 *     high |= (r5 << 27) | (g5 << 19) | (b5 << 11) | kDiffBit;
 *
 *     int bestTableIndex = 0, bestPixelIndex = 0;
 *     int bestSoFar = 1024;
 *     for (int tableIndex = 0; tableIndex < kNumETC1ModifierTables; ++tableIndex) {
 *         for (int pixelIndex = 0; pixelIndex < kNumETC1PixelIndices; ++pixelIndex) {
 *             int score = test_table_entry(rOrig, gOrig, bOrig, r8, g8, b8,
 *                                          tableIndex, pixelIndex);
 *
 *             if (bestSoFar > score) {
 *                 bestSoFar = score;
 *                 bestTableIndex = tableIndex;
 *                 bestPixelIndex = pixelIndex;
 *             }
 *         }
 *     }
 *
 *     high |= (bestTableIndex << 5) | (bestTableIndex << 2);
 *
 *     if (bestPixelIndex & 0x1) {
 *         low |= 0xFFFF;
 *     }
 *     if (bestPixelIndex & 0x2) {
 *         low |= 0xFFFF0000;
 *     }
 *
 *     block->fHigh = SkBSwap32(high);
 *     block->fLow = SkBSwap32(low);
 * }
 * ```
 */
public fun createEtc1Block(col: SkColor, block: ETC1Block?) {
  TODO("Implement createEtc1Block")
}

/**
 * C++ original:
 * ```cpp
 * static int num_ETC1_blocks(int w, int h) {
 *     w = num_4x4_blocks(w);
 *     h = num_4x4_blocks(h);
 *
 *     return w * h;
 * }
 * ```
 */
public fun numETC1Blocks(w: Int, h: Int): Int {
  TODO("Implement numETC1Blocks")
}

/**
 * C++ original:
 * ```cpp
 * size_t NumCompressedBlocks(SkTextureCompressionType type, SkISize baseDimensions) {
 *     switch (type) {
 *         case SkTextureCompressionType::kNone:
 *             return baseDimensions.width() * baseDimensions.height();
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: {
 *             int numBlocksWidth = num_4x4_blocks(baseDimensions.width());
 *             int numBlocksHeight = num_4x4_blocks(baseDimensions.height());
 *
 *             return numBlocksWidth * numBlocksHeight;
 *         }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun numCompressedBlocks(type: SkTextureCompressionType, baseDimensions: SkISize): ULong {
  TODO("Implement numCompressedBlocks")
}

/**
 * C++ original:
 * ```cpp
 * size_t CompressedRowBytes(SkTextureCompressionType type, int width) {
 *     switch (type) {
 *         case SkTextureCompressionType::kNone:
 *             return 0;
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: {
 *             int numBlocksWidth = num_4x4_blocks(width);
 *
 *             static_assert(sizeof(ETC1Block) == sizeof(BC1Block));
 *             return numBlocksWidth * sizeof(ETC1Block);
 *         }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun compressedRowBytes(type: SkTextureCompressionType, width: Int): ULong {
  TODO("Implement compressedRowBytes")
}

/**
 * C++ original:
 * ```cpp
 * SkISize CompressedDimensions(SkTextureCompressionType type, SkISize baseDimensions) {
 *     switch (type) {
 *         case SkTextureCompressionType::kNone:
 *             return baseDimensions;
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: {
 *             SkISize blockDims = CompressedDimensionsInBlocks(type, baseDimensions);
 *             // Each BC1_RGB8_UNORM and ETC1 block has 16 pixels
 *             return { 4 * blockDims.fWidth, 4 * blockDims.fHeight };
 *         }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun compressedDimensions(type: SkTextureCompressionType, baseDimensions: SkISize): SkISize {
  TODO("Implement compressedDimensions")
}

/**
 * C++ original:
 * ```cpp
 * SkISize CompressedDimensionsInBlocks(SkTextureCompressionType type, SkISize baseDimensions) {
 *     switch (type) {
 *         case SkTextureCompressionType::kNone:
 *             return baseDimensions;
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: {
 *             int numBlocksWidth = num_4x4_blocks(baseDimensions.width());
 *             int numBlocksHeight = num_4x4_blocks(baseDimensions.height());
 *
 *             // Each BC1_RGB8_UNORM and ETC1 block has 16 pixels
 *             return { numBlocksWidth, numBlocksHeight };
 *         }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun compressedDimensionsInBlocks(type: SkTextureCompressionType, baseDimensions: SkISize): SkISize {
  TODO("Implement compressedDimensionsInBlocks")
}

/**
 * C++ original:
 * ```cpp
 * static void fillin_ETC1_with_color(SkISize dimensions, const SkColor4f& colorf, char* dest) {
 *     SkColor color = colorf.toSkColor();
 *
 *     ETC1Block block;
 *     create_etc1_block(color, &block);
 *
 *     int numBlocks = num_ETC1_blocks(dimensions.width(), dimensions.height());
 *
 *     for (int i = 0; i < numBlocks; ++i) {
 *         memcpy(dest, &block, sizeof(ETC1Block));
 *         dest += sizeof(ETC1Block);
 *     }
 * }
 * ```
 */
public fun fillinETC1WithColor(
  dimensions: SkISize,
  colorf: SkColor4f,
  dest: String?,
) {
  TODO("Implement fillinETC1WithColor")
}

/**
 * C++ original:
 * ```cpp
 * static void fillin_BC1_with_color(SkISize dimensions, const SkColor4f& colorf, char* dest) {
 *     SkColor color = colorf.toSkColor();
 *
 *     BC1Block block;
 *     create_BC1_block(color, color, &block);
 *
 *     int numBlocks = num_ETC1_blocks(dimensions.width(), dimensions.height());
 *
 *     for (int i = 0; i < numBlocks; ++i) {
 *         memcpy(dest, &block, sizeof(BC1Block));
 *         dest += sizeof(BC1Block);
 *     }
 * }
 * ```
 */
public fun fillinBC1WithColor(
  dimensions: SkISize,
  colorf: SkColor4f,
  dest: String?,
) {
  TODO("Implement fillinBC1WithColor")
}

/**
 * C++ original:
 * ```cpp
 * void FillInCompressedData(SkTextureCompressionType type,
 *                           SkISize dimensions,
 *                           skgpu::Mipmapped mipmapped,
 *                           char* dstPixels,
 *                           const SkColor4f& colorf) {
 *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
 *
 *     int numMipLevels = 1;
 *     if (mipmapped == skgpu::Mipmapped::kYes) {
 *         numMipLevels = SkMipmap::ComputeLevelCount(dimensions) + 1;
 *     }
 *
 *     size_t offset = 0;
 *
 *     for (int i = 0; i < numMipLevels; ++i) {
 *         size_t levelSize = SkCompressedDataSize(type, dimensions, nullptr, false);
 *
 *         if (SkTextureCompressionType::kETC2_RGB8_UNORM == type) {
 *             fillin_ETC1_with_color(dimensions, colorf, &dstPixels[offset]);
 *         } else {
 *             SkASSERT(type == SkTextureCompressionType::kBC1_RGB8_UNORM ||
 *                      type == SkTextureCompressionType::kBC1_RGBA8_UNORM);
 *             fillin_BC1_with_color(dimensions, colorf, &dstPixels[offset]);
 *         }
 *
 *         offset += levelSize;
 *         dimensions = {std::max(1, dimensions.width()/2), std::max(1, dimensions.height()/2)};
 *     }
 * }
 * ```
 */
public fun fillInCompressedData(
  type: SkTextureCompressionType,
  dimensions: SkISize,
  mipmapped: Mipmapped,
  dstPixels: String?,
  colorf: SkColor4f,
) {
  TODO("Implement fillInCompressedData")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t ResourceKeyHash(const uint32_t* data, size_t size) {
 *     return SkChecksum::Hash32(data, size);
 * }
 * ```
 */
public fun resourceKeyHash(`data`: UInt?, size: ULong): UInt {
  TODO("Implement resourceKeyHash")
}

/**
 * C++ original:
 * ```cpp
 * float DitherRangeForConfig(SkColorType dstColorType) {
 *     SkASSERT(dstColorType != kUnknown_SkColorType);
 *
 *     // We use 1 / (2^bitdepth-1) as the range since each channel can hold 2^bitdepth values
 *     switch (dstColorType) {
 *         // 4 bit
 *         case kARGB_4444_SkColorType:
 *             return 1 / 15.f;
 *
 *         // 6 bit
 *         case kRGB_565_SkColorType:
 *             return 1 / 63.f;
 *
 *         // 8 bit
 *         case kAlpha_8_SkColorType:
 *         case kGray_8_SkColorType:
 *         case kR8_unorm_SkColorType:
 *         case kR8G8_unorm_SkColorType:
 *         case kRGB_888x_SkColorType:
 *         case kRGBA_8888_SkColorType:
 *         case kSRGBA_8888_SkColorType:
 *         case kBGRA_8888_SkColorType:
 *             return 1 / 255.f;
 *
 *         // 10 bit
 *         case kRGBA_1010102_SkColorType:
 *         case kBGRA_1010102_SkColorType:
 *         case kRGB_101010x_SkColorType:
 *         case kBGR_101010x_SkColorType:
 *         case kBGR_101010x_XR_SkColorType:
 *         case kBGRA_10101010_XR_SkColorType:
 *         case kRGBA_10x6_SkColorType:
 *             return 1 / 1023.f;
 *
 *         // 16 bit
 *         case kA16_unorm_SkColorType:
 *         case kR16_unorm_SkColorType:
 *         case kR16G16_unorm_SkColorType:
 *         case kR16G16B16A16_unorm_SkColorType:
 *             return 1 / 32767.f;
 *
 *         // Unknown
 *         case kUnknown_SkColorType:
 *         // Half
 *         case kA16_float_SkColorType:
 *         case kR16G16_float_SkColorType:
 *         case kRGBA_F16_SkColorType:
 *         case kRGB_F16F16F16x_SkColorType:
 *         case kRGBA_F16Norm_SkColorType:
 *         // Float
 *         case kRGBA_F32_SkColorType:
 *             return 0.f; // no dithering
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun ditherRangeForConfig(dstColorType: SkColorType): Float {
  TODO("Implement ditherRangeForConfig")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap MakeDitherLUT() {
 *     static constexpr struct DitherTable {
 *         constexpr DitherTable() : data() {
 *             constexpr int kImgSize = 8; // if changed, also change value in sk_dither_shader
 *
 *             for (int x = 0; x < kImgSize; ++x) {
 *                 for (int y = 0; y < kImgSize; ++y) {
 *                     // The computation of 'm' and 'value' is lifted from CPU backend.
 *                     unsigned int m = (y & 1) << 5 | (x & 1) << 4 |
 *                                      (y & 2) << 2 | (x & 2) << 1 |
 *                                      (y & 4) >> 1 | (x & 4) >> 2;
 *                     float value = float(m) * 1.0 / 64.0 - 63.0 / 128.0;
 *                     // Bias by 0.5 to be in 0..1, mul by 255 and round to nearest int to make byte.
 *                     data[y * 8 + x] = (uint8_t)((value + 0.5) * 255.f + 0.5f);
 *                 }
 *             }
 *         }
 *         uint8_t data[64];
 *     } gTable;
 *
 *     SkBitmap bmp;
 *     bmp.setInfo(SkImageInfo::MakeA8(8, 8));
 *     bmp.setPixels(const_cast<uint8_t*>(gTable.data));
 *     bmp.setImmutable();
 *     return bmp;
 * }
 * ```
 */
public fun makeDitherLUT(): SkBitmap {
  TODO("Implement makeDitherLUT")
}

/**
 * C++ original:
 * ```cpp
 * ShaderErrorHandler* DefaultShaderErrorHandler() {
 *     class DefaultShaderErrorHandler : public ShaderErrorHandler {
 *     public:
 *         void compileError(const char* shader, const char* errors) override {
 *             std::string message = SkShaderUtils::BuildShaderErrorMessage(shader, errors);
 *             SkShaderUtils::VisitLineByLine(message, [](int, const char* lineText) {
 *                 SkDebugf("%s\n", lineText);
 *             });
 *             SkDEBUGFAILF("Shader compilation failed!\n\n%s", message.c_str());
 *         }
 *     };
 *
 *     static DefaultShaderErrorHandler gHandler;
 *     return &gHandler;
 * }
 * ```
 */
public fun defaultShaderErrorHandler(): ShaderErrorHandler {
  TODO("Implement defaultShaderErrorHandler")
}

/**
 * C++ original:
 * ```cpp
 * SkISize GetApproxSize(SkISize size) {
 *     // Map 'value' to a larger multiple of 2. Values <= 'kMagicTol' will pop up to
 *     // the next power of 2. Those above 'kMagicTol' will only go up half the floor power of 2.
 *     auto adjust = [](int value) {
 *         constexpr int kMinApproxSize = 16;
 *         constexpr int kMagicTol = 1024;
 *
 *         value = std::max(kMinApproxSize, value);
 *
 *         if (SkIsPow2(value)) {
 *             return value;
 *         }
 *
 *         int ceilPow2 = SkNextPow2(value);
 *         if (value <= kMagicTol) {
 *             return ceilPow2;
 *         }
 *
 *         int floorPow2 = ceilPow2 >> 1;
 *         int mid = floorPow2 + (floorPow2 >> 1);
 *
 *         if (value <= mid) {
 *             return mid;
 *         }
 *         return ceilPow2;
 *     };
 *
 *     return {adjust(size.width()), adjust(size.height())};
 * }
 * ```
 */
public fun getApproxSize(size: SkISize): SkISize {
  TODO("Implement getApproxSize")
}

/**
 * C++ original:
 * ```cpp
 * bool SkSLToBackend(const SkSL::ShaderCaps* caps,
 *                    bool (*toBackend)(SkSL::Program&, const SkSL::ShaderCaps*, SkSL::NativeShader*),
 *                    const char* backendLabel,
 *                    const std::string& sksl,
 *                    SkSL::ProgramKind programKind,
 *                    const SkSL::ProgramSettings& settings,
 *                    SkSL::NativeShader* output,
 *                    SkSL::ProgramInterface* outInterface,
 *                    ShaderErrorHandler* errorHandler) {
 * #ifdef SK_DEBUG
 *     std::string src = SkShaderUtils::PrettyPrint(sksl);
 * #else
 *     const std::string& src = sksl;
 * #endif
 *     SkSL::Compiler compiler;
 *     std::unique_ptr<SkSL::Program> program = compiler.convertProgram(programKind, src, settings);
 *     if (!program || !(*toBackend)(*program, caps, output)) {
 *         errorHandler->compileError(src.c_str(),
 *                                    compiler.errorText().c_str(),
 *                                    /*shaderWasCached=*/false);
 *         return false;
 *     }
 *
 * #if defined(SK_PRINT_SKSL_SHADERS)
 *     const bool kPrintSkSL = true;
 * #else
 *     const bool kPrintSkSL = false;
 * #endif
 *     const bool kSkSLPostCompilation = false;
 * #if defined(SK_PRINT_NATIVE_SHADERS)
 *     const bool printBackendSL = (backendLabel != nullptr);
 * #else
 *     const bool printBackendSL = false;
 * #endif
 *
 *     if (kPrintSkSL || kSkSLPostCompilation || printBackendSL) {
 *         SkShaderUtils::PrintShaderBanner(programKind);
 *         if (kPrintSkSL) {
 *             SkDebugf("SkSL:\n");
 *             SkShaderUtils::PrintLineByLine(SkShaderUtils::PrettyPrint(sksl));
 *         }
 *         if (kSkSLPostCompilation) {
 *             SkDebugf("SkSL (post-compilation):\n");
 *             SkShaderUtils::PrintLineByLine(SkShaderUtils::PrettyPrint(program->description()));
 *         }
 *         if (printBackendSL) {
 *             SkDebugf("%s:\n", backendLabel);
 *             if (output->isBinary()) {
 *                 const std::string asHex = SkShaderUtils::SpirvAsHexStream(output->fBinary);
 *                 SkShaderUtils::PrintLineByLine(asHex);
 *             } else {
 *                 SkShaderUtils::PrintLineByLine(output->fText);
 *             }
 *         }
 *     }
 *
 *     if (outInterface) {
 *         *outInterface = program->fInterface;
 *     }
 *     return true;
 * }
 * ```
 */
public fun skSLToBackend(
  caps: ShaderCaps?,
  param1: (
    Program,
    ShaderCaps?,
    NativeShader?,
  ) -> Boolean,
  backendLabel: String?,
  sksl: String,
  programKind: ProgramKind,
  settings: ProgramSettings,
  output: NativeShader?,
  outInterface: ProgramInterface?,
  errorHandler: ShaderErrorHandler?,
): Boolean {
  TODO("Implement skSLToBackend")
}

/**
 * C++ original:
 * ```cpp
 * size_t get_tile_count(const SkIRect& srcRect, int tileSize)  {
 *     int tilesX = (srcRect.fRight / tileSize) - (srcRect.fLeft / tileSize) + 1;
 *     int tilesY = (srcRect.fBottom / tileSize) - (srcRect.fTop / tileSize) + 1;
 *     // We calculate expected tile count before we read the bitmap's pixels, so hypothetically we can
 *     // have lazy images with excessive dimensions that would cause (tilesX*tilesY) to overflow int.
 *     // In these situations we also later fail to allocate a bitmap to store the lazy image, so there
 *     // isn't really a performance concern around one image turning into millions of tiles.
 *     return SkSafeMath::Mul(tilesX, tilesY);
 * }
 * ```
 */
public fun getTileCount(srcRect: SkIRect, tileSize: Int): ULong {
  TODO("Implement getTileCount")
}

/**
 * C++ original:
 * ```cpp
 * int determine_tile_size(const SkIRect& src, int maxTileSize) {
 *     if (maxTileSize <= kBmpSmallTileSize) {
 *         return maxTileSize;
 *     }
 *
 *     size_t maxTileTotalTileSize = get_tile_count(src, maxTileSize);
 *     size_t smallTotalTileSize = get_tile_count(src, kBmpSmallTileSize);
 *
 *     maxTileTotalTileSize *= maxTileSize * maxTileSize;
 *     smallTotalTileSize *= kBmpSmallTileSize * kBmpSmallTileSize;
 *
 *     if (maxTileTotalTileSize > 2 * smallTotalTileSize) {
 *         return kBmpSmallTileSize;
 *     } else {
 *         return maxTileSize;
 *     }
 * }
 * ```
 */
public fun determineTileSize(src: SkIRect, maxTileSize: Int): Int {
  TODO("Implement determineTileSize")
}

/**
 * C++ original:
 * ```cpp
 * SkIRect determine_clipped_src_rect(SkIRect clippedSrcIRect,
 *                                    const SkMatrix& viewMatrix,
 *                                    const SkMatrix& srcToDstRect,
 *                                    const SkISize& imageDimensions,
 *                                    const SkRect* srcRectPtr) {
 *     SkMatrix inv = SkMatrix::Concat(viewMatrix, srcToDstRect);
 *     if (auto inverse = inv.invert()) {
 *         inv = *inverse;
 *     } else {
 *         return SkIRect::MakeEmpty();
 *     }
 *     SkRect clippedSrcRect = SkRect::Make(clippedSrcIRect);
 *     inv.mapRect(&clippedSrcRect);
 *     if (srcRectPtr) {
 *         if (!clippedSrcRect.intersect(*srcRectPtr)) {
 *             return SkIRect::MakeEmpty();
 *         }
 *     }
 *     clippedSrcRect.roundOut(&clippedSrcIRect);
 *     SkIRect bmpBounds = SkIRect::MakeSize(imageDimensions);
 *     if (!clippedSrcIRect.intersect(bmpBounds)) {
 *         return SkIRect::MakeEmpty();
 *     }
 *
 *     return clippedSrcIRect;
 * }
 * ```
 */
public fun determineClippedSrcRect(
  clippedSrcIRect: SkIRect,
  viewMatrix: SkMatrix,
  srcToDstRect: SkMatrix,
  imageDimensions: SkISize,
  srcRectPtr: SkRect?,
): SkIRect {
  TODO("Implement determineClippedSrcRect")
}

/**
 * C++ original:
 * ```cpp
 * int draw_tiled_image(SkCanvas* canvas,
 *                      std::function<sk_sp<SkImage>(SkIRect)> imageProc,
 *                      SkISize originalSize,
 *                      int tileSize,
 *                      const SkMatrix& srcToDst,
 *                      const SkRect& srcRect,
 *                      const SkIRect& clippedSrcIRect,
 *                      const SkPaint* paint,
 *                      SkCanvas::QuadAAFlags origAAFlags,
 *                      SkCanvas::SrcRectConstraint constraint,
 *                      SkSamplingOptions sampling) {
 *     if (sampling.isAniso()) {
 *         sampling = SkSamplingPriv::AnisoFallback(/* imageIsMipped= */ false);
 *     }
 *     SkRect clippedSrcRect = SkRect::Make(clippedSrcIRect);
 *
 *     int nx = originalSize.width() / tileSize;
 *     int ny = originalSize.height() / tileSize;
 *
 *     int numTilesDrawn = 0;
 *
 *     skia_private::TArray<SkCanvas::ImageSetEntry> imgSet(nx * ny);
 *
 *     for (int x = 0; x <= nx; x++) {
 *         for (int y = 0; y <= ny; y++) {
 *             SkRect tileR;
 *             // TODO: this will prevent int overflow, however at sizes > 2^24 the float can't
 *             // represent all the bits in the int
 *             int tileRight = (x == nx) ? originalSize.width() : (x + 1) * tileSize;
 *             int tileBottom = (y == ny) ? originalSize.height() : (y + 1) * tileSize;
 *             tileR.setLTRB(SkIntToScalar(x * tileSize), SkIntToScalar(y * tileSize),
 *                           SkIntToScalar(tileRight),    SkIntToScalar(tileBottom));
 *
 *             if (!SkRect::Intersects(tileR, clippedSrcRect)) {
 *                 continue;
 *             }
 *
 *             if (!tileR.intersect(srcRect)) {
 *                 continue;
 *             }
 *
 *             SkIRect iTileR;
 *             tileR.roundOut(&iTileR);
 *             SkVector offset = SkPoint::Make(SkIntToScalar(iTileR.fLeft),
 *                                             SkIntToScalar(iTileR.fTop));
 *             SkRect rectToDraw = tileR;
 *             if (!srcToDst.mapRect(&rectToDraw)) {
 *                 continue;
 *             }
 *
 *             if (sampling.filter != SkFilterMode::kNearest || sampling.useCubic) {
 *                 SkIRect iClampRect;
 *
 *                 if (SkCanvas::kFast_SrcRectConstraint == constraint) {
 *                     // In bleed mode we want to always expand the tile on all edges
 *                     // but stay within the bitmap bounds
 *                     iClampRect = SkIRect::MakeWH(originalSize.width(), originalSize.height());
 *                 } else {
 *                     // In texture-domain/clamp mode we only want to expand the
 *                     // tile on edges interior to "srcRect" (i.e., we want to
 *                     // not bleed across the original clamped edges)
 *                     srcRect.roundOut(&iClampRect);
 *                 }
 *                 int outset = sampling.useCubic ? kBicubicFilterTexelPad : 1;
 *                 skgpu::TiledTextureUtils::ClampedOutsetWithOffset(&iTileR, outset, &offset,
 *                                                                   iClampRect);
 *             }
 *
 *             sk_sp<SkImage> image = imageProc(iTileR);
 *             if (!image) {
 *                 continue;
 *             }
 *
 *             unsigned aaFlags = SkCanvas::kNone_QuadAAFlags;
 *             // Preserve the original edge AA flags for the exterior tile edges.
 *             if (tileR.fLeft <= srcRect.fLeft && (origAAFlags & SkCanvas::kLeft_QuadAAFlag)) {
 *                 aaFlags |= SkCanvas::kLeft_QuadAAFlag;
 *             }
 *             if (tileR.fRight >= srcRect.fRight && (origAAFlags & SkCanvas::kRight_QuadAAFlag)) {
 *                 aaFlags |= SkCanvas::kRight_QuadAAFlag;
 *             }
 *             if (tileR.fTop <= srcRect.fTop && (origAAFlags & SkCanvas::kTop_QuadAAFlag)) {
 *                 aaFlags |= SkCanvas::kTop_QuadAAFlag;
 *             }
 *             if (tileR.fBottom >= srcRect.fBottom &&
 *                 (origAAFlags & SkCanvas::kBottom_QuadAAFlag)) {
 *                 aaFlags |= SkCanvas::kBottom_QuadAAFlag;
 *             }
 *
 *             // Offset the source rect to make it "local" to our tmp bitmap
 *             tileR.offset(-offset.fX, -offset.fY);
 *
 *             imgSet.push_back(SkCanvas::ImageSetEntry(std::move(image),
 *                                                      tileR,
 *                                                      rectToDraw,
 *                                                      /* matrixIndex= */ -1,
 *                                                      /* alpha= */ 1.0f,
 *                                                      aaFlags,
 *                                                      /* hasClip= */ false));
 *
 *             numTilesDrawn += 1;
 *         }
 *     }
 *
 *     canvas->experimental_DrawEdgeAAImageSet(imgSet.data(),
 *                                             imgSet.size(),
 *                                             /* dstClips= */ nullptr,
 *                                             /* preViewMatrices= */ nullptr,
 *                                             sampling,
 *                                             paint,
 *                                             constraint);
 *     return numTilesDrawn;
 * }
 * ```
 */
public fun drawTiledImage(
  canvas: SkCanvas?,
  imageProc: (SkIRect) -> SkSp<SkImage>,
  originalSize: SkISize,
  tileSize: Int,
  srcToDst: SkMatrix,
  srcRect: SkRect,
  clippedSrcIRect: SkIRect,
  paint: SkPaint?,
  origAAFlags: SkCanvas.QuadAAFlags,
  constraint: SkCanvas.SrcRectConstraint,
  sampling: SkSamplingOptions,
): Int {
  TODO("Implement drawTiledImage")
}

/**
 * C++ original:
 * ```cpp
 * SkPath PreChopPathCurves(float tessellationPrecision,
 *                          const SkPath& path,
 *                          const SkMatrix& matrix,
 *                          const SkRect& viewport) {
 *     // If the viewport is exceptionally large, we could end up blowing out memory with an unbounded
 *     // number of of chops. Therefore, we require that the viewport is manageable enough that a fully
 *     // contained curve can be tessellated in kMaxTessellationSegmentsPerCurve or fewer. (Any larger
 *     // and that amount of pixels wouldn't fit in memory anyway.)
 *     SkASSERT(wangs_formula::worst_case_cubic(
 *                      tessellationPrecision,
 *                      viewport.width(),
 *                      viewport.height()) <= kMaxSegmentsPerCurve);
 *     PathChopper chopper(tessellationPrecision, matrix, viewport);
 *     for (auto [verb, p, w] : SkPathPriv::Iterate(path)) {
 *         switch (verb) {
 *             case SkPathVerb::kMove:
 *                 chopper.moveTo(p[0]);
 *                 break;
 *             case SkPathVerb::kLine:
 *                 chopper.lineTo(p);
 *                 break;
 *             case SkPathVerb::kQuad:
 *                 chopper.quadTo(p);
 *                 break;
 *             case SkPathVerb::kConic:
 *                 chopper.conicTo(p, *w);
 *                 break;
 *             case SkPathVerb::kCubic:
 *                 chopper.cubicTo(p);
 *                 break;
 *             case SkPathVerb::kClose:
 *                 chopper.close();
 *                 break;
 *         }
 *     }
 *     // Must preserve the input path's fill type (see crbug.com/1472747)
 *     return chopper.detachPath(path.getFillType());
 * }
 * ```
 */
public fun preChopPathCurves(
  tessellationPrecision: Float,
  path: SkPath,
  matrix: SkMatrix,
  viewport: SkRect,
): SkPath {
  TODO("Implement preChopPathCurves")
}

/**
 * C++ original:
 * ```cpp
 * int FindCubicConvex180Chops(const SkPoint pts[], float T[2], bool* areCusps) {
 *     SkASSERT(pts);
 *     SkASSERT(T);
 *     SkASSERT(areCusps);
 *
 *     // If a chop falls within a distance of "kEpsilon" from 0 or 1, throw it out. Tangents become
 *     // unstable when we chop too close to the boundary. This works out because the tessellation
 *     // shaders don't allow more than 2^10 parametric segments, and they snap the beginning and
 *     // ending edges at 0 and 1. So if we overstep an inflection or point of 180-degree rotation by a
 *     // fraction of a tessellation segment, it just gets snapped.
 *     constexpr static float kEpsilon = 1.f / (1 << 11);
 *     // Floating-point representation of "1 - 2*kEpsilon".
 *     constexpr static uint32_t kIEEE_one_minus_2_epsilon = (127 << 23) - 2 * (1 << (24 - 11));
 *     // Unfortunately we don't have a way to static_assert this, but we can runtime assert that the
 *     // kIEEE_one_minus_2_epsilon bits are correct.
 *     SkASSERT(sk_bit_cast<float>(kIEEE_one_minus_2_epsilon) == 1 - 2*kEpsilon);
 *
 *     float2 p0 = sk_bit_cast<float2>(pts[0]);
 *     float2 p1 = sk_bit_cast<float2>(pts[1]);
 *     float2 p2 = sk_bit_cast<float2>(pts[2]);
 *     float2 p3 = sk_bit_cast<float2>(pts[3]);
 *
 *     // Find the cubic's power basis coefficients. These define the bezier curve as:
 *     //
 *     //                                    |T^3|
 *     //     Cubic(T) = x,y = |A  3B  3C| * |T^2| + P0
 *     //                      |.   .   .|   |T  |
 *     //
 *     // And the tangent direction (scaled by a uniform 1/3) will be:
 *     //
 *     //                                                 |T^2|
 *     //     Tangent_Direction(T) = dx,dy = |A  2B  C| * |T  |
 *     //                                    |.   .  .|   |1  |
 *     //
 *     float2 C = p1 - p0;
 *     float2 D = p2 - p1;
 *     float2 E = p3 - p0;
 *     float2 B = D - C;
 *     float2 A = -3*D + E;
 *
 *     // Now find the cubic's inflection function. There are inflections where F' x F'' == 0.
 *     // We formulate this as a quadratic equation:  F' x F'' == aT^2 + bT + c == 0.
 *     // See: https://www.microsoft.com/en-us/research/wp-content/uploads/2005/01/p1000-loop.pdf
 *     // NOTE: We only need the roots, so a uniform scale factor does not affect the solution.
 *     float a = cross(A,B);
 *     float b = cross(A,C);
 *     float c = cross(B,C);
 *     float b_over_minus_2 = -.5f * b;
 *     float discr_over_4 = b_over_minus_2*b_over_minus_2 - a*c;
 *
 *     // If -cuspThreshold <= discr_over_4 <= cuspThreshold, it means the two roots are within
 *     // kEpsilon of one another (in parametric space). This is close enough for our purposes to
 *     // consider them a single cusp.
 *     float cuspThreshold = a * (kEpsilon/2);
 *     cuspThreshold *= cuspThreshold;
 *
 *     if (discr_over_4 < -cuspThreshold) {
 *         // The curve does not inflect or cusp. This means it might rotate more than 180 degrees
 *         // instead. Chop were rotation == 180 deg. (This is the 2nd root where the tangent is
 *         // parallel to tan0.)
 *         //
 *         //      Tangent_Direction(T) x tan0 == 0
 *         //      (AT^2 x tan0) + (2BT x tan0) + (C x tan0) == 0
 *         //      (A x C)T^2 + (2B x C)T + (C x C) == 0  [[because tan0 == P1 - P0 == C]]
 *         //      bT^2 + 2cT + 0 == 0  [[because A x C == b, B x C == c]]
 *         //      T = [0, -2c/b]
 *         //
 *         // NOTE: if C == 0, then C != tan0. But this is fine because the curve is definitely
 *         // convex-180 if any points are colocated, and T[0] will equal NaN which returns 0 chops.
 *         *areCusps = false;
 *         float root = sk_ieee_float_divide(c, b_over_minus_2);
 *         // Is "root" inside the range [kEpsilon, 1 - kEpsilon)?
 *         if (sk_bit_cast<uint32_t>(root - kEpsilon) < kIEEE_one_minus_2_epsilon) {
 *             T[0] = root;
 *             return 1;
 *         }
 *         return 0;
 *     }
 *
 *     *areCusps = (discr_over_4 <= cuspThreshold);
 *     if (*areCusps) {
 *         // The two roots are close enough that we can consider them a single cusp.
 *         if (a != 0 || b_over_minus_2 != 0 || c != 0) {
 *             // Pick the average of both roots.
 *             float root = sk_ieee_float_divide(b_over_minus_2, a);
 *             // Is "root" inside the range [kEpsilon, 1 - kEpsilon)?
 *             if (sk_bit_cast<uint32_t>(root - kEpsilon) < kIEEE_one_minus_2_epsilon) {
 *                 T[0] = root;
 *                 return 1;
 *             }
 *             return 0;
 *         }
 *
 *         // The curve is a flat line. The standard inflection function doesn't detect cusps from flat
 *         // lines. Find cusps by searching instead for points where the tangent is perpendicular to
 *         // tan0. This will find any cusp point.
 *         //
 *         //     dot(tan0, Tangent_Direction(T)) == 0
 *         //
 *         //                         |T^2|
 *         //     tan0 * |A  2B  C| * |T  | == 0
 *         //            |.   .  .|   |1  |
 *         //
 *         float2 tan0 = skvx::if_then_else(C != 0, C, p2 - p0);
 *         a = dot(tan0, A);
 *         b_over_minus_2 = -dot(tan0, B);
 *         c = dot(tan0, C);
 *         discr_over_4 = b_over_minus_2*b_over_minus_2 - a*c;
 *         if (discr_over_4 < -cuspThreshold) {
 *             // With the updated discriminant, this line actually wouldn't have cusps (e.g. it never
 *             // turns back on itself).
 *             return 0;
 *         }
 *
 *         discr_over_4 = std::max(discr_over_4, 0.f);
 *     }
 *
 *     // Solve our quadratic equation to find where to chop. See the quadratic formula from
 *     // Numerical Recipes in C.
 *     float q = sqrtf(discr_over_4);
 *     q = copysignf(q, b_over_minus_2);
 *     q = q + b_over_minus_2;
 *     float2 roots = float2{q,c} / float2{a,q};
 *
 *     auto inside = (roots > kEpsilon) & (roots < (1 - kEpsilon));
 *     if (inside[0]) {
 *         if (inside[1] && roots[0] != roots[1]) {
 *             if (roots[0] > roots[1]) {
 *                 roots = skvx::shuffle<1,0>(roots);  // Sort.
 *             }
 *             roots.store(T);
 *             return 2;
 *         }
 *         T[0] = roots[0];
 *         return 1;
 *     }
 *     if (inside[1]) {
 *         T[0] = roots[1];
 *         return 1;
 *     }
 *     return 0;
 * }
 * ```
 */
public fun findCubicConvex180Chops(
  pts: Array<SkPoint>,
  t: FloatArray,
  areCusps: Boolean?,
): Int {
  TODO("Implement findCubicConvex180Chops")
}

/**
 * C++ original:
 * ```cpp
 * void write_curve_index_buffer_base_index(VertexWriter vertexWriter,
 *                                          size_t bufferSize,
 *                                          uint16_t baseIndex) {
 *     int triangleCount = bufferSize / (sizeof(uint16_t) * 3);
 *     SkASSERT(triangleCount >= 1);
 *     TArray<std::array<uint16_t, 3>> indexData(triangleCount);
 *
 *     // Connect the vertices with a middle-out triangulation. Refer to InitFixedCountVertexBuffer()
 *     // for the exact vertex ordering.
 *     //
 *     // Resolve level 1 is just a single triangle at T=[0, 1/2, 1].
 *     const auto* neighborInLastResolveLevel = &indexData.push_back({baseIndex,
 *                                                                    (uint16_t)(baseIndex + 2),
 *                                                                    (uint16_t)(baseIndex + 1)});
 *
 *     // Resolve levels 2..maxResolveLevel
 *     int maxResolveLevel = SkPrevLog2(triangleCount + 1);
 *     uint16_t nextIndex = baseIndex + 3;
 *     SkASSERT(NumCurveTrianglesAtResolveLevel(maxResolveLevel) == triangleCount);
 *     for (int resolveLevel = 2; resolveLevel <= maxResolveLevel; ++resolveLevel) {
 *         SkDEBUGCODE(auto* firstTriangleInCurrentResolveLevel = indexData.end());
 *         int numOuterTrianglelsInResolveLevel = 1 << (resolveLevel - 1);
 *         SkASSERT(numOuterTrianglelsInResolveLevel % 2 == 0);
 *         int numTrianglePairsInResolveLevel = numOuterTrianglelsInResolveLevel >> 1;
 *         for (int i = 0; i < numTrianglePairsInResolveLevel; ++i) {
 *             // First triangle shares the left edge of "neighborInLastResolveLevel".
 *             indexData.push_back({(*neighborInLastResolveLevel)[0],
 *                                  nextIndex++,
 *                                  (*neighborInLastResolveLevel)[1]});
 *             // Second triangle shares the right edge of "neighborInLastResolveLevel".
 *             indexData.push_back({(*neighborInLastResolveLevel)[1],
 *                                  nextIndex++,
 *                                  (*neighborInLastResolveLevel)[2]});
 *             ++neighborInLastResolveLevel;
 *         }
 *         SkASSERT(neighborInLastResolveLevel == firstTriangleInCurrentResolveLevel);
 *     }
 *     SkASSERT(indexData.size() == triangleCount);
 *     SkASSERT(nextIndex == baseIndex + triangleCount + 2);
 *
 *     vertexWriter << VertexWriter::Array(indexData.data(), indexData.size());
 * }
 * ```
 */
public fun writeCurveIndexBufferBaseIndex(
  vertexWriter: VertexWriter,
  bufferSize: ULong,
  baseIndex: UShort,
) {
  TODO("Implement writeCurveIndexBufferBaseIndex")
}

/**
 * C++ original:
 * ```cpp
 * SkScalar* build_distance_adjust_table(SkScalar deviceGamma) {
 *     // This is used for an approximation of the mask gamma hack, used by raster and bitmap
 *     // text. The mask gamma hack is based off of guessing what the blend color is going to
 *     // be, and adjusting the mask so that when run through the linear blend will
 *     // produce the value closest to the desired result. However, in practice this means
 *     // that the 'adjusted' mask is just increasing or decreasing the coverage of
 *     // the mask depending on what it is thought it will blit against. For black (on
 *     // assumed white) this means that coverages are decreased (on a curve). For white (on
 *     // assumed black) this means that coverages are increased (on a a curve). At
 *     // middle (perceptual) gray (which could be blit against anything) the coverages
 *     // remain the same.
 *     //
 *     // The idea here is that instead of determining the initial (real) coverage and
 *     // then adjusting that coverage, we determine an adjusted coverage directly by
 *     // essentially manipulating the geometry (in this case, the distance to the glyph
 *     // edge). So for black (on assumed white) this thins a bit; for white (on
 *     // assumed black) this fake bolds the geometry a bit.
 *     //
 *     // The distance adjustment is calculated by determining the actual coverage value which
 *     // when fed into in the mask gamma table gives us an 'adjusted coverage' value of 0.5. This
 *     // actual coverage value (assuming it's between 0 and 1) corresponds to a distance from the
 *     // actual edge. So by subtracting this distance adjustment and computing without the
 *     // the coverage adjustment we should get 0.5 coverage at the same point.
 *     //
 *     // This has several implications:
 *     //     For non-gray lcd smoothed text, each subpixel essentially is using a
 *     //     slightly different geometry.
 *     //
 *     //     For black (on assumed white) this may not cover some pixels which were
 *     //     previously covered; however those pixels would have been only slightly
 *     //     covered and that slight coverage would have been decreased anyway. Also, some pixels
 *     //     which were previously fully covered may no longer be fully covered.
 *     //
 *     //     For white (on assumed black) this may cover some pixels which weren't
 *     //     previously covered at all.
 *
 *     int width, height;
 *     size_t size;
 *     SkScalar contrast = SK_GAMMA_CONTRAST;
 *
 *     size = SkScalerContext::GetGammaLUTSize(contrast, deviceGamma,
 *         &width, &height);
 *
 *     SkASSERT(kExpectedDistanceAdjustTableSize == height);
 *     SkScalar* table = new SkScalar[height];
 *
 *     AutoTArray<uint8_t> data((int)size);
 *     if (!SkScalerContext::GetGammaLUTData(contrast, deviceGamma, data.get())) {
 *         // if no valid data is available simply do no adjustment
 *         for (int row = 0; row < height; ++row) {
 *             table[row] = 0;
 *         }
 *         return table;
 *     }
 *
 *     // find the inverse points where we cross 0.5
 *     // binsearch might be better, but we only need to do this once on creation
 *     for (int row = 0; row < height; ++row) {
 *         uint8_t* rowPtr = data.get() + row*width;
 *         for (int col = 0; col < width - 1; ++col) {
 *             if (rowPtr[col] <= 127 && rowPtr[col + 1] >= 128) {
 *                 // compute point where a mask value will give us a result of 0.5
 *                 float interp = (127.5f - rowPtr[col]) / (rowPtr[col + 1] - rowPtr[col]);
 *                 float borderAlpha = (col + interp) / 255.f;
 *
 *                 // compute t value for that alpha
 *                 // this is an approximate inverse for smoothstep()
 *                 float t = borderAlpha*(borderAlpha*(4.0f*borderAlpha - 6.0f) + 5.0f) / 3.0f;
 *
 *                 // compute distance which gives us that t value
 *                 const float kDistanceFieldAAFactor = 0.65f; // should match SK_DistanceFieldAAFactor
 *                 float d = 2.0f*kDistanceFieldAAFactor*t - kDistanceFieldAAFactor;
 *
 *                 table[row] = d;
 *                 break;
 *             }
 *         }
 *     }
 *
 *     return table;
 * }
 * ```
 */
public fun buildDistanceAdjustTable(deviceGamma: SkScalar): SkScalar {
  TODO("Implement buildDistanceAdjustTable")
}

/**
 * C++ original:
 * ```cpp
 * SkMatrix position_matrix(const SkMatrix& drawMatrix, SkPoint drawOrigin) {
 *     SkMatrix position_matrix = drawMatrix;
 *     return position_matrix.preTranslate(drawOrigin.x(), drawOrigin.y());
 * }
 * ```
 */
public fun positionMatrix(drawMatrix: SkMatrix, drawOrigin: SkPoint): SkMatrix {
  TODO("Implement positionMatrix")
}

/**
 * C++ original:
 * ```cpp
 * SkSpan<const SkPackedGlyphID> get_packedIDs(SkZip<const SkPackedGlyphID, const SkPoint> accepted) {
 *     return accepted.get<0>();
 * }
 * ```
 */
public fun getPackedIDs(accepted: SkZip<SkPackedGlyphID, SkPoint>): SkSpan<SkPackedGlyphID> {
  TODO("Implement getPackedIDs")
}

/**
 * C++ original:
 * ```cpp
 * SkSpan<const SkGlyphID> get_glyphIDs(SkZip<const SkGlyphID, const SkPoint> accepted) {
 *     return accepted.get<0>();
 * }
 * ```
 */
public fun getGlyphIDs(accepted: SkZip<SkGlyphID, SkPoint>): SkSpan<SkGlyphID> {
  TODO("Implement getGlyphIDs")
}

/**
 * C++ original:
 * ```cpp
 * template <typename U>
 * SkSpan<const SkPoint> get_positions(SkZip<U, const SkPoint> accepted) {
 *     return accepted.template get<1>();
 * }
 * ```
 */
public fun <U> getPositions(accepted: SkZip<U, SkPoint>): SkSpan<SkPoint> {
  TODO("Implement getPositions")
}

/**
 * C++ original:
 * ```cpp
 * bool has_some_antialiasing(const SkFont& font ) {
 *     SkFont::Edging edging = font.getEdging();
 *     return edging == SkFont::Edging::kAntiAlias
 *            || edging == SkFont::Edging::kSubpixelAntiAlias;
 * }
 * ```
 */
public fun hasSomeAntialiasing(font: SkFont): Boolean {
  TODO("Implement hasSomeAntialiasing")
}

/**
 * C++ original:
 * ```cpp
 * template<typename AddSingleMaskFormat>
 * void add_multi_mask_format(
 *         AddSingleMaskFormat addSingleMaskFormat,
 *         SkZip<const SkPackedGlyphID, const SkPoint, const SkMask::Format> accepted) {
 *     if (accepted.empty()) { return; }
 *
 *     auto maskSpan = accepted.get<2>();
 *     MaskFormat format = Glyph::FormatFromSkGlyph(maskSpan[0]);
 *     size_t startIndex = 0;
 *     for (size_t i = 1; i < accepted.size(); i++) {
 *         MaskFormat nextFormat = Glyph::FormatFromSkGlyph(maskSpan[i]);
 *         if (format != nextFormat) {
 *             auto interval = accepted.subspan(startIndex, i - startIndex);
 *             // Only pass the packed glyph ids and positions.
 *             auto glyphsWithSameFormat = SkMakeZip(interval.get<0>(), interval.get<1>());
 *             // Take a ref on the strike. This should rarely happen.
 *             addSingleMaskFormat(glyphsWithSameFormat, format);
 *             format = nextFormat;
 *             startIndex = i;
 *         }
 *     }
 *     auto interval = accepted.last(accepted.size() - startIndex);
 *     auto glyphsWithSameFormat = SkMakeZip(interval.get<0>(), interval.get<1>());
 *     addSingleMaskFormat(glyphsWithSameFormat, format);
 * }
 * ```
 */
public fun <AddSingleMaskFormat> addMultiMaskFormat(addSingleMaskFormat: AddSingleMaskFormat, accepted: SkZip<SkPackedGlyphID, SkPoint, SkMask.Format>) {
  TODO("Implement addMultiMaskFormat")
}

/**
 * C++ original:
 * ```cpp
 * SkScalar find_maximum_glyph_dimension(StrikeForGPU* strike, SkSpan<const SkGlyphID> glyphs) {
 *     StrikeMutationMonitor m{strike};
 *     SkScalar maxDimension = 0;
 *     for (SkGlyphID glyphID : glyphs) {
 *         SkGlyphDigest digest = strike->digestFor(kMask, SkPackedGlyphID{glyphID});
 *         maxDimension = std::max(static_cast<SkScalar>(digest.maxDimension()), maxDimension);
 *     }
 *
 *     return maxDimension;
 * }
 * ```
 */
public fun findMaximumGlyphDimension(strike: StrikeForGPU?, glyphs: SkSpan<SkGlyphID>): SkScalar {
  TODO("Implement findMaximumGlyphDimension")
}

/**
 * C++ original:
 * ```cpp
 * std::tuple<SkZip<const SkPackedGlyphID, const SkPoint>, SkZip<SkGlyphID, SkPoint>, SkRect>
 * prepare_for_SDFT_drawing(StrikeForGPU* strike,
 *                          const SkMatrix& creationMatrix,
 *                          SkZip<const SkGlyphID, const SkPoint> source,
 *                          SkZip<SkPackedGlyphID, SkPoint> acceptedBuffer,
 *                          SkZip<SkGlyphID, SkPoint> rejectedBuffer) {
 *     int acceptedSize = 0,
 *         rejectedSize = 0;
 *     SkGlyphRect boundingRect = skglyph::empty_rect();
 *     StrikeMutationMonitor m{strike};
 *     for (const auto [glyphID, pos] : source) {
 *         if (!SkIsFinite(pos.x(), pos.y())) {
 *             continue;
 *         }
 *
 *         const SkPackedGlyphID packedID{glyphID};
 *         switch (const SkGlyphDigest digest = strike->digestFor(skglyph::kSDFT, packedID);
 *                 digest.actionFor(skglyph::kSDFT)) {
 *             case GlyphAction::kAccept: {
 *                 SkPoint mappedPos = creationMatrix.mapPoint(pos);
 *                 const SkGlyphRect glyphBounds =
 *                     digest.bounds()
 *                         // The SDFT glyphs have 2-pixel wide padding that should
 *                         // not be used in calculating the source rectangle.
 *                         .inset(SK_DistanceFieldInset, SK_DistanceFieldInset)
 *                         .offset(mappedPos);
 *                 boundingRect = skglyph::rect_union(boundingRect, glyphBounds);
 *                 acceptedBuffer[acceptedSize++] = std::make_tuple(packedID, glyphBounds.leftTop());
 *                 break;
 *             }
 *             case GlyphAction::kReject:
 *                 rejectedBuffer[rejectedSize++] = std::make_tuple(glyphID, pos);
 *             break;
 *             default:
 *                 break;
 *         }
 *     }
 *
 *     return {acceptedBuffer.first(acceptedSize),
 *             rejectedBuffer.first(rejectedSize),
 *             boundingRect.rect()};
 * }
 * ```
 */
public fun prepareForSDFTDrawing(
  strike: StrikeForGPU?,
  creationMatrix: SkMatrix,
  source: SkZip<SkGlyphID, SkPoint>,
  acceptedBuffer: SkZip<SkPackedGlyphID, SkPoint>,
  rejectedBuffer: SkZip<SkGlyphID, SkPoint>,
): Triple<SkZip<SkPackedGlyphID, SkPoint>, SkZip<SkGlyphID, SkPoint>, SkRect> {
  TODO("Implement prepareForSDFTDrawing")
}

/**
 * C++ original:
 * ```cpp
 * static std::tuple<SkStrikeSpec, SkScalar, sktext::gpu::SDFTMatrixRange>
 * make_sdft_strike_spec(const SkFont& font, const SkPaint& paint,
 *                       const SkSurfaceProps& surfaceProps, const SkMatrix& deviceMatrix,
 *                       const SkPoint& textLocation, const sktext::gpu::SubRunControl& control) {
 *     // Add filter to the paint which creates the SDFT data for A8 masks.
 *     SkPaint dfPaint{paint};
 *     dfPaint.setMaskFilter(sktext::gpu::SDFMaskFilter::Make());
 *
 *     auto [dfFont, strikeToSourceScale, matrixRange] = control.getSDFFont(font, deviceMatrix,
 *                                                                          textLocation);
 *
 *     // Adjust the stroke width by the scale factor for drawing the SDFT.
 *     dfPaint.setStrokeWidth(paint.getStrokeWidth() / strikeToSourceScale);
 *
 *     // Check for dashing and adjust the intervals.
 *     if (SkPathEffect* pathEffect = paint.getPathEffect(); pathEffect != nullptr) {
 *         if (auto info = as_PEB(pathEffect)->asADash()) {
 *             SkSpan<const SkScalar> src = info->fIntervals;
 *             SkASSERT(src.size() > 1);
 *             // Allocate the intervals.
 *             std::vector<SkScalar> scaledIntervals(src.size());
 *             for (size_t i = 0; i < src.size(); ++i) {
 *                 scaledIntervals[i] = src[i] / strikeToSourceScale;
 *             }
 *             auto scaledDashes = SkDashPathEffect::Make(scaledIntervals,
 *                                                        info->fPhase / strikeToSourceScale);
 *             dfPaint.setPathEffect(scaledDashes);
 *         }
 *     }
 *
 *     // Fake-gamma and subpixel antialiasing are applied in the shader, so we ignore the
 *     // passed-in scaler context flags. (It's only used when we fall-back to bitmap text).
 *     SkScalerContextFlags flags = SkScalerContextFlags::kNone;
 *     SkStrikeSpec strikeSpec = SkStrikeSpec::MakeMask(dfFont, dfPaint, surfaceProps, flags,
 *                                                      SkMatrix::I());
 *
 *     return std::make_tuple(std::move(strikeSpec), strikeToSourceScale, matrixRange);
 * }
 * ```
 */
public fun makeSdftStrikeSpec(
  font: SkFont,
  paint: SkPaint,
  surfaceProps: SkSurfaceProps,
  deviceMatrix: SkMatrix,
  textLocation: SkPoint,
  control: SubRunControl,
): Triple<SkStrikeSpec, SkScalar, SDFTMatrixRange> {
  TODO("Implement makeSdftStrikeSpec")
}

/**
 * C++ original:
 * ```cpp
 * SkSpan<SkPoint> MakePointsFromBuffer(SkReadBuffer& buffer, SubRunAllocator* alloc) {
 *     uint32_t glyphCount = buffer.getArrayCount();
 *
 *     // Zero indicates a problem with serialization.
 *     if (!buffer.validate(glyphCount != 0)) { return {}; }
 *
 *     // Check that the count will not overflow the arena.
 *     if (!buffer.validate(glyphCount <= INT_MAX &&
 *                          BagOfBytes::WillCountFit<SkPoint>(glyphCount))) { return {}; }
 *
 *     SkPoint* positionsData = alloc->makePODArray<SkPoint>(glyphCount);
 *     if (!buffer.readPointArray({positionsData, glyphCount})) { return {}; }
 *     return {positionsData, glyphCount};
 * }
 * ```
 */
public fun makePointsFromBuffer(buffer: SkReadBuffer, alloc: SubRunAllocator?): SkSpan<SkPoint> {
  TODO("Implement makePointsFromBuffer")
}

/**
 * C++ original:
 * ```cpp
 * std::tuple<bool, SkVector> can_use_direct(
 *         const SkMatrix& initialPositionMatrix, const SkMatrix& positionMatrix) {
 *     // The existing direct glyph info can be used if the initialPositionMatrix, and the
 *     // positionMatrix have the same 2x2, and the translation between them is integer.
 *     // Calculate the translation in source space to a translation in device space by mapping
 *     // (0, 0) through both the initial position matrix and the position matrix; take the difference.
 *     SkVector translation = positionMatrix.mapOrigin() - initialPositionMatrix.mapOrigin();
 *     return {initialPositionMatrix.getScaleX() == positionMatrix.getScaleX() &&
 *             initialPositionMatrix.getScaleY() == positionMatrix.getScaleY() &&
 *             initialPositionMatrix.getSkewX()  == positionMatrix.getSkewX()  &&
 *             initialPositionMatrix.getSkewY()  == positionMatrix.getSkewY()  &&
 *             SkScalarIsInt(translation.x()) && SkScalarIsInt(translation.y()),
 *             translation};
 * }
 * ```
 */
public fun canUseDirect(initialPositionMatrix: SkMatrix, positionMatrix: SkMatrix): Pair<Boolean, SkVector> {
  TODO("Implement canUseDirect")
}

/**
 * C++ original:
 * ```cpp
 * static SkColor compute_canonical_color(const SkPaint& paint, bool lcd) {
 *     SkColor canonicalColor = SkPaintPriv::ComputeLuminanceColor(paint);
 *     if (lcd) {
 *         // This is the correct computation for canonicalColor, but there are tons of cases where LCD
 *         // can be modified. For now we just regenerate if any run in a textblob has LCD.
 *         // TODO figure out where all of these modifications are and see if we can incorporate that
 *         //      logic at a higher level *OR* use sRGB
 *         //canonicalColor = SkMaskGamma::CanonicalColor(canonicalColor);
 *
 *         // TODO we want to figure out a way to be able to use the canonical color on LCD text,
 *         // see the note above.  We pick a placeholder value for LCD text to ensure we always match
 *         // the same key
 *         return SK_ColorTRANSPARENT;
 *     } else {
 *         // A8, though can have mixed BMP text but it shouldn't matter because BMP text won't have
 *         // gamma corrected masks anyways, nor color
 *         U8CPU lum = SkComputeLuminance(SkColorGetR(canonicalColor),
 *                                        SkColorGetG(canonicalColor),
 *                                        SkColorGetB(canonicalColor));
 *         // reduce to our finite number of bits
 *         canonicalColor = SkMaskGamma::CanonicalColor(SkColorSetRGB(lum, lum, lum));
 *     }
 *     return canonicalColor;
 * }
 * ```
 */
public fun computeCanonicalColor(paint: SkPaint, lcd: Boolean): SkColor {
  TODO("Implement computeCanonicalColor")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<Slug> MakeSlug(const SkMatrix& drawMatrix,
 *                      const sktext::GlyphRunList& glyphRunList,
 *                      const SkPaint& paint,
 *                      SkStrikeDeviceInfo strikeDeviceInfo,
 *                      sktext::StrikeForGPUCacheInterface* strikeCache) {
 *     return SlugImpl::Make(drawMatrix, glyphRunList, paint, strikeDeviceInfo, strikeCache);
 * }
 * ```
 */
public fun makeSlug(
  drawMatrix: SkMatrix,
  glyphRunList: GlyphRunList,
  paint: SkPaint,
  strikeDeviceInfo: SkStrikeDeviceInfo,
  strikeCache: StrikeForGPUCacheInterface?,
): SkSp<Slug> {
  TODO("Implement makeSlug")
}

/**
 * C++ original:
 * ```cpp
 * static void post_purge_blob_message(uint32_t blobID, uint32_t cacheID) {
 *     using PurgeBlobMessage = TextBlobRedrawCoordinator::PurgeBlobMessage;
 *     SkASSERT(blobID != SK_InvalidGenID);
 *     SkMessageBus<PurgeBlobMessage, uint32_t>::Post(PurgeBlobMessage(blobID, cacheID));
 * }
 * ```
 */
public fun postPurgeBlobMessage(blobID: UInt, cacheID: UInt) {
  TODO("Implement postPurgeBlobMessage")
}

/**
 * C++ original:
 * ```cpp
 * bool MtlFormatIsCompressed(MTLPixelFormat mtlFormat) {
 *     switch (mtlFormat) {
 *         case MTLPixelFormatETC2_RGB8:
 *             return true;
 * #ifdef SK_BUILD_FOR_MAC
 *         case MTLPixelFormatBC1_RGBA:
 *             return true;
 * #endif
 *         default:
 *             return false;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mtlFormatIsCompressed(mtlFormat: MTLPixelFormat): Boolean {
  TODO("Implement mtlFormatIsCompressed")
}

/**
 * C++ original:
 * ```cpp
 * const char* MtlFormatToString(MTLPixelFormat mtlFormat) {
 *     switch (mtlFormat) {
 *         case MTLPixelFormatInvalid:         return "Invalid";
 *         case MTLPixelFormatRGBA8Unorm:      return "RGBA8Unorm";
 *         case MTLPixelFormatR8Unorm:         return "R8Unorm";
 *         case MTLPixelFormatA8Unorm:         return "A8Unorm";
 *         case MTLPixelFormatBGRA8Unorm:      return "BGRA8Unorm";
 *         case MTLPixelFormatB5G6R5Unorm:     return "B5G6R5Unorm";
 *         case MTLPixelFormatRGBA16Float:     return "RGBA16Float";
 *         case MTLPixelFormatR16Float:        return "R16Float";
 *         case MTLPixelFormatRG8Unorm:        return "RG8Unorm";
 *         case MTLPixelFormatRGB10A2Unorm:    return "RGB10A2Unorm";
 *         case MTLPixelFormatBGR10A2Unorm:    return "BGR10A2Unorm";
 *         case MTLPixelFormatABGR4Unorm:      return "ABGR4Unorm";
 *         case MTLPixelFormatRGBA8Unorm_sRGB: return "RGBA8Unorm_sRGB";
 *         case MTLPixelFormatR16Unorm:        return "R16Unorm";
 *         case MTLPixelFormatRG16Unorm:       return "RG16Unorm";
 *         case MTLPixelFormatETC2_RGB8:       return "ETC2_RGB8";
 * #ifdef SK_BUILD_FOR_MAC
 *         case MTLPixelFormatBC1_RGBA:        return "BC1_RGBA";
 * #endif
 *         case MTLPixelFormatRGBA16Unorm:     return "RGBA16Unorm";
 *         case MTLPixelFormatRG16Float:       return "RG16Float";
 *         case MTLPixelFormatStencil8:        return "Stencil8";
 *
 *         default:                            return "Unknown";
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mtlFormatToString(mtlFormat: MTLPixelFormat): Char {
  TODO("Implement mtlFormatToString")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t MtlFormatChannels(MTLPixelFormat mtlFormat) {
 *     switch (mtlFormat) {
 *         case MTLPixelFormatRGBA8Unorm:      return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatR8Unorm:         return kRed_SkColorChannelFlag;
 *         case MTLPixelFormatA8Unorm:         return kAlpha_SkColorChannelFlag;
 *         case MTLPixelFormatBGRA8Unorm:      return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatB5G6R5Unorm:     return kRGB_SkColorChannelFlags;
 *         case MTLPixelFormatRGBA16Float:     return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatR16Float:        return kRed_SkColorChannelFlag;
 *         case MTLPixelFormatRG8Unorm:        return kRG_SkColorChannelFlags;
 *         case MTLPixelFormatRGB10A2Unorm:    return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatBGR10A2Unorm:    return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatABGR4Unorm:      return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatRGBA8Unorm_sRGB: return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatR16Unorm:        return kRed_SkColorChannelFlag;
 *         case MTLPixelFormatRG16Unorm:       return kRG_SkColorChannelFlags;
 *         case MTLPixelFormatETC2_RGB8:       return kRGB_SkColorChannelFlags;
 * #ifdef SK_BUILD_FOR_MAC
 *         case MTLPixelFormatBC1_RGBA:        return kRGBA_SkColorChannelFlags;
 * #endif
 *         case MTLPixelFormatRGBA16Unorm:     return kRGBA_SkColorChannelFlags;
 *         case MTLPixelFormatRG16Float:       return kRG_SkColorChannelFlags;
 *         case MTLPixelFormatStencil8:        return 0;
 *
 *         default:                            return 0;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mtlFormatChannels(mtlFormat: MTLPixelFormat): UInt {
  TODO("Implement mtlFormatChannels")
}

/**
 * C++ original:
 * ```cpp
 * size_t MtlFormatBytesPerBlock(MTLPixelFormat mtlFormat) {
 *     switch (mtlFormat) {
 *         case MTLPixelFormatInvalid:         return 0;
 *         case MTLPixelFormatRGBA8Unorm:      return 4;
 *         case MTLPixelFormatR8Unorm:         return 1;
 *         case MTLPixelFormatA8Unorm:         return 1;
 *         case MTLPixelFormatBGRA8Unorm:      return 4;
 *         case MTLPixelFormatB5G6R5Unorm:     return 2;
 *         case MTLPixelFormatRGBA16Float:     return 8;
 *         case MTLPixelFormatR16Float:        return 2;
 *         case MTLPixelFormatRG8Unorm:        return 2;
 *         case MTLPixelFormatRGB10A2Unorm:    return 4;
 *         case MTLPixelFormatBGR10A2Unorm:    return 4;
 *         case MTLPixelFormatABGR4Unorm:      return 2;
 *         case MTLPixelFormatRGBA8Unorm_sRGB: return 4;
 *         case MTLPixelFormatR16Unorm:        return 2;
 *         case MTLPixelFormatRG16Unorm:       return 4;
 *         case MTLPixelFormatETC2_RGB8:       return 8;
 * #ifdef SK_BUILD_FOR_MAC
 *         case MTLPixelFormatBC1_RGBA:        return 8;
 * #endif
 *         case MTLPixelFormatRGBA16Unorm:     return 8;
 *         case MTLPixelFormatRG16Float:       return 4;
 *         case MTLPixelFormatStencil8:        return 1;
 *
 *         default:                            return 0;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mtlFormatBytesPerBlock(mtlFormat: MTLPixelFormat): ULong {
  TODO("Implement mtlFormatBytesPerBlock")
}

/**
 * C++ original:
 * ```cpp
 * SkTextureCompressionType MtlFormatToCompressionType(MTLPixelFormat mtlFormat) {
 *     switch (mtlFormat) {
 *         case MTLPixelFormatETC2_RGB8: return SkTextureCompressionType::kETC2_RGB8_UNORM;
 * #ifdef SK_BUILD_FOR_MAC
 *         case MTLPixelFormatBC1_RGBA:  return SkTextureCompressionType::kBC1_RGBA8_UNORM;
 * #endif
 *         default:                      return SkTextureCompressionType::kNone;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mtlFormatToCompressionType(mtlFormat: MTLPixelFormat): SkTextureCompressionType {
  TODO("Implement mtlFormatToCompressionType")
}

/**
 * C++ original:
 * ```cpp
 * static bool use_clip_atlas(const Recorder* recorder) {
 *     // Currently only the raster atlas strategy utilizes the clip atlas.
 *     return recorder->priv().rendererProvider()->pathRendererStrategy() ==
 *             PathRendererStrategy::kRasterAtlas;
 * }
 * ```
 */
public fun useClipAtlas(recorder: Recorder?): Boolean {
  TODO("Implement useClipAtlas")
}

/**
 * C++ original:
 * ```cpp
 * static inline void assert_is_supported_backend(const BackendApi& backend) {
 *     SkASSERT(backend == BackendApi::kDawn ||
 *              backend == BackendApi::kMetal ||
 *              backend == BackendApi::kVulkan);
 * }
 * ```
 */
public fun assertIsSupportedBackend(backend: BackendApi) {
  TODO("Implement assertIsSupportedBackend")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkColorType color_type_fallback(SkColorType ct) {
 *     switch (ct) {
 *         // kRGBA_8888 is our default fallback for many color types that may not have renderable
 *         // backend formats.
 *         case kAlpha_8_SkColorType:
 *         case kRGB_565_SkColorType:
 *         case kARGB_4444_SkColorType:
 *         case kBGRA_8888_SkColorType:
 *         case kRGBA_1010102_SkColorType:
 *         case kBGRA_1010102_SkColorType:
 *         case kRGBA_F16_SkColorType:
 *         case kRGBA_F16Norm_SkColorType:
 *             return kRGBA_8888_SkColorType;
 *         case kA16_float_SkColorType:
 *             return kRGBA_F16_SkColorType;
 *         case kGray_8_SkColorType:
 *         case kRGB_F16F16F16x_SkColorType:
 *         case kRGB_101010x_SkColorType:
 *             return kRGB_888x_SkColorType;
 *         default:
 *             return kUnknown_SkColorType;
 *     }
 * }
 * ```
 */
public fun colorTypeFallback(ct: SkColorType): SkColorType {
  TODO("Implement colorTypeFallback")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t validate_count_and_stride(size_t count, size_t stride, uint32_t alignment) {
 *     // size_t may just be uint32_t, so this ensures we have enough bits to
 *     // compute the required byte product.
 *     uint64_t count64 = SkTo<uint64_t>(count);
 *     uint64_t stride64 = SkTo<uint64_t>(stride);
 *     uint64_t bytes64 = count64*stride64;
 *     if (count64 > std::numeric_limits<uint32_t>::max() ||
 *         stride64 > std::numeric_limits<uint32_t>::max() ||
 *         bytes64 > std::numeric_limits<uint32_t>::max() - (alignment + 1)) {
 *         // Return 0 to skip further allocation attempts.
 *         return 0;
 *     }
 *     // Since count64 and stride64 fit into 32-bits, their product won't overflow a 64-bit multiply,
 *     // and we've confirmed product fits into 32-bits with head room to be aligned w/o overflow.
 *     return SkTo<uint32_t>(bytes64);
 * }
 * ```
 */
public fun validateCountAndStride(
  count: ULong,
  stride: ULong,
  alignment: UInt,
): UInt {
  TODO("Implement validateCountAndStride")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t lcm_alignment(uint32_t alignMaybePow2, uint32_t alignProbNonPow2) {
 *     SkASSERT(alignMaybePow2 != 0 && alignProbNonPow2 != 0);
 *     if (alignMaybePow2 == 1 ||
 *         alignMaybePow2 == alignProbNonPow2 ||
 *         (SkIsPow2(alignMaybePow2) &&
 *                 alignProbNonPow2 > alignMaybePow2 &&
 *                 (alignProbNonPow2 & (alignMaybePow2 - 1)) == 0)) {
 *         // Trivial LCM since alignProbNonPow2 is the same or a larger multiple of alignMaybePow2
 *         return alignProbNonPow2;
 *     } else {
 *         return std::lcm(alignMaybePow2, alignProbNonPow2);
 *     }
 * }
 * ```
 */
public fun lcmAlignment(alignMaybePow2: UInt, alignProbNonPow2: UInt): UInt {
  TODO("Implement lcmAlignment")
}

/**
 * C++ original:
 * ```cpp
 * AccessPattern get_gpu_access_pattern(bool isGpuOnlyAccess, const DrawBufferManager::Options& opts) {
 *     if (isGpuOnlyAccess) {
 * #if defined(GPU_TEST_UTILS)
 *         if (opts.fAllowCopyingGpuOnly) {
 *             return AccessPattern::kGpuOnlyCopySrc;
 *         }
 * #endif
 *         return AccessPattern::kGpuOnly;
 *     } else {
 *         return AccessPattern::kHostVisible;
 *     }
 * }
 * ```
 */
public fun getGpuAccessPattern(isGpuOnlyAccess: Boolean, opts: DrawBufferManager.Options): AccessPattern {
  TODO("Implement getGpuAccessPattern")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t minimum_alignment(BufferType type, bool useTransferBuffers, const Caps* caps) {
 *     uint32_t alignment = 4;
 *     if (type == BufferType::kUniform) {
 *         alignment = SkTo<uint32_t>(caps->requiredUniformBufferAlignment());
 *     } else if (type == BufferType::kStorage || type == BufferType::kVertexStorage ||
 *                type == BufferType::kIndexStorage || type == BufferType::kIndirect) {
 *         alignment = SkTo<uint32_t>(caps->requiredStorageBufferAlignment());
 *     }
 *     if (useTransferBuffers) {
 *         // Both alignment and the requiredTransferBufferAlignment must be powers of two, so max
 *         // provides the correct alignment semantics
 *         alignment = std::max(alignment, SkTo<uint32_t>(caps->requiredTransferBufferAlignment()));
 *     }
 *     return alignment;
 * }
 * ```
 */
public fun minimumAlignment(
  type: BufferType,
  useTransferBuffers: Boolean,
  caps: Caps?,
): UInt {
  TODO("Implement minimumAlignment")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t min_block_size(BufferType type,
 *                         uint32_t minAlignment,
 *                         const DrawBufferManager::Options& opts) {
 *     uint32_t size;
 *     if (type == BufferType::kIndex || type == BufferType::kIndexStorage) {
 *         size = opts.fIndexBufferSize;
 *     } else if (type == BufferType::kVertex || type == BufferType::kVertexStorage) {
 *         size = opts.fVertexBufferMinSize;
 *     } else {
 *         size = opts.fStorageBufferMinSize;
 *     }
 * #if defined(GPU_TEST_UTILS)
 *     if (opts.fUseExactBuffSizes) {
 *         return size; // No extra alignment
 *     }
 * #endif
 *
 *     return SkAlignTo(size, minAlignment);
 * }
 * ```
 */
public fun minBlockSize(
  type: BufferType,
  minAlignment: UInt,
  opts: DrawBufferManager.Options,
): UInt {
  TODO("Implement minBlockSize")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t max_block_size(BufferType type,
 *                         uint32_t minAlignment,
 *                         const DrawBufferManager::Options& opts) {
 * #if defined(GPU_TEST_UTILS)
 *     if (opts.fUseExactBuffSizes) {
 *         // Clamp to the minimum size
 *         return min_block_size(type, minAlignment, opts);
 *     }
 * #endif
 *
 *     uint32_t size;
 *     if (type == BufferType::kIndex || type == BufferType::kIndexStorage) {
 *         size = opts.fIndexBufferSize;
 *     } else if (type == BufferType::kVertex || type == BufferType::kVertexStorage) {
 *         size = opts.fVertexBufferMaxSize;
 *     } else {
 *         size = opts.fStorageBufferMaxSize;
 *     }
 *
 *     return SkAlignTo(size, minAlignment);
 * }
 * ```
 */
public fun maxBlockSize(
  type: BufferType,
  minAlignment: UInt,
  opts: DrawBufferManager.Options,
): UInt {
  TODO("Implement maxBlockSize")
}

/**
 * C++ original:
 * ```cpp
 * SkAlpha initial_alpha_for_elements(const ClipStack::ElementList& elements) {
 *     SkASSERT(!elements.empty());
 *     return elements[0]->fOp == SkClipOp::kIntersect ? 0x00 : 0xFF;
 * }
 * ```
 */
public fun initialAlphaForElements(elements: ClipStack.ElementList): SkAlpha {
  TODO("Implement initialAlphaForElements")
}

/**
 * C++ original:
 * ```cpp
 * void render_elements(RasterMaskHelper* helper, const ClipStack::ElementList& elements) {
 *     SkASSERT(!elements.empty());
 *     bool isFirst = true;
 *     for (const auto& ePtr : elements) {
 *         const auto& e = *ePtr;
 *         uint8_t alpha;
 *         bool invert;
 *         if (e.fOp == SkClipOp::kIntersect) {
 *             // Intersect modifies pixels outside of its geometry. If this is the first element,
 *             // we can draw directly with coverage 1 since we cleared to 0. Otherwise we draw the
 *             // inverse-filled shape with 0 coverage to erase everything outside the element.
 *             if (isFirst) {
 *                 alpha = 0xFF;
 *                 invert = false;
 *             } else {
 *                 alpha = 0x00;
 *                 invert = true;
 *             }
 *         } else {
 *             // For difference ops, can always just subtract the shape directly by drawing 0 coverage
 *             SkASSERT(e.fOp == SkClipOp::kDifference);
 *             alpha = 0x00;
 *             invert = false;
 *         }
 *
 *         // Draw the shape; based on how we've initialized the buffer and chosen alpha+invert,
 *         // every element is drawn with the kReplace_Op
 *         if (invert != e.fShape.inverted()) {
 *             Shape inverted(e.fShape);
 *             inverted.setInverted(invert);
 *             helper->drawClip(inverted, e.fLocalToDevice, alpha);
 *         } else {
 *             helper->drawClip(e.fShape, e.fLocalToDevice, alpha);
 *         }
 *         isFirst = false;
 *     }
 * }
 * ```
 */
public fun renderElements(helper: RasterMaskHelper?, elements: ClipStack.ElementList) {
  TODO("Implement renderElements")
}

/**
 * C++ original:
 * ```cpp
 * Rect subtract(const Rect& a, const Rect& b, bool exact) {
 *     SkRect diff;
 *     if (SkRectPriv::Subtract(a.asSkRect(), b.asSkRect(), &diff) || !exact) {
 *         // Either A-B is exactly the rectangle stored in diff, or we don't need an exact answer
 *         // and can settle for the subrect of A excluded from B (which is also 'diff')
 *         return Rect{diff};
 *     } else {
 *         // For our purposes, we want the original A when A-B cannot be exactly represented
 *         return a;
 *     }
 * }
 * ```
 */
public fun subtract(
  a: Rect,
  b: Rect,
  exact: Boolean,
): Rect {
  TODO("Implement subtract")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t next_gen_id() {
 *     // 0-2 are reserved for invalid, empty & wide-open
 *     static const uint32_t kFirstUnreservedGenID = 3;
 *     static std::atomic<uint32_t> nextID{kFirstUnreservedGenID};
 *
 *     uint32_t id;
 *     do {
 *         id = nextID.fetch_add(1, std::memory_order_relaxed);
 *     } while (id < kFirstUnreservedGenID);
 *     return id;
 * }
 * ```
 */
public fun nextGenId(): UInt {
  TODO("Implement nextGenId")
}

/**
 * C++ original:
 * ```cpp
 * bool oriented_bbox_intersection(const Rect& a, const Transform& aXform,
 *                                 const Rect& b, const Transform& bXform) {
 *     // NOTE: We intentionally exclude projected bounds for two reasons:
 *     //   1. We can skip the division by w and worring about clipping to w = 0.
 *     //   2. W/o the projective case, the separating axes are simpler to compute (see below).
 *     SkASSERT(aXform.type() != Transform::Type::kPerspective &&
 *              bXform.type() != Transform::Type::kPerspective);
 *     SkV4 quadA[4], quadB[4];
 *
 *     aXform.mapPoints(a, quadA);
 *     bXform.mapPoints(b, quadB);
 *
 *     // There are 4 separating axes, defined by the two normals from quadA and from quadB, but
 *     // since they were produced by transforming a rectangle by an affine transform, we know the
 *     // normals are orthoganal to the basis vectors of upper 2x2 of their two transforms.
 *     auto axesX = skvx::float4(-aXform.matrix().rc(1,0), -aXform.matrix().rc(1,1),
 *                               -bXform.matrix().rc(1,0), -bXform.matrix().rc(1,1));
 *     auto axesY = skvx::float4(aXform.matrix().rc(0,0), aXform.matrix().rc(0,1),
 *                               bXform.matrix().rc(0,0), bXform.matrix().rc(0,1));
 *
 *     // Projections of the 4 corners of each quadrilateral vs. the 4 axes. For orthonormal
 *     // transforms, the projections of a quad's corners to its own normal axes should work out
 *     // to the original dimensions of the rectangle, but this code handles skew and scale factors
 *     // without branching.
 *     auto aProj0 = quadA[0].x * axesX + quadA[0].y * axesY;
 *     auto aProj1 = quadA[1].x * axesX + quadA[1].y * axesY;
 *     auto aProj2 = quadA[2].x * axesX + quadA[2].y * axesY;
 *     auto aProj3 = quadA[3].x * axesX + quadA[3].y * axesY;
 *
 *     auto bProj0 = quadB[0].x * axesX + quadB[0].y * axesY;
 *     auto bProj1 = quadB[1].x * axesX + quadB[1].y * axesY;
 *     auto bProj2 = quadB[2].x * axesX + quadB[2].y * axesY;
 *     auto bProj3 = quadB[3].x * axesX + quadB[3].y * axesY;
 *
 *     // Minimum and maximum projected values against the 4 axes, for both quadA and quadB, which
 *     // gives us four pairs of intervals to test for separation.
 *     auto minA = min(min(aProj0, aProj1), min(aProj2, aProj3));
 *     auto maxA = max(max(aProj0, aProj1), max(aProj2, aProj3));
 *     auto minB = min(min(bProj0, bProj1), min(bProj2, bProj3));
 *     auto maxB = max(max(bProj0, bProj1), max(bProj2, bProj3));
 *
 *     auto overlaps = (minB <= maxA) & (minA <= maxB);
 *     return all(overlaps); // any non-overlapping interval would imply no intersection
 * }
 * ```
 */
public fun orientedBboxIntersection(
  a: Rect,
  aXform: Transform,
  b: Rect,
  bXform: Transform,
): Boolean {
  TODO("Implement orientedBboxIntersection")
}

/**
 * C++ original:
 * ```cpp
 * SkEnumBitMask<EdgeAAQuad::Flags> clipped_edges(const Rect& shape, const Rect& other) {
 *     // Since RB are stored negated in vals(), this works out to
 *     //     [other.LT >= shape.LT, other.RB <= shape.RB]
 *     auto insideMask = other.vals() >= shape.vals();
 *     return (insideMask[0] ? EdgeAAQuad::Flags::kLeft   : EdgeAAQuad::Flags::kNone) |
 *            (insideMask[1] ? EdgeAAQuad::Flags::kTop    : EdgeAAQuad::Flags::kNone) |
 *            (insideMask[2] ? EdgeAAQuad::Flags::kRight  : EdgeAAQuad::Flags::kNone) |
 *            (insideMask[3] ? EdgeAAQuad::Flags::kBottom : EdgeAAQuad::Flags::kNone);
 * }
 * ```
 */
public fun clippedEdges(shape: Rect, other: Rect): SkEnumBitMask<EdgeAAQuad.Flags> {
  TODO("Implement clippedEdges")
}

/**
 * C++ original:
 * ```cpp
 * bool intersect_shape(const Transform& otherToDevice, const Shape& otherShape,
 *                      const Transform& localToDevice, Shape* shape,
 *                      SkEnumBitMask<EdgeAAQuad::Flags>* edgeFlags) {
 *     // There are only a subset of shape types that we can analytically intersect with each other,
 *     // assuming a simple fill style (always the case for clip shapes):
 *     //
 *     //  rects, rrects, flood-fills (empty+inverse-fill)
 *     //
 *     // Flood-fills only appear as part of a draw, so it's only checked for `shape` and not
 *     // `otherShape`. In theory, per-edge AA quads could also be included but they do not appear as
 *     // clip shapes.
 *     //
 *     // Paths and arcs have complex intersection logic, so are skipped under the assumption that
 *     // simple cases have already been mapped to a rect or rrect. Lines are only ever stroked, so
 *     // are incompatible with this function.
 *     //
 *     // EdgeAAQuads that are rectangular can be intersected by being treated as a rect shape and
 *     // adjusting edge flags as non-AA edges are clipped out.
 *     bool shapeIntersectable = shape->isRect() ||
 *                               shape->isRRect() ||
 *                               shape->isFloodFill();
 *     bool otherIntersectable = otherShape.isRect() || otherShape.isRRect();
 *     // Only clip shapes are used for `otherShape`, so we shouldn't see any flood fills here
 *     SkASSERT(!otherShape.isFloodFill());
 *     // Only rects should have edge flags other than kAll
 *     SkASSERT(*edgeFlags == EdgeAAQuad::Flags::kAll || shape->isRect());
 *
 *     if (!shapeIntersectable || !otherIntersectable) {
 *         // Technically if shapeIntersectable was true for empty+inverse, we could turn the flood
 *         // fill into `otherShape` regardless of its type, but those other types are more expensive
 *         // to render and in the situation where many draws fill against a clip path, we'd want to
 *         // draw the clip a single time vs. drawing the path multiple times.
 *         return false;
 *     }
 *
 *     // In order to combine, otherShape must be able to map into `localToDevice` without changing
 *     // shape class (e.g. to a path when rotated) in order for shading to apply in the same
 *     // coordinate space. This is possible if the relative transform between otherToDevice and
 *     // localToDevice is rectStaysRect.
 *     Transform storage{SkM44::kUninitialized_Constructor};
 *
 *     // We track `local` to `other` and use the `inverseMapRect` functions to map the `otherShape`
 *     // into local space when possible. Using `localToOther` instead of `otherToLocal` allows the
 *     // common case of a device-space clip (otherToDevice == I) and an axis-aligned draw to
 *     // simply use `localToDevice` as `localToOther`.
 *     const Transform* localToOther;
 *
 *     if (otherToDevice == localToDevice) {
 *         // No coordinate space conversion, so set to null to signal identity mapping is skippable.
 *         // NOTE: This case arises in clip-clip combinations when both were axis-aligned and pre-
 *         // transformed to device space.
 *         localToOther = nullptr;
 *     } else if (otherToDevice.type() == Transform::Type::kIdentity &&
 *                localToDevice.type() <= Transform::Type::kRectStaysRect) {
 *         // Relative transform is (otherToDevice)^-1*localToDevice = localToDevice
 *         localToOther = &localToDevice;
 *     } else if (otherToDevice.type() <= Transform::Type::kRectStaysRect &&
 *                localToDevice.type() == Transform::Type::kIdentity) {
 *         // Relative transform is otherToDevice^-1*localToDevice = otherToDevice^-1
 *         // (which may not occur in a common scenario but is harmless). Inverse() is mostly
 *         // shuffling bytes around, not recomputing the inverse.
 *         storage = Transform::Inverse(otherToDevice);
 *         localToOther = &storage;
 *     } else {
 *         // Calculate (otherToDevice)^-1*localToDevice and see if the relative transform is
 *         // of the right type.
 *         storage = Transform(otherToDevice.inverse() * localToDevice);
 *         if (storage.type() <= Transform::Type::kRectStaysRect) {
 *             localToOther = &storage;
 *         } else {
 *             // `otherShape` can't be trivially mapped to the local coordinate space
 *             return false;
 *         }
 *     }
 *
 *     // Since `otherShape` is either a rect or a round rect, bounds() is tight to the linear edges.
 *     Rect localOtherRect = otherShape.bounds();
 *     if (localToOther) {
 *         // In this block, `localOtherRect` is defined in the other coord space and `mapped` is in
 *         // the local coord space. At the end of the block, `localOtherRect` is set to `mapped` so
 *         // that afterwards it is always defined in local space.
 *         Rect mapped = localToOther->inverseMapRect(localOtherRect);
 *         // If we don't have enough precision, the other shape might not map back to the geometry.
 *         // Allow up to 1/1000th of a pixel in tolerance when mapping between coordinate spaces,
 *         // otherwise we'll have to clip the shapes independently.
 *         const float otherTol =
 *                 Shape::kDefaultPixelTolerance * otherToDevice.localAARadius(localOtherRect);
 *         if (localOtherRect.isEmptyNegativeOrNaN() ||
 *             !localToOther->mapRect(mapped).nearlyEquals(localOtherRect, otherTol)) {
 *             return false;
 *         }
 *         localOtherRect = mapped;
 *     }
 *     // Remember the edges that get clipped by the intersection
 *     SkEnumBitMask<EdgeAAQuad::Flags> clippedEdges = clipped_edges(shape->bounds(), localOtherRect);
 *     if (!shape->isFloodFill()) {
 *         // And now it's tight to the intersection with `shape`, sans any corner rounding
 *         localOtherRect.intersect(shape->bounds());
 *     }
 *     // Make sure that the intersected shape does not become subpixel in size, since drawing a
 *     // subpixel/hairline shape produces a different result than something that's clipped.
 *     float localAARadius = localToDevice.localAARadius(localOtherRect);
 *     if (!std::isfinite(localAARadius) || any(localOtherRect.size() <= localAARadius)) {
 *         return false;
 *     }
 *
 *     SkRRect localOtherRRect;
 *     if (otherShape.isRect()) {
 *         if (shape->isRect() || shape->isFloodFill()) {
 *             SkASSERT(*edgeFlags == EdgeAAQuad::Flags::kAll || !shape->isFloodFill());
 *             // Assuming that non-AA edges seam with non-AA edges other quads to create a uniform
 *             // coverage field, we turn on the AA edge flag when coincident or clipped. This will
 *             // create a nice AA edge from this draw while the other non-AA quad is discarded.
 *             *edgeFlags |= clippedEdges; // This is a no-op if shape was a flood fill
 *             shape->setRect(localOtherRect);
 *             return true;
 *         } else {
 *             // Fall back to rrect+rrect intersection
 *             localOtherRRect = SkRRect::MakeRect(localOtherRect.asSkRect());
 *         }
 *     } else {
 *         SkASSERT(otherShape.isRRect());
 *         if (localToOther) {
 *             if (auto rr = otherShape.rrect().transform(localToOther->inverse().asM33())) {
 *                 localOtherRRect = *rr;
 *             } else {
 *                 // Transformation produced invalid geometry
 *                 return false;
 *             }
 *         } else {
 *             localOtherRRect = otherShape.rrect();
 *         }
 *
 *         if (shape->isRect() && *edgeFlags != EdgeAAQuad::Flags::kAll) {
 *             // When combining a mixed edge AA quad with a rounded rectangle, we require that all
 *             // non-AA edges be clipped out entirely.
 *             if ((clippedEdges | *edgeFlags) != EdgeAAQuad::Flags::kAll) {
 *                 // The intersection shows AA'ed round corners and non-AA'ed edges, which can't be
 *                 // represented by just Geometry or Shape.
 *                 return false;
 *             }
 *         } else if (shape->isFloodFill()) {
 *             SkASSERT(*edgeFlags == EdgeAAQuad::Flags::kAll);
 *             shape->setRRect(localOtherRRect);
 *             return true;
 *         } // Else continue with rrect+rrect intersection
 *     }
 *
 *     // `shape` can only be rect or rrect at this point, flood fill should already have returned.
 *     // If we've made it this far, we've also determined that the edge flags should be set to kAll
 *     // on a successful rrect+rrect intersection.
 *     SkASSERT(shape->isRect() || shape->isRRect());
 *
 *     SkRRect localRRect = SkRRectPriv::ConservativeIntersect(
 *             localOtherRRect,
 *             shape->isRect() ? SkRRect::MakeRect(shape->rect().asSkRect()) : shape->rrect());
 *     if (localRRect.isRect()) {
 *         // Valid shape that can be simplified to rect
 *         shape->setRect(localRRect.rect());
 *         *edgeFlags = EdgeAAQuad::Flags::kAll;
 *         return true;
 *     } else if (!localRRect.isEmpty()) {
 *         // Intersection is representable as a rrect still
 *         shape->setRRect(localRRect);
 *         *edgeFlags = EdgeAAQuad::Flags::kAll;
 *         return true;
 *     } else {
 *         // Intersection is complex, leave edge flags unmodified
 *         return false;
 *     }
 * }
 * ```
 */
public fun intersectShape(
  otherToDevice: Transform,
  otherShape: Shape,
  localToDevice: Transform,
  shape: Shape?,
  edgeFlags: SkEnumBitMask<EdgeAAQuad.Flags>?,
): Boolean {
  TODO("Implement intersectShape")
}

/**
 * C++ original:
 * ```cpp
 * Rect snap_scissor(const Rect& a, const Rect& deviceBounds) {
 *     // Snapping to 4 pixel boundaries seems to give a good tradeoff between rasterizing slightly
 *     // more (but being clipped by the depth test), vs. setting a tight scissor that forces a state
 *     // change.
 *     // NOTE: This rounds out to the *next* multiple of 4, so that if the input rectangle happens to
 *     // land on a multiple of 4 we still create some padding to avoid scissoring just AA outsets.
 *     static constexpr int kRes = 4;
 *     Rect snapped = a.makeOutset(kRes - 1.f);
 *     snapped = Rect::FromVals(snapped.vals() * (1.f / kRes)).makeRoundOut();
 *     return Rect::FromVals(snapped.vals() * kRes).makeIntersect(deviceBounds);
 * }
 * ```
 */
public fun snapScissor(a: Rect, deviceBounds: Rect): Rect {
  TODO("Implement snapScissor")
}

/**
 * C++ original:
 * ```cpp
 * bool ClipStack::TransformedShape::intersects(const TransformedShape& o) const {
 *     if (!fOuterBounds.intersects(o.fOuterBounds)) {
 *         return false;
 *     }
 *
 *     if (fLocalToDevice.type() <= Transform::Type::kRectStaysRect &&
 *         o.fLocalToDevice.type() <= Transform::Type::kRectStaysRect) {
 *         // The two shape's coordinate spaces are different but both rect-stays-rect or simpler.
 *         // This means, though, that their outer bounds approximations are tight to their transormed
 *         // shape bounds. There's no point to do further tests given that and that we already found
 *         // that these outer bounds *do* intersect.
 *         return true;
 *     } else if (fLocalToDevice == o.fLocalToDevice) {
 *         // Since the two shape's local coordinate spaces are the same, we can compare shape
 *         // bounds directly for a more accurate intersection test. We intentionally do not go
 *         // further and do shape-specific intersection tests since these could have unknown
 *         // complexity (for paths) and limited utility (e.g. two round rects that are disjoint
 *         // solely from their corner curves).
 *         return fShape.bounds().intersects(o.fShape.bounds());
 *     } else if (fLocalToDevice.type() != Transform::Type::kPerspective &&
 *                o.fLocalToDevice.type() != Transform::Type::kPerspective) {
 *         // The shapes don't share the same coordinate system, and their approximate 'outer'
 *         // bounds in device space could have substantial outsetting to contain the transformed
 *         // shape (e.g. 45 degree rotation). Perform a more detailed check on their oriented
 *         // bounding boxes.
 *         return oriented_bbox_intersection(fShape.bounds(), fLocalToDevice,
 *                                           o.fShape.bounds(), o.fLocalToDevice);
 *     }
 *     // Else multiple perspective transforms are involved, so assume intersection and allow the
 *     // rasterizer to handle perspective clipping.
 *     return true;
 * }
 * ```
 */
public fun intersects(o: TransformedShape) {
  TODO("Implement intersects")
}

/**
 * C++ original:
 * ```cpp
 * bool ClipStack::DrawShape::applyStyle(const SkStrokeRec& style, const Rect& deviceBounds) {
 *     // For overriding fLocalToDevice when the shape is only tracking device-space bounds
 *     static const Transform kIdentity = Transform::Identity();
 *
 *     fTransformedShapeBounds = fShape.bounds(); // not scissor'ed, regular fill rule bounds
 *     auto origSize = fTransformedShapeBounds.size();
 *     if (!SkIsFinite(origSize.x(), origSize.y())) {
 *         // Discard all non-finite geometry as if it were clipped out
 *         return false;
 *     }
 *
 *     // Discard fills and strokes that cannot produce any coverage: an empty fill, or a
 *     // zero-length stroke that has butt caps. Otherwise the stroke style applies to a vertical
 *     // or horizontal line (making it non-empty), or it's a zero-length path segment that
 *     // must produce round or square caps (making it non-empty):
 *     //     https://www.w3.org/TR/SVG11/implnote.html#PathElementImplementationNotes
 *     if (!fShape.inverted() && (fShape.isLine() || any(origSize == 0.f))) {
 *         if (style.isFillStyle() || (style.getCap() == SkPaint::kButt_Cap && all(origSize == 0.f))) {
 *             return false;
 *         }
 *     }
 *
 *     // Anti-aliasing makes shapes larger than their original coordinates, but we only care about
 *     // that for local clip checks in certain cases (see above).
 *     // NOTE: After this if-else block, `transformedShapeBounds` will be in device space.
 *     float localAAOutset = fLocalToDevice->localAARadius(fTransformedShapeBounds);
 *     if (!SkIsFinite(localAAOutset)) SK_UNLIKELY {
 *         // We cannot calculate an accurate local shape bounds, and transformedShapeBounds is meant
 *         // to be unclipped. This is to maximize atlas reuse for mostly unclipped draws and to detect
 *         // when a scissor state change is required. Setting transformedShapeBounds to deviceBounds
 *         // is harmless in this case as these benefits are unlikely to apply for this transform.
 *         fTransformedShapeBounds = deviceBounds;
 *         fShape.setRect(deviceBounds);
 *         fLocalToDevice = &kIdentity;
 *         fShapeMatchesGeometry = false;
 *     } else {
 *         // SkStrokeRec::GetInflationRadius() returns a device-space inflation for hairlines.
 *         float localOutset = 0.f;
 *         if (!style.isFillStyle() && !style.isHairlineStyle()) {
 *             // Rectangles, rounded rectangles, and lines do not produce miters so don't count the
 *             // pessimistic limit against their draw bounds.
 *             const float effectiveMiterLimit = fShape.isPath() ? style.getMiter() : 1.f;
 *             // Rectangles and rounded rectangles don't have caps, so don't count that against their
 *             // draw bounds (if we could efficiently know a path was a closed contour, it could
 *             // be included here too).
 *             const SkPaint::Cap effectiveCap = fShape.isRect() || fShape.isRRect()
 *                     ? SkPaint::kButt_Cap : style.getCap();
 *             localOutset = SkStrokeRec::GetInflationRadius(style.getJoin(),
 *                                                           effectiveMiterLimit,
 *                                                           effectiveCap,
 *                                                           style.getWidth());
 *         }
 *
 *         if (style.isHairlineStyle() ||
 *             (!style.isFillStyle() && style.getWidth() < localAAOutset) ||
 *             (style.isFillStyle() && !fShape.inverted() && any(origSize < localAAOutset))) {
 *             // The geometry is a hairline or projects to a subpixel shape, so rendering will not
 *             // follow the typical 1/2px outset anti-aliasing that is compatible with clipping.
 *             // In this case, apply the local AA radius to the shape to have a conservative clip
 *             // query while preserving the oriented bounding box.
 *             localOutset += localAAOutset;
 *         }
 *
 *         if (localOutset > 0.f) {
 *             // Propagate style and AA outset into styledShape so clip queries reflect style.
 *             fTransformedShapeBounds.outset(localOutset);
 *
 *             bool inverted = fShape.inverted();
 *             if (fShape.isRRect()) {
 *                 // Try to preserve the rounded corners, which can reduce the chance of clipping
 *                 // stroked rounded rects that are clipped to a round rect matching their outer edge.
 *                 fShape.rrect().outset(localOutset, localOutset, &fShape.rrect());
 *             } else
 *             {
 *                 fShape.setRect(fTransformedShapeBounds); // it's still local at this point
 *             }
 *             fShape.setInverted(inverted);  // preserve original inversion state
 *             fShapeMatchesGeometry = false;
 *         }
 *
 *         fTransformedShapeBounds = fLocalToDevice->mapRect(fTransformedShapeBounds);
 *     }
 *
 *     fOuterBounds = fTransformedShapeBounds;
 *     fInnerBounds = Rect::InfiniteInverted();
 *
 *     if (this->shapeCanBeModified() && fLocalToDevice->type() <= Transform::Type::kRectStaysRect) {
 *         if (fShape.isRect()) {
 *             fInnerBounds = fOuterBounds;
 *         } else if (fShape.isRRect()) {
 *             SkRect rrectInnerBounds = SkRRectPriv::InnerBounds(fShape.rrect());
 *             if (!rrectInnerBounds.isEmpty()) {
 *                 fInnerBounds = fLocalToDevice->mapRect(rrectInnerBounds);
 *             }
 *         }
 *         // Otherwise it's a flood fill, but should have empty bounds anyways
 *     }
 *     // Otherwise we either don't need the inner bounds, or the inner bounds can't be computed
 *     // for a non-axis-aligned transform
 *
 *      return true; // Something can be drawn based on style (might still be clipped out)
 * }
 * ```
 */
public fun applyStyle(style: SkStrokeRec, deviceBounds: Rect) {
  TODO("Implement applyStyle")
}

/**
 * C++ original:
 * ```cpp
 * void ClipStack::DrawShape::applyScissor(const Rect& scissor) {
 *     // Apply the scissor to the outer bounds because it restricts rasterization and will allow
 *     // the SaveRecord::testForDraw() case to detect no clip influence if only the scissor is
 *     // needed.
 *     SkASSERT(scissor == Rect(scissor.asSkIRect())); // `scissor` must be integer valued
 *     fScissor.intersect(scissor); // For first call, fScissor is infinite so this is assignment
 *     fOuterBounds.intersect(scissor);
 *     fInnerBounds.intersect(scissor);
 * }
 * ```
 */
public fun applyScissor(scissor: Rect) {
  TODO("Implement applyScissor")
}

/**
 * C++ original:
 * ```cpp
 * Clip ClipStack::DrawShape::toClip(Geometry* geometry,
 *                                   const NonMSAAClip& analyticClip,
 *                                   const SkShader* clipShader) {
 *     if (fShapeWasModified) {
 *         // Sync back to the geometry that will be drawn
 *         SkASSERT(this->shapeCanBeModified());
 *         if (geometry->isEdgeAAQuad() && fShape.isRect()) {
 *             // Preserve the EdgeAAQuad geometry type and sync updated edge flags
 *             SkASSERT(geometry->edgeAAQuad().isRect());
 *             geometry->setEdgeAAQuad(EdgeAAQuad(fShape.rect(), fEdgeFlags));
 *         } else {
 *             SkASSERT(fEdgeFlags == EdgeAAQuad::Flags::kAll);
 *             geometry->setShape(fShape);
 *         }
 *         // Reconstruct new transformedShapeBounds and outer bounds
 *         fTransformedShapeBounds = fLocalToDevice->mapRect(fShape.bounds());
 *         fOuterBounds = fTransformedShapeBounds.makeIntersect(fScissor);
 *     }
 *
 *     Rect drawBounds = fShape.inverted() ? fScissor : fOuterBounds;
 *     // If the draw isn't clipped out (empty drawBounds), it should be in the scissor rect
 *     SkASSERT(drawBounds.isEmptyNegativeOrNaN() || fScissor.contains(drawBounds));
 *     // If the scissor is empty, the draw bounds must also be empty
 *     SkASSERT(!fScissor.isEmptyNegativeOrNaN() || drawBounds.isEmptyNegativeOrNaN());
 *     // fScissor.asSkIRect() must be equivalent
 *     SkASSERT(fScissor == Rect(fScissor.asSkIRect()));
 *     return Clip(drawBounds, fTransformedShapeBounds,
 *                 fScissor.asSkIRect(), analyticClip, clipShader);
 * }
 * ```
 */
public fun toClip(
  geometry: Geometry?,
  analyticClip: NonMSAAClip,
  clipShader: SkShader?,
) {
  TODO("Implement toClip")
}

/**
 * C++ original:
 * ```cpp
 * void ClipStack::DrawShape::resetToFloodFill() {
 *     if (this->shapeCanBeModified() && !fShape.isFloodFill()) {
 *         fShape.reset();
 *         fShape.setInverted(true);
 *         fEdgeFlags = EdgeAAQuad::Flags::kAll;
 *         fOuterBounds = fInnerBounds = Rect::InfiniteInverted();
 *         fShapeWasModified = true;
 *     }
 * }
 * ```
 */
public fun resetToFloodFill() {
  TODO("Implement resetToFloodFill")
}

/**
 * C++ original:
 * ```cpp
 * bool ClipStack::DrawShape::intersectClipElement(const RawElement& clip) {
 *     SkASSERT(clip.op() == SkClipOp::kIntersect);
 *     if (this->shapeCanBeModified() &&
 *         intersect_shape(clip.localToDevice(), clip.shape(),
 *                         *fLocalToDevice, &fShape, &fEdgeFlags)) {
 *         SkASSERT(!fShape.inverted());
 *         if (fOuterBounds.isEmptyNegativeOrNaN()) {
 *             // Changing from a flood fill to the clip's shape
 *             fOuterBounds = clip.outerBounds();
 *             fInnerBounds = clip.innerBounds();
 *         } else {
 *             // Restricting the shape's geometry by the clip
 *             fOuterBounds.intersect(clip.outerBounds());
 *             fInnerBounds.intersect(clip.innerBounds());
 *             SkASSERT(!fOuterBounds.isEmptyNegativeOrNaN()); // Should have been caught earlier
 *         }
 *
 *         fShapeWasModified = true;
 *         return true;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun intersectClipElement(clip: RawElement) {
  TODO("Implement intersectClipElement")
}

/**
 * C++ original:
 * ```cpp
 * AnalyticClip can_apply_analytic_clip(const Shape& shape, const Transform& localToDevice) {
 *     if (localToDevice.type() != Transform::Type::kIdentity) {
 *         return {};
 *     }
 *
 *     // The circular rrect clip only handles rrect radii >= kRadiusMin, circular radii less than
 *     // this are coerced to be rectangular.
 *     static constexpr float kRadiusMin = SK_ScalarHalf;
 *
 *     // Can handle Rect directly.
 *     if (shape.isRect()) {
 *         return {shape.rect(), kRadiusMin, AnalyticClip::kNone_EdgeFlag, shape.inverted()};
 *     }
 *
 *     // Otherwise we only handle certain kinds of RRects, specifically only approximately simple
 *     // circular rrects (e.g. all 4 corners can be described by a single radius value).
 *     if (!shape.isRRect()) {
 *         return {};
 *     }
 *
 *     const SkRRect& rrect = shape.rrect();
 *     if (rrect.isOval() || rrect.isSimple()) {
 *         SkVector radii = SkRRectPriv::GetSimpleRadii(rrect);
 *         if (radii.fX < kRadiusMin || radii.fY < kRadiusMin) {
 *             // In this case the corners are extremely close to rectangular and we collapse the
 *             // clip to a rectangular clip.
 *             return {rrect.rect(), kRadiusMin, AnalyticClip::kNone_EdgeFlag, shape.inverted()};
 *         }
 *         if (SkRRectPriv::IsRelativelyCircular(radii.fX, radii.fY, Shape::kDefaultPixelTolerance)) {
 *             return {rrect.rect(), radii.fX, AnalyticClip::kAll_EdgeFlag, shape.inverted()};
 *         } else {
 *             return {};
 *         }
 *     }
 *
 *     // If rrect is not an oval or simple, it's either empty, rect, 9-patch, or complex. However,
 *     // empty should have been handled by the clip stack, and rect ought to have been simplified
 *     // into an explicit Rect shape (already handled above). That leaves 9-patch and complex,
 *     // so we check for the "tab" cases - two adjacent circular corners and two square corners.
 *     // It just so happens that if a rect RRect slipped through the cracks, we detect it here too.
 *     constexpr uint32_t kCornerFlags[4] = {
 *         AnalyticClip::kTop_EdgeFlag | AnalyticClip::kLeft_EdgeFlag,
 *         AnalyticClip::kTop_EdgeFlag | AnalyticClip::kRight_EdgeFlag,
 *         AnalyticClip::kBottom_EdgeFlag | AnalyticClip::kRight_EdgeFlag,
 *         AnalyticClip::kBottom_EdgeFlag | AnalyticClip::kLeft_EdgeFlag,
 *     };
 *     SkScalar circularRadius = 0;
 *     uint32_t edgeFlags = 0;
 *     int squareCount = 0;
 *     for (int corner = 0; corner < 4; ++corner) {
 *         SkVector radii = rrect.radii((SkRRect::Corner)corner);
 *         // Can only handle circular radii.
 *         // Also applies to corners with both zero and non-zero radii.
 *         if (!SkRRectPriv::IsRelativelyCircular(radii.fX, radii.fY, Shape::kDefaultPixelTolerance)) {
 *             return {};
 *         }
 *         if (radii.fX < kRadiusMin || radii.fY < kRadiusMin) {
 *             // The corner is square, so no need to flag as circular.
 *             squareCount++;
 *             continue;
 *         }
 *         // First circular corner seen
 *         if (!edgeFlags) {
 *             circularRadius = radii.fX;
 *         } else if (!SkRRectPriv::IsRelativelyCircular(radii.fX,
 *                                                       circularRadius,
 *                                                       Shape::kDefaultPixelTolerance)) {
 *             // Radius doesn't match previously seen circular radius
 *             return {};
 *         }
 *         edgeFlags |= kCornerFlags[corner];
 *     }
 *
 *     if (edgeFlags == AnalyticClip::kNone_EdgeFlag) {
 *         // It's a rect (or coerced to a rect)
 *         return {rrect.rect(), kRadiusMin, edgeFlags, shape.inverted()};
 *     } else if (edgeFlags == AnalyticClip::kAll_EdgeFlag && squareCount != 0) {
 *         // If any rounded corner pairs are non-adjacent or if there are three rounded corners all
 *         // edge flags will be set, which is not valid.
 *         return {};
 *     } else {
 *         // At least one corner is rounded, or two adjacent corners are rounded, or all corners
 *         // are approximately the same (but not classified as simple due to inexactness).
 *         return {rrect.rect(), circularRadius, edgeFlags, shape.inverted()};
 *     }
 * }
 * ```
 */
public fun canApplyAnalyticClip(shape: Shape, localToDevice: Transform): AnalyticClip {
  TODO("Implement canApplyAnalyticClip")
}

/**
 * C++ original:
 * ```cpp
 * bool CanUseHardwareBlending(const Caps* caps,
 *                             TextureFormat targetFormat,
 *                             SkBlendMode bm,
 *                             Coverage coverage) {
 *     // Check for special cases that would prevent the usage of direct hardware blending and
 *     // require us to fall back to using shader-based blending.
 *     const bool hasCoverage = coverage != Coverage::kNone;
 *     const bool dstIsFast = caps->getDstReadStrategy() != DstReadStrategy::kTextureCopy;
 *     if (// Using LCD coverage (which must be applied after the blend equation) with any blend mode
 *         // besides SkBlendMode::kSrcOver
 *         // TODO(b/414597217): Add support to use dual-source blending with LCD coverage.
 *         (coverage == Coverage::kLCD && bm != SkBlendMode::kSrcOver) ||
 *
 *         // SkBlendMode::kPlus clamps its output to [0,1], e.g. clamp(D+S,0,1), which is then
 *         // combined with coverage (f) for a final written value of:
 *         //   (1-f)*D + f*clamp(D+S,0,1)
 *         //
 *         // This can be rewritten to min(D+f*S, D+f*(1-D)), which is not representable with *any*
 *         // hardware blend configuration. However, when the target format clamps to [0,1], we can
 *         // approximate the output as min(D+f*S, 1) with a slight degradation in AA quality.
 *         //
 *         // If access to D doesn't require a texture copy, prefer shader blending for the quality.
 *         (bm == SkBlendMode::kPlus && (dstIsFast || !TextureFormatAutoClamps(targetFormat))) ||
 *
 *         // Using an advanced blend mode but the hardware does not support them
 *         (bm > SkBlendMode::kLastCoeffMode && !caps->supportsHardwareAdvancedBlending()) ||
 *
 *         // The blend formula requires dual-source blending, but it is not supported by hardware
 *         (bm <= SkBlendMode::kLastCoeffMode &&
 *          (coverage == Coverage::kLCD ? skgpu::GetLCDBlendFormula(bm).hasSecondaryOutput()
 *                                      : skgpu::GetBlendFormula(/*isOpaque=*/false,
 *                                                               hasCoverage,
 *                                                               bm).hasSecondaryOutput()) &&
 *          !caps->shaderCaps()->fDualSourceBlendingSupport)) {
 *         return false;
 *     }
 *
 *     // In all other cases (which are more commonly encountered; e.g. using a simple blend mode),
 *     // we can use direct HW blending.
 *     return true;
 * }
 * ```
 */
public fun canUseHardwareBlending(
  caps: Caps?,
  targetFormat: TextureFormat,
  bm: SkBlendMode,
  coverage: Coverage,
): Boolean {
  TODO("Implement canUseHardwareBlending")
}

/**
 * C++ original:
 * ```cpp
 * void CollectIntrinsicUniforms(const Caps* caps,
 *                               SkIRect viewport,
 *                               SkIRect dstReadBounds,
 *                               UniformManager* uniforms) {
 *     SkDEBUGCODE(uniforms->setExpectedUniforms(kIntrinsicUniforms, /*isSubstruct=*/false);)
 *
 *     // viewport
 *     {
 *         // The vertex shader needs to divide by the dimension and then multiply by 2, so do this
 *         // once on the CPU. This is because viewport normalization wants to range from -1 to 1, and
 *         // not 0 to 1. If any other user of the viewport uniform requires the true reciprocal or
 *         // original dimensions, this can be adjusted.
 *         SkASSERT(!viewport.isEmpty());
 *         float invTwoW = 2.f / viewport.width();
 *         float invTwoH = 2.f / viewport.height();
 *
 *         // If the NDC Y axis points up (opposite normal skia convention and the underlying view
 *         // convention), upload the inverse height as a negative value. See ShaderInfo::Make
 *         // for how this is used.
 *         if (!caps->ndcYAxisPointsDown()) {
 *             invTwoH *= -1.f;
 *         }
 *         uniforms->write(SkV4{(float) viewport.left(), (float) viewport.top(), invTwoW, invTwoH});
 *     }
 *
 *     // dstReadBounds
 *     {
 *         // Unlike viewport, dstReadBounds can be empty so check for 0 dimensions and set the
 *         // reciprocal to 0. It is also not doubled since its purpose is to normalize texture coords
 *         // to 0 to 1, and not -1 to 1.
 *         int width = dstReadBounds.width();
 *         int height = dstReadBounds.height();
 *         uniforms->write(SkV4{(float) dstReadBounds.left(), (float) dstReadBounds.top(),
 *                              width ? 1.f / width : 0.f, height ? 1.f / height : 0.f});
 *     }
 *
 *     SkDEBUGCODE(uniforms->doneWithExpectedUniforms());
 * }
 * ```
 */
public fun collectIntrinsicUniforms(
  caps: Caps?,
  viewport: SkIRect,
  dstReadBounds: SkIRect,
  uniforms: UniformManager?,
) {
  TODO("Implement collectIntrinsicUniforms")
}

/**
 * C++ original:
 * ```cpp
 * std::string EmitSamplerLayout(const ResourceBindingRequirements& bindingReqs, int* binding) {
 *     std::string result;
 *
 *     if (bindingReqs.fSeparateTextureAndSamplerBinding) {
 *         int samplerIndex = (*binding)++;
 *         int textureIndex = (*binding)++;
 *         result = SkSL::String::printf("layout(webgpu, set=%d, sampler=%d, texture=%d)",
 *                                       bindingReqs.fTextureSamplerSetIdx,
 *                                       samplerIndex,
 *                                       textureIndex);
 *     } else {
 *         int samplerIndex = (*binding)++;
 *         result = SkSL::String::printf("layout(set=%d, binding=%d)",
 *                                       bindingReqs.fTextureSamplerSetIdx,
 *                                       samplerIndex);
 *     }
 *     return result;
 * }
 * ```
 */
public fun emitSamplerLayout(bindingReqs: ResourceBindingRequirements, binding: Int?): String {
  TODO("Implement emitSamplerLayout")
}

/**
 * C++ original:
 * ```cpp
 * std::string GetPipelineLabel(const Caps* caps,
 *                              const ShaderCodeDictionary* dict,
 *                              const RenderPassDesc& renderPassDesc,
 *                              const RenderStep* renderStep,
 *                              UniquePaintParamsID paintID) {
 *     // KEEP IN SYNC with ShaderInfo::pipelineLabel()
 *     std::string label = renderPassDesc.toPipelineLabel().c_str(); // includes the write swizzle
 *     label += " + ";
 *     label += renderStep->name();
 *     label += " + ";
 *     // the shader portion will be "(empty)" for depth-only draws
 *     label += dict->idToString(caps, paintID).c_str();
 *     return label;
 * }
 * ```
 */
public fun getPipelineLabel(
  caps: Caps?,
  dict: ShaderCodeDictionary?,
  renderPassDesc: RenderPassDesc,
  renderStep: RenderStep?,
  paintID: UniquePaintParamsID,
): String {
  TODO("Implement getPipelineLabel")
}

/**
 * C++ original:
 * ```cpp
 * std::string BuildComputeSkSL(const Caps* caps, const ComputeStep* step, BackendApi backend) {
 *     std::string sksl =
 *             SkSL::String::printf("layout(local_size_x=%u, local_size_y=%u, local_size_z=%u) in;\n",
 *                                  step->localDispatchSize().fWidth,
 *                                  step->localDispatchSize().fHeight,
 *                                  step->localDispatchSize().fDepth);
 *
 *     const auto& bindingReqs = caps->resourceBindingRequirements();
 *     const bool texturesUseDistinctIdxRanges = bindingReqs.fComputeUsesDistinctIdxRangesForTextures;
 *     int index = 0;
 *     // NOTE: SkSL Metal codegen always assigns the same binding index to a texture and its sampler.
 *     // TODO: This could cause sampler indices to not be tightly packed if the sampler2D declaration
 *     // comes after 1 or more storage texture declarations (which don't have samplers). An optional
 *     // "layout(msl, sampler=T, texture=T)" syntax to count them separately (like we do for WGSL)
 *     // could come in handy here but it's not supported in MSL codegen yet.
 *     int texIdx = 0;
 *     for (const ComputeStep::ResourceDesc& r : step->resources()) {
 *         using Type = ComputeStep::ResourceType;
 *         switch (r.fType) {
 *             case Type::kUniformBuffer:
 *                 SkSL::String::appendf(&sksl, "layout(binding=%d) uniform ", index++);
 *                 sksl += r.fSkSL;
 *                 break;
 *             case Type::kStorageBuffer:
 *             case Type::kIndirectBuffer:
 *                 SkSL::String::appendf(&sksl, "layout(binding=%d) buffer ", index++);
 *                 sksl += r.fSkSL;
 *                 break;
 *             case Type::kReadOnlyStorageBuffer:
 *                 SkSL::String::appendf(&sksl, "layout(binding=%d) readonly buffer ", index++);
 *                 sksl += r.fSkSL;
 *                 break;
 *             case Type::kWriteOnlyStorageTexture:
 *                 SkSL::String::appendf(&sksl, "layout(binding=%d, rgba8) writeonly texture2D ",
 *                                       texturesUseDistinctIdxRanges ? texIdx++ : index++);
 *                 sksl += r.fSkSL;
 *                 break;
 *             case Type::kReadOnlyTexture:
 *                 SkSL::String::appendf(&sksl, "layout(binding=%d, rgba8) readonly texture2D ",
 *                                       texturesUseDistinctIdxRanges ? texIdx++ : index++);
 *                 sksl += r.fSkSL;
 *                 break;
 *             case Type::kSampledTexture:
 *                 // The following SkSL expects specific backends to have certain resource binding
 *                 // requirements. Before appending the SkSL, assert that these assumptions hold true.
 *                 // TODO(b/396420770): Have this method be more backend-agnostic.
 *                 if (backend == BackendApi::kMetal) {
 *                      // Metal is expected to use combined texture/samplers.
 *                     SkASSERT(!bindingReqs.fSeparateTextureAndSamplerBinding);
 *                     SkSL::String::appendf(&sksl,
 *                                           "layout(metal, binding=%d) ",
 *                                           texturesUseDistinctIdxRanges ? texIdx++ : index++);
 *                 } else if (backend == BackendApi::kDawn) {
 *                     // Dawn is expected to use separate texture/samplers and not use distinct
 *                     // index ranges for texture resources.
 *                     SkASSERT(bindingReqs.fSeparateTextureAndSamplerBinding &&
 *                              !texturesUseDistinctIdxRanges);
 *                     SkSL::String::appendf(
 *                         &sksl, "layout(webgpu, sampler=%d, texture=%d) ", index, index + 1);
 *                     index += 2;
 *                 } else {
 *                     // This SkSL depends upon the assumption that we are using combined texture/
 *                     // samplers and that we are not using separate resource indices for textures.
 *                     SkASSERT(!bindingReqs.fSeparateTextureAndSamplerBinding &&
 *                              !texturesUseDistinctIdxRanges);
 *                     SkSL::String::appendf(&sksl, "layout(binding=%d) ", index++);
 *                 }
 *                 sksl += "sampler2D ";
 *                 sksl += r.fSkSL;
 *                 break;
 *         }
 *         sksl += ";\n";
 *     }
 *
 *     sksl += step->computeSkSL();
 *     return sksl;
 * }
 * ```
 */
public fun buildComputeSkSL(
  caps: Caps?,
  step: ComputeStep?,
  backend: BackendApi,
): String {
  TODO("Implement buildComputeSkSL")
}

/**
 * C++ original:
 * ```cpp
 * const SkStrokeRec& DefaultFillStyle() {
 *     static const SkStrokeRec kFillStyle(SkStrokeRec::kFill_InitStyle);
 *     return kFillStyle;
 * }
 * ```
 */
public fun defaultFillStyle(): SkStrokeRec {
  TODO("Implement defaultFillStyle")
}

/**
 * C++ original:
 * ```cpp
 * std::optional<SkColor4f> extract_paint_color(const PaintParams& paint,
 *                                              const SkColorInfo& dstColorInfo) {
 *     SkBlendMode bm = paint.finalBlendMode();
 *     // Since we don't depend on the dst, a dst-out blend mode implies source is
 *     // opaque, which causes dst-out to behave like clear.
 *     if (bm == SkBlendMode::kClear || bm == SkBlendMode::kDstOut) {
 *         return SkColors::kTransparent;
 *     }
 *
 *     // PaintParams has already consolidated constant shaders or images and applied color filters to
 *     // constant input colors. If the paint still has any of those fields, then we can't extract it.
 *     if (paint.shader() || paint.imageShader() || paint.colorFilter()) {
 *         return std::nullopt;
 *     }
 *
 *     // However, PaintParams converted the color in sRGB and we need to return this in the
 *     // destination color space.
 *     return PaintParams::Color4fPrepForDst(paint.color(), dstColorInfo);
 * }
 * ```
 */
public fun extractPaintColor(paint: PaintParams, dstColorInfo: SkColorInfo): SkColor4f? {
  TODO("Implement extractPaintColor")
}

/**
 * C++ original:
 * ```cpp
 * Rect snap_rect_to_pixels(const Transform& localToDevice,
 *                          const Rect& rect,
 *                          float* strokeWidth=nullptr) {
 *     if (localToDevice.type() > Transform::Type::kRectStaysRect) {
 *         return rect;
 *     }
 *
 *     Rect snappedDeviceRect;
 *     if (!strokeWidth) {
 *         // Just a fill, use round() to emulate non-AA rasterization (vs. roundOut() to get the
 *         // covering bounds). This matches how ClipStack treats clipRects with PixelSnapping::kYes.
 *         snappedDeviceRect = localToDevice.mapRect(rect).round();
 *     } else if (strokeWidth) {
 *         if (*strokeWidth == 0.f) {
 *             // Hairline case needs to be outset by 1/2 device pixels *before* rounding, and then
 *             // inset by 1/2px to get the base shape while leaving the stroke width as 0.
 *             snappedDeviceRect = localToDevice.mapRect(rect);
 *             snappedDeviceRect.outset(0.5f).round().inset(0.5f);
 *         } else {
 *             // For regular strokes, outset by the stroke radius *before* mapping to device space,
 *             // and then round.
 *             snappedDeviceRect = localToDevice.mapRect(rect.makeOutset(0.5f*(*strokeWidth))).round();
 *
 *             // devScales.x() holds scale factor affecting device-space X axis (so max of |m00| or
 *             // |m01|) and y() holds the device Y axis scale (max of |m10| or |m11|).
 *             skvx::float2 devScales = max(abs(skvx::float2(localToDevice.matrix().rc(0,0),
 *                                                           localToDevice.matrix().rc(1,0))),
 *                                          abs(skvx::float2(localToDevice.matrix().rc(0,1),
 *                                                           localToDevice.matrix().rc(1,1))));
 *             skvx::float2 devStrokeWidth = max(round(*strokeWidth * devScales), 1.f);
 *
 *             // Prioritize the axis that has the largest device-space radius (any error from a
 *             // non-uniform scale factor will go into the inner edge of the opposite axis).
 *             // During animating scale factors, preserving the large axis leads to better behavior.
 *             if (devStrokeWidth.x() > devStrokeWidth.y()) {
 *                 *strokeWidth = devStrokeWidth.x() / devScales.x();
 *             } else {
 *                 *strokeWidth = devStrokeWidth.y() / devScales.y();
 *             }
 *
 *             snappedDeviceRect.inset(0.5f * devScales * (*strokeWidth));
 *         }
 *     }
 *
 *     // Map back to local space so that it can be drawn with appropriate coord interpolation.
 *     Rect snappedLocalRect = localToDevice.inverseMapRect(snappedDeviceRect);
 *     // If the transform has an extreme scale factor or large translation, it's possible for floating
 *     // point precision to round `snappedLocalRect` in such a way that re-transforming it by the
 *     // local-to-device matrix no longer matches the expected device bounds.
 *     if (snappedDeviceRect.nearlyEquals(localToDevice.mapRect(snappedLocalRect))) {
 *         return snappedLocalRect;
 *     } else {
 *         // In this case we will just return the original geometry and the pixels will show
 *         // fractional coverage.
 *         return rect;
 *     }
 * }
 * ```
 */
public fun snapRectToPixels(
  localToDevice: Transform,
  rect: Rect,
  strokeWidth: Float? = TODO(),
): Rect {
  TODO("Implement snapRectToPixels")
}

/**
 * C++ original:
 * ```cpp
 * void snap_src_and_dst_rect_to_pixels(const Transform& localToDevice,
 *                                      SkRect* srcRect,
 *                                      SkRect* dstRect) {
 *     if (localToDevice.type() > Transform::Type::kRectStaysRect) {
 *         return;
 *     }
 *
 *     // Assume snapping will succeed and always update 'src' to match; in the event snapping
 *     // returns the original dst rect, then the recalculated src rect is a no-op.
 *     SkMatrix dstToSrc = SkMatrix::RectToRectOrIdentity(*dstRect, *srcRect);
 *     *dstRect = snap_rect_to_pixels(localToDevice, *dstRect).asSkRect();
 *     *srcRect = dstToSrc.mapRect(*dstRect);
 * }
 * ```
 */
public fun snapSrcAndDstRectToPixels(
  localToDevice: Transform,
  srcRect: SkRect?,
  dstRect: SkRect?,
) {
  TODO("Implement snapSrcAndDstRectToPixels")
}

/**
 * C++ original:
 * ```cpp
 * Rect get_inner_bounds(const Geometry& geometry, const Transform& localToDevice) {
 *     auto applyAAInset = [&](Rect rect) {
 *         // If the aa inset is too large, rect becomes empty and the inner bounds draw is
 *         // automatically skipped
 *         float aaInset = localToDevice.localAARadius(rect);
 *         rect.inset(aaInset);
 *         // Only add a second draw if it will have a reasonable number of covered pixels; otherwise
 *         // we are just adding draws to sort and pipelines to switch around.
 *         static constexpr float kInnerFillArea = 64*64;
 *         // Approximate the device-space area based on the minimum scale factor of the transform.
 *         float scaleFactor = sk_ieee_float_divide(1.f, aaInset);
 *         return scaleFactor*rect.area() >= kInnerFillArea ? rect : Rect::InfiniteInverted();
 *     };
 *
 *     if (geometry.isEdgeAAQuad()) {
 *         const EdgeAAQuad& quad = geometry.edgeAAQuad();
 *         if (quad.isRect()) {
 *             return applyAAInset(quad.bounds());
 *         }
 *         // else currently we don't have a function to calculate the largest interior axis aligned
 *         // bounding box of a quadrilateral so skip the inner fill draw.
 *     } else if (geometry.isShape()) {
 *         const Shape& shape = geometry.shape();
 *         if (shape.isRect()) {
 *             return applyAAInset(shape.rect());
 *         } else if (shape.isRRect()) {
 *             return applyAAInset(SkRRectPriv::InnerBounds(shape.rrect()));
 *         }
 *     }
 *
 *     return Rect::InfiniteInverted();
 * }
 * ```
 */
public fun getInnerBounds(geometry: Geometry, localToDevice: Transform): Rect {
  TODO("Implement getInnerBounds")
}

/**
 * C++ original:
 * ```cpp
 * SkIRect rect_to_pixelbounds(const Rect& r) {
 *     return r.makeRoundOut().asSkIRect();
 * }
 * ```
 */
public fun rectToPixelbounds(r: Rect): SkIRect {
  TODO("Implement rectToPixelbounds")
}

/**
 * C++ original:
 * ```cpp
 * bool is_pixel_aligned(const Rect& r, const Transform& t) {
 *     if (t.type() <= Transform::Type::kRectStaysRect) {
 *         Rect devRect = t.mapRect(r);
 *         return devRect.nearlyEquals(devRect.makeRound(), Shape::kDefaultPixelTolerance);
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun isPixelAligned(r: Rect, t: Transform): Boolean {
  TODO("Implement isPixelAligned")
}

/**
 * C++ original:
 * ```cpp
 * bool is_simple_shape(const Shape& shape, const Transform& localToDevice, SkStrokeRec::Style type) {
 *     if (shape.isFloodFill()) {
 *         return true; // Always supported
 *     } else if (!shape.inverted() && type != SkStrokeRec::kStrokeAndFill_Style) {
 *         // A filled line renders nothing but that should be caught earlier, so the actual branches
 *         // in this function can be simplified.
 *         SkASSERT(!shape.isLine() || type != SkStrokeRec::kFill_Style);
 *
 *         if (shape.isRRect() && type == SkStrokeRec::kStroke_Style) {
 *             // Non-hairline stroked round rects require the corner radii to be circular to be
 *             // compatible with the shared Renderer.
 *             const float tol =
 *                     localToDevice.localAARadius(shape.bounds()) * Shape::kDefaultPixelTolerance;
 *             return SkRRectPriv::AllCornersRelativelyCircular(shape.rrect(), tol);
 *         } else if (shape.isRRect() || shape.isRect() || shape.isLine()) {
 *             // There are no restrictions on filled or hairline [r]rects and lines.
 *             return true;
 *         } // Fallthrough
 *     }
 *
 *     // Requires path rendering
 *     return false;
 * }
 * ```
 */
public fun isSimpleShape(
  shape: Shape,
  localToDevice: Transform,
  type: SkStrokeRec.Style,
): Boolean {
  TODO("Implement isSimpleShape")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t next_compilation_id() {
 *     static std::atomic<uint32_t> nextId{0};
 *     // Not worried about overflow since we don't expect that many GraphicsPipelines.
 *     // Even if it wraps around to 0, this is solely for debug logging.
 *     return nextId.fetch_add(1, std::memory_order_relaxed);
 * }
 * ```
 */
public fun nextCompilationId(): UInt {
  TODO("Implement nextCompilationId")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> MakeWithFilter(skgpu::graphite::Recorder* recorder,
 *                               sk_sp<SkImage> src,
 *                               const SkImageFilter* filter,
 *                               const SkIRect& subset,
 *                               const SkIRect& clipBounds,
 *                               SkIRect* outSubset,
 *                               SkIPoint* offset) {
 *     if (!recorder || !src || !filter) {
 *         return nullptr;
 *     }
 *
 *     sk_sp<skif::Backend> backend = skif::MakeGraphiteBackend(recorder, {}, src->colorType());
 *     return as_IFB(filter)->makeImageWithFilter(std::move(backend),
 *                                                std::move(src),
 *                                                subset,
 *                                                clipBounds,
 *                                                outSubset,
 *                                                offset);
 * }
 * ```
 */
public fun makeWithFilter(
  recorder: Recorder?,
  src: SkSp<SkImage>,
  filter: SkImageFilter?,
  subset: SkIRect,
  clipBounds: SkIRect,
  outSubset: SkIRect?,
  offset: SkIPoint?,
): SkSp<SkImage> {
  TODO("Implement makeWithFilter")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> generate_picture_texture(skgpu::graphite::Recorder* recorder,
 *                                                const SkImage_Picture* img,
 *                                                const SkImageInfo& info,
 *                                                SkImage::RequiredProperties requiredProps) {
 *     auto mm = requiredProps.fMipmapped ? skgpu::Mipmapped::kYes : skgpu::Mipmapped::kNo;
 *     // Use a non-budgeted surface since the image wrapping the surface's texture will be owned by
 *     // the client.
 *     sk_sp<Surface> surface = Surface::Make(recorder,
 *                                            info,
 *                                            "LazySkImagePictureTexture",
 *                                            skgpu::Budgeted::kNo,
 *                                            mm,
 *                                            SkBackingFit::kExact,
 *                                            img->props());
 *
 *     if (!surface) {
 *         SKGPU_LOG_E("Failed to create Surface");
 *         return nullptr;
 *     }
 *
 *     img->replay(surface->getCanvas());
 *     // If the surface was created with mipmaps, they will be automatically generated when flushing
 *     // the tasks when 'surface' goes out of scope.
 *     return surface->asImage();
 * }
 * ```
 */
public fun generatePictureTexture(
  recorder: Recorder?,
  img: SkImagePicture?,
  info: SkImageInfo,
  requiredProps: SkImage.RequiredProperties,
): SkSp<SkImage> {
  TODO("Implement generatePictureTexture")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> make_texture_image_from_lazy(skgpu::graphite::Recorder* recorder,
 *                                                    const SkImage_Lazy* img,
 *                                                    SkImage::RequiredProperties requiredProps) {
 *     // 1. Ask the generator to natively create one.
 *     {
 *         if (img->type() == SkImage_Base::Type::kLazyPicture) {
 *             sk_sp<SkImage> newImage =
 *                     generate_picture_texture(recorder,
 *                                              static_cast<const SkImage_Picture*>(img),
 *                                              img->imageInfo(),
 *                                              requiredProps);
 *             if (newImage) {
 *                 SkASSERT(as_IB(newImage)->isGraphiteBacked());
 *                 return newImage;
 *             }
 *             // The fallback for this would be to generate a bitmap, but some picture-backed
 *             // images can only be played back on the GPU.
 *             return nullptr;
 *         }
 *         // There is not an analog to GrTextureGenerator for Graphite yet, but if there was,
 *         // we would want to call it here.
 *     }
 *
 *     // 2. Ask the generator to return a bitmap, which the GPU can convert.
 *     {
 *         SkBitmap bitmap;
 *         if (img->getROPixels(nullptr, &bitmap, SkImage_Lazy::CachingHint::kDisallow_CachingHint)) {
 *             return skgpu::graphite::MakeFromBitmap(recorder,
 *                                                    img->imageInfo().colorInfo(),
 *                                                    bitmap,
 *                                                    nullptr,
 *                                                    skgpu::Budgeted::kNo,
 *                                                    requiredProps,
 *                                                    "LazySkImageBitmapTexture");
 *         }
 *     }
 *
 *     return nullptr;
 * }
 * ```
 */
public fun makeTextureImageFromLazy(
  recorder: Recorder?,
  img: SkImageLazy?,
  requiredProps: SkImage.RequiredProperties,
): SkSp<SkImage> {
  TODO("Implement makeTextureImageFromLazy")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> TextureFromYUVAImages(Recorder* recorder,
 *                                      const SkYUVAInfo& yuvaInfo,
 *                                      SkSpan<const sk_sp<SkImage>> images,
 *                                      sk_sp<SkColorSpace> imageColorSpace) {
 *     // This factory is just a view of the images, so does not actually trigger any work on the
 *     // recorder. It is just used to provide the Caps.
 *     return Image_YUVA::WrapImages(recorder->priv().caps(), yuvaInfo, images, imageColorSpace);
 * }
 * ```
 */
public fun textureFromYUVAImages(
  recorder: Recorder?,
  yuvaInfo: SkYUVAInfo,
  images: SkSpan<SkSp<SkImage>>,
  imageColorSpace: SkSp<SkColorSpace>,
): SkSp<SkImage> {
  TODO("Implement textureFromYUVAImages")
}

/**
 * C++ original:
 * ```cpp
 * TextureProxy* get_base_proxy_for_label(const Image_Base* baseImage) {
 *     if (baseImage->type() == SkImage_Base::Type::kGraphite) {
 *         const Image* img = static_cast<const Image*>(baseImage);
 *         return img->textureProxyView().proxy();
 *     }
 *     SkASSERT(baseImage->type() == SkImage_Base::Type::kGraphiteYUVA);
 *     // We will end up flattening to RGBA for a YUVA image when we get a subset. We just grab
 *     // the label off of the first channel's proxy and use that to be the stand in label.
 *     const Image_YUVA* img = static_cast<const Image_YUVA*>(baseImage);
 *     return img->proxyView(0).proxy();
 * }
 * ```
 */
public fun getBaseProxyForLabel(baseImage: ImageBase?): TextureProxy {
  TODO("Implement getBaseProxyForLabel")
}

/**
 * C++ original:
 * ```cpp
 * static SkAlphaType yuva_alpha_type(const SkYUVAInfo& yuvaInfo) {
 *     // If an alpha channel is present we always use kPremul. This is because, although the planar
 *     // data is always un-premul and the final interleaved RGBA sample produced in the shader is
 *     // unpremul (and similar if flattened), the client is expecting premul.
 *     return yuvaInfo.hasAlpha() ? kPremul_SkAlphaType : kOpaque_SkAlphaType;
 * }
 * ```
 */
public fun yuvaAlphaType(yuvaInfo: SkYUVAInfo): SkAlphaType {
  TODO("Implement yuvaAlphaType")
}

/**
 * C++ original:
 * ```cpp
 * void add_solid_uniform_data(const KeyContext& keyContext, const SkPMColor4f& premulColor) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kSolidColorShader)
 *     keyContext.pipelineDataGatherer()->write(premulColor);
 * }
 * ```
 */
public fun addSolidUniformData(keyContext: KeyContext, premulColor: SkPMColor4f) {
  TODO("Implement addSolidUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_rgb_paint_color_uniform_data(const KeyContext& keyContext) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kRGBPaintColor)
 *     keyContext.pipelineDataGatherer()->writePaintColor(keyContext.paintColor());
 * }
 * ```
 */
public fun addRgbPaintColorUniformData(keyContext: KeyContext) {
  TODO("Implement addRgbPaintColorUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_alpha_only_paint_color_uniform_data(const KeyContext& keyContext) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kAlphaOnlyPaintColor)
 *     keyContext.pipelineDataGatherer()->writePaintColor(keyContext.paintColor());
 * }
 * ```
 */
public fun addAlphaOnlyPaintColorUniformData(keyContext: KeyContext) {
  TODO("Implement addAlphaOnlyPaintColorUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_gradient_preamble(const GradientShaderBlocks::GradientData& gradData,
 *                            PipelineDataGatherer* gatherer) {
 *     constexpr int kInternalStopLimit = GradientShaderBlocks::GradientData::kNumInternalStorageStops;
 *
 *     if (gradData.fNumStops <= kInternalStopLimit) {
 *         if (gradData.fNumStops <= 4) {
 *             // Round up to 4 stops.
 *             gatherer->writeArray(SkSpan{gradData.fColors, 4});
 *             gatherer->write(gradData.fOffsets[0]);
 *         } else if (gradData.fNumStops <= 8) {
 *             // Round up to 8 stops.
 *             gatherer->writeArray(SkSpan{gradData.fColors, 8});
 *             gatherer->writeArray(SkSpan{gradData.fOffsets, 2});
 *         } else {
 *             // Did kNumInternalStorageStops change?
 *             SkUNREACHABLE;
 *         }
 *     }
 * }
 * ```
 */
public fun addGradientPreamble(gradData: GradientShaderBlocks.GradientData, gatherer: PipelineDataGatherer?) {
  TODO("Implement addGradientPreamble")
}

/**
 * C++ original:
 * ```cpp
 * void add_gradient_postamble(const GradientShaderBlocks::GradientData& gradData,
 *                             int bufferOffset,
 *                             PipelineDataGatherer* gatherer) {
 *     using ColorSpace = SkGradient::Interpolation::ColorSpace;
 *
 *     constexpr int kInternalStopLimit = GradientShaderBlocks::GradientData::kNumInternalStorageStops;
 *
 *     static_assert(static_cast<int>(ColorSpace::kLab)           == 2);
 *     static_assert(static_cast<int>(ColorSpace::kOKLab)         == 3);
 *     static_assert(static_cast<int>(ColorSpace::kOKLabGamutMap) == 4);
 *     static_assert(static_cast<int>(ColorSpace::kLCH)           == 5);
 *     static_assert(static_cast<int>(ColorSpace::kOKLCH)         == 6);
 *     static_assert(static_cast<int>(ColorSpace::kOKLCHGamutMap) == 7);
 *     static_assert(static_cast<int>(ColorSpace::kHSL)           == 9);
 *     static_assert(static_cast<int>(ColorSpace::kHWB)           == 10);
 *
 *     bool inputPremul = static_cast<bool>(gradData.fInterpolation.fInPremul);
 *
 *     if (gradData.fNumStops > kInternalStopLimit) {
 *         gatherer->write(gradData.fNumStops);
 *         if (gradData.fUseStorageBuffer) {
 *             gatherer->write(bufferOffset);
 *         }
 *     }
 *
 *     gatherer->write(static_cast<int>(gradData.fTM));
 *     gatherer->write(static_cast<int>(gradData.fInterpolation.fColorSpace));
 *     gatherer->write(static_cast<int>(inputPremul));
 * }
 * ```
 */
public fun addGradientPostamble(
  gradData: GradientShaderBlocks.GradientData,
  bufferOffset: Int,
  gatherer: PipelineDataGatherer?,
) {
  TODO("Implement addGradientPostamble")
}

/**
 * C++ original:
 * ```cpp
 * void add_linear_gradient_uniform_data(const KeyContext& keyContext,
 *                                       BuiltInCodeSnippetID codeSnippetID,
 *                                       const GradientShaderBlocks::GradientData& gradData,
 *                                       int bufferOffset) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, codeSnippetID)
 *
 *     add_gradient_preamble(gradData, keyContext.pipelineDataGatherer());
 *     add_gradient_postamble(gradData, bufferOffset, keyContext.pipelineDataGatherer());
 * }
 * ```
 */
public fun addLinearGradientUniformData(
  keyContext: KeyContext,
  codeSnippetID: BuiltInCodeSnippetID,
  gradData: GradientShaderBlocks.GradientData,
  bufferOffset: Int,
) {
  TODO("Implement addLinearGradientUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_radial_gradient_uniform_data(const KeyContext& keyContext,
 *                                       BuiltInCodeSnippetID codeSnippetID,
 *                                       const GradientShaderBlocks::GradientData& gradData,
 *                                       int bufferOffset) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, codeSnippetID)
 *
 *     add_gradient_preamble(gradData, keyContext.pipelineDataGatherer());
 *     add_gradient_postamble(gradData, bufferOffset, keyContext.pipelineDataGatherer());
 * }
 * ```
 */
public fun addRadialGradientUniformData(
  keyContext: KeyContext,
  codeSnippetID: BuiltInCodeSnippetID,
  gradData: GradientShaderBlocks.GradientData,
  bufferOffset: Int,
) {
  TODO("Implement addRadialGradientUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_sweep_gradient_uniform_data(const KeyContext& keyContext,
 *                                      BuiltInCodeSnippetID codeSnippetID,
 *                                      const GradientShaderBlocks::GradientData& gradData,
 *                                      int bufferOffset) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, codeSnippetID)
 *
 *     add_gradient_preamble(gradData, keyContext.pipelineDataGatherer());
 *     keyContext.pipelineDataGatherer()->write(gradData.fBias);
 *     keyContext.pipelineDataGatherer()->write(gradData.fScale);
 *     add_gradient_postamble(gradData, bufferOffset, keyContext.pipelineDataGatherer());
 * }
 * ```
 */
public fun addSweepGradientUniformData(
  keyContext: KeyContext,
  codeSnippetID: BuiltInCodeSnippetID,
  gradData: GradientShaderBlocks.GradientData,
  bufferOffset: Int,
) {
  TODO("Implement addSweepGradientUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_conical_gradient_uniform_data(const KeyContext& keyContext,
 *                                      BuiltInCodeSnippetID codeSnippetID,
 *                                      const GradientShaderBlocks::GradientData& gradData,
 *                                      int bufferOffset) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, codeSnippetID)
 *
 *     float dRadius = gradData.fRadii[1] - gradData.fRadii[0];
 *     bool isRadial = SkPoint::Distance(gradData.fPoints[1], gradData.fPoints[0])
 *                                       < SK_ScalarNearlyZero;
 *
 *     // When a == 0, encode invA == 1 for radial case, and invA == 0 for linear edge case.
 *     float a = 0;
 *     float invA = 1;
 *     if (!isRadial) {
 *         a = 1 - dRadius * dRadius;
 *         if (std::abs(a) > SK_ScalarNearlyZero) {
 *             invA = 1.0 / (2.0 * a);
 *         } else {
 *             a = 0;
 *             invA = 0;
 *         }
 *     } else {
 *         // Since radius0 is being scaled by 1 / dRadius, and the original radius
 *         // is always positive, this gives us the original sign of dRadius.
 *         dRadius = gradData.fRadii[0] > 0 ? 1 : -1;
 *     }
 *
 *     add_gradient_preamble(gradData, keyContext.pipelineDataGatherer());
 *     keyContext.pipelineDataGatherer()->write(gradData.fRadii[0]);
 *     keyContext.pipelineDataGatherer()->write(dRadius);
 *     keyContext.pipelineDataGatherer()->write(a);
 *     keyContext.pipelineDataGatherer()->write(invA);
 *     add_gradient_postamble(gradData, bufferOffset, keyContext.pipelineDataGatherer());
 * }
 * ```
 */
public fun addConicalGradientUniformData(
  keyContext: KeyContext,
  codeSnippetID: BuiltInCodeSnippetID,
  gradData: GradientShaderBlocks.GradientData,
  bufferOffset: Int,
) {
  TODO("Implement addConicalGradientUniformData")
}

/**
 * C++ original:
 * ```cpp
 * static int write_color_and_offset_bufdata(int numStops,
 *                                            const SkPMColor4f* colors,
 *                                            const float* offsets,
 *                                            const SkGradientBaseShader* shader,
 *                                            FloatStorageManager* floatStorageManager) {
 *     auto [dstData, bufferOffset] = floatStorageManager->allocateGradientData(numStops, shader);
 *     if (dstData) {
 *         // Data doesn't already exist so we need to write it.
 *         // Writes all offset data, then color data. This way when binary searching through the
 *         // offsets, there is better cache locality.
 *         for (int i = 0, colorIdx = numStops; i < numStops; i++, colorIdx+=4) {
 *             float offset = offsets ? offsets[i] : SkIntToFloat(i) / (numStops - 1);
 *             SkASSERT(offset >= 0.0f && offset <= 1.0f);
 *
 *             dstData[i] = offset;
 *             dstData[colorIdx + 0] = colors[i].fR;
 *             dstData[colorIdx + 1] = colors[i].fG;
 *             dstData[colorIdx + 2] = colors[i].fB;
 *             dstData[colorIdx + 3] = colors[i].fA;
 *         }
 *     }
 *
 *     return bufferOffset;
 * }
 * ```
 */
public fun writeColorAndOffsetBufdata(
  numStops: Int,
  colors: SkPMColor4f?,
  offsets: Float?,
  shader: SkGradientBaseShader?,
  floatStorageManager: FloatStorageManager?,
): Int {
  TODO("Implement writeColorAndOffsetBufdata")
}

/**
 * C++ original:
 * ```cpp
 * void add_image_uniform_data(const KeyContext& keyContext,
 *                             const ImageShaderBlock::ImageData& imgData) {
 *     SkASSERT(!imgData.fSampling.useCubic);
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                              1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.first));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.second));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fSampling.filter));
 * }
 * ```
 */
public fun addImageUniformData(keyContext: KeyContext, imgData: ImageShaderBlock.ImageData) {
  TODO("Implement addImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_clamp_image_uniform_data(const KeyContext& keyContext,
 *                                   const ImageShaderBlock::ImageData& imgData) {
 *     SkASSERT(!imgData.fSampling.useCubic);
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kImageShaderClamp)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *
 *     // Matches GrTextureEffect::kLinearInset, to make sure we don't touch an outer row or column
 *     // with a weight of 0 when linear filtering.
 *     const float kLinearInset = 0.5f + 0.00001f;
 *
 *     // The subset should clamp texel coordinates to an inset subset to prevent sampling neighboring
 *     // texels when coords fall exactly at texel boundaries.
 *     SkRect subsetInsetClamp = imgData.fSubset;
 *     if (imgData.fSampling.filter == SkFilterMode::kNearest) {
 *         subsetInsetClamp.roundOut(&subsetInsetClamp);
 *     }
 *     subsetInsetClamp.inset(kLinearInset, kLinearInset);
 *     keyContext.pipelineDataGatherer()->write(subsetInsetClamp);
 * }
 * ```
 */
public fun addClampImageUniformData(keyContext: KeyContext, imgData: ImageShaderBlock.ImageData) {
  TODO("Implement addClampImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_cubic_image_uniform_data(const KeyContext& keyContext,
 *                                   const ImageShaderBlock::ImageData& imgData) {
 *     SkASSERT(imgData.fSampling.useCubic);
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kCubicImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.first));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.second));
 *     const SkCubicResampler& cubic = imgData.fSampling.cubic;
 *     keyContext.pipelineDataGatherer()->writeHalf(
 *             SkImageShader::CubicResamplerMatrix(cubic.B, cubic.C));
 * }
 * ```
 */
public fun addCubicImageUniformData(keyContext: KeyContext, imgData: ImageShaderBlock.ImageData) {
  TODO("Implement addCubicImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * bool should_substitute_decal(const std::pair<SkTileMode, SkTileMode>& tileMode, const Caps* caps) {
 *     return !caps->clampToBorderSupport() && (tileMode.first == SkTileMode::kDecal ||
 *                                              tileMode.second == SkTileMode::kDecal);
 * }
 * ```
 */
public fun shouldSubstituteDecal(tileMode: Pair<SkTileMode, SkTileMode>, caps: Caps?): Boolean {
  TODO("Implement shouldSubstituteDecal")
}

/**
 * C++ original:
 * ```cpp
 * bool can_do_tiling_in_hw(const ImageShaderBlock::ImageData& imgData, const Caps* caps) {
 *     return !should_substitute_decal(imgData.fTileModes, caps) &&
 *             imgData.fSubset.contains(SkRect::Make(imgData.fImgSize));
 * }
 * ```
 */
public fun canDoTilingInHw(imgData: ImageShaderBlock.ImageData, caps: Caps?): Boolean {
  TODO("Implement canDoTilingInHw")
}

/**
 * C++ original:
 * ```cpp
 * void add_sampler_data_to_key(const KeyContext& keyContext, const SamplerDesc& samplerDesc) {
 *     if (samplerDesc.isImmutable()) {
 *         keyContext.paintParamsKeyBuilder()->addData(samplerDesc.asSpan());
 *     } else {
 *         // Means we have a regular dynamic sampler. Append a default SamplerDesc to convey this,
 *         // allowing the key to maintain and convey sampler binding order.
 *         keyContext.paintParamsKeyBuilder()->addData({});
 *     }
 * }
 * ```
 */
public fun addSamplerDataToKey(keyContext: KeyContext, samplerDesc: SamplerDesc) {
  TODO("Implement addSamplerDataToKey")
}

/**
 * C++ original:
 * ```cpp
 * void add_yuv_image_uniform_data(const KeyContext& keyContext,
 *                                 const YUVImageShaderBlock::ImageData& imgData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kYUVImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSizeUV.width(),
 *                                                           1.f/imgData.fImgSizeUV.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *     keyContext.pipelineDataGatherer()->write(imgData.fLinearFilterUVInset);
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.first));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.second));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fSampling.filter));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fSamplingUV.filter));
 *
 *     for (int i = 0; i < 4; ++i) {
 *         keyContext.pipelineDataGatherer()->writeHalf(imgData.fChannelSelect[i]);
 *     }
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBMatrix);
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBTranslate);
 * }
 * ```
 */
public fun addYuvImageUniformData(keyContext: KeyContext, imgData: YUVImageShaderBlock.ImageData) {
  TODO("Implement addYuvImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_cubic_yuv_image_uniform_data(const KeyContext& keyContext,
 *                                       const YUVImageShaderBlock::ImageData& imgData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kCubicYUVImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSizeUV.width(),
 *                                                           1.f/imgData.fImgSizeUV.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.first));
 *     keyContext.pipelineDataGatherer()->write(SkTo<int>(imgData.fTileModes.second));
 *     const SkCubicResampler& cubic = imgData.fSampling.cubic;
 *     keyContext.pipelineDataGatherer()->writeHalf(SkImageShader::CubicResamplerMatrix(cubic.B, cubic.C));
 *
 *     for (int i = 0; i < 4; ++i) {
 *         keyContext.pipelineDataGatherer()->writeHalf(imgData.fChannelSelect[i]);
 *     }
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBMatrix);
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBTranslate);
 * }
 * ```
 */
public fun addCubicYuvImageUniformData(keyContext: KeyContext, imgData: YUVImageShaderBlock.ImageData) {
  TODO("Implement addCubicYuvImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_hw_yuv_image_uniform_data(const KeyContext& keyContext,
 *                                    const YUVImageShaderBlock::ImageData& imgData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kHWYUVImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSizeUV.width(),
 *                                                           1.f/imgData.fImgSizeUV.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *
 *     SkPoint linearFilterUVInset = imgData.fLinearFilterUVInset;
 *     // We sign-encode whether we need to adjust the UV coords by applying `fLinearFilterUVInset` for
 *     // nearest neighbor filtering in `linearFilterUVInset.fX`.
 *     if (imgData.fSampling.filter == SkFilterMode::kNearest) {
 *         linearFilterUVInset.fX = -linearFilterUVInset.fX;
 *     }
 *     // We sign-encode whether we need clamping for subset or mismatched Y/UV plane size draws in
 *     // `linearFilterUVInset.fY` - only clamp tiling modes are supported though.
 *     if (!imgData.fSubset.contains(SkRect::Make(imgData.fImgSize)) ||
 *         imgData.fImgSize != imgData.fImgSizeUV) {
 *         SkASSERT(imgData.fTileModes.first == SkTileMode::kClamp &&
 *                  imgData.fTileModes.second == SkTileMode::kClamp);
 *         linearFilterUVInset.fY = -linearFilterUVInset.fY;
 *     }
 *     keyContext.pipelineDataGatherer()->write(linearFilterUVInset);
 *
 *     for (int i = 0; i < 4; ++i) {
 *         keyContext.pipelineDataGatherer()->writeHalf(imgData.fChannelSelect[i]);
 *     }
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBMatrix);
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBTranslate);
 * }
 * ```
 */
public fun addHwYuvImageUniformData(keyContext: KeyContext, imgData: YUVImageShaderBlock.ImageData) {
  TODO("Implement addHwYuvImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_hw_yuv_no_swizzle_image_uniform_data(const KeyContext& keyContext,
 *                                               const YUVImageShaderBlock::ImageData& imgData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kHWYUVNoSwizzleImageShader)
 *
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSize.width(),
 *                                                           1.f/imgData.fImgSize.height()));
 *     keyContext.pipelineDataGatherer()->write(SkSize::Make(1.f/imgData.fImgSizeUV.width(),
 *                                                           1.f/imgData.fImgSizeUV.height()));
 *     keyContext.pipelineDataGatherer()->write(imgData.fSubset);
 *
 *     SkPoint linearFilterUVInset = imgData.fLinearFilterUVInset;
 *     // We sign-encode whether we need to adjust the UV coords by applying `fLinearFilterUVInset` for
 *     // nearest neighbor filtering in `linearFilterUVInset.fX`.
 *     if (imgData.fSampling.filter == SkFilterMode::kNearest) {
 *         linearFilterUVInset.fX = -linearFilterUVInset.fX;
 *     }
 *     // We sign-encode whether we need clamping for subset or mismatched Y/UV plane size draws in
 *     // `linearFilterUVInset.fY` - only clamp tiling modes are supported though.
 *     if (!imgData.fSubset.contains(SkRect::Make(imgData.fImgSize)) ||
 *         imgData.fImgSize != imgData.fImgSizeUV) {
 *         SkASSERT(imgData.fTileModes.first == SkTileMode::kClamp &&
 *                  imgData.fTileModes.second == SkTileMode::kClamp);
 *         linearFilterUVInset.fY = -linearFilterUVInset.fY;
 *     }
 *     keyContext.pipelineDataGatherer()->write(linearFilterUVInset);
 *
 *     keyContext.pipelineDataGatherer()->writeHalf(imgData.fYUVtoRGBMatrix);
 *     SkV4 yuvToRGBXlateAlphaParam = {
 *         imgData.fYUVtoRGBTranslate.fX,
 *         imgData.fYUVtoRGBTranslate.fY,
 *         imgData.fYUVtoRGBTranslate.fZ,
 *         imgData.fAlphaParam
 *     };
 *     keyContext.pipelineDataGatherer()->writeHalf(yuvToRGBXlateAlphaParam);
 * }
 * ```
 */
public fun addHwYuvNoSwizzleImageUniformData(keyContext: KeyContext, imgData: YUVImageShaderBlock.ImageData) {
  TODO("Implement addHwYuvNoSwizzleImageUniformData")
}

/**
 * C++ original:
 * ```cpp
 * static bool can_do_yuv_tiling_in_hw(const YUVImageShaderBlock::ImageData& imgData,
 *                                     const Caps* caps) {
 *     if (should_substitute_decal(imgData.fTileModes, caps)) {
 *         return false;
 *     }
 *     // Use the HW tiling shader variant if we're drawing the full rect with matched Y and UV plane
 *     // sizes and any tiling mode, or if we're drawing a subset with clamp tiling mode.
 *     return (imgData.fSubset.contains(SkRect::Make(imgData.fImgSize)) &&
 *             imgData.fImgSize == imgData.fImgSizeUV) ||
 *            (imgData.fTileModes.first == SkTileMode::kClamp &&
 *             imgData.fTileModes.second == SkTileMode::kClamp);
 * }
 * ```
 */
public fun canDoYuvTilingInHw(imgData: YUVImageShaderBlock.ImageData, caps: Caps?): Boolean {
  TODO("Implement canDoYuvTilingInHw")
}

/**
 * C++ original:
 * ```cpp
 * static bool no_yuv_swizzle(const YUVImageShaderBlock::ImageData& imgData) {
 *     // Y_U_V or U_Y_V format, reading from R channel for each texture
 *     if (imgData.fChannelSelect[0].x == 1 &&
 *         imgData.fChannelSelect[1].x == 1 &&
 *         imgData.fChannelSelect[2].x == 1 &&
 *         imgData.fChannelSelect[3].x == 1) {
 *         return true;
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun noYuvSwizzle(imgData: YUVImageShaderBlock.ImageData): Boolean {
  TODO("Implement noYuvSwizzle")
}

/**
 * C++ original:
 * ```cpp
 * void add_coord_normalize_uniform_data(const KeyContext& keyContext,
 *                                       const CoordNormalizeShaderBlock::CoordNormalizeData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kCoordNormalizeShader)
 *     keyContext.pipelineDataGatherer()->write(data.fInvDimensions);
 * }
 * ```
 */
public fun addCoordNormalizeUniformData(keyContext: KeyContext, `data`: CoordNormalizeShaderBlock.CoordNormalizeData) {
  TODO("Implement addCoordNormalizeUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_coordclamp_uniform_data(const KeyContext& keyContext,
 *                                  const CoordClampShaderBlock::CoordClampData& clampData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kCoordClampShader)
 *     keyContext.pipelineDataGatherer()->write(clampData.fSubset);
 * }
 * ```
 */
public fun addCoordclampUniformData(keyContext: KeyContext, clampData: CoordClampShaderBlock.CoordClampData) {
  TODO("Implement addCoordclampUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_dither_uniform_data(const KeyContext& keyContext,
 *                              const DitherShaderBlock::DitherData& ditherData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kDitherShader)
 *
 *     keyContext.pipelineDataGatherer()->writeHalf(ditherData.fRange);
 * }
 * ```
 */
public fun addDitherUniformData(keyContext: KeyContext, ditherData: DitherShaderBlock.DitherData) {
  TODO("Implement addDitherUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_perlin_noise_uniform_data(const KeyContext& keyContext,
 *                                    const PerlinNoiseShaderBlock::PerlinNoiseData& noiseData) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kPerlinNoiseShader)
 *
 *     auto gatherer = keyContext.pipelineDataGatherer();
 *     gatherer->write(noiseData.fBaseFrequency);
 *     gatherer->write(noiseData.fStitchData);
 *     gatherer->write(static_cast<int>(noiseData.fType));
 *     gatherer->write(noiseData.fNumOctaves);
 *     gatherer->write(static_cast<int>(noiseData.stitching()));
 *
 *     static const std::pair<SkTileMode, SkTileMode> kRepeatXTileModes =
 *             { SkTileMode::kRepeat, SkTileMode::kClamp };
 *     gatherer->add(noiseData.fPermutationsProxy, {SkFilterMode::kNearest, kRepeatXTileModes});
 *     gatherer->add(noiseData.fNoiseProxy, {SkFilterMode::kNearest, kRepeatXTileModes});
 * }
 * ```
 */
public fun addPerlinNoiseUniformData(keyContext: KeyContext, noiseData: PerlinNoiseShaderBlock.PerlinNoiseData) {
  TODO("Implement addPerlinNoiseUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_matrix_colorfilter_uniform_data(const KeyContext& keyContext,
 *                                          const MatrixColorFilterBlock::MatrixColorFilterData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kMatrixColorFilter)
 *     auto gatherer = keyContext.pipelineDataGatherer();
 *     gatherer->writeHalf(data.fMatrix);
 *     gatherer->writeHalf(data.fTranslate);
 *     if (data.fClamp) {
 *         gatherer->writeHalf(SkV2{0.f, 1.f});
 *     } else {
 *         // Alpha is always clamped to 1. RGB clamp to the max finite half value.
 *         static constexpr float kUnclamped = 65504.f; // SK_HalfMax converted back to float
 *         SkASSERT(SkHalfToFloat(SkFloatToHalf(kUnclamped)) == kUnclamped);
 *         SkASSERT(SkHalfToFloat(SkFloatToHalf(-kUnclamped)) == -kUnclamped);
 *         gatherer->writeHalf(SkV2{-kUnclamped, kUnclamped});
 *     }
 * }
 * ```
 */
public fun addMatrixColorfilterUniformData(keyContext: KeyContext, `data`: MatrixColorFilterBlock.MatrixColorFilterData) {
  TODO("Implement addMatrixColorfilterUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_hsl_matrix_colorfilter_uniform_data(
 *         const KeyContext& keyContext,
 *         const MatrixColorFilterBlock::MatrixColorFilterData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kHSLMatrixColorFilter)
 *     auto gatherer = keyContext.pipelineDataGatherer();
 *     gatherer->writeHalf(data.fMatrix);
 *     gatherer->writeHalf(data.fTranslate);
 * }
 * ```
 */
public fun addHslMatrixColorfilterUniformData(keyContext: KeyContext, `data`: MatrixColorFilterBlock.MatrixColorFilterData) {
  TODO("Implement addHslMatrixColorfilterUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_table_colorfilter_uniform_data(const KeyContext& keyContext,
 *                                         const TableColorFilterBlock::TableColorFilterData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kTableColorFilter)
 *
 *     keyContext.pipelineDataGatherer()->add(data.fTextureProxy, {SkFilterMode::kNearest, SkTileMode::kClamp});
 * }
 * ```
 */
public fun addTableColorfilterUniformData(keyContext: KeyContext, `data`: TableColorFilterBlock.TableColorFilterData) {
  TODO("Implement addTableColorfilterUniformData")
}

/**
 * C++ original:
 * ```cpp
 * void add_color_space_uniforms(const KeyContext& keyContext,
 *                               BuiltInCodeSnippetID id,
 *                               const SkColorSpaceXformSteps& steps,
 *                               ReadSwizzle readSwizzle) {
 *     SkASSERT(id == BuiltInCodeSnippetID::kColorSpaceXformPremul ||     // premul/unpremul/opaque
 *              id == BuiltInCodeSnippetID::kColorSpaceXformSRGB ||       // + sRGB [d]encode/gamut
 *              id == BuiltInCodeSnippetID::kColorSpaceXformColorFilter); // + everything else
 *
 *     BEGIN_WRITE_UNIFORMS(keyContext, id)
 *
 *     // To encode whether to do premul/unpremul or make the output opaque, we use
 *     // srcDEF_args.w and dstDEF_args.w:
 *     // - identity: {0, 1}
 *     // - do unpremul: {-1, 1}
 *     // - do premul: {0, 0}
 *     // - do both: {-1, 0}
 *     // - alpha swizzle 1: {1, 1}
 *     // - alpha swizzle r: {1, 0}
 *     const bool alphaSwizzleR = readSwizzle == ReadSwizzle::k000R;
 *     const bool alphaSwizzle1 = readSwizzle == ReadSwizzle::kRGB1 ||
 *                                readSwizzle == ReadSwizzle::kRRR1;
 *
 *     // It doesn't make sense to unpremul/premul in opaque cases, but we might get a request to
 *     // anyways, which we can just ignore.
 *     const bool unpremul = alphaSwizzle1 ? false : steps.fFlags.unpremul;
 *     const bool premul = alphaSwizzle1 ? false : steps.fFlags.premul;
 *
 *     const float srcW = unpremul ? -1.f :
 *                        (alphaSwizzleR || alphaSwizzle1) ? 1.f :
 *                                                           0.f;
 *     const float dstW = (premul || alphaSwizzleR) ? 0.f : 1.f;
 *
 *     if (id == BuiltInCodeSnippetID::kColorSpaceXformPremul) {
 *         // If either of these asserts would fail, we can't correctly use this specialized shader for
 *         // the given transform.
 *         SkASSERT(readSwizzle == ReadSwizzle::kRGBA || readSwizzle == ReadSwizzle::kRGB1);
 *         // If these are both true, that implies there's a color space transfer or gamut transform.
 *         SkASSERT(!(steps.fFlags.unpremul && steps.fFlags.premul));
 *         // And given these assertions, the 6 cases encoded in srcW and dstW are reduced to:
 *         //    identity, do unpremul, do premul, and make opaque (alpha swizzle 1)
 *         keyContext.pipelineDataGatherer()->writeHalf(SkV2{srcW, dstW});
 *         return;
 *     }
 *
 *     // srcW and dstW will be used later with the other transfer function values, but for the
 *     // more complex shaders, we put the gamut matrix first for alignment.
 *
 *     SkMatrix gamutTransform;
 *     const float identity[] = { 1, 0, 0, 0, 1, 0, 0, 0, 1 };
 *     // TODO: it seems odd to copy this into an SkMatrix just to write it to the gatherer
 *     // fSrcToDstMatrix is column-major, SkMatrix is row-major.
 *     const float* m = steps.fFlags.gamut_transform ? steps.fSrcToDstMatrix : identity;
 *     if (readSwizzle == ReadSwizzle::kRRR1) {
 *         gamutTransform.setAll(m[0] + m[3] + m[6], 0, 0,
 *                             m[1] + m[4] + m[7], 0, 0,
 *                             m[2] + m[5] + m[8], 0, 0);
 *     } else if (readSwizzle == ReadSwizzle::kBGRA) {
 *         gamutTransform.setAll(m[6], m[3], m[0],
 *                             m[7], m[4], m[1],
 *                             m[8], m[5], m[2]);
 *     } else if (readSwizzle == ReadSwizzle::k000R) {
 *         gamutTransform.setAll(0, 0, 0,
 *                             0, 0, 0,
 *                             0, 0, 0);
 *     } else if (steps.fFlags.gamut_transform) {
 *         gamutTransform.setAll(m[0], m[3], m[6],
 *                             m[1], m[4], m[7],
 *                             m[2], m[5], m[8]);
 *     }
 *     keyContext.pipelineDataGatherer()->writeHalf(gamutTransform);
 *
 *     // To encode which transfer function to apply, we use the src and dst gamma values:
 *     // - identity: 0
 *     // - sRGB: g > 0
 *     // - PQ: -2
 *     // - HLG: -1
 *     // For the sRGB shader, we allow linear sRGB but that shader has no branches on TF type, so
 *     // we have to replace the values with an actual identity sRGB-ish function.
 *     const bool treatLinearAsSRGB = id == BuiltInCodeSnippetID::kColorSpaceXformSRGB;
 *     if (steps.fFlags.linearize) {
 *         const skcms_TFType type = skcms_TransferFunction_getType(&steps.fSrcTF);
 *         const float srcG = type == skcms_TFType_sRGBish ? steps.fSrcTF.g :
 *                            type == skcms_TFType_PQish ? -2.f :
 *                            type == skcms_TFType_HLGish ? -1.f :
 *                                                          0.f;
 *         keyContext.pipelineDataGatherer()->write(SkV4{srcG, steps.fSrcTF.a,
 *                                                       steps.fSrcTF.b, steps.fSrcTF.c});
 *         keyContext.pipelineDataGatherer()->write(SkV4{steps.fSrcTF.d, steps.fSrcTF.e,
 *                                                       steps.fSrcTF.f, srcW});
 *     } else if (treatLinearAsSRGB) {
 *         // Branchless identity function with g=1 (sRGB-ish)
 *         static constexpr skcms_TransferFunction kI = SkNamedTransferFn::kLinear;
 *         keyContext.pipelineDataGatherer()->write(SkV4{kI.g, kI.a, kI.b, kI.c});
 *         keyContext.pipelineDataGatherer()->write(SkV4{kI.d, kI.e, kI.f, srcW});
 *     } else {
 *         // Branched identity that actually skips all operations
 *         keyContext.pipelineDataGatherer()->write(SkV4{0.f, 0.f, 0.f, 0.f});
 *         keyContext.pipelineDataGatherer()->write(SkV4{0.f, 0.f, 0.f, srcW});
 *     }
 *
 *     if (steps.fFlags.encode) {
 *         const skcms_TFType type = skcms_TransferFunction_getType(&steps.fDstTFInv);
 *         const float dstG = type == skcms_TFType_sRGBish ? steps.fDstTFInv.g :
 *                            type == skcms_TFType_PQish ? -2.f :
 *                            type == skcms_TFType_HLGinvish ? -1.f :
 *                                                             0.f;
 *         keyContext.pipelineDataGatherer()->write(SkV4{dstG, steps.fDstTFInv.a,
 *                                                       steps.fDstTFInv.b, steps.fDstTFInv.c});
 *         keyContext.pipelineDataGatherer()->write(SkV4{steps.fDstTFInv.d, steps.fDstTFInv.e,
 *                                                       steps.fDstTFInv.f, dstW});
 *     } else if (treatLinearAsSRGB) {
 *         static constexpr skcms_TransferFunction kI = SkNamedTransferFn::kLinear;
 *         keyContext.pipelineDataGatherer()->write(SkV4{kI.g, kI.a, kI.b, kI.c});
 *         keyContext.pipelineDataGatherer()->write(SkV4{kI.d, kI.e, kI.f, dstW});
 *     } else {
 *         keyContext.pipelineDataGatherer()->write(SkV4{0.f, 0.f, 0.f, 0.f});
 *         keyContext.pipelineDataGatherer()->write(SkV4{0.f, 0.f, 0.f, dstW});
 *     }
 *
 *     const bool hasOOTFUniforms = id == BuiltInCodeSnippetID::kColorSpaceXformColorFilter;
 *     if (hasOOTFUniforms) {
 *         SkV4 src_ootf = {0.f, 0.f, 0.f, 0.f};
 *         SkV4 dst_ootf = {0.f, 0.f, 0.f, 0.f};
 *
 *         if (steps.fFlags.src_ootf) {
 *           if (readSwizzle == ReadSwizzle::kBGRA) {
 *               src_ootf = SkV4{
 *                   steps.fSrcOotf[2], steps.fSrcOotf[1], steps.fSrcOotf[0], steps.fSrcOotf[3]};
 *           } else {
 *               src_ootf = SkV4{
 *                   steps.fSrcOotf[0], steps.fSrcOotf[1], steps.fSrcOotf[2], steps.fSrcOotf[3]};
 *           }
 *         }
 *
 *         if (steps.fFlags.dst_ootf) {
 *           if (readSwizzle == ReadSwizzle::kBGRA) {
 *               dst_ootf = SkV4{
 *                   steps.fDstOotf[2], steps.fDstOotf[1], steps.fDstOotf[0], steps.fDstOotf[3]};
 *           } else {
 *               dst_ootf = SkV4{
 *                   steps.fDstOotf[0], steps.fDstOotf[1], steps.fDstOotf[2], steps.fDstOotf[3]};
 *           }
 *         }
 *
 *         keyContext.pipelineDataGatherer()->write(src_ootf);
 *         keyContext.pipelineDataGatherer()->write(dst_ootf);
 *     } else {
 *       SkASSERT(!steps.fFlags.src_ootf);
 *       SkASSERT(!steps.fFlags.dst_ootf);
 *     }
 * }
 * ```
 */
public fun addColorSpaceUniforms(
  keyContext: KeyContext,
  id: BuiltInCodeSnippetID,
  steps: SkColorSpaceXformSteps,
  readSwizzle: ReadSwizzle,
) {
  TODO("Implement addColorSpaceUniforms")
}

/**
 * C++ original:
 * ```cpp
 * void add_analytic_clip_data(const KeyContext& keyContext,
 *                             const NonMSAAClipBlock::NonMSAAClipData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kAnalyticClip)
 *     keyContext.pipelineDataGatherer()->write(data.fRect);
 *     keyContext.pipelineDataGatherer()->write(data.fRadiusPlusHalf);
 *     keyContext.pipelineDataGatherer()->writeHalf(data.fEdgeSelect);
 * }
 * ```
 */
public fun addAnalyticClipData(keyContext: KeyContext, `data`: NonMSAAClipBlock.NonMSAAClipData) {
  TODO("Implement addAnalyticClipData")
}

/**
 * C++ original:
 * ```cpp
 * void add_analytic_and_atlas_clip_data(const KeyContext& keyContext,
 *                                       const NonMSAAClipBlock::NonMSAAClipData& data) {
 *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kAnalyticAndAtlasClip)
 *     keyContext.pipelineDataGatherer()->write(data.fRect);
 *     keyContext.pipelineDataGatherer()->write(data.fRadiusPlusHalf);
 *     keyContext.pipelineDataGatherer()->writeHalf(data.fEdgeSelect);
 *     keyContext.pipelineDataGatherer()->write(data.fTexCoordOffset);
 *     keyContext.pipelineDataGatherer()->write(data.fMaskBounds);
 *     if (data.fAtlasTexture) {
 *         keyContext.pipelineDataGatherer()->write(
 *                 SkSize::Make(1.f/data.fAtlasTexture->dimensions().width(),
 *                              1.f/data.fAtlasTexture->dimensions().height()));
 *     } else {
 *         keyContext.pipelineDataGatherer()->write(SkSize::Make(0, 0));
 *     }
 * }
 * ```
 */
public fun addAnalyticAndAtlasClipData(keyContext: KeyContext, `data`: NonMSAAClipBlock.NonMSAAClipData) {
  TODO("Implement addAnalyticAndAtlasClipData")
}

/**
 * C++ original:
 * ```cpp
 * void AddPrimitiveColor(const KeyContext& keyContext, bool skipColorXform) {
 *     /**
 *      * When skipColorXform is true, we assume the primitive color is already in the dst color space.
 *     */
 *     if (skipColorXform) {
 *          keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPrimitiveColor);
 *          return;
 *     }
 *
 *     /**
 *      * If skipColorXform is false (most cases), the primitive color is assumed to be in sRGB.
 *     */
 *     ColorSpaceTransformBlock::ColorSpaceTransformData toDst(sk_srgb_singleton(),
 *                                                             kPremul_SkAlphaType,
 *                                                             keyContext.dstColorInfo().colorSpace(),
 *                                                             keyContext.dstColorInfo().alphaType());
 *
 *     Compose(keyContext,
 *             /* addInnerToKey= */ [&]() -> void {
 *                 keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPrimitiveColor);
 *             },
 *             /* addOuterToKey= */ [&]() -> void {
 *                 ColorSpaceTransformBlock::AddBlock(keyContext, toDst);
 *             });
 * }
 * ```
 */
public fun addPrimitiveColor(keyContext: KeyContext, skipColorXform: Boolean) {
  TODO("Implement addPrimitiveColor")
}

/**
 * C++ original:
 * ```cpp
 * void AddBlendModeColorFilter(const KeyContext& keyContext, SkBlendMode bm,
 *                              const SkPMColor4f& srcColor) {
 *     Blend(keyContext,
 *           /* addBlendToKey= */ [&] () -> void {
 *               AddBlendMode(keyContext, bm);
 *           },
 *           /* addSrcToKey= */ [&]() -> void {
 *               SolidColorShaderBlock::AddBlock(keyContext, srcColor);
 *           },
 *           /* addDstToKey= */ [&]() -> void {
 *               keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
 *           });
 * }
 * ```
 */
public fun addBlendModeColorFilter(
  keyContext: KeyContext,
  bm: SkBlendMode,
  srcColor: SkPMColor4f,
) {
  TODO("Implement addBlendModeColorFilter")
}

/**
 * C++ original:
 * ```cpp
 * static bool skdata_matches(const SkData* a, const SkData* b) {
 *     // Returns true if both SkData objects hold the same contents, or if they are both null.
 *     // (SkData::equals supports passing null, and returns false.)
 *     return a ? a->equals(b) : (a == b);
 * }
 * ```
 */
public fun skdataMatches(a: SkData?, b: SkData?): Boolean {
  TODO("Implement skdataMatches")
}

/**
 * C++ original:
 * ```cpp
 * static void gather_runtime_effect_uniforms(const KeyContext& keyContext,
 *                                            const SkRuntimeEffect* effect,
 *                                            SkSpan<const Uniform> graphiteUniforms,
 *                                            const SkData* uniformData,
 *                                            PipelineDataGatherer* gatherer) {
 *     if (!uniformData) {
 *         return;  // precompiling
 *     }
 *
 *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, graphiteUniforms);)
 *
 *     SkSpan<const SkRuntimeEffect::Uniform> rtsUniforms = effect->uniforms();
 *
 *     if (!rtsUniforms.empty() && uniformData) {
 *         // Collect all the other uniforms from the provided SkData.
 *         const uint8_t* uniformBase = uniformData->bytes();
 *         for (size_t index = 0; index < rtsUniforms.size(); ++index) {
 *             const Uniform& uniform = graphiteUniforms[index];
 *             // Get a pointer to the offset in our data for this uniform.
 *             const uint8_t* uniformPtr = uniformBase + rtsUniforms[index].offset;
 *             // Pass the uniform data to the gatherer.
 *             gatherer->write(uniform, uniformPtr);
 *         }
 *     }
 * }
 * ```
 */
public fun gatherRuntimeEffectUniforms(
  keyContext: KeyContext,
  effect: SkRuntimeEffect?,
  graphiteUniforms: SkSpan<Uniform>,
  uniformData: SkData?,
  gatherer: PipelineDataGatherer?,
) {
  TODO("Implement gatherRuntimeEffectUniforms")
}

/**
 * C++ original:
 * ```cpp
 * void add_children_to_key(const KeyContext& keyContext,
 *                          SkSpan<const SkRuntimeEffect::ChildPtr> children,
 *                          const SkRuntimeEffect* effect) {
 *     SkSpan<const SkRuntimeEffect::Child> childInfo = effect->children();
 *     SkASSERT(children.size() == childInfo.size());
 *
 *     using ChildType = SkRuntimeEffect::ChildType;
 *
 *     for (size_t index = 0; index < children.size(); ++index) {
 *         const SkRuntimeEffect::ChildPtr& child = children[index];
 *         KeyContext childContext = keyContext.forRuntimeEffect(effect, index);
 *
 *         std::optional<ChildType> type = child.type();
 *         if (type == ChildType::kShader) {
 *             AddToKey(childContext, child.shader());
 *         } else if (type == ChildType::kColorFilter) {
 *             AddToKey(childContext, child.colorFilter());
 *         } else if (type == ChildType::kBlender) {
 *             AddToKey(childContext, child.blender());
 *         } else {
 *             // We don't have a child effect. Substitute in a no-op effect.
 *             switch (childInfo[index].type) {
 *                 case ChildType::kShader:
 *                     // A missing shader returns transparent black
 *                     SolidColorShaderBlock::AddBlock(childContext,
 *                                                     SK_PMColor4fTRANSPARENT);
 *                     break;
 *
 *                 case ChildType::kColorFilter:
 *                     // A "passthrough" color filter returns the input color as-is.
 *                     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
 *                     break;
 *
 *                 case ChildType::kBlender:
 *                     // A "passthrough" blender performs `blend_src_over(src, dest)`.
 *                     AddFixedBlendMode(childContext, SkBlendMode::kSrcOver);
 *                     break;
 *             }
 *         }
 *     }
 *
 *     RuntimeEffectBlock::HandleIntrinsics(keyContext, effect);
 * }
 * ```
 */
public fun addChildrenToKey(
  keyContext: KeyContext,
  children: SkSpan<SkRuntimeEffect.ChildPtr>,
  effect: SkRuntimeEffect?,
) {
  TODO("Implement addChildrenToKey")
}

/**
 * C++ original:
 * ```cpp
 * static SkPMColor4f map_color(const SkColor4f& c,
 *                              SkColorSpace* src,
 *                              SkColorSpace* dst,
 *                              SkAlphaType dstAlphaType) {
 *     SkPMColor4f color = {c.fR, c.fG, c.fB, c.fA};
 *     SkColorSpaceXformSteps(src, kUnpremul_SkAlphaType, dst, dstAlphaType).apply(color.vec());
 *     return color;
 * }
 * ```
 */
public fun mapColor(
  c: SkColor4f,
  src: SkColorSpace?,
  dst: SkColorSpace?,
  dstAlphaType: SkAlphaType,
): SkPMColor4f {
  TODO("Implement mapColor")
}

/**
 * C++ original:
 * ```cpp
 * template <typename AddInnerToKeyT>
 * static void add_local_matrix_to_key(const KeyContext& keyContext,
 *                                     const SkMatrix& localMatrix,
 *                                     const SkMatrix& postInverseMatrix,
 *                                     AddInnerToKeyT addInnerToKey) {
 *     auto lmInverse = localMatrix.invert();
 *     if (!lmInverse) {
 *         keyContext.paintParamsKeyBuilder()->addErrorBlock();
 *         return;
 *     }
 *
 *     lmInverse->postConcat(postInverseMatrix);
 *
 *     LocalMatrixShaderBlock::BeginBlock(keyContext,
 *                                        LocalMatrixShaderBlock::LMShaderData(*lmInverse));
 *     KeyContextWithLocalMatrix lmContext(keyContext, localMatrix);
 *     addInnerToKey(lmContext);
 *     keyContext.paintParamsKeyBuilder()->endBlock();
 * }
 * ```
 */
public fun <AddInnerToKeyT> addLocalMatrixToKey(
  keyContext: KeyContext,
  localMatrix: SkMatrix,
  postInverseMatrix: SkMatrix,
  addInnerToKey: AddInnerToKeyT,
) {
  TODO("Implement addLocalMatrixToKey")
}

/**
 * C++ original:
 * ```cpp
 * static void add_yuv_image_to_key(const KeyContext& keyContext,
 *                                  const SkImage* imageToDraw,
 *                                  SkRect subset,
 *                                  SkSamplingOptions sampling,
 *                                  SkTileMode tileModeX,
 *                                  SkTileMode tileModeY,
 *                                  bool isRaw) {
 *     SkASSERT(!imageToDraw->isAlphaOnly());
 *
 *     const Image_YUVA* yuvaImage = static_cast<const Image_YUVA*>(imageToDraw);
 *     const SkYUVAInfo& yuvaInfo = yuvaImage->yuvaInfo();
 *     // We would want to add a translation to the local matrix to handle other sitings.
 *     SkASSERT(yuvaInfo.sitingX() == SkYUVAInfo::Siting::kCentered);
 *     SkASSERT(yuvaInfo.sitingY() == SkYUVAInfo::Siting::kCentered);
 *
 *     YUVImageShaderBlock::ImageData imgData(sampling,
 *                                            tileModeX,
 *                                            tileModeY,
 *                                            imageToDraw->dimensions(),
 *                                            subset);
 *     for (int locIndex = 0; locIndex < SkYUVAInfo::kYUVAChannelCount; ++locIndex) {
 *         const TextureProxyView& view = yuvaImage->proxyView(locIndex);
 *         if (view) {
 *             imgData.fTextureProxies[locIndex] = view.refProxy();
 *             // The view's swizzle has the data channel for the YUVA location in all slots, so read
 *             // the 0th slot to determine fChannelSelect
 *             switch(view.swizzle()[0]) {
 *                 case 'r': imgData.fChannelSelect[locIndex] = {1.f, 0.f, 0.f, 0.f}; break;
 *                 case 'g': imgData.fChannelSelect[locIndex] = {0.f, 1.f, 0.f, 0.f}; break;
 *                 case 'b': imgData.fChannelSelect[locIndex] = {0.f, 0.f, 1.f, 0.f}; break;
 *                 case 'a': imgData.fChannelSelect[locIndex] = {0.f, 0.f, 0.f, 1.f}; break;
 *                 default:
 *                     imgData.fChannelSelect[locIndex] = {0.f, 0.f, 0.f, 0.f};
 *                     SkDEBUGFAILF("Unexpected swizzle for YUVA data: %c", view.swizzle()[0]);
 *                     break;
 *             }
 *         } else {
 *             // Only the A proxy view should be null, in which case we bind the Y proxy view to
 *             // pass validation and send all 1s for the channel selection to signal opaque alpha.
 *             SkASSERT(locIndex == 3);
 *             imgData.fTextureProxies[locIndex] = yuvaImage->proxyView(SkYUVAInfo::kY).refProxy();
 *             imgData.fChannelSelect[locIndex] = {1.f, 1.f, 1.f, 1.f};
 *             // For the hardcoded sampling no-swizzle case, we use this to set constant alpha
 *             imgData.fAlphaParam = 1;
 *         }
 *     }
 *
 *     auto [ssx, ssy] = yuvaImage->uvSubsampleFactors();
 *     if (ssx > 1 || ssy > 1) {
 *         // We need to adjust the image size we use for sampling to reflect the actual image size of
 *         // the UV planes. However, since our coordinates are in Y's texel space we need to scale
 *         // accordingly.
 *         const TextureProxyView& view = yuvaImage->proxyView(SkYUVAInfo::kU);
 *         imgData.fImgSizeUV = {view.dimensions().width()*ssx, view.dimensions().height()*ssy};
 *         // This promotion of nearest to linear filtering for UV planes exists to mimic
 *         // libjpeg[-turbo]'s do_fancy_upsampling option. We will filter the subsampled plane,
 *         // however we want to filter at a fixed point for each logical image pixel to simulate
 *         // nearest neighbor. In the shader we detect that the UV filtermode doesn't match the Y
 *         // filtermode, and snap to Y pixel centers.
 *         if (imgData.fSampling.filter == SkFilterMode::kNearest) {
 *             imgData.fSamplingUV = SkSamplingOptions(SkFilterMode::kLinear,
 *                                                     imgData.fSampling.mipmap);
 *             // Consider a logical image pixel at the edge of the subset. When computing the logical
 *             // pixel color value we should use a blend of two values from the subsampled plane.
 *             // Depending on where the subset edge falls in actual subsampled plane, one of those
 *             // values may come from outside the subset. Hence, we will use the default inset
 *             // in Y texel space of 1/2. This applies the wrap mode to the subset but allows
 *             // linear filtering to read pixels that are just outside the subset.
 *             imgData.fLinearFilterUVInset.fX = 0.5f;
 *             imgData.fLinearFilterUVInset.fY = 0.5f;
 *         } else if (imgData.fSampling.filter == SkFilterMode::kLinear) {
 *             // We need to inset so that we aren't sampling outside the subset, but no farther.
 *             // Start by mapping the subset to UV texel space
 *             float scaleX = 1.f/ssx;
 *             float scaleY = 1.f/ssy;
 *             SkRect subsetUV = {imgData.fSubset.fLeft  *scaleX,
 *                                imgData.fSubset.fTop   *scaleY,
 *                                imgData.fSubset.fRight *scaleX,
 *                                imgData.fSubset.fBottom*scaleY};
 *             // Round to UV texel borders
 *             SkIRect iSubsetUV = subsetUV.roundOut();
 *             // Inset in UV and map back to Y texel space. This gives us the largest possible
 *             // inset rectangle that will not sample outside of the subset texels in UV space.
 *             SkRect insetRectUV = {(iSubsetUV.fLeft  +0.5f)*ssx,
 *                                   (iSubsetUV.fTop   +0.5f)*ssy,
 *                                   (iSubsetUV.fRight -0.5f)*ssx,
 *                                   (iSubsetUV.fBottom-0.5f)*ssy};
 *             // Compute intersection with original inset
 *             SkRect insetRect = imgData.fSubset.makeOutset(-0.5f, -0.5f);
 *             (void) insetRect.intersect(insetRectUV);
 *             // Compute max inset values to ensure we always remain within the subset.
 *             imgData.fLinearFilterUVInset = {std::max(insetRect.fLeft - imgData.fSubset.fLeft,
 *                                                      imgData.fSubset.fRight - insetRect.fRight),
 *                                             std::max(insetRect.fTop - imgData.fSubset.fTop,
 *                                                      imgData.fSubset.fBottom - insetRect.fBottom)};
 *         }
 *     }
 *
 *     float yuvM[20];
 *     SkColorMatrix_YUV2RGB(yuvaInfo.yuvColorSpace(), yuvM);
 *     // We drop the fourth column entirely since the transformation
 *     // should not depend on alpha. The fifth column is sent as a separate
 *     // vector. The fourth row is also dropped entirely because alpha should
 *     // never be modified.
 *     SkASSERT(yuvM[3] == 0 && yuvM[8] == 0 && yuvM[13] == 0 && yuvM[18] == 1);
 *     SkASSERT(yuvM[15] == 0 && yuvM[16] == 0 && yuvM[17] == 0 && yuvM[19] == 0);
 *     imgData.fYUVtoRGBMatrix.setAll(
 *         yuvM[ 0], yuvM[ 1], yuvM[ 2],
 *         yuvM[ 5], yuvM[ 6], yuvM[ 7],
 *         yuvM[10], yuvM[11], yuvM[12]
 *     );
 *     imgData.fYUVtoRGBTranslate = {yuvM[4], yuvM[9], yuvM[14]};
 *
 *     SkColorSpaceXformSteps steps;
 *     SkASSERT(steps.fFlags.mask() == 0);   // By default, the colorspace should have no effect
 *
 *     // The actual output from the YUV image shader for non-opaque images is unpremul so
 *     // we need to correct for the fact that the Image_YUVA_Graphite's alpha type is premul.
 *     SkAlphaType srcAT = imageToDraw->alphaType() == kPremul_SkAlphaType
 *                                 ? kUnpremul_SkAlphaType
 *                                 : imageToDraw->alphaType();
 *     if (isRaw) {
 *         // Because we've avoided the premul alpha step in the YUV shader, we need to make sure
 *         // it happens when drawing unpremul (i.e., non-opaque) images.
 *         steps = SkColorSpaceXformSteps(imageToDraw->colorSpace(),
 *                                        srcAT,
 *                                        imageToDraw->colorSpace(),
 *                                        imageToDraw->alphaType());
 *     } else {
 *         SkAlphaType dstAT = keyContext.dstColorInfo().alphaType();
 *         // Setting the dst alphaType up this way is necessary because otherwise the constructor
 *         // for SkColorSpaceXformSteps will set dstAT = srcAT when dstAT == kOpaque, and the
 *         // premul step needed for non-opaque images won't occur.
 *         if (dstAT == kOpaque_SkAlphaType && srcAT == kUnpremul_SkAlphaType) {
 *             dstAT = kPremul_SkAlphaType;
 *         }
 *         steps = SkColorSpaceXformSteps(imageToDraw->colorSpace(),
 *                                        srcAT,
 *                                        keyContext.dstColorInfo().colorSpace(),
 *                                        dstAT);
 *     }
 *     ColorSpaceTransformBlock::ColorSpaceTransformData data(steps);
 *
 *     Compose(keyContext,
 *             /* addInnerToKey= */ [&]() -> void {
 *                 YUVImageShaderBlock::AddBlock(keyContext, imgData);
 *             },
 *             /* addOuterToKey= */ [&]() -> void {
 *                 ColorSpaceTransformBlock::AddBlock(keyContext, data);
 *             });
 * }
 * ```
 */
public fun addYuvImageToKey(
  keyContext: KeyContext,
  imageToDraw: SkImage?,
  subset: SkRect,
  sampling: SkSamplingOptions,
  tileModeX: SkTileMode,
  tileModeY: SkTileMode,
  isRaw: Boolean,
) {
  TODO("Implement addYuvImageToKey")
}

/**
 * C++ original:
 * ```cpp
 * static void add_image_to_key(const KeyContext& keyContext,
 *                              const SkImage* image,
 *                              SkRect subset,
 *                              SkSamplingOptions sampling,
 *                              SkTileMode tileModeX,
 *                              SkTileMode tileModeY,
 *                              bool isRaw) {
 *     auto [ imageToDraw, newSampling ] = GetGraphiteBacked(keyContext.recorder(),
 *                                                           image,
 *                                                           sampling);
 *     if (!imageToDraw) {
 *         SKGPU_LOG_W("Couldn't convert SkImage to a Graphite-backed representation");
 *         keyContext.paintParamsKeyBuilder()->addErrorBlock();
 *         return;
 *     }
 *
 *     // We must call notifyInUse() here to link the final, Graphite-backed 'imageToDraw'
 *     // to the DrawContext that will sample it.
 *     //
 *     // This is necessary for two primary cases:
 *     // 1. The original image was not Graphite-backed.
 *     // 2. The original image was already Graphite-backed, but produced through Image::Copy, possibly
 *     //    from a different DrawContext.
 *     //
 *     // Failing to call this can lead to leaked Device and DrawContext memory (b/338453542).
 *     SkASSERT(as_IB(imageToDraw)->isGraphiteBacked());
 *     SkASSERT(keyContext.drawContext());
 *     static_cast<Image_Base*>(imageToDraw.get())->notifyInUse(keyContext.recorder(),
 *                                                              keyContext.drawContext());
 *     if (as_IB(imageToDraw)->isYUVA()) {
 *         return add_yuv_image_to_key(keyContext,
 *                                     imageToDraw.get(),
 *                                     subset,
 *                                     newSampling,
 *                                     tileModeX,
 *                                     tileModeY,
 *                                     isRaw);
 *     }
 *
 *     auto view = AsView(imageToDraw.get());
 *     SkASSERT(newSampling.mipmap == SkMipmapMode::kNone || view.mipmapped() == Mipmapped::kYes);
 *
 *     ImageShaderBlock::ImageData imgData(newSampling,
 *                                         tileModeX,
 *                                         tileModeY,
 *                                         view.proxy()->dimensions(),
 *                                         subset);
 *
 *     // Here we detect pixel aligned blit-like image draws. Some devices have low precision filtering
 *     // and will produce degraded (blurry) images unexpectedly for sequential exact pixel blits when
 *     // not using nearest filtering. This is common for canvas scrolling implementations. Forcing
 *     // nearest filtering when possible can also be a minor perf/power optimization depending on the
 *     // hardware.
 *     bool samplingHasNoEffect = false;
 *     // Cubic sampling is will not filter the same as nearest even when pixel aligned.
 *     if (!(keyContext.flags() & KeyGenFlags::kDisableSamplingOptimization || newSampling.useCubic)) {
 *         SkMatrix totalM = keyContext.local2Dev().asM33();
 *         if (keyContext.localMatrix()) {
 *             totalM.preConcat(*keyContext.localMatrix());
 *         }
 *         totalM.normalizePerspective();
 *         // The matrix should be translation with only pixel aligned 2d translation.
 *         samplingHasNoEffect = totalM.isTranslate() && SkScalarIsInt(totalM.getTranslateX()) &&
 *                               SkScalarIsInt(totalM.getTranslateY());
 *     }
 *
 *     imgData.fSampling = samplingHasNoEffect ? SkFilterMode::kNearest : newSampling;
 *     imgData.fTextureProxy = view.refProxy();
 *     skgpu::Swizzle readSwizzle = view.swizzle();
 *     // If the color type is alpha-only, propagate the alpha value to the other channels.
 *     if (imageToDraw->isAlphaOnly()) {
 *         readSwizzle = skgpu::Swizzle::Concat(readSwizzle, skgpu::Swizzle("000a"));
 *     }
 *     ColorSpaceTransformBlock::ColorSpaceTransformData colorXformData(
 *             SwizzleClassToReadEnum(readSwizzle));
 *
 *     if (!isRaw) {
 *         colorXformData.fSteps = SkColorSpaceXformSteps(imageToDraw->colorSpace(),
 *                                                        imageToDraw->alphaType(),
 *                                                        keyContext.dstColorInfo().colorSpace(),
 *                                                        keyContext.dstColorInfo().alphaType());
 *
 *         if (imageToDraw->isAlphaOnly() &&
 *             !(keyContext.flags() & KeyGenFlags::kDisableAlphaOnlyImageColorization)) {
 *             // NOTE: Alpha is not affected by colorspace conversion to the dst, and the paint color
 *             // is already xformed to the dst, but the ColorSpaceTransformBlock is necessary to apply
 *             // any read swizzle, which is often necessary for alpha-only color types.
 *             Blend(keyContext,
 *                   /* addBlendToKey= */ [&] () -> void {
 *                       AddFixedBlendMode(keyContext, SkBlendMode::kDstIn);
 *                   },
 *                   /* addSrcToKey= */ [&] () -> void {
 *                       Compose(keyContext,
 *                               /* addInnerToKey= */ [&]() -> void {
 *                                   ImageShaderBlock::AddBlock(keyContext,
 *                                                              imgData);
 *                               },
 *                               /* addOuterToKey= */ [&]() -> void {
 *                                   ColorSpaceTransformBlock::AddBlock(keyContext,
 *                                                                      colorXformData);
 *                               });
 *                   },
 *                   /* addDstToKey= */ [&]() -> void {
 *                       RGBPaintColorBlock::AddBlock(keyContext);
 *                   });
 *             return;
 *         }
 *     }
 *
 *     Compose(keyContext,
 *             /* addInnerToKey= */ [&]() -> void {
 *                 ImageShaderBlock::AddBlock(keyContext, imgData);
 *             },
 *             /* addOuterToKey= */ [&]() -> void {
 *                 ColorSpaceTransformBlock::AddBlock(keyContext, colorXformData);
 *             });
 * }
 * ```
 */
public fun addImageToKey(
  keyContext: KeyContext,
  image: SkImage?,
  subset: SkRect,
  sampling: SkSamplingOptions,
  tileModeX: SkTileMode,
  tileModeY: SkTileMode,
  isRaw: Boolean,
) {
  TODO("Implement addImageToKey")
}

/**
 * C++ original:
 * ```cpp
 * static SkMatrix get_image_origin_matrix(const SkImage* image) {
 *     // If the image is not graphite backed then we can assume the origin will be TopLeft as we
 *     // require that in the ImageProvider utility. Also Graphite YUV images are assumed to be TopLeft
 *     // origin.
 *     SkASSERT(image);
 *     const auto* imgBase = as_IB(image);
 *     if (imgBase->isGraphiteBacked()) {
 *         // The YUV formats can encode their own origin including reflection and rotation,
 *         // so we need to concat that to the local matrix transform.
 *         if (imgBase->isYUVA()) {
 *             auto imgYUVA = static_cast<const Image_YUVA*>(imgBase);
 *             return imgYUVA->yuvaInfo().inverseOriginMatrix();
 *         } else {
 *             const auto& view = static_cast<const Image*>(imgBase)->textureProxyView();
 *             if (view.origin() == Origin::kBottomLeft) {
 *                 return SkMatrix::ScaleTranslate(1.f, -1.f, 0.f, view.height());
 *             }
 *         }
 *     }
 *     // Otherwise no modification required
 *     return SkMatrix::I();
 * }
 * ```
 */
public fun getImageOriginMatrix(image: SkImage?): SkMatrix {
  TODO("Implement getImageOriginMatrix")
}

/**
 * C++ original:
 * ```cpp
 * static SkMatrix get_gradient_matrix(const SkGradientBaseShader* gradShader) {
 *     // Override the conical gradient matrix since graphite uses a different algorithm
 *     // than the ganesh and raster backends.
 *     if (gradShader->asGradient() == SkShaderBase::GradientType::kConical) {
 *         auto conicalShader = static_cast<const SkConicalGradient*>(gradShader);
 *
 *         if (conicalShader->getType() == SkConicalGradient::Type::kRadial) {
 *             SkMatrix conicalMatrix = SkMatrix::Translate(-conicalShader->getStartCenter());
 *             float scale = sk_ieee_float_divide(1, conicalShader->getDiffRadius());
 *             conicalMatrix.postScale(scale, scale);
 *             return conicalMatrix;
 *         } else {
 *             auto mx = (SkConicalGradient::MapToUnitX(conicalShader->getStartCenter(),
 *                                                      conicalShader->getEndCenter()));
 *             return *mx;
 *         }
 *     } else {
 *         // Use the standard gradient matrix for other types
 *         return gradShader->getGradientMatrix();
 *     }
 * }
 * ```
 */
public fun getGradientMatrix(gradShader: SkGradientBaseShader?): SkMatrix {
  TODO("Implement getGradientMatrix")
}

/**
 * C++ original:
 * ```cpp
 * static SkBitmap create_color_and_offset_bitmap(int numStops,
 *                                                const SkPMColor4f* colors,
 *                                                const float* offsets) {
 *     SkBitmap colorsAndOffsetsBitmap;
 *
 *     colorsAndOffsetsBitmap.allocPixels(
 *             SkImageInfo::Make(numStops, 2, kRGBA_F16_SkColorType, kPremul_SkAlphaType));
 *
 *     for (int i = 0; i < numStops; i++) {
 *         // TODO: there should be a way to directly set a premul pixel in a bitmap with
 *         // a premul color.
 *         SkColor4f unpremulColor = colors[i].unpremul();
 *         colorsAndOffsetsBitmap.erase(unpremulColor, SkIRect::MakeXYWH(i, 0, 1, 1));
 *
 *         float offset = offsets ? offsets[i] : SkIntToFloat(i) / (numStops - 1);
 *         SkASSERT(offset >= 0.0f && offset <= 1.0f);
 *
 *         int exponent;
 *         float mantissa = frexp(offset, &exponent);
 *
 *         SkHalf halfE = SkFloatToHalf(exponent);
 *         if ((int)SkHalfToFloat(halfE) != exponent) {
 *             SKGPU_LOG_W("Encoding gradient to f16 failed");
 *             return {};
 *         }
 *
 * #if defined(SK_DEBUG)
 *         SkHalf halfM = SkFloatToHalf(mantissa);
 *
 *         float restored = ldexp(SkHalfToFloat(halfM), (int)SkHalfToFloat(halfE));
 *         float error = abs(restored - offset);
 *         SkASSERT(error < 0.001f);
 * #endif
 *
 *         // TODO: we're only using 2 of the f16s here. The encoding could be altered to better
 *         // preserve precision. This encoding yields < 0.001f error for 2^20 evenly spaced stops.
 *         colorsAndOffsetsBitmap.erase(SkColor4f{mantissa, (float)exponent, 0, 1},
 *                                      SkIRect::MakeXYWH(i, 1, 1, 1));
 *     }
 *
 *     return colorsAndOffsetsBitmap;
 * }
 * ```
 */
public fun createColorAndOffsetBitmap(
  numStops: Int,
  colors: SkPMColor4f?,
  offsets: Float?,
): SkBitmap {
  TODO("Implement createColorAndOffsetBitmap")
}

/**
 * C++ original:
 * ```cpp
 * static void make_interpolated_to_dst(const KeyContext& keyContext,
 *                                      const GradientShaderBlocks::GradientData& gradData,
 *                                      const SkGradient::Interpolation& interp,
 *                                      SkColorSpace* intermediateCS) {
 *     using ColorSpace = SkGradient::Interpolation::ColorSpace;
 *
 *     bool inputPremul = static_cast<bool>(interp.fInPremul);
 *
 *     switch (interp.fColorSpace) {
 *         case ColorSpace::kLab:
 *         case ColorSpace::kOKLab:
 *         case ColorSpace::kOKLabGamutMap:
 *         case ColorSpace::kLCH:
 *         case ColorSpace::kOKLCH:
 *         case ColorSpace::kOKLCHGamutMap:
 *         case ColorSpace::kHSL:
 *         case ColorSpace::kHWB:
 *             inputPremul = false;
 *             break;
 *         default:
 *             break;
 *     }
 *
 *     const SkColorInfo& dstColorInfo = keyContext.dstColorInfo();
 *
 *     SkColorSpace* dstColorSpace =
 *             dstColorInfo.colorSpace() ? dstColorInfo.colorSpace() : sk_srgb_singleton();
 *
 *     SkAlphaType intermediateAlphaType = inputPremul ? kPremul_SkAlphaType : kUnpremul_SkAlphaType;
 *
 *     ColorSpaceTransformBlock::ColorSpaceTransformData data(
 *             intermediateCS, intermediateAlphaType, dstColorSpace, dstColorInfo.alphaType());
 *
 *     // The gradient block and colorSpace conversion block need to be combined
 *     // (via the Compose block) so that the localMatrix block can treat them as
 *     // one child.
 *     Compose(keyContext,
 *             /* addInnerToKey= */ [&]() -> void {
 *                 GradientShaderBlocks::AddBlock(keyContext, gradData);
 *             },
 *             /* addOuterToKey= */ [&]() -> void {
 *                 ColorSpaceTransformBlock::AddBlock(keyContext, data);
 *             });
 * }
 * ```
 */
public fun makeInterpolatedToDst(
  keyContext: KeyContext,
  gradData: GradientShaderBlocks.GradientData,
  interp: SkGradient.Interpolation,
  intermediateCS: SkColorSpace?,
) {
  TODO("Implement makeInterpolatedToDst")
}

/**
 * C++ original:
 * ```cpp
 * static void add_gradient_to_key(const KeyContext& keyContext,
 *                                 const SkSweepGradient* shader) {
 *     add_gradient_to_key(keyContext,
 *                         shader,
 *                         shader->center(),
 *                         { 0.0f, 0.0f },
 *                         0.0f,
 *                         0.0f,
 *                         shader->tBias(),
 *                         shader->tScale());
 * }
 * ```
 */
public fun addGradientToKey(keyContext: KeyContext, shader: SkSweepGradient?) {
  TODO("Implement addGradientToKey")
}

/**
 * C++ original:
 * ```cpp
 * void AddToKey(const KeyContext& keyContext, const PaintParams::SimpleImage& simpleImage) {
 *     add_local_matrix_to_key(keyContext,
 *                             simpleImage.fLocalMatrix ? *simpleImage.fLocalMatrix : SkMatrix::I(),
 *                             get_image_origin_matrix(simpleImage.fImage),
 *                             [&](const KeyContext& childCtx) {
 *                                     add_image_to_key(childCtx,
 *                                                      simpleImage.fImage,
 *                                                      simpleImage.fSubset,
 *                                                      simpleImage.fSamplingOptions,
 *                                                      SkTileMode::kClamp,
 *                                                      SkTileMode::kClamp,
 *                                                      /*isRaw=*/false);
 *                             });
 * }
 * ```
 */
public fun addToKey(keyContext: KeyContext, simpleImage: PaintParams.SimpleImage) {
  TODO("Implement addToKey")
}

/**
 * C++ original:
 * ```cpp
 * void AddFixedBlendMode(const KeyContext& keyContext, SkBlendMode bm) {
 *     SkASSERT(bm <= SkBlendMode::kLastMode);
 *     BuiltInCodeSnippetID id = static_cast<BuiltInCodeSnippetID>(kFixedBlendIDOffset +
 *                                                                 static_cast<int>(bm));
 *     keyContext.paintParamsKeyBuilder()->addBlock(id);
 * }
 * ```
 */
public fun addFixedBlendMode(keyContext: KeyContext, bm: SkBlendMode) {
  TODO("Implement addFixedBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * void AddBlendMode(const KeyContext& keyContext, SkBlendMode bm) {
 *     // For non-fixed blends, coefficient blend modes are combined into the same shader snippet.
 *     // The same goes for the HSLC advanced blends. The remaining advanced blends are fairly unique
 *     // in their implementations. To avoid having to compile all of their SkSL, they are treated as
 *     // fixed blend modes.
 *     SkSpan<const float> coeffs = skgpu::GetPorterDuffBlendConstants(bm);
 *     if (!coeffs.empty()) {
 *         PorterDuffBlenderBlock::AddBlock(keyContext, coeffs);
 *     } else if (bm >= SkBlendMode::kHue) {
 *         ReducedBlendModeInfo blendInfo = GetReducedBlendModeInfo(bm);
 *         HSLCBlenderBlock::AddBlock(keyContext, blendInfo.fUniformData);
 *     } else {
 *         AddFixedBlendMode(keyContext, bm);
 *     }
 * }
 * ```
 */
public fun addBlendMode(keyContext: KeyContext, bm: SkBlendMode) {
  TODO("Implement addBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * void AddDitherBlock(const KeyContext& keyContext, SkColorType ct) {
 *     static const SkBitmap gLUT = skgpu::MakeDitherLUT();
 *
 *     sk_sp<TextureProxy> proxy = RecorderPriv::CreateCachedProxy(keyContext.recorder(), gLUT,
 *                                                                 "DitherLUT");
 *     if (keyContext.recorder() && !proxy) {
 *         SKGPU_LOG_W("Couldn't create dither shader's LUT");
 *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
 *         return;
 *     }
 *
 *     DitherShaderBlock::DitherData data(skgpu::DitherRangeForConfig(ct), std::move(proxy));
 *
 *     DitherShaderBlock::AddBlock(keyContext, data);
 * }
 * ```
 */
public fun addDitherBlock(keyContext: KeyContext, ct: SkColorType) {
  TODO("Implement addDitherBlock")
}

/**
 * C++ original:
 * ```cpp
 * bool should_dither(const PaintParams& p, SkColorType dstCT) {
 *     // The paint dither flag can veto.
 *     if (!p.dither()) {
 *         return false;
 *     }
 *
 *     if (dstCT == kUnknown_SkColorType) {
 *         return false;
 *     }
 *
 *     // We always dither 565 or 4444 when requested.
 *     if (dstCT == kRGB_565_SkColorType || dstCT == kARGB_4444_SkColorType) {
 *         return true;
 *     }
 *
 *     // Otherwise, dither is only needed for non-const paints.
 *     return p.imageShader() || (p.shader() && !as_SB(p.shader())->isConstant());
 * }
 * ```
 */
public fun shouldDither(p: PaintParams, dstCT: SkColorType): Boolean {
  TODO("Implement shouldDither")
}

/**
 * C++ original:
 * ```cpp
 * bool blendmode_depends_on_dst(SkBlendMode blendMode, bool srcIsOpaque) {
 *     if (blendMode == SkBlendMode::kSrc || blendMode == SkBlendMode::kClear) {
 *         // src and clear blending never depends on dst
 *         return false;
 *     }
 *
 *     if (blendMode == SkBlendMode::kSrcOver || blendMode == SkBlendMode::kDstOut) {
 *         // src-over depends on dst if src is transparent (a != 1)
 *         // dst-out simplifies to kClear if a == 1
 *         return !srcIsOpaque;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun blendmodeDependsOnDst(blendMode: SkBlendMode, srcIsOpaque: Boolean): Boolean {
  TODO("Implement blendmodeDependsOnDst")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<const SkBlender*, SkBlendMode> get_final_blend(const SkBlender* blender) {
 *     if (!blender) {
 *         return {nullptr, SkBlendMode::kSrcOver};
 *     }
 *
 *     auto optionalBlendMode = as_BB(blender)->asBlendMode();
 *     if (optionalBlendMode.has_value()) {
 *         return {nullptr, *optionalBlendMode};
 *     } else {
 *         return {blender, SkBlendMode::kSrc};
 *     }
 * }
 * ```
 */
public fun getFinalBlend(blender: SkBlender?): Pair<SkBlender?, SkBlendMode> {
  TODO("Implement getFinalBlend")
}

/**
 * C++ original:
 * ```cpp
 * Coverage get_renderer_coverage(Coverage coverage,
 *                                const SkShader* clipShader,
 *                                const NonMSAAClip& nonMSAAClip) {
 *     return (clipShader || !nonMSAAClip.isEmpty()) && coverage == Coverage::kNone ?
 *             Coverage::kSingleChannel : coverage;
 * }
 * ```
 */
public fun getRendererCoverage(
  coverage: Coverage,
  clipShader: SkShader?,
  nonMSAAClip: NonMSAAClip,
): Coverage {
  TODO("Implement getRendererCoverage")
}

/**
 * C++ original:
 * ```cpp
 * SkEnumBitMask<DstUsage> get_dst_usage(const Caps* caps,
 *                                       TextureFormat targetFormat,
 *                                       const PaintParams& paint,
 *                                       Coverage rendererCoverage) {
 *     if (paint.finalBlender()) {
 *         return DstUsage::kDstReadRequired;
 *     }
 *
 *     SkBlendMode finalBlendMode = paint.finalBlendMode();
 *     SkEnumBitMask<DstUsage> dstUsage =
 *             CanUseHardwareBlending(caps, targetFormat, finalBlendMode, rendererCoverage)
 *                             ? DstUsage::kNone
 *                             : DstUsage::kDstReadRequired;
 *     if (finalBlendMode > SkBlendMode::kLastCoeffMode) {
 *         dstUsage |= DstUsage::kAdvancedBlend;
 *     }
 *     return dstUsage;
 * }
 * ```
 */
public fun getDstUsage(
  caps: Caps?,
  targetFormat: TextureFormat,
  paint: PaintParams,
  rendererCoverage: Coverage,
): SkEnumBitMask<DstUsage> {
  TODO("Implement getDstUsage")
}

/**
 * C++ original:
 * ```cpp
 * bool lift_coord_expressions(SkSpan<ShaderNode*> nodes, int* availableVaryings) {
 *     bool anyNeedLocalCoords = false;
 *
 *     for (ShaderNode* node : nodes) {
 *         bool curNeedsLocalCoords =
 *                 SkToBool(node->requiredFlags() & SnippetRequirementFlags::kLocalCoords);
 *
 *         // Lift expressions from nodes whose liftable expressions are on coordinate inputs.
 *         if (*availableVaryings > 0 && curNeedsLocalCoords &&
 *             node->entry()->fLiftableExpressionType ==
 *                     ShaderSnippet::LiftableExpressionType::kLocalCoords) {
 *             --*availableVaryings;
 *
 * #if !defined(SK_USE_LEGACY_UNIFORM_LIFTING_GRAPHITE)
 *             // We can potentially lift the nested expressions under here as well.
 *             const bool childNeedsOurCoords =
 *                     lift_coord_expressions(node->children(), availableVaryings);
 *             // If no child needs our lifted coords, we can omit them from the fragment shader
 *             // entirely, and only use them in the vertex shader for calculating other coords.
 *             if (!childNeedsOurCoords) {
 *                 node->setOmitExpressionFlag();
 *             } else {
 *                 node->setLiftExpressionFlag();
 *             }
 * #else
 *             node->setLiftExpressionFlag();
 * #endif
 *             // Since we lifted the coordinate expression here, this node no longer needs a local
 *             // coords argument.
 *             curNeedsLocalCoords = false;
 *             node->unsetLocalCoordsFlag();
 *
 * #if !defined(SK_USE_LEGACY_UNIFORM_LIFTING_GRAPHITE)
 *         // If the node passes through its local coords to its children, we check if those perform
 *         // modifications that can be lifted.
 *         } else if (*availableVaryings > 0 &&
 *                    node->requiredFlags() & SnippetRequirementFlags::kPassthroughLocalCoords) {
 *             // Assume that this node doesn't need local coordinates unless its actual shader snippet
 *             // entry does, or one of its children does even after accounting for lifting.
 *             const bool entryNeedsLocalCoords = node->entry()->needsLocalCoords();
 *             const bool childNeedsLocalCoords = lift_coord_expressions(node->children(),
 *                                                                       availableVaryings);
 *             curNeedsLocalCoords = entryNeedsLocalCoords || childNeedsLocalCoords;
 *             if (!curNeedsLocalCoords) {
 *                 node->unsetLocalCoordsFlag();
 *             }
 * #endif
 *         }
 *
 *         anyNeedLocalCoords |= curNeedsLocalCoords;
 *     }
 *
 *     return anyNeedLocalCoords;
 * }
 * ```
 */
public fun liftCoordExpressions(nodes: SkSpan<ShaderNode?>, availableVaryings: Int?): Boolean {
  TODO("Implement liftCoordExpressions")
}

/**
 * C++ original:
 * ```cpp
 * void lift_color_expressions(SkSpan<ShaderNode*> nodes, int* availableVaryings) {
 * #if !defined(SK_USE_LEGACY_UNIFORM_LIFTING_GRAPHITE)
 *     for (ShaderNode* node : nodes) {
 *         if (*availableVaryings > 0 &&
 *             node->entry()->fLiftableExpressionType ==
 *                     ShaderSnippet::LiftableExpressionType::kPriorStageOutput) {
 *             --*availableVaryings;
 *             node->setLiftExpressionFlag();
 *         }
 *     }
 * #endif
 * }
 * ```
 */
public fun liftColorExpressions(nodes: SkSpan<ShaderNode?>, availableVaryings: Int?) {
  TODO("Implement liftColorExpressions")
}

/**
 * C++ original:
 * ```cpp
 * static void append_as_base64(SkString* str, SkSpan<const uint32_t> data) {
 *     str->append("(");
 *     str->appendU32(data.size());
 *     str->append(": ");
 *     // Encode data in base64 to shorten it
 *     const size_t srcDataSize = data.size() * sizeof(uint32_t); // size in bytes
 *     SkAutoMalloc encodedData{SkBase64::EncodedSize(srcDataSize)};
 *     char* dst = static_cast<char*>(encodedData.get());
 *     size_t encodedLen = SkBase64::Encode(data.data(), srcDataSize, dst);
 *     str->append(dst, encodedLen);
 *     str->append(")");
 * }
 * ```
 */
public fun appendAsBase64(str: String?, `data`: SkSpan<UInt>) {
  TODO("Implement appendAsBase64")
}

/**
 * C++ original:
 * ```cpp
 * static int key_to_string(const Caps* caps,
 *                          SkString* str,
 *                          const ShaderCodeDictionary* dict,
 *                          SkSpan<const uint32_t> keyData,
 *                          int currentIndex,
 *                          int indent) {
 *     SkASSERT(currentIndex < SkTo<int>(keyData.size()));
 *
 *     const bool multiline = indent >= 0;
 *     if (multiline) {
 *         // Format for multi-line printing
 *         str->appendf("%*c", 2 * indent, ' ');
 *     }
 *
 *     uint32_t id = keyData[currentIndex++];
 *     auto entry = dict->getEntry(id);
 *     if (!entry) {
 *         str->append("UnknownCodeSnippetID:");
 *         str->appendS32(id);
 *         str->append(" ");
 *         return currentIndex;
 *     }
 *
 *     str->append(entry->fName);
 *
 *     if (entry->storesSamplerDescData()) {
 *         SkASSERT(currentIndex + 1 < SkTo<int>(keyData.size()));
 *
 *         // If an entry stores data, then the next key value reports the quantity of key indices that
 *         // are used to house the data for this snippet. This way, we know how many indices to
 *         // iterate over in order to capture the snippet's data before we may encounter another
 *         // snippet ID.
 *         // For example:
 *         // [snippetId using 2 indices worth of data] [2] [dataValue0] [dataValue1] [next snippet ID]
 *         const size_t dataIndexCount = keyData[currentIndex++];
 *         SkASSERT(currentIndex + dataIndexCount < keyData.size());
 *
 *         bool descriptiveFormAppended = false;
 *         if (dataIndexCount == 0) {
 *             // We shorten the string for the common case of no extra data.
 *             str->append("(0)");
 *             descriptiveFormAppended = true;
 *         } else {
 *             SkASSERTF(dataIndexCount == 2 || dataIndexCount == 3, "count %zu", dataIndexCount);
 *
 *             // Attempt to append the sampler data as human-readable YCbCr information
 *             SamplerDesc s(keyData[currentIndex],
 *                           keyData[currentIndex+1],
 *                           /* extFormatMSB= */ dataIndexCount == 3 ? keyData[currentIndex+2] : 0);
 *
 *             if (s.isImmutable()) {
 *                 std::string tmp = caps->toString(s.immutableSamplerInfo());
 *                 if (!tmp.empty()) {
 *                     str->append("(");
 *                     str->append(tmp);
 *                     str->append(")");
 *                     descriptiveFormAppended = true;
 *                 }
 *             }
 *         }
 *
 *         if (!descriptiveFormAppended) {
 *             append_as_base64(str, { &keyData[currentIndex], dataIndexCount });
 *         }
 *
 *         // Increment current index past the indices which contain data
 *         currentIndex += dataIndexCount;
 *     }
 *
 *     if (entry->fNumChildren > 0) {
 *         if (multiline) {
 *             str->append(":\n");
 *             indent++;
 *         } else {
 *             str->append(" [ ");
 *         }
 *
 *         for (int i = 0; i < entry->fNumChildren; ++i) {
 *             currentIndex = key_to_string(caps, str, dict, keyData, currentIndex, indent);
 *         }
 *
 *         if (!multiline) {
 *             str->append("]");
 *         }
 *     }
 *
 *     if (!multiline) {
 *         str->append(" ");
 *     } else if (entry->fNumChildren == 0) {
 *         str->append("\n");
 *     }
 *     return currentIndex;
 * }
 * ```
 */
public fun keyToString(
  caps: Caps?,
  str: String?,
  dict: ShaderCodeDictionary?,
  keyData: SkSpan<UInt>,
  currentIndex: Int,
  indent: Int,
): Int {
  TODO("Implement keyToString")
}

/**
 * C++ original:
 * ```cpp
 * bool is_block_valid(const ShaderCodeDictionary* dict,
 *                                   SkSpan<const uint32_t> keyData,
 *                                   int* currentIndex) {
 *     if (*currentIndex >= SkTo<int>(keyData.size())) {
 *         return false;
 *     }
 *
 *     uint32_t id = keyData[(*currentIndex)++];
 *     if (id >= kBuiltInCodeSnippetIDCount &&
 *         !SkKnownRuntimeEffects::IsSkiaKnownRuntimeEffect(id) &&
 *         !dict->isUserDefinedKnownRuntimeEffect(id)) {
 *         return false;
 *     }
 *
 *     auto entry = dict->getEntry(id);
 *     if (!entry) {
 *         return false;
 *     }
 *
 *     if (entry->storesSamplerDescData()) {
 *         if (*currentIndex >= SkTo<int>(keyData.size())) {
 *             return false;
 *         }
 *
 *         const int dataLength = keyData[(*currentIndex)++];
 *
 *         if (*currentIndex + dataLength > SkTo<int>(keyData.size())) {
 *             return false;
 *         }
 *
 *         *currentIndex += dataLength;
 *     }
 *
 *     if (entry->fNumChildren > 0) {
 *         for (int i = 0; i < entry->fNumChildren; ++i) {
 *             if (!is_block_valid(dict, keyData, currentIndex)) {
 *                 return false;
 *             }
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun isBlockValid(
  dict: ShaderCodeDictionary?,
  keyData: SkSpan<UInt>,
  currentIndex: Int?,
): Boolean {
  TODO("Implement isBlockValid")
}

/**
 * C++ original:
 * ```cpp
 * void make_bitmap_key(skgpu::UniqueKey* key, const SkBitmap& bm) {
 *     SkASSERT(key);
 *
 *     SkIPoint origin = bm.pixelRefOrigin();
 *     SkIRect subset = SkIRect::MakePtSize(origin, bm.dimensions());
 *
 *     static const skgpu::UniqueKey::Domain kProxyCacheDomain = skgpu::UniqueKey::GenerateDomain();
 *     skgpu::UniqueKey::Builder builder(key, kProxyCacheDomain, 5, "ProxyCache");
 *     builder[0] = bm.pixelRef()->getGenerationID();
 *     builder[1] = subset.fLeft;
 *     builder[2] = subset.fTop;
 *     builder[3] = subset.fRight;
 *     builder[4] = subset.fBottom;
 * }
 * ```
 */
public fun makeBitmapKey(key: UniqueKey?, bm: SkBitmap) {
  TODO("Implement makeBitmapKey")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkIDChangeListener> make_unique_key_invalidation_listener(const skgpu::UniqueKey& key,
 *                                                                 uint32_t recorderID) {
 *     class Listener : public SkIDChangeListener {
 *     public:
 *         Listener(const skgpu::UniqueKey& key, uint32_t recorderUniqueID)
 *                 : fMsg(key, recorderUniqueID) {}
 *
 *         void changed() override {
 *             SkMessageBus<skgpu::UniqueKeyInvalidatedMsg_Graphite, uint32_t>::Post(fMsg);
 *         }
 *
 *     private:
 *         skgpu::UniqueKeyInvalidatedMsg_Graphite fMsg;
 *     };
 *
 *     return sk_make_sp<Listener>(key, recorderID);
 * }
 * ```
 */
public fun makeUniqueKeyInvalidationListener(key: UniqueKey, recorderID: UInt): SkSp<SkIDChangeListener> {
  TODO("Implement makeUniqueKeyInvalidationListener")
}

/**
 * C++ original:
 * ```cpp
 * skcpu::Draw make_draw(const SkPixmap& pm, const SkRasterClip& rc, const SkMatrix& m) {
 *     skcpu::Draw draw;
 *     draw.fDst = pm;
 *     draw.fBlitterChooser = SkA8Blitter_Choose;
 *     draw.fCTM = &m;
 *     draw.fRC = &rc;
 *     return draw;
 * }
 * ```
 */
public fun makeDraw(
  pm: SkPixmap,
  rc: SkRasterClip,
  m: SkMatrix,
): Draw {
  TODO("Implement makeDraw")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t add_transform_key(skgpu::UniqueKey::Builder* builder,
 *                            int startIndex,
 *                            const Transform& transform) {
 *     // We require the upper left 2x2 of the matrix to match exactly for a cache hit.
 *     SkMatrix mat = transform.matrix().asM33();
 *     SkScalar sx = mat.get(SkMatrix::kMScaleX);
 *     SkScalar sy = mat.get(SkMatrix::kMScaleY);
 *     SkScalar kx = mat.get(SkMatrix::kMSkewX);
 *     SkScalar ky = mat.get(SkMatrix::kMSkewY);
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     // Fractional translate does not affect caching on Android. This is done for better cache
 *     // hit ratio and speed and is matching HWUI behavior, which didn't consider the matrix
 *     // at all when caching paths.
 *     SkFixed fracX = 0;
 *     SkFixed fracY = 0;
 * #else
 *     SkScalar tx = mat.get(SkMatrix::kMTransX);
 *     SkScalar ty = mat.get(SkMatrix::kMTransY);
 *     // Allow 8 bits each in x and y of subpixel positioning.
 *     SkFixed fracX = SkScalarToFixed(SkScalarFraction(tx)) & 0x0000FF00;
 *     SkFixed fracY = SkScalarToFixed(SkScalarFraction(ty)) & 0x0000FF00;
 * #endif
 *     (*builder)[startIndex + 0] = SkFloat2Bits(sx);
 *     (*builder)[startIndex + 1] = SkFloat2Bits(sy);
 *     (*builder)[startIndex + 2] = SkFloat2Bits(kx);
 *     (*builder)[startIndex + 3] = SkFloat2Bits(ky);
 *     // FracX and fracY are &ed with 0x0000ff00, so need to shift one down to fill 16 bits.
 *     uint32_t fracBits = fracX | (fracY >> 8);
 *
 *     return fracBits;
 * }
 * ```
 */
public fun addTransformKey(
  builder: UniqueKey.Builder?,
  startIndex: Int,
  transform: Transform,
): UInt {
  TODO("Implement addTransformKey")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::UniqueKey GeneratePathMaskKey(const Shape& shape,
 *                                      const Transform& transform,
 *                                      const SkStrokeRec& strokeRec,
 *                                      skvx::half2 maskOrigin,
 *                                      skvx::half2 maskSize) {
 *     skgpu::UniqueKey maskKey;
 *     {
 *         static const skgpu::UniqueKey::Domain kDomain = skgpu::UniqueKey::GenerateDomain();
 *         int styleKeySize = 7;
 *         if (!strokeRec.isHairlineStyle() && !strokeRec.isFillStyle()) {
 *             // Add space for width and miter if needed
 *             styleKeySize += 2;
 *         }
 *         skgpu::UniqueKey::Builder builder(&maskKey, kDomain, styleKeySize + shape.keySize(),
 *                                           "Raster Path Mask");
 *         builder[0] = maskOrigin.x() | (maskOrigin.y() << 16);
 *         builder[1] = maskSize.x() | (maskSize.y() << 16);
 *
 *         // Add transform key and get packed fractional translation bits
 *         uint32_t fracBits = add_transform_key(&builder, 2, transform);
 *         // Distinguish between path styles. For anything but fill, we also need to include
 *         // the cap. (SW grows hairlines by 0.5 pixel with round and square caps). For stroke
 *         // or fill-and-stroke we need to include the join, width, and miter.
 *         static_assert(SkStrokeRec::kStyleCount <= (1 << 2));
 *         static_assert(SkPaint::kCapCount <= (1 << 2));
 *         static_assert(SkPaint::kJoinCount <= (1 << 2));
 *         uint32_t styleBits = strokeRec.getStyle();
 *         if (!strokeRec.isFillStyle()) {
 *             styleBits |= (strokeRec.getCap() << 2);
 *         }
 *         if (!strokeRec.isHairlineStyle() && !strokeRec.isFillStyle()) {
 *             styleBits |= (strokeRec.getJoin() << 4);
 *             builder[6] = SkFloat2Bits(strokeRec.getWidth());
 *             builder[7] = SkFloat2Bits(strokeRec.getMiter());
 *         }
 *         builder[styleKeySize-1] = fracBits | (styleBits << 16);
 *         shape.writeKey(&builder[styleKeySize], /*includeInverted=*/false);
 *     }
 *     return maskKey;
 * }
 * ```
 */
public fun generatePathMaskKey(
  shape: Shape,
  transform: Transform,
  strokeRec: SkStrokeRec,
  maskOrigin: Half2,
  maskSize: Half2,
): UniqueKey {
  TODO("Implement generatePathMaskKey")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::UniqueKey GenerateClipMaskKey(uint32_t stackRecordID,
 *                                      const ClipStack::ElementList* elementsForMask,
 *                                      SkIRect maskDeviceBounds,
 *                                      bool includeBounds,
 *                                      SkIRect* keyBounds,
 *                                      bool* usesPathKey) {
 *     static constexpr int kMaxShapeCountForKey = 2;
 *     static const skgpu::UniqueKey::Domain kDomain = skgpu::UniqueKey::GenerateDomain();
 *
 *     skgpu::UniqueKey maskKey;
 *     // if the element list is too large we just use the stackRecordID
 *     if (elementsForMask->size() <= kMaxShapeCountForKey) {
 *         constexpr int kXformKeySize = 5;
 *         int keySize = 0;
 *         bool canCreateKey = true;
 *         // Iterate through to get key size and see if we can create a key at all
 *         for (int i = 0; i < elementsForMask->size(); ++i) {
 *             int shapeKeySize = (*elementsForMask)[i]->fShape.keySize();
 *             if (shapeKeySize < 0) {
 *                 canCreateKey = false;
 *                 break;
 *             }
 *             keySize += kXformKeySize + shapeKeySize;
 *         }
 *         if (canCreateKey) {
 *             if (includeBounds) {
 *                 keySize += 2;
 *             }
 *             skgpu::UniqueKey::Builder builder(&maskKey, kDomain, keySize,
 *                                               "Clip Path Mask");
 *             int elementKeyIndex = 0;
 *             Rect unclippedBounds = Rect::InfiniteInverted();
 *             for (int i = 0; i < elementsForMask->size(); ++i) {
 *                 const ClipStack::Element* element = (*elementsForMask)[i];
 *
 *                 // Add transform key and get packed fractional translation bits
 *                 uint32_t fracBits = add_transform_key(&builder,
 *                                                       elementKeyIndex,
 *                                                       element->fLocalToDevice);
 *                 uint32_t opBits = static_cast<uint32_t>(element->fOp);
 *                 builder[elementKeyIndex + 4] = fracBits | (opBits << 16);
 *
 *                 const Shape& shape = element->fShape;
 *                 shape.writeKey(&builder[elementKeyIndex + kXformKeySize],
 *                                /*includeInverted=*/true);
 *
 *                 elementKeyIndex += kXformKeySize + shape.keySize();
 *
 *                 Rect transformedBounds = element->fLocalToDevice.mapRect(element->fShape.bounds());
 *                 unclippedBounds.join(transformedBounds);
 *             }
 *
 *             // The keyBounds are the maskDeviceBounds relative to the full transformed mask. We use
 *             // this to ensure we capture the situation where the maskDeviceBounds are equal in two
 *             // cases but actually enclose different regions of the full mask due to an integer
 *             // translation (which is not captured in the key) in the element transforms.
 *             *keyBounds = maskDeviceBounds.makeOffset(-unclippedBounds.left(),
 *                                                      -unclippedBounds.top());
 *
 *             if (includeBounds) {
 *                 SkASSERT(SkTFitsIn<int16_t>(keyBounds->left()));
 *                 SkASSERT(SkTFitsIn<int16_t>(keyBounds->top()));
 *                 SkASSERT(SkTFitsIn<int16_t>(keyBounds->right()));
 *                 SkASSERT(SkTFitsIn<int16_t>(keyBounds->bottom()));
 *
 *                 builder[elementKeyIndex] = keyBounds->left() | (keyBounds->top() << 16);
 *                 builder[elementKeyIndex+1] = keyBounds->right() | (keyBounds->bottom() << 16);
 *             }
 *
 *             *usesPathKey = true;
 *             return maskKey;
 *         }
 *     }
 *
 *     // Either we have too many elements or at least one shape can't create a key
 *     skgpu::UniqueKey::Builder builder(&maskKey, kDomain, 1, "Clip SaveRecord Mask");
 *     builder[0] = stackRecordID;
 *
 *     *usesPathKey = false;
 *     // It doesn't matter what the keyBounds are in this case --
 *     // the stackRecordID is enough to distinguish between clips.
 *     *keyBounds = {};
 *     return maskKey;
 * }
 * ```
 */
public fun generateClipMaskKey(
  stackRecordID: UInt,
  elementsForMask: ClipStack.ElementList?,
  maskDeviceBounds: SkIRect,
  includeBounds: Boolean,
  keyBounds: SkIRect?,
  usesPathKey: Boolean?,
): UniqueKey {
  TODO("Implement generateClipMaskKey")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t create_unique_id() {
 *     static std::atomic<uint32_t> nextID{1};
 *     uint32_t id;
 *     do {
 *         id = nextID.fetch_add(1, std::memory_order_relaxed);
 *     } while (id == SK_InvalidUniqueID);
 *     return id;
 * }
 * ```
 */
public fun createUniqueId(): UInt {
  TODO("Implement createUniqueId")
}

/**
 * C++ original:
 * ```cpp
 * bool dimensions_are_valid(const int maxTextureSize, const SkISize& dimensions) {
 *     if (dimensions.isEmpty() ||
 *         dimensions.width()  > maxTextureSize ||
 *         dimensions.height() > maxTextureSize) {
 *         SKGPU_LOG_W("Call to createBackendTexture has requested dimensions (%d, %d) larger than the"
 *                     " supported gpu max texture size: %d. Or the dimensions are empty.",
 *                     dimensions.fWidth, dimensions.fHeight, maxTextureSize);
 *         return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun dimensionsAreValid(maxTextureSize: Int, dimensions: SkISize): Boolean {
  TODO("Implement dimensionsAreValid")
}

/**
 * C++ original:
 * ```cpp
 * constexpr bool is_valid_samplecount(uint32_t sampleCount) {
 *     return SkIsPow2(sampleCount) && sampleCount >= 1 && sampleCount <= 16;
 * }
 * ```
 */
public fun isValidSamplecount(sampleCount: UInt): Boolean {
  TODO("Implement isValidSamplecount")
}

/**
 * C++ original:
 * ```cpp
 * bool stream_is_pipeline(SkStream* stream) {
 *     char magic[8];
 *     static_assert(sizeof(kMagic) == sizeof(magic), "");
 *
 *     if (stream->read(magic, sizeof(kMagic)) != sizeof(kMagic)) {
 *         return false;
 *     }
 *
 *     if (0 != memcmp(magic, kMagic, sizeof(kMagic))) {
 *         return false;
 *     }
 *
 *     uint32_t version;
 *     if (!stream->readU32(&version)) {
 *         return false;
 *     }
 *
 *     if (version != kCurrent_Version) {
 *         return false;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun streamIsPipeline(stream: SkStream?): Boolean {
  TODO("Implement streamIsPipeline")
}

/**
 * C++ original:
 * ```cpp
 * bool serialize_graphics_pipeline_desc(ShaderCodeDictionary* shaderCodeDictionary,
 *                                                     SkWStream* stream,
 *                                                     const GraphicsPipelineDesc& pipelineDesc) {
 *     PaintParamsKey key = shaderCodeDictionary->lookup(pipelineDesc.paintParamsID());
 *
 *     if (!stream->write32(static_cast<uint32_t>(pipelineDesc.renderStepID()))) {
 *         return false;
 *     }
 *
 *     if (!key.isValid()) {
 *         if (!stream->write32(0)) {
 *             return false;
 *         }
 *         // Not all GraphicsPipeline have a valid PaintParamsKey
 *         return true;
 *     }
 *
 *     const SkSpan<const uint32_t> keySpan = key.data();
 *
 *     if (!key.isSerializable(shaderCodeDictionary)) {
 *         return false;
 *     }
 *
 *     if (!stream->write32(SkToU32(keySpan.size()))) {
 *         return false;
 *     }
 *     if (!stream->write(keySpan.data(), 4 * keySpan.size())) {
 *         return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun serializeGraphicsPipelineDesc(
  shaderCodeDictionary: ShaderCodeDictionary?,
  stream: SkWStream?,
  pipelineDesc: GraphicsPipelineDesc,
): Boolean {
  TODO("Implement serializeGraphicsPipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool deserialize_graphics_pipeline_desc(ShaderCodeDictionary* shaderCodeDictionary,
 *                                                       SkStream* stream,
 *                                                       GraphicsPipelineDesc* pipelineDesc) {
 *     uint32_t tmp;
 *     if (!stream->readU32(&tmp)) {
 *         return false;
 *     }
 *
 *     if (tmp >= RenderStep::kNumRenderSteps) {
 *         return false;
 *     }
 *     RenderStep::RenderStepID renderStepID = static_cast<RenderStep::RenderStepID>(tmp);
 *
 *     if (!stream->readU32(&tmp)) {
 *         return false;
 *     }
 *
 *     UniquePaintParamsID paintParamsID = UniquePaintParamsID::Invalid();
 *     if (tmp) {
 *         SkAutoMalloc storage(4 * tmp);
 *         if (stream->read(storage.get(), 4 * tmp) != 4 * tmp) {
 *             return false;
 *         }
 *
 *         PaintParamsKey ppk = PaintParamsKey(SkSpan<uint32_t>((uint32_t*) storage.get(), tmp));
 *
 *         if (!ppk.isSerializable(shaderCodeDictionary)) {
 *             return false;
 *         }
 *
 *         paintParamsID = shaderCodeDictionary->findOrCreate(ppk);
 *     }
 *
 *     *pipelineDesc = GraphicsPipelineDesc(renderStepID, paintParamsID);
 *     return true;
 * }
 * ```
 */
public fun deserializeGraphicsPipelineDesc(
  shaderCodeDictionary: ShaderCodeDictionary?,
  stream: SkStream?,
  pipelineDesc: GraphicsPipelineDesc?,
): Boolean {
  TODO("Implement deserializeGraphicsPipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool serialize_attachment_desc(SkWStream* stream,
 *                                              const AttachmentDesc& attachmentDesc) {
 *     uint32_t tag = attachmentDesc.fFormat == TextureFormat::kUnsupported
 *             ? SkSetFourByteTag(static_cast<uint8_t>(TextureFormat::kUnsupported), 0, 0, 1)
 *             : SkSetFourByteTag(static_cast<uint8_t>(attachmentDesc.fFormat),
 *                                static_cast<uint8_t>(attachmentDesc.fLoadOp),
 *                                static_cast<uint8_t>(attachmentDesc.fStoreOp),
 *                                static_cast<uint8_t>(attachmentDesc.fSampleCount));
 *     return stream->write32(tag);
 * }
 * ```
 */
public fun serializeAttachmentDesc(stream: SkWStream?, attachmentDesc: AttachmentDesc): Boolean {
  TODO("Implement serializeAttachmentDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool deserialize_attachment_desc(SkStream* stream,
 *                                                AttachmentDesc* attachmentDesc) {
 *     uint32_t tag;
 *     if (!stream->readU32(&tag)) {
 *         return false;
 *     }
 *
 *     uint8_t format      = static_cast<uint8_t>((tag >> 24) & 0xFF);
 *     uint8_t loadOp      = static_cast<uint8_t>((tag >> 16) & 0xFF);
 *     uint8_t storeOp     = static_cast<uint8_t>((tag >>  8) & 0xFF);
 *     uint8_t sampleCount = static_cast<uint8_t>((tag >>  0) & 0xFF);
 *
 *     if (format >= kTextureFormatCount) {
 *         return false;
 *     }
 *     if (loadOp >= kLoadOpCount) {
 *         return false;
 *     }
 *     if (storeOp >= kStoreOpCount) {
 *         return false;
 *     }
 *     if (!is_valid_samplecount(sampleCount)) {
 *         return false;
 *     }
 *
 *     *attachmentDesc = {static_cast<TextureFormat>(format),
 *                        static_cast<LoadOp>(loadOp),
 *                        static_cast<StoreOp>(storeOp),
 *                        static_cast<SampleCount>(sampleCount)};
 *
 *     return true;
 * }
 * ```
 */
public fun deserializeAttachmentDesc(stream: SkStream?, attachmentDesc: AttachmentDesc?): Boolean {
  TODO("Implement deserializeAttachmentDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool serialize_render_pass_desc(SkWStream* stream,
 *                                               const RenderPassDesc& renderPassDesc) {
 *     if (!serialize_attachment_desc(stream, renderPassDesc.fColorAttachment)) {
 *         return false;
 *     }
 *     if (!serialize_attachment_desc(stream, renderPassDesc.fColorResolveAttachment)) {
 *         return false;
 *     }
 *     if (!serialize_attachment_desc(stream, renderPassDesc.fDepthStencilAttachment)) {
 *         return false;
 *     }
 *
 *     if (!stream->write16(renderPassDesc.fWriteSwizzle.asKey())) {
 *         return false;
 *     }
 *     if (!stream->write8(static_cast<uint8_t>(renderPassDesc.fSampleCount))) {
 *         return false;
 *     }
 *
 *     // Omit clear values for the various attachments as they do not effect structure.
 *     // Omit fDstReadStrategy from the serialization because it is not a part of RenderPassDesc
 *     // keys and does not impact pipeline creation. When deserializing, the strategy can be
 *     // obtained via caps->getDstReadStrategy().
 *
 *     return true;
 * }
 * ```
 */
public fun serializeRenderPassDesc(stream: SkWStream?, renderPassDesc: RenderPassDesc): Boolean {
  TODO("Implement serializeRenderPassDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool deserialize_render_pass_desc(const Caps* caps,
 *                                                 SkStream* stream,
 *                                                 RenderPassDesc* renderPassDesc) {
 *     if (!deserialize_attachment_desc(stream, &renderPassDesc->fColorAttachment)) {
 *         return false;
 *     }
 *     if (!deserialize_attachment_desc(stream, &renderPassDesc->fColorResolveAttachment)) {
 *         return false;
 *     }
 *     if (!deserialize_attachment_desc(stream, &renderPassDesc->fDepthStencilAttachment)) {
 *         return false;
 *     }
 *
 *     uint16_t swizzle;
 *     if (!stream->readU16(&swizzle)) {
 *         return false;
 *     }
 *     renderPassDesc->fWriteSwizzle = SwizzleCtorAccessor::Make(swizzle);
 *
 *     uint8_t sampleCount;
 *     if (!stream->readU8(&sampleCount) || !is_valid_samplecount(sampleCount)) {
 *         return false;
 *     }
 *     renderPassDesc->fSampleCount = static_cast<SampleCount>(sampleCount);
 *
 *     // RenderPassDesc dst read strategy is not serialized as it is not something we key on and does
 *     // not impact pipeline creation. When deserializing, simply query Caps again for a
 *     // DstReadStrategy. Leave clear color/depth/stencil as their default values.
 *     renderPassDesc->fDstReadStrategy = caps->getDstReadStrategy();
 *
 *     return true;
 * }
 * ```
 */
public fun deserializeRenderPassDesc(
  caps: Caps?,
  stream: SkStream?,
  renderPassDesc: RenderPassDesc?,
): Boolean {
  TODO("Implement deserializeRenderPassDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool SerializePipelineDesc(ShaderCodeDictionary* shaderCodeDictionary,
 *                            SkWStream* stream,
 *                            const GraphicsPipelineDesc& pipelineDesc,
 *                            const RenderPassDesc& renderPassDesc) {
 *
 *     stream->write(kMagic, sizeof(kMagic));
 *     stream->write32(kCurrent_Version);
 *
 *     if (!serialize_graphics_pipeline_desc(shaderCodeDictionary, stream, pipelineDesc)) {
 *         return false;
 *     }
 *
 *     if (!serialize_render_pass_desc(stream, renderPassDesc)) {
 *         return false;
 *     }
 *
 *     stream->write32(SK_BLOB_END_TAG);
 *     return true;
 * }
 * ```
 */
public fun serializePipelineDesc(
  shaderCodeDictionary: ShaderCodeDictionary?,
  stream: SkWStream?,
  pipelineDesc: GraphicsPipelineDesc,
  renderPassDesc: RenderPassDesc,
): Boolean {
  TODO("Implement serializePipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool DeserializePipelineDesc(const Caps* caps,
 *                              ShaderCodeDictionary* shaderCodeDictionary,
 *                              SkStream* stream,
 *                              GraphicsPipelineDesc* pipelineDesc,
 *                              RenderPassDesc* renderPassDesc) {
 *     SkASSERT(stream);
 *
 *     if (!stream_is_pipeline(stream)) {
 *         return false;
 *     }
 *
 *     if (!deserialize_graphics_pipeline_desc(shaderCodeDictionary, stream, pipelineDesc)) {
 *         return false;
 *     }
 *
 *     if (!deserialize_render_pass_desc(caps, stream, renderPassDesc)) {
 *         return false;
 *     }
 *
 *     uint32_t tag;
 *     if (!stream->readU32(&tag)) {
 *         return false;
 *     }
 *
 *     if (tag != SK_BLOB_END_TAG) {
 *         return false;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun deserializePipelineDesc(
  caps: Caps?,
  shaderCodeDictionary: ShaderCodeDictionary?,
  stream: SkStream?,
  pipelineDesc: GraphicsPipelineDesc?,
  renderPassDesc: RenderPassDesc?,
): Boolean {
  TODO("Implement deserializePipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkData> PipelineDescToData(const Caps* caps,
 *                                  ShaderCodeDictionary* shaderCodeDictionary,
 *                                  const GraphicsPipelineDesc& pipelineDesc,
 *                                  const RenderPassDesc& renderPassDesc) {
 *     SkDynamicMemoryWStream stream;
 *
 *     if (!SerializePipelineDesc(shaderCodeDictionary,
 *                                &stream,
 *                                pipelineDesc, renderPassDesc)) {
 *         return nullptr;
 *     }
 *
 *     sk_sp<SkData> data = stream.detachAsData();
 *
 * #if 0  // Enable this to thoroughly test Pipeline serialization
 *     {
 *         // Check that the PipelineDesc round trips through serialization
 *         GraphicsPipelineDesc readBackPipelineDesc;
 *         RenderPassDesc readBackRenderPassDesc;
 *
 *         SkAssertResult(DataToPipelineDesc(caps,
 *                                           shaderCodeDictionary,
 *                                           data.get(),
 *                                           &readBackPipelineDesc,
 *                                           &readBackRenderPassDesc));
 *
 *         DumpPipelineDesc("invokeCallback - original", shaderCodeDictionary,
 *                          pipelineDesc, renderPassDesc);
 *
 *         DumpPipelineDesc("invokeCallback - readback", shaderCodeDictionary,
 *               readBackPipelineDesc, readBackRenderPassDesc);
 *
 *         SkASSERT(ComparePipelineDescs(pipelineDesc, renderPassDesc,
 *                                       readBackPipelineDesc, readBackRenderPassDesc));
 *     }
 * #endif
 *
 *     return data;
 * }
 * ```
 */
public fun pipelineDescToData(
  caps: Caps?,
  shaderCodeDictionary: ShaderCodeDictionary?,
  pipelineDesc: GraphicsPipelineDesc,
  renderPassDesc: RenderPassDesc,
): SkSp<SkData> {
  TODO("Implement pipelineDescToData")
}

/**
 * C++ original:
 * ```cpp
 * bool DataToPipelineDesc(const Caps* caps,
 *                         ShaderCodeDictionary* shaderCodeDictionary,
 *                         const SkData* data,
 *                         GraphicsPipelineDesc* pipelineDesc,
 *                         RenderPassDesc* renderPassDesc) {
 *     if (!data) {
 *         return false;
 *     }
 *     SkMemoryStream stream(data->data(), data->size());
 *
 *     if (!DeserializePipelineDesc(caps,
 *                                  shaderCodeDictionary,
 *                                  &stream,
 *                                  pipelineDesc,
 *                                  renderPassDesc)) {
 *         return false;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun dataToPipelineDesc(
  caps: Caps?,
  shaderCodeDictionary: ShaderCodeDictionary?,
  `data`: SkData?,
  pipelineDesc: GraphicsPipelineDesc?,
  renderPassDesc: RenderPassDesc?,
): Boolean {
  TODO("Implement dataToPipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * void DumpPipelineDesc(const Caps* caps,
 *                       const char* label,
 *                       ShaderCodeDictionary* shaderCodeDictionary,
 *                       const GraphicsPipelineDesc& pipelineDesc,
 *                       const RenderPassDesc& renderPassDesc) {
 *     SkString pipelineStr = pipelineDesc.toString(caps, shaderCodeDictionary);
 *     SkString renderPassStr = renderPassDesc.toPipelineLabel();
 *     SkDebugf("%s: %s - %s\n", label, pipelineStr.c_str(), renderPassStr.c_str());
 * }
 * ```
 */
public fun dumpPipelineDesc(
  caps: Caps?,
  label: String?,
  shaderCodeDictionary: ShaderCodeDictionary?,
  pipelineDesc: GraphicsPipelineDesc,
  renderPassDesc: RenderPassDesc,
) {
  TODO("Implement dumpPipelineDesc")
}

/**
 * C++ original:
 * ```cpp
 * bool ComparePipelineDescs(const GraphicsPipelineDesc& a1, const RenderPassDesc& b1,
 *                           const GraphicsPipelineDesc& a2, const RenderPassDesc& b2) {
 *     return (a1 == a2) && (b1 == b2);
 * }
 * ```
 */
public fun comparePipelineDescs(
  a1: GraphicsPipelineDesc,
  b1: RenderPassDesc,
  a2: GraphicsPipelineDesc,
  b2: RenderPassDesc,
): Boolean {
  TODO("Implement comparePipelineDescs")
}

/**
 * C++ original:
 * ```cpp
 * const char* get_known_rte_name(StableKey key) {
 *     switch (key) {
 * #define M(type) case StableKey::k##type : return "KnownRuntimeEffect_" #type;
 * #define M1(type)
 * #define M2(type, initializer) case StableKey::k##type : return "KnownRuntimeEffect_" #type;
 *         SK_ALL_STABLEKEYS(M, M1, M2)
 * #undef M2
 * #undef M1
 * #undef M
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun getKnownRteName(key: StableKey): Char {
  TODO("Implement getKnownRteName")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_storage_buffer_access(const char* ssboIndex,
 *                                       const char* uniformName) {
 *     return SkSL::String::printf("combinedUniformData[%s].%s", ssboIndex, uniformName);
 * }
 * ```
 */
public fun getStorageBufferAccess(ssboIndex: String?, uniformName: String?): String {
  TODO("Implement getStorageBufferAccess")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_mangled_name(const std::string& baseName, int manglingSuffix) {
 *     return baseName + "_" + std::to_string(manglingSuffix);
 * }
 * ```
 */
public fun getMangledName(baseName: String, manglingSuffix: Int): String {
  TODO("Implement getMangledName")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_mangled_uniform_name(const ShaderInfo& shaderInfo,
 *                                      const Uniform& uniform,
 *                                      int manglingSuffix) {
 *     std::string result;
 *
 *     if (uniform.isPaintColor()) {
 *         // Due to deduplication there will only ever be one of these
 *         result = uniform.name();
 *     } else {
 *         result = uniform.name() + std::string("_") + std::to_string(manglingSuffix);
 *     }
 *     if (shaderInfo.uniformSsboIndex()) {
 *         result = get_storage_buffer_access(shaderInfo.uniformSsboIndex(), result.c_str());
 *     }
 *     return result;
 * }
 * ```
 */
public fun getMangledUniformName(
  shaderInfo: ShaderInfo,
  uniform: Uniform,
  manglingSuffix: Int,
): String {
  TODO("Implement getMangledUniformName")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_mangled_sampler_name(const TextureAndSampler& tex, int manglingSuffix) {
 *     return tex.name() + std::string("_") + std::to_string(manglingSuffix);
 * }
 * ```
 */
public fun getMangledSamplerName(tex: TextureAndSampler, manglingSuffix: Int): String {
  TODO("Implement getMangledSamplerName")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_mangled_struct_reference(const ShaderInfo& shaderInfo,
 *                                          const ShaderNode* node) {
 *     SkASSERT(node->entry()->fUniformStructName);
 *     std::string result = "node_" + std::to_string(node->keyIndex()); // Field holding the struct
 *     if (shaderInfo.uniformSsboIndex()) {
 *         result = get_storage_buffer_access(shaderInfo.uniformSsboIndex(), result.c_str());
 *     }
 *     return result;
 * }
 * ```
 */
public fun getMangledStructReference(shaderInfo: ShaderInfo, node: ShaderNode?): String {
  TODO("Implement getMangledStructReference")
}

/**
 * C++ original:
 * ```cpp
 * std::string stitch_csv(SkSpan<const std::string> args) {
 *     std::string code = "";
 *     const char* separator = "";
 *     for (const std::string& arg : args) {
 *         code += separator;
 *         code += arg;
 *         separator = ", ";
 *     }
 *
 *     return code;
 * }
 * ```
 */
public fun stitchCsv(args: SkSpan<String>): String {
  TODO("Implement stitchCsv")
}

/**
 * C++ original:
 * ```cpp
 * void append_defaults(TArray<std::string>* list,
 *                      const ShaderNode* node,
 *                      const ShaderSnippet::Args* args) {
 *     // Use the node's aggregate required flags so that the provided dynamic variables propagate
 *     // to the child nodes that require them.
 *     if (node->requiredFlags() & SnippetRequirementFlags::kPriorStageOutput) {
 *         list->push_back(args ? args->fPriorStageOutput.c_str() : "half4 inColor");
 *     }
 *     if (node->requiredFlags() & SnippetRequirementFlags::kBlenderDstColor) {
 *         list->push_back(args ? args->fBlenderDstColor.c_str() : "half4 destColor");
 *     }
 *     if (node->requiredFlags() & SnippetRequirementFlags::kLocalCoords) {
 *         list->push_back(args ? args->fFragCoord.c_str() : "float2 pos");
 *     }
 *
 *     // Special variables and/or "global" scope variables that have to propagate
 *     // through the node tree.
 *     if (node->requiredFlags() & SnippetRequirementFlags::kPrimitiveColor) {
 *         list->push_back(args ? "primitiveColor" : "half4 primitiveColor");
 *     }
 * }
 * ```
 */
public fun appendDefaults(
  list: TArray<String>?,
  node: ShaderNode?,
  args: ShaderSnippet.Args?,
) {
  TODO("Implement appendDefaults")
}

/**
 * C++ original:
 * ```cpp
 * void append_uniforms(TArray<std::string>* list,
 *                      const ShaderInfo& shaderInfo,
 *                      const ShaderNode* node,
 *                      SkSpan<const std::string> childOutputs) {
 *     const ShaderSnippet* entry = node->entry();
 *
 *     if (entry->fUniformStructName) {
 *         // The node's uniforms are aggregated in a sub-struct within the global uniforms so we just
 *         // need to append a reference to the node's instance
 *         list->push_back(get_mangled_struct_reference(shaderInfo, node));
 *     } else {
 *         // The uniforms are in the global scope, so just pass in the ones bound to 'node'
 *         for (int i = 0; i < entry->fUniforms.size(); ++i) {
 *             list->push_back(get_mangled_uniform_name(shaderInfo,
 *                                                      entry->fUniforms[i],
 *                                                      node->keyIndex()));
 *         }
 *     }
 *
 *     // Append samplers
 *     for (int i = 0; i < entry->fTexturesAndSamplers.size(); ++i) {
 *         list->push_back(get_mangled_sampler_name(entry->fTexturesAndSamplers[i], node->keyIndex()));
 *     }
 *
 *     // Append gradient buffer.
 *     if (node->requiredFlags() & SnippetRequirementFlags::kGradientBuffer) {
 *         list->push_back(ShaderInfo::kGradientBufferName);
 *     }
 *
 *     // Append child output names.
 *     if (!childOutputs.empty()) {
 *         list->push_back_n(childOutputs.size(), childOutputs.data());
 *     }
 * }
 * ```
 */
public fun appendUniforms(
  list: TArray<String>?,
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  childOutputs: SkSpan<String>,
) {
  TODO("Implement appendUniforms")
}

/**
 * C++ original:
 * ```cpp
 * std::string invoke_node(const ShaderInfo& shaderInfo,
 *                         const ShaderNode* node,
 *                         const ShaderSnippet::Args& args) {
 *     std::string fnName;
 *     STArray<3, std::string> params; // 1-2 inputs and a uniform struct or texture
 *
 *     if (node->numChildren() == 0 && node->entry()->fStaticFunctionName) {
 *         // We didn't generate a helper function in the preamble, so add uniforms to the parameter
 *         // list and call the static function directly.
 *         fnName = node->entry()->fStaticFunctionName;
 *         append_defaults(&params, node, &args);
 *         append_uniforms(&params, shaderInfo, node, /*childOutputs=*/{});
 *     } else {
 *         // Invoke the generated helper function added to the preamble, which will handle invoking
 *         // any children and appending their values to the rest of the static fn's arguments.
 *         fnName = get_mangled_name(node->entry()->fName, node->keyIndex());
 *         append_defaults(&params, node, &args);
 *     }
 *
 *     return SkSL::String::printf("%s(%s)", fnName.c_str(), stitch_csv(params).c_str());
 * }
 * ```
 */
public fun invokeNode(
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  args: ShaderSnippet.Args,
): String {
  TODO("Implement invokeNode")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_helper_declaration(const ShaderNode* node) {
 *     const ShaderSnippet* entry = node->entry();
 *     std::string helperFnName = get_mangled_name(entry->fName, node->keyIndex());
 *
 *     STArray<3, std::string> params;
 *     append_defaults(&params, node, /*args=*/nullptr); // null args emits declarations
 *
 *     return SkSL::String::printf("half4 %s(%s)", helperFnName.c_str(), stitch_csv(params).c_str());
 * }
 * ```
 */
public fun emitHelperDeclaration(node: ShaderNode?): String {
  TODO("Implement emitHelperDeclaration")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateSolidColorExpression(const ShaderInfo& shaderInfo,
 *                                          const ShaderNode* node,
 *                                          const ShaderSnippet::Args& args) {
 *     std::string uniform =
 *             get_mangled_uniform_name(shaderInfo, node->entry()->fUniforms[0], node->keyIndex());
 *     return SkSL::String::printf("half4(%s)", uniform.c_str());
 * }
 * ```
 */
public fun generateSolidColorExpression(
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  args: ShaderSnippet.Args,
): String {
  TODO("Implement generateSolidColorExpression")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateSolidColorPreamble(const ShaderInfo& shaderInfo,
 *                                        const ShaderNode* node) {
 *     std::string code = emit_helper_declaration(node) + " {return ";
 *
 *     if (node->requiredFlags() & SnippetRequirementFlags::kLiftExpression) {
 *         code += node->getExpressionVaryingName();
 *     } else if (node->requiredFlags() & SnippetRequirementFlags::kOmitExpression) {
 *         code += "half4(0)";
 *     } else {
 *         code += GenerateSolidColorExpression(shaderInfo, node, ShaderSnippet::kDefaultArgs);
 *     }
 *
 *     return code + ";}";
 * }
 * ```
 */
public fun generateSolidColorPreamble(shaderInfo: ShaderInfo, node: ShaderNode?): String {
  TODO("Implement generateSolidColorPreamble")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateLocalMatrixExpression(const ShaderInfo& shaderInfo,
 *                                           const ShaderNode* node,
 *                                           const ShaderSnippet::Args& args) {
 *     // NOTE: upper2x2 is a float2x2 packed in column major order into a float4
 *     std::string upper2x2 =
 *             get_mangled_uniform_name(shaderInfo, node->entry()->fUniforms[0], node->keyIndex());
 *     std::string translation =
 *             get_mangled_uniform_name(shaderInfo, node->entry()->fUniforms[1], node->keyIndex());
 *     return SkSL::String::printf("float2x2(%s.xy, %s.zw)*%s + %s",
 *                                 upper2x2.c_str(),
 *                                 upper2x2.c_str(),
 *                                 args.fFragCoord.c_str(),
 *                                 translation.c_str());
 * }
 * ```
 */
public fun generateLocalMatrixExpression(
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  args: ShaderSnippet.Args,
): String {
  TODO("Implement generateLocalMatrixExpression")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateCoordNormalizeExpression(const ShaderInfo& shaderInfo,
 *                                              const ShaderNode* node,
 *                                              const ShaderSnippet::Args& args) {
 *     std::string uniform =
 *             get_mangled_uniform_name(shaderInfo, node->entry()->fUniforms[0], node->keyIndex());
 *     return SkSL::String::printf("(%s * %s)",
 *                                 uniform.c_str(),
 *                                 args.fFragCoord.c_str());
 * }
 * ```
 */
public fun generateCoordNormalizeExpression(
  shaderInfo: ShaderInfo,
  node: ShaderNode?,
  args: ShaderSnippet.Args,
): String {
  TODO("Implement generateCoordNormalizeExpression")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateCoordManipulationPreamble(const ShaderInfo& shaderInfo,
 *                                               const ShaderNode* node) {
 *     SkASSERT(node->numChildren() == kNumCoordinateManipulateChildren);
 *
 *     std::string perspectiveStatement;
 *
 *     const ShaderSnippet::Args& defaultArgs = ShaderSnippet::kDefaultArgs;
 *     ShaderSnippet::Args localArgs = ShaderSnippet::kDefaultArgs;
 *     if (node->child(0)->requiredFlags() & SnippetRequirementFlags::kLocalCoords) {
 *         std::string controlUni =
 *                 get_mangled_uniform_name(shaderInfo, node->entry()->fUniforms[0], node->keyIndex());
 *
 *         if (node->codeSnippetId() == (int) BuiltInCodeSnippetID::kLocalMatrixShader) {
 *             if (node->requiredFlags() & SnippetRequirementFlags::kLiftExpression) {
 *                 localArgs.fFragCoord = node->getExpressionVaryingName();
 *             } else if (!(node->requiredFlags() & SnippetRequirementFlags::kOmitExpression)) {
 *                 localArgs.fFragCoord = GenerateLocalMatrixExpression(shaderInfo, node, defaultArgs);
 *             }
 *         } else if (node->codeSnippetId() == (int) BuiltInCodeSnippetID::kLocalMatrixShaderPersp) {
 *             perspectiveStatement = SkSL::String::printf("float3 perspCoord = %s * %s.xy1;",
 *                                                         controlUni.c_str(),
 *                                                         defaultArgs.fFragCoord.c_str());
 *             localArgs.fFragCoord = "perspCoord.xy / perspCoord.z";
 *         } else if (node->codeSnippetId() == (int) BuiltInCodeSnippetID::kCoordNormalizeShader) {
 *             if (node->requiredFlags() & SnippetRequirementFlags::kLiftExpression) {
 *                 localArgs.fFragCoord = node->getExpressionVaryingName();
 *             } else if (!(node->requiredFlags() & SnippetRequirementFlags::kOmitExpression)) {
 *                 localArgs.fFragCoord =
 *                         GenerateCoordNormalizeExpression(shaderInfo, node, defaultArgs);
 *             }
 *         } else {
 *             SkASSERT(node->codeSnippetId() == (int) BuiltInCodeSnippetID::kCoordClampShader);
 *             localArgs.fFragCoord = SkSL::String::printf("clamp(%s, %s.LT, %s.RB)",
 *                                                         defaultArgs.fFragCoord.c_str(),
 *                                                         controlUni.c_str(), controlUni.c_str());
 *         }
 *     } // else this is a no-op
 *
 *     std::string decl = emit_helper_declaration(node);
 *     std::string invokeChild = invoke_node(shaderInfo, node->child(0), localArgs);
 *     return SkSL::String::printf("%s { %s return %s; }",
 *                                 decl.c_str(),
 *                                 perspectiveStatement.c_str(),
 *                                 invokeChild.c_str());
 * }
 * ```
 */
public fun generateCoordManipulationPreamble(shaderInfo: ShaderInfo, node: ShaderNode?): String {
  TODO("Implement generateCoordManipulationPreamble")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateComposePreamble(const ShaderInfo& shaderInfo, const ShaderNode* node) {
 *     SkASSERT(node->numChildren() >= 2);
 *
 *     const ShaderNode* outer = node->child(node->numChildren() - 1);
 *
 * #if defined(SK_DEBUG)
 *     const int numOuterParameters =
 *             SkToBool((outer->requiredFlags() & SnippetRequirementFlags::kPriorStageOutput)) +
 *             SkToBool((outer->requiredFlags() & SnippetRequirementFlags::kBlenderDstColor)) +
 *             SkToBool((outer->requiredFlags() & SnippetRequirementFlags::kLocalCoords));
 *     SkASSERT(node->numChildren() == numOuterParameters + 1);
 * #endif
 *
 *     const ShaderSnippet::Args& defaultArgs = ShaderSnippet::kDefaultArgs;
 *     ShaderSnippet::Args outerArgs = ShaderSnippet::kDefaultArgs;
 *     int child = 0;
 *     if (outer->requiredFlags() & SnippetRequirementFlags::kLocalCoords) {
 *         outerArgs.fFragCoord = invoke_node(shaderInfo, node->child(child++), defaultArgs);
 *     }
 *     if (outer->requiredFlags() & SnippetRequirementFlags::kPriorStageOutput) {
 *         outerArgs.fPriorStageOutput = invoke_node(shaderInfo, node->child(child++), defaultArgs);
 *     }
 *     if (outer->requiredFlags() & SnippetRequirementFlags::kBlenderDstColor) {
 *         outerArgs.fBlenderDstColor = invoke_node(shaderInfo, node->child(child++), defaultArgs);
 *     }
 *
 *     std::string decl = emit_helper_declaration(node);
 *     std::string invokeOuter = invoke_node(shaderInfo, outer, outerArgs);
 *     return SkSL::String::printf("%s { return %s; }", decl.c_str(), invokeOuter.c_str());
 * }
 * ```
 */
public fun generateComposePreamble(shaderInfo: ShaderInfo, node: ShaderNode?): String {
  TODO("Implement generateComposePreamble")
}

/**
 * C++ original:
 * ```cpp
 * std::string GenerateRuntimeShaderPreamble(const ShaderInfo& shaderInfo,
 *                                           const ShaderNode* node) {
 *     // Find this runtime effect in the shader-code or runtime-effect dictionary.
 *     SkASSERT(node->codeSnippetId() >= kBuiltInCodeSnippetIDCount);
 *     const SkRuntimeEffect* effect;
 *
 *     if (IsSkiaKnownRuntimeEffect(node->codeSnippetId())) {
 *         effect = GetKnownRuntimeEffect(static_cast<StableKey>(node->codeSnippetId()));
 *     } else if (SkKnownRuntimeEffects::IsViableUserDefinedKnownRuntimeEffect(
 *                                                               node->codeSnippetId())) {
 *         effect = shaderInfo.shaderCodeDictionary()->getUserDefinedKnownRuntimeEffect(
 *                 node->codeSnippetId());
 *     } else {
 *         SkASSERT(IsUserDefinedRuntimeEffect(node->codeSnippetId()));
 *         effect = shaderInfo.runtimeEffectDictionary()->find(node->codeSnippetId());
 *     }
 *     // This should always be true given the circumstances in which we call convertRuntimeEffect
 *     SkASSERT(effect);
 *
 *     const SkSL::Program& program = SkRuntimeEffectPriv::Program(*effect);
 *     const ShaderSnippet::Args& args = ShaderSnippet::kDefaultArgs;
 *     std::string preamble;
 *     GraphitePipelineCallbacks callbacks{shaderInfo, node, &preamble, effect};
 *     SkSL::PipelineStage::ConvertProgram(program,
 *                                         args.fFragCoord.c_str(),
 *                                         args.fPriorStageOutput.c_str(),
 *                                         args.fBlenderDstColor.c_str(),
 *                                         &callbacks);
 *     return preamble;
 * }
 * ```
 */
public fun generateRuntimeShaderPreamble(shaderInfo: ShaderInfo, node: ShaderNode?): String {
  TODO("Implement generateRuntimeShaderPreamble")
}

/**
 * C++ original:
 * ```cpp
 * static SkSLType uniform_type_to_sksl_type(const SkRuntimeEffect::Uniform& u) {
 *     using Type = SkRuntimeEffect::Uniform::Type;
 *     if (u.flags & SkRuntimeEffect::Uniform::kHalfPrecision_Flag) {
 *         switch (u.type) {
 *             case Type::kFloat:    return SkSLType::kHalf;
 *             case Type::kFloat2:   return SkSLType::kHalf2;
 *             case Type::kFloat3:   return SkSLType::kHalf3;
 *             case Type::kFloat4:   return SkSLType::kHalf4;
 *             case Type::kFloat2x2: return SkSLType::kHalf2x2;
 *             case Type::kFloat3x3: return SkSLType::kHalf3x3;
 *             case Type::kFloat4x4: return SkSLType::kHalf4x4;
 *             // NOTE: shorts cannot be uniforms, so we shouldn't ever get here.
 *             // Defensively return the full precision integer type.
 *             case Type::kInt:      SkDEBUGFAIL("unsupported uniform type"); return SkSLType::kInt;
 *             case Type::kInt2:     SkDEBUGFAIL("unsupported uniform type"); return SkSLType::kInt2;
 *             case Type::kInt3:     SkDEBUGFAIL("unsupported uniform type"); return SkSLType::kInt3;
 *             case Type::kInt4:     SkDEBUGFAIL("unsupported uniform type"); return SkSLType::kInt4;
 *         }
 *     } else {
 *         switch (u.type) {
 *             case Type::kFloat:    return SkSLType::kFloat;
 *             case Type::kFloat2:   return SkSLType::kFloat2;
 *             case Type::kFloat3:   return SkSLType::kFloat3;
 *             case Type::kFloat4:   return SkSLType::kFloat4;
 *             case Type::kFloat2x2: return SkSLType::kFloat2x2;
 *             case Type::kFloat3x3: return SkSLType::kFloat3x3;
 *             case Type::kFloat4x4: return SkSLType::kFloat4x4;
 *             case Type::kInt:      return SkSLType::kInt;
 *             case Type::kInt2:     return SkSLType::kInt2;
 *             case Type::kInt3:     return SkSLType::kInt3;
 *             case Type::kInt4:     return SkSLType::kInt4;
 *         }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun uniformTypeToSkslType(u: SkRuntimeEffect.Uniform): SkSLType {
  TODO("Implement uniformTypeToSkslType")
}

/**
 * C++ original:
 * ```cpp
 * static bool all_sample_usages_are_passthrough(const SkRuntimeEffect* effect) {
 *     for (size_t i = 0; i < effect->children().size(); ++i) {
 *         if (!SkRuntimeEffectPriv::ChildSampleUsage(effect, i).isPassThrough()) {
 *             return false;
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun allSampleUsagesArePassthrough(effect: SkRuntimeEffect?): Boolean {
  TODO("Implement allSampleUsagesArePassthrough")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_uniform_header(int set, int bufferID) {
 *     std::string result;
 *     SkSL::String::appendf(
 *             &result, "layout (set=%d, binding=%d) uniform CombinedUniforms {\n", set, bufferID);
 *     return result;
 * }
 * ```
 */
public fun getUniformHeader(`set`: Int, bufferID: Int): String {
  TODO("Implement getUniformHeader")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_uniforms(UniformOffsetCalculator* offsetter,
 *                          SkSpan<const Uniform> uniforms,
 *                          int manglingSuffix,
 *                          bool* wrotePaintColor) {
 *     std::string result;
 *     std::string uniformName;
 *     for (const Uniform& u : uniforms) {
 *         uniformName = u.name();
 *
 *         if (u.isPaintColor() && wrotePaintColor) {
 *             if (*wrotePaintColor) {
 *                 SkSL::String::appendf(&result, "    // deduplicated %s\n", u.name());
 *                 continue;
 *             }
 *
 *             *wrotePaintColor = true;
 *         } else {
 *             if (manglingSuffix >= 0) {
 *                 uniformName.append("_");
 *                 uniformName.append(std::to_string(manglingSuffix));
 *             }
 *         }
 *
 *         SkSL::String::appendf(&result,
 *                               "    layout(offset=%d) %s %s",
 *                               offsetter->advanceOffset(u.type(), u.count()),
 *                               SkSLTypeString(u.type()),
 *                               uniformName.c_str());
 *         if (u.count()) {
 *             result.append("[");
 *             result.append(std::to_string(u.count()));
 *             result.append("]");
 *         }
 *         result.append(";\n");
 *     }
 *
 *     return result;
 * }
 * ```
 */
public fun getUniforms(
  offsetter: UniformOffsetCalculator?,
  uniforms: SkSpan<Uniform>,
  manglingSuffix: Int,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement getUniforms")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_node_uniforms(UniformOffsetCalculator* offsetter,
 *                               const ShaderNode* node,
 *                               int* numUniforms,
 *                               int* numUnliftedUniforms,
 *                               bool* wrotePaintColor) {
 *     std::string result;
 *     SkSpan<const Uniform> uniforms = node->entry()->fUniforms;
 *
 *     if (!uniforms.empty()) {
 *         *numUniforms += uniforms.size();
 *         if (!((node->requiredFlags() & SnippetRequirementFlags::kLiftExpression) ||
 *               (node->requiredFlags() & SnippetRequirementFlags::kOmitExpression))) {
 *             *numUnliftedUniforms += uniforms.size();
 *         }
 *
 *         if (node->entry()->fUniformStructName) {
 *             auto substruct = UniformOffsetCalculator::ForStruct(offsetter->layout());
 *             for (const Uniform& u : uniforms) {
 *                 substruct.advanceOffset(u.type(), u.count());
 *             }
 *
 *             const int structOffset = offsetter->advanceStruct(substruct);
 *             SkSL::String::appendf(&result,
 *                                   "layout(offset=%d) %s node_%d;",
 *                                   structOffset,
 *                                   node->entry()->fUniformStructName,
 *                                   node->keyIndex());
 *         } else {
 * #if defined(SK_DEBUG)
 *             SkSL::String::appendf(&result, "// %d - %s uniforms\n",
 *                                   node->keyIndex(), node->entry()->fName);
 * #endif
 *             result += get_uniforms(offsetter, uniforms, node->keyIndex(), wrotePaintColor);
 *         }
 *     }
 *
 *     for (const ShaderNode* child : node->children()) {
 *         result += get_node_uniforms(
 *                 offsetter, child, numUniforms, numUnliftedUniforms, wrotePaintColor);
 *     }
 *     return result;
 * }
 * ```
 */
public fun getNodeUniforms(
  offsetter: UniformOffsetCalculator?,
  node: ShaderNode?,
  numUniforms: Int?,
  numUnliftedUniforms: Int?,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement getNodeUniforms")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_ssbo_fields(SkSpan<const Uniform> uniforms,
 *                             int manglingSuffix,
 *                             bool* wrotePaintColor) {
 *     std::string result;
 *
 *     std::string uniformName;
 *     for (const Uniform& u : uniforms) {
 *         uniformName = u.name();
 *
 *         if (u.isPaintColor() && wrotePaintColor) {
 *             if (*wrotePaintColor) {
 * #if defined(SK_DEBUG)
 *                 SkSL::String::appendf(&result, "    // deduplicated %s\n", u.name());
 * #endif
 *                 continue;
 *             }
 *
 *             *wrotePaintColor = true;
 *         } else {
 *             if (manglingSuffix >= 0) {
 *                 uniformName.append("_");
 *                 uniformName.append(std::to_string(manglingSuffix));
 *             }
 *         }
 *
 *         SkSL::String::appendf(&result, "    %s %s", SkSLTypeString(u.type()), uniformName.c_str());
 *         if (u.count()) {
 *             SkSL::String::appendf(&result, "[%d]", u.count());
 *         }
 *         result.append(";\n");
 *     }
 *
 *     return result;
 * }
 * ```
 */
public fun getSsboFields(
  uniforms: SkSpan<Uniform>,
  manglingSuffix: Int,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement getSsboFields")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_node_ssbo_fields(const ShaderNode* node,
 *                                  int* numUniforms,
 *                                  int* numUnliftedUniforms,
 *                                  bool* wrotePaintColor) {
 *     std::string result;
 *     SkSpan<const Uniform> uniforms = node->entry()->fUniforms;
 *
 *     if (!uniforms.empty()) {
 *         *numUniforms += uniforms.size();
 *         if (!((node->requiredFlags() & SnippetRequirementFlags::kLiftExpression) ||
 *               (node->requiredFlags() & SnippetRequirementFlags::kOmitExpression))) {
 *             *numUnliftedUniforms += uniforms.size();
 *         }
 *
 *         if (node->entry()->fUniformStructName) {
 *             SkSL::String::appendf(&result, "%s node_%d;",
 *                                   node->entry()->fUniformStructName, node->keyIndex());
 *         } else {
 * #if defined(SK_DEBUG)
 *             SkSL::String::appendf(&result, "// %d - %s uniforms\n",
 *                                   node->keyIndex(), node->entry()->fName);
 * #endif
 *             result += get_ssbo_fields(uniforms, node->keyIndex(), wrotePaintColor);
 *         }
 *     }
 *
 *     for (const ShaderNode* child : node->children()) {
 *         result += get_node_ssbo_fields(child, numUniforms, numUnliftedUniforms, wrotePaintColor);
 *     }
 *     return result;
 * }
 * ```
 */
public fun getNodeSsboFields(
  node: ShaderNode?,
  numUniforms: Int?,
  numUnliftedUniforms: Int?,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement getNodeSsboFields")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_intrinsic_constants(const ResourceBindingRequirements& bindingReqs) {
 *     std::string result;
 *     auto offsetter = UniformOffsetCalculator::ForTopLevel(bindingReqs.fUniformBufferLayout);
 *
 *     if (bindingReqs.fUsePushConstantsForIntrinsicConstants) {
 *         SkASSERT(bindingReqs.fBackendApi == BackendApi::kVulkan ||
 *                  bindingReqs.fBackendApi == BackendApi::kDawn);
 *         result = SkSL::String::printf(
 *                 "layout (%s, push_constant) uniform IntrinsicUniforms {\n",
 *                 bindingReqs.fBackendApi == BackendApi::kVulkan ? "vulkan" : "webgpu");
 *     } else {
 *         std::string header;
 *         SkSL::String::appendf(&header,
 *                               "layout (set=%d, binding=%d) uniform IntrinsicUniforms {\n",
 *                               bindingReqs.fUniformsSetIdx,
 *                               bindingReqs.fIntrinsicBufferBinding);
 *         result = std::move(header);
 *     }
 *     result += get_uniforms(&offsetter, kIntrinsicUniforms, -1, /* wrotePaintColor= */ nullptr);
 *     result.append("};\n\n");
 *     SkASSERTF(bindingReqs.fUsePushConstantsForIntrinsicConstants ||
 *               result.find('[') == std::string::npos,
 *               "Arrays are not supported in intrinsic uniforms");
 *     return result;
 * }
 * ```
 */
public fun emitIntrinsicConstants(bindingReqs: ResourceBindingRequirements): String {
  TODO("Implement emitIntrinsicConstants")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_combined_uniforms(int set,
 *                                    int bufferID,
 *                                    const Layout layout,
 *                                    SkSpan<const ShaderNode*> nodes,
 *                                    SkSpan<const Uniform> stepUniforms,
 *                                    int* numPaintUniforms,
 *                                    int* numUnliftedPaintUniforms,
 *                                    bool* wrotePaintColor) {
 *     auto offsetter = UniformOffsetCalculator::ForTopLevel(layout);
 *
 *     std::string result = get_uniform_header(set, bufferID);
 *     for (const ShaderNode* n : nodes) {
 *         result += get_node_uniforms(
 *                 &offsetter, n, numPaintUniforms, numUnliftedPaintUniforms, wrotePaintColor);
 *     }
 *
 *     // Paint and RenderStep uniforms share a binding. When RenderSteps are processed in DrawList,
 *     // the paint uniforms are always processed before the render step ones, so the emitted uniforms
 *     // must respect that ordering.
 *     if (!stepUniforms.empty()) {
 *         result += get_uniforms(&offsetter, stepUniforms, -1, /* wrotePaintColor= */ nullptr);
 *     }
 *
 *     result.append("};\n\n");
 *
 *     if (*numPaintUniforms == 0 && stepUniforms.empty()) {
 *         // No uniforms were added
 *         return {};
 *     }
 *
 *     return result;
 * }
 * ```
 */
public fun emitCombinedUniforms(
  `set`: Int,
  bufferID: Int,
  layout: Layout,
  nodes: SkSpan<ShaderNode?>,
  stepUniforms: SkSpan<Uniform>,
  numPaintUniforms: Int?,
  numUnliftedPaintUniforms: Int?,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement emitCombinedUniforms")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_combined_storage_buffer(int set,
 *                                          int bufferID,
 *                                          SkSpan<const ShaderNode*> nodes,
 *                                          SkSpan<const Uniform> stepUniforms,
 *                                          int* numPaintUniforms,
 *                                          int* numUnliftedPaintUniforms,
 *                                          bool* wrotePaintColor) {
 *     std::string fields;
 *     for (const ShaderNode* n : nodes) {
 *         fields += get_node_ssbo_fields(
 *                 n, numPaintUniforms, numUnliftedPaintUniforms, wrotePaintColor);
 *     }
 *
 *     if (!stepUniforms.empty()) {
 *         fields += get_ssbo_fields(stepUniforms, -1, /*wrotePaintColor=*/nullptr);
 *     }
 *
 *     if (*numPaintUniforms == 0 && stepUniforms.empty()) {
 *         // No uniforms were added
 *         return {};
 *     }
 *
 *     return SkSL::String::printf(
 *             "struct CombinedUniformData {\n"
 *             "    %s\n"
 *             "};\n\n"
 *             "layout (set=%d, binding=%d) readonly buffer CombinedUniforms {\n"
 *             "    CombinedUniformData combinedUniformData[];\n"
 *             "};\n",
 *             fields.c_str(),
 *             set,
 *             bufferID);
 * }
 * ```
 */
public fun emitCombinedStorageBuffer(
  `set`: Int,
  bufferID: Int,
  nodes: SkSpan<ShaderNode?>,
  stepUniforms: SkSpan<Uniform>,
  numPaintUniforms: Int?,
  numUnliftedPaintUniforms: Int?,
  wrotePaintColor: Boolean?,
): String {
  TODO("Implement emitCombinedStorageBuffer")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_uniforms_from_storage_buffer(const char* indexVariableName,
 *                                               SkSpan<const Uniform> uniforms) {
 *     std::string result;
 *
 *     for (const Uniform& u : uniforms) {
 *         SkSL::String::appendf(&result, "%s %s", SkSLTypeString(u.type()), u.name());
 *         if (u.count()) {
 *             SkSL::String::appendf(&result, "[%d]", u.count());
 *         }
 *         SkSL::String::appendf(&result,
 *                               " = combinedUniformData[%s].%s;\n",
 *                               indexVariableName,
 *                               u.name());
 *     }
 *
 *     return result;
 * }
 * ```
 */
public fun emitUniformsFromStorageBuffer(indexVariableName: String?, uniforms: SkSpan<Uniform>): String {
  TODO("Implement emitUniformsFromStorageBuffer")
}

/**
 * C++ original:
 * ```cpp
 * void append_sampler_descs(const SkSpan<const uint32_t> samplerData,
 *                           skia_private::TArray<SamplerDesc>& outDescs) {
 *     // Sampler data consists of variable-length SamplerDesc representations which can differ based
 *     // upon a sampler's immutability and format. For this reason, handle incrementing i in the loop.
 *     for (size_t i = 0; i < samplerData.size();) {
 *         // Create a default-initialized SamplerDesc (which only takes up one uint32). If we are
 *         // using a dynamic sampler, this will be directly inserted into outDescs. Otherwise, it will
 *         // be populated with actual immutable sampler data and then inserted.
 *         SamplerDesc desc{};
 *         size_t samplerDescLength = 1;
 *         SkASSERT(desc.asSpan().size() == samplerDescLength);
 *
 *         // Isolate the ImmutableSamplerInfo portion of the SamplerDesc represented by samplerData.
 *         // If immutableSamplerInfo is non-zero, that means we are using an immutable sampler.
 *         uint32_t immutableSamplerInfo = samplerData[i] >> SamplerDesc::kImmutableSamplerInfoShift;
 *         if (immutableSamplerInfo != 0) {
 *             // Consult the first bit of immutableSamplerInfo which tells us whether the sampler uses
 *             // a known or external format. With this, update sampler description length.
 *             bool usesExternalFormat = immutableSamplerInfo & 0b1;
 *             samplerDescLength = usesExternalFormat ? SamplerDesc::kInt32sNeededExternalFormat
 *                                                    : SamplerDesc::kInt32sNeededKnownFormat;
 *             // Populate a SamplerDesc with samplerDescLength quantity of immutable sampler data
 *             memcpy(&desc, samplerData.data() + i, samplerDescLength * sizeof(uint32_t));
 *         }
 *         outDescs.push_back(desc);
 *         i += samplerDescLength;
 *     }
 * }
 * ```
 */
public fun appendSamplerDescs(samplerData: SkSpan<UInt>, outDescs: TArray<SamplerDesc>) {
  TODO("Implement appendSamplerDescs")
}

/**
 * C++ original:
 * ```cpp
 * std::string get_node_texture_samplers(const ResourceBindingRequirements& bindingReqs,
 *                                       const ShaderNode* node,
 *                                       int* binding,
 *                                       skia_private::TArray<SamplerDesc>* outDescs) {
 *     std::string result;
 *     SkSpan<const TextureAndSampler> samplers = node->entry()->fTexturesAndSamplers;
 *
 *     if (!samplers.empty()) {
 * #if defined(SK_DEBUG)
 *         SkSL::String::appendf(&result, "// %d - %s samplers\n",
 *                               node->keyIndex(), node->entry()->fName);
 * #endif
 *
 *         // Determine whether we need to analyze & interpret a ShaderNode's data as immutable
 *         // SamplerDescs based upon whether:
 *         // 1) A backend passes in a non-nullptr outImmutableSamplers param (may be nullptr in
 *         //    backends or circumstances where we know immutable sampler data is never stored)
 *         // 2) Any data is stored on the ShaderNode
 *         // 3) Whether the ShaderNode snippet's ID matches that of any snippet ID that could store
 *         //    immutable sampler data.
 *         int32_t snippetId = node->codeSnippetId();
 *         if (outDescs) {
 *             // TODO(b/369846881): Refactor checking snippet ID to instead having a named
 *             // snippet requirement flag that we can check here to decrease fragility.
 *             if (!node->data().empty() &&
 *                 (snippetId == static_cast<int32_t>(BuiltInCodeSnippetID::kImageShader) ||
 *                  snippetId == static_cast<int32_t>(BuiltInCodeSnippetID::kImageShaderClamp) ||
 *                  snippetId == static_cast<int32_t>(BuiltInCodeSnippetID::kCubicImageShader) ||
 *                  snippetId == static_cast<int32_t>(BuiltInCodeSnippetID::kHWImageShader))) {
 *                 append_sampler_descs(node->data(), *outDescs);
 *             } else {
 *                 // Add default SamplerDescs for any dynamic samplers to outDescs.
 *                 outDescs->push_back_n(samplers.size());
 *             }
 *         }
 *
 *         for (const TextureAndSampler& t : samplers) {
 *             result += EmitSamplerLayout(bindingReqs, binding);
 *             SkSL::String::appendf(&result, " sampler2D %s_%d;\n", t.name(), node->keyIndex());
 *         }
 *     }
 *
 *     for (const ShaderNode* child : node->children()) {
 *         result += get_node_texture_samplers(bindingReqs, child, binding, outDescs);
 *     }
 *     return result;
 * }
 * ```
 */
public fun getNodeTextureSamplers(
  bindingReqs: ResourceBindingRequirements,
  node: ShaderNode?,
  binding: Int?,
  outDescs: TArray<SamplerDesc>?,
): String {
  TODO("Implement getNodeTextureSamplers")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_textures_and_samplers(const ResourceBindingRequirements& bindingReqs,
 *                                        SkSpan<const ShaderNode*> nodes,
 *                                        int* binding,
 *                                        skia_private::TArray<SamplerDesc>* outDescs) {
 *     std::string result;
 *     for (const ShaderNode* n : nodes) {
 *         result += get_node_texture_samplers(bindingReqs, n, binding, outDescs);
 *     }
 *     return result;
 * }
 * ```
 */
public fun emitTexturesAndSamplers(
  bindingReqs: ResourceBindingRequirements,
  nodes: SkSpan<ShaderNode?>,
  binding: Int?,
  outDescs: TArray<SamplerDesc>?,
): String {
  TODO("Implement emitTexturesAndSamplers")
}

/**
 * C++ original:
 * ```cpp
 * SkSLType sksl_type_for_lifted_expression(
 *         ShaderSnippet::LiftableExpressionType liftedExpressionType) {
 *     switch (liftedExpressionType) {
 *         case ShaderSnippet::LiftableExpressionType::kNone:
 *             return SkSLType::kVoid;
 *         case ShaderSnippet::LiftableExpressionType::kLocalCoords:
 *             return SkSLType::kFloat2;
 *         case ShaderSnippet::LiftableExpressionType::kPriorStageOutput:
 *             return SkSLType::kHalf4;
 *     }
 *     return SkSLType::kVoid;
 * }
 * ```
 */
public fun skslTypeForLiftedExpression(liftedExpressionType: ShaderSnippet.LiftableExpressionType): SkSLType {
  TODO("Implement skslTypeForLiftedExpression")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_varyings(const RenderStep* step,
 *                           const char* direction,
 *                           SkSpan<const LiftedExpression> liftedExpressions,
 *                           bool emitSsboIndexVarying,
 *                           bool emitLocalCoordsVarying) {
 *     std::string result;
 *     int location = 0;
 *
 *     auto appendVarying = [&](const Varying& v) {
 *         const char* interpolation;
 *         switch (v.interpolation()) {
 *             case Interpolation::kPerspective: interpolation = ""; break;
 *             case Interpolation::kLinear:      interpolation = "noperspective "; break;
 *             case Interpolation::kFlat:        interpolation = "flat "; break;
 *         }
 *         SkSL::String::appendf(&result, "layout(location=%d) %s %s%s %s;\n",
 *                               location++,
 *                               direction,
 *                               interpolation,
 *                               SkSLTypeString(v.gpuType()),
 *                               v.name());
 *     };
 *
 *     if (emitSsboIndexVarying) {
 *         appendVarying({RenderStep::ssboIndexVarying(), SkSLType::kUInt});
 *     }
 *
 *     if (emitLocalCoordsVarying) {
 *         appendVarying({"localCoordsVar", SkSLType::kFloat2});
 *     }
 *
 *     for (const LiftedExpression& expr : liftedExpressions) {
 *         if (expr.fEmitVarying) {
 *             const ShaderNode* node = expr.fNode;
 *             const std::string name = node->getExpressionVaryingName();
 *             appendVarying({name.c_str(),
 *                            sksl_type_for_lifted_expression(node->entry()->fLiftableExpressionType),
 *                            node->entry()->fLiftableExpressionInterpolation});
 *         }
 *     }
 *
 *     for (auto v : step->varyings()) {
 *         appendVarying(v);
 *     }
 *
 *     return result;
 * }
 * ```
 */
public fun emitVaryings(
  step: RenderStep?,
  direction: String?,
  liftedExpressions: SkSpan<LiftedExpression>,
  emitSsboIndexVarying: Boolean,
  emitLocalCoordsVarying: Boolean,
): String {
  TODO("Implement emitVaryings")
}

/**
 * C++ original:
 * ```cpp
 * void emit_preambles(const ShaderInfo& shaderInfo,
 *                     SkSpan<const ShaderNode*> nodes,
 *                     std::string treeLabel,
 *                     std::string* preamble) {
 *     for (int i = 0; i < SkTo<int>(nodes.size()); ++i) {
 *         const ShaderNode* node = nodes[i];
 *         std::string nodeLabel = std::to_string(i);
 *         std::string nextLabel = treeLabel.empty() ? nodeLabel : (treeLabel + "<-" + nodeLabel);
 *
 *         if (node->numChildren() > 0) {
 *             emit_preambles(shaderInfo, node->children(), nextLabel, preamble);
 *         }
 *
 *         std::string nodePreamble = node->entry()->fPreambleGenerator
 *                                            ? node->entry()->fPreambleGenerator(shaderInfo, node)
 *                                            : node->generateDefaultPreamble(shaderInfo);
 *         if (!nodePreamble.empty()) {
 *             SkSL::String::appendf(preamble,
 *                                   "// [%d]   %s: %s\n"
 *                                   "%s\n",
 *                                   node->keyIndex(),
 *                                   nextLabel.c_str(),
 *                                   node->entry()->fName,
 *                                   nodePreamble.c_str());
 *         }
 *     }
 * }
 * ```
 */
public fun emitPreambles(
  shaderInfo: ShaderInfo,
  nodes: SkSpan<ShaderNode?>,
  treeLabel: String,
  preamble: String?,
) {
  TODO("Implement emitPreambles")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_color_output(BlendFormula::OutputType outputType,
 *                               const char* outColor,
 *                               const char* inColor) {
 *     switch (outputType) {
 *         case BlendFormula::kNone_OutputType:
 *             return SkSL::String::printf("%s = half4(0.0);", outColor);
 *
 *         case BlendFormula::kCoverage_OutputType:
 *             return SkSL::String::printf("%s = outputCoverage;", outColor);
 *
 *         case BlendFormula::kModulate_OutputType:
 *             return SkSL::String::printf("%s = %s * outputCoverage;", outColor, inColor);
 *
 *         case BlendFormula::kSAModulate_OutputType:
 *             return SkSL::String::printf("%s = %s.a * outputCoverage;", outColor, inColor);
 *
 *         case BlendFormula::kISAModulate_OutputType:
 *             return SkSL::String::printf("%s = (1.0 - %s.a) * outputCoverage;", outColor, inColor);
 *
 *         case BlendFormula::kISCModulate_OutputType:
 *             return SkSL::String::printf(
 *                     "%s = (half4(1.0) - %s) * outputCoverage;", outColor, inColor);
 *
 *         default:
 *             SkUNREACHABLE;
 *     }
 * }
 * ```
 */
public fun emitColorOutput(
  outputType: BlendFormula.OutputType,
  outColor: String?,
  inColor: String?,
): String {
  TODO("Implement emitColorOutput")
}

/**
 * C++ original:
 * ```cpp
 * std::string emit_advanced_blend_color_output(const char* outColor, const char* inColor) {
 *     /*
 *       When using hardware for advanced blend modes, we apply coverage for advanced blend modes by
 *       multiplying it into the src color before blending. This will "just work" given the following:
 *
 *       The general SVG blend equation is defined in the spec as follows:
 *
 *         Dca' = B(Sc, Dc) * Sa * Da + Y * Sca * (1-Da) + Z * Dca * (1-Sa)
 *         Da'  = X * Sa * Da + Y * Sa * (1-Da) + Z * Da * (1-Sa)
 *
 *       (Note that Sca, Dca indicate RGB vectors that are premultiplied by alpha,
 *        and that B(Sc, Dc) is a mode-specific function that accepts non-multiplied
 *        RGB colors.)
 *
 *       For every blend mode supported by this class, i.e. the "advanced" blend
 *       modes, X=Y=Z=1 and this equation reduces to the PDF blend equation.
 *
 *       It can be shown that when X=Y=Z=1, these equations can modulate alpha for
 *       coverage.
 *
 *
 *       == Color ==
 *
 *       We substitute Y=Z=1 and define a blend() function that calculates Dca' in
 *       terms of premultiplied alpha only:
 *
 *         blend(Sca, Dca, Sa, Da) = {
 *                 Dca : if Sa == 0,
 *                 Sca : if Da == 0,
 *                 B(Sca/Sa, Dca/Da) * Sa * Da + Sca * (1-Da) + Dca * (1-Sa) : if Sa,Da != 0}
 *
 *       And for coverage modulation, we use a post blend src-over model:
 *
 *         Dca'' = f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *
 *       (Where f is the fractional coverage.)
 *
 *       Next we show that we can multiply coverage into the src color by proving the
 *       following relationship:
 *
 *         blend(f*Sca, Dca, f*Sa, Da) == f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *
 *       General case (f,Sa,Da != 0):
 *
 *         f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *           = f * (B(Sca/Sa, Dca/Da) * Sa * Da + Sca * (1-Da) + Dca * (1-Sa)) + (1-f) * Dca
 *             [Sa,Da != 0, definition of blend()]
 *           = B(Sca/Sa, Dca/Da) * f*Sa * Da + f*Sca * (1-Da) + f*Dca * (1-Sa) + Dca - f*Dca
 *           = B(Sca/Sa, Dca/Da) * f*Sa * Da + f*Sca - f*Sca * Da + f*Dca - f*Dca * Sa + Dca - f*Dca
 *           = B(Sca/Sa, Dca/Da) * f*Sa * Da + f*Sca - f*Sca * Da - f*Dca * Sa + Dca
 *           = B(Sca/Sa, Dca/Da) * f*Sa * Da + f*Sca * (1-Da) - f*Dca * Sa + Dca
 *           = B(Sca/Sa, Dca/Da) * f*Sa * Da + f*Sca * (1-Da) + Dca * (1 - f*Sa)
 *           = B(f*Sca/f*Sa, Dca/Da) * f*Sa * Da + f*Sca * (1-Da) + Dca * (1 - f*Sa)  [f!=0]
 *           = blend(f*Sca, Dca, f*Sa, Da)  [definition of blend()]
 *
 *       Corner cases (Sa=0, Da=0, and f=0):
 *
 *         Sa=0: f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *                 = f * Dca + (1-f) * Dca  [Sa=0, definition of blend()]
 *                 = Dca
 *                 = blend(0, Dca, 0, Da)  [definition of blend()]
 *                 = blend(f*Sca, Dca, f*Sa, Da)  [Sa=0]
 *
 *         Da=0: f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *                 = f * Sca + (1-f) * Dca  [Da=0, definition of blend()]
 *                 = f * Sca  [Da=0]
 *                 = blend(f*Sca, 0, f*Sa, 0)  [definition of blend()]
 *                 = blend(f*Sca, Dca, f*Sa, Da)  [Da=0]
 *
 *         f=0: f * blend(Sca, Dca, Sa, Da) + (1-f) * Dca
 *                = Dca  [f=0]
 *                = blend(0, Dca, 0, Da)  [definition of blend()]
 *                = blend(f*Sca, Dca, f*Sa, Da)  [f=0]
 *
 *       == Alpha ==
 *
 *       We substitute X=Y=Z=1 and define a blend() function that calculates Da':
 *
 *         blend(Sa, Da) = Sa * Da + Sa * (1-Da) + Da * (1-Sa)
 *                       = Sa * Da + Sa - Sa * Da + Da - Da * Sa
 *                       = Sa + Da - Sa * Da
 *
 *       We use the same model for coverage modulation as we did with color:
 *
 *         Da'' = f * blend(Sa, Da) + (1-f) * Da
 *
 *       And show that show that we can multiply coverage into src alpha by proving the following
 *       relationship:
 *
 *         blend(f*Sa, Da) == f * blend(Sa, Da) + (1-f) * Da
 *
 *         f * blend(Sa, Da) + (1-f) * Da
 *           = f * (Sa + Da - Sa * Da) + (1-f) * Da
 *           = f*Sa + f*Da - f*Sa * Da + Da - f*Da
 *           = f*Sa - f*Sa * Da + Da
 *           = f*Sa + Da - f*Sa * Da
 *           = blend(f*Sa, Da)
 *     */
 *    return emit_color_output(BlendFormula::OutputType::kModulate_OutputType, outColor, inColor);
 * }
 * ```
 */
public fun emitAdvancedBlendColorOutput(outColor: String?, inColor: String?): String {
  TODO("Implement emitAdvancedBlendColorOutput")
}

/**
 * C++ original:
 * ```cpp
 * std::vector<LiftedExpression> collect_lifted_expressions(SkSpan<const ShaderNode*> nodes) {
 *     std::vector<LiftedExpression> lifted;
 *     ShaderSnippet::Args args = ShaderSnippet::kDefaultArgs;
 *     args.fFragCoord = "stepLocalCoords";  // Render Steps' stepLocalCoords
 *     collect_lifted_expressions(nodes, args, lifted);
 *     return lifted;
 * }
 * ```
 */
public fun collectLiftedExpressions(nodes: SkSpan<ShaderNode?>): List<LiftedExpression> {
  TODO("Implement collectLiftedExpressions")
}

/**
 * C++ original:
 * ```cpp
 * std::string dst_read_strategy_to_str(DstReadStrategy strategy) {
 *     switch (strategy) {
 *         case DstReadStrategy::kNoneRequired:
 *             return "NoneRequired";
 *         case DstReadStrategy::kTextureCopy:
 *             return "TextureCopy";
 *         case DstReadStrategy::kTextureSample:
 *             return "TextureSample";
 *         case DstReadStrategy::kReadFromInput:
 *             return "ReadFromInput";
 *         case DstReadStrategy::kFramebufferFetch:
 *             return "FramebufferFetch";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun dstReadStrategyToStr(strategy: DstReadStrategy): String {
  TODO("Implement dstReadStrategyToStr")
}

/**
 * C++ original:
 * ```cpp
 * constexpr skgpu::BlendInfo make_simple_blendInfo(skgpu::BlendCoeff srcCoeff,
 *                                                  skgpu::BlendCoeff dstCoeff) {
 *     return { skgpu::BlendEquation::kAdd,
 *              srcCoeff,
 *              dstCoeff,
 *              SK_PMColor4fTRANSPARENT,
 *              skgpu::BlendModifiesDst(skgpu::BlendEquation::kAdd, srcCoeff, dstCoeff) };
 * }
 * ```
 */
public fun makeSimpleBlendInfo(srcCoeff: BlendCoeff, dstCoeff: BlendCoeff): BlendInfo {
  TODO("Implement makeSimpleBlendInfo")
}

/**
 * C++ original:
 * ```cpp
 * constexpr skgpu::BlendEquation get_advanced_blend_equation(SkBlendMode mode) {
 *     SkASSERT(mode > SkBlendMode::kLastCoeffMode);
 *
 *     constexpr int kEqOffset = ((int)skgpu::BlendEquation::kOverlay - (int)SkBlendMode::kOverlay);
 *     static_assert((int)skgpu::BlendEquation::kOverlay ==
 *                   (int)SkBlendMode::kOverlay + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kDarken ==
 *                   (int)SkBlendMode::kDarken + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kLighten ==
 *                   (int)SkBlendMode::kLighten + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kColorDodge ==
 *                   (int)SkBlendMode::kColorDodge + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kColorBurn ==
 *                   (int)SkBlendMode::kColorBurn + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kHardLight ==
 *                   (int)SkBlendMode::kHardLight + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kSoftLight ==
 *                   (int)SkBlendMode::kSoftLight + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kDifference ==
 *                   (int)SkBlendMode::kDifference + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kExclusion ==
 *                   (int)SkBlendMode::kExclusion + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kMultiply ==
 *                   (int)SkBlendMode::kMultiply + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kHSLHue ==
 *                   (int)SkBlendMode::kHue + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kHSLSaturation ==
 *                   (int)SkBlendMode::kSaturation + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kHSLColor ==
 *                   (int)SkBlendMode::kColor + kEqOffset);
 *     static_assert((int)skgpu::BlendEquation::kHSLLuminosity ==
 *                   (int)SkBlendMode::kLuminosity + kEqOffset);
 *     // There's an illegal BlendEquation that corresponds to no SkBlendMode, hence the extra +1.
 *     static_assert(skgpu::kBlendEquationCnt == (int)SkBlendMode::kLastMode + 1 + 1 + kEqOffset);
 *
 *     return static_cast<skgpu::BlendEquation>((int)mode + kEqOffset);
 * }
 * ```
 */
public fun getAdvancedBlendEquation(mode: SkBlendMode): BlendEquation {
  TODO("Implement getAdvancedBlendEquation")
}

/**
 * C++ original:
 * ```cpp
 * constexpr skgpu::BlendInfo make_hardware_advanced_blendInfo(SkBlendMode advancedBlendMode) {
 *     BlendInfo blendInfo;
 *     blendInfo.fEquation = get_advanced_blend_equation(advancedBlendMode);
 *     return blendInfo;
 * }
 * ```
 */
public fun makeHardwareAdvancedBlendInfo(advancedBlendMode: SkBlendMode): BlendInfo {
  TODO("Implement makeHardwareAdvancedBlendInfo")
}

/**
 * C++ original:
 * ```cpp
 * static Layout get_binding_layout(const Caps* caps) {
 *     ResourceBindingRequirements reqs = caps->resourceBindingRequirements();
 *     return caps->storageBufferSupport() ? reqs.fStorageBufferLayout : reqs.fUniformBufferLayout;
 * }
 * ```
 */
public fun getBindingLayout(caps: Caps?): Layout {
  TODO("Implement getBindingLayout")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSpecialImage> MakeGraphite(skgpu::graphite::Recorder* recorder,
 *                                    const SkIRect& subset,
 *                                    sk_sp<SkImage> image,
 *                                    const SkSurfaceProps& props) {
 *     // 'recorder' can be null if we're wrapping a graphite-backed image since there's no work that
 *     // needs to be added. This can happen when snapping a special image from a Device that's been
 *     // marked as immutable and abandoned its recorder.
 *     if (!image || subset.isEmpty()) {
 *         return nullptr;
 *     }
 *
 *     SkASSERT(image->bounds().contains(subset));
 *
 *     // Use the Recorder's client ImageProvider to convert to a graphite-backed image when
 *     // possible, but this does not necessarily mean the provider will produce a valid image.
 *     if (!as_IB(image)->isGraphiteBacked()) {
 *         if (!recorder) {
 *             return nullptr;
 *         }
 *         auto [graphiteImage, _] =
 *                 skgpu::graphite::GetGraphiteBacked(recorder, image.get(), {});
 *         if (!graphiteImage) {
 *             return nullptr;
 *         }
 *
 *         image = graphiteImage;
 *     }
 *
 *     return sk_make_sp<skgpu::graphite::SpecialImage>(subset, std::move(image), props);
 * }
 * ```
 */
public fun makeGraphite(
  recorder: Recorder?,
  subset: SkIRect,
  image: SkSp<SkImage>,
  props: SkSurfaceProps,
): SkSp<SkSpecialImage> {
  TODO("Implement makeGraphite")
}

/**
 * C++ original:
 * ```cpp
 * void Flush(SkSurface* surface) {
 *     if (!surface) {
 *         return;
 *     }
 *     auto sb = asSB(surface);
 *     if (!sb->isGraphiteBacked()) {
 *         return;
 *     }
 *     auto gs = static_cast<Surface*>(surface);
 *     gs->fDevice->flushPendingWork(/*drawContext=*/nullptr);
 * }
 * ```
 */
public fun flush(surface: SkSurface?) {
  TODO("Implement flush")
}

/**
 * C++ original:
 * ```cpp
 * bool validate_backend_texture(const Caps* caps,
 *                               const BackendTexture& texture,
 *                               const SkColorInfo& info) {
 *     if (!texture.isValid() ||
 *         texture.dimensions().width() <= 0 ||
 *         texture.dimensions().height() <= 0) {
 *         return false;
 *     }
 *
 *     if (!SkColorInfoIsValid(info)) {
 *         return false;
 *     }
 *
 *     if (!caps->isRenderable(texture.info())) {
 *         return false;
 *     }
 *
 *     return caps->areColorTypeAndTextureInfoCompatible(info.colorType(), texture.info());
 * }
 * ```
 */
public fun validateBackendTexture(
  caps: Caps?,
  texture: BackendTexture,
  info: SkColorInfo,
): Boolean {
  TODO("Implement validateBackendTexture")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> AsImage(sk_sp<const SkSurface> surface) {
 *     if (!surface) {
 *         return nullptr;
 *     }
 *     auto sb = asConstSB(surface.get());
 *     if (!sb->isGraphiteBacked()) {
 *         return nullptr;
 *     }
 *     auto gs = static_cast<const Surface*>(surface.get());
 *     return gs->asImage();
 * }
 * ```
 */
public fun asImage(surface: SkSp<SkSurface>): SkSp<SkImage> {
  TODO("Implement asImage")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> AsImageCopy(sk_sp<const SkSurface> surface,
 *                            const SkIRect* subset,
 *                            skgpu::Mipmapped mipmapped) {
 *     if (!surface) {
 *         return nullptr;
 *     }
 *     auto sb = asConstSB(surface.get());
 *     if (!sb->isGraphiteBacked()) {
 *         return nullptr;
 *     }
 *     auto gs = static_cast<const Surface*>(surface.get());
 *     return gs->makeImageCopy(subset, mipmapped);
 * }
 * ```
 */
public fun asImageCopy(
  surface: SkSp<SkSurface>,
  subset: SkIRect?,
  mipmapped: Mipmapped,
): SkSp<SkImage> {
  TODO("Implement asImageCopy")
}

/**
 * C++ original:
 * ```cpp
 * const char* TextureFormatName(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kUnsupported:    return "Unsupported";
 *         case TextureFormat::kR8:             return "R8";
 *         case TextureFormat::kR16:            return "R16";
 *         case TextureFormat::kR16F:           return "R16F";
 *         case TextureFormat::kR32F:           return "R32F";
 *         case TextureFormat::kA8:             return "A8";
 *         case TextureFormat::kRG8:            return "RG8";
 *         case TextureFormat::kRG16:           return "RG16";
 *         case TextureFormat::kRG16F:          return "RG16F";
 *         case TextureFormat::kRG32F:          return "RG32F";
 *         case TextureFormat::kRGB8:           return "RGB8";
 *         case TextureFormat::kBGR8:           return "BGR8";
 *         case TextureFormat::kB5_G6_R5:       return "B5_G6_R5";
 *         case TextureFormat::kR5_G6_B5:       return "R5_G6_B5";
 *         case TextureFormat::kRGB16:          return "RGB16";
 *         case TextureFormat::kRGB16F:         return "RGB16F";
 *         case TextureFormat::kRGB32F:         return "RGB32F";
 *         case TextureFormat::kRGB8_sRGB:      return "RGB8_sRGB";
 *         case TextureFormat::kBGR10_XR:       return "BGR10_XR";
 *         case TextureFormat::kRGBA8:          return "RGBA8";
 *         case TextureFormat::kRGBA16:         return "RBGA16";
 *         case TextureFormat::kRGBA16F:        return "RGBA16F";
 *         case TextureFormat::kRGBA32F:        return "RGBA32F";
 *         case TextureFormat::kRGB10_A2:       return "RGB10_A2";
 *         case TextureFormat::kRGBA10x6:       return "RGBA10x6";
 *         case TextureFormat::kRGBA8_sRGB:     return "RGBA8_sRGB";
 *         case TextureFormat::kBGRA8:          return "BGRA8";
 *         case TextureFormat::kBGR10_A2:       return "BGR10_A2";
 *         case TextureFormat::kBGRA8_sRGB:     return "BGRA8_sRGB";
 *         case TextureFormat::kABGR4:          return "ABGR4";
 *         case TextureFormat::kARGB4:          return "ARGB4";
 *         case TextureFormat::kBGRA10x6_XR:    return "BGRA10x6_XR";
 *         case TextureFormat::kRGB8_ETC2:      return "RGB8_ETC2";
 *         case TextureFormat::kRGB8_ETC2_sRGB: return "RGB8_ETC2_sRGB";
 *         case TextureFormat::kRGB8_BC1:       return "RGB8_BC1";
 *         case TextureFormat::kRGBA8_BC1:      return "RGBA8_BC1";
 *         case TextureFormat::kRGBA8_BC1_sRGB: return "RGBA8_BC1_sRGB";
 *         case TextureFormat::kYUV8_P2_420:    return "YUV8_P2_420";
 *         case TextureFormat::kYUV8_P3_420:    return "YUV8_P3_420";
 *         case TextureFormat::kYUV10x6_P2_420: return "YUV10x6_P2_420";
 *         case TextureFormat::kExternal:       return "External";
 *         case TextureFormat::kS8:             return "S8";
 *         case TextureFormat::kD16:            return "D16";
 *         case TextureFormat::kD32F:           return "D32F";
 *         case TextureFormat::kD24_S8:         return "D24_S8";
 *         case TextureFormat::kD32F_S8:        return "D32F_S8";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun textureFormatName(format: TextureFormat): Char {
  TODO("Implement textureFormatName")
}

/**
 * C++ original:
 * ```cpp
 * SkTextureCompressionType TextureFormatCompressionType(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kRGB8_ETC2:      [[fallthrough]];
 *         case TextureFormat::kRGB8_ETC2_sRGB: return SkTextureCompressionType::kETC2_RGB8_UNORM;
 *         case TextureFormat::kRGB8_BC1:       return SkTextureCompressionType::kBC1_RGB8_UNORM;
 *         case TextureFormat::kRGBA8_BC1:      [[fallthrough]];
 *         case TextureFormat::kRGBA8_BC1_sRGB: return SkTextureCompressionType::kBC1_RGBA8_UNORM;
 *         default:                             return SkTextureCompressionType::kNone;
 *     }
 * }
 * ```
 */
public fun textureFormatCompressionType(format: TextureFormat): SkTextureCompressionType {
  TODO("Implement textureFormatCompressionType")
}

/**
 * C++ original:
 * ```cpp
 * size_t TextureFormatBytesPerBlock(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kUnsupported: return 0;
 *         case TextureFormat::kR8:          return 1;
 *         case TextureFormat::kR16:         return 2;
 *         case TextureFormat::kR16F:        return 2;
 *         case TextureFormat::kR32F:        return 4;
 *         case TextureFormat::kA8:          return 1;
 *         case TextureFormat::kRG8:         return 2;
 *         case TextureFormat::kRG16:        return 4;
 *         case TextureFormat::kRG16F:       return 4;
 *         case TextureFormat::kRG32F:       return 8;
 *         case TextureFormat::kRGB8:        return 3;
 *         case TextureFormat::kBGR8:        return 3;
 *         case TextureFormat::kB5_G6_R5:    return 2;
 *         case TextureFormat::kR5_G6_B5:    return 2;
 *         case TextureFormat::kRGB16:       return 6;
 *         case TextureFormat::kRGB16F:      return 6;
 *         case TextureFormat::kRGB32F:      return 12;
 *         case TextureFormat::kRGB8_sRGB:   return 3;
 *         case TextureFormat::kBGR10_XR:    return 4;
 *         case TextureFormat::kRGBA8:       return 4;
 *         case TextureFormat::kRGBA16:      return 8;
 *         case TextureFormat::kRGBA16F:     return 8;
 *         case TextureFormat::kRGBA32F:     return 16;
 *         case TextureFormat::kRGB10_A2:    return 4;
 *         case TextureFormat::kRGBA10x6:    return 8;
 *         case TextureFormat::kRGBA8_sRGB:  return 4;
 *         case TextureFormat::kBGRA8:       return 4;
 *         case TextureFormat::kBGR10_A2:    return 4;
 *         case TextureFormat::kBGRA8_sRGB:  return 4;
 *         case TextureFormat::kABGR4:       return 2;
 *         case TextureFormat::kARGB4:       return 2;
 *         case TextureFormat::kBGRA10x6_XR: return 8;
 *         case TextureFormat::kS8:          return 1;
 *         case TextureFormat::kD16:         return 2;
 *         case TextureFormat::kD32F:        return 4;
 *         case TextureFormat::kD24_S8:      return 4;
 *         case TextureFormat::kD32F_S8:     return 8;
 *         // NOTE: For compressed formats, the block size refers to an actual compressed block of
 *         // multiple texels, whereas with other formats the block size represents a single pixel.
 *         case TextureFormat::kRGB8_ETC2:
 *         case TextureFormat::kRGB8_ETC2_sRGB:
 *         case TextureFormat::kRGB8_BC1:
 *         case TextureFormat::kRGBA8_BC1:
 *         case TextureFormat::kRGBA8_BC1_sRGB:
 *             return 8;
 *         // NOTE: We don't actually know the size of external formats, so this is an arbitrary value.
 *         // We will see external formats only in wrapped SkImages, so this won't impact Skia's
 *         // internal budgeting.
 *         case TextureFormat::kExternal:
 *             return 4;
 *         // TODO(b/401016699): We are just over estimating this value to be used in gpu size
 *         // calculations even though the actually size is probably less. We should instead treat
 *         // planar formats similar to compressed textures that go through their own special query for
 *         // calculating size.
 *         case TextureFormat::kYUV8_P2_420:
 *         case TextureFormat::kYUV8_P3_420:
 *             return 3;
 *         case TextureFormat::kYUV10x6_P2_420:
 *             return 6;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun textureFormatBytesPerBlock(format: TextureFormat): ULong {
  TODO("Implement textureFormatBytesPerBlock")
}

/**
 * C++ original:
 * ```cpp
 * uint32_t TextureFormatChannelMask(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kA8:             return kAlpha_SkColorChannelFlag;
 *
 *         case TextureFormat::kR8:             [[fallthrough]];
 *         case TextureFormat::kR16:
 *         case TextureFormat::kR16F:
 *         case TextureFormat::kR32F:           return kRed_SkColorChannelFlag;
 *
 *         case TextureFormat::kRG8:            [[fallthrough]];
 *         case TextureFormat::kRG16:
 *         case TextureFormat::kRG16F:
 *         case TextureFormat::kRG32F:          return kRG_SkColorChannelFlags;
 *
 *         case TextureFormat::kRGB8:           [[fallthrough]];
 *         case TextureFormat::kBGR8:
 *         case TextureFormat::kB5_G6_R5:
 *         case TextureFormat::kR5_G6_B5:
 *         case TextureFormat::kRGB16:
 *         case TextureFormat::kRGB16F:
 *         case TextureFormat::kRGB32F:
 *         case TextureFormat::kRGB8_sRGB:
 *         case TextureFormat::kBGR10_XR:
 *         case TextureFormat::kRGB8_ETC2:
 *         case TextureFormat::kRGB8_ETC2_sRGB:
 *         case TextureFormat::kRGB8_BC1:
 *         case TextureFormat::kYUV8_P2_420:
 *         case TextureFormat::kYUV8_P3_420:
 *         case TextureFormat::kYUV10x6_P2_420: return kRGB_SkColorChannelFlags;
 *
 *         case TextureFormat::kRGBA8:          [[fallthrough]];
 *         case TextureFormat::kRGBA16:
 *         case TextureFormat::kRGBA16F:
 *         case TextureFormat::kRGBA32F:
 *         case TextureFormat::kRGB10_A2:
 *         case TextureFormat::kRGBA10x6:
 *         case TextureFormat::kRGBA8_sRGB:
 *         case TextureFormat::kBGRA8:
 *         case TextureFormat::kBGR10_A2:
 *         case TextureFormat::kBGRA8_sRGB:
 *         case TextureFormat::kABGR4:
 *         case TextureFormat::kARGB4:
 *         case TextureFormat::kBGRA10x6_XR:
 *         case TextureFormat::kRGBA8_BC1:
 *         case TextureFormat::kRGBA8_BC1_sRGB:
 *         case TextureFormat::kExternal:       return kRGBA_SkColorChannelFlags;
 *
 *         case TextureFormat::kS8:             [[fallthrough]];
 *         case TextureFormat::kD16:
 *         case TextureFormat::kD32F:
 *         case TextureFormat::kD24_S8:
 *         case TextureFormat::kD32F_S8:
 *         case TextureFormat::kUnsupported:    return 0;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun textureFormatChannelMask(format: TextureFormat): UInt {
  TODO("Implement textureFormatChannelMask")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatAutoClamps(TextureFormat format) {
 *     // Floating point formats, extended range formats, and non-normalized integer formats do not
 *     // auto-clamp. Everything behaves like an unsigned normalized number.
 *     return !(TextureFormatIsFloatingPoint(format) ||
 *              format == TextureFormat::kBGR10_XR ||
 *              format == TextureFormat::kBGRA10x6_XR ||
 *              format == TextureFormat::kS8);
 * }
 * ```
 */
public fun textureFormatAutoClamps(format: TextureFormat): Boolean {
  TODO("Implement textureFormatAutoClamps")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatIsFloatingPoint(TextureFormat format) {
 *     switch (format) {
 *         // Floating point formats
 *         case TextureFormat::kR16F:           [[fallthrough]];
 *         case TextureFormat::kR32F:
 *         case TextureFormat::kRG16F:
 *         case TextureFormat::kRG32F:
 *         case TextureFormat::kRGB16F:
 *         case TextureFormat::kRGB32F:
 *         case TextureFormat::kRGBA16F:
 *         case TextureFormat::kRGBA32F:
 *         case TextureFormat::kD32F:
 *         case TextureFormat::kD32F_S8:        return true;
 *
 *         // Everything else is unorm, unorm-srgb, fixed point, or integral
 *         case TextureFormat::kUnsupported:    [[fallthrough]];
 *         case TextureFormat::kR8:
 *         case TextureFormat::kR16:
 *         case TextureFormat::kA8:
 *         case TextureFormat::kRG8:
 *         case TextureFormat::kRG16:
 *         case TextureFormat::kRGB8:
 *         case TextureFormat::kBGR8:
 *         case TextureFormat::kB5_G6_R5:
 *         case TextureFormat::kR5_G6_B5:
 *         case TextureFormat::kRGB16:
 *         case TextureFormat::kRGB8_sRGB:
 *         case TextureFormat::kBGR10_XR:
 *         case TextureFormat::kRGBA8:
 *         case TextureFormat::kRGBA16:
 *         case TextureFormat::kRGB10_A2:
 *         case TextureFormat::kRGBA10x6:
 *         case TextureFormat::kRGBA8_sRGB:
 *         case TextureFormat::kBGRA8:
 *         case TextureFormat::kBGR10_A2:
 *         case TextureFormat::kBGRA8_sRGB:
 *         case TextureFormat::kABGR4:
 *         case TextureFormat::kARGB4:
 *         case TextureFormat::kBGRA10x6_XR:
 *         case TextureFormat::kRGB8_ETC2:
 *         case TextureFormat::kRGB8_ETC2_sRGB:
 *         case TextureFormat::kRGB8_BC1:
 *         case TextureFormat::kRGBA8_BC1:
 *         case TextureFormat::kRGBA8_BC1_sRGB:
 *         case TextureFormat::kYUV8_P2_420:
 *         case TextureFormat::kYUV8_P3_420:
 *         case TextureFormat::kYUV10x6_P2_420:
 *         case TextureFormat::kExternal:
 *         case TextureFormat::kS8:
 *         case TextureFormat::kD16:
 *         case TextureFormat::kD24_S8:          return false;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun textureFormatIsFloatingPoint(format: TextureFormat): Boolean {
  TODO("Implement textureFormatIsFloatingPoint")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatIsDepthOrStencil(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kS8:      [[fallthrough]];
 *         case TextureFormat::kD16:
 *         case TextureFormat::kD32F:
 *         case TextureFormat::kD24_S8:
 *         case TextureFormat::kD32F_S8:
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun textureFormatIsDepthOrStencil(format: TextureFormat): Boolean {
  TODO("Implement textureFormatIsDepthOrStencil")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatHasDepth(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kD16:     [[fallthrough]];
 *         case TextureFormat::kD32F:
 *         case TextureFormat::kD24_S8:
 *         case TextureFormat::kD32F_S8:
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun textureFormatHasDepth(format: TextureFormat): Boolean {
  TODO("Implement textureFormatHasDepth")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatHasStencil(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kS8:      [[fallthrough]];
 *         case TextureFormat::kD24_S8:
 *         case TextureFormat::kD32F_S8:
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun textureFormatHasStencil(format: TextureFormat): Boolean {
  TODO("Implement textureFormatHasStencil")
}

/**
 * C++ original:
 * ```cpp
 * bool TextureFormatIsMultiplanar(TextureFormat format) {
 *     switch (format) {
 *         case TextureFormat::kYUV8_P2_420:    [[fallthrough]];
 *         case TextureFormat::kYUV8_P3_420:
 *         case TextureFormat::kYUV10x6_P2_420:
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun textureFormatIsMultiplanar(format: TextureFormat): Boolean {
  TODO("Implement textureFormatIsMultiplanar")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<Surface> make_renderable_scratch_surface(
 *         Recorder* recorder,
 *         const SkImageInfo& info,
 *         SkBackingFit backingFit,
 *         std::string_view label,
 *         const SkSurfaceProps* surfaceProps = nullptr) {
 *     SkColorType ct = recorder->priv().caps()->getRenderableColorType(info.colorType());
 *     if (ct == kUnknown_SkColorType) {
 *         return nullptr;
 *     }
 *
 *     // TODO(b/323886870): Historically the scratch surfaces used here were exact-fit but they should
 *     // be able to be approx-fit and uninstantiated.
 *     return Surface::MakeScratch(recorder,
 *                                 info.makeColorType(ct),
 *                                 std::move(label),
 *                                 Budgeted::kYes,
 *                                 Mipmapped::kNo,
 *                                 backingFit);
 * }
 * ```
 */
public fun makeRenderableScratchSurface(
  recorder: Recorder?,
  info: SkImageInfo,
  backingFit: SkBackingFit,
  label: String,
  surfaceProps: SkSurfaceProps? = TODO(),
): SkSp<Surface> {
  TODO("Implement makeRenderableScratchSurface")
}

/**
 * C++ original:
 * ```cpp
 * bool valid_client_provided_image(const SkImage* clientProvided,
 *                                  const SkImage* original,
 *                                  SkImage::RequiredProperties requiredProps) {
 *     if (!clientProvided ||
 *         !as_IB(clientProvided)->isGraphiteBacked() ||
 *         original->dimensions() != clientProvided->dimensions() ||
 *         original->alphaType() != clientProvided->alphaType()) {
 *         return false;
 *     }
 *
 *     uint32_t origChannels = SkColorTypeChannelFlags(original->colorType());
 *     uint32_t clientChannels = SkColorTypeChannelFlags(clientProvided->colorType());
 *     if ((origChannels & clientChannels) != origChannels) {
 *         return false;
 *     }
 *
 *     // We require provided images to have a TopLeft origin
 *     auto graphiteImage = static_cast<const Image*>(clientProvided);
 *     if (graphiteImage->textureProxyView().origin() != Origin::kTopLeft) {
 *         SKGPU_LOG_E("Client provided image must have a TopLeft origin.");
 *         return false;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun validClientProvidedImage(
  clientProvided: SkImage?,
  original: SkImage?,
  requiredProps: SkImage.RequiredProperties,
): Boolean {
  TODO("Implement validClientProvidedImage")
}

/**
 * C++ original:
 * ```cpp
 * std::tuple<TextureProxyView, SkColorType> MakeBitmapProxyView(Recorder* recorder,
 *                                                               const SkBitmap& bitmap,
 *                                                               sk_sp<SkMipmap> mipmapsIn,
 *                                                               Mipmapped mipmapped,
 *                                                               Budgeted budgeted,
 *                                                               std::string_view label) {
 *     // Adjust params based on input and Caps
 *     const Caps* caps = recorder->priv().caps();
 *     SkColorType ct = bitmap.info().colorType();
 *
 *     if (bitmap.dimensions().area() <= 1) {
 *         mipmapped = Mipmapped::kNo;
 *     }
 *
 *     Protected isProtected = recorder->priv().isProtected();
 *     auto textureInfo = caps->getDefaultSampledTextureInfo(ct, mipmapped, isProtected,
 *                                                           Renderable::kNo);
 *     if (!textureInfo.isValid()) {
 *         ct = kRGBA_8888_SkColorType;
 *         textureInfo = caps->getDefaultSampledTextureInfo(ct, mipmapped, isProtected,
 *                                                          Renderable::kNo);
 *     }
 *     SkASSERT(textureInfo.isValid());
 *
 *     // Convert bitmap to texture colortype if necessary
 *     SkBitmap bmpToUpload;
 *     if (ct != bitmap.info().colorType()) {
 *         if (!bmpToUpload.tryAllocPixels(bitmap.info().makeColorType(ct)) ||
 *             !bitmap.readPixels(bmpToUpload.pixmap())) {
 *             return {};
 *         }
 *         bmpToUpload.setImmutable();
 *     } else {
 *         bmpToUpload = bitmap;
 *     }
 *
 *     if (!SkImageInfoIsValid(bmpToUpload.info())) {
 *         return {};
 *     }
 *
 *     int mipLevelCount = (mipmapped == Mipmapped::kYes) ?
 *             SkMipmap::ComputeLevelCount(bitmap.width(), bitmap.height()) + 1 : 1;
 *
 *
 *     // setup MipLevels
 *     sk_sp<SkMipmap> mipmaps;
 *     std::vector<MipLevel> texels;
 *     if (mipLevelCount == 1) {
 *         texels.resize(mipLevelCount);
 *         texels[0].fPixels = bmpToUpload.getPixels();
 *         texels[0].fRowBytes = bmpToUpload.rowBytes();
 *     } else {
 *         mipmaps = SkToBool(mipmapsIn)
 *                           ? mipmapsIn
 *                           : sk_sp<SkMipmap>(SkMipmap::Build(bmpToUpload.pixmap(), nullptr));
 *         if (!mipmaps) {
 *             return {};
 *         }
 *
 *         SkASSERT(mipLevelCount == mipmaps->countLevels() + 1);
 *         texels.resize(mipLevelCount);
 *
 *         texels[0].fPixels = bmpToUpload.getPixels();
 *         texels[0].fRowBytes = bmpToUpload.rowBytes();
 *
 *         for (int i = 1; i < mipLevelCount; ++i) {
 *             SkMipmap::Level generatedMipLevel;
 *             mipmaps->getLevel(i - 1, &generatedMipLevel);
 *             texels[i].fPixels = generatedMipLevel.fPixmap.addr();
 *             texels[i].fRowBytes = generatedMipLevel.fPixmap.rowBytes();
 *             SkASSERT(texels[i].fPixels);
 *             SkASSERT(generatedMipLevel.fPixmap.colorType() == bmpToUpload.colorType());
 *         }
 *     }
 *
 *     // Create proxy
 *     sk_sp<TextureProxy> proxy = TextureProxy::Make(caps,
 *                                                    recorder->priv().resourceProvider(),
 *                                                    bmpToUpload.dimensions(),
 *                                                    textureInfo,
 *                                                    std::move(label),
 *                                                    budgeted);
 *     if (!proxy) {
 *         return {};
 *     }
 *     SkASSERT(caps->areColorTypeAndTextureInfoCompatible(ct, proxy->textureInfo()));
 *     SkASSERT(mipmapped == Mipmapped::kNo || proxy->mipmapped() == Mipmapped::kYes);
 *
 *     // Src and dst colorInfo are the same
 *     const SkColorInfo& colorInfo = bmpToUpload.info().colorInfo();
 *     // Add upload to the root upload list. These bitmaps are uploaded to unique textures so there is
 *     // no need to coordinate resource sharing. It is better to then group them into a single task
 *     // at the start of the Recording.
 *     const SkIRect dimensions = SkIRect::MakeSize(bmpToUpload.dimensions());
 *     UploadSource uploadSource = UploadSource::Make(
 *             recorder->priv().caps(), *proxy, colorInfo, colorInfo, texels, dimensions);
 *     if (!uploadSource.isValid()) {
 *         SKGPU_LOG_E("MakeBitmapProxyView: Could not create UploadSource");
 *         return {};
 *     }
 *     if (!recorder->priv().rootUploadList()->recordUpload(recorder,
 *                                                          proxy,
 *                                                          colorInfo,
 *                                                          colorInfo,
 *                                                          uploadSource,
 *                                                          dimensions,
 *                                                          std::make_unique<ImageUploadContext>())) {
 *         SKGPU_LOG_E("MakeBitmapProxyView: Could not create UploadInstance");
 *         return {};
 *     }
 *
 *     Swizzle swizzle = caps->getReadSwizzle(ct, textureInfo);
 *     // If the color type is alpha-only, propagate the alpha value to the other channels.
 *     if (SkColorTypeIsAlphaOnly(colorInfo.colorType())) {
 *         swizzle = Swizzle::Concat(swizzle, Swizzle("aaaa"));
 *     }
 *     return {{std::move(proxy), swizzle}, ct};
 * }
 * ```
 */
public fun makeBitmapProxyView(
  recorder: Recorder?,
  bitmap: SkBitmap,
  mipmapsIn: SkSp<SkMipmap>,
  mipmapped: Mipmapped,
  budgeted: Budgeted,
  label: String,
): Pair<TextureProxyView, SkColorType> {
  TODO("Implement makeBitmapProxyView")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<TextureProxy> MakePromiseImageLazyProxy(
 *         const Caps* caps,
 *         SkISize dimensions,
 *         TextureInfo textureInfo,
 *         Volatile isVolatile,
 *         sk_sp<RefCntedCallback> releaseHelper,
 *         GraphitePromiseTextureFulfillProc fulfillProc,
 *         GraphitePromiseTextureFulfillContext fulfillContext,
 *         GraphitePromiseTextureReleaseProc textureReleaseProc,
 *         std::string_view label) {
 *     SkASSERT(!dimensions.isEmpty());
 *     SkASSERT(releaseHelper);
 *
 *     if (!fulfillProc) {
 *         return nullptr;
 *     }
 *
 *     PromiseLazyInstantiateCallback callback{std::move(releaseHelper), fulfillProc,
 *                                             fulfillContext, textureReleaseProc, std::move(label)};
 *     // Proxies for promise images are assumed to always be destined for a client's SkImage so
 *     // are never considered budgeted.
 *     return TextureProxy::MakeLazy(caps, dimensions, textureInfo, Budgeted::kNo, isVolatile,
 *                                   std::move(callback));
 * }
 * ```
 */
public fun makePromiseImageLazyProxy(
  caps: Caps?,
  dimensions: SkISize,
  textureInfo: TextureInfo,
  isVolatile: Volatile,
  releaseHelper: SkSp<RefCntedCallback>,
  fulfillProc: GraphitePromiseTextureFulfillProc,
  fulfillContext: GraphitePromiseTextureFulfillContext,
  textureReleaseProc: GraphitePromiseTextureReleaseProc,
  label: String,
): SkSp<TextureProxy> {
  TODO("Implement makePromiseImageLazyProxy")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> MakeFromBitmap(Recorder* recorder,
 *                               const SkColorInfo& colorInfo,
 *                               const SkBitmap& bitmap,
 *                               sk_sp<SkMipmap> mipmaps,
 *                               Budgeted budgeted,
 *                               SkImage::RequiredProperties requiredProps,
 *                               std::string_view label) {
 *     auto mm = requiredProps.fMipmapped ? Mipmapped::kYes : Mipmapped::kNo;
 *     auto [view, ct] = MakeBitmapProxyView(recorder,
 *                                           bitmap,
 *                                           std::move(mipmaps),
 *                                           mm,
 *                                           budgeted,
 *                                           std::move(label));
 *     if (!view) {
 *         return nullptr;
 *     }
 *
 *     SkASSERT(!requiredProps.fMipmapped || view.proxy()->mipmapped() == Mipmapped::kYes);
 *     return sk_make_sp<skgpu::graphite::Image>(std::move(view), colorInfo.makeColorType(ct));
 * }
 * ```
 */
public fun makeFromBitmap(
  recorder: Recorder?,
  colorInfo: SkColorInfo,
  bitmap: SkBitmap,
  mipmaps: SkSp<SkMipmap>,
  budgeted: Budgeted,
  requiredProps: SkImage.RequiredProperties,
  label: String,
): SkSp<SkImage> {
  TODO("Implement makeFromBitmap")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<Image> CopyAsDraw(Recorder* recorder,
 *                         DrawContext* drawContext,
 *                         const SkImage* image,
 *                         const SkIRect& subset,
 *                         const SkColorInfo& dstColorInfo,
 *                         Budgeted budgeted,
 *                         Mipmapped mipmapped,
 *                         SkBackingFit backingFit,
 *                         std::string_view label) {
 *     SkColorType ct = recorder->priv().caps()->getRenderableColorType(dstColorInfo.colorType());
 *     if (ct == kUnknown_SkColorType) {
 *         return nullptr;
 *     }
 *     SkImageInfo dstInfo = SkImageInfo::Make(subset.size(),
 *                                             dstColorInfo.makeColorType(ct)
 *                                                         .makeAlphaType(kPremul_SkAlphaType));
 *     // The surface goes out of scope when we return, so it can be scratch, but it may or may
 *     // not be budgeted depending on how the copied image is used (or returned to the client).
 *     sk_sp<Surface> surface = Surface::MakeScratch(recorder,
 *                                                   dstInfo,
 *                                                   std::move(label),
 *                                                   budgeted,
 *                                                   mipmapped,
 *                                                   backingFit);
 *     if (!surface) {
 *         return nullptr;
 *     }
 *
 *     SkPaint paint;
 *     paint.setBlendMode(SkBlendMode::kSrc);
 *     surface->getCanvas()->drawImage(image, -subset.left(), -subset.top(),
 *                                     SkFilterMode::kNearest, &paint);
 *     surface->flushToDrawContext(drawContext);
 *     return surface->asImage();
 * }
 * ```
 */
public fun copyAsDraw(
  recorder: Recorder?,
  drawContext: DrawContext?,
  image: SkImage?,
  subset: SkIRect,
  dstColorInfo: SkColorInfo,
  budgeted: Budgeted,
  mipmapped: Mipmapped,
  backingFit: SkBackingFit,
  label: String,
): SkSp<Image> {
  TODO("Implement copyAsDraw")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> RescaleImage(Recorder* recorder,
 *                             const SkImage* srcImage,
 *                             SkIRect srcIRect,
 *                             const SkImageInfo& dstInfo,
 *                             SkImage::RescaleGamma rescaleGamma,
 *                             SkImage::RescaleMode rescaleMode) {
 *     TRACE_EVENT0("skia.gpu", TRACE_FUNC);
 *     TRACE_EVENT_INSTANT2("skia.gpu", "RescaleImage Src", TRACE_EVENT_SCOPE_THREAD,
 *                          "width", srcIRect.width(), "height", srcIRect.height());
 *     TRACE_EVENT_INSTANT2("skia.gpu", "RescaleImage Dst", TRACE_EVENT_SCOPE_THREAD,
 *                          "width", dstInfo.width(), "height", dstInfo.height());
 *
 *     // RescaleImage() should only be called when we already know that srcImage is graphite-backed
 *     SkASSERT(srcImage && as_IB(srcImage)->isGraphiteBacked());
 *
 *     // For now this needs to be texturable because we can't depend on copies to scale.
 *     // NOTE: srcView may be empty if srcImage is YUVA.
 *     const TextureProxyView srcView = AsView(srcImage);
 *     if (srcView && !recorder->priv().caps()->isTexturable(srcView.proxy()->textureInfo())) {
 *         // With the current definition of SkImage, this shouldn't happen. If we allow non-texturable
 *         // formats for compute, we'll need to copy to a texturable format.
 *         SkASSERT(false);
 *         return nullptr;
 *     }
 *
 *     // make a Surface *exactly* matching dstInfo to rescale into
 *     SkSurfaceProps surfaceProps = {};
 *     sk_sp<SkSurface> dst = make_renderable_scratch_surface(recorder,
 *                                                            dstInfo,
 *                                                            SkBackingFit::kExact,
 *                                                            "RescaleDstTexture",
 *                                                            &surfaceProps);
 *     if (!dst) {
 *         return nullptr;
 *     }
 *
 *     SkRect srcRect = SkRect::Make(srcIRect);
 *     SkRect dstRect = SkRect::Make(dstInfo.dimensions());
 *
 *     SkISize finalSize = SkISize::Make(dstRect.width(), dstRect.height());
 *     if (finalSize == srcIRect.size()) {
 *         rescaleGamma = Image::RescaleGamma::kSrc;
 *         rescaleMode = Image::RescaleMode::kNearest;
 *     }
 *
 *     // Within a rescaling pass tempInput is read from and tempOutput is written to.
 *     // At the end of the pass tempOutput's texture is wrapped and assigned to tempInput.
 *     sk_sp<SkImage> tempInput = sk_ref_sp(srcImage);
 *     sk_sp<SkSurface> tempOutput;
 *
 *     // Assume we should ignore the rescale linear request if the surface has no color space since
 *     // it's unclear how we'd linearize from an unknown color space.
 *     const SkImageInfo& srcImageInfo = srcImage->imageInfo();
 *     if (rescaleGamma == Image::RescaleGamma::kLinear &&
 *         srcImageInfo.colorSpace() &&
 *         !srcImageInfo.colorSpace()->gammaIsLinear()) {
 *         // Draw the src image into a new surface with linear gamma, and make that the new tempInput
 *         sk_sp<SkColorSpace> linearGamma = srcImageInfo.colorSpace()->makeLinearGamma();
 *         SkImageInfo gammaDstInfo = SkImageInfo::Make(srcIRect.size(),
 *                                                      tempInput->imageInfo().colorType(),
 *                                                      kPremul_SkAlphaType,
 *                                                      std::move(linearGamma));
 *         tempOutput = make_renderable_scratch_surface(recorder, gammaDstInfo, SkBackingFit::kApprox,
 *                                                      "RescaleLinearGammaTexture", &surfaceProps);
 *         if (!tempOutput) {
 *             return nullptr;
 *         }
 *         SkCanvas* gammaDst = tempOutput->getCanvas();
 *         SkRect gammaDstRect = SkRect::Make(srcIRect.size());
 *
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kSrc);
 *         gammaDst->drawImageRect(tempInput, srcRect, gammaDstRect,
 *                                 SkSamplingOptions(SkFilterMode::kNearest), &paint,
 *                                 SkCanvas::kStrict_SrcRectConstraint);
 *         tempInput = SkSurfaces::AsImage(std::move(tempOutput));
 *         srcRect = gammaDstRect;
 *     }
 *
 *     SkImageInfo outImageInfo = tempInput->imageInfo().makeAlphaType(kPremul_SkAlphaType);
 *     do {
 *         SkISize nextDims = finalSize;
 *         if (rescaleMode != Image::RescaleMode::kNearest &&
 *             rescaleMode != Image::RescaleMode::kLinear) {
 *             if (srcRect.width() > finalSize.width()) {
 *                 nextDims.fWidth = std::max((srcRect.width() + 1)/2, (float)finalSize.width());
 *             } else if (srcRect.width() < finalSize.width()) {
 *                 nextDims.fWidth = std::min(srcRect.width()*2, (float)finalSize.width());
 *             }
 *             if (srcRect.height() > finalSize.height()) {
 *                 nextDims.fHeight = std::max((srcRect.height() + 1)/2, (float)finalSize.height());
 *             } else if (srcRect.height() < finalSize.height()) {
 *                 nextDims.fHeight = std::min(srcRect.height()*2, (float)finalSize.height());
 *             }
 *         }
 *
 *         SkRect stepDstRect;
 *         if (nextDims == finalSize) {
 *             tempOutput = dst;
 *             stepDstRect = dstRect;
 *         } else {
 *             SkImageInfo nextInfo = outImageInfo.makeDimensions(nextDims);
 *             tempOutput = make_renderable_scratch_surface(recorder, nextInfo, SkBackingFit::kApprox,
 *                                                          "RescaleImageTempTexture", &surfaceProps);
 *             if (!tempOutput) {
 *                 return nullptr;
 *             }
 *             stepDstRect = SkRect::Make(tempOutput->imageInfo().dimensions());
 *         }
 *
 *         SkSamplingOptions samplingOptions;
 *         if (rescaleMode == Image::RescaleMode::kRepeatedCubic) {
 *             samplingOptions = SkSamplingOptions(SkCubicResampler::CatmullRom());
 *         } else {
 *             samplingOptions = (rescaleMode == Image::RescaleMode::kNearest) ?
 *                                SkSamplingOptions(SkFilterMode::kNearest) :
 *                                SkSamplingOptions(SkFilterMode::kLinear);
 *         }
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kSrc);
 *         tempOutput->getCanvas()->drawImageRect(tempInput, srcRect, stepDstRect, samplingOptions,
 *                                                &paint, SkCanvas::kStrict_SrcRectConstraint);
 *
 *         tempInput = SkSurfaces::AsImage(std::move(tempOutput));
 *         srcRect = SkRect::Make(nextDims);
 *     } while (srcRect.width() != finalSize.width() || srcRect.height() != finalSize.height());
 *
 *     return SkSurfaces::AsImage(std::move(dst));
 * }
 * ```
 */
public fun rescaleImage(
  recorder: Recorder?,
  srcImage: SkImage?,
  srcIRect: SkIRect,
  dstInfo: SkImageInfo,
  rescaleGamma: SkImage.RescaleGamma,
  rescaleMode: SkImage.RescaleMode,
): SkSp<SkImage> {
  TODO("Implement rescaleImage")
}

/**
 * C++ original:
 * ```cpp
 * bool GenerateMipmaps(Recorder* recorder,
 *                      DrawContext* drawContext,
 *                      sk_sp<TextureProxy> texture,
 *                      const SkColorInfo& colorInfo) {
 *     constexpr SkSamplingOptions kSamplingOptions = SkSamplingOptions(SkFilterMode::kLinear);
 *
 *     SkASSERT(texture->mipmapped() == Mipmapped::kYes);
 *
 *     // Within a rescaling pass scratchImg is read from and a scratch surface is written to.
 *     // At the end of the pass the scratch surface's texture is wrapped and assigned to scratchImg.
 *
 *     // The scratch surface we create below will use a write swizzle derived from SkColorType and
 *     // pixel format. We have to be consistent and swizzle on the read.
 *     auto imgSwizzle = recorder->priv().caps()->getReadSwizzle(colorInfo.colorType(),
 *                                                               texture->textureInfo());
 *     sk_sp<SkImage> scratchImg(new Image(TextureProxyView(texture, imgSwizzle), colorInfo));
 *
 *     SkISize srcSize = texture->dimensions();
 *     const SkColorInfo outColorInfo = colorInfo.makeAlphaType(kPremul_SkAlphaType);
 *
 *     // Alternate between two scratch surfaces to avoid reading from and writing to a texture in the
 *     // same pass. The dimensions of the first usages of the two scratch textures will be 1/2 and 1/4
 *     // those of the original texture, respectively.
 *     sk_sp<Surface> scratchSurfaces[2];
 *     for (int i = 0; i < 2; ++i) {
 *         scratchSurfaces[i] = make_renderable_scratch_surface(
 *                 recorder,
 *                 SkImageInfo::Make(SkISize::Make(std::max(1, srcSize.width() >> (i + 1)),
 *                                                 std::max(1, srcSize.height() >> (i + 1))),
 *                                   outColorInfo),
 *                 SkBackingFit::kApprox,
 *                 "GenerateMipmapsScratchTexture");
 *         if (!scratchSurfaces[i]) {
 *             return false;
 *         }
 *     }
 *
 *     for (int mipLevel = 1; srcSize.width() > 1 || srcSize.height() > 1; ++mipLevel) {
 *         const SkISize dstSize = SkISize::Make(std::max(srcSize.width() >> 1, 1),
 *                                               std::max(srcSize.height() >> 1, 1));
 *
 *         Surface* scratchSurface = scratchSurfaces[(mipLevel - 1) & 1].get();
 *
 *         SkPaint paint;
 *         paint.setBlendMode(SkBlendMode::kSrc);
 *         scratchSurface->getCanvas()->drawImageRect(scratchImg,
 *                                                    SkRect::Make(srcSize),
 *                                                    SkRect::Make(dstSize),
 *                                                    kSamplingOptions,
 *                                                    &paint,
 *                                                    SkCanvas::kStrict_SrcRectConstraint);
 *
 *         // Make sure the rescaling draw finishes before copying the results.
 *         scratchSurface->flushToDrawContext(drawContext);
 *
 *         sk_sp<CopyTextureToTextureTask> copyTask = CopyTextureToTextureTask::Make(
 *                 static_cast<const Surface*>(scratchSurface)->readSurfaceView().refProxy(),
 *                 SkIRect::MakeSize(dstSize),
 *                 texture,
 *                 {0, 0},
 *                 mipLevel);
 *         if (!copyTask) {
 *             return false;
 *         }
 *
 *         if (drawContext) {
 *             drawContext->recordDependency(std::move(copyTask));
 *         } else {
 *             recorder->priv().add(std::move(copyTask));
 *         }
 *
 *         scratchImg = scratchSurface->asImage();
 *         srcSize = dstSize;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun generateMipmaps(
  recorder: Recorder?,
  drawContext: DrawContext?,
  texture: SkSp<TextureProxy>,
  colorInfo: SkColorInfo,
): Boolean {
  TODO("Implement generateMipmaps")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkImage>, SkSamplingOptions> GetGraphiteBacked(Recorder* recorder,
 *                                                                const SkImage* imageIn,
 *                                                                SkSamplingOptions sampling) {
 *     Mipmapped mipmapped = (sampling.mipmap != SkMipmapMode::kNone)
 *                                      ? Mipmapped::kYes : Mipmapped::kNo;
 *
 *     if (imageIn->dimensions().area() <= 1 && mipmapped == Mipmapped::kYes) {
 *         mipmapped = Mipmapped::kNo;
 *         sampling = SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone);
 *     }
 *
 *     sk_sp<SkImage> result;
 *     if (as_IB(imageIn)->isGraphiteBacked()) {
 *         result = sk_ref_sp(imageIn);
 *
 *         // If the preexisting Graphite-backed image doesn't have the required mipmaps we will drop
 *         // down the sampling
 *         if (mipmapped == Mipmapped::kYes && !result->hasMipmaps()) {
 *             mipmapped = Mipmapped::kNo;
 *             sampling = SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone);
 *         }
 *     } else {
 *         auto clientImageProvider = recorder->clientImageProvider();
 *         result = clientImageProvider->findOrCreate(
 *                 recorder, imageIn, {mipmapped == Mipmapped::kYes});
 *
 *         if (!valid_client_provided_image(
 *                     result.get(), imageIn, {mipmapped == Mipmapped::kYes})) {
 *             // The client did not fulfill the ImageProvider contract so drop the image.
 *             result = nullptr;
 *         }
 *     }
 *
 *     if (sampling.isAniso() && result) {
 *         sampling = SkSamplingPriv::AnisoFallback(result->hasMipmaps());
 *     }
 *
 *     return { result, sampling };
 * }
 * ```
 */
public fun getGraphiteBacked(
  recorder: Recorder?,
  imageIn: SkImage?,
  sampling: SkSamplingOptions,
): Pair<SkSp<SkImage>, SkSamplingOptions> {
  TODO("Implement getGraphiteBacked")
}

/**
 * C++ original:
 * ```cpp
 * SkColorType ComputeShaderCoverageMaskTargetFormat(const Caps* caps) {
 *     // GPU compute coverage mask renderers need to bind the mask texture as a storage binding, which
 *     // support a limited set of color formats. In general, we use RGBA8 if Alpha8 can't be
 *     // supported.
 *     if (caps->isStorage(caps->getDefaultStorageTextureInfo(kAlpha_8_SkColorType))) {
 *         return kAlpha_8_SkColorType;
 *     }
 *     return kRGBA_8888_SkColorType;
 * }
 * ```
 */
public fun computeShaderCoverageMaskTargetFormat(caps: Caps?): SkColorType {
  TODO("Implement computeShaderCoverageMaskTargetFormat")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<Backend> MakeGraphiteBackend(skgpu::graphite::Recorder* recorder,
 *                                    const SkSurfaceProps& surfaceProps,
 *                                    SkColorType colorType) {
 *     SkASSERT(recorder);
 *     return sk_make_sp<GraphiteBackend>(recorder, surfaceProps, colorType);
 * }
 * ```
 */
public fun makeGraphiteBackend(
  recorder: Recorder?,
  surfaceProps: SkSurfaceProps,
  colorType: SkColorType,
): SkSp<Backend> {
  TODO("Implement makeGraphiteBackend")
}

/**
 * C++ original:
 * ```cpp
 * static std::pair<SkSLType, int> adjust_for_matrix_type(SkSLType type, int count) {
 *     // All Layouts flatten matrices and arrays of matrices into arrays of columns, so update
 *     // 'type' to be the column type and either multiply 'count' by the number of columns for
 *     // arrays of matrices, or set to exactly the number of columns for a "non-array" matrix.
 *     switch(type) {
 *         case SkSLType::kFloat2x2: return {SkSLType::kFloat2, 2*std::max(1, count)};
 *         case SkSLType::kFloat3x3: return {SkSLType::kFloat3, 3*std::max(1, count)};
 *         case SkSLType::kFloat4x4: return {SkSLType::kFloat4, 4*std::max(1, count)};
 *
 *         case SkSLType::kHalf2x2:  return {SkSLType::kHalf2,  2*std::max(1, count)};
 *         case SkSLType::kHalf3x3:  return {SkSLType::kHalf3,  3*std::max(1, count)};
 *         case SkSLType::kHalf4x4:  return {SkSLType::kHalf4,  4*std::max(1, count)};
 *
 *         // Otherwise leave type and count alone.
 *         default:                  return {type, count};
 *     }
 * }
 * ```
 */
public fun adjustForMatrixType(type: SkSLType, count: Int): Pair<SkSLType, Int> {
  TODO("Implement adjustForMatrixType")
}

/**
 * C++ original:
 * ```cpp
 * int num_channels(uint32_t ChannelMasks) {
 *     switch (ChannelMasks) {
 *         case kRed_SkColorChannelFlag        : return 1;
 *         case kAlpha_SkColorChannelFlag      : return 1;
 *         case kGray_SkColorChannelFlag       : return 1;
 *         case kGrayAlpha_SkColorChannelFlags : return 2;
 *         case kRG_SkColorChannelFlags        : return 2;
 *         case kRGB_SkColorChannelFlags       : return 3;
 *         case kRGBA_SkColorChannelFlags      : return 4;
 *         default                             : return 0;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun numChannels(channelMasks: UInt): Int {
  TODO("Implement numChannels")
}

/**
 * C++ original:
 * ```cpp
 * static uint32_t next_id() {
 *     static std::atomic<uint32_t> nextId{0};
 *     // Not worried about overflow since a Context isn't expected to have that many ComputeSteps.
 *     // Even if this it wraps around to 0, that ComputeStep will not be in the same Context as the
 *     // original 0.
 *     return nextId.fetch_add(1, std::memory_order_relaxed);
 * }
 * ```
 */
public fun nextId(): UInt {
  TODO("Implement nextId")
}

/**
 * C++ original:
 * ```cpp
 * std::optional<Rect> outset_bounds(const SkMatrix& localToDevice,
 *                                   float devSigma,
 *                                   const SkRect& srcRect) {
 *     float outsetX = 3.0f * devSigma;
 *     float outsetY = 3.0f * devSigma;
 *     if (localToDevice.isScaleTranslate()) {
 *         outsetX /= std::fabs(localToDevice.getScaleX());
 *         outsetY /= std::fabs(localToDevice.getScaleY());
 *     } else {
 *         SkSize scale;
 *         if (!localToDevice.decomposeScale(&scale, nullptr)) {
 *             return std::nullopt;
 *         }
 *         outsetX /= scale.width();
 *         outsetY /= scale.height();
 *     }
 *     return srcRect.makeOutset(outsetX, outsetY);
 * }
 * ```
 */
public fun outsetBounds(
  localToDevice: SkMatrix,
  devSigma: Float,
  srcRect: SkRect,
): Rect? {
  TODO("Implement outsetBounds")
}

/**
 * C++ original:
 * ```cpp
 * static float quantize(float deviceSpaceFloat) {
 *     // Snap the device-space value to the nearest 1/32 to increase cache hits w/o impacting the
 *     // visible output since it should be hard to see a change limited to 1/32 of a pixel.
 *     // Clamp the value to 1/32 as identity blurs and points should be caught earlier.
 *     return std::max(SkScalarRoundToInt(deviceSpaceFloat * 32.f) / 32.f, 1.f / 32.f);
 * }
 * ```
 */
public fun quantize(deviceSpaceFloat: Float): Float {
  TODO("Implement quantize")
}

/**
 * C++ original:
 * ```cpp
 * int path_key_from_data_size(const SkPath& path) {
 *     const int verbCnt = path.countVerbs();
 *     if (verbCnt > kMaxKeyFromDataVerbCnt) {
 *         return -1;
 *     }
 *     const size_t pointCnt = path.points().size();
 *     const size_t conicWeightCnt = path.conicWeights().size();
 *
 *     static_assert(sizeof(SkPoint) == 2 * sizeof(uint32_t));
 *     static_assert(sizeof(SkScalar) == sizeof(uint32_t));
 *     // 1 is for the verb count. Each verb is a byte but we'll pad the verb data out to
 *     // a uint32_t length.
 *     return 1 + (SkAlign4(verbCnt) >> 2) + 2 * pointCnt + conicWeightCnt;
 * }
 * ```
 */
public fun pathKeyFromDataSize(path: SkPath): Int {
  TODO("Implement pathKeyFromDataSize")
}

/**
 * C++ original:
 * ```cpp
 * void write_path_key_from_data(const SkPath& path, uint32_t* origKey) {
 *     uint32_t* key = origKey;
 *     // The check below should take care of negative values casted positive.
 *     SkSpan<const SkPathVerb> verbs = path.verbs();
 *     SkSpan<const SkPoint> points = path.points();
 *     SkSpan<const float> conics = path.conicWeights();
 *     SkASSERT(verbs.size() <= kMaxKeyFromDataVerbCnt);
 *     SkASSERT(points.size() && verbs.size());
 *     *key++ = SkToInt(verbs.size());
 *     memcpy(key, verbs.data(), verbs.size_bytes());
 *     const size_t verbKeySize = SkAlign4(verbs.size());
 *     // pad out to uint32_t alignment using value that will stand out when debugging.
 *     uint8_t* pad = reinterpret_cast<uint8_t*>(key)+ verbs.size();
 *     memset(pad, 0xDE, verbKeySize - verbs.size());
 *     key += verbKeySize >> 2;
 *
 *     memcpy(key, points.data(), points.size_bytes());
 *     static_assert(sizeof(SkPoint) == 2 * sizeof(uint32_t));
 *     key += 2 * points.size();
 *     sk_careful_memcpy(key, conics.data(), conics.size_bytes());
 *     static_assert(sizeof(SkScalar) == sizeof(uint32_t));
 *     SkDEBUGCODE(key += conics.size());
 *     SkASSERT(key - origKey == path_key_from_data_size(path));
 * }
 * ```
 */
public fun writePathKeyFromData(path: SkPath, origKey: UInt?) {
  TODO("Implement writePathKeyFromData")
}

/**
 * C++ original:
 * ```cpp
 * SkPathFillType noninverted_fill_type(SkPathFillType fillType) {
 *     switch (fillType) {
 *         case SkPathFillType::kWinding:
 *         case SkPathFillType::kInverseWinding:
 *             return SkPathFillType::kWinding;
 *         case SkPathFillType::kEvenOdd:
 *         case SkPathFillType::kInverseEvenOdd:
 *             return SkPathFillType::kEvenOdd;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun noninvertedFillType(fillType: SkPathFillType): SkPathFillType {
  TODO("Implement noninvertedFillType")
}

/**
 * C++ original:
 * ```cpp
 * skvx::float4 scale_translate_rect(skvx::float4 rectVals, float sx, float sy, float tx, float ty) {
 *     // The (-tx,-ty) terms preserve the calculated values in (l,t,-r,-b) form so that the return
 *     // value can be passed directly into FromVals() to avoid extra negation operations in ltrb().
 *     return rectVals * skvx::float4{sx,sy,sx,sy} + skvx::float4{tx,ty,-tx,-ty};
 * }
 * ```
 */
public fun scaleTranslateRect(
  rectVals: Float4,
  sx: Float,
  sy: Float,
  tx: Float,
  ty: Float,
): Float4 {
  TODO("Implement scaleTranslateRect")
}

/**
 * C++ original:
 * ```cpp
 * Rect map_rect(Transform::Type type, const SkM44& m, const Rect& r) {
 *     switch (type) {
 *         case Transform::Type::kIdentity:
 *             return r;
 *         case Transform::Type::kSimpleRectStaysRect:
 *             // Since scale factors are positive, the returned rectangle is already sorted
 *             return Rect::FromVals(
 *                     scale_translate_rect(r.vals(), m.rc(0,0), m.rc(1,1), m.rc(0,3), m.rc(1,3)));
 *         case Transform::Type::kRectStaysRect: {
 *             // Which is not the case for general rect-stays-rect transforms
 *             skvx::float4 xformed = r.vals();
 *             if (m.rc(0,0) == 0.f) {
 *                 // Anti-diagonal matrix (90/270 rotation), so scale L+R by m10 and T+B by m01 and
 *                 // then swizzle so that the transformed values swap X and Y components and then sort
 *                 xformed = skvx::shuffle<1,0,3,2>(
 *                         scale_translate_rect(xformed, m.rc(1,0), m.rc(0,1), m.rc(1,3), m.rc(0,3)));
 *             } else {
 *                 // Mirror or 180 rotation, so X and/or Y edges may be flipped so just sort after.
 *                 xformed = scale_translate_rect(xformed, m.rc(0,0), m.rc(1,1), m.rc(0,3), m.rc(1,3));
 *             }
 *             return Rect::FromVals(xformed).sort();
 *         }
 *         case Transform::Type::kAffine:
 *             [[fallthrough]];
 *         case Transform::Type::kPerspective:
 *             return SkMatrixPriv::MapRect(m, r.asSkRect());
 *         case Transform::Type::kInvalid:
 *             return Rect::InfiniteInverted();
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun mapRect(
  type: Transform.Type,
  m: SkM44,
  r: Rect,
): Rect {
  TODO("Implement mapRect")
}

/**
 * C++ original:
 * ```cpp
 * void map_points(const SkM44& m, const SkV4* in, SkV4* out, int count) {
 *     // TODO: These maybe should go into SkM44, since bulk point mapping seems generally useful
 *     auto c0 = skvx::float4::Load(SkMatrixPriv::M44ColMajor(m) + 0);
 *     auto c1 = skvx::float4::Load(SkMatrixPriv::M44ColMajor(m) + 4);
 *     auto c2 = skvx::float4::Load(SkMatrixPriv::M44ColMajor(m) + 8);
 *     auto c3 = skvx::float4::Load(SkMatrixPriv::M44ColMajor(m) + 12);
 *
 *     for (int i = 0; i < count; ++i) {
 *         auto p = (c0 * in[i].x) + (c1 * in[i].y) + (c2 * in[i].z) + (c3 * in[i].w);
 *         p.store(out + i);
 *     }
 * }
 * ```
 */
public fun mapPoints(
  m: SkM44,
  `in`: SkV4?,
  `out`: SkV4?,
  count: Int,
) {
  TODO("Implement mapPoints")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<float, float> compute_svd(float m00, float m01, float m10, float m11) {
 *     // no-persp, these are the singular values of [m00,m01][m10,m11], which is just the upper 2x2
 *     // and equivalent to SkMatrix::getMinmaxScales().
 *     float s1 = m00*m00 + m01*m01 + m10*m10 + m11*m11;
 *
 *     float e = m00*m00 + m01*m01 - m10*m10 - m11*m11;
 *     float f = m00*m10 + m01*m11;
 *     float s2 = SkScalarSqrt(e*e + 4*f*f);
 *
 *     // s2 >= 0, so (s1 - s2) <= (s1 + s2) so this always returns {min, max}.
 *     return {SkScalarSqrt(0.5f * (s1 - s2)),
 *             SkScalarSqrt(0.5f * (s1 + s2))};
 * }
 * ```
 */
public fun computeSvd(
  m00: Float,
  m01: Float,
  m10: Float,
  m11: Float,
): Pair<Float, Float> {
  TODO("Implement computeSvd")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<float, float> sort_scale(float sx, float sy) {
 *     float min = std::abs(sx);
 *     float max = std::abs(sy);
 *     if (min > max) {
 *         return {max, min};
 *     } else {
 *         return {min, max};
 *     }
 * }
 * ```
 */
public fun sortScale(sx: Float, sy: Float): Pair<Float, Float> {
  TODO("Implement sortScale")
}

/**
 * C++ original:
 * ```cpp
 * static skvx::float4 load_x_radii(const SkRRect& rrect) {
 *     return skvx::float4{rrect.radii(SkRRect::kUpperLeft_Corner).fX,
 *                         rrect.radii(SkRRect::kUpperRight_Corner).fX,
 *                         rrect.radii(SkRRect::kLowerRight_Corner).fX,
 *                         rrect.radii(SkRRect::kLowerLeft_Corner).fX};
 * }
 * ```
 */
public fun loadXRadii(rrect: SkRRect): Float4 {
  TODO("Implement loadXRadii")
}

/**
 * C++ original:
 * ```cpp
 * static skvx::float4 load_y_radii(const SkRRect& rrect) {
 *     return skvx::float4{rrect.radii(SkRRect::kUpperLeft_Corner).fY,
 *                         rrect.radii(SkRRect::kUpperRight_Corner).fY,
 *                         rrect.radii(SkRRect::kLowerRight_Corner).fY,
 *                         rrect.radii(SkRRect::kLowerLeft_Corner).fY};
 * }
 * ```
 */
public fun loadYRadii(rrect: SkRRect): Float4 {
  TODO("Implement loadYRadii")
}

/**
 * C++ original:
 * ```cpp
 * static bool opposite_insets_intersect(const Geometry& geometry,
 *                                       float strokeRadius,
 *                                       float aaRadius) {
 *     if (geometry.isEdgeAAQuad()) {
 *         SkASSERT(strokeRadius == 0.f);
 *         const EdgeAAQuad& quad = geometry.edgeAAQuad();
 *         if (quad.edgeFlags() == AAFlags::kNone) {
 *             // If all edges are non-AA, there won't be any insetting. This allows completely non-AA
 *             // quads to use the fill triangles for simpler fragment shader work.
 *             return false;
 *         } else if (quad.isRect() && quad.edgeFlags() == AAFlags::kAll) {
 *             return opposite_insets_intersect(quad.bounds(), 0.f, aaRadius);
 *         } else {
 *             // Quads with mixed AA edges are tiles where non-AA edges must seam perfectly together.
 *             // If we were to inset along just the axis with AA at a corner, two adjacent quads could
 *             // arrive at slightly different inset coordinates and then we wouldn't have a perfect
 *             // mesh. Forcing insets to snap to the center means all non-AA edges are formed solely
 *             // by the original quad coordinates and should seam perfectly assuming perfect input.
 *             // The only downside to this is the fill triangles cannot be used since they would
 *             // partially extend into the coverage ramp from adjacent AA edges.
 *             return true;
 *         }
 *     } else {
 *         const Shape& shape = geometry.shape();
 *         if (shape.isLine()) {
 *             return strokeRadius <= aaRadius;
 *         } else if (shape.isRect()) {
 *             return opposite_insets_intersect(shape.rect(), strokeRadius, aaRadius);
 *         } else {
 *             SkASSERT(shape.isRRect());
 *             return opposite_insets_intersect(shape.rrect(), strokeRadius, aaRadius);
 *         }
 *     }
 * }
 * ```
 */
public fun oppositeInsetsIntersect(
  geometry: Geometry,
  strokeRadius: Float,
  aaRadius: Float,
): Boolean {
  TODO("Implement oppositeInsetsIntersect")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_clockwise(const EdgeAAQuad& quad) {
 *     if (quad.isRect()) {
 *         return true; // by construction, these are always locally clockwise
 *     }
 *
 *     // This assumes that each corner has a consistent winding, which is the case for convex inputs,
 *     // which is an assumption of the per-edge AA API. Check the sign of cross product between the
 *     // first two edges.
 *     const skvx::float4& xs = quad.xs();
 *     const skvx::float4& ys = quad.ys();
 *
 *     float winding = (xs[0] - xs[3])*(ys[1] - ys[0]) - (ys[0] - ys[3])*(xs[1] - xs[0]);
 *     if (winding == 0.f) {
 *         // The input possibly forms a triangle with duplicate vertices, so check the opposite corner
 *         winding = (xs[2] - xs[1])*(ys[3] - ys[2]) - (ys[2] - ys[1])*(xs[3] - xs[2]);
 *     }
 *
 *     // At this point if winding is < 0, the quad's vertices are CCW. If it's still 0, the vertices
 *     // form a line, in which case the vertex shader constructs a correct CW winding. Otherwise,
 *     // the quad or triangle vertices produce a positive winding and are CW.
 *     return winding >= 0.f;
 * }
 * ```
 */
public fun isClockwise(quad: EdgeAAQuad): Boolean {
  TODO("Implement isClockwise")
}

/**
 * C++ original:
 * ```cpp
 * static skvx::float2 quad_center(const EdgeAAQuad& quad) {
 *     // The center of the bounding box is *not* a good center to use. Take the average of the
 *     // four points instead (which is slightly biased if they form a triangle, but still okay).
 *     return skvx::float2(dot(quad.xs(), skvx::float4(0.25f)),
 *                         dot(quad.ys(), skvx::float4(0.25f)));
 * }
 * ```
 */
public fun quadCenter(quad: EdgeAAQuad): Float2 {
  TODO("Implement quadCenter")
}

/**
 * C++ original:
 * ```cpp
 * static void write_vertex_buffer(VertexWriter writer) {
 *     // Normalized geometry for octagons that circumscribe/inscribe a unit circle.
 *     // Outer ring offset
 *     static constexpr float kOctOffset = 0.41421356237f;  // sqrt(2) - 1
 *     // Inner ring points
 *     static constexpr SkScalar kCosPi8 = 0.923579533f;
 *     static constexpr SkScalar kSinPi8 = 0.382683432f;
 *
 *     // Directional offset for anti-aliasing.
 *     // Also used as marker for whether this is an outer or inner vertex.
 *     static constexpr float kOuterAAOffset = 0.5f;
 *     static constexpr float kInnerAAOffset = -0.5f;
 *
 *     static constexpr SkV3 kOctagonVertices[kVertexCount] = {
 *         {-kOctOffset, -1,          kOuterAAOffset},
 *         {-kSinPi8,    -kCosPi8,    kInnerAAOffset},
 *         { kOctOffset, -1,          kOuterAAOffset},
 *         {kSinPi8,     -kCosPi8,    kInnerAAOffset},
 *         { 1,          -kOctOffset, kOuterAAOffset},
 *         {kCosPi8,     -kSinPi8,    kInnerAAOffset},
 *         { 1,           kOctOffset, kOuterAAOffset},
 *         {kCosPi8,      kSinPi8,    kInnerAAOffset},
 *         { kOctOffset,  1,          kOuterAAOffset},
 *         {kSinPi8,      kCosPi8,    kInnerAAOffset},
 *         {-kOctOffset,  1,          kOuterAAOffset},
 *         {-kSinPi8,     kCosPi8,    kInnerAAOffset},
 *         {-1,           kOctOffset, kOuterAAOffset},
 *         {-kCosPi8,     kSinPi8,    kInnerAAOffset},
 *         {-1,          -kOctOffset, kOuterAAOffset},
 *         {-kCosPi8,    -kSinPi8,    kInnerAAOffset},
 *         {-kOctOffset, -1,          kOuterAAOffset},
 *         {-kSinPi8,    -kCosPi8,    kInnerAAOffset},
 *     };
 *
 *     if (writer) {
 *         writer << kOctagonVertices;
 *     } // otherwise static buffer creation failed, so do nothing; Context initialization will fail.
 * }
 * ```
 */
public fun writeVertexBuffer(writer: VertexWriter) {
  TODO("Implement writeVertexBuffer")
}

/**
 * C++ original:
 * ```cpp
 * static skvx::float2 get_device_translation(const SkM44& localToDevice) {
 *     float m00 = localToDevice.rc(0,0), m01 = localToDevice.rc(0,1);
 *     float m10 = localToDevice.rc(1,0), m11 = localToDevice.rc(1,1);
 *
 *     float det = m00*m11 - m01*m10;
 *     if (SkScalarNearlyZero(det)) {
 *         // We can't extract any pre-translation, since the upper 2x2 is not invertible. Return (0,0)
 *         // so that the maskToDeviceRemainder matrix remains the full transform.
 *         return {0.f, 0.f};
 *     }
 *
 *     // Calculate inv([[m00,m01][m10,m11]])*[[m30][m31]] to get the pre-remainder device translation.
 *     float tx = localToDevice.rc(0,3), ty = localToDevice.rc(1,3);
 *     skvx::float4 invT = skvx::float4{m11, -m10, -m01, m00} * skvx::float4{tx,tx,ty,ty};
 *     return (invT.xy() + invT.zw()) / det;
 * }
 * ```
 */
public fun getDeviceTranslation(localToDevice: SkM44): Float2 {
  TODO("Implement getDeviceTranslation")
}

/**
 * C++ original:
 * ```cpp
 * static void write_index_buffer(VertexWriter writer) {
 *     static constexpr uint16_t kTL = 0 * kCornerVertexCount;
 *     static constexpr uint16_t kTR = 1 * kCornerVertexCount;
 *     static constexpr uint16_t kBR = 2 * kCornerVertexCount;
 *     static constexpr uint16_t kBL = 3 * kCornerVertexCount;
 *
 *     static const uint16_t kIndices[kIndexCount] = {
 *         // Exterior AA ramp outset
 *         kTL+1,kTL+2,kTL+3,kTR+0,kTR+3,kTR+1,
 *         kTR+1,kTR+2,kTR+3,kBR+0,kBR+3,kBR+1,
 *         kBR+1,kBR+2,kBR+3,kBL+0,kBL+3,kBL+1,
 *         kBL+1,kBL+2,kBL+3,kTL+0,kTL+3,kTL+1,
 *         kTL+3,
 *         // Fill triangles
 *         kTL+3,kTR+3,kBL+3,kBR+3
 *     };
 *
 *     if (writer) {
 *         writer << kIndices;
 *     } // otherwise static buffer creation failed, so do nothing; Context initialization will fail.
 * }
 * ```
 */
public fun writeIndexBuffer(writer: VertexWriter) {
  TODO("Implement writeIndexBuffer")
}

/**
 * C++ original:
 * ```cpp
 * RenderStep::RenderStepID variant_id(PrimitiveType type, bool hasColor, bool hasTexCoords) {
 *     if (type == PrimitiveType::kTriangles) {
 *         if (hasColor) {
 *             if (hasTexCoords) {
 *                 return RenderStep::RenderStepID::kVertices_TrisColorTexCoords;
 *             } else {
 *                 return RenderStep::RenderStepID::kVertices_TrisColor;
 *             }
 *         } else {
 *             if (hasTexCoords) {
 *                 return RenderStep::RenderStepID::kVertices_TrisTexCoords;
 *             } else {
 *                 return RenderStep::RenderStepID::kVertices_Tris;
 *             }
 *         }
 *     } else {
 *         SkASSERT(type == PrimitiveType::kTriangleStrip);
 *
 *         if (hasColor) {
 *             if (hasTexCoords) {
 *                 return RenderStep::RenderStepID::kVertices_TristripsColorTexCoords;
 *             } else {
 *                 return RenderStep::RenderStepID::kVertices_TristripsColor;
 *             }
 *         } else {
 *             if (hasTexCoords) {
 *                 return RenderStep::RenderStepID::kVertices_TristripsTexCoords;
 *             } else {
 *                 return RenderStep::RenderStepID::kVertices_Tristrips;
 *             }
 *         }
 *     }
 * }
 * ```
 */
public fun variantId(
  type: PrimitiveType,
  hasColor: Boolean,
  hasTexCoords: Boolean,
): RenderStep.RenderStepID {
  TODO("Implement variantId")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<SkISize, SkIPoint> get_msaa_size_and_resolve_offset(const SkISize& targetSize,
 *                                                               const SkIRect& drawBounds,
 *                                                               const Caps& caps,
 *                                                               LoadOp loadOp) {
 *     if (caps.differentResolveAttachmentSizeSupport()) {
 *         // If possible, use approx size that can fit all draws. This reduces the MSAA texture size
 *         // and also reuses the textures better.
 *         // Note: we don't do this if loadOp=Clear because it's supposed to update the whole target
 *         // texture.
 *         auto smallEnoughBounds = drawBounds;
 *         if (loadOp != LoadOp::kClear && !smallEnoughBounds.isEmpty() &&
 *             smallEnoughBounds.intersect(SkIRect::MakeSize(targetSize))) {
 *             SkIPoint resolveOffset = smallEnoughBounds.topLeft();
 *             return {GetApproxSize(smallEnoughBounds.size()), resolveOffset};
 *         } else {
 *             return {GetApproxSize(targetSize), {0, 0}};
 *         }
 *     }
 *
 *     return {targetSize, {0, 0}};
 * }
 * ```
 */
public fun getMsaaSizeAndResolveOffset(
  targetSize: SkISize,
  drawBounds: SkIRect,
  caps: Caps,
  loadOp: LoadOp,
): Pair<SkISize, SkIPoint> {
  TODO("Implement getMsaaSizeAndResolveOffset")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<size_t, size_t> compute_combined_buffer_size(
 *         const Caps* caps,
 *         int mipLevelCount,
 *         size_t bytesPerBlock,
 *         const SkISize& baseDimensions,
 *         SkTextureCompressionType compressionType,
 *         TArray<std::pair<size_t, size_t>>* levelOffsetsAndRowBytes) {
 *     SkASSERT(levelOffsetsAndRowBytes && levelOffsetsAndRowBytes->empty());
 *     SkASSERT(mipLevelCount >= 1);
 *
 *     SkISize compressedBlockDimensions = CompressedDimensionsInBlocks(compressionType,
 *                                                                      baseDimensions);
 *
 *     size_t minTransferBufferAlignment =
 *             std::max(bytesPerBlock, caps->requiredTransferBufferAlignment());
 *     size_t alignedBytesPerRow =
 *             caps->getAlignedTextureDataRowBytes(compressedBlockDimensions.width() * bytesPerBlock);
 *
 *     levelOffsetsAndRowBytes->push_back({0, alignedBytesPerRow});
 *     size_t combinedBufferSize = SkAlignTo(alignedBytesPerRow * baseDimensions.height(),
 *                                           minTransferBufferAlignment);
 *     SkISize levelDimensions = baseDimensions;
 *
 *     for (int currentMipLevel = 1; currentMipLevel < mipLevelCount; ++currentMipLevel) {
 *         levelDimensions = {std::max(1, levelDimensions.width() / 2),
 *                            std::max(1, levelDimensions.height() / 2)};
 *         compressedBlockDimensions = CompressedDimensionsInBlocks(compressionType, levelDimensions);
 *         alignedBytesPerRow = caps->getAlignedTextureDataRowBytes(
 *                 compressedBlockDimensions.width() * bytesPerBlock);
 *         size_t alignedSize = SkAlignTo(alignedBytesPerRow * compressedBlockDimensions.height(),
 *                                        minTransferBufferAlignment);
 *         SkASSERT(combinedBufferSize % minTransferBufferAlignment == 0);
 *
 *         levelOffsetsAndRowBytes->push_back({combinedBufferSize, alignedBytesPerRow});
 *         combinedBufferSize += alignedSize;
 *     }
 *
 *     SkASSERT(levelOffsetsAndRowBytes->size() == mipLevelCount);
 *     SkASSERT(combinedBufferSize % minTransferBufferAlignment == 0);
 *     return {combinedBufferSize, minTransferBufferAlignment};
 * }
 * ```
 */
public fun computeCombinedBufferSize(
  caps: Caps?,
  mipLevelCount: Int,
  bytesPerBlock: ULong,
  baseDimensions: SkISize,
  compressionType: SkTextureCompressionType,
  levelOffsetsAndRowBytes: TArray<Pair<ULong, ULong>>?,
): Pair<ULong, ULong> {
  TODO("Implement computeCombinedBufferSize")
}

/**
 * C++ original:
 * ```cpp
 * template <typename INT_TYPE>
 * static void expand_bits(INT_TYPE* dst,
 *                         const uint8_t* src,
 *                         int width,
 *                         int height,
 *                         int dstRowBytes,
 *                         int srcRowBytes) {
 *     for (int y = 0; y < height; ++y) {
 *         int rowWritesLeft = width;
 *         const uint8_t* s = src;
 *         INT_TYPE* d = dst;
 *         while (rowWritesLeft > 0) {
 *             unsigned mask = *s++;
 *             for (int x = 7; x >= 0 && rowWritesLeft; --x, --rowWritesLeft) {
 *                 *d++ = (mask & (1 << x)) ? (INT_TYPE)(~0UL) : 0;
 *             }
 *         }
 *         dst = reinterpret_cast<INT_TYPE*>(reinterpret_cast<intptr_t>(dst) + dstRowBytes);
 *         src += srcRowBytes;
 *     }
 * }
 * ```
 */
public fun <INT_TYPE> expandBits(
  dst: INT_TYPE,
  src: UByte?,
  width: Int,
  height: Int,
  dstRowBytes: Int,
  srcRowBytes: Int,
) {
  TODO("Implement expandBits")
}

/**
 * C++ original:
 * ```cpp
 * static void get_packed_glyph_image(
 *         const SkGlyph& glyph, int dstRB, MaskFormat expectedMaskFormat, void* dst) {
 *     const int width = glyph.width();
 *     const int height = glyph.height();
 *     const void* src = glyph.image();
 *     SkASSERT(src != nullptr);
 *
 *     MaskFormat maskFormat = Glyph::FormatFromSkGlyph(glyph.maskFormat());
 *     if (maskFormat == expectedMaskFormat) {
 *         int srcRB = glyph.rowBytes();
 *         // Notice this comparison is with the glyphs raw mask format, and not its MaskFormat.
 *         if (glyph.maskFormat() != SkMask::kBW_Format) {
 *             if (srcRB != dstRB) {
 *                 const int bbp = MaskFormatBytesPerPixel(expectedMaskFormat);
 *                 for (int y = 0; y < height; y++) {
 *                     memcpy(dst, src, width * bbp);
 *                     src = (const char*) src + srcRB;
 *                     dst = (char*) dst + dstRB;
 *                 }
 *             } else {
 *                 memcpy(dst, src, dstRB * height);
 *             }
 *         } else {
 *             // Handle 8-bit format by expanding the mask to the expected format.
 *             const uint8_t* bits = reinterpret_cast<const uint8_t*>(src);
 *             switch (expectedMaskFormat) {
 *                 case MaskFormat::kA8: {
 *                     uint8_t* bytes = reinterpret_cast<uint8_t*>(dst);
 *                     expand_bits(bytes, bits, width, height, dstRB, srcRB);
 *                     break;
 *                 }
 *                 case MaskFormat::kA565: {
 *                     uint16_t* rgb565 = reinterpret_cast<uint16_t*>(dst);
 *                     expand_bits(rgb565, bits, width, height, dstRB, srcRB);
 *                     break;
 *                 }
 *                 default:
 *                     SK_ABORT("Invalid MaskFormat");
 *             }
 *         }
 *     } else if (maskFormat == MaskFormat::kA565 &&
 *                expectedMaskFormat == MaskFormat::kARGB) {
 *         // Convert if the glyph uses a 565 mask format since it is using LCD text rendering
 *         // but the expected format is 8888 (will happen on Intel MacOS with Metal since that
 *         // combination does not support 565).
 *         static constexpr SkMasks masks{
 *                 {0b1111'1000'0000'0000, 11, 5},  // Red
 *                 {0b0000'0111'1110'0000,  5, 6},  // Green
 *                 {0b0000'0000'0001'1111,  0, 5},  // Blue
 *                 {0, 0, 0}                        // Alpha
 *         };
 *         constexpr int a565Bpp = MaskFormatBytesPerPixel(MaskFormat::kA565);
 *         constexpr int argbBpp = MaskFormatBytesPerPixel(MaskFormat::kARGB);
 *         constexpr bool kBGRAIsNative = kN32_SkColorType == kBGRA_8888_SkColorType;
 *         char* dstRow = (char*)dst;
 *         for (int y = 0; y < height; y++) {
 *             dst = dstRow;
 *             for (int x = 0; x < width; x++) {
 *                 uint16_t color565 = 0;
 *                 memcpy(&color565, src, a565Bpp);
 *                 uint32_t color8888;
 *                 // On Windows (and possibly others), font data is stored as BGR.
 *                 // So we need to swizzle the data to reflect that.
 *                 if (kBGRAIsNative) {
 *                     color8888 = masks.getBlue(color565) |
 *                                 (masks.getGreen(color565) << 8) |
 *                                 (masks.getRed(color565) << 16) |
 *                                 (0xFF << 24);
 *                 } else {
 *                     color8888 = masks.getRed(color565) |
 *                                 (masks.getGreen(color565) << 8) |
 *                                 (masks.getBlue(color565) << 16) |
 *                                 (0xFF << 24);
 *                 }
 *                 memcpy(dst, &color8888, argbBpp);
 *                 src = (const char*)src + a565Bpp;
 *                 dst = (      char*)dst + argbBpp;
 *             }
 *             dstRow += dstRB;
 *         }
 *     } else {
 *         SkUNREACHABLE;
 *     }
 * }
 * ```
 */
public fun getPackedGlyphImage(
  glyph: SkGlyph,
  dstRB: Int,
  expectedMaskFormat: MaskFormat,
  dst: Unit?,
) {
  TODO("Implement getPackedGlyphImage")
}

/**
 * C++ original:
 * ```cpp
 * CFTypeRef GetMtlEvent(const BackendSemaphore& sem) {
 *     if (!sem.isValid() || sem.backend() != skgpu::BackendApi::kMetal) {
 *         return nullptr;
 *     }
 *     const MtlBackendSemaphoreData* mtlData = get_and_cast_data(sem);
 *     SkASSERT(mtlData);
 *     return mtlData->event();
 * }
 * ```
 */
public fun getMtlEvent(sem: BackendSemaphore): CFTypeRef {
  TODO("Implement getMtlEvent")
}

/**
 * C++ original:
 * ```cpp
 * uint64_t GetMtlValue(const BackendSemaphore& sem) {
 *     if (!sem.isValid() || sem.backend() != skgpu::BackendApi::kMetal) {
 *         return 0;
 *     }
 *     const MtlBackendSemaphoreData* mtlData = get_and_cast_data(sem);
 *     SkASSERT(mtlData);
 *     return mtlData->value();
 * }
 * ```
 */
public fun getMtlValue(sem: BackendSemaphore): ULong {
  TODO("Implement getMtlValue")
}

/**
 * C++ original:
 * ```cpp
 * static const MtlBackendTextureData* get_and_cast_data(const BackendTexture& tex) {
 *     auto data = BackendTexturePriv::GetData(tex);
 *     SkASSERT(!data || data->type() == skgpu::BackendApi::kMetal);
 *     return static_cast<const MtlBackendTextureData*>(data);
 * }
 * ```
 */
public fun getAndCastData(tex: BackendTexture): MtlBackendTextureData {
  TODO("Implement getAndCastData")
}

/**
 * C++ original:
 * ```cpp
 * CFTypeRef GetMtlTexture(const BackendTexture& tex) {
 *     if (!tex.isValid() || tex.backend() != skgpu::BackendApi::kMetal) {
 *         return nullptr;
 *     }
 *     const MtlBackendTextureData* mtlData = get_and_cast_data(tex);
 *     SkASSERT(mtlData);
 *     return mtlData->texture();
 * }
 * ```
 */
public fun getMtlTexture(tex: BackendTexture): CFTypeRef {
  TODO("Implement getMtlTexture")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::UniqueKey::Domain get_domain() {
 *     static const skgpu::UniqueKey::Domain kMtlGraphicsPipelineDomain =
 *             skgpu::UniqueKey::GenerateDomain();
 *
 *     return kMtlGraphicsPipelineDomain;
 * }
 * ```
 */
public fun getDomain(): UniqueKeyDomain {
  TODO("Implement getDomain")
}

/**
 * C++ original:
 * ```cpp
 * MTLPixelFormat format_from_compression(SkTextureCompressionType compression) {
 *     switch (compression) {
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM:
 *             return kMTLPixelFormatETC2_RGB8;
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM:
 * #ifdef SK_BUILD_FOR_MAC
 *             return MTLPixelFormatBC1_RGBA;
 * #endif
 *         default:
 *             return MTLPixelFormatInvalid;
 *     }
 * }
 * ```
 */
public fun formatFromCompression(compression: SkTextureCompressionType): MTLPixelFormat {
  TODO("Implement formatFromCompression")
}

/**
 * C++ original:
 * ```cpp
 * static MTLPrimitiveType graphite_to_mtl_primitive(PrimitiveType primitiveType) {
 *     const static MTLPrimitiveType mtlPrimitiveType[] {
 *         MTLPrimitiveTypeTriangle,
 *         MTLPrimitiveTypeTriangleStrip,
 *         MTLPrimitiveTypePoint,
 *     };
 *     static_assert((int)PrimitiveType::kTriangles == 0);
 *     static_assert((int)PrimitiveType::kTriangleStrip == 1);
 *     static_assert((int)PrimitiveType::kPoints == 2);
 *
 *     SkASSERT(primitiveType <= PrimitiveType::kPoints);
 *     return mtlPrimitiveType[static_cast<int>(primitiveType)];
 * }
 * ```
 */
public fun graphiteToMtlPrimitive(primitiveType: PrimitiveType): MTLPrimitiveType {
  TODO("Implement graphiteToMtlPrimitive")
}

/**
 * C++ original:
 * ```cpp
 * static bool check_max_blit_width(int widthInPixels) {
 *     if (widthInPixels > 32767) {
 *         SkASSERT(false); // surfaces should not be this wide anyway
 *         return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun checkMaxBlitWidth(widthInPixels: Int): Boolean {
  TODO("Implement checkMaxBlitWidth")
}

/**
 * C++ original:
 * ```cpp
 * sk_cfp<id<MTLLibrary>> MtlCompileShaderLibrary(const MtlSharedContext* sharedContext,
 *                                                std::string_view label,
 *                                                std::string_view msl,
 *                                                ShaderErrorHandler* errorHandler) {
 *     TRACE_EVENT0("skia.shaders", "driver_compile_shader");
 *     NSString* nsSource = [[NSString alloc] initWithBytesNoCopy:const_cast<char*>(msl.data())
 *                                                         length:msl.size()
 *                                                       encoding:NSUTF8StringEncoding
 *                                                   freeWhenDone:NO];
 *     if (!nsSource) {
 *         return nil;
 *     }
 *     MTLCompileOptions* options = [[MTLCompileOptions alloc] init];
 *
 *     // Framebuffer fetch is supported in MSL 2.3 in MacOS 11+.
 *     if (@available(macOS 11.0, iOS 14.0, tvOS 14.0, *)) {
 *         options.languageVersion = MTLLanguageVersion2_3;
 *
 *     // array<> is supported in MSL 2.0 on MacOS 10.13+ and iOS 11+,
 *     // and in MSL 1.2 on iOS 10+ (but not MacOS).
 *     } else if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *         options.languageVersion = MTLLanguageVersion2_0;
 * #if defined(SK_BUILD_FOR_IOS)
 *     } else if (@available(macOS 10.12, iOS 10.0, tvOS 10.0, *)) {
 *         options.languageVersion = MTLLanguageVersion1_2;
 * #endif
 *     }
 *
 *     NSError* error = nil;
 *     // TODO: do we need a version with a timeout?
 *     sk_cfp<id<MTLLibrary>> compiledLibrary(
 *             [sharedContext->device() newLibraryWithSource:(NSString* _Nonnull)nsSource
 *                                                   options:options
 *                                                     error:&error]);
 *     if (!compiledLibrary) {
 *         std::string mslStr(msl);
 *         errorHandler->compileError(
 *                 mslStr.c_str(), error.debugDescription.UTF8String, /*shaderWasCached=*/false);
 *         return nil;
 *     }
 *
 *     NSString* nsLabel = [[NSString alloc] initWithBytesNoCopy:const_cast<char*>(label.data())
 *                                                        length:label.size()
 *                                                      encoding:NSUTF8StringEncoding
 *                                                  freeWhenDone:NO];
 *     compiledLibrary.get().label = nsLabel;
 *     return compiledLibrary;
 * }
 * ```
 */
public fun mtlCompileShaderLibrary(
  sharedContext: MtlSharedContext?,
  label: String,
  msl: String,
  errorHandler: ShaderErrorHandler?,
): SkCfp<Any> {
  TODO("Implement mtlCompileShaderLibrary")
}

/**
 * C++ original:
 * ```cpp
 * static void validate_mtl_pixelformats() {
 * #if defined(SK_BUILD_FOR_MAC)
 *     SkASSERT(MTLPixelFormatBC1_RGBA_ == MTLPixelFormatBC1_RGBA);
 *     SkASSERT(MTLPixelFormatBC1_RGBA_sRGB_ == MTLPixelFormatBC1_RGBA_sRGB);
 *     SkASSERT(MTLPixelFormatDepth24Unorm_Stencil8_ == MTLPixelFormatDepth24Unorm_Stencil8);
 * #endif
 *     if (@available(macOS 11.0, iOS 10.0, *)) {
 *         SkASSERT(MTLPixelFormatB5G6R5Unorm_ == MTLPixelFormatB5G6R5Unorm);
 *         SkASSERT(MTLPixelFormatABGR4Unorm_ == MTLPixelFormatABGR4Unorm);
 *         SkASSERT(MTLPixelFormatBGR10_XR_ == MTLPixelFormatBGR10_XR);
 *         SkASSERT(MTLPixelFormatBGRA10_XR_ == MTLPixelFormatBGRA10_XR);
 *         SkASSERT(MTLPixelFormatETC2_RGB8_ == MTLPixelFormatETC2_RGB8);
 *         SkASSERT(MTLPixelFormatETC2_RGB8_sRGB_ == MTLPixelFormatETC2_RGB8_sRGB);
 *     }
 * }
 * ```
 */
public fun validateMtlPixelformats() {
  TODO("Implement validateMtlPixelformats")
}

/**
 * C++ original:
 * ```cpp
 * TextureFormat MTLPixelFormatToTextureFormat(MTLPixelFormat format) {
 * #define M(TF, MTL) case MTL: return TF;
 *     switch(format) {
 *         MTL_FORMAT_MAPPING(M)
 *         default: return TextureFormat::kUnsupported;
 *     }
 * #undef M
 * }
 * ```
 */
public fun mTLPixelFormatToTextureFormat(format: MTLPixelFormat): TextureFormat {
  TODO("Implement mTLPixelFormatToTextureFormat")
}

/**
 * C++ original:
 * ```cpp
 * MTLPixelFormat TextureFormatToMTLPixelFormat(TextureFormat format) {
 *     // Validate constants that can't be statically validated due to @available
 *     validate_mtl_pixelformats();
 *
 * #define M(TF, MTL) case TF: return MTL;
 *     switch(format) {
 *         MTL_FORMAT_MAPPING(M)
 *         default: return MTLPixelFormatInvalid;
 *     }
 * #undef M
 * }
 * ```
 */
public fun textureFormatToMTLPixelFormat(format: TextureFormat): MTLPixelFormat {
  TODO("Implement textureFormatToMTLPixelFormat")
}

/**
 * C++ original:
 * ```cpp
 * inline MTLVertexFormat attribute_type_to_mtlformat(VertexAttribType type) {
 *     switch (type) {
 *         case VertexAttribType::kFloat:
 *             return MTLVertexFormatFloat;
 *         case VertexAttribType::kFloat2:
 *             return MTLVertexFormatFloat2;
 *         case VertexAttribType::kFloat3:
 *             return MTLVertexFormatFloat3;
 *         case VertexAttribType::kFloat4:
 *             return MTLVertexFormatFloat4;
 *         case VertexAttribType::kHalf:
 *             if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLVertexFormatHalf;
 *             } else {
 *                 return MTLVertexFormatInvalid;
 *             }
 *         case VertexAttribType::kHalf2:
 *             return MTLVertexFormatHalf2;
 *         case VertexAttribType::kHalf4:
 *             return MTLVertexFormatHalf4;
 *         case VertexAttribType::kInt2:
 *             return MTLVertexFormatInt2;
 *         case VertexAttribType::kInt3:
 *             return MTLVertexFormatInt3;
 *         case VertexAttribType::kInt4:
 *             return MTLVertexFormatInt4;
 *         case VertexAttribType::kUInt2:
 *             return MTLVertexFormatUInt2;
 *         case VertexAttribType::kByte:
 *             if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLVertexFormatChar;
 *             } else {
 *                 return MTLVertexFormatInvalid;
 *             }
 *         case VertexAttribType::kByte2:
 *             return MTLVertexFormatChar2;
 *         case VertexAttribType::kByte4:
 *             return MTLVertexFormatChar4;
 *         case VertexAttribType::kUByte:
 *             if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLVertexFormatUChar;
 *             } else {
 *                 return MTLVertexFormatInvalid;
 *             }
 *         case VertexAttribType::kUByte2:
 *             return MTLVertexFormatUChar2;
 *         case VertexAttribType::kUByte4:
 *             return MTLVertexFormatUChar4;
 *         case VertexAttribType::kUByte_norm:
 *             if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLVertexFormatUCharNormalized;
 *             } else {
 *                 return MTLVertexFormatInvalid;
 *             }
 *         case VertexAttribType::kUByte4_norm:
 *             return MTLVertexFormatUChar4Normalized;
 *         case VertexAttribType::kShort2:
 *             return MTLVertexFormatShort2;
 *         case VertexAttribType::kShort4:
 *             return MTLVertexFormatShort4;
 *         case VertexAttribType::kUShort2:
 *             return MTLVertexFormatUShort2;
 *         case VertexAttribType::kUShort2_norm:
 *             return MTLVertexFormatUShort2Normalized;
 *         case VertexAttribType::kInt:
 *             return MTLVertexFormatInt;
 *         case VertexAttribType::kUInt:
 *             return MTLVertexFormatUInt;
 *         case VertexAttribType::kUShort_norm:
 *             if (@available(macOS 10.13, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLVertexFormatUShortNormalized;
 *             } else {
 *                 return MTLVertexFormatInvalid;
 *             }
 *         case VertexAttribType::kUShort4_norm:
 *             return MTLVertexFormatUShort4Normalized;
 *     }
 *     SK_ABORT("Unknown vertex attribute type");
 * }
 * ```
 */
public fun attributeTypeToMtlformat(type: VertexAttribType): MTLVertexFormat {
  TODO("Implement attributeTypeToMtlformat")
}

/**
 * C++ original:
 * ```cpp
 * MTLVertexDescriptor* create_vertex_descriptor(MTLVertexStepFunction appendStepFunc,
 *                                               SkSpan<const Attribute> staticAttrs,
 *                                               SkSpan<const Attribute> appendAttrs) {
 *     auto vertexDescriptor = [[MTLVertexDescriptor alloc] init];
 *     int attributeIndex = 0;
 *
 *     size_t staticAttributeOffset = 0;
 *     for (const auto& attribute : staticAttrs) {
 *         MTLVertexAttributeDescriptor* mtlAttribute = vertexDescriptor.attributes[attributeIndex];
 *         MTLVertexFormat format = attribute_type_to_mtlformat(attribute.cpuType());
 *         SkASSERT(MTLVertexFormatInvalid != format);
 *         mtlAttribute.format = format;
 *         mtlAttribute.offset = staticAttributeOffset;
 *         mtlAttribute.bufferIndex = MtlGraphicsPipeline::kStaticDataBufferIndex;
 *
 *         staticAttributeOffset += attribute.sizeAlign4();
 *         attributeIndex++;
 *     }
 *
 *     if (staticAttributeOffset) {
 *         MTLVertexBufferLayoutDescriptor* staticDataBufferLayout =
 *                 vertexDescriptor.layouts[MtlGraphicsPipeline::kStaticDataBufferIndex];
 *         staticDataBufferLayout.stepFunction = MTLVertexStepFunctionPerVertex;
 *         staticDataBufferLayout.stepRate = 1;
 *         staticDataBufferLayout.stride = staticAttributeOffset;
 *     }
 *
 *     size_t appendAttributeOffset = 0;
 *     for (const auto& attribute : appendAttrs) {
 *         MTLVertexAttributeDescriptor* mtlAttribute = vertexDescriptor.attributes[attributeIndex];
 *         MTLVertexFormat format = attribute_type_to_mtlformat(attribute.cpuType());
 *         SkASSERT(MTLVertexFormatInvalid != format);
 *         mtlAttribute.format = format;
 *         mtlAttribute.offset = appendAttributeOffset;
 *         mtlAttribute.bufferIndex = MtlGraphicsPipeline::kAppendDataBufferIndex;
 *
 *         appendAttributeOffset += attribute.sizeAlign4();
 *         attributeIndex++;
 *     }
 *
 *     if (appendAttributeOffset) {
 *         MTLVertexBufferLayoutDescriptor* appendBufferDataLayout =
 *                 vertexDescriptor.layouts[MtlGraphicsPipeline::kAppendDataBufferIndex];
 *         appendBufferDataLayout.stepFunction = appendStepFunc;
 *         appendBufferDataLayout.stepRate = 1;
 *         appendBufferDataLayout.stride = appendAttributeOffset;
 *     }
 *     return vertexDescriptor;
 * }
 * ```
 */
public fun createVertexDescriptor(
  appendStepFunc: MTLVertexStepFunction,
  staticAttrs: SkSpan<Attribute>,
  appendAttrs: SkSpan<Attribute>,
): Int {
  TODO("Implement createVertexDescriptor")
}

/**
 * C++ original:
 * ```cpp
 * static MTLBlendFactor blend_coeff_to_mtl_blend(skgpu::BlendCoeff coeff) {
 *     switch (coeff) {
 *         case skgpu::BlendCoeff::kZero:
 *             return MTLBlendFactorZero;
 *         case skgpu::BlendCoeff::kOne:
 *             return MTLBlendFactorOne;
 *         case skgpu::BlendCoeff::kSC:
 *             return MTLBlendFactorSourceColor;
 *         case skgpu::BlendCoeff::kISC:
 *             return MTLBlendFactorOneMinusSourceColor;
 *         case skgpu::BlendCoeff::kDC:
 *             return MTLBlendFactorDestinationColor;
 *         case skgpu::BlendCoeff::kIDC:
 *             return MTLBlendFactorOneMinusDestinationColor;
 *         case skgpu::BlendCoeff::kSA:
 *             return MTLBlendFactorSourceAlpha;
 *         case skgpu::BlendCoeff::kISA:
 *             return MTLBlendFactorOneMinusSourceAlpha;
 *         case skgpu::BlendCoeff::kDA:
 *             return MTLBlendFactorDestinationAlpha;
 *         case skgpu::BlendCoeff::kIDA:
 *             return MTLBlendFactorOneMinusDestinationAlpha;
 *         case skgpu::BlendCoeff::kConstC:
 *             return MTLBlendFactorBlendColor;
 *         case skgpu::BlendCoeff::kIConstC:
 *             return MTLBlendFactorOneMinusBlendColor;
 *         case skgpu::BlendCoeff::kS2C:
 *             if (@available(macOS 10.12, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLBlendFactorSource1Color;
 *             } else {
 *                 return MTLBlendFactorZero;
 *             }
 *         case skgpu::BlendCoeff::kIS2C:
 *             if (@available(macOS 10.12, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLBlendFactorOneMinusSource1Color;
 *             } else {
 *                 return MTLBlendFactorZero;
 *             }
 *         case skgpu::BlendCoeff::kS2A:
 *             if (@available(macOS 10.12, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLBlendFactorSource1Alpha;
 *             } else {
 *                 return MTLBlendFactorZero;
 *             }
 *         case skgpu::BlendCoeff::kIS2A:
 *             if (@available(macOS 10.12, iOS 11.0, tvOS 11.0, *)) {
 *                 return MTLBlendFactorOneMinusSource1Alpha;
 *             } else {
 *                 return MTLBlendFactorZero;
 *             }
 *         case skgpu::BlendCoeff::kIllegal:
 *             return MTLBlendFactorZero;
 *     }
 *
 *     SK_ABORT("Unknown blend coefficient");
 * }
 * ```
 */
public fun blendCoeffToMtlBlend(coeff: BlendCoeff): MTLBlendFactor {
  TODO("Implement blendCoeffToMtlBlend")
}

/**
 * C++ original:
 * ```cpp
 * static MTLBlendOperation blend_equation_to_mtl_blend_op(skgpu::BlendEquation equation) {
 *     static const MTLBlendOperation gTable[] = {
 *             MTLBlendOperationAdd,              // skgpu::BlendEquation::kAdd
 *             MTLBlendOperationSubtract,         // skgpu::BlendEquation::kSubtract
 *             MTLBlendOperationReverseSubtract,  // skgpu::BlendEquation::kReverseSubtract
 *     };
 *     static_assert(std::size(gTable) == (int)skgpu::BlendEquation::kFirstAdvanced);
 *     static_assert(0 == (int)skgpu::BlendEquation::kAdd);
 *     static_assert(1 == (int)skgpu::BlendEquation::kSubtract);
 *     static_assert(2 == (int)skgpu::BlendEquation::kReverseSubtract);
 *
 *     SkASSERT((unsigned)equation < skgpu::kBlendEquationCnt);
 *     return gTable[(int)equation];
 * }
 * ```
 */
public fun blendEquationToMtlBlendOp(equation: BlendEquation): MTLBlendOperation {
  TODO("Implement blendEquationToMtlBlendOp")
}

/**
 * C++ original:
 * ```cpp
 * static MTLRenderPipelineColorAttachmentDescriptor* create_color_attachment(
 *         MTLPixelFormat format,
 *         const BlendInfo& blendInfo) {
 *
 *     skgpu::BlendEquation equation = blendInfo.fEquation;
 *     skgpu::BlendCoeff srcCoeff = blendInfo.fSrcBlend;
 *     skgpu::BlendCoeff dstCoeff = blendInfo.fDstBlend;
 *     bool blendOn = !skgpu::BlendShouldDisable(equation, srcCoeff, dstCoeff);
 *
 *     // TODO: I *think* this gets cleaned up by the pipelineDescriptor?
 *     auto mtlColorAttachment = [[MTLRenderPipelineColorAttachmentDescriptor alloc] init];
 *
 *     mtlColorAttachment.pixelFormat = format;
 *
 *     mtlColorAttachment.blendingEnabled = blendOn;
 *
 *     if (blendOn) {
 *         mtlColorAttachment.sourceRGBBlendFactor = blend_coeff_to_mtl_blend(srcCoeff);
 *         mtlColorAttachment.destinationRGBBlendFactor = blend_coeff_to_mtl_blend(dstCoeff);
 *         mtlColorAttachment.rgbBlendOperation = blend_equation_to_mtl_blend_op(equation);
 *         mtlColorAttachment.sourceAlphaBlendFactor = blend_coeff_to_mtl_blend(srcCoeff);
 *         mtlColorAttachment.destinationAlphaBlendFactor = blend_coeff_to_mtl_blend(dstCoeff);
 *         mtlColorAttachment.alphaBlendOperation = blend_equation_to_mtl_blend_op(equation);
 *     }
 *
 *     mtlColorAttachment.writeMask = blendInfo.fWritesColor ? MTLColorWriteMaskAll
 *                                                           : MTLColorWriteMaskNone;
 *
 *     return mtlColorAttachment;
 * }
 * ```
 */
public fun createColorAttachment(format: MTLPixelFormat, blendInfo: BlendInfo): Int {
  TODO("Implement createColorAttachment")
}

/**
 * C++ original:
 * ```cpp
 * static inline MTLSamplerAddressMode tile_mode_to_mtl_sampler_address(SkTileMode tileMode,
 *                                                                      const Caps& caps) {
 *     switch (tileMode) {
 *         case SkTileMode::kClamp:
 *             return MTLSamplerAddressModeClampToEdge;
 *         case SkTileMode::kRepeat:
 *             return MTLSamplerAddressModeRepeat;
 *         case SkTileMode::kMirror:
 *             return MTLSamplerAddressModeMirrorRepeat;
 *         case SkTileMode::kDecal:
 *             // For this tilemode, we should have checked that clamp-to-border support exists.
 *             // If it doesn't we should have fallen back to a shader instead.
 *             // TODO: for textures with alpha, we could use ClampToZero if there's no
 *             // ClampToBorderColor as they'll clamp to (0,0,0,0).
 *             // Unfortunately textures without alpha end up clamping to (0,0,0,1).
 *             if (@available(macOS 10.12, iOS 14.0, tvOS 14.0, *)) {
 *                 SkASSERT(caps.clampToBorderSupport());
 *                 return MTLSamplerAddressModeClampToBorderColor;
 *             } else {
 *                 SkASSERT(false);
 *                 return MTLSamplerAddressModeClampToZero;
 *             }
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun tileModeToMtlSamplerAddress(tileMode: SkTileMode, caps: Caps): MTLSamplerAddressMode {
  TODO("Implement tileModeToMtlSamplerAddress")
}

/**
 * C++ original:
 * ```cpp
 * MTLCompareFunction compare_op_to_mtl(CompareOp op) {
 *     switch (op) {
 *         case CompareOp::kAlways:
 *             return MTLCompareFunctionAlways;
 *         case CompareOp::kNever:
 *             return MTLCompareFunctionNever;
 *         case CompareOp::kGreater:
 *             return MTLCompareFunctionGreater;
 *         case CompareOp::kGEqual:
 *             return MTLCompareFunctionGreaterEqual;
 *         case CompareOp::kLess:
 *             return MTLCompareFunctionLess;
 *         case CompareOp::kLEqual:
 *             return MTLCompareFunctionLessEqual;
 *         case CompareOp::kEqual:
 *             return MTLCompareFunctionEqual;
 *         case CompareOp::kNotEqual:
 *             return MTLCompareFunctionNotEqual;
 *     }
 * }
 * ```
 */
public fun compareOpToMtl(op: CompareOp): MTLCompareFunction {
  TODO("Implement compareOpToMtl")
}

/**
 * C++ original:
 * ```cpp
 * MTLStencilOperation stencil_op_to_mtl(StencilOp op) {
 *     switch (op) {
 *         case StencilOp::kKeep:
 *             return MTLStencilOperationKeep;
 *         case StencilOp::kZero:
 *             return MTLStencilOperationZero;
 *         case StencilOp::kReplace:
 *             return MTLStencilOperationReplace;
 *         case StencilOp::kInvert:
 *             return MTLStencilOperationInvert;
 *         case StencilOp::kIncWrap:
 *             return MTLStencilOperationIncrementWrap;
 *         case StencilOp::kDecWrap:
 *             return MTLStencilOperationDecrementWrap;
 *         case StencilOp::kIncClamp:
 *             return MTLStencilOperationIncrementClamp;
 *         case StencilOp::kDecClamp:
 *             return MTLStencilOperationDecrementClamp;
 *     }
 * }
 * ```
 */
public fun stencilOpToMtl(op: StencilOp): MTLStencilOperation {
  TODO("Implement stencilOpToMtl")
}

/**
 * C++ original:
 * ```cpp
 * MTLStencilDescriptor* stencil_face_to_mtl(DepthStencilSettings::Face face) {
 *     MTLStencilDescriptor* result = [[MTLStencilDescriptor alloc] init];
 *     result.stencilCompareFunction = compare_op_to_mtl(face.fCompareOp);
 *     result.readMask = face.fReadMask;
 *     result.writeMask = face.fWriteMask;
 *     result.depthStencilPassOperation = stencil_op_to_mtl(face.fDepthStencilPassOp);
 *     result.stencilFailureOperation = stencil_op_to_mtl(face.fStencilFailOp);
 *     return result;
 * }
 * ```
 */
public fun stencilFaceToMtl(face: DepthStencilSettings.Face): Int {
  TODO("Implement stencilFaceToMtl")
}

/**
 * C++ original:
 * ```cpp
 * static bool has_transient_usage(const TextureInfo& info) {
 *     if (@available(macOS 11.0, iOS 10.0, tvOS 10.0, *)) {
 *         const auto& mtlInfo = TextureInfoPriv::Get<MtlTextureInfo>(info);
 *         return mtlInfo.fStorageMode == MTLStorageModeMemoryless;
 *     }
 *     return false;
 * }
 * ```
 */
public fun hasTransientUsage(info: TextureInfo): Boolean {
  TODO("Implement hasTransientUsage")
}

/**
 * C++ original:
 * ```cpp
 * void compile(const RendererProvider* rendererProvider,
 *              ResourceProvider* resourceProvider,
 *              const KeyContext& keyContext,
 *              UniquePaintParamsID uniqueID,
 *              DrawTypeFlags drawTypes,
 *              const RenderPassDesc& renderPassDesc,
 *              bool withPrimitiveBlender,
 *              Coverage coverage) {
 *
 *     for (const Renderer* r : rendererProvider->renderers()) {
 *         if (!(r->drawTypes() & drawTypes)) {
 *             continue;
 *         }
 *
 *         if (r->emitsPrimitiveColor() != withPrimitiveBlender) {
 *             // UniqueIDs are explicitly built either w/ or w/o primitiveBlending so must
 *             // match what the Renderer requires
 *             continue;
 *         }
 *
 *         if (r->coverage() != coverage) {
 *             // For now, UniqueIDs are explicitly built with a specific type of coverage so must
 *             // match what the Renderer requires
 *             continue;
 *         }
 *
 *         for (auto&& s : r->steps()) {
 *             SkASSERT(!s->performsShading() || s->emitsPrimitiveColor() == withPrimitiveBlender);
 *
 *             UniquePaintParamsID paintID = s->performsShading() ? uniqueID
 *                                                                : UniquePaintParamsID::Invalid();
 *
 *             GraphicsPipelineHandle handle = resourceProvider->createGraphicsPipelineHandle(
 *                     { s->renderStepID(), paintID },
 *                     renderPassDesc,
 *                     PipelineCreationFlags::kForPrecompilation);
 *             resourceProvider->startPipelineCreationTask(keyContext.rtEffectDict(), handle);
 *         }
 *     }
 * }
 * ```
 */
public fun compile(
  rendererProvider: RendererProvider?,
  resourceProvider: ResourceProvider?,
  keyContext: KeyContext,
  uniqueID: UniquePaintParamsID,
  drawTypes: DrawTypeFlags,
  renderPassDesc: RenderPassDesc,
  withPrimitiveBlender: Boolean,
  coverage: Coverage,
) {
  TODO("Implement compile")
}

/**
 * C++ original:
 * ```cpp
 * void Precompile(PrecompileContext* precompileContext,
 *                 const PaintOptions& options,
 *                 DrawTypeFlags drawTypes,
 *                 SkSpan<const RenderPassProperties> renderPassProperties) {
 *
 *     ShaderCodeDictionary* dict = precompileContext->priv().shaderCodeDictionary();
 *     const RendererProvider* rendererProvider = precompileContext->priv().rendererProvider();
 *     ResourceProvider* resourceProvider = precompileContext->priv().resourceProvider();
 *     const Caps* caps = precompileContext->priv().caps();
 *
 *     sk_sp<RuntimeEffectDictionary> rtEffectDict = sk_make_sp<RuntimeEffectDictionary>();
 *
 *     for (const RenderPassProperties& rpp : renderPassProperties) {
 *         // TODO: Allow the client to pass in mipmapping and protection too?
 *         TextureInfo info = caps->getDefaultSampledTextureInfo(rpp.fDstCT,
 *                                                               Mipmapped::kNo,
 *                                                               Protected::kNo,
 *                                                               Renderable::kYes);
 *
 *         Swizzle writeSwizzle = caps->getWriteSwizzle(rpp.fDstCT, info);
 *
 *         // TODO(robertphillips): address mismatches between the MSAA requirements of the Renderers
 *         // associated w/ the requested drawTypes and the specified MSAA setting
 *
 *         // On Native Metal, the LoadOp, StoreOp and clearColor fields don't influence
 *         // the actual RenderPassDescKey.
 *         // For Dawn, the LoadOp will sometimes matter. We add an extra LoadOp::kLoad combination
 *         // when necessary.
 *         const LoadOp kLoadOps[2] = { LoadOp::kClear, LoadOp::kLoad };
 *
 *         int numLoadOps = 1;
 *         if (rpp.fRequiresMSAA &&
 *             !caps->msaaRenderToSingleSampledSupport() &&
 *             caps->loadOpAffectsMSAAPipelines()) {
 *             numLoadOps = 2;
 *         }
 *
 *         for (int loadOpIndex = 0; loadOpIndex < numLoadOps; ++loadOpIndex) {
 *             const RenderPassDesc renderPassDesc =
 *                     RenderPassDesc::Make(caps,
 *                                          info,
 *                                          kLoadOps[loadOpIndex],
 *                                          StoreOp::kStore,
 *                                          rpp.fDSFlags,
 *                                          /* clearColor= */ { .0f, .0f, .0f, .0f },
 *                                          rpp.fRequiresMSAA,
 *                                          writeSwizzle,
 *                                          caps->getDstReadStrategy());
 *
 *             SkColorInfo ci(rpp.fDstCT, kPremul_SkAlphaType, rpp.fDstCS);
 *
 *             // The PipelineDataGatherer and FloatStorageManager are only used to accumulate uniform
 *             // data. In the pre-compile case we don't need to record the uniform data but the
 *             // process of generating it is required to create the correct key.
 *             FloatStorageManager floatStorageManager;
 *             PipelineDataGatherer gatherer(Layout::kMetal);
 *             PaintParamsKeyBuilder builder(dict);
 *             KeyContext keyContext(caps, &floatStorageManager, &builder, &gatherer, dict,
 *                                   rtEffectDict, ci);
 *
 *             for (Coverage coverage : { Coverage::kNone, Coverage::kSingleChannel }) {
 *                 PrecompileCombinations(
 *                         rendererProvider,
 *                         resourceProvider,
 *                         options, keyContext,
 *                         static_cast<DrawTypeFlags>(drawTypes & ~(DrawTypeFlags::kBitmapText_Color |
 *                                                                  DrawTypeFlags::kBitmapText_LCD |
 *                                                                  DrawTypeFlags::kSDFText_LCD |
 *                                                                  DrawTypeFlags::kDrawVertices |
 *                                                                  DrawTypeFlags::kDropShadows)),
 *                         /* withPrimitiveBlender= */ false,
 *                         coverage,
 *                         renderPassDesc);
 *             }
 *
 *             if (drawTypes & DrawTypeFlags::kNonSimpleShape) {
 *                 // Special case handling to pick up the:
 *                 //     "CoverBoundsRenderStep[InverseCover] + (empty)"
 *                 // pipelines.
 *                 const RenderStep* renderStep =
 *                     rendererProvider->lookup(RenderStep::RenderStepID::kCoverBounds_InverseCover);
 *
 *                 GraphicsPipelineHandle handle = resourceProvider->createGraphicsPipelineHandle(
 *                         { renderStep->renderStepID(), UniquePaintParamsID::Invalid() },
 *                         renderPassDesc,
 *                         PipelineCreationFlags::kForPrecompilation);
 *                 resourceProvider->startPipelineCreationTask(keyContext.rtEffectDict(), handle);
 *             }
 *
 *             if (drawTypes & DrawTypeFlags::kBitmapText_Color) {
 *                 DrawTypeFlags reducedTypes =
 *                         static_cast<DrawTypeFlags>(drawTypes & (DrawTypeFlags::kBitmapText_Color |
 *                                                                 DrawTypeFlags::kAnalyticClip));
 *                 // For color emoji text, shaders don't affect the final color
 *                 PaintOptions tmp = options;
 *                 tmp.setShaders({});
 *
 *                 // ARGB text doesn't emit coverage and always has a primitive blender
 *                 PrecompileCombinations(rendererProvider,
 *                                        resourceProvider,
 *                                        tmp,
 *                                        keyContext,
 *                                        reducedTypes,
 *                                        /* withPrimitiveBlender= */ true,
 *                                        Coverage::kNone,
 *                                        renderPassDesc);
 *             }
 *
 *             if (drawTypes & (DrawTypeFlags::kBitmapText_LCD | DrawTypeFlags::kSDFText_LCD)) {
 *                 DrawTypeFlags reducedTypes =
 *                         static_cast<DrawTypeFlags>(drawTypes & (DrawTypeFlags::kBitmapText_LCD |
 *                                                                 DrawTypeFlags::kSDFText_LCD |
 *                                                                 DrawTypeFlags::kAnalyticClip));
 *                 // LCD-based text always emits LCD coverage but never has primitiveBlenders
 *                 PrecompileCombinations(
 *                         rendererProvider,
 *                         resourceProvider,
 *                         options, keyContext,
 *                         reducedTypes,
 *                         /* withPrimitiveBlender= */ false,
 *                         Coverage::kLCD,
 *                         renderPassDesc);
 *             }
 *
 *             if (drawTypes & DrawTypeFlags::kDrawVertices) {
 *                 DrawTypeFlags reducedTypes =
 *                         static_cast<DrawTypeFlags>(drawTypes & (DrawTypeFlags::kDrawVertices |
 *                                                                 DrawTypeFlags::kAnalyticClip));
 *                 // drawVertices w/ colors use a primitiveBlender while those w/o don't. It never
 *                 // emits coverage.
 *                 for (bool withPrimitiveBlender : { true, false }) {
 *                     PrecompileCombinations(rendererProvider,
 *                                            resourceProvider,
 *                                            options, keyContext,
 *                                            reducedTypes,
 *                                            withPrimitiveBlender,
 *                                            Coverage::kNone,
 *                                            renderPassDesc);
 *                 }
 *             }
 *
 *             if (drawTypes & DrawTypeFlags::kDropShadows) {
 *                 DrawTypeFlags reducedTypes =
 *                         static_cast<DrawTypeFlags>(drawTypes & (DrawTypeFlags::kDropShadows |
 *                                                                 DrawTypeFlags::kAnalyticClip));
 *
 *                 PaintOptions newOptions;
 *                 newOptions.setBlendModes(SKSPAN_INIT_ONE(SkBlendMode::kSrcOver));
 *
 *                 // Analytic
 *                 {
 *                     PrecompileCombinations(rendererProvider,
 *                                            resourceProvider,
 *                                            newOptions, keyContext,
 *                                            reducedTypes,
 *                                            /* withPrimitiveBlender= */ false,
 *                                            Coverage::kSingleChannel,
 *                                            renderPassDesc);
 *                 }
 *
 *                 // Geometric
 *                 {
 *                     sk_sp<PrecompileColorFilter> cf = PrecompileColorFilters::Compose(
 *                             {{ PrecompileColorFilters::Blend(SKSPAN_INIT_ONE(SkBlendMode::kModulate)) }},
 *                             {{ PrecompileColorFiltersPriv::Gaussian() }});
 *
 *                     newOptions.setColorFilters({{ std::move(cf) }});
 *                     newOptions.priv().setPrimitiveBlendMode(SkBlendMode::kDst);
 *                     newOptions.priv().setSkipColorXform(true);
 *
 *                     PrecompileCombinations(rendererProvider,
 *                                            resourceProvider,
 *                                            newOptions, keyContext,
 *                                            reducedTypes,
 *                                            /* withPrimitiveBlender= */ true,
 *                                            Coverage::kNone,
 *                                            renderPassDesc);
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
public fun precompile(
  precompileContext: PrecompileContext?,
  options: PaintOptions,
  drawTypes: DrawTypeFlags,
  renderPassProperties: SkSpan<RenderPassProperties>,
) {
  TODO("Implement precompile")
}

/**
 * C++ original:
 * ```cpp
 * void PrecompileCombinations(const RendererProvider* rendererProvider,
 *                             ResourceProvider* resourceProvider,
 *                             const PaintOptions& options,
 *                             const KeyContext& keyContext,
 *                             DrawTypeFlags drawTypes,
 *                             bool withPrimitiveBlender,
 *                             Coverage coverage,
 *                             const RenderPassDesc& renderPassDescIn) {
 *     if (drawTypes == DrawTypeFlags::kNone) {
 *         return;
 *     }
 *
 *     options.priv().buildCombinations(
 *         keyContext,
 *         drawTypes,
 *         withPrimitiveBlender,
 *         coverage,
 *         renderPassDescIn,
 *         [rendererProvider, resourceProvider, &keyContext](UniquePaintParamsID uniqueID,
 *                                                           DrawTypeFlags drawTypes,
 *                                                           bool withPrimitiveBlender,
 *                                                           Coverage coverage,
 *                                                           const RenderPassDesc& renderPassDesc) {
 *                compile(rendererProvider,
 *                        resourceProvider,
 *                        keyContext,
 *                        uniqueID,
 *                        drawTypes,
 *                        renderPassDesc,
 *                        withPrimitiveBlender,
 *                        coverage);
 *         });
 * }
 * ```
 */
public fun precompileCombinations(
  rendererProvider: RendererProvider?,
  resourceProvider: ResourceProvider?,
  options: PaintOptions,
  keyContext: KeyContext,
  drawTypes: DrawTypeFlags,
  withPrimitiveBlender: Boolean,
  coverage: Coverage,
  renderPassDescIn: RenderPassDesc,
) {
  TODO("Implement precompileCombinations")
}

/**
 * C++ original:
 * ```cpp
 * MtlTextureInfo::MtlTextureInfo(CFTypeRef texture) {
 *     SkASSERT(texture);
 *     id<MTLTexture> mtlTex = (id<MTLTexture>)texture;
 *
 *     fSampleCount = ToSampleCount(mtlTex.sampleCount);
 *     fMipmapped = mtlTex.mipmapLevelCount > 1 ? Mipmapped::kYes : Mipmapped::kNo;
 *
 *     fFormat = mtlTex.pixelFormat;
 *     fUsage = mtlTex.usage;
 *     fStorageMode = mtlTex.storageMode;
 *     fFramebufferOnly = mtlTex.framebufferOnly;
 * }
 * ```
 */
public fun mtlTextureInfo(texture: CFTypeRef) {
  TODO("Implement mtlTextureInfo")
}

/**
 * C++ original:
 * ```cpp
 * TextureFormat MtlTextureInfo::viewFormat() const {
 *     return MTLPixelFormatToTextureFormat(fFormat);
 * }
 * ```
 */
public fun viewFormat() {
  TODO("Implement viewFormat")
}

/**
 * C++ original:
 * ```cpp
 * SkString MtlTextureInfo::toBackendString() const {
 *     return SkStringPrintf("usage=0x%04X,storageMode=%u,framebufferOnly=%d",
 *                           (uint32_t)fUsage,
 *                           (uint32_t)fStorageMode,
 *                           fFramebufferOnly);
 * }
 * ```
 */
public fun toBackendString() {
  TODO("Implement toBackendString")
}

/**
 * C++ original:
 * ```cpp
 * bool MtlTextureInfo::isCompatible(const TextureInfo& that, bool requireExact) const {
 *     const auto& mt = TextureInfoPriv::Get<MtlTextureInfo>(that);
 *     // The usages may match or the usage passed in may be a superset of the usage stored within.
 *     const auto usageMask = requireExact ? mt.fUsage : fUsage;
 *     return fFormat == mt.fFormat &&
 *            fStorageMode == mt.fStorageMode &&
 *            fFramebufferOnly == mt.fFramebufferOnly &&
 *            (usageMask & mt.fUsage) == fUsage;
 * }
 * ```
 */
public fun isCompatible(that: TextureInfo, requireExact: Boolean) {
  TODO("Implement isCompatible")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::graphite::TextureInfo MakeMetal(const MtlTextureInfo& mtlInfo) {
 *     return TextureInfoPriv::Make(mtlInfo);
 * }
 * ```
 */
public fun makeMetal(mtlInfo: MtlTextureInfo): TextureInfo {
  TODO("Implement makeMetal")
}

/**
 * C++ original:
 * ```cpp
 * bool GetMtlTextureInfo(const TextureInfo& info, MtlTextureInfo* out) {
 *     return TextureInfoPriv::Copy(info, out);
 * }
 * ```
 */
public fun getMtlTextureInfo(info: TextureInfo, `out`: MtlTextureInfo?): Boolean {
  TODO("Implement getMtlTextureInfo")
}

/**
 * C++ original:
 * ```cpp
 * void create_image_drawing_pipelines(const KeyContext& keyContext,
 *                                     const PaintOptions& orig,
 *                                     const RenderPassDesc& renderPassDesc,
 *                                     const PaintOptionsPriv::ProcessCombination& processCombination) {
 *     PaintOptions imagePaintOptions;
 *
 *     // For imagefilters we know we don't have alpha-only textures and don't need cubic filtering.
 *     sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *             PrecompileShaders::ImageShaderFlags::kNoAlphaNoCubic);
 *
 *     imagePaintOptions.setShaders({{ imageShader }});
 *     imagePaintOptions.setBlendModes(orig.getBlendModes());
 *     imagePaintOptions.setBlenders(orig.getBlenders());
 *     imagePaintOptions.setColorFilters(orig.getColorFilters());
 *     imagePaintOptions.priv().addColorFilter(nullptr);
 *
 *     imagePaintOptions.priv().buildCombinations(keyContext,
 *                                                DrawTypeFlags::kSimpleShape,
 *                                                /* withPrimitiveBlender= */ false,
 *                                                Coverage::kSingleChannel,
 *                                                renderPassDesc,
 *                                                processCombination);
 * }
 * ```
 */
public fun createImageDrawingPipelines(
  keyContext: KeyContext,
  orig: PaintOptions,
  renderPassDesc: RenderPassDesc,
  processCombination: PaintOptionsPriv.ProcessCombination,
) {
  TODO("Implement createImageDrawingPipelines")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileBlender> PrecompileBlenders::Mode(SkBlendMode blendMode) {
 *     return sk_make_sp<PrecompileBlendModeBlender>(blendMode);
 * }
 * ```
 */
public fun mode(blendMode: SkBlendMode): SkSp<PrecompileBlender> {
  TODO("Implement mode")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::Matrix() {
 *     return sk_make_sp<PrecompileMatrixColorFilter>(/*inHSLA=*/false);
 * }
 * ```
 */
public fun matrix(): SkSp<PrecompileColorFilter> {
  TODO("Implement matrix")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::HSLAMatrix() {
 *     return sk_make_sp<PrecompileMatrixColorFilter>(/*inHSLA=*/true);
 * }
 * ```
 */
public fun hSLAMatrix(): SkSp<PrecompileColorFilter> {
  TODO("Implement hSLAMatrix")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::LinearToSRGBGamma() {
 *     return PrecompileColorFiltersPriv::ColorSpaceXform({{ SkColorSpace::MakeSRGBLinear() }},
 *                                                        {{ SkColorSpace::MakeSRGB() }});
 * }
 * ```
 */
public fun linearToSRGBGamma(): SkSp<PrecompileColorFilter> {
  TODO("Implement linearToSRGBGamma")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::SRGBToLinearGamma() {
 *     return PrecompileColorFiltersPriv::ColorSpaceXform({{ SkColorSpace::MakeSRGB() }},
 *                                                        {{ SkColorSpace::MakeSRGBLinear() }});
 * }
 * ```
 */
public fun sRGBToLinearGamma(): SkSp<PrecompileColorFilter> {
  TODO("Implement sRGBToLinearGamma")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFiltersPriv::ColorSpaceXform(
 *         SkSpan<const sk_sp<SkColorSpace>> src, SkSpan<const sk_sp<SkColorSpace>> dst) {
 *     return sk_make_sp<PrecompileColorSpaceXformColorFilter>(src, dst);
 * }
 * ```
 */
public fun colorSpaceXform(src: SkSpan<SkSp<SkColorSpace>>, dst: SkSpan<SkSp<SkColorSpace>>): SkSp<PrecompileColorFilter> {
  TODO("Implement colorSpaceXform")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::HighContrast() {
 *     const SkRuntimeEffect* highContrastEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kHighContrast);
 *
 *     sk_sp<PrecompileColorFilter> cf =
 *             PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(highContrastEffect));
 *     if (!cf) {
 *         return nullptr;
 *     }
 *
 *     // These color space working format arguments should match those from
 *     // src/effects/SkHighContrastFilter.cpp.
 *     const skcms_TransferFunction kTF = SkNamedTransferFn::kLinear;
 *     const SkAlphaType kUnpremul = kUnpremul_SkAlphaType;
 *     return PrecompileColorFiltersPriv::WithWorkingFormat(
 *             {{std::move(cf)}}, &kTF, /* gamut= */ nullptr, &kUnpremul);
 * }
 * ```
 */
public fun highContrast(): SkSp<PrecompileColorFilter> {
  TODO("Implement highContrast")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::Luma() {
 *     const SkRuntimeEffect* lumaEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kLuma);
 *
 *     return PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(lumaEffect));
 * }
 * ```
 */
public fun luma(): SkSp<PrecompileColorFilter> {
  TODO("Implement luma")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFilters::Overdraw() {
 *     const SkRuntimeEffect* overdrawEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kOverdraw);
 *
 *     return PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(overdrawEffect));
 * }
 * ```
 */
public fun overdraw(): SkSp<PrecompileColorFilter> {
  TODO("Implement overdraw")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> PrecompileColorFiltersPriv::WithWorkingFormat(
 *         SkSpan<const sk_sp<PrecompileColorFilter>> childOptions,
 *         const skcms_TransferFunction* tf,
 *         const skcms_Matrix3x3* gamut,
 *         const SkAlphaType* at) {
 *     return sk_make_sp<PrecompileWithWorkingFormatColorFilter>(childOptions, tf, gamut, at);
 * }
 * ```
 */
public fun withWorkingFormat(
  childOptions: SkSpan<SkSp<PrecompileColorFilter>>,
  tf: SkcmsTransferFunction?,
  gamut: SkcmsMatrix3x3?,
  at: SkAlphaType?,
): SkSp<PrecompileColorFilter> {
  TODO("Implement withWorkingFormat")
}

/**
 * C++ original:
 * ```cpp
 * void CreateBlurImageFilterPipelines(
 *         const KeyContext& keyContext,
 *         const RenderPassDesc& renderPassDesc,
 *         const PaintOptionsPriv::ProcessCombination& processCombination) {
 *
 *     PaintOptions blurPaintOptions;
 *
 *     // For blur imagefilters we know we don't have alpha-only textures and don't need cubic
 *     // filtering.
 *     sk_sp<PrecompileShader> imageShader = PrecompileShaders::Image(
 *             ImageShaderFlags::kNoAlphaNoCubic);
 *
 *     static const SkBlendMode kBlurBlendModes[] = { SkBlendMode::kSrc };
 *     blurPaintOptions.setShaders({{ PrecompileShadersPriv::Blur(imageShader) }});
 *     blurPaintOptions.setBlendModes(kBlurBlendModes);
 *
 *     blurPaintOptions.priv().buildCombinations(keyContext,
 *                                               DrawTypeFlags::kSimpleShape,
 *                                               /* withPrimitiveBlender= */ false,
 *                                               Coverage::kSingleChannel,
 *                                               renderPassDesc,
 *                                               processCombination);
 * }
 * ```
 */
public fun createBlurImageFilterPipelines(
  keyContext: KeyContext,
  renderPassDesc: RenderPassDesc,
  processCombination: PaintOptionsPriv.ProcessCombination,
) {
  TODO("Implement createBlurImageFilterPipelines")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileImageFilter> PrecompileImageFilters::Arithmetic(
 *         sk_sp<PrecompileImageFilter> background,
 *         sk_sp<PrecompileImageFilter> foreground) {
 *     return Blend(PrecompileBlenders::Arithmetic(), std::move(background), std::move(foreground));
 * }
 * ```
 */
public fun arithmetic(background: SkSp<PrecompileImageFilter>, foreground: SkSp<PrecompileImageFilter>): SkSp<PrecompileImageFilter> {
  TODO("Implement arithmetic")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileImageFilter> PrecompileImageFilters::DisplacementMap(
 *             sk_sp<PrecompileImageFilter> input) {
 *     return sk_make_sp<PrecompileDisplacementMapImageFilter>(SkSpan(&input, 1));
 * }
 * ```
 */
public fun displacementMap(input: SkSp<PrecompileImageFilter>): SkSp<PrecompileImageFilter> {
  TODO("Implement displacementMap")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileImageFilter> PrecompileImageFilters::Morphology(
 *         sk_sp<PrecompileImageFilter> input) {
 *     return sk_make_sp<PrecompileMorphologyImageFilter>(SkSpan(&input, 1));
 * }
 * ```
 */
public fun morphology(input: SkSp<PrecompileImageFilter>): SkSp<PrecompileImageFilter> {
  TODO("Implement morphology")
}

/**
 * C++ original:
 * ```cpp
 * bool precompilebase_is_valid_as_child(const PrecompileBase *child) {
 *     if (!child) {
 *         return true;
 *     }
 *
 *     switch (child->type()) {
 *         case PrecompileBase::Type::kShader:
 *         case PrecompileBase::Type::kColorFilter:
 *         case PrecompileBase::Type::kBlender:
 *             return true;
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun precompilebaseIsValidAsChild(child: PrecompileBase?): Boolean {
  TODO("Implement precompilebaseIsValidAsChild")
}

/**
 * C++ original:
 * ```cpp
 * int num_options_in_set(const SkSpan<const sk_sp<PrecompileBase>>& optionSet) {
 *     int numOptions = 0;
 *     for (const sk_sp<PrecompileBase>& childOption : optionSet) {
 *         // A missing child will fall back to a passthrough object
 *         if (childOption) {
 *             numOptions += childOption->priv().numCombinations();
 *         } else {
 *             ++numOptions;
 *         }
 *     }
 *
 *     return numOptions;
 * }
 * ```
 */
public fun numOptionsInSet(optionSet: SkSpan<SkSp<PrecompileBase>>): Int {
  TODO("Implement numOptionsInSet")
}

/**
 * C++ original:
 * ```cpp
 * PrecompileBase::Type to_precompile_type(SkRuntimeEffect::ChildType childType) {
 *     switch(childType) {
 *         case SkRuntimeEffect::ChildType::kShader:      return PrecompileBase::Type::kShader;
 *         case SkRuntimeEffect::ChildType::kColorFilter: return PrecompileBase::Type::kColorFilter;
 *         case SkRuntimeEffect::ChildType::kBlender:     return PrecompileBase::Type::kBlender;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun toPrecompileType(childType: SkRuntimeEffect.ChildType): PrecompileBase.Type {
  TODO("Implement toPrecompileType")
}

/**
 * C++ original:
 * ```cpp
 * bool children_are_valid(SkRuntimeEffect* effect,
 *                         SkSpan<const PrecompileChildOptions> childOptions) {
 *     SkSpan<const SkRuntimeEffect::Child> childInfo = effect->children();
 *     if (childOptions.size() != childInfo.size()) {
 *         return false;
 *     }
 *
 *     for (size_t i = 0; i < childInfo.size(); ++i) {
 *         const PrecompileBase::Type expectedType = to_precompile_type(childInfo[i].type);
 *         for (const sk_sp<PrecompileBase>& childOption : childOptions[i]) {
 *             if (childOption && expectedType != childOption->type()) {
 *                 return false;
 *             }
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun childrenAreValid(effect: SkRuntimeEffect?, childOptions: SkSpan<PrecompileChildOptions>): Boolean {
  TODO("Implement childrenAreValid")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::CoordClamp(SkSpan<const sk_sp<PrecompileShader>> input) {
 *     return sk_make_sp<PrecompileCoordClampShader>(input);
 * }
 * ```
 */
public fun coordClamp(input: SkSpan<SkSp<PrecompileShader>>): SkSp<PrecompileShader> {
  TODO("Implement coordClamp")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::MakeFractalNoise() {
 *     return sk_make_sp<PrecompilePerlinNoiseShader>();
 * }
 * ```
 */
public fun makeFractalNoise(): SkSp<PrecompileShader> {
  TODO("Implement makeFractalNoise")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::MakeTurbulence() {
 *     return sk_make_sp<PrecompilePerlinNoiseShader>();
 * }
 * ```
 */
public fun makeTurbulence(): SkSp<PrecompileShader> {
  TODO("Implement makeTurbulence")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkColorSpace> get_gradient_intermediate_cs(SkColorSpace* dstColorSpace,
 *                                                  SkGradient::Interpolation interpolation) {
 *     // Any gradient shader will do, as long as it has the correct interpolation settings.
 *     constexpr SkPoint pts[2] = {{0.f, 0.f}, {1.f, 0.f}};
 *     constexpr SkColor4f colors[2] = {SkColors::kBlack, SkColors::kWhite};
 *     constexpr float pos[2] = {0.f, 1.f};
 *     SkLinearGradient shader(pts, {{colors, pos, SkTileMode::kClamp, nullptr}, interpolation});
 *
 *     SkColor4fXformer xformedColors(&shader, dstColorSpace);
 *     return xformedColors.fIntermediateColorSpace;
 * }
 * ```
 */
public fun getGradientIntermediateCs(dstColorSpace: SkColorSpace?, interpolation: SkGradient.Interpolation): SkSp<SkColorSpace> {
  TODO("Implement getGradientIntermediateCs")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::Picture(bool withLM) {
 *     sk_sp<PrecompileShader> s = PrecompileShaders::Image();
 *     if (withLM) {
 *         return PrecompileShaders::LocalMatrix({{ std::move(s) }});
 *     }
 *     return s;
 * }
 * ```
 */
public fun picture(withLM: Boolean): SkSp<PrecompileShader> {
  TODO("Implement picture")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::LocalMatrix(
 *         SkSpan<const sk_sp<PrecompileShader>> wrapped,
 *         bool isPerspective) {
 *     return sk_make_sp<PrecompileLocalMatrixShader>(
 *             std::move(wrapped),
 *             isPerspective ? PrecompileLocalMatrixShader::Flags::kIsPerspective
 *                           : PrecompileLocalMatrixShader::Flags::kNone);
 * }
 * ```
 */
public fun localMatrix(wrapped: SkSpan<SkSp<PrecompileShader>>, isPerspective: Boolean): SkSp<PrecompileShader> {
  TODO("Implement localMatrix")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::LocalMatrixBothVariants(
 *         SkSpan<const sk_sp<PrecompileShader>> wrapped) {
 *     return sk_make_sp<PrecompileLocalMatrixShader>(
 *             std::move(wrapped),
 *             PrecompileLocalMatrixShader::Flags::kIncludeWithOutVariant);
 * }
 * ```
 */
public fun localMatrixBothVariants(wrapped: SkSpan<SkSp<PrecompileShader>>): SkSp<PrecompileShader> {
  TODO("Implement localMatrixBothVariants")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::ColorFilter(
 *         SkSpan<const sk_sp<PrecompileShader>> shaders,
 *         SkSpan<const sk_sp<PrecompileColorFilter>> colorFilters) {
 *     return sk_make_sp<PrecompileColorFilterShader>(std::move(shaders), std::move(colorFilters));
 * }
 * ```
 */
public fun colorFilter(shaders: SkSpan<SkSp<PrecompileShader>>, colorFilters: SkSpan<SkSp<PrecompileColorFilter>>): SkSp<PrecompileShader> {
  TODO("Implement colorFilter")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShaders::WorkingColorSpaceExplicit(
 *         SkSpan<const sk_sp<PrecompileShader>> shaders,
 *         SkSpan<const std::pair</*input =*/sk_sp<SkColorSpace>,
 *                                /*output=*/sk_sp<SkColorSpace>>> inputAndOutputSpaces) {
 *     return sk_make_sp<PrecompileWorkingColorSpaceShader>(std::move(shaders),
 *                                                          std::move(inputAndOutputSpaces));
 * }
 * ```
 */
public fun workingColorSpaceExplicit(shaders: SkSpan<SkSp<PrecompileShader>>, param1: Any = TODO()): SkSp<PrecompileShader> {
  TODO("Implement workingColorSpaceExplicit")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::CTM(SkSpan<const sk_sp<PrecompileShader>> wrapped) {
 *     return sk_make_sp<PrecompileCTMShader>(std::move(wrapped));
 * }
 * ```
 */
public fun ctm(wrapped: SkSpan<SkSp<PrecompileShader>>): SkSp<PrecompileShader> {
  TODO("Implement ctm")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::Blur(sk_sp<PrecompileShader> wrapped) {
 *     return sk_make_sp<PrecompileBlurShader>(std::move(wrapped));
 * }
 * ```
 */
public fun blur(wrapped: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement blur")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::MatrixConvolution(
 *         sk_sp<skgpu::graphite::PrecompileShader> wrapped) {
 *     return sk_make_sp<PrecompileMatrixConvolutionShader>(std::move(wrapped));
 * }
 * ```
 */
public fun matrixConvolution(wrapped: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement matrixConvolution")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::LinearMorphology(sk_sp<PrecompileShader> wrapped) {
 *     return sk_make_sp<PrecompileMorphologyShader>(
 *             std::move(wrapped),
 *             SkKnownRuntimeEffects::StableKey::kLinearMorphology);
 * }
 * ```
 */
public fun linearMorphology(wrapped: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement linearMorphology")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::SparseMorphology(sk_sp<PrecompileShader> wrapped) {
 *     return sk_make_sp<PrecompileMorphologyShader>(
 *             std::move(wrapped),
 *             SkKnownRuntimeEffects::StableKey::kSparseMorphology);
 * }
 * ```
 */
public fun sparseMorphology(wrapped: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement sparseMorphology")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::Displacement(sk_sp<PrecompileShader> displacement,
 *                                                             sk_sp<PrecompileShader> color) {
 *     return sk_make_sp<PrecompileDisplacementShader>(std::move(displacement), std::move(color));
 * }
 * ```
 */
public fun displacement(displacement: SkSp<PrecompileShader>, color: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement displacement")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> PrecompileShadersPriv::Lighting(sk_sp<PrecompileShader> wrapped) {
 *     return sk_make_sp<PrecompileLightingShader>(std::move(wrapped));
 * }
 * ```
 */
public fun lighting(wrapped: SkSp<PrecompileShader>): SkSp<PrecompileShader> {
  TODO("Implement lighting")
}

/**
 * C++ original:
 * ```cpp
 * inline constexpr int MaskFormatBytesPerPixel(MaskFormat format) {
 *     SkASSERT(static_cast<int>(format) < kMaskFormatCount);
 *     // kA8   (0) -> 1
 *     // kA565 (1) -> 2
 *     // kARGB (2) -> 4
 *     static_assert(static_cast<int>(MaskFormat::kA8) == 0, "enum_order_dependency");
 *     static_assert(static_cast<int>(MaskFormat::kA565) == 1, "enum_order_dependency");
 *     static_assert(static_cast<int>(MaskFormat::kARGB) == 2, "enum_order_dependency");
 *
 *     return SkTo<int>(1u << static_cast<int>(format));
 * }
 * ```
 */
public fun maskFormatBytesPerPixel(format: MaskFormat): Int {
  TODO("Implement maskFormatBytesPerPixel")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr SkColorType MaskFormatToColorType(MaskFormat format) {
 *     switch (format) {
 *         case MaskFormat::kA8:
 *             return kAlpha_8_SkColorType;
 *         case MaskFormat::kA565:
 *             return kRGB_565_SkColorType;
 *         case MaskFormat::kARGB:
 *             return kRGBA_8888_SkColorType;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun maskFormatToColorType(format: MaskFormat): SkColorType {
  TODO("Implement maskFormatToColorType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffRefsSrc(const BlendCoeff coeff) {
 *     return BlendCoeff::kSC == coeff || BlendCoeff::kISC == coeff || BlendCoeff::kSA == coeff ||
 *            BlendCoeff::kISA == coeff;
 * }
 * ```
 */
public fun blendCoeffRefsSrc(coeff: BlendCoeff): Boolean {
  TODO("Implement blendCoeffRefsSrc")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffRefsDst(const BlendCoeff coeff) {
 *     return BlendCoeff::kDC == coeff || BlendCoeff::kIDC == coeff || BlendCoeff::kDA == coeff ||
 *            BlendCoeff::kIDA == coeff;
 * }
 * ```
 */
public fun blendCoeffRefsDst(coeff: BlendCoeff): Boolean {
  TODO("Implement blendCoeffRefsDst")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffRefsSrc2(const BlendCoeff coeff) {
 *     return BlendCoeff::kS2C == coeff || BlendCoeff::kIS2C == coeff ||
 *            BlendCoeff::kS2A == coeff || BlendCoeff::kIS2A == coeff;
 * }
 * ```
 */
public fun blendCoeffRefsSrc2(coeff: BlendCoeff): Boolean {
  TODO("Implement blendCoeffRefsSrc2")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffsUseSrcColor(BlendCoeff srcCoeff, BlendCoeff dstCoeff) {
 *     return BlendCoeff::kZero != srcCoeff || BlendCoeffRefsSrc(dstCoeff);
 * }
 * ```
 */
public fun blendCoeffsUseSrcColor(srcCoeff: BlendCoeff, dstCoeff: BlendCoeff): Boolean {
  TODO("Implement blendCoeffsUseSrcColor")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffsUseDstColor(BlendCoeff srcCoeff,
 *                                              BlendCoeff dstCoeff,
 *                                              bool srcColorIsOpaque) {
 *     return BlendCoeffRefsDst(srcCoeff) ||
 *            (dstCoeff != BlendCoeff::kZero && !(dstCoeff == BlendCoeff::kISA && srcColorIsOpaque));
 * }
 * ```
 */
public fun blendCoeffsUseDstColor(
  srcCoeff: BlendCoeff,
  dstCoeff: BlendCoeff,
  srcColorIsOpaque: Boolean,
): Boolean {
  TODO("Implement blendCoeffsUseDstColor")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendEquationIsAdvanced(BlendEquation equation) {
 *     return equation >= BlendEquation::kFirstAdvanced &&
 *            equation != BlendEquation::kIllegal;
 * }
 * ```
 */
public fun blendEquationIsAdvanced(equation: BlendEquation): Boolean {
  TODO("Implement blendEquationIsAdvanced")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendModifiesDst(BlendEquation equation,
 *                                        BlendCoeff srcCoeff,
 *                                        BlendCoeff dstCoeff) {
 *     return (BlendEquation::kAdd != equation && BlendEquation::kReverseSubtract != equation) ||
 *             BlendCoeff::kZero != srcCoeff || BlendCoeff::kOne != dstCoeff;
 * }
 * ```
 */
public fun blendModifiesDst(
  equation: BlendEquation,
  srcCoeff: BlendCoeff,
  dstCoeff: BlendCoeff,
): Boolean {
  TODO("Implement blendModifiesDst")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendCoeffRefsConstant(const BlendCoeff coeff) {
 *     return coeff == BlendCoeff::kConstC || coeff == BlendCoeff::kIConstC;
 * }
 * ```
 */
public fun blendCoeffRefsConstant(coeff: BlendCoeff): Boolean {
  TODO("Implement blendCoeffRefsConstant")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendShouldDisable(BlendEquation equation,
 *                                          BlendCoeff srcCoeff,
 *                                          BlendCoeff dstCoeff) {
 *     return (BlendEquation::kAdd == equation || BlendEquation::kSubtract == equation) &&
 *             BlendCoeff::kOne == srcCoeff && BlendCoeff::kZero == dstCoeff;
 * }
 * ```
 */
public fun blendShouldDisable(
  equation: BlendEquation,
  srcCoeff: BlendCoeff,
  dstCoeff: BlendCoeff,
): Boolean {
  TODO("Implement blendShouldDisable")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool BlendAllowsCoverageAsAlpha(BlendEquation equation,
 *                                                  BlendCoeff srcCoeff,
 *                                                  BlendCoeff dstCoeff) {
 *     return BlendEquationIsAdvanced(equation) ||
 *            !BlendModifiesDst(equation, srcCoeff, dstCoeff) ||
 *            ((BlendEquation::kAdd == equation || BlendEquation::kReverseSubtract == equation) &&
 *             !BlendCoeffRefsSrc(srcCoeff) &&
 *             (BlendCoeff::kOne == dstCoeff || BlendCoeff::kISC == dstCoeff ||
 *              BlendCoeff::kISA == dstCoeff));
 * }
 * ```
 */
public fun blendAllowsCoverageAsAlpha(
  equation: BlendEquation,
  srcCoeff: BlendCoeff,
  dstCoeff: BlendCoeff,
): Boolean {
  TODO("Implement blendAllowsCoverageAsAlpha")
}

/**
 * C++ original:
 * ```cpp
 * constexpr int BlurKernelWidth(int radius) { return SkShaderBlurAlgorithm::KernelWidth(radius); }
 * ```
 */
public fun blurKernelWidth(radius: Int): Int {
  TODO("Implement blurKernelWidth")
}

/**
 * C++ original:
 * ```cpp
 * constexpr int BlurLinearKernelWidth(int radius) {
 *     return SkShaderBlurAlgorithm::LinearKernelWidth(radius);
 * }
 * ```
 */
public fun blurLinearKernelWidth(radius: Int): Int {
  TODO("Implement blurLinearKernelWidth")
}

/**
 * C++ original:
 * ```cpp
 * constexpr bool BlurIsEffectivelyIdentity(float sigma) {
 *     return SkBlurEngine::IsEffectivelyIdentity(sigma);
 * }
 * ```
 */
public fun blurIsEffectivelyIdentity(sigma: Float): Boolean {
  TODO("Implement blurIsEffectivelyIdentity")
}

/**
 * C++ original:
 * ```cpp
 * inline int BlurSigmaRadius(float sigma) { return SkBlurEngine::SigmaToRadius(sigma); }
 * ```
 */
public fun blurSigmaRadius(sigma: Float): Int {
  TODO("Implement blurSigmaRadius")
}

/**
 * C++ original:
 * ```cpp
 * inline const SkRuntimeEffect* GetBlur2DEffect(const SkISize& radii) {
 *     return SkShaderBlurAlgorithm::GetBlur2DEffect(radii);
 * }
 * ```
 */
public fun getBlur2DEffect(radii: SkISize): SkRuntimeEffect {
  TODO("Implement getBlur2DEffect")
}

/**
 * C++ original:
 * ```cpp
 * inline const SkRuntimeEffect* GetLinearBlur1DEffect(int radius) {
 *     return SkShaderBlurAlgorithm::GetLinearBlur1DEffect(radius);
 * }
 * ```
 */
public fun getLinearBlur1DEffect(radius: Int): SkRuntimeEffect {
  TODO("Implement getLinearBlur1DEffect")
}

/**
 * C++ original:
 * ```cpp
 * inline void Compute2DBlurKernel(SkSize sigma,
 *                                 SkISize radius,
 *                                 std::array<SkV4, kMaxBlurSamples/4>& kernel) {
 *     SkShaderBlurAlgorithm::Compute2DBlurKernel(sigma, radius, kernel);
 * }
 * ```
 */
public fun compute2DBlurKernel(
  sigma: SkSize,
  radius: SkISize,
  kernel: Array<SkV4>,
) {
  TODO("Implement compute2DBlurKernel")
}

/**
 * C++ original:
 * ```cpp
 * inline void Compute1DBlurKernel(float sigma, int radius, SkSpan<float> kernel) {
 *     SkShaderBlurAlgorithm::Compute1DBlurKernel(sigma, radius, kernel);
 * }
 * ```
 */
public fun compute1DBlurKernel(
  sigma: Float,
  radius: Int,
  kernel: SkSpan<Float>,
) {
  TODO("Implement compute1DBlurKernel")
}

/**
 * C++ original:
 * ```cpp
 * inline void Compute2DBlurOffsets(SkISize radius, std::array<SkV4, kMaxBlurSamples/2>& offsets) {
 *     SkShaderBlurAlgorithm::Compute2DBlurOffsets(radius, offsets);
 * }
 * ```
 */
public fun compute2DBlurOffsets(radius: SkISize, offsets: Array<SkV4>) {
  TODO("Implement compute2DBlurOffsets")
}

/**
 * C++ original:
 * ```cpp
 * inline void Compute1DBlurLinearKernel(float sigma,
 *                                       int radius,
 *                                       std::array<SkV4, kMaxBlurSamples/2>& offsetsAndKernel) {
 *     SkShaderBlurAlgorithm::Compute1DBlurLinearKernel(sigma, radius, offsetsAndKernel);
 * }
 * ```
 */
public fun compute1DBlurLinearKernel(
  sigma: Float,
  radius: Int,
  offsetsAndKernel: Array<SkV4>,
) {
  TODO("Implement compute1DBlurLinearKernel")
}

/**
 * C++ original:
 * ```cpp
 * static inline void skgpu_init_static_unique_key_once(SkAlignedSTStorage<1, UniqueKey>* keyStorage) {
 *     UniqueKey* key = new (keyStorage->get()) UniqueKey;
 *     UniqueKey::Builder builder(key, UniqueKey::GenerateDomain(), 0);
 * }
 * ```
 */
public fun skgpuInitStaticUniqueKeyOnce(keyStorage: SkAlignedSTStorage1<UniqueKey>) {
  TODO("Implement skgpuInitStaticUniqueKeyOnce")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkShouldPostMessageToBus(const UniqueKeyInvalidatedMsg_Graphite& msg,
 *                                             uint32_t msgBusUniqueID) {
 *     return msg.recorderID() == msgBusUniqueID;
 * }
 * ```
 */
public fun skShouldPostMessageToBus(msg: UniqueKeyInvalidatedMsgGraphite, msgBusUniqueID: UInt): Boolean {
  TODO("Implement skShouldPostMessageToBus")
}

/**
 * C++ original:
 * ```cpp
 * constexpr static int NumCurveTrianglesAtResolveLevel(int resolveLevel) {
 *     // resolveLevel=0 -> 0 line segments -> 0 triangles
 *     // resolveLevel=1 -> 2 line segments -> 1 triangle
 *     // resolveLevel=2 -> 4 line segments -> 3 triangles
 *     // resolveLevel=3 -> 8 line segments -> 7 triangles
 *     // ...
 *     return (1 << resolveLevel) - 1;
 * }
 * ```
 */
public fun numCurveTrianglesAtResolveLevel(resolveLevel: Int): Int {
  TODO("Implement numCurveTrianglesAtResolveLevel")
}

/**
 * C++ original:
 * ```cpp
 * constexpr size_t PatchAttribsStride(PatchAttribs attribs) {
 *     return (attribs & PatchAttribs::kJoinControlPoint ? sizeof(float) * 2 : 0) +
 *            (attribs & PatchAttribs::kFanPoint ? sizeof(float) * 2 : 0) +
 *            (attribs & PatchAttribs::kStrokeParams ? sizeof(float) * 2 : 0) +
 *            (attribs & PatchAttribs::kColor
 *                     ? (attribs & PatchAttribs::kWideColorIfEnabled ? sizeof(float)
 *                                                                    : sizeof(uint8_t)) * 4 : 0) +
 *            (attribs & PatchAttribs::kPaintDepth ? sizeof(float) : 0) +
 *            (attribs & PatchAttribs::kExplicitCurveType ? sizeof(float) : 0) +
 *            (attribs & PatchAttribs::kSsboIndex ? sizeof(uint32_t) : 0);
 * }
 * ```
 */
public fun patchAttribsStride(attribs: PatchAttribs): ULong {
  TODO("Implement patchAttribsStride")
}

/**
 * C++ original:
 * ```cpp
 * constexpr size_t PatchStride(PatchAttribs attribs) {
 *     return 4*sizeof(SkPoint) + PatchAttribsStride(attribs);
 * }
 * ```
 */
public fun patchStride(attribs: PatchAttribs): ULong {
  TODO("Implement patchStride")
}

/**
 * C++ original:
 * ```cpp
 * inline bool ConicHasCusp(const SkPoint p[3]) {
 *     SkVector a = p[1] - p[0];
 *     SkVector b = p[2] - p[1];
 *     // A conic of any class can only have a cusp if it is a degenerate flat line with a 180 degree
 *     // turnarund. To detect this, the beginning and ending tangents must be parallel
 *     // (a.cross(b) == 0) and pointing in opposite directions (a.dot(b) < 0).
 *     return a.cross(b) == 0 && a.dot(b) < 0;
 * }
 * ```
 */
public fun conicHasCusp(p: Array<SkPoint>): Boolean {
  TODO("Implement conicHasCusp")
}

/**
 * C++ original:
 * ```cpp
 * inline float GetJoinType(const SkStrokeRec& stroke) {
 *     switch (stroke.getJoin()) {
 *         case SkPaint::kRound_Join: return -1;
 *         case SkPaint::kBevel_Join: return 0;
 *         case SkPaint::kMiter_Join: SkASSERT(stroke.getMiter() >= 0); return stroke.getMiter();
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun getJoinType(stroke: SkStrokeRec): Float {
  TODO("Implement getJoinType")
}

/**
 * C++ original:
 * ```cpp
 * inline bool StrokesHaveEqualParams(const SkStrokeRec& a, const SkStrokeRec& b) {
 *     return a.getWidth() == b.getWidth() && a.getJoin() == b.getJoin() &&
 *             (a.getJoin() != SkPaint::kMiter_Join || a.getMiter() == b.getMiter());
 * }
 * ```
 */
public fun strokesHaveEqualParams(a: SkStrokeRec, b: SkStrokeRec): Boolean {
  TODO("Implement strokesHaveEqualParams")
}

/**
 * C++ original:
 * ```cpp
 * constexpr int NumFixedEdgesInJoin(const StrokeParams& strokeParams) {
 *     // The caller is responsible for counting the variable number of segments for round joins.
 *     return strokeParams.fJoinType > 0.f ? /* miter */ 4 : /* round or bevel */ 3;
 * }
 * ```
 */
public fun numFixedEdgesInJoin(strokeParams: StrokeParams): Int {
  TODO("Implement numFixedEdgesInJoin")
}

/**
 * C++ original:
 * ```cpp
 * inline float CalcNumRadialSegmentsPerRadian(float approxDevStrokeRadius) {
 *     float cosTheta = 1.f - (1.f / kPrecision) / approxDevStrokeRadius;
 *     return .5f / acosf(std::max(cosTheta, -1.f));
 * }
 * ```
 */
public fun calcNumRadialSegmentsPerRadian(approxDevStrokeRadius: Float): Float {
  TODO("Implement calcNumRadialSegmentsPerRadian")
}

/**
 * C++ original:
 * ```cpp
 * constexpr float length_term(float precision) {
 *     return (Degree * (Degree - 1) / 8.f) * precision;
 * }
 * ```
 */
public fun lengthTerm(precision: Float): Float {
  TODO("Implement lengthTerm")
}

/**
 * C++ original:
 * ```cpp
 * constexpr float length_term_p2(float precision) {
 *     return ((Degree * Degree) * ((Degree - 1) * (Degree - 1)) / 64.f) * (precision * precision);
 * }
 * ```
 */
public fun lengthTermP2(precision: Float): Float {
  TODO("Implement lengthTermP2")
}

/**
 * C++ original:
 * ```cpp
 * AI float root4(float x) {
 *     return sqrtf(sqrtf(x));
 * }
 * ```
 */
public fun root4(x: Float): Any {
  TODO("Implement root4")
}

/**
 * C++ original:
 * ```cpp
 * AI int nextlog2(float x) {
 *     if (x <= 1) {
 *         return 0;
 *     }
 *
 *     uint32_t bits = SkFloat2Bits(x);
 *     static constexpr uint32_t kDigitsAfterBinaryPoint = std::numeric_limits<float>::digits - 1;
 *
 *     // The constant is a significand of all 1s -- 0b0'00000000'111'1111111111'111111111. So, if
 *     // the significand of x is all 0s (and therefore an integer power of two) this will not
 *     // increment the exponent, but if it is just one ULP above the power of two the carry will
 *     // ripple into the exponent incrementing the exponent by 1.
 *     bits += (1u << kDigitsAfterBinaryPoint) - 1u;
 *
 *     // Shift the exponent down, and adjust it by the exponent offset so that 2^0 is really 0 instead
 *     // of 127. Remember that 1 was added to the exponent, if x is NaN, then the exponent will
 *     // carry a 1 into the sign bit during the addition to bits. Be sure to strip off the sign bit.
 *     // In addition, infinity is an exponent of all 1's, and a significand of all 0, so
 *     // the exponent is not affected during the addition to bits, and the exponent remains all 1's.
 *     const int exp = ((bits >> kDigitsAfterBinaryPoint) & 0b1111'1111) - 127;
 *
 *     // Return 0 for x <= 1.
 *     return exp > 0 ? exp : 0;
 * }
 * ```
 */
public fun nextlog2(x: Float): Any {
  TODO("Implement nextlog2")
}

/**
 * C++ original:
 * ```cpp
 * AI int nextlog4(float x) {
 *     return (nextlog2(x) + 1) >> 1;
 * }
 * ```
 */
public fun nextlog4(x: Float): Any {
  TODO("Implement nextlog4")
}

/**
 * C++ original:
 * ```cpp
 * AI int nextlog16(float x) {
 *     return (nextlog2(x) + 3) >> 2;
 * }
 * ```
 */
public fun nextlog16(x: Float): Any {
  TODO("Implement nextlog16")
}

/**
 * C++ original:
 * ```cpp
 * AI float quadratic_p4(float precision,
 *                       const SkPoint pts[],
 *                       const VectorXform& vectorXform = VectorXform()) {
 *     return quadratic_p4(precision,
 *                         sk_bit_cast<skvx::float2>(pts[0]),
 *                         sk_bit_cast<skvx::float2>(pts[1]),
 *                         sk_bit_cast<skvx::float2>(pts[2]),
 *                         vectorXform);
 * }
 * ```
 */
public fun quadraticP4(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement quadraticP4")
}

/**
 * C++ original:
 * ```cpp
 * AI float quadratic(float precision,
 *                    const SkPoint pts[],
 *                    const VectorXform& vectorXform = VectorXform()) {
 *     return root4(quadratic_p4(precision, pts, vectorXform));
 * }
 * ```
 */
public fun quadratic(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement quadratic")
}

/**
 * C++ original:
 * ```cpp
 * AI int quadratic_log2(float precision,
 *                       const SkPoint pts[],
 *                       const VectorXform& vectorXform = VectorXform()) {
 *     // nextlog16(x) == ceil(log2(sqrt(sqrt(x))))
 *     return nextlog16(quadratic_p4(precision, pts, vectorXform));
 * }
 * ```
 */
public fun quadraticLog2(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement quadraticLog2")
}

/**
 * C++ original:
 * ```cpp
 * AI float cubic_p4(float precision,
 *                   const SkPoint pts[],
 *                   const VectorXform& vectorXform = VectorXform()) {
 *     return cubic_p4(precision,
 *                     sk_bit_cast<skvx::float2>(pts[0]),
 *                     sk_bit_cast<skvx::float2>(pts[1]),
 *                     sk_bit_cast<skvx::float2>(pts[2]),
 *                     sk_bit_cast<skvx::float2>(pts[3]),
 *                     vectorXform);
 * }
 * ```
 */
public fun cubicP4(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement cubicP4")
}

/**
 * C++ original:
 * ```cpp
 * AI float cubic(float precision,
 *                const SkPoint pts[],
 *                const VectorXform& vectorXform = VectorXform()) {
 *     return root4(cubic_p4(precision, pts, vectorXform));
 * }
 * ```
 */
public fun cubic(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement cubic")
}

/**
 * C++ original:
 * ```cpp
 * AI int cubic_log2(float precision,
 *                   const SkPoint pts[],
 *                   const VectorXform& vectorXform = VectorXform()) {
 *     // nextlog16(x) == ceil(log2(sqrt(sqrt(x))))
 *     return nextlog16(cubic_p4(precision, pts, vectorXform));
 * }
 * ```
 */
public fun cubicLog2(
  precision: Float,
  pts: Array<SkPoint>,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement cubicLog2")
}

/**
 * C++ original:
 * ```cpp
 * AI float worst_case_cubic_p4(float precision, float devWidth, float devHeight) {
 *     float kk = length_term_p2<3>(precision);
 *     return 4*kk * (devWidth * devWidth + devHeight * devHeight);
 * }
 * ```
 */
public fun worstCaseCubicP4(
  precision: Float,
  devWidth: Float,
  devHeight: Float,
): Any {
  TODO("Implement worstCaseCubicP4")
}

/**
 * C++ original:
 * ```cpp
 * AI float worst_case_cubic(float precision, float devWidth, float devHeight) {
 *     return root4(worst_case_cubic_p4(precision, devWidth, devHeight));
 * }
 * ```
 */
public fun worstCaseCubic(
  precision: Float,
  devWidth: Float,
  devHeight: Float,
): Any {
  TODO("Implement worstCaseCubic")
}

/**
 * C++ original:
 * ```cpp
 * AI int worst_case_cubic_log2(float precision, float devWidth, float devHeight) {
 *     // nextlog16(x) == ceil(log2(sqrt(sqrt(x))))
 *     return nextlog16(worst_case_cubic_p4(precision, devWidth, devHeight));
 * }
 * ```
 */
public fun worstCaseCubicLog2(
  precision: Float,
  devWidth: Float,
  devHeight: Float,
): Any {
  TODO("Implement worstCaseCubicLog2")
}

/**
 * C++ original:
 * ```cpp
 * AI float conic_p2(float precision,
 *                   const SkPoint pts[],
 *                   float w,
 *                   const VectorXform& vectorXform = VectorXform()) {
 *     return conic_p2(precision,
 *                     sk_bit_cast<skvx::float2>(pts[0]),
 *                     sk_bit_cast<skvx::float2>(pts[1]),
 *                     sk_bit_cast<skvx::float2>(pts[2]),
 *                     w,
 *                     vectorXform);
 * }
 * ```
 */
public fun conicP2(
  precision: Float,
  pts: Array<SkPoint>,
  w: Float,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement conicP2")
}

/**
 * C++ original:
 * ```cpp
 * AI float conic(float tolerance,
 *                const SkPoint pts[],
 *                float w,
 *                const VectorXform& vectorXform = VectorXform()) {
 *     return sqrtf(conic_p2(tolerance, pts, w, vectorXform));
 * }
 * ```
 */
public fun conic(
  tolerance: Float,
  pts: Array<SkPoint>,
  w: Float,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement conic")
}

/**
 * C++ original:
 * ```cpp
 * AI int conic_log2(float tolerance,
 *                   const SkPoint pts[],
 *                   float w,
 *                   const VectorXform& vectorXform = VectorXform()) {
 *     // nextlog4(x) == ceil(log2(sqrt(x)))
 *     return nextlog4(conic_p2(tolerance, pts, w, vectorXform));
 * }
 * ```
 */
public fun conicLog2(
  tolerance: Float,
  pts: Array<SkPoint>,
  w: Float,
  vectorXform: VectorXform = TODO(),
): Any {
  TODO("Implement conicLog2")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * [[maybe_unused]] inline VertexWriter& operator<<(VertexWriter& w, const VertexColor& color) {
 *     w << color.fColor[0];
 *     if (color.fWideColor) {
 *         w << color.fColor[1]
 *           << color.fColor[2]
 *           << color.fColor[3];
 *     }
 *     return w;
 * }
 * ```
 */
public fun `operator`(w: VertexWriter, color: VertexColor): VertexWriter {
  TODO("Implement operator")
}

/**
 * C++ original:
 * ```cpp
 * inline IndexWriter& operator<<(IndexWriter& w, int val) { return (w << SkTo<uint16_t>(val)); }
 * ```
 */
public fun shl(w: IndexWriter, `val`: Int): IndexWriter {
  TODO("Implement shl")
}

/**
 * C++ original:
 * ```cpp
 * inline bool SkSLToMSL(const SkSL::ShaderCaps* caps,
 *                       const std::string& sksl,
 *                       SkSL::ProgramKind programKind,
 *                       const SkSL::ProgramSettings& settings,
 *                       SkSL::NativeShader* msl,
 *                       SkSL::ProgramInterface* outInterface,
 *                       ShaderErrorHandler* errorHandler) {
 *     return SkSLToBackend(caps,
 *                          &SkSL::ToMetal,
 *                          "MSL",
 *                          sksl,
 *                          programKind,
 *                          settings,
 *                          msl,
 *                          outInterface,
 *                          errorHandler);
 * }
 * ```
 */
public fun skSLToMSL(
  caps: ShaderCaps?,
  sksl: String,
  programKind: ProgramKind,
  settings: ProgramSettings,
  msl: NativeShader?,
  outInterface: ProgramInterface?,
  errorHandler: ShaderErrorHandler?,
): Boolean {
  TODO("Implement skSLToMSL")
}

/**
 * C++ original:
 * ```cpp
 * inline Recorder* AsGraphiteRecorder(SkRecorder* recorder) {
 *     if (!recorder) {
 *         return nullptr;
 *     }
 *     if (recorder->type() != SkRecorder::Type::kGraphite) {
 *         return nullptr;
 *     }
 *     return static_cast<Recorder*>(recorder);
 * }
 * ```
 */
public fun asGraphiteRecorder(recorder: SkRecorder?): Int {
  TODO("Implement asGraphiteRecorder")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr SampleCount KeyToSamples(uint32_t keyBits) {
 *     SkASSERT(keyBits <= 4);
 *     return static_cast<SampleCount::V>(1 << keyBits);
 * }
 * ```
 */
public fun keyToSamples(keyBits: UInt): SampleCount {
  TODO("Implement keyToSamples")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr const char* LayoutString(Layout layout) {
 *     switch(layout) {
 *         case Layout::kStd140:  return "std140";
 *         case Layout::kStd430:  return "std430";
 *         case Layout::kMetal:   return "metal";
 *         case Layout::kInvalid: return "invalid";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun layoutString(layout: Layout): Char {
  TODO("Implement layoutString")
}

/**
 * C++ original:
 * ```cpp
 * inline TextureProxyView AsView(sk_sp<SkImage> image) { return AsView(image.get()); }
 * ```
 */
public fun asView(image: SkSp<SkImage>): TextureProxyView {
  TODO("Implement asView")
}

/**
 * C++ original:
 * ```cpp
 * constexpr SampleCount ToSampleCount(uint32_t sampleCount) {
 *     return sampleCount >= 16 ? SampleCount::k16 :
 *            sampleCount >= 8  ? SampleCount::k8  :
 *            sampleCount >= 4  ? SampleCount::k4  :
 *            sampleCount >= 2  ? SampleCount::k2  :
 *                                SampleCount::k1;
 * }
 * ```
 */
public fun toSampleCount(sampleCount: UInt): SampleCount {
  TODO("Implement toSampleCount")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSurface> RenderTarget(skgpu::graphite::Recorder*,
 *                                      const SkImageInfo& imageInfo,
 *                                      skgpu::Mipmapped = skgpu::Mipmapped::kNo,
 *                                      const SkSurfaceProps* surfaceProps = nullptr,
 *                                      std::string_view label = {})
 * ```
 */
public fun renderTarget(
  param0: Recorder?,
  param1: SkImageInfo,
  param2: Int,
  param3: Int?,
  param4: String,
): SkSp<SkSurface> {
  TODO("Implement renderTarget")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSurface> WrapBackendTexture(skgpu::graphite::Recorder*,
 *                                            const skgpu::graphite::BackendTexture&,
 *                                            SkColorType colorType,
 *                                            sk_sp<SkColorSpace> colorSpace,
 *                                            const SkSurfaceProps* props,
 *                                            TextureReleaseProc = nullptr,
 *                                            ReleaseContext = nullptr,
 *                                            std::string_view label = {})
 * ```
 */
public fun wrapBackendTexture(
  param0: Recorder?,
  param1: BackendTexture,
  param2: Int,
  param3: Int,
  param4: Int?,
  param5: TextureReleaseProc,
  param6: ReleaseContext,
  param7: String,
): SkSp<SkSurface> {
  TODO("Implement wrapBackendTexture")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr SkColorType CompressionTypeToSkColorType(SkTextureCompressionType compression) {
 *     switch (compression) {
 *         case SkTextureCompressionType::kNone:            return kUnknown_SkColorType;
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM: return kRGB_888x_SkColorType;
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:  return kRGB_888x_SkColorType;
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: return kRGBA_8888_SkColorType;
 *     }
 *
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun compressionTypeToSkColorType(compression: SkTextureCompressionType): SkColorType {
  TODO("Implement compressionTypeToSkColorType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr const char* CompressionTypeToStr(SkTextureCompressionType compression) {
 *     switch (compression) {
 *         case SkTextureCompressionType::kNone:            return "kNone";
 *         case SkTextureCompressionType::kETC2_RGB8_UNORM: return "kETC2_RGB8_UNORM";
 *         case SkTextureCompressionType::kBC1_RGB8_UNORM:  return "kBC1_RGB8_UNORM";
 *         case SkTextureCompressionType::kBC1_RGBA8_UNORM: return "kBC1_RGBA8_UNORM";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun compressionTypeToStr(compression: SkTextureCompressionType): Char {
  TODO("Implement compressionTypeToStr")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr const char* BackendApiToStr(BackendApi backend) {
 *     switch (backend) {
 *         case BackendApi::kDawn:        return "kDawn";
 *         case BackendApi::kMetal:       return "kMetal";
 *         case BackendApi::kVulkan:      return "kVulkan";
 *         case BackendApi::kMock:        return "kMock";
 *         case BackendApi::kUnsupported: return "kUnsupported";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun backendApiToStr(backend: BackendApi): Char {
  TODO("Implement backendApiToStr")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool PadVec3Size(Layout layout) { return layout == Layout::kMetal; }
 * ```
 */
public fun padVec3Size(layout: Layout): Boolean {
  TODO("Implement padVec3Size")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool AlignArraysAsVec4(Layout layout) { return layout == Layout::kStd140; }
 * ```
 */
public fun alignArraysAsVec4(layout: Layout): Boolean {
  TODO("Implement alignArraysAsVec4")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool UseFullPrecision(Layout layout) { return layout != Layout::kMetal; }
 * ```
 */
public fun useFullPrecision(layout: Layout): Boolean {
  TODO("Implement useFullPrecision")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> WrapTexture(skgpu::graphite::Recorder*,
 *                                   const skgpu::graphite::BackendTexture&,
 *                                   SkColorType colorType,
 *                                   SkAlphaType alphaType,
 *                                   sk_sp<SkColorSpace> colorSpace,
 *                                   TextureReleaseProc = nullptr,
 *                                   ReleaseContext = nullptr,
 *                                   std::string_view label = {})
 * ```
 */
public fun wrapTexture(
  param0: Recorder?,
  param1: BackendTexture,
  param2: Int,
  param3: Int,
  param4: Int,
  param5: TextureReleaseProc,
  param6: Int,
  param7: String,
): SkSp<SkImage> {
  TODO("Implement wrapTexture")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> PromiseTextureFrom(skgpu::graphite::Recorder*,
 *                                          SkISize dimensions,
 *                                          const skgpu::graphite::TextureInfo&,
 *                                          const SkColorInfo&,
 *                                          skgpu::Origin origin,
 *                                          skgpu::graphite::Volatile,
 *                                          GraphitePromiseTextureFulfillProc,
 *                                          GraphitePromiseImageReleaseProc,
 *                                          GraphitePromiseTextureReleaseProc,
 *                                          GraphitePromiseImageContext,
 *                                          std::string_view label = {})
 * ```
 */
public fun promiseTextureFrom(
  param0: Recorder?,
  param1: Int,
  param2: TextureInfo,
  param3: Int,
  param4: Int,
  param5: Volatile,
  param6: GraphitePromiseTextureFulfillProc,
  param7: GraphitePromiseImageReleaseProc,
  param8: GraphitePromiseTextureReleaseProc,
  param9: GraphitePromiseImageContext,
  param10: String,
): SkSp<SkImage> {
  TODO("Implement promiseTextureFrom")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> PromiseTextureFromYUVA(skgpu::graphite::Recorder*,
 *                                              const skgpu::graphite::YUVABackendTextureInfo&,
 *                                              sk_sp<SkColorSpace> imageColorSpace,
 *                                              skgpu::graphite::Volatile,
 *                                              GraphitePromiseTextureFulfillProc,
 *                                              GraphitePromiseImageReleaseProc,
 *                                              GraphitePromiseTextureReleaseProc,
 *                                              GraphitePromiseImageContext imageContext,
 *                                              GraphitePromiseTextureFulfillContext planeContexts[],
 *                                              std::string_view label = {})
 * ```
 */
public fun promiseTextureFromYUVA(
  param0: Recorder?,
  param1: YUVABackendTextureInfo,
  param2: Int,
  param3: Volatile,
  param4: GraphitePromiseTextureFulfillProc,
  param5: GraphitePromiseImageReleaseProc,
  param6: GraphitePromiseTextureReleaseProc,
  param7: GraphitePromiseImageContext,
  param8: GraphitePromiseTextureFulfillContext?,
  param9: String,
): SkSp<SkImage> {
  TODO("Implement promiseTextureFromYUVA")
}

/**
 * C++ original:
 * ```cpp
 * inline sk_sp<SkImage> TextureFromImage(skgpu::graphite::Recorder* r,
 *                                        const sk_sp<const SkImage>& img,
 *                                        SkImage::RequiredProperties props = {}) {
 *     return TextureFromImage(r, img.get(), props);
 * }
 * ```
 */
public fun textureFromImage(
  param0: Recorder?,
  param1: Int,
  param2: Int,
): SkSp<SkImage> {
  TODO("Implement textureFromImage")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> TextureFromYUVAPixmaps(skgpu::graphite::Recorder*,
 *                                              const SkYUVAPixmaps& pixmaps,
 *                                              SkImage::RequiredProperties = {},
 *                                              bool limitToMaxTextureSize = false,
 *                                              sk_sp<SkColorSpace> imgColorSpace = nullptr,
 *                                              std::string_view label = {})
 * ```
 */
public fun textureFromYUVAPixmaps(
  param0: Recorder?,
  param1: SkYUVAPixmaps,
  param2: Int,
  param3: Boolean,
  param4: Int,
  param5: String,
): SkSp<SkImage> {
  TODO("Implement textureFromYUVAPixmaps")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> TextureFromYUVATextures(
 *         skgpu::graphite::Recorder* recorder,
 *         const skgpu::graphite::YUVABackendTextures& yuvaBackendTextures,
 *         sk_sp<SkColorSpace> imageColorSpace,
 *         TextureReleaseProc = nullptr,
 *         ReleaseContext = nullptr,
 *         std::string_view label = {})
 * ```
 */
public fun textureFromYUVATextures(
  param0: Recorder?,
  param1: YUVABackendTextures,
  param2: Int,
  param3: TextureReleaseProc,
  param4: Int,
  param5: String,
): SkSp<SkImage> {
  TODO("Implement textureFromYUVATextures")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> SubsetTextureFrom(skgpu::graphite::Recorder* recorder,
 *                                         const SkImage* img,
 *                                         const SkIRect& subset,
 *                                         SkImage::RequiredProperties props = {})
 * ```
 */
public fun subsetTextureFrom(
  param0: Recorder?,
  param1: Int?,
  param2: SkIRect,
  param3: Int,
): SkSp<SkImage> {
  TODO("Implement subsetTextureFrom")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr inline size_t VertexAttribTypeSize(VertexAttribType type) {
 *     switch (type) {
 *         case VertexAttribType::kFloat:
 *             return sizeof(float);
 *         case VertexAttribType::kFloat2:
 *             return 2 * sizeof(float);
 *         case VertexAttribType::kFloat3:
 *             return 3 * sizeof(float);
 *         case VertexAttribType::kFloat4:
 *             return 4 * sizeof(float);
 *         case VertexAttribType::kHalf:
 *             return sizeof(uint16_t);
 *         case VertexAttribType::kHalf2:
 *             return 2 * sizeof(uint16_t);
 *         case VertexAttribType::kHalf4:
 *             return 4 * sizeof(uint16_t);
 *         case VertexAttribType::kInt2:
 *             return 2 * sizeof(int32_t);
 *         case VertexAttribType::kInt3:
 *             return 3 * sizeof(int32_t);
 *         case VertexAttribType::kInt4:
 *             return 4 * sizeof(int32_t);
 *         case VertexAttribType::kUInt2:
 *             return 2 * sizeof(uint32_t);
 *         case VertexAttribType::kByte:
 *             return 1 * sizeof(char);
 *         case VertexAttribType::kByte2:
 *             return 2 * sizeof(char);
 *         case VertexAttribType::kByte4:
 *             return 4 * sizeof(char);
 *         case VertexAttribType::kUByte:
 *             return 1 * sizeof(char);
 *         case VertexAttribType::kUByte2:
 *             return 2 * sizeof(char);
 *         case VertexAttribType::kUByte4:
 *             return 4 * sizeof(char);
 *         case VertexAttribType::kUByte_norm:
 *             return 1 * sizeof(char);
 *         case VertexAttribType::kUByte4_norm:
 *             return 4 * sizeof(char);
 *         case VertexAttribType::kShort2:
 *             return 2 * sizeof(int16_t);
 *         case VertexAttribType::kShort4:
 *             return 4 * sizeof(int16_t);
 *         case VertexAttribType::kUShort2: [[fallthrough]];
 *         case VertexAttribType::kUShort2_norm:
 *             return 2 * sizeof(uint16_t);
 *         case VertexAttribType::kInt:
 *             return sizeof(int32_t);
 *         case VertexAttribType::kUInt:
 *             return sizeof(uint32_t);
 *         case VertexAttribType::kUShort_norm:
 *             return sizeof(uint16_t);
 *         case VertexAttribType::kUShort4_norm:
 *             return 4 * sizeof(uint16_t);
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun vertexAttribTypeSize(type: VertexAttribType): ULong {
  TODO("Implement vertexAttribTypeSize")
}

/**
 * C++ original:
 * ```cpp
 * template <typename AddBlendToKeyT, typename AddSrcToKeyT, typename AddDstToKeyT>
 * void Blend(const KeyContext& keyContext,
 *            AddBlendToKeyT addBlendToKey,
 *            AddSrcToKeyT addSrcToKey,
 *            AddDstToKeyT addDstToKey) {
 *     BlendComposeBlock::BeginBlock(keyContext);
 *
 *         addSrcToKey();
 *
 *         addDstToKey();
 *
 *         addBlendToKey();
 *
 *     keyContext.paintParamsKeyBuilder()->endBlock();  // BlendComposeBlock
 * }
 * ```
 */
public fun <AddBlendToKeyT, AddSrcToKeyT, AddDstToKeyT> blend(
  keyContext: KeyContext,
  addBlendToKey: AddBlendToKeyT,
  addSrcToKey: AddSrcToKeyT,
  addDstToKey: AddDstToKeyT,
) {
  TODO("Implement blend")
}

/**
 * C++ original:
 * ```cpp
 * template <typename AddInnerToKeyT, typename AddOuterToKeyT>
 * void Compose(const KeyContext& keyContext,
 *              AddInnerToKeyT addInnerToKey,
 *              AddOuterToKeyT addOuterToKey) {
 *     ComposeBlock::BeginBlock(keyContext);
 *
 *         addInnerToKey();
 *
 *         addOuterToKey();
 *
 *     keyContext.paintParamsKeyBuilder()->endBlock();  // ComposeBlock
 * }
 * ```
 */
public fun <AddInnerToKeyT, AddOuterToKeyT> compose(
  keyContext: KeyContext,
  addInnerToKey: AddInnerToKeyT,
  addOuterToKey: AddOuterToKeyT,
) {
  TODO("Implement compose")
}

/**
 * C++ original:
 * ```cpp
 * inline skgpu::graphite::ReadSwizzle SwizzleClassToReadEnum(const skgpu::Swizzle& swizzle) {
 *     if (swizzle == skgpu::Swizzle::RGBA()) {
 *         return skgpu::graphite::ReadSwizzle::kRGBA;
 *     } else if (swizzle == skgpu::Swizzle::RGB1()) {
 *         return skgpu::graphite::ReadSwizzle::kRGB1;
 *     } else if (swizzle == skgpu::Swizzle("rrr1")) {
 *         return skgpu::graphite::ReadSwizzle::kRRR1;
 *     } else if (swizzle == skgpu::Swizzle::BGRA()) {
 *         return skgpu::graphite::ReadSwizzle::kBGRA;
 *     } else if (swizzle == skgpu::Swizzle("000r")) {
 *         return skgpu::graphite::ReadSwizzle::k000R;
 *     } else {
 *         SKGPU_LOG_W("%s is an unsupported read swizzle. Defaulting to RGBA.\n",
 *                     swizzle.asString().data());
 *         return skgpu::graphite::ReadSwizzle::kRGBA;
 *     }
 * }
 * ```
 */
public fun swizzleClassToReadEnum(swizzle: Swizzle): ReadSwizzle {
  TODO("Implement swizzleClassToReadEnum")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> Image(SkSpan<const SkColorInfo> colorInfos,
 *                                          SkSpan<const SkTileMode> = { kAllTileModes })
 * ```
 */
public fun image(param0: Int, param1: Int): SkSp<PrecompileShader> {
  TODO("Implement image")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> RawImage(ImageShaderFlags = ImageShaderFlags::kExcludeCubic,
 *                                             SkSpan<const SkColorInfo> = {},
 *                                             SkSpan<const SkTileMode> = { kAllTileModes })
 * ```
 */
public fun rawImage(
  param0: ImageShaderFlags,
  param1: Int,
  param2: Int,
): SkSp<PrecompileShader> {
  TODO("Implement rawImage")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> YUVImage(
 *             YUVImageShaderFlags = YUVImageShaderFlags::kExcludeCubic,
 *             SkSpan<const SkColorInfo> = {})
 * ```
 */
public fun yUVImage(param0: YUVImageShaderFlags, param1: Int): SkSp<PrecompileShader> {
  TODO("Implement yUVImage")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> WorkingColorSpace(
 *             SkSpan<const sk_sp<PrecompileShader>> shaders,
 *             SkSpan<const sk_sp<SkColorSpace>> inputSpaces,
 *             SkSpan<const sk_sp<SkColorSpace>> outputSpaces = {})
 * ```
 */
public fun workingColorSpace(
  param0: Int,
  param1: Int,
  param2: Int,
): SkSp<PrecompileShader> {
  TODO("Implement workingColorSpace")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> MakePrecompileShader(
 *         sk_sp<SkRuntimeEffect> effect,
 *         SkSpan<const PrecompileChildOptions> childOptions = {})
 * ```
 */
public fun makePrecompileShader(param0: Int, param1: Int): SkSp<PrecompileShader> {
  TODO("Implement makePrecompileShader")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileColorFilter> MakePrecompileColorFilter(
 *         sk_sp<SkRuntimeEffect> effect,
 *         SkSpan<const PrecompileChildOptions> childOptions = {})
 * ```
 */
public fun makePrecompileColorFilter(param0: Int, param1: Int): SkSp<PrecompileColorFilter> {
  TODO("Implement makePrecompileColorFilter")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileBlender> MakePrecompileBlender(
 *         sk_sp<SkRuntimeEffect> effect,
 *         SkSpan<const PrecompileChildOptions> childOptions = {})
 * ```
 */
public fun makePrecompileBlender(param0: Int, param1: Int): SkSp<PrecompileBlender> {
  TODO("Implement makePrecompileBlender")
}

/**
 * C++ original:
 * ```cpp
 * inline bool SkSLToWGSL(const SkSL::ShaderCaps* caps,
 *                        const std::string& sksl,
 *                        SkSL::ProgramKind programKind,
 *                        const SkSL::ProgramSettings& settings,
 *                        SkSL::NativeShader* wgsl,
 *                        SkSL::ProgramInterface* outInterface,
 *                        ShaderErrorHandler* errorHandler) {
 *     return SkSLToBackend(caps,
 *                          &SkSL::ToWGSL,
 *                          "WGSL",
 *                          sksl,
 *                          programKind,
 *                          settings,
 *                          wgsl,
 *                          outInterface,
 *                          errorHandler);
 * }
 * ```
 */
public fun skSLToWGSL(
  caps: ShaderCaps?,
  sksl: String,
  programKind: ProgramKind,
  settings: ProgramSettings,
  wgsl: NativeShader?,
  outInterface: ProgramInterface?,
  errorHandler: ShaderErrorHandler?,
): Boolean {
  TODO("Implement skSLToWGSL")
}

/**
 * C++ original:
 * ```cpp
 * static inline constexpr size_t GrSizeDivRoundUp(size_t x, size_t y) { return (x + (y - 1)) / y; }
 * ```
 */
public fun grSizeDivRoundUp(x: ULong, y: ULong): ULong {
  TODO("Implement grSizeDivRoundUp")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrIsPrimTypeLines(GrPrimitiveType type) {
 *     return GrPrimitiveType::kLines == type || GrPrimitiveType::kLineStrip == type;
 * }
 * ```
 */
public fun grIsPrimTypeLines(type: GrPrimitiveType): Boolean {
  TODO("Implement grIsPrimTypeLines")
}

/**
 * C++ original:
 * ```cpp
 * inline GrFillRule GrFillRuleForPathFillType(SkPathFillType fillType) {
 *     switch (fillType) {
 *         case SkPathFillType::kWinding:
 *         case SkPathFillType::kInverseWinding:
 *             return GrFillRule::kNonzero;
 *         case SkPathFillType::kEvenOdd:
 *         case SkPathFillType::kInverseEvenOdd:
 *             return GrFillRule::kEvenOdd;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grFillRuleForPathFillType(fillType: SkPathFillType): GrFillRule {
  TODO("Implement grFillRuleForPathFillType")
}

/**
 * C++ original:
 * ```cpp
 * inline GrFillRule GrFillRuleForSkPath(const SkPath& path) {
 *     return GrFillRuleForPathFillType(path.getFillType());
 * }
 * ```
 */
public fun grFillRuleForSkPath(path: SkPath): GrFillRule {
  TODO("Implement grFillRuleForSkPath")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrAATypeIsHW(GrAAType type) {
 *     switch (type) {
 *         case GrAAType::kNone:
 *             return false;
 *         case GrAAType::kCoverage:
 *             return false;
 *         case GrAAType::kMSAA:
 *             return true;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grAATypeIsHW(type: GrAAType): Boolean {
  TODO("Implement grAATypeIsHW")
}

/**
 * C++ original:
 * ```cpp
 * static inline GrQuadAAFlags SkToGrQuadAAFlags(unsigned flags) {
 *     return static_cast<GrQuadAAFlags>(flags);
 * }
 * ```
 */
public fun skToGrQuadAAFlags(flags: UInt): GrQuadAAFlags {
  TODO("Implement skToGrQuadAAFlags")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool GrTextureTypeHasRestrictedSampling(GrTextureType type) {
 *     switch (type) {
 *         case GrTextureType::k2D:
 *             return false;
 *         case GrTextureType::kRectangle:
 *             return true;
 *         case GrTextureType::kExternal:
 *             return true;
 *         default:
 *             SK_ABORT("Unexpected texture type");
 *     }
 * }
 * ```
 */
public fun grTextureTypeHasRestrictedSampling(type: GrTextureType): Boolean {
  TODO("Implement grTextureTypeHasRestrictedSampling")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrClipEdgeTypeIsFill(GrClipEdgeType edgeType) {
 *     return (GrClipEdgeType::kFillAA == edgeType || GrClipEdgeType::kFillBW == edgeType);
 * }
 * ```
 */
public fun grClipEdgeTypeIsFill(edgeType: GrClipEdgeType): Boolean {
  TODO("Implement grClipEdgeTypeIsFill")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrClipEdgeTypeIsInverseFill(GrClipEdgeType edgeType) {
 *     return (GrClipEdgeType::kInverseFillAA == edgeType ||
 *             GrClipEdgeType::kInverseFillBW == edgeType);
 * }
 * ```
 */
public fun grClipEdgeTypeIsInverseFill(edgeType: GrClipEdgeType): Boolean {
  TODO("Implement grClipEdgeTypeIsInverseFill")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrClipEdgeTypeIsAA(GrClipEdgeType edgeType) {
 *     return (GrClipEdgeType::kFillBW != edgeType &&
 *             GrClipEdgeType::kInverseFillBW != edgeType);
 * }
 * ```
 */
public fun grClipEdgeTypeIsAA(edgeType: GrClipEdgeType): Boolean {
  TODO("Implement grClipEdgeTypeIsAA")
}

/**
 * C++ original:
 * ```cpp
 * static inline GrClipEdgeType GrInvertClipEdgeType(GrClipEdgeType edgeType) {
 *     switch (edgeType) {
 *         case GrClipEdgeType::kFillBW:
 *             return GrClipEdgeType::kInverseFillBW;
 *         case GrClipEdgeType::kFillAA:
 *             return GrClipEdgeType::kInverseFillAA;
 *         case GrClipEdgeType::kInverseFillBW:
 *             return GrClipEdgeType::kFillBW;
 *         case GrClipEdgeType::kInverseFillAA:
 *             return GrClipEdgeType::kFillAA;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grInvertClipEdgeType(edgeType: GrClipEdgeType): GrClipEdgeType {
  TODO("Implement grInvertClipEdgeType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr SkColorType GrColorTypeToSkColorType(GrColorType ct) {
 *     switch (ct) {
 *         case GrColorType::kUnknown:          return kUnknown_SkColorType;
 *         case GrColorType::kAlpha_8:          return kAlpha_8_SkColorType;
 *         case GrColorType::kBGR_565:          return kRGB_565_SkColorType;
 *         case GrColorType::kRGB_565:          return kUnknown_SkColorType;
 *         case GrColorType::kABGR_4444:        return kARGB_4444_SkColorType;
 *         case GrColorType::kRGBA_8888:        return kRGBA_8888_SkColorType;
 *         case GrColorType::kRGBA_8888_SRGB:   return kSRGBA_8888_SkColorType;
 *         case GrColorType::kRGB_888x:         return kRGB_888x_SkColorType;
 *         case GrColorType::kRG_88:            return kR8G8_unorm_SkColorType;
 *         case GrColorType::kBGRA_8888:        return kBGRA_8888_SkColorType;
 *         case GrColorType::kRGBA_1010102:     return kRGBA_1010102_SkColorType;
 *         case GrColorType::kBGRA_1010102:     return kBGRA_1010102_SkColorType;
 *         case GrColorType::kRGB_101010x:      return kRGB_101010x_SkColorType;
 *         case GrColorType::kRGBA_10x6:        return kRGBA_10x6_SkColorType;
 *         case GrColorType::kGray_8:           return kGray_8_SkColorType;
 *         case GrColorType::kGrayAlpha_88:     return kUnknown_SkColorType;
 *         case GrColorType::kAlpha_F16:        return kA16_float_SkColorType;
 *         case GrColorType::kRGBA_F16:         return kRGBA_F16_SkColorType;
 *         case GrColorType::kRGBA_F16_Clamped: return kRGBA_F16Norm_SkColorType;
 *         case GrColorType::kRGB_F16F16F16x:   return kRGB_F16F16F16x_SkColorType;
 *         case GrColorType::kRGBA_F32:         return kRGBA_F32_SkColorType;
 *         case GrColorType::kAlpha_8xxx:       return kUnknown_SkColorType;
 *         case GrColorType::kAlpha_F32xxx:     return kUnknown_SkColorType;
 *         case GrColorType::kGray_8xxx:        return kUnknown_SkColorType;
 *         case GrColorType::kR_8xxx:           return kUnknown_SkColorType;
 *         case GrColorType::kAlpha_16:         return kA16_unorm_SkColorType;
 *         case GrColorType::kRG_1616:          return kR16G16_unorm_SkColorType;
 *         case GrColorType::kRGBA_16161616:    return kR16G16B16A16_unorm_SkColorType;
 *         case GrColorType::kRG_F16:           return kR16G16_float_SkColorType;
 *         case GrColorType::kRGB_888:          return kUnknown_SkColorType;
 *         case GrColorType::kR_8:              return kR8_unorm_SkColorType;
 *         case GrColorType::kR_16:             return kR16_unorm_SkColorType;
 *         case GrColorType::kR_F16:            return kUnknown_SkColorType;
 *         case GrColorType::kGray_F16:         return kUnknown_SkColorType;
 *         case GrColorType::kARGB_4444:        return kUnknown_SkColorType;
 *         case GrColorType::kBGRA_4444:        return kUnknown_SkColorType;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grColorTypeToSkColorType(ct: GrColorType): SkColorType {
  TODO("Implement grColorTypeToSkColorType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr GrColorType SkColorTypeToGrColorType(SkColorType ct) {
 *     switch (ct) {
 *         case kUnknown_SkColorType:            return GrColorType::kUnknown;
 *         case kAlpha_8_SkColorType:            return GrColorType::kAlpha_8;
 *         case kRGB_565_SkColorType:            return GrColorType::kBGR_565;
 *         case kARGB_4444_SkColorType:          return GrColorType::kABGR_4444;
 *         case kRGBA_8888_SkColorType:          return GrColorType::kRGBA_8888;
 *         case kSRGBA_8888_SkColorType:         return GrColorType::kRGBA_8888_SRGB;
 *         case kRGB_888x_SkColorType:           return GrColorType::kRGB_888x;
 *         case kBGRA_8888_SkColorType:          return GrColorType::kBGRA_8888;
 *         case kGray_8_SkColorType:             return GrColorType::kGray_8;
 *         case kRGBA_F16Norm_SkColorType:       return GrColorType::kRGBA_F16_Clamped;
 *         case kRGBA_F16_SkColorType:           return GrColorType::kRGBA_F16;
 *         case kRGB_F16F16F16x_SkColorType:     return GrColorType::kRGB_F16F16F16x;
 *         case kRGBA_1010102_SkColorType:       return GrColorType::kRGBA_1010102;
 *         case kRGB_101010x_SkColorType:        return GrColorType::kRGB_101010x;
 *         case kBGRA_1010102_SkColorType:       return GrColorType::kBGRA_1010102;
 *         case kBGR_101010x_SkColorType:        return GrColorType::kUnknown;
 *         case kBGR_101010x_XR_SkColorType:     return GrColorType::kUnknown;
 *         case kBGRA_10101010_XR_SkColorType:   return GrColorType::kUnknown;
 *         case kRGBA_10x6_SkColorType:          return GrColorType::kRGBA_10x6;
 *         case kRGBA_F32_SkColorType:           return GrColorType::kRGBA_F32;
 *         case kR8G8_unorm_SkColorType:         return GrColorType::kRG_88;
 *         case kA16_unorm_SkColorType:          return GrColorType::kAlpha_16;
 *         case kR16_unorm_SkColorType:          return GrColorType::kR_16;
 *         case kR16G16_unorm_SkColorType:       return GrColorType::kRG_1616;
 *         case kA16_float_SkColorType:          return GrColorType::kAlpha_F16;
 *         case kR16G16_float_SkColorType:       return GrColorType::kRG_F16;
 *         case kR16G16B16A16_unorm_SkColorType: return GrColorType::kRGBA_16161616;
 *         case kR8_unorm_SkColorType:           return GrColorType::kR_8;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun skColorTypeToGrColorType(ct: SkColorType): GrColorType {
  TODO("Implement skColorTypeToGrColorType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr uint32_t GrColorTypeChannelFlags(GrColorType ct) {
 *     switch (ct) {
 *         case GrColorType::kUnknown:          return 0;
 *         case GrColorType::kAlpha_8:          return kAlpha_SkColorChannelFlag;
 *         case GrColorType::kBGR_565:          return kRGB_SkColorChannelFlags;
 *         case GrColorType::kRGB_565:          return kRGB_SkColorChannelFlags;
 *         case GrColorType::kABGR_4444:        return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGBA_8888:        return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGBA_8888_SRGB:   return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGB_888x:         return kRGB_SkColorChannelFlags;
 *         case GrColorType::kRG_88:            return kRG_SkColorChannelFlags;
 *         case GrColorType::kBGRA_8888:        return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGBA_1010102:     return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kBGRA_1010102:     return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGB_101010x:      return kRGB_SkColorChannelFlags;
 *         case GrColorType::kRGBA_10x6:        return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kGray_8:           return kGray_SkColorChannelFlag;
 *         case GrColorType::kGrayAlpha_88:     return kGrayAlpha_SkColorChannelFlags;
 *         case GrColorType::kAlpha_F16:        return kAlpha_SkColorChannelFlag;
 *         case GrColorType::kRGBA_F16:         return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGBA_F16_Clamped: return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRGB_F16F16F16x:   return kRGB_SkColorChannelFlags;
 *         case GrColorType::kRGBA_F32:         return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kAlpha_8xxx:       return kAlpha_SkColorChannelFlag;
 *         case GrColorType::kAlpha_F32xxx:     return kAlpha_SkColorChannelFlag;
 *         case GrColorType::kGray_8xxx:        return kGray_SkColorChannelFlag;
 *         case GrColorType::kR_8xxx:           return kRed_SkColorChannelFlag;
 *         case GrColorType::kAlpha_16:         return kAlpha_SkColorChannelFlag;
 *         case GrColorType::kRG_1616:          return kRG_SkColorChannelFlags;
 *         case GrColorType::kRGBA_16161616:    return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kRG_F16:           return kRG_SkColorChannelFlags;
 *         case GrColorType::kRGB_888:          return kRGB_SkColorChannelFlags;
 *         case GrColorType::kR_8:              return kRed_SkColorChannelFlag;
 *         case GrColorType::kR_16:             return kRed_SkColorChannelFlag;
 *         case GrColorType::kR_F16:            return kRed_SkColorChannelFlag;
 *         case GrColorType::kGray_F16:         return kGray_SkColorChannelFlag;
 *         case GrColorType::kARGB_4444:        return kRGBA_SkColorChannelFlags;
 *         case GrColorType::kBGRA_4444:        return kRGBA_SkColorChannelFlags;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grColorTypeChannelFlags(ct: GrColorType): UInt {
  TODO("Implement grColorTypeChannelFlags")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr GrColorFormatDesc GrGetColorTypeDesc(GrColorType ct) {
 *     switch (ct) {
 *         case GrColorType::kUnknown:
 *             return GrColorFormatDesc::MakeInvalid();
 *         case GrColorType::kAlpha_8:
 *             return GrColorFormatDesc::MakeAlpha(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kBGR_565:
 *             return GrColorFormatDesc::MakeRGB(5, 6, 5, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGB_565:
 *             return GrColorFormatDesc::MakeRGB(5, 6, 5, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kABGR_4444:
 *             return GrColorFormatDesc::MakeRGBA(4, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGBA_8888:
 *             return GrColorFormatDesc::MakeRGBA(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGBA_8888_SRGB:
 *             return GrColorFormatDesc::MakeRGBA(8, GrColorTypeEncoding::kSRGBUnorm);
 *         case GrColorType::kRGB_888x:
 *             return GrColorFormatDesc::MakeRGB(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRG_88:
 *             return GrColorFormatDesc::MakeRG(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kBGRA_8888:
 *             return GrColorFormatDesc::MakeRGBA(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGBA_1010102:
 *             return GrColorFormatDesc::MakeRGBA(10, 2, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kBGRA_1010102:
 *             return GrColorFormatDesc::MakeRGBA(10, 2, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGB_101010x:
 *             return GrColorFormatDesc::MakeRGB(10, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGBA_10x6:
 *             return GrColorFormatDesc::MakeRGBA(10, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kGray_8:
 *             return GrColorFormatDesc::MakeGray(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kGrayAlpha_88:
 *             return GrColorFormatDesc::MakeGrayAlpha(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kAlpha_F16:
 *             return GrColorFormatDesc::MakeAlpha(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kRGBA_F16:
 *             return GrColorFormatDesc::MakeRGBA(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kRGB_F16F16F16x:
 *             return GrColorFormatDesc::MakeRGB(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kRGBA_F16_Clamped:
 *             return GrColorFormatDesc::MakeRGBA(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kRGBA_F32:
 *             return GrColorFormatDesc::MakeRGBA(32, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kAlpha_8xxx:
 *             return GrColorFormatDesc::MakeAlpha(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kAlpha_F32xxx:
 *             return GrColorFormatDesc::MakeAlpha(32, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kGray_8xxx:
 *             return GrColorFormatDesc::MakeGray(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kR_8xxx:
 *             return GrColorFormatDesc::MakeR(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kAlpha_16:
 *             return GrColorFormatDesc::MakeAlpha(16, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRG_1616:
 *             return GrColorFormatDesc::MakeRG(16, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRGBA_16161616:
 *             return GrColorFormatDesc::MakeRGBA(16, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kRG_F16:
 *             return GrColorFormatDesc::MakeRG(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kRGB_888:
 *             return GrColorFormatDesc::MakeRGB(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kR_8:
 *             return GrColorFormatDesc::MakeR(8, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kR_16:
 *             return GrColorFormatDesc::MakeR(16, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kR_F16:
 *             return GrColorFormatDesc::MakeR(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kGray_F16:
 *             return GrColorFormatDesc::MakeGray(16, GrColorTypeEncoding::kFloat);
 *         case GrColorType::kARGB_4444:
 *             return GrColorFormatDesc::MakeRGBA(4, GrColorTypeEncoding::kUnorm);
 *         case GrColorType::kBGRA_4444:
 *             return GrColorFormatDesc::MakeRGBA(4, GrColorTypeEncoding::kUnorm);
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grGetColorTypeDesc(ct: GrColorType): GrColorFormatDesc {
  TODO("Implement grGetColorTypeDesc")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr GrClampType GrColorTypeClampType(GrColorType colorType) {
 *     if (GrGetColorTypeDesc(colorType).encoding() == GrColorTypeEncoding::kUnorm ||
 *         GrGetColorTypeDesc(colorType).encoding() == GrColorTypeEncoding::kSRGBUnorm) {
 *         return GrClampType::kAuto;
 *     }
 *     return GrColorType::kRGBA_F16_Clamped == colorType ? GrClampType::kManual : GrClampType::kNone;
 * }
 * ```
 */
public fun grColorTypeClampType(colorType: GrColorType): GrClampType {
  TODO("Implement grColorTypeClampType")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrColorTypeIsWiderThan(GrColorType colorType, int n) {
 *     SkASSERT(n > 0);
 *     auto desc = GrGetColorTypeDesc(colorType);
 *     return (desc.r() && desc.r() > n )||
 *            (desc.g() && desc.g() > n) ||
 *            (desc.b() && desc.b() > n) ||
 *            (desc.a() && desc.a() > n) ||
 *            (desc.gray() && desc.gray() > n);
 * }
 * ```
 */
public fun grColorTypeIsWiderThan(colorType: GrColorType, n: Int): Boolean {
  TODO("Implement grColorTypeIsWiderThan")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrColorTypeIsAlphaOnly(GrColorType ct) {
 *     return GrColorTypeChannelFlags(ct) == kAlpha_SkColorChannelFlag;
 * }
 * ```
 */
public fun grColorTypeIsAlphaOnly(ct: GrColorType): Boolean {
  TODO("Implement grColorTypeIsAlphaOnly")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr bool GrColorTypeHasAlpha(GrColorType ct) {
 *     return GrColorTypeChannelFlags(ct) & kAlpha_SkColorChannelFlag;
 * }
 * ```
 */
public fun grColorTypeHasAlpha(ct: GrColorType): Boolean {
  TODO("Implement grColorTypeHasAlpha")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr size_t GrColorTypeBytesPerPixel(GrColorType ct) {
 *     switch (ct) {
 *         case GrColorType::kUnknown:          return 0;
 *         case GrColorType::kAlpha_8:          return 1;
 *         case GrColorType::kBGR_565:          return 2;
 *         case GrColorType::kRGB_565:          return 2;
 *         case GrColorType::kABGR_4444:        return 2;
 *         case GrColorType::kRGBA_8888:        return 4;
 *         case GrColorType::kRGBA_8888_SRGB:   return 4;
 *         case GrColorType::kRGB_888x:         return 4;
 *         case GrColorType::kRG_88:            return 2;
 *         case GrColorType::kBGRA_8888:        return 4;
 *         case GrColorType::kRGBA_1010102:     return 4;
 *         case GrColorType::kBGRA_1010102:     return 4;
 *         case GrColorType::kRGB_101010x:      return 4;
 *         case GrColorType::kRGBA_10x6:        return 8;
 *         case GrColorType::kGray_8:           return 1;
 *         case GrColorType::kGrayAlpha_88:     return 2;
 *         case GrColorType::kAlpha_F16:        return 2;
 *         case GrColorType::kRGBA_F16:         return 8;
 *         case GrColorType::kRGBA_F16_Clamped: return 8;
 *         case GrColorType::kRGB_F16F16F16x:   return 8;
 *         case GrColorType::kRGBA_F32:         return 16;
 *         case GrColorType::kAlpha_8xxx:       return 4;
 *         case GrColorType::kAlpha_F32xxx:     return 16;
 *         case GrColorType::kGray_8xxx:        return 4;
 *         case GrColorType::kR_8xxx:           return 4;
 *         case GrColorType::kAlpha_16:         return 2;
 *         case GrColorType::kRG_1616:          return 4;
 *         case GrColorType::kRGBA_16161616:    return 8;
 *         case GrColorType::kRG_F16:           return 4;
 *         case GrColorType::kRGB_888:          return 3;
 *         case GrColorType::kR_8:              return 1;
 *         case GrColorType::kR_16:             return 2;
 *         case GrColorType::kR_F16:            return 2;
 *         case GrColorType::kGray_F16:         return 2;
 *         case GrColorType::kARGB_4444:        return 2;
 *         case GrColorType::kBGRA_4444:        return 2;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun grColorTypeBytesPerPixel(ct: GrColorType): ULong {
  TODO("Implement grColorTypeBytesPerPixel")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<PrecompileShader> VulkanYCbCrImage(skgpu::VulkanYcbcrConversionInfo& YCbCrInfo,
 *                                                 ImageShaderFlags = ImageShaderFlags::kAll,
 *                                                 SkSpan<const SkColorInfo> = {},
 *                                                 SkSpan<const SkTileMode> = { kAllTileModes })
 * ```
 */
public fun vulkanYCbCrImage(
  param0: VulkanYcbcrConversionInfo,
  param1: Int,
  param2: Int,
  param3: Int,
): SkSp<PrecompileShader> {
  TODO("Implement vulkanYCbCrImage")
}

/**
 * C++ original:
 * ```cpp
 * gr_rp<T> gr_ref_rp(const T* obj) {
 *     return gr_rp<T>(const_cast<T*>(SkSafeRef(obj)));
 * }
 * ```
 */
public fun <T> grRefRp(obj: T): GrRp<T> {
  TODO("Implement grRefRp")
}

/**
 * C++ original:
 * ```cpp
 * inline void DawnNativeProcessEventsFunction(const wgpu::Instance& instance) {
 *     instance.ProcessEvents();
 * }
 * ```
 */
public fun dawnNativeProcessEventsFunction(instance: Instance) {
  TODO("Implement dawnNativeProcessEventsFunction")
}
