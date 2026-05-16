package org.skia.modules

import StopColorArray
import StopPositionArray
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Href
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGGradient : public SkSVGHiddenContainer {
 * public:
 *     SVG_ATTR(Href, SkSVGIRI, SkSVGIRI())
 *     SVG_ATTR(GradientTransform, SkSVGTransformType, SkSVGTransformType(SkMatrix::I()))
 *     SVG_ATTR(SpreadMethod, SkSVGSpreadMethod, SkSVGSpreadMethod(SkSVGSpreadMethod::Type::kPad))
 *     SVG_ATTR(GradientUnits,
 *              SkSVGObjectBoundingBoxUnits,
 *              SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox))
 *
 * protected:
 *     explicit SkSVGGradient(SkSVGTag t) : INHERITED(t) {}
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     bool onAsPaint(const SkSVGRenderContext&, SkPaint*) const final;
 *
 *     virtual sk_sp<SkShader> onMakeShader(const SkSVGRenderContext&,
 *                                          const SkColor4f*, const SkScalar*, int count,
 *                                          SkTileMode, const SkMatrix& localMatrix) const = 0;
 *
 * private:
 *     using StopPositionArray = skia_private::STArray<2, SkScalar , true>;
 *     using    StopColorArray = skia_private::STArray<2, SkColor4f, true>;
 *     void collectColorStops(const SkSVGRenderContext&, StopPositionArray*, StopColorArray*) const;
 *     SkColor4f resolveStopColor(const SkSVGRenderContext&, const SkSVGStop&) const;
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public abstract class SkSVGGradient : SkSVGHiddenContainer() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Href, SkSVGIRI, SkSVGIRI())
   * ```
   */
  public fun svgATTR(
    param0: Href,
    param1: SkSVGIRI,
    param2: () -> SkSVGIRI,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGGradient::onAsPaint(const SkSVGRenderContext& ctx, SkPaint* paint) const {
   *     StopColorArray colors;
   *     StopPositionArray pos;
   *
   *     this->collectColorStops(ctx, &pos, &colors);
   *
   *     // TODO:
   *     //       * stop (lazy?) sorting
   *     //       * href loop detection
   *     //       * href attribute inheritance (not just color stops)
   *     //       * objectBoundingBox units support
   *
   *     static_assert(static_cast<SkTileMode>(SkSVGSpreadMethod::Type::kPad) ==
   *                   SkTileMode::kClamp, "SkSVGSpreadMethod::Type is out of sync");
   *     static_assert(static_cast<SkTileMode>(SkSVGSpreadMethod::Type::kRepeat) ==
   *                   SkTileMode::kRepeat, "SkSVGSpreadMethod::Type is out of sync");
   *     static_assert(static_cast<SkTileMode>(SkSVGSpreadMethod::Type::kReflect) ==
   *                   SkTileMode::kMirror, "SkSVGSpreadMethod::Type is out of sync");
   *     const auto tileMode = static_cast<SkTileMode>(fSpreadMethod.type());
   *
   *     const auto obbt = ctx.transformForCurrentOBB(fGradientUnits);
   *     const auto localMatrix = SkMatrix::Translate(obbt.offset.x, obbt.offset.y)
   *                            * SkMatrix::Scale(obbt.scale.x, obbt.scale.y)
   *                            * fGradientTransform;
   *
   *     paint->setShader(this->onMakeShader(ctx, colors.begin(), pos.begin(), colors.size(), tileMode,
   *                                         localMatrix));
   *     return true;
   * }
   * ```
   */
  protected fun onAsPaint(ctx: SkSVGRenderContext, paint: SkPaint?): Boolean {
    TODO("Implement onAsPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkShader> onMakeShader(const SkSVGRenderContext&,
   *                                          const SkColor4f*, const SkScalar*, int count,
   *                                          SkTileMode, const SkMatrix& localMatrix) const = 0
   * ```
   */
  protected abstract fun onMakeShader(
    param0: SkSVGRenderContext,
    param1: SkColor4f?,
    param2: SkScalar?,
    count: Int,
    param4: SkTileMode,
    localMatrix: SkMatrix,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * void SkSVGGradient::collectColorStops(const SkSVGRenderContext& ctx,
   *                                       StopPositionArray* pos,
   *                                       StopColorArray* colors) const {
   *     // Used to resolve percentage offsets.
   *     const SkSVGLengthContext ltx(SkSize::Make(1, 1));
   *
   *     this->forEachChild<SkSVGStop>([&](const SkSVGStop* stop) {
   *         colors->push_back(this->resolveStopColor(ctx, *stop));
   *         pos->push_back(
   *             SkTPin(ltx.resolve(stop->getOffset(), SkSVGLengthContext::LengthType::kOther),
   *                    0.f, 1.f));
   *     });
   *
   *     SkASSERT(colors->size() == pos->size());
   *
   *     if (pos->empty() && !fHref.iri().isEmpty()) {
   *         const auto ref = ctx.findNodeById(fHref);
   *         if (ref && (ref->tag() == SkSVGTag::kLinearGradient ||
   *                     ref->tag() == SkSVGTag::kRadialGradient)) {
   *             static_cast<const SkSVGGradient*>(ref.get())->collectColorStops(ctx, pos, colors);
   *         }
   *     }
   * }
   * ```
   */
  private fun collectColorStops(
    ctx: SkSVGRenderContext,
    pos: StopPositionArray?,
    colors: StopColorArray?,
  ) {
    TODO("Implement collectColorStops")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f SkSVGGradient::resolveStopColor(const SkSVGRenderContext& ctx,
   *                                           const SkSVGStop& stop) const {
   *     const auto& stopColor = stop.getStopColor();
   *     const auto& stopOpacity = stop.getStopOpacity();
   *     // Uninherited presentation attrs should have a concrete value at this point.
   *     if (!stopColor.isValue() || !stopOpacity.isValue()) {
   *         SkDEBUGF("unhandled: stop-color or stop-opacity has no value\n");
   *         return SkColors::kBlack;
   *     }
   *
   *     const auto color = SkColor4f::FromColor(ctx.resolveSvgColor(*stopColor));
   *
   *     return { color.fR, color.fG, color.fB, *stopOpacity * color.fA };
   * }
   * ```
   */
  private fun resolveStopColor(ctx: SkSVGRenderContext, stop: SkSVGStop): Int {
    TODO("Implement resolveStopColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGGradient::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setGradientTransform(SkSVGAttributeParser::parse<SkSVGTransformType>(
   *                    "gradientTransform", name, value)) ||
   *            this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", name, value)) ||
   *            this->setSpreadMethod(
   *                    SkSVGAttributeParser::parse<SkSVGSpreadMethod>("spreadMethod", name, value)) ||
   *            this->setGradientUnits(SkSVGAttributeParser::parse<SkSVGObjectBoundingBoxUnits>(
   *                    "gradientUnits", name, value));
   * }
   * ```
   */
  public fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }
}
