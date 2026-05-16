package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkPaint
import undefined.Href

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGPattern final : public SkSVGHiddenContainer {
 * public:
 *     static sk_sp<SkSVGPattern> Make() {
 *         return sk_sp<SkSVGPattern>(new SkSVGPattern());
 *     }
 *
 *     SVG_ATTR(Href, SkSVGIRI, SkSVGIRI())
 *     SVG_OPTIONAL_ATTR(X               , SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Y               , SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Width           , SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Height          , SkSVGLength)
 *     SVG_OPTIONAL_ATTR(PatternTransform, SkSVGTransformType)
 *
 * protected:
 *     SkSVGPattern();
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     bool onAsPaint(const SkSVGRenderContext&, SkPaint*) const override;
 *
 * private:
 *     struct PatternAttributes {
 *         std::optional<SkSVGLength>  fX,
 *                                     fY,
 *                                     fWidth,
 *                                     fHeight;
 *         std::optional<SkSVGTransformType> fPatternTransform;
 *     };
 *
 *     const SkSVGPattern* resolveHref(const SkSVGRenderContext&, PatternAttributes*) const;
 *     const SkSVGPattern* hrefTarget(const SkSVGRenderContext&) const;
 *
 *     // TODO:
 *     //   - patternUnits
 *     //   - patternContentUnits
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public class SkSVGPattern public constructor() : SkSVGHiddenContainer(TODO()) {
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
   * bool SkSVGPattern::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", name, value)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", name, value)) ||
   *            this->setWidth(SkSVGAttributeParser::parse<SkSVGLength>("width", name, value)) ||
   *            this->setHeight(SkSVGAttributeParser::parse<SkSVGLength>("height", name, value)) ||
   *            this->setPatternTransform(SkSVGAttributeParser::parse<SkSVGTransformType>(
   *                    "patternTransform", name, value)) ||
   *            this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGPattern::onAsPaint(const SkSVGRenderContext& ctx, SkPaint* paint) const {
   *     PatternAttributes attrs;
   *     const auto* contentNode = this->resolveHref(ctx, &attrs);
   *
   *     const auto tile = ctx.lengthContext().resolveRect(
   *             attrs.fX.has_value()      ? *attrs.fX      : SkSVGLength(0),
   *             attrs.fY.has_value()      ? *attrs.fY      : SkSVGLength(0),
   *             attrs.fWidth.has_value()  ? *attrs.fWidth  : SkSVGLength(0),
   *             attrs.fHeight.has_value() ? *attrs.fHeight : SkSVGLength(0));
   *
   *     if (tile.isEmpty()) {
   *         return false;
   *     }
   *
   *     const SkMatrix* patternTransform = SkOptAddressOrNull(attrs.fPatternTransform);
   *
   *     SkPictureRecorder recorder;
   *     SkSVGRenderContext recordingContext(ctx, recorder.beginRecording(tile));
   *
   *     // Cannot call into INHERITED:: because SkSVGHiddenContainer skips rendering.
   *     contentNode->SkSVGContainer::onRender(recordingContext);
   *
   *     paint->setShader(recorder.finishRecordingAsPicture()->makeShader(
   *                                                  SkTileMode::kRepeat,
   *                                                  SkTileMode::kRepeat,
   *                                                  SkFilterMode::kLinear,
   *                                                  patternTransform,
   *                                                  &tile));
   *     return true;
   * }
   * ```
   */
  protected override fun onAsPaint(ctx: SkSVGRenderContext, paint: SkPaint?): Boolean {
    TODO("Implement onAsPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSVGPattern* SkSVGPattern::resolveHref(const SkSVGRenderContext& ctx,
   *                                               PatternAttributes* attrs) const {
   *     const SkSVGPattern *currentNode = this,
   *                        *contentNode = this;
   *     do {
   *         // Bitwise OR to avoid short-circuiting.
   *         const bool didInherit =
   *             inherit_if_needed(currentNode->fX               , attrs->fX)      |
   *             inherit_if_needed(currentNode->fY               , attrs->fY)      |
   *             inherit_if_needed(currentNode->fWidth           , attrs->fWidth)  |
   *             inherit_if_needed(currentNode->fHeight          , attrs->fHeight) |
   *             inherit_if_needed(currentNode->fPatternTransform, attrs->fPatternTransform);
   *
   *         if (!contentNode->hasChildren()) {
   *             contentNode = currentNode;
   *         }
   *
   *         if (contentNode->hasChildren() && !didInherit) {
   *             // All attributes have been resolved, and a valid content node has been found.
   *             // We can terminate the href chain early.
   *             break;
   *         }
   *
   *         // TODO: reference loop mitigation.
   *         currentNode = currentNode->hrefTarget(ctx);
   *     } while (currentNode);
   *
   *     return contentNode;
   * }
   * ```
   */
  private fun resolveHref(ctx: SkSVGRenderContext, attrs: PatternAttributes?): SkSVGPattern {
    TODO("Implement resolveHref")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSVGPattern* SkSVGPattern::hrefTarget(const SkSVGRenderContext& ctx) const {
   *     if (fHref.iri().isEmpty()) {
   *         return nullptr;
   *     }
   *
   *     const auto href = ctx.findNodeById(fHref);
   *     if (!href || href->tag() != SkSVGTag::kPattern) {
   *         return nullptr;
   *     }
   *
   *     return static_cast<const SkSVGPattern*>(href.get());
   * }
   * ```
   */
  private fun hrefTarget(ctx: SkSVGRenderContext): SkSVGPattern {
    TODO("Implement hrefTarget")
  }

  public data class PatternAttributes public constructor(
    public var fX: Int,
    public var fY: Int,
    public var fWidth: Int,
    public var fHeight: Int,
    public var fPatternTransform: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGPattern> Make() {
     *         return sk_sp<SkSVGPattern>(new SkSVGPattern());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
