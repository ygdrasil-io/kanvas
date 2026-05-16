package org.skia.modules

import SkSVGNumberType
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.gpu.Type

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeColorMatrix final : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeColorMatrix> Make() {
 *         return sk_sp<SkSVGFeColorMatrix>(new SkSVGFeColorMatrix());
 *     }
 *
 *     SVG_ATTR(Type, SkSVGFeColorMatrixType, SkSVGFeColorMatrixType(SkSVGFeColorMatrixType::kMatrix))
 *     SVG_ATTR(Values, SkSVGFeColorMatrixValues, SkSVGFeColorMatrixValues())
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGFeColorMatrix() : INHERITED(SkSVGTag::kFeColorMatrix) {}
 *
 *     SkColorMatrix makeMatrixForType() const;
 *
 *     static SkColorMatrix MakeSaturate(SkSVGNumberType s);
 *
 *     static SkColorMatrix MakeHueRotate(SkSVGNumberType degrees);
 *
 *     static SkColorMatrix MakeLuminanceToAlpha();
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public class SkSVGFeColorMatrix public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Type, SkSVGFeColorMatrixType, SkSVGFeColorMatrixType(SkSVGFeColorMatrixType::kMatrix))
   * ```
   */
  public fun svgATTR(
    param0: Type,
    param1: SkSVGFeColorMatrixType,
    param2: (SkSVGFeColorMatrixType.KMatrix) -> SkSVGFeColorMatrixType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
   * ```
   */
  protected override fun getInputs(): Int {
    TODO("Implement getInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeColorMatrix::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setType(
   *                    SkSVGAttributeParser::parse<SkSVGFeColorMatrixType>("type", name, value)) ||
   *            this->setValues(
   *                    SkSVGAttributeParser::parse<SkSVGFeColorMatrixValues>("values", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorMatrix SkSVGFeColorMatrix::makeMatrixForType() const {
   *     if (fValues.empty() && fType != SkSVGFeColorMatrixType::kLuminanceToAlpha) {
   *         return SkColorMatrix();
   *     }
   *
   *     switch (fType) {
   *         case SkSVGFeColorMatrixType::kMatrix: {
   *             if (fValues.size() < 20) {
   *                 return SkColorMatrix();
   *             }
   *             SkColorMatrix m;
   *             m.setRowMajor(fValues.data());
   *             return m;
   *         }
   *         case SkSVGFeColorMatrixType::kSaturate:
   *             return MakeSaturate(!fValues.empty() ? fValues[0] : 1);
   *         case SkSVGFeColorMatrixType::kHueRotate:
   *             return MakeHueRotate(!fValues.empty() ? fValues[0] : 0);
   *         case SkSVGFeColorMatrixType::kLuminanceToAlpha:
   *             return MakeLuminanceToAlpha();
   *     }
   *
   *     SkUNREACHABLE;
   * }
   * ```
   */
  private fun makeMatrixForType(): Int {
    TODO("Implement makeMatrixForType")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeColorMatrix::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                            const SkSVGFilterContext& fctx) const {
   *     return SkImageFilters::ColorFilter(
   *             SkColorFilters::Matrix(makeMatrixForType()),
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx)),
   *             this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  public fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): SkSp<SkImageFilter> {
    TODO("Implement onMakeImageFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeColorMatrix> Make() {
     *         return sk_sp<SkSVGFeColorMatrix>(new SkSVGFeColorMatrix());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColorMatrix SkSVGFeColorMatrix::MakeSaturate(SkSVGNumberType s) {
     *     SkColorMatrix m;
     *     m.setSaturation(s);
     *     return m;
     * }
     * ```
     */
    private fun makeSaturate(s: SkSVGNumberType): Int {
      TODO("Implement makeSaturate")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColorMatrix SkSVGFeColorMatrix::MakeHueRotate(SkSVGNumberType degrees) {
     *     const SkScalar theta = SkDegreesToRadians(degrees);
     *     const SkSVGNumberType c = SkScalarCos(theta);
     *     const SkSVGNumberType s = SkScalarSin(theta);
     *     return SkColorMatrix(
     *         0.213f + c* 0.787f + s*-0.213f,
     *         0.715f + c*-0.715f + s*-0.715f,
     *         0.072f + c*-0.072f + s* 0.928f,
     *         0,
     *         0,
     *
     *         0.213f + c*-0.213f + s* 0.143f,
     *         0.715f + c* 0.285f + s* 0.140f,
     *         0.072f + c*-0.072f + s*-0.283f,
     *         0,
     *         0,
     *
     *         0.213f + c*-0.213f + s*-0.787f,
     *         0.715f + c*-0.715f + s* 0.715f,
     *         0.072f + c* 0.928f + s* 0.072f,
     *         0,
     *         0,
     *
     *         0,0,0,1,0
     *     );
     * }
     * ```
     */
    private fun makeHueRotate(degrees: SkSVGNumberType): Int {
      TODO("Implement makeHueRotate")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColorMatrix SkSVGFeColorMatrix::MakeLuminanceToAlpha() {
     *     return SkColorMatrix(
     *         0, 0, 0, 0, 0,
     *         0, 0, 0, 0, 0,
     *         0, 0, 0, 0, 0,
     *         SK_LUM_COEFF_R, SK_LUM_COEFF_G, SK_LUM_COEFF_B, 0, 0
     *     );
     * }
     * ```
     */
    private fun makeLuminanceToAlpha(): Int {
      TODO("Implement makeLuminanceToAlpha")
    }
  }
}
