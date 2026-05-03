package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.ULong
import kotlin.Unit
import org.skia.pdf.Key
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * struct MipMapRec : public SkResourceCache::Rec {
 *     MipMapRec(const SkBitmapCacheDesc& desc, const SkMipmap* result)
 *         : fKey(desc)
 *         , fMipMap(result)
 *     {
 *         fMipMap->attachToCacheAndRef();
 *     }
 *
 *     ~MipMapRec() override {
 *         fMipMap->detachFromCacheAndUnref();
 *     }
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return sizeof(fKey) + fMipMap->size(); }
 *     const char* getCategory() const override { return "mipmap"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
 *         return fMipMap->diagnostic_only_getDiscardable();
 *     }
 *
 *     static bool Finder(const SkResourceCache::Rec& baseRec, void* contextMip) {
 *         const MipMapRec& rec = static_cast<const MipMapRec&>(baseRec);
 *         const SkMipmap* mm = SkRef(rec.fMipMap);
 *         // the call to ref() above triggers a "lock" in the case of discardable memory,
 *         // which means we can now check for null (in case the lock failed).
 *         if (nullptr == mm->data()) {
 *             mm->unref();    // balance our call to ref()
 *             return false;
 *         }
 *         // the call must call unref() when they are done.
 *         *(const SkMipmap**)contextMip = mm;
 *         return true;
 *     }
 *
 * private:
 *     MipMapKey       fKey;
 *     const SkMipmap* fMipMap;
 * }
 * ```
 */
public open class MipMapRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * MipMapKey       fKey
   * ```
   */
  private var fKey: MipMapKey,
  /**
   * C++ original:
   * ```cpp
   * const SkMipmap* fMipMap
   * ```
   */
  private val fMipMap: SkMipmap?,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * MipMapRec(const SkBitmapCacheDesc& desc, const SkMipmap* result)
   *         : fKey(desc)
   *         , fMipMap(result)
   *     {
   *         fMipMap->attachToCacheAndRef();
   *     }
   * ```
   */
  public constructor(desc: SkBitmapCacheDesc, result: SkMipmap?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const Key& getKey() const override { return fKey; }
   * ```
   */
  public override fun getKey(): Key {
    TODO("Implement getKey")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesUsed() const override { return sizeof(fKey) + fMipMap->size(); }
   * ```
   */
  public override fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "mipmap"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
   *         return fMipMap->diagnostic_only_getDiscardable();
   *     }
   * ```
   */
  public override fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
    TODO("Implement diagnosticOnlyGetDiscardable")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Finder(const SkResourceCache::Rec& baseRec, void* contextMip) {
     *         const MipMapRec& rec = static_cast<const MipMapRec&>(baseRec);
     *         const SkMipmap* mm = SkRef(rec.fMipMap);
     *         // the call to ref() above triggers a "lock" in the case of discardable memory,
     *         // which means we can now check for null (in case the lock failed).
     *         if (nullptr == mm->data()) {
     *             mm->unref();    // balance our call to ref()
     *             return false;
     *         }
     *         // the call must call unref() when they are done.
     *         *(const SkMipmap**)contextMip = mm;
     *         return true;
     *     }
     * ```
     */
    public fun finder(baseRec: SkResourceCache.Rec, contextMip: Unit?): Boolean {
      TODO("Implement finder")
    }
  }
}
