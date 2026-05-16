package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct MipMapKey : public SkResourceCache::Key {
 * public:
 *     MipMapKey(const SkBitmapCacheDesc& desc) : fDesc(desc) {
 *         this->init(&gMipMapKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(fDesc.fImageID),
 *                    sizeof(fDesc));
 *     }
 *
 *     const SkBitmapCacheDesc fDesc;
 * }
 * ```
 */
public open class MipMapKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkBitmapCacheDesc fDesc
   * ```
   */
  public val fDesc: SkBitmapCacheDesc,
) : SkResourceCache.Key(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * MipMapKey(const SkBitmapCacheDesc& desc) : fDesc(desc) {
   *         this->init(&gMipMapKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(fDesc.fImageID),
   *                    sizeof(fDesc));
   *     }
   * ```
   */
  public constructor(desc: SkBitmapCacheDesc) : this() {
    TODO("Implement constructor")
  }
}
