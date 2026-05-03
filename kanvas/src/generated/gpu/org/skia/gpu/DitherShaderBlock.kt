package org.skia.gpu

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DitherShaderBlock {
 *     struct DitherData {
 *         DitherData(float range, sk_sp<TextureProxy> proxy)
 *             : fRange(range)
 *             , fLUTProxy(std::move(proxy)) {}
 *
 *         float fRange;
 *         sk_sp<TextureProxy> fLUTProxy;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const DitherData&);
 * }
 * ```
 */
public open class DitherShaderBlock {
  public data class DitherData public constructor(
    public var fRange: Float,
    public var fLUTProxy: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void DitherShaderBlock::AddBlock(const KeyContext& keyContext, const DitherData& data) {
     *     auto gatherer = keyContext.pipelineDataGatherer();
     *     add_dither_uniform_data(keyContext, data);
     *
     *     SkASSERT(data.fLUTProxy || !keyContext.recorder());
     *     gatherer->add(data.fLUTProxy, {SkFilterMode::kNearest, SkTileMode::kRepeat});
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kDitherShader);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, `data`: DitherData) {
      TODO("Implement addBlock")
    }
  }
}
