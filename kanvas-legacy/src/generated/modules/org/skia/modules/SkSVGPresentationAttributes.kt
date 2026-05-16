package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkSVGPresentationAttributes {
 *     static SkSVGPresentationAttributes MakeInitial();
 *
 *     // TODO: SkSVGProperty adds an extra ptr per attribute; refactor to reduce overhead.
 *
 *     SkSVGProperty<SkSVGPaint     , true> fFill;
 *     SkSVGProperty<SkSVGNumberType, true> fFillOpacity;
 *     SkSVGProperty<SkSVGFillRule  , true> fFillRule;
 *     SkSVGProperty<SkSVGFillRule  , true> fClipRule;
 *
 *     SkSVGProperty<SkSVGPaint     , true> fStroke;
 *     SkSVGProperty<SkSVGDashArray , true> fStrokeDashArray;
 *     SkSVGProperty<SkSVGLength    , true> fStrokeDashOffset;
 *     SkSVGProperty<SkSVGLineCap   , true> fStrokeLineCap;
 *     SkSVGProperty<SkSVGLineJoin  , true> fStrokeLineJoin;
 *     SkSVGProperty<SkSVGNumberType, true> fStrokeMiterLimit;
 *     SkSVGProperty<SkSVGNumberType, true> fStrokeOpacity;
 *     SkSVGProperty<SkSVGLength    , true> fStrokeWidth;
 *
 *     SkSVGProperty<SkSVGVisibility, true> fVisibility;
 *
 *     SkSVGProperty<SkSVGColorType , true> fColor;
 *     SkSVGProperty<SkSVGColorspace, true> fColorInterpolation;
 *     SkSVGProperty<SkSVGColorspace, true> fColorInterpolationFilters;
 *
 *     SkSVGProperty<SkSVGFontFamily, true> fFontFamily;
 *     SkSVGProperty<SkSVGFontStyle , true> fFontStyle;
 *     SkSVGProperty<SkSVGFontSize  , true> fFontSize;
 *     SkSVGProperty<SkSVGFontWeight, true> fFontWeight;
 *     SkSVGProperty<SkSVGTextAnchor, true> fTextAnchor;
 *
 *     // uninherited
 *     SkSVGProperty<SkSVGNumberType, false> fOpacity;
 *     SkSVGProperty<SkSVGFuncIRI   , false> fClipPath;
 *     SkSVGProperty<SkSVGDisplay   , false> fDisplay;
 *     SkSVGProperty<SkSVGFuncIRI   , false> fMask;
 *     SkSVGProperty<SkSVGFuncIRI   , false> fFilter;
 *     SkSVGProperty<SkSVGColor     , false> fStopColor;
 *     SkSVGProperty<SkSVGNumberType, false> fStopOpacity;
 *     SkSVGProperty<SkSVGColor     , false> fFloodColor;
 *     SkSVGProperty<SkSVGNumberType, false> fFloodOpacity;
 *     SkSVGProperty<SkSVGColor     , false> fLightingColor;
 * }
 * ```
 */
public data class SkSVGPresentationAttributes public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGPaint     , true> fFill
   * ```
   */
  public var fFill: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, true> fFillOpacity
   * ```
   */
  public var fFillOpacity: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFillRule  , true> fFillRule
   * ```
   */
  public var fFillRule: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFillRule  , true> fClipRule
   * ```
   */
  public var fClipRule: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGPaint     , true> fStroke
   * ```
   */
  public var fStroke: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGDashArray , true> fStrokeDashArray
   * ```
   */
  public var fStrokeDashArray: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGLength    , true> fStrokeDashOffset
   * ```
   */
  public var fStrokeDashOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGLineCap   , true> fStrokeLineCap
   * ```
   */
  public var fStrokeLineCap: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGLineJoin  , true> fStrokeLineJoin
   * ```
   */
  public var fStrokeLineJoin: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, true> fStrokeMiterLimit
   * ```
   */
  public var fStrokeMiterLimit: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, true> fStrokeOpacity
   * ```
   */
  public var fStrokeOpacity: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGLength    , true> fStrokeWidth
   * ```
   */
  public var fStrokeWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGVisibility, true> fVisibility
   * ```
   */
  public var fVisibility: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColorType , true> fColor
   * ```
   */
  public var fColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColorspace, true> fColorInterpolation
   * ```
   */
  public var fColorInterpolation: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColorspace, true> fColorInterpolationFilters
   * ```
   */
  public var fColorInterpolationFilters: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFontFamily, true> fFontFamily
   * ```
   */
  public var fFontFamily: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFontStyle , true> fFontStyle
   * ```
   */
  public var fFontStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFontSize  , true> fFontSize
   * ```
   */
  public var fFontSize: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFontWeight, true> fFontWeight
   * ```
   */
  public var fFontWeight: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGTextAnchor, true> fTextAnchor
   * ```
   */
  public var fTextAnchor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, false> fOpacity
   * ```
   */
  public var fOpacity: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFuncIRI   , false> fClipPath
   * ```
   */
  public var fClipPath: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGDisplay   , false> fDisplay
   * ```
   */
  public var fDisplay: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFuncIRI   , false> fMask
   * ```
   */
  public var fMask: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGFuncIRI   , false> fFilter
   * ```
   */
  public var fFilter: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColor     , false> fStopColor
   * ```
   */
  public var fStopColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, false> fStopOpacity
   * ```
   */
  public var fStopOpacity: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColor     , false> fFloodColor
   * ```
   */
  public var fFloodColor: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGNumberType, false> fFloodOpacity
   * ```
   */
  public var fFloodOpacity: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSVGProperty<SkSVGColor     , false> fLightingColor
   * ```
   */
  public var fLightingColor: Int,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkSVGPresentationAttributes SkSVGPresentationAttributes::MakeInitial() {
     *     SkSVGPresentationAttributes result;
     *
     *     result.fFill.set(SkSVGPaint(SkSVGColor(SK_ColorBLACK)));
     *     result.fFillOpacity.set(SkSVGNumberType(1));
     *     result.fFillRule.set(SkSVGFillRule(SkSVGFillRule::Type::kNonZero));
     *     result.fClipRule.set(SkSVGFillRule(SkSVGFillRule::Type::kNonZero));
     *
     *     result.fStroke.set(SkSVGPaint(SkSVGPaint::Type::kNone));
     *     result.fStrokeDashArray.set(SkSVGDashArray(SkSVGDashArray::Type::kNone));
     *     result.fStrokeDashOffset.set(SkSVGLength(0));
     *     result.fStrokeLineCap.set(SkSVGLineCap::kButt);
     *     result.fStrokeLineJoin.set(SkSVGLineJoin(SkSVGLineJoin::Type::kMiter));
     *     result.fStrokeMiterLimit.set(SkSVGNumberType(4));
     *     result.fStrokeOpacity.set(SkSVGNumberType(1));
     *     result.fStrokeWidth.set(SkSVGLength(1));
     *
     *     result.fVisibility.set(SkSVGVisibility(SkSVGVisibility::Type::kVisible));
     *
     *     result.fColor.set(SkSVGColorType(SK_ColorBLACK));
     *     result.fColorInterpolation.set(SkSVGColorspace::kSRGB);
     *     result.fColorInterpolationFilters.set(SkSVGColorspace::kLinearRGB);
     *
     *     result.fFontFamily.init("Sans");
     *     result.fFontStyle.init(SkSVGFontStyle::Type::kNormal);
     *     result.fFontSize.init(SkSVGLength(24));
     *     result.fFontWeight.init(SkSVGFontWeight::Type::kNormal);
     *     result.fTextAnchor.init(SkSVGTextAnchor::Type::kStart);
     *
     *     result.fDisplay.init(SkSVGDisplay::kInline);
     *
     *     result.fStopColor.set(SkSVGColor(SK_ColorBLACK));
     *     result.fStopOpacity.set(SkSVGNumberType(1));
     *     result.fFloodColor.set(SkSVGColor(SK_ColorBLACK));
     *     result.fFloodOpacity.set(SkSVGNumberType(1));
     *     result.fLightingColor.set(SkSVGColor(SK_ColorWHITE));
     *
     *     return result;
     * }
     * ```
     */
    public fun makeInitial(): SkSVGPresentationAttributes {
      TODO("Implement makeInitial")
    }
  }
}
