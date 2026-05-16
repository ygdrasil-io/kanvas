package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkMaskFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import undefined.BlurRec

/**
 * C++ original:
 * ```cpp
 * class SkShaderMaskFilterImpl : public SkMaskFilterBase {
 * public:
 *     explicit SkShaderMaskFilterImpl(sk_sp<SkShader> shader) : fShader(std::move(shader)) {}
 *
 *     SkMask::Format getFormat() const override { return SkMask::kA8_Format; }
 *     SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kShader; }
 *
 *     bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
 *                     SkIPoint* margin) const override;
 *     std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(const SkMatrix&,
 *                                                         const SkPaint&) const override;
 *
 *     void computeFastBounds(const SkRect& src, SkRect* dst) const override {
 *         *dst = src;
 *     }
 *
 *     bool asABlur(BlurRec*) const override { return false; }
 *     sk_sp<SkShader> shader() const { return fShader; }
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkShaderMaskFilterImpl)
 *
 *     sk_sp<SkShader> fShader;
 *
 *     SkShaderMaskFilterImpl(SkReadBuffer&);
 *     void flatten(SkWriteBuffer&) const override;
 *
 *     friend class SkShaderMaskFilter;
 * }
 * ```
 */
public open class SkShaderMaskFilterImpl public constructor(
  shader: SkSp<SkShader>,
) : SkMaskFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * explicit SkShaderMaskFilterImpl(sk_sp<SkShader> shader) : fShader(std::move(shader)) {}
   * ```
   */
  public constructor(param0: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format getFormat() const override { return SkMask::kA8_Format; }
   * ```
   */
  public override fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kShader; }
   * ```
   */
  public override fun type(): SkMaskFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkShaderMaskFilterImpl::filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix& ctm,
   *                                         SkIPoint* margin) const {
   *     if (src.fFormat != SkMask::kA8_Format) {
   *         return false;
   *     }
   *
   *     if (margin) {
   *         margin->set(0, 0);
   *     }
   *     dst->bounds()   = src.fBounds;
   *     dst->rowBytes() = src.fBounds.width();   // need alignment?
   *     dst->format()   = SkMask::kA8_Format;
   *
   *     if (src.fImage == nullptr) {
   *         dst->image() = nullptr;
   *         return true;
   *     }
   *     size_t size = dst->computeImageSize();
   *     if (0 == size) {
   *         return false;   // too big to allocate, abort
   *     }
   *
   *     // Allocate and initialize dst image with a copy of the src image
   *     dst->image() = SkMaskBuilder::AllocImage(size);
   *     rect_memcpy(dst->image(), dst->fRowBytes, src.fImage, src.fRowBytes,
   *                 src.fBounds.width() * sizeof(uint8_t), src.fBounds.height());
   *
   *     // Now we have a dst-mask, just need to setup a canvas and draw into it
   *     SkBitmap bitmap;
   *     if (!bitmap.installPixels(SkImageInfo::MakeA8(dst->fBounds.width(), dst->fBounds.height()),
   *                               dst->image(),
   *                               dst->fRowBytes)) {
   *         return false;
   *     }
   *
   *     SkPaint paint;
   *     paint.setShader(fShader);
   *     // this blendmode is the trick: we only draw the shader where the mask is
   *     paint.setBlendMode(SkBlendMode::kSrcIn);
   *
   *     SkCanvas canvas(bitmap);
   *     canvas.translate(-SkIntToScalar(dst->fBounds.fLeft), -SkIntToScalar(dst->fBounds.fTop));
   *     canvas.concat(ctm);
   *     canvas.drawPaint(paint);
   *     return true;
   * }
   * ```
   */
  public override fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    ctm: SkMatrix,
    margin: SkIPoint?,
  ): Boolean {
    TODO("Implement filterMask")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkImageFilter>, bool> SkShaderMaskFilterImpl::asImageFilter(const SkMatrix&,
   *                                                                             const SkPaint&) const {
   *     sk_sp<SkImageFilter> filter =  SkImageFilters::Shader(fShader);
   *     return {SkImageFilters::Blend(SkBlendMode::kDstIn, std::move(filter), nullptr), false};
   * }
   * ```
   */
  public override fun asImageFilter(param0: SkMatrix, param1: SkPaint): Int {
    TODO("Implement asImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void computeFastBounds(const SkRect& src, SkRect* dst) const override {
   *         *dst = src;
   *     }
   * ```
   */
  public override fun computeFastBounds(src: SkRect, dst: SkRect?) {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool asABlur(BlurRec*) const override { return false; }
   * ```
   */
  public override fun asABlur(param0: BlurRec?): Boolean {
    TODO("Implement asABlur")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> shader() const { return fShader; }
   * ```
   */
  public fun shader(): SkSp<SkShader> {
    TODO("Implement shader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaderMaskFilterImpl::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fShader.get());
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkShaderMaskFilterImpl::CreateProc(SkReadBuffer& buffer) {
   *     return SkShaderMaskFilter::Make(buffer.readShader());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
