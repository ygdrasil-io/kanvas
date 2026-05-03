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
 * struct RectsBlurRec : public SkResourceCache::Rec {
 *     RectsBlurRec(RectsBlurKey key, const SkMask& mask, SkCachedData* data)
 *         : fKey(key), fValue({{nullptr, mask.fBounds, mask.fRowBytes, mask.fFormat}, data})
 *     {
 *         fValue.fData->attachToCacheAndRef();
 *     }
 *     ~RectsBlurRec() override {
 *         fValue.fData->detachFromCacheAndUnref();
 *     }
 *
 *     RectsBlurKey   fKey;
 *     MaskValue      fValue;
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return sizeof(*this) + fValue.fData->size(); }
 *     const char* getCategory() const override { return "rects-blur"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
 *         return fValue.fData->diagnostic_only_getDiscardable();
 *     }
 *
 *     static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextData) {
 *         const RectsBlurRec& rec = static_cast<const RectsBlurRec&>(baseRec);
 *         SkTLazy<MaskValue>* result = static_cast<SkTLazy<MaskValue>*>(contextData);
 *
 *         SkCachedData* tmpData = rec.fValue.fData;
 *         tmpData->ref();
 *         if (nullptr == tmpData->data()) {
 *             tmpData->unref();
 *             return false;
 *         }
 *         result->init(rec.fValue);
 *         return true;
 *     }
 * }
 * ```
 */
public open class RectsBlurRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * RectsBlurKey   fKey
   * ```
   */
  public var fKey: RectsBlurKey,
  /**
   * C++ original:
   * ```cpp
   * MaskValue      fValue
   * ```
   */
  public var fValue: MaskValue,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * RectsBlurRec(RectsBlurKey key, const SkMask& mask, SkCachedData* data)
   *         : fKey(key), fValue({{nullptr, mask.fBounds, mask.fRowBytes, mask.fFormat}, data})
   *     {
   *         fValue.fData->attachToCacheAndRef();
   *     }
   * ```
   */
  public constructor(
    key: RectsBlurKey,
    mask: SkMask,
    `data`: SkCachedData?,
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
   * const char* getCategory() const override { return "rects-blur"; }
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
     *         const RectsBlurRec& rec = static_cast<const RectsBlurRec&>(baseRec);
     *         SkTLazy<MaskValue>* result = static_cast<SkTLazy<MaskValue>*>(contextData);
     *
     *         SkCachedData* tmpData = rec.fValue.fData;
     *         tmpData->ref();
     *         if (nullptr == tmpData->data()) {
     *             tmpData->unref();
     *             return false;
     *         }
     *         result->init(rec.fValue);
     *         return true;
     *     }
     * ```
     */
    public fun visitor(baseRec: SkResourceCache.Rec, contextData: Unit?): Boolean {
      TODO("Implement visitor")
    }
  }
}
