package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.size_t
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API Recording final {
 * public:
 *     ~Recording();
 *
 *     RecordingPriv priv();
 *
 * private:
 *     friend class Recorder;  // for ctor and LazyProxyData
 *     friend class RecordingPriv;
 *
 *     // LazyProxyData is used if this recording should be replayed to a target that is provided on
 *     // replay, and it handles the target proxy's instantiation with the provided target.
 *     class LazyProxyData {
 *     public:
 *         LazyProxyData(const Caps*, SkISize dimensions, const TextureInfo&);
 *         ~LazyProxyData();
 *
 *         TextureProxy* lazyProxy();
 *         sk_sp<TextureProxy> refLazyProxy();
 *
 *         bool lazyInstantiate(ResourceProvider*, sk_sp<Texture>);
 *
 *     private:
 *         sk_sp<Texture> fTarget;
 *         sk_sp<TextureProxy> fTargetProxy;
 *     };
 *
 *     struct ProxyHash {
 *         std::size_t operator()(const sk_sp<TextureProxy>& proxy) const;
 *     };
 *
 *     Recording(uint32_t uniqueID,
 *               uint32_t recorderID,
 *               std::unique_ptr<LazyProxyData> targetProxyData,
 *               skia_private::TArray<sk_sp<RefCntedCallback>>&& finishedProcs);
 *
 *     void addResourceRef(sk_sp<Resource>);
 *
 *     // Used to verify ordering if recorder ID is not SK_InvalidGenID
 *     uint32_t fUniqueID;
 *     uint32_t fRecorderID;
 *
 *     // This is held by a pointer instead of being inline to allow TaskList to be forward declared.
 *     std::unique_ptr<TaskList> fRootTaskList;
 *     // We don't always take refs to all resources used by specific Tasks (e.g. a common buffer used
 *     // for uploads). Instead we'll just hold onto one ref for those Resources outside the Tasks.
 *     // Those refs are stored in the array here and will eventually be passed onto a CommandBuffer
 *     // when the Recording adds its commands.
 *     std::vector<sk_sp<Resource>> fExtraResourceRefs;
 *
 *     std::unordered_set<sk_sp<TextureProxy>, ProxyHash> fNonVolatileLazyProxies;
 *     std::unordered_set<sk_sp<TextureProxy>, ProxyHash> fVolatileLazyProxies;
 *
 *     std::unique_ptr<LazyProxyData> fTargetProxyData;
 *
 *     skia_private::TArray<sk_sp<RefCntedCallback>> fFinishedProcs;
 * }
 * ```
 */
public data class Recording public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  private var fUniqueID: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fRecorderID
   * ```
   */
  private var fRecorderID: UInt,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<TaskList> fRootTaskList
   * ```
   */
  private var fRootTaskList: TaskList?,
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<Resource>> fExtraResourceRefs
   * ```
   */
  private var fExtraResourceRefs: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unordered_set<sk_sp<TextureProxy>, ProxyHash> fNonVolatileLazyProxies
   * ```
   */
  private var fNonVolatileLazyProxies: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unordered_set<sk_sp<TextureProxy>, ProxyHash> fVolatileLazyProxies
   * ```
   */
  private var fVolatileLazyProxies: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<LazyProxyData> fTargetProxyData
   * ```
   */
  private var fTargetProxyData: LazyProxyData?,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<RefCntedCallback>> fFinishedProcs
   * ```
   */
  private var fFinishedProcs: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * RecordingPriv priv()
   * ```
   */
  public fun priv(): RecordingPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * void addResourceRef(sk_sp<Resource>)
   * ```
   */
  private fun addResourceRef(param0: SkSp<Resource>) {
    TODO("Implement addResourceRef")
  }

  public data class LazyProxyData public constructor(
    private var fTarget: Int,
    private var fTargetProxy: Int,
  ) {
    public fun lazyProxy(): TextureProxy {
      TODO("Implement lazyProxy")
    }

    public fun refLazyProxy(): Int {
      TODO("Implement refLazyProxy")
    }

    public fun lazyInstantiate(resourceProvider: ResourceProvider?, texture: SkSp<Texture>): Boolean {
      TODO("Implement lazyInstantiate")
    }
  }

  public open class ProxyHash {
    public operator fun invoke(proxy: SkSp<TextureProxy>): size_t {
      TODO("Implement invoke")
    }
  }
}
