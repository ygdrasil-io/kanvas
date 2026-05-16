package org.skia.effects

import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import org.skia.`external`.Clamp
import org.skia.core.SkStageRec
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkMatrixColorFilter final : public SkColorFilterBase {
 * public:
 *     enum class Domain : uint8_t { kRGBA, kHSLA };
 *     using Clamp = SkColorFilters::Clamp;
 *
 *     explicit SkMatrixColorFilter(const float array[20], Domain, Clamp);
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     bool onIsAlphaUnchanged() const override { return fAlphaIsUnchanged; }
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kMatrix; }
 *
 *     Domain domain() const { return fDomain; }
 *     SkColorFilters::Clamp clamp() const { return fClamp; }
 *     const float* matrix() const { return fMatrix; }
 *
 * private:
 *     friend void ::SkRegisterMatrixColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkMatrixColorFilter)
 *
 *     void flatten(SkWriteBuffer&) const override;
 *     bool onAsAColorMatrix(float matrix[20]) const override;
 *
 *     float fMatrix[20];
 *     bool fAlphaIsUnchanged;
 *     Domain fDomain;
 *     Clamp fClamp;
 * }
 * ```
 */
public class SkMatrixColorFilter public constructor(
  array: Float,
  param1: Domain,
  param2: Clamp,
) : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * float fMatrix[20]
   * ```
   */
  private var fMatrix: FloatArray = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * bool fAlphaIsUnchanged
   * ```
   */
  private var fAlphaIsUnchanged: Boolean = TODO("Initialize fAlphaIsUnchanged")

  /**
   * C++ original:
   * ```cpp
   * Domain fDomain
   * ```
   */
  private var fDomain: Domain = TODO("Initialize fDomain")

  /**
   * C++ original:
   * ```cpp
   * Clamp fClamp
   * ```
   */
  private var fClamp: Int = TODO("Initialize fClamp")

  /**
   * C++ original:
   * ```cpp
   * explicit SkMatrixColorFilter(const float array[20], Domain, Clamp)
   * ```
   */
  public constructor(
    array: FloatArray,
    domain: Domain,
    clamp: Clamp,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrixColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     const bool willStayOpaque = shaderIsOpaque && fAlphaIsUnchanged,
   *                hsla = fDomain == Domain::kHSLA,
   *                clamp = fClamp == Clamp::kYes;
   *
   *     SkRasterPipeline* p = rec.fPipeline;
   *     if (!shaderIsOpaque) {
   *         p->append(SkRasterPipelineOp::unpremul);
   *     }
   *     if (hsla) {
   *         p->append(SkRasterPipelineOp::rgb_to_hsl);
   *     }
   *     if (true) {
   *         p->append(SkRasterPipelineOp::matrix_4x5, fMatrix);
   *     }
   *     if (hsla) {
   *         p->append(SkRasterPipelineOp::hsl_to_rgb);
   *     }
   *     if (clamp) {
   *         p->append(SkRasterPipelineOp::clamp_01);
   *     } else {
   *         // We still need to clamp alpha, regardless
   *         p->append(SkRasterPipelineOp::clamp_a_01);
   *     }
   *     if (!willStayOpaque) {
   *         p->append(SkRasterPipelineOp::premul);
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onIsAlphaUnchanged() const override { return fAlphaIsUnchanged; }
   * ```
   */
  public override fun onIsAlphaUnchanged(): Boolean {
    TODO("Implement onIsAlphaUnchanged")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kMatrix; }
   * ```
   */
  public override fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Domain domain() const { return fDomain; }
   * ```
   */
  public fun domain(): Domain {
    TODO("Implement domain")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorFilters::Clamp clamp() const { return fClamp; }
   * ```
   */
  public fun clamp(): Int {
    TODO("Implement clamp")
  }

  /**
   * C++ original:
   * ```cpp
   * const float* matrix() const { return fMatrix; }
   * ```
   */
  public fun matrix(): Float {
    TODO("Implement matrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMatrixColorFilter::onAsAColorMatrix(float matrix[20]) const {
   *     if (matrix) {
   *         memcpy(matrix, fMatrix, 20 * sizeof(float));
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
    TODO("Implement onAsAColorMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrixColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     SkASSERT(sizeof(fMatrix) / sizeof(float) == 20);
   *     buffer.writeScalarArray(fMatrix);
   *
   *     // RGBA flag
   *     buffer.writeBool(fDomain == Domain::kRGBA);
   *     buffer.writeBool(fClamp == Clamp::kYes);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkMatrixColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     float matrix[20];
   *     if (!buffer.readScalarArray(matrix)) {
   *         return nullptr;
   *     }
   *
   *     auto is_rgba = buffer.readBool();
   *     Clamp clamp = buffer.isVersionLT(SkPicturePriv::kUnclampedMatrixColorFilter)
   *                           ? Clamp::kYes
   *                           : (buffer.readBool() ? Clamp::kYes : Clamp::kNo);
   *     // clamp option is ignored for HSL-domain filters
   *     return is_rgba ? SkColorFilters::Matrix(matrix, clamp) : SkColorFilters::HSLAMatrix(matrix);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public enum class Domain {
    kRGBA,
    kHSLA,
  }
}
