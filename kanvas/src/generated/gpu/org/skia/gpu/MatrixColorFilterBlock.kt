package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct MatrixColorFilterBlock {
 *     struct MatrixColorFilterData {
 *         MatrixColorFilterData(const float matrix[20], bool inHSLA, bool clamp)
 *                 : fMatrix(matrix[ 0], matrix[ 1], matrix[ 2], matrix[ 3],
 *                           matrix[ 5], matrix[ 6], matrix[ 7], matrix[ 8],
 *                           matrix[10], matrix[11], matrix[12], matrix[13],
 *                           matrix[15], matrix[16], matrix[17], matrix[18])
 *                 , fTranslate{matrix[4], matrix[9], matrix[14], matrix[19]}
 *                 , fInHSLA(inHSLA)
 *                 , fClamp(clamp) {
 *         }
 *
 *         SkM44 fMatrix;
 *         SkV4  fTranslate;
 *         bool  fInHSLA;
 *         bool  fClamp;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const MatrixColorFilterData&);
 * }
 * ```
 */
public open class MatrixColorFilterBlock {
  public data class MatrixColorFilterData public constructor(
    public var fMatrix: Int,
    public var fTranslate: Int,
    public var fInHSLA: Boolean,
    public var fClamp: Boolean,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void MatrixColorFilterBlock::AddBlock(const KeyContext& keyContext,
     *                                       const MatrixColorFilterData& matrixCFData) {
     *     if (matrixCFData.fInHSLA) {
     *         add_hsl_matrix_colorfilter_uniform_data(keyContext, matrixCFData);
     *
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kHSLMatrixColorFilter);
     *     } else {
     *         add_matrix_colorfilter_uniform_data(keyContext, matrixCFData);
     *
     *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kMatrixColorFilter);
     *     }
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, matrixCFData: MatrixColorFilterData) {
      TODO("Implement addBlock")
    }
  }
}
