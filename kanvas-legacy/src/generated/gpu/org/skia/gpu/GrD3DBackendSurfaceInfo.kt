package org.skia.gpu

import GrD3DResourceState
import kotlin.Boolean
import kotlin.Int
import undefined.GrD3DTextureResourceInfo

/**
 * C++ original:
 * ```cpp
 * struct GrD3DBackendSurfaceInfo {
 *     GrD3DBackendSurfaceInfo(const GrD3DTextureResourceInfo& info, GrD3DResourceState* state);
 *
 *     void cleanup();
 *
 *     GrD3DBackendSurfaceInfo& operator=(const GrD3DBackendSurfaceInfo&) = delete;
 *
 *     // Assigns the passed in GrD3DBackendSurfaceInfo to this object. if isValid is true we will also
 *     // attempt to unref the old fLayout on this object.
 *     void assign(const GrD3DBackendSurfaceInfo&, bool isValid);
 *
 *     void setResourceState(GrD3DResourceStateEnum state);
 *
 *     sk_sp<GrD3DResourceState> getGrD3DResourceState() const;
 *
 *     GrD3DTextureResourceInfo snapTextureResourceInfo() const;
 *
 *     bool isProtected() const;
 * #if defined(GPU_TEST_UTILS)
 *     bool operator==(const GrD3DBackendSurfaceInfo& that) const;
 * #endif
 *
 * private:
 *     GrD3DTextureResourceInfo* fTextureResourceInfo;
 *     GrD3DResourceState* fResourceState;
 * }
 * ```
 */
public data class GrD3DBackendSurfaceInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * GrD3DTextureResourceInfo* fTextureResourceInfo
   * ```
   */
  private var fTextureResourceInfo: GrD3DTextureResourceInfo?,
  /**
   * C++ original:
   * ```cpp
   * GrD3DResourceState* fResourceState
   * ```
   */
  private var fResourceState: GrD3DResourceState?,
) {
  /**
   * C++ original:
   * ```cpp
   * void cleanup()
   * ```
   */
  public fun cleanup() {
    TODO("Implement cleanup")
  }

  /**
   * C++ original:
   * ```cpp
   * GrD3DBackendSurfaceInfo& operator=(const GrD3DBackendSurfaceInfo&) = delete
   * ```
   */
  public fun assign(param0: GrD3DBackendSurfaceInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void assign(const GrD3DBackendSurfaceInfo&, bool isValid)
   * ```
   */
  public fun assign(param0: GrD3DBackendSurfaceInfo, isValid: Boolean) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void setResourceState(GrD3DResourceStateEnum state)
   * ```
   */
  public fun setResourceState(state: GrD3DResourceStateEnum) {
    TODO("Implement setResourceState")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrD3DResourceState> getGrD3DResourceState() const
   * ```
   */
  public fun getGrD3DResourceState(): Int {
    TODO("Implement getGrD3DResourceState")
  }

  /**
   * C++ original:
   * ```cpp
   * GrD3DTextureResourceInfo snapTextureResourceInfo() const
   * ```
   */
  public fun snapTextureResourceInfo(): GrD3DTextureResourceInfo {
    TODO("Implement snapTextureResourceInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isProtected() const
   * ```
   */
  public fun isProtected(): Boolean {
    TODO("Implement isProtected")
  }
}
