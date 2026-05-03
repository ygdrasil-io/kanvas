package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkWorkingColorSpaceShader final : public SkShaderBase {
 * public:
 *     // NOTE: `workInUnpremul` is not exposed to the public API yet as many shader implementations
 *     // across CPU, Ganesh, and Graphite have to be updated to convert to unpremul.
 *     static sk_sp<SkShader> Make(sk_sp<SkShader> shader,
 *                                 sk_sp<SkColorSpace> inputCS,
 *                                 sk_sp<SkColorSpace> outputCS,
 *                                 bool workInUnpremul);
 *
 *     ShaderType type() const override { return ShaderType::kWorkingColorSpace; }
 *
 *     sk_sp<SkShader> shader() const { return fShader; }
 *
 *     std::tuple</*inputCS=*/sk_sp<SkColorSpace>,
 *                /*outputCS=*/sk_sp<SkColorSpace>,
 *                /*workingAT=*/SkAlphaType>
 *     workingSpace(sk_sp<SkColorSpace> dstCS, SkAlphaType dstAT) const {
 *         sk_sp<SkColorSpace> inputSpace  = fInputSpace  ? fInputSpace  : dstCS;
 *         sk_sp<SkColorSpace> outputSpace = fOutputSpace ? fOutputSpace : inputSpace;
 *         return {inputSpace, outputSpace, fWorkInUnpremul ? kUnpremul_SkAlphaType : dstAT};
 *     }
 *
 * private:
 *     SkWorkingColorSpaceShader(sk_sp<SkShader> shader,
 *                               sk_sp<SkColorSpace> inputCS,
 *                               sk_sp<SkColorSpace> outputCS,
 *                               bool workInUnpremul)
 *             : fShader(std::move(shader))
 *             , fInputSpace(std::move(inputCS))
 *             , fOutputSpace(std::move(outputCS))
 *             , fWorkInUnpremul(workInUnpremul) {
 *         SkASSERT(fShader);
 *         SkASSERT(fInputSpace || fOutputSpace || fWorkInUnpremul);
 *     }
 *
 *     bool appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const override;
 *
 *     friend void ::SkRegisterWorkingColorSpaceShaderFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkWorkingColorSpaceShader)
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     sk_sp<SkShader> fShader;
 *     sk_sp<SkColorSpace> fInputSpace;
 *     sk_sp<SkColorSpace> fOutputSpace;
 *     bool fWorkInUnpremul;
 * }
 * ```
 */
public class SkWorkingColorSpaceShader public constructor(
  shader: SkSp<SkShader>,
  inputCS: SkSp<SkColorSpace>,
  outputCS: SkSp<SkColorSpace>,
  workInUnpremul: Boolean,
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
   * sk_sp<SkColorSpace> fInputSpace
   * ```
   */
  private var fInputSpace: SkSp<SkColorSpace> = TODO("Initialize fInputSpace")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fOutputSpace
   * ```
   */
  private var fOutputSpace: SkSp<SkColorSpace> = TODO("Initialize fOutputSpace")

  /**
   * C++ original:
   * ```cpp
   * bool fWorkInUnpremul
   * ```
   */
  private var fWorkInUnpremul: Boolean = TODO("Initialize fWorkInUnpremul")

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kWorkingColorSpace; }
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
   * std::tuple</*inputCS=*/sk_sp<SkColorSpace>,
   *                /*outputCS=*/sk_sp<SkColorSpace>,
   *                /*workingAT=*/SkAlphaType>
   *     workingSpace(sk_sp<SkColorSpace> dstCS, SkAlphaType dstAT) const {
   *         sk_sp<SkColorSpace> inputSpace  = fInputSpace  ? fInputSpace  : dstCS;
   *         sk_sp<SkColorSpace> outputSpace = fOutputSpace ? fOutputSpace : inputSpace;
   *         return {inputSpace, outputSpace, fWorkInUnpremul ? kUnpremul_SkAlphaType : dstAT};
   *     }
   * ```
   */
  public fun workingSpace(dstCS: SkSp<SkColorSpace>, dstAT: SkAlphaType): Int {
    TODO("Implement workingSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkWorkingColorSpaceShader::appendStages(const SkStageRec& rec,
   *                                              const SkShaders::MatrixRec& mRec) const {
   *     sk_sp<SkColorSpace> dstCS = sk_ref_sp(rec.fDstCS);
   *     if (!dstCS) {
   *         dstCS = SkColorSpace::MakeSRGB();
   *     }
   *
   *     // TODO(b/431253455): Should get the dstAT from `rec`
   *     const SkAlphaType dstAT = kPremul_SkAlphaType;
   *     auto [inputCS, outputCS, workingAT]  = this->workingSpace(dstCS, dstAT);
   *
   *     SkColorInfo dst    = {rec.fDstColorType, dstAT,     dstCS},
   *                 input  = {rec.fDstColorType, workingAT, inputCS},
   *                 output = {rec.fDstColorType, workingAT, outputCS};
   *
   *     const auto* dstToInput  = rec.fAlloc->make<SkColorSpaceXformSteps>(dst, input);
   *     const auto* outputToDst = rec.fAlloc->make<SkColorSpaceXformSteps>(output, dst);
   *     // NOTE: There is no inputToOutput steps to apply because it is assumed that the child shader
   *     // is responsible for such conversion (or input == output and it's a no-op).
   *
   *     // Alpha-only image shaders reference the paint color, which is already in the destination
   *     // color space. We need to transform it to the working space for consistency.
   *     SkColor4f paintColorInWorkingSpace = rec.fPaintColor.makeOpaque();
   *     dstToInput->apply(paintColorInWorkingSpace.vec());
   *
   *     // TODO(b/431253455): The working rec should have its alpha type set to `workingAT`
   *     SkStageRec workingRec = {rec.fPipeline,
   *                              rec.fAlloc,
   *                              rec.fDstColorType,
   *                              fInputSpace.get(),
   *                              paintColorInWorkingSpace,
   *                              rec.fSurfaceProps,
   *                              rec.fDstBounds};
   *
   *     if (!as_SB(fShader)->appendStages(workingRec, mRec)) {
   *         return false;
   *     }
   *
   *     outputToDst->apply(rec.fPipeline);
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
   * void SkWorkingColorSpaceShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeFlattenable(fShader.get());
   *     buffer.writeBool(fWorkInUnpremul);
   *
   *     buffer.writeBool(SkToBool(fInputSpace));
   *     if (fInputSpace) {
   *         buffer.writeDataAsByteArray(fInputSpace->serialize().get());
   *     }
   *
   *     buffer.writeBool(SkToBool(fOutputSpace));
   *     if (fOutputSpace) {
   *         buffer.writeDataAsByteArray(fOutputSpace->serialize().get());
   *     }
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkWorkingColorSpaceShader::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkShader> shader(buffer.readShader());
   *
   *     // If true, will not work in unpremul and assume inputSpace will be non-null and the outputSpace
   *     // will be null.
   *     const bool legacyWorkingCS = buffer.isVersionLT(SkPicturePriv::kWorkingColorSpaceOutput);
   *
   *     bool workInUnpremul = !legacyWorkingCS && buffer.readBool();
   *
   *     // The input/output spaces are allowed to be null, but if we think we have a non-null CS, then
   *     // it better be deserializable.
   *     sk_sp<SkColorSpace> inputSpace;
   *     if (legacyWorkingCS || buffer.readBool()) {
   *         auto data = buffer.readByteArrayAsData();
   *         if (!buffer.validate(data != nullptr)) {
   *             return nullptr;
   *         }
   *         inputSpace = SkColorSpace::Deserialize(data->data(), data->size());
   *         if (!buffer.validate(inputSpace != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     sk_sp<SkColorSpace> outputSpace;
   *     if (!legacyWorkingCS && buffer.readBool()) {
   *         auto data = buffer.readByteArrayAsData();
   *         if (!buffer.validate(data != nullptr)) {
   *             return nullptr;
   *         }
   *         outputSpace = SkColorSpace::Deserialize(data->data(), data->size());
   *         if (!buffer.validate(outputSpace != nullptr)) {
   *             return nullptr;
   *         }
   *     }
   *
   *     return Make(std::move(shader), std::move(inputSpace), std::move(outputSpace), workInUnpremul);
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
     * sk_sp<SkShader> SkWorkingColorSpaceShader::Make(sk_sp<SkShader> shader,
     *                                                 sk_sp<SkColorSpace> inputCS,
     *                                                 sk_sp<SkColorSpace> outputCS,
     *                                                 bool workInUnpremul) {
     *     if (!shader) {
     *         return nullptr;
     *     }
     *
     *     if (!inputCS && !outputCS && !workInUnpremul) {
     *         // A null input is the final dst CS, and a null output is the input CS, so if both are null
     *         // then there's no additional conversion for children and no additional conversion
     *         // applied to the shader's output.
     *         return shader;
     *     } else {
     *         // Otherwise there's some conversion that has to happen on the input side or the output side
     *         // that makes this not a no-op.
     *         return sk_sp(new SkWorkingColorSpaceShader(std::move(shader),
     *                                                    std::move(inputCS),
     *                                                    std::move(outputCS),
     *                                                    workInUnpremul));
     *     }
     * }
     * ```
     */
    public fun make(
      shader: SkSp<SkShader>,
      inputCS: SkSp<SkColorSpace>,
      outputCS: SkSp<SkColorSpace>,
      workInUnpremul: Boolean,
    ): SkSp<SkShader> {
      TODO("Implement make")
    }
  }
}
