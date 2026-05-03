package org.skia.modules

import Request.Hash
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.`external`.UBreakIterator
import org.skia.core.THashMap
import org.skia.foundation.SkRefCntBase
import org.skia.foundation.SkSp
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkIcuBreakIteratorCache final {
 *     struct Request final {
 *         Request(SkUnicode::BreakType type, const char* icuLocale)
 *             : fType(type)
 *             , fIcuLocale(icuLocale)
 *             , hash(SkGoodHash()(type) ^ SkGoodHash()(fIcuLocale))
 *         {}
 *         const SkUnicode::BreakType fType;
 *         const SkString fIcuLocale;
 *         const uint32_t hash;
 *         struct Hash {
 *             uint32_t operator()(const Request& key) const {
 *                 return key.hash;
 *             }
 *         };
 *         bool operator==(const Request& that) const {
 *             return this->fType == that.fType && this->fIcuLocale == that.fIcuLocale;
 *         }
 *     };
 *     /* Every holder of this class is referencing the same (logical) break iterator.
 *      * Due to caching, the actual break iterator may come and go.
 *      */
 *     class BreakIteratorRef final {
 *     public:
 *         BreakIteratorRef(ICUBreakIterator iter) : breakIterator(iter.release()), fRefCnt(1) {
 *             ++Instances;
 *         }
 *         BreakIteratorRef(SkRefCntBase&&) = delete;
 *         BreakIteratorRef(const SkRefCntBase&) = delete;
 *         BreakIteratorRef& operator=(SkRefCntBase&&) = delete;
 *         BreakIteratorRef& operator=(const SkRefCntBase&) = delete;
 *         ~BreakIteratorRef() {
 *             if (breakIterator) {
 *                 ubrk_close_wrapper(breakIterator);
 *             }
 *         }
 *
 *         void ref() const {
 *             SkASSERT(fRefCnt > 0);
 *             ++fRefCnt;
 *         }
 *         void unref() const {
 *             SkASSERT(fRefCnt > 0);
 *             if (1 == fRefCnt--) {
 *                 delete this;
 *                 --Instances;
 *             }
 *         }
 *
 *         UBreakIterator* breakIterator;
 *         static int32_t GetInstanceCount() { return Instances; }
 *     private:
 *         mutable int32_t fRefCnt;
 *         static int32_t Instances;
 *     };
 *     THashMap<Request, sk_sp<BreakIteratorRef>, Request::Hash> fRequestCache;
 *     SkMutex fCacheMutex;
 *
 *     void purgeIfNeeded() {
 *         // If there are too many requests remove some (oldest first?)
 *         // This may free some break iterators
 *         if (fRequestCache.count() > 100) {
 *             // remove the oldest requests
 *             fRequestCache.reset();
 *         }
 *         // If there are still too many break iterators remove some (oldest first?)
 *         if (BreakIteratorRef::GetInstanceCount() > 4) {
 *             // delete the oldest break iterators and set the references to nullptr
 *             for (auto&& [key, value] : fRequestCache) {
 *                 if (value->breakIterator) {
 *                     sk_ubrk_close(value->breakIterator);
 *                     value->breakIterator = nullptr;
 *                 }
 *             }
 *         }
 *     }
 *
 *  public:
 *     static SkIcuBreakIteratorCache& get() {
 *         static SkIcuBreakIteratorCache instance;
 *         return instance;
 *     }
 *
 *     ICUBreakIterator makeBreakIterator(SkUnicode::BreakType type, const char* bcp47) {
 *         SkAutoMutexExclusive lock(fCacheMutex);
 *         UErrorCode status = U_ZERO_ERROR;
 *
 *         // Get ICU locale for BCP47 langtag
 *         char localeIDStorage[ULOC_FULLNAME_CAPACITY];
 *         const char* localeID = nullptr;
 *         if (bcp47) {
 *             sk_uloc_forLanguageTag(bcp47, localeIDStorage, ULOC_FULLNAME_CAPACITY, nullptr, &status);
 *             if (U_FAILURE(status)) {
 *                 SkDEBUGF("Break error could not get language tag: %s", sk_u_errorName(status));
 *             } else if (localeIDStorage[0]) {
 *                 localeID = localeIDStorage;
 *             }
 *         }
 *         if (!localeID) {
 *             localeID = sk_uloc_getDefault();
 *         }
 *
 *         auto make = [](const Request& request) -> UBreakIterator* {
 *             UErrorCode status = U_ZERO_ERROR;
 *             UBreakIterator* bi = sk_ubrk_open(convertType(request.fType),
 *                                               request.fIcuLocale.c_str(),
 *                                               nullptr, 0, &status);
 *             if (U_FAILURE(status)) {
 *                 SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             }
 *             return bi;
 *         };
 *
 *         auto clone = [](const UBreakIterator* existing) -> ICUBreakIterator {
 *             if (!existing) {
 *                 return nullptr;
 *             }
 *
 *             UErrorCode status = U_ZERO_ERROR;
 *             ICUBreakIterator clone(sk_ubrk_clone(existing, &status));
 *             if (U_FAILURE(status)) {
 *                 SkDEBUGF("Break error: %s", sk_u_errorName(status));
 *             }
 *             return clone;
 *         };
 *
 *         Request request(type, localeID);
 *
 *         // See if this request is already in the cache
 *         const sk_sp<BreakIteratorRef>* ref = fRequestCache.find(request);
 *         if (ref) {
 *             // See if the breakIterator needs to be re-created
 *             if (!(*ref)->breakIterator) {
 *                 (*ref)->breakIterator = make(request);
 *             }
 *             return clone((*ref)->breakIterator);
 *         }
 *
 *         // This request was not in the cache, create an iterator.
 *         ICUBreakIterator newIter(make(request));
 *         if (!newIter) {
 *             return nullptr;
 *         }
 *
 *         sk_sp<BreakIteratorRef> newRef;
 *
 *         // Check if the new iterator is a duplicate
 *         // Android doesn't expose ubrk_getLocaleByType so there is no means of de-duplicating.
 *         // ubrk_getAvailable seems like it should work, but the implementation is just every locale.
 *         if (SkGetICULib()->f_ubrk_getLocaleByType) {
 *             const char* actualLocale = SkGetICULib()->f_ubrk_getLocaleByType(
 *                                            newIter.get(), ULOC_ACTUAL_LOCALE, &status);
 *             // Android doesn't expose ubrk_getLocaleByType so a wrapper may return an error.
 *             if (!U_FAILURE(status)) {
 *                 if (!actualLocale) {
 *                     actualLocale = "";
 *                 }
 *                 // If the actual locale is the same as the requested locale we know there is no entry.
 *                 if (strcmp(actualLocale, localeID) != 0) {
 *                     Request actualRequest(type, actualLocale);
 *                     const sk_sp<BreakIteratorRef>* actualRef = fRequestCache.find(actualRequest);
 *                     if (actualRef) {
 *                         if (!(*actualRef)->breakIterator) {
 *                             (*actualRef)->breakIterator = newIter.release();
 *                         }
 *                         actualRef = fRequestCache.set(request, *actualRef);
 *                         return clone((*actualRef)->breakIterator);
 *                     } else {
 *                         this->purgeIfNeeded();
 *                         newRef = sk_make_sp<BreakIteratorRef>(std::move(newIter));
 *                         fRequestCache.set(actualRequest, newRef);
 *                     }
 *                 }
 *             }
 *         }
 *
 *         if (!newRef) {
 *             this->purgeIfNeeded();
 *             newRef = sk_make_sp<BreakIteratorRef>(std::move(newIter));
 *         }
 *         fRequestCache.set(request, newRef);
 *
 *         return clone(newRef->breakIterator);
 *     }
 * }
 * ```
 */
public data class SkIcuBreakIteratorCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * THashMap<Request, sk_sp<BreakIteratorRef>, Request::Hash> fRequestCache
   * ```
   */
  private var fRequestCache: THashMap<undefined.Request, SkSp<undefined.BreakIteratorRef>, Hash>,
  /**
   * C++ original:
   * ```cpp
   * SkMutex fCacheMutex
   * ```
   */
  private var fCacheMutex: SkMutex,
) {
  /**
   * C++ original:
   * ```cpp
   * void purgeIfNeeded() {
   *         // If there are too many requests remove some (oldest first?)
   *         // This may free some break iterators
   *         if (fRequestCache.count() > 100) {
   *             // remove the oldest requests
   *             fRequestCache.reset();
   *         }
   *         // If there are still too many break iterators remove some (oldest first?)
   *         if (BreakIteratorRef::GetInstanceCount() > 4) {
   *             // delete the oldest break iterators and set the references to nullptr
   *             for (auto&& [key, value] : fRequestCache) {
   *                 if (value->breakIterator) {
   *                     sk_ubrk_close(value->breakIterator);
   *                     value->breakIterator = nullptr;
   *                 }
   *             }
   *         }
   *     }
   * ```
   */
  private fun purgeIfNeeded() {
    TODO("Implement purgeIfNeeded")
  }

  /**
   * C++ original:
   * ```cpp
   * ICUBreakIterator makeBreakIterator(SkUnicode::BreakType type, const char* bcp47) {
   *         SkAutoMutexExclusive lock(fCacheMutex);
   *         UErrorCode status = U_ZERO_ERROR;
   *
   *         // Get ICU locale for BCP47 langtag
   *         char localeIDStorage[ULOC_FULLNAME_CAPACITY];
   *         const char* localeID = nullptr;
   *         if (bcp47) {
   *             sk_uloc_forLanguageTag(bcp47, localeIDStorage, ULOC_FULLNAME_CAPACITY, nullptr, &status);
   *             if (U_FAILURE(status)) {
   *                 SkDEBUGF("Break error could not get language tag: %s", sk_u_errorName(status));
   *             } else if (localeIDStorage[0]) {
   *                 localeID = localeIDStorage;
   *             }
   *         }
   *         if (!localeID) {
   *             localeID = sk_uloc_getDefault();
   *         }
   *
   *         auto make = [](const Request& request) -> UBreakIterator* {
   *             UErrorCode status = U_ZERO_ERROR;
   *             UBreakIterator* bi = sk_ubrk_open(convertType(request.fType),
   *                                               request.fIcuLocale.c_str(),
   *                                               nullptr, 0, &status);
   *             if (U_FAILURE(status)) {
   *                 SkDEBUGF("Break error: %s", sk_u_errorName(status));
   *             }
   *             return bi;
   *         };
   *
   *         auto clone = [](const UBreakIterator* existing) -> ICUBreakIterator {
   *             if (!existing) {
   *                 return nullptr;
   *             }
   *
   *             UErrorCode status = U_ZERO_ERROR;
   *             ICUBreakIterator clone(sk_ubrk_clone(existing, &status));
   *             if (U_FAILURE(status)) {
   *                 SkDEBUGF("Break error: %s", sk_u_errorName(status));
   *             }
   *             return clone;
   *         };
   *
   *         Request request(type, localeID);
   *
   *         // See if this request is already in the cache
   *         const sk_sp<BreakIteratorRef>* ref = fRequestCache.find(request);
   *         if (ref) {
   *             // See if the breakIterator needs to be re-created
   *             if (!(*ref)->breakIterator) {
   *                 (*ref)->breakIterator = make(request);
   *             }
   *             return clone((*ref)->breakIterator);
   *         }
   *
   *         // This request was not in the cache, create an iterator.
   *         ICUBreakIterator newIter(make(request));
   *         if (!newIter) {
   *             return nullptr;
   *         }
   *
   *         sk_sp<BreakIteratorRef> newRef;
   *
   *         // Check if the new iterator is a duplicate
   *         // Android doesn't expose ubrk_getLocaleByType so there is no means of de-duplicating.
   *         // ubrk_getAvailable seems like it should work, but the implementation is just every locale.
   *         if (SkGetICULib()->f_ubrk_getLocaleByType) {
   *             const char* actualLocale = SkGetICULib()->f_ubrk_getLocaleByType(
   *                                            newIter.get(), ULOC_ACTUAL_LOCALE, &status);
   *             // Android doesn't expose ubrk_getLocaleByType so a wrapper may return an error.
   *             if (!U_FAILURE(status)) {
   *                 if (!actualLocale) {
   *                     actualLocale = "";
   *                 }
   *                 // If the actual locale is the same as the requested locale we know there is no entry.
   *                 if (strcmp(actualLocale, localeID) != 0) {
   *                     Request actualRequest(type, actualLocale);
   *                     const sk_sp<BreakIteratorRef>* actualRef = fRequestCache.find(actualRequest);
   *                     if (actualRef) {
   *                         if (!(*actualRef)->breakIterator) {
   *                             (*actualRef)->breakIterator = newIter.release();
   *                         }
   *                         actualRef = fRequestCache.set(request, *actualRef);
   *                         return clone((*actualRef)->breakIterator);
   *                     } else {
   *                         this->purgeIfNeeded();
   *                         newRef = sk_make_sp<BreakIteratorRef>(std::move(newIter));
   *                         fRequestCache.set(actualRequest, newRef);
   *                     }
   *                 }
   *             }
   *         }
   *
   *         if (!newRef) {
   *             this->purgeIfNeeded();
   *             newRef = sk_make_sp<BreakIteratorRef>(std::move(newIter));
   *         }
   *         fRequestCache.set(request, newRef);
   *
   *         return clone(newRef->breakIterator);
   *     }
   * ```
   */
  public fun makeBreakIterator(type: SkUnicode.BreakType, bcp47: String?): Int {
    TODO("Implement makeBreakIterator")
  }

  public data class Request public constructor(
    public val fType: SkUnicode.BreakType,
    public val fIcuLocale: String,
    public val hash: UInt,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public open class Hash {
      public operator fun invoke(key: undefined.Request): UInt {
        TODO("Implement invoke")
      }
    }
  }

  public data class BreakIteratorRef public constructor(
    public var breakIterator: UBreakIterator?,
    private var fRefCnt: Int,
  ) {
    public fun assign(param0: SkRefCntBase) {
      TODO("Implement assign")
    }

    public fun ref() {
      TODO("Implement ref")
    }

    public fun unref() {
      TODO("Implement unref")
    }

    public companion object {
      private var instances: Int = TODO("Initialize instances")

      public fun getInstanceCount(): Int {
        TODO("Implement getInstanceCount")
      }
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkIcuBreakIteratorCache& get() {
     *         static SkIcuBreakIteratorCache instance;
     *         return instance;
     *     }
     * ```
     */
    public fun `get`(): SkIcuBreakIteratorCache {
      TODO("Implement get")
    }
  }
}
