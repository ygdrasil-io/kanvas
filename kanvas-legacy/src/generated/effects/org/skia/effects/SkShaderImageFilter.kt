package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkShaderImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkShaderImageFilter(sk_sp<SkShader> shader, SkImageFilters::Dither dither)
 *             : SkImageFilter_Base(nullptr, 0)
 *             , fShader(std::move(shader))
 *             , fDither(dither) {
 *         SkASSERT(fShader);
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& /*bounds*/) const override {
 *         return SkRectPriv::MakeLargeS32();
 *     }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterShaderImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkShaderImageFilter)
 *
 *     bool onAffectsTransparentBlack() const override { return true; }
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
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
 *     sk_sp<SkShader> fShader;
 *     SkImageFilters::Dither fDither;
 * }
 * ```
 */
public class SkShaderImageFilter public constructor(
  shader: SkSp<SkShader>,
  dither: SkImageFilters.Dither,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * SkImageFilters::Dither fDither
   * ```
   */
  private var fDither: SkImageFilters.Dither = TODO("Initialize fDither")

  /**
   * C++ original:
   * ```cpp
   * SkRect computeFastBounds(const SkRect& /*bounds*/) const override {
   *         return SkRectPriv::MakeLargeS32();
   *     }
   * ```
   */
  public override fun computeFastBounds(param0: Int): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkShaderImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeFlattenable(fShader.get());
   *     buffer.writeBool(fDither == SkImageFilters::Dither::kYes);
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
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkShaderImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     const bool dither = fDither == SkImageFilters::Dither::kYes;
   *     return skif::FilterResult::MakeFromShader(ctx, fShader, dither);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkShaderImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping&,
   *         const skif::LayerSpace<SkIRect>&,
   *         std::optional<skif::LayerSpace<SkIRect>>) const {
   *     // This is a leaf filter, it requires no input and no further recursion
   *     return skif::LayerSpace<SkIRect>::Empty();
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
   * std::optional<skif::LayerSpace<SkIRect>> SkShaderImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping&,
   *         std::optional<skif::LayerSpace<SkIRect>>) const {
   *     // The output of a shader is infinite, unless we were to inspect the shader for a decal
   *     // tile mode around a gradient or image.
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
   * sk_sp<SkFlattenable> SkShaderImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 0);
   *     sk_sp<SkShader> shader;
   *     bool dither;
   *     if (buffer.isVersionLT(SkPicturePriv::kShaderImageFilterSerializeShader)) {
   *         // The old implementation stored an entire SkPaint, but we only need the SkShader and dither
   *         // boolean. We could fail if the paint stores more effects than that, but this is simpler.
   *         SkPaint paint = buffer.readPaint();
   *         shader = paint.getShader() ? paint.refShader()
   *                                    : SkShaders::Color(paint.getColor4f(), nullptr);
   *         dither = paint.isDither();
   *     } else {
   *         shader = buffer.readShader();
   *         dither = buffer.readBool();
   *     }
   *     return SkImageFilters::Shader(std::move(shader),
   *                                   SkImageFilters::Dither(dither),
   *                                   common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
