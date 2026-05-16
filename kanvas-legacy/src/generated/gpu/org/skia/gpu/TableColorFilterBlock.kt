package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * struct TableColorFilterBlock {
 *     struct TableColorFilterData {
 *         TableColorFilterData(sk_sp<TextureProxy> proxy) : fTextureProxy(std::move(proxy)) {}
 *
 *         sk_sp<TextureProxy> fTextureProxy;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const TableColorFilterData&);
 * }
 * ```
 */
public open class TableColorFilterBlock {
  public data class TableColorFilterData public constructor()

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void TableColorFilterBlock::AddBlock(const KeyContext& keyContext,
     *                                      const TableColorFilterData& data) {
     *     SkASSERT(data.fTextureProxy || !keyContext.recorder());
     *
     *     add_table_colorfilter_uniform_data(keyContext, data);
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kTableColorFilter);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, `data`: TableColorFilterData) {
      TODO("Implement addBlock")
    }
  }
}
