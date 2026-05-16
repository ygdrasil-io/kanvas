package org.skia.gpu

import org.skia.core.SkPMColor4f

/**
 * C++ original:
 * ```cpp
 * struct SolidColorShaderBlock {
 *     static void AddBlock(const KeyContext&, const SkPMColor4f&);
 * }
 * ```
 */
public open class SolidColorShaderBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SolidColorShaderBlock::AddBlock(const KeyContext& keyContext, const SkPMColor4f& premulColor) {
     *     add_solid_uniform_data(keyContext, premulColor);
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kSolidColorShader);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, premulColor: SkPMColor4f) {
      TODO("Implement addBlock")
    }
  }
}
