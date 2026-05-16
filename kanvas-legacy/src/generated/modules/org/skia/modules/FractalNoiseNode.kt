package org.skia.modules

import kotlin.Float
import org.skia.core.SkCanvas
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkV2
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class FractalNoiseNode final : public sksg::CustomRenderNode {
 * public:
 *     explicit FractalNoiseNode(sk_sp<RenderNode> child) : INHERITED({std::move(child)}) {}
 *
 *     SG_ATTRIBUTE(Matrix         , SkMatrix    , fMatrix         )
 *     SG_ATTRIBUTE(SubMatrix      , SkMatrix    , fSubMatrix      )
 *
 *     SG_ATTRIBUTE(NoiseFilter    , NoiseFilter , fFilter         )
 *     SG_ATTRIBUTE(NoiseFractal   , NoiseFractal, fFractal        )
 *     SG_ATTRIBUTE(NoisePlanes    , SkV2        , fNoisePlanes    )
 *     SG_ATTRIBUTE(NoiseWeight    , float       , fNoiseWeight    )
 *     SG_ATTRIBUTE(Octaves        , float       , fOctaves        )
 *     SG_ATTRIBUTE(Persistence    , float       , fPersistence    )
 *
 * private:
 *     sk_sp<SkRuntimeEffect> getEffect(NoiseFilter filter) const {
 *         switch (fFractal) {
 *             case NoiseFractal::kBasic:
 *                 return noise_effect(fOctaves, filter, NoiseFractal::kBasic);
 *             case NoiseFractal::kTurbulentBasic:
 *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentBasic);
 *             case NoiseFractal::kTurbulentSmooth:
 *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentSmooth);
 *             case NoiseFractal::kTurbulentSharp:
 *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentSharp);
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     sk_sp<SkRuntimeEffect> getEffect() const {
 *         switch (fFilter) {
 *             case NoiseFilter::kNearest   : return this->getEffect(NoiseFilter::kNearest);
 *             case NoiseFilter::kLinear    : return this->getEffect(NoiseFilter::kLinear);
 *             case NoiseFilter::kSoftLinear: return this->getEffect(NoiseFilter::kSoftLinear);
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     sk_sp<SkShader> buildEffectShader() const {
 *         SkRuntimeShaderBuilder builder(this->getEffect());
 *
 *         builder.uniform("u_noise_planes") = fNoisePlanes;
 *         builder.uniform("u_noise_weight") = fNoiseWeight;
 *         builder.uniform("u_octaves"     ) = fOctaves;
 *         builder.uniform("u_persistence" ) = fPersistence;
 *         builder.uniform("u_submatrix"   ) = std::array<float,9>{
 *             fSubMatrix.rc(0,0), fSubMatrix.rc(1,0), fSubMatrix.rc(2,0),
 *             fSubMatrix.rc(0,1), fSubMatrix.rc(1,1), fSubMatrix.rc(2,1),
 *             fSubMatrix.rc(0,2), fSubMatrix.rc(1,2), fSubMatrix.rc(2,2),
 *         };
 *
 *         return builder.makeShader(&fMatrix);
 *     }
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         const auto& child = this->children()[0];
 *         const auto bounds = child->revalidate(ic, ctm);
 *
 *         fEffectShader = this->buildEffectShader();
 *
 *         return bounds;
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
 *
 *     SkMatrix     fMatrix,
 *                  fSubMatrix;
 *     NoiseFilter  fFilter          = NoiseFilter::kNearest;
 *     NoiseFractal fFractal         = NoiseFractal::kBasic;
 *     SkV2         fNoisePlanes     = {0,0};
 *     float        fNoiseWeight     = 0,
 *                  fOctaves         = 1,
 *                  fPersistence     = 1;
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class FractalNoiseNode public constructor(
  child: SkSp<RenderNode>,
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
   * SkMatrix     fMatrix
   * ```
   */
  private var fMatrix: SkMatrix = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix     fMatrix,
   *                  fSubMatrix
   * ```
   */
  private var fSubMatrix: SkMatrix = TODO("Initialize fSubMatrix")

  /**
   * C++ original:
   * ```cpp
   * NoiseFilter  fFilter          = NoiseFilter::kNearest
   * ```
   */
  private var fFilter: NoiseFilter = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * NoiseFractal fFractal         = NoiseFractal::kBasic
   * ```
   */
  private var fFractal: NoiseFractal = TODO("Initialize fFractal")

  /**
   * C++ original:
   * ```cpp
   * SkV2         fNoisePlanes     = {0,0}
   * ```
   */
  private var fNoisePlanes: SkV2 = TODO("Initialize fNoisePlanes")

  /**
   * C++ original:
   * ```cpp
   * float        fNoiseWeight     = 0
   * ```
   */
  private var fNoiseWeight: Float = TODO("Initialize fNoiseWeight")

  /**
   * C++ original:
   * ```cpp
   * float        fNoiseWeight     = 0,
   *                  fOctaves         = 1
   * ```
   */
  private var fOctaves: Float = TODO("Initialize fOctaves")

  /**
   * C++ original:
   * ```cpp
   * float        fNoiseWeight     = 0,
   *                  fOctaves         = 1,
   *                  fPersistence     = 1
   * ```
   */
  private var fPersistence: Float = TODO("Initialize fPersistence")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> getEffect(NoiseFilter filter) const {
   *         switch (fFractal) {
   *             case NoiseFractal::kBasic:
   *                 return noise_effect(fOctaves, filter, NoiseFractal::kBasic);
   *             case NoiseFractal::kTurbulentBasic:
   *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentBasic);
   *             case NoiseFractal::kTurbulentSmooth:
   *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentSmooth);
   *             case NoiseFractal::kTurbulentSharp:
   *                 return noise_effect(fOctaves, filter, NoiseFractal::kTurbulentSharp);
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  private fun getEffect(filter: NoiseFilter): SkSp<SkRuntimeEffect> {
    TODO("Implement getEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> getEffect() const {
   *         switch (fFilter) {
   *             case NoiseFilter::kNearest   : return this->getEffect(NoiseFilter::kNearest);
   *             case NoiseFilter::kLinear    : return this->getEffect(NoiseFilter::kLinear);
   *             case NoiseFilter::kSoftLinear: return this->getEffect(NoiseFilter::kSoftLinear);
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  private fun getEffect(): SkSp<SkRuntimeEffect> {
    TODO("Implement getEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> buildEffectShader() const {
   *         SkRuntimeShaderBuilder builder(this->getEffect());
   *
   *         builder.uniform("u_noise_planes") = fNoisePlanes;
   *         builder.uniform("u_noise_weight") = fNoiseWeight;
   *         builder.uniform("u_octaves"     ) = fOctaves;
   *         builder.uniform("u_persistence" ) = fPersistence;
   *         builder.uniform("u_submatrix"   ) = std::array<float,9>{
   *             fSubMatrix.rc(0,0), fSubMatrix.rc(1,0), fSubMatrix.rc(2,0),
   *             fSubMatrix.rc(0,1), fSubMatrix.rc(1,1), fSubMatrix.rc(2,1),
   *             fSubMatrix.rc(0,2), fSubMatrix.rc(1,2), fSubMatrix.rc(2,2),
   *         };
   *
   *         return builder.makeShader(&fMatrix);
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
   *         const auto bounds = child->revalidate(ic, ctm);
   *
   *         fEffectShader = this->buildEffectShader();
   *
   *         return bounds;
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
