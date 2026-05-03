package org.skia.core

import kotlin.Boolean
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
 * class SkConicalGradient final : public SkGradientBaseShader {
 * public:
 *     // See https://skia.org/dev/design/conical for what focal data means and how our shader works.
 *     // We make it public so the GPU shader can also use it.
 *     struct FocalData {
 *         SkScalar fR1;      // r1 after mapping focal point to (0, 0)
 *         SkScalar fFocalX;  // f
 *         bool fIsSwapped;   // whether we swapped r0, r1
 *
 *         // The input r0, r1 are the radii when we map centers to {(0, 0), (1, 0)}.
 *         // We'll post concat matrix with our transformation matrix that maps focal point to (0, 0).
 *         // Returns true if the set succeeded
 *         bool set(SkScalar r0, SkScalar r1, SkMatrix* matrix);
 *
 *         // Whether the focal point (0, 0) is on the end circle with center (1, 0) and radius r1. If
 *         // this is true, it's as if an aircraft is flying at Mach 1 and all circles (soundwaves)
 *         // will go through the focal point (aircraft). In our previous implementations, this was
 *         // known as the edge case where the inside circle touches the outside circle (on the focal
 *         // point). If we were to solve for t bruteforcely using a quadratic equation, this case
 *         // implies that the quadratic equation degenerates to a linear equation.
 *         bool isFocalOnCircle() const { return SkScalarNearlyZero(1 - fR1); }
 *
 *         bool isSwapped() const { return fIsSwapped; }
 *         bool isWellBehaved() const { return !this->isFocalOnCircle() && fR1 > 1; }
 *         bool isNativelyFocal() const { return SkScalarNearlyZero(fFocalX); }
 *     };
 *
 *     enum class Type { kRadial, kStrip, kFocal };
 *
 *     static std::optional<SkMatrix> MapToUnitX(const SkPoint& startCenter,
 *                                               const SkPoint& endCenter);
 *
 *     static sk_sp<SkShader> Create(const SkPoint& start,
 *                                   SkScalar startRadius,
 *                                   const SkPoint& end,
 *                                   SkScalar endRadius,
 *                                   const SkGradient&,
 *                                   const SkMatrix* localMatrix);
 *
 *     GradientType asGradient(GradientInfo* info, SkMatrix* localMatrix) const override;
 *     bool isOpaque() const override;
 *
 *     SkScalar getCenterX1() const { return SkPoint::Distance(fCenter1, fCenter2); }
 *     SkScalar getStartRadius() const { return fRadius1; }
 *     SkScalar getDiffRadius() const { return fRadius2 - fRadius1; }
 *     const SkPoint& getStartCenter() const { return fCenter1; }
 *     const SkPoint& getEndCenter() const { return fCenter2; }
 *     SkScalar getEndRadius() const { return fRadius2; }
 *
 *     Type getType() const { return fType; }
 *     const FocalData& getFocalData() const { return fFocalData; }
 *
 *     SkConicalGradient(const SkPoint& c0,
 *                       SkScalar r0,
 *                       const SkPoint& c1,
 *                       SkScalar r1,
 *                       const SkGradient&,
 *                       Type,
 *                       const SkMatrix&,
 *                       const FocalData&);
 *
 * protected:
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     void appendGradientStages(SkArenaAlloc* alloc,
 *                               SkRasterPipeline* tPipeline,
 *                               SkRasterPipeline* postPipeline) const override;
 *
 * private:
 *     friend void ::SkRegisterConicalGradientShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkConicalGradient)
 *
 *     SkPoint fCenter1;
 *     SkPoint fCenter2;
 *     SkScalar fRadius1;
 *     SkScalar fRadius2;
 *     Type fType;
 *
 *     FocalData fFocalData;
 * }
 * ```
 */
public class SkConicalGradient public constructor(
  start: SkPoint,
  startRadius: SkScalar,
  end: SkPoint,
  endRadius: SkScalar,
  desc: SkGradient,
  type: Type,
  gradientMatrix: SkMatrix,
  `data`: FocalData,
) : SkGradientBaseShader(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkPoint fCenter2
   * ```
   */
  private var fCenter2: Int = TODO("Initialize fCenter2")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius1
   * ```
   */
  private var fRadius1: Int = TODO("Initialize fRadius1")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius2
   * ```
   */
  private var fRadius2: Int = TODO("Initialize fRadius2")

  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  private var fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * FocalData fFocalData
   * ```
   */
  private var fFocalData: FocalData = TODO("Initialize fFocalData")

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkConicalGradient::asGradient(GradientInfo* info,
   *                                                          SkMatrix* localMatrix) const {
   *     if (info) {
   *         commonAsAGradient(info);
   *         info->fPoint[0] = fCenter1;
   *         info->fPoint[1] = fCenter2;
   *         info->fRadius[0] = fRadius1;
   *         info->fRadius[1] = fRadius2;
   *     }
   *     if (localMatrix) {
   *         *localMatrix = SkMatrix::I();
   *     }
   *     return GradientType::kConical;
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, localMatrix: SkMatrix?): Int {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkConicalGradient::isOpaque() const {
   *     // Because areas outside the cone are left untouched, we cannot treat the
   *     // shader as opaque even if the gradient itself is opaque.
   *     // TODO(junov): Compute whether the cone fills the plane crbug.com/222380
   *     return false;
   * }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getCenterX1() const { return SkPoint::Distance(fCenter1, fCenter2); }
   * ```
   */
  public fun getCenterX1(): Int {
    TODO("Implement getCenterX1")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getStartRadius() const { return fRadius1; }
   * ```
   */
  public fun getStartRadius(): Int {
    TODO("Implement getStartRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getDiffRadius() const { return fRadius2 - fRadius1; }
   * ```
   */
  public fun getDiffRadius(): Int {
    TODO("Implement getDiffRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& getStartCenter() const { return fCenter1; }
   * ```
   */
  public fun getStartCenter(): Int {
    TODO("Implement getStartCenter")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& getEndCenter() const { return fCenter2; }
   * ```
   */
  public fun getEndCenter(): Int {
    TODO("Implement getEndCenter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getEndRadius() const { return fRadius2; }
   * ```
   */
  public fun getEndRadius(): Int {
    TODO("Implement getEndRadius")
  }

  /**
   * C++ original:
   * ```cpp
   * Type getType() const { return fType; }
   * ```
   */
  public fun getType(): Type {
    TODO("Implement getType")
  }

  /**
   * C++ original:
   * ```cpp
   * const FocalData& getFocalData() const { return fFocalData; }
   * ```
   */
  public fun getFocalData(): FocalData {
    TODO("Implement getFocalData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConicalGradient::flatten(SkWriteBuffer& buffer) const {
   *     this->SkGradientBaseShader::flatten(buffer);
   *     buffer.writePoint(fCenter1);
   *     buffer.writePoint(fCenter2);
   *     buffer.writeScalar(fRadius1);
   *     buffer.writeScalar(fRadius2);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkConicalGradient::appendGradientStages(SkArenaAlloc* alloc,
   *                                              SkRasterPipeline* p,
   *                                              SkRasterPipeline* postPipeline) const {
   *     const auto dRadius = fRadius2 - fRadius1;
   *
   *     if (fType == Type::kRadial) {
   *         p->append(SkRasterPipelineOp::xy_to_radius);
   *
   *         // Tiny twist: radial computes a t for [0, r2], but we want a t for [r1, r2].
   *         auto scale = std::max(fRadius1, fRadius2) / dRadius;
   *         auto bias = -fRadius1 / dRadius;
   *
   *         p->appendMatrix(alloc, SkMatrix::Translate(bias, 0) * SkMatrix::Scale(scale, 1));
   *         return;
   *     }
   *
   *     if (fType == Type::kStrip) {
   *         auto* ctx = alloc->make<SkRasterPipelineContexts::Conical2PtCtx>();
   *         SkScalar scaledR0 = fRadius1 / this->getCenterX1();
   *         ctx->fP0 = scaledR0 * scaledR0;
   *         p->append(SkRasterPipelineOp::xy_to_2pt_conical_strip, ctx);
   *         p->append(SkRasterPipelineOp::mask_2pt_conical_nan, ctx);
   *         postPipeline->append(SkRasterPipelineOp::apply_vector_mask, &ctx->fMask);
   *         return;
   *     }
   *
   *     auto* ctx = alloc->make<SkRasterPipelineContexts::Conical2PtCtx>();
   *     ctx->fP0 = 1 / fFocalData.fR1;
   *     ctx->fP1 = fFocalData.fFocalX;
   *
   *     if (fFocalData.isFocalOnCircle()) {
   *         p->append(SkRasterPipelineOp::xy_to_2pt_conical_focal_on_circle);
   *     } else if (fFocalData.isWellBehaved()) {
   *         p->append(SkRasterPipelineOp::xy_to_2pt_conical_well_behaved, ctx);
   *     } else if (fFocalData.isSwapped() || 1 - fFocalData.fFocalX < 0) {
   *         p->append(SkRasterPipelineOp::xy_to_2pt_conical_smaller, ctx);
   *     } else {
   *         p->append(SkRasterPipelineOp::xy_to_2pt_conical_greater, ctx);
   *     }
   *
   *     if (!fFocalData.isWellBehaved()) {
   *         p->append(SkRasterPipelineOp::mask_2pt_conical_degenerates, ctx);
   *     }
   *     if (1 - fFocalData.fFocalX < 0) {
   *         p->append(SkRasterPipelineOp::negate_x);
   *     }
   *     if (!fFocalData.isNativelyFocal()) {
   *         p->append(SkRasterPipelineOp::alter_2pt_conical_compensate_focal, ctx);
   *     }
   *     if (fFocalData.isSwapped()) {
   *         p->append(SkRasterPipelineOp::alter_2pt_conical_unswap);
   *     }
   *     if (!fFocalData.isWellBehaved()) {
   *         postPipeline->append(SkRasterPipelineOp::apply_vector_mask, &ctx->fMask);
   *     }
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
   * sk_sp<SkFlattenable> SkConicalGradient::CreateProc(SkReadBuffer& buffer) {
   *     SkGradientScope scope;
   *     SkMatrix legacyLocalMatrix, *lmPtr = nullptr;
   *     auto grad = scope.unflatten(buffer, &legacyLocalMatrix);
   *     if (!grad) {
   *         return nullptr;
   *     }
   *     if (!legacyLocalMatrix.isIdentity()) {
   *         lmPtr = &legacyLocalMatrix;
   *     }
   *     SkPoint c1 = buffer.readPoint();
   *     SkPoint c2 = buffer.readPoint();
   *     SkScalar r1 = buffer.readScalar();
   *     SkScalar r2 = buffer.readScalar();
   *
   *     if (!buffer.isValid()) {
   *         return nullptr;
   *     }
   *     return SkShaders::TwoPointConicalGradient(c1, r1, c2, r2, *grad, lmPtr);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public data class FocalData public constructor(
    public var fR1: Int,
    public var fFocalX: Int,
    public var fIsSwapped: Boolean,
  ) {
    public fun `set`(
      r0: SkScalar,
      r1: SkScalar,
      matrix: SkMatrix?,
    ): Boolean {
      TODO("Implement set")
    }

    public fun isFocalOnCircle(): Boolean {
      TODO("Implement isFocalOnCircle")
    }

    public fun isSwapped(): Boolean {
      TODO("Implement isSwapped")
    }

    public fun isWellBehaved(): Boolean {
      TODO("Implement isWellBehaved")
    }

    public fun isNativelyFocal(): Boolean {
      TODO("Implement isNativelyFocal")
    }
  }

  public enum class Type {
    kRadial,
    kStrip,
    kFocal,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::optional<SkMatrix> SkConicalGradient::MapToUnitX(const SkPoint &startCenter,
     *                                                       const SkPoint &endCenter) {
     *     const SkPoint centers[2] = { startCenter, endCenter };
     *     const SkPoint unitvec[2] = { {0, 0}, {1, 0} };
     *     return SkMatrix::PolyToPoly(centers, unitvec);
     * }
     * ```
     */
    public fun mapToUnitX(startCenter: SkPoint, endCenter: SkPoint): Int {
      TODO("Implement mapToUnitX")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkConicalGradient::Create(const SkPoint& c0,
     *                                           SkScalar r0,
     *                                           const SkPoint& c1,
     *                                           SkScalar r1,
     *                                           const SkGradient& desc,
     *                                           const SkMatrix* localMatrix) {
     *     SkMatrix gradientMatrix;
     *     Type gradientType;
     *
     *     if (SkScalarNearlyZero((c0 - c1).length())) {
     *         if (SkScalarNearlyZero(std::max(r0, r1)) || SkScalarNearlyEqual(r0, r1)) {
     *             // Degenerate case; avoid dividing by zero. Should have been caught by caller but
     *             // just in case, recheck here.
     *             return nullptr;
     *         }
     *         // Concentric case: we can pretend we're radial (with a tiny twist).
     *         const SkScalar scale = sk_ieee_float_divide(1, std::max(r0, r1));
     *         gradientMatrix = SkMatrix::Translate(-c1.x(), -c1.y());
     *         gradientMatrix.postScale(scale, scale);
     *
     *         gradientType = Type::kRadial;
     *     } else {
     *         auto mx = MapToUnitX(c0, c1);
     *         if (!mx) {
     *             // Degenerate case.
     *             return nullptr;
     *         }
     *         gradientMatrix = *mx;
     *
     *         gradientType = SkScalarNearlyZero(r1 - r0) ? Type::kStrip : Type::kFocal;
     *     }
     *
     *     FocalData focalData;
     *     if (gradientType == Type::kFocal) {
     *         const auto dCenter = (c0 - c1).length();
     *         if (!focalData.set(r0 / dCenter, r1 / dCenter, &gradientMatrix)) {
     *             return nullptr;
     *         }
     *     }
     *
     *     sk_sp<SkShader> s = sk_make_sp<SkConicalGradient>(
     *             c0, r0, c1, r1, desc, gradientType, gradientMatrix, focalData);
     *     return s->makeWithLocalMatrix(localMatrix ? *localMatrix : SkMatrix::I());
     * }
     * ```
     */
    public fun create(
      start: SkPoint,
      startRadius: SkScalar,
      end: SkPoint,
      endRadius: SkScalar,
      desc: SkGradient,
      localMatrix: SkMatrix?,
    ): Int {
      TODO("Implement create")
    }
  }
}
