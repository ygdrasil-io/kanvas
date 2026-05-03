package org.skia.modules

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize
import undefined.RenderContext

/**
 * C++ original:
 * ```cpp
 * class TileRenderNode final : public sksg::CustomRenderNode {
 * public:
 *     TileRenderNode(const SkSize& size, sk_sp<sksg::RenderNode> layer)
 *         : INHERITED({std::move(layer)})
 *         , fLayerSize(size) {}
 *
 *     SG_ATTRIBUTE(TileCenter     , SkPoint , fTileCenter     )
 *     SG_ATTRIBUTE(TileWidth      , SkScalar, fTileW          )
 *     SG_ATTRIBUTE(TileHeight     , SkScalar, fTileH          )
 *     SG_ATTRIBUTE(OutputWidth    , SkScalar, fOutputW        )
 *     SG_ATTRIBUTE(OutputHeight   , SkScalar, fOutputH        )
 *     SG_ATTRIBUTE(Phase          , SkScalar, fPhase          )
 *     SG_ATTRIBUTE(MirrorEdges    , bool    , fMirrorEdges    )
 *     SG_ATTRIBUTE(HorizontalPhase, bool    , fHorizontalPhase)
 *
 * protected:
 *     const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; } // no hit-testing
 *
 *     SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
 *         // Re-record the layer picture if needed.
 *         if (!fLayerPicture || this->hasChildrenInval()) {
 *             SkASSERT(this->children().size() == 1ul);
 *             const auto& layer = this->children()[0];
 *
 *             layer->revalidate(ic, ctm);
 *
 *             SkPictureRecorder recorder;
 *             layer->render(recorder.beginRecording(fLayerSize.width(), fLayerSize.height()));
 *             fLayerPicture = recorder.finishRecordingAsPicture();
 *         }
 *
 *         // tileW and tileH use layer size percentage units.
 *         const auto tileW = SkTPin(fTileW, 0.0f, 100.0f) * 0.01f * fLayerSize.width(),
 *                    tileH = SkTPin(fTileH, 0.0f, 100.0f) * 0.01f * fLayerSize.height();
 *         const auto tile_size = SkSize::Make(std::max(tileW, 1.0f),
 *                                             std::max(tileH, 1.0f));
 *         const auto tile  = SkRect::MakeXYWH(fTileCenter.fX - 0.5f * tile_size.width(),
 *                                             fTileCenter.fY - 0.5f * tile_size.height(),
 *                                             tile_size.width(),
 *                                             tile_size.height());
 *
 *         const auto layerShaderMatrix = SkMatrix::RectToRectOrIdentity(
 *                     SkRect::MakeWH(fLayerSize.width(), fLayerSize.height()), tile);
 *
 *         const auto tm = fMirrorEdges ? SkTileMode::kMirror : SkTileMode::kRepeat;
 *         auto layer_shader = fLayerPicture->makeShader(tm, tm, SkFilterMode::kLinear,
 *                                                       &layerShaderMatrix, nullptr);
 *
 *         if (fPhase && layer_shader && tile.isFinite()) {
 *             // To implement AE phase semantics, we construct a mask shader for the pass-through
 *             // rows/columns.  We then draw the layer content through this mask, and then again
 *             // through the inverse mask with a phase shift.
 *             const auto phase_vec = fHorizontalPhase
 *                     ? SkVector::Make(tile.width(), 0)
 *                     : SkVector::Make(0, tile.height());
 *             const auto phase_shift = SkVector::Make(phase_vec.fX, phase_vec.fY)
 *                                      * std::fmod(fPhase * (1/360.0f), 1);
 *             const auto phase_shader_matrix = SkMatrix::Translate(phase_shift.x(), phase_shift.y());
 *
 *             // The mask is generated using a step gradient shader, spanning 2 x tile width/height,
 *             // and perpendicular to the phase vector.
 *             static constexpr SkColor4f colors[] = { {1, 1, 1, 1}, {0, 0, 0, 0} };
 *             static constexpr float        pos[] = {       0.5f,       0.5f };
 *
 *             const SkPoint pts[] = {{ tile.x(), tile.y() },
 *                                    { tile.x() + 2 * (tile.width()  - phase_vec.fX),
 *                                      tile.y() + 2 * (tile.height() - phase_vec.fY) }};
 *
 *             auto mask_shader = SkShaders::LinearGradient(pts,
 *                                                          {{colors, pos, SkTileMode::kRepeat}, {}});
 *
 *             // First drawing pass: in-place masked layer content.
 *             fMainPassShader  = SkShaders::Blend(SkBlendMode::kSrcIn , mask_shader, layer_shader);
 *             // Second pass: phased-shifted layer content, with an inverse mask.
 *             fPhasePassShader = SkShaders::Blend(SkBlendMode::kSrcOut, mask_shader, layer_shader)
 *                                ->makeWithLocalMatrix(phase_shader_matrix);
 *         } else {
 *             fMainPassShader  = std::move(layer_shader);
 *             fPhasePassShader = nullptr;
 *         }
 *
 *         // outputW and outputH also use layer size percentage units.
 *         const auto outputW = fOutputW * 0.01f * fLayerSize.width(),
 *                    outputH = fOutputH * 0.01f * fLayerSize.height();
 *
 *         return SkRect::MakeXYWH((fLayerSize.width()  - outputW) * 0.5f,
 *                                 (fLayerSize.height() - outputH) * 0.5f,
 *                                 outputW, outputH);
 *     }
 *
 *     void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
 *         // AE allow one of the tile dimensions to collapse, but not both.
 *         if (this->bounds().isEmpty() || (fTileW <= 0 && fTileH <= 0)) {
 *             return;
 *         }
 *
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         if (ctx) {
 *             // apply any pending paint effects via the shader paint
 *             ctx->modulatePaint(canvas->getLocalToDeviceAs3x3(), &paint);
 *         }
 *
 *         paint.setShader(fMainPassShader);
 *         canvas->drawRect(this->bounds(), paint);
 *
 *         if (fPhasePassShader) {
 *             paint.setShader(fPhasePassShader);
 *             canvas->drawRect(this->bounds(), paint);
 *         }
 *     }
 *
 * private:
 *     const SkSize fLayerSize;
 *
 *     SkPoint  fTileCenter      = { 0, 0 };
 *     SkScalar fTileW           = 1,
 *              fTileH           = 1,
 *              fOutputW         = 1,
 *              fOutputH         = 1,
 *              fPhase           = 0;
 *     bool     fMirrorEdges     = false;
 *     bool     fHorizontalPhase = false;
 *
 *     // These are computed/cached on revalidation.
 *     sk_sp<SkPicture> fLayerPicture;      // cached picture for layer content
 *     sk_sp<SkShader>  fMainPassShader,    // shader for the main tile(s)
 *                      fPhasePassShader;   // shader for the phased tile(s)
 *
 *     using INHERITED = sksg::CustomRenderNode;
 * }
 * ```
 */
public class TileRenderNode public constructor(
  size: SkSize,
  layer: SkSp<RenderNode>,
) : CustomRenderNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkSize fLayerSize
   * ```
   */
  private val fLayerSize: SkSize = TODO("Initialize fLayerSize")

  /**
   * C++ original:
   * ```cpp
   * SkPoint  fTileCenter      = { 0, 0 }
   * ```
   */
  private var fTileCenter: SkPoint = TODO("Initialize fTileCenter")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fTileW           = 1
   * ```
   */
  private var fTileW: SkScalar = TODO("Initialize fTileW")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fTileW           = 1,
   *              fTileH           = 1
   * ```
   */
  private var fTileH: SkScalar = TODO("Initialize fTileH")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fTileW           = 1,
   *              fTileH           = 1,
   *              fOutputW         = 1
   * ```
   */
  private var fOutputW: SkScalar = TODO("Initialize fOutputW")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fTileW           = 1,
   *              fTileH           = 1,
   *              fOutputW         = 1,
   *              fOutputH         = 1
   * ```
   */
  private var fOutputH: SkScalar = TODO("Initialize fOutputH")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fTileW           = 1,
   *              fTileH           = 1,
   *              fOutputW         = 1,
   *              fOutputH         = 1,
   *              fPhase           = 0
   * ```
   */
  private var fPhase: SkScalar = TODO("Initialize fPhase")

  /**
   * C++ original:
   * ```cpp
   * bool     fMirrorEdges     = false
   * ```
   */
  private var fMirrorEdges: Boolean = TODO("Initialize fMirrorEdges")

  /**
   * C++ original:
   * ```cpp
   * bool     fHorizontalPhase = false
   * ```
   */
  private var fHorizontalPhase: Boolean = TODO("Initialize fHorizontalPhase")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fLayerPicture
   * ```
   */
  private var fLayerPicture: SkSp<SkPicture> = TODO("Initialize fLayerPicture")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>  fMainPassShader
   * ```
   */
  private var fMainPassShader: SkSp<SkShader> = TODO("Initialize fMainPassShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>  fMainPassShader,    // shader for the main tile(s)
   *                      fPhasePassShader
   * ```
   */
  private var fPhasePassShader: SkSp<SkShader> = TODO("Initialize fPhasePassShader")

  /**
   * C++ original:
   * ```cpp
   * const RenderNode* onNodeAt(const SkPoint&) const override { return nullptr; }
   * ```
   */
  protected override fun onNodeAt(param0: SkPoint): RenderNode {
    TODO("Implement onNodeAt")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect onRevalidate(sksg::InvalidationController* ic, const SkMatrix& ctm) override {
   *         // Re-record the layer picture if needed.
   *         if (!fLayerPicture || this->hasChildrenInval()) {
   *             SkASSERT(this->children().size() == 1ul);
   *             const auto& layer = this->children()[0];
   *
   *             layer->revalidate(ic, ctm);
   *
   *             SkPictureRecorder recorder;
   *             layer->render(recorder.beginRecording(fLayerSize.width(), fLayerSize.height()));
   *             fLayerPicture = recorder.finishRecordingAsPicture();
   *         }
   *
   *         // tileW and tileH use layer size percentage units.
   *         const auto tileW = SkTPin(fTileW, 0.0f, 100.0f) * 0.01f * fLayerSize.width(),
   *                    tileH = SkTPin(fTileH, 0.0f, 100.0f) * 0.01f * fLayerSize.height();
   *         const auto tile_size = SkSize::Make(std::max(tileW, 1.0f),
   *                                             std::max(tileH, 1.0f));
   *         const auto tile  = SkRect::MakeXYWH(fTileCenter.fX - 0.5f * tile_size.width(),
   *                                             fTileCenter.fY - 0.5f * tile_size.height(),
   *                                             tile_size.width(),
   *                                             tile_size.height());
   *
   *         const auto layerShaderMatrix = SkMatrix::RectToRectOrIdentity(
   *                     SkRect::MakeWH(fLayerSize.width(), fLayerSize.height()), tile);
   *
   *         const auto tm = fMirrorEdges ? SkTileMode::kMirror : SkTileMode::kRepeat;
   *         auto layer_shader = fLayerPicture->makeShader(tm, tm, SkFilterMode::kLinear,
   *                                                       &layerShaderMatrix, nullptr);
   *
   *         if (fPhase && layer_shader && tile.isFinite()) {
   *             // To implement AE phase semantics, we construct a mask shader for the pass-through
   *             // rows/columns.  We then draw the layer content through this mask, and then again
   *             // through the inverse mask with a phase shift.
   *             const auto phase_vec = fHorizontalPhase
   *                     ? SkVector::Make(tile.width(), 0)
   *                     : SkVector::Make(0, tile.height());
   *             const auto phase_shift = SkVector::Make(phase_vec.fX, phase_vec.fY)
   *                                      * std::fmod(fPhase * (1/360.0f), 1);
   *             const auto phase_shader_matrix = SkMatrix::Translate(phase_shift.x(), phase_shift.y());
   *
   *             // The mask is generated using a step gradient shader, spanning 2 x tile width/height,
   *             // and perpendicular to the phase vector.
   *             static constexpr SkColor4f colors[] = { {1, 1, 1, 1}, {0, 0, 0, 0} };
   *             static constexpr float        pos[] = {       0.5f,       0.5f };
   *
   *             const SkPoint pts[] = {{ tile.x(), tile.y() },
   *                                    { tile.x() + 2 * (tile.width()  - phase_vec.fX),
   *                                      tile.y() + 2 * (tile.height() - phase_vec.fY) }};
   *
   *             auto mask_shader = SkShaders::LinearGradient(pts,
   *                                                          {{colors, pos, SkTileMode::kRepeat}, {}});
   *
   *             // First drawing pass: in-place masked layer content.
   *             fMainPassShader  = SkShaders::Blend(SkBlendMode::kSrcIn , mask_shader, layer_shader);
   *             // Second pass: phased-shifted layer content, with an inverse mask.
   *             fPhasePassShader = SkShaders::Blend(SkBlendMode::kSrcOut, mask_shader, layer_shader)
   *                                ->makeWithLocalMatrix(phase_shader_matrix);
   *         } else {
   *             fMainPassShader  = std::move(layer_shader);
   *             fPhasePassShader = nullptr;
   *         }
   *
   *         // outputW and outputH also use layer size percentage units.
   *         const auto outputW = fOutputW * 0.01f * fLayerSize.width(),
   *                    outputH = fOutputH * 0.01f * fLayerSize.height();
   *
   *         return SkRect::MakeXYWH((fLayerSize.width()  - outputW) * 0.5f,
   *                                 (fLayerSize.height() - outputH) * 0.5f,
   *                                 outputW, outputH);
   *     }
   * ```
   */
  protected override fun onRevalidate(ic: InvalidationController?, ctm: SkMatrix): SkRect {
    TODO("Implement onRevalidate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onRender(SkCanvas* canvas, const RenderContext* ctx) const override {
   *         // AE allow one of the tile dimensions to collapse, but not both.
   *         if (this->bounds().isEmpty() || (fTileW <= 0 && fTileH <= 0)) {
   *             return;
   *         }
   *
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *
   *         if (ctx) {
   *             // apply any pending paint effects via the shader paint
   *             ctx->modulatePaint(canvas->getLocalToDeviceAs3x3(), &paint);
   *         }
   *
   *         paint.setShader(fMainPassShader);
   *         canvas->drawRect(this->bounds(), paint);
   *
   *         if (fPhasePassShader) {
   *             paint.setShader(fPhasePassShader);
   *             canvas->drawRect(this->bounds(), paint);
   *         }
   *     }
   * ```
   */
  protected override fun onRender(canvas: SkCanvas?, ctx: RenderContext?) {
    TODO("Implement onRender")
  }
}
