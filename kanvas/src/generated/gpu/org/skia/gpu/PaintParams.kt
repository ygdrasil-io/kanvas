package org.skia.gpu

import SkColor4f
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkImage
import org.skia.foundation.SkShader

/**
 * C++ original:
 * ```cpp
 * class PaintParams {
 * public:
 *     // Stores just the parameters of the implicit image shading model used by drawImageRect and
 *     // other image-drawing APIs, e.g. to apply SkModifyPaintAndDstForDrawImageRect without the
 *     // overhead of creating additional SkShader objects. Assumes clamp tiling; if no clamping is
 *     // required, set to the image's bounds.
 *     struct SimpleImage {
 *         // fImage (required) and fLocalMatrix (optional) must outlive the PaintParams object
 *         const SkImage* fImage;
 *         const SkMatrix* fLocalMatrix = nullptr;
 *         // Post local matrix strict clamping rectangle (e.g. relative to image's texels)
 *         SkRect fSubset;
 *         SkSamplingOptions fSamplingOptions;
 *     };
 *
 *     // Converts an SkPaint to PaintParams, possibly adding a primitive blender (e.g. for
 *     // drawVertices or text rendering).
 *     explicit PaintParams(const SkPaint& paint,
 *                          const SkBlender* primitiveBlender = nullptr,
 *                          bool skipColorXform = false,
 *                          bool ignoreShader = false);
 *
 *     // Converts an SkPaint to PaintParams and accounts for the implicit SkImage shader override
 *     // from drawImageRect and related functions. Multiplies `xtraAlpha` with the paint's alpha.
 *     //
 *     // NOTE: Does not copy `imageOverride`, this must live for the lifetime of PaintParams
 *     PaintParams(const SkPaint&, const SimpleImage& imageOverride, float xtraAlpha=1.f);
 *
 *     // Creates a constant color PaintParams with the specific blend mode.
 *     PaintParams(const SkColor4f& color, SkBlendMode finalBlendMode);
 *
 *     const SkColor4f& color() const { return fColor; }
 *     const SkShader* shader() const { return fShader; }
 *     const SimpleImage* imageShader() const { return fImageShader; }
 *     const SkColorFilter* colorFilter() const { return fColorFilter; }
 *     const SkBlender* primitiveBlender() const { return fPrimitiveBlender; }
 *     bool skipPrimitiveColorXform() const { return fSkipColorXform; }
 *
 *     const SkBlender* finalBlender() const { return fFinalBlend.first; }
 *     // Must also check finalBlender() to see if that overrides finalBlendMode() behavior.
 *     SkBlendMode finalBlendMode() const { SkASSERT(!fFinalBlend.first); return fFinalBlend.second; }
 *
 *     bool dither() const { return fDither; }
 *
 *     /** Converts an SkColor4f to the destination color space. */
 *     static SkColor4f Color4fPrepForDst(SkColor4f srgb, const SkColorInfo& dstColorInfo);
 *
 * private:
 *     PaintParams(const SkPaint&,
 *                 const SimpleImage* imageOverride,
 *                 const SkBlender* primitiveBlender,
 *                 bool skipColorXform,
 *                 bool ignoreShader);
 *
 *     SkColor4f fColor;
 *
 *     // Either a non-null SkBlender for runtime blending, or the SkBlendMode to use instead. If
 *     // the blender is non-null, the blend mode is set to kSrc to match the HW blend config used for
 *     // shader-based blending.
 *     std::pair<const SkBlender*, SkBlendMode> fFinalBlend;
 *
 *     const SkShader*      fShader;
 *     const SimpleImage*   fImageShader; // Overrides fShader for color images, mixes for alpha
 *     const SkColorFilter* fColorFilter;
 *
 *     // A nullptr fPrimitiveBlender means there's no primitive color blending and it is skipped.
 *     // In the case where there is primitive blending, the primitive color is the source color and
 *     // the dest is the paint's color (or the paint's shader's computed color).
 *     const SkBlender* fPrimitiveBlender;
 *     bool             fSkipColorXform;
 *     bool             fDither;
 * }
 * ```
 */
public data class PaintParams public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColor
   * ```
   */
  private var fColor: SkColorInfo,
  /**
   * C++ original:
   * ```cpp
   * std::pair<const SkBlender*, SkBlendMode> fFinalBlend
   * ```
   */
  private var fFinalBlend: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkShader*      fShader
   * ```
   */
  private val fShader: SkShader?,
  /**
   * C++ original:
   * ```cpp
   * const SimpleImage*   fImageShader
   * ```
   */
  private val fImageShader: SimpleImage?,
  /**
   * C++ original:
   * ```cpp
   * const SkColorFilter* fColorFilter
   * ```
   */
  private val fColorFilter: Int?,
  /**
   * C++ original:
   * ```cpp
   * const SkBlender* fPrimitiveBlender
   * ```
   */
  private val fPrimitiveBlender: Int?,
  /**
   * C++ original:
   * ```cpp
   * bool             fSkipColorXform
   * ```
   */
  private var fSkipColorXform: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool             fDither
   * ```
   */
  private var fDither: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkColor4f& color() const { return fColor; }
   * ```
   */
  public fun color(): SkColorInfo {
    TODO("Implement color")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkShader* shader() const { return fShader; }
   * ```
   */
  public fun shader(): SkShader {
    TODO("Implement shader")
  }

  /**
   * C++ original:
   * ```cpp
   * const SimpleImage* imageShader() const { return fImageShader; }
   * ```
   */
  public fun imageShader(): SimpleImage {
    TODO("Implement imageShader")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorFilter* colorFilter() const { return fColorFilter; }
   * ```
   */
  public fun colorFilter(): Int {
    TODO("Implement colorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlender* primitiveBlender() const { return fPrimitiveBlender; }
   * ```
   */
  public fun primitiveBlender(): Int {
    TODO("Implement primitiveBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * bool skipPrimitiveColorXform() const { return fSkipColorXform; }
   * ```
   */
  public fun skipPrimitiveColorXform(): Boolean {
    TODO("Implement skipPrimitiveColorXform")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBlender* finalBlender() const { return fFinalBlend.first; }
   * ```
   */
  public fun finalBlender(): Int {
    TODO("Implement finalBlender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode finalBlendMode() const { SkASSERT(!fFinalBlend.first); return fFinalBlend.second; }
   * ```
   */
  public fun finalBlendMode(): Int {
    TODO("Implement finalBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool dither() const { return fDither; }
   * ```
   */
  public fun dither(): Boolean {
    TODO("Implement dither")
  }

  public data class SimpleImage public constructor(
    public val fImage: SkImage?,
    public val fLocalMatrix: Int?,
    public var fSubset: Int,
    public var fSamplingOptions: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkColor4f PaintParams::Color4fPrepForDst(SkColor4f srcColor, const SkColorInfo& dstColorInfo) {
     *     // xform from sRGB to the destination colorspace
     *     SkColorSpaceXformSteps steps(sk_srgb_singleton(),       kUnpremul_SkAlphaType,
     *                                  dstColorInfo.colorSpace(), kUnpremul_SkAlphaType);
     *
     *     SkColor4f result = srcColor;
     *     steps.apply(result.vec());
     *     return result;
     * }
     * ```
     */
    public fun color4fPrepForDst(srgb: SkColor4f, dstColorInfo: SkColorInfo): SkColorInfo {
      TODO("Implement color4fPrepForDst")
    }
  }
}
