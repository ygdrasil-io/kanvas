package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * struct BlendComposeBlock {
 *     static void BeginBlock(const KeyContext&);
 * }
 * ```
 */
public open class BlendComposeBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void BlendComposeBlock::BeginBlock(const KeyContext& keyContext) {
     *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kBlendCompose)
     *
     *     keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kBlendCompose);
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext) {
      TODO("Implement beginBlock")
    }
  }
}
