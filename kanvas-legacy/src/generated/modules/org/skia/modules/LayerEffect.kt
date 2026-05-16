package org.skia.modules

import kotlin.Int
import org.skia.`external`.Mode
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class LayerEffect final : public EffectNode {
 * public:
 *     ~LayerEffect() override;
 *
 *     static sk_sp<LayerEffect> Make(sk_sp<RenderNode> child,
 *                                    SkBlendMode mode = SkBlendMode::kSrcOver);
 *
 *     SG_ATTRIBUTE(Mode, SkBlendMode, fMode)
 *
 * private:
 *     LayerEffect(sk_sp<RenderNode> child, SkBlendMode mode);
 *
 *     void onRender(SkCanvas*, const RenderContext*) const override;
 *
 *     SkBlendMode fMode;
 *
 *     using INHERITED = EffectNode;
 * }
 * ```
 */
public class LayerEffect public constructor(
  child: SkSp<RenderNode>,
  mode: SkBlendMode,
) : EffectNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fMode
   * ```
   */
  private var fMode: Int = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Mode, SkBlendMode, fMode)
   * ```
   */
  public fun sgATTRIBUTE(param0: Mode, param1: SkBlendMode): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * void LayerEffect::onRender(SkCanvas* canvas, const RenderContext* ctx) const {
   *     SkAutoCanvasRestore acr(canvas, false);
   *
   *     // Commit any potential pending paint effects to their own layer.
   *     const auto local_ctx = ScopedRenderContext(canvas, ctx).setIsolation(this->bounds(),
   *                                                                          canvas->getTotalMatrix(),
   *                                                                          true);
   *
   *     SkPaint layer_paint;
   *     if (ctx) {
   *         // Apply all optional context overrides upfront.
   *         ctx->modulatePaint(canvas->getTotalMatrix(), &layer_paint);
   *     }
   *     layer_paint.setBlendMode(fMode);
   *
   *     canvas->saveLayer(nullptr, &layer_paint);
   *
   *     this->INHERITED::onRender(canvas, nullptr);
   * }
   * ```
   */
  public override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<LayerEffect> LayerEffect::Make(sk_sp<RenderNode> child, SkBlendMode mode) {
     *     return child ? sk_sp<LayerEffect>(new LayerEffect(std::move(child), mode))
     *                  : nullptr;
     * }
     * ```
     */
    public fun make(child: SkSp<RenderNode>, mode: SkBlendMode = TODO()): Int {
      TODO("Implement make")
    }
  }
}
