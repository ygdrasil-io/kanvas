package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkTraceMemoryDump
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class Resource {
 *     enum class RefType {
 *         kUsage, // Counts controlled by `sk_sp` and tracks liveness from external C++ code.
 *         kCommandBuffer, // Incremented in Context::insertRecording, decremented by finish procs.
 *         kCache, // At most 1 ref, added in registerWithCache(), removed on cache shutdown or purge.
 *         kReturnQueue, // At most 1 ref, held while in the cache's return queue.
 *     };
 *
 * public:
 *     Resource(const Resource&) = delete;
 *     Resource(Resource&&) = delete;
 *     Resource& operator=(const Resource&) = delete;
 *     Resource& operator=(Resource&&) = delete;
 *
 *     // Adds a usage ref to the resource. Named ref so we can easily manage usage refs with sk_sp.
 *     void ref() const {
 *         // Only the cache should be able to add the first usage ref to a resource.
 *         this->addRef<RefType::kUsage>();
 *     }
 *
 *     // Removes a usage ref from the resource
 *     void unref() const {
 *         this->removeRef<RefType::kUsage>();
 *     }
 *
 *     // Adds a command buffer ref to the resource
 *     void refCommandBuffer() const {
 *         this->addRef<RefType::kCommandBuffer>();
 *     }
 *
 *     // Removes a command buffer ref from the resource
 *     void unrefCommandBuffer() const {
 *         this->removeRef<RefType::kCommandBuffer>();
 *     }
 *
 *     // Whether the resource is currently in use by the GPU: any resource that is used in a command
 *     // buffer is considered in use by the GPU.
 *     //
 *     // NOTE: This is currently only correct for textures, hence the name. Once the rest of the
 *     // resources use the command buffer ref instead of usage ref appropriately, this can be made
 *     // more generaic.
 *     bool isTextureBusyOnGPU() const {
 *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kCommandBuffer)) != 0;
 *     }
 *
 *     Ownership ownership() const { return fOwnership; }
 *     bool requiresPrepareForReturnToCache() const { return fRequiresPrepareForReturnToCache; }
 *
 *     Budgeted budgeted() const { return fBudgeted; }
 *     Shareable shareable() const { return fShareable; }
 *     const GraphiteResourceKey& key() const { return fKey; }
 *
 *     // Retrieves the amount of GPU memory used by this resource in bytes. It is approximate since we
 *     // aren't aware of additional padding or copies made by the driver.
 *     size_t gpuMemorySize() const { return fGpuMemorySize; }
 *
 *     class UniqueID {
 *     public:
 *         UniqueID() = default;
 *
 *         explicit UniqueID(uint32_t id) : fID(id) {}
 *
 *         uint32_t asUInt() const { return fID; }
 *
 *         bool operator==(const UniqueID& other) const { return fID == other.fID; }
 *         bool operator!=(const UniqueID& other) const { return !(*this == other); }
 *
 *     private:
 *         uint32_t fID = SK_InvalidUniqueID;
 *     };
 *
 *     // Gets an id that is unique for this Resource object. It is static in that it does not change
 *     // when the content of the Resource object changes. This will never return 0.
 *     UniqueID uniqueID() const { return fUniqueID; }
 *
 *     const char* getLabel() const { return fLabel.c_str(); }
 *
 *     // We allow the label on a Resource to change when used for a different function. For example
 *     // when reusing a scratch Texture we can change the label to match callers current use.
 *     void setLabel(std::string_view label) {
 *         if (fLabel == label) {
 *             return;
 *         }
 *
 *         fLabel = label;
 *
 *         if (!fLabel.empty()) {
 *             const std::string fullLabel = "Skia_" + fLabel;
 *             this->setBackendLabel(fullLabel.c_str());
 *         }
 *     }
 *
 *     // Tests whether a object has been abandoned or released. All objects will be in this state
 *     // after their creating Context is destroyed or abandoned.
 *     //
 *     // @return true if the object has been released or abandoned,
 *     //         false otherwise.
 *     // TODO: As of now this function isn't really needed because in freeGpuData we are always
 *     // deleting this object. However, I want to implement all the purging logic first to make sure
 *     // we don't have a use case for calling internalDispose but not wanting to delete the actual
 *     // object yet.
 *     bool wasDestroyed() const { return fSharedContext == nullptr; }
 *
 *     // Describes the type of gpu resource that is represented by the implementing
 *     // class (e.g. texture, buffer, etc).  This data is used for diagnostic
 *     // purposes by dumpMemoryStatistics().
 *     //
 *     // The value returned is expected to be long lived and will not be copied by the caller.
 *     virtual const char* getResourceType() const = 0;
 *
 *     virtual const Texture* asTexture() const { return nullptr; }
 *
 * #if defined(GPU_TEST_UTILS)
 *     bool testingShouldDeleteASAP() const { return fDeleteASAP == DeleteASAP::kYes; }
 * #endif
 *
 * protected:
 *     Resource(const SharedContext*,
 *              Ownership,
 *              size_t gpuMemorySize,
 *              bool reusableRequiresPurgeable = false,
 *              bool requiresPrepareForReturnToCache = false);
 *     virtual ~Resource();
 *
 *     const SharedContext* sharedContext() const { return fSharedContext; }
 *
 *     // Needs to be protected for DawnBuffer's emscripten prepareForReturnToCache
 *     void setDeleteASAP() { fDeleteASAP = DeleteASAP::kYes; }
 *
 * private:
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *     // The following set of functions are only meant to be called by the [Global|Proxy]Cache. We
 *     // don't want them public general users of a Resource, but they also aren't purely internal.
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *     friend class ProxyCache; // for setDeleteASAP and updateAccessTime
 *     friend GlobalCache; // for lastAccessTime and updateAccessTime
 *
 *     enum class DeleteASAP : bool {
 *         kNo = false,
 *         kYes = true,
 *     };
 *
 *     DeleteASAP shouldDeleteASAP() const { return fDeleteASAP; }
 *
 *     // In the ResourceCache this is called whenever a Resource is moved into the purgeableQueue. It
 *     // may also be called by the ProxyCache and GlobalCache to track the time on Resources they are
 *     // holding on to.
 *     void updateAccessTime() { fLastAccess = skgpu::StdSteadyClock::now(); }
 *     skgpu::StdSteadyClock::time_point lastAccessTime() const { return fLastAccess; }
 *
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *     // The following set of functions are only meant to be called by the ResourceCache. We don't
 *     // want them public general users of a Resource, but they also aren't purely internal calls.
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *     friend class ResourceCache;
 *
 *     void setBudgeted(Budgeted budgeted) {
 *         SkASSERT(budgeted == Budgeted::kNo || fOwnership == Ownership::kOwned);
 *         fBudgeted = budgeted;
 *     }
 *     void setShareable(Shareable shareable) {
 *         SkASSERT(shareable == Shareable::kNo || fBudgeted == Budgeted::kYes);
 *         fShareable = shareable;
 *     }
 *
 *     void setAvailableForReuse(bool avail) { fAvailableForReuse = avail; }
 *     bool isAvailableForReuse() const { return fAvailableForReuse; }
 *
 *     uint32_t lastUseToken() const { return fLastUseToken; }
 *     void setLastUseToken(uint32_t token) { fLastUseToken = token; }
 *
 *     void setNextInReturnQueue(Resource* next) {
 *         SkASSERT(this->hasReturnQueueRef());
 *         fNextInReturnQueue = next;
 *     }
 *
 *     int* accessCacheIndex() const { return &fCacheArrayIndex; }
 *     const ResourceCache* cache() const { return fReturnCache.get(); }
 *
 *     // If possible, queries the backend API to check the current allocation size of the gpu
 *     // resource and updates the tracked value. This is specifically useful for Vulkan backends which
 *     // use lazy allocated memory for "memoryless" resources. Ideally that memory should stay zero
 *     // throughout its usage, but certain usage patterns can trigger the device to commit real memory
 *     // to the resource. So this will allow us to have a more accurate tracking of our memory usage.
 *     void updateGpuMemorySize() { fGpuMemorySize = this->onUpdateGpuMemorySize(); }
 *
 *     // Dumps memory usage information for this Resource to traceMemoryDump.
 *     void dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump, bool inPurgeableQueue) const;
 *
 *     /**
 *      * If the resource has a non-shareable key then this gives the resource subclass an opportunity
 *      * to prepare itself to re-enter the cache. The ResourceCache extends its privilege to take the
 *      * first UsageRef to this function via takeRef. If takeRef is called this resource will not
 *      * immediately enter the cache but will be re-reprocessed when the usage ref count again reaches
 *      * zero.
 *      *
 *      * Return true if takeRef() was invoked.
 *      */
 *     virtual bool prepareForReturnToCache(const std::function<void()>& takeRef) { return false; }
 *
 *     // Adds a cache ref to the resource. May only be called once.
 *     void registerWithCache(sk_sp<ResourceCache>, const GraphiteResourceKey&, Budgeted, Shareable);
 *
 *     // This version of ref allows adding a ref when the usage count is 0. This should only be called
 *     // from the ResourceCache.
 *     void initialUsageRef() const {
 *         this->addRef<RefType::kUsage, /*MustHaveUsageRefs=*/false>();
 *     }
 *
 *     // Removes a cache ref from the resource. The unref here should only ever be called from the
 *     // ResourceCache and only in the Recorder/Context thread the ResourceCache is part of.
 *     void unrefCache() const {
 *         SkASSERT(fReturnCache);
 *         this->removeRef<RefType::kCache>();
 *     }
 *
 *     // Removes the return queue ref that was held while the Resource was in the queue. This can only
 *     // be called by the ResourceCache on its thread. It should not be called after unrefCache().
 *     // It must only be called after the cache has removed the resource from its return queue.
 *     //
 *     // Returns {isReusable, isPurgeable} atomically based on the reference state when the return
 *     // queue ref was removed. `isReusable` is true if all refs affecting reusability were zero
 *     // when the queue ref was removed. `isPurgeable` is true if all usage and command buffer refs
 *     // were zero.
 *     //
 *     // If true is returned there are no other refs that could trigger a reusable or purgeable state
 *     // change. A resource that entered the return queue due to becoming reusable or purgeable only
 *     // happens if that ref count reached zero, so there should be no external ref holder (other than
 *     // the ResourceCache with its separate cache ref). However, unrefReturnQueue() is only called by
 *     // the ResourceCache so it won't be simultaneously handing out usage refs.
 *     //
 *     // If false is returned, it is possible for the resource to immediately become purgeable on
 *     // another thread but since this thread has released its return queue ref, the Resource will
 *     // simply go back in the next return queue.
 *     //
 *     // The cache should track the Resource based on this return value instead of re-checking the
 *     // ref counts as that would not be an atomic operation.
 *     std::tuple<bool, bool, Resource*> unrefReturnQueue() {
 *         // We must reset the fNextInReturnQueue value *before* removing the return queue ref, but we
 *         // need to return the old value to the ResourceCache so that it can continue iterating over
 *         // the linked list.
 *         Resource* next = fNextInReturnQueue;
 *         fNextInReturnQueue = nullptr;
 *
 *         uint64_t origRefs = this->removeRef<RefType::kReturnQueue>();
 *
 *         // Since we should always have a cache ref when this is called, the Resource will never be
 *         // transitioning to having zero refs, although if `true` is returned the cache may choose to
 *         // then drop its cache ref.
 *         SkASSERT((origRefs & RefMask(RefType::kCache)) != 0);
 *         // `fReusableRefMask` always includes the ReturnQueue ref mask, and since we just removed
 *         // the return ref value, `origRefs` also includes the the ReturnQueue ref mask bit. We have
 *         // to compare to the ref mask to detect the case when the actual reusable refs are all zero.
 *         // Since PurgeableMask() does not add the ReturnQueue ref mask, it *can* compare to zero.
 *         return {(origRefs & fReusableRefMask) == RefMask(RefType::kReturnQueue),
 *                 (origRefs & PurgeableMask()) == 0,
 *                 next};
 *     }
 *
 * #if defined(SK_DEBUG) || defined(GPU_TEST_UTILS)
 *     bool hasCacheRef() const {
 *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kCache)) != 0;
 *     }
 *
 *     bool hasReturnQueueRef() const {
 *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kReturnQueue)) != 0;
 *     }
 *
 *     bool inReturnQueue() const {
 *         return this->hasReturnQueueRef() && SkToBool(fNextInReturnQueue);
 *     }
 *
 *     bool isUsableAsScratch() const {
 *         // This is only called by the ResourceCache, so the state of the Resource's refs won't
 *         // be changed by another thread when isReusable is true.
 *         uint64_t origRefs = fRefs.load(std::memory_order_acquire) & ~RefMask(RefType::kReturnQueue);
 *         bool isReusable = (origRefs & fReusableRefMask) == 0;
 *         return fShareable == Shareable::kScratch || (fShareable == Shareable::kNo && isReusable);
 *     }
 *
 *     bool isPurgeable() const {
 *         // This is only called by the ResourceCache on its thread; if the usage and CB ref counts
 *         // are 0, the ResourceCache is the only way in which they can become non-zero again.
 *         return (fRefs.load(std::memory_order_acquire) & PurgeableMask()) == 0;
 *     }
 *
 *     bool isUniquelyHeld() const {
 *         // This intentionally checks that the cache ref and return queue refs are 0, so that fRefs
 *         // is compared to the value it is initialized with.
 *         return fRefs.load(std::memory_order_acquire) == RefIncrement(RefType::kUsage);
 *     }
 *
 *     bool hasAnyRefs() const {
 *         // Because all ref counts are packed into the same atomic, when this load is actually 0
 *         // there are no other threads that can reach the object and add new refs (assuming a raw
 *         // pointer has never leaked).
 *         return fRefs.load(std::memory_order_acquire) != 0;
 *     }
 * #endif
 *
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *     // The remaining calls are meant to be truely private (including virtuals for subclasses)
 *     ///////////////////////////////////////////////////////////////////////////////////////////////
 *
 *     // Overridden to free GPU resources in the backend API.
 *     virtual void freeGpuData() = 0;
 *
 *     // Overridden to call any release callbacks, if necessary
 *     virtual void invokeReleaseProc() {}
 *
 *     // Overridden to set the label on the underlying GPU resource
 *     virtual void setBackendLabel(char const* label) {}
 *
 *     // Overridden to add extra information to the memory dump.
 *     virtual void onDumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump,
 *                                         const char* dumpName) const {}
 *
 *
 *     // Overridden to calculate a more up-to-date size in bytes.
 *     virtual size_t onUpdateGpuMemorySize() { return fGpuMemorySize; }
 *
 *     // Try to add the Resource to the cache's return queue for pending reuse.
 *     // This should only be called when there is a cache to return to, and the calling thread
 *     // successfully transitioned from no "return queue" ref to setting the return queue ref.
 *     //
 *     // Returns true if the cache accepted the Resource (in which case the set return ref should
 *     // remain set for the cache to remove). If false is returned, the caller should clear the
 *     // return queue ref (and possible dispose of the object).
 *     bool returnToCache() const;
 *
 *     // Frees the object in the underlying 3D API *and* deletes the object itself.
 *     void internalDispose();
 *
 *     // Resource tracks its different ref counts packed into a single atomic 64-bit value.
 *     // The bits are split into subfields:
 *     // commandBufferRefs:31 e.g. RefMask(kCB)
 *     // usageRefs:31              RefMask(kUsage)
 *     // returnQueueRef: 1         RefMask(kReturnQueue)
 *     // cacheRefs:1               RefMask(kCache)
 *     //
 *     // RefIncrement() and RefMask() help access specific ref type's values.
 *     static constexpr uint64_t RefIncrement(RefType refType) {
 *         switch (refType) {
 *             case RefType::kCommandBuffer: return (uint64_t) 1 << 33;
 *             case RefType::kUsage:         return (uint64_t) 1 << 2;
 *             case RefType::kCache:         return (uint64_t) 1 << 1;
 *             case RefType::kReturnQueue:   return (uint64_t) 1 << 0;
 *         }
 *         SkUNREACHABLE;
 *     }
 *     static inline constexpr uint64_t RefMask(RefType refType) {
 *         switch (refType) {
 *             case RefType::kCommandBuffer: return (((uint64_t)1 << 31) - 1) << 33;
 *             case RefType::kUsage:         return (((uint64_t)1 << 31) - 1) << 2;
 *             case RefType::kCache:         return 0b10;
 *             case RefType::kReturnQueue:   return 0b01;
 *         }
 *         SkUNREACHABLE;
 *     }
 *     static inline constexpr uint64_t PurgeableMask() {
 *         return RefMask(RefType::kUsage) | RefMask(RefType::kCommandBuffer);
 *     }
 *
 *     template <RefType kType, bool MustHaveUsageRefs=true>
 *     void addRef() const {
 *         static_assert(kType != RefType::kReturnQueue, "return queue refs cannot be added directly");
 *         static constexpr uint64_t kRefIncrement = RefIncrement(kType);
 *         // No barrier required
 *         [[maybe_unused]] uint64_t origCnt =
 *                 fRefs.fetch_add(kRefIncrement, std::memory_order_relaxed);
 *         // Require that there was an already held usage ref in order to add this new ref,
 *         // e.g. to add a command buffer ref, a usage ref must already be held; calling code can't
 *         // add usage refs if it wasn't explicitly handed out by the cache.
 *         SkASSERT(!MustHaveUsageRefs || (origCnt & RefMask(RefType::kUsage)) > 0);
 *         // And make sure that the specific type of ref did not overflow into another field
 *         SkASSERT((RefMask(kType) - (origCnt & RefMask(kType))) >= kRefIncrement);
 *     }
 *
 *     template <RefType kType>
 *     uint64_t removeRef() const {
 *         static constexpr uint64_t kRefIncrement = RefIncrement(kType);
 *
 *         uint64_t origRefs;
 *         if (kType == RefType::kCache || kType == RefType::kReturnQueue || !fReturnCache) {
 *             // Without a ResourceCache, or when it's a cache/return-queue unref, there is no
 *             // non-atomic work that has to happen so simply update the ref count. If the net ref
 *             // count reaches 0 we can safely delete the resource because no other thread will
 *             // increase the refs.
 *             origRefs = fRefs.fetch_sub(kRefIncrement, std::memory_order_acq_rel);
 *             SkASSERT((origRefs & RefMask(kType)) >= kRefIncrement); // had a ref to remove
 *
 *             if (origRefs == kRefIncrement) {
 *                 SkASSERT(!this->hasAnyRefs());
 *                 Resource* mutableThis = const_cast<Resource*>(this);
 *                 mutableThis->internalDispose();
 *             }
 *         } else {
 *             SkASSERT(kType == RefType::kCommandBuffer || kType == RefType::kUsage);
 *             // When removing a usage or CB ref and the resource is registered with the cache,
 *             // it may need to be returned to the cache. A resource can only be in the return queue
 *             // a single time and must remain alive until cache removes it from the queue. A CAS
 *             // loop is used to atomically decrement the ref and add the return queue ref.
 *             uint64_t nextRefs;
 *             bool needsReturn;
 *             do {
 *                 origRefs = fRefs.load(std::memory_order_acquire);
 *                 SkASSERT((origRefs & RefMask(kType)) >= kRefIncrement); // have a ref to remove
 *
 *                 // When unreffing a usage or command buffer ref, the Resource needs to return to
 *                 // the queue when:
 *                 //  - it's not already in the return queue (return queue ref is 0) AND
 *                 //  - it's transitioning from non-reusable -> reusable OR non-purgeable -> purgeable
 *                 //
 *                 // Including RefMask(kReturnQueue) in the bitwise &'s before comparing to the
 *                 // ref increment ensures that the return queue ref was 0 in origRefs.
 *                 static constexpr uint64_t kPurgeableReturnMask = PurgeableMask() |
 *                                                                  RefMask(RefType::kReturnQueue);
 *                 // fReusableRefMask should have added this bit added during construction.
 *                 SkASSERT((fReusableRefMask & RefMask(RefType::kReturnQueue)) != 0);
 *                 // This expression matches the above logic for returning because:
 *                 //  - Both kPurgeableReturnMask and fReusableRefMask include the return queue bit,
 *                 //    but kRefIncrement does not. The only way the comparisons can be true is if
 *                 //    the return queue bit is unset.
 *                 //  - When the resource is reusable only when purgeable, then both sides of the ||
 *                 //    are identical because fReusableRefMask will equal kPurgeableReturnMask.
 *                 //    And if the == returns true, we know origRefs was non-zero and nextRefs will
 *                 //    be zero since it subtracts kRefIncrement.
 *                 //  - When the resource is reusable when just the usage refs reach 0, the purgeable
 *                 //    state transition works like before. But fReusableRefMask will mask out any
 *                 //    non-zero bits in the command buffer subfield.
 *                 //      - When kRefType==kUsage, the right-hand == will be true when origRefs had
 *                 //        one usage ref left and nextRefs holds zero.
 *                 //      - When kRefType==kCommandBuffer, the right-hand side of the || will always
 *                 //        be false because kRefIncrement will hold bits outside of fReusableRefMask.
 *                 //        This ensures that the non-reusable -> reusable transition occurs solely
 *                 //        on removing a usage ref.
 *                 SkASSERT((kRefIncrement & RefMask(RefType::kReturnQueue)) == 0);
 *                 needsReturn = ((origRefs & kPurgeableReturnMask) == kRefIncrement) ||
 *                               ((origRefs & fReusableRefMask) == kRefIncrement);
 *
 *                 nextRefs = (origRefs - kRefIncrement) |
 *                            (needsReturn ? RefMask(RefType::kReturnQueue) : 0);
 *                 // If origRefs already included a return queue ref, nextRefs hasn't changed that
 *                 SkASSERT((origRefs & RefMask(RefType::kReturnQueue)) ==
 *                          (nextRefs & RefMask(RefType::kReturnQueue)) || needsReturn);
 *             } while (!fRefs.compare_exchange_weak(origRefs, nextRefs,
 *                                                   std::memory_order_release,
 *                                                   std::memory_order_relaxed));
 *             // NOTE: because RefMask(RefType::kReturnQueue) was included in the `needsReturn` check,
 *             // we know that it was unset in `origRefs`, and was added to `nextRefs`. The CAS ensures
 *             // that this was the thread that added the return queue ref if `needsReturn` is true
 *             // when the do-while loop exits.
 *
 *             if (needsReturn && !this->returnToCache()) {
 *                 // The cache rejected the resource, so we need to unset the "return queue" ref that
 *                 // we added above, which may be the last ref keeping the object alive.
 *                 SkASSERT(!fNextInReturnQueue);
 *                 origRefs = this->removeRef<RefType::kReturnQueue>();
 *                 // so do not access *this* after this point!
 *             }
 *             // else we weren't returning the resource yet, or the cache is maintaining the return
 *             // ref until the return queue has been drained.
 *         }
 *
 *         return origRefs;
 *     }
 *
 *     static constexpr size_t kInvalidGpuMemorySize = ~static_cast<size_t>(0);
 *
 *     // See RefIncrement() for how the bits in this field are interpreted.
 *     mutable std::atomic<uint64_t> fRefs;
 *
 *     // Depending on when the resource can be reused, there are two base values:
 *     // 1. RefMask(kUsage): reused while there is outstanding GPU work (CB ref count is ignored).
 *     // 2. RefMask(kUsage) | RefMask(kCB): cannot be reused until it is also purgeable.
 *     // To simplify logic in removeRef(), this value always includes RefMask(kReturnQueue).
 *     // See removeRef() for rationale.
 *     //
 *     // NOTE: Reusability is related but distinct from shareability. Shareability takes into account
 *     // external information about when and for how long the state of the resource must remain stable
 *     // We track when all resources become "reusable" again even if they were fully shareable because
 *     // that marks when the resource can also change its Shareable type.
 *     const uint64_t fReusableRefMask;
 *
 *     // This is not ref'ed but internalDispose() will be called before the Gpu object is destroyed.
 *     // That call will set this to nullptr.
 *     const SharedContext* fSharedContext;
 *
 *     const UniqueID fUniqueID;
 *     const Ownership fOwnership;
 *     const bool fRequiresPrepareForReturnToCache;
 *
 *     // The resource key and return cache are both set at most once, during registerWithCache().
 *     /*const*/ GraphiteResourceKey  fKey;
 *     /*const*/ sk_sp<ResourceCache> fReturnCache;
 *
 *     // Resources added to their return cache's queue are tracked in a lock-free thread-safe
 *     // singly-linked list whose head element is stored on the cache, and next elements are stored
 *     // inline in Resource. This can only be modified by the thread that set the return queue ref,
 *     // or by the thread that is removing said ref.
 *     //
 *     // A null value means the Resource is not in the return queue. A non-null value means it is in
 *     // the queue, although the ResourceCache assigns a special sentinel value for the tail address.
 *     Resource* fNextInReturnQueue = nullptr;
 *
 *     // The remaining fields are mutable state that is only modified by the ResourceCache on the
 *     // cache's thread, guarded by `fReturnCache::fSingleOwner`.
 *
 *     size_t fGpuMemorySize = kInvalidGpuMemorySize;
 *
 *     // All resources created internally by Graphite that are held in the ResourceCache as shared or
 *     // available scratch resources are considered budgeted. Resources that back client-owned objects
 *     // (e.g. SkSurface or SkImage) and wrapper objects (e.g. BackendTexture) do not count against
 *     // cache limits and therefore should never be budgeted.
 *     Budgeted fBudgeted = Budgeted::kNo;
 *     // All resources start out as non-shareable (the strictest mode) and revert to non-shareable
 *     // when they are returned to the cache and have no more usage refs. An available resource can
 *     // be returned if its shareable type matches the request, or if it was non-shareable at which
 *     // point the resource is upgraded to the more permissive mode (until all shared usages are
 *     // dropped at which point it can be used for any purpose again).
 *     Shareable fShareable = Shareable::kNo;
 *
 *     // This is only used by ProxyCache::purgeProxiesNotUsedSince which is called from
 *     // ResourceCache::purgeResourcesNotUsedSince. When kYes, this signals that the Resource
 *     // should've been purged based on its timestamp at some point regardless of what its
 *     // current timestamp may indicate (since the timestamp will be updated when the Resource
 *     // is returned to the ResourceCache).
 *     DeleteASAP fDeleteASAP = DeleteASAP::kNo;
 *
 *     // Set to true when the resource is contained in its cache's `fResourceMap`, which allows it to
 *     // be returned from findAndRefResource().
 *     bool fAvailableForReuse = false;
 *
 *     // An index into a heap when this resource is purgeable or an array when not. This is maintained
 *     // by the cache. Must be mutable to fit SkTDPQueue's access API.
 *     mutable int fCacheArrayIndex = -1;
 *
 *     // This value reflects how recently this resource was accessed in the cache. This is maintained
 *     // by the cache. It defines a total order over resources, even if their fLastAccess times are
 *     // the same (i.e. returned at time points less than the system's granularity).
 *     uint32_t fLastUseToken;
 *     skgpu::StdSteadyClock::time_point fLastAccess;
 *
 *     // String used to describe the current use of this Resource.
 *     std::string fLabel;
 * }
 * ```
 */
public abstract class Resource public constructor(
  param0: Resource,
) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kInvalidGpuMemorySize = ~static_cast<size_t>(0
   * ```
   */
  private var fRefs: Int = TODO("Initialize fRefs")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<uint64_t> fRefs
   * ```
   */
  private val fReusableRefMask: Int = TODO("Initialize fReusableRefMask")

  /**
   * C++ original:
   * ```cpp
   * const uint64_t fReusableRefMask
   * ```
   */
  private val fSharedContext: SharedContext? = TODO("Initialize fSharedContext")

  /**
   * C++ original:
   * ```cpp
   * const SharedContext* fSharedContext
   * ```
   */
  private val fUniqueID: UniqueID = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * const UniqueID fUniqueID
   * ```
   */
  private val fOwnership: Int = TODO("Initialize fOwnership")

  /**
   * C++ original:
   * ```cpp
   * const Ownership fOwnership
   * ```
   */
  private val fRequiresPrepareForReturnToCache: Boolean =
      TODO("Initialize fRequiresPrepareForReturnToCache")

  /**
   * C++ original:
   * ```cpp
   * const bool fRequiresPrepareForReturnToCache
   * ```
   */
  private var fKey: Int = TODO("Initialize fKey")

  /**
   * C++ original:
   * ```cpp
   * GraphiteResourceKey  fKey
   * ```
   */
  private var fReturnCache: Int = TODO("Initialize fReturnCache")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ResourceCache> fReturnCache
   * ```
   */
  private var fNextInReturnQueue: Resource? = TODO("Initialize fNextInReturnQueue")

  /**
   * C++ original:
   * ```cpp
   * Resource* fNextInReturnQueue = nullptr
   * ```
   */
  private var fGpuMemorySize: Int = TODO("Initialize fGpuMemorySize")

  /**
   * C++ original:
   * ```cpp
   * size_t fGpuMemorySize
   * ```
   */
  private var fBudgeted: Int = TODO("Initialize fBudgeted")

  /**
   * C++ original:
   * ```cpp
   * Budgeted fBudgeted
   * ```
   */
  private var fShareable: Int = TODO("Initialize fShareable")

  /**
   * C++ original:
   * ```cpp
   * Shareable fShareable
   * ```
   */
  private var fDeleteASAP: DeleteASAP = TODO("Initialize fDeleteASAP")

  /**
   * C++ original:
   * ```cpp
   * DeleteASAP fDeleteASAP = DeleteASAP::kNo
   * ```
   */
  private var fAvailableForReuse: Boolean = TODO("Initialize fAvailableForReuse")

  /**
   * C++ original:
   * ```cpp
   * bool fAvailableForReuse = false
   * ```
   */
  private var fCacheArrayIndex: Int = TODO("Initialize fCacheArrayIndex")

  /**
   * C++ original:
   * ```cpp
   * mutable int fCacheArrayIndex = -1
   * ```
   */
  private var fLastUseToken: Int = TODO("Initialize fLastUseToken")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fLastUseToken
   * ```
   */
  private var fLastAccess: Int = TODO("Initialize fLastAccess")

  /**
   * C++ original:
   * ```cpp
   * skgpu::StdSteadyClock::time_point fLastAccess
   * ```
   */
  private var fLabel: Int = TODO("Initialize fLabel")

  /**
   * C++ original:
   * ```cpp
   * Resource(const Resource&) = delete
   * ```
   */
  public constructor(
    param0: SharedContext,
    param1: Ownership,
    gpuMemorySize: ULong,
    reusableRequiresPurgeable: Boolean,
    requiresPrepareForReturnToCache: Boolean,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Resource(Resource&&) = delete
   * ```
   */
  public constructor(
    sharedContext: SharedContext?,
    ownership: Ownership,
    gpuMemorySize: ULong,
    reusableRequiresPurgeable: Boolean,
    requiresPrepareForReturnToCache: Boolean,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Resource& operator=(const Resource&) = delete
   * ```
   */
  public fun assign(param0: Resource) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * Resource& operator=(Resource&&) = delete
   * ```
   */
  public fun ref() {
    TODO("Implement ref")
  }

  /**
   * C++ original:
   * ```cpp
   * void ref() const {
   *         // Only the cache should be able to add the first usage ref to a resource.
   *         this->addRef<RefType::kUsage>();
   *     }
   * ```
   */
  public fun unref() {
    TODO("Implement unref")
  }

  /**
   * C++ original:
   * ```cpp
   * void unref() const {
   *         this->removeRef<RefType::kUsage>();
   *     }
   * ```
   */
  public fun refCommandBuffer() {
    TODO("Implement refCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void refCommandBuffer() const {
   *         this->addRef<RefType::kCommandBuffer>();
   *     }
   * ```
   */
  public fun unrefCommandBuffer() {
    TODO("Implement unrefCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void unrefCommandBuffer() const {
   *         this->removeRef<RefType::kCommandBuffer>();
   *     }
   * ```
   */
  public fun isTextureBusyOnGPU(): Boolean {
    TODO("Implement isTextureBusyOnGPU")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isTextureBusyOnGPU() const {
   *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kCommandBuffer)) != 0;
   *     }
   * ```
   */
  public fun ownership(): Int {
    TODO("Implement ownership")
  }

  /**
   * C++ original:
   * ```cpp
   * Ownership ownership() const { return fOwnership; }
   * ```
   */
  public fun requiresPrepareForReturnToCache(): Boolean {
    TODO("Implement requiresPrepareForReturnToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requiresPrepareForReturnToCache() const { return fRequiresPrepareForReturnToCache; }
   * ```
   */
  public fun budgeted(): Int {
    TODO("Implement budgeted")
  }

  /**
   * C++ original:
   * ```cpp
   * Budgeted budgeted() const { return fBudgeted; }
   * ```
   */
  public fun shareable(): Int {
    TODO("Implement shareable")
  }

  /**
   * C++ original:
   * ```cpp
   * Shareable shareable() const { return fShareable; }
   * ```
   */
  public fun key(): Int {
    TODO("Implement key")
  }

  /**
   * C++ original:
   * ```cpp
   * const GraphiteResourceKey& key() const { return fKey; }
   * ```
   */
  public fun gpuMemorySize(): Int {
    TODO("Implement gpuMemorySize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t gpuMemorySize() const { return fGpuMemorySize; }
   * ```
   */
  private fun uniqueID(): UniqueID {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * UniqueID uniqueID() const { return fUniqueID; }
   * ```
   */
  private fun getLabel(): Char {
    TODO("Implement getLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getLabel() const { return fLabel.c_str(); }
   * ```
   */
  private fun setLabel(label: String) {
    TODO("Implement setLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLabel(std::string_view label) {
   *         if (fLabel == label) {
   *             return;
   *         }
   *
   *         fLabel = label;
   *
   *         if (!fLabel.empty()) {
   *             const std::string fullLabel = "Skia_" + fLabel;
   *             this->setBackendLabel(fullLabel.c_str());
   *         }
   *     }
   * ```
   */
  private fun wasDestroyed(): Boolean {
    TODO("Implement wasDestroyed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool wasDestroyed() const { return fSharedContext == nullptr; }
   * ```
   */
  private abstract fun getResourceType(): Char

  /**
   * C++ original:
   * ```cpp
   * virtual const char* getResourceType() const = 0
   * ```
   */
  public open fun asTexture(): Texture {
    TODO("Implement asTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const Texture* asTexture() const { return nullptr; }
   * ```
   */
  private fun testingShouldDeleteASAP(): Boolean {
    TODO("Implement testingShouldDeleteASAP")
  }

  /**
   * C++ original:
   * ```cpp
   * bool testingShouldDeleteASAP() const { return fDeleteASAP == DeleteASAP::kYes; }
   * ```
   */
  protected fun sharedContext(): SharedContext {
    TODO("Implement sharedContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const SharedContext* sharedContext() const { return fSharedContext; }
   * ```
   */
  protected fun setDeleteASAP() {
    TODO("Implement setDeleteASAP")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDeleteASAP() { fDeleteASAP = DeleteASAP::kYes; }
   * ```
   */
  private fun shouldDeleteASAP(): DeleteASAP {
    TODO("Implement shouldDeleteASAP")
  }

  /**
   * C++ original:
   * ```cpp
   * DeleteASAP shouldDeleteASAP() const { return fDeleteASAP; }
   * ```
   */
  private fun updateAccessTime() {
    TODO("Implement updateAccessTime")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateAccessTime() { fLastAccess = skgpu::StdSteadyClock::now(); }
   * ```
   */
  private fun lastAccessTime(): Int {
    TODO("Implement lastAccessTime")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::StdSteadyClock::time_point lastAccessTime() const { return fLastAccess; }
   * ```
   */
  private fun setBudgeted(budgeted: Budgeted) {
    TODO("Implement setBudgeted")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBudgeted(Budgeted budgeted) {
   *         SkASSERT(budgeted == Budgeted::kNo || fOwnership == Ownership::kOwned);
   *         fBudgeted = budgeted;
   *     }
   * ```
   */
  private fun setShareable(shareable: Shareable) {
    TODO("Implement setShareable")
  }

  /**
   * C++ original:
   * ```cpp
   * void setShareable(Shareable shareable) {
   *         SkASSERT(shareable == Shareable::kNo || fBudgeted == Budgeted::kYes);
   *         fShareable = shareable;
   *     }
   * ```
   */
  private fun setAvailableForReuse(avail: Boolean) {
    TODO("Implement setAvailableForReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAvailableForReuse(bool avail) { fAvailableForReuse = avail; }
   * ```
   */
  private fun isAvailableForReuse(): Boolean {
    TODO("Implement isAvailableForReuse")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAvailableForReuse() const { return fAvailableForReuse; }
   * ```
   */
  private fun lastUseToken(): Int {
    TODO("Implement lastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t lastUseToken() const { return fLastUseToken; }
   * ```
   */
  private fun setLastUseToken(token: UInt) {
    TODO("Implement setLastUseToken")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLastUseToken(uint32_t token) { fLastUseToken = token; }
   * ```
   */
  private fun setNextInReturnQueue(next: Resource?) {
    TODO("Implement setNextInReturnQueue")
  }

  /**
   * C++ original:
   * ```cpp
   * void setNextInReturnQueue(Resource* next) {
   *         SkASSERT(this->hasReturnQueueRef());
   *         fNextInReturnQueue = next;
   *     }
   * ```
   */
  private fun accessCacheIndex(): Int {
    TODO("Implement accessCacheIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int* accessCacheIndex() const { return &fCacheArrayIndex; }
   * ```
   */
  private fun cache(): ResourceCache {
    TODO("Implement cache")
  }

  /**
   * C++ original:
   * ```cpp
   * const ResourceCache* cache() const { return fReturnCache.get(); }
   * ```
   */
  private fun updateGpuMemorySize() {
    TODO("Implement updateGpuMemorySize")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateGpuMemorySize() { fGpuMemorySize = this->onUpdateGpuMemorySize(); }
   * ```
   */
  private fun dumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?, inPurgeableQueue: Boolean) {
    TODO("Implement dumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * void Resource::dumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump,
   *                                     bool inPurgeableQueue) const {
   *     if (this->ownership() == Ownership::kWrapped && !traceMemoryDump->shouldDumpWrappedObjects()) {
   *         return;
   *     }
   *
   *     if (this->budgeted() == skgpu::Budgeted::kNo &&
   *         !traceMemoryDump->shouldDumpUnbudgetedObjects()) {
   *         return;
   *     }
   *
   *     size_t size = this->gpuMemorySize();
   *
   *     // Dump zero-sized objects (e.g. Samplers, pipelines, etc) per traceMemoryDump implementation.
   *     // Always dump memoryless textures.
   *     if (size == 0 && !traceMemoryDump->shouldDumpSizelessObjects() &&
   *         this->asTexture() == nullptr) {
   *         return;
   *     }
   *
   *     SkString resourceName("skia/gpu_resources/resource_");
   *     resourceName.appendU32(this->uniqueID().asUInt());
   *
   *     traceMemoryDump->dumpNumericValue(resourceName.c_str(), "size", "bytes", size);
   *     traceMemoryDump->dumpStringValue(resourceName.c_str(), "type", this->getResourceType());
   *     traceMemoryDump->dumpStringValue(resourceName.c_str(), "label", this->getLabel());
   *     if (inPurgeableQueue) {
   *         traceMemoryDump->dumpNumericValue(resourceName.c_str(), "purgeable_size", "bytes", size);
   *     }
   *     if (traceMemoryDump->shouldDumpWrappedObjects()) {
   *         traceMemoryDump->dumpWrappedState(resourceName.c_str(),
   *                                           this->ownership() == Ownership::kWrapped);
   *     }
   *     if (traceMemoryDump->shouldDumpUnbudgetedObjects()) {
   *         traceMemoryDump->dumpBudgetedState(resourceName.c_str(),
   *                                            this->budgeted() == skgpu::Budgeted::kYes);
   *     }
   *
   *     this->onDumpMemoryStatistics(traceMemoryDump, resourceName.c_str());
   *
   *     // TODO: implement this to report real gpu id backing the resource. Will be virtual implemented
   *     // by backend specific resource subclasses.
   *     //this->setMemoryBacking(traceMemoryDump, resourceName);
   * }
   * ```
   */
  public open fun prepareForReturnToCache(takeRef: () -> Unit): Boolean {
    TODO("Implement prepareForReturnToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool prepareForReturnToCache(const std::function<void()>& takeRef) { return false; }
   * ```
   */
  private fun registerWithCache(
    returnCache: SkSp<ResourceCache>,
    key: GraphiteResourceKey,
    initialBudgetedState: Budgeted,
    initialShareableState: Shareable,
  ) {
    TODO("Implement registerWithCache")
  }

  /**
   * C++ original:
   * ```cpp
   * void Resource::registerWithCache(sk_sp<ResourceCache> returnCache,
   *                                  const GraphiteResourceKey& key,
   *                                  Budgeted initialBudgetedState,
   *                                  Shareable initialShareableState) {
   *     // ResourceCache should be registered before the Resource escapes the ResourceProvider, e.g. it
   *     // has a single usage ref and no others.
   *     SkASSERT(this->isUniquelyHeld());
   *     SkASSERT(!fReturnCache);
   *     SkASSERT(returnCache);
   *
   *     fKey = key;
   *     fReturnCache = std::move(returnCache);
   *
   *     this->addRef<RefType::kCache>();
   *
   *     this->setBudgeted(initialBudgetedState);
   *     this->setShareable(initialShareableState);
   * }
   * ```
   */
  private fun initialUsageRef() {
    TODO("Implement initialUsageRef")
  }

  /**
   * C++ original:
   * ```cpp
   * void initialUsageRef() const {
   *         this->addRef<RefType::kUsage, /*MustHaveUsageRefs=*/false>();
   *     }
   * ```
   */
  private fun unrefCache() {
    TODO("Implement unrefCache")
  }

  /**
   * C++ original:
   * ```cpp
   * void unrefCache() const {
   *         SkASSERT(fReturnCache);
   *         this->removeRef<RefType::kCache>();
   *     }
   * ```
   */
  private fun unrefReturnQueue(): Int {
    TODO("Implement unrefReturnQueue")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, bool, Resource*> unrefReturnQueue() {
   *         // We must reset the fNextInReturnQueue value *before* removing the return queue ref, but we
   *         // need to return the old value to the ResourceCache so that it can continue iterating over
   *         // the linked list.
   *         Resource* next = fNextInReturnQueue;
   *         fNextInReturnQueue = nullptr;
   *
   *         uint64_t origRefs = this->removeRef<RefType::kReturnQueue>();
   *
   *         // Since we should always have a cache ref when this is called, the Resource will never be
   *         // transitioning to having zero refs, although if `true` is returned the cache may choose to
   *         // then drop its cache ref.
   *         SkASSERT((origRefs & RefMask(RefType::kCache)) != 0);
   *         // `fReusableRefMask` always includes the ReturnQueue ref mask, and since we just removed
   *         // the return ref value, `origRefs` also includes the the ReturnQueue ref mask bit. We have
   *         // to compare to the ref mask to detect the case when the actual reusable refs are all zero.
   *         // Since PurgeableMask() does not add the ReturnQueue ref mask, it *can* compare to zero.
   *         return {(origRefs & fReusableRefMask) == RefMask(RefType::kReturnQueue),
   *                 (origRefs & PurgeableMask()) == 0,
   *                 next};
   *     }
   * ```
   */
  private fun hasCacheRef(): Boolean {
    TODO("Implement hasCacheRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasCacheRef() const {
   *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kCache)) != 0;
   *     }
   * ```
   */
  private fun hasReturnQueueRef(): Boolean {
    TODO("Implement hasReturnQueueRef")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasReturnQueueRef() const {
   *         return (fRefs.load(std::memory_order_acquire) & RefMask(RefType::kReturnQueue)) != 0;
   *     }
   * ```
   */
  private fun inReturnQueue(): Boolean {
    TODO("Implement inReturnQueue")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inReturnQueue() const {
   *         return this->hasReturnQueueRef() && SkToBool(fNextInReturnQueue);
   *     }
   * ```
   */
  private fun isUsableAsScratch(): Boolean {
    TODO("Implement isUsableAsScratch")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isUsableAsScratch() const {
   *         // This is only called by the ResourceCache, so the state of the Resource's refs won't
   *         // be changed by another thread when isReusable is true.
   *         uint64_t origRefs = fRefs.load(std::memory_order_acquire) & ~RefMask(RefType::kReturnQueue);
   *         bool isReusable = (origRefs & fReusableRefMask) == 0;
   *         return fShareable == Shareable::kScratch || (fShareable == Shareable::kNo && isReusable);
   *     }
   * ```
   */
  private fun isPurgeable(): Boolean {
    TODO("Implement isPurgeable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isPurgeable() const {
   *         // This is only called by the ResourceCache on its thread; if the usage and CB ref counts
   *         // are 0, the ResourceCache is the only way in which they can become non-zero again.
   *         return (fRefs.load(std::memory_order_acquire) & PurgeableMask()) == 0;
   *     }
   * ```
   */
  private fun isUniquelyHeld(): Boolean {
    TODO("Implement isUniquelyHeld")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isUniquelyHeld() const {
   *         // This intentionally checks that the cache ref and return queue refs are 0, so that fRefs
   *         // is compared to the value it is initialized with.
   *         return fRefs.load(std::memory_order_acquire) == RefIncrement(RefType::kUsage);
   *     }
   * ```
   */
  private fun hasAnyRefs(): Boolean {
    TODO("Implement hasAnyRefs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasAnyRefs() const {
   *         // Because all ref counts are packed into the same atomic, when this load is actually 0
   *         // there are no other threads that can reach the object and add new refs (assuming a raw
   *         // pointer has never leaked).
   *         return fRefs.load(std::memory_order_acquire) != 0;
   *     }
   * ```
   */
  private abstract fun freeGpuData()

  /**
   * C++ original:
   * ```cpp
   * virtual void freeGpuData() = 0
   * ```
   */
  public open fun invokeReleaseProc() {
    TODO("Implement invokeReleaseProc")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void invokeReleaseProc() {}
   * ```
   */
  public open fun setBackendLabel(label: String?) {
    TODO("Implement setBackendLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void setBackendLabel(char const* label) {}
   * ```
   */
  public open fun onDumpMemoryStatistics(traceMemoryDump: SkTraceMemoryDump?, dumpName: String?) {
    TODO("Implement onDumpMemoryStatistics")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onDumpMemoryStatistics(SkTraceMemoryDump* traceMemoryDump,
   *                                         const char* dumpName) const {}
   * ```
   */
  public open fun onUpdateGpuMemorySize(): Int {
    TODO("Implement onUpdateGpuMemorySize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual size_t onUpdateGpuMemorySize() { return fGpuMemorySize; }
   * ```
   */
  private fun returnToCache(): Boolean {
    TODO("Implement returnToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Resource::returnToCache() const {
   *     // No resource should have been destroyed if there was still any sort of ref on it.
   *     SkASSERT(!this->wasDestroyed());
   *
   *     // Not all resources are registered with the cache, but returnToCache() should only be called
   *     // when they have been registered.
   *     SkASSERT(fReturnCache);
   *     // In order to be returned, the Resource's "return queue" ref bit must be set. Its cache ref
   *     // may not be set if the cache has been shut down (but `fReturnCache` remains valid and just
   *     // returns false to reject the resource return).
   *     SkASSERT(this->hasReturnQueueRef());
   *     return fReturnCache->returnResource(const_cast<Resource*>(this));
   * }
   * ```
   */
  private fun internalDispose() {
    TODO("Implement internalDispose")
  }

  /**
   * C++ original:
   * ```cpp
   * void Resource::internalDispose() {
   *     SkASSERT(fSharedContext);
   *     this->invokeReleaseProc();
   *     this->freeGpuData();
   *     fSharedContext = nullptr;
   *     // TODO: If we ever support freeing all the backend objects without deleting the object, we'll
   *     // need to add a hasAnyRefs() check here.
   *     delete this;
   * }
   * ```
   */
  private fun <kType, MustHaveUsageRefs> addRef() {
    TODO("Implement addRef")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <RefType kType, bool MustHaveUsageRefs=true>
   *     void addRef() const {
   *         static_assert(kType != RefType::kReturnQueue, "return queue refs cannot be added directly");
   *         static constexpr uint64_t kRefIncrement = RefIncrement(kType);
   *         // No barrier required
   *         [[maybe_unused]] uint64_t origCnt =
   *                 fRefs.fetch_add(kRefIncrement, std::memory_order_relaxed);
   *         // Require that there was an already held usage ref in order to add this new ref,
   *         // e.g. to add a command buffer ref, a usage ref must already be held; calling code can't
   *         // add usage refs if it wasn't explicitly handed out by the cache.
   *         SkASSERT(!MustHaveUsageRefs || (origCnt & RefMask(RefType::kUsage)) > 0);
   *         // And make sure that the specific type of ref did not overflow into another field
   *         SkASSERT((RefMask(kType) - (origCnt & RefMask(kType))) >= kRefIncrement);
   *     }
   * ```
   */
  private fun <kType> removeRef(): Int {
    TODO("Implement removeRef")
  }

  public data class UniqueID public constructor(
    private var fID: Int,
  ) {
    public fun asUInt(): Int {
      TODO("Implement asUInt")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }

  public enum class RefType {
    kUsage,
    kCommandBuffer,
    kCache,
    kReturnQueue,
  }

  public enum class DeleteASAP {
    kNo,
    kYes,
  }

  public companion object {
    private val kInvalidGpuMemorySize: Int = TODO("Initialize kInvalidGpuMemorySize")

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint64_t RefIncrement(RefType refType) {
     *         switch (refType) {
     *             case RefType::kCommandBuffer: return (uint64_t) 1 << 33;
     *             case RefType::kUsage:         return (uint64_t) 1 << 2;
     *             case RefType::kCache:         return (uint64_t) 1 << 1;
     *             case RefType::kReturnQueue:   return (uint64_t) 1 << 0;
     *         }
     *         SkUNREACHABLE;
     *     }
     * ```
     */
    private fun refIncrement(refType: RefType): Int {
      TODO("Implement refIncrement")
    }

    /**
     * C++ original:
     * ```cpp
     * static inline constexpr uint64_t RefMask(RefType refType) {
     *         switch (refType) {
     *             case RefType::kCommandBuffer: return (((uint64_t)1 << 31) - 1) << 33;
     *             case RefType::kUsage:         return (((uint64_t)1 << 31) - 1) << 2;
     *             case RefType::kCache:         return 0b10;
     *             case RefType::kReturnQueue:   return 0b01;
     *         }
     *         SkUNREACHABLE;
     *     }
     * ```
     */
    private fun refMask(refType: RefType): Int {
      TODO("Implement refMask")
    }

    /**
     * C++ original:
     * ```cpp
     * static inline constexpr uint64_t PurgeableMask() {
     *         return RefMask(RefType::kUsage) | RefMask(RefType::kCommandBuffer);
     *     }
     * ```
     */
    private fun purgeableMask(): Int {
      TODO("Implement purgeableMask")
    }
  }
}
