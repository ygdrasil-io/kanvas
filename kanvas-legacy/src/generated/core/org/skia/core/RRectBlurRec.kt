package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkCachedData
import org.skia.foundation.SkMask
import org.skia.pdf.Key
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * struct RRectBlurRec : public SkResourceCache::Rec {
 *     RRectBlurRec(RRectBlurKey key, const SkMask& mask, SkCachedData* data)
 *         : fKey(key), fValue({{nullptr, mask.fBounds, mask.fRowBytes, mask.fFormat}, data})
 *     {
 *         fValue.fData->attachToCacheAndRef();
 *     }
 *     ~RRectBlurRec() override {
 *         fValue.fData->detachFromCacheAndUnref();
 *     }
 *
 *     RRectBlurKey   fKey;
 *     MaskValue      fValue;
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override { return sizeof(*this) + fValue.fData->size(); }
 *     const char* getCategory() const override { return "rrect-blur"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override {
 *         return fValue.fData->diagnostic_only_getDiscardable();
 *     }
 *
 *     static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextData) {
 *         const RRectBlurRec& rec = static_cast<const RRectBlurRec&>(baseRec);
 *         SkTLazy<MaskValue>* result = (SkTLazy<MaskValue>*)contextData;
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
public open class RRectBlurRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * RRectBlurKey   fKey
   * ```
   */
  public var fKey: RRectBlurKey,
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
   * RRectBlurRec(RRectBlurKey key, const SkMask& mask, SkCachedData* data)
   *         : fKey(key), fValue({{nullptr, mask.fBounds, mask.fRowBytes, mask.fFormat}, data})
   *     {
   *         fValue.fData->attachToCacheAndRef();
   *     }
   * ```
   */
  public constructor(
    key: RRectBlurKey,
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
   * const char* getCategory() const override { return "rrect-blur"; }
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
     *         const RRectBlurRec& rec = static_cast<const RRectBlurRec&>(baseRec);
     *         SkTLazy<MaskValue>* result = (SkTLazy<MaskValue>*)contextData;
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
