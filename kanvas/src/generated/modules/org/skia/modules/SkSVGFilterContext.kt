package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkRect
import undefined.SkSVGStringType

/**
 * C++ original:
 * ```cpp
 * class SkSVGFilterContext {
 * public:
 *     SkSVGFilterContext(const SkRect& filterEffectsRegion,
 *                        const SkSVGObjectBoundingBoxUnits& primitiveUnits)
 *             : fFilterEffectsRegion(filterEffectsRegion)
 *             , fPrimitiveUnits(primitiveUnits)
 *             , fPreviousResult({nullptr, filterEffectsRegion, SkSVGColorspace::kSRGB}) {}
 *
 *     const SkRect& filterEffectsRegion() const { return fFilterEffectsRegion; }
 *
 *     const SkRect& filterPrimitiveSubregion(const SkSVGFeInputType&) const;
 *
 *     const SkSVGObjectBoundingBoxUnits& primitiveUnits() const { return fPrimitiveUnits; }
 *
 *     void registerResult(const SkSVGStringType&, const sk_sp<SkImageFilter>&, const SkRect&, SkSVGColorspace);
 *
 *     void setPreviousResult(const sk_sp<SkImageFilter>&, const SkRect&, SkSVGColorspace);
 *
 *     bool previousResultIsSourceGraphic() const;
 *
 *     SkSVGColorspace resolveInputColorspace(const SkSVGRenderContext&,
 *                                            const SkSVGFeInputType&) const;
 *
 *     sk_sp<SkImageFilter> resolveInput(const SkSVGRenderContext&, const SkSVGFeInputType&) const;
 *
 *     sk_sp<SkImageFilter> resolveInput(const SkSVGRenderContext&, const SkSVGFeInputType&, SkSVGColorspace) const;
 *
 * private:
 *     struct Result {
 *         sk_sp<SkImageFilter> fImageFilter;
 *         SkRect fFilterSubregion;
 *         SkSVGColorspace fColorspace;
 *     };
 *
 *     const Result* findResultById(const SkSVGStringType&) const;
 *
 *     std::tuple<sk_sp<SkImageFilter>, SkSVGColorspace> getInput(const SkSVGRenderContext&,
 *                                                                const SkSVGFeInputType&) const;
 *
 *     SkRect fFilterEffectsRegion;
 *
 *     SkSVGObjectBoundingBoxUnits fPrimitiveUnits;
 *
 *     skia_private::THashMap<SkSVGStringType, Result> fResults;
 *
 *     Result fPreviousResult;
 * }
 * ```
 */
public data class SkSVGFilterContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRect fFilterEffectsRegion
   * ```
   */
  private var fFilterEffectsRegion: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGObjectBoundingBoxUnits fPrimitiveUnits
   * ```
   */
  private var fPrimitiveUnits: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<SkSVGStringType, Result> fResults
   * ```
   */
  private var fResults: Int,
  /**
   * C++ original:
   * ```cpp
   * Result fPreviousResult
   * ```
   */
  private var fPreviousResult: Result,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkRect& filterEffectsRegion() const { return fFilterEffectsRegion; }
   * ```
   */
  public fun filterEffectsRegion(): Int {
    TODO("Implement filterEffectsRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& SkSVGFilterContext::filterPrimitiveSubregion(const SkSVGFeInputType& input) const {
   *     const Result* res = nullptr;
   *     if (input.type() == SkSVGFeInputType::Type::kFilterPrimitiveReference) {
   *         res = fResults.find(input.id());
   *     } else if (input.type() == SkSVGFeInputType::Type::kUnspecified) {
   *         res = &fPreviousResult;
   *     }
   *     return res ? res->fFilterSubregion : fFilterEffectsRegion;
   * }
   * ```
   */
  public fun filterPrimitiveSubregion(input: SkSVGFeInputType): Int {
    TODO("Implement filterPrimitiveSubregion")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSVGObjectBoundingBoxUnits& primitiveUnits() const { return fPrimitiveUnits; }
   * ```
   */
  public fun primitiveUnits(): Int {
    TODO("Implement primitiveUnits")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGFilterContext::registerResult(const SkSVGStringType& id,
   *                                         const sk_sp<SkImageFilter>& result,
   *                                         const SkRect& subregion,
   *                                         SkSVGColorspace resultColorspace) {
   *     SkASSERT(!id.isEmpty());
   *     fResults[id] = {result, subregion, resultColorspace};
   * }
   * ```
   */
  public fun registerResult(
    id: SkSVGStringType,
    result: SkSp<SkImageFilter>,
    subregion: SkRect,
    resultColorspace: SkSVGColorspace,
  ) {
    TODO("Implement registerResult")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGFilterContext::setPreviousResult(const sk_sp<SkImageFilter>& result,
   *                                            const SkRect& subregion,
   *                                            SkSVGColorspace resultColorspace) {
   *     fPreviousResult = {result, subregion, resultColorspace};
   * }
   * ```
   */
  public fun setPreviousResult(
    result: SkSp<SkImageFilter>,
    subregion: SkRect,
    resultColorspace: SkSVGColorspace,
  ) {
    TODO("Implement setPreviousResult")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFilterContext::previousResultIsSourceGraphic() const {
   *     return fPreviousResult.fImageFilter == nullptr;
   * }
   * ```
   */
  public fun previousResultIsSourceGraphic(): Boolean {
    TODO("Implement previousResultIsSourceGraphic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGColorspace SkSVGFilterContext::resolveInputColorspace(
   *         const SkSVGRenderContext& ctx, const SkSVGFeInputType& inputType) const {
   *     return std::get<1>(this->getInput(ctx, inputType));
   * }
   * ```
   */
  public fun resolveInputColorspace(ctx: SkSVGRenderContext, inputType: SkSVGFeInputType): Int {
    TODO("Implement resolveInputColorspace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFilterContext::resolveInput(const SkSVGRenderContext& ctx,
   *                                                       const SkSVGFeInputType& inputType) const {
   *     return std::get<0>(this->getInput(ctx, inputType));
   * }
   * ```
   */
  public fun resolveInput(ctx: SkSVGRenderContext, inputType: SkSVGFeInputType): Int {
    TODO("Implement resolveInput")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFilterContext::resolveInput(const SkSVGRenderContext& ctx,
   *                                                       const SkSVGFeInputType& inputType,
   *                                                       SkSVGColorspace colorspace) const {
   *     auto [result, inputCS] = this->getInput(ctx, inputType);
   *     return ConvertFilterColorspace(std::move(result), inputCS, colorspace);
   * }
   * ```
   */
  public fun resolveInput(
    ctx: SkSVGRenderContext,
    inputType: SkSVGFeInputType,
    colorspace: SkSVGColorspace,
  ): Int {
    TODO("Implement resolveInput")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSVGFilterContext::Result* SkSVGFilterContext::findResultById(
   *         const SkSVGStringType& id) const {
   *     return fResults.find(id);
   * }
   * ```
   */
  private fun findResultById(id: SkSVGStringType): Result {
    TODO("Implement findResultById")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<sk_sp<SkImageFilter>, SkSVGColorspace> SkSVGFilterContext::getInput(
   *         const SkSVGRenderContext& ctx, const SkSVGFeInputType& inputType) const {
   *     SkSVGColorspace inputCS = SkSVGColorspace::kSRGB;
   *     sk_sp<SkImageFilter> result;
   *     switch (inputType.type()) {
   *         case SkSVGFeInputType::Type::kSourceAlpha: {
   *             SkColorMatrix m;
   *             m.setScale(0, 0, 0, 1.0f);
   *             result = SkImageFilters::ColorFilter(SkColorFilters::Matrix(m), nullptr);
   *             break;
   *         }
   *         case SkSVGFeInputType::Type::kSourceGraphic:
   *             // Do nothing.
   *             break;
   *         case SkSVGFeInputType::Type::kFillPaint: {
   *             const auto& fillPaint = ctx.fillPaint();
   *             if (fillPaint.has_value()) {
   *                 auto dither = fillPaint->isDither() ? SkImageFilters::Dither::kYes
   *                                                     : SkImageFilters::Dither::kNo;
   *                 result = SkImageFilters::Shader(paint_as_shader(*fillPaint), dither);
   *             }
   *             break;
   *         }
   *         case SkSVGFeInputType::Type::kStrokePaint: {
   *             // The paint filter doesn't apply fill/stroke styling, but use the paint settings
   *             // defined for strokes.
   *             const auto& strokePaint = ctx.strokePaint();
   *             if (strokePaint.has_value()) {
   *                 auto dither = strokePaint->isDither() ? SkImageFilters::Dither::kYes
   *                                                       : SkImageFilters::Dither::kNo;
   *                 result = SkImageFilters::Shader(paint_as_shader(*strokePaint), dither);
   *             }
   *             break;
   *         }
   *         case SkSVGFeInputType::Type::kFilterPrimitiveReference: {
   *             const Result* res = findResultById(inputType.id());
   *             if (res) {
   *                 result = res->fImageFilter;
   *                 inputCS = res->fColorspace;
   *             }
   *             break;
   *         }
   *         case SkSVGFeInputType::Type::kUnspecified: {
   *             result = fPreviousResult.fImageFilter;
   *             inputCS = fPreviousResult.fColorspace;
   *             break;
   *         }
   *         default:
   *             SkDEBUGF("unhandled filter input type %d\n", (int)inputType.type());
   *             break;
   *     }
   *
   *     return {result, inputCS};
   * }
   * ```
   */
  private fun getInput(ctx: SkSVGRenderContext, inputType: SkSVGFeInputType): Int {
    TODO("Implement getInput")
  }

  public open class Result public constructor(
    public var fImageFilter: Int,
    public var fFilterSubregion: Int,
    public var fColorspace: Int,
  )
}
