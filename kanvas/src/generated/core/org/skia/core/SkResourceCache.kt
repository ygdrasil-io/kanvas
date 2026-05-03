package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.`external`.Hash
import undefined.RecKey
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * class SkResourceCache {
 * public:
 *     struct Key {
 *         /** Key subclasses must call this after their own fields and data are initialized.
 *          *  All fields and data must be tightly packed.
 *          *  @param nameSpace must be unique per Key subclass.
 *          *  @param sharedID == 0 means ignore this field, does not support group purging.
 *          *  @param dataSize is size of fields and data of the subclass, must be a multiple of 4.
 *          */
 *         void init(void* nameSpace, uint64_t sharedID, size_t dataSize);
 *
 *         /** Returns the size of this key. */
 *         size_t size() const {
 *             return fCount32 << 2;
 *         }
 *
 *         void* getNamespace() const { return fNamespace; }
 *         uint64_t getSharedID() const { return ((uint64_t)fSharedID_hi << 32) | fSharedID_lo; }
 *
 *         // This is only valid after having called init().
 *         uint32_t hash() const { return fHash; }
 *
 *         bool operator==(const Key& other) const {
 *             const uint32_t* a = this->as32();
 *             const uint32_t* b = other.as32();
 *             for (int i = 0; i < fCount32; ++i) {  // (This checks fCount == other.fCount first.)
 *                 if (a[i] != b[i]) {
 *                     return false;
 *                 }
 *             }
 *             return true;
 *         }
 *
 *     private:
 *         int32_t  fCount32;   // local + user contents count32
 *         uint32_t fHash;
 *         // split uint64_t into hi and lo so we don't force ourselves to pad on 32bit machines.
 *         uint32_t fSharedID_lo;
 *         uint32_t fSharedID_hi;
 *         void*    fNamespace; // A unique namespace tag. This is hashed.
 *         /* uint32_t fContents32[] */
 *
 *         const uint32_t* as32() const { return (const uint32_t*)this; }
 *     };
 *
 *     struct Rec {
 *         typedef SkResourceCache::Key Key;
 *
 *         Rec() {}
 *         virtual ~Rec() {}
 *
 *         uint32_t getHash() const { return this->getKey().hash(); }
 *
 *         virtual const Key& getKey() const = 0;
 *         virtual size_t bytesUsed() const = 0;
 *
 *         // Called if the cache needs to purge/remove/delete the Rec. Default returns true.
 *         // Subclass may return false if there are outstanding references to it (e.g. bitmaps).
 *         // Will only be deleted/removed-from-the-cache when this returns true.
 *         virtual bool canBePurged() { return true; }
 *
 *         // A rec is first created/initialized, and then added to the cache. As part of the add(),
 *         // the cache will callback into the rec with postAddInstall, passing in whatever payload
 *         // was passed to add/Add.
 *         //
 *         // This late-install callback exists because the process of add-ing might end up deleting
 *         // the new rec (if an existing rec in the cache has the same key and cannot be purged).
 *         // If the new rec will be deleted during add, the pre-existing one (with the same key)
 *         // will have postAddInstall() called on it instead, so that either way an "install" will
 *         // happen during the add.
 *         virtual void postAddInstall(void*) {}
 *
 *         // for memory usage diagnostics
 *         virtual const char* getCategory() const = 0;
 *         virtual SkDiscardableMemory* diagnostic_only_getDiscardable() const { return nullptr; }
 *
 *     private:
 *         Rec*    fNext;
 *         Rec*    fPrev;
 *
 *         friend class SkResourceCache;
 *     };
 *
 *     // Used with SkMessageBus
 *     struct PurgeSharedIDMessage {
 *         PurgeSharedIDMessage(uint64_t sharedID) : fSharedID(sharedID) {}
 *         uint64_t fSharedID;
 *     };
 *
 *     typedef const Rec* ID;
 *
 *     /**
 *      *  Callback function for find(). If called, the cache will have found a match for the
 *      *  specified Key, and will pass in the corresponding Rec, along with a caller-specified
 *      *  context. The function can read the data in Rec, and copy whatever it likes into context
 *      *  (casting context to whatever it really is).
 *      *
 *      *  The return value determines what the cache will do with the Rec. If the function returns
 *      *  true, then the Rec is considered "valid". If false is returned, the Rec will be considered
 *      *  "stale" and will be purged from the cache.
 *      */
 *     typedef bool (*FindVisitor)(const Rec&, void* context);
 *
 *     /**
 *      *  Returns a locked/pinned SkDiscardableMemory instance for the specified
 *      *  number of bytes, or nullptr on failure.
 *      */
 *     typedef SkDiscardableMemory* (*DiscardableFactory)(size_t bytes);
 *
 *     /*
 *      *  The following static methods are thread-safe wrappers around a global
 *      *  instance of this cache.
 *      */
 *
 *     /**
 *      *  Returns true if the visitor was called on a matching Key, and the visitor returned true.
 *      *
 *      *  Find() will search the cache for the specified Key. If no match is found, return false and
 *      *  do not call the FindVisitor. If a match is found, return whatever the visitor returns.
 *      *  Its return value is interpreted to mean:
 *      *      true  : Rec is valid
 *      *      false : Rec is "stale" -- the cache will purge it.
 *      */
 *     static bool Find(const Key& key, FindVisitor, void* context);
 *     static void Add(Rec*, void* payload = nullptr);
 *
 *     typedef void (*Visitor)(const Rec&, void* context);
 *     // Call the visitor for every Rec in the cache.
 *     static void VisitAll(Visitor, void* context);
 *
 *     static size_t GetTotalBytesUsed();
 *     static size_t GetTotalByteLimit();
 *     static size_t SetTotalByteLimit(size_t newLimit);
 *
 *     static size_t SetSingleAllocationByteLimit(size_t);
 *     static size_t GetSingleAllocationByteLimit();
 *     static size_t GetEffectiveSingleAllocationByteLimit();
 *
 *     static void PurgeAll();
 *     static void CheckMessages();
 *
 *     static void TestDumpMemoryStatistics();
 *
 *     /** Dump memory usage statistics of every Rec in the cache using the
 *         SkTraceMemoryDump interface.
 *      */
 *     static void DumpMemoryStatistics(SkTraceMemoryDump* dump);
 *
 *     /**
 *      *  Returns the DiscardableFactory used by the global cache, or nullptr.
 *      */
 *     static DiscardableFactory GetDiscardableFactory();
 *
 *     static SkCachedData* NewCachedData(size_t bytes);
 *
 *     static void PostPurgeSharedID(uint64_t sharedID);
 *
 *     /**
 *      *  Call SkDebugf() with diagnostic information about the state of the cache
 *      */
 *     static void Dump();
 *
 *     ///////////////////////////////////////////////////////////////////////////
 *
 *     /**
 *      *  Construct the cache to call DiscardableFactory when it
 *      *  allocates memory for the pixels. In this mode, the cache has
 *      *  not explicit budget, and so methods like getTotalBytesUsed()
 *      *  and getTotalByteLimit() will return 0, and setTotalByteLimit
 *      *  will ignore its argument and return 0.
 *      */
 *     SkResourceCache(DiscardableFactory);
 *
 *     /**
 *      *  Construct the cache, allocating memory with malloc, and respect the
 *      *  byteLimit, purging automatically when a new image is added to the cache
 *      *  that pushes the total bytesUsed over the limit. Note: The limit can be
 *      *  changed at runtime with setTotalByteLimit.
 *      */
 *     explicit SkResourceCache(size_t byteLimit);
 *     virtual ~SkResourceCache();
 *
 *     /**
 *      *  Returns true if the visitor was called on a matching Key, and the visitor returned true.
 *      *
 *      *  find() will search the cache for the specified Key. If no match is found, return false and
 *      *  do not call the FindVisitor. If a match is found, return whatever the visitor returns.
 *      *  Its return value is interpreted to mean:
 *      *      true  : Rec is valid
 *      *      false : Rec is "stale" -- the cache will purge it.
 *      */
 *     virtual bool find(const Key&, FindVisitor, void* context) ;
 *     virtual void add(Rec*, void* payload = nullptr);
 *     virtual void visitAll(Visitor, void* context);
 *
 *     virtual size_t getTotalBytesUsed() const { return fTotalBytesUsed; }
 *     virtual size_t getTotalByteLimit() const { return fTotalByteLimit; }
 *
 *     /**
 *      *  This is respected by SkBitmapProcState::possiblyScaleImage.
 *      *  0 is no maximum at all; this is the default.
 *      *  setSingleAllocationByteLimit() returns the previous value.
 *      */
 *     virtual size_t setSingleAllocationByteLimit(size_t maximumAllocationSize);
 *     virtual size_t getSingleAllocationByteLimit() const;
 *     // returns the logical single allocation size (pinning against the budget when the cache
 *     // is not backed by discardable memory.
 *     virtual size_t getEffectiveSingleAllocationByteLimit() const;
 *
 *     /**
 *      *  Set the maximum number of bytes available to this cache. If the current
 *      *  cache exceeds this new value, it will be purged to try to fit within
 *      *  this new limit.
 *      */
 *     virtual size_t setTotalByteLimit(size_t newLimit);
 *
 *     virtual void purgeSharedID(uint64_t sharedID);
 *
 *     virtual void purgeAll() {
 *         this->purgeAsNeeded(true);
 *     }
 *
 *     virtual DiscardableFactory discardableFactory() const { return fDiscardableFactory; }
 *
 *     virtual SkCachedData* newCachedData(size_t bytes);
 *
 *     /**
 *      *  Call SkDebugf() with diagnostic information about the state of the cache
 *      */
 *     virtual void dump() const;
 *
 * private:
 *     Rec*    fHead;
 *     Rec*    fTail;
 *
 *     class Hash;
 *     Hash*   fHash;
 *
 *     DiscardableFactory  fDiscardableFactory;
 *
 *     size_t  fTotalBytesUsed;
 *     size_t  fTotalByteLimit;
 *     size_t  fSingleAllocationByteLimit;
 *     int     fCount;
 *
 *     SkMessageBus<PurgeSharedIDMessage, uint32_t>::Inbox fPurgeSharedIDInbox;
 *
 *     void checkMessages();
 *     void purgeAsNeeded(bool forcePurge = false);
 *
 *     // linklist management
 *     void moveToHead(Rec*);
 *     void addToHead(Rec*);
 *     void release(Rec*);
 *     void remove(Rec*);
 *
 *     void init();    // called by constructors
 *
 * #ifdef SK_DEBUG
 *     void validate() const;
 * #else
 *     void validate() const {}
 * #endif
 * }
 * ```
 */
public open class SkResourceCache public constructor(
  factory: SkResourceCacheDiscardableFactory,
) {
  /**
   * C++ original:
   * ```cpp
   * Rec*    fHead
   * ```
   */
  private var fHead: Rec? = TODO("Initialize fHead")

  /**
   * C++ original:
   * ```cpp
   * Rec*    fTail
   * ```
   */
  private var fTail: Rec? = TODO("Initialize fTail")

  /**
   * C++ original:
   * ```cpp
   * Hash*   fHash
   * ```
   */
  private var fHash: Hash? = TODO("Initialize fHash")

  /**
   * C++ original:
   * ```cpp
   * DiscardableFactory  fDiscardableFactory
   * ```
   */
  private var fDiscardableFactory: SkResourceCacheDiscardableFactory =
      TODO("Initialize fDiscardableFactory")

  /**
   * C++ original:
   * ```cpp
   * size_t  fTotalBytesUsed
   * ```
   */
  private var fTotalBytesUsed: Int = TODO("Initialize fTotalBytesUsed")

  /**
   * C++ original:
   * ```cpp
   * size_t  fTotalByteLimit
   * ```
   */
  private var fTotalByteLimit: Int = TODO("Initialize fTotalByteLimit")

  /**
   * C++ original:
   * ```cpp
   * size_t  fSingleAllocationByteLimit
   * ```
   */
  private var fSingleAllocationByteLimit: Int = TODO("Initialize fSingleAllocationByteLimit")

  /**
   * C++ original:
   * ```cpp
   * int     fCount
   * ```
   */
  private var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * SkMessageBus<PurgeSharedIDMessage, uint32_t>::Inbox fPurgeSharedIDInbox
   * ```
   */
  private var fPurgeSharedIDInbox: Int = TODO("Initialize fPurgeSharedIDInbox")

  /**
   * C++ original:
   * ```cpp
   * SkResourceCache::SkResourceCache(DiscardableFactory factory)
   *         : fPurgeSharedIDInbox(SK_InvalidUniqueID) {
   *     this->init();
   *     fDiscardableFactory = factory;
   * }
   * ```
   */
  public constructor(byteLimit: ULong) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkResourceCache::find(const Key& key, FindVisitor visitor, void* context) {
   *     this->checkMessages();
   *
   *     if (auto found = fHash->find(key)) {
   *         Rec* rec = *found;
   *         if (visitor(*rec, context)) {
   *             this->moveToHead(rec);  // for our LRU
   *             return true;
   *         } else {
   *             this->remove(rec);  // stale
   *             return false;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public open fun find(
    key: Key,
    visitor: SkResourceCacheFindVisitor,
    context: Unit?,
  ): Boolean {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::add(Rec* rec, void* payload) {
   *     this->checkMessages();
   *
   *     SkASSERT(rec);
   *     // See if we already have this key (racy inserts, etc.)
   *     if (Rec** preexisting = fHash->find(rec->getKey())) {
   *         Rec* prev = *preexisting;
   *         if (prev->canBePurged()) {
   *             // if it can be purged, the install may fail, so we have to remove it
   *             this->remove(prev);
   *         } else {
   *             // if it cannot be purged, we reuse it and delete the new one
   *             prev->postAddInstall(payload);
   *             delete rec;
   *             return;
   *         }
   *     }
   *
   *     this->addToHead(rec);
   *     fHash->set(rec);
   *     rec->postAddInstall(payload);
   *
   *     if (gDumpCacheTransactions) {
   *         SkString bytesStr, totalStr;
   *         make_size_str(rec->bytesUsed(), &bytesStr);
   *         make_size_str(fTotalBytesUsed, &totalStr);
   *         SkDebugf("RC:    add %5s %12p key %08x -- total %5s, count %d\n",
   *                  bytesStr.c_str(), rec, rec->getHash(), totalStr.c_str(), fCount);
   *     }
   *
   *     // since the new rec may push us over-budget, we perform a purge check now
   *     this->purgeAsNeeded();
   * }
   * ```
   */
  public open fun add(rec: Rec?, payload: Unit? = null) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::visitAll(Visitor visitor, void* context) {
   *     // go backwards, just like purgeAsNeeded, just to make the code similar.
   *     // could iterate either direction and still be correct.
   *     Rec* rec = fTail;
   *     while (rec) {
   *         visitor(*rec, context);
   *         rec = rec->fPrev;
   *     }
   * }
   * ```
   */
  public open fun visitAll(visitor: SkResourceCacheVisitor, context: Unit?) {
    TODO("Implement visitAll")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getTotalBytesUsed() const { return fTotalBytesUsed; }
   * ```
   */
  public open fun getTotalBytesUsed(): Int {
    TODO("Implement getTotalBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t getTotalByteLimit() const { return fTotalByteLimit; }
   * ```
   */
  public open fun getTotalByteLimit(): Int {
    TODO("Implement getTotalByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkResourceCache::setSingleAllocationByteLimit(size_t newLimit) {
   *     size_t oldLimit = fSingleAllocationByteLimit;
   *     fSingleAllocationByteLimit = newLimit;
   *     return oldLimit;
   * }
   * ```
   */
  public open fun setSingleAllocationByteLimit(maximumAllocationSize: ULong): Int {
    TODO("Implement setSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkResourceCache::getSingleAllocationByteLimit() const {
   *     return fSingleAllocationByteLimit;
   * }
   * ```
   */
  public open fun getSingleAllocationByteLimit(): Int {
    TODO("Implement getSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkResourceCache::getEffectiveSingleAllocationByteLimit() const {
   *     // fSingleAllocationByteLimit == 0 means the caller is asking for our default
   *     size_t limit = fSingleAllocationByteLimit;
   *
   *     // if we're not discardable (i.e. we are fixed-budget) then cap the single-limit
   *     // to our budget.
   *     if (nullptr == fDiscardableFactory) {
   *         if (0 == limit) {
   *             limit = fTotalByteLimit;
   *         } else {
   *             limit = std::min(limit, fTotalByteLimit);
   *         }
   *     }
   *     return limit;
   * }
   * ```
   */
  public open fun getEffectiveSingleAllocationByteLimit(): Int {
    TODO("Implement getEffectiveSingleAllocationByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkResourceCache::setTotalByteLimit(size_t newLimit) {
   *     size_t prevLimit = fTotalByteLimit;
   *     fTotalByteLimit = newLimit;
   *     if (newLimit < prevLimit) {
   *         this->purgeAsNeeded();
   *     }
   *     return prevLimit;
   * }
   * ```
   */
  public open fun setTotalByteLimit(newLimit: ULong): Int {
    TODO("Implement setTotalByteLimit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::purgeSharedID(uint64_t sharedID) {
   *     if (0 == sharedID) {
   *         return;
   *     }
   *
   * #ifdef SK_TRACK_PURGE_SHAREDID_HITRATE
   *     gPurgeCallCounter += 1;
   *     bool found = false;
   * #endif
   *     // go backwards, just like purgeAsNeeded, just to make the code similar.
   *     // could iterate either direction and still be correct.
   *     Rec* rec = fTail;
   *     while (rec) {
   *         Rec* prev = rec->fPrev;
   *         if (rec->getKey().getSharedID() == sharedID) {
   *             // even though the "src" is now dead, caches could still be in-flight, so
   *             // we have to check if it can be removed.
   *             if (rec->canBePurged()) {
   *                 this->remove(rec);
   *             }
   * #ifdef SK_TRACK_PURGE_SHAREDID_HITRATE
   *             found = true;
   * #endif
   *         }
   *         rec = prev;
   *     }
   *
   * #ifdef SK_TRACK_PURGE_SHAREDID_HITRATE
   *     if (found) {
   *         gPurgeHitCounter += 1;
   *     }
   *
   *     SkDebugf("PurgeShared calls=%d hits=%d rate=%g\n", gPurgeCallCounter, gPurgeHitCounter,
   *              gPurgeHitCounter * 100.0 / gPurgeCallCounter);
   * #endif
   * }
   * ```
   */
  public open fun purgeSharedID(sharedID: ULong) {
    TODO("Implement purgeSharedID")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void purgeAll() {
   *         this->purgeAsNeeded(true);
   *     }
   * ```
   */
  public open fun purgeAll() {
    TODO("Implement purgeAll")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual DiscardableFactory discardableFactory() const { return fDiscardableFactory; }
   * ```
   */
  public open fun discardableFactory(): SkResourceCacheDiscardableFactory {
    TODO("Implement discardableFactory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCachedData* SkResourceCache::newCachedData(size_t bytes) {
   *     this->checkMessages();
   *
   *     if (fDiscardableFactory) {
   *         SkDiscardableMemory* dm = fDiscardableFactory(bytes);
   *         return dm ? new SkCachedData(bytes, dm) : nullptr;
   *     } else {
   *         return new SkCachedData(sk_malloc_throw(bytes), bytes);
   *     }
   * }
   * ```
   */
  public open fun newCachedData(bytes: ULong): SkCachedData {
    TODO("Implement newCachedData")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::dump() const {
   *     this->validate();
   *
   *     SkDebugf("SkResourceCache: count=%d bytes=%zu %s\n",
   *              fCount, fTotalBytesUsed, fDiscardableFactory ? "discardable" : "malloc");
   * }
   * ```
   */
  public open fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::checkMessages() {
   *     TArray<PurgeSharedIDMessage> msgs;
   *     fPurgeSharedIDInbox.poll(&msgs);
   *     for (int i = 0; i < msgs.size(); ++i) {
   *         this->purgeSharedID(msgs[i].fSharedID);
   *     }
   * }
   * ```
   */
  private fun checkMessages() {
    TODO("Implement checkMessages")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::purgeAsNeeded(bool forcePurge) {
   *     size_t byteLimit;
   *     int    countLimit;
   *
   *     if (fDiscardableFactory) {
   *         countLimit = SK_DISCARDABLEMEMORY_SCALEDIMAGECACHE_COUNT_LIMIT;
   *         byteLimit = UINT32_MAX;  // no limit based on bytes
   *     } else {
   *         countLimit = SK_MaxS32; // no limit based on count
   *         byteLimit = fTotalByteLimit;
   *     }
   *
   *     Rec* rec = fTail;
   *     while (rec) {
   *         if (!forcePurge && fTotalBytesUsed < byteLimit && fCount < countLimit) {
   *             break;
   *         }
   *
   *         Rec* prev = rec->fPrev;
   *         if (rec->canBePurged()) {
   *             this->remove(rec);
   *         }
   *         rec = prev;
   *     }
   * }
   * ```
   */
  private fun purgeAsNeeded(forcePurge: Boolean = false) {
    TODO("Implement purgeAsNeeded")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::moveToHead(Rec* rec) {
   *     if (fHead == rec) {
   *         return;
   *     }
   *
   *     SkASSERT(fHead);
   *     SkASSERT(fTail);
   *
   *     this->validate();
   *
   *     this->release(rec);
   *
   *     fHead->fPrev = rec;
   *     rec->fNext = fHead;
   *     fHead = rec;
   *
   *     this->validate();
   * }
   * ```
   */
  private fun moveToHead(rec: Rec?) {
    TODO("Implement moveToHead")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::addToHead(Rec* rec) {
   *     this->validate();
   *
   *     rec->fPrev = nullptr;
   *     rec->fNext = fHead;
   *     if (fHead) {
   *         fHead->fPrev = rec;
   *     }
   *     fHead = rec;
   *     if (!fTail) {
   *         fTail = rec;
   *     }
   *     fTotalBytesUsed += rec->bytesUsed();
   *     fCount += 1;
   *
   *     this->validate();
   * }
   * ```
   */
  private fun addToHead(rec: Rec?) {
    TODO("Implement addToHead")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::release(Rec* rec) {
   *     Rec* prev = rec->fPrev;
   *     Rec* next = rec->fNext;
   *
   *     if (!prev) {
   *         SkASSERT(fHead == rec);
   *         fHead = next;
   *     } else {
   *         prev->fNext = next;
   *     }
   *
   *     if (!next) {
   *         fTail = prev;
   *     } else {
   *         next->fPrev = prev;
   *     }
   *
   *     rec->fNext = rec->fPrev = nullptr;
   * }
   * ```
   */
  private fun release(rec: Rec?) {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::remove(Rec* rec) {
   *     SkASSERT(rec->canBePurged());
   *     size_t used = rec->bytesUsed();
   *     SkASSERT(used <= fTotalBytesUsed);
   *
   *     this->release(rec);
   *     fHash->remove(rec->getKey());
   *
   *     fTotalBytesUsed -= used;
   *     fCount -= 1;
   *
   *     //SkDebugf("-RC count [%3d] bytes %d\n", fCount, fTotalBytesUsed);
   *
   *     if (gDumpCacheTransactions) {
   *         SkString bytesStr, totalStr;
   *         make_size_str(used, &bytesStr);
   *         make_size_str(fTotalBytesUsed, &totalStr);
   *         SkDebugf("RC: remove %5s %12p key %08x -- total %5s, count %d\n",
   *                  bytesStr.c_str(), rec, rec->getHash(), totalStr.c_str(), fCount);
   *     }
   *
   *     delete rec;
   * }
   * ```
   */
  private fun remove(rec: Rec?) {
    TODO("Implement remove")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::init() {
   *     fHead = nullptr;
   *     fTail = nullptr;
   *     fHash = new Hash;
   *     fTotalBytesUsed = 0;
   *     fCount = 0;
   *     fSingleAllocationByteLimit = 0;
   *
   *     // One of these should be explicit set by the caller after we return.
   *     fTotalByteLimit = 0;
   *     fDiscardableFactory = nullptr;
   * }
   * ```
   */
  private fun `init`() {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkResourceCache::validate() const {
   *     if (nullptr == fHead) {
   *         SkASSERT(nullptr == fTail);
   *         SkASSERT(0 == fTotalBytesUsed);
   *         return;
   *     }
   *
   *     if (fHead == fTail) {
   *         SkASSERT(nullptr == fHead->fPrev);
   *         SkASSERT(nullptr == fHead->fNext);
   *         SkASSERT(fHead->bytesUsed() == fTotalBytesUsed);
   *         return;
   *     }
   *
   *     SkASSERT(nullptr == fHead->fPrev);
   *     SkASSERT(fHead->fNext);
   *     SkASSERT(nullptr == fTail->fNext);
   *     SkASSERT(fTail->fPrev);
   *
   *     size_t used = 0;
   *     int count = 0;
   *     const Rec* rec = fHead;
   *     while (rec) {
   *         count += 1;
   *         used += rec->bytesUsed();
   *         SkASSERT(used <= fTotalBytesUsed);
   *         rec = rec->fNext;
   *     }
   *     SkASSERT(fCount == count);
   *
   *     rec = fTail;
   *     while (rec) {
   *         SkASSERT(count > 0);
   *         count -= 1;
   *         SkASSERT(used >= rec->bytesUsed());
   *         used -= rec->bytesUsed();
   *         rec = rec->fPrev;
   *     }
   *
   *     SkASSERT(0 == count);
   *     SkASSERT(0 == used);
   * }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  public open class Key public constructor(
    private var fCount32: Int,
    private var fHash: Int,
    private var fSharedIDLo: Int,
    private var fSharedIDHi: Int,
    private var fNamespace: Unit?,
  ) {
    public fun `init`(
      nameSpace: Unit?,
      sharedID: ULong,
      dataSize: ULong,
    ) {
      TODO("Implement init")
    }

    public fun size(): Int {
      TODO("Implement size")
    }

    public fun getNamespace() {
      TODO("Implement getNamespace")
    }

    public fun getSharedID(): Int {
      TODO("Implement getSharedID")
    }

    public fun hash(): Int {
      TODO("Implement hash")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    private fun as32(): Int {
      TODO("Implement as32")
    }
  }

  public abstract class Rec public constructor(
    private var fNext: undefined.Rec?,
    private var fPrev: undefined.Rec?,
  ) {
    public constructor() : this() {
      TODO("Implement constructor")
    }

    public fun getHash(): Int {
      TODO("Implement getHash")
    }

    public abstract fun getKey(): RecKey

    public abstract fun bytesUsed(): Int

    public open fun canBePurged(): Boolean {
      TODO("Implement canBePurged")
    }

    public open fun postAddInstall(param0: Unit?) {
      TODO("Implement postAddInstall")
    }

    public abstract fun getCategory(): Char

    public open fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
      TODO("Implement diagnosticOnlyGetDiscardable")
    }
  }

  public data class PurgeSharedIDMessage public constructor(
    public var fSharedID: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkResourceCache::Find(const Key& key, FindVisitor visitor, void* context) {
     *     return get_cache()->find(key, visitor, context);
     * }
     * ```
     */
    private fun find(
      key: Key,
      visitor: SkResourceCacheFindVisitor,
      context: Unit?,
    ): Boolean {
      TODO("Implement find")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::Add(Rec* rec, void* payload) {
     *     get_cache()->add(rec, payload);
     * }
     * ```
     */
    private fun add(rec: Rec?, payload: Unit? = null) {
      TODO("Implement add")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::VisitAll(Visitor visitor, void* context) {
     *     get_cache()->visitAll(visitor, context);
     * }
     * ```
     */
    private fun visitAll(visitor: SkResourceCacheVisitor, context: Unit?) {
      TODO("Implement visitAll")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::GetTotalBytesUsed() {
     *     return get_cache()->getTotalBytesUsed();
     * }
     * ```
     */
    private fun getTotalBytesUsed(): Int {
      TODO("Implement getTotalBytesUsed")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::GetTotalByteLimit() {
     *     return get_cache()->getTotalByteLimit();
     * }
     * ```
     */
    private fun getTotalByteLimit(): Int {
      TODO("Implement getTotalByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::SetTotalByteLimit(size_t newLimit) {
     *     return get_cache()->setTotalByteLimit(newLimit);
     * }
     * ```
     */
    private fun setTotalByteLimit(newLimit: ULong): Int {
      TODO("Implement setTotalByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::SetSingleAllocationByteLimit(size_t size) {
     *     return get_cache()->setSingleAllocationByteLimit(size);
     * }
     * ```
     */
    private fun setSingleAllocationByteLimit(size: ULong): Int {
      TODO("Implement setSingleAllocationByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::GetSingleAllocationByteLimit() {
     *     return get_cache()->getSingleAllocationByteLimit();
     * }
     * ```
     */
    private fun getSingleAllocationByteLimit(): Int {
      TODO("Implement getSingleAllocationByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * size_t SkResourceCache::GetEffectiveSingleAllocationByteLimit() {
     *     return get_cache()->getEffectiveSingleAllocationByteLimit();
     * }
     * ```
     */
    private fun getEffectiveSingleAllocationByteLimit(): Int {
      TODO("Implement getEffectiveSingleAllocationByteLimit")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::PurgeAll() {
     *     return get_cache()->purgeAll();
     * }
     * ```
     */
    private fun purgeAll() {
      TODO("Implement purgeAll")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::CheckMessages() {
     *     return get_cache()->checkMessages();
     * }
     * ```
     */
    private fun checkMessages() {
      TODO("Implement checkMessages")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::TestDumpMemoryStatistics() {
     *     VisitAll(dump_visitor, nullptr);
     * }
     * ```
     */
    private fun testDumpMemoryStatistics() {
      TODO("Implement testDumpMemoryStatistics")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::DumpMemoryStatistics(SkTraceMemoryDump* dump) {
     *     // Since resource could be backed by malloc or discardable, the cache always dumps detailed
     *     // stats to be accurate.
     *     VisitAll(sk_trace_dump_visitor, dump);
     * }
     * ```
     */
    private fun dumpMemoryStatistics(dump: SkTraceMemoryDump?) {
      TODO("Implement dumpMemoryStatistics")
    }

    /**
     * C++ original:
     * ```cpp
     * SkResourceCache::DiscardableFactory SkResourceCache::GetDiscardableFactory() {
     *     return get_cache()->discardableFactory();
     * }
     * ```
     */
    private fun getDiscardableFactory(): SkResourceCacheDiscardableFactory {
      TODO("Implement getDiscardableFactory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkCachedData* SkResourceCache::NewCachedData(size_t bytes) {
     *     return get_cache()->newCachedData(bytes);
     * }
     * ```
     */
    private fun newCachedData(bytes: ULong): SkCachedData {
      TODO("Implement newCachedData")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::PostPurgeSharedID(uint64_t sharedID) {
     *     if (sharedID) {
     *         SkMessageBus<PurgeSharedIDMessage, uint32_t>::Post(PurgeSharedIDMessage(sharedID));
     *     }
     * }
     * ```
     */
    private fun postPurgeSharedID(sharedID: ULong) {
      TODO("Implement postPurgeSharedID")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkResourceCache::Dump() {
     *     get_cache()->dump();
     * }
     * ```
     */
    private fun dump() {
      TODO("Implement dump")
    }
  }
}
