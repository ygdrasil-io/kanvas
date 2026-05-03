package org.skia.gpu

import StateData

/**
 * C++ original:
 * ```cpp
 * class MutableTextureStatePriv final {
 * public:
 *     template <typename StateData>
 *     static MutableTextureState MakeMutableTextureState(BackendApi backend,
 *                                                        const StateData& data) {
 *         return MutableTextureState(backend, data);
 *     }
 *
 *     static const MutableTextureStateData* GetStateData(const MutableTextureState& mts) {
 *         return mts.fStateData.get();
 *     }
 *
 *     static const MutableTextureStateData* GetStateData(const MutableTextureState* mts) {
 *         SkASSERT(mts);
 *         return mts->fStateData.get();
 *     }
 *
 *     static MutableTextureStateData* GetStateData(MutableTextureState* mts) {
 *         SkASSERT(mts);
 *         return mts->fStateData.get();
 *     }
 * }
 * ```
 */
public class MutableTextureStatePriv {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     *     template <typename StateData>
     *     static MutableTextureState MakeMutableTextureState(BackendApi backend,
     *                                                        const StateData& data) {
     *         return MutableTextureState(backend, data);
     *     }
     * ```
     */
    public fun <StateData> makeMutableTextureState(backend: BackendApi, `data`: StateData): MutableTextureState {
      TODO("Implement makeMutableTextureState")
    }

    /**
     * C++ original:
     * ```cpp
     * static const MutableTextureStateData* GetStateData(const MutableTextureState& mts) {
     *         return mts.fStateData.get();
     *     }
     * ```
     */
    public fun getStateData(mts: MutableTextureState): MutableTextureStateData {
      TODO("Implement getStateData")
    }

    /**
     * C++ original:
     * ```cpp
     * static const MutableTextureStateData* GetStateData(const MutableTextureState* mts) {
     *         SkASSERT(mts);
     *         return mts->fStateData.get();
     *     }
     * ```
     */
    public fun getStateData(mts: MutableTextureState?): MutableTextureStateData {
      TODO("Implement getStateData")
    }
  }
}
