package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * struct ComposeBlock {
 *     static void BeginBlock(const KeyContext&);
 * }
 * ```
 */
public open class ComposeBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void ComposeBlock::BeginBlock(const KeyContext& keyContext) {
     *     keyContext.paintParamsKeyBuilder()->beginBlock(BuiltInCodeSnippetID::kCompose);
     * }
     * ```
     */
    public fun beginBlock(keyContext: KeyContext) {
      TODO("Implement beginBlock")
    }
  }
}
