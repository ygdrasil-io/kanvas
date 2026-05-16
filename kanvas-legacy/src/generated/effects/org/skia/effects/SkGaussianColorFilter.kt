package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkStageRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkGaussianColorFilter final : public SkColorFilterBase {
 * public:
 *     SkGaussianColorFilter();
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kGaussian; }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override {}
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkGaussianColorFilter)
 * }
 * ```
 */
public class SkGaussianColorFilter public constructor() : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * bool SkGaussianColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     rec.fPipeline->append(SkRasterPipelineOp::gauss_a_to_rgba);
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kGaussian; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer&) const override {}
   * ```
   */
  protected override fun flatten(param0: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkGaussianColorFilter::CreateProc(SkReadBuffer&) {
   *     return SkColorFilterPriv::MakeGaussian();
   * }
   * ```
   */
  public fun createProc(param0: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
