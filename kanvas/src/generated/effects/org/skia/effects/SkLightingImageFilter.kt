package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkLightingImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkLightingImageFilter(const Light& light, const Material& material, sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fLight(light)
 *             , fMaterial(material) {}
 *
 *     SkRect computeFastBounds(const SkRect& src) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterLightingImageFilterFlattenables();
 *     SK_FLATTENABLE_HOOKS(SkLightingImageFilter)
 *     static Light LegacyDeserializeLight(SkReadBuffer& buffer);
 *     static sk_sp<SkFlattenable> LegacyDiffuseCreateProc(SkReadBuffer& buffer);
 *     static sk_sp<SkFlattenable> LegacySpecularCreateProc(SkReadBuffer& buffer);
 *
 *     bool onAffectsTransparentBlack() const override { return true; }
 *
 *     skif::FilterResult onFilterImage(const skif::Context&) const override;
 *
 *     skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     skif::LayerSpace<SkIRect> requiredInput(const skif::LayerSpace<SkIRect>& desiredOutput) const {
 *         // We request 1px of padding so that the visible normal map can do a regular Sobel kernel
 *         // eval. The Sobel kernel is always applied in layer pixels
 *         skif::LayerSpace<SkIRect> requiredInput = desiredOutput;
 *         requiredInput.outset(skif::LayerSpace<SkISize>({1, 1}));
 *         return requiredInput;
 *     }
 *
 *     Light fLight;
 *     Material fMaterial;
 * }
 * ```
 */
public class SkLightingImageFilter public constructor(
  light: Light,
  material: Material,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
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
   * Material fMaterial
   * ```
   */
  private var fMaterial: Material = TODO("Initialize fMaterial")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkLightingImageFilter::computeFastBounds(const SkRect& src) const {
   *     return SkRectPriv::MakeLargeS32();
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLightingImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *
   *     // Light
   *     buffer.writeInt((int) fLight.fType);
   *     buffer.writeColor(fLight.fLightColor);
   *
   *     buffer.writePoint(SkPoint(fLight.fLocationXY));
   *     buffer.writeScalar(ZValue(fLight.fLocationZ));
   *
   *     skif::Vector dirXY{fLight.fDirectionXY};
   *     buffer.writePoint(SkPoint{dirXY.fX, dirXY.fY});
   *     buffer.writeScalar(ZValue(fLight.fDirectionZ));
   *
   *     buffer.writeScalar(fLight.fFalloffExponent);
   *     buffer.writeScalar(fLight.fCosCutoffAngle);
   *
   *     // Material
   *     buffer.writeInt((int) fMaterial.fType);
   *     buffer.writeScalar(ZValue(fMaterial.fSurfaceDepth));
   *     buffer.writeScalar(fMaterial.fK);
   *     buffer.writeScalar(fMaterial.fShininess);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAffectsTransparentBlack() const override { return true; }
   * ```
   */
  public override fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkLightingImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     using ShaderFlags = skif::FilterResult::ShaderFlags;
   *
   *     auto mapZToLayer = [&ctx](skif::ParameterSpace<ZValue> z) {
   *         return skif::LayerSpace<ZValue>::Map(ctx.mapping(), z);
   *     };
   *
   *     // Map lighting and material parameters into layer space
   *     skif::LayerSpace<ZValue> surfaceDepth = mapZToLayer(fMaterial.fSurfaceDepth);
   *     skif::LayerSpace<SkPoint> lightLocationXY = ctx.mapping().paramToLayer(fLight.fLocationXY);
   *     skif::LayerSpace<ZValue> lightLocationZ = mapZToLayer(fLight.fLocationZ);
   *     skif::LayerSpace<skif::Vector> lightDirXY = ctx.mapping().paramToLayer(fLight.fDirectionXY);
   *     skif::LayerSpace<ZValue> lightDirZ = mapZToLayer(fLight.fDirectionZ);
   *
   *     // The normal map is determined by a 3x3 kernel, so we request a 1px outset of what should be
   *     // filled by the lighting equation. Ideally this means there are no boundary conditions visible.
   *     // If the required input is incomplete, the lighting filter handles the boundaries in two ways:
   *     // - When the actual child output's edge matches the desired output's edge, it uses clamped
   *     //   tiling at the desired output. This approximates the modified Sobel kernel's specified in
   *     //   https://drafts.fxtf.org/filter-effects/#feDiffuseLightingElement. NOTE: It's identical to
   *     //   the interior kernel and near equal on the 4 edges (only weights are biased differently).
   *     //   The four corners' convolution sums with clamped tiling are not equal, but should not be
   *     //   objectionable since the normals produced are reasonable and still further processed by the
   *     //   lighting equation. The increased complexity is not worth it for just 4 pixels of output.
   *     // - However, when the desired output is far larger than the produced image, we process the
   *     //   child output with the default decal tiling that the Skia image filter pipeline relies on.
   *     //   This creates a visual bevel at the image boundary but avoids producing streaked normals if
   *     //   the clamped tiling was used in all scenarios.
   *     skif::LayerSpace<SkIRect> requiredInput = this->requiredInput(ctx.desiredOutput());
   *     skif::FilterResult childOutput =
   *             this->getChildOutput(0, ctx.withNewDesiredOutput(requiredInput));
   *
   *     skif::LayerSpace<SkIRect> clampRect = requiredInput; // effectively no clamping of normals
   *     if (!childOutput.layerBounds().contains(requiredInput)) {
   *         // Adjust clampRect edges to desiredOutput if the actual child output matched the lighting
   *         // output size (typical SVG case). Otherwise leave coordinates alone to use decal tiling
   *         // automatically for the pixels outside the child image but inside the desired output.
   *         auto edgeClamp = [](int actualEdgeValue, int requestedEdgeValue, int outputEdge) {
   *             return actualEdgeValue == outputEdge ? outputEdge : requestedEdgeValue;
   *         };
   *         auto inputRect = childOutput.layerBounds();
   *         auto clampTo = ctx.desiredOutput();
   *         clampRect = skif::LayerSpace<SkIRect>({
   *                 edgeClamp(inputRect.left(),   requiredInput.left(),   clampTo.left()),
   *                 edgeClamp(inputRect.top(),    requiredInput.top(),    clampTo.top()),
   *                 edgeClamp(inputRect.right(),  requiredInput.right(),  clampTo.right()),
   *                 edgeClamp(inputRect.bottom(), requiredInput.bottom(), clampTo.bottom())});
   *     }
   *
   *     skif::FilterResult::Builder builder{ctx};
   *     builder.add(childOutput, /*sampleBounds=*/clampRect, ShaderFlags::kSampledRepeatedly);
   *     return builder.eval([&](SkSpan<sk_sp<SkShader>> input) {
   *         // TODO: Once shaders are deferred in FilterResult, it will likely make sense to have an
   *         // internal normal map filter that uses this shader, and then have the lighting effects as
   *         // a separate filter. It's common for multiple lights to use the same input (producing the
   *         // same normal map) before being merged together. With a separate normal image filter, its
   *         // output would be automatically cached, and the lighting equation shader would be deferred
   *         // to the merge's draw operation, making for a maximum of 2 renderpasses instead of N+1.
   *         sk_sp<SkShader> normals = make_normal_shader(std::move(input[0]), clampRect, surfaceDepth);
   *         return make_lighting_shader(std::move(normals),
   *                                     // Light in layer space
   *                                     fLight.fType,
   *                                     fLight.fLightColor,
   *                                     lightLocationXY,
   *                                     lightLocationZ,
   *                                     lightDirXY,
   *                                     lightDirZ,
   *                                     fLight.fFalloffExponent,
   *                                     fLight.fCosCutoffAngle,
   *                                     // Material in layer space
   *                                     fMaterial.fType,
   *                                     surfaceDepth,
   *                                     fMaterial.fK,
   *                                     fMaterial.fShininess);
   *     });
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkLightingImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     skif::LayerSpace<SkIRect> requiredInput = this->requiredInput(desiredOutput);
   *     return this->getChildInputLayerBounds(0, mapping, requiredInput, contentBounds);
   * }
   * ```
   */
  public override fun onGetInputLayerBounds(
    mapping: Mapping,
    desiredOutput: LayerSpace<SkIRect>,
    contentBounds: LayerSpace<SkIRect>?,
  ): LayerSpace<SkIRect> {
    TODO("Implement onGetInputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skif::LayerSpace<SkIRect>> SkLightingImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The lighting equation is defined on the entire plane, even if the input image that defines
   *     // the normal map is bounded. It just is evaluated at a constant normal vector, which can still
   *     // produce non-constant color since the direction to the eye and light change per pixel.
   *     return skif::LayerSpace<SkIRect>::Unbounded();
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> requiredInput(const skif::LayerSpace<SkIRect>& desiredOutput) const {
   *         // We request 1px of padding so that the visible normal map can do a regular Sobel kernel
   *         // eval. The Sobel kernel is always applied in layer pixels
   *         skif::LayerSpace<SkIRect> requiredInput = desiredOutput;
   *         requiredInput.outset(skif::LayerSpace<SkISize>({1, 1}));
   *         return requiredInput;
   *     }
   * ```
   */
  private fun requiredInput(desiredOutput: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement requiredInput")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkLightingImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *
   *     Light light;
   *     light.fType = buffer.read32LE(Light::Type::kLast);
   *     light.fLightColor = buffer.readColor();
   *
   *     SkPoint3 lightPos, lightDir;
   *     buffer.readPoint3(&lightPos);
   *     light.fLocationXY = skif::ParameterSpace<SkPoint>({lightPos.fX, lightPos.fY});
   *     light.fLocationZ = skif::ParameterSpace<ZValue>(lightPos.fZ);
   *
   *     buffer.readPoint3(&lightDir);
   *     light.fDirectionXY = skif::ParameterSpace<skif::Vector>({lightDir.fX, lightDir.fY});
   *     light.fDirectionZ = skif::ParameterSpace<ZValue>(lightDir.fZ);
   *
   *     light.fFalloffExponent = buffer.readScalar();
   *     light.fCosCutoffAngle = buffer.readScalar();
   *
   *     Material material;
   *     material.fType = buffer.read32LE(Material::Type::kLast);
   *     material.fSurfaceDepth = skif::ParameterSpace<ZValue>(buffer.readScalar());
   *     material.fK = buffer.readScalar();
   *     material.fShininess = buffer.readScalar();
   *
   *     if (!buffer.isValid()) {
   *         return nullptr;
   *     }
   *
   *     return make_lighting(light, material, common.getInput(0), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * Light SkLightingImageFilter::LegacyDeserializeLight(SkReadBuffer& buffer) {
     *     // Light::Type has the same order as the legacy SkImageFilterLight::LightType enum
     *     Light::Type lightType = buffer.read32LE(Light::Type::kLast);
     *     if (!buffer.isValid()) {
     *         return {};
     *     }
     *
     *     // Legacy lights stored just the RGB, but as floats (notably *not* normalized to [0-1])
     *     SkColor lightColor = SkColorSetARGB(/*a (ignored)=*/255,
     *                                         /*r=*/ (U8CPU) buffer.readScalar(),
     *                                         /*g=*/ (U8CPU) buffer.readScalar(),
     *                                         /*b=*/ (U8CPU) buffer.readScalar());
     *     // Legacy lights only serialized fields specific to that type
     *     switch (lightType) {
     *         case Light::Type::kDistant: {
     *             SkPoint3 dir = {buffer.readScalar(), buffer.readScalar(), buffer.readScalar()};
     *             return Light::Distant(lightColor, dir);
     *         }
     *         case Light::Type::kPoint: {
     *             SkPoint3 loc = {buffer.readScalar(), buffer.readScalar(), buffer.readScalar()};
     *             return Light::Point(lightColor, loc);
     *         }
     *         case Light::Type::kSpot: {
     *             SkPoint3 loc = {buffer.readScalar(), buffer.readScalar(), buffer.readScalar()};
     *             SkPoint3 target = {buffer.readScalar(), buffer.readScalar(), buffer.readScalar()};
     *             float falloffExponent = buffer.readScalar();
     *             float cosOuterConeAngle = buffer.readScalar();
     *             buffer.readScalar(); // skip cosInnerConeAngle, derived from outer cone angle
     *             buffer.readScalar(); // skip coneScale, which is a constant
     *             buffer.readScalar(); // skip S, which is normalize(target - loc)
     *             buffer.readScalar(); //  ""
     *             buffer.readScalar(); //  ""
     *             return Light::Spot(lightColor, loc, target - loc, falloffExponent, cosOuterConeAngle);
     *         }
     *     }
     *
     *     SkUNREACHABLE; // Validation by read32LE() should avoid this
     * }
     * ```
     */
    private fun legacyDeserializeLight(buffer: SkReadBuffer): Light {
      TODO("Implement legacyDeserializeLight")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFlattenable> SkLightingImageFilter::LegacyDiffuseCreateProc(SkReadBuffer& buffer) {
     *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
     *
     *     Light light = LegacyDeserializeLight(buffer);
     *
     *     // Legacy implementations used (scale/255) when filtering, but serialized (fScale*255) so the
     *     // buffer held the original unmodified surface scale.
     *     float surfaceScale = buffer.readScalar();
     *     float kd = buffer.readScalar();
     *     Material material = Material::Diffuse(kd, surfaceScale);
     *
     *     return make_lighting(light, material, common.getInput(0), common.cropRect());
     * }
     * ```
     */
    private fun legacyDiffuseCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement legacyDiffuseCreateProc")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFlattenable> SkLightingImageFilter::LegacySpecularCreateProc(SkReadBuffer& buffer) {
     *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
     *
     *     Light light = LegacyDeserializeLight(buffer);
     *
     *     // Legacy implementations used (scale/255) when filtering, but serialized (fScale*255) so the
     *     // buffer held the original unmodified surface scale.
     *     float surfaceScale = buffer.readScalar();
     *     float ks = buffer.readScalar();
     *     float shininess = buffer.readScalar();
     *     Material material = Material::Specular(ks, shininess, surfaceScale);
     *
     *     return make_lighting(light, material, common.getInput(0), common.cropRect());
     * }
     * ```
     */
    private fun legacySpecularCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement legacySpecularCreateProc")
    }
  }
}
