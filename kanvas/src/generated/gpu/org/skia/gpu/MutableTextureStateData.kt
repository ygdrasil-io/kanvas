package org.skia.gpu

import undefined.AnyStateData

/**
 * C++ original:
 * ```cpp
 * class MutableTextureStateData {
 * public:
 *     virtual ~MutableTextureStateData();
 *
 * #if defined(SK_DEBUG)
 *     virtual BackendApi type() const = 0;
 * #endif
 * protected:
 *     MutableTextureStateData() = default;
 *     MutableTextureStateData(const MutableTextureStateData&) = default;
 *
 *     using AnyStateData = MutableTextureState::AnyStateData;
 *
 * private:
 *     friend class MutableTextureState;
 *     virtual void copyTo(AnyStateData&) const = 0;
 * }
 * ```
 */
public abstract class MutableTextureStateData public constructor() {
  /**
   * C++ original:
   * ```cpp
   * MutableTextureStateData() = default
   * ```
   */
  public constructor(param0: MutableTextureStateData) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual BackendApi type() const = 0
   * ```
   */
  public abstract fun type(): BackendApi

  /**
   * C++ original:
   * ```cpp
   * virtual void copyTo(AnyStateData&) const = 0
   * ```
   */
  private abstract fun copyTo(param0: AnyStateData)
}
