package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class Image final : public RenderNode {
 * public:
 *     static sk_sp<Image> Make(sk_sp<SkImage> image) {
 *         return sk_sp<Image>(new Image(std::move(image)));
 *     }
 *
 *     SG_ATTRIBUTE(Image          , sk_sp<SkImage>   , fImage          )
 *     SG_ATTRIBUTE(SamplingOptions, SkSamplingOptions, fSamplingOptions)
 *     SG_ATTRIBUTE(AntiAlias      , bool             , fAntiAlias      )
 *
 * protected:
 *     explicit Image(sk_sp<SkImage>);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     SkSamplingOptions fSamplingOptions;
 *     sk_sp<SkImage>    fImage;
 *     bool              fAntiAlias = true;
 *
 *     using INHERITED = RenderNode;
 * }
 * ```
 */
public class Image public constructor(
  image: SkSp<SkImage>,
) : RenderNode() {
  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions fSamplingOptions
   * ```
   */
  private var fSamplingOptions: Int = TODO("Initialize fSamplingOptions")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>    fImage
   * ```
   */
  private var fImage: Int = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * bool              fAntiAlias = true
   * ```
   */
  private var fAntiAlias: Boolean = TODO("Initialize fAntiAlias")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Image          , sk_sp<SkImage>   , fImage          )
   * ```
   */
  public fun sgATTRIBUTE(param0: Image, param1: SkSp<SkImage>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void Image::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     if (!fImage) {
   *         return;
   *     }
   *
   *     SkPaint paint;
   *     paint.setAntiAlias(fAntiAlias);
   *
   *     sksg::RenderNode::ScopedRenderContext local_ctx(canvas, ctx);
   *     if (ctx) {
   *         if (ctx->fMaskShader) {
   *             // Mask shaders cannot be applied via drawImage - we need layer isolation.
   *             // TODO: remove after clipShader conversion.
   *             local_ctx.setIsolation(this->bounds(), canvas->getTotalMatrix(), true);
   *         }
   *         local_ctx->modulatePaint(canvas->getTotalMatrix(), &paint);
   *     }
   *
   *     canvas->drawImage(fImage, 0, 0, fSamplingOptions, &paint);
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* Image::onNodeAt(const SkPoint& p) const {
   *     SkASSERT(this->bounds().contains(p.x(), p.y()));
   *     return this;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect Image::onRevalidate(InvalidationController*, const SkMatrix& ctm) {
   *     return fImage ? SkRect::Make(fImage->bounds()) : SkRect::MakeEmpty();
   * }
   * ```
   */
  protected override fun onRevalidate(param0: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Image> Make(sk_sp<SkImage> image) {
     *         return sk_sp<Image>(new Image(std::move(image)));
     *     }
     * ```
     */
    public fun make(image: SkSp<SkImage>): Int {
      TODO("Implement make")
    }
  }
}
