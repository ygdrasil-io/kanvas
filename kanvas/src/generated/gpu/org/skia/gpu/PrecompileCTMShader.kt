package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileCTMShader final : public PrecompileShader {
 * public:
 *     PrecompileCTMShader(SkSpan<const sk_sp<PrecompileShader>> wrapped)
 *             : fWrapped(wrapped.begin(), wrapped.end()) {
 *         fNumWrappedCombos = 0;
 *         for (const auto& s : fWrapped) {
 *             fNumWrappedCombos += s->priv().numCombinations();
 *         }
 *     }
 *
 *     bool isConstant(int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumWrappedCombos);
 *
 *         std::pair<sk_sp<PrecompileShader>, int> wrapped =
 *                 PrecompileBase::SelectOption(SkSpan(fWrapped), desiredCombination);
 *         if (wrapped.first) {
 *             return wrapped.first->priv().isConstant(wrapped.second);
 *         }
 *
 *         return false;
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumWrappedCombos);
 *
 *         LocalMatrixShaderBlock::LMShaderData kIgnoredLMShaderData(SkMatrix::I());
 *
 *         LocalMatrixShaderBlock::BeginBlock(keyContext, kIgnoredLMShaderData);
 *
 *         AddToKey<PrecompileShader>(keyContext, fWrapped, desiredCombination);
 *
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     std::vector<sk_sp<PrecompileShader>> fWrapped;
 *     int fNumWrappedCombos;
 * }
 * ```
 */
public class PrecompileCTMShader public constructor(
  wrapped: SkSpan<SkSp<PrecompileShader>>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>> fWrapped
   * ```
   */
  private var fWrapped: Int = TODO("Initialize fWrapped")

  /**
   * C++ original:
   * ```cpp
   * int fNumWrappedCombos
   * ```
   */
  private var fNumWrappedCombos: Int = TODO("Initialize fNumWrappedCombos")

  /**
   * C++ original:
   * ```cpp
   * bool isConstant(int desiredCombination) const override {
   *         SkASSERT(desiredCombination < fNumWrappedCombos);
   *
   *         std::pair<sk_sp<PrecompileShader>, int> wrapped =
   *                 PrecompileBase::SelectOption(SkSpan(fWrapped), desiredCombination);
   *         if (wrapped.first) {
   *             return wrapped.first->priv().isConstant(wrapped.second);
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  public override fun isConstant(desiredCombination: Int): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumWrappedCombos; }
   * ```
   */
  public override fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination < fNumWrappedCombos);
   *
   *         LocalMatrixShaderBlock::LMShaderData kIgnoredLMShaderData(SkMatrix::I());
   *
   *         LocalMatrixShaderBlock::BeginBlock(keyContext, kIgnoredLMShaderData);
   *
   *         AddToKey<PrecompileShader>(keyContext, fWrapped, desiredCombination);
   *
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
