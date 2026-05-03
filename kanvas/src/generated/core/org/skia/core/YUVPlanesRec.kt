package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.Unit
import org.skia.pdf.Key
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * struct YUVPlanesRec : public SkResourceCache::Rec {
 *     YUVPlanesRec(YUVPlanesKey key, SkCachedData* data, const SkYUVAPixmaps& pixmaps)
 *         : fKey(key)
 *     {
 *         fValue.fData = data;
 *         fValue.fPixmaps = pixmaps;
 *         fValue.fData->attachToCacheAndRef();
 *     }
 *     ~YUVPlanesRec() override {
 *         fValue.fData->detachFromCacheAndUnref();
 *     }
 *
 *     YUVPlanesKey  fKey;
 *     YUVValue      fValue;
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return sizeof(*this) + fValue.fData->size(); }
 *     const char* getCategory() const override { return "yuv-planes"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
 *         return fValue.fData->diagnostic_only_getDiscardable();
 *     }
 *
 *     static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextData) {
 *         const YUVPlanesRec& rec = static_cast<const YUVPlanesRec&>(baseRec);
 *         YUVValue* result = static_cast<YUVValue*>(contextData);
 *
 *         SkCachedData* tmpData = rec.fValue.fData;
 *         tmpData->ref();
 *         if (nullptr == tmpData->data()) {
 *             tmpData->unref();
 *             return false;
 *         }
 *         result->fData = tmpData;
 *         result->fPixmaps = rec.fValue.fPixmaps;
 *         return true;
 *     }
 * }
 * ```
 */
public open class YUVPlanesRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * YUVPlanesKey  fKey
   * ```
   */
  public var fKey: YUVPlanesKey,
  /**
   * C++ original:
   * ```cpp
   * YUVValue      fValue
   * ```
   */
  public var fValue: YUVValue,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * YUVPlanesRec(YUVPlanesKey key, SkCachedData* data, const SkYUVAPixmaps& pixmaps)
   *         : fKey(key)
   *     {
   *         fValue.fData = data;
   *         fValue.fPixmaps = pixmaps;
   *         fValue.fData->attachToCacheAndRef();
   *     }
   * ```
   */
  public constructor(
    key: YUVPlanesKey,
    `data`: SkCachedData?,
    pixmaps: SkYUVAPixmaps,
  ) : this() {
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
   * size_t bytesUsed() const override { return sizeof(*this) + fValue.fData->size(); }
   * ```
   */
  public override fun bytesUsed(): Int {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "yuv-planes"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
   *         return fValue.fData->diagnostic_only_getDiscardable();
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
     * static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextData) {
     *         const YUVPlanesRec& rec = static_cast<const YUVPlanesRec&>(baseRec);
     *         YUVValue* result = static_cast<YUVValue*>(contextData);
     *
     *         SkCachedData* tmpData = rec.fValue.fData;
     *         tmpData->ref();
     *         if (nullptr == tmpData->data()) {
     *             tmpData->unref();
     *             return false;
     *         }
     *         result->fData = tmpData;
     *         result->fPixmaps = rec.fValue.fPixmaps;
     *         return true;
     *     }
     * ```
     */
    public fun visitor(baseRec: SkResourceCache.Rec, contextData: Unit?): Boolean {
      TODO("Implement visitor")
    }
  }
}
