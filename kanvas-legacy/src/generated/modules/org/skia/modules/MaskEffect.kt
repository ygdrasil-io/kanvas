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
 * class MaskEffect final : public EffectNode {
 * public:
 *     enum class Mode : uint32_t {
 *         kAlphaNormal,
 *         kAlphaInvert,
 *         kLumaNormal,
 *         kLumaInvert,
 *     };
 *
 *     static sk_sp<MaskEffect> Make(sk_sp<RenderNode> child, sk_sp<RenderNode> mask,
 *                                   Mode mode = Mode::kAlphaNormal) {
 *         return (child && mask)
 *             ? sk_sp<MaskEffect>(new MaskEffect(std::move(child), std::move(mask), mode))
 *             : nullptr;
 *     }
 *
 *     ~MaskEffect() override;
 *
 * protected:
 *     MaskEffect(sk_sp<RenderNode>, sk_sp<RenderNode> mask, Mode);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *     const RenderNode* onNodeAt(const SkPoint&)     const override;
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override;
 *
 * private:
 *     const sk_sp<RenderNode> fMaskNode;
 *     const Mode              fMaskMode;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class MaskEffect public constructor(
  child: SkSp<RenderNode>,
  mask: SkSp<RenderNode>,
  mode: Mode,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<RenderNode> fMaskNode
   * ```
   */
  private val fMaskNode: Int = TODO("Initialize fMaskNode")

  /**
   * C++ original:
   * ```cpp
   * const Mode              fMaskMode
   * ```
   */
  private val fMaskMode: Mode = TODO("Initialize fMaskMode")

  /**
   * C++ original:
   * ```cpp
   * void MaskEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     SkAutoCanvasRestore acr(canvas, false);
   *
   *     // The mask mode covers two independent bits.
   *     //
   *     //   - mask source controls how the mask coverage is generated:
   *     //     * alpha => coverage = mask_alpha
   *     //     * luma  => coverage = luma(mask_rgb)
   *     //
   *     //   - mask type controls how the mask coverage is interpreted:
   *     //     * normal   => coverage' = coverage
   *     //     * inverted => coverage' = 1 - coverage
   *
   *     {
   *         // Outer layer: mask coverage stored in the alpha channel.
   *         SkPaint mask_layer_paint;
   *         if (ctx) {
   *             // Apply all optional context overrides upfront.
   *             ctx->modulatePaint(canvas->getTotalMatrix(), &mask_layer_paint);
   *         }
   *
   *         RenderContext mask_render_context;
   *         if (is_luma(fMaskMode)) {
   *             mask_render_context.fColorFilter = SkLumaColorFilter::Make();
   *         }
   *
   *         // TODO: could be an A8 layer?
   *         canvas->saveLayer(this->bounds(), &mask_layer_paint);
   *         fMaskNode->render(canvas, &mask_render_context);
   *
   *         {
   *             // Inner layer: masked content.
   *             SkPaint content_layer_paint;
   *             content_layer_paint.setBlendMode(is_inverted(fMaskMode) ? SkBlendMode::kSrcOut
   *                                                                     : SkBlendMode::kSrcIn);
   *             canvas->saveLayer(this->bounds(), &content_layer_paint);
   *
   *             this->INHERITED::onRender(canvas, nullptr);
   *         }
   *     }
   * }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* MaskEffect::onNodeAt(const SkPoint& p) const {
   *     const auto mask_hit = (SkToBool(fMaskNode->nodeAt(p)) == !is_inverted(fMaskMode));
   *
   *     if (!mask_hit) {
   *         return nullptr;
   *     }
   *
   *     return this->INHERITED::onNodeAt(p);
   * }
   * ```
   */
  protected override fun onNodeAt(p: SkPoint): Int {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect MaskEffect::onRevalidate(InvalidationController* ic, const SkMatrix& ctm) {
   *     SkASSERT(this->hasInval());
   *
   *     const auto maskBounds = fMaskNode->revalidate(ic, ctm);
   *     auto childBounds = this->INHERITED::onRevalidate(ic, ctm);
   *
   *     return (is_inverted(fMaskMode) || childBounds.intersect(maskBounds))
   *         ? childBounds
   *         : SkRect::MakeEmpty();
   * }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): Int {
    TODO("Implement onRevalidate")
  }

  public enum class Mode {
    kAlphaNormal,
    kAlphaInvert,
    kLumaNormal,
    kLumaInvert,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MaskEffect> Make(sk_sp<RenderNode> child, sk_sp<RenderNode> mask,
     *                                   Mode mode = Mode::kAlphaNormal) {
     *         return (child && mask)
     *             ? sk_sp<MaskEffect>(new MaskEffect(std::move(child), std::move(mask), mode))
     *             : nullptr;
     *     }
     * ```
     */
    public fun make(
      child: SkSp<RenderNode>,
      mask: SkSp<RenderNode>,
      mode: Mode = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
