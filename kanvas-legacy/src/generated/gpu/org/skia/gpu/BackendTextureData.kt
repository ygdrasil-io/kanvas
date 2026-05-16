package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class BackendTextureData {
 * public:
 *     virtual ~BackendTextureData();
 *
 * #if defined(SK_DEBUG)
 *     virtual skgpu::BackendApi type() const = 0;
 * #endif
 * protected:
 *     BackendTextureData() = default;
 *     BackendTextureData(const BackendTextureData&) = default;
 *
 *     using AnyBackendTextureData = BackendTexture::AnyBackendTextureData;
 *
 * private:
 *     friend class BackendTexture;
 *
 *     virtual void copyTo(AnyBackendTextureData& dstData) const = 0;
 *     virtual bool equal(const BackendTextureData* that) const = 0;
 * }
 * ```
 */
public abstract class BackendTextureData public constructor() {
  /**
   * C++ original:
   * ```cpp
   * BackendTextureData() = default
   * ```
   */
  public constructor(param0: BackendTextureData) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void copyTo(AnyBackendTextureData& dstData) const = 0
   * ```
   */
  private abstract fun copyTo(dstData: BackendTextureDataAnyBackendTextureData)

  /**
   * C++ original:
   * ```cpp
   * virtual bool equal(const BackendTextureData* that) const = 0
   * ```
   */
  private abstract fun equal(that: BackendTextureData?): Boolean
}
