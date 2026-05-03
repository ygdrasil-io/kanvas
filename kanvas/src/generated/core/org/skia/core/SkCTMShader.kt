package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.tests.ShaderType
import undefined.GradientInfo
import undefined.GradientType
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkCTMShader final : public SkShaderBase {
 * public:
 *     SkCTMShader(sk_sp<SkShader> proxy, const SkMatrix& ctm);
 *
 *     bool isOpaque() const override { return fProxyShader->isOpaque(); }
 *
 *     bool isConstant(SkColor4f* color = nullptr) const override;
 *     GradientType asGradient(GradientInfo* info, SkMatrix* localMatrix) const override;
 *
 *     ShaderType type() const override { return ShaderType::kCTM; }
 *
 *     const SkMatrix& ctm() const { return fCTM; }
 *     sk_sp<SkShader> proxyShader() const { return fProxyShader; }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override { SkASSERT(false); }
 *
 *     bool appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkCTMShader)
 *
 *     sk_sp<SkShader> fProxyShader;
 *     SkMatrix fCTM;
 * }
 * ```
 */
public class SkCTMShader public constructor(
  proxy: SkSp<SkShader>,
  ctm: SkMatrix,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fProxyShader
   * ```
   */
  private var fProxyShader: SkSp<SkShader> = TODO("Initialize fProxyShader")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix fCTM
   * ```
   */
  private var fCTM: SkMatrix = TODO("Initialize fCTM")

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const override { return fProxyShader->isOpaque(); }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCTMShader::isConstant(SkColor4f* color) const {
   *     return as_SB(fProxyShader)->isConstant(color);
   * }
   * ```
   */
  public override fun isConstant(color: SkColor4f? = TODO()): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkCTMShader::asGradient(GradientInfo* info,
   *                                                    SkMatrix* localMatrix) const {
   *     return as_SB(fProxyShader)->asGradient(info, localMatrix);
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, localMatrix: SkMatrix?): GradientType {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kCTM; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& ctm() const { return fCTM; }
   * ```
   */
  public fun ctm(): SkMatrix {
    TODO("Implement ctm")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> proxyShader() const { return fProxyShader; }
   * ```
   */
  public fun proxyShader(): SkSp<SkShader> {
    TODO("Implement proxyShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer&) const override { SkASSERT(false); }
   * ```
   */
  protected override fun flatten(param0: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCTMShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const {
   *     return as_SB(fProxyShader)->appendRootStages(rec, fCTM);
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, param1: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkCTMShader::CreateProc(SkReadBuffer& buffer) {
   *     SkASSERT(false);
   *     return nullptr;
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
