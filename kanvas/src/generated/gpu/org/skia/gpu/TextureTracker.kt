package org.skia.gpu

import DrawPassCommands.List
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TextureTracker {
 * public:
 *     TextureTracker(TextureDataCache* textureCache)
 *             : fTextureCache(textureCache) {}
 *
 *     bool setCurrentTextureBindings(TextureDataCache::Index bindingIndex) {
 *         if (bindingIndex < TextureDataCache::kInvalidIndex && fLastIndex != bindingIndex) {
 *             fLastIndex = bindingIndex;
 *             return true;
 *         }
 *         // No binding change
 *         return false;
 *     }
 *
 *     void bindTextures(DrawPassCommands::List* commandList) {
 *         SkASSERT(fLastIndex < TextureDataCache::kInvalidIndex);
 *         TextureDataBlock binding = fTextureCache->lookup(fLastIndex);
 *
 *         auto [textures, samplers] =
 *                 commandList->bindDeferredTexturesAndSamplers(binding.numTextures());
 *
 *         for (int i = 0; i < binding.numTextures(); ++i) {
 *             auto [t, s] = binding.texture(i);
 *             textures[i] = t.get();
 *             samplers[i] = s;
 *         }
 *     }
 *
 * private:
 *     TextureDataCache::Index fLastIndex = TextureDataCache::kInvalidIndex;
 *
 *     TextureDataCache* const fTextureCache;
 * }
 * ```
 */
public data class TextureTracker public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextureDataCache::Index fLastIndex
   * ```
   */
  private var fLastIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * TextureDataCache* const fTextureCache
   * ```
   */
  private val fTextureCache: TextureDataCache?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool setCurrentTextureBindings(TextureDataCache::Index bindingIndex) {
   *         if (bindingIndex < TextureDataCache::kInvalidIndex && fLastIndex != bindingIndex) {
   *             fLastIndex = bindingIndex;
   *             return true;
   *         }
   *         // No binding change
   *         return false;
   *     }
   * ```
   */
  public fun setCurrentTextureBindings(bindingIndex: TextureDataCache.Index): Boolean {
    TODO("Implement setCurrentTextureBindings")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindTextures(DrawPassCommands::List* commandList) {
   *         SkASSERT(fLastIndex < TextureDataCache::kInvalidIndex);
   *         TextureDataBlock binding = fTextureCache->lookup(fLastIndex);
   *
   *         auto [textures, samplers] =
   *                 commandList->bindDeferredTexturesAndSamplers(binding.numTextures());
   *
   *         for (int i = 0; i < binding.numTextures(); ++i) {
   *             auto [t, s] = binding.texture(i);
   *             textures[i] = t.get();
   *             samplers[i] = s;
   *         }
   *     }
   * ```
   */
  public fun bindTextures(commandList: List?) {
    TODO("Implement bindTextures")
  }
}
