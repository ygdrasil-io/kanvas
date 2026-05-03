package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.effects.SkColorFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkColorFilterShader : public SkShaderBase {
 * public:
 *     static sk_sp<SkShader> Make(sk_sp<SkShader> shader, float alpha, sk_sp<SkColorFilter> filter);
 *
 *     ShaderType type() const override { return ShaderType::kColorFilter; }
 *
 *     sk_sp<SkShader> shader() const { return fShader; }
 *     sk_sp<SkColorFilterBase> filter() const { return fFilter; }
 *     float alpha() const { return fAlpha; }
 *
 * private:
 *     SkColorFilterShader(sk_sp<SkShader> shader, float alpha, sk_sp<SkColorFilter> filter);
 *
 *     bool isOpaque() const override;
 *     void flatten(SkWriteBuffer&) const override;
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 *     SK_FLATTENABLE_HOOKS(SkColorFilterShader)
 *
 *     sk_sp<SkShader>          fShader;
 *     sk_sp<SkColorFilterBase> fFilter;
 *     float fAlpha;
 * }
 * ```
 */
public open class SkColorFilterShader public constructor(
  shader: SkSp<SkShader>,
  alpha: Float,
  filter: SkSp<SkColorFilter>,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader>          fShader
   * ```
   */
  private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilterBase> fFilter
   * ```
   */
  private var fFilter: SkSp<SkColorFilterBase> = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * float fAlpha
   * ```
   */
  private var fAlpha: Float = TODO("Initialize fAlpha")

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kColorFilter; }
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
   * sk_sp<SkColorFilterBase> filter() const { return fFilter; }
   * ```
   */
  public fun filter(): SkSp<SkColorFilterBase> {
    TODO("Implement filter")
  }

  /**
   * C++ original:
   * ```cpp
   * float alpha() const { return fAlpha; }
   * ```
   */
  public fun alpha(): Float {
    TODO("Implement alpha")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilterShader::isOpaque() const {
   *     return fShader->isOpaque() && fAlpha == 1.0f && as_CFB(fFilter)->isAlphaUnchanged();
   * }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorFilterShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fShader.get());
   *     SkASSERT(fAlpha == 1.0f);  // Not exposed in public API SkShader::makeWithColorFilter().
   *     buffer.writeFlattenable(fFilter.get());
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorFilterShader::appendStages(const SkStageRec& rec,
   *                                        const SkShaders::MatrixRec& mRec) const {
   *     if (!as_SB(fShader)->appendStages(rec, mRec)) {
   *         return false;
   *     }
   *     if (fAlpha != 1.0f) {
   *         rec.fPipeline->append(SkRasterPipelineOp::scale_1_float, rec.fAlloc->make<float>(fAlpha));
   *     }
   *     if (!fFilter->appendStages(rec, fAlpha == 1.0f && fShader->isOpaque())) {
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkColorFilterShader::CreateProc(SkReadBuffer& buffer) {
   *     auto shader = buffer.readShader();
   *     auto filter = buffer.readColorFilter();
   *     return Make(std::move(shader), 1.0f, std::move(filter));
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
     * sk_sp<SkShader> SkColorFilterShader::Make(sk_sp<SkShader> shader,
     *                                           float alpha,
     *                                           sk_sp<SkColorFilter> filter) {
     *     if (!shader) {
     *         return nullptr;
     *     } else if (!filter) {
     *         return shader;
     *     } else {
     *         return sk_sp(new SkColorFilterShader(std::move(shader), alpha, std::move(filter)));
     *     }
     * }
     * ```
     */
    public fun make(
      shader: SkSp<SkShader>,
      alpha: Float,
      filter: SkSp<SkColorFilter>,
    ): SkSp<SkShader> {
      TODO("Implement make")
    }
  }
}
