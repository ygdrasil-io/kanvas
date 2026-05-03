package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileImageFilter : public PrecompileBase {
 * public:
 *     ~PrecompileImageFilter() override;
 *
 *     // Provides access to functions that aren't part of the public API.
 *     PrecompileImageFilterPriv priv();
 *     const PrecompileImageFilterPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * protected:
 *     PrecompileImageFilter(SkSpan<sk_sp<PrecompileImageFilter>> inputs);
 *
 * private:
 *     friend class PaintOptions;  // for createPipelines() access
 *     friend class PrecompileImageFilterPriv;
 *
 *     int countInputs() const { return fInputs.count(); }
 *
 *     const PrecompileImageFilter* getInput(int index) const {
 *         SkASSERT(index < this->countInputs());
 *         return fInputs[index].get();
 *     }
 *
 *     virtual sk_sp<PrecompileColorFilter> isColorFilterNode() const { return nullptr; }
 *
 *     sk_sp<PrecompileColorFilter> asAColorFilter() const;
 *
 *     // The PrecompileImageFilter classes do not use the PrecompileBase::addToKey virtual since
 *     // they, in general, do not themselves contribute to a given SkPaint/Pipeline but, rather,
 *     // create separate SkPaints/Pipelines from whole cloth (in onCreatePipelines).
 *     void addToKey(const KeyContext& /* keyContext */, int /* desiredCombination */) const final {
 *         SkASSERT(false);
 *     }
 *
 *     virtual void onCreatePipelines(const KeyContext&,
 *                                    const RenderPassDesc&,
 *                                    const PaintOptions::ProcessCombination&) const = 0;
 *
 *     void createPipelines(const KeyContext&,
 *                          const RenderPassDesc&,
 *                          const PaintOptions::ProcessCombination&);
 *
 *     skia_private::AutoSTArray<2, sk_sp<PrecompileImageFilter>> fInputs;
 * }
 * ```
 */
public abstract class PrecompileImageFilter public constructor(
  inputs: SkSpan<SkSp<PrecompileImageFilter>>,
) : PrecompileBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTArray<2, sk_sp<PrecompileImageFilter>> fInputs
   * ```
   */
  private var fInputs: Int = TODO("Initialize fInputs")

  /**
   * C++ original:
   * ```cpp
   * PrecompileImageFilterPriv priv()
   * ```
   */
  public override fun priv(): PrecompileImageFilterPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileImageFilterPriv priv() const
   * ```
   */
  private fun countInputs(): Int {
    TODO("Implement countInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * int countInputs() const { return fInputs.count(); }
   * ```
   */
  private fun getInput(index: Int): PrecompileImageFilter {
    TODO("Implement getInput")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileImageFilter* getInput(int index) const {
   *         SkASSERT(index < this->countInputs());
   *         return fInputs[index].get();
   *     }
   * ```
   */
  public open fun isColorFilterNode(): Int {
    TODO("Implement isColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<PrecompileColorFilter> isColorFilterNode() const { return nullptr; }
   * ```
   */
  private fun asAColorFilter(): Int {
    TODO("Implement asAColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileColorFilter> PrecompileImageFilter::asAColorFilter() const {
   *     sk_sp<PrecompileColorFilter> tmp = this->isColorFilterNode();
   *     if (!tmp) {
   *         return nullptr;
   *     }
   *     SkASSERT(this->countInputs() == 1);
   *     if (this->getInput(0)) {
   *         return nullptr;
   *     }
   *     // TODO: as in SkImageFilter::asAColorFilter, handle the special case of
   *     // affectsTransparentBlack. This is tricky for precompilation since we don't,
   *     // necessarily, have all the parameters of the ColorFilter in order to evaluate
   *     // filterColor4f(SkColors::kTransparent) - the normal API's implementation.
   *     return tmp;
   * }
   * ```
   */
  private fun addToKey(param0: Int, param1: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& /* keyContext */, int /* desiredCombination */) const final {
   *         SkASSERT(false);
   *     }
   * ```
   */
  private abstract fun onCreatePipelines(
    param0: KeyContext,
    param1: RenderPassDesc,
    param2: PaintOptionsProcessCombination,
  )

  /**
   * C++ original:
   * ```cpp
   * virtual void onCreatePipelines(const KeyContext&,
   *                                    const RenderPassDesc&,
   *                                    const PaintOptions::ProcessCombination&) const = 0
   * ```
   */
  private fun createPipelines(
    keyContext: KeyContext,
    renderPassDesc: RenderPassDesc,
    processCombination: PaintOptionsProcessCombination,
  ) {
    TODO("Implement createPipelines")
  }
}
