package org.skia.gpu

import kotlin.Float
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct PorterDuffBlenderBlock {
 *     static void AddBlock(const KeyContext&, SkSpan<const float> coeffs);
 * }
 * ```
 */
public open class PorterDuffBlenderBlock {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void PorterDuffBlenderBlock::AddBlock(const KeyContext& keyContext, SkSpan<const float> coeffs) {
     *     BEGIN_WRITE_UNIFORMS(keyContext, BuiltInCodeSnippetID::kPorterDuffBlender)
     *     SkASSERT(coeffs.size() == 4);
     *     keyContext.pipelineDataGatherer()->writeHalf(SkV4{coeffs[0], coeffs[1], coeffs[2], coeffs[3]});
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPorterDuffBlender);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, coeffs: SkSpan<Float>) {
      TODO("Implement addBlock")
    }
  }
}
