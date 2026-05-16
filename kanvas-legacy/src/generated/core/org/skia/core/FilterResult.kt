package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class FilterResult {
 * public:
 *     FilterResult() : FilterResult(nullptr) {}
 *
 *     explicit FilterResult(sk_sp<SkSpecialImage> image)
 *             : FilterResult(std::move(image), LayerSpace<SkIPoint>({0, 0})) {}
 *
 *     FilterResult(sk_sp<SkSpecialImage> image, const LayerSpace<SkIPoint>& origin)
 *             : FilterResult(std::move(image), origin, PixelBoundary::kUnknown) {}
 *
 *     // Renders the 'pic', clipped by 'cullRect', into an optimally sized surface (depending on
 *     // picture bounds and 'ctx's desired output). The picture is transformed by the context's
 *     // layer matrix. 'pic' must not be null.
 *     static FilterResult MakeFromPicture(const Context& ctx,
 *                                         sk_sp<SkPicture> pic,
 *                                         ParameterSpace<SkRect> cullRect);
 *
 *     // Renders 'shader' into a surface that fills the context's desired output bounds, 'shader' must
 *     // not be null.
 *     // TODO: Update 'dither' to SkImageFilters::Dither, but that cannot be forward declared at the
 *     // moment because SkImageFilters is a class and not a namespace.
 *     static FilterResult MakeFromShader(const Context& ctx,
 *                                        sk_sp<SkShader> shader,
 *                                        bool dither);
 *
 *     // Converts image to a FilterResult. If 'srcRect' is pixel-aligned it does so without rendering.
 *     // Otherwise it draws the src->dst sampling of 'image' into an optimally sized surface based
 *     // on the context's desired output. 'image' must not be null.
 *     static FilterResult MakeFromImage(const Context& ctx,
 *                                       sk_sp<SkImage> image,
 *                                       SkRect srcRect,
 *                                       ParameterSpace<SkRect> dstRect,
 *                                       const SkSamplingOptions& sampling);
 *
 *     // Bilinear is used as the default because it can be downgraded to nearest-neighbor when the
 *     // final transform is pixel-aligned, and chaining multiple bilinear samples and transforms is
 *     // assumed to be visually close enough to sampling once at highest quality and final transform.
 *     static constexpr SkSamplingOptions kDefaultSampling{SkFilterMode::kLinear};
 *
 *     explicit operator bool() const { return SkToBool(fImage); }
 *
 *     // TODO(michaelludwig): Given the planned expansion of FilterResult state, it might be nice to
 *     // pull this back and not expose anything other than its bounding box. This will be possible if
 *     // all rendering can be handled by functions defined on FilterResult.
 *     const SkSpecialImage* image() const { return fImage.get(); }
 *     sk_sp<SkSpecialImage> refImage() const { return fImage; }
 *
 *     // Get the layer-space bounds of the result. This will incorporate any layer-space transform.
 *     LayerSpace<SkIRect> layerBounds() const { return fLayerBounds; }
 *     SkTileMode tileMode() const { return fTileMode; }
 *     SkSamplingOptions sampling() const { return fSamplingOptions; }
 *
 *     const SkColorFilter* colorFilter() const { return fColorFilter.get(); }
 *
 *     // Produce a new FilterResult that has been cropped to 'crop', taking into account the context's
 *     // desired output. When possible, the returned FilterResult will reuse the underlying image and
 *     // adjust its metadata. This will depend on the current transform and tile mode as well as how
 *     // the crop rect intersects this result's layer bounds.
 *     FilterResult applyCrop(const Context& ctx,
 *                            const LayerSpace<SkIRect>& crop,
 *                            SkTileMode tileMode=SkTileMode::kDecal) const;
 *
 *     // Produce a new FilterResult that is the transformation of this FilterResult. When this
 *     // result's sampling and transform are compatible with the new transformation, the returned
 *     // FilterResult can reuse the same image data and adjust just the metadata.
 *     FilterResult applyTransform(const Context& ctx,
 *                                 const LayerSpace<SkMatrix>& transform,
 *                                 const SkSamplingOptions& sampling) const;
 *
 *     // Produce a new FilterResult that is visually equivalent to the output of the SkColorFilter
 *     // evaluating this FilterResult. If the color filter affects transparent black, the returned
 *     // FilterResult can become non-empty even if the input were empty.
 *     FilterResult applyColorFilter(const Context& ctx,
 *                                   sk_sp<SkColorFilter> colorFilter) const;
 *
 *     // Extract image and origin, safely when the image is null. If there are deferred operations
 *     // on FilterResult (such as tiling or transforms) not representable as an image+origin pair,
 *     // the returned image will be the resolution resulting from that metadata and not necessarily
 *     // equal to the original 'image()'.
 *     // TODO (michaelludwig) - This is intended for convenience until all call sites of
 *     // SkImageFilter_Base::filterImage() have been updated to work in the new type system
 *     // (which comes later as SkDevice, SkCanvas, etc. need to be modified, and coordinate space
 *     // tagging needs to be added).
 *     sk_sp<SkSpecialImage> imageAndOffset(const Context& ctx, SkIPoint* offset) const;
 *     // TODO (michaelludwig) - This is a more type-safe version of the above imageAndOffset() and
 *     // may need to remain to support SkBlurImageFilter calling out to the SkBlurEngine. An alternate
 *     // option would be for FilterResult::Builder to have a blur() function that internally can
 *     // resolve the input and pass to the skif::Context's blur engine. Then imageAndOffset() can go
 *     // away entirely.
 *     std::pair<sk_sp<SkSpecialImage>, LayerSpace<SkIPoint>> imageAndOffset(const Context& ctx) const;
 *
 *      // Draw this FilterResult into 'target' by applying the remaining layer-to-device transform of
 *      // 'mapping', using the provided 'blender' to composite the effective image on top of 'target'.
 *      // If 'blender' is null, it's equivalent to kSrcOver blending.
 *     void draw(const Context& ctx, SkDevice* target, const SkBlender* blender) const;
 *
 *     // SkCanvas can prepare layer source images with transparent padding, similarly to AutoSurface.
 *     // This adjusts the FilterResult metadata to be aware of that padding. This should only be
 *     // called when it's externally known that the FilterResult has a 1px buffer of transparent
 *     // black pixels and has had no further modifications.
 *     FilterResult insetForSaveLayer() const;
 *
 *     class Builder;
 *
 *     enum class ShaderFlags : int {
 *         kNone = 0,
 *         // A hint that the input FilterResult will be sampled repeatedly per pixel. If there's
 *         // colorspace conversions or deferred color filtering, it's worth resolving to a temporary
 *         // image so that those calculations are performed once per pixel instead of N times.
 *         kSampledRepeatedly = 1 << 0,
 *         // Specifies that the shader performs non-trivial operations on its coordinates to determine
 *         // how to sample any input FilterResults, so their sampling options should not be converted
 *         // to nearest-neighbor even if they appeared pixel-aligned with the output surface.
 *         kNonTrivialSampling = 1 << 1,
 *         // TODO: Add option to convey that the output can carry input tiling forward to make a
 *         // smaller backing surface somehow. May not be a flag and just args passed to eval().
 *     };
 *     SK_DECL_BITMASK_OPS_FRIENDS(ShaderFlags)
 *
 * private:
 *     friend class ::FilterResultTestAccess; // For testing draw() and asShader()
 *
 *     class AutoSurface;
 *
 *     enum class PixelBoundary : int {
 *         kUnknown,     // Pixels outside the image subset are of unknown value, possibly unitialized
 *         kTransparent, // Pixels bordering the image subset are transparent black
 *         kInitialized, // Pixels bordering the image are known to be initialized
 *     };
 *
 *     FilterResult(sk_sp<SkSpecialImage> image,
 *                  const LayerSpace<SkIPoint>& origin,
 *                  PixelBoundary boundary)
 *             : fImage(std::move(image))
 *             , fBoundary(boundary)
 *             , fSamplingOptions(kDefaultSampling)
 *             , fTileMode(SkTileMode::kDecal)
 *             , fTransform(SkMatrix::Translate(origin.x(), origin.y()))
 *             , fColorFilter(nullptr)
 *             , fLayerBounds(
 *                     fTransform.mapRect(LayerSpace<SkIRect>(fImage ? fImage->dimensions()
 *                                                                   : SkISize{0, 0}))) {}
 *
 *     // Renders this FilterResult into a new, but visually equivalent, image that fills 'dstBounds',
 *     // has default sampling, no color filter, and a transform that translates by only 'dstBounds's
 *     // top-left corner. 'dstBounds' is intersected with 'fLayerBounds' unless 'preserveDstBounds'
 *     // is true.
 *     FilterResult resolve(const Context& ctx, LayerSpace<SkIRect> dstBounds,
 *                          bool preserveDstBounds=false) const;
 *     // Returns a decal-tiled subset view of this FilterResult, requiring that this has an integer
 *     // translation equivalent to 'knownOrigin'. If 'clampSrcIfDisjoint' is true and the image bounds
 *     // do not overlap with dstBounds, the closest edge/corner pixels of the image will be extracted,
 *     // assuming it will be tiled with kClamp.
 *     FilterResult subset(const LayerSpace<SkIPoint>& knownOrigin,
 *                         const LayerSpace<SkIRect>& subsetBounds,
 *                         bool clampSrcIfDisjoint=false) const;
 *     // Convenient version of subset() that insets a single pixel.
 *     FilterResult insetByPixel() const;
 *
 *     enum class BoundsAnalysis : int {
 *         // The image can be drawn directly, without needing to apply tiling, or handling how any
 *         // color filter might affect transparent black.
 *         kSimple = 0,
 *         // The image does not directly cover the intersection of 'dstBounds' and the layer bounds.
 *         // (ignoring tiling or color filters).
 *         kDstBoundsNotCovered = 1 << 0,
 *         // Added when kDstBoundsNotCovered is true, *and* there are non-decal tiling or transparency
 *         // affecting color filters that would fill to the layer bounds, not covered by the image
 *         // itself.
 *         kHasLayerFillingEffect = 1 << 1,
 *         // The crop boundary induced by `fLayerBounds` is visible when rendering to the 'dstBounds',
 *         // although this could be either because it intersects the image's content or because
 *         // kHasLayerFillingEffect is true.
 *         kRequiresLayerCrop = 1 << 2,
 *         // The image's sampling would access pixel data outside of its valid subset so shader-based
 *         // tiling is necessary. This can be true even if kHasLayerFillingEffect is false due to the
 *         // filter sampling radius; it can also be false when kHasLayerFillingEffect is true if the
 *         // image can use HW tiling.
 *         kRequiresShaderTiling = 1 << 3,
 *         // The image's decal tiling/sampling would operate at the wrong resolution (e.g. drawImage
 *         // vs. image-shader look different), so it has to be applied with a wrapping shader effect
 *         kRequiresDecalInLayerSpace = 1 << 4,
 *     };
 *     SK_DECL_BITMASK_OPS_FRIENDS(BoundsAnalysis)
 *
 *     enum class BoundsScope : int {
 *         kDeferred,        // The bounds analysis won't be used for any rendering yet
 *         kCanDrawDirectly, // The rendering may draw the image directly if analysis allows it
 *         kShaderOnly,      // The rendering will always use a filling shader, e.g. drawPaint()
 *         kRescale          // The rendering is controlled by rescaling logic, so ignores decal size
 *     };
 *
 *     // Determine what effects are visible based on the target 'dstBounds' and extra transform that
 *     // will be applied when this FilterResult is drawn. These are not LayerSpace because the
 *     // 'xtraTransform' may be either a within-layer transform, or a layer-to-device space transform.
 *     // The 'dstBounds' should be in the same coordinate space that 'xtraTransform' maps to. When
 *     // that is the identity matrix, 'dstBounds' is in layer space.
 *     SkEnumBitMask<BoundsAnalysis> analyzeBounds(const SkMatrix& xtraTransform,
 *                                                 const SkIRect& dstBounds,
 *                                                 BoundsScope scope = BoundsScope::kDeferred) const;
 *     SkEnumBitMask<BoundsAnalysis> analyzeBounds(const LayerSpace<SkIRect>& dstBounds,
 *                                                 BoundsScope scope = BoundsScope::kDeferred) const {
 *         return this->analyzeBounds(SkMatrix::I(), SkIRect(dstBounds), scope);
 *     }
 *
 *     // If true, the tile mode can be changed to kClamp to sample the transparent black pixels in
 *     // the boundary. This will be visually equivalent to the decal tiling or anti-aliasing of a
 *     // drawn image.
 *     bool canClampToTransparentBoundary(SkEnumBitMask<BoundsAnalysis> analysis) const {
 *         return fTileMode == SkTileMode::kDecal &&
 *                fBoundary == PixelBoundary::kTransparent &&
 *                !(analysis & BoundsAnalysis::kRequiresDecalInLayerSpace);
 *     }
 *
 *     // Return an equivalent FilterResult such that its backing image dimensions have been reduced
 *     // by the X and Y scale factors in 'scale' (assumed to be in [0, 1]). The returned FilterResult
 *     // will have a transform that aligns it with the original FilterResult (i.e. a deferred upscale)
 *     // and may also have a deferred tilemode. If 'enforceDecal' is true, the returned
 *     // FilterResult will be kDecal sampled and any tiling will already be applied.
 *     //
 *     // If `allowOverscaling` is true, the returned image may be scaled beyond what's requested in
 *     // `scale` to remain a multiple of 1/2X steps.
 *     //
 *     // All deferred effects, other than potentially tile mode, will be applied. The FilterResult
 *     // will also be converted to the color type and color space of 'ctx' so the result is suitable
 *     // to pass to the blur engine.
 *     FilterResult rescale(const Context& ctx,
 *                          const LayerSpace<SkSize>& scale,
 *                          bool enforceDecal,
 *                          bool allowOverscaling) const;
 *     // Draw directly to the device, which draws the same image as produced by resolve() but can be
 *     // useful if multiple operations need to be performed on the canvas.
 *     //
 *     // This assumes that the device's transform is set to match the current layer space coordinate
 *     // system. This will concat any internal extra transform and apply clipping as necessary. If
 *     // 'preserveDeviceState' is true it will undo any modifications. This can be set to false if the
 *     // device is a one-off that will be snapped to an image after this returns.
 *     //
 *     // If 'blender' is null, the filter result is drawn with src-over blending. If it's not, it will
 *     // be drawn using the given 'blender', filling the device's current clip when the blend
 *     // modifies transparent black.
 *     void draw(const Context& ctx,
 *               SkDevice* device,
 *               bool preserveDeviceState,
 *               const SkBlender* blender=nullptr) const;
 *
 *     // Returns the FilterResult as a shader, ideally without resolving to an axis-aligned image.
 *     // 'xtraSampling' is the sampling that any parent shader applies to the FilterResult.
 *     // 'sampleBounds' is the bounding box of coords the shader will be evaluated at by any parent.
 *     //
 *     // This variant may resolve to an intermediate image if needed. The returned shader encapsulates
 *     // all deferred effects of the FilterResult.
 *     sk_sp<SkShader> asShader(const Context& ctx,
 *                              const SkSamplingOptions& xtraSampling,
 *                              SkEnumBitMask<ShaderFlags> flags,
 *                              const LayerSpace<SkIRect>& sampleBounds) const;
 *
 *     // This variant should only be called after analysis and final sampling has been determined, and
 *     // there's no need to resolve the FilterResult to an intermediate image. This version will
 *     // never introduce a new image pass but is unable to handle the layer crop. If (analysis &
 *     // kRequiresLayerCrop) is true, it must be accounted for outside of this shader.
 *     sk_sp<SkShader> getAnalyzedShaderView(const Context& ctx,
 *                                           const SkSamplingOptions& finalSampling,
 *                                           SkEnumBitMask<BoundsAnalysis> analysis) const;
 *
 *     // Safely updates fTileMode, doing nothing if the FilterResult is empty. Updates the layer
 *     // bounds to the context's desired output if the tilemode is not decal.
 *     void updateTileMode(const Context& ctx, SkTileMode tileMode);
 *
 *     // The effective image of a FilterResult is 'fImage' sampled by 'fSamplingOptions' and
 *     // respecting 'fTileMode' (on the SkSpecialImage's subset), transformed by 'fTransform',
 *     // filtered by 'fColorFilter', and then clipped to 'fLayerBounds'.
 *     sk_sp<SkSpecialImage> fImage;
 *     PixelBoundary         fBoundary;
 *
 *     SkSamplingOptions     fSamplingOptions;
 *     SkTileMode            fTileMode;
 *     // Typically this will be an integer translation that encodes the origin of the top left corner,
 *     // but can become more complex when combined with applyTransform().
 *     LayerSpace<SkMatrix>  fTransform;
 *
 *     // A null color filter is the identity function. Since the output is clipped to fLayerBounds
 *     // after color filtering, SkColorFilters that affect transparent black are not unbounded.
 *     sk_sp<SkColorFilter>  fColorFilter;
 *
 *     // The layer bounds are initially fImage's dimensions mapped by fTransform. As the filter result
 *     // is processed by the image filter DAG, it can be further restricted by crop rects or the
 *     // implicit desired output at each node.
 *     LayerSpace<SkIRect>   fLayerBounds;
 * }
 * ```
 */
public data class FilterResult public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr SkSamplingOptions kDefaultSampling{SkFilterMode::kLinear}
   * ```
   */
  private var fImage: SkSp<SkSpecialImage>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> fImage
   * ```
   */
  private var fBoundary: PixelBoundary,
  /**
   * C++ original:
   * ```cpp
   * PixelBoundary         fBoundary
   * ```
   */
  private var fSamplingOptions: SkSamplingOptions,
  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions     fSamplingOptions
   * ```
   */
  private var fTileMode: SkTileMode,
  /**
   * C++ original:
   * ```cpp
   * SkTileMode            fTileMode
   * ```
   */
  private var fTransform: LayerSpace<SkMatrix>,
  /**
   * C++ original:
   * ```cpp
   * LayerSpace<SkMatrix>  fTransform
   * ```
   */
  private var fColorFilter: SkSp<SkColorFilter>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter>  fColorFilter
   * ```
   */
  private var fLayerBounds: LayerSpace<SkIRect>,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkSpecialImage* image() const { return fImage.get(); }
   * ```
   */
  public fun image(): SkSpecialImage {
    TODO("Implement image")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> refImage() const { return fImage; }
   * ```
   */
  public fun refImage(): SkSp<SkSpecialImage> {
    TODO("Implement refImage")
  }

  /**
   * C++ original:
   * ```cpp
   * LayerSpace<SkIRect> layerBounds() const { return fLayerBounds; }
   * ```
   */
  public fun layerBounds(): LayerSpace<SkIRect> {
    TODO("Implement layerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileMode() const { return fTileMode; }
   * ```
   */
  public fun tileMode(): SkTileMode {
    TODO("Implement tileMode")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions sampling() const { return fSamplingOptions; }
   * ```
   */
  public fun sampling(): SkSamplingOptions {
    TODO("Implement sampling")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorFilter* colorFilter() const { return fColorFilter.get(); }
   * ```
   */
  public fun colorFilter(): SkColorFilter {
    TODO("Implement colorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::applyCrop(const Context& ctx,
   *                                      const LayerSpace<SkIRect>& crop,
   *                                      SkTileMode tileMode) const {
   *     static const LayerSpace<SkMatrix> kIdentity{SkMatrix::I()};
   *
   *     if (crop.isEmpty() || ctx.desiredOutput().isEmpty()) {
   *         // An empty crop cannot be anything other than fully transparent
   *         return {};
   *     }
   *
   *     // First, determine how this image's layer bounds interact with the crop rect, which determines
   *     // the portion of 'crop' that could have non-transparent content.
   *     LayerSpace<SkIRect> cropContent = crop;
   *     if (!fImage ||
   *         !cropContent.intersect(fLayerBounds)) {
   *         // The pixels within 'crop' would be fully transparent, and tiling won't change that.
   *         return {};
   *     }
   *
   *     // Second, determine the subset of 'crop' that is relevant to ctx.desiredOutput().
   *     LayerSpace<SkIRect> fittedCrop = crop.relevantSubset(ctx.desiredOutput(), tileMode);
   *
   *     // Third, check if there's overlap with the known non-transparent cropped content and what's
   *     // used to tile the desired output. If not, the image is known to be empty. This modifies
   *     // 'cropContent' and not 'fittedCrop' so that any transparent padding remains if we have to
   *     // apply repeat/mirror tiling to the original geometry.
   *     if (!cropContent.intersect(fittedCrop)) {
   *         return {};
   *     }
   *
   *     // Fourth, a periodic tiling that covers the output with a single instance of the image can be
   *     // simplified to just a transform.
   *     auto periodicTransform = periodic_axis_transform(tileMode, fittedCrop, ctx.desiredOutput());
   *     if (periodicTransform) {
   *         return this->applyTransform(ctx, *periodicTransform, FilterResult::kDefaultSampling);
   *     }
   *
   *     bool preserveTransparencyInCrop = false;
   *     if (tileMode == SkTileMode::kDecal) {
   *         // We can reduce the crop dimensions to what's non-transparent
   *         fittedCrop = cropContent;
   *     } else if (fittedCrop.contains(ctx.desiredOutput())) {
   *         tileMode = SkTileMode::kDecal;
   *         fittedCrop = ctx.desiredOutput();
   *     } else if (!cropContent.contains(fittedCrop)) {
   *         // There is transparency in fittedCrop that must be resolved in order to maintain the new
   *         // tiling geometry.
   *         preserveTransparencyInCrop = true;
   *         if (fTileMode == SkTileMode::kDecal && tileMode == SkTileMode::kClamp) {
   *             // include 1px buffer for transparency from original kDecal tiling
   *             cropContent.outset(skif::LayerSpace<SkISize>({1, 1}));
   *             SkAssertResult(fittedCrop.intersect(cropContent));
   *         }
   *     } // Otherwise cropContent == fittedCrop
   *
   *     // Fifth, when the transform is an integer translation, any prior tiling and the new tiling
   *     // can sometimes be addressed analytically without producing a new image. Moving the crop into
   *     // the image dimensions allows future operations like applying a transform or color filter to
   *     // be composed without rendering a new image since there will not be an intervening crop.
   *     const bool doubleClamp = fTileMode == SkTileMode::kClamp && tileMode == SkTileMode::kClamp;
   *     LayerSpace<SkIPoint> origin;
   *     if (!preserveTransparencyInCrop &&
   *         is_nearly_integer_translation(fTransform, &origin) &&
   *         (doubleClamp ||
   *          !(this->analyzeBounds(fittedCrop) & BoundsAnalysis::kHasLayerFillingEffect))) {
   *         // Since the transform is axis-aligned, the tile mode can be applied to the original
   *         // image pre-transformation and still be consistent with the 'crop' geometry. When the
   *         // original tile mode is decal, extract_subset is always valid. When the original mode is
   *         // mirror/repeat, !kHasLayerFillingEffect ensures that 'fittedCrop' is contained within
   *         // the base image bounds, so extract_subset is valid. When the original mode is clamp
   *         // and the new mode is not clamp, that is also the case. When both modes are clamp, we have
   *         // to consider how 'fittedCrop' intersects (or doesn't) with the base image bounds.
   *         FilterResult restrictedOutput = this->subset(origin, fittedCrop, doubleClamp);
   *         restrictedOutput.updateTileMode(ctx, tileMode);
   *         if (restrictedOutput.fBoundary == PixelBoundary::kInitialized ||
   *             tileMode != SkTileMode::kDecal) {
   *             // Discard kInitialized since a crop is a strict constraint on sampling outside of it.
   *             // But preserve (kTransparent+kDecal) if this is a no-op crop.
   *             restrictedOutput.fBoundary = PixelBoundary::kUnknown;
   *         }
   *         return restrictedOutput;
   *     } else if (tileMode == SkTileMode::kDecal) {
   *         // A decal crop can always be applied as the final operation by adjusting layer bounds, and
   *         // does not modify any prior tile mode.
   *         SkASSERT(!preserveTransparencyInCrop);
   *         FilterResult restrictedOutput = *this;
   *         restrictedOutput.fLayerBounds = fittedCrop;
   *         return restrictedOutput;
   *     } else {
   *         // There is a non-trivial transform to the image data that must be applied before the
   *         // non-decal tilemode is meant to be applied to the axis-aligned 'crop'.
   *         FilterResult tiled = this->resolve(ctx, fittedCrop, /*preserveDstBounds=*/true);
   *         tiled.updateTileMode(ctx, tileMode);
   *         return tiled;
   *     }
   * }
   * ```
   */
  public fun applyCrop(
    ctx: Context,
    crop: LayerSpace<SkIRect>,
    tileMode: SkTileMode = TODO(),
  ): FilterResult {
    TODO("Implement applyCrop")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::applyTransform(const Context& ctx,
   *                                           const LayerSpace<SkMatrix>& transform,
   *                                           const SkSamplingOptions &sampling) const {
   *     if (!fImage || ctx.desiredOutput().isEmpty()) {
   *         // Transformed transparent black remains transparent black.
   *         SkASSERT(!fColorFilter);
   *         return {};
   *     }
   *
   *     if (!transform.invert(nullptr)) {
   *         return {};
   *     }
   *
   *     // Extract the sampling options that matter based on the current and next transforms.
   *     // We make sure the new sampling is bilerp (default) if the new transform doesn't matter
   *     // (and assert that the current is bilerp if its transform didn't matter). Bilerp can be
   *     // maximally combined, so simplifies the logic in compatible_sampling().
   *     const bool currentXformIsInteger = is_nearly_integer_translation(fTransform);
   *     const bool nextXformIsInteger = is_nearly_integer_translation(transform);
   *
   *     SkASSERT(!currentXformIsInteger || fSamplingOptions == kDefaultSampling);
   *     SkSamplingOptions nextSampling = nextXformIsInteger ? kDefaultSampling : sampling;
   *
   *     // Determine if the image is being visibly cropped by the layer bounds, in which case we can't
   *     // merge this transform with any previous transform (unless the new transform is an integer
   *     // translation in which case any visible edge is aligned with the desired output and can be
   *     // resolved by intersecting the transformed layer bounds and the output bounds).
   *     bool isCropped = !nextXformIsInteger &&
   *                      (this->analyzeBounds(SkMatrix(transform), SkIRect(ctx.desiredOutput()))
   *                             & BoundsAnalysis::kRequiresLayerCrop);
   *
   *     FilterResult transformed;
   *     if (!isCropped && compatible_sampling(fSamplingOptions, currentXformIsInteger,
   *                                           &nextSampling, nextXformIsInteger)) {
   *         // We can concat transforms and 'nextSampling' will be either fSamplingOptions,
   *         // sampling, or a merged combination depending on the two transforms in play.
   *         transformed = *this;
   *     } else {
   *         // We'll have to resolve this FilterResult first before 'transform' and 'sampling' can be
   *         // correctly evaluated. 'nextSampling' will always be 'sampling'.
   *         LayerSpace<SkIRect> tightBounds;
   *         if (transform.inverseMapRect(ctx.desiredOutput(), &tightBounds)) {
   *             transformed = this->resolve(ctx, tightBounds);
   *         }
   *
   *         if (!transformed.fImage) {
   *             // Transform not invertible or resolve failed to create an image
   *             return {};
   *         }
   *     }
   *
   *     transformed.fSamplingOptions = nextSampling;
   *     transformed.fTransform.postConcat(transform);
   *     // Rebuild the layer bounds and then restrict to the current desired output. The original value
   *     // of fLayerBounds includes the image mapped by the original fTransform as well as any
   *     // accumulated soft crops from desired outputs of prior stages. To prevent discarding that info,
   *     // we map fLayerBounds by the additional transform, instead of re-mapping the image bounds.
   *     transformed.fLayerBounds = transform.mapRect(transformed.fLayerBounds);
   *     if (!LayerSpace<SkIRect>::Intersects(transformed.fLayerBounds, ctx.desiredOutput())) {
   *         // The transformed output doesn't touch the desired, so it would just be transparent black.
   *         return {};
   *     }
   *
   *     return transformed;
   * }
   * ```
   */
  public fun applyTransform(
    ctx: Context,
    transform: LayerSpace<SkMatrix>,
    sampling: SkSamplingOptions,
  ): FilterResult {
    TODO("Implement applyTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::applyColorFilter(const Context& ctx,
   *                                             sk_sp<SkColorFilter> colorFilter) const {
   *     // A null filter is the identity, so it should have been caught during image filter DAG creation
   *     SkASSERT(colorFilter);
   *
   *     if (ctx.desiredOutput().isEmpty()) {
   *         return {};
   *     }
   *
   *     // Color filters are applied after the transform and image sampling, but before the fLayerBounds
   *     // crop. We can compose 'colorFilter' with any previously applied color filter regardless
   *     // of the transform/sample state, so long as it respects the effect of the current crop.
   *     LayerSpace<SkIRect> newLayerBounds = fLayerBounds;
   *     if (as_CFB(colorFilter)->affectsTransparentBlack()) {
   *         if (!fImage || !newLayerBounds.intersect(ctx.desiredOutput())) {
   *             // The current image's intersection with the desired output is fully transparent, but
   *             // the new color filter converts that into a non-transparent color. The desired output
   *             // is filled with this color, but use a 1x1 surface and clamp tiling.
   *             AutoSurface surface{ctx,
   *                                 LayerSpace<SkIRect>{SkIRect::MakeXYWH(ctx.desiredOutput().left(),
   *                                                                       ctx.desiredOutput().top(),
   *                                                                       1, 1)},
   *                                 PixelBoundary::kInitialized,
   *                                 /*renderInParameterSpace=*/false};
   *             if (surface) {
   *                 SkPaint paint;
   *                 paint.setColor4f(SkColors::kTransparent, /*colorSpace=*/nullptr);
   *                 paint.setColorFilter(std::move(colorFilter));
   * #if !defined(SK_USE_SRCOVER_FOR_FILTERS)
   *                 paint.setBlendMode(SkBlendMode::kSrc);
   * #endif
   *                 surface->drawPaint(paint);
   *             }
   *             FilterResult solidColor = surface.snap();
   *             solidColor.updateTileMode(ctx, SkTileMode::kClamp);
   *             return solidColor;
   *         }
   *
   *         if (this->analyzeBounds(ctx.desiredOutput()) & BoundsAnalysis::kRequiresLayerCrop) {
   *             // Since 'colorFilter' modifies transparent black, the new result's layer bounds must
   *             // be the desired output. But if the current image is cropped we need to resolve the
   *             // image to avoid losing the effect of the current 'fLayerBounds'.
   *             newLayerBounds.outset(LayerSpace<SkISize>({1, 1}));
   *             SkAssertResult(newLayerBounds.intersect(ctx.desiredOutput()));
   *             FilterResult filtered = this->resolve(ctx, newLayerBounds, /*preserveDstBounds=*/true);
   *             filtered.fColorFilter = std::move(colorFilter);
   *             filtered.updateTileMode(ctx, SkTileMode::kClamp);
   *             return filtered;
   *         }
   *
   *         // otherwise we can fill out to the desired output without worrying about losing the crop.
   *         newLayerBounds = ctx.desiredOutput();
   *     } else {
   *         if (!fImage || !LayerSpace<SkIRect>::Intersects(newLayerBounds, ctx.desiredOutput())) {
   *             // The color filter does not modify transparent black, so it remains transparent
   *             return {};
   *         }
   *         // otherwise a non-transparent affecting color filter can always be lifted before any crop
   *         // because it does not change the "shape" of the prior FilterResult.
   *     }
   *
   *     // If we got here we can compose the new color filter with the previous filter and the prior
   *     // layer bounds are either soft-cropped to the desired output, or we fill out the desired output
   *     // when the new color filter affects transparent black. We don't check if the entire composed
   *     // filter affects transparent black because earlier floods are restricted by the layer bounds.
   *     FilterResult filtered = *this;
   *     filtered.fLayerBounds = newLayerBounds;
   *     filtered.fColorFilter = SkColorFilters::Compose(std::move(colorFilter), fColorFilter);
   *     return filtered;
   * }
   * ```
   */
  public fun applyColorFilter(ctx: Context, colorFilter: SkSp<SkColorFilter>): FilterResult {
    TODO("Implement applyColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> FilterResult::imageAndOffset(const Context& ctx, SkIPoint* offset) const {
   *     auto [image, origin] = this->imageAndOffset(ctx);
   *     *offset = SkIPoint(origin);
   *     return image;
   * }
   * ```
   */
  public fun imageAndOffset(ctx: Context, offset: SkIPoint?): SkSp<SkSpecialImage> {
    TODO("Implement imageAndOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkSpecialImage>, LayerSpace<SkIPoint>>FilterResult::imageAndOffset(
   *         const Context& ctx) const {
   *     FilterResult resolved = this->resolve(ctx, ctx.desiredOutput());
   *     return {resolved.fImage, resolved.layerBounds().topLeft()};
   * }
   * ```
   */
  public fun imageAndOffset(ctx: Context): Int {
    TODO("Implement imageAndOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * void FilterResult::draw(const Context& ctx, SkDevice* target, const SkBlender* blender) const {
   *     SkAutoDeviceTransformRestore adtr{target, ctx.mapping().layerToDevice()};
   *     this->draw(ctx, target, /*preserveDeviceState=*/true, blender);
   * }
   * ```
   */
  public fun draw(
    ctx: Context,
    target: SkDevice?,
    blender: SkBlender?,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::insetForSaveLayer() const {
   *     if (!fImage) {
   *         return {};
   *     }
   *
   *     // SkCanvas processing should have prepared a decal-tiled image before calling this.
   *     SkASSERT(fTileMode == SkTileMode::kDecal);
   *
   *     // PixelBoundary tracking assumes the special image's subset does not include the padding, so
   *     // inset by a single pixel.
   *     FilterResult inset = this->insetByPixel();
   *     // Trust that SkCanvas configured the layer's SkDevice to ensure the padding remained
   *     // transparent. Upgrading this pixel boundary knowledge allows the source image to use the
   *     // simpler clamp math (vs. decal math) when used in a shader context.
   *     SkASSERT(inset.fBoundary == PixelBoundary::kInitialized &&
   *              inset.fTileMode == SkTileMode::kDecal);
   *     inset.fBoundary = PixelBoundary::kTransparent;
   *     return inset;
   * }
   * ```
   */
  public fun insetForSaveLayer(): FilterResult {
    TODO("Implement insetForSaveLayer")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::resolve(const Context& ctx,
   *                                    LayerSpace<SkIRect> dstBounds,
   *                                    bool preserveDstBounds) const {
   *     // The layer bounds is the final clip, so it can always be used to restrict 'dstBounds'. Even
   *     // if there's a non-decal tile mode or transparent-black affecting color filter, those floods
   *     // are restricted to fLayerBounds.
   *     if (!fImage || (!preserveDstBounds && !dstBounds.intersect(fLayerBounds))) {
   *         return {nullptr, {}};
   *     }
   *
   *     // If we have any extra effect to apply, there's no point in trying to extract a subset.
   *     const bool subsetCompatible = !fColorFilter &&
   *                                   fTileMode == SkTileMode::kDecal &&
   *                                   !preserveDstBounds;
   *
   *     // TODO(michaelludwig): If we get to the point where all filter results track bounds in
   *     // floating point, then we can extend this case to any S+T transform.
   *     LayerSpace<SkIPoint> origin;
   *     if (subsetCompatible && is_nearly_integer_translation(fTransform, &origin)) {
   *         return this->subset(origin, dstBounds);
   *     } // else fall through and attempt a draw
   *
   *     // Don't use context properties to avoid DMSAA on internal stages of filter evaluation.
   *     SkSurfaceProps props = {};
   *     PixelBoundary boundary = preserveDstBounds ? PixelBoundary::kUnknown
   *                                                : PixelBoundary::kTransparent;
   *     AutoSurface surface{ctx, dstBounds, boundary, /*renderInParameterSpace=*/false, &props};
   *     if (surface) {
   *         this->draw(ctx, surface.device(), /*preserveDeviceState=*/false);
   *     }
   *     return surface.snap();
   * }
   * ```
   */
  private fun resolve(
    ctx: Context,
    dstBounds: LayerSpace<SkIRect>,
    preserveDstBounds: Boolean = TODO(),
  ): FilterResult {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::subset(const LayerSpace<SkIPoint>& knownOrigin,
   *                                   const LayerSpace<SkIRect>& subsetBounds,
   *                                   bool clampSrcIfDisjoint) const {
   *     SkDEBUGCODE(LayerSpace<SkIPoint> actualOrigin;)
   *     SkASSERT(is_nearly_integer_translation(fTransform, &actualOrigin) &&
   *              SkIPoint(actualOrigin) == SkIPoint(knownOrigin));
   *
   *
   *     LayerSpace<SkIRect> imageBounds(SkIRect::MakeXYWH(knownOrigin.x(), knownOrigin.y(),
   *                                                       fImage->width(), fImage->height()));
   *     imageBounds = imageBounds.relevantSubset(subsetBounds, clampSrcIfDisjoint ? SkTileMode::kClamp
   *                                                                               : SkTileMode::kDecal);
   *     if (imageBounds.isEmpty()) {
   *         return {};
   *     }
   *
   *     // Offset the image subset directly to avoid issues negating (origin). With the prior
   *     // intersection (bounds - origin) will be >= 0, but (bounds + (-origin)) may not, (e.g.
   *     // origin is INT_MIN).
   *     SkIRect subset = { imageBounds.left() - knownOrigin.x(),
   *                        imageBounds.top() - knownOrigin.y(),
   *                        imageBounds.right() - knownOrigin.x(),
   *                        imageBounds.bottom() - knownOrigin.y() };
   *     SkASSERT(subset.fLeft >= 0 && subset.fTop >= 0 &&
   *              subset.fRight <= fImage->width() && subset.fBottom <= fImage->height());
   *
   *     FilterResult result{fImage->makeSubset(subset), imageBounds.topLeft()};
   *     result.fColorFilter = fColorFilter;
   *
   *     // Update what's known about PixelBoundary based on how the subset aligns.
   *     SkASSERT(result.fBoundary == PixelBoundary::kUnknown);
   *     // If the pixel bounds didn't change, preserve the original boundary value
   *     if (fImage->subset() == result.fImage->subset()) {
   *         result.fBoundary = fBoundary;
   *     } else {
   *         // If the new pixel bounds are bordered by valid data, upgrade to kInitialized
   *         SkIRect safeSubset = fImage->subset();
   *         if (fBoundary == PixelBoundary::kUnknown) {
   *             safeSubset.inset(1, 1);
   *         }
   *         if (safeSubset.contains(result.fImage->subset())) {
   *             result.fBoundary = PixelBoundary::kInitialized;
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  private fun subset(
    knownOrigin: LayerSpace<SkIPoint>,
    subsetBounds: LayerSpace<SkIRect>,
    clampSrcIfDisjoint: Boolean = TODO(),
  ): FilterResult {
    TODO("Implement subset")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::insetByPixel() const {
   *     // This assumes that the image is pixel aligned with its layer bounds, which is validated in
   *     // the call to subset().
   *     auto insetBounds = fLayerBounds;
   *     insetBounds.inset(LayerSpace<SkISize>({1, 1}));
   *      // Shouldn't be calling this except in situations where padding was explicitly added before.
   *     SkASSERT(!insetBounds.isEmpty());
   *     return this->subset(fLayerBounds.topLeft(), insetBounds);
   * }
   * ```
   */
  private fun insetByPixel(): FilterResult {
    TODO("Implement insetByPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<FilterResult::BoundsAnalysis> FilterResult::analyzeBounds(
   *         const SkMatrix& xtraTransform,
   *         const SkIRect& dstBounds,
   *         BoundsScope scope) const {
   *     static constexpr SkSamplingOptions kNearestNeighbor = {};
   *     static constexpr float kHalfPixel = 0.5f;
   *     static constexpr float kCubicRadius = 1.5f;
   *
   *     SkEnumBitMask<BoundsAnalysis> analysis = BoundsAnalysis::kSimple;
   *     const bool fillsLayerBounds = fTileMode != SkTileMode::kDecal ||
   *                                   (fColorFilter && as_CFB(fColorFilter)->affectsTransparentBlack());
   *
   *     // 1. Is the layer geometry visible in the dstBounds (ignoring whether or not there are shading
   *     //    effects that highlight that boundary).
   *     SkRect pixelCenterBounds = SkRect::Make(dstBounds);
   *     if (!SkRectPriv::QuadContainsRect(xtraTransform,
   *                                       SkIRect(fLayerBounds),
   *                                       dstBounds,
   *                                       kRoundEpsilon)) {
   *         // 1a. If an effect doesn't fill out to the layer bounds, is the image content itself
   *         //     clipped by the layer bounds?
   *         bool requireLayerCrop = fillsLayerBounds;
   *         if (!fillsLayerBounds) {
   *             LayerSpace<SkIRect> imageBounds =
   *                     fTransform.mapRect(LayerSpace<SkIRect>{fImage->dimensions()});
   *             requireLayerCrop = !fLayerBounds.contains(imageBounds);
   *         }
   *
   *         if (requireLayerCrop) {
   *             analysis |= BoundsAnalysis::kRequiresLayerCrop;
   *             // And since the layer crop will have to be applied externally, we can restrict the
   *             // sample bounds to the intersection of dstBounds and layerBounds
   *             SkIRect layerBoundsInDst = Mapping::map(SkIRect(fLayerBounds), xtraTransform);
   *             // In some cases these won't intersect, usually in a complex graph where the input is
   *             // a bitmap or the dynamic source, in which case it hasn't been clipped or dropped by
   *             // earlier image filter processing for that particular node. We could return a flag here
   *             // to signal that the operation should be treated as transparent black, but that would
   *             // create more shader combinations and image sampling will still do the right thing by
   *             // leaving 'pixelCenterBounds' as the original 'dstBounds'.
   *             (void) pixelCenterBounds.intersect(SkRect::Make(layerBoundsInDst));
   *         }
   *         // else this is a decal-tiled, non-transparent affecting FilterResult that doesn't have
   *         // its pixel data clipped by the layer bounds, so the layer crop doesn't have to be applied
   *         // separately. But this means that the image will be sampled over all of 'dstBounds'.
   *     }
   *     // else the layer bounds geometry isn't visible, so 'dstBounds' is already a tighter bounding
   *     // box for how the image will be sampled.
   *
   *     // 2. Are the tiling and deferred color filter effects visible in the sampled bounds
   *     SkRect imageBounds = SkRect::Make(fImage->dimensions());
   *     LayerSpace<SkMatrix> netTransform = fTransform;
   *     netTransform.postConcat(LayerSpace<SkMatrix>(xtraTransform));
   *     SkM44 netM44{SkMatrix(netTransform)};
   *
   *     const auto [xAxisAligned, yAxisAligned] = are_axes_nearly_integer_aligned(netTransform);
   *     const bool isPixelAligned = xAxisAligned && yAxisAligned;
   *     // When decal sampling, we use an inset image bounds for checking if the dst is covered. If not,
   *     // an image that exactly filled the dst bounds could still sample transparent black, in which
   *     // case the transform's scale factor needs to be taken into account.
   *     const bool decalLeaks = scope != BoundsScope::kRescale &&
   *                             fTileMode == SkTileMode::kDecal &&
   *                             fSamplingOptions != kNearestNeighbor &&
   *                             !isPixelAligned;
   *
   *     const float sampleRadius = fSamplingOptions.useCubic ? kCubicRadius : kHalfPixel;
   *     SkRect safeImageBounds = imageBounds.makeInset(sampleRadius, sampleRadius);
   *     if (fSamplingOptions == kDefaultSampling && !isPixelAligned) {
   *         // When using default sampling, integer translations are eventually downgraded to nearest
   *         // neighbor, so the 1/2px inset clamping is sufficient to safely access within the subset.
   *         // When staying with linear filtering, a sample at 1/2px inset exactly will end up accessing
   *         // one external pixel with a weight of 0 (but MSAN will complain and not all GPUs actually
   *         // seem to get that correct). To be safe we have to clamp to epsilon inside the 1/2px.
   *         safeImageBounds.inset(xAxisAligned ? 0.f : kRoundEpsilon,
   *                               yAxisAligned ? 0.f : kRoundEpsilon);
   *     }
   *     bool hasPixelPadding = fBoundary != PixelBoundary::kUnknown;
   *
   *     if (!SkRectPriv::QuadContainsRect(netM44,
   *                                       decalLeaks ? safeImageBounds : imageBounds,
   *                                       pixelCenterBounds,
   *                                       kRoundEpsilon)) {
   *         analysis |= BoundsAnalysis::kDstBoundsNotCovered;
   *         if (fillsLayerBounds) {
   *             analysis |= BoundsAnalysis::kHasLayerFillingEffect;
   *         }
   *         if (decalLeaks) {
   *             // Some amount of decal tiling will be visible in the output so check the relative size
   *             // of the decal interpolation from texel to dst space; if it's not close to 1 it needs
   *             // to be handled specially to keep rendering methods visually consistent.
   *             float scaleFactors[2];
   *             if (!(SkMatrix(netTransform).getMinMaxScales(scaleFactors) &&
   *                     SkScalarNearlyEqual(scaleFactors[0], 1.f, 0.2f) &&
   *                     SkScalarNearlyEqual(scaleFactors[1], 1.f, 0.2f))) {
   *                 analysis |= BoundsAnalysis::kRequiresDecalInLayerSpace;
   *                 if (fBoundary == PixelBoundary::kTransparent) {
   *                     // Turn off considering the transparent padding as safe to prevent that
   *                     // transparency from multiplying with the layer-space decal effect.
   *                     hasPixelPadding = false;
   *                 }
   *             }
   *         }
   *     }
   *
   *     if (scope == BoundsScope::kDeferred) {
   *         return analysis; // skip sampling analysis
   *     } else if (scope == BoundsScope::kCanDrawDirectly &&
   *                !(analysis & BoundsAnalysis::kHasLayerFillingEffect)) {
   *         // When drawing the image directly, the geometry is limited to the image. If the texels
   *         // are pixel aligned, then it is safe to skip shader-based tiling.
   *         const bool nnOrBilerp = fSamplingOptions == kDefaultSampling ||
   *                                 fSamplingOptions == kNearestNeighbor;
   *         if (nnOrBilerp && (hasPixelPadding || isPixelAligned)) {
   *             return analysis;
   *         }
   *     }
   *
   *     // 3. Would image pixels outside of its subset be sampled if shader-clamping is skipped?
   *
   *     // Include the padding for sampling analysis and inset the dst by 1/2 px to represent where the
   *     // sampling is evaluated at.
   *     if (hasPixelPadding) {
   *         safeImageBounds.outset(1.f, 1.f);
   *     }
   *     pixelCenterBounds.inset(kHalfPixel, kHalfPixel);
   *
   *     // True if all corners of 'pixelCenterBounds' are on the inside of each edge of
   *     // 'safeImageBounds', ordered T,R,B,L.
   *     skvx::int4 edgeMask = SkRectPriv::QuadContainsRectMask(netM44,
   *                                                            safeImageBounds,
   *                                                            pixelCenterBounds,
   *                                                            kRoundEpsilon);
   *     if (!all(edgeMask)) {
   *         // Sampling outside the image subset occurs, but if the edges that are exceeded are HW
   *         // edges, then we can avoid using shader-based tiling.
   *         skvx::int4 hwEdge{fImage->subset().fTop == 0,
   *                           fImage->subset().fRight == fImage->backingStoreDimensions().fWidth,
   *                           fImage->subset().fBottom == fImage->backingStoreDimensions().fHeight,
   *                           fImage->subset().fLeft == 0};
   *         if (fTileMode == SkTileMode::kRepeat || fTileMode == SkTileMode::kMirror) {
   *             // For periodic tile modes, we require both edges on an axis to be HW edges
   *             hwEdge = hwEdge & skvx::shuffle<2,3,0,1>(hwEdge); // TRBL & BLTR
   *         }
   *         if (!all(edgeMask | hwEdge)) {
   *             analysis |= BoundsAnalysis::kRequiresShaderTiling;
   *         }
   *     }
   *
   *     return analysis;
   * }
   * ```
   */
  private fun analyzeBounds(
    xtraTransform: SkMatrix,
    dstBounds: SkIRect,
    scope: BoundsScope = TODO(),
  ): SkEnumBitMask<BoundsAnalysis> {
    TODO("Implement analyzeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<BoundsAnalysis> analyzeBounds(const LayerSpace<SkIRect>& dstBounds,
   *                                                 BoundsScope scope = BoundsScope::kDeferred) const {
   *         return this->analyzeBounds(SkMatrix::I(), SkIRect(dstBounds), scope);
   *     }
   * ```
   */
  private fun analyzeBounds(dstBounds: LayerSpace<SkIRect>, scope: BoundsScope = TODO()): SkEnumBitMask<BoundsAnalysis> {
    TODO("Implement analyzeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canClampToTransparentBoundary(SkEnumBitMask<BoundsAnalysis> analysis) const {
   *         return fTileMode == SkTileMode::kDecal &&
   *                fBoundary == PixelBoundary::kTransparent &&
   *                !(analysis & BoundsAnalysis::kRequiresDecalInLayerSpace);
   *     }
   * ```
   */
  private fun canClampToTransparentBoundary(analysis: SkEnumBitMask<BoundsAnalysis>): Boolean {
    TODO("Implement canClampToTransparentBoundary")
  }

  /**
   * C++ original:
   * ```cpp
   * FilterResult FilterResult::rescale(const Context& ctx,
   *                                    const LayerSpace<SkSize>& scale,
   *                                    bool enforceDecal,
   *                                    bool allowOverscaling) const {
   *     LayerSpace<SkIRect> visibleLayerBounds = fLayerBounds;
   *     if (!fImage || !visibleLayerBounds.intersect(ctx.desiredOutput()) ||
   *         scale.width() <= 0.f || scale.height() <= 0.f) {
   *         return {};
   *     }
   *
   *     // NOTE: For the first pass, PixelSpace and LayerSpace are equivalent
   *     PixelSpace<SkIPoint> origin;
   *     const bool pixelAligned = is_nearly_integer_translation(fTransform, &origin);
   *     SkEnumBitMask<BoundsAnalysis> analysis = this->analyzeBounds(ctx.desiredOutput(),
   *                                                                  BoundsScope::kRescale);
   *
   *     // If there's no actual scaling, and no other effects that have to be resolved for blur(),
   *     // then just extract the necessary subset. Otherwise fall through and apply the effects with
   *     // scale factor (possibly identity).
   *     const bool canDeferTiling =
   *             pixelAligned &&
   *             !(analysis & BoundsAnalysis::kRequiresLayerCrop) &&
   *             !(enforceDecal && (analysis & BoundsAnalysis::kHasLayerFillingEffect));
   *
   *     // To match legacy color space conversion logic, treat a null src as sRGB and a null dst as
   *     // as the src CS.
   *     const SkColorSpace* srcCS = fImage->getColorSpace() ? fImage->getColorSpace()
   *                                                         : sk_srgb_singleton();
   *     const SkColorSpace* dstCS = ctx.colorSpace() ? ctx.colorSpace() : srcCS;
   *     const bool hasEffectsToApply =
   *             !canDeferTiling ||
   *             SkToBool(fColorFilter) ||
   *             fImage->colorType() != ctx.backend()->colorType() ||
   *             !SkColorSpace::Equals(srcCS, dstCS);
   *
   *     int xSteps = downscale_step_count(scale.width());
   *     int ySteps = downscale_step_count(scale.height());
   *     if (xSteps == 0 && ySteps == 0 && !hasEffectsToApply) {
   *         if (analysis & BoundsAnalysis::kHasLayerFillingEffect) {
   *             // At this point, the only effects that could be visible is a non-decal mode, so just
   *             // return the image with adjusted layer bounds to match desired output.
   *             FilterResult noop = *this;
   *             noop.fLayerBounds = visibleLayerBounds;
   *             return noop;
   *         } else {
   *             // The visible layer bounds represents a tighter bounds than the image itself
   *             return this->subset(origin, visibleLayerBounds);
   *         }
   *     }
   *
   *     PixelSpace<SkIRect> srcRect;
   *     SkTileMode tileMode;
   *     bool cfBorder = false;
   *     bool deferPeriodicTiling = false;
   *     if (canDeferTiling && (analysis & BoundsAnalysis::kHasLayerFillingEffect)) {
   *         // When we can defer tiling, and said tiling is visible, rescaling the original image
   *         // uses smaller textures.
   *         srcRect = LayerSpace<SkIRect>(SkIRect::MakeXYWH(origin.x(), origin.y(),
   *                                                         fImage->width(), fImage->height()));
   *         if (fTileMode == SkTileMode::kDecal &&
   *             (analysis & BoundsAnalysis::kHasLayerFillingEffect)) {
   *             // Like in applyColorFilter() evaluate the transparent CF'ed border and clamp to it.
   *             tileMode = SkTileMode::kClamp;
   *             cfBorder = true;
   *         } else {
   *             tileMode = fTileMode;
   *             deferPeriodicTiling = tileMode == SkTileMode::kRepeat ||
   *                                   tileMode == SkTileMode::kMirror;
   *         }
   *     } else {
   *         // Otherwise we either have to rescale the layer-bounds-sized image (!canDeferTiling)
   *         // or the tiling isn't visible so the layer bounds represents a smaller effective
   *         // image than the original image data.
   *         srcRect = visibleLayerBounds;
   *         tileMode = SkTileMode::kDecal;
   *     }
   *
   *     srcRect = srcRect.relevantSubset(ctx.desiredOutput(), tileMode);
   *     // To avoid incurring error from rounding up the dimensions at every step, the logical size of
   *     // the image is tracked in floats through the whole process; rounding to integers is only done
   *     // to produce a conservative pixel buffer and clamp-tiling is used so that partially covered
   *     // pixels are filled with the un-weighted color.
   *     PixelSpace<SkRect> stepBoundsF{srcRect};
   *     if (stepBoundsF.isEmpty()) {
   *         return {};
   *     }
   *     // stepPixelBounds holds integer pixel values (as floats) and includes any padded outsetting
   *     // that was rendered by the previous step, while stepBoundsF does not have any padding.
   *     PixelSpace<SkRect> stepPixelBounds{srcRect};
   *
   *     // If we made it here, at least one iteration is required, even if xSteps and ySteps are 0.
   *     FilterResult image = *this;
   *     if (!pixelAligned && (xSteps > 0 || ySteps > 0)) {
   *         // If the source image has a deferred transform with a downscaling factor, we don't want to
   *         // necessarily compose the first rescale step's transform with it because we will then be
   *         // missing pixels in the bilinear filtering and create sampling artifacts during animations.
   *         // NOTE: Force nextSteps counts to the max integer value when the accumulated scale factor
   *         // is not finite, to force the input image to be resolved.
   *         LayerSpace<SkSize> netScale = image.fTransform.mapSize(scale);
   *         int nextXSteps = std::isfinite(netScale.width()) ? downscale_step_count(netScale.width())
   *                                                          : std::numeric_limits<int>::max();
   *         int nextYSteps = std::isfinite(netScale.height()) ? downscale_step_count(netScale.height())
   *                                                           : std::numeric_limits<int>::max();
   *         // We only need to resolve the deferred transform if the rescaling along an axis is not
   *         // near identity (steps > 0). If it's near identity, there's no real difference in sampling
   *         // between resolving here and deferring it to the first rescale iteration.
   *         if ((xSteps > 0 && nextXSteps > xSteps) || (ySteps > 0 && nextYSteps > ySteps)) {
   *             // Resolve the deferred transform. We don't just fold the deferred scale factor into
   *             // the rescaling steps because, for better or worse, the deferred transform does not
   *             // otherwise participate in progressive scaling so we should be consistent.
   *             image = image.resolve(ctx, srcRect);
   *             if (!image) {
   *                 // Early out if the resolve failed
   *                 return {};
   *             }
   *             if (!cfBorder) {
   *                 // This sets the resolved image to match either kDecal or the deferred tile mode.
   *                 image.fTileMode = tileMode;
   *             } // else leave it as kDecal when cfBorder is true
   *         }
   *     }
   *
   *     if (deferPeriodicTiling) {
   *         // The periodic tiling effect will be manually rendered into the lower resolution image so
   *         // that clamp tiling can be used at each decimation.
   *         image.fTileMode = SkTileMode::kClamp;
   *     } else {
   *         // When not deferring periodic tiling, it provides a better user behavior for animating
   *         // sigma values and matrix scale factors to not overscale to the next factor of 1/2 and just
   *         // scale the requisite amount between 1/2 and 1 for the final step.
   *         //
   *         // This can lead to some slight flickering when content animates underneath a fixed blur
   *         // region, but this scenario is most likely to occur with backdrop filters. Backdrop filters
   *         // generally use kMirror for their boundary condition so would hit the periodic tiling case
   *         // anyways.
   *         //
   *         // The long term solution to address all of these issues is to be able to track bounds and
   *         // image placement in floating point, and blend over and underscaled images into an image
   *         // of the exact required size.
   *         allowOverscaling = false;
   *     }
   *
   *     // For now, if we are deferring periodic tiling, we need to ensure that the low-res image bounds
   *     // are pixel aligned. This is because the tiling is applied at the pixel level in SkImageShader,
   *     // and we need the period of the low-res image to align with the original high-resolution period
   *     // If/when SkImageShader supports shader-tiling over fractional bounds, this can relax.
   *     float finalScaleX = xSteps > 0 ? (allowOverscaling ? (1.f / (1 << xSteps))
   *                                                        : scale.width())
   *                                    : 1.f;
   *     float finalScaleY = ySteps > 0 ? (allowOverscaling ? (1.f / (1 << ySteps))
   *                                                        : scale.height())
   *                                    : 1.f;
   *
   *     do {
   *         float sx = 1.f;
   *         if (xSteps > 0) {
   *             sx = xSteps > 1 ? 0.5f : srcRect.width()*finalScaleX / stepBoundsF.width();
   *             xSteps--;
   *         }
   *
   *         float sy = 1.f;
   *         if (ySteps > 0) {
   *             sy = ySteps > 1 ? 0.5f : srcRect.height()*finalScaleY / stepBoundsF.height();
   *             ySteps--;
   *         }
   *
   *         // Downscale relative to the center of the image, which better distributes any sort of
   *         // sampling errors across the image (vs. emphasizing the bottom right edges).
   *         PixelSpace<SkRect> dstBoundsF = scale_about_center(stepBoundsF, sx, sy);
   *         const bool finalXStep = xSteps == 0 && sx != 1.f;
   *         const bool finalYStep = ySteps == 0 && sy != 1.f;
   *         if (deferPeriodicTiling && (finalXStep || finalYStep)) {
   *             PixelSpace<SkIRect> dstPixels = dstBoundsF.roundOut();
   *             dstBoundsF = PixelSpace<SkRect>({
   *                 finalXStep ? (float) dstPixels.left()   : dstBoundsF.left(),
   *                 finalYStep ? (float) dstPixels.top()    : dstBoundsF.top(),
   *                 finalXStep ? (float) dstPixels.right()  : dstBoundsF.right(),
   *                 finalYStep ? (float) dstPixels.bottom() : dstBoundsF.bottom()});
   *         }
   *
   *         // NOTE: Rounding out is overly conservative when dstBoundsF has an odd integer width/height
   *         // but with coordinates at 1/2. In this case, we could create a pixel grid that has a
   *         // fractional translation in the final FilterResult but that will best be done when
   *         // FilterResult tracks floating bounds.
   *         PixelSpace<SkIRect> dstPixelBounds = dstBoundsF.roundOut();
   *
   *         PixelBoundary boundary = PixelBoundary::kUnknown;
   *         PixelSpace<SkIRect> sampleBounds = dstPixelBounds;
   *         if (tileMode == SkTileMode::kDecal) {
   *             boundary = PixelBoundary::kTransparent;
   *         } else {
   *             // This is roughly equivalent to using PixelBoundary::kInitialized, but keeps some of
   *             // the later logic simpler.
   *             dstPixelBounds.outset(LayerSpace<SkISize>({1,1}));
   *         }
   *
   *         AutoSurface surface{ctx, dstPixelBounds, boundary, /*renderInParameterSpace=*/false};
   *         if (surface) {
   *             const auto scaleXform = PixelSpace<SkMatrix>::RectToRect(stepBoundsF, dstBoundsF);
   *
   *             // Redo analysis with the actual scale transform and padded low res bounds.
   *             // With the padding added to dstPixelBounds, intermediate steps should not require
   *             // shader tiling. Unfortunately, when the last step requires a scale factor other than
   *             // 1/2, shader based clamping may still be necessary with just a single pixel of padding
   *             // TODO: Given that the final step may often require shader-based tiling, it may make
   *             // sense to tile into a large enough texture that the subsequent blurs will not require
   *             // any shader-based tiling.
   *             analysis = image.analyzeBounds(SkMatrix(scaleXform),
   *                                            SkIRect(sampleBounds),
   *                                            BoundsScope::kRescale);
   *
   *             // Primary fill that will cover all of 'sampleBounds'
   *             SkPaint paint;
   *             paint.setShader(image.getAnalyzedShaderView(ctx, image.sampling(), analysis));
   * #if !defined(SK_USE_SRCOVER_FOR_FILTERS)
   *             paint.setBlendMode(SkBlendMode::kSrc);
   * #endif
   *
   *             PixelSpace<SkRect> srcSampled;
   *             SkAssertResult(scaleXform.inverseMapRect(PixelSpace<SkRect>(sampleBounds),
   *                                                      &srcSampled));
   *
   *             surface->save();
   *                 surface->concat(SkMatrix(scaleXform));
   *                 surface->drawRect(SkRect(srcSampled), paint);
   *             surface->restore();
   *
   *             if (cfBorder) {
   *                 // Fill in the border with the transparency-affecting color filter, which is
   *                 // what the image shader's tile mode would have produced anyways but this avoids
   *                 // triggering shader-based tiling.
   *                 SkASSERT(fColorFilter && as_CFB(fColorFilter)->affectsTransparentBlack());
   *                 SkASSERT(tileMode == SkTileMode::kClamp);
   *
   *                 draw_color_filtered_border(surface.canvas(), dstPixelBounds, fColorFilter);
   *                 // Clamping logic will preserve its values on subsequent rescale steps.
   *                 cfBorder = false;
   *             } else if (tileMode != SkTileMode::kDecal) {
   *                 // Draw the edges of the shader into the padded border, respecting the tile mode
   *                 draw_tiled_border(surface.canvas(), tileMode, paint, scaleXform,
   *                                   stepPixelBounds, PixelSpace<SkRect>(dstPixelBounds));
   *             }
   *         } else {
   *             // Rescaling can't complete, no sense in downscaling non-existent data
   *             return {};
   *         }
   *
   *         image = surface.snap();
   *         // If we are deferring periodic tiling, use kClamp on subsequent steps to preserve the
   *         // border pixels. The original tile mode will be restored at the end.
   *         image.fTileMode = deferPeriodicTiling ? SkTileMode::kClamp : tileMode;
   *
   *         stepBoundsF = dstBoundsF;
   *         stepPixelBounds = PixelSpace<SkRect>(dstPixelBounds);
   *     } while(xSteps > 0 || ySteps > 0);
   *
   *
   *     // Rebuild the downscaled image, including a transform back to the original layer-space
   *     // resolution, restoring the layer bounds it should fill, and setting tile mode.
   *     if (deferPeriodicTiling) {
   *         // Inset the image to undo the manually added border of pixels, which will allow the result
   *         // to have the kInitialized boundary state.
   *         image = image.insetByPixel();
   *     } else {
   *         SkASSERT(tileMode == SkTileMode::kDecal || tileMode == SkTileMode::kClamp);
   *         // Leave the image as-is. If it's decal tiled, this preserves the known transparent
   *         // boundary. If it's clamp tiled, we want to clamp to the carefully maintained boundary
   *         // pixels that better preserved the original boundary. Taking a subset like we did for
   *         // periodic tiles would effectively clamp to the interior of the image.
   *     }
   *     image.fTileMode = tileMode;
   *     image.fTransform.postConcat(
   *             LayerSpace<SkMatrix>::RectToRect(stepBoundsF, LayerSpace<SkRect>{srcRect}));
   *     image.fLayerBounds = visibleLayerBounds;
   *
   *     SkASSERT(!enforceDecal || image.fTileMode == SkTileMode::kDecal);
   *     SkASSERT(image.fTileMode != SkTileMode::kDecal ||
   *              image.fBoundary == PixelBoundary::kTransparent);
   *     SkASSERT(!deferPeriodicTiling || image.fBoundary == PixelBoundary::kInitialized);
   *     return image;
   * }
   * ```
   */
  private fun rescale(
    ctx: Context,
    scale: LayerSpace<SkSize>,
    enforceDecal: Boolean,
    allowOverscaling: Boolean,
  ): FilterResult {
    TODO("Implement rescale")
  }

  /**
   * C++ original:
   * ```cpp
   * void FilterResult::draw(const Context& ctx,
   *                         SkDevice* device,
   *                         bool preserveDeviceState,
   *                         const SkBlender* blender) const {
   *     const bool blendAffectsTransparentBlack = blender && as_BB(blender)->affectsTransparentBlack();
   *     if (!fImage) {
   *         // The image is transparent black, this is a no-op unless we need to apply the blend mode
   *         if (blendAffectsTransparentBlack) {
   *             SkPaint clear;
   *             clear.setColor4f(SkColors::kTransparent);
   *             clear.setBlender(sk_ref_sp(blender));
   *             device->drawPaint(clear);
   *         }
   *         return;
   *     }
   *
   *     BoundsScope scope = blendAffectsTransparentBlack ? BoundsScope::kShaderOnly
   *                                                      : BoundsScope::kCanDrawDirectly;
   *     SkEnumBitMask<BoundsAnalysis> analysis = this->analyzeBounds(device->localToDevice(),
   *                                                                  device->devClipBounds(),
   *                                                                  scope);
   *
   *     if (analysis & BoundsAnalysis::kRequiresLayerCrop) {
   *         if (blendAffectsTransparentBlack) {
   *             // This is similar to the resolve() path in applyColorFilter() when the filter affects
   *             // transparent black but must be applied after the prior visible layer bounds clip.
   *             // NOTE: We map devClipBounds() by the local-to-device matrix instead of the Context
   *             // mapping because that works for both use cases: drawing to the final device (where
   *             // the transforms are the same), or drawing to intermediate layer images (where they
   *             // are not the same).
   *             LayerSpace<SkIRect> dstBounds;
   *             if (!LayerSpace<SkMatrix>(device->localToDevice()).inverseMapRect(
   *                         LayerSpace<SkIRect>(device->devClipBounds()), &dstBounds)) {
   *                 return;
   *             }
   *             // Regardless of the scenario, the end result is that it's in layer space.
   *             FilterResult clipped = this->resolve(ctx, dstBounds);
   *             clipped.draw(ctx, device, preserveDeviceState, blender);
   *             return;
   *         }
   *         // Otherwise we can apply the layer bounds as a clip to avoid an intermediate render pass
   *         if (preserveDeviceState) {
   *             device->pushClipStack();
   *         }
   *         device->clipRect(SkRect::Make(SkIRect(fLayerBounds)), SkClipOp::kIntersect, /*aa=*/true);
   *     }
   *
   *     // If we are an integer translate, the default bilinear sampling *should* be equivalent to
   *     // nearest-neighbor. Going through the direct image-drawing path tends to detect this
   *     // and reduce sampling automatically. When we have to use an image shader, this isn't
   *     // detected and some GPUs' linear filtering doesn't exactly match nearest-neighbor and can
   *     // lead to leaks beyond the image's subset. Detect and reduce sampling explicitly.
   *     const bool pixelAligned =
   *             is_nearly_integer_translation(fTransform) &&
   *             is_nearly_integer_translation(skif::LayerSpace<SkMatrix>(device->localToDevice()));
   *     SkSamplingOptions sampling = fSamplingOptions;
   *     if (sampling == kDefaultSampling && pixelAligned) {
   *         sampling = {};
   *     }
   *
   *     if (analysis & BoundsAnalysis::kHasLayerFillingEffect ||
   *         (blendAffectsTransparentBlack && (analysis & BoundsAnalysis::kDstBoundsNotCovered))) {
   *         // Fill the canvas with the shader, so that the pixels beyond the image dimensions are still
   *         // covered by the draw and either resolve tiling into the image, color filter transparent
   *         // black, apply the blend mode to the dst, or any combination thereof.
   *         SkPaint paint;
   *         if (!preserveDeviceState && !blender) {
   *             // When we don't care about the device's prior contents, the default blender can be kSrc
   * #if !defined(SK_USE_SRCOVER_FOR_FILTERS)
   *             paint.setBlendMode(SkBlendMode::kSrc);
   * #endif
   *         } else {
   *             paint.setBlender(sk_ref_sp(blender));
   *         }
   *         paint.setShader(this->getAnalyzedShaderView(ctx, sampling, analysis));
   *         device->drawPaint(paint);
   *     } else {
   *         SkPaint paint;
   *         paint.setBlender(sk_ref_sp(blender));
   *         paint.setColorFilter(fColorFilter);
   *
   *         // src's origin is embedded in fTransform. For historical reasons, drawSpecial() does
   *         // not automatically use the device's current local-to-device matrix, but that's what preps
   *         // it to match the expected layer coordinate system.
   *         SkMatrix netTransform = SkMatrix::Concat(device->localToDevice(), SkMatrix(fTransform));
   *
   *         // Check fSamplingOptions for linear filtering, not 'sampling' since it may have been
   *         // reduced to nearest neighbor.
   *         if (this->canClampToTransparentBoundary(analysis) && fSamplingOptions == kDefaultSampling) {
   *             SkASSERT(!(analysis & BoundsAnalysis::kRequiresShaderTiling));
   *             // Draw non-AA with a 1px outset image so that the transparent boundary filtering is
   *             // not multiplied with the AA (which creates a harsher AA transition).
   *             if (!preserveDeviceState && !blender) {
   *                 // Since this is a non-AA draw, kSrc can be more efficient if we are the default
   *                 // blend mode and can assume the prior dst pixels were transparent black.
   * #if !defined(SK_USE_SRCOVER_FOR_FILTERS)
   *                 paint.setBlendMode(SkBlendMode::kSrc);
   * #endif
   *             }
   *             netTransform.preTranslate(-1.f, -1.f);
   *             device->drawSpecial(fImage->makePixelOutset().get(), netTransform, sampling, paint,
   *                                 SkCanvas::kFast_SrcRectConstraint);
   *         } else {
   *             paint.setAntiAlias(true);
   *             SkCanvas::SrcRectConstraint constraint = SkCanvas::kFast_SrcRectConstraint;
   *             if (analysis & BoundsAnalysis::kRequiresShaderTiling) {
   *                 constraint = SkCanvas::kStrict_SrcRectConstraint;
   *                 ctx.markShaderBasedTilingRequired(SkTileMode::kClamp);
   *             }
   *             device->drawSpecial(fImage.get(), netTransform, sampling, paint, constraint);
   *         }
   *     }
   *
   *     if (preserveDeviceState && (analysis & BoundsAnalysis::kRequiresLayerCrop)) {
   *         device->popClipStack();
   *     }
   * }
   * ```
   */
  private fun draw(
    ctx: Context,
    device: SkDevice?,
    preserveDeviceState: Boolean,
    blender: SkBlender? = TODO(),
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> FilterResult::asShader(const Context& ctx,
   *                                        const SkSamplingOptions& xtraSampling,
   *                                        SkEnumBitMask<ShaderFlags> flags,
   *                                        const LayerSpace<SkIRect>& sampleBounds) const {
   *     if (!fImage) {
   *         return nullptr;
   *     }
   *     // Even if flags don't force resolving the filter result to an axis-aligned image, if the
   *     // extra sampling to be applied is not compatible with the accumulated transform and sampling,
   *     // or if the logical image is cropped by the layer bounds, the FilterResult will need to be
   *     // resolved to an image before we wrap it as an SkShader. When checking if cropped, we use the
   *     // FilterResult's layer bounds instead of the context's desired output, assuming that the layer
   *     // bounds reflect the bounds of the coords a parent shader will pass to eval().
   *     const bool currentXformIsInteger = is_nearly_integer_translation(fTransform);
   *     const bool nextXformIsInteger = !(flags & ShaderFlags::kNonTrivialSampling);
   *
   *     SkBlendMode colorFilterMode;
   *     SkEnumBitMask<BoundsAnalysis> analysis = this->analyzeBounds(sampleBounds,
   *                                                                  BoundsScope::kShaderOnly);
   *
   *     SkSamplingOptions sampling = xtraSampling;
   *     const bool needsResolve =
   *             // Deferred calculations on the input would be repeated with each sample, but we allow
   *             // simple color filters to skip resolving since their repeated math should be cheap.
   *             (flags & ShaderFlags::kSampledRepeatedly &&
   *                     ((fColorFilter && (!fColorFilter->asAColorMode(nullptr, &colorFilterMode) ||
   *                                        colorFilterMode > SkBlendMode::kLastCoeffMode)) ||
   *                      !SkColorSpace::Equals(fImage->getColorSpace(), ctx.colorSpace()))) ||
   *             // The deferred sampling options can't be merged with the one requested
   *             !compatible_sampling(fSamplingOptions, currentXformIsInteger,
   *                                  &sampling, nextXformIsInteger) ||
   *             // The deferred edge of the layer bounds is visible to sampling
   *             (analysis & BoundsAnalysis::kRequiresLayerCrop);
   *
   *     // Downgrade to nearest-neighbor if the sequence of sampling doesn't do anything
   *     if (sampling == kDefaultSampling && nextXformIsInteger &&
   *         (needsResolve || currentXformIsInteger)) {
   *         sampling = {};
   *     }
   *
   *     sk_sp<SkShader> shader;
   *     if (needsResolve) {
   *         // The resolve takes care of fTransform (sans origin), fTileMode, fColorFilter, and
   *         // fLayerBounds.
   *         FilterResult resolved = this->resolve(ctx, sampleBounds);
   *         if (resolved) {
   *             // Redo the analysis, however, because it's hard to predict HW edge tiling. Since the
   *             // original layer crop was visible, that implies that the now-resolved image won't cover
   *             // dst bounds. Since we are using this as a shader to fill the dst bounds, we may have
   *             // to still do shader-clamping (to a transparent boundary) if the resolved image doesn't
   *             // have HW-tileable boundaries.
   *             [[maybe_unused]] static constexpr SkEnumBitMask<BoundsAnalysis> kExpectedAnalysis =
   *                     BoundsAnalysis::kDstBoundsNotCovered | BoundsAnalysis::kRequiresShaderTiling;
   *             analysis = resolved.analyzeBounds(sampleBounds, BoundsScope::kShaderOnly);
   *             SkASSERT(!(analysis & ~kExpectedAnalysis));
   *             return resolved.getAnalyzedShaderView(ctx, sampling, analysis);
   *         }
   *     } else {
   *         shader = this->getAnalyzedShaderView(ctx, sampling, analysis);
   *     }
   *
   *     return shader;
   * }
   * ```
   */
  private fun asShader(
    ctx: Context,
    xtraSampling: SkSamplingOptions,
    flags: SkEnumBitMask<ShaderFlags>,
    sampleBounds: LayerSpace<SkIRect>,
  ): SkSp<SkShader> {
    TODO("Implement asShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> FilterResult::getAnalyzedShaderView(
   *         const Context& ctx,
   *         const SkSamplingOptions& finalSampling,
   *         SkEnumBitMask<BoundsAnalysis> analysis) const {
   *     const SkMatrix& localMatrix(fTransform);
   *     const SkRect imageBounds = SkRect::Make(fImage->dimensions());
   *     // We need to apply the decal in a coordinate space that matches the resolution of the layer
   *     // space. If the transform preserves rectangles, map the image bounds by the transform so we
   *     // can apply it before we evaluate the shader. Otherwise decompose the transform into a
   *     // non-scaling post-decal transform and a scaling pre-decal transform.
   *     SkMatrix postDecal, preDecal;
   *     if (localMatrix.rectStaysRect() ||
   *         !(analysis & BoundsAnalysis::kRequiresDecalInLayerSpace)) {
   *         postDecal = SkMatrix::I();
   *         preDecal = localMatrix;
   *     } else {
   *         decompose_transform(localMatrix, imageBounds.center(), &postDecal, &preDecal);
   *     }
   *
   *     // If the image covers the dst bounds, then its tiling won't be visible, so we can switch
   *     // to the faster kClamp for either HW or shader-based tiling. If we are applying the decal
   *     // in layer space, then that extra shader implements the tiling, so we can switch to clamp
   *     // for the image shader itself.
   *     SkTileMode effectiveTileMode = fTileMode;
   *     const bool decalClampToTransparent = this->canClampToTransparentBoundary(analysis);
   *     const bool strict = SkToBool(analysis & BoundsAnalysis::kRequiresShaderTiling);
   *
   *     sk_sp<SkShader> imageShader;
   *     if (strict && decalClampToTransparent) {
   *         // Make the image shader apply to the 1px outset so that the strict subset includes the
   *         // transparent pixels.
   *         preDecal.preTranslate(-1.f, -1.f);
   *         imageShader = fImage->makePixelOutset()->asShader(SkTileMode::kClamp, finalSampling,
   *                                                           preDecal, strict);
   *         effectiveTileMode = SkTileMode::kClamp;
   *     } else {
   *         if (!(analysis & BoundsAnalysis::kDstBoundsNotCovered) ||
   *             (analysis & BoundsAnalysis::kRequiresDecalInLayerSpace)) {
   *             effectiveTileMode = SkTileMode::kClamp;
   *         }
   *         imageShader = fImage->asShader(effectiveTileMode, finalSampling, preDecal, strict);
   *     }
   *     if (strict) {
   *         ctx.markShaderBasedTilingRequired(effectiveTileMode);
   *     }
   *
   *     if (analysis & BoundsAnalysis::kRequiresDecalInLayerSpace) {
   *         SkASSERT(fTileMode == SkTileMode::kDecal);
   *         // TODO(skbug.com/40043877) - As part of fully supporting subsets in image shaders, it probably
   *         // makes sense to share the subset tiling logic that's in GrTextureEffect as dedicated
   *         // SkShaders. Graphite can then add those to its program as-needed vs. always doing
   *         // shader-based tiling, and CPU can have raster-pipeline tiling applied more flexibly than
   *         // at the bitmap level. At that point, this effect is redundant and can be replaced with the
   *         // decal-subset shader.
   *         const SkRuntimeEffect* decalEffect =
   *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kDecal);
   *
   *         SkRuntimeShaderBuilder builder(sk_ref_sp(decalEffect));
   *         builder.child("image") = std::move(imageShader);
   *         builder.uniform("decalBounds") = preDecal.mapRect(imageBounds);
   *
   *         imageShader = builder.makeShader();
   *     }
   *
   *     if (imageShader && (analysis & BoundsAnalysis::kRequiresDecalInLayerSpace)) {
   *         imageShader = imageShader->makeWithLocalMatrix(postDecal);
   *     }
   *
   *     if (imageShader && fColorFilter) {
   *         imageShader = imageShader->makeWithColorFilter(fColorFilter);
   *     }
   *
   *     // Shader now includes the image, the sampling, the tile mode, the transform, and the color
   *     // filter, skipping deferred effects that aren't present or aren't visible given 'analysis'.
   *     // The last "effect", layer bounds cropping, must be handled externally by either resolving
   *     // the image before hand or clipping the device that's drawing the returned shader.
   *     return imageShader;
   * }
   * ```
   */
  private fun getAnalyzedShaderView(
    ctx: Context,
    finalSampling: SkSamplingOptions,
    analysis: SkEnumBitMask<BoundsAnalysis>,
  ): SkSp<SkShader> {
    TODO("Implement getAnalyzedShaderView")
  }

  /**
   * C++ original:
   * ```cpp
   * void FilterResult::updateTileMode(const Context& ctx, SkTileMode tileMode) {
   *     if (fImage) {
   *         fTileMode = tileMode;
   *         if (tileMode != SkTileMode::kDecal) {
   *             fLayerBounds = ctx.desiredOutput();
   *         }
   *     }
   * }
   * ```
   */
  private fun updateTileMode(ctx: Context, tileMode: SkTileMode) {
    TODO("Implement updateTileMode")
  }

  public enum class ShaderFlags {
    kNone,
    kSampledRepeatedly,
    kNonTrivialSampling,
  }

  public enum class PixelBoundary {
    kUnknown,
    kTransparent,
    kInitialized,
  }

  public enum class BoundsAnalysis {
    kSimple,
    kDstBoundsNotCovered,
    kHasLayerFillingEffect,
    kRequiresLayerCrop,
    kRequiresShaderTiling,
    kRequiresDecalInLayerSpace,
  }

  public enum class BoundsScope {
    kDeferred,
    kCanDrawDirectly,
    kShaderOnly,
    kRescale,
  }

  public companion object {
    public val kDefaultSampling: SkSamplingOptions = TODO("Initialize kDefaultSampling")

    /**
     * C++ original:
     * ```cpp
     * FilterResult FilterResult::MakeFromPicture(const Context& ctx,
     *                                            sk_sp<SkPicture> pic,
     *                                            ParameterSpace<SkRect> cullRect) {
     *     SkASSERT(pic);
     *     LayerSpace<SkIRect> dstBounds = ctx.mapping().paramToLayer(cullRect).roundOut();
     *     if (!dstBounds.intersect(ctx.desiredOutput())) {
     *         return {};
     *     }
     *
     *     // Given the standard usage of the picture image filter (i.e., to render content at a fixed
     *     // resolution that, most likely, differs from the screen's) disable LCD text by removing any
     *     // knowledge of the pixel geometry.
     *     // TODO: Should we just generally do this for layers with image filters? Or can we preserve it
     *     // for layers that are still axis-aligned?
     *     SkSurfaceProps props = ctx.backend()->surfaceProps()
     *                                          .cloneWithPixelGeometry(kUnknown_SkPixelGeometry);
     *     // TODO(b/329700315): The SkPicture may contain dithered content, which would be affected by any
     *     // boundary padding. Until we can control the dither origin, force it to have no padding.
     *     AutoSurface surface{ctx, dstBounds, PixelBoundary::kUnknown,
     *                         /*renderInParameterSpace=*/true, &props};
     *     if (surface) {
     *         surface->clipRect(SkRect(cullRect));
     *         surface->drawPicture(std::move(pic));
     *     }
     *     return surface.snap();
     * }
     * ```
     */
    public fun makeFromPicture(
      ctx: Context,
      pic: SkSp<SkPicture>,
      cullRect: ParameterSpace<SkRect>,
    ): FilterResult {
      TODO("Implement makeFromPicture")
    }

    /**
     * C++ original:
     * ```cpp
     * FilterResult FilterResult::MakeFromShader(const Context& ctx,
     *                                           sk_sp<SkShader> shader,
     *                                           bool dither) {
     *     SkASSERT(shader);
     *
     *     // TODO(b/329700315): Using a boundary other than unknown shifts the origin of dithering, which
     *     // complicates layout test validation in chrome. Until we can control the dither origin,
     *     // force dithered shader FilterResults to have no padding.
     *     PixelBoundary boundary = dither ? PixelBoundary::kUnknown : PixelBoundary::kTransparent;
     *     AutoSurface surface{ctx, ctx.desiredOutput(), boundary, /*renderInParameterSpace=*/true};
     *     if (surface) {
     *         SkPaint paint;
     *         paint.setShader(shader);
     *         paint.setDither(dither);
     * #if !defined(SK_USE_SRCOVER_FOR_FILTERS)
     *         paint.setBlendMode(SkBlendMode::kSrc);
     * #endif
     *         surface->drawPaint(paint);
     *     }
     *     return surface.snap();
     * }
     * ```
     */
    public fun makeFromShader(
      ctx: Context,
      shader: SkSp<SkShader>,
      dither: Boolean,
    ): FilterResult {
      TODO("Implement makeFromShader")
    }

    /**
     * C++ original:
     * ```cpp
     * FilterResult FilterResult::MakeFromImage(const Context& ctx,
     *                                          sk_sp<SkImage> image,
     *                                          SkRect srcRect,
     *                                          ParameterSpace<SkRect> dstRect,
     *                                          const SkSamplingOptions& sampling) {
     *     SkASSERT(image);
     *
     *     SkRect imageBounds = SkRect::Make(image->dimensions());
     *     if (!imageBounds.contains(srcRect)) {
     *         SkMatrix srcToDst = SkMatrix::RectToRectOrIdentity(srcRect, SkRect(dstRect));
     *         if (!srcRect.intersect(imageBounds)) {
     *             return {}; // No overlap, so return an empty/transparent image
     *         }
     *         // Adjust dstRect to match the updated srcRect
     *         dstRect = ParameterSpace<SkRect>{srcToDst.mapRect(srcRect)};
     *     }
     *
     *     if (SkRect(dstRect).isEmpty()) {
     *         return {}; // Output collapses to empty
     *     }
     *
     *     // Check for direct conversion to an SkSpecialImage and then FilterResult. Eventually this
     *     // whole function should be replaceable with:
     *     //    FilterResult(fImage, fSrcRect, fDstRect).applyTransform(mapping.layerMatrix(), fSampling);
     *     SkIRect srcSubset = RoundOut(srcRect);
     *     if (SkRect::Make(srcSubset) == srcRect) {
     *         // Construct an SkSpecialImage from the subset directly instead of drawing.
     *         sk_sp<SkSpecialImage> specialImage = ctx.backend()->makeImage(srcSubset, std::move(image));
     *
     *         // Treat the srcRect's top left as "layer" space since we are folding the src->dst transform
     *         // and the param->layer transform into a single transform step. We don't override the
     *         // PixelBoundary from kUnknown even if srcRect is contained within the 'image' because the
     *         // client could be doing their own external approximate-fit texturing.
     *         skif::FilterResult subset{std::move(specialImage),
     *                                   skif::LayerSpace<SkIPoint>(srcSubset.topLeft())};
     *         SkM44 transform = ctx.mapping().layerMatrix() * SkM44::RectToRect(srcRect, SkRect(dstRect));
     *         return subset.applyTransform(ctx, skif::LayerSpace<SkMatrix>(transform.asM33()), sampling);
     *     }
     *
     *     // For now, draw the src->dst subset of image into a new image.
     *     LayerSpace<SkIRect> dstBounds = ctx.mapping().paramToLayer(dstRect).roundOut();
     *     if (!dstBounds.intersect(ctx.desiredOutput())) {
     *         return {};
     *     }
     *
     *     AutoSurface surface{ctx, dstBounds, PixelBoundary::kTransparent,
     *                         /*renderInParameterSpace=*/true};
     *     if (surface) {
     *         SkPaint paint;
     *         paint.setAntiAlias(true);
     *         surface->drawImageRect(std::move(image), srcRect, SkRect(dstRect), sampling, &paint,
     *                                SkCanvas::kStrict_SrcRectConstraint);
     *     }
     *     return surface.snap();
     * }
     * ```
     */
    public fun makeFromImage(
      ctx: Context,
      image: SkSp<SkImage>,
      srcRect: SkRect,
      dstRect: ParameterSpace<SkRect>,
      sampling: SkSamplingOptions,
    ): FilterResult {
      TODO("Implement makeFromImage")
    }
  }
}
