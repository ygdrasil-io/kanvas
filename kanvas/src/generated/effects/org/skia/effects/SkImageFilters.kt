package org.skia.effects

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.nullopt_t
import org.skia.core.SkPicture
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlender
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkImageFilters {
 * public:
 *     // This is just a convenience type to allow passing SkIRects, SkRects, and optional pointers
 *     // to those types as a crop rect for the image filter factories. It's not intended to be used
 *     // directly.
 *     struct CropRect : public std::optional<SkRect> {
 *         CropRect() {}
 *         // Intentionally not explicit so callers don't have to use this type but can use SkIRect or
 *         // SkRect as desired.
 *         CropRect(const SkIRect& crop) : std::optional<SkRect>(SkRect::Make(crop)) {}
 *         CropRect(const SkRect& crop) : std::optional<SkRect>(crop) {}
 *         CropRect(const std::optional<SkRect>& crop) : std::optional<SkRect>(crop) {}
 *         CropRect(const std::nullopt_t&) : std::optional<SkRect>() {}
 *
 *         // Backwards compatibility for when the APIs used to explicitly accept "const SkRect*"
 *         CropRect(std::nullptr_t) {}
 *         CropRect(const SkIRect* optionalCrop) {
 *             if (optionalCrop) {
 *                 *this = SkRect::Make(*optionalCrop);
 *             }
 *         }
 *         CropRect(const SkRect* optionalCrop) {
 *             if (optionalCrop) {
 *                 *this = *optionalCrop;
 *             }
 *         }
 *
 *         // std::optional doesn't define == when comparing to another optional...
 *         bool operator==(const CropRect& o) const {
 *             return this->has_value() == o.has_value() &&
 *                    (!this->has_value() || this->value() == *o);
 *         }
 *     };
 *
 *     /**
 *      *  Create a filter that implements a custom blend mode. Each output pixel is the result of
 *      *  combining the corresponding background and foreground pixels using the 4 coefficients:
 *      *     k1 * foreground * background + k2 * foreground + k3 * background + k4
 *      *  @param k1, k2, k3, k4 The four coefficients used to combine the foreground and background.
 *      *  @param enforcePMColor If true, the RGB channels will be clamped to the calculated alpha.
 *      *  @param background     The background content, using the source bitmap when this is null.
 *      *  @param foreground     The foreground content, using the source bitmap when this is null.
 *      *  @param cropRect       Optional rectangle that crops the inputs and output.
 *      */
 *     static sk_sp<SkImageFilter> Arithmetic(SkScalar k1, SkScalar k2, SkScalar k3, SkScalar k4,
 *                                            bool enforcePMColor, sk_sp<SkImageFilter> background,
 *                                            sk_sp<SkImageFilter> foreground,
 *                                            const CropRect& cropRect = {});
 *
 *     /**
 *      *  This filter takes an SkBlendMode and uses it to composite the two filters together.
 *      *  @param mode       The blend mode that defines the compositing operation
 *      *  @param background The Dst pixels used in blending, if null the source bitmap is used.
 *      *  @param foreground The Src pixels used in blending, if null the source bitmap is used.
 *      *  @cropRect         Optional rectangle to crop input and output.
 *      */
 *     static sk_sp<SkImageFilter> Blend(SkBlendMode mode, sk_sp<SkImageFilter> background,
 *                                       sk_sp<SkImageFilter> foreground = nullptr,
 *                                       const CropRect& cropRect = {});
 *
 *     /**
 *      *  This filter takes an SkBlendMode and uses it to composite the two filters together.
 *      *  @param blender       The blender that defines the compositing operation
 *      *  @param background The Dst pixels used in blending, if null the source bitmap is used.
 *      *  @param foreground The Src pixels used in blending, if null the source bitmap is used.
 *      *  @cropRect         Optional rectangle to crop input and output.
 *      */
 *     static sk_sp<SkImageFilter> Blend(sk_sp<SkBlender> blender, sk_sp<SkImageFilter> background,
 *                                       sk_sp<SkImageFilter> foreground = nullptr,
 *                                       const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that blurs its input by the separate X and Y sigmas. The provided tile mode
 *      *  is used when the blur kernel goes outside the input image.
 *      *  @param sigmaX   The Gaussian sigma value for blurring along the X axis.
 *      *  @param sigmaY   The Gaussian sigma value for blurring along the Y axis.
 *      *  @param tileMode The tile mode applied at edges .
 *      *                  TODO (michaelludwig) - kMirror is not supported yet
 *      *  @param input    The input filter that is blurred, uses source bitmap if this is null.
 *      *  @param cropRect Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> Blur(SkScalar sigmaX, SkScalar sigmaY, SkTileMode tileMode,
 *                                      sk_sp<SkImageFilter> input, const CropRect& cropRect = {});
 *     // As above, but defaults to the decal tile mode.
 *     static sk_sp<SkImageFilter> Blur(SkScalar sigmaX, SkScalar sigmaY, sk_sp<SkImageFilter> input,
 *                                      const CropRect& cropRect = {}) {
 *         return Blur(sigmaX, sigmaY, SkTileMode::kDecal, std::move(input), cropRect);
 *     }
 *
 *     /**
 *      *  Create a filter that applies the color filter to the input filter results.
 *      *  @param cf       The color filter that transforms the input image.
 *      *  @param input    The input filter, or uses the source bitmap if this is null.
 *      *  @param cropRect Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> ColorFilter(sk_sp<SkColorFilter> cf, sk_sp<SkImageFilter> input,
 *                                             const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that composes 'inner' with 'outer', such that the results of 'inner' are
 *      *  treated as the source bitmap passed to 'outer', i.e. result = outer(inner(source)).
 *      *  @param outer The outer filter that evaluates the results of inner.
 *      *  @param inner The inner filter that produces the input to outer.
 *      */
 *     static sk_sp<SkImageFilter> Compose(sk_sp<SkImageFilter> outer, sk_sp<SkImageFilter> inner);
 *
 *     /**
 *      *  Create a filter that applies a crop to the result of the 'input' filter. Pixels within the
 *      *  crop rectangle are unmodified from what 'input' produced. Pixels outside of crop match the
 *      *  provided SkTileMode (defaulting to kDecal).
 *      *
 *      *  NOTE: The optional CropRect argument for many of the factories is equivalent to creating the
 *      *  filter without a CropRect and then wrapping it in ::Crop(rect, kDecal). Explicitly adding
 *      *  Crop filters lets you control their tiling and use different geometry for the input and the
 *      *  output of another filter.
 *      *
 *      *  @param rect     The cropping geometry
 *      *  @param tileMode The tilemode applied to pixels *outside* of 'crop'
 *      *  @param input    The input filter that is cropped, uses source image if this is null
 *     */
 *     static sk_sp<SkImageFilter> Crop(const SkRect& rect,
 *                                      SkTileMode tileMode,
 *                                      sk_sp<SkImageFilter> input);
 *     static sk_sp<SkImageFilter> Crop(const SkRect& rect, sk_sp<SkImageFilter> input) {
 *         return Crop(rect, SkTileMode::kDecal, std::move(input));
 *     }
 *
 *     /**
 *      *  Create a filter that moves each pixel in its color input based on an (x,y) vector encoded
 *      *  in its displacement input filter. Two color components of the displacement image are
 *      *  mapped into a vector as scale * (color[xChannel], color[yChannel]), where the channel
 *      *  selectors are one of R, G, B, or A.
 *      *  @param xChannelSelector RGBA channel that encodes the x displacement per pixel.
 *      *  @param yChannelSelector RGBA channel that encodes the y displacement per pixel.
 *      *  @param scale            Scale applied to displacement extracted from image.
 *      *  @param displacement     The filter defining the displacement image, or null to use source.
 *      *  @param color            The filter providing the color pixels to be displaced. If null,
 *      *                          it will use the source.
 *      *  @param cropRect         Optional rectangle that crops the color input and output.
 *      */
 *     static sk_sp<SkImageFilter> DisplacementMap(SkColorChannel xChannelSelector,
 *                                                 SkColorChannel yChannelSelector,
 *                                                 SkScalar scale, sk_sp<SkImageFilter> displacement,
 *                                                 sk_sp<SkImageFilter> color,
 *                                                 const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that draws a drop shadow under the input content. This filter produces an
 *      *  image that includes the inputs' content.
 *      *  @param dx         X offset of the shadow.
 *      *  @param dy         Y offset of the shadow.
 *      *  @param sigmaX     blur radius for the shadow, along the X axis.
 *      *  @param sigmaY     blur radius for the shadow, along the Y axis.
 *      *  @param color      color of the drop shadow.
 *      *  @param colorSpace The color space of the drop shadow color.
 *      *  @param input      The input filter, or will use the source bitmap if this is null.
 *      *  @param cropRect   Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> DropShadow(SkScalar dx, SkScalar dy,
 *                                            SkScalar sigmaX, SkScalar sigmaY,
 *                                            SkColor4f color, sk_sp<SkColorSpace> colorSpace,
 *                                            sk_sp<SkImageFilter> input,
 *                                            const CropRect& cropRect = {});
 *     static sk_sp<SkImageFilter> DropShadow(SkScalar dx, SkScalar dy,
 *                                            SkScalar sigmaX, SkScalar sigmaY,
 *                                            SkColor color, sk_sp<SkImageFilter> input,
 *                                            const CropRect& cropRect = {}) {
 *         return DropShadow(dx, dy,
 *                           sigmaX, sigmaY,
 *                           SkColor4f::FromColor(color), /*colorSpace=*/nullptr,
 *                           std::move(input),
 *                           cropRect);
 *     }
 *
 *     /**
 *      *  Create a filter that renders a drop shadow, in exactly the same manner as ::DropShadow,
 *      *  except that the resulting image does not include the input content. This allows the shadow
 *      *  and input to be composed by a filter DAG in a more flexible manner.
 *      *  @param dx         The X offset of the shadow.
 *      *  @param dy         The Y offset of the shadow.
 *      *  @param sigmaX     The blur radius for the shadow, along the X axis.
 *      *  @param sigmaY     The blur radius for the shadow, along the Y axis.
 *      *  @param color      The color of the drop shadow.
 *      *  @param colorSpace The color space of the drop shadow color.
 *      *  @param input      The input filter, or will use the source bitmap if this is null.
 *      *  @param cropRect   Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> DropShadowOnly(SkScalar dx, SkScalar dy,
 *                                                SkScalar sigmaX, SkScalar sigmaY,
 *                                                SkColor4f color, sk_sp<SkColorSpace>,
 *                                                sk_sp<SkImageFilter> input,
 *                                                const CropRect& cropRect = {});
 *     static sk_sp<SkImageFilter> DropShadowOnly(SkScalar dx, SkScalar dy,
 *                                                SkScalar sigmaX, SkScalar sigmaY,
 *                                                SkColor color, sk_sp<SkImageFilter> input,
 *                                                const CropRect& cropRect = {}) {
 *         return DropShadowOnly(dx, dy,
 *                               sigmaX, sigmaY,
 *                               SkColor4f::FromColor(color), /*colorSpace=*/nullptr,
 *                               std::move(input),
 *                               cropRect);
 *     }
 *
 *     /**
 *      * Create a filter that always produces transparent black.
 *      */
 *     static sk_sp<SkImageFilter> Empty();
 *
 *     /**
 *      *  Create a filter that draws the 'srcRect' portion of image into 'dstRect' using the given
 *      *  filter quality. Similar to SkCanvas::drawImageRect. The returned image filter evaluates
 *      *  to transparent black if 'image' is null.
 *      *
 *      *  @param image    The image that is output by the filter, subset by 'srcRect'.
 *      *  @param srcRect  The source pixels sampled into 'dstRect'
 *      *  @param dstRect  The local rectangle to draw the image into.
 *      *  @param sampling The sampling to use when drawing the image.
 *      */
 *     static sk_sp<SkImageFilter> Image(sk_sp<SkImage> image, const SkRect& srcRect,
 *                                       const SkRect& dstRect, const SkSamplingOptions& sampling);
 *
 *     /**
 *      *  Create a filter that draws the image using the given sampling.
 *      *  Similar to SkCanvas::drawImage. The returned image filter evaluates to transparent black if
 *      *  'image' is null.
 *      *
 *      *  @param image    The image that is output by the filter.
 *      *  @param sampling The sampling to use when drawing the image.
 *      */
 *     static sk_sp<SkImageFilter> Image(sk_sp<SkImage> image, const SkSamplingOptions& sampling) {
 *         if (image) {
 *             SkRect r = SkRect::Make(image->bounds());
 *             return Image(std::move(image), r, r, sampling);
 *         } else {
 *             return nullptr;
 *         }
 *     }
 *
 *     /**
 *      *  Create a filter that fills 'lensBounds' with a magnification of the input.
 *      *
 *      *  @param lensBounds The outer bounds of the magnifier effect
 *      *  @param zoomAmount The amount of magnification applied to the input image
 *      *  @param inset      The size or width of the fish-eye distortion around the magnified content
 *      *  @param sampling   The SkSamplingOptions applied to the input image when magnified
 *      *  @param input      The input filter that is magnified; if null the source bitmap is used
 *      *  @param cropRect   Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> Magnifier(const SkRect& lensBounds,
 *                                           SkScalar zoomAmount,
 *                                           SkScalar inset,
 *                                           const SkSamplingOptions& sampling,
 *                                           sk_sp<SkImageFilter> input,
 *                                           const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that applies an NxM image processing kernel to the input image. This can be
 *      *  used to produce effects such as sharpening, blurring, edge detection, etc.
 *      *  @param kernelSize    The kernel size in pixels, in each dimension (N by M).
 *      *  @param kernel        The image processing kernel. Must contain N * M elements, in row order.
 *      *  @param gain          A scale factor applied to each pixel after convolution. This can be
 *      *                       used to normalize the kernel, if it does not already sum to 1.
 *      *  @param bias          A bias factor added to each pixel after convolution.
 *      *  @param kernelOffset  An offset applied to each pixel coordinate before convolution.
 *      *                       This can be used to center the kernel over the image
 *      *                       (e.g., a 3x3 kernel should have an offset of {1, 1}).
 *      *  @param tileMode      How accesses outside the image are treated.
 *      *                       TODO (michaelludwig) - kMirror is not supported yet
 *      *  @param convolveAlpha If true, all channels are convolved. If false, only the RGB channels
 *      *                       are convolved, and alpha is copied from the source image.
 *      *  @param input         The input image filter, if null the source bitmap is used instead.
 *      *  @param cropRect      Optional rectangle to which the output processing will be limited.
 *      */
 *     static sk_sp<SkImageFilter> MatrixConvolution(const SkISize& kernelSize,
 *                                                   const SkScalar kernel[], SkScalar gain,
 *                                                   SkScalar bias, const SkIPoint& kernelOffset,
 *                                                   SkTileMode tileMode, bool convolveAlpha,
 *                                                   sk_sp<SkImageFilter> input,
 *                                                   const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that transforms the input image by 'matrix'. This matrix transforms the
 *      *  local space, which means it effectively happens prior to any transformation coming from the
 *      *  SkCanvas initiating the filtering.
 *      *  @param matrix   The matrix to apply to the original content.
 *      *  @param sampling How the image will be sampled when it is transformed
 *      *  @param input    The image filter to transform, or null to use the source image.
 *      */
 *     static sk_sp<SkImageFilter> MatrixTransform(const SkMatrix& matrix,
 *                                                 const SkSamplingOptions& sampling,
 *                                                 sk_sp<SkImageFilter> input);
 *
 *     /**
 *      *  Create a filter that merges the 'count' filters together by drawing their results in order
 *      *  with src-over blending.
 *      *  @param filters  The input filter array to merge, which must have 'count' elements. Any null
 *      *                  filter pointers will use the source bitmap instead.
 *      *  @param count    The number of input filters to be merged.
 *      *  @param cropRect Optional rectangle that crops all input filters and the output.
 *      */
 *     static sk_sp<SkImageFilter> Merge(sk_sp<SkImageFilter>* const filters, int count,
 *                                       const CropRect& cropRect = {});
 *     /**
 *      *  Create a filter that merges the results of the two filters together with src-over blending.
 *      *  @param first    The first input filter, or the source bitmap if this is null.
 *      *  @param second   The second input filter, or the source bitmap if this null.
 *      *  @param cropRect Optional rectangle that crops the inputs and output.
 *      */
 *     static sk_sp<SkImageFilter> Merge(sk_sp<SkImageFilter> first, sk_sp<SkImageFilter> second,
 *                                       const CropRect& cropRect = {}) {
 *         sk_sp<SkImageFilter> array[] = { std::move(first), std::move(second) };
 *         return Merge(array, 2, cropRect);
 *     }
 *
 *     /**
 *      *  Create a filter that offsets the input filter by the given vector.
 *      *  @param dx       The x offset in local space that the image is shifted.
 *      *  @param dy       The y offset in local space that the image is shifted.
 *      *  @param input    The input that will be moved, if null the source bitmap is used instead.
 *      *  @param cropRect Optional rectangle to crop the input and output.
 *      */
 *     static sk_sp<SkImageFilter> Offset(SkScalar dx, SkScalar dy, sk_sp<SkImageFilter> input,
 *                                        const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that produces the SkPicture as its output, clipped to both 'targetRect' and
 *      *  the picture's internal cull rect.
 *      *
 *      *  If 'pic' is null, the returned image filter produces transparent black.
 *      *
 *      *  @param pic        The picture that is drawn for the filter output.
 *      *  @param targetRect The drawing region for the picture.
 *      */
 *     static sk_sp<SkImageFilter> Picture(sk_sp<SkPicture> pic, const SkRect& targetRect);
 *     // As above, but uses SkPicture::cullRect for the drawing region.
 *     static sk_sp<SkImageFilter> Picture(sk_sp<SkPicture> pic) {
 *         SkRect target = pic ? pic->cullRect() : SkRect::MakeEmpty();
 *         return Picture(std::move(pic), target);
 *     }
 *
 *     /**
 *      *  Create a filter that fills the output with the per-pixel evaluation of the SkShader produced
 *      *  by the SkRuntimeEffectBuilder. The shader is defined in the image filter's local coordinate
 *      *  system, so it will automatically be affected by SkCanvas' transform.
 *      *
 *      *  This variant assumes that the runtime shader samples 'childShaderName' with the same input
 *      *  coordinate passed to to shader.
 *      *
 *      *  This requires a GPU backend or SkSL to be compiled in.
 *      *
 *      *  @param builder         The builder used to produce the runtime shader, that will in turn
 *      *                         fill the result image
 *      *  @param childShaderName The name of the child shader defined in the builder that will be
 *      *                         bound to the input param (or the source image if the input param
 *      *                         is null).  If empty, the builder can have exactly one child shader,
 *      *                         which automatically binds the input param.
 *      *  @param input           The image filter that will be provided as input to the runtime
 *      *                         shader. If null the implicit source image is used instead
 *      */
 *     static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
 *                                               std::string_view childShaderName,
 *                                               sk_sp<SkImageFilter> input) {
 *         return RuntimeShader(builder, /*sampleRadius=*/0.f, childShaderName, std::move(input));
 *     }
 *
 *     /**
 *      * As above, but 'sampleRadius' defines the sampling radius of 'childShaderName' relative to
 *      * the runtime shader produced by 'builder'. If greater than 0, the coordinate passed to
 *      * childShader.eval() will be up to 'sampleRadius' away (maximum absolute offset in 'x' or 'y')
 *      * from the coordinate passed into the runtime shader.
 *      *
 *      * This allows Skia to provide sampleable values for the image filter without worrying about
 *      * boundary conditions.
 *      *
 *      * This requires a GPU backend or SkSL to be compiled in.
 *     */
 *     static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
 *                                               SkScalar sampleRadius,
 *                                               std::string_view childShaderName,
 *                                               sk_sp<SkImageFilter> input);
 *
 *     /**
 *      *  Create a filter that fills the output with the per-pixel evaluation of the SkShader produced
 *      *  by the SkRuntimeEffectBuilder. The shader is defined in the image filter's local coordinate
 *      *  system, so it will automatically be affected by SkCanvas' transform.
 *      *
 *      *  This requires a GPU backend or SkSL to be compiled in.
 *      *
 *      *  @param builder          The builder used to produce the runtime shader, that will in turn
 *      *                          fill the result image
 *      *  @param childShaderNames The names of the child shaders defined in the builder that will be
 *      *                          bound to the input params (or the source image if the input param
 *      *                          is null). If any name is null, or appears more than once, factory
 *      *                          fails and returns nullptr.
 *      *  @param inputs           The image filters that will be provided as input to the runtime
 *      *                          shader. If any are null, the implicit source image is used instead.
 *      *  @param inputCount       How many entries are present in 'childShaderNames' and 'inputs'.
 *      */
 *     static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
 *                                               std::string_view childShaderNames[],
 *                                               const sk_sp<SkImageFilter> inputs[],
 *                                               int inputCount) {
 *         return RuntimeShader(builder, /*maxSampleRadius=*/0.f, childShaderNames,
 *                              inputs, inputCount);
 *     }
 *
 *     /**
 *      * As above, but 'maxSampleRadius' defines the sampling limit on coordinates provided to all
 *      * child shaders. Like the single-child variant with a sample radius, this can be used to
 *      * inform Skia that the runtime shader guarantees that all dynamic children (defined in
 *      * childShaderNames) will be evaluated with coordinates at most 'maxSampleRadius' away from the
 *      * coordinate provided to the runtime shader itself.
 *      *
 *      *  This requires a GPU backend or SkSL to be compiled in.
 *      */
 *     static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
 *                                               SkScalar maxSampleRadius,
 *                                               std::string_view childShaderNames[],
 *                                               const sk_sp<SkImageFilter> inputs[],
 *                                               int inputCount);
 *
 *     enum class Dither : bool {
 *         kNo = false,
 *         kYes = true
 *     };
 *
 *     /**
 *      *  Create a filter that fills the output with the per-pixel evaluation of the SkShader. The
 *      *  shader is defined in the image filter's local coordinate system, so will automatically
 *      *  be affected by SkCanvas' transform.
 *      *
 *      *  Like Image() and Picture(), this is a leaf filter that can be used to introduce inputs to
 *      *  a complex filter graph, but should generally be combined with a filter that as at least
 *      *  one null input to use the implicit source image.
 *      *
 *      *  Returns an image filter that evaluates to transparent black if 'shader' is null.
 *      *
 *      *  @param shader The shader that fills the result image
 *      */
 *     static sk_sp<SkImageFilter> Shader(sk_sp<SkShader> shader, const CropRect& cropRect = {}) {
 *         return Shader(std::move(shader), Dither::kNo, cropRect);
 *     }
 *     static sk_sp<SkImageFilter> Shader(sk_sp<SkShader> shader, Dither dither,
 *                                        const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a tile image filter.
 *      *  @param src   Defines the pixels to tile
 *      *  @param dst   Defines the pixel region that the tiles will be drawn to
 *      *  @param input The input that will be tiled, if null the source bitmap is used instead.
 *      */
 *     static sk_sp<SkImageFilter> Tile(const SkRect& src, const SkRect& dst,
 *                                      sk_sp<SkImageFilter> input);
 *
 *     // Morphology filter effects
 *
 *     /**
 *      *  Create a filter that dilates each input pixel's channel values to the max value within the
 *      *  given radii along the x and y axes.
 *      *  @param radiusX  The distance to dilate along the x axis to either side of each pixel.
 *      *  @param radiusY  The distance to dilate along the y axis to either side of each pixel.
 *      *  @param input    The image filter that is dilated, using source bitmap if this is null.
 *      *  @param cropRect Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> Dilate(SkScalar radiusX, SkScalar radiusY,
 *                                        sk_sp<SkImageFilter> input,
 *                                        const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that erodes each input pixel's channel values to the minimum channel value
 *      *  within the given radii along the x and y axes.
 *      *  @param radiusX  The distance to erode along the x axis to either side of each pixel.
 *      *  @param radiusY  The distance to erode along the y axis to either side of each pixel.
 *      *  @param input    The image filter that is eroded, using source bitmap if this is null.
 *      *  @param cropRect Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> Erode(SkScalar radiusX, SkScalar radiusY,
 *                                       sk_sp<SkImageFilter> input,
 *                                       const CropRect& cropRect = {});
 *
 *     // Lighting filter effects
 *
 *     /**
 *      *  Create a filter that calculates the diffuse illumination from a distant light source,
 *      *  interpreting the alpha channel of the input as the height profile of the surface (to
 *      *  approximate normal vectors).
 *      *  @param direction    The direction to the distance light.
 *      *  @param lightColor   The color of the diffuse light source.
 *      *  @param surfaceScale Scale factor to transform from alpha values to physical height.
 *      *  @param kd           Diffuse reflectance coefficient.
 *      *  @param input        The input filter that defines surface normals (as alpha), or uses the
 *      *                      source bitmap when null.
 *      *  @param cropRect     Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> DistantLitDiffuse(const SkPoint3& direction, SkColor lightColor,
 *                                                   SkScalar surfaceScale, SkScalar kd,
 *                                                   sk_sp<SkImageFilter> input,
 *                                                   const CropRect& cropRect = {});
 *     /**
 *      *  Create a filter that calculates the diffuse illumination from a point light source, using
 *      *  alpha channel of the input as the height profile of the surface (to approximate normal
 *      *  vectors).
 *      *  @param location     The location of the point light.
 *      *  @param lightColor   The color of the diffuse light source.
 *      *  @param surfaceScale Scale factor to transform from alpha values to physical height.
 *      *  @param kd           Diffuse reflectance coefficient.
 *      *  @param input        The input filter that defines surface normals (as alpha), or uses the
 *      *                      source bitmap when null.
 *      *  @param cropRect     Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> PointLitDiffuse(const SkPoint3& location, SkColor lightColor,
 *                                                 SkScalar surfaceScale, SkScalar kd,
 *                                                 sk_sp<SkImageFilter> input,
 *                                                 const CropRect& cropRect = {});
 *     /**
 *      *  Create a filter that calculates the diffuse illumination from a spot light source, using
 *      *  alpha channel of the input as the height profile of the surface (to approximate normal
 *      *  vectors). The spot light is restricted to be within 'cutoffAngle' of the vector between
 *      *  the location and target.
 *      *  @param location        The location of the spot light.
 *      *  @param target          The location that the spot light is point towards
 *      *  @param falloffExponent Exponential falloff parameter for illumination outside of cutoffAngle
 *      *  @param cutoffAngle     Maximum angle from lighting direction that receives full light
 *      *  @param lightColor      The color of the diffuse light source.
 *      *  @param surfaceScale    Scale factor to transform from alpha values to physical height.
 *      *  @param kd              Diffuse reflectance coefficient.
 *      *  @param input           The input filter that defines surface normals (as alpha), or uses the
 *      *                         source bitmap when null.
 *      *  @param cropRect        Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> SpotLitDiffuse(const SkPoint3& location, const SkPoint3& target,
 *                                                SkScalar falloffExponent, SkScalar cutoffAngle,
 *                                                SkColor lightColor, SkScalar surfaceScale,
 *                                                SkScalar kd, sk_sp<SkImageFilter> input,
 *                                                const CropRect& cropRect = {});
 *
 *     /**
 *      *  Create a filter that calculates the specular illumination from a distant light source,
 *      *  interpreting the alpha channel of the input as the height profile of the surface (to
 *      *  approximate normal vectors).
 *      *  @param direction    The direction to the distance light.
 *      *  @param lightColor   The color of the specular light source.
 *      *  @param surfaceScale Scale factor to transform from alpha values to physical height.
 *      *  @param ks           Specular reflectance coefficient.
 *      *  @param shininess    The specular exponent determining how shiny the surface is.
 *      *  @param input        The input filter that defines surface normals (as alpha), or uses the
 *      *                      source bitmap when null.
 *      *  @param cropRect     Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> DistantLitSpecular(const SkPoint3& direction, SkColor lightColor,
 *                                                    SkScalar surfaceScale, SkScalar ks,
 *                                                    SkScalar shininess, sk_sp<SkImageFilter> input,
 *                                                    const CropRect& cropRect = {});
 *     /**
 *      *  Create a filter that calculates the specular illumination from a point light source, using
 *      *  alpha channel of the input as the height profile of the surface (to approximate normal
 *      *  vectors).
 *      *  @param location     The location of the point light.
 *      *  @param lightColor   The color of the specular light source.
 *      *  @param surfaceScale Scale factor to transform from alpha values to physical height.
 *      *  @param ks           Specular reflectance coefficient.
 *      *  @param shininess    The specular exponent determining how shiny the surface is.
 *      *  @param input        The input filter that defines surface normals (as alpha), or uses the
 *      *                      source bitmap when null.
 *      *  @param cropRect     Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> PointLitSpecular(const SkPoint3& location, SkColor lightColor,
 *                                                  SkScalar surfaceScale, SkScalar ks,
 *                                                  SkScalar shininess, sk_sp<SkImageFilter> input,
 *                                                  const CropRect& cropRect = {});
 *     /**
 *      *  Create a filter that calculates the specular illumination from a spot light source, using
 *      *  alpha channel of the input as the height profile of the surface (to approximate normal
 *      *  vectors). The spot light is restricted to be within 'cutoffAngle' of the vector between
 *      *  the location and target.
 *      *  @param location        The location of the spot light.
 *      *  @param target          The location that the spot light is point towards
 *      *  @param falloffExponent Exponential falloff parameter for illumination outside of cutoffAngle
 *      *  @param cutoffAngle     Maximum angle from lighting direction that receives full light
 *      *  @param lightColor      The color of the specular light source.
 *      *  @param surfaceScale    Scale factor to transform from alpha values to physical height.
 *      *  @param ks              Specular reflectance coefficient.
 *      *  @param shininess       The specular exponent determining how shiny the surface is.
 *      *  @param input           The input filter that defines surface normals (as alpha), or uses the
 *      *                         source bitmap when null.
 *      *  @param cropRect        Optional rectangle that crops the input and output.
 *      */
 *     static sk_sp<SkImageFilter> SpotLitSpecular(const SkPoint3& location, const SkPoint3& target,
 *                                                 SkScalar falloffExponent, SkScalar cutoffAngle,
 *                                                 SkColor lightColor, SkScalar surfaceScale,
 *                                                 SkScalar ks, SkScalar shininess,
 *                                                 sk_sp<SkImageFilter> input,
 *                                                 const CropRect& cropRect = {});
 *
 * private:
 *     SkImageFilters() = delete;
 * }
 * ```
 */
public open class SkImageFilters public constructor() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkImageFilters::Blend(SkBlendMode mode,
   *                                            sk_sp<SkImageFilter> background,
   *                                            sk_sp<SkImageFilter> foreground,
   *                                            const CropRect& cropRect) {
   *     return make_blend(SkBlender::Mode(mode),
   *                       std::move(background),
   *                       std::move(foreground),
   *                       cropRect);
   * }
   * ```
   */
  public fun blend(
    mode: SkBlendMode,
    background: SkSp<SkImageFilter>,
    foreground: SkSp<SkImageFilter>,
    cropRect: CropRect,
  ): SkSp<SkImageFilter> {
    TODO("Implement blend")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkImageFilters::Blend(sk_sp<SkBlender> blender,
   *                                            sk_sp<SkImageFilter> background,
   *                                            sk_sp<SkImageFilter> foreground,
   *                                            const CropRect& cropRect) {
   *     return make_blend(std::move(blender), std::move(background), std::move(foreground), cropRect);
   * }
   * ```
   */
  public fun blend(
    blender: SkSp<SkBlender>,
    background: SkSp<SkImageFilter>,
    foreground: SkSp<SkImageFilter>,
    cropRect: CropRect,
  ): SkSp<SkImageFilter> {
    TODO("Implement blend")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkImageFilters::Merge(sk_sp<SkImageFilter>* const filters, int count,
   *                                            const CropRect& cropRect) {
   *     if (count <= 0 || !filters) {
   *         return SkImageFilters::Empty();
   *     }
   *
   *     sk_sp<SkImageFilter> filter{new SkMergeImageFilter(filters, count)};
   *     if (cropRect) {
   *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
   *     }
   *     return filter;
   * }
   * ```
   */
  public fun merge(
    filters: SkSp<SkImageFilter>?,
    count: Int,
    cropRect: CropRect,
  ): SkSp<SkImageFilter> {
    TODO("Implement merge")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkImageFilters::RuntimeShader(const SkRuntimeShaderBuilder& builder,
   *                                                    SkScalar sampleRadius,
   *                                                    std::string_view childShaderName,
   *                                                    sk_sp<SkImageFilter> input) {
   *     // If no childShaderName is provided, check to see if we can implicitly assign it to the only
   *     // child in the effect.
   *     if (childShaderName.empty()) {
   *         auto children = builder.effect()->children();
   *         if (children.size() != 1) {
   *             return nullptr;
   *         }
   *         childShaderName = children.front().name;
   *     }
   *
   *     return SkImageFilters::RuntimeShader(builder, sampleRadius, &childShaderName, &input, 1);
   * }
   * ```
   */
  public fun runtimeShader(
    builder: SkRuntimeShaderBuilder,
    sampleRadius: SkScalar,
    childShaderName: String,
    input: SkSp<SkImageFilter>,
  ): SkSp<SkImageFilter> {
    TODO("Implement runtimeShader")
  }

  public open class CropRect public constructor() : Any?(), SkRect {
    public constructor(crop: SkIRect) : this(TODO()) {
      TODO("Implement constructor")
    }

    public constructor(crop: SkRect) : this(TODO()) {
      TODO("Implement constructor")
    }

    public constructor(crop: SkRect?) : this(TODO()) {
      TODO("Implement constructor")
    }

    public constructor(param0: nullopt_t) : this() {
      TODO("Implement constructor")
    }

    public constructor(param0: Any?) : this() {
      TODO("Implement constructor")
    }

    public constructor(optionalCrop: SkIRect?) : this() {
      TODO("Implement constructor")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public enum class Dither {
    kNo,
    kYes,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Arithmetic(SkScalar k1,
     *                                                 SkScalar k2,
     *                                                 SkScalar k3,
     *                                                 SkScalar k4,
     *                                                 bool enforcePMColor,
     *                                                 sk_sp<SkImageFilter> background,
     *                                                 sk_sp<SkImageFilter> foreground,
     *                                                 const CropRect& cropRect) {
     *     auto blender = SkBlenders::Arithmetic(k1, k2, k3, k4, enforcePMColor);
     *     if (!blender) {
     *         // Arithmetic() returns null on an error, not to optimize src-over
     *         return nullptr;
     *     }
     *     return make_blend(std::move(blender),
     *                       std::move(background),
     *                       std::move(foreground),
     *                       cropRect,
     *                       // Carry arithmetic coefficients and premul behavior into image filter for
     *                       // serialization and bounds analysis
     *                       SkV4{k1, k2, k3, k4},
     *                       enforcePMColor);
     * }
     * ```
     */
    public fun arithmetic(
      k1: Int,
      k2: Int,
      k3: Int,
      k4: Int,
      enforcePMColor: Boolean,
      background: Int,
      foreground: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement arithmetic")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Blend(SkBlendMode mode, sk_sp<SkImageFilter> background,
     *                                       sk_sp<SkImageFilter> foreground = nullptr,
     *                                       const CropRect& cropRect = {})
     * ```
     */
    public fun blend(
      param0: SkBlendMode,
      param1: Int,
      param2: Int,
      param3: CropRect,
    ): Int {
      TODO("Implement blend")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Blend(sk_sp<SkBlender> blender, sk_sp<SkImageFilter> background,
     *                                       sk_sp<SkImageFilter> foreground = nullptr,
     *                                       const CropRect& cropRect = {})
     * ```
     */
    public fun blend(
      param0: Int,
      param1: Int,
      param2: Int,
      param3: CropRect,
    ): Int {
      TODO("Implement blend")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Blur(
     *         SkScalar sigmaX, SkScalar sigmaY, SkTileMode tileMode, sk_sp<SkImageFilter> input,
     *         const CropRect& cropRect) {
     *     if (!SkIsFinite(sigmaX, sigmaY) || sigmaX < 0.f || sigmaY < 0.f) {
     *         // Non-finite or negative sigmas are error conditions. We allow 0 sigma for X and/or Y
     *         // for 1D blurs; onFilterImage() will detect when no visible blurring would occur based on
     *         // the Context mapping.
     *         return nullptr;
     *     }
     *
     *     // Temporarily allow tiling with no crop rect
     *     if (tileMode != SkTileMode::kDecal && !cropRect) {
     *         return sk_make_sp<SkBlurImageFilter>(SkSize{sigmaX, sigmaY}, tileMode, std::move(input));
     *     }
     *
     *     // The 'tileMode' behavior is not well-defined if there is no crop. We only apply it if
     *     // there is a provided 'cropRect'.
     *     sk_sp<SkImageFilter> filter = std::move(input);
     *     if (tileMode != SkTileMode::kDecal && cropRect) {
     *         // Historically the input image was restricted to the cropRect when tiling was not
     *         // kDecal, so that the kernel evaluated the tiled edge conditions, while a kDecal crop
     *         // only affected the output.
     *         filter = SkImageFilters::Crop(*cropRect, tileMode, std::move(filter));
     *     }
     *
     *     filter = sk_make_sp<SkBlurImageFilter>(SkSize{sigmaX, sigmaY}, std::move(filter));
     *     if (cropRect) {
     *         // But regardless of the tileMode, the output is always decal cropped
     *         filter = SkImageFilters::Crop(*cropRect, SkTileMode::kDecal, std::move(filter));
     *     }
     *     return filter;
     * }
     * ```
     */
    public fun blur(
      sigmaX: Int,
      sigmaY: Int,
      tileMode: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement blur")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Blur(SkScalar sigmaX, SkScalar sigmaY, sk_sp<SkImageFilter> input,
     *                                      const CropRect& cropRect = {}) {
     *         return Blur(sigmaX, sigmaY, SkTileMode::kDecal, std::move(input), cropRect);
     *     }
     * ```
     */
    public fun blur(
      param0: Int,
      param1: Int,
      param2: Int,
      param3: CropRect,
    ): Int {
      TODO("Implement blur")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::ColorFilter(sk_sp<SkColorFilter> cf,
     *                                                  sk_sp<SkImageFilter> input,
     *                                                  const CropRect& cropRect) {
     *     if (cf) {
     *         SkColorFilter* inputCF;
     *         // This is an optimization, as it collapses the hierarchy by just combining the two
     *         // colorfilters into a single one, which the new imagefilter will wrap.
     *         // NOTE: FilterResults are capable of composing non-adjacent CF nodes together. We could
     *         // remove this optimization at construction time, but may as well do the work just once.
     *         if (input && input->isColorFilterNode(&inputCF)) {
     *             cf = cf->makeComposed(sk_sp<SkColorFilter>(inputCF));
     *             input = sk_ref_sp(input->getInput(0));
     *         }
     *     }
     *
     *     sk_sp<SkImageFilter> filter = std::move(input);
     *     if (cf) {
     *         filter = sk_sp<SkImageFilter>(
     *                 new SkColorFilterImageFilter(std::move(cf), std::move(filter)));
     *     }
     *     if (cropRect) {
     *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
     *     }
     *     return filter;
     * }
     * ```
     */
    public fun colorFilter(
      cf: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement colorFilter")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Compose(sk_sp<SkImageFilter> outer,
     *                                              sk_sp<SkImageFilter> inner) {
     *     if (!outer) {
     *         return inner;
     *     }
     *     if (!inner) {
     *         return outer;
     *     }
     *     sk_sp<SkImageFilter> inputs[2] = { std::move(outer), std::move(inner) };
     *     return sk_sp<SkImageFilter>(new SkComposeImageFilter(inputs));
     * }
     * ```
     */
    public fun compose(outer: SkSp<SkImageFilter>, `inner`: SkSp<SkImageFilter>): Int {
      TODO("Implement compose")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Crop(const SkRect& rect,
     *                                           SkTileMode tileMode,
     *                                           sk_sp<SkImageFilter> input) {
     *     if (!SkIsValidRect(rect)) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkImageFilter>(new SkCropImageFilter(rect, tileMode, std::move(input)));
     * }
     * ```
     */
    public fun crop(
      rect: SkRect,
      tileMode: SkTileMode,
      input: SkSp<SkImageFilter>,
    ): Int {
      TODO("Implement crop")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Crop(const SkRect& rect, sk_sp<SkImageFilter> input) {
     *         return Crop(rect, SkTileMode::kDecal, std::move(input));
     *     }
     * ```
     */
    public fun crop(rect: SkRect, input: SkSp<SkImageFilter>): Int {
      TODO("Implement crop")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::DisplacementMap(
     *         SkColorChannel xChannelSelector, SkColorChannel yChannelSelector, SkScalar scale,
     *         sk_sp<SkImageFilter> displacement, sk_sp<SkImageFilter> color, const CropRect& cropRect) {
     *     if (!channel_selector_type_is_valid(xChannelSelector) ||
     *         !channel_selector_type_is_valid(yChannelSelector)) {
     *         return nullptr;
     *     }
     *     if (!SkIsFinite(scale)) {
     *         return nullptr;
     *     }
     *
     *     sk_sp<SkImageFilter> inputs[2] = { std::move(displacement), std::move(color) };
     *     sk_sp<SkImageFilter> filter(new SkDisplacementMapImageFilter(xChannelSelector, yChannelSelector,
     *                                                                  scale, inputs));
     *     if (cropRect) {
     *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
     *     }
     *     return filter;
     * }
     * ```
     */
    public fun displacementMap(
      xChannelSelector: Int,
      yChannelSelector: Int,
      scale: Int,
      displacement: Int,
      color: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement displacementMap")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::DropShadow(
     *         SkScalar dx, SkScalar dy, SkScalar sigmaX, SkScalar sigmaY, SkColor4f color,
     *         sk_sp<SkColorSpace> colorSpace,
     *         sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_drop_shadow_graph({dx, dy}, {sigmaX, sigmaY}, color, std::move(colorSpace),
     *                                   /*shadowOnly=*/false, std::move(input), cropRect);
     * }
     * ```
     */
    public fun dropShadow(
      dx: Int,
      dy: Int,
      sigmaX: Int,
      sigmaY: Int,
      color: Int,
      colorSpace: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement dropShadow")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> DropShadow(SkScalar dx, SkScalar dy,
     *                                            SkScalar sigmaX, SkScalar sigmaY,
     *                                            SkColor color, sk_sp<SkImageFilter> input,
     *                                            const CropRect& cropRect = {}) {
     *         return DropShadow(dx, dy,
     *                           sigmaX, sigmaY,
     *                           SkColor4f::FromColor(color), /*colorSpace=*/nullptr,
     *                           std::move(input),
     *                           cropRect);
     *     }
     * ```
     */
    public fun dropShadow(
      param0: Int,
      param1: Int,
      param2: Int,
      param3: Int,
      param4: Int,
      param5: Int,
      param6: CropRect,
    ): Int {
      TODO("Implement dropShadow")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::DropShadowOnly(
     *         SkScalar dx, SkScalar dy, SkScalar sigmaX, SkScalar sigmaY, SkColor4f color,
     *         sk_sp<SkColorSpace> colorSpace, sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_drop_shadow_graph({dx, dy}, {sigmaX, sigmaY}, color, std::move(colorSpace),
     *                                   /*shadowOnly=*/true, std::move(input), cropRect);
     * }
     * ```
     */
    public fun dropShadowOnly(
      dx: Int,
      dy: Int,
      sigmaX: Int,
      sigmaY: Int,
      color: Int,
      colorSpace: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement dropShadowOnly")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> DropShadowOnly(SkScalar dx, SkScalar dy,
     *                                                SkScalar sigmaX, SkScalar sigmaY,
     *                                                SkColor color, sk_sp<SkImageFilter> input,
     *                                                const CropRect& cropRect = {}) {
     *         return DropShadowOnly(dx, dy,
     *                               sigmaX, sigmaY,
     *                               SkColor4f::FromColor(color), /*colorSpace=*/nullptr,
     *                               std::move(input),
     *                               cropRect);
     *     }
     * ```
     */
    public fun dropShadowOnly(
      param0: Int,
      param1: Int,
      param2: Int,
      param3: Int,
      param4: Int,
      param5: Int,
      param6: CropRect,
    ): Int {
      TODO("Implement dropShadowOnly")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Empty() {
     *     return SkImageFilters::Crop(SkRect::MakeEmpty(), SkTileMode::kDecal, nullptr);
     * }
     * ```
     */
    public fun empty(): Int {
      TODO("Implement empty")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Image(sk_sp<SkImage> image,
     *                                            const SkRect& srcRect,
     *                                            const SkRect& dstRect,
     *                                            const SkSamplingOptions& sampling) {
     *     if (srcRect.isEmpty() || dstRect.isEmpty() || !image) {
     *         // There is no content to draw, so the filter should produce transparent black
     *         return SkImageFilters::Empty();
     *     } else {
     *         SkRect imageBounds = SkRect::Make(image->dimensions());
     *         if (imageBounds.contains(srcRect)) {
     *             // No change to srcRect and dstRect needed
     *             return sk_sp<SkImageFilter>(new SkImageImageFilter(
     *                     std::move(image), srcRect, dstRect, sampling));
     *         } else {
     *             SkMatrix srcToDst = SkMatrix::RectToRectOrIdentity(srcRect, dstRect);
     *             if (!imageBounds.intersect(srcRect)) {
     *                 // No overlap, so draw empty
     *                 return SkImageFilters::Empty();
     *             }
     *
     *             // Adjust dstRect to match the updated src (which is stored in imageBounds)
     *             SkRect mappedBounds = srcToDst.mapRect(imageBounds);
     *             if (mappedBounds.isEmpty()) {
     *                 return SkImageFilters::Empty();
     *             }
     *             return sk_sp<SkImageFilter>(new SkImageImageFilter(
     *                     std::move(image), imageBounds, mappedBounds, sampling));
     *         }
     *     }
     * }
     * ```
     */
    public fun image(
      image: SkSp<SkImage>,
      srcRect: SkRect,
      dstRect: SkRect,
      sampling: SkSamplingOptions,
    ): Int {
      TODO("Implement image")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Image(sk_sp<SkImage> image, const SkSamplingOptions& sampling) {
     *         if (image) {
     *             SkRect r = SkRect::Make(image->bounds());
     *             return Image(std::move(image), r, r, sampling);
     *         } else {
     *             return nullptr;
     *         }
     *     }
     * ```
     */
    public fun image(image: SkSp<SkImage>, sampling: SkSamplingOptions): Int {
      TODO("Implement image")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Magnifier(const SkRect& lensBounds,
     *                                                SkScalar zoomAmount,
     *                                                SkScalar inset,
     *                                                const SkSamplingOptions& sampling,
     *                                                sk_sp<SkImageFilter> input,
     *                                                const CropRect& cropRect) {
     *     if (lensBounds.isEmpty() || !lensBounds.isFinite() ||
     *         zoomAmount <= 0.f || inset < 0.f ||
     *         !SkIsFinite(zoomAmount, inset)) {
     *         return nullptr; // invalid
     *     }
     *     // The magnifier automatically restricts its output based on the size of the image it receives
     *     // as input, so 'cropRect' only applies to its input.
     *     if (cropRect) {
     *         input = SkImageFilters::Crop(*cropRect, std::move(input));
     *     }
     *
     *     if (zoomAmount > 1.f) {
     *         return sk_sp<SkImageFilter>(new SkMagnifierImageFilter(lensBounds, zoomAmount, inset,
     *                                                                sampling, std::move(input)));
     *     } else {
     *         // Zooming with a value less than 1 is technically a downscaling, which "works" but the
     *         // non-linear distortion behaves unintuitively. At zoomAmount = 1, this filter is an
     *         // expensive identity function so treat zoomAmount <= 1 as a no-op.
     *         return input;
     *     }
     * }
     * ```
     */
    public fun magnifier(
      lensBounds: Int,
      zoomAmount: Int,
      inset: Int,
      sampling: SkSamplingOptions,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement magnifier")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::MatrixConvolution(const SkISize& kernelSize,
     *                                                        const SkScalar kernel[],
     *                                                        SkScalar gain,
     *                                                        SkScalar bias,
     *                                                        const SkIPoint& kernelOffset,
     *                                                        SkTileMode tileMode,
     *                                                        bool convolveAlpha,
     *                                                        sk_sp<SkImageFilter> input,
     *                                                        const CropRect& cropRect) {
     *     if (kernelSize.width() < 1 || kernelSize.height() < 1) {
     *         return nullptr;
     *     }
     *     if (SkSafeMath::Mul(kernelSize.width(), kernelSize.height()) > kLargeKernelSize) {
     *         return nullptr;
     *     }
     *     if (!kernel) {
     *         return nullptr;
     *     }
     *     if ((kernelOffset.fX < 0) || (kernelOffset.fX >= kernelSize.fWidth) ||
     *         (kernelOffset.fY < 0) || (kernelOffset.fY >= kernelSize.fHeight)) {
     *         return nullptr;
     *     }
     *
     *     // The 'tileMode' behavior is not well-defined if there is no crop, so we only apply it if
     *     // there is a provided 'cropRect'.
     *     sk_sp<SkImageFilter> filter = std::move(input);
     *     if (cropRect && tileMode != SkTileMode::kDecal) {
     *         // Historically the input image was restricted to the cropRect when tiling was not kDecal
     *         // so that the kernel evaluated the tiled edge conditions, while a kDecal crop only affected
     *         // the output.
     *         filter = SkImageFilters::Crop(*cropRect, tileMode, std::move(filter));
     *     }
     *     filter = sk_sp<SkImageFilter>(new SkMatrixConvolutionImageFilter(
     *             kernelSize, kernel, gain, bias, kernelOffset, convolveAlpha, &filter));
     *     if (cropRect) {
     *         // But regardless of the tileMode, the output is decal cropped.
     *         filter = SkImageFilters::Crop(*cropRect, SkTileMode::kDecal, std::move(filter));
     *     }
     *     return filter;
     * }
     * ```
     */
    public fun matrixConvolution(
      kernelSize: SkISize,
      kernel: Int?,
      gain: Int,
      bias: Int,
      kernelOffset: SkIPoint,
      tileMode: Int,
      convolveAlpha: Boolean,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement matrixConvolution")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::MatrixTransform(const SkMatrix& transform,
     *                                                      const SkSamplingOptions& sampling,
     *                                                      sk_sp<SkImageFilter> input) {
     *     if (!transform.invert(/*inverse=*/nullptr)) {
     *         return nullptr;
     *     }
     *     return sk_sp<SkImageFilter>(new SkMatrixTransformImageFilter(transform,
     *                                                                  sampling,
     *                                                                  std::move(input)));
     * }
     * ```
     */
    public fun matrixTransform(
      matrix: SkMatrix,
      sampling: SkSamplingOptions,
      input: SkSp<SkImageFilter>,
    ): Int {
      TODO("Implement matrixTransform")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Merge(sk_sp<SkImageFilter>* const filters, int count,
     *                                       const CropRect& cropRect = {})
     * ```
     */
    public fun merge(
      param0: Int?,
      param1: Int,
      param2: CropRect,
    ): Int {
      TODO("Implement merge")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Merge(sk_sp<SkImageFilter> first, sk_sp<SkImageFilter> second,
     *                                       const CropRect& cropRect = {}) {
     *         sk_sp<SkImageFilter> array[] = { std::move(first), std::move(second) };
     *         return Merge(array, 2, cropRect);
     *     }
     * ```
     */
    public fun merge(
      param0: Int,
      param1: Int,
      param2: CropRect,
    ): Int {
      TODO("Implement merge")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Offset(SkScalar dx, SkScalar dy,
     *                                             sk_sp<SkImageFilter> input,
     *                                             const CropRect& cropRect) {
     *     // The legacy ::Offset() implementation rounded its offset vector to layer-space pixels, which
     *     // is roughly equivalent to using nearest-neighbor sampling with the translation matrix.
     *     sk_sp<SkImageFilter> offset = SkImageFilters::MatrixTransform(
     *             SkMatrix::Translate(dx, dy),
     *             SkFilterMode::kNearest,
     *             std::move(input));
     *     // The legacy 'cropRect' applies only to the output of the offset filter.
     *     if (cropRect) {
     *         offset = SkImageFilters::Crop(*cropRect, std::move(offset));
     *     }
     *     return offset;
     * }
     * ```
     */
    public fun offset(
      dx: Int,
      dy: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement offset")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Picture(sk_sp<SkPicture> pic, const SkRect& targetRect) {
     *     if (pic) {
     *         SkRect cullRect = pic->cullRect();
     *         if (cullRect.intersect(targetRect)) {
     *             return sk_sp<SkImageFilter>(new SkPictureImageFilter(std::move(pic), cullRect));
     *         }
     *     }
     *     return SkImageFilters::Empty();
     * }
     * ```
     */
    public fun picture(pic: SkSp<SkPicture>, targetRect: SkRect): Int {
      TODO("Implement picture")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Picture(sk_sp<SkPicture> pic) {
     *         SkRect target = pic ? pic->cullRect() : SkRect::MakeEmpty();
     *         return Picture(std::move(pic), target);
     *     }
     * ```
     */
    public fun picture(pic: SkSp<SkPicture>): Int {
      TODO("Implement picture")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
     *                                               std::string_view childShaderName,
     *                                               sk_sp<SkImageFilter> input) {
     *         return RuntimeShader(builder, /*sampleRadius=*/0.f, childShaderName, std::move(input));
     *     }
     * ```
     */
    public fun runtimeShader(
      builder: SkRuntimeEffectBuilder,
      childShaderName: String,
      input: SkSp<SkImageFilter>,
    ): Int {
      TODO("Implement runtimeShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
     *                                               SkScalar sampleRadius,
     *                                               std::string_view childShaderName,
     *                                               sk_sp<SkImageFilter> input)
     * ```
     */
    public fun runtimeShader(
      builder: SkRuntimeEffectBuilder,
      sampleRadius: SkScalar,
      childShaderName: String,
      input: SkSp<SkImageFilter>,
    ): Int {
      TODO("Implement runtimeShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> RuntimeShader(const SkRuntimeEffectBuilder& builder,
     *                                               std::string_view childShaderNames[],
     *                                               const sk_sp<SkImageFilter> inputs[],
     *                                               int inputCount) {
     *         return RuntimeShader(builder, /*maxSampleRadius=*/0.f, childShaderNames,
     *                              inputs, inputCount);
     *     }
     * ```
     */
    public fun runtimeShader(
      builder: SkRuntimeEffectBuilder,
      childShaderNames: Array<String>,
      inputs: Array<SkSp<SkImageFilter>>,
      inputCount: Int,
    ): Int {
      TODO("Implement runtimeShader")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::RuntimeShader(const SkRuntimeShaderBuilder& builder,
     *                                                    SkScalar maxSampleRadius,
     *                                                    std::string_view childShaderNames[],
     *                                                    const sk_sp<SkImageFilter> inputs[],
     *                                                    int inputCount) {
     *     if (maxSampleRadius < 0.f) {
     *         return nullptr; // invalid sample radius
     *     }
     *
     *     auto child_is_shader = [](const SkRuntimeEffect::Child* child) {
     *         return child && child->type == SkRuntimeEffect::ChildType::kShader;
     *     };
     *
     *     for (int i = 0; i < inputCount; i++) {
     *         std::string_view name = childShaderNames[i];
     *         // All names must be non-empty, and present as a child shader in the effect:
     *         if (name.empty() || !child_is_shader(builder.effect()->findChild(name))) {
     *             return nullptr;
     *         }
     *
     *         // We don't allow duplicates, either:
     *         for (int j = 0; j < i; j++) {
     *             if (name == childShaderNames[j]) {
     *                 return nullptr;
     *             }
     *         }
     *     }
     *
     *     return sk_sp<SkImageFilter>(new SkRuntimeImageFilter(builder, maxSampleRadius, childShaderNames,
     *                                                          inputs, inputCount));
     * }
     * ```
     */
    public fun runtimeShader(
      builder: SkRuntimeEffectBuilder,
      maxSampleRadius: SkScalar,
      childShaderNames: Array<String>,
      inputs: Array<SkSp<SkImageFilter>>,
      inputCount: Int,
    ): Int {
      TODO("Implement runtimeShader")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Shader(sk_sp<SkShader> shader, const CropRect& cropRect = {}) {
     *         return Shader(std::move(shader), Dither::kNo, cropRect);
     *     }
     * ```
     */
    public fun shader(param0: Int, param1: CropRect): Int {
      TODO("Implement shader")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Shader(sk_sp<SkShader> shader,
     *                                             Dither dither,
     *                                             const CropRect& cropRect) {
     *     if (!shader) {
     *         return SkImageFilters::Empty();
     *     }
     *
     *     sk_sp<SkImageFilter> filter{new SkShaderImageFilter(std::move(shader), dither)};
     *     if (cropRect) {
     *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
     *     }
     *     return filter;
     * }
     * ```
     */
    public fun shader(
      shader: Int,
      dither: Dither,
      cropRect: CropRect,
    ): Int {
      TODO("Implement shader")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Tile(const SkRect& src,
     *                                           const SkRect& dst,
     *                                           sk_sp<SkImageFilter> input) {
     *     // The Tile filter is simply a crop to 'src' with a kRepeat tile mode wrapped in a crop to 'dst'
     *     // with a kDecal tile mode.
     *     sk_sp<SkImageFilter> filter = SkImageFilters::Crop(src, SkTileMode::kRepeat, std::move(input));
     *     filter = SkImageFilters::Crop(dst, SkTileMode::kDecal, std::move(filter));
     *     return filter;
     * }
     * ```
     */
    public fun tile(
      src: SkRect,
      dst: SkRect,
      input: SkSp<SkImageFilter>,
    ): Int {
      TODO("Implement tile")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Dilate(SkScalar radiusX, SkScalar radiusY,
     *                                             sk_sp<SkImageFilter> input,
     *                                             const CropRect& cropRect) {
     *     return make_morphology(MorphType::kDilate, {radiusX, radiusY}, std::move(input), cropRect);
     * }
     * ```
     */
    public fun dilate(
      radiusX: Int,
      radiusY: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement dilate")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::Erode(SkScalar radiusX, SkScalar radiusY,
     *                                            sk_sp<SkImageFilter> input,
     *                                            const CropRect& cropRect) {
     *     return make_morphology(MorphType::kErode, {radiusX, radiusY}, std::move(input), cropRect);
     * }
     * ```
     */
    public fun erode(
      radiusX: Int,
      radiusY: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement erode")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::DistantLitDiffuse(
     *         const SkPoint3& direction, SkColor lightColor, SkScalar surfaceScale, SkScalar kd,
     *         sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_lighting(Light::Distant(lightColor, direction),
     *                          Material::Diffuse(kd, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun distantLitDiffuse(
      direction: SkPoint3,
      lightColor: Int,
      surfaceScale: Int,
      kd: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement distantLitDiffuse")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::PointLitDiffuse(
     *         const SkPoint3& location, SkColor lightColor, SkScalar surfaceScale, SkScalar kd,
     *         sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_lighting(Light::Point(lightColor, location),
     *                          Material::Diffuse(kd, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun pointLitDiffuse(
      location: SkPoint3,
      lightColor: Int,
      surfaceScale: Int,
      kd: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement pointLitDiffuse")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::SpotLitDiffuse(
     *         const SkPoint3& location, const SkPoint3& target, SkScalar falloffExponent,
     *         SkScalar cutoffAngle, SkColor lightColor, SkScalar surfaceScale, SkScalar kd,
     *         sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     SkPoint3 dir = target - location;
     *     float cosCutoffAngle = SkScalarCos(SkDegreesToRadians(cutoffAngle));
     *     return make_lighting(Light::Spot(lightColor, location, dir, falloffExponent, cosCutoffAngle),
     *                          Material::Diffuse(kd, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun spotLitDiffuse(
      location: SkPoint3,
      target: SkPoint3,
      falloffExponent: Int,
      cutoffAngle: Int,
      lightColor: Int,
      surfaceScale: Int,
      kd: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement spotLitDiffuse")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::DistantLitSpecular(
     *         const SkPoint3& direction, SkColor lightColor, SkScalar surfaceScale, SkScalar ks,
     *         SkScalar shininess, sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_lighting(Light::Distant(lightColor, direction),
     *                          Material::Specular(ks, shininess, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun distantLitSpecular(
      direction: SkPoint3,
      lightColor: Int,
      surfaceScale: Int,
      ks: Int,
      shininess: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement distantLitSpecular")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::PointLitSpecular(
     *         const SkPoint3& location, SkColor lightColor, SkScalar surfaceScale, SkScalar ks,
     *         SkScalar shininess, sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     return make_lighting(Light::Point(lightColor, location),
     *                          Material::Specular(ks, shininess, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun pointLitSpecular(
      location: SkPoint3,
      lightColor: Int,
      surfaceScale: Int,
      ks: Int,
      shininess: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement pointLitSpecular")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkImageFilters::SpotLitSpecular(
     *         const SkPoint3& location, const SkPoint3& target, SkScalar falloffExponent,
     *         SkScalar cutoffAngle, SkColor lightColor, SkScalar surfaceScale, SkScalar ks,
     *         SkScalar shininess, sk_sp<SkImageFilter> input, const CropRect& cropRect) {
     *     SkPoint3 dir = target - location;
     *     float cosCutoffAngle = SkScalarCos(SkDegreesToRadians(cutoffAngle));
     *     return make_lighting(Light::Spot(lightColor, location, dir, falloffExponent, cosCutoffAngle),
     *                          Material::Specular(ks, shininess, surfaceScale),
     *                          std::move(input), cropRect);
     * }
     * ```
     */
    public fun spotLitSpecular(
      location: SkPoint3,
      target: SkPoint3,
      falloffExponent: Int,
      cutoffAngle: Int,
      lightColor: Int,
      surfaceScale: Int,
      ks: Int,
      shininess: Int,
      input: Int,
      cropRect: CropRect,
    ): Int {
      TODO("Implement spotLitSpecular")
    }
  }
}
