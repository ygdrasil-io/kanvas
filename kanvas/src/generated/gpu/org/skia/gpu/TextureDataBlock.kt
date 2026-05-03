package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class TextureDataBlock {
 * public:
 *     using SampledTexture = std::pair<sk_sp<TextureProxy>, SamplerDesc>;
 *
 *     constexpr TextureDataBlock(const TextureDataBlock&) = default;
 *     constexpr TextureDataBlock() = default;
 *
 *     static TextureDataBlock Make(TextureDataBlock toClone, SkArenaAlloc* arena) {
 *         SampledTexture* copy = arena->makeArrayCopy<SampledTexture>(toClone.fTextures);
 *         return TextureDataBlock(SkSpan(copy, toClone.numTextures()));
 *     }
 *
 *     // TODO(b/330864257): Once Device::drawCoverageMask() can keep its texture proxy alive without
 *     // creating a temporary TextureDataBlock this constructor can go away.
 *     explicit TextureDataBlock(const SampledTexture& texture) : fTextures(&texture, 1) {}
 *
 *     constexpr TextureDataBlock& operator=(const TextureDataBlock&) = default;
 *
 *     explicit operator bool() const { return !this->empty(); }
 *     bool empty() const { return fTextures.empty(); }
 *
 *     int numTextures() const { return SkTo<int>(fTextures.size()); }
 *     const SampledTexture& texture(int index) const { return fTextures[index]; }
 *
 *     bool operator==(TextureDataBlock other) const {
 *         if (fTextures.size() != other.fTextures.size()) {
 *             return false;
 *         }
 *         if (fTextures.data() == other.fTextures.data()) {
 *             return true; // shortcut for the same span
 *         }
 *
 *         for (size_t i = 0; i < fTextures.size(); ++i) {
 *             if (fTextures[i] != other.fTextures[i]) {
 *                 return false;
 *             }
 *         }
 *
 *         return true;
 *     }
 *     bool operator!=(TextureDataBlock other) const { return !(*this == other);  }
 *
 *     struct Hash {
 *         uint32_t operator()(TextureDataBlock block) const {
 *             uint32_t hash = 0;
 *
 *             for (auto& d : block.fTextures) {
 *                 SamplerDesc samplerKey = std::get<1>(d);
 *                 hash = SkChecksum::Hash32(&samplerKey, sizeof(samplerKey), hash);
 *
 *                 // Because the lifetime of the TextureDataCache is for just one Recording and the
 *                 // TextureDataBlocks hold refs on their proxies, we can just use the proxy's pointer
 *                 // for the hash here.
 *                 uintptr_t proxy = reinterpret_cast<uintptr_t>(std::get<0>(d).get());
 *                 hash = SkChecksum::Hash32(&proxy, sizeof(proxy), hash);
 *             }
 *
 *             return hash;
 *         }
 *     };
 *
 * private:
 *     friend class PipelineDataGatherer;
 *
 *     // Initial TextureDataBlocks must come from a PipelineDataGatherer
 *     constexpr TextureDataBlock(SkSpan<const SampledTexture> textures) : fTextures(textures) {}
 *
 *     SkSpan<const SampledTexture> fTextures;
 * }
 * ```
 */
public data class TextureDataBlock public constructor() {
  /**
   * C++ original:
   * ```cpp
   * constexpr TextureDataBlock& operator=(const TextureDataBlock&) = default
   * ```
   */
  public fun assign(param0: TextureDataBlock) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fTextures.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * int numTextures() const { return SkTo<int>(fTextures.size()); }
   * ```
   */
  public fun numTextures(): Int {
    TODO("Implement numTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * const SampledTexture& texture(int index) const { return fTextures[index]; }
   * ```
   */
  public fun texture(index: Int): Int {
    TODO("Implement texture")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(TextureDataBlock other) const {
   *         if (fTextures.size() != other.fTextures.size()) {
   *             return false;
   *         }
   *         if (fTextures.data() == other.fTextures.data()) {
   *             return true; // shortcut for the same span
   *         }
   *
   *         for (size_t i = 0; i < fTextures.size(); ++i) {
   *             if (fTextures[i] != other.fTextures[i]) {
   *                 return false;
   *             }
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public open class Hash {
    public operator fun invoke(block: TextureDataBlock): Int {
      TODO("Implement invoke")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static TextureDataBlock Make(TextureDataBlock toClone, SkArenaAlloc* arena) {
     *         SampledTexture* copy = arena->makeArrayCopy<SampledTexture>(toClone.fTextures);
     *         return TextureDataBlock(SkSpan(copy, toClone.numTextures()));
     *     }
     * ```
     */
    public fun make(toClone: TextureDataBlock, arena: SkArenaAlloc?): TextureDataBlock {
      TODO("Implement make")
    }
  }
}
