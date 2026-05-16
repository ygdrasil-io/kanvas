package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkCachedData
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSpan
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkMaskFilterBase : public SkMaskFilter {
 * public:
 *     /** Returns the format of the resulting mask that this subclass will return
 *         when its filterMask() method is called.
 *     */
 *     virtual SkMask::Format getFormat() const = 0;
 *
 *     /** Create a new mask by filter the src mask.
 *         If src.fImage == null, then do not allocate or create the dst image
 *         but do fill out the other fields in dstMask.
 *         If you do allocate a dst image, use SkMask::AllocImage()
 *         If this returns false, dst mask is ignored.
 *         @param  dst the result of the filter. If src.fImage == null, dst should not allocate its image
 *         @param src the original image to be filtered.
 *         @param matrix the CTM
 *         @param margin   if not null, return the buffer dx/dy need when calculating the effect. Used when
 *                         drawing a clipped object to know how much larger to allocate the src before
 *                         applying the filter. If returning false, ignore this parameter.
 *         @return true if the dst mask was correctly created.
 *     */
 *     virtual bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
 *                             SkIPoint* margin) const = 0;
 *
 *     enum class Type {
 *         kBlur,
 *         kEmboss,
 *         kSDF,
 *         kShader,
 *         kTable,
 *     };
 *
 *     virtual Type type() const = 0;
 *
 *     /**
 *      * The fast bounds function is used to enable the paint to be culled early
 *      * in the drawing pipeline. This function accepts the current bounds of the
 *      * paint as its src param and the filter adjust those bounds using its
 *      * current mask and returns the result using the dest param. Callers are
 *      * allowed to provide the same struct for both src and dest so each
 *      * implementation must accommodate that behavior.
 *      *
 *      *  The default impl calls filterMask with the src mask having no image,
 *      *  but subclasses may override this if they can compute the rect faster.
 *      */
 *     virtual void computeFastBounds(const SkRect& src, SkRect* dest) const;
 *
 *     struct BlurRec {
 *         SkScalar        fSigma;
 *         SkBlurStyle     fStyle;
 *     };
 *     /**
 *      *  If this filter can be represented by a BlurRec, return true and (if not null) fill in the
 *      *  provided BlurRec parameter. If this effect cannot be represented as a BlurRec, return false
 *      *  and ignore the BlurRec parameter.
 *      */
 *     virtual bool asABlur(BlurRec*) const;
 *
 *     /**
 *      * Return an SkImageFilter representation of this mask filter that SkCanvas can apply
 *      * to an alpha-only image to produce an equivalent effect to running the mask filter directly.
 *      *
 *      * Additionally, return a boolean that indicates if the image filter applies shading properties.
 *      * When restoring a layer, this affects whether to draw a rgba image or blend the coverage
 *      * mask (A8 image).
 *      *
 *      * The paint parameter can be used to apply shading. Some mask filters (e.g. EmbossMaskFilter)
 *      * may not produce correct results under these circumstances and different blend modes,
 *      * given that the coverage mask will be blended in the mask filter as image filter impl in
 *      * these cases.
 *      */
 *     virtual std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(const SkMatrix& ctm,
 *                                                                 const SkPaint& paint) const;
 *
 *     static SkFlattenable::Type GetFlattenableType() {
 *         return kSkMaskFilter_Type;
 *     }
 *
 *     SkFlattenable::Type getFlattenableType() const override {
 *         return kSkMaskFilter_Type;
 *     }
 *
 * protected:
 *     SkMaskFilterBase() {}
 *
 *     enum class FilterReturn {
 *         kFalse,
 *         kTrue,
 *         kUnimplemented,
 *     };
 *
 *     class NinePatch final : ::SkNoncopyable {
 *     public:
 *         NinePatch(const SkMask& mask, SkIRect outerRect, SkIPoint center, SkCachedData* cache)
 *             : fMask(mask), fOuterRect(outerRect), fCenter(center), fCache(cache) {}
 *         NinePatch(NinePatch&&) = delete;  // the transfer of fCache makes this not work
 *         ~NinePatch();
 *
 *         SkMask      fMask;      // fBounds must have [0,0] in its top-left
 *         SkIRect     fOuterRect; // width/height must be >= fMask.fBounds'
 *         SkIPoint    fCenter;    // identifies center row/col for stretching
 *         SkCachedData* fCache = nullptr;
 *     };
 *
 *     /**
 *      *  As an optimization, some filters can be applied to a smaller nine-patch
 *      *  instead of the full-sized rectangle. These nine-patches are not only smaller,
 *      *  but more re-usable/cacheable. Then, when drawing/blitting, the ninepatch
 *      *  can be expanded to the desired size.
 *      *
 *      *  Override if your subclass can filter a rect, and return the answer as
 *      *  a ninepatch mask to be stretched over the returned outerRect. On success
 *      *  return FilterReturn::kTrue. On failure (e.g. out of memory) return
 *      *  FilterReturn::kFalse. If the normal filterMask() entry-point should be
 *      *  called (the default) return FilterReturn::kUnimplemented.
 *      *
 *      *  By convention, the caller will take the center rol/col from the returned
 *      *  mask as the slice it can replicate horizontally and vertically as we
 *      *  stretch the mask to fit inside outerRect. It is an error for outerRect
 *      *  to be smaller than the mask's bounds. This would imply that the width
 *      *  and height of the mask should be odd. This is not required, just that
 *      *  the caller will call mask.fBounds.centerX() and centerY() to find the
 *      *  strips that will be replicated.
 *      */
 *     virtual FilterReturn filterRectsToNine(SkSpan<const SkRect>,
 *                                            const SkMatrix&,
 *                                            const SkIRect& clipBounds,
 *                                            std::optional<NinePatch>*,
 *                                            SkResourceCache*) const;
 *     /**
 *      *  Similar to filterRectsToNine, except it performs the work on a round rect.
 *      */
 *     virtual std::optional<NinePatch> filterRRectToNine(const SkRRect&,
 *                                                        const SkMatrix&,
 *                                                        const SkIRect& clipBounds,
 *                                                        SkResourceCache*) const;
 *
 * private:
 *     friend class skcpu::Draw;
 *
 *     /** Helper method that, given a raw path in device space, will rasterize it into a
 *      kA8_Format mask and then call filterMask(). If this returns true, the specified blitter
 *      will be called to render that mask. Returns false if filterMask() returned false.
 *      This method is not exported to java.
 *      */
 *     bool filterPath(const SkPathRaw& devRaw,
 *                     const SkMatrix& ctm,
 *                     const SkRasterClip&,
 *                     SkBlitter*,
 *                     SkStrokeRec::InitStyle,
 *                     SkResourceCache*) const;
 *
 *     /** Helper method that, given a roundRect in device space, will rasterize it into a kA8_Format
 *      mask and then call filterMask(). If this returns true, the specified blitter will be called
 *      to render that mask. Returns false if filterMask() returned false.
 *      */
 *     bool filterRRect(const SkRRect& devRRect,
 *                      const SkMatrix& ctm,
 *                      const SkRasterClip&,
 *                      SkBlitter*,
 *                      SkResourceCache*) const;
 *
 *     FilterReturn filterRects(SkSpan<const SkRect> devRects,
 *                      const SkMatrix& ctm,
 *                      const SkRasterClip& clip,
 *                      SkBlitter* blitter,
 *                      SkResourceCache* cache) const;
 * }
 * ```
 */
public abstract class SkMaskFilterBase public constructor() : SkMaskFilter() {
  /**
   * C++ original:
   * ```cpp
   * virtual SkMask::Format getFormat() const = 0
   * ```
   */
  public abstract fun getFormat(): SkMask.Format

  /**
   * C++ original:
   * ```cpp
   * virtual bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
   *                             SkIPoint* margin) const = 0
   * ```
   */
  public abstract fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    param2: SkMatrix,
    margin: SkIPoint?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual Type type() const = 0
   * ```
   */
  public abstract fun type(): Type

  /**
   * C++ original:
   * ```cpp
   * void SkMaskFilterBase::computeFastBounds(const SkRect& src, SkRect* dst) const {
   *     SkMask srcM(nullptr, src.roundOut(), 0, SkMask::kA8_Format);
   *     SkMaskBuilder dstM;
   *
   *     SkIPoint margin;    // ignored
   *     if (this->filterMask(&dstM, srcM, SkMatrix::I(), &margin)) {
   *         dst->set(dstM.fBounds);
   *     } else {
   *         dst->set(srcM.fBounds);
   *     }
   * }
   * ```
   */
  public open fun computeFastBounds(src: SkRect, dest: SkRect?) {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMaskFilterBase::asABlur(BlurRec*) const {
   *     return false;
   * }
   * ```
   */
  public open fun asABlur(param0: BlurRec?): Boolean {
    TODO("Implement asABlur")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkImageFilter>, bool> SkMaskFilterBase::asImageFilter(const SkMatrix& ctm,
   *                                                                       const SkPaint& paint) const {
   *     return std::make_pair(nullptr, false);
   * }
   * ```
   */
  public open fun asImageFilter(ctm: SkMatrix, paint: SkPaint): Int {
    TODO("Implement asImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override {
   *         return kSkMaskFilter_Type;
   *     }
   * ```
   */
  public override fun getFlattenableType(): SkFlattenable.Type {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::FilterReturn SkMaskFilterBase::filterRectsToNine(SkSpan<const SkRect>,
   *                                                                    const SkMatrix&,
   *                                                                    const SkIRect&,
   *                                                                    std::optional<NinePatch>*,
   *                                                                    SkResourceCache*) const {
   *     return FilterReturn::kUnimplemented;
   * }
   * ```
   */
  public open fun filterRectsToNine(
    param0: SkSpan<SkRect>,
    param1: SkMatrix,
    clipBounds: SkIRect,
    param3: undefined.NinePatch?,
    param4: SkResourceCache?,
  ): FilterReturn {
    TODO("Implement filterRectsToNine")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMaskFilterBase::NinePatch> SkMaskFilterBase::filterRRectToNine(
   *         const SkRRect&, const SkMatrix&, const SkIRect&, SkResourceCache*) const {
   *     return std::nullopt;
   * }
   * ```
   */
  public open fun filterRRectToNine(
    param0: SkRRect,
    param1: SkMatrix,
    clipBounds: SkIRect,
    param3: SkResourceCache?,
  ): Int {
    TODO("Implement filterRRectToNine")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMaskFilterBase::filterPath(const SkPathRaw& devRaw,
   *                                   const SkMatrix& matrix,
   *                                   const SkRasterClip& clip,
   *                                   SkBlitter* blitter,
   *                                   SkStrokeRec::InitStyle style,
   *                                   SkResourceCache* cache) const {
   *     SkRect rects[2];
   *     int rectCount = 0;
   *     if (SkStrokeRec::kFill_InitStyle == style) {
   *         rectCount = countNestedRects(devRaw, rects);
   *     }
   *     if (rectCount > 0) {
   *         switch (this->filterRects(SkSpan(rects, rectCount), matrix, clip, blitter, cache)) {
   *             case FilterReturn::kFalse:
   *                 return false;
   *             case FilterReturn::kTrue:
   *                 return true;
   *             case FilterReturn::kUnimplemented:
   *                 break;
   *         }
   *     }
   *
   *     SkMaskBuilder srcM, dstM;
   *
   * #if defined(SK_BUILD_FOR_FUZZER)
   *     if (devRaw.verbs().size() > 1000 || devRaw.points().size() > 1000) {
   *         return false;
   *     }
   * #endif
   *     if (!skcpu::DrawToMask(devRaw,
   *                            clip.getBounds(),
   *                            this,
   *                            &matrix,
   *                            &srcM,
   *                            SkMaskBuilder::kComputeBoundsAndRenderImage_CreateMode,
   *                            style)) {
   *         return false;
   *     }
   *     SkAutoMaskFreeImage autoSrc(srcM.image());
   *
   *     if (!this->filterMask(&dstM, srcM, matrix, nullptr)) {
   *         return false;
   *     }
   *     SkAutoMaskFreeImage autoDst(dstM.image());
   *
   *     // if we get here, we need to (possibly) resolve the clip and blitter
   *     SkAAClipBlitterWrapper wrapper(clip, blitter);
   *     blitter = wrapper.getBlitter();
   *
   *     SkRegion::Cliperator clipper(wrapper.getRgn(), dstM.fBounds);
   *
   *     if (!clipper.done()) {
   *         const SkIRect& cr = clipper.rect();
   *         do {
   *             blitter->blitMask(dstM, cr);
   *             clipper.next();
   *         } while (!clipper.done());
   *     }
   *
   *     return true;
   * }
   * ```
   */
  private fun filterPath(
    devRaw: SkPathRaw,
    ctm: SkMatrix,
    clip: SkRasterClip,
    blitter: SkBlitter?,
    style: SkStrokeRec.InitStyle,
    cache: SkResourceCache?,
  ): Boolean {
    TODO("Implement filterPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMaskFilterBase::filterRRect(const SkRRect& devRRect,
   *                                    const SkMatrix& matrix,
   *                                    const SkRasterClip& clip,
   *                                    SkBlitter* blitter,
   *                                    SkResourceCache* cache) const {
   *     // Attempt to speed up drawing by creating a nine patch. If a nine patch
   *     // cannot be used, return false to allow our caller to recover and perform
   *     // the drawing another way.
   *     std::optional<NinePatch> patch =
   *             this->filterRRectToNine(devRRect, matrix, clip.getBounds(), cache);
   *
   *     if (!patch.has_value()) {
   *         return false;
   *     }
   *     draw_nine(patch->fMask, patch->fOuterRect, patch->fCenter, true, clip, blitter);
   *     return true;
   * }
   * ```
   */
  private fun filterRRect(
    devRRect: SkRRect,
    ctm: SkMatrix,
    clip: SkRasterClip,
    blitter: SkBlitter?,
    cache: SkResourceCache?,
  ): Boolean {
    TODO("Implement filterRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::FilterReturn SkMaskFilterBase::filterRects(SkSpan<const SkRect> devRects,
   *                                                              const SkMatrix& matrix,
   *                                                              const SkRasterClip& clip,
   *                                                              SkBlitter* blitter,
   *                                                              SkResourceCache* cache) const {
   *     std::optional<NinePatch> patch;
   *
   *     FilterReturn filterReturn = this->filterRectsToNine(
   *         devRects, matrix, clip.getBounds(), &patch, cache);
   *     switch (filterReturn) {
   *         case FilterReturn::kFalse:
   *             SkASSERT(!patch.has_value());
   *             break;
   *
   *         case FilterReturn::kTrue:
   *             draw_nine(patch->fMask, patch->fOuterRect, patch->fCenter, 1 == devRects.size(), clip,
   *                       blitter);
   *             break;
   *
   *         case FilterReturn::kUnimplemented:
   *             SkASSERT(!patch.has_value());
   *             // fall out
   *             break;
   *     }
   *     return filterReturn;
   * }
   * ```
   */
  private fun filterRects(
    devRects: SkSpan<SkRect>,
    ctm: SkMatrix,
    clip: SkRasterClip,
    blitter: SkBlitter?,
    cache: SkResourceCache?,
  ): FilterReturn {
    TODO("Implement filterRects")
  }

  public data class BlurRec public constructor(
    public var fSigma: SkScalar,
    public var fStyle: SkBlurStyle,
  )

  public class NinePatch public constructor(
    mask: SkMask,
    outerRect: SkIRect,
    center: SkIPoint,
    cache: SkCachedData?,
  ) : SkNoncopyable() {
    public var fMask: SkMask = TODO("Initialize fMask")

    public var fOuterRect: SkIRect = TODO("Initialize fOuterRect")

    public var fCenter: SkIPoint = TODO("Initialize fCenter")

    public var fCache: SkCachedData? = TODO("Initialize fCache")

    public constructor(param0: undefined.NinePatch) : this() {
      TODO("Implement constructor")
    }
  }

  public enum class Type {
    kBlur,
    kEmboss,
    kSDF,
    kShader,
    kTable,
  }

  public enum class FilterReturn {
    kFalse,
    kTrue,
    kUnimplemented,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() {
     *         return kSkMaskFilter_Type;
     *     }
     * ```
     */
    public fun getFlattenableType(): SkFlattenable.Type {
      TODO("Implement getFlattenableType")
    }
  }
}
