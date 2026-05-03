package org.skia.modules

import kotlin.Float
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkM44
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkSize
import org.skia.math.SkV3
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class SphereNode final : public sksg::CustomRenderNode {
 * public:
 *     SphereNode(sk_sp<RenderNode> child, const SkSize& child_size)
 *         : INHERITED({std::move(child)})
 *         , fChildSize(child_size) {}
 *
 *     enum class RenderSide {
 *         kFull,
 *         kOutside,
 *         kInside,
 *     };
 *
 *     SG_ATTRIBUTE(Center  , SkPoint   , fCenter)
 *     SG_ATTRIBUTE(Radius  , float     , fRadius)
 *     SG_ATTRIBUTE(Rotation, SkM44     , fRot   )
 *     SG_ATTRIBUTE(Side    , RenderSide, fSide  )
 *
 *     SG_ATTRIBUTE(LightVec     , SkV3 , fLightVec     )
 *     SG_ATTRIBUTE(LightColor   , SkV3 , fLightColor   )
 *     SG_ATTRIBUTE(AmbientLight , float, fAmbientLight )
 *     SG_ATTRIBUTE(DiffuseLight , float, fDiffuseLight )
 *     SG_ATTRIBUTE(SpecularLight, float, fSpecularLight)
 *     SG_ATTRIBUTE(SpecularExp  , float, fSpecularExp  )
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
 *     sk_sp<SkShader> buildEffectShader(float selector) {
 *         const auto has_fancy_light =
 *                 fLightVec.length() > 0 && (fDiffuseLight > 0 || fSpecularLight > 0);
 *
 *         SkRuntimeShaderBuilder builder(has_fancy_light
 *                                            ? sphere_fancylight_effect()
 *                                            : sphere_basiclight_effect());
 *
 *         builder.child  ("child")       = this->contentShader();
 *         builder.uniform("child_scale") = fChildSize;
 *         builder.uniform("side_select") = selector;
 *         builder.uniform("rot_matrix")  = std::array<float,9>{
 *             fRot.rc(0,0), fRot.rc(0,1), fRot.rc(0,2),
 *             fRot.rc(1,0), fRot.rc(1,1), fRot.rc(1,2),
 *             fRot.rc(2,0), fRot.rc(2,1), fRot.rc(2,2),
 *         };
 *
 *         builder.uniform("l_coeff_ambient")  = fAmbientLight;
 *
 *         if (has_fancy_light) {
 *             builder.uniform("l_vec")            = fLightVec * -selector;
 *             builder.uniform("l_color")          = fLightColor;
 *             builder.uniform("l_coeff_diffuse")  = fDiffuseLight;
 *             builder.uniform("l_coeff_specular") = fSpecularLight;
 *             builder.uniform("l_specular_exp")   = fSpecularExp;
 *         }
 *
 *         const auto lm = SkMatrix::Translate(fCenter.fX, fCenter.fY) *
 *                         SkMatrix::Scale(fRadius, fRadius);
 *
 *         return builder.makeShader(&lm);
 *     }
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         fSphereShader.reset();
 *         if (fSide != RenderSide::kOutside) {
 *             fSphereShader = this->buildEffectShader(1);
 *         }
 *         if (fSide != RenderSide::kInside) {
 *             auto outside = this->buildEffectShader(-1);
 *             fSphereShader = fSphereShader
 *                     ? SkShaders::Blend(SkBlendMode::kSrcOver,
 *                                        std::move(fSphereShader),
 *                                        std::move(outside))
 *                     : std::move(outside);
 *         }
 *         SkASSERT(fSphereShader);
 *
 *         return SkRect::MakeLTRB(fCenter.fX - fRadius,
 *                                 fCenter.fY - fRadius,
 *                                 fCenter.fX + fRadius,
 *                                 fCenter.fY + fRadius);
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         if (fRadius <= 0) {
 *             return;
 *         }
 *
 *         SkPaint sphere_paint;
 *         sphere_paint.setAntiAlias(true);
 *         sphere_paint.setShader(fSphereShader);
 *
 *         canvas->drawCircle(fCenter, fRadius, sphere_paint);
 *     }
 *
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     const SkSize fChildSize;
 *
 *     // Cached shaders
 *     sk_sp<SkShader> fSphereShader;
 *     sk_sp<SkShader> fContentShader;
 *
 *     // Effect controls.
 *     SkM44      fRot;
 *     SkPoint    fCenter = {0,0};
 *     float      fRadius = 0;
 *     RenderSide fSide   = RenderSide::kFull;
 *
 *     SkV3       fLightVec      = {0,0,1},
 *                fLightColor    = {1,1,1};
 *     float      fAmbientLight  = 1,
 *                fDiffuseLight  = 0,
 *                fSpecularLight = 0,
 *                fSpecularExp   = 0;
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class SphereNode public constructor(
  child: SkSp<RenderNode>,
  childSize: SkSize,
) : CustomRenderNode(TODO()) {
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
   * sk_sp<SkShader> fSphereShader
   * ```
   */
  private var fSphereShader: SkSp<SkShader> = TODO("Initialize fSphereShader")

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
   * SkM44      fRot
   * ```
   */
  private var fRot: SkM44 = TODO("Initialize fRot")

  /**
   * C++ original:
   * ```cpp
   * SkPoint    fCenter = {0,0}
   * ```
   */
  private var fCenter: SkPoint = TODO("Initialize fCenter")

  /**
   * C++ original:
   * ```cpp
   * float      fRadius = 0
   * ```
   */
  private var fRadius: Float = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * RenderSide fSide   = RenderSide::kFull
   * ```
   */
  private var fSide: RenderSide = TODO("Initialize fSide")

  /**
   * C++ original:
   * ```cpp
   * SkV3       fLightVec      = {0,0,1}
   * ```
   */
  private var fLightVec: SkV3 = TODO("Initialize fLightVec")

  /**
   * C++ original:
   * ```cpp
   * SkV3       fLightVec      = {0,0,1},
   *                fLightColor    = {1,1,1}
   * ```
   */
  private var fLightColor: SkV3 = TODO("Initialize fLightColor")

  /**
   * C++ original:
   * ```cpp
   * float      fAmbientLight  = 1
   * ```
   */
  private var fAmbientLight: Float = TODO("Initialize fAmbientLight")

  /**
   * C++ original:
   * ```cpp
   * float      fAmbientLight  = 1,
   *                fDiffuseLight  = 0
   * ```
   */
  private var fDiffuseLight: Float = TODO("Initialize fDiffuseLight")

  /**
   * C++ original:
   * ```cpp
   * float      fAmbientLight  = 1,
   *                fDiffuseLight  = 0,
   *                fSpecularLight = 0
   * ```
   */
  private var fSpecularLight: Float = TODO("Initialize fSpecularLight")

  /**
   * C++ original:
   * ```cpp
   * float      fAmbientLight  = 1,
   *                fDiffuseLight  = 0,
   *                fSpecularLight = 0,
   *                fSpecularExp   = 0
   * ```
   */
  private var fSpecularExp: Float = TODO("Initialize fSpecularExp")

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
   * sk_sp<SkShader> buildEffectShader(float selector) {
   *         const auto has_fancy_light =
   *                 fLightVec.length() > 0 && (fDiffuseLight > 0 || fSpecularLight > 0);
   *
   *         SkRuntimeShaderBuilder builder(has_fancy_light
   *                                            ? sphere_fancylight_effect()
   *                                            : sphere_basiclight_effect());
   *
   *         builder.child  ("child")       = this->contentShader();
   *         builder.uniform("child_scale") = fChildSize;
   *         builder.uniform("side_select") = selector;
   *         builder.uniform("rot_matrix")  = std::array<float,9>{
   *             fRot.rc(0,0), fRot.rc(0,1), fRot.rc(0,2),
   *             fRot.rc(1,0), fRot.rc(1,1), fRot.rc(1,2),
   *             fRot.rc(2,0), fRot.rc(2,1), fRot.rc(2,2),
   *         };
   *
   *         builder.uniform("l_coeff_ambient")  = fAmbientLight;
   *
   *         if (has_fancy_light) {
   *             builder.uniform("l_vec")            = fLightVec * -selector;
   *             builder.uniform("l_color")          = fLightColor;
   *             builder.uniform("l_coeff_diffuse")  = fDiffuseLight;
   *             builder.uniform("l_coeff_specular") = fSpecularLight;
   *             builder.uniform("l_specular_exp")   = fSpecularExp;
   *         }
   *
   *         const auto lm = SkMatrix::Translate(fCenter.fX, fCenter.fY) *
   *                         SkMatrix::Scale(fRadius, fRadius);
   *
   *         return builder.makeShader(&lm);
   *     }
   * ```
   */
  private fun buildEffectShader(selector: Float): SkSp<SkShader> {
    TODO("Implement buildEffectShader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         fSphereShader.reset();
   *         if (fSide != RenderSide::kOutside) {
   *             fSphereShader = this->buildEffectShader(1);
   *         }
   *         if (fSide != RenderSide::kInside) {
   *             auto outside = this->buildEffectShader(-1);
   *             fSphereShader = fSphereShader
   *                     ? SkShaders::Blend(SkBlendMode::kSrcOver,
   *                                        std::move(fSphereShader),
   *                                        std::move(outside))
   *                     : std::move(outside);
   *         }
   *         SkASSERT(fSphereShader);
   *
   *         return SkRect::MakeLTRB(fCenter.fX - fRadius,
   *                                 fCenter.fY - fRadius,
   *                                 fCenter.fX + fRadius,
   *                                 fCenter.fY + fRadius);
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
   *         if (fRadius <= 0) {
   *             return;
   *         }
   *
   *         SkPaint sphere_paint;
   *         sphere_paint.setAntiAlias(true);
   *         sphere_paint.setShader(fSphereShader);
   *
   *         canvas->drawCircle(fCenter, fRadius, sphere_paint);
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

  public enum class RenderSide {
    kFull,
    kOutside,
    kInside,
  }
}
