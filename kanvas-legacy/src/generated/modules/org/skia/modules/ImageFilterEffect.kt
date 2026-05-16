package org.skia.modules

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class ImageFilterEffect final : public EffectNode {
 * public:
 *     ~ImageFilterEffect() override;
 *
 *     static sk_sp<RenderNode> Make(sk_sp<RenderNode> child, sk_sp<ImageFilter> filter);
 *
 *     enum class Cropping {
 *         kNone,    // Doesn't use a crop rect.
 *         kContent, // Uses the content bounding box as a crop rect.
 *     };
 *
 *     SG_ATTRIBUTE(Cropping, Cropping, fCropping)
 *
 * protected:
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     ImageFilterEffect(sk_sp<RenderNode> child, sk_sp<ImageFilter> filter);
 *
 *     sk_sp<ImageFilter> fImageFilter;
 *     Cropping           fCropping = Cropping::kNone;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class ImageFilterEffect public constructor(
  child: SkSp<RenderNode>,
  filter: SkSp<ImageFilter>,
) : EffectNode(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ImageFilterEffect(sk_sp<RenderNode> child, sk_sp<ImageFilter> filter)
   * ```
   */
  private var skSp: ImageFilterEffect = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageFilter> fImageFilter
   * ```
   */
  private var fImageFilter: Int = TODO("Initialize fImageFilter")

  /**
   * C++ original:
   * ```cpp
   * Cropping           fCropping = Cropping::kNone
   * ```
   */
  private var fCropping: Cropping = TODO("Initialize fCropping")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Cropping, Cropping, fCropping)
   * ```
   */
  public fun sgATTRIBUTE(param0: Cropping, param1: Cropping): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* ImageFilterEffect::onNodeAt(const SkPoint& p) const {
   *     // TODO: map p through the filter DAG and dispatch to descendants?
   *     // For now, image filters occlude hit-testing.
   *     SkASSERT(this->bounds().contains(p.x(), p.y()));
   *     return this;
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): RenderNode {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect ImageFilterEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     const auto content_bounds = this->INHERITED::onRevalidate(ic, ctm);
   *
   *     if (fCropping == Cropping::kContent) {
   *         fImageFilter->setCropRect(content_bounds);
   *     } else {
   *         fImageFilter->setCropRect(std::nullopt);
   *     }
   *
   *     // FIXME: image filter effects should replace the descendents' damage!
   *     fImageFilter->revalidate(ic, ctm);
   *
   *     const auto& filter = fImageFilter->getFilter();
   *
   *     // Would be nice for this this to stick, but canComputeFastBounds()
   *     // appears to be conservative (false negatives).
   *     // SkASSERT(!filter || filter->canComputeFastBounds());
   *
   *     return filter ? filter->computeFastBounds(content_bounds)
   *                   : content_bounds;
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void ImageFilterEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     // Note: we're using the source content bounds for saveLayer, not our local/filtered bounds.
   *     const auto filter_ctx =
   *         ScopedRenderContext(canvas, ctx).setFilterIsolation(this->getChild()->bounds(),
   *                                                             canvas->getTotalMatrix(),
   *                                                             fImageFilter->getFilter());
   *     this->INHERITED::onRender(canvas, filter_ctx);
   * }
   * ```
   */
  public fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  public enum class Cropping {
    kNone,
    kContent,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<RenderNode> ImageFilterEffect::Make(sk_sp<RenderNode> child, sk_sp<ImageFilter> filter) {
     *     return filter ? sk_sp<RenderNode>(new ImageFilterEffect(std::move(child), std::move(filter)))
     *                   : child;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, filter: SkSp<ImageFilter>): Int {
      TODO("Implement make")
    }
  }
}
