package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import undefined.SkDiscardableMemory
import undefined.SkMutex

/**
 * C++ original:
 * ```cpp
 * class SkCachedData : ::SkNoncopyable {
 * public:
 *     SkCachedData(void* mallocData, size_t size);
 *     SkCachedData(size_t size, SkDiscardableMemory*);
 *     virtual ~SkCachedData();
 *
 *     size_t size() const { return fSize; }
 *     const void* data() const { return fData; }
 *
 *     void* writable_data() { return fData; }
 *
 *     void ref() const { this->internalRef(false); }
 *     void unref() const { this->internalUnref(false); }
 *
 *     int testing_only_getRefCnt() const { return fRefCnt; }
 *     bool testing_only_isLocked() const { return fIsLocked; }
 *     bool testing_only_isInCache() const { return fInCache; }
 *
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const {
 *         return kDiscardableMemory_StorageType == fStorageType ? fStorage.fDM : nullptr;
 *     }
 *
 * protected:
 *     // called when fData changes. could be nullptr.
 *     virtual void onDataChange(void* oldData, void* newData) {}
 *
 * private:
 *     SkMutex fMutex;     // could use a pool of these...
 *
 *     enum StorageType {
 *         kDiscardableMemory_StorageType,
 *         kMalloc_StorageType
 *     };
 *
 *     union {
 *         SkDiscardableMemory*    fDM;
 *         void*                   fMalloc;
 *     } fStorage;
 *     void*       fData;
 *     size_t      fSize;
 *     int         fRefCnt;    // low-bit means we're owned by the cache
 *     StorageType fStorageType;
 *     bool        fInCache;
 *     bool        fIsLocked;
 *
 *     void internalRef(bool fromCache) const;
 *     void internalUnref(bool fromCache) const;
 *
 *     void inMutexRef(bool fromCache);
 *     bool inMutexUnref(bool fromCache);  // returns true if we should delete "this"
 *     void inMutexLock();
 *     void inMutexUnlock();
 *
 *     // called whenever our fData might change (lock or unlock)
 *     void setData(void* newData) {
 *         if (newData != fData) {
 *             // notify our subclasses of the change
 *             this->onDataChange(fData, newData);
 *             fData = newData;
 *         }
 *     }
 *
 *     class AutoMutexWritable;
 *
 * public:
 * #ifdef SK_DEBUG
 *     void validate() const;
 * #else
 *     void validate() const {}
 * #endif
 *
 *    /*
 *      *  Attaching a data to to a SkResourceCache (only one at a time) enables the data to be
 *      *  unlocked when the cache is the only owner, thus freeing it to be purged (assuming the
 *      *  data is backed by a SkDiscardableMemory).
 *      *
 *      *  When attached, it also automatically attempts to "lock" the data when the first client
 *      *  ref's the data (typically from a find(key, visitor) call).
 *      *
 *      *  Thus the data will always be "locked" when a non-cache has a ref on it (whether or not
 *      *  the lock succeeded to recover the memory -- check data() to see if it is nullptr).
 *      */
 *
 *     /*
 *      *  Call when adding this instance to a SkResourceCache::Rec subclass
 *      *  (typically in the Rec's constructor).
 *      */
 *     void attachToCacheAndRef() const { this->internalRef(true); }
 *
 *     /*
 *      *  Call when removing this instance from a SkResourceCache::Rec subclass
 *      *  (typically in the Rec's destructor).
 *      */
 *     void detachFromCacheAndUnref() const { this->internalUnref(true); }
 * }
 * ```
 */
public open class SkCachedData public constructor(
  mallocData: Unit?,
  size: ULong,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * SkMutex fMutex
   * ```
   */
  private var fMutex: SkMutex = TODO("Initialize fMutex")

  /**
   * C++ original:
   * ```cpp
   * union {
   *         SkDiscardableMemory*    fDM;
   *         void*                   fMalloc;
   *     } fStorage
   * ```
   */
  private var fStorage: Any = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * void*       fData
   * ```
   */
  private var fData: Unit? = TODO("Initialize fData")

  /**
   * C++ original:
   * ```cpp
   * size_t      fSize
   * ```
   */
  private var fSize: Int = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * int         fRefCnt
   * ```
   */
  private var fRefCnt: Int = TODO("Initialize fRefCnt")

  /**
   * C++ original:
   * ```cpp
   * StorageType fStorageType
   * ```
   */
  private var fStorageType: StorageType = TODO("Initialize fStorageType")

  /**
   * C++ original:
   * ```cpp
   * bool        fInCache
   * ```
   */
  private var fInCache: Boolean = TODO("Initialize fInCache")

  /**
   * C++ original:
   * ```cpp
   * bool        fIsLocked
   * ```
   */
  private var fIsLocked: Boolean = TODO("Initialize fIsLocked")

  private var fDM: SkDiscardableMemory? = TODO("Initialize fDM")

  private var fMalloc: Unit? = TODO("Initialize fMalloc")

  /**
   * C++ original:
   * ```cpp
   * SkCachedData::SkCachedData(void* data, size_t size)
   *     : fData(data)
   *     , fSize(size)
   *     , fRefCnt(1)
   *     , fStorageType(kMalloc_StorageType)
   *     , fInCache(false)
   *     , fIsLocked(true)
   * {
   *     fStorage.fMalloc = data;
   * }
   * ```
   */
  public constructor(size: ULong, dm: SkDiscardableMemory?) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fSize; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* data() const { return fData; }
   * ```
   */
  public fun `data`() {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * void* writable_data() { return fData; }
   * ```
   */
  public fun writableData() {
    TODO("Implement writableData")
  }

  /**
   * C++ original:
   * ```cpp
   * void ref() const { this->internalRef(false); }
   * ```
   */
  public fun ref() {
    TODO("Implement ref")
  }

  /**
   * C++ original:
   * ```cpp
   * void unref() const { this->internalUnref(false); }
   * ```
   */
  public fun unref() {
    TODO("Implement unref")
  }

  /**
   * C++ original:
   * ```cpp
   * int testing_only_getRefCnt() const { return fRefCnt; }
   * ```
   */
  public fun testingOnlyGetRefCnt(): Int {
    TODO("Implement testingOnlyGetRefCnt")
  }

  /**
   * C++ original:
   * ```cpp
   * bool testing_only_isLocked() const { return fIsLocked; }
   * ```
   */
  public fun testingOnlyIsLocked(): Boolean {
    TODO("Implement testingOnlyIsLocked")
  }

  /**
   * C++ original:
   * ```cpp
   * bool testing_only_isInCache() const { return fInCache; }
   * ```
   */
  public fun testingOnlyIsInCache(): Boolean {
    TODO("Implement testingOnlyIsInCache")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* diagnostic_only_getDiscardable() const {
   *         return kDiscardableMemory_StorageType == fStorageType ? fStorage.fDM : nullptr;
   *     }
   * ```
   */
  public fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
    TODO("Implement diagnosticOnlyGetDiscardable")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onDataChange(void* oldData, void* newData) {}
   * ```
   */
  protected open fun onDataChange(oldData: Unit?, newData: Unit?) {
    TODO("Implement onDataChange")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::internalRef(bool fromCache) const {
   *     AutoMutexWritable(this)->inMutexRef(fromCache);
   * }
   * ```
   */
  private fun internalRef(fromCache: Boolean) {
    TODO("Implement internalRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::internalUnref(bool fromCache) const {
   *     if (AutoMutexWritable(this)->inMutexUnref(fromCache)) {
   *         // can't delete inside doInternalUnref, since it is locking a mutex (which we own)
   *         delete this;
   *     }
   * }
   * ```
   */
  private fun internalUnref(fromCache: Boolean) {
    TODO("Implement internalUnref")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::inMutexRef(bool fromCache) {
   *     if ((1 == fRefCnt) && fInCache) {
   *         this->inMutexLock();
   *     }
   *
   *     fRefCnt += 1;
   *     if (fromCache) {
   *         SkASSERT(!fInCache);
   *         fInCache = true;
   *     }
   * }
   * ```
   */
  private fun inMutexRef(fromCache: Boolean) {
    TODO("Implement inMutexRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCachedData::inMutexUnref(bool fromCache) {
   *     switch (--fRefCnt) {
   *         case 0:
   *             // we're going to be deleted, so we need to be unlocked (for DiscardableMemory)
   *             if (fIsLocked) {
   *                 this->inMutexUnlock();
   *             }
   *             break;
   *         case 1:
   *             if (fInCache && !fromCache) {
   *                 // If we're down to 1 owner, and that owner is the cache, this it is safe
   *                 // to unlock (and mutate fData) even if the cache is in a different thread,
   *                 // as the cache is NOT allowed to inspect or use fData.
   *                 this->inMutexUnlock();
   *             }
   *             break;
   *         default:
   *             break;
   *     }
   *
   *     if (fromCache) {
   *         SkASSERT(fInCache);
   *         fInCache = false;
   *     }
   *
   *     // return true when we need to be deleted
   *     return 0 == fRefCnt;
   * }
   * ```
   */
  private fun inMutexUnref(fromCache: Boolean): Boolean {
    TODO("Implement inMutexUnref")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::inMutexLock() {
   *     fMutex.assertHeld();
   *
   *     SkASSERT(!fIsLocked);
   *     fIsLocked = true;
   *
   *     switch (fStorageType) {
   *         case kMalloc_StorageType:
   *             this->setData(fStorage.fMalloc);
   *             break;
   *         case kDiscardableMemory_StorageType:
   *             if (fStorage.fDM->lock()) {
   *                 void* ptr = fStorage.fDM->data();
   *                 SkASSERT(ptr);
   *                 this->setData(ptr);
   *             } else {
   *                 this->setData(nullptr);   // signal failure to lock, contents are gone
   *             }
   *             break;
   *     }
   * }
   * ```
   */
  private fun inMutexLock() {
    TODO("Implement inMutexLock")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::inMutexUnlock() {
   *     fMutex.assertHeld();
   *
   *     SkASSERT(fIsLocked);
   *     fIsLocked = false;
   *
   *     switch (fStorageType) {
   *         case kMalloc_StorageType:
   *             // nothing to do/check
   *             break;
   *         case kDiscardableMemory_StorageType:
   *             if (fData) {    // did the previous lock succeed?
   *                 fStorage.fDM->unlock();
   *             }
   *             break;
   *     }
   *     this->setData(nullptr);   // signal that we're in an unlocked state
   * }
   * ```
   */
  private fun inMutexUnlock() {
    TODO("Implement inMutexUnlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void setData(void* newData) {
   *         if (newData != fData) {
   *             // notify our subclasses of the change
   *             this->onDataChange(fData, newData);
   *             fData = newData;
   *         }
   *     }
   * ```
   */
  private fun setData(newData: Unit?) {
    TODO("Implement setData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCachedData::validate() const {
   *     if (fIsLocked) {
   *         SkASSERT((fInCache && fRefCnt > 1) || !fInCache);
   *         switch (fStorageType) {
   *             case kMalloc_StorageType:
   *                 SkASSERT(fData == fStorage.fMalloc);
   *                 break;
   *             case kDiscardableMemory_StorageType:
   *                 // fData can be null or the actual value, depending if DM's lock succeeded
   *                 break;
   *         }
   *     } else {
   *         SkASSERT((fInCache && 1 == fRefCnt) || (0 == fRefCnt));
   *         SkASSERT(nullptr == fData);
   *     }
   * }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * void attachToCacheAndRef() const { this->internalRef(true); }
   * ```
   */
  public fun attachToCacheAndRef() {
    TODO("Implement attachToCacheAndRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void detachFromCacheAndUnref() const { this->internalUnref(true); }
   * ```
   */
  public fun detachFromCacheAndUnref() {
    TODO("Implement detachFromCacheAndUnref")
  }

  public enum class StorageType {
    kDiscardableMemory_StorageType,
    kMalloc_StorageType,
  }
}
