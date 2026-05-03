package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ResourceProviderProxyBase : public ResourceProvider {
 * protected:
 *     explicit ResourceProviderProxyBase(sk_sp<ResourceProvider>);
 *
 *     sk_sp<SkData> load(const char[], const char[]) const override;
 *     sk_sp<ImageAsset> loadImageAsset(const char[], const char[], const char[]) const override;
 *     sk_sp<SkTypeface> loadTypeface(const char[], const char[]) const override;
 *     sk_sp<SkData> loadFont(const char[], const char[]) const override;
 *     sk_sp<ExternalTrackAsset> loadAudioAsset(const char[], const char[], const char[]) override;
 *
 * protected:
 *     const sk_sp<ResourceProvider> fProxy;
 * }
 * ```
 */
public open class ResourceProviderProxyBase public constructor(
  rp: SkSp<ResourceProvider>,
) : ResourceProvider() {
  /**
   * C++ original:
   * ```cpp
   * explicit ResourceProviderProxyBase(sk_sp<ResourceProvider>)
   * ```
   */
  protected var skSp: ResourceProviderProxyBase = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<ResourceProvider> fProxy
   * ```
   */
  protected val fProxy: Int = TODO("Initialize fProxy")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> ResourceProviderProxyBase::load(const char resource_path[],
   *                                               const char resource_name[]) const {
   *     return fProxy ? fProxy->load(resource_path, resource_name)
   *                   : nullptr;
   * }
   * ```
   */
  protected override fun load(resourcePath: CharArray, resourceName: CharArray): Int {
    TODO("Implement load")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageAsset> ResourceProviderProxyBase::loadImageAsset(const char rpath[],
   *                                                             const char rname[],
   *                                                             const char rid[]) const {
   *     return fProxy ? fProxy->loadImageAsset(rpath, rname, rid)
   *                   : nullptr;
   * }
   * ```
   */
  protected override fun loadImageAsset(
    rpath: CharArray,
    rname: CharArray,
    rid: CharArray,
  ): Int {
    TODO("Implement loadImageAsset")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> ResourceProviderProxyBase::loadTypeface(const char name[],
   *                                                           const char url[]) const {
   *     return fProxy ? fProxy->loadTypeface(name, url)
   *                   : nullptr;
   * }
   * ```
   */
  protected override fun loadTypeface(name: CharArray, url: CharArray): Int {
    TODO("Implement loadTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> ResourceProviderProxyBase::loadFont(const char name[], const char url[]) const {
   *     return fProxy ? fProxy->loadFont(name, url)
   *                   : nullptr;
   * }
   * ```
   */
  protected override fun loadFont(name: CharArray, url: CharArray): Int {
    TODO("Implement loadFont")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ExternalTrackAsset> ResourceProviderProxyBase::loadAudioAsset(const char path[],
   *                                                                     const char name[],
   *                                                                     const char id[]) {
   *     return fProxy ? fProxy->loadAudioAsset(path, name, id)
   *                   : nullptr;
   * }
   * ```
   */
  protected override fun loadAudioAsset(
    path: CharArray,
    name: CharArray,
    id: CharArray,
  ): Int {
    TODO("Implement loadAudioAsset")
  }
}

public typealias CachingResourceProviderINHERITED = ResourceProviderProxyBase

public typealias DataURIResourceProviderProxyINHERITED = ResourceProviderProxyBase
