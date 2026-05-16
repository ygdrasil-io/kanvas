package org.skia.gpu

import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkEnumBitMask
import org.skia.core.SkShaderBase
import org.skia.effects.SkGradient

/**
 * C++ original:
 * ```cpp
 * class PrecompileGradientShader final : public PrecompileShader {
 * public:
 *     PrecompileGradientShader(SkShaderBase::GradientType type,
 *                              SkEnumBitMask<GradientShaderFlags> flags,
 *                              const SkGradient::Interpolation& interpolation)
 *             : fType(type)
 *             , fInterpolation(interpolation) {
 *         this->setupStopVariants(flags);
 *     }
 *
 * private:
 *     /*
 *      * The gradients can have up to three specializations based on the number of stops.
 *      */
 *     inline static constexpr int kMaxStopVariants = 3;
 *
 *     void setupStopVariants(SkEnumBitMask<GradientShaderFlags> flags) {
 *         fNumStopVariants = 0;
 *
 *         if (flags & GradientShaderFlags::kSmall) {
 *             fStopVariants[fNumStopVariants++] = 4;
 *         }
 *         if (flags & GradientShaderFlags::kMedium) {
 *             fStopVariants[fNumStopVariants++] = 8;
 *         }
 *         if (flags & GradientShaderFlags::kLarge) {
 *             fStopVariants[fNumStopVariants++] =
 *                     GradientShaderBlocks::GradientData::kNumInternalStorageStops+1;
 *         }
 *
 *         SkASSERT(fNumStopVariants == SkPopCount(flags.value()));
 *         SkASSERT(fNumStopVariants <= kMaxStopVariants);
 *     }
 *
 *     int numIntrinsicCombinations() const override { return fNumStopVariants; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(this->numChildCombinations() == 1);
 *         SkASSERT(desiredCombination < fNumStopVariants);
 *
 *         bool useStorageBuffer = keyContext.caps()->gradientBufferSupport();
 *
 *         GradientShaderBlocks::GradientData gradData(fType,
 *                                                     fStopVariants[desiredCombination],
 *                                                     useStorageBuffer);
 *
 *         // The logic for setting up color spaces here should match that in the "add_gradient_to_key"
 *         // functions from src/gpu/graphite/KeyHelpers.cpp.
 *         sk_sp<SkColorSpace> intermediateCS = get_gradient_intermediate_cs(
 *                 keyContext.dstColorInfo().colorSpace(), fInterpolation);
 *         const SkColorSpace* dstCS = keyContext.dstColorInfo().colorSpace()
 *                                             ? keyContext.dstColorInfo().colorSpace()
 *                                             : sk_srgb_singleton();
 *
 *         ColorSpaceTransformBlock::ColorSpaceTransformData csData(
 *                 intermediateCS.get(), kPremul_SkAlphaType,
 *                 dstCS, kPremul_SkAlphaType);
 *
 *         Compose(keyContext,
 *                 /* addInnerToKey= */ [&]() -> void {
 *                     GradientShaderBlocks::AddBlock(keyContext, gradData);
 *                 },
 *                 /* addOuterToKey= */  [&]() -> void {
 *                     ColorSpaceTransformBlock::AddBlock(keyContext, csData);
 *                 });
 *     }
 *
 *     const SkShaderBase::GradientType fType;
 *     const SkGradient::Interpolation fInterpolation;
 *
 *     int fNumStopVariants = 0;
 *     int fStopVariants[kMaxStopVariants];
 * }
 * ```
 */
public class PrecompileGradientShader public constructor(
  type: SkShaderBase.GradientType,
  flags: SkEnumBitMask<GradientShaderFlags>,
  interpolation: SkGradient.Interpolation,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxStopVariants = 3
   * ```
   */
  private val fType: SkShaderBase.GradientType = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * const SkShaderBase::GradientType fType
   * ```
   */
  private val fInterpolation: SkGradient.Interpolation = TODO("Initialize fInterpolation")

  /**
   * C++ original:
   * ```cpp
   * const SkGradient::Interpolation fInterpolation
   * ```
   */
  private var fNumStopVariants: Int = TODO("Initialize fNumStopVariants")

  /**
   * C++ original:
   * ```cpp
   * int fNumStopVariants = 0
   * ```
   */
  private var fStopVariants: IntArray = TODO("Initialize fStopVariants")

  /**
   * C++ original:
   * ```cpp
   * void setupStopVariants(SkEnumBitMask<GradientShaderFlags> flags) {
   *         fNumStopVariants = 0;
   *
   *         if (flags & GradientShaderFlags::kSmall) {
   *             fStopVariants[fNumStopVariants++] = 4;
   *         }
   *         if (flags & GradientShaderFlags::kMedium) {
   *             fStopVariants[fNumStopVariants++] = 8;
   *         }
   *         if (flags & GradientShaderFlags::kLarge) {
   *             fStopVariants[fNumStopVariants++] =
   *                     GradientShaderBlocks::GradientData::kNumInternalStorageStops+1;
   *         }
   *
   *         SkASSERT(fNumStopVariants == SkPopCount(flags.value()));
   *         SkASSERT(fNumStopVariants <= kMaxStopVariants);
   *     }
   * ```
   */
  private fun setupStopVariants(flags: SkEnumBitMask<GradientShaderFlags>) {
    TODO("Implement setupStopVariants")
  }

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override { return fNumStopVariants; }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(this->numChildCombinations() == 1);
   *         SkASSERT(desiredCombination < fNumStopVariants);
   *
   *         bool useStorageBuffer = keyContext.caps()->gradientBufferSupport();
   *
   *         GradientShaderBlocks::GradientData gradData(fType,
   *                                                     fStopVariants[desiredCombination],
   *                                                     useStorageBuffer);
   *
   *         // The logic for setting up color spaces here should match that in the "add_gradient_to_key"
   *         // functions from src/gpu/graphite/KeyHelpers.cpp.
   *         sk_sp<SkColorSpace> intermediateCS = get_gradient_intermediate_cs(
   *                 keyContext.dstColorInfo().colorSpace(), fInterpolation);
   *         const SkColorSpace* dstCS = keyContext.dstColorInfo().colorSpace()
   *                                             ? keyContext.dstColorInfo().colorSpace()
   *                                             : sk_srgb_singleton();
   *
   *         ColorSpaceTransformBlock::ColorSpaceTransformData csData(
   *                 intermediateCS.get(), kPremul_SkAlphaType,
   *                 dstCS, kPremul_SkAlphaType);
   *
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     GradientShaderBlocks::AddBlock(keyContext, gradData);
   *                 },
   *                 /* addOuterToKey= */  [&]() -> void {
   *                     ColorSpaceTransformBlock::AddBlock(keyContext, csData);
   *                 });
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  public companion object {
    private val kMaxStopVariants: Int = TODO("Initialize kMaxStopVariants")
  }
}
