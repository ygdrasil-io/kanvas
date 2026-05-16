package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkShader

/**
 * C++ original:
 * ```cpp
 * class ShadingParams {
 * public:
 *     // NOTE: Does not copy `paint`, `nonMSAAClip` or `clipShader`; these must outlive ShadingParams.
 *     ShadingParams(const Caps* caps,
 *                   const PaintParams& paint,
 *                   const NonMSAAClip& nonMSAAClip,
 *                   const SkShader* clipShader,
 *                   Coverage coverage,
 *                   TextureFormat targetFormat);
 *
 *     Coverage rendererCoverage()  const { return fRendererCoverage; }
 *     bool dstReadRequired() const { return SkToBool(fDstUsage & DstUsage::kDstReadRequired); }
 *
 *     using Result = std::tuple<UniquePaintParamsID, SkEnumBitMask<DstUsage>>;
 *     std::optional<Result> toKey(const KeyContext&) const;
 *
 * private:
 *     bool addPaintColorToKey(const KeyContext&) const;
 *     bool handlePrimitiveColor(const KeyContext&) const;
 *     bool handlePaintAlpha(const KeyContext&) const;
 *     bool handleColorFilter(const KeyContext&) const;
 *     bool handleDithering(const KeyContext&) const;
 *     bool handleDstRead(const KeyContext&) const;
 *     void handleClipping(const KeyContext&) const;
 *
 *     const PaintParams&      fPaint;
 *     const NonMSAAClip&      fNonMSAAClip;
 *     const SkShader*         fClipShader;
 *
 *     Coverage                fRendererCoverage;
 *     TextureFormat           fTargetFormat;
 *     SkEnumBitMask<DstUsage> fDstUsage;
 * }
 * ```
 */
public data class ShadingParams public constructor(
  /**
   * C++ original:
   * ```cpp
   * const PaintParams&      fPaint
   * ```
   */
  private val fPaint: PaintParams,
  /**
   * C++ original:
   * ```cpp
   * const NonMSAAClip&      fNonMSAAClip
   * ```
   */
  private val fNonMSAAClip: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkShader*         fClipShader
   * ```
   */
  private val fClipShader: SkShader?,
  /**
   * C++ original:
   * ```cpp
   * Coverage                fRendererCoverage
   * ```
   */
  private var fRendererCoverage: Int,
  /**
   * C++ original:
   * ```cpp
   * TextureFormat           fTargetFormat
   * ```
   */
  private var fTargetFormat: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<DstUsage> fDstUsage
   * ```
   */
  private var fDstUsage: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * Coverage rendererCoverage()  const { return fRendererCoverage; }
   * ```
   */
  public fun rendererCoverage(): Int {
    TODO("Implement rendererCoverage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool dstReadRequired() const { return SkToBool(fDstUsage & DstUsage::kDstReadRequired); }
   * ```
   */
  public fun dstReadRequired(): Boolean {
    TODO("Implement dstReadRequired")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<ShadingParams::Result> ShadingParams::toKey(const KeyContext& keyContext) const {
   *     // Root Node 0 is the source color, which is the output of all effects post dithering
   *     bool isOpaque = this->handleDithering(keyContext);
   *
   *     // Root Node 1 is the final blender
   *     bool dependsOnDst = fRendererCoverage != Coverage::kNone;
   *     if (fPaint.finalBlender()) {
   *         AddToKey(keyContext, fPaint.finalBlender());
   *         // Cannot inspect runtime blenders to pessimistically assume they will always use the dst.
   *         dependsOnDst = true;
   *     } else {
   *         if (!(fDstUsage & DstUsage::kDstReadRequired)) {
   *             // With no shader blending, be as explicit as possible about the final blend
   *             AddFixedBlendMode(keyContext, fPaint.finalBlendMode());
   *         } else {
   *             // With shader blending, use AddBlendMode() to select the more universal blend functions
   *             // when possible. Technically we could always use a fixed blend mode but would then
   *             // over-generate when encountering certain classes of blends. This is most problematic
   *             // on devices that wouldn't support dual-source blending, so help them out by at least
   *             // not requiring lots of pipelines.
   *             AddBlendMode(keyContext, fPaint.finalBlendMode());
   *         }
   *
   *         // Blend modes can be analyzed to determine if specific src colors still depend on the dst.
   *         dependsOnDst |= blendmode_depends_on_dst(fPaint.finalBlendMode(), isOpaque);
   *     }
   *
   *     // Optional Root Node 2 is the clip
   *     this->handleClipping(keyContext);
   *
   *     UniquePaintParamsID paintID =
   *             keyContext.recorder()->priv().shaderCodeDictionary()->findOrCreate(
   *                     keyContext.paintParamsKeyBuilder());
   *
   *     if (!paintID.isValid()) {
   *         return {};
   *     } else {
   *         return Result{paintID,
   *                       fDstUsage | (dependsOnDst ? DstUsage::kDependsOnDst : DstUsage::kNone)};
   *     }
   * }
   * ```
   */
  public fun toKey(keyContext: KeyContext): Int {
    TODO("Implement toKey")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShadingParams::addPaintColorToKey(const KeyContext& keyContext) const {
   *     const auto& simpleImage = fPaint.imageShader();
   *     if (simpleImage) {
   *         // There is an implicit image shader, match handling of SkModifyPaintForDrawImageRect
   *         if (fPaint.shader()) {
   *             // Alpha-only images for drawImageRect() get colorized with the paint's shader. This
   *             // differs from alpha-only image shaders that might be encountered within an SkShader
   *             // graph, which get colorized by the paint's opaque color.
   *             SkASSERT(SkColorTypeIsAlphaOnly(simpleImage->fImage->colorType()));
   *             Blend(keyContext,
   *                   /* addBlendToKey */ [&] () -> void {
   *                       AddFixedBlendMode(keyContext, SkBlendMode::kDstIn);
   *                   },
   *                   /* addSrcToKey = */ [&] () -> void {
   *                       // Since colorization is handled here, disable paint color-colorization later.
   *                       AddToKey(keyContext.withExtraFlags(
   *                                        KeyGenFlags::kDisableAlphaOnlyImageColorization),
   *                                *simpleImage);
   *                   },
   *                   /* addDstToKey = */ [&] () -> void {
   *                       AddToKey(keyContext, fPaint.shader());
   *                   });
   *             return false; // Colorizing with an alpha-only texture probably isn't opaque
   *         } else {
   *             // Encode the image structure directly, which includes handling alpha-only images that
   *             // combine with the paint's color (RGB1) stored on `keyContext`.
   *             AddToKey(keyContext, *simpleImage);
   *             return simpleImage->fImage->isOpaque();
   *         }
   *     } else if (fPaint.shader()) {
   *         AddToKey(keyContext, fPaint.shader());
   *         return fPaint.shader()->isOpaque();
   *     } else {
   *         RGBPaintColorBlock::AddBlock(keyContext);
   *         return true; // rgb1, always opaque
   *     }
   * }
   * ```
   */
  private fun addPaintColorToKey(keyContext: KeyContext): Boolean {
    TODO("Implement addPaintColorToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShadingParams::handlePrimitiveColor(const KeyContext& keyContext) const {
   *     // If no primitive blending is required, simply add the paint color.
   *     if (!fPaint.primitiveBlender()) {
   *         return this->addPaintColorToKey(keyContext);
   *     }
   *
   *     // If no color space conversion is required and the primitive blend mode is kDst, the src
   *     // branch of the blend does not matter and we can simply emit the primitive color.
   *     std::optional<SkBlendMode> primBlend = as_BB(fPaint.primitiveBlender())->asBlendMode();
   *     const bool canSkipBlendStep = fPaint.skipPrimitiveColorXform() &&
   *                                   primBlend == SkBlendMode::kDst;
   *
   *     if (canSkipBlendStep) {
   *         AddPrimitiveColor(keyContext, fPaint.skipPrimitiveColorXform());
   *         return false;
   *     }
   *
   *     bool srcIsOpaque = false;
   *     Blend(keyContext,
   *         /* addBlendToKey= */ [&] () -> void {
   *             AddToKey(keyContext, fPaint.primitiveBlender());
   *         },
   *         /* addSrcToKey= */ [&] () -> void {
   *             srcIsOpaque = this->addPaintColorToKey(keyContext);
   *         },
   *         /* addDstToKey= */ [&] () -> void {
   *             AddPrimitiveColor(keyContext, fPaint.skipPrimitiveColorXform());
   *         });
   *     if (primBlend.has_value() && srcIsOpaque) {
   *         // If the input paint/shader is opaque, the result is only opaque if the primitive blend
   *         // mode is kSrc or kSrcOver. All other modes can introduce transparency.
   *         return *primBlend == SkBlendMode::kSrc || *primBlend == SkBlendMode::kSrcOver;
   *     }
   *
   *     // If the input was already transparent, or if it's a runtime/complex blend mode,
   *     // the result cannot be considered opaque.
   *     return false;
   * }
   * ```
   */
  private fun handlePrimitiveColor(keyContext: KeyContext): Boolean {
    TODO("Implement handlePrimitiveColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShadingParams::handlePaintAlpha(const KeyContext& keyContext) const {
   *     if (!fPaint.shader() && !fPaint.imageShader() && !fPaint.primitiveBlender()) {
   *         // If there is no shader and no primitive blending the input to the colorFilter stage
   *         // is just the premultiplied paint color.
   *         SkPMColor4f paintColor = PaintParams::Color4fPrepForDst(fPaint.color(),
   *                                                                 keyContext.dstColorInfo()).premul();
   *         SolidColorShaderBlock::AddBlock(keyContext, paintColor);
   *         return fPaint.color().isOpaque();
   *     }
   *
   *     if (!fPaint.color().isOpaque()) {
   *         Blend(keyContext,
   *               /* addBlendToKey= */ [&] () -> void {
   *                   AddFixedBlendMode(keyContext, SkBlendMode::kSrcIn);
   *               },
   *               /* addSrcToKey= */ [&]() -> void {
   *                   this->handlePrimitiveColor(keyContext);
   *               },
   *               /* addDstToKey= */ [&]() -> void {
   *                   AlphaOnlyPaintColorBlock::AddBlock(keyContext);
   *               });
   *         // The result is guaranteed to be non-opaque because we're blending with fColor's alpha.
   *         return false;
   *     } else {
   *         return this->handlePrimitiveColor(keyContext);
   *     }
   * }
   * ```
   */
  private fun handlePaintAlpha(keyContext: KeyContext): Boolean {
    TODO("Implement handlePaintAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShadingParams::handleColorFilter(const KeyContext& keyContext) const {
   *     if (fPaint.colorFilter()) {
   *         bool srcIsOpaque = false;
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     srcIsOpaque = this->handlePaintAlpha(keyContext);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     AddToKey(keyContext, fPaint.colorFilter());
   *                 });
   *         return srcIsOpaque && fPaint.colorFilter()->isAlphaUnchanged();
   *     } else {
   *         return this->handlePaintAlpha(keyContext);
   *     }
   * }
   * ```
   */
  private fun handleColorFilter(keyContext: KeyContext): Boolean {
    TODO("Implement handleColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ShadingParams::handleDithering(const KeyContext& keyContext) const {
   *
   * #ifndef SK_IGNORE_GPU_DITHER
   *     SkColorType ct = keyContext.dstColorInfo().colorType();
   *     if (should_dither(fPaint, ct)) {
   *         bool srcIsOpaque = false;
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     srcIsOpaque = this->handleColorFilter(keyContext);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     AddDitherBlock(keyContext, ct);
   *                 });
   *         return srcIsOpaque;
   *     } else
   * #endif
   *     {
   *         return this->handleColorFilter(keyContext);
   *     }
   * }
   * ```
   */
  private fun handleDithering(keyContext: KeyContext): Boolean {
    TODO("Implement handleDithering")
  }

  /**
   * C++ original:
   * ```cpp
   * bool handleDstRead(const KeyContext&) const
   * ```
   */
  private fun handleDstRead(param0: KeyContext): Boolean {
    TODO("Implement handleDstRead")
  }

  /**
   * C++ original:
   * ```cpp
   * void ShadingParams::handleClipping(const KeyContext& keyContext) const {
   *     if (!fNonMSAAClip.isEmpty()) {
   *         const AnalyticClip& analyticClip = fNonMSAAClip.fAnalyticClip;
   *         SkPoint radiusPair;
   *         SkRect analyticBounds;
   *         if (!analyticClip.isEmpty()) {
   *             float radius = analyticClip.fRadius + 0.5f;
   *             // N.B.: Because the clip data is normally used with depth-based clipping,
   *             // the shape is inverted from its usual state. We re-invert here to
   *             // match what the shader snippet expects.
   *             radiusPair = {(analyticClip.fInverted) ? radius : -radius, 1.0f/radius};
   *             analyticBounds = analyticClip.fBounds.makeOutset(0.5f).asSkRect();
   *         } else {
   *             // This will generate no analytic clip.
   *             radiusPair = { -0.5f, 1.f };
   *             analyticBounds = { 0, 0, 0, 0 };
   *         }
   *
   *         const AtlasClip& atlasClip = fNonMSAAClip.fAtlasClip;
   *         SkISize maskSize = atlasClip.fMaskBounds.size();
   *         SkRect texMaskBounds = SkRect::MakeXYWH(atlasClip.fOutPos.x(), atlasClip.fOutPos.y(),
   *                                                 maskSize.width(), maskSize.height());
   *         // Outset bounds to capture some of the padding (necessary for inverse clip)
   *         texMaskBounds.outset(0.5f, 0.5f);
   *         SkPoint texCoordOffset = SkPoint::Make(atlasClip.fOutPos.x() - atlasClip.fMaskBounds.left(),
   *                                                atlasClip.fOutPos.y() - atlasClip.fMaskBounds.top());
   *
   *         NonMSAAClipBlock::NonMSAAClipData data(
   *                 analyticBounds,
   *                 radiusPair,
   *                 analyticClip.edgeSelectRect(),
   *                 texCoordOffset,
   *                 texMaskBounds,
   *                 atlasClip.fAtlasTexture);
   *         if (fClipShader) {
   *             // For both an analytic clip and clip shader, we need to compose them together into
   *             // a single clipping root node.
   *             Blend(keyContext,
   *                   /* addBlendToKey= */ [&]() -> void {
   *                       AddFixedBlendMode(keyContext, SkBlendMode::kModulate);
   *                   },
   *                   /* addSrcToKey= */ [&]() -> void {
   *                       NonMSAAClipBlock::AddBlock(keyContext, data);
   *                   },
   *                   /* addDstToKey= */ [&]() -> void {
   *                       AddToKey(keyContext, fClipShader);
   *                   });
   *         } else {
   *             // Without a clip shader, the analytic clip can be the clipping root node.
   *             NonMSAAClipBlock::AddBlock(keyContext, data);
   *         }
   *     } else if (fClipShader) {
   *         // Since there's no analytic clip, the clipping root node can be fClipShader directly.
   *         AddToKey(keyContext, fClipShader);
   *     }
   * }
   * ```
   */
  private fun handleClipping(keyContext: KeyContext) {
    TODO("Implement handleClipping")
  }
}
