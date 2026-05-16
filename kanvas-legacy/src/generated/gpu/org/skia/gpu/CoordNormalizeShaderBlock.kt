package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct CoordNormalizeShaderBlock {
 *     struct CoordNormalizeData {
 *         CoordNormalizeData(SkSize dimensions)
 *                 : fInvDimensions(
 *                           SkSize::Make(1.0f / dimensions.width(), 1.0f / dimensions.height())) {}
 *         SkSize fInvDimensions;
 *     };
 *
 *     static void BeginBlock(const KeyContext&, const CoordNormalizeData&);
 * }
 * ```
 */
public open class CoordNormalizeShaderBlock {
  public data class CoordNormalizeData public constructor(
    public var fInvDimensions: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void CoordNormalizeShaderBlock::BeginBlock(const KeyContext& keyContext,
     *                                            const CoordNormalizeData& data) {
     *     add_coord_normalize_uniform_data(keyContext, data);
     *     keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kCoordNormalizeShader);
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext, `data`: CoordNormalizeData) {
      TODO("Implement beginBlock")
    }
  }
}
