package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.SkDevice
import org.skia.core.Stats
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class FilterResultTestAccess {
 *     using BoundsAnalysis = FilterResult::BoundsAnalysis;
 * public:
 *     static void Draw(const skif::Context& ctx,
 *                      SkDevice* device,
 *                      const skif::FilterResult& image,
 *                      bool preserveDeviceState) {
 *         image.draw(ctx, device, preserveDeviceState, /*blender=*/nullptr);
 *     }
 *
 *     static sk_sp<SkShader> AsShader(const skif::Context& ctx,
 *                                     const skif::FilterResult& image,
 *                                     const skif::LayerSpace<SkIRect>& sampleBounds) {
 *         return image.asShader(ctx, FilterResult::kDefaultSampling,
 *                               FilterResult::ShaderFlags::kNone, sampleBounds);
 *     }
 *
 *     static sk_sp<SkShader> StrictShader(const skif::Context& ctx,
 *                                         const skif::FilterResult& image) {
 *         auto analysis = image.analyzeBounds(ctx.desiredOutput());
 *         if (analysis & FilterResult::BoundsAnalysis::kRequiresLayerCrop) {
 *             // getAnalyzedShaderView() doesn't include the layer crop, this will be handled by
 *             // the FilterResultImageResolver.
 *            return nullptr;
 *         } else {
 *             // Add flags to ensure no deferred effects or clamping logic are optimized away.
 *             analysis |= BoundsAnalysis::kDstBoundsNotCovered;
 *             analysis |= BoundsAnalysis::kRequiresShaderTiling;
 *             if (image.tileMode() == SkTileMode::kDecal) {
 *                 analysis |= BoundsAnalysis::kRequiresDecalInLayerSpace;
 *             }
 *             return image.getAnalyzedShaderView(ctx, image.sampling(), analysis);
 *         }
 *     }
 *
 *     static skif::FilterResult Rescale(const skif::Context& ctx,
 *                                       const skif::FilterResult& image,
 *                                       const skif::LayerSpace<SkSize> scale) {
 *         return image.rescale(ctx, scale, /*enforceDecal=*/false, /*allowOverscaling=*/false);
 *     }
 *
 *     static void TrackStats(skif::Context* ctx, skif::Stats* stats) {
 *         ctx->fStats = stats;
 *     }
 *
 *     static bool IsIntegerTransform(const skif::FilterResult& image) {
 *         SkMatrix m = SkMatrix(image.fTransform);
 *         return m.isTranslate() &&
 *                SkScalarIsInt(m.getTranslateX()) &&
 *                SkScalarIsInt(m.getTranslateY());
 *     }
 *
 *     static std::optional<std::pair<float, float>> DeferredScaleFactors(
 *             const skif::FilterResult& image) {
 *         float scaleFactors[2];
 *         if (SkMatrix(image.fTransform).getMinMaxScales(scaleFactors)) {
 *             return {{scaleFactors[0], scaleFactors[1]}};
 *         } else {
 *             return {};
 *         }
 *     }
 *
 *     enum class ShaderSampleMode {
 *         kFast,
 *         kShaderClamp,
 *         kShaderTile
 *     };
 *     static ShaderSampleMode GetExpectedShaderSampleMode(const skif::Context& ctx,
 *                                                         const skif::FilterResult& image,
 *                                                         bool actionSupportsDirectDrawing) {
 *         if (!image) {
 *             return ShaderSampleMode::kFast;
 *         }
 *         auto analysis = image.analyzeBounds(ctx.desiredOutput());
 *         bool mustFillDecal = image.tileMode() == SkTileMode::kDecal &&
 *                              (analysis & BoundsAnalysis::kDstBoundsNotCovered) &&
 *                              !actionSupportsDirectDrawing;
 *         if ((analysis & BoundsAnalysis::kHasLayerFillingEffect) || mustFillDecal) {
 *             // The image won't be drawn directly so some form of shader is needed. The faster clamp
 *             // can be used when clamping explicitly or decal-with-transparent-padding.
 *             if (image.tileMode() == SkTileMode::kClamp ||
 *                 (image.tileMode() == SkTileMode::kDecal &&
 *                  image.fBoundary == FilterResult::PixelBoundary::kTransparent)) {
 *                 return ShaderSampleMode::kShaderClamp;
 *             } else {
 *                 // These cases should be covered by the more expensive shader tiling.
 *                 return ShaderSampleMode::kShaderTile;
 *             }
 *         }
 *         // If we got here, it will be drawn directly but a clamp can be needed if the data outside
 *         // the image is unknown and sampling might pull those values in accidentally.
 *         if (image.fBoundary == FilterResult::PixelBoundary::kUnknown) {
 *             return ShaderSampleMode::kShaderClamp;
 *         } else {
 *             return ShaderSampleMode::kFast;
 *         }
 *     }
 * }
 * ```
 */
public open class FilterResultTestAccess {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void Draw(const skif::Context& ctx,
     *                      SkDevice* device,
     *                      const skif::FilterResult& image,
     *                      bool preserveDeviceState) {
     *         image.draw(ctx, device, preserveDeviceState, /*blender=*/nullptr);
     *     }
     * ```
     */
    public fun draw(
      ctx: Context,
      device: SkDevice?,
      image: FilterResult,
      preserveDeviceState: Boolean,
    ) {
      TODO("Implement draw")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkShader> AsShader(const skif::Context& ctx,
     *                                     const skif::FilterResult& image,
     *                                     const skif::LayerSpace<SkIRect>& sampleBounds) {
     *         return image.asShader(ctx, FilterResult::kDefaultSampling,
     *                               FilterResult::ShaderFlags::kNone, sampleBounds);
     *     }
     * ```
     */
    public fun asShader(
      ctx: Context,
      image: FilterResult,
      sampleBounds: LayerSpace<SkIRect>,
    ): SkSp<SkShader> {
      TODO("Implement asShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkShader> StrictShader(const skif::Context& ctx,
     *                                         const skif::FilterResult& image) {
     *         auto analysis = image.analyzeBounds(ctx.desiredOutput());
     *         if (analysis & FilterResult::BoundsAnalysis::kRequiresLayerCrop) {
     *             // getAnalyzedShaderView() doesn't include the layer crop, this will be handled by
     *             // the FilterResultImageResolver.
     *            return nullptr;
     *         } else {
     *             // Add flags to ensure no deferred effects or clamping logic are optimized away.
     *             analysis |= BoundsAnalysis::kDstBoundsNotCovered;
     *             analysis |= BoundsAnalysis::kRequiresShaderTiling;
     *             if (image.tileMode() == SkTileMode::kDecal) {
     *                 analysis |= BoundsAnalysis::kRequiresDecalInLayerSpace;
     *             }
     *             return image.getAnalyzedShaderView(ctx, image.sampling(), analysis);
     *         }
     *     }
     * ```
     */
    public fun strictShader(ctx: Context, image: FilterResult): SkSp<SkShader> {
      TODO("Implement strictShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static skif::FilterResult Rescale(const skif::Context& ctx,
     *                                       const skif::FilterResult& image,
     *                                       const skif::LayerSpace<SkSize> scale) {
     *         return image.rescale(ctx, scale, /*enforceDecal=*/false, /*allowOverscaling=*/false);
     *     }
     * ```
     */
    public fun rescale(
      ctx: Context,
      image: FilterResult,
      scale: LayerSpace<SkSize>,
    ): FilterResult {
      TODO("Implement rescale")
    }

    /**
     * C++ original:
     * ```cpp
     * static void TrackStats(skif::Context* ctx, skif::Stats* stats) {
     *         ctx->fStats = stats;
     *     }
     * ```
     */
    public fun trackStats(ctx: Context?, stats: Stats?) {
      TODO("Implement trackStats")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsIntegerTransform(const skif::FilterResult& image) {
     *         SkMatrix m = SkMatrix(image.fTransform);
     *         return m.isTranslate() &&
     *                SkScalarIsInt(m.getTranslateX()) &&
     *                SkScalarIsInt(m.getTranslateY());
     *     }
     * ```
     */
    public fun isIntegerTransform(image: FilterResult): Boolean {
      TODO("Implement isIntegerTransform")
    }

    /**
     * C++ original:
     * ```cpp
     * static ShaderSampleMode GetExpectedShaderSampleMode(const skif::Context& ctx,
     *                                                         const skif::FilterResult& image,
     *                                                         bool actionSupportsDirectDrawing) {
     *         if (!image) {
     *             return ShaderSampleMode::kFast;
     *         }
     *         auto analysis = image.analyzeBounds(ctx.desiredOutput());
     *         bool mustFillDecal = image.tileMode() == SkTileMode::kDecal &&
     *                              (analysis & BoundsAnalysis::kDstBoundsNotCovered) &&
     *                              !actionSupportsDirectDrawing;
     *         if ((analysis & BoundsAnalysis::kHasLayerFillingEffect) || mustFillDecal) {
     *             // The image won't be drawn directly so some form of shader is needed. The faster clamp
     *             // can be used when clamping explicitly or decal-with-transparent-padding.
     *             if (image.tileMode() == SkTileMode::kClamp ||
     *                 (image.tileMode() == SkTileMode::kDecal &&
     *                  image.fBoundary == FilterResult::PixelBoundary::kTransparent)) {
     *                 return ShaderSampleMode::kShaderClamp;
     *             } else {
     *                 // These cases should be covered by the more expensive shader tiling.
     *                 return ShaderSampleMode::kShaderTile;
     *             }
     *         }
     *         // If we got here, it will be drawn directly but a clamp can be needed if the data outside
     *         // the image is unknown and sampling might pull those values in accidentally.
     *         if (image.fBoundary == FilterResult::PixelBoundary::kUnknown) {
     *             return ShaderSampleMode::kShaderClamp;
     *         } else {
     *             return ShaderSampleMode::kFast;
     *         }
     *     }
     * ```
     */
    public fun getExpectedShaderSampleMode(
      ctx: Context,
      image: FilterResult,
      actionSupportsDirectDrawing: Boolean,
    ): Int {
      TODO("Implement getExpectedShaderSampleMode")
    }
  }
}
