package org.skia.utils

import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrBackendTexture

/**
 * C++ original:
 * ```cpp
 * class SK_API GrPromiseImageTexture : public SkNVRefCnt<GrPromiseImageTexture> {
 * public:
 *     GrPromiseImageTexture() = delete;
 *     GrPromiseImageTexture(const GrPromiseImageTexture&) = delete;
 *     GrPromiseImageTexture(GrPromiseImageTexture&&) = delete;
 *     ~GrPromiseImageTexture();
 *     GrPromiseImageTexture& operator=(const GrPromiseImageTexture&) = delete;
 *     GrPromiseImageTexture& operator=(GrPromiseImageTexture&&) = delete;
 *
 *     static sk_sp<GrPromiseImageTexture> Make(const GrBackendTexture& backendTexture) {
 *         if (!backendTexture.isValid()) {
 *             return nullptr;
 *         }
 *         return sk_sp<GrPromiseImageTexture>(new GrPromiseImageTexture(backendTexture));
 *     }
 *
 *     GrBackendTexture backendTexture() const { return fBackendTexture; }
 *
 * private:
 *     explicit GrPromiseImageTexture(const GrBackendTexture& backendTexture);
 *
 *     GrBackendTexture fBackendTexture;
 * }
 * ```
 */
public open class GrPromiseImageTexture public constructor() : SkNVRefCnt(), GrPromiseImageTexture {
  /**
   * C++ original:
   * ```cpp
   * GrBackendTexture fBackendTexture
   * ```
   */
  private var fBackendTexture: GrBackendTexture = TODO("Initialize fBackendTexture")

  /**
   * C++ original:
   * ```cpp
   * GrPromiseImageTexture() = delete
   * ```
   */
  public constructor(param0: GrPromiseImageTexture) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * GrPromiseImageTexture(const GrPromiseImageTexture&) = delete
   * ```
   */
  public constructor(backendTexture: GrBackendTexture) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * GrPromiseImageTexture& operator=(const GrPromiseImageTexture&) = delete
   * ```
   */
  public override fun assign(param0: GrPromiseImageTexture) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * GrPromiseImageTexture& operator=(GrPromiseImageTexture&&) = delete
   * ```
   */
  public override fun backendTexture(): GrBackendTexture {
    TODO("Implement backendTexture")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GrPromiseImageTexture> Make(const GrBackendTexture& backendTexture) {
     *         if (!backendTexture.isValid()) {
     *             return nullptr;
     *         }
     *         return sk_sp<GrPromiseImageTexture>(new GrPromiseImageTexture(backendTexture));
     *     }
     * ```
     */
    public override fun make(backendTexture: GrBackendTexture): SkSp<GrPromiseImageTexture> {
      TODO("Implement make")
    }
  }
}
