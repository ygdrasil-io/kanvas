package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.memory.SkArenaAlloc
import org.skia.tests.ShaderType
import undefined.ContextRec
import undefined.GradientInfo
import undefined.GradientType
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SkLocalMatrixShader final : public SkShaderBase {
 * public:
 *     template <typename T, typename... Args>
 *     static std::enable_if_t<std::is_base_of_v<SkShader, T>, sk_sp<SkShader>>
 *     MakeWrapped(const SkMatrix* localMatrix, Args&&... args) {
 *         auto t = sk_make_sp<T>(std::forward<Args>(args)...);
 *         if (localMatrix) {
 *             return t->makeWithLocalMatrix(*localMatrix);
 *         }
 *         return t;
 *     }
 *
 *     SkLocalMatrixShader(sk_sp<SkShader> wrapped, const SkMatrix& localMatrix)
 *             : fLocalMatrix(localMatrix), fWrappedShader(std::move(wrapped)) {}
 *
 *     bool isOpaque() const override { return as_SB(fWrappedShader)->isOpaque(); }
 *
 *     bool isConstant(SkColor4f* color = nullptr) const override;
 *     GradientType asGradient(GradientInfo* info, SkMatrix* localMatrix) const override;
 *     ShaderType type() const override { return ShaderType::kLocalMatrix; }
 *
 *     sk_sp<SkShader> makeAsALocalMatrixShader(SkMatrix* localMatrix) const override {
 *         if (localMatrix) {
 *             *localMatrix = fLocalMatrix;
 *         }
 *         return fWrappedShader;
 *     }
 *
 *     const SkMatrix& localMatrix() const { return fLocalMatrix; }
 *     sk_sp<SkShader> wrappedShader() const { return fWrappedShader; }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * #ifdef SK_ENABLE_LEGACY_SHADERCONTEXT
 *     Context* onMakeContext(const ContextRec&, SkArenaAlloc*) const override;
 * #endif
 *
 *     SkImage* onIsAImage(SkMatrix* matrix, SkTileMode* mode) const override;
 *
 *     bool onAsLuminanceColor(SkColor4f*) const override;
 *
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkLocalMatrixShader)
 *
 *     SkMatrix fLocalMatrix;
 *     sk_sp<SkShader> fWrappedShader;
 * }
 * ```
 */
public class SkLocalMatrixShader : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * SkMatrix fLocalMatrix
   * ```
   */
  private var fLocalMatrix: SkMatrix = TODO("Initialize fLocalMatrix")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> fWrappedShader
   * ```
   */
  private var fWrappedShader: SkSp<SkShader> = TODO("Initialize fWrappedShader")

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::GradientType SkLocalMatrixShader::asGradient(GradientInfo* info,
   *                                                            SkMatrix* localMatrix) const {
   *     GradientType type = as_SB(fWrappedShader)->asGradient(info, localMatrix);
   *     if (type != SkShaderBase::GradientType::kNone && localMatrix) {
   *         *localMatrix = ConcatLocalMatrices(fLocalMatrix, *localMatrix);
   *     }
   *     return type;
   * }
   * ```
   */
  public override fun asGradient(info: GradientInfo?, localMatrix: SkMatrix?): GradientType {
    TODO("Implement asGradient")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kLocalMatrix; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeAsALocalMatrixShader(SkMatrix* localMatrix) const override {
   *         if (localMatrix) {
   *             *localMatrix = fLocalMatrix;
   *         }
   *         return fWrappedShader;
   *     }
   * ```
   */
  public override fun makeAsALocalMatrixShader(localMatrix: SkMatrix?): SkSp<SkShader> {
    TODO("Implement makeAsALocalMatrixShader")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& localMatrix() const { return fLocalMatrix; }
   * ```
   */
  public fun localMatrix(): SkMatrix {
    TODO("Implement localMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> wrappedShader() const { return fWrappedShader; }
   * ```
   */
  public fun wrappedShader(): SkSp<SkShader> {
    TODO("Implement wrappedShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkLocalMatrixShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeMatrix(fLocalMatrix);
   *     buffer.writeFlattenable(fWrappedShader.get());
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::Context* SkLocalMatrixShader::onMakeContext(const ContextRec& rec,
   *                                                           SkArenaAlloc* alloc) const {
   *     return as_SB(fWrappedShader)->makeContext(ContextRec::Concat(rec, fLocalMatrix), alloc);
   * }
   * ```
   */
  protected override fun onMakeContext(rec: ContextRec, alloc: SkArenaAlloc?): Context {
    TODO("Implement onMakeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImage* SkLocalMatrixShader::onIsAImage(SkMatrix* outMatrix, SkTileMode* mode) const {
   *     SkMatrix imageMatrix;
   *     SkImage* image = fWrappedShader->isAImage(&imageMatrix, mode);
   *     if (image && outMatrix) {
   *         *outMatrix = ConcatLocalMatrices(fLocalMatrix, imageMatrix);
   *     }
   *
   *     return image;
   * }
   * ```
   */
  protected override fun onIsAImage(matrix: SkMatrix?, mode: SkTileMode?): SkImage {
    TODO("Implement onIsAImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkLocalMatrixShader::onAsLuminanceColor(SkColor4f* color) const {
   *     return as_SB(fWrappedShader)->asLuminanceColor(color);
   * }
   * ```
   */
  protected override fun onAsLuminanceColor(color: SkColor4f?): Boolean {
    TODO("Implement onAsLuminanceColor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkLocalMatrixShader::appendStages(const SkStageRec& rec,
   *                                        const SkShaders::MatrixRec& mRec) const {
   *     return as_SB(fWrappedShader)->appendStages(rec, mRec.concat(fLocalMatrix));
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkLocalMatrixShader::isConstant(SkColor4f* color) const {
   *     return as_SB(fWrappedShader)->isConstant(color);
   * }
   * ```
   */
  public override fun isConstant(color: SkColor4f?): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkLocalMatrixShader::CreateProc(SkReadBuffer& buffer) {
   *     SkMatrix lm;
   *     buffer.readMatrix(&lm);
   *     auto baseShader(buffer.readShader());
   *     if (!baseShader) {
   *         return nullptr;
   *     }
   *     return baseShader->makeWithLocalMatrix(lm);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
