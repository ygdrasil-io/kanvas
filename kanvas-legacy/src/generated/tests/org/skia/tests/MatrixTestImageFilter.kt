package org.skia.tests

import kotlin.Char
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.SkImageFilterBase
import org.skia.math.SkIRect
import org.skia.math.SkM44
import org.skia.modules.Factory

/**
 * C++ original:
 * ```cpp
 * class MatrixTestImageFilter : public SkImageFilter_Base {
 * public:
 *     MatrixTestImageFilter(skiatest::Reporter* reporter, const SkM44& expectedMatrix)
 *             : SkImageFilter_Base(nullptr, 0)
 *             , fReporter(reporter)
 *             , fExpectedMatrix(expectedMatrix) {
 *         // Layers have an extra pixel of padding that adjusts the coordinate space
 *         fExpectedMatrix.postTranslate(1.f, 1.f);
 *     }
 *
 * private:
 *     Factory getFactory() const override {
 *         SK_ABORT("Does not participate in serialization");
 *         return nullptr;
 *     }
 *     const char* getTypeName() const override { return "MatrixTestImageFilter"; }
 *
 *     skif::FilterResult onFilterImage(const skif::Context& ctx) const override {
 *         REPORTER_ASSERT(fReporter, ctx.mapping().layerMatrix() == fExpectedMatrix);
 *         return ctx.source();
 *     }
 *
 *     skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override {
 *         return desiredOutput;
 *     }
 *
 *     std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override {
 *         return contentBounds;
 *     }
 *
 *     skiatest::Reporter* fReporter;
 *     SkM44 fExpectedMatrix;
 * }
 * ```
 */
public open class MatrixTestImageFilter public constructor(
  reporter: Reporter?,
  expectedMatrix: SkM44,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  private var fReporter: Reporter? = TODO("Initialize fReporter")

  /**
   * C++ original:
   * ```cpp
   * SkM44 fExpectedMatrix
   * ```
   */
  private var fExpectedMatrix: SkM44 = TODO("Initialize fExpectedMatrix")

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override {
   *         SK_ABORT("Does not participate in serialization");
   *         return nullptr;
   *     }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return "MatrixTestImageFilter"; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult onFilterImage(const skif::Context& ctx) const override {
   *         REPORTER_ASSERT(fReporter, ctx.mapping().layerMatrix() == fExpectedMatrix);
   *         return ctx.source();
   *     }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> onGetInputLayerBounds(
   *             const skif::Mapping& mapping,
   *             const skif::LayerSpace<SkIRect>& desiredOutput,
   *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override {
   *         return desiredOutput;
   *     }
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
   * std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
   *             const skif::Mapping& mapping,
   *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override {
   *         return contentBounds;
   *     }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }
}
