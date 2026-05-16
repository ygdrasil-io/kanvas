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
 * class SkRadialGradient final : public SkGradientBaseShader {
 * public:
 *     SkRadialGradient(const SkPoint& center, SkScalar radius, const SkGradient&);
 *
 *     GradientType asGradient(GradientInfo* info, SkMatrix* matrix) const override;
 *
 *     const SkPoint& center() const { return fCenter; }
 *     SkScalar radius() const { return fRadius; }
 * protected:
 *     SkRadialGradient(SkReadBuffer& buffer);
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     void appendGradientStages(SkArenaAlloc* alloc,
 *                               SkRasterPipeline* tPipeline,
 *                               SkRasterPipeline* postPipeline) const override;
 * private:
 *     friend void ::SkRegisterRadialGradientShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkRadialGradient)
 *
 *     const SkPoint fCenter;
 *     const SkScalar fRadius;
 * }
 * ```
 */
public class SkRadialGradient public constructor(
  center: SkPoint,
  radius: SkScalar,
  desc: SkGradient,
) : SkGradientBaseShader(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fRadius
   * ```
   */
  private val fRadius: Int = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * SkRadialGradient(const SkPoint& center, SkScalar radius, const SkGradient&)
   * ```
   */
  public constructor(buffer: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkRadialGradient::asGradient(GradientInfo* info,
   *                                                         SkMatrix* localMatrix) const {
   *     if (info) {
   *         commonAsAGradient(info);
   *         info->fPoint[0] = fCenter;
   *         info->fRadius[0] = fRadius;
   *     }
   *     if (localMatrix) {
   *         *localMatrix = SkMatrix::I();
   *     }
   *     return GradientType::kRadial;
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, matrix: SkMatrix?): Int {
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
   * SkScalar radius() const { return fRadius; }
   * ```
   */
  public fun radius(): Int {
    TODO("Implement radius")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRadialGradient::flatten(SkWriteBuffer& buffer) const {
   *     this->SkGradientBaseShader::flatten(buffer);
   *     buffer.writePoint(fCenter);
   *     buffer.writeScalar(fRadius);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRadialGradient::appendGradientStages(SkArenaAlloc*, SkRasterPipeline* p,
   *                                             SkRasterPipeline*) const {
   *     p->append(SkRasterPipelineOp::xy_to_radius);
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
   * sk_sp<SkFlattenable> SkRadialGradient::CreateProc(SkReadBuffer& buffer) {
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
   *     const SkScalar radius = buffer.readScalar();
   *     return SkShaders::RadialGradient(center, radius, *grad, lmPtr);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
