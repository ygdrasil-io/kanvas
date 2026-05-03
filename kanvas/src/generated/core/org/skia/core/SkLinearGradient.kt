package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.effects.SkGradient
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.memory.SkArenaAlloc
import undefined.GradientInfo

/**
 * C++ original:
 * ```cpp
 * class SkLinearGradient final : public SkGradientBaseShader {
 * public:
 *     SkLinearGradient(const SkPoint pts[2], const SkGradient&);
 *
 *     GradientType asGradient(GradientInfo* info, SkMatrix* localMatrix) const override;
 *
 *     const SkPoint& start() const { return fStart; }
 *     const SkPoint& end() const { return fEnd; }
 * protected:
 *     SkLinearGradient(SkReadBuffer& buffer);
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     void appendGradientStages(SkArenaAlloc* alloc, SkRasterPipeline* tPipeline,
 *                               SkRasterPipeline* postPipeline) const final;
 *
 * private:
 *     friend void ::SkRegisterLinearGradientShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkLinearGradient)
 *
 *     class LinearGradient4fContext;
 *
 *     friend class SkGradientShader;
 *     using INHERITED = SkGradientBaseShader;
 *     const SkPoint fStart;
 *     const SkPoint fEnd;
 * }
 * ```
 */
public class SkLinearGradient public constructor(
  pts: SkPoint,
  param1: SkGradient,
) : SkGradientBaseShader() {
  /**
   * C++ original:
   * ```cpp
   * const SkPoint fStart
   * ```
   */
  private val fStart: Int = TODO("Initialize fStart")

  /**
   * C++ original:
   * ```cpp
   * const SkPoint fEnd
   * ```
   */
  private val fEnd: Int = TODO("Initialize fEnd")

  /**
   * C++ original:
   * ```cpp
   * SkLinearGradient(const SkPoint pts[2], const SkGradient&)
   * ```
   */
  public constructor(buffer: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkLinearGradient(SkReadBuffer& buffer)
   * ```
   */
  public constructor(pts: Array<SkPoint>, desc: SkGradient) : super(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkLinearGradient::asGradient(GradientInfo* info,
   *                                                         SkMatrix* localMatrix) const {
   *     if (info) {
   *         commonAsAGradient(info);
   *         info->fPoint[0] = fStart;
   *         info->fPoint[1] = fEnd;
   *     }
   *     if (localMatrix) {
   *         *localMatrix = SkMatrix::I();
   *     }
   *     return GradientType::kLinear;
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, localMatrix: SkMatrix?): Int {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& start() const { return fStart; }
   * ```
   */
  public fun start(): Int {
    TODO("Implement start")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& end() const { return fEnd; }
   * ```
   */
  public fun end(): Int {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLinearGradient::flatten(SkWriteBuffer& buffer) const {
   *     this->INHERITED::flatten(buffer);
   *     buffer.writePoint(fStart);
   *     buffer.writePoint(fEnd);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLinearGradient::appendGradientStages(SkArenaAlloc*, SkRasterPipeline*,
   *                                             SkRasterPipeline*) const {
   *     // No extra stage needed for linear gradients.
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
   * sk_sp<SkFlattenable> SkLinearGradient::CreateProc(SkReadBuffer& buffer) {
   *     SkGradientScope scope;
   *     SkMatrix legacyLocalMatrix, *lmPtr = nullptr;
   *     auto grad = scope.unflatten(buffer, &legacyLocalMatrix);
   *     if (!grad) {
   *         return nullptr;
   *     }
   *     if (!legacyLocalMatrix.isIdentity()) {
   *         lmPtr = &legacyLocalMatrix;
   *     }
   *     SkPoint pts[2];
   *     pts[0] = buffer.readPoint();
   *     pts[1] = buffer.readPoint();
   *     return SkShaders::LinearGradient(pts, *grad, lmPtr);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
