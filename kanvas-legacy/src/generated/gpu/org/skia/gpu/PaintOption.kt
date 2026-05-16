package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorType

/**
 * C++ original:
 * ```cpp
 * class PaintOption {
 * public:
 *     PaintOption(bool opaquePaintColor,
 *                 const std::pair<sk_sp<PrecompileBlender>, int>& finalBlender,
 *                 const std::pair<sk_sp<PrecompileShader>, int>& shader,
 *                 const std::pair<sk_sp<PrecompileColorFilter>, int>& colorFilter,
 *                 bool hasPrimitiveBlender,
 *                 SkBlendMode primitiveBlendMode,
 *                 bool skipColorXform,
 *                 const std::pair<sk_sp<PrecompileShader>, int>& clipShader,
 *                 Coverage coverage,
 *                 TextureFormat targetFormat,
 *                 bool dither,
 *                 bool analyticClip);
 *
 *     const PrecompileBlender* finalBlender() const { return fFinalBlender.first.get(); }
 *
 *     void toKey(const KeyContext&) const;
 *
 * private:
 *     void addPaintColorToKey(const KeyContext&) const;
 *     void handlePrimitiveColor(const KeyContext&) const;
 *     void handlePaintAlpha(const KeyContext&) const;
 *     void handleColorFilter(const KeyContext&) const;
 *     bool shouldDither(SkColorType dstCT) const;
 *     void handleDithering(const KeyContext&) const;
 *     void handleClipping(const KeyContext&) const;
 *
 *     bool fOpaquePaintColor;
 *     std::pair<sk_sp<PrecompileBlender>, int> fFinalBlender;
 *     std::pair<sk_sp<PrecompileShader>, int> fShader;
 *     std::pair<sk_sp<PrecompileColorFilter>, int> fColorFilter;
 *     SkBlendMode fPrimitiveBlendMode;
 *     bool fHasPrimitiveBlender;
 *     bool fSkipColorXform;
 *     std::pair<sk_sp<PrecompileShader>, int> fClipShader;
 *     Coverage fRendererCoverage;
 *     TextureFormat fTargetFormat;
 *     bool fDither;
 *     bool fAnalyticClip;
 * }
 * ```
 */
public data class PaintOption public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fOpaquePaintColor
   * ```
   */
  private var fOpaquePaintColor: Boolean,
  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<PrecompileBlender>, int> fFinalBlender
   * ```
   */
  private var fFinalBlender: Int,
  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<PrecompileShader>, int> fShader
   * ```
   */
  private var fShader: Int,
  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<PrecompileColorFilter>, int> fColorFilter
   * ```
   */
  private var fColorFilter: Int,
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fPrimitiveBlendMode
   * ```
   */
  private var fPrimitiveBlendMode: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHasPrimitiveBlender
   * ```
   */
  private var fHasPrimitiveBlender: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fSkipColorXform
   * ```
   */
  private var fSkipColorXform: Boolean,
  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<PrecompileShader>, int> fClipShader
   * ```
   */
  private var fClipShader: Int,
  /**
   * C++ original:
   * ```cpp
   * Coverage fRendererCoverage
   * ```
   */
  private var fRendererCoverage: Int,
  /**
   * C++ original:
   * ```cpp
   * TextureFormat fTargetFormat
   * ```
   */
  private var fTargetFormat: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fDither
   * ```
   */
  private var fDither: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fAnalyticClip
   * ```
   */
  private var fAnalyticClip: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const PrecompileBlender* finalBlender() const { return fFinalBlender.first.get(); }
   * ```
   */
  public fun finalBlender(): PrecompileBlender {
    TODO("Implement finalBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::toKey(const KeyContext& keyContext) const {
   *     // Root Node 0 is the source color, which is the output of all effects post dithering
   *     this->handleDithering(keyContext);
   *
   *     // Root Node 1 is the final blender
   *     std::optional<SkBlendMode> finalBlendMode =
   *             this->finalBlender() ? this->finalBlender()->priv().asBlendMode()
   *                                  : SkBlendMode::kSrcOver;
   *
   *     Coverage finalCoverage = fRendererCoverage;
   *     if ((fClipShader.first || fAnalyticClip) && fRendererCoverage == Coverage::kNone) {
   *         finalCoverage = Coverage::kSingleChannel;
   *     }
   *     bool dstReadReq = !finalBlendMode.has_value() ||
   *                       !CanUseHardwareBlending(keyContext.caps(),
   *                                               fTargetFormat,
   *                                               *finalBlendMode,
   *                                               finalCoverage);
   *
   *     if (finalBlendMode) {
   *         if (!dstReadReq) {
   *             AddFixedBlendMode(keyContext, *finalBlendMode);
   *         } else {
   *             AddBlendMode(keyContext, *finalBlendMode);
   *         }
   *     } else {
   *         SkASSERT(this->finalBlender());
   *         fFinalBlender.first->priv().addToKey(keyContext, fFinalBlender.second);
   *     }
   *
   *     // Optional Root Node 2 is the clip
   *     this->handleClipping(keyContext);
   * }
   * ```
   */
  public fun toKey(keyContext: KeyContext) {
    TODO("Implement toKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::addPaintColorToKey(const KeyContext& keyContext) const {
   *     if (fShader.first) {
   *         fShader.first->priv().addToKey(keyContext, fShader.second);
   *     } else {
   *         RGBPaintColorBlock::AddBlock(keyContext);
   *     }
   * }
   * ```
   */
  private fun addPaintColorToKey(keyContext: KeyContext) {
    TODO("Implement addPaintColorToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::handlePrimitiveColor(const KeyContext& keyContext) const {
   *     if (!fHasPrimitiveBlender) {
   *         this->addPaintColorToKey(keyContext);
   *         return;
   *     }
   *
   *     if (fSkipColorXform && fPrimitiveBlendMode == SkBlendMode::kDst) {
   *         AddPrimitiveColor(keyContext, fSkipColorXform);
   *         return;
   *     }
   *
   *     Blend(keyContext,
   *             /* addBlendToKey= */ [&] () -> void {
   *                 /**
   *                  * TODO: Allow clients to provide precompile SkBlender options for primitive
   *                  * blending. For now we have a back door to internally specify an SkBlendMode.
   *                  */
   *                 AddToKey(keyContext, GetBlendModeSingleton(fPrimitiveBlendMode));
   *             },
   *             /* addSrcToKey= */ [&]() -> void {
   *                 this->addPaintColorToKey(keyContext);
   *             },
   *             /* addDstToKey= */ [&]() -> void {
   *                 AddPrimitiveColor(keyContext, fSkipColorXform);
   *             });
   * }
   * ```
   */
  private fun handlePrimitiveColor(keyContext: KeyContext) {
    TODO("Implement handlePrimitiveColor")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::handlePaintAlpha(const KeyContext& keyContext) const {
   *
   *     if (!fShader.first && !fHasPrimitiveBlender) {
   *         // If there is no shader and no primitive blending the input to the colorFilter stage
   *         // is just the premultiplied paint color.
   *         SolidColorShaderBlock::AddBlock(keyContext, SK_PMColor4fWHITE);
   *         return;
   *     }
   *
   *     if (!fOpaquePaintColor) {
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
   *     } else {
   *         this->handlePrimitiveColor(keyContext);
   *     }
   * }
   * ```
   */
  private fun handlePaintAlpha(keyContext: KeyContext) {
    TODO("Implement handlePaintAlpha")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::handleColorFilter(const KeyContext& keyContext) const {
   *     if (fColorFilter.first) {
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     this->handlePaintAlpha(keyContext);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     fColorFilter.first->priv().addToKey(keyContext, fColorFilter.second);
   *                 });
   *     } else {
   *         this->handlePaintAlpha(keyContext);
   *     }
   * }
   * ```
   */
  private fun handleColorFilter(keyContext: KeyContext) {
    TODO("Implement handleColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool PaintOption::shouldDither(SkColorType dstCT) const {
   *     // The paint dither flag can veto.
   *     if (!fDither) {
   *         return false;
   *     }
   *
   *     if (dstCT == kUnknown_SkColorType) {
   *         return false;
   *     }
   *
   *     // We always dither 565 or 4444 when requested.
   *     if (dstCT == kRGB_565_SkColorType || dstCT == kARGB_4444_SkColorType) {
   *         return true;
   *     }
   *
   *     // Otherwise, dither is only needed for non-const paints.
   *     return fShader.first && !fShader.first->priv().isConstant(fShader.second);
   * }
   * ```
   */
  private fun shouldDither(dstCT: SkColorType): Boolean {
    TODO("Implement shouldDither")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::handleDithering(const KeyContext& keyContext) const {
   *
   * #ifndef SK_IGNORE_GPU_DITHER
   *     SkColorType ct = keyContext.dstColorInfo().colorType();
   *     if (this->shouldDither(ct)) {
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     this->handleColorFilter(keyContext);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     AddDitherBlock(keyContext, ct);
   *                 });
   *     } else
   * #endif
   *     {
   *         this->handleColorFilter(keyContext);
   *     }
   * }
   * ```
   */
  private fun handleDithering(keyContext: KeyContext) {
    TODO("Implement handleDithering")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintOption::handleClipping(const KeyContext& keyContext) const {
   *     if (fAnalyticClip) {
   *         NonMSAAClipBlock::NonMSAAClipData data(
   *                 /* rect= */ {},
   *                 /* radiusPlusHalf= */ {},
   *                 /* edgeSelect= */ {},
   *                 /* texCoordOffset= */ {},
   *                 /* maskBounds= */ {},
   *                 // TODO: the kAnalyticAndAtlasClip vs. kAnalyticClip decision is based on this
   *                 // being a valid TextureProxy.
   *                 /* atlasTexture= */ nullptr);
   *         if (fClipShader.first) {
   *             // For both an analytic clip and clip shader, we need to compose them together into
   *             // a single clipping root node.
   *             Blend(keyContext,
   *                     /* addBlendToKey= */ [&]() -> void {
   *                         AddFixedBlendMode(keyContext, SkBlendMode::kModulate);
   *                     },
   *                     /* addSrcToKey= */ [&]() -> void {
   *                         NonMSAAClipBlock::AddBlock(keyContext, data);
   *                     },
   *                     /* addDstToKey= */ [&]() -> void {
   *                         fClipShader.first->priv().addToKey(keyContext, fClipShader.second);
   *                     });
   *         } else {
   *             // Without a clip shader, the analytic clip can be the clipping root node.
   *             NonMSAAClipBlock::AddBlock(keyContext, data);
   *         }
   *     } else if (fClipShader.first) {
   *         // Since there's no analytic clip, the clipping root node can be fClipShader directly.
   *         fClipShader.first->priv().addToKey(keyContext, fClipShader.second);
   *     }
   * }
   * ```
   */
  private fun handleClipping(keyContext: KeyContext) {
    TODO("Implement handleClipping")
  }
}
