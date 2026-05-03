package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGImage final : public SkSVGTransformableNode {
 * public:
 *     static sk_sp<SkSVGImage> Make() {
 *         return sk_sp<SkSVGImage>(new SkSVGImage());
 *     }
 *
 *     void appendChild(sk_sp<SkSVGNode>) override {
 *         SkDEBUGF("cannot append child nodes to this element.\n");
 *     }
 *
 *     bool onPrepareToRender(SkSVGRenderContext*) const override;
 *     void onRender(const SkSVGRenderContext&) const override;
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *     SkRect onTransformableObjectBoundingBox(const SkSVGRenderContext&) const override;
 *
 *     struct ImageInfo {
 *         sk_sp<SkImage> fImage;
 *         SkRect         fDst;
 *     };
 *     static ImageInfo LoadImage(const sk_sp<skresources::ResourceProvider>&,
 *                                const SkSVGIRI&,
 *                                const SkRect&,
 *                                SkSVGPreserveAspectRatio);
 *
 *     SVG_ATTR(X                  , SkSVGLength             , SkSVGLength(0))
 *     SVG_ATTR(Y                  , SkSVGLength             , SkSVGLength(0))
 *     SVG_ATTR(Width              , SkSVGLength             , SkSVGLength(0))
 *     SVG_ATTR(Height             , SkSVGLength             , SkSVGLength(0))
 *     SVG_ATTR(Href               , SkSVGIRI                , SkSVGIRI())
 *     SVG_ATTR(PreserveAspectRatio, SkSVGPreserveAspectRatio, SkSVGPreserveAspectRatio())
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGImage() : INHERITED(SkSVGTag::kImage) {}
 *
 *     using INHERITED = SkSVGTransformableNode;
 * }
 * ```
 */
public class SkSVGImage public constructor() : SkSVGTransformableNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void appendChild(sk_sp<SkSVGNode>) override {
   *         SkDEBUGF("cannot append child nodes to this element.\n");
   *     }
   * ```
   */
  public override fun appendChild(param0: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGImage::onPrepareToRender(SkSVGRenderContext* ctx) const {
   *     // Width or height of 0 disables rendering per spec:
   *     // https://www.w3.org/TR/SVG11/struct.html#ImageElement
   *     return !fHref.iri().isEmpty() && fWidth.value() > 0 && fHeight.value() > 0 &&
   *            INHERITED::onPrepareToRender(ctx);
   * }
   * ```
   */
  public override fun onPrepareToRender(ctx: SkSVGRenderContext?): Boolean {
    TODO("Implement onPrepareToRender")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGImage::onRender(const SkSVGRenderContext& ctx) const {
   *     // Per spec: x, w, width, height attributes establish the new viewport.
   *     const SkSVGLengthContext& lctx = ctx.lengthContext();
   *     const SkRect viewPort = lctx.resolveRect(fX, fY, fWidth, fHeight);
   *
   *     const auto imgInfo = LoadImage(ctx.resourceProvider(), fHref, viewPort, fPreserveAspectRatio);
   *     if (!imgInfo.fImage) {
   *         SkDEBUGF("can't render image: load image failed\n");
   *         return;
   *     }
   *
   *     // TODO: image-rendering property
   *     ctx.canvas()->drawImageRect(
   *             imgInfo.fImage, imgInfo.fDst, SkSamplingOptions(SkFilterMode::kLinear));
   * }
   * ```
   */
  public override fun onRender(ctx: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGImage::onAsPath(const SkSVGRenderContext&) const { return {}; }
   * ```
   */
  public override fun onAsPath(param0: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGImage::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     const SkSVGLengthContext& lctx = ctx.lengthContext();
   *     return lctx.resolveRect(fX, fY, fWidth, fHeight);
   * }
   * ```
   */
  public override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X                  , SkSVGLength             , SkSVGLength(0))
   * ```
   */
  public fun svgATTR(
    param0: X,
    param1: SkSVGLength,
    param2: (Int) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGImage::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", n, v)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", n, v)) ||
   *            this->setWidth(SkSVGAttributeParser::parse<SkSVGLength>("width", n, v)) ||
   *            this->setHeight(SkSVGAttributeParser::parse<SkSVGLength>("height", n, v)) ||
   *            this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", n, v)) ||
   *            this->setPreserveAspectRatio(SkSVGAttributeParser::parse<SkSVGPreserveAspectRatio>(
   *                    "preserveAspectRatio", n, v));
   * }
   * ```
   */
  public fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public data class ImageInfo public constructor(
    public var fImage: Int,
    public var fDst: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGImage> Make() {
     *         return sk_sp<SkSVGImage>(new SkSVGImage());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSVGImage::ImageInfo SkSVGImage::LoadImage(const sk_sp<skresources::ResourceProvider>& rp,
     *                                             const SkSVGIRI& iri,
     *                                             const SkRect& viewPort,
     *                                             SkSVGPreserveAspectRatio par) {
     *     SkASSERT(rp);
     *
     *     // TODO: svg sources
     *     sk_sp<SkImage> image = ::LoadImage(rp, iri);
     *     if (!image) {
     *         return {};
     *     }
     *
     *     // Per spec: raster content has implicit viewbox of '0 0 width height'.
     *     const SkRect viewBox = SkRect::Make(image->bounds());
     *
     *     // Map and place at x, y specified by viewport
     *     const SkMatrix m = ComputeViewboxMatrix(viewBox, viewPort, par);
     *     const SkRect dst = m.mapRect(viewBox).makeOffset(viewPort.fLeft, viewPort.fTop);
     *
     *     return {std::move(image), dst};
     * }
     * ```
     */
    public fun loadImage(
      rp: SkSp<ResourceProvider>,
      iri: SkSVGIRI,
      viewPort: SkRect,
      par: SkSVGPreserveAspectRatio,
    ): ImageInfo {
      TODO("Implement loadImage")
    }
  }
}
