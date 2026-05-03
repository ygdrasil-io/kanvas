package org.skia.core

import kotlin.Any
import kotlin.Int
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkLocalMatrixImageFilter : public SkImageFilter_Base {
 * public:
 *     static sk_sp<SkImageFilter> Make(const SkMatrix& localMatrix, sk_sp<SkImageFilter> input);
 *
 *     SkRect computeFastBounds(const SkRect&) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkLocalMatrixImageFilter)
 *
 *     SkLocalMatrixImageFilter(const SkMatrix& localMatrix,
 *                              const SkMatrix& invLocalMatrix,
 *                              sk_sp<SkImageFilter> const* input)
 *             : SkImageFilter_Base(input, 1)
 *             , fLocalMatrix{localMatrix}
 *             , fInvLocalMatrix{invLocalMatrix} {}
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
 *
 *     skif::FilterResult onFilterImage(const skif::Context& ctx) const override;
 *
 *     skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping&,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping&,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     skif::Mapping localMapping(const skif::Mapping&) const;
 *
 *     // NOTE: This is not a ParameterSpace<SkMatrix> like that of SkMatrixTransformImageFilter.
 *     // It's a bit pedantic, but does impact the math. A parameter-space transform has to be modified
 *     // to represent a layer-space transform: (L*P*L^-1); while this local matrix changes L directly
 *     // to L*P for its child filter.
 *     SkMatrix fLocalMatrix;
 *     SkMatrix fInvLocalMatrix;
 * }
 * ```
 */
public open class SkLocalMatrixImageFilter public constructor(
  localMatrix: SkMatrix,
  invLocalMatrix: SkMatrix,
  input: Any?,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fLocalMatrix
   * ```
   */
  private var fLocalMatrix: SkMatrix = TODO("Initialize fLocalMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix fInvLocalMatrix
   * ```
   */
  private var fInvLocalMatrix: SkMatrix = TODO("Initialize fInvLocalMatrix")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkLocalMatrixImageFilter::computeFastBounds(const SkRect& bounds) const {
   *     // In onGet[Input|Output]LayerBounds, there is a Mapping that can be adjusted by the
   *     // local matrix, so their layer-space parameters do not need to be modified. Since
   *     // computeFastBounds() takes no matrix, it always operates as if it has the identity mapping.
   *     //
   *     // In order to match the behavior of onGetInputLayerBounds, we map 'bounds' by the inverse of
   *     // the local matrix, pass that to the child, and then map the result by the local matrix.
   *     // TODO: Implementing computeFastBounds in terms of onGetOutputLayerBounds() trivially removes
   *     // this complexity.
   *     SkRect localBounds = fInvLocalMatrix.mapRect(bounds);
   *     return fLocalMatrix.mapRect(this->getInput(0)->computeFastBounds(localBounds));
   * }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLocalMatrixImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeMatrix(fLocalMatrix);
   *     // fInvLocalMatrix will be reconstructed
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
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
   * skif::FilterResult SkLocalMatrixImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     skif::Mapping localMapping = this->localMapping(ctx.mapping());
   *     return this->getChildOutput(0, ctx.withNewMapping(localMapping));
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkLocalMatrixImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The local matrix changes 'mapping' by adjusting the parameter space of the image filter, but
   *     // both 'desiredOutput' and 'contentBounds' have already been transformed to the consistent
   *     // layer space. They remain unchanged with the new mapping.
   *     return this->getChildInputLayerBounds(0, this->localMapping(mapping),
   *                                           desiredOutput, contentBounds);
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
   * std::optional<skif::LayerSpace<SkIRect>> SkLocalMatrixImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     return this->getChildOutputLayerBounds(0, this->localMapping(mapping), contentBounds);
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::Mapping SkLocalMatrixImageFilter::localMapping(const skif::Mapping& mapping) const {
   *     skif::Mapping localMapping = mapping;
   *     localMapping.concatLocal(fLocalMatrix);
   *     return localMapping;
   * }
   * ```
   */
  private fun localMapping(mapping: Mapping): Mapping {
    TODO("Implement localMapping")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkLocalMatrixImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *     SkMatrix lm;
   *     buffer.readMatrix(&lm);
   *     return SkLocalMatrixImageFilter::Make(lm, common.getInput(0));
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
     * sk_sp<SkImageFilter> SkLocalMatrixImageFilter::Make(const SkMatrix& localMatrix,
     *                                                     sk_sp<SkImageFilter> input) {
     *     if (!input) {
     *         return nullptr;
     *     }
     *     if (localMatrix.isIdentity()) {
     *         return input;
     *     }
     *
     *     MatrixCapability inputCapability = as_IFB(input)->getCTMCapability();
     *     if ((inputCapability == MatrixCapability::kTranslate && !localMatrix.isTranslate()) ||
     *         (inputCapability == MatrixCapability::kScaleTranslate && !localMatrix.isScaleTranslate())) {
     *         // Nothing we can do at this point
     *         return nullptr;
     *     }
     *
     *     if (auto invLocal = localMatrix.invert()) {
     *         return sk_sp<SkImageFilter>(new SkLocalMatrixImageFilter(localMatrix, *invLocal, &input));
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun make(localMatrix: SkMatrix, input: SkSp<SkImageFilter>): SkSp<SkImageFilter> {
      TODO("Implement make")
    }
  }
}
