package org.skia.modules

import kotlin.Float
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkSize
import org.skia.math.SkVector
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class BulgeNode final : public sksg::CustomRenderNode {
 * public:
 *     explicit BulgeNode(sk_sp<RenderNode> child, const SkSize& child_size)
 *         : INHERITED({std::move(child)})
 *         , fChildSize(child_size) {}
 *
 *     SG_ATTRIBUTE(Center  , SkPoint   , fCenter)
 *     SG_ATTRIBUTE(Radius  , SkVector  , fRadius)
 *     SG_ATTRIBUTE(Height  , float     , fHeight)
 *
 * private:
 *     sk_sp<SkShader> contentShader() {
 *         if (!fContentShader || this->hasChildrenInval()) {
 *             const auto& child = this->children()[0];
 *             child->revalidate(nullptr, SkMatrix::I());
 *
 *             SkPictureRecorder recorder;
 *             child->render(recorder.beginRecording(SkRect::MakeSize(fChildSize)));
 *
 *             fContentShader = recorder.finishRecordingAsPicture()
 *                     ->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkFilterMode::kLinear,
 *                                  nullptr, nullptr);
 *         }
 *
 *         return fContentShader;
 *     }
 *
 *     sk_sp<SkShader> buildEffectShader() {
 *         if (fHeight == 0) {
 *             return nullptr;
 *         }
 *
 *         SkRuntimeShaderBuilder builder(bulge_effect());
 *         float adjHeight = std::abs(fHeight)/4;
 *         float r = (1 + adjHeight)/2/sqrt(adjHeight);
 *         float h = std::pow(adjHeight, 3)*1.3;
 *         builder.uniform("u_center")       = fCenter;
 *         builder.uniform("u_radius")       = fRadius;
 *         builder.uniform("u_radius_inv")   = SkVector{1/fRadius.fX, 1/fRadius.fY};
 *         builder.uniform("u_h")            = h;
 *         builder.uniform("u_rcpR")         = 1.0f/r;
 *         builder.uniform("u_rcpAsinInvR")  = 1.0f/std::asin(1/r);
 *         builder.uniform("u_selector")     = (fHeight > 0 ? 1.0f : -1.0f);
 *
 *         builder.child("u_layer") = this->contentShader();
 *
 *         return builder.makeShader();
 *     }
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         const auto& child = this->children()[0];
 *         fEffectShader = buildEffectShader();
 *         return child->revalidate(ic, ctm);
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         if (fHeight == 0) {
 *             this->children()[0]->render(canvas, ctx);
 *             return;
 *         }
 *         const auto& bounds = this->bounds();
 *         const auto local_ctx = ScopedRenderContext(canvas, ctx)
 *                 .setIsolation(bounds, canvas->getTotalMatrix(), true);
 *
 *         canvas->saveLayer(&bounds, nullptr);
 *
 *         SkPaint effect_paint;
 *         effect_paint.setShader(fEffectShader);
 *         effect_paint.setBlendMode(SkBlendMode::kSrcOver);
 *
 *         canvas->drawPaint(effect_paint);
 *     }
 *
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     sk_sp<SkShader> fEffectShader;
 *     sk_sp<SkShader> fContentShader;
 *     const SkSize fChildSize;
 *
 *     SkPoint  fCenter = {0,0};
 *     SkVector fRadius = {0,0};
 *     float   fHeight = 0;
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class BulgeNode public constructor(
  child: SkSp<RenderNode>,
  childSize: SkSize,
) : CustomRenderNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fEffectShader
   * ```
   */
  private var fEffectShader: SkSp<SkShader> = TODO("Initialize fEffectShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fContentShader
   * ```
   */
  private var fContentShader: SkSp<SkShader> = TODO("Initialize fContentShader")

  /**
   * C++ original:
   * ```cpp
   * const SkSize fChildSize
   * ```
   */
  private val fChildSize: SkSize = TODO("Initialize fChildSize")

  /**
   * C++ original:
   * ```cpp
   * SkPoint  fCenter = {0,0}
   * ```
   */
  private var fCenter: SkPoint = TODO("Initialize fCenter")

  /**
   * C++ original:
   * ```cpp
   * SkVector fRadius = {0,0}
   * ```
   */
  private var fRadius: SkVector = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * float   fHeight = 0
   * ```
   */
  private var fHeight: Float = TODO("Initialize fHeight")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> contentShader() {
   *         if (!fContentShader || this->hasChildrenInval()) {
   *             const auto& child = this->children()[0];
   *             child->revalidate(nullptr, SkMatrix::I());
   *
   *             SkPictureRecorder recorder;
   *             child->render(recorder.beginRecording(SkRect::MakeSize(fChildSize)));
   *
   *             fContentShader = recorder.finishRecordingAsPicture()
   *                     ->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkFilterMode::kLinear,
   *                                  nullptr, nullptr);
   *         }
   *
   *         return fContentShader;
   *     }
   * ```
   */
  private fun contentShader(): SkSp<SkShader> {
    TODO("Implement contentShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> buildEffectShader() {
   *         if (fHeight == 0) {
   *             return nullptr;
   *         }
   *
   *         SkRuntimeShaderBuilder builder(bulge_effect());
   *         float adjHeight = std::abs(fHeight)/4;
   *         float r = (1 + adjHeight)/2/sqrt(adjHeight);
   *         float h = std::pow(adjHeight, 3)*1.3;
   *         builder.uniform("u_center")       = fCenter;
   *         builder.uniform("u_radius")       = fRadius;
   *         builder.uniform("u_radius_inv")   = SkVector{1/fRadius.fX, 1/fRadius.fY};
   *         builder.uniform("u_h")            = h;
   *         builder.uniform("u_rcpR")         = 1.0f/r;
   *         builder.uniform("u_rcpAsinInvR")  = 1.0f/std::asin(1/r);
   *         builder.uniform("u_selector")     = (fHeight > 0 ? 1.0f : -1.0f);
   *
   *         builder.child("u_layer") = this->contentShader();
   *
   *         return builder.makeShader();
   *     }
   * ```
   */
  private fun buildEffectShader(): SkSp<SkShader> {
    TODO("Implement buildEffectShader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         const auto& child = this->children()[0];
   *         fEffectShader = buildEffectShader();
   *         return child->revalidate(ic, ctm);
   *     }
   * ```
   */
  public override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): SkRect {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
   *         if (fHeight == 0) {
   *             this->children()[0]->render(canvas, ctx);
   *             return;
   *         }
   *         const auto& bounds = this->bounds();
   *         const auto local_ctx = ScopedRenderContext(canvas, ctx)
   *                 .setIsolation(bounds, canvas->getTotalMatrix(), true);
   *
   *         canvas->saveLayer(&bounds, nullptr);
   *
   *         SkPaint effect_paint;
   *         effect_paint.setShader(fEffectShader);
   *         effect_paint.setBlendMode(SkBlendMode::kSrcOver);
   *
   *         canvas->drawPaint(effect_paint);
   *     }
   * ```
   */
  public override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; }
   * ```
   */
  public override fun onNodeAt(param0: SkPoint): RenderNode {
    TODO("Implement onNodeAt")
  }
}
