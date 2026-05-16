package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * struct AlphaOnlyPaintColorBlock {
 *     static void AddBlock(const KeyContext&);
 * }
 * ```
 */
public open class AlphaOnlyPaintColorBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void AlphaOnlyPaintColorBlock::AddBlock(const KeyContext& keyContext) {
     *     add_alpha_only_paint_color_uniform_data(keyContext);
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kAlphaOnlyPaintColor);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext) {
      TODO("Implement addBlock")
    }
  }
}
