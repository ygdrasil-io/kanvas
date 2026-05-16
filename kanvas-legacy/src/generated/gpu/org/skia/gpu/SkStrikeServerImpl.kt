package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkStrikeSpec
import org.skia.core.StrikeForGPU
import org.skia.core.StrikeForGPUCacheInterface
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkSp
import org.skia.utils.SkStrikeServer

/**
 * C++ original:
 * ```cpp
 * class SkStrikeServerImpl final : public sktext::StrikeForGPUCacheInterface {
 * public:
 *     explicit SkStrikeServerImpl(
 *             SkStrikeServer::DiscardableHandleManager* discardableHandleManager);
 *
 *     // SkStrikeServer API methods
 *     void writeStrikeData(std::vector<uint8_t>* memory);
 *
 *     sk_sp<sktext::StrikeForGPU> findOrCreateScopedStrike(const SkStrikeSpec& strikeSpec) override;
 *
 *     // Methods for testing
 *     void setMaxEntriesInDescriptorMapForTesting(size_t count);
 *     size_t remoteStrikeMapSizeForTesting() const;
 *
 * private:
 *     inline static constexpr size_t kMaxEntriesInDescriptorMap = 2000u;
 *
 *     void checkForDeletedEntries();
 *
 *     sk_sp<RemoteStrike> getOrCreateCache(const SkStrikeSpec& strikeSpec);
 *
 *     struct MapOps {
 *         size_t operator()(const SkDescriptor* key) const {
 *             return key->getChecksum();
 *         }
 *         bool operator()(const SkDescriptor* lhs, const SkDescriptor* rhs) const {
 *             return *lhs == *rhs;
 *         }
 *     };
 *
 *     using DescToRemoteStrike =
 *         std::unordered_map<const SkDescriptor*, sk_sp<RemoteStrike>, MapOps, MapOps>;
 *     DescToRemoteStrike fDescToRemoteStrike;
 *
 *     SkStrikeServer::DiscardableHandleManager* const fDiscardableHandleManager;
 *     THashSet<SkTypefaceID> fCachedTypefaces;
 *     size_t fMaxEntriesInDescriptorMap = kMaxEntriesInDescriptorMap;
 *
 *     // State cached until the next serialization.
 *     THashSet<RemoteStrike*> fRemoteStrikesToSend;
 *     std::vector<SkTypefaceProxyPrototype> fTypefacesToSend;
 * }
 * ```
 */
public class SkStrikeServerImpl public constructor(
  discardableHandleManager: SkStrikeServer.DiscardableHandleManager?,
) : StrikeForGPUCacheInterface() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kMaxEntriesInDescriptorMap = 2000u
   * ```
   */
  private var fDescToRemoteStrike: Int = TODO("Initialize fDescToRemoteStrike")

  /**
   * C++ original:
   * ```cpp
   * DescToRemoteStrike fDescToRemoteStrike
   * ```
   */
  private val fDiscardableHandleManager: SkStrikeServer.DiscardableHandleManager? =
      TODO("Initialize fDiscardableHandleManager")

  /**
   * C++ original:
   * ```cpp
   * SkStrikeServer::DiscardableHandleManager* const fDiscardableHandleManager
   * ```
   */
  private var fCachedTypefaces: Int = TODO("Initialize fCachedTypefaces")

  /**
   * C++ original:
   * ```cpp
   * THashSet<SkTypefaceID> fCachedTypefaces
   * ```
   */
  private var fMaxEntriesInDescriptorMap: ULong = TODO("Initialize fMaxEntriesInDescriptorMap")

  /**
   * C++ original:
   * ```cpp
   * size_t fMaxEntriesInDescriptorMap = kMaxEntriesInDescriptorMap
   * ```
   */
  private var fRemoteStrikesToSend: Int = TODO("Initialize fRemoteStrikesToSend")

  /**
   * C++ original:
   * ```cpp
   * THashSet<RemoteStrike*> fRemoteStrikesToSend
   * ```
   */
  private var fTypefacesToSend: Int = TODO("Initialize fTypefacesToSend")

  /**
   * C++ original:
   * ```cpp
   * void writeStrikeData(std::vector<uint8_t>* memory)
   * ```
   */
  public fun writeStrikeData(memory: List<UByte>?) {
    TODO("Implement writeStrikeData")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<StrikeForGPU> SkStrikeServerImpl::findOrCreateScopedStrike(
   *         const SkStrikeSpec& strikeSpec) {
   *     return this->getOrCreateCache(strikeSpec);
   * }
   * ```
   */
  public override fun findOrCreateScopedStrike(strikeSpec: SkStrikeSpec): SkSp<StrikeForGPU> {
    TODO("Implement findOrCreateScopedStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeServerImpl::setMaxEntriesInDescriptorMapForTesting(size_t count) {
   *     fMaxEntriesInDescriptorMap = count;
   * }
   * ```
   */
  public fun setMaxEntriesInDescriptorMapForTesting(count: ULong) {
    TODO("Implement setMaxEntriesInDescriptorMapForTesting")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkStrikeServerImpl::remoteStrikeMapSizeForTesting() const {
   *     return fDescToRemoteStrike.size();
   * }
   * ```
   */
  public fun remoteStrikeMapSizeForTesting(): ULong {
    TODO("Implement remoteStrikeMapSizeForTesting")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikeServerImpl::checkForDeletedEntries() {
   *     auto it = fDescToRemoteStrike.begin();
   *     while (fDescToRemoteStrike.size() > fMaxEntriesInDescriptorMap &&
   *            it != fDescToRemoteStrike.end()) {
   *         RemoteStrike* strike = it->second.get();
   *         if (fDiscardableHandleManager->isHandleDeleted(strike->discardableHandleId())) {
   *             // If we are trying to send the strike, then do not erase it.
   *             if (!fRemoteStrikesToSend.contains(strike)) {
   *                 // Erase returns the iterator following the removed element.
   *                 it = fDescToRemoteStrike.erase(it);
   *                 continue;
   *             }
   *         }
   *         ++it;
   *     }
   * }
   * ```
   */
  private fun checkForDeletedEntries() {
    TODO("Implement checkForDeletedEntries")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<RemoteStrike> SkStrikeServerImpl::getOrCreateCache(const SkStrikeSpec& strikeSpec) {
   *     // In cases where tracing is turned off, make sure not to get an unused function warning.
   *     // Lambdaize the function.
   *     TRACE_EVENT1("skia", "RecForDesc", "rec",
   *                  TRACE_STR_COPY(
   *                          [&strikeSpec](){
   *                              auto ptr =
   *                                  strikeSpec.descriptor().findEntry(kRec_SkDescriptorTag, nullptr);
   *                              SkScalerContextRec rec;
   *                              std::memcpy((void*)&rec, ptr, sizeof(rec));
   *                              return rec.dump();
   *                          }().c_str()
   *                  )
   *     );
   *
   *     if (auto it = fDescToRemoteStrike.find(&strikeSpec.descriptor());
   *         it != fDescToRemoteStrike.end())
   *     {
   *         // We have processed the RemoteStrike before. Reuse it.
   *         sk_sp<RemoteStrike> strike = it->second;
   *         strike->setStrikeSpec(strikeSpec);
   *         if (fRemoteStrikesToSend.contains(strike.get())) {
   *             // Already tracking
   *             return strike;
   *         }
   *
   *         // Strike is in unknown state on GPU. Start tracking strike on GPU by locking it.
   *         bool locked = fDiscardableHandleManager->lockHandle(it->second->discardableHandleId());
   *         if (locked) {
   *             fRemoteStrikesToSend.add(strike.get());
   *             return strike;
   *         }
   *
   *         // If it wasn't locked, then forget this strike, and build it anew below.
   *         fDescToRemoteStrike.erase(it);
   *     }
   *
   *     const SkTypeface& typeface = strikeSpec.typeface();
   *     // Create a new RemoteStrike. Start by processing the typeface.
   *     const SkTypefaceID typefaceId = typeface.uniqueID();
   *     if (!fCachedTypefaces.contains(typefaceId)) {
   *         fCachedTypefaces.add(typefaceId);
   *         fTypefacesToSend.emplace_back(typeface);
   *     }
   *
   *     auto context = strikeSpec.createScalerContext();
   *     auto newHandle = fDiscardableHandleManager->createHandle();  // Locked on creation
   *     auto remoteStrike = sk_make_sp<RemoteStrike>(strikeSpec, std::move(context), newHandle);
   *     remoteStrike->setStrikeSpec(strikeSpec);
   *     fRemoteStrikesToSend.add(remoteStrike.get());
   *     auto d = &remoteStrike->getDescriptor();
   *     fDescToRemoteStrike[d] = remoteStrike;
   *
   *     checkForDeletedEntries();
   *
   *     return remoteStrike;
   * }
   * ```
   */
  private fun getOrCreateCache(strikeSpec: SkStrikeSpec): SkSp<RemoteStrike> {
    TODO("Implement getOrCreateCache")
  }

  public open class MapOps {
    public operator fun invoke(key: SkDescriptor?): ULong {
      TODO("Implement invoke")
    }

    public operator fun invoke(lhs: SkDescriptor?, rhs: SkDescriptor?): Boolean {
      TODO("Implement invoke")
    }
  }

  public companion object {
    private val kMaxEntriesInDescriptorMap: ULong = TODO("Initialize kMaxEntriesInDescriptorMap")
  }
}
