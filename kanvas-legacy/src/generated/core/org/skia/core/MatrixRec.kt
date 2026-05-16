package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class MatrixRec {
 * public:
 *     MatrixRec() = default;
 *
 *     explicit MatrixRec(const SkMatrix& ctm);
 *
 *     /**
 *      * Returns a new MatrixRec that represents the existing total and pending matrix
 *      * pre-concat'ed with m.
 *      */
 *     [[nodiscard]] MatrixRec concat(const SkMatrix& m) const;
 *
 *     /**
 *      * Appends a mul by the inverse of the pending local matrix to the pipeline. 'postInv' is an
 *      * additional matrix to post-apply to the inverted pending matrix. If the pending matrix is
 *      * not invertible the std::optional result won't have a value and the pipeline will be
 *      * unmodified.
 *      */
 *     [[nodiscard]] std::optional<MatrixRec> apply(const SkStageRec& rec,
 *                                                  const SkMatrix& postInv = {}) const;
 *
 *     /**
 *      * FP matrices work differently than SkRasterPipeline. The starting coordinates provided to the
 *      * root SkShader's FP are already in local space. So we never apply the inverse CTM. This
 *      * returns the inverted pending local matrix with the provided postInv matrix applied after it.
 *      * If the pending local matrix cannot be inverted, the boolean is false.
 *      */
 *     std::tuple<SkMatrix, bool> applyForFragmentProcessor(const SkMatrix& postInv) const;
 *
 *     /**
 *      * A parent FP may need to create a FP for its child by calling
 *      * SkShaderBase::asFragmentProcessor() and then pass the result to the apply() above.
 *      * This comes up when the parent needs to ensure pending matrices are applied before the
 *      * child because the parent is going to manipulate the coordinates *after* any pending
 *      * matrix and pass the resulting coords to the child. This function gets a MatrixRec that
 *      * reflects the state after this MatrixRec has bee applied but it does not apply it!
 *      * Example:
 *      * auto childFP = fChild->asFragmentProcessor(args, mrec.applied());
 *      * childFP = MakeAWrappingFPThatModifiesChildsCoords(std::move(childFP));
 *      * auto [success, parentFP] = mrec.apply(std::move(childFP));
 *      */
 *     MatrixRec applied() const;
 *
 *     /** Call to indicate that the mapping from shader to device space is not known. */
 *     void markTotalMatrixInvalid() { fTotalMatrixIsValid = false; }
 *
 *     /** Marks the CTM as already applied; can avoid re-seeding the shader unnecessarily. */
 *     void markCTMApplied() { fCTMApplied = true; }
 *
 *     /**
 *      * Indicates whether the total matrix of a MatrixRec passed to a SkShader actually
 *      * represents the full transform between that shader's coordinate space and device space.
 *      */
 *     bool totalMatrixIsValid() const { return fTotalMatrixIsValid; }
 *
 *     /**
 *      * Gets the total transform from the current shader's space to device space. This may or
 *      * may not be valid. Shaders should avoid making decisions based on this matrix if
 *      * totalMatrixIsValid() is false.
 *      */
 *     SkMatrix totalMatrix() const { return SkMatrix::Concat(fCTM, fTotalLocalMatrix); }
 *
 *     /** Gets the inverse of totalMatrix(), if invertible. */
 *     std::optional<SkMatrix> totalInverse() const {
 *         return this->totalMatrix().invert();
 *     }
 *
 *     /** Is there a transform that has not yet been applied by a parent shader? */
 *     bool hasPendingMatrix() const {
 *         return (!fCTMApplied && !fCTM.isIdentity()) || !fPendingLocalMatrix.isIdentity();
 *     }
 *
 *     /** When generating raster pipeline, have the device coordinates been seeded? */
 *     bool rasterPipelineCoordsAreSeeded() const { return fCTMApplied; }
 *
 * private:
 *     MatrixRec(const SkMatrix& ctm,
 *               const SkMatrix& totalLocalMatrix,
 *               const SkMatrix& pendingLocalMatrix,
 *               bool totalIsValid,
 *               bool ctmApplied)
 *             : fCTM(ctm)
 *             , fTotalLocalMatrix(totalLocalMatrix)
 *             , fPendingLocalMatrix(pendingLocalMatrix)
 *             , fTotalMatrixIsValid(totalIsValid)
 *             , fCTMApplied(ctmApplied) {}
 *
 *     const SkMatrix fCTM;
 *
 *     // Concatenation of all local matrices, including those already applied.
 *     const SkMatrix fTotalLocalMatrix;
 *
 *     // The accumulated local matrices from walking down the shader hierarchy that have NOT yet
 *     // been incorporated into the SkRasterPipeline.
 *     const SkMatrix fPendingLocalMatrix;
 *
 *     bool fTotalMatrixIsValid = true;
 *
 *     // Tracks whether the CTM has already been applied (and in raster pipeline whether the
 *     // device coords have been seeded.)
 *     bool fCTMApplied = false;
 * }
 * ```
 */
public data class MatrixRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fCTM
   * ```
   */
  private val fCTM: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fTotalLocalMatrix
   * ```
   */
  private val fTotalLocalMatrix: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fPendingLocalMatrix
   * ```
   */
  private val fPendingLocalMatrix: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * bool fTotalMatrixIsValid = true
   * ```
   */
  private var fTotalMatrixIsValid: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fCTMApplied = false
   * ```
   */
  private var fCTMApplied: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * MatrixRec MatrixRec::concat(const SkMatrix& m) const {
   *     return {fCTM,
   *             SkShaderBase::ConcatLocalMatrices(fTotalLocalMatrix, m),
   *             SkShaderBase::ConcatLocalMatrices(fPendingLocalMatrix, m),
   *             fTotalMatrixIsValid,
   *             fCTMApplied};
   * }
   * ```
   */
  public fun concat(m: SkMatrix): MatrixRec {
    TODO("Implement concat")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<MatrixRec> MatrixRec::apply(const SkStageRec& rec, const SkMatrix& postInv) const {
   *     SkMatrix total = fPendingLocalMatrix;
   *     if (!fCTMApplied) {
   *         total = SkMatrix::Concat(fCTM, total);
   *     }
   *     if (auto inv = total.invert()) {
   *         total = SkMatrix::Concat(postInv, *inv);
   *     } else {
   *         return {};
   *     }
   *     if (!fCTMApplied) {
   *         rec.fPipeline->append(SkRasterPipelineOp::seed_shader);
   *     }
   *     // appendMatrix is a no-op if total worked out to identity.
   *     rec.fPipeline->appendMatrix(rec.fAlloc, total);
   *     return MatrixRec{fCTM,
   *                      fTotalLocalMatrix,
   *                      /*pendingLocalMatrix=*/SkMatrix::I(),
   *                      fTotalMatrixIsValid,
   *                      /*ctmApplied=*/true};
   * }
   * ```
   */
  public fun apply(rec: SkStageRec, postInv: SkMatrix): Int {
    TODO("Implement apply")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkMatrix, bool> MatrixRec::applyForFragmentProcessor(const SkMatrix& postInv) const {
   *     SkASSERT(!fCTMApplied);
   *     if (auto total = fPendingLocalMatrix.invert()) {
   *         return {SkMatrix::Concat(postInv, *total), true};
   *     } else {
   *         return {SkMatrix::I(), false};
   *     }
   * }
   * ```
   */
  public fun applyForFragmentProcessor(postInv: SkMatrix): Int {
    TODO("Implement applyForFragmentProcessor")
  }

  /**
   * C++ original:
   * ```cpp
   * MatrixRec MatrixRec::applied() const {
   *     // We mark the CTM as "not applied" because we *never* apply the CTM for FPs. Their starting
   *     // coords are local, not device, coords.
   *     return MatrixRec{fCTM,
   *                      fTotalLocalMatrix,
   *                      /*pendingLocalMatrix=*/SkMatrix::I(),
   *                      fTotalMatrixIsValid,
   *                      /*ctmApplied=*/false};
   * }
   * ```
   */
  public fun applied(): MatrixRec {
    TODO("Implement applied")
  }

  /**
   * C++ original:
   * ```cpp
   * void markTotalMatrixInvalid() { fTotalMatrixIsValid = false; }
   * ```
   */
  public fun markTotalMatrixInvalid() {
    TODO("Implement markTotalMatrixInvalid")
  }

  /**
   * C++ original:
   * ```cpp
   * void markCTMApplied() { fCTMApplied = true; }
   * ```
   */
  public fun markCTMApplied() {
    TODO("Implement markCTMApplied")
  }

  /**
   * C++ original:
   * ```cpp
   * bool totalMatrixIsValid() const { return fTotalMatrixIsValid; }
   * ```
   */
  public fun totalMatrixIsValid(): Boolean {
    TODO("Implement totalMatrixIsValid")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix totalMatrix() const { return SkMatrix::Concat(fCTM, fTotalLocalMatrix); }
   * ```
   */
  public fun totalMatrix(): SkMatrix {
    TODO("Implement totalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkMatrix> totalInverse() const {
   *         return this->totalMatrix().invert();
   *     }
   * ```
   */
  public fun totalInverse(): Int {
    TODO("Implement totalInverse")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasPendingMatrix() const {
   *         return (!fCTMApplied && !fCTM.isIdentity()) || !fPendingLocalMatrix.isIdentity();
   *     }
   * ```
   */
  public fun hasPendingMatrix(): Boolean {
    TODO("Implement hasPendingMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool rasterPipelineCoordsAreSeeded() const { return fCTMApplied; }
   * ```
   */
  public fun rasterPipelineCoordsAreSeeded(): Boolean {
    TODO("Implement rasterPipelineCoordsAreSeeded")
  }
}
