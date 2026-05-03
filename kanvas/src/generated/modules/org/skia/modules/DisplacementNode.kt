package org.skia.modules

import kotlin.Boolean
import kotlin.Float
import org.skia.core.SkCanvas
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkSize
import org.skia.math.SkV2
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class DisplacementNode final : public sksg::CustomRenderNode {
 * public:
 *     ~DisplacementNode() override {
 *         this->unobserveInval(fDisplSource);
 *     }
 *
 *     static sk_sp<DisplacementNode> Make(sk_sp<RenderNode> child,
 *                                         const SkSize& child_size,
 *                                         sk_sp<RenderNode> displ,
 *                                         const SkSize& displ_size) {
 *         if (!child || !displ) {
 *             return nullptr;
 *         }
 *
 *         return sk_sp<DisplacementNode>(new DisplacementNode(std::move(child), child_size,
 *                                                             std::move(displ), displ_size));
 *     }
 *
 *     enum class Pos : unsigned {
 *         kCenter,
 *         kStretch,
 *         kTile,
 *
 *         kLast = kTile,
 *     };
 *
 *     enum class Selector : unsigned {
 *         kR,
 *         kG,
 *         kB,
 *         kA,
 *         kLuminance,
 *         kHue,
 *         kLightness,
 *         kSaturation,
 *         kFull,
 *         kHalf,
 *         kOff,
 *
 *         kLast = kOff,
 *     };
 *
 *     SG_ATTRIBUTE(Scale        , SkV2      , fScale         )
 *     SG_ATTRIBUTE(ChildTileMode, SkTileMode, fChildTileMode )
 *     SG_ATTRIBUTE(Pos          , Pos       , fPos           )
 *     SG_ATTRIBUTE(XSelector    , Selector  , fXSelector     )
 *     SG_ATTRIBUTE(YSelector    , Selector  , fYSelector     )
 *     SG_ATTRIBUTE(ExpandBounds , bool      , fExpandBounds  )
 *
 * private:
 *     DisplacementNode(sk_sp<RenderNode> child, const SkSize& child_size,
 *                      sk_sp<RenderNode> displ, const SkSize& displ_size)
 *         : INHERITED({std::move(child)})
 *         , fDisplSource(std::move(displ))
 *         , fDisplSize(displ_size)
 *         , fChildSize(child_size)
 *     {
 *         this->observeInval(fDisplSource);
 *     }
 *
 *     struct SelectorCoeffs {
 *         float dr, dg, db, da, d_offset,  // displacement contribution
 *               c_scale, c_offset;         // coverage as a function of alpha
 *     };
 *
 *     static SelectorCoeffs Coeffs(Selector sel) {
 *         // D = displacement input
 *         // C = displacement coverage
 *         static constexpr SelectorCoeffs gCoeffs[] = {
 *             { 1,0,0,0,0,   1,0 },   // kR: D = r, C = a
 *             { 0,1,0,0,0,   1,0 },   // kG: D = g, C = a
 *             { 0,0,1,0,0,   1,0 },   // kB: D = b, C = a
 *             { 0,0,0,1,0,   0,1 },   // kA: D = a, C = 1.0
 *             { SK_LUM_COEFF_R,SK_LUM_COEFF_G, SK_LUM_COEFF_B,0,0,   1,0},
 *                                     // kLuminance: D = lum(rgb), C = a
 *             { 1,0,0,0,0,   0,1 },   // kH: D = h, C = 1.0   (HSLA)
 *             { 0,1,0,0,0,   0,1 },   // kL: D = l, C = 1.0   (HSLA)
 *             { 0,0,1,0,0,   0,1 },   // kS: D = s, C = 1.0   (HSLA)
 *             { 0,0,0,0,1,   0,1 },   // kFull: D = 1.0, C = 1.0
 *             { 0,0,0,0,.5f, 0,1 },   // kHalf: D = 0.5, C = 1.0
 *             { 0,0,0,0,0,   0,1 },   // kOff:  D = 0.0, C = 1.0
 *         };
 *
 *         const auto i = static_cast<size_t>(sel);
 *         SkASSERT(i < std::size(gCoeffs));
 *
 *         return gCoeffs[i];
 *     }
 *
 *     static bool IsConst(Selector s) {
 *         return s == Selector::kFull
 *             || s == Selector::kHalf
 *             || s == Selector::kOff;
 *     }
 *
 *     sk_sp<SkShader> buildEffectShader(sksg::InvalidationController* ic, const SkMatrix& ctm) {
 *         // AE quirk: combining two const/generated modes does not displace - we need at
 *         // least one non-const selector to trigger the effect.  *shrug*
 *         if ((IsConst(fXSelector) && IsConst(fYSelector)) ||
 *             (SkScalarNearlyZero(fScale.x) && SkScalarNearlyZero(fScale.y))) {
 *             return nullptr;
 *         }
 *
 *         auto get_content_picture = [](const sk_sp<sksg::RenderNode>& node,
 *                                       sksg::InvalidationController* ic, const SkMatrix& ctm) {
 *             if (!node) {
 *                 return sk_sp<SkPicture>(nullptr);
 *             }
 *
 *             const auto bounds = node->revalidate(ic, ctm);
 *
 *             SkPictureRecorder recorder;
 *             node->render(recorder.beginRecording(bounds));
 *             return recorder.finishRecordingAsPicture();
 *         };
 *
 *         const auto child_content = get_content_picture(this->children()[0], ic, ctm),
 *                    displ_content = get_content_picture(fDisplSource, ic, ctm);
 *         if (!child_content || !displ_content) {
 *             return nullptr;
 *         }
 *
 *         const auto child_tile = SkRect::MakeSize(fChildSize);
 *         auto child_shader = child_content->makeShader(fChildTileMode,
 *                                                       fChildTileMode,
 *                                                       SkFilterMode::kLinear,
 *                                                       nullptr,
 *                                                       &child_tile);
 *
 *         const auto displ_tile   = SkRect::MakeSize(fDisplSize);
 *         const auto displ_mode   = this->displacementTileMode();
 *         const auto displ_matrix = this->displacementMatrix();
 *         auto displ_shader = displ_content->makeShader(displ_mode,
 *                                                       displ_mode,
 *                                                       SkFilterMode::kLinear,
 *                                                       &displ_matrix,
 *                                                       &displ_tile);
 *
 *         SkRuntimeShaderBuilder builder(displacement_effect_singleton());
 *         builder.child("child") = std::move(child_shader);
 *         builder.child("displ") = std::move(displ_shader);
 *
 *         const auto xc = Coeffs(fXSelector),
 *                    yc = Coeffs(fYSelector);
 *
 *         const auto s = fScale * 2;
 *
 *         const float selector_m[] = {
 *             xc.dr*s.x, yc.dr*s.y,          0,          0,
 *             xc.dg*s.x, yc.dg*s.y,          0,          0,
 *             xc.db*s.x, yc.db*s.y,          0,          0,
 *             xc.da*s.x, yc.da*s.y, xc.c_scale, yc.c_scale,
 *
 *             //  │          │               │           └────  A -> vertical modulation
 *             //  │          │               └────────────────  B -> horizontal modulation
 *             //  │          └────────────────────────────────  G -> vertical displacement
 *             //  └───────────────────────────────────────────  R -> horizontal displacement
 *         };
 *         const float selector_o[] = {
 *             (xc.d_offset - .5f) * s.x,
 *             (yc.d_offset - .5f) * s.y,
 *                           xc.c_offset,
 *                           yc.c_offset,
 *         };
 *
 *         builder.uniform("selector_matrix") = selector_m;
 *         builder.uniform("selector_offset") = selector_o;
 *
 *         // TODO: RGB->HSL stage
 *         return builder.makeShader();
 *     }
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         fEffectShader = this->buildEffectShader(ic, ctm);
 *
 *         auto bounds = this->children()[0]->revalidate(ic, ctm);
 *         if (fExpandBounds) {
 *             // Expand the bounds to accommodate max displacement (which is |fScale|).
 *             bounds.outset(std::abs(fScale.x), std::abs(fScale.y));
 *         }
 *
 *         return bounds;
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         if (!fEffectShader) {
 *             // no displacement effect - just render the content
 *             this->children()[0]->render(canvas, ctx);
 *             return;
 *         }
 *
 *         auto local_ctx = ScopedRenderContext(canvas, ctx).setIsolation(this->bounds(),
 *                                                                        canvas->getTotalMatrix(),
 *                                                                        true);
 *         SkPaint shader_paint;
 *         shader_paint.setShader(fEffectShader);
 *
 *         canvas->drawRect(this->bounds(), shader_paint);
 *     }
 *
 *     SkTileMode displacementTileMode() const {
 *         return fPos == Pos::kTile
 *                 ? SkTileMode::kRepeat
 *                 : SkTileMode::kClamp;
 *     }
 *
 *     SkMatrix displacementMatrix() const {
 *         switch (fPos) {
 *             case Pos::kCenter:  return SkMatrix::Translate(
 *                                     (fChildSize.fWidth  - fDisplSize.fWidth ) / 2,
 *                                     (fChildSize.fHeight - fDisplSize.fHeight) / 2);
 *             case Pos::kStretch: return SkMatrix::Scale(
 *                                     fChildSize.fWidth  / fDisplSize.fWidth,
 *                                     fChildSize.fHeight / fDisplSize.fHeight);
 *             case Pos::kTile:    return SkMatrix::I();
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     const sk_sp<sksg::RenderNode> fDisplSource;
 *     const SkSize                  fDisplSize,
 *                                   fChildSize;
 *
 *     // Cached top-level shader
 *     sk_sp<SkShader>        fEffectShader;
 *
 *     SkV2                   fScale          = { 0, 0 };
 *     SkTileMode             fChildTileMode  = SkTileMode::kDecal;
 *     Pos                    fPos            = Pos::kCenter;
 *     Selector               fXSelector      = Selector::kR,
 *                            fYSelector      = Selector::kR;
 *     bool                   fExpandBounds   = false;
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class DisplacementNode public constructor(
  child: SkSp<RenderNode>,
  childSize: SkSize,
  displ: SkSp<RenderNode>,
  displSize: SkSize,
) : CustomRenderNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::RenderNode> fDisplSource
   * ```
   */
  private val fDisplSource: SkSp<RenderNode> = TODO("Initialize fDisplSource")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                  fDisplSize
   * ```
   */
  private val fDisplSize: SkSize = TODO("Initialize fDisplSize")

  /**
   * C++ original:
   * ```cpp
   * const SkSize                  fDisplSize,
   *                                   fChildSize
   * ```
   */
  private val fChildSize: SkSize = TODO("Initialize fChildSize")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>        fEffectShader
   * ```
   */
  private var fEffectShader: SkSp<SkShader> = TODO("Initialize fEffectShader")

  /**
   * C++ original:
   * ```cpp
   * SkV2                   fScale          = { 0, 0 }
   * ```
   */
  private var fScale: SkV2 = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode             fChildTileMode  = SkTileMode::kDecal
   * ```
   */
  private var fChildTileMode: SkTileMode = TODO("Initialize fChildTileMode")

  /**
   * C++ original:
   * ```cpp
   * Pos                    fPos            = Pos::kCenter
   * ```
   */
  private var fPos: Pos = TODO("Initialize fPos")

  /**
   * C++ original:
   * ```cpp
   * Selector               fXSelector      = Selector::kR
   * ```
   */
  private var fXSelector: Selector = TODO("Initialize fXSelector")

  /**
   * C++ original:
   * ```cpp
   * Selector               fXSelector      = Selector::kR,
   *                            fYSelector      = Selector::kR
   * ```
   */
  private var fYSelector: Selector = TODO("Initialize fYSelector")

  /**
   * C++ original:
   * ```cpp
   * bool                   fExpandBounds   = false
   * ```
   */
  private var fExpandBounds: Boolean = TODO("Initialize fExpandBounds")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> buildEffectShader(sksg::InvalidationController* ic, const SkMatrix& ctm) {
   *         // AE quirk: combining two const/generated modes does not displace - we need at
   *         // least one non-const selector to trigger the effect.  *shrug*
   *         if ((IsConst(fXSelector) && IsConst(fYSelector)) ||
   *             (SkScalarNearlyZero(fScale.x) && SkScalarNearlyZero(fScale.y))) {
   *             return nullptr;
   *         }
   *
   *         auto get_content_picture = [](const sk_sp<sksg::RenderNode>& node,
   *                                       sksg::InvalidationController* ic, const SkMatrix& ctm) {
   *             if (!node) {
   *                 return sk_sp<SkPicture>(nullptr);
   *             }
   *
   *             const auto bounds = node->revalidate(ic, ctm);
   *
   *             SkPictureRecorder recorder;
   *             node->render(recorder.beginRecording(bounds));
   *             return recorder.finishRecordingAsPicture();
   *         };
   *
   *         const auto child_content = get_content_picture(this->children()[0], ic, ctm),
   *                    displ_content = get_content_picture(fDisplSource, ic, ctm);
   *         if (!child_content || !displ_content) {
   *             return nullptr;
   *         }
   *
   *         const auto child_tile = SkRect::MakeSize(fChildSize);
   *         auto child_shader = child_content->makeShader(fChildTileMode,
   *                                                       fChildTileMode,
   *                                                       SkFilterMode::kLinear,
   *                                                       nullptr,
   *                                                       &child_tile);
   *
   *         const auto displ_tile   = SkRect::MakeSize(fDisplSize);
   *         const auto displ_mode   = this->displacementTileMode();
   *         const auto displ_matrix = this->displacementMatrix();
   *         auto displ_shader = displ_content->makeShader(displ_mode,
   *                                                       displ_mode,
   *                                                       SkFilterMode::kLinear,
   *                                                       &displ_matrix,
   *                                                       &displ_tile);
   *
   *         SkRuntimeShaderBuilder builder(displacement_effect_singleton());
   *         builder.child("child") = std::move(child_shader);
   *         builder.child("displ") = std::move(displ_shader);
   *
   *         const auto xc = Coeffs(fXSelector),
   *                    yc = Coeffs(fYSelector);
   *
   *         const auto s = fScale * 2;
   *
   *         const float selector_m[] = {
   *             xc.dr*s.x, yc.dr*s.y,          0,          0,
   *             xc.dg*s.x, yc.dg*s.y,          0,          0,
   *             xc.db*s.x, yc.db*s.y,          0,          0,
   *             xc.da*s.x, yc.da*s.y, xc.c_scale, yc.c_scale,
   *
   *             //  │          │               │           └────  A -> vertical modulation
   *             //  │          │               └────────────────  B -> horizontal modulation
   *             //  │          └────────────────────────────────  G -> vertical displacement
   *             //  └───────────────────────────────────────────  R -> horizontal displacement
   *         };
   *         const float selector_o[] = {
   *             (xc.d_offset - .5f) * s.x,
   *             (yc.d_offset - .5f) * s.y,
   *                           xc.c_offset,
   *                           yc.c_offset,
   *         };
   *
   *         builder.uniform("selector_matrix") = selector_m;
   *         builder.uniform("selector_offset") = selector_o;
   *
   *         // TODO: RGB->HSL stage
   *         return builder.makeShader();
   *     }
   * ```
   */
  private fun buildEffectShader(ic: InvalidationController?, ctm: SkMatrix): SkSp<SkShader> {
    TODO("Implement buildEffectShader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         fEffectShader = this->buildEffectShader(ic, ctm);
   *
   *         auto bounds = this->children()[0]->revalidate(ic, ctm);
   *         if (fExpandBounds) {
   *             // Expand the bounds to accommodate max displacement (which is |fScale|).
   *             bounds.outset(std::abs(fScale.x), std::abs(fScale.y));
   *         }
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
   *         if (!fEffectShader) {
   *             // no displacement effect - just render the content
   *             this->children()[0]->render(canvas, ctx);
   *             return;
   *         }
   *
   *         auto local_ctx = ScopedRenderContext(canvas, ctx).setIsolation(this->bounds(),
   *                                                                        canvas->getTotalMatrix(),
   *                                                                        true);
   *         SkPaint shader_paint;
   *         shader_paint.setShader(fEffectShader);
   *
   *         canvas->drawRect(this->bounds(), shader_paint);
   *     }
   * ```
   */
  public override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode displacementTileMode() const {
   *         return fPos == Pos::kTile
   *                 ? SkTileMode::kRepeat
   *                 : SkTileMode::kClamp;
   *     }
   * ```
   */
  private fun displacementTileMode(): SkTileMode {
    TODO("Implement displacementTileMode")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix displacementMatrix() const {
   *         switch (fPos) {
   *             case Pos::kCenter:  return SkMatrix::Translate(
   *                                     (fChildSize.fWidth  - fDisplSize.fWidth ) / 2,
   *                                     (fChildSize.fHeight - fDisplSize.fHeight) / 2);
   *             case Pos::kStretch: return SkMatrix::Scale(
   *                                     fChildSize.fWidth  / fDisplSize.fWidth,
   *                                     fChildSize.fHeight / fDisplSize.fHeight);
   *             case Pos::kTile:    return SkMatrix::I();
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  private fun displacementMatrix(): SkMatrix {
    TODO("Implement displacementMatrix")
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

  public data class SelectorCoeffs public constructor(
    public var dr: Float,
    public var dg: Float,
    public var db: Float,
    public var da: Float,
    public var dOffset: Float,
    public var cScale: Float,
    public var cOffset: Float,
  )

  public enum class Pos {
    kCenter,
    kStretch,
    kTile,
    kLast,
  }

  public enum class Selector {
    kR,
    kG,
    kB,
    kA,
    kLuminance,
    kHue,
    kLightness,
    kSaturation,
    kFull,
    kHalf,
    kOff,
    kLast,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<DisplacementNode> Make(sk_sp<RenderNode> child,
     *                                         const SkSize& child_size,
     *                                         sk_sp<RenderNode> displ,
     *                                         const SkSize& displ_size) {
     *         if (!child || !displ) {
     *             return nullptr;
     *         }
     *
     *         return sk_sp<DisplacementNode>(new DisplacementNode(std::move(child), child_size,
     *                                                             std::move(displ), displ_size));
     *     }
     * ```
     */
    public fun make(
      child: SkSp<RenderNode>,
      childSize: SkSize,
      displ: SkSp<RenderNode>,
      displSize: SkSize,
    ): SkSp<DisplacementNode> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SelectorCoeffs Coeffs(Selector sel) {
     *         // D = displacement input
     *         // C = displacement coverage
     *         static constexpr SelectorCoeffs gCoeffs[] = {
     *             { 1,0,0,0,0,   1,0 },   // kR: D = r, C = a
     *             { 0,1,0,0,0,   1,0 },   // kG: D = g, C = a
     *             { 0,0,1,0,0,   1,0 },   // kB: D = b, C = a
     *             { 0,0,0,1,0,   0,1 },   // kA: D = a, C = 1.0
     *             { SK_LUM_COEFF_R,SK_LUM_COEFF_G, SK_LUM_COEFF_B,0,0,   1,0},
     *                                     // kLuminance: D = lum(rgb), C = a
     *             { 1,0,0,0,0,   0,1 },   // kH: D = h, C = 1.0   (HSLA)
     *             { 0,1,0,0,0,   0,1 },   // kL: D = l, C = 1.0   (HSLA)
     *             { 0,0,1,0,0,   0,1 },   // kS: D = s, C = 1.0   (HSLA)
     *             { 0,0,0,0,1,   0,1 },   // kFull: D = 1.0, C = 1.0
     *             { 0,0,0,0,.5f, 0,1 },   // kHalf: D = 0.5, C = 1.0
     *             { 0,0,0,0,0,   0,1 },   // kOff:  D = 0.0, C = 1.0
     *         };
     *
     *         const auto i = static_cast<size_t>(sel);
     *         SkASSERT(i < std::size(gCoeffs));
     *
     *         return gCoeffs[i];
     *     }
     * ```
     */
    private fun coeffs(sel: Selector): SelectorCoeffs {
      TODO("Implement coeffs")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsConst(Selector s) {
     *         return s == Selector::kFull
     *             || s == Selector::kHalf
     *             || s == Selector::kOff;
     *     }
     * ```
     */
    private fun isConst(s: Selector): Boolean {
      TODO("Implement isConst")
    }
  }
}
