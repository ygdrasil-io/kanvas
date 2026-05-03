package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import undefined.BlurRec
import undefined.FilterReturn
import undefined.NinePatch

/**
 * C++ original:
 * ```cpp
 * template <typename T> class sk_sp;
 *
 * class SkBlurMaskFilterImpl : public SkMaskFilterBase {
 * public:
 *     SkBlurMaskFilterImpl(SkScalar sigma, SkBlurStyle, bool respectCTM);
 *
 *     // From SkMaskFilterBase.h
 *     SkMask::Format getFormat() const override;
 *     bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
 *                     SkIPoint* margin) const override;
 *     SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kBlur; }
 *
 *     void computeFastBounds(const SkRect&, SkRect*) const override;
 *     bool asABlur(BlurRec*) const override;
 *     std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(const SkMatrix& ctm,
 *                                                         const SkPaint&) const override;
 *
 *     SkScalar computeXformedSigma(const SkMatrix& ctm) const;
 *     SkBlurStyle blurStyle() const {return fBlurStyle;}
 *     SkScalar sigma() const {return fSigma;}
 *     bool ignoreXform() const { return !fRespectCTM; }
 *
 * private:
 *     FilterReturn filterRectsToNine(SkSpan<const SkRect>,
 *                                    const SkMatrix&,
 *                                    const SkIRect& clipBounds,
 *                                    std::optional<NinePatch>*,
 *                                    SkResourceCache*) const override;
 *
 *     std::optional<NinePatch> filterRRectToNine(const SkRRect&,
 *                                                const SkMatrix&,
 *                                                const SkIRect& clipBounds,
 *                                                SkResourceCache*) const override;
 *
 *     bool filterRectMask(SkMaskBuilder* dstM,
 *                         const SkRect& r,
 *                         const SkMatrix& matrix,
 *                         SkIPoint* margin,
 *                         SkMaskBuilder::CreateMode createMode) const;
 *
 *     SK_FLATTENABLE_HOOKS(SkBlurMaskFilterImpl)
 *
 *     SkScalar    fSigma;
 *     SkBlurStyle fBlurStyle;
 *     bool        fRespectCTM;
 *
 *     SkBlurMaskFilterImpl(SkReadBuffer&);
 *     void flatten(SkWriteBuffer&) const override;
 *
 *     friend class SkBlurMaskFilter;
 *
 *     friend void sk_register_blur_maskfilter_createproc();
 * }
 * ```
 */
public open class SkBlurMaskFilterImpl public constructor(
  sigma: SkScalar,
  style: SkBlurStyle,
  respectCTM: Boolean,
) : SkMaskFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fSigma
   * ```
   */
  private var fSigma: SkScalar = TODO("Initialize fSigma")

  /**
   * C++ original:
   * ```cpp
   * SkBlurStyle fBlurStyle
   * ```
   */
  private var fBlurStyle: SkBlurStyle = TODO("Initialize fBlurStyle")

  /**
   * C++ original:
   * ```cpp
   * bool        fRespectCTM
   * ```
   */
  private var fRespectCTM: Boolean = TODO("Initialize fRespectCTM")

  /**
   * C++ original:
   * ```cpp
   * SkBlurMaskFilterImpl::SkBlurMaskFilterImpl(SkScalar sigma, SkBlurStyle style, bool respectCTM)
   *     : fSigma(sigma)
   *     , fBlurStyle(style)
   *     , fRespectCTM(respectCTM) {
   *     SkASSERT(fSigma > 0);
   *     SkASSERT((unsigned)style <= kLastEnum_SkBlurStyle);
   * }
   * ```
   */
  public constructor(param0: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format SkBlurMaskFilterImpl::getFormat() const {
   *     return SkMask::kA8_Format;
   * }
   * ```
   */
  public override fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlurMaskFilterImpl::filterMask(SkMaskBuilder* dst, const SkMask& src,
   *                                       const SkMatrix& matrix,
   *                                       SkIPoint* margin) const {
   *     SkScalar sigma = this->computeXformedSigma(matrix);
   *     return SkBlurMask::BoxBlur(dst, src, sigma, fBlurStyle, margin);
   * }
   * ```
   */
  public override fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    matrix: SkMatrix,
    margin: SkIPoint?,
  ): Boolean {
    TODO("Implement filterMask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kBlur; }
   * ```
   */
  public override fun type(): SkMaskFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlurMaskFilterImpl::computeFastBounds(const SkRect& src,
   *                                              SkRect* dst) const {
   *     // TODO: if we're doing kInner blur, should we return a different outset?
   *     //       i.e. pad == 0 ?
   *
   *     SkScalar pad = 3.0f * fSigma;
   *
   *     dst->setLTRB(src.fLeft  - pad, src.fTop    - pad,
   *                  src.fRight + pad, src.fBottom + pad);
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect, dst: SkRect?) {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlurMaskFilterImpl::asABlur(BlurRec* rec) const {
   *     if (this->ignoreXform()) {
   *         return false;
   *     }
   *
   *     if (rec) {
   *         rec->fSigma = fSigma;
   *         rec->fStyle = fBlurStyle;
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun asABlur(rec: BlurRec?): Boolean {
    TODO("Implement asABlur")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkImageFilter>, bool> SkBlurMaskFilterImpl::asImageFilter(const SkMatrix& ctm,
   *                                                                           const SkPaint&) const {
   *     // Mask filters apply a uniform blur in either local or device space. Depending on the scale
   *     // factors of the `ctm`, the actual blur radii can end up non-uniform.
   *     SkV2 sigma = {fSigma, fSigma};
   *     if (this->ignoreXform()) {
   *         // This is analogous to computeXformedSigma(), but it might be more correct to wrap the
   *         // blur image filter in a local matrix with ctm^-1, or to control the skif::Mapping when
   *         // the mask filter layer is restored. It calculates new blur radii such that transforming
   *         // these to the layer space of the image filter will match the original device-space fSigma.
   *         // This can be inaccurate when 'ctm' has skew or perspective. A full fix requires layers
   *         // having flexible operating coordinate spaces (e.g. parent or root canvas).
   *         const float xScaleFactor = fSigma / ctm.mapVector(fSigma, 0.f).length();
   *         const float yScaleFactor = fSigma / ctm.mapVector(0.f, fSigma).length();
   *         sigma = {fSigma * xScaleFactor, fSigma * yScaleFactor};
   *     }
   *
   *     // The null input image filter will be bound to the original coverage mask.
   *     sk_sp<SkImageFilter> filter = SkImageFilters::Blur(sigma.x, sigma.y, nullptr);
   *     // Combine the original coverage mask (src) and the blurred coverage mask (dst)
   *     switch(fBlurStyle) {
   *         case kInner_SkBlurStyle: //  dst = dst * src
   *                                  //      = 0 * src + src * dst
   *             return {SkImageFilters::Blend(SkBlendMode::kDstIn, std::move(filter), nullptr), false};
   *         case kSolid_SkBlurStyle: //  dst = src + dst - src * dst
   *                                  //      = 1 * src + (1 - src) * dst
   *             return {SkImageFilters::Blend(SkBlendMode::kSrcOver, std::move(filter), nullptr), false};
   *         case kOuter_SkBlurStyle: //  dst = dst * (1 - src)
   *                                  //      = 0 * src + (1 - src) * dst
   *             return {SkImageFilters::Blend(SkBlendMode::kDstOut, std::move(filter), nullptr), false};
   *         case kNormal_SkBlurStyle:
   *             return {filter, false};
   *     }
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public override fun asImageFilter(ctm: SkMatrix, param1: SkPaint): Int {
    TODO("Implement asImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar SkBlurMaskFilterImpl::computeXformedSigma(const SkMatrix& ctm) const {
   *     constexpr SkScalar kMaxBlurSigma = SkIntToScalar(128);
   *     SkScalar xformedSigma = this->ignoreXform() ? fSigma : ctm.mapRadius(fSigma);
   *     return std::min(xformedSigma, kMaxBlurSigma);
   * }
   * ```
   */
  public fun computeXformedSigma(ctm: SkMatrix): SkScalar {
    TODO("Implement computeXformedSigma")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlurStyle blurStyle() const {return fBlurStyle;}
   * ```
   */
  public fun blurStyle(): SkBlurStyle {
    TODO("Implement blurStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar sigma() const {return fSigma;}
   * ```
   */
  public fun sigma(): SkScalar {
    TODO("Implement sigma")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ignoreXform() const { return !fRespectCTM; }
   * ```
   */
  public fun ignoreXform(): Boolean {
    TODO("Implement ignoreXform")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::FilterReturn SkBlurMaskFilterImpl::filterRectsToNine(
   *         SkSpan<const SkRect> rects,
   *         const SkMatrix& matrix,
   *         const SkIRect& clipBounds,
   *         std::optional<NinePatch>* patch,
   *         SkResourceCache* cache) const {
   *     SkASSERT(patch != nullptr);
   *     SkASSERT(rects.size() == 1 || rects.size() == 2);
   *
   *     // TODO: report correct metrics for innerstyle, where we do not grow the
   *     // total bounds, but we do need an inset the size of our blur-radius
   *     if (kInner_SkBlurStyle == fBlurStyle || kOuter_SkBlurStyle == fBlurStyle) {
   *         return FilterReturn::kUnimplemented;
   *     }
   *
   *     // TODO: take clipBounds into account to limit our coordinates up front
   *     // for now, just skip too-large src rects (to take the old code path).
   *     if (rect_exceeds(rects[0], SkIntToScalar(32767))) {
   *         return FilterReturn::kUnimplemented;
   *     }
   *
   *     SkIPoint margin;
   *     SkMaskBuilder srcM(nullptr, rects[0].roundOut(), 0, SkMask::kA8_Format), dstM;
   *
   *     bool filterResult = false;
   *     if (rects.size() == 1) {
   *         // special case for fast rect blur
   *         // don't actually do the blur the first time, just compute the correct size
   *         filterResult = this->filterRectMask(&dstM, rects[0], matrix, &margin,
   *                                             SkMaskBuilder::kJustComputeBounds_CreateMode);
   *     } else {
   *         filterResult = this->filterMask(&dstM, srcM, matrix, &margin);
   *     }
   *
   *     if (!filterResult) {
   *         return FilterReturn::kFalse;
   *     }
   *
   *     /*
   *      *  smallR is the smallest version of 'rect' that will still guarantee that
   *      *  we get the same blur results on all edges, plus 1 center row/col that is
   *      *  representative of the extendible/stretchable edges of the ninepatch.
   *      *  Since our actual edge may be fractional we inset 1 more to be sure we
   *      *  don't miss any interior blur.
   *      *  x is an added pixel of blur, and { and } are the (fractional) edge
   *      *  pixels from the original rect.
   *      *
   *      *   x x { x x .... x x } x x
   *      *
   *      *  Thus, in this case, we inset by a total of 5 (on each side) beginning
   *      *  with our outer-rect (dstM.fBounds)
   *      */
   *     SkRect smallR[2];
   *     int rectCount;
   *     SkIPoint center;
   *
   *     // +2 is from +1 for each edge (to account for possible fractional edges
   *     int smallW = dstM.fBounds.width() - srcM.fBounds.width() + 2;
   *     int smallH = dstM.fBounds.height() - srcM.fBounds.height() + 2;
   *     SkIRect innerIR;
   *
   *     if (rects.size() == 1) {
   *         rectCount = 1;
   *         innerIR = srcM.fBounds;
   *         center.set(smallW, smallH);
   *     } else {
   *         rectCount = 2;
   *         rects[1].roundIn(&innerIR);
   *         center.set(smallW + (innerIR.left() - srcM.fBounds.left()),
   *                    smallH + (innerIR.top() - srcM.fBounds.top()));
   *     }
   *
   *     // +1 so we get a clean, stretchable, center row/col
   *     smallW += 1;
   *     smallH += 1;
   *
   *     // we want the inset amounts to be integral, so we don't change any
   *     // fractional phase on the fRight or fBottom of our smallR.
   *     const SkScalar dx = SkIntToScalar(innerIR.width() - smallW);
   *     const SkScalar dy = SkIntToScalar(innerIR.height() - smallH);
   *     if (dx < 0 || dy < 0) {
   *         // we're too small, relative to our blur, to break into nine-patch,
   *         // so we ask to have our normal filterMask() be called.
   *         return FilterReturn::kUnimplemented;
   *     }
   *
   *     smallR[0].setLTRB(rects[0].left(),       rects[0].top(),
   *                       rects[0].right() - dx, rects[0].bottom() - dy);
   *     if (smallR[0].width() < 2 || smallR[0].height() < 2) {
   *         return FilterReturn::kUnimplemented;
   *     }
   *     if (rectCount == 2) {
   *         smallR[1].setLTRB(rects[1].left(), rects[1].top(),
   *                           rects[1].right() - dx, rects[1].bottom() - dy);
   *         SkASSERT(!smallR[1].isEmpty());
   *     }
   *
   *     const SkScalar sigma = this->computeXformedSigma(matrix);
   *     SkTLazy<SkMask> cachedMask;
   *     SkSpan<const SkRect> smallRects = SkSpan(smallR, rectCount);
   *     SkCachedData* cached = find_cached_rects(&cachedMask, sigma, fBlurStyle, smallRects, cache);
   *     if (!cached) {
   *         SkMaskBuilder filterM;
   *         if (rectCount == 2) {
   *             if (!draw_rects_into_mask(smallRects, &srcM)) {
   *                 return FilterReturn::kFalse;
   *             }
   *
   *             SkAutoMaskFreeImage amf(srcM.image());
   *
   *             if (!this->filterMask(&filterM, srcM, matrix, nullptr)) {
   *                 return FilterReturn::kFalse;
   *             }
   *         } else {
   *             if (!this->filterRectMask(&filterM, smallR[0], matrix, nullptr,
   *                                       SkMaskBuilder::kComputeBoundsAndRenderImage_CreateMode)) {
   *                 return FilterReturn::kFalse;
   *             }
   *         }
   *         cached = add_cached_rects(&filterM, sigma, fBlurStyle, smallRects, cache);
   *         cachedMask.init(filterM);
   *     }
   *     SkIRect bounds = cachedMask->fBounds;
   *     bounds.offsetTo(0, 0);
   *     patch->emplace(SkMask{cachedMask->fImage, bounds, cachedMask->fRowBytes, cachedMask->fFormat},
   *                    dstM.fBounds,
   *                    center,
   *                    cached);  // transfer ownership to patch
   *     return FilterReturn::kTrue;
   * }
   * ```
   */
  public override fun filterRectsToNine(
    rects: SkSpan<SkRect>,
    matrix: SkMatrix,
    clipBounds: SkIRect,
    patch: NinePatch?,
    cache: SkResourceCache?,
  ): FilterReturn {
    TODO("Implement filterRectsToNine")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMaskFilterBase::NinePatch> SkBlurMaskFilterImpl::filterRRectToNine(
   *         const SkRRect& rrect,
   *         const SkMatrix& matrix,
   *         const SkIRect& clipBounds,
   *         SkResourceCache* cache) const {
   *     switch (rrect.getType()) {
   *         case SkRRect::kEmpty_Type:
   *             // Nothing to draw.
   *             return std::nullopt;
   *
   *         case SkRRect::kRect_Type:
   *             // We should have caught this earlier.
   *             SkDEBUGFAIL("Should use a different special case");
   *             return std::nullopt;
   *         case SkRRect::kOval_Type:
   *             // The nine patch special case does not handle ovals, and we
   *             // already have code for rectangles.
   *             return std::nullopt;
   *
   *         // These three can take advantage of this fast path.
   *         case SkRRect::kSimple_Type:
   *         case SkRRect::kNinePatch_Type:
   *         case SkRRect::kComplex_Type:
   *             break;
   *     }
   *
   *     // TODO: report correct metrics for innerstyle, where we do not grow the
   *     // total bounds, but we do need an inset the size of our blur-radius
   *     if (kInner_SkBlurStyle == fBlurStyle) {
   *         return std::nullopt;
   *     }
   *
   *     // TODO: take clipBounds into account to limit our coordinates up front
   *     // for now, just skip too-large src rects (to take the old code path).
   *     if (rect_exceeds(rrect.rect(), SkIntToScalar(32767))) {
   *         return std::nullopt;
   *     }
   *
   *     // To better understand the following code, consider the concrete example
   *     // where the passed in rrect is a 16x12 rounded rectangle with (rX,rY)
   *     // being (2.5, 2.0) and the sigma is 0.5 (which was turned into matrix).
   *     // The full non-blurred rrect would look something like this (intensity
   *     // ranges from 0-F and each letter is one pixel in the bitmap).
   *     // 4BFFFFFFFFFFFFB4
   *     // CFFFFFFFFFFFFFFC
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // FFFFFFFFFFFFFFFF
   *     // CFFFFFFFFFFFFFFC
   *     // 4BFFFFFFFFFFFFB4
   *
   *     // We first figure out how much we need to expand the mask (the margin) to account
   *     // for the blurred area. In the example, margin will be 1x1
   *     SkIVector margin;
   *     SkMaskBuilder srcM(nullptr, rrect.rect().roundOut(), 0, SkMask::kA8_Format), dstM;
   *     if (!this->filterMask(&dstM, srcM, matrix, &margin)) {
   *         return std::nullopt;
   *     }
   *
   *     // Most of the pixels in the center of the bitmap are the same as their neighbors, so
   *     // blurring is a waste of compute. If we made a smaller nine-patch rrect, blurred
   *     // that, we could then expand the result later, saving cycles. As a bonus, we can cache
   *     // that smaller rectangle and re-use it for other rrects with the same radii/sigma
   *     // combinations.
   *
   *     // To figure out the appropriate width and height of the nine patch rrect, we use the
   *     // larger radius per side as well as the margin, to account for inner blur. In the example,
   *     // the minimum nine patch is 9 x 7
   *     //
   *     // width: ceil(2.5) + 1 (margin) + 1 (stretch) + 1 (margin) + ceil(2.5) = 9
   *     // height: ceil(2.0) + 1 (margin) + 1 (stretch) + 1 (margin) + ceil(2.0) = 7
   *     //
   *     // 4BFF F FFB4
   *     // CFFF F FFFC
   *     // FFFF F FFFF
   *     //
   *     // FFFF F FFFF
   *     //
   *     // FFFF F FFFF
   *     // CFFF F FFFC
   *     // 4BFF F FFB4
   *     const SkVector& UL = rrect.radii(SkRRect::kUpperLeft_Corner);
   *     const SkVector& UR = rrect.radii(SkRRect::kUpperRight_Corner);
   *     const SkVector& LR = rrect.radii(SkRRect::kLowerRight_Corner);
   *     const SkVector& LL = rrect.radii(SkRRect::kLowerLeft_Corner);
   *
   *     // If there's a fractional radii, round up so that our final rrect is an integer width
   *     // to allow for symmetrical blurring across the x and y axes.
   *     const int32_t leftUnstretched  = SkScalarCeilToInt(std::max(UL.fX, LL.fX)) + margin.fX;
   *     const int32_t rightUnstretched = SkScalarCeilToInt(std::max(UR.fX, LR.fX)) + margin.fX;
   *
   *     // Extra space in the middle to ensure an unchanging piece for stretching.
   *     const int32_t stretchSize = 1;
   *
   *     const int32_t totalSmallWidth = leftUnstretched + rightUnstretched + stretchSize;
   *     if (totalSmallWidth >= rrect.rect().width()) {
   *         // There is no valid piece to stretch.
   *         return std::nullopt;
   *     }
   *
   *     const int32_t topUnstretched = SkScalarCeilToInt(std::max(UL.fY, UR.fY)) + margin.fY;
   *     const int32_t botUnstretched = SkScalarCeilToInt(std::max(LL.fY, LR.fY)) + margin.fY;
   *
   *     const int32_t totalSmallHeight = topUnstretched + botUnstretched + stretchSize;
   *     if (totalSmallHeight >= rrect.rect().height()) {
   *         // There is no valid piece to stretch.
   *         return std::nullopt;
   *     }
   *
   *     // Now make that scaled down nine patch rrect.
   *     SkRect smallR = SkRect::MakeWH(totalSmallWidth, totalSmallHeight);
   *     SkRRect smallRR;
   *     smallRR.setRectRadii(smallR, rrect.radii().data());
   *
   *     const float sigma = this->computeXformedSigma(matrix);
   *     // If we've already blurred this small rrect, pull it out of the cache and we are done
   *     SkTLazy<SkMask> cachedMask;
   *     SkCachedData* cached = find_cached_rrect(&cachedMask, sigma, fBlurStyle, smallRR, cache);
   *     if (!cached) {
   *         // Blit the small rrect into a buffer (9x7)
   *         // 4BFFFFFB4
   *         // CFFFFFFFC
   *         // FFFFFFFFF
   *         // FFFFFFFFF
   *         // FFFFFFFFF
   *         // CFFFFFFFC
   *         // 4BFFFFFB4
   *         if (!draw_rrect_into_mask(smallRR, &srcM)) {
   *             return std::nullopt;
   *         }
   *         SkAutoMaskFreeImage amf(srcM.image()); // delete small rrect's pixels when done
   *
   *         // Blur the small rrect. This will expand the mask on all sides by margin to account for
   *         // outer blur (11x9)
   *         // 00111111100
   *         // 04ADEEEDA40
   *         // 1BFFFFFFFB1
   *         // 1EFFFFFFFE1
   *         // 1EFFFFFFFE1
   *         // 1EFFFFFFFE1
   *         // 1BFFFFFFFB1
   *         // 04ADEEEDA40
   *         // 00111111100
   *         SkMaskBuilder filterM;
   *         if (!this->filterMask(&filterM, srcM, matrix, nullptr)) {
   *             return std::nullopt;
   *         }
   *         SkASSERT(filterM.fBounds.width() == (srcM.fBounds.width() + 2*margin.fX));
   *         SkASSERT(filterM.fBounds.height() == (srcM.fBounds.height() + 2*margin.fY));
   *
   *         cached = add_cached_rrect(&filterM, sigma, fBlurStyle, smallRR, cache);
   *         cachedMask.init(filterM);
   *     }
   *
   *     // The bounds of the blurred mask are at -margin, so we need to offset it back to 0,0
   *     SkIRect bounds = cachedMask->fBounds;
   *     bounds.offsetTo(0, 0);
   *     // The ninepatch could be asymmetrical (e.g. if the left rX are wider than the right), so
   *     // we must tell the caller where the center stretchy bit is in both directions. We added
   *     // margin once before to unstretched to account for inner blur, but now we add to account
   *     // for the outer blur. In the example, this will be (5, 4) [note that it's 0-indexed]
   *     SkIPoint center = SkIPoint{margin.fX + leftUnstretched,
   *                                margin.fY + topUnstretched};
   *     return std::optional<SkMaskFilterBase::NinePatch>(
   *             std::in_place,
   *             SkMask{cachedMask->fImage, bounds, cachedMask->fRowBytes, cachedMask->fFormat},
   *             dstM.fBounds,
   *             center,
   *             cached);  // transfer ownership to patch
   * }
   * ```
   */
  public override fun filterRRectToNine(
    rrect: SkRRect,
    matrix: SkMatrix,
    clipBounds: SkIRect,
    cache: SkResourceCache?,
  ): Int {
    TODO("Implement filterRRectToNine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlurMaskFilterImpl::filterRectMask(SkMaskBuilder* dst, const SkRect& r,
   *                                           const SkMatrix& matrix,
   *                                           SkIPoint* margin,
   *                                           SkMaskBuilder::CreateMode createMode) const {
   *     SkScalar sigma = computeXformedSigma(matrix);
   *
   *     return SkBlurMask::BlurRect(sigma, dst, r, fBlurStyle, margin, createMode);
   * }
   * ```
   */
  private fun filterRectMask(
    dstM: SkMaskBuilder?,
    r: SkRect,
    matrix: SkMatrix,
    margin: SkIPoint?,
    createMode: SkMaskBuilder.CreateMode,
  ): Boolean {
    TODO("Implement filterRectMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlurMaskFilterImpl::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeScalar(fSigma);
   *     buffer.writeUInt(fBlurStyle);
   *     buffer.writeUInt(!fRespectCTM); // historically we recorded ignoreCTM
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlurMaskFilterImpl::CreateProc(SkReadBuffer& buffer) {
   *     const SkScalar sigma = buffer.readScalar();
   *     SkBlurStyle style = buffer.read32LE(kLastEnum_SkBlurStyle);
   *
   *     uint32_t flags = buffer.read32LE(0x3);  // historically we only recorded 2 bits
   *     bool respectCTM = !(flags & 1); // historically we stored ignoreCTM in low bit
   *
   *     return SkMaskFilter::MakeBlur((SkBlurStyle)style, sigma, respectCTM);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
