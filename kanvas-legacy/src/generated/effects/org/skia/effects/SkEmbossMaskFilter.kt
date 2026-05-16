package org.skia.effects

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkMaskFilterBase
import org.skia.foundation.SkColor
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkEmbossMaskFilter : public SkMaskFilterBase {
 * public:
 *     struct Light {
 *         SkScalar    fDirection[3];  // x,y,z
 *         uint16_t    fPad;
 *         uint8_t     fAmbient;
 *         uint8_t     fSpecular;      // exponent, 4.4 right now
 *     };
 *
 *     static sk_sp<SkMaskFilter> Make(SkScalar blurSigma, const Light& light);
 *
 *     // overrides from SkMaskFilter
 *     //  This method is not exported to java.
 *     SkMask::Format getFormat() const override;
 *     //  This method is not exported to java.
 *     bool filterMask(SkMaskBuilder* dst, const SkMask& src, const SkMatrix&,
 *                     SkIPoint* margin) const override;
 *     SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kEmboss; }
 *     std::pair<sk_sp<SkImageFilter>, bool> asImageFilter(const SkMatrix& ctm,
 *                                                         const SkPaint& paint) const override;
 *
 * protected:
 *     SkEmbossMaskFilter(SkScalar blurSigma, const Light& light);
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkEmbossMaskFilter)
 *
 *    /**
 *      *  Create a filter that calculates the specular illumination from a distant light source,
 *      *  interpreting the alpha channel of the input as the height profile of the surface (to
 *      *  approximate normal vectors). This is based on the legacy raster implementation of the
 *      *  emboss mask filter for clients that still use it.
 *      *  @param direction    The direction to the distance light.
 *      *  @param lightColor   The color of the specular light source.
 *      *  @param surfaceScale Scale factor to transform from alpha values to physical height.
 *      *  @param ks           Specular reflectance coefficient.
 *      *  @param shininess    The specular exponent determining how shiny the surface is.
 *      *  @param input        The input filter that defines surface normals (as alpha), or uses the
 *      *                      source bitmap when null.
 *      *  @param cropRect     Optional rectangle that crops the input and output.
 *      *
 *      * Defined in SkLightingImageFilter.cpp because it overlaps heavily with
 *      * SkImageFilters::DistantLitSpecular and that family of functions.
 *      */
 *     static sk_sp<SkImageFilter> LegacySpecular(const SkPoint3& direction, SkColor lightColor,
 *                                                SkScalar surfaceScale, SkScalar ks,
 *                                                SkScalar shininess, sk_sp<SkImageFilter> input);
 *
 *     Light fLight;
 *     SkScalar    fBlurSigma;
 *
 *     using INHERITED = SkMaskFilter;
 * }
 * ```
 */
public open class SkEmbossMaskFilter public constructor(
  blurSigma: SkScalar,
  light: Light,
) : SkMaskFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * Light fLight
   * ```
   */
  private var fLight: Light = TODO("Initialize fLight")

  /**
   * C++ original:
   * ```cpp
   * SkScalar    fBlurSigma
   * ```
   */
  private var fBlurSigma: SkScalar = TODO("Initialize fBlurSigma")

  /**
   * C++ original:
   * ```cpp
   * SkMask::Format SkEmbossMaskFilter::getFormat() const {
   *     return SkMask::k3D_Format;
   * }
   * ```
   */
  public override fun getFormat(): SkMask.Format {
    TODO("Implement getFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkEmbossMaskFilter::filterMask(SkMaskBuilder* dst, const SkMask& src,
   *                                     const SkMatrix& matrix, SkIPoint* margin) const {
   *     if (src.fFormat != SkMask::kA8_Format) {
   *         return false;
   *     }
   *
   *     SkScalar sigma = matrix.mapRadius(fBlurSigma);
   *
   *     if (!SkBlurMask::BoxBlur(dst, src, sigma, kInner_SkBlurStyle)) {
   *         return false;
   *     }
   *
   *     dst->format() = SkMask::k3D_Format;
   *     if (margin) {
   *         margin->set(SkScalarCeilToInt(3*sigma), SkScalarCeilToInt(3*sigma));
   *     }
   *
   *     if (src.fImage == nullptr) {
   *         return true;
   *     }
   *
   *     // create a larger buffer for the other two channels (should force fBlur to do this for us)
   *
   *     {
   *         uint8_t* alphaPlane = dst->image();
   *         size_t totalSize = dst->computeTotalImageSize();
   *         if (totalSize == 0) {
   *             return false;  // too big to allocate, abort
   *         }
   *         size_t planeSize = dst->computeImageSize();
   *         SkASSERT(planeSize != 0);  // if totalSize didn't overflow, this can't either
   *         dst->image() = SkMaskBuilder::AllocImage(totalSize);
   *         memcpy(dst->image(), alphaPlane, planeSize);
   *         SkMaskBuilder::FreeImage(alphaPlane);
   *     }
   *
   *     // run the light direction through the matrix...
   *     Light   light = fLight;
   *     matrix.mapVectors({(SkVector*)(void*)light.fDirection, 1},
   *                       {(SkVector*)(void*)fLight.fDirection, 1});
   *
   *     // now restore the length of the XY component
   *     // cast to SkVector so we can call setLength (this double cast silences alias warnings)
   *     SkVector* vec = (SkVector*)(void*)light.fDirection;
   *     vec->setLength(light.fDirection[0],
   *                    light.fDirection[1],
   *                    SkPoint::Length(fLight.fDirection[0], fLight.fDirection[1]));
   *
   *     SkEmbossMask::Emboss(dst, light);
   *
   *     // restore original alpha
   *     memcpy(dst->image(), src.fImage, src.computeImageSize());
   *
   *     return true;
   * }
   * ```
   */
  public override fun filterMask(
    dst: SkMaskBuilder?,
    src: SkMask,
    matrix: SkMatrix,
    margin: SkIPoint?,
  ): Boolean {
    TODO("Implement filterMask")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskFilterBase::Type type() const override { return SkMaskFilterBase::Type::kEmboss; }
   * ```
   */
  public override fun type(): SkMaskFilterBase.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<SkImageFilter>, bool> SkEmbossMaskFilter::asImageFilter(
   *         const SkMatrix& ctm, const SkPaint& paint) const {
   *     // Here the original bitmap we are operating on (nullptr for imageFilters) should be
   *     // our coverage mask, as a white RGBA8 image where the alpha corresponds to the coverage.
   *     sk_sp<SkImageFilter> coverageBlurred = SkImageFilters::Blur(fBlurSigma, fBlurSigma, nullptr);
   *
   *     // The paint should have the original shading properties that we want to apply.
   *     sk_sp<SkShader> srcShader = SkShaders::Color(paint.getColor4f(), /*cs=*/nullptr);
   *     if (paint.getShader()) {
   *         srcShader = SkShaders::Blend(SkBlendMode::kDstIn, paint.refShader(), std::move(srcShader));
   *     }
   *     srcShader = srcShader->makeWithColorFilter(paint.refColorFilter());
   *     sk_sp<SkImageFilter> srcColor = SkImageFilters::Shader(
   *         std::move(srcShader), paint.isDither() ? SkImageFilters::Dither::kYes
   *                                                : SkImageFilters::Dither::kNo);
   *
   *     // ka = fLight.fAmbient
   *     float ambientf = fLight.fAmbient / 255.f;
   *     SkColor4f ambientColor = {ambientf, ambientf, ambientf, 1};
   *     sk_sp<SkImageFilter> ambient = SkImageFilters::Shader(SkShaders::Color(ambientColor, nullptr));
   *
   *     // L = fLight.fDirection
   *     SkPoint3 lightDirection = SkPoint3::Make(fLight.fDirection[0],
   *                                              fLight.fDirection[1],
   *                                              fLight.fDirection[2]);
   *
   *
   *     // Amount to scale the alpha by to calculate N, set this way to mimic the legacy
   *     // emboss mask filter implementation.
   *     // Made negative to match functionality of legacy emboss mask filter which calculates
   *     // the normal "into" the monitor, away from the user, whereas all other documentation
   *     // points normals towards negative directions (towards user).
   *     const float surfaceScale = -255.f/ 32.f;
   *
   *     // diffuse = kd * dot(L, N)
   *     sk_sp<SkImageFilter> diffuseCF = SkImageFilters::DistantLitDiffuse(lightDirection,
   *                                                                        SK_ColorWHITE,
   *                                                                        surfaceScale,
   *                                                                        1,
   *                                                                        coverageBlurred);
   *     // mul = ka + diffuse
   *     sk_sp<SkImageFilter> ambientdiffuse = SkImageFilters::Blend(SkBlendMode::kPlus,
   *                                                                 diffuseCF,
   *                                                                 ambient);
   *     // ambientdiffuseColor = srcColor * mul
   *     sk_sp<SkImageFilter> ambientdiffuseBlend = SkImageFilters::Blend(
   *         SkBlendMode::kModulate, srcColor, ambientdiffuse);
   *
   *     // fLight.fSpecular is in a fixed 4.4 format.
   *     // This uses the legacy implementation for emboss which calculates the specular
   *     // lighting differently than standard specular functions.
   *     //
   *     // specular = ks * pow((2 * (L * N) - L_z) * L_z), shininess)
   *     float shininess = ((fLight.fSpecular >> 4) + 1);
   *
   *     sk_sp<SkImageFilter> specular = LegacySpecular(lightDirection,
   *                                                    SK_ColorWHITE,
   *                                                    surfaceScale,
   *                                                    1,
   *                                                    shininess,
   *                                                    coverageBlurred);
   *
   *     // dstColor = ambientdiffuseColor + specular
   *     //          = srcColor * (ka + kd * dot(L, N)) + ks * pow((2 * (L * N) - L_z) * L_z), shininess)
   *     sk_sp<SkImageFilter> finalFilter = SkImageFilters::Blend(SkBlendMode::kPlus,
   *                                                              ambientdiffuseBlend,
   *                                                              specular);
   *     // Mask by original coverage mask, it remains unchanged.
   *     // Return true to indicate applies shading.
   *     return {SkImageFilters::Blend(SkBlendMode::kDstIn, finalFilter, nullptr), true};
   * }
   * ```
   */
  public override fun asImageFilter(ctm: SkMatrix, paint: SkPaint): Int {
    TODO("Implement asImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkEmbossMaskFilter::flatten(SkWriteBuffer& buffer) const {
   *     Light tmpLight = fLight;
   *     tmpLight.fPad = 0;    // for the font-cache lookup to be clean
   *     buffer.writeByteArray(&tmpLight, sizeof(tmpLight));
   *     buffer.writeScalar(fBlurSigma);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkEmbossMaskFilter::CreateProc(SkReadBuffer& buffer) {
   *     Light light;
   *     if (buffer.readByteArray(&light, sizeof(Light))) {
   *         light.fPad = 0; // for the font-cache lookup to be clean
   *         const SkScalar sigma = buffer.readScalar();
   *         return Make(sigma, light);
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public data class Light public constructor(
    public var fDirection: Array<SkScalar>,
    public var fPad: Int,
    public var fAmbient: Int,
    public var fSpecular: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkMaskFilter> SkEmbossMaskFilter::Make(SkScalar blurSigma, const Light& light) {
     *     if (!SkIsFinite(blurSigma) || blurSigma <= 0) {
     *         return nullptr;
     *     }
     *
     *     SkPoint3 lightDir{light.fDirection[0], light.fDirection[1], light.fDirection[2]};
     *     if (!lightDir.normalize()) {
     *         return nullptr;
     *     }
     *     Light newLight = light;
     *     newLight.fDirection[0] = lightDir.x();
     *     newLight.fDirection[1] = lightDir.y();
     *     newLight.fDirection[2] = lightDir.z();
     *
     *     return sk_sp<SkMaskFilter>(new SkEmbossMaskFilter(blurSigma, newLight));
     * }
     * ```
     */
    public fun make(blurSigma: SkScalar, light: Light): SkSp<SkMaskFilter> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImageFilter> SkEmbossMaskFilter::LegacySpecular(
     *         const SkPoint3& direction, SkColor lightColor, SkScalar surfaceScale, SkScalar ks,
     *         SkScalar shininess, sk_sp<SkImageFilter> input) {
     *     return make_lighting(::Light::Distant(lightColor, direction),
     *                          Material::EmbossSpecular(ks, shininess, surfaceScale),
     *                          std::move(input), {});
     * }
     * ```
     */
    private fun legacySpecular(
      direction: SkPoint3,
      lightColor: SkColor,
      surfaceScale: SkScalar,
      ks: SkScalar,
      shininess: SkScalar,
      input: SkSp<SkImageFilter>,
    ): SkSp<SkImageFilter> {
      TODO("Implement legacySpecular")
    }
  }
}
