package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkCanvas
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGRenderContext {
 * public:
 *     // Captures data required for object bounding box resolution.
 *     struct OBBScope {
 *         const SkSVGNode*          fNode;
 *         const SkSVGRenderContext* fCtx;
 *     };
 *
 *     SkSVGRenderContext(SkCanvas*,
 *                        const sk_sp<SkFontMgr>&,
 *                        const sk_sp<skresources::ResourceProvider>&,
 *                        const SkSVGIDMapper&,
 *                        const SkSVGLengthContext&,
 *                        const SkSVGPresentationContext&,
 *                        const OBBScope&,
 *                        const sk_sp<SkShapers::Factory>&);
 *     SkSVGRenderContext(const SkSVGRenderContext&);
 *     SkSVGRenderContext(const SkSVGRenderContext&, SkCanvas*);
 *     // Establish a new OBB scope.  Normally used when entering a node's render scope.
 *     SkSVGRenderContext(const SkSVGRenderContext&, const SkSVGNode*);
 *     ~SkSVGRenderContext();
 *
 *     const SkSVGLengthContext& lengthContext() const { return *fLengthContext; }
 *     SkSVGLengthContext* writableLengthContext() { return fLengthContext.writable(); }
 *
 *     const SkSVGPresentationContext& presentationContext() const { return *fPresentationContext; }
 *
 *     SkCanvas* canvas() const { return fCanvas; }
 *     void saveOnce();
 *
 *     enum ApplyFlags {
 *         kLeaf = 1 << 0, // the target node doesn't have descendants
 *     };
 *     void applyPresentationAttributes(const SkSVGPresentationAttributes&, uint32_t flags);
 *
 *     // Scoped wrapper that temporarily clears the original node reference.
 *     class BorrowedNode {
 *     public:
 *         explicit BorrowedNode(sk_sp<SkSVGNode>* node)
 *             : fOwner(node) {
 *             if (fOwner) {
 *                 fBorrowed = std::move(*fOwner);
 *                 *fOwner = nullptr;
 *             }
 *         }
 *
 *         ~BorrowedNode() {
 *             if (fOwner) {
 *                 *fOwner = std::move(fBorrowed);
 *             }
 *         }
 *
 *         const SkSVGNode* get() const { return fBorrowed.get(); }
 *         const SkSVGNode* operator->() const { return fBorrowed.get(); }
 *         const SkSVGNode& operator*() const { return *fBorrowed; }
 *
 *         explicit operator bool() const { return !!fBorrowed; }
 *
 *     private:
 *         // noncopyable
 *         BorrowedNode(const BorrowedNode&)      = delete;
 *         BorrowedNode& operator=(BorrowedNode&) = delete;
 *
 *         sk_sp<SkSVGNode>* fOwner;
 *         sk_sp<SkSVGNode>  fBorrowed;
 *     };
 *
 *     // Note: the id->node association is cleared for the lifetime of the returned value
 *     // (effectively breaks reference cycles, assuming appropriate return value scoping).
 *     BorrowedNode findNodeById(const SkSVGIRI&) const;
 *
 *     std::optional<SkPaint> fillPaint() const;
 *     std::optional<SkPaint> strokePaint() const;
 *
 *     SkSVGColorType resolveSvgColor(const SkSVGColor&) const;
 *
 *     // The local computed clip path (not inherited).
 *     const SkPath* clipPath() const { return SkOptAddressOrNull(fClipPath); }
 *
 *     const sk_sp<skresources::ResourceProvider>& resourceProvider() const {
 *         return fResourceProvider;
 *     }
 *
 *     sk_sp<SkFontMgr> fontMgr() const {
 *         // It is probably an oversight to try to render <text> without having set the SkFontMgr.
 *         // We will assert this in debug mode, but fallback to an empty fontmgr in release builds.
 *         SkASSERT(fFontMgr);
 *         return fFontMgr ? fFontMgr : SkFontMgr::RefEmpty();
 *     }
 *
 *     // Returns the translate/scale transformation required to map into the current OBB scope,
 *     // with the specified units.
 *     struct OBBTransform {
 *         SkV2 offset, scale;
 *     };
 *     OBBTransform transformForCurrentOBB(SkSVGObjectBoundingBoxUnits) const;
 *
 *     SkRect resolveOBBRect(const SkSVGLength& x, const SkSVGLength& y,
 *                           const SkSVGLength& w, const SkSVGLength& h,
 *                           SkSVGObjectBoundingBoxUnits) const;
 *
 *     const OBBScope& currentOBBScope() const { return fOBBScope; }
 *
 *     std::unique_ptr<SkShaper> makeShaper() const {
 *         SkASSERT(fTextShapingFactory);
 *         return fTextShapingFactory->makeShaper(this->fontMgr());
 *     }
 *
 *     std::unique_ptr<SkShaper::BiDiRunIterator> makeBidiRunIterator(const char* utf8,
 *                                                                    size_t utf8Bytes,
 *                                                                    uint8_t bidiLevel) const {
 *         SkASSERT(fTextShapingFactory);
 *         return fTextShapingFactory->makeBidiRunIterator(utf8, utf8Bytes, bidiLevel);
 *     }
 *
 *     std::unique_ptr<SkShaper::ScriptRunIterator> makeScriptRunIterator(const char* utf8,
 *                                                                        size_t utf8Bytes) const {
 *         SkASSERT(fTextShapingFactory);
 *         constexpr SkFourByteTag unknownScript = SkSetFourByteTag('Z', 'z', 'z', 'z');
 *         return fTextShapingFactory->makeScriptRunIterator(utf8, utf8Bytes, unknownScript);
 *     }
 *
 * private:
 *     // Stack-only
 *     void* operator new(size_t)                               = delete;
 *     void* operator new(size_t, void*)                        = delete;
 *     SkSVGRenderContext& operator=(const SkSVGRenderContext&) = delete;
 *
 *     void applyOpacity(SkScalar opacity, uint32_t flags, bool hasFilter);
 *     void applyFilter(const SkSVGFuncIRI&);
 *     void applyClip(const SkSVGFuncIRI&);
 *     void applyMask(const SkSVGFuncIRI&);
 *
 *     std::optional<SkPaint> commonPaint(const SkSVGPaint&, float opacity) const;
 *
 *     const sk_sp<SkFontMgr>&                       fFontMgr;
 *     const sk_sp<SkShapers::Factory>&              fTextShapingFactory;
 *     const sk_sp<skresources::ResourceProvider>&   fResourceProvider;
 *     const SkSVGIDMapper&                          fIDMapper;
 *     SkTCopyOnFirstWrite<SkSVGLengthContext>       fLengthContext;
 *     SkTCopyOnFirstWrite<SkSVGPresentationContext> fPresentationContext;
 *     SkCanvas*                                     fCanvas;
 *     // The save count on 'fCanvas' at construction time.
 *     // A restoreToCount() will be issued on destruction.
 *     int                                           fCanvasSaveCount;
 *
 *     // clipPath, if present for the current context (not inherited).
 *     std::optional<SkPath>                         fClipPath;
 *
 *     // Deferred opacity optimization for leaf nodes.
 *     float                                         fDeferredPaintOpacity = 1;
 *
 *     // Current object bounding box scope.
 *     const OBBScope                                fOBBScope;
 * }
 * ```
 */
public data class SkSVGRenderContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkFontMgr>&                       fFontMgr
   * ```
   */
  private val fFontMgr: Int,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkShapers::Factory>&              fTextShapingFactory
   * ```
   */
  private val fTextShapingFactory: Int,
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skresources::ResourceProvider>&   fResourceProvider
   * ```
   */
  private val fResourceProvider: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkSVGIDMapper&                          fIDMapper
   * ```
   */
  private val fIDMapper: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTCopyOnFirstWrite<SkSVGLengthContext>       fLengthContext
   * ```
   */
  private var fLengthContext: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTCopyOnFirstWrite<SkSVGPresentationContext> fPresentationContext
   * ```
   */
  private var fPresentationContext: Int,
  /**
   * C++ original:
   * ```cpp
   * SkCanvas*                                     fCanvas
   * ```
   */
  private var fCanvas: SkCanvas?,
  /**
   * C++ original:
   * ```cpp
   * int                                           fCanvasSaveCount
   * ```
   */
  private var fCanvasSaveCount: Int,
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPath>                         fClipPath
   * ```
   */
  private var fClipPath: Int,
  /**
   * C++ original:
   * ```cpp
   * float                                         fDeferredPaintOpacity = 1
   * ```
   */
  private var fDeferredPaintOpacity: Float,
  /**
   * C++ original:
   * ```cpp
   * const OBBScope                                fOBBScope
   * ```
   */
  private val fOBBScope: OBBScope,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkSVGLengthContext& lengthContext() const { return *fLengthContext; }
   * ```
   */
  public fun lengthContext(): SkSVGLengthContext {
    TODO("Implement lengthContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGLengthContext* writableLengthContext() { return fLengthContext.writable(); }
   * ```
   */
  public fun writableLengthContext(): SkSVGLengthContext {
    TODO("Implement writableLengthContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSVGPresentationContext& presentationContext() const { return *fPresentationContext; }
   * ```
   */
  public fun presentationContext(): SkSVGPresentationContext {
    TODO("Implement presentationContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCanvas* canvas() const { return fCanvas; }
   * ```
   */
  public fun canvas(): SkCanvas {
    TODO("Implement canvas")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::saveOnce() {
   *     // The canvas only needs to be saved once, per local SkSVGRenderContext.
   *     if (fCanvas->getSaveCount() == fCanvasSaveCount) {
   *         fCanvas->save();
   *     }
   *
   *     SkASSERT(fCanvas->getSaveCount() > fCanvasSaveCount);
   * }
   * ```
   */
  public fun saveOnce() {
    TODO("Implement saveOnce")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::applyPresentationAttributes(const SkSVGPresentationAttributes& attrs,
   *                                                      uint32_t flags) {
   *
   * #define ApplyLazyInheritedAttribute(ATTR)                                               \
   *     do {                                                                                \
   *         /* All attributes should be defined on the inherited context. */                \
   *         SkASSERT(fPresentationContext->fInherited.f ## ATTR.isValue());                 \
   *         const auto& attr = attrs.f ## ATTR;                                             \
   *         if (attr.isValue() && *attr != *fPresentationContext->fInherited.f ## ATTR) {   \
   *             /* Update the local attribute value */                                      \
   *             fPresentationContext.writable()->fInherited.f ## ATTR.set(*attr);           \
   *         }                                                                               \
   *     } while (false)
   *
   *     ApplyLazyInheritedAttribute(Fill);
   *     ApplyLazyInheritedAttribute(FillOpacity);
   *     ApplyLazyInheritedAttribute(FillRule);
   *     ApplyLazyInheritedAttribute(FontFamily);
   *     ApplyLazyInheritedAttribute(FontSize);
   *     ApplyLazyInheritedAttribute(FontStyle);
   *     ApplyLazyInheritedAttribute(FontWeight);
   *     ApplyLazyInheritedAttribute(ClipRule);
   *     ApplyLazyInheritedAttribute(Stroke);
   *     ApplyLazyInheritedAttribute(StrokeDashOffset);
   *     ApplyLazyInheritedAttribute(StrokeDashArray);
   *     ApplyLazyInheritedAttribute(StrokeLineCap);
   *     ApplyLazyInheritedAttribute(StrokeLineJoin);
   *     ApplyLazyInheritedAttribute(StrokeMiterLimit);
   *     ApplyLazyInheritedAttribute(StrokeOpacity);
   *     ApplyLazyInheritedAttribute(StrokeWidth);
   *     ApplyLazyInheritedAttribute(TextAnchor);
   *     ApplyLazyInheritedAttribute(Visibility);
   *     ApplyLazyInheritedAttribute(Color);
   *     ApplyLazyInheritedAttribute(ColorInterpolation);
   *     ApplyLazyInheritedAttribute(ColorInterpolationFilters);
   *
   * #undef ApplyLazyInheritedAttribute
   *
   *     // Uninherited attributes.  Only apply to the current context.
   *
   *     const bool hasFilter = attrs.fFilter.isValue();
   *     if (attrs.fOpacity.isValue()) {
   *         this->applyOpacity(*attrs.fOpacity, flags, hasFilter);
   *     }
   *
   *     if (attrs.fClipPath.isValue()) {
   *         this->applyClip(*attrs.fClipPath);
   *     }
   *
   *     if (attrs.fMask.isValue()) {
   *         this->applyMask(*attrs.fMask);
   *     }
   *
   *     // TODO: when both a filter and opacity are present, we can apply both with a single layer
   *     if (hasFilter) {
   *         this->applyFilter(*attrs.fFilter);
   *     }
   *
   *     // Remaining uninherited presentation attributes are accessed as SkSVGNode fields, not via
   *     // the render context.
   *     // TODO: resolve these in a pre-render styling pass and assert here that they are values.
   *     // - stop-color
   *     // - stop-opacity
   *     // - flood-color
   *     // - flood-opacity
   *     // - lighting-color
   * }
   * ```
   */
  public fun applyPresentationAttributes(attrs: SkSVGPresentationAttributes, flags: UInt) {
    TODO("Implement applyPresentationAttributes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGRenderContext::BorrowedNode SkSVGRenderContext::findNodeById(const SkSVGIRI& iri) const {
   *     if (iri.type() != SkSVGIRI::Type::kLocal) {
   *         SkDEBUGF("non-local iri references not currently supported");
   *         return BorrowedNode(nullptr);
   *     }
   *     return BorrowedNode(fIDMapper.find(iri.iri()));
   * }
   * ```
   */
  private fun findNodeById(iri: SkSVGIRI): BorrowedNode {
    TODO("Implement findNodeById")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint> SkSVGRenderContext::fillPaint() const {
   *     const auto& props = fPresentationContext->fInherited;
   *     auto p = this->commonPaint(*props.fFill, *props.fFillOpacity);
   *
   *     if (p.has_value()) {
   *         p->setStyle(SkPaint::kFill_Style);
   *     }
   *
   *     return p;
   * }
   * ```
   */
  private fun fillPaint(): Int {
    TODO("Implement fillPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint> SkSVGRenderContext::strokePaint() const {
   *     const auto& props = fPresentationContext->fInherited;
   *     auto p = this->commonPaint(*props.fStroke, *props.fStrokeOpacity);
   *
   *     if (p.has_value()) {
   *         p->setStyle(SkPaint::kStroke_Style);
   *         p->setStrokeWidth(fLengthContext->resolve(*props.fStrokeWidth,
   *                                                   SkSVGLengthContext::LengthType::kOther));
   *         p->setStrokeCap(toSkCap(*props.fStrokeLineCap));
   *         p->setStrokeJoin(toSkJoin(*props.fStrokeLineJoin));
   *         p->setStrokeMiter(*props.fStrokeMiterLimit);
   *         p->setPathEffect(dash_effect(props, *fLengthContext));
   *     }
   *
   *     return p;
   * }
   * ```
   */
  private fun strokePaint(): Int {
    TODO("Implement strokePaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGColorType SkSVGRenderContext::resolveSvgColor(const SkSVGColor& color) const {
   *     if (fPresentationContext->fNamedColors) {
   *         for (auto&& ident : color.vars()) {
   *             SkSVGColorType* c = fPresentationContext->fNamedColors->find(ident);
   *             if (c) {
   *                 return *c;
   *             }
   *         }
   *     }
   *     switch (color.type()) {
   *         case SkSVGColor::Type::kColor:
   *             return color.color();
   *         case SkSVGColor::Type::kCurrentColor:
   *             return *fPresentationContext->fInherited.fColor;
   *         case SkSVGColor::Type::kICCColor:
   *             SkDEBUGF("ICC color unimplemented");
   *             return SK_ColorBLACK;
   *     }
   *     SkUNREACHABLE;
   * }
   * ```
   */
  private fun resolveSvgColor(color: SkSVGColor): Int {
    TODO("Implement resolveSvgColor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPath* clipPath() const { return SkOptAddressOrNull(fClipPath); }
   * ```
   */
  private fun clipPath(): Int {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skresources::ResourceProvider>& resourceProvider() const {
   *         return fResourceProvider;
   *     }
   * ```
   */
  private fun resourceProvider(): Int {
    TODO("Implement resourceProvider")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fontMgr() const {
   *         // It is probably an oversight to try to render <text> without having set the SkFontMgr.
   *         // We will assert this in debug mode, but fallback to an empty fontmgr in release builds.
   *         SkASSERT(fFontMgr);
   *         return fFontMgr ? fFontMgr : SkFontMgr::RefEmpty();
   *     }
   * ```
   */
  private fun fontMgr(): Int {
    TODO("Implement fontMgr")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGRenderContext::OBBTransform
   * SkSVGRenderContext::transformForCurrentOBB(SkSVGObjectBoundingBoxUnits u) const {
   *     if (!fOBBScope.fNode || u.type() == SkSVGObjectBoundingBoxUnits::Type::kUserSpaceOnUse) {
   *         return {{0,0},{1,1}};
   *     }
   *     SkASSERT(fOBBScope.fCtx);
   *
   *     const auto obb = fOBBScope.fNode->objectBoundingBox(*fOBBScope.fCtx);
   *     return {{obb.x(), obb.y()}, {obb.width(), obb.height()}};
   * }
   * ```
   */
  private fun transformForCurrentOBB(u: SkSVGObjectBoundingBoxUnits): OBBTransform {
    TODO("Implement transformForCurrentOBB")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGRenderContext::resolveOBBRect(const SkSVGLength& x, const SkSVGLength& y,
   *                                           const SkSVGLength& w, const SkSVGLength& h,
   *                                           SkSVGObjectBoundingBoxUnits obbu) const {
   *     SkTCopyOnFirstWrite<SkSVGLengthContext> lctx(fLengthContext);
   *
   *     if (obbu.type() == SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox) {
   *         *lctx.writable() = SkSVGLengthContext({1,1});
   *     }
   *
   *     auto r = lctx->resolveRect(x, y, w, h);
   *     const auto obbt = this->transformForCurrentOBB(obbu);
   *
   *     return SkRect::MakeXYWH(obbt.scale.x * r.x() + obbt.offset.x,
   *                             obbt.scale.y * r.y() + obbt.offset.y,
   *                             obbt.scale.x * r.width(),
   *                             obbt.scale.y * r.height());
   * }
   * ```
   */
  private fun resolveOBBRect(
    x: SkSVGLength,
    y: SkSVGLength,
    w: SkSVGLength,
    h: SkSVGLength,
    obbu: SkSVGObjectBoundingBoxUnits,
  ): Int {
    TODO("Implement resolveOBBRect")
  }

  /**
   * C++ original:
   * ```cpp
   * const OBBScope& currentOBBScope() const { return fOBBScope; }
   * ```
   */
  private fun currentOBBScope(): OBBScope {
    TODO("Implement currentOBBScope")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper> makeShaper() const {
   *         SkASSERT(fTextShapingFactory);
   *         return fTextShapingFactory->makeShaper(this->fontMgr());
   *     }
   * ```
   */
  private fun makeShaper(): Int {
    TODO("Implement makeShaper")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::BiDiRunIterator> makeBidiRunIterator(const char* utf8,
   *                                                                    size_t utf8Bytes,
   *                                                                    uint8_t bidiLevel) const {
   *         SkASSERT(fTextShapingFactory);
   *         return fTextShapingFactory->makeBidiRunIterator(utf8, utf8Bytes, bidiLevel);
   *     }
   * ```
   */
  private fun makeBidiRunIterator(
    utf8: String?,
    utf8Bytes: ULong,
    bidiLevel: UByte,
  ): Int {
    TODO("Implement makeBidiRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkShaper::ScriptRunIterator> makeScriptRunIterator(const char* utf8,
   *                                                                        size_t utf8Bytes) const {
   *         SkASSERT(fTextShapingFactory);
   *         constexpr SkFourByteTag unknownScript = SkSetFourByteTag('Z', 'z', 'z', 'z');
   *         return fTextShapingFactory->makeScriptRunIterator(utf8, utf8Bytes, unknownScript);
   *     }
   * ```
   */
  private fun makeScriptRunIterator(utf8: String?, utf8Bytes: ULong): Int {
    TODO("Implement makeScriptRunIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t)                               = delete
   * ```
   */
  private fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t, void*)                        = delete
   * ```
   */
  private fun toNew(param0: ULong, param1: Unit?) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGRenderContext& operator=(const SkSVGRenderContext&) = delete
   * ```
   */
  private fun assign(param0: SkSVGRenderContext) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::applyOpacity(SkScalar opacity, uint32_t flags, bool hasFilter) {
   *     if (opacity >= 1) {
   *         return;
   *     }
   *
   *     const auto& props = fPresentationContext->fInherited;
   *     const bool hasFill   = props.fFill  ->type() != SkSVGPaint::Type::kNone,
   *                hasStroke = props.fStroke->type() != SkSVGPaint::Type::kNone;
   *
   *     // We can apply the opacity as paint alpha if it only affects one atomic draw.
   *     // For now, this means all of the following must be true:
   *     //   - the target node doesn't have any descendants;
   *     //   - it only has a stroke or a fill (but not both);
   *     //   - it does not have a filter.
   *     // Going forward, we may needto refine this heuristic (e.g. to accommodate markers).
   *     if ((flags & kLeaf) && (hasFill ^ hasStroke) && !hasFilter) {
   *         fDeferredPaintOpacity *= opacity;
   *     } else {
   *         // Expensive, layer-based fall back.
   *         SkPaint opacityPaint;
   *         opacityPaint.setAlphaf(SkTPin(opacity, 0.0f, 1.0f));
   *         // Balanced in the destructor, via restoreToCount().
   *         fCanvas->saveLayer(nullptr, &opacityPaint);
   *     }
   * }
   * ```
   */
  private fun applyOpacity(
    opacity: SkScalar,
    flags: UInt,
    hasFilter: Boolean,
  ) {
    TODO("Implement applyOpacity")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::applyFilter(const SkSVGFuncIRI& filter) {
   *     if (filter.type() != SkSVGFuncIRI::Type::kIRI) {
   *         return;
   *     }
   *
   *     const auto node = this->findNodeById(filter.iri());
   *     if (!node || node->tag() != SkSVGTag::kFilter) {
   *         return;
   *     }
   *
   *     const SkSVGFilter* filterNode = reinterpret_cast<const SkSVGFilter*>(node.get());
   *     sk_sp<SkImageFilter> imageFilter = filterNode->buildFilterDAG(*this);
   *     if (imageFilter) {
   *         SkPaint filterPaint;
   *         filterPaint.setImageFilter(imageFilter);
   *         // Balanced in the destructor, via restoreToCount().
   *         fCanvas->saveLayer(nullptr, &filterPaint);
   *     }
   * }
   * ```
   */
  private fun applyFilter(filter: SkSVGFuncIRI) {
    TODO("Implement applyFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::applyClip(const SkSVGFuncIRI& clip) {
   *     if (clip.type() != SkSVGFuncIRI::Type::kIRI) {
   *         return;
   *     }
   *
   *     const auto clipNode = this->findNodeById(clip.iri());
   *     if (!clipNode || clipNode->tag() != SkSVGTag::kClipPath) {
   *         return;
   *     }
   *
   *     const SkPath clipPath = static_cast<const SkSVGClipPath*>(clipNode.get())->resolveClip(*this);
   *
   *     // We use the computed clip path in two ways:
   *     //
   *     //   - apply to the current canvas, for drawing
   *     //   - track in the presentation context, for asPath() composition
   *     //
   *     // TODO: the two uses are exclusive, avoid canvas churn when non needed.
   *
   *     this->saveOnce();
   *
   *     fCanvas->clipPath(clipPath, true);
   *     fClipPath = clipPath;
   * }
   * ```
   */
  private fun applyClip(clip: SkSVGFuncIRI) {
    TODO("Implement applyClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRenderContext::applyMask(const SkSVGFuncIRI& mask) {
   *     if (mask.type() != SkSVGFuncIRI::Type::kIRI) {
   *         return;
   *     }
   *
   *     const auto node = this->findNodeById(mask.iri());
   *     if (!node || node->tag() != SkSVGTag::kMask) {
   *         return;
   *     }
   *
   *     const auto* mask_node = static_cast<const SkSVGMask*>(node.get());
   *     const auto mask_bounds = mask_node->bounds(*this);
   *
   *     // Isolation/mask layer.
   *     fCanvas->saveLayer(mask_bounds, nullptr);
   *
   *     // Render and filter mask content.
   *     mask_node->renderMask(*this);
   *
   *     // Content layer
   *     SkPaint masking_paint;
   *     masking_paint.setBlendMode(SkBlendMode::kSrcIn);
   *     fCanvas->saveLayer(mask_bounds, &masking_paint);
   *
   *     // Content is also clipped to the specified mask bounds.
   *     fCanvas->clipRect(mask_bounds, true);
   *
   *     // At this point we're set up for content rendering.
   *     // The pending layers are restored in the destructor (render context scope exit).
   *     // Restoring triggers srcIn-compositing the content against the mask.
   * }
   * ```
   */
  private fun applyMask(mask: SkSVGFuncIRI) {
    TODO("Implement applyMask")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPaint> SkSVGRenderContext::commonPaint(const SkSVGPaint& paint_selector,
   *                                                  float paint_opacity) const {
   *     if (paint_selector.type() == SkSVGPaint::Type::kNone) {
   *         return std::optional<SkPaint>();
   *     }
   *
   *     SkPaint p;
   *
   *     switch (paint_selector.type()) {
   *     case SkSVGPaint::Type::kColor:
   *         p.setColor(this->resolveSvgColor(paint_selector.color()));
   *         break;
   *     case SkSVGPaint::Type::kIRI: {
   *         // Our property inheritance is borked as it follows the render path and not the tree
   *         // hierarchy.  To avoid gross transgressions like leaf node presentation attributes
   *         // leaking into the paint server context, use a pristine presentation context when
   *         // following hrefs.
   *         //
   *         // Preserve the OBB scope because some paints use object bounding box coords
   *         // (e.g. gradient control points), which requires access to the render context
   *         // and node being rendered.
   *         SkSVGPresentationContext pctx;
   *         pctx.fNamedColors = fPresentationContext->fNamedColors;
   *         SkSVGRenderContext local_ctx(fCanvas,
   *                                      fFontMgr,
   *                                      fResourceProvider,
   *                                      fIDMapper,
   *                                      *fLengthContext,
   *                                      pctx,
   *                                      fOBBScope,
   *                                      fTextShapingFactory);
   *
   *         const auto node = this->findNodeById(paint_selector.iri());
   *         if (!node || !node->asPaint(local_ctx, &p)) {
   *             // Use the fallback color.
   *             p.setColor(this->resolveSvgColor(paint_selector.color()));
   *         }
   *     } break;
   *     default:
   *         SkUNREACHABLE;
   *     }
   *
   *     p.setAntiAlias(true); // TODO: shape-rendering support
   *
   *     // We observe 3 opacity components:
   *     //   - initial paint server opacity (e.g. color stop opacity)
   *     //   - paint-specific opacity (e.g. 'fill-opacity', 'stroke-opacity')
   *     //   - deferred opacity override (optimization for leaf nodes 'opacity')
   *     p.setAlphaf(SkTPin(p.getAlphaf() * paint_opacity * fDeferredPaintOpacity, 0.0f, 1.0f));
   *
   *     return p;
   * }
   * ```
   */
  private fun commonPaint(paintSelector: SkSVGPaint, opacity: Float): Int {
    TODO("Implement commonPaint")
  }

  public data class OBBScope public constructor(
    public val fNode: Int?,
    public val fCtx: SkSVGRenderContext?,
  )

  public data class BorrowedNode public constructor(
    private var fOwner: Int?,
    private var fBorrowed: Int,
  ) {
    private fun assign(param0: undefined.BorrowedNode) {
      TODO("Implement assign")
    }
  }

  public data class OBBTransform public constructor(
    public var offset: Int,
    public var scale: Int,
  )

  public enum class ApplyFlags {
    kLeaf,
  }
}
