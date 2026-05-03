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
 * class SkBlendShader final : public SkShaderBase {
 * public:
 *     SkBlendShader(SkBlendMode mode, sk_sp<SkShader> dst, sk_sp<SkShader> src)
 *             : fDst(std::move(dst)), fSrc(std::move(src)), fMode(mode) {}
 *
 *     ShaderType type() const override { return ShaderType::kBlend; }
 *
 *     sk_sp<SkShader> dst() const { return fDst; }
 *     sk_sp<SkShader> src() const { return fSrc; }
 *     SkBlendMode mode() const { return fMode; }
 *
 * protected:
 *     SkBlendShader(SkReadBuffer&);
 *     void flatten(SkWriteBuffer&) const override;
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 * private:
 *     friend void ::SkRegisterBlendShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkBlendShader)
 *
 *     sk_sp<SkShader> fDst;
 *     sk_sp<SkShader> fSrc;
 *     SkBlendMode fMode;
 * }
 * ```
 */
public class SkBlendShader public constructor(
  mode: SkBlendMode,
  dst: SkSp<SkShader>,
  src: SkSp<SkShader>,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fDst
   * ```
   */
  private var fDst: SkSp<SkShader> = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fSrc
   * ```
   */
  private var fSrc: SkSp<SkShader> = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fMode
   * ```
   */
  private var fMode: SkBlendMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SkBlendShader(SkBlendMode mode, sk_sp<SkShader> dst, sk_sp<SkShader> src)
   *             : fDst(std::move(dst)), fSrc(std::move(src)), fMode(mode) {}
   * ```
   */
  public constructor(param0: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kBlend; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> dst() const { return fDst; }
   * ```
   */
  public fun dst(): SkSp<SkShader> {
    TODO("Implement dst")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> src() const { return fSrc; }
   * ```
   */
  public fun src(): SkSp<SkShader> {
    TODO("Implement src")
  }

  /**
   * C++ original:
   * ```cpp
   * SkBlendMode mode() const { return fMode; }
   * ```
   */
  public fun mode(): SkBlendMode {
    TODO("Implement mode")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlendShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fDst.get());
   *     buffer.writeFlattenable(fSrc.get());
   *     buffer.write32((int)fMode);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkBlendShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec& mRec) const {
   *     float* res0 = append_two_shaders(rec, mRec, fDst.get(), fSrc.get());
   *     if (!res0) {
   *         return false;
   *     }
   *
   *     rec.fPipeline->append(SkRasterPipelineOp::load_dst, res0);
   *     SkBlendMode_AppendStages(fMode, rec.fPipeline);
   *     return true;
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlendShader::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkShader> dst(buffer.readShader());
   *     sk_sp<SkShader> src(buffer.readShader());
   *     if (!buffer.validate(dst && src)) {
   *         return nullptr;
   *     }
   *
   *     unsigned mode = buffer.read32();
   *
   *     if (mode == kCustom_SkBlendMode) {
   *         sk_sp<SkBlender> blender = buffer.readBlender();
   *         if (buffer.validate(blender != nullptr)) {
   *             return SkShaders::Blend(std::move(blender), std::move(dst), std::move(src));
   *         }
   *     } else {
   *         if (buffer.validate(mode <= (unsigned)SkBlendMode::kLastMode)) {
   *             return SkShaders::Blend(static_cast<SkBlendMode>(mode), std::move(dst), std::move(src));
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
