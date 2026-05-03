package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.modules.Factory
import org.skia.tests.ShaderType

/**
 * C++ original:
 * ```cpp
 * class SkTriColorShader : public SkShaderBase {
 * public:
 *     SkTriColorShader(bool isOpaque, bool usePersp) : fIsOpaque(isOpaque), fUsePersp(usePersp) {}
 *
 *     ShaderType type() const override { return ShaderType::kTriColor; }
 *
 *     // This gets called for each triangle, without re-calling appendStages.
 *     bool update(const SkMatrix& ctmInv,
 *                 const SkPoint pts[],
 *                 const SkPMColor4f colors[],
 *                 int index0,
 *                 int index1,
 *                 int index2);
 *
 * protected:
 *     bool appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const override;
 *
 * private:
 *     bool isOpaque() const override { return fIsOpaque; }
 *     // For serialization.  This will never be called.
 *     Factory getFactory() const override { return nullptr; }
 *     const char* getTypeName() const override { return nullptr; }
 *
 *     struct Matrix43 {
 *         float fMat[12];  // column major
 *
 *         // Pass a by value, so we don't have to worry about aliasing with this
 *         void setConcat(const Matrix43 a, const SkMatrix& b) {
 *             SkASSERT(!b.hasPerspective());
 *
 *             fMat[0] = a.dot(0, b.getScaleX(), b.getSkewY());
 *             fMat[1] = a.dot(1, b.getScaleX(), b.getSkewY());
 *             fMat[2] = a.dot(2, b.getScaleX(), b.getSkewY());
 *             fMat[3] = a.dot(3, b.getScaleX(), b.getSkewY());
 *
 *             fMat[4] = a.dot(0, b.getSkewX(), b.getScaleY());
 *             fMat[5] = a.dot(1, b.getSkewX(), b.getScaleY());
 *             fMat[6] = a.dot(2, b.getSkewX(), b.getScaleY());
 *             fMat[7] = a.dot(3, b.getSkewX(), b.getScaleY());
 *
 *             fMat[8] = a.dot(0, b.getTranslateX(), b.getTranslateY()) + a.fMat[8];
 *             fMat[9] = a.dot(1, b.getTranslateX(), b.getTranslateY()) + a.fMat[9];
 *             fMat[10] = a.dot(2, b.getTranslateX(), b.getTranslateY()) + a.fMat[10];
 *             fMat[11] = a.dot(3, b.getTranslateX(), b.getTranslateY()) + a.fMat[11];
 *         }
 *
 *     private:
 *         float dot(int index, float x, float y) const {
 *             return fMat[index + 0] * x + fMat[index + 4] * y;
 *         }
 *     };
 *
 *     // If fUsePersp, we need both of these matrices,
 *     // otherwise we can combine them, and only use fM43
 *
 *     Matrix43 fM43;
 *     SkMatrix fM33;
 *     const bool fIsOpaque;
 *     const bool fUsePersp;  // controls our stages, and what we do in update()
 * }
 * ```
 */
public open class SkTriColorShader public constructor(
  isOpaque: Boolean,
  usePersp: Boolean,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * Matrix43 fM43
   * ```
   */
  private var fM43: Matrix43 = TODO("Initialize fM43")

  /**
   * C++ original:
   * ```cpp
   * SkMatrix fM33
   * ```
   */
  private var fM33: SkMatrix = TODO("Initialize fM33")

  /**
   * C++ original:
   * ```cpp
   * const bool fIsOpaque
   * ```
   */
  private val fIsOpaque: Boolean = TODO("Initialize fIsOpaque")

  /**
   * C++ original:
   * ```cpp
   * const bool fUsePersp
   * ```
   */
  private val fUsePersp: Boolean = TODO("Initialize fUsePersp")

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kTriColor; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTriColorShader::update(const SkMatrix& ctmInv,
   *                               const SkPoint pts[],
   *                               const SkPMColor4f colors[],
   *                               int index0,
   *                               int index1,
   *                               int index2) {
   *     SkMatrix m, im;
   *     m.reset();
   *     m.set(0, pts[index1].fX - pts[index0].fX);
   *     m.set(1, pts[index2].fX - pts[index0].fX);
   *     m.set(2, pts[index0].fX);
   *     m.set(3, pts[index1].fY - pts[index0].fY);
   *     m.set(4, pts[index2].fY - pts[index0].fY);
   *     m.set(5, pts[index0].fY);
   *     if (!m.invert(&im)) {
   *         return false;
   *     }
   *
   *     fM33.setConcat(im, ctmInv);
   *
   *     auto c0 = skvx::float4::Load(colors[index0].vec()),
   *          c1 = skvx::float4::Load(colors[index1].vec()),
   *          c2 = skvx::float4::Load(colors[index2].vec());
   *
   *     (c1 - c0).store(&fM43.fMat[0]);
   *     (c2 - c0).store(&fM43.fMat[4]);
   *     c0.store(&fM43.fMat[8]);
   *
   *     if (!fUsePersp) {
   *         fM43.setConcat(fM43, fM33);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun update(
    ctmInv: SkMatrix,
    pts: Array<SkPoint>,
    colors: Array<SkPMColor4f>,
    index0: Int,
    index1: Int,
    index2: Int,
  ): Boolean {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTriColorShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec&) const {
   *     rec.fPipeline->append(SkRasterPipelineOp::seed_shader);
   *     if (fUsePersp) {
   *         rec.fPipeline->append(SkRasterPipelineOp::matrix_perspective, &fM33);
   *     }
   *     rec.fPipeline->append(SkRasterPipelineOp::matrix_4x3, &fM43);
   *     return true;
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, param1: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const override { return fIsOpaque; }
   * ```
   */
  public override fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * Factory getFactory() const override { return nullptr; }
   * ```
   */
  public override fun getFactory(): Factory {
    TODO("Implement getFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getTypeName() const override { return nullptr; }
   * ```
   */
  public override fun getTypeName(): Char {
    TODO("Implement getTypeName")
  }

  public data class Matrix43 public constructor(
    public var fMat: FloatArray,
  ) {
    public fun setConcat(a: undefined.Matrix43, b: SkMatrix) {
      TODO("Implement setConcat")
    }

    private fun dot(
      index: Int,
      x: Float,
      y: Float,
    ): Float {
      TODO("Implement dot")
    }
  }
}
