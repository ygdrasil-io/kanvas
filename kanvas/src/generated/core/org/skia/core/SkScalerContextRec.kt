package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.UShort
import org.skia.foundation.SkColor
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SkScalerContextRec {
 *     SkTypefaceID fTypefaceID;
 *     SkScalar     fTextSize, fPreScaleX, fPreSkewX;
 *     SkScalar     fPost2x2[2][2];
 *     SkScalar     fFrameWidth, fMiterLimit;
 *
 *     // This will be set if to the paint's foreground color if
 *     // kNeedsForegroundColor is set, which will usually be the case for COLRv0 and
 *     // COLRv1 fonts.
 *     uint32_t fForegroundColor{SK_ColorBLACK};
 *
 * private:
 *     //These describe the parameters to create (uniquely identify) the pre-blend.
 *     uint32_t      fLumBits;
 *     uint8_t       fDeviceGamma; //2.6, (0.0, 4.0) gamma, 0.0 for sRGB
 *     const uint8_t fReservedAlign2{0};
 *     uint8_t       fContrast;    //0.8+1, [0.0, 1.0] artificial contrast
 *     const uint8_t fReservedAlign{0};
 *
 *     static constexpr SkScalar ExternalGammaFromInternal(uint8_t g) {
 *         return SkIntToScalar(g) / (1 << 6);
 *     }
 *     static constexpr uint8_t InternalGammaFromExternal(SkScalar g) {
 *         // C++23 use constexpr std::floor
 *         return static_cast<uint8_t>(g * (1 << 6));
 *     }
 *     static constexpr SkScalar ExternalContrastFromInternal(uint8_t c) {
 *         return SkIntToScalar(c) / ((1 << 8) - 1);
 *     }
 *     static constexpr uint8_t InternalContrastFromExternal(SkScalar c) {
 *         // C++23 use constexpr std::round
 *         return static_cast<uint8_t>((c * ((1 << 8) - 1)) + 0.5f);
 *     }
 * public:
 *     void setDeviceGamma(SkScalar g) {
 *         sk_ignore_unused_variable(fReservedAlign2);
 *         SkASSERT(SkSurfaceProps::kMinGammaInclusive <= g &&
 *                  g < SkIntToScalar(SkSurfaceProps::kMaxGammaExclusive));
 *         fDeviceGamma = InternalGammaFromExternal(g);
 *     }
 *
 *     void setContrast(SkScalar c) {
 *         sk_ignore_unused_variable(fReservedAlign);
 *         SkASSERT(SkSurfaceProps::kMinContrastInclusive <= c &&
 *                  c <= SkIntToScalar(SkSurfaceProps::kMaxContrastInclusive));
 *         fContrast = InternalContrastFromExternal(c);
 *     }
 *
 *     static const SkMaskGamma& CachedMaskGamma(uint8_t contrast, uint8_t gamma);
 *     const SkMaskGamma& cachedMaskGamma() const {
 *         return CachedMaskGamma(fContrast, fDeviceGamma);
 *     }
 *
 *     /**
 *      *  Causes the luminance color to be ignored, and the paint and device
 *      *  gamma to be effectively 1.0
 *      */
 *     void ignoreGamma() {
 *         setLuminanceColor(SK_ColorTRANSPARENT);
 *         setDeviceGamma(SK_Scalar1);
 *     }
 *
 *     /**
 *      *  Causes the luminance color and contrast to be ignored, and the
 *      *  paint and device gamma to be effectively 1.0.
 *      */
 *     void ignorePreBlend() {
 *         ignoreGamma();
 *         setContrast(0);
 *     }
 *
 *     /** If the kEmbolden_Flag is set, drop it and use stroking instead. */
 *     void useStrokeForFakeBold();
 *
 *     SkMask::Format fMaskFormat;
 *
 * private:
 *     uint8_t        fStrokeJoin : 4;
 *     uint8_t        fStrokeCap  : 4;
 *
 * public:
 *     uint16_t    fFlags;
 *
 *     // Warning: when adding members note that the size of this structure
 *     // must be a multiple of 4. SkDescriptor requires that its arguments be
 *     // multiples of four and this structure is put in an SkDescriptor in
 *     // SkPaint::MakeRecAndEffects.
 *
 *     SkString dump() const {
 *         SkString msg;
 *         msg.appendf("    Rec\n");
 *         msg.appendf("      textsize %a prescale %a preskew %a post [%a %a %a %a]\n",
 *                    fTextSize, fPreScaleX, fPreSkewX, fPost2x2[0][0],
 *                    fPost2x2[0][1], fPost2x2[1][0], fPost2x2[1][1]);
 *         msg.appendf("      frame %g miter %g format %d join %d cap %d flags %#hx\n",
 *                    fFrameWidth, fMiterLimit, fMaskFormat, fStrokeJoin, fStrokeCap, fFlags);
 *         msg.appendf("      lum bits %x, device gamma %d, contrast %d\n", fLumBits,
 *                     fDeviceGamma, fContrast);
 *         msg.appendf("      foreground color %x\n", fForegroundColor);
 *         return msg;
 *     }
 *
 *     SkMatrix getMatrixFrom2x2() const;
 *     SkMatrix getLocalMatrix() const;
 *     SkMatrix getSingleMatrix() const;
 *
 *     /** The kind of scale which will be applied by the underlying port (pre-matrix). */
 *     enum class PreMatrixScale {
 *         kFull,  // The underlying port can apply both x and y scale.
 *         kVertical,  // The underlying port can only apply a y scale.
 *         kVerticalInteger  // The underlying port can only apply an integer y scale.
 *     };
 *     /**
 *      *  Compute useful matrices for use with sizing in underlying libraries.
 *      *
 *      *  There are two kinds of text size, a 'requested/logical size' which is like asking for size
 *      *  '12' and a 'real' size which is the size after the matrix is applied. The matrices produced
 *      *  by this method are based on the 'real' size. This method effectively finds the total device
 *      *  matrix and decomposes it in various ways.
 *      *
 *      *  The most useful decomposition is into 'scale' and 'remaining'. The 'scale' is applied first
 *      *  and then the 'remaining' to fully apply the total matrix. This decomposition is useful when
 *      *  the text size ('scale') may have meaning apart from the total matrix. This is true when
 *      *  hinting, and sometimes true for other properties as well.
 *      *
 *      *  The second (optional) decomposition is of 'remaining' into a non-rotational part
 *      *  'remainingWithoutRotation' and a rotational part 'remainingRotation'. The 'scale' is applied
 *      *  first, then 'remainingWithoutRotation', then 'remainingRotation' to fully apply the total
 *      *  matrix. This decomposition is helpful when only horizontal metrics can be trusted, so the
 *      *  'scale' and 'remainingWithoutRotation' will be handled by the underlying library, but
 *      *  the final rotation 'remainingRotation' will be handled manually.
 *      *
 *      *  The 'total' matrix is also (optionally) available. This is useful in cases where the
 *      *  underlying library will not be used, often when working directly with font data.
 *      *
 *      *  The parameters 'scale' and 'remaining' are required, the other pointers may be nullptr.
 *      *
 *      *  @param preMatrixScale the kind of scale to extract from the total matrix.
 *      *  @param scale the scale extracted from the total matrix (both values positive).
 *      *  @param remaining apply after scale to apply the total matrix.
 *      *  @param remainingWithoutRotation apply after scale to apply the total matrix sans rotation.
 *      *  @param remainingRotation apply after remainingWithoutRotation to apply the total matrix.
 *      *  @param total the total matrix.
 *      *  @return false if the matrix was singular. The output will be valid but not invertible.
 *      */
 *     bool computeMatrices(PreMatrixScale preMatrixScale,
 *                          SkVector* scale, SkMatrix* remaining,
 *                          SkMatrix* remainingWithoutRotation = nullptr,
 *                          SkMatrix* remainingRotation = nullptr,
 *                          SkMatrix* total = nullptr) const;
 *
 *     SkAxisAlignment computeAxisAlignmentForHText() const;
 *
 *     inline SkFontHinting getHinting() const;
 *     inline void setHinting(SkFontHinting);
 *
 *     SkMask::Format getFormat() const {
 *         return fMaskFormat;
 *     }
 *
 *     SkColor getLuminanceColor() const {
 *         return fLumBits;
 *     }
 *
 *     // setLuminanceColor forces the alpha to be 0xFF because the blitter that draws the glyph
 *     // will apply the alpha from the paint. Don't apply the alpha twice.
 *     void setLuminanceColor(SkColor c);
 *
 * private:
 *     // TODO: remove
 *     friend class SkScalerContext;
 * }
 * ```
 */
public data class SkScalerContextRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID fTypefaceID
   * ```
   */
  public var fTypefaceID: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fTextSize
   * ```
   */
  public var fTextSize: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fTextSize, fPreScaleX
   * ```
   */
  public var fPreScaleX: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fTextSize, fPreScaleX, fPreSkewX
   * ```
   */
  public var fPreSkewX: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fPost2x2[2][2]
   * ```
   */
  public var fPost2x2: Array<SkScalar>,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fFrameWidth
   * ```
   */
  public var fFrameWidth: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar     fFrameWidth, fMiterLimit
   * ```
   */
  public var fMiterLimit: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fForegroundColor{SK_ColorBLACK}
   * ```
   */
  public var fForegroundColor: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t      fLumBits
   * ```
   */
  private var fLumBits: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint8_t       fDeviceGamma
   * ```
   */
  private var fDeviceGamma: UByte,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t fReservedAlign2{0}
   * ```
   */
  private val fReservedAlign2: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t       fContrast
   * ```
   */
  private var fContrast: UByte,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t fReservedAlign{0}
   * ```
   */
  private val fReservedAlign: UByte,
  /**
   * C++ original:
   * ```cpp
   * SkMask::Format fMaskFormat
   * ```
   */
  public var fMaskFormat: SkMask.Format,
  /**
   * C++ original:
   * ```cpp
   * uint8_t        fStrokeJoin : 4
   * ```
   */
  private var fStrokeJoin: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t        fStrokeCap  : 4
   * ```
   */
  private var fStrokeCap: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint16_t    fFlags
   * ```
   */
  public var fFlags: UShort,
) {
  /**
   * C++ original:
   * ```cpp
   * void setDeviceGamma(SkScalar g) {
   *         sk_ignore_unused_variable(fReservedAlign2);
   *         SkASSERT(SkSurfaceProps::kMinGammaInclusive <= g &&
   *                  g < SkIntToScalar(SkSurfaceProps::kMaxGammaExclusive));
   *         fDeviceGamma = InternalGammaFromExternal(g);
   *     }
   * ```
   */
  public fun setDeviceGamma(g: SkScalar) {
    TODO("Implement setDeviceGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * void setContrast(SkScalar c) {
   *         sk_ignore_unused_variable(fReservedAlign);
   *         SkASSERT(SkSurfaceProps::kMinContrastInclusive <= c &&
   *                  c <= SkIntToScalar(SkSurfaceProps::kMaxContrastInclusive));
   *         fContrast = InternalContrastFromExternal(c);
   *     }
   * ```
   */
  public fun setContrast(c: SkScalar) {
    TODO("Implement setContrast")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMaskGamma& cachedMaskGamma() const {
   *         return CachedMaskGamma(fContrast, fDeviceGamma);
   *     }
   * ```
   */
  public fun cachedMaskGamma(): SkMaskGamma {
    TODO("Implement cachedMaskGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * void ignoreGamma() {
   *         setLuminanceColor(SK_ColorTRANSPARENT);
   *         setDeviceGamma(SK_Scalar1);
   *     }
   * ```
   */
  public fun ignoreGamma() {
    TODO("Implement ignoreGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * void ignorePreBlend() {
   *         ignoreGamma();
   *         setContrast(0);
   *     }
   * ```
   */
  public fun ignorePreBlend() {
    TODO("Implement ignorePreBlend")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContextRec::useStrokeForFakeBold() {
   *     if (!SkToBool(fFlags & SkScalerContext::kEmbolden_Flag)) {
   *         return;
   *     }
   *     fFlags &= ~SkScalerContext::kEmbolden_Flag;
   *
   *     SkScalar fakeBoldScale = SkFloatInterpFunc(fTextSize,
   *                                                kStdFakeBoldInterpKeys,
   *                                                kStdFakeBoldInterpValues,
   *                                                kStdFakeBoldInterpLength);
   *     SkScalar extra = fTextSize * fakeBoldScale;
   *
   *     if (fFrameWidth >= 0) {
   *         fFrameWidth += extra;
   *     } else {
   *         fFlags |= SkScalerContext::kFrameAndFill_Flag;
   *         fFrameWidth = extra;
   *         SkPaint paint;
   *         fMiterLimit = paint.getStrokeMiter();
   *         fStrokeJoin = SkToU8(paint.getStrokeJoin());
   *         fStrokeCap = SkToU8(paint.getStrokeCap());
   *     }
   * }
   * ```
   */
  public fun useStrokeForFakeBold() {
    TODO("Implement useStrokeForFakeBold")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString dump() const {
   *         SkString msg;
   *         msg.appendf("    Rec\n");
   *         msg.appendf("      textsize %a prescale %a preskew %a post [%a %a %a %a]\n",
   *                    fTextSize, fPreScaleX, fPreSkewX, fPost2x2[0][0],
   *                    fPost2x2[0][1], fPost2x2[1][0], fPost2x2[1][1]);
   *         msg.appendf("      frame %g miter %g format %d join %d cap %d flags %#hx\n",
   *                    fFrameWidth, fMiterLimit, fMaskFormat, fStrokeJoin, fStrokeCap, fFlags);
   *         msg.appendf("      lum bits %x, device gamma %d, contrast %d\n", fLumBits,
   *                     fDeviceGamma, fContrast);
   *         msg.appendf("      foreground color %x\n", fForegroundColor);
   *         return msg;
   *     }
   * ```
   */
  public fun dump(): String {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix SkScalerContextRec::getMatrixFrom2x2() const {
   *     return SkMatrix::MakeAll(fPost2x2[0][0], fPost2x2[0][1], 0,
   *                              fPost2x2[1][0], fPost2x2[1][1], 0,
   *                              0,              0,              1);
   * }
   * ```
   */
  public fun getMatrixFrom2x2(): SkMatrix {
    TODO("Implement getMatrixFrom2x2")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix SkScalerContextRec::getLocalMatrix() const {
   *     return SkFontPriv::MakeTextMatrix(fTextSize, fPreScaleX, fPreSkewX);
   * }
   * ```
   */
  public fun getLocalMatrix(): SkMatrix {
    TODO("Implement getLocalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix SkScalerContextRec::getSingleMatrix() const {
   *     return this->getLocalMatrix().postConcat(this->getMatrixFrom2x2());
   * }
   * ```
   */
  public fun getSingleMatrix(): SkMatrix {
    TODO("Implement getSingleMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkScalerContextRec::computeMatrices(PreMatrixScale preMatrixScale, SkVector* s, SkMatrix* sA,
   *                                          SkMatrix* GsA, SkMatrix* G_inv, SkMatrix* A_out) const
   * {
   *     // A is the 'total' matrix.
   *     const SkMatrix A = this->getSingleMatrix();
   *
   *     // The caller may find the 'total' matrix useful when dealing directly with EM sizes.
   *     if (A_out) {
   *         *A_out = A;
   *     }
   *
   *     // GA is the matrix A with rotation removed.
   *     SkMatrix GA;
   *     bool skewedOrFlipped = A.getSkewX() || A.getSkewY() || A.getScaleX() < 0 || A.getScaleY() < 0;
   *     if (skewedOrFlipped) {
   *         // QR by Givens rotations. G is Q^T and GA is R. G is rotational (no reflections).
   *         // h is where A maps the horizontal baseline.
   *         SkPoint h = A.mapPoint({SK_Scalar1, 0});
   *
   *         // G is the Givens Matrix for A (rotational matrix where GA[0][1] == 0).
   *         SkMatrix G;
   *         SkComputeGivensRotation(h, &G);
   *
   *         GA = G;
   *         GA.preConcat(A);
   *
   *         // The 'remainingRotation' is G inverse, which is fairly simple since G is 2x2 rotational.
   *         if (G_inv) {
   *             G_inv->setAll(
   *                 G.get(SkMatrix::kMScaleX), -G.get(SkMatrix::kMSkewX), G.get(SkMatrix::kMTransX),
   *                 -G.get(SkMatrix::kMSkewY), G.get(SkMatrix::kMScaleY), G.get(SkMatrix::kMTransY),
   *                 G.get(SkMatrix::kMPersp0), G.get(SkMatrix::kMPersp1), G.get(SkMatrix::kMPersp2));
   *         }
   *     } else {
   *         GA = A;
   *         if (G_inv) {
   *             G_inv->reset();
   *         }
   *     }
   *
   *     // If the 'total' matrix is singular, set the 'scale' to something finite and zero the matrices.
   *     // All underlying ports have issues with zero text size, so use the matricies to zero.
   *     // If one of the scale factors is less than 1/256 then an EM filling square will
   *     // never affect any pixels.
   *     // If there are any nonfinite numbers in the matrix, bail out and set the matrices to zero.
   *     if (SkScalarAbs(GA.get(SkMatrix::kMScaleX)) <= SK_ScalarNearlyZero ||
   *         SkScalarAbs(GA.get(SkMatrix::kMScaleY)) <= SK_ScalarNearlyZero ||
   *         !GA.isFinite())
   *     {
   *         s->fX = SK_Scalar1;
   *         s->fY = SK_Scalar1;
   *         sA->setScale(0, 0);
   *         if (GsA) {
   *             GsA->setScale(0, 0);
   *         }
   *         if (G_inv) {
   *             G_inv->reset();
   *         }
   *         return false;
   *     }
   *
   *     // At this point, given GA, create s.
   *     switch (preMatrixScale) {
   *         case PreMatrixScale::kFull:
   *             s->fX = SkScalarAbs(GA.get(SkMatrix::kMScaleX));
   *             s->fY = SkScalarAbs(GA.get(SkMatrix::kMScaleY));
   *             break;
   *         case PreMatrixScale::kVertical: {
   *             SkScalar yScale = SkScalarAbs(GA.get(SkMatrix::kMScaleY));
   *             s->fX = yScale;
   *             s->fY = yScale;
   *             break;
   *         }
   *         case PreMatrixScale::kVerticalInteger: {
   *             SkScalar realYScale = SkScalarAbs(GA.get(SkMatrix::kMScaleY));
   *             SkScalar intYScale = SkScalarRoundToScalar(realYScale);
   *             if (intYScale == 0) {
   *                 intYScale = SK_Scalar1;
   *             }
   *             s->fX = intYScale;
   *             s->fY = intYScale;
   *             break;
   *         }
   *     }
   *
   *     // The 'remaining' matrix sA is the total matrix A without the scale.
   *     if (!skewedOrFlipped && (
   *             (PreMatrixScale::kFull == preMatrixScale) ||
   *             (PreMatrixScale::kVertical == preMatrixScale && A.getScaleX() == A.getScaleY())))
   *     {
   *         // If GA == A and kFull, sA is identity.
   *         // If GA == A and kVertical and A.scaleX == A.scaleY, sA is identity.
   *         sA->reset();
   *     } else if (!skewedOrFlipped && PreMatrixScale::kVertical == preMatrixScale) {
   *         // If GA == A and kVertical, sA.scaleY is SK_Scalar1.
   *         sA->reset();
   *         sA->setScaleX(A.getScaleX() / s->fY);
   *     } else {
   *         // TODO: like kVertical, kVerticalInteger with int scales.
   *         *sA = A;
   *         sA->preScale(SkScalarInvert(s->fX), SkScalarInvert(s->fY));
   *     }
   *
   *     // The 'remainingWithoutRotation' matrix GsA is the non-rotational part of A without the scale.
   *     if (GsA) {
   *         *GsA = GA;
   *          // G is rotational so reorders with the scale.
   *         GsA->preScale(SkScalarInvert(s->fX), SkScalarInvert(s->fY));
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun computeMatrices(
    preMatrixScale: PreMatrixScale,
    scale: SkVector?,
    remaining: SkMatrix?,
    remainingWithoutRotation: SkMatrix? = null,
    remainingRotation: SkMatrix? = null,
    total: SkMatrix? = null,
  ): Boolean {
    TODO("Implement computeMatrices")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAxisAlignment SkScalerContextRec::computeAxisAlignmentForHText() const {
   *     // Why fPost2x2 can be used here.
   *     // getSingleMatrix multiplies in getLocalMatrix, which consists of
   *     // * fTextSize (a scale, which has no effect)
   *     // * fPreScaleX (a scale in x, which has no effect)
   *     // * fPreSkewX (has no effect, but would on vertical text alignment).
   *     // In other words, making the text bigger, stretching it along the
   *     // horizontal axis, or fake italicizing it does not move the baseline.
   *     if (!SkToBool(fFlags & SkScalerContext::kBaselineSnap_Flag)) {
   *         return SkAxisAlignment::kNone;
   *     }
   *
   *     if (0 == fPost2x2[1][0]) {
   *         // The x axis is mapped onto the x axis.
   *         return SkAxisAlignment::kX;
   *     }
   *     if (0 == fPost2x2[0][0]) {
   *         // The x axis is mapped onto the y axis.
   *         return SkAxisAlignment::kY;
   *     }
   *     return SkAxisAlignment::kNone;
   * }
   * ```
   */
  public fun computeAxisAlignmentForHText(): SkAxisAlignment {
    TODO("Implement computeAxisAlignmentForHText")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontHinting SkScalerContextRec::getHinting() const {
   *     unsigned hint = (fFlags & SkScalerContext::kHinting_Mask) >>
   *                                             SkScalerContext::kHinting_Shift;
   *     return static_cast<SkFontHinting>(hint);
   * }
   * ```
   */
  public fun getHinting(): SkFontHinting {
    TODO("Implement getHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContextRec::setHinting(SkFontHinting hinting) {
   *     fFlags = (fFlags & ~SkScalerContext::kHinting_Mask) |
   *                         (static_cast<unsigned>(hinting) << SkScalerContext::kHinting_Shift);
   * }
   * ```
   */
  public fun setHinting(hinting: SkFontHinting) {
    TODO("Implement setHinting")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format getFormat() const {
   *         return fMaskFormat;
   *     }
   * ```
   */
  public fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor getLuminanceColor() const {
   *         return fLumBits;
   *     }
   * ```
   */
  public fun getLuminanceColor(): SkColor {
    TODO("Implement getLuminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContextRec::setLuminanceColor(SkColor c) {
   *     fLumBits = SkMaskGamma::CanonicalColor(
   *             SkColorSetRGB(SkColorGetR(c), SkColorGetG(c), SkColorGetB(c)));
   * }
   * ```
   */
  public fun setLuminanceColor(c: SkColor) {
    TODO("Implement setLuminanceColor")
  }

  public enum class PreMatrixScale {
    kFull,
    kVertical,
    kVerticalInteger,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkScalar ExternalGammaFromInternal(uint8_t g) {
     *         return SkIntToScalar(g) / (1 << 6);
     *     }
     * ```
     */
    private fun externalGammaFromInternal(g: UByte): SkScalar {
      TODO("Implement externalGammaFromInternal")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint8_t InternalGammaFromExternal(SkScalar g) {
     *         // C++23 use constexpr std::floor
     *         return static_cast<uint8_t>(g * (1 << 6));
     *     }
     * ```
     */
    private fun internalGammaFromExternal(g: SkScalar): UByte {
      TODO("Implement internalGammaFromExternal")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkScalar ExternalContrastFromInternal(uint8_t c) {
     *         return SkIntToScalar(c) / ((1 << 8) - 1);
     *     }
     * ```
     */
    private fun externalContrastFromInternal(c: UByte): SkScalar {
      TODO("Implement externalContrastFromInternal")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint8_t InternalContrastFromExternal(SkScalar c) {
     *         // C++23 use constexpr std::round
     *         return static_cast<uint8_t>((c * ((1 << 8) - 1)) + 0.5f);
     *     }
     * ```
     */
    private fun internalContrastFromExternal(c: SkScalar): UByte {
      TODO("Implement internalContrastFromExternal")
    }

    /**
     * C++ original:
     * ```cpp
     * const SkMaskGamma& SkScalerContextRec::CachedMaskGamma(uint8_t contrast, uint8_t gamma) {
     *     mask_gamma_cache_mutex().assertHeld();
     *
     *     constexpr uint8_t contrast0 = InternalContrastFromExternal(0);
     *     constexpr uint8_t gamma1 = InternalGammaFromExternal(1);
     *     if (contrast0 == contrast && gamma1 == gamma) {
     *         return linear_gamma();
     *     }
     *     constexpr uint8_t defaultContrast = InternalContrastFromExternal(SK_GAMMA_CONTRAST);
     *     constexpr uint8_t defaultGamma = InternalGammaFromExternal(SK_GAMMA_EXPONENT);
     *     if (defaultContrast == contrast && defaultGamma == gamma) {
     *         if (!gDefaultMaskGamma) {
     *             gDefaultMaskGamma = new SkMaskGamma(ExternalContrastFromInternal(contrast),
     *                                                 ExternalGammaFromInternal(gamma));
     *         }
     *         return *gDefaultMaskGamma;
     *     }
     *     if (!gMaskGamma || gContrast != contrast || gGamma != gamma) {
     *         SkSafeUnref(gMaskGamma);
     *         gMaskGamma = new SkMaskGamma(ExternalContrastFromInternal(contrast),
     *                                      ExternalGammaFromInternal(gamma));
     *         gContrast = contrast;
     *         gGamma = gamma;
     *     }
     *     return *gMaskGamma;
     * }
     * ```
     */
    public fun cachedMaskGamma(contrast: UByte, gamma: UByte): SkMaskGamma {
      TODO("Implement cachedMaskGamma")
    }
  }
}
