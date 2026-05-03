package org.skia.modules

import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkSize
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class SkSLShaderNode final : public sksg::CustomRenderNode {
 * public:
 *     explicit SkSLShaderNode(sk_sp<RenderNode> child, const SkSize& content_size)
 *         : INHERITED({std::move(child)})
 *         , fContentSize(content_size) {}
 *
 *     sk_sp<SkShader> contentShader() {
 *         if (!fContentShader || this->hasChildrenInval()) {
 *             const auto& child = this->children()[0];
 *             child->revalidate(nullptr, SkMatrix::I());
 *
 *             SkPictureRecorder recorder;
 *             child->render(recorder.beginRecording(SkRect::MakeSize(fContentSize)));
 *
 *             fContentShader = recorder.finishRecordingAsPicture()
 *                     ->makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat, SkFilterMode::kLinear,
 *                                  nullptr, nullptr);
 *         }
 *
 *         return fContentShader;
 *     }
 *
 *     SG_ATTRIBUTE(Shader, sk_sp<SkShader>, fEffectShader)
 * private:
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         const auto& child = this->children()[0];
 *         return child->revalidate(ic, ctm);
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         const auto& bounds = this->bounds();
 *         const auto local_ctx = ScopedRenderContext(canvas, ctx)
 *                 .setIsolation(bounds, canvas->getTotalMatrix(), true);
 *
 *         canvas->saveLayer(&bounds, nullptr);
 *         this->children()[0]->render(canvas, local_ctx);
 *
 *         SkPaint effect_paint;
 *         effect_paint.setShader(fEffectShader);
 *         effect_paint.setBlendMode(SkBlendMode::kSrcIn);
 *
 *         canvas->drawPaint(effect_paint);
 *     }
 *
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     sk_sp<SkShader> fEffectShader;
 *     sk_sp<SkShader> fContentShader;
 *     const SkSize fContentSize;
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class SkSLShaderNode public constructor(
  child: SkSp<RenderNode>,
  contentSize: SkSize,
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
   * const SkSize fContentSize
   * ```
   */
  private val fContentSize: SkSize = TODO("Initialize fContentSize")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> contentShader() {
   *         if (!fContentShader || this->hasChildrenInval()) {
   *             const auto& child = this->children()[0];
   *             child->revalidate(nullptr, SkMatrix::I());
   *
   *             SkPictureRecorder recorder;
   *             child->render(recorder.beginRecording(SkRect::MakeSize(fContentSize)));
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
  public fun contentShader(): SkSp<SkShader> {
    TODO("Implement contentShader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         const auto& child = this->children()[0];
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
   *         const auto& bounds = this->bounds();
   *         const auto local_ctx = ScopedRenderContext(canvas, ctx)
   *                 .setIsolation(bounds, canvas->getTotalMatrix(), true);
   *
   *         canvas->saveLayer(&bounds, nullptr);
   *         this->children()[0]->render(canvas, local_ctx);
   *
   *         SkPaint effect_paint;
   *         effect_paint.setShader(fEffectShader);
   *         effect_paint.setBlendMode(SkBlendMode::kSrcIn);
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
