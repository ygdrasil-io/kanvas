package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct CoordClampShaderBlock {
 *     struct CoordClampData {
 *         CoordClampData(SkRect subset) : fSubset(subset) {}
 *
 *         SkRect fSubset;
 *     };
 *
 *     static void BeginBlock(const KeyContext&, const CoordClampData&);
 * }
 * ```
 */
public open class CoordClampShaderBlock {
  public data class CoordClampData public constructor(
    public var fSubset: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void CoordClampShaderBlock::BeginBlock(const KeyContext& keyContext,
     *                                        const CoordClampData& clampData) {
     *     add_coordclamp_uniform_data(keyContext, clampData);
     *     keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kCoordClampShader);
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext, clampData: CoordClampData) {
      TODO("Implement beginBlock")
    }
  }
}
