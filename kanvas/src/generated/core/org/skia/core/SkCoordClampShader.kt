package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkCoordClampShader final : public SkShaderBase {
 * public:
 *     SkCoordClampShader(sk_sp<SkShader> shader, const SkRect& subset)
 *             : fShader(std::move(shader)), fSubset(subset) {}
 *
 *     ShaderType type() const override { return ShaderType::kCoordClamp; }
 *
 *     sk_sp<SkShader> shader() const { return fShader; }
 *     SkRect subset() const { return fSubset; }
 *
 * protected:
 *     SkCoordClampShader(SkReadBuffer&);
 *     void flatten(SkWriteBuffer&) const override;
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 * private:
 *     friend void ::SkRegisterCoordClampShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkCoordClampShader)
 *
 *     sk_sp<SkShader> fShader;
 *     SkRect fSubset;
 * }
 * ```
 */
public class SkCoordClampShader public constructor(
  shader: SkSp<SkShader>,
  subset: SkRect,
) : SkShaderBase() {
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
   * SkRect fSubset
   * ```
   */
  private var fSubset: SkRect = TODO("Initialize fSubset")

  /**
   * C++ original:
   * ```cpp
   * SkCoordClampShader(sk_sp<SkShader> shader, const SkRect& subset)
   *             : fShader(std::move(shader)), fSubset(subset) {}
   * ```
   */
  public constructor(param0: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kCoordClamp; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> shader() const { return fShader; }
   * ```
   */
  public fun shader(): SkSp<SkShader> {
    TODO("Implement shader")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect subset() const { return fSubset; }
   * ```
   */
  public fun subset(): SkRect {
    TODO("Implement subset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoordClampShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fShader.get());
   *     buffer.writeRect(fSubset);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoordClampShader::appendStages(const SkStageRec& rec,
   *                                       const SkShaders::MatrixRec& mRec) const {
   *     std::optional<SkShaders::MatrixRec> childMRec = mRec.apply(rec);
   *     if (!childMRec.has_value()) {
   *         return false;
   *     }
   *     // Strictly speaking, childMRec's total matrix is not valid. It is only valid inside the subset
   *     // rectangle. However, we don't mark it as such because we want the "total matrix is valid"
   *     // behavior in SkImageShader for filtering.
   *     auto clampCtx = rec.fAlloc->make<SkRasterPipelineContexts::CoordClampCtx>();
   *     *clampCtx = {fSubset.fLeft, fSubset.fTop, fSubset.fRight, fSubset.fBottom};
   *     rec.fPipeline->append(SkRasterPipelineOp::clamp_x_and_y, clampCtx);
   *     return as_SB(fShader)->appendStages(rec, *childMRec);
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkCoordClampShader::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkShader> shader(buffer.readShader());
   *     SkRect subset = buffer.readRect();
   *     if (!buffer.validate(SkToBool(shader))) {
   *         return nullptr;
   *     }
   *     return SkShaders::CoordClamp(std::move(shader), subset);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
