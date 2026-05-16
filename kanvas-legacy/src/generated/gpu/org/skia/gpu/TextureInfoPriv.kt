package org.skia.gpu

import BackendTextureInfo
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class TextureInfoPriv {
 * public:
 *     static TextureFormat ViewFormat(const TextureInfo& info) {
 *         return info.fViewFormat;
 *     }
 *     static uint32_t ChannelMask(const TextureInfo& info) {
 *         return TextureFormatChannelMask(ViewFormat(info));
 *     }
 *
 *     template <typename BackendTextureInfo>
 *     static TextureInfo Make(const BackendTextureInfo& data) {
 *         return TextureInfo(data);
 *     }
 *
 *     template <typename BackendTextureInfo>
 *     static const BackendTextureInfo& Get(const TextureInfo& info) {
 *         SkASSERT(info.isValid() && info.backend() == BackendTextureInfo::kBackend);
 *         return *(static_cast<const BackendTextureInfo*>(info.fData.get()));
 *     }
 *
 *     template <typename BackendTextureInfo>
 *     static bool Copy(const TextureInfo& info, BackendTextureInfo* out) {
 *         if (!info.isValid() || info.backend() != BackendTextureInfo::kBackend) {
 *             return false;
 *         }
 *
 *         SkASSERT(out);
 *         *out = Get<BackendTextureInfo>(info);
 *         return true;
 *     }
 *
 * private:
 *     TextureInfoPriv() = delete;
 *     TextureInfoPriv(const TextureInfoPriv&) = delete;
 * }
 * ```
 */
public open class TextureInfoPriv public constructor() {
  /**
   * C++ original:
   * ```cpp
   * TextureInfoPriv() = delete
   * ```
   */
  public constructor(param0: TextureInfoPriv) : this() {
    TODO("Implement constructor")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static TextureFormat ViewFormat(const TextureInfo& info) {
     *         return info.fViewFormat;
     *     }
     * ```
     */
    public fun viewFormat(info: TextureInfo): Int {
      TODO("Implement viewFormat")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t ChannelMask(const TextureInfo& info) {
     *         return TextureFormatChannelMask(ViewFormat(info));
     *     }
     * ```
     */
    public fun channelMask(info: TextureInfo): Int {
      TODO("Implement channelMask")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename BackendTextureInfo>
     *     static TextureInfo Make(const BackendTextureInfo& data) {
     *         return TextureInfo(data);
     *     }
     * ```
     */
    public fun <BackendTextureInfo> make(`data`: BackendTextureInfo): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename BackendTextureInfo>
     *     static const BackendTextureInfo& Get(const TextureInfo& info) {
     *         SkASSERT(info.isValid() && info.backend() == BackendTextureInfo::kBackend);
     *         return *(static_cast<const BackendTextureInfo*>(info.fData.get()));
     *     }
     * ```
     */
    public fun <BackendTextureInfo> `get`(info: TextureInfo): BackendTextureInfo {
      TODO("Implement get")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename BackendTextureInfo>
     *     static bool Copy(const TextureInfo& info, BackendTextureInfo* out) {
     *         if (!info.isValid() || info.backend() != BackendTextureInfo::kBackend) {
     *             return false;
     *         }
     *
     *         SkASSERT(out);
     *         *out = Get<BackendTextureInfo>(info);
     *         return true;
     *     }
     * ```
     */
    public fun <BackendTextureInfo> copy(info: TextureInfo, `out`: BackendTextureInfo?): Boolean {
      TODO("Implement copy")
    }
  }
}
