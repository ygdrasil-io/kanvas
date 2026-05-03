package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct LocalMatrixShaderBlock {
 *     struct LMShaderData {
 *         LMShaderData(const SkMatrix& localMatrix)
 *                 : fLocalMatrix(localMatrix) {}
 *
 *         // Local matrices are applied to coords.xy01, so a 4x4 matrix can be flattened to a 3x3
 *         // for less data upload to the GPU at this point (as there will be no more coordinate
 *         // space manipulation that might require the full 4x4).
 *         const SkMatrix fLocalMatrix;
 *     };
 *
 *     static void BeginBlock(const KeyContext&, const LMShaderData&);
 * }
 * ```
 */
public open class LocalMatrixShaderBlock {
  public data class LMShaderData public constructor(
    public val fLocalMatrix: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void LocalMatrixShaderBlock::BeginBlock(const KeyContext& keyContext,
     *                                         const LMShaderData& lmShaderData) {
     *     const SkMatrix& m = lmShaderData.fLocalMatrix;
     *
     *     if (lmShaderData.fLocalMatrix.hasPerspective()) {
     *         // Perspective local matrices are rare enough and add enough extra instructions that it's
     *         // worth specializing since it has to perform a per-pixel division.
     *         keyContext.paintParamsKeyBuilder()->beginBlock(
     *                 BuiltInCodeSnippetID::kLocalMatrixShaderPersp);
     *         BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kLocalMatrixShaderPersp)
     *         keyContext.pipelineDataGatherer()->write(m);
     *     } else {
     *         // For an affine 2D transform, we only need to upload the upper 2x2 and XY translation.
     *         keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kLocalMatrixShader);
     *
     *         BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kLocalMatrixShader)
     *         // The upper 2x2 is expected to be in column major order, but SkMatrix is 3x3 row major.
     *         keyContext.pipelineDataGatherer()->write(SkV4{m.getScaleX(), m.getSkewY(),
     *                              m.getSkewX(),  m.getScaleY()});
     *         keyContext.pipelineDataGatherer()->write(SkV2{m.getTranslateX(), m.getTranslateY()});
     *     }
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext, lmShaderData: LMShaderData) {
      TODO("Implement beginBlock")
    }
  }
}
