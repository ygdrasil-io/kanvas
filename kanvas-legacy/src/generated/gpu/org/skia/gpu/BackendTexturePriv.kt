package org.skia.gpu

import SomeBackendTextureData
import kotlin.Int
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BackendTexturePriv {
 * public:
 *     template <typename SomeBackendTextureData>
 *     static BackendTexture Make(SkISize dimensions,
 *                                TextureInfo info,
 *                                const SomeBackendTextureData& textureData) {
 *         return BackendTexture(dimensions, info, textureData);
 *     }
 *
 *     static const BackendTextureData* GetData(const BackendTexture& info) {
 *         return info.fTextureData.get();
 *     }
 *
 *     static BackendTextureData* GetData(BackendTexture* info) {
 *         SkASSERT(info);
 *         return info->fTextureData.get();
 *     }
 * }
 * ```
 */
public open class BackendTexturePriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template <typename SomeBackendTextureData>
     *     static BackendTexture Make(SkISize dimensions,
     *                                TextureInfo info,
     *                                const SomeBackendTextureData& textureData) {
     *         return BackendTexture(dimensions, info, textureData);
     *     }
     * ```
     */
    public fun <SomeBackendTextureData> make(
      dimensions: SkISize,
      info: TextureInfo,
      textureData: SomeBackendTextureData,
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static const BackendTextureData* GetData(const BackendTexture& info) {
     *         return info.fTextureData.get();
     *     }
     * ```
     */
    public fun getData(info: BackendTexture): BackendTextureData {
      TODO("Implement getData")
    }

    /**
     * C++ original:
     * ```cpp
     * static BackendTextureData* GetData(BackendTexture* info) {
     *         SkASSERT(info);
     *         return info->fTextureData.get();
     *     }
     * ```
     */
    public fun getData(info: BackendTexture?): BackendTextureData {
      TODO("Implement getData")
    }
  }
}
