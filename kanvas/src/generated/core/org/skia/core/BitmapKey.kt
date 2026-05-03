package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct BitmapKey : public SkResourceCache::Key {
 * public:
 *     BitmapKey(const SkBitmapCacheDesc& desc) : fDesc(desc) {
 *         this->init(&gBitmapKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(fDesc.fImageID),
 *                    sizeof(fDesc));
 *     }
 *
 *     const SkBitmapCacheDesc fDesc;
 * }
 * ```
 */
public open class BitmapKey public constructor(
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
   * BitmapKey(const SkBitmapCacheDesc& desc) : fDesc(desc) {
   *         this->init(&gBitmapKeyNamespaceLabel, SkMakeResourceCacheSharedIDForBitmap(fDesc.fImageID),
   *                    sizeof(fDesc));
   *     }
   * ```
   */
  public constructor(desc: SkBitmapCacheDesc) : this() {
    TODO("Implement constructor")
  }
}
