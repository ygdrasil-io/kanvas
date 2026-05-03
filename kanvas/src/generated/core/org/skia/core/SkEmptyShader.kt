package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkEmptyShader : public SkShaderBase {
 * public:
 *     SkEmptyShader() {}
 *
 * protected:
 *     void flatten(SkWriteBuffer& buffer) const override {
 *         // Do nothing.
 *         // We just don't want to fall through to SkShader::flatten(),
 *         // which will write data we don't care to serialize or decode.
 *     }
 *
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override {
 *         return false;
 *     }
 *
 *     ShaderType type() const override { return ShaderType::kEmpty; }
 *
 * private:
 *     friend void ::SkRegisterEmptyShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkEmptyShader)
 * }
 * ```
 */
public open class SkEmptyShader public constructor() : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * void flatten(SkWriteBuffer& buffer) const override {
   *         // Do nothing.
   *         // We just don't want to fall through to SkShader::flatten(),
   *         // which will write data we don't care to serialize or decode.
   *     }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override {
   *         return false;
   *     }
   * ```
   */
  protected override fun appendStages(param0: SkStageRec, param1: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kEmpty; }
   * ```
   */
  protected override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkEmptyShader::CreateProc(SkReadBuffer&) {
   *     return SkShaders::Empty();
   * }
   * ```
   */
  public fun createProc(param0: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
