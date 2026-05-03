package org.skia.core

import kotlin.Boolean
import kotlin.ULong
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkSpecialImage_Raster final : public SkSpecialImage {
 * public:
 *     SkSpecialImage_Raster(const SkIRect& subset, const SkBitmap& bm, const SkSurfaceProps& props)
 *             : SkSpecialImage(subset, bm.getGenerationID(), bm.info().colorInfo(), props)
 *             , fBitmap(bm) {
 *         SkASSERT(bm.pixelRef());
 *         SkASSERT(fBitmap.getPixels());
 *     }
 *
 *     bool getROPixels(SkBitmap* bm) const {
 *         return fBitmap.extractSubset(bm, this->subset());
 *     }
 *
 *     SkISize backingStoreDimensions() const override { return fBitmap.dimensions(); }
 *
 *     size_t getSize() const override { return fBitmap.computeByteSize(); }
 *
 *     sk_sp<SkImage> asImage() const override { return fBitmap.asImage(); }
 *
 *     sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const override {
 *         // No need to extract subset, onGetROPixels handles that when needed
 *         return SkSpecialImages::MakeFromRaster(subset, fBitmap, this->props());
 *     }
 *
 *     sk_sp<SkShader> asShader(SkTileMode tileMode,
 *                              const SkSamplingOptions& sampling,
 *                              const SkMatrix& lm,
 *                              bool strict) const override {
 *         if (strict) {
 *             // TODO(skbug.com/40043877): SkImage::makeShader() doesn't support a subset yet, but
 *             // SkBitmap supports subset views so create the shader from the subset bitmap instead of
 *             // fBitmap.
 *             SkBitmap subsetBM;
 *             if (!this->getROPixels(&subsetBM)) {
 *                 return nullptr;
 *             }
 *             return subsetBM.makeShader(tileMode, tileMode, sampling, lm);
 *         } else {
 *             // The special image's logical (0,0) is at its subset's topLeft() so we need to
 *             // account for that in the local matrix used when sampling.
 *             SkMatrix subsetOrigin = SkMatrix::Translate(-this->subset().topLeft());
 *             subsetOrigin.postConcat(lm);
 *             return fBitmap.makeShader(tileMode, tileMode, sampling, subsetOrigin);
 *         }
 *     }
 *
 * private:
 *     SkBitmap fBitmap;
 * }
 * ```
 */
public class SkSpecialImageRaster public constructor(
  subset: SkIRect,
  bm: SkBitmap,
  props: SkSurfaceProps,
) : SkSpecialImage(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkBitmap fBitmap
   * ```
   */
  private var fBitmap: SkBitmap = TODO("Initialize fBitmap")

  /**
   * C++ original:
   * ```cpp
   * bool getROPixels(SkBitmap* bm) const {
   *         return fBitmap.extractSubset(bm, this->subset());
   *     }
   * ```
   */
  public fun getROPixels(bm: SkBitmap?): Boolean {
    TODO("Implement getROPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize backingStoreDimensions() const override { return fBitmap.dimensions(); }
   * ```
   */
  public override fun backingStoreDimensions(): SkISize {
    TODO("Implement backingStoreDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t getSize() const override { return fBitmap.computeByteSize(); }
   * ```
   */
  public override fun getSize(): ULong {
    TODO("Implement getSize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> asImage() const override { return fBitmap.asImage(); }
   * ```
   */
  public override fun asImage(): SkSp<SkImage> {
    TODO("Implement asImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> onMakeBackingStoreSubset(const SkIRect& subset) const override {
   *         // No need to extract subset, onGetROPixels handles that when needed
   *         return SkSpecialImages::MakeFromRaster(subset, fBitmap, this->props());
   *     }
   * ```
   */
  public override fun onMakeBackingStoreSubset(subset: SkIRect): SkSp<SkSpecialImage> {
    TODO("Implement onMakeBackingStoreSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> asShader(SkTileMode tileMode,
   *                              const SkSamplingOptions& sampling,
   *                              const SkMatrix& lm,
   *                              bool strict) const override {
   *         if (strict) {
   *             // TODO(skbug.com/40043877): SkImage::makeShader() doesn't support a subset yet, but
   *             // SkBitmap supports subset views so create the shader from the subset bitmap instead of
   *             // fBitmap.
   *             SkBitmap subsetBM;
   *             if (!this->getROPixels(&subsetBM)) {
   *                 return nullptr;
   *             }
   *             return subsetBM.makeShader(tileMode, tileMode, sampling, lm);
   *         } else {
   *             // The special image's logical (0,0) is at its subset's topLeft() so we need to
   *             // account for that in the local matrix used when sampling.
   *             SkMatrix subsetOrigin = SkMatrix::Translate(-this->subset().topLeft());
   *             subsetOrigin.postConcat(lm);
   *             return fBitmap.makeShader(tileMode, tileMode, sampling, subsetOrigin);
   *         }
   *     }
   * ```
   */
  public override fun asShader(
    tileMode: SkTileMode,
    sampling: SkSamplingOptions,
    lm: SkMatrix,
    strict: Boolean,
  ): SkSp<SkShader> {
    TODO("Implement asShader")
  }
}
