package org.skia.core

import kotlin.Int
import org.skia.effects.SkGradient
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc
import undefined.GradientInfo

/**
 * C++ original:
 * ```cpp
 * class SkSweepGradient final : public SkGradientBaseShader {
 * public:
 *     SkSweepGradient(const SkPoint& center, SkScalar t0, SkScalar t1, const SkGradient&);
 *
 *     GradientType asGradient(GradientInfo* info, SkMatrix* localMatrix) const override;
 *
 *     const SkPoint& center() const { return fCenter; }
 *     SkScalar tBias() const { return fTBias; }
 *     SkScalar tScale() const { return fTScale; }
 *
 * protected:
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     void appendGradientStages(SkArenaAlloc* alloc,
 *                               SkRasterPipeline* tPipeline,
 *                               SkRasterPipeline* postPipeline) const override;
 *
 * private:
 *     friend void ::SkRegisterSweepGradientShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkSweepGradient)
 *
 *     const SkPoint fCenter;
 *     const SkScalar fTBias;
 *     const SkScalar fTScale;
 * }
 * ```
 */
public class SkSweepGradient public constructor(
  center: SkPoint,
  t0: SkScalar,
  t1: SkScalar,
  desc: SkGradient,
) : SkGradientBaseShader(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fTBias
   * ```
   */
  private val fTBias: Int = TODO("Initialize fTBias")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar fTScale
   * ```
   */
  private val fTScale: Int = TODO("Initialize fTScale")

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkSweepGradient::asGradient(GradientInfo* info,
   *                                                        SkMatrix* localMatrix) const {
   *     if (info) {
   *         commonAsAGradient(info);
   *         info->fPoint[0] = fCenter;
   *         info->fPoint[1].fX = fTScale;
   *         info->fPoint[1].fY = fTBias;
   *     }
   *     if (localMatrix) {
   *         *localMatrix = SkMatrix::I();
   *     }
   *     return GradientType::kSweep;
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, localMatrix: SkMatrix?): Int {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& center() const { return fCenter; }
   * ```
   */
  public fun center(): Int {
    TODO("Implement center")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar tBias() const { return fTBias; }
   * ```
   */
  public fun tBias(): Int {
    TODO("Implement tBias")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar tScale() const { return fTScale; }
   * ```
   */
  public fun tScale(): Int {
    TODO("Implement tScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSweepGradient::flatten(SkWriteBuffer& buffer) const {
   *     this->SkGradientBaseShader::flatten(buffer);
   *     buffer.writePoint(fCenter);
   *     buffer.writeScalar(fTBias);
   *     buffer.writeScalar(fTScale);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSweepGradient::appendGradientStages(SkArenaAlloc* alloc, SkRasterPipeline* p,
   *                                            SkRasterPipeline*) const {
   *     p->append(SkRasterPipelineOp::xy_to_unit_angle);
   *     p->appendMatrix(alloc, SkMatrix::Scale(fTScale, 1) * SkMatrix::Translate(fTBias, 0));
   * }
   * ```
   */
  protected override fun appendGradientStages(
    alloc: SkArenaAlloc?,
    tPipeline: SkRasterPipeline?,
    postPipeline: SkRasterPipeline?,
  ) {
    TODO("Implement appendGradientStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkSweepGradient::CreateProc(SkReadBuffer& buffer) {
   *     SkGradientScope scope;
   *     SkMatrix legacyLocalMatrix, *lmPtr = nullptr;
   *     auto grad = scope.unflatten(buffer, &legacyLocalMatrix);
   *     if (!grad) {
   *         return nullptr;
   *     }
   *     if (!legacyLocalMatrix.isIdentity()) {
   *         lmPtr = &legacyLocalMatrix;
   *     }
   *     const SkPoint center = buffer.readPoint();
   *
   *     const auto tBias  = buffer.readScalar(),
   *                tScale = buffer.readScalar();
   *     auto [startAngle, endAngle] = angles_from_t_coeff(tBias, tScale);
   *
   *     return SkShaders::SweepGradient(center, startAngle, endAngle, *grad, lmPtr);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
