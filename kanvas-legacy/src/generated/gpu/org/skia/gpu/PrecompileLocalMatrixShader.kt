package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileLocalMatrixShader final : public PrecompileShader {
 * public:
 *     enum class Flags {
 *         kNone                  = 0b00,
 *         kIsPerspective         = 0b01,
 *         kIncludeWithOutVariant = 0b10,
 *     };
 *
 *     PrecompileLocalMatrixShader(SkSpan<const sk_sp<PrecompileShader>> wrapped,
 *                                 SkEnumBitMask<Flags> flags = Flags::kNone)
 *             : fWrapped(wrapped.begin(), wrapped.end())
 *             , fFlags(flags) {
 *         fNumWrappedCombos = 0;
 *         for (const auto& s : fWrapped) {
 *             fNumWrappedCombos += s->priv().numCombinations();
 *         }
 *     }
 *
 *     bool isConstant(int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         /*
 *          * Regardless of whether the LocalMatrixShader elides itself or not, we always want
 *          * the Constant-ness of the wrapped shader.
 *          */
 *         int desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
 *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
 *
 *         std::pair<sk_sp<PrecompileShader>, int> wrapped =
 *                 PrecompileBase::SelectOption(SkSpan(fWrapped), desiredWrappedCombination);
 *         if (wrapped.first) {
 *             return wrapped.first->priv().isConstant(wrapped.second);
 *         }
 *
 *         return false;
 *     }
 *
 *     SkSpan<const sk_sp<PrecompileShader>> getWrapped() const {
 *         return fWrapped;
 *     }
 *
 *     SkEnumBitMask<Flags> getFlags() const { return fFlags; }
 *
 * private:
 *     // The LocalMatrixShader has two potential variants: with and without the LocalMatrixShader
 *     // In the "with" variant, the kIsPerspective flag will determine if the shader performs
 *     // the perspective division or not.
 *     inline static constexpr int kNumIntrinsicCombinations = 2;
 *     inline static constexpr int kWithLocalMatrix    = 1;
 *     inline static constexpr int kWithoutLocalMatrix = 0;
 *
 *     bool isALocalMatrixShader() const override { return true; }
 *
 *     int numIntrinsicCombinations() const override {
 *         if (!(fFlags & Flags::kIncludeWithOutVariant)) {
 *             return 1;   // just kWithLocalMatrix
 *         }
 *         return kNumIntrinsicCombinations;
 *     }
 *
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         int desiredLMCombination, desiredWrappedCombination;
 *
 *         if (!(fFlags & Flags::kIncludeWithOutVariant)) {
 *             desiredLMCombination = kWithLocalMatrix;
 *             desiredWrappedCombination = desiredCombination;
 *         } else {
 *             desiredLMCombination = desiredCombination % kNumIntrinsicCombinations;
 *             desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
 *         }
 *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
 *
 *         if (desiredLMCombination == kWithLocalMatrix) {
 *             SkMatrix matrix = SkMatrix::I();
 *             if (fFlags & Flags::kIsPerspective) {
 *                 matrix.setPerspX(0.1f);
 *             }
 *             LocalMatrixShaderBlock::LMShaderData lmShaderData(matrix);
 *
 *             LocalMatrixShaderBlock::BeginBlock(keyContext, matrix);
 *         }
 *
 *         AddToKey<PrecompileShader>(keyContext, fWrapped, desiredWrappedCombination);
 *
 *         if (desiredLMCombination == kWithLocalMatrix) {
 *             keyContext.paintParamsKeyBuilder()->endBlock();
 *         }
 *     }
 *
 *     std::vector<sk_sp<PrecompileShader>> fWrapped;
 *     int fNumWrappedCombos;
 *     SkEnumBitMask<Flags> fFlags;
 * }
 * ```
 */
public class PrecompileLocalMatrixShader public constructor(
  wrapped: SkSpan<SkSp<PrecompileShader>>,
  flags: SkEnumBitMask<org.skia.`external`.Flags> = TODO(),
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumIntrinsicCombinations = 2
   * ```
   */
  private var fWrapped: Int = TODO("Initialize fWrapped")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kWithLocalMatrix    = 1
   * ```
   */
  private var fNumWrappedCombos: Int = TODO("Initialize fNumWrappedCombos")

  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kWithoutLocalMatrix = 0
   * ```
   */
  private var fFlags: SkEnumBitMask<org.skia.`external`.Flags> = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * bool isConstant(int desiredCombination) const override {
   *         SkASSERT(desiredCombination < this->numCombinations());
   *
   *         /*
   *          * Regardless of whether the LocalMatrixShader elides itself or not, we always want
   *          * the Constant-ness of the wrapped shader.
   *          */
   *         int desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
   *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
   *
   *         std::pair<sk_sp<PrecompileShader>, int> wrapped =
   *                 PrecompileBase::SelectOption(SkSpan(fWrapped), desiredWrappedCombination);
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
   * SkSpan<const sk_sp<PrecompileShader>> getWrapped() const {
   *         return fWrapped;
   *     }
   * ```
   */
  public fun getWrapped(): SkSpan<SkSp<PrecompileShader>> {
    TODO("Implement getWrapped")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<Flags> getFlags() const { return fFlags; }
   * ```
   */
  public fun getFlags(): SkEnumBitMask<org.skia.`external`.Flags> {
    TODO("Implement getFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isALocalMatrixShader() const override { return true; }
   * ```
   */
  public override fun isALocalMatrixShader(): Boolean {
    TODO("Implement isALocalMatrixShader")
  }

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override {
   *         if (!(fFlags & Flags::kIncludeWithOutVariant)) {
   *             return 1;   // just kWithLocalMatrix
   *         }
   *         return kNumIntrinsicCombinations;
   *     }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
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
   *         SkASSERT(desiredCombination < this->numCombinations());
   *
   *         int desiredLMCombination, desiredWrappedCombination;
   *
   *         if (!(fFlags & Flags::kIncludeWithOutVariant)) {
   *             desiredLMCombination = kWithLocalMatrix;
   *             desiredWrappedCombination = desiredCombination;
   *         } else {
   *             desiredLMCombination = desiredCombination % kNumIntrinsicCombinations;
   *             desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
   *         }
   *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
   *
   *         if (desiredLMCombination == kWithLocalMatrix) {
   *             SkMatrix matrix = SkMatrix::I();
   *             if (fFlags & Flags::kIsPerspective) {
   *                 matrix.setPerspX(0.1f);
   *             }
   *             LocalMatrixShaderBlock::LMShaderData lmShaderData(matrix);
   *
   *             LocalMatrixShaderBlock::BeginBlock(keyContext, matrix);
   *         }
   *
   *         AddToKey<PrecompileShader>(keyContext, fWrapped, desiredWrappedCombination);
   *
   *         if (desiredLMCombination == kWithLocalMatrix) {
   *             keyContext.paintParamsKeyBuilder()->endBlock();
   *         }
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  public enum class Flags {
    kNone,
    kIsPerspective,
    kIncludeWithOutVariant,
  }

  public companion object {
    private val kNumIntrinsicCombinations: Int = TODO("Initialize kNumIntrinsicCombinations")

    private val kWithLocalMatrix: Int = TODO("Initialize kWithLocalMatrix")

    private val kWithoutLocalMatrix: Int = TODO("Initialize kWithoutLocalMatrix")
  }
}
