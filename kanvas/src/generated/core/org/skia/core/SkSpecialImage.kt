package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkSpecialImage : public SkRefCnt {
 * public:
 *     typedef void* ReleaseContext;
 *     typedef void(*RasterReleaseProc)(void* pixels, ReleaseContext);
 *
 *     const SkSurfaceProps& props() const { return fProps; }
 *
 *     int width() const { return fSubset.width(); }
 *     int height() const { return fSubset.height(); }
 *     SkISize dimensions() const { return { this->width(), this->height() }; }
 *     const SkIRect& subset() const { return fSubset; }
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     virtual SkISize backingStoreDimensions() const = 0;
 *
 *     virtual size_t getSize() const = 0;
 *
 *     bool isExactFit() const { return fSubset == SkIRect::MakeSize(this->backingStoreDimensions()); }
 *
 *     const SkColorInfo& colorInfo() const { return fColorInfo; }
 *     SkAlphaType alphaType() const { return fColorInfo.alphaType(); }
 *     SkColorType colorType() const { return fColorInfo.colorType(); }
 *     SkColorSpace* getColorSpace() const { return fColorInfo.colorSpace(); }
 *
 *     /**
 *      *  Draw this SpecialImage into the canvas, automatically taking into account the image's subset
 *      */
 *     void draw(SkCanvas* canvas,
 *               SkScalar x, SkScalar y,
 *               const SkSamplingOptions& sampling,
 *               const SkPaint* paint,
 *               bool strict = true) const;
 *     void draw(SkCanvas* canvas, SkScalar x, SkScalar y) const {
 *         this->draw(canvas, x, y, SkSamplingOptions(), nullptr);
 *     }
 *
 *     /**
 *      * Extract a subset of this special image and return it as a special image.
 *      * It may or may not point to the same backing memory. The input 'subset' is relative to the
 *      * special image's content rect.
 *      */
 *     sk_sp<SkSpecialImage> makeSubset(const SkIRect& subset) const {
 *         SkIRect absolute = subset.makeOffset(this->subset().topLeft());
 *         return this->onMakeBackingStoreSubset(absolute);
 *     }
 *
 *     /**
 *      * Return a special image with a 1px larger subset in the backing store compared to this image.
 *      * This should only be used when it's externally known that those outer pixels are valid.
 *      */
 *     sk_sp<SkSpecialImage> makePixelOutset() const {
 *         return this->onMakeBackingStoreSubset(this->subset().makeOutset(1, 1));
 *     }
 *
 *     /**
 *      * Create an SkImage view of the contents of this special image, pointing to the same
 *      * underlying memory.
 *      *
 *      * TODO: If SkImages::MakeFiltered were to return an SkShader that accounted for the subset
 *      * constraint and offset, then this could move to a private virtual for use in draw() and
 *      * asShader().
 *      */
 *     virtual sk_sp<SkImage> asImage() const = 0;
 *
 *     /**
 *      * Create an SkShader that samples the contents of this special image, applying tile mode for
 *      * any sample that falls outside its internal subset.
 *      *
 *      * 'strict' defaults to true and applies shader-based tiling to the subset. If the subset is
 *      * the same as the backing store dimensions, it is automatically degraded to non-strict
 *      * (HW tiling and sampling). 'strict' can be set to false if it's known that the subset
 *      * boundaries aren't visible AND the texel data in adjacent rows/cols is valid to be included
 *      * by the given sampling options.
 *      */
 *     virtual sk_sp<SkShader> asShader(SkTileMode,
 *                                      const SkSamplingOptions&,
 *                                      const SkMatrix& lm,
 *                                      bool strict=true) const;
 *
 *     /**
 *      *  If the SpecialImage is backed by a gpu texture, return true.
 *      */
 *     virtual bool isGaneshBacked() const { return false; }
 *     virtual bool isGraphiteBacked() const { return false; }
 *
 *     /**
 *      * Return the GrRecordingContext if the SkSpecialImage is GrTexture-backed
 *      */
 *     virtual GrRecordingContext* getContext() const { return nullptr; }
 *
 * protected:
 *     SkSpecialImage(const SkIRect& subset,
 *                    uint32_t uniqueID,
 *                    const SkColorInfo&,
 *                    const SkSurfaceProps&);
 *
 *     // This subset is relative to the backing store's coordinate frame, it has already been mapped
 *     // from the content rect by the non-virtual makeSubset(). The provided 'subset' is not
 *     // necessarily contained within this special image's subset.
 *     virtual sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const = 0;
 *
 * private:
 *     const SkIRect        fSubset;
 *     const uint32_t       fUniqueID;
 *     const SkColorInfo    fColorInfo;
 *     const SkSurfaceProps fProps;
 * }
 * ```
 */
public abstract class SkSpecialImage public constructor(
  subset: SkIRect,
  uniqueID: UInt,
  colorInfo: SkColorInfo,
  props: SkSurfaceProps,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const SkIRect        fSubset
   * ```
   */
  private val fSubset: SkIRect = TODO("Initialize fSubset")

  /**
   * C++ original:
   * ```cpp
   * const uint32_t       fUniqueID
   * ```
   */
  private val fUniqueID: Int = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * const SkColorInfo    fColorInfo
   * ```
   */
  private val fColorInfo: SkColorInfo = TODO("Initialize fColorInfo")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps fProps
   * ```
   */
  private val fProps: SkSurfaceProps = TODO("Initialize fProps")

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps& props() const { return fProps; }
   * ```
   */
  public fun props(): SkSurfaceProps {
    TODO("Implement props")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fSubset.width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fSubset.height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return { this->width(), this->height() }; }
   * ```
   */
  public fun dimensions(): SkISize {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& subset() const { return fSubset; }
   * ```
   */
  public fun subset(): SkIRect {
    TODO("Implement subset")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkISize backingStoreDimensions() const = 0
   * ```
   */
  public abstract fun backingStoreDimensions(): SkISize

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getSize() const = 0
   * ```
   */
  public abstract fun getSize(): Int

  /**
   * C++ original:
   * ```cpp
   * bool isExactFit() const { return fSubset == SkIRect::MakeSize(this->backingStoreDimensions()); }
   * ```
   */
  public fun isExactFit(): Boolean {
    TODO("Implement isExactFit")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorInfo& colorInfo() const { return fColorInfo; }
   * ```
   */
  public fun colorInfo(): SkColorInfo {
    TODO("Implement colorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType alphaType() const { return fColorInfo.alphaType(); }
   * ```
   */
  public fun alphaType(): SkAlphaType {
    TODO("Implement alphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fColorInfo.colorType(); }
   * ```
   */
  public fun colorType(): SkColorType {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* getColorSpace() const { return fColorInfo.colorSpace(); }
   * ```
   */
  public fun getColorSpace(): SkColorSpace {
    TODO("Implement getColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSpecialImage::draw(SkCanvas* canvas,
   *                           SkScalar x, SkScalar y,
   *                           const SkSamplingOptions& sampling,
   *                           const SkPaint* paint, bool strict) const {
   *     SkRect dst = SkRect::MakeXYWH(x, y, this->subset().width(), this->subset().height());
   *
   *     canvas->drawImageRect(this->asImage(), SkRect::Make(this->subset()), dst,
   *                           sampling, paint, strict ? SkCanvas::kStrict_SrcRectConstraint
   *                                                   : SkCanvas::kFast_SrcRectConstraint);
   * }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
    sampling: SkSamplingOptions,
    paint: SkPaint?,
    strict: Boolean = TODO(),
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(SkCanvas* canvas, SkScalar x, SkScalar y) const {
   *         this->draw(canvas, x, y, SkSamplingOptions(), nullptr);
   *     }
   * ```
   */
  public fun draw(
    canvas: SkCanvas?,
    x: SkScalar,
    y: SkScalar,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> makeSubset(const SkIRect& subset) const {
   *         SkIRect absolute = subset.makeOffset(this->subset().topLeft());
   *         return this->onMakeBackingStoreSubset(absolute);
   *     }
   * ```
   */
  public fun makeSubset(subset: SkIRect): SkSp<SkSpecialImage> {
    TODO("Implement makeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> makePixelOutset() const {
   *         return this->onMakeBackingStoreSubset(this->subset().makeOutset(1, 1));
   *     }
   * ```
   */
  public fun makePixelOutset(): SkSp<SkSpecialImage> {
    TODO("Implement makePixelOutset")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImage> asImage() const = 0
   * ```
   */
  public abstract fun asImage(): SkSp<SkImage>

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkSpecialImage::asShader(SkTileMode tileMode,
   *                                          const SkSamplingOptions& sampling,
   *                                          const SkMatrix& lm,
   *                                          bool strict) const {
   *     // The special image's logical (0,0) is at its subset's topLeft() so we need to account for
   *     // that in the local matrix used when sampling.
   *     SkMatrix subsetOrigin = SkMatrix::Translate(-this->subset().topLeft());
   *     subsetOrigin.postConcat(lm);
   *
   *     if (strict) {
   *         // However, we don't need to modify the subset itself since that is defined with respect
   *         // to the base image, and the local matrix is applied before any tiling/clamping.
   *         const SkRect subset = SkRect::Make(this->subset());
   *
   *         // asImage() w/o a subset makes no copy; create the SkImageShader directly to remember
   *         // the subset used to access the image.
   *         return SkImageShader::MakeSubset(
   *                 this->asImage(), subset, tileMode, tileMode, sampling, &subsetOrigin);
   *     } else {
   *         // Ignore 'subset' other than its origin translation applied to the local matrix.
   *         return this->asImage()->makeShader(tileMode, tileMode, sampling, subsetOrigin);
   *     }
   * }
   * ```
   */
  public open fun asShader(
    tileMode: SkTileMode,
    sampling: SkSamplingOptions,
    lm: SkMatrix,
    strict: Boolean = TODO(),
  ): SkSp<SkShader> {
    TODO("Implement asShader")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isGaneshBacked() const { return false; }
   * ```
   */
  public open fun isGaneshBacked(): Boolean {
    TODO("Implement isGaneshBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isGraphiteBacked() const { return false; }
   * ```
   */
  public open fun isGraphiteBacked(): Boolean {
    TODO("Implement isGraphiteBacked")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrRecordingContext* getContext() const { return nullptr; }
   * ```
   */
  public open fun getContext(): GrRecordingContext {
    TODO("Implement getContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const = 0
   * ```
   */
  protected abstract fun onMakeBackingStoreSubset(subset: SkIRect): SkSp<SkSpecialImage>
}
