package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkRefCnt

public typealias DataResourceProviderINHERITED = ResourceProvider

public typealias ResourceProvider = ResourceProvider

public typealias FakeWebFontProviderINHERITED = ResourceProvider

/**
 * C++ original:
 * ```cpp
 * class SK_API ResourceProvider : public SkRefCnt {
 * public:
 *     /**
 *      * Load a generic resource (currently only nested animations) specified by |path| + |name|,
 *      * and return as an SkData.
 *      */
 *     virtual sk_sp<SkData> load(const char[] /* resource_path */,
 *                                const char[] /* resource_name */) const {
 *         return nullptr;
 *     }
 *
 *     /**
 *      * Load an image asset specified by |path| + |name|, and returns the corresponding
 *      * ImageAsset proxy.
 *      */
 *     virtual sk_sp<ImageAsset> loadImageAsset(const char[] /* resource_path */,
 *                                              const char[] /* resource_name */,
 *                                              const char[] /* resource_id   */) const {
 *         return nullptr;
 *     }
 *
 *     /**
 *      * Load an external audio track specified by |path|/|name|/|id|.
 *      */
 *     virtual sk_sp<ExternalTrackAsset> loadAudioAsset(const char[] /* resource_path */,
 *                                                      const char[] /* resource_name */,
 *                                                      const char[] /* resource_id   */) {
 *         return nullptr;
 *     }
 *
 *     /**
 *      * DEPRECATED: implement loadTypeface() instead.
 *      *
 *      * Load an external font and return as SkData.
 *      *
 *      * @param name  font name    ("fName" Lottie property)
 *      * @param url   web font URL ("fPath" Lottie property)
 *      *
 *      * -- Note --
 *      *
 *      *   This mechanism assumes monolithic fonts (single data blob).  Some web font providers may
 *      *   serve multiple font blobs, segmented for various unicode ranges, depending on user agent
 *      *   capabilities (woff, woff2).  In that case, the embedder would need to advertise no user
 *      *   agent capabilities when fetching the URL, in order to receive full font data.
 *      */
 *     virtual sk_sp<SkData> loadFont(const char[] /* name */,
 *                                    const char[] /* url  */) const {
 *         return nullptr;
 *     }
 *
 *     /**
 *      * Load an external font and return as SkTypeface.
 *      *
 *      * @param name  font name
 *      * @param url   web font URL
 *      */
 *     virtual sk_sp<SkTypeface> loadTypeface(const char[] /* name */,
 *                                            const char[] /* url  */) const {
 *         return nullptr;
 *     }
 * }
 * ```
 */
public open class ResourceProvider : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkData> load(const char[] /* resource_path */,
   *                                const char[] /* resource_name */) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun load(param0: Int, param1: Int): Int {
    TODO("Implement load")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ImageAsset> loadImageAsset(const char[] /* resource_path */,
   *                                              const char[] /* resource_name */,
   *                                              const char[] /* resource_id   */) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun loadImageAsset(
    param0: Int,
    param1: Int,
    param2: Int,
  ): Int {
    TODO("Implement loadImageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<ExternalTrackAsset> loadAudioAsset(const char[] /* resource_path */,
   *                                                      const char[] /* resource_name */,
   *                                                      const char[] /* resource_id   */) {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun loadAudioAsset(
    param0: Int,
    param1: Int,
    param2: Int,
  ): Int {
    TODO("Implement loadAudioAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkData> loadFont(const char[] /* name */,
   *                                    const char[] /* url  */) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun loadFont(param0: Int, param1: Int): Int {
    TODO("Implement loadFont")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> loadTypeface(const char[] /* name */,
   *                                            const char[] /* url  */) const {
   *         return nullptr;
   *     }
   * ```
   */
  public open fun loadTypeface(param0: Int, param1: Int): Int {
    TODO("Implement loadTypeface")
  }
}

public typealias FileResourceProviderINHERITED = ResourceProvider
