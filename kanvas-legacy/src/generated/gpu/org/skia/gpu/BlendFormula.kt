package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class BlendFormula {
 * public:
 *     /**
 *      * Values the shader can write to primary and secondary outputs. These are all modulated by
 *      * coverage. We will ignore the multiplies when not using coverage.
 *      */
 *     enum OutputType {
 *         kNone_OutputType,        //<! 0
 *         kCoverage_OutputType,    //<! inputCoverage
 *         kModulate_OutputType,    //<! inputColor * inputCoverage
 *         kSAModulate_OutputType,  //<! inputColor.a * inputCoverage
 *         kISAModulate_OutputType, //<! (1 - inputColor.a) * inputCoverage
 *         kISCModulate_OutputType, //<! (1 - inputColor) * inputCoverage
 *
 *         kLast_OutputType = kISCModulate_OutputType
 *     };
 *
 *     constexpr BlendFormula(OutputType primaryOut,
 *                            OutputType secondaryOut,
 *                            skgpu::BlendEquation equation,
 *                            skgpu::BlendCoeff srcCoeff,
 *                            skgpu::BlendCoeff dstCoeff)
 *             : fPrimaryOutputType(primaryOut)
 *             , fSecondaryOutputType(secondaryOut)
 *             , fBlendEquation(SkTo<uint8_t>(equation))
 *             , fSrcCoeff(SkTo<uint8_t>(srcCoeff))
 *             , fDstCoeff(SkTo<uint8_t>(dstCoeff))
 *             , fProps(GetProperties(primaryOut, secondaryOut, equation, srcCoeff, dstCoeff)) {}
 *
 *     BlendFormula(const BlendFormula&) = default;
 *     BlendFormula& operator=(const BlendFormula&) = default;
 *
 *     bool operator==(const BlendFormula& that) const {
 *         return fPrimaryOutputType == that.fPrimaryOutputType &&
 *                fSecondaryOutputType == that. fSecondaryOutputType &&
 *                fBlendEquation == that.fBlendEquation &&
 *                fSrcCoeff == that.fSrcCoeff &&
 *                fDstCoeff == that.fDstCoeff &&
 *                fProps == that.fProps;
 *     }
 *
 *     bool hasSecondaryOutput() const {
 *         return kNone_OutputType != fSecondaryOutputType;
 *     }
 *     bool modifiesDst() const {
 *         return SkToBool(fProps & kModifiesDst_Property);
 *     }
 *     bool unaffectedByDst() const {
 *         return SkToBool(fProps & kUnaffectedByDst_Property);
 *     }
 *     // We don't always fully optimize the blend formula (e.g., for opaque src-over), so we include
 *     // an "IfOpaque" variant to help set AnalysisProperties::kUnaffectedByDstValue in those cases.
 *     bool unaffectedByDstIfOpaque() const {
 *         return SkToBool(fProps & kUnaffectedByDstIfOpaque_Property);
 *     }
 *     bool usesInputColor() const {
 *         return SkToBool(fProps & kUsesInputColor_Property);
 *     }
 *     bool canTweakAlphaForCoverage() const {
 *         return SkToBool(fProps & kCanTweakAlphaForCoverage_Property);
 *     }
 *
 *     skgpu::BlendEquation equation() const {
 *         return static_cast<skgpu::BlendEquation>(fBlendEquation);
 *     }
 *
 *     skgpu::BlendCoeff srcCoeff() const {
 *         return static_cast<skgpu::BlendCoeff>(fSrcCoeff);
 *     }
 *
 *     skgpu::BlendCoeff dstCoeff() const {
 *         return static_cast<skgpu::BlendCoeff>(fDstCoeff);
 *     }
 *
 *     OutputType primaryOutput() const {
 *         return fPrimaryOutputType;
 *     }
 *
 *     OutputType secondaryOutput() const {
 *         return fSecondaryOutputType;
 *     }
 *
 * private:
 *     enum Properties {
 *         kModifiesDst_Property              = 1 << 0,
 *         kUnaffectedByDst_Property          = 1 << 1,
 *         kUnaffectedByDstIfOpaque_Property  = 1 << 2,
 *         kUsesInputColor_Property           = 1 << 3,
 *         kCanTweakAlphaForCoverage_Property = 1 << 4,
 *
 *         kLast_Property = kCanTweakAlphaForCoverage_Property
 *     };
 *     SK_DECL_BITFIELD_OPS_FRIENDS(Properties)
 *
 *     /**
 *      * Deduce the properties of a BlendFormula.
 *      */
 *     constexpr BlendFormula::Properties GetProperties(OutputType PrimaryOut,
 *                                                      OutputType SecondaryOut,
 *                                                      skgpu::BlendEquation BlendEquation,
 *                                                      skgpu::BlendCoeff SrcCoeff,
 *                                                      skgpu::BlendCoeff DstCoeff) {
 *         return
 *         // The provided formula should already be optimized before a BlendFormula is constructed.
 *         // Assert that here while setting up the properties in the constexpr constructor.
 *         SkASSERT((kNone_OutputType == PrimaryOut) ==
 *                  !skgpu::BlendCoeffsUseSrcColor(SrcCoeff, DstCoeff)),
 *         SkASSERT(!skgpu::BlendCoeffRefsSrc2(SrcCoeff)),
 *         SkASSERT((kNone_OutputType == SecondaryOut) == !skgpu::BlendCoeffRefsSrc2(DstCoeff)),
 *         SkASSERT(PrimaryOut != SecondaryOut || kNone_OutputType == PrimaryOut),
 *         SkASSERT(kNone_OutputType != PrimaryOut || kNone_OutputType == SecondaryOut),
 *
 *         static_cast<Properties>(
 *             (skgpu::BlendModifiesDst(BlendEquation, SrcCoeff, DstCoeff)
 *                         ? kModifiesDst_Property
 *                         : 0) |
 *             (!skgpu::BlendCoeffsUseDstColor(SrcCoeff, DstCoeff, false/*srcColorIsOpaque*/)
 *                         ? kUnaffectedByDst_Property
 *                         : 0) |
 *             (!skgpu::BlendCoeffsUseDstColor(SrcCoeff, DstCoeff, true/*srcColorIsOpaque*/)
 *                         ? kUnaffectedByDstIfOpaque_Property
 *                         : 0) |
 *             ((PrimaryOut >= kModulate_OutputType &&
 *                                 skgpu::BlendCoeffsUseSrcColor(SrcCoeff, DstCoeff)) ||
 *                                 (SecondaryOut >= kModulate_OutputType &&
 *                                 skgpu::BlendCoeffRefsSrc2(DstCoeff))
 *                         ? kUsesInputColor_Property
 *                         : 0) |  // We assert later that SrcCoeff doesn't ref src2.
 *             ((kModulate_OutputType == PrimaryOut || kNone_OutputType == PrimaryOut) &&
 *                                 kNone_OutputType == SecondaryOut &&
 *                                 skgpu::BlendAllowsCoverageAsAlpha(BlendEquation, SrcCoeff, DstCoeff)
 *                         ? kCanTweakAlphaForCoverage_Property
 *                         : 0));
 *     }
 *
 *     struct {
 *         // We allot the enums one more bit than they require because MSVC seems to sign-extend
 *         // them when the top bit is set. (This is in violation of the C++03 standard 9.6/4)
 *         OutputType fPrimaryOutputType   : 4;
 *         OutputType fSecondaryOutputType : 4;
 *         uint32_t   fBlendEquation       : 6;
 *         uint32_t   fSrcCoeff            : 6;
 *         uint32_t   fDstCoeff            : 6;
 *         Properties fProps               : 32 - (4 + 4 + 6 + 6 + 6);
 *     };
 *
 *     static_assert(kLast_OutputType                              < (1 << 3));
 *     static_assert(static_cast<int>(skgpu::BlendEquation::kLast) < (1 << 5));
 *     static_assert(static_cast<int>(skgpu::BlendCoeff::kLast)    < (1 << 5));
 *     static_assert(kLast_Property                                < (1 << 6));
 * }
 * ```
 */
public data class BlendFormula public constructor(
  public var fPrimaryOutputType: OutputType,
  public var fSecondaryOutputType: OutputType,
  public var fBlendEquation: Int,
  public var fSrcCoeff: Int,
  public var fDstCoeff: Int,
  public var fProps: Properties,
) {
  /**
   * C++ original:
   * ```cpp
   * BlendFormula& operator=(const BlendFormula&) = default
   * ```
   */
  public fun assign(param0: BlendFormula) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const BlendFormula& that) const {
   *         return fPrimaryOutputType == that.fPrimaryOutputType &&
   *                fSecondaryOutputType == that. fSecondaryOutputType &&
   *                fBlendEquation == that.fBlendEquation &&
   *                fSrcCoeff == that.fSrcCoeff &&
   *                fDstCoeff == that.fDstCoeff &&
   *                fProps == that.fProps;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasSecondaryOutput() const {
   *         return kNone_OutputType != fSecondaryOutputType;
   *     }
   * ```
   */
  public fun hasSecondaryOutput(): Boolean {
    TODO("Implement hasSecondaryOutput")
  }

  /**
   * C++ original:
   * ```cpp
   * bool modifiesDst() const {
   *         return SkToBool(fProps & kModifiesDst_Property);
   *     }
   * ```
   */
  public fun modifiesDst(): Boolean {
    TODO("Implement modifiesDst")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unaffectedByDst() const {
   *         return SkToBool(fProps & kUnaffectedByDst_Property);
   *     }
   * ```
   */
  public fun unaffectedByDst(): Boolean {
    TODO("Implement unaffectedByDst")
  }

  /**
   * C++ original:
   * ```cpp
   * bool unaffectedByDstIfOpaque() const {
   *         return SkToBool(fProps & kUnaffectedByDstIfOpaque_Property);
   *     }
   * ```
   */
  public fun unaffectedByDstIfOpaque(): Boolean {
    TODO("Implement unaffectedByDstIfOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usesInputColor() const {
   *         return SkToBool(fProps & kUsesInputColor_Property);
   *     }
   * ```
   */
  public fun usesInputColor(): Boolean {
    TODO("Implement usesInputColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canTweakAlphaForCoverage() const {
   *         return SkToBool(fProps & kCanTweakAlphaForCoverage_Property);
   *     }
   * ```
   */
  public fun canTweakAlphaForCoverage(): Boolean {
    TODO("Implement canTweakAlphaForCoverage")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendEquation equation() const {
   *         return static_cast<skgpu::BlendEquation>(fBlendEquation);
   *     }
   * ```
   */
  public fun equation(): BlendEquation {
    TODO("Implement equation")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendCoeff srcCoeff() const {
   *         return static_cast<skgpu::BlendCoeff>(fSrcCoeff);
   *     }
   * ```
   */
  public fun srcCoeff(): BlendCoeff {
    TODO("Implement srcCoeff")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::BlendCoeff dstCoeff() const {
   *         return static_cast<skgpu::BlendCoeff>(fDstCoeff);
   *     }
   * ```
   */
  public fun dstCoeff(): BlendCoeff {
    TODO("Implement dstCoeff")
  }

  /**
   * C++ original:
   * ```cpp
   * OutputType primaryOutput() const {
   *         return fPrimaryOutputType;
   *     }
   * ```
   */
  public fun primaryOutput(): OutputType {
    TODO("Implement primaryOutput")
  }

  /**
   * C++ original:
   * ```cpp
   * OutputType secondaryOutput() const {
   *         return fSecondaryOutputType;
   *     }
   * ```
   */
  public fun secondaryOutput(): OutputType {
    TODO("Implement secondaryOutput")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr BlendFormula::Properties GetProperties(OutputType PrimaryOut,
   *                                                      OutputType SecondaryOut,
   *                                                      skgpu::BlendEquation BlendEquation,
   *                                                      skgpu::BlendCoeff SrcCoeff,
   *                                                      skgpu::BlendCoeff DstCoeff) {
   *         return
   *         // The provided formula should already be optimized before a BlendFormula is constructed.
   *         // Assert that here while setting up the properties in the constexpr constructor.
   *         SkASSERT((kNone_OutputType == PrimaryOut) ==
   *                  !skgpu::BlendCoeffsUseSrcColor(SrcCoeff, DstCoeff)),
   *         SkASSERT(!skgpu::BlendCoeffRefsSrc2(SrcCoeff)),
   *         SkASSERT((kNone_OutputType == SecondaryOut) == !skgpu::BlendCoeffRefsSrc2(DstCoeff)),
   *         SkASSERT(PrimaryOut != SecondaryOut || kNone_OutputType == PrimaryOut),
   *         SkASSERT(kNone_OutputType != PrimaryOut || kNone_OutputType == SecondaryOut),
   *
   *         static_cast<Properties>(
   *             (skgpu::BlendModifiesDst(BlendEquation, SrcCoeff, DstCoeff)
   *                         ? kModifiesDst_Property
   *                         : 0) |
   *             (!skgpu::BlendCoeffsUseDstColor(SrcCoeff, DstCoeff, false/*srcColorIsOpaque*/)
   *                         ? kUnaffectedByDst_Property
   *                         : 0) |
   *             (!skgpu::BlendCoeffsUseDstColor(SrcCoeff, DstCoeff, true/*srcColorIsOpaque*/)
   *                         ? kUnaffectedByDstIfOpaque_Property
   *                         : 0) |
   *             ((PrimaryOut >= kModulate_OutputType &&
   *                                 skgpu::BlendCoeffsUseSrcColor(SrcCoeff, DstCoeff)) ||
   *                                 (SecondaryOut >= kModulate_OutputType &&
   *                                 skgpu::BlendCoeffRefsSrc2(DstCoeff))
   *                         ? kUsesInputColor_Property
   *                         : 0) |  // We assert later that SrcCoeff doesn't ref src2.
   *             ((kModulate_OutputType == PrimaryOut || kNone_OutputType == PrimaryOut) &&
   *                                 kNone_OutputType == SecondaryOut &&
   *                                 skgpu::BlendAllowsCoverageAsAlpha(BlendEquation, SrcCoeff, DstCoeff)
   *                         ? kCanTweakAlphaForCoverage_Property
   *                         : 0));
   *     }
   * ```
   */
  private fun getProperties(
    primaryOut: OutputType,
    secondaryOut: OutputType,
    blendEquation: BlendEquation,
    srcCoeff: BlendCoeff,
    dstCoeff: BlendCoeff,
  ): Properties {
    TODO("Implement getProperties")
  }

  public enum class OutputType {
    kNone_OutputType,
    kCoverage_OutputType,
    kModulate_OutputType,
    kSAModulate_OutputType,
    kISAModulate_OutputType,
    kISCModulate_OutputType,
    kLast_OutputType,
  }

  public enum class Properties {
    kModifiesDst_Property,
    kUnaffectedByDst_Property,
    kUnaffectedByDstIfOpaque_Property,
    kUsesInputColor_Property,
    kCanTweakAlphaForCoverage_Property,
    kLast_Property,
  }
}
