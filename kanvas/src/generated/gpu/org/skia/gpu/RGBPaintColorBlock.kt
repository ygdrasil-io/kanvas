package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * struct RGBPaintColorBlock {
 *     static void AddBlock(const KeyContext&);
 * }
 * ```
 */
public open class RGBPaintColorBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void RGBPaintColorBlock::AddBlock(const KeyContext& keyContext) {
     *     add_rgb_paint_color_uniform_data(keyContext);
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kRGBPaintColor);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext) {
      TODO("Implement addBlock")
    }
  }
}
