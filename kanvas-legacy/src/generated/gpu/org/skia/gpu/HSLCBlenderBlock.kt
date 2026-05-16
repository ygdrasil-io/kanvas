package org.skia.gpu

import kotlin.Float
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct HSLCBlenderBlock {
 *     static void AddBlock(const KeyContext&, SkSpan<const float> coeffs);
 * }
 * ```
 */
public open class HSLCBlenderBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void HSLCBlenderBlock::AddBlock(const KeyContext& keyContext, SkSpan<const float> coeffs) {
     *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kHSLCBlender)
     *     SkASSERT(coeffs.size() == 2);
     *     keyContext.pipelineDataGatherer()->writeHalf(SkV2{coeffs[0], coeffs[1]});
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kHSLCBlender);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, coeffs: SkSpan<Float>) {
      TODO("Implement addBlock")
    }
  }
}
