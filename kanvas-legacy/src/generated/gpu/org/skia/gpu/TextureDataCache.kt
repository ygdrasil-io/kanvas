package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.`external`.Index

/**
 * C++ original:
 * ```cpp
 * class TextureDataCache {
 *     struct TakeTextureRef {
 *         TextureProxy* persist(TextureProxy* proxy) {
 *             // This ref will be adopted by the sk_sp() value stored in the TextureProxyCache.
 *             proxy->ref();
 *             return proxy;
 *         }
 *     };
 *     using TextureProxyCache = DenseBiMap<TextureProxy*, sk_sp<TextureProxy>, TakeTextureRef>;
 *
 *     struct TextureCopier {
 *         TextureDataBlock persist(TextureDataBlock textures) {
 *             // Insert every referenced texture into fUniqueTextures to hand off to DrawPass.
 *             for (int i = 0; i < textures.numTextures(); ++i) {
 *                 (void) fUniqueTextures.insert(textures.texture(i).first.get());
 *             }
 *
 *             // Confirm that we're getting the right value back.
 * #if defined(SK_DEBUG)
 *             auto t = TextureDataBlock::Make(textures, &fArena);
 *             SkASSERT(textures == t);
 *             return t;
 * #else
 *             return TextureDataBlock::Make(textures, &fArena);
 * #endif
 *         }
 *
 *         SkArenaAlloc fArena{0};
 *         TextureProxyCache fUniqueTextures;
 *     };
 *     using TextureDataMap = DenseBiMap<TextureDataBlock,
 *                                       TextureDataBlock,
 *                                       TextureCopier,
 *                                       TextureDataBlock::Hash>;
 *     TextureDataMap fTextures;
 *
 * public:
 *     using Index = TextureDataMap::Index;
 *     static constexpr Index kInvalidIndex = TextureDataMap::kInvalidIndex;
 *
 *     TextureDataCache() = default;
 *
 *     void reset() { fTextures.reset(); }
 *
 *     Index insert(TextureDataBlock dataBlock) { return fTextures.insert(dataBlock); }
 *
 *     TextureDataBlock lookup(Index index) const { return fTextures.lookup(index); }
 *
 *     bool hasTexture(const TextureProxy* texture) const {
 *         // The template for TextureProxyCache uses `TextureProxy*` because `sk_sp` does not
 *         // take a const pointer; this contains() check just uses the address and doesn't do
 *         // anything that actually requires it to be non-const.
 *         return fTextures.storage().fUniqueTextures.contains(
 *                 const_cast<TextureProxy*>(texture));
 *     }
 *
 *     skia_private::TArray<sk_sp<TextureProxy>> detachTextures() {
 *         return fTextures.storage().fUniqueTextures.detach();
 *     }
 *
 *     const skia_private::TArray<TextureDataBlock>& getBindings() const {
 *         return fTextures.get();
 *     }
 *
 * #if defined(GPU_TEST_UTILS)
 *     int bindingCount() { return fTextures.count(); }
 *     int uniqueTextureCount() { return fTextures.storage().fUniqueTextures.count(); }
 * #endif
 * }
 * ```
 */
public data class TextureDataCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextureDataMap fTextures
   * ```
   */
  private var fTextures: TextureDataCacheTextureDataMap,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset() { fTextures.reset(); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * Index insert(TextureDataBlock dataBlock) { return fTextures.insert(dataBlock); }
   * ```
   */
  public fun insert(dataBlock: TextureDataBlock): Int {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * TextureDataBlock lookup(Index index) const { return fTextures.lookup(index); }
   * ```
   */
  public fun lookup(index: Index): TextureDataBlock {
    TODO("Implement lookup")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasTexture(const TextureProxy* texture) const {
   *         // The template for TextureProxyCache uses `TextureProxy*` because `sk_sp` does not
   *         // take a const pointer; this contains() check just uses the address and doesn't do
   *         // anything that actually requires it to be non-const.
   *         return fTextures.storage().fUniqueTextures.contains(
   *                 const_cast<TextureProxy*>(texture));
   *     }
   * ```
   */
  public fun hasTexture(texture: TextureProxy?): Boolean {
    TODO("Implement hasTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<TextureProxy>> detachTextures() {
   *         return fTextures.storage().fUniqueTextures.detach();
   *     }
   * ```
   */
  public fun detachTextures(): Int {
    TODO("Implement detachTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<TextureDataBlock>& getBindings() const {
   *         return fTextures.get();
   *     }
   * ```
   */
  public fun getBindings(): Int {
    TODO("Implement getBindings")
  }

  /**
   * C++ original:
   * ```cpp
   * int bindingCount() { return fTextures.count(); }
   * ```
   */
  public fun bindingCount(): Int {
    TODO("Implement bindingCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int uniqueTextureCount() { return fTextures.storage().fUniqueTextures.count(); }
   * ```
   */
  public fun uniqueTextureCount(): Int {
    TODO("Implement uniqueTextureCount")
  }

  public open class TakeTextureRef {
    public fun persist(proxy: TextureProxy?): Int {
      TODO("Implement persist")
    }
  }

  public data class TextureCopier public constructor(
    public var fArena: Int,
    public var fUniqueTextures: Int,
  ) {
    public fun persist(textures: TextureDataBlock): TextureDataBlock {
      TODO("Implement persist")
    }
  }

  public companion object {
    public val kInvalidIndex: Int = TODO("Initialize kInvalidIndex")
  }
}
