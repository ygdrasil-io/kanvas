package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import org.skia.modules.Factory
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkTransformShader : public SkShaderBase {
 * public:
 *     explicit SkTransformShader(const SkShaderBase& shader, bool allowPerspective);
 *
 *     // Adds a pipestage to multiply the incoming coords in 'r' and 'g' by the matrix. The child
 *     // shader is called with no pending local matrix and the total transform as unknowable.
 *     bool appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const override;
 *
 *     // Change the matrix used by the generated SkRasterPipeline.
 *     bool update(const SkMatrix& matrix);
 *
 *     ShaderType type() const override { return ShaderType::kTransform; }
 *
 *     // These are never serialized/deserialized
 *     Factory getFactory() const override {
 *         SkDEBUGFAIL("SkTransformShader shouldn't be serialized.");
 *         return {};
 *     }
 *     const char* getTypeName() const override {
 *         SkDEBUGFAIL("SkTransformShader shouldn't be serialized.");
 *         return nullptr;
 *     }
 *
 *     bool isOpaque() const override { return fShader.isOpaque(); }
 *
 * private:
 *     const SkShaderBase& fShader;
 *     SkScalar fMatrixStorage[9];  // actual memory used by generated RP or VM
 *     bool fAllowPerspective;
 * }
 * ```
 */
public open class SkTransformShader public constructor(
  shader: SkShaderBase,
  allowPerspective: Boolean,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * const SkShaderBase& fShader
   * ```
   */
  private val fShader: SkShaderBase = TODO("Initialize fShader")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fMatrixStorage[9]
   * ```
   */
  private var fMatrixStorage: Array<SkScalar> = TODO("Initialize fMatrixStorage")

  /**
   * C++ original:
   * ```cpp
   * bool fAllowPerspective
   * ```
   */
  private var fAllowPerspective: Boolean = TODO("Initialize fAllowPerspective")

  /**
   * C++ original:
   * ```cpp
   * bool SkTransformShader::appendStages(const SkStageRec& rec,
   *                                      const SkShaders::MatrixRec& mRec) const {
   *     // We have to seed and apply any constant matrices before appending our matrix that may
   *     // mutate. We could try to add one matrix stage and then incorporate the parent matrix
   *     // with the variable matrix in each call to update(). However, in practice our callers
   *     // fold the CTM into the update() matrix and don't wrap the transform shader in local matrix
   *     // shaders so the call to apply below should just seed the coordinates. If this assert fires
   *     // it just indicates an optimization opportunity, not a correctness bug.
   *     SkASSERT(!mRec.hasPendingMatrix());
   *     std::optional<SkShaders::MatrixRec> childMRec = mRec.apply(rec);
   *     if (!childMRec.has_value()) {
   *         return false;
   *     }
   *     // The matrix we're about to insert gets updated between uses of the pipeline so our children
   *     // can't know the total transform when they add their stages. We don't even incorporate this
   *     // matrix into the SkShaders::MatrixRec at all.
   *     childMRec->markTotalMatrixInvalid();
   *
   *     auto type = fAllowPerspective ? SkRasterPipelineOp::matrix_perspective
   *                                   : SkRasterPipelineOp::matrix_2x3;
   *     rec.fPipeline->append(type, fMatrixStorage);
   *
   *     fShader.appendStages(rec, *childMRec);
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
   * bool SkTransformShader::update(const SkMatrix& matrix) {
   *     if (!fAllowPerspective && matrix.hasPerspective()) {
   *         return false;
   *     }
   *
   *     matrix.get9(fMatrixStorage);
   *     return true;
   * }
   * ```
   */
  public fun update(matrix: SkMatrix): Boolean {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kTransform; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override {
   *         SkDEBUGFAIL("SkTransformShader shouldn't be serialized.");
   *         return {};
   *     }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override {
   *         SkDEBUGFAIL("SkTransformShader shouldn't be serialized.");
   *         return nullptr;
   *     }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const override { return fShader.isOpaque(); }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }
}
