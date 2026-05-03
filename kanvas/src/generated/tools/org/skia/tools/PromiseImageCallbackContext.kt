package org.skia.tools

import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.gpu.ganesh.GrBackendFormat
import org.skia.gpu.ganesh.GrBackendTexture
import org.skia.gpu.ganesh.GrDirectContext

/**
 * C++ original:
 * ```cpp
 * class PromiseImageCallbackContext : public SkRefCnt {
 * public:
 *     PromiseImageCallbackContext(GrDirectContext* direct, GrBackendFormat backendFormat)
 *             : fContext(direct)
 *             , fBackendFormat(backendFormat) {}
 *
 *     ~PromiseImageCallbackContext() override;
 *
 *     const GrBackendFormat& backendFormat() const { return fBackendFormat; }
 *
 *     void setBackendTexture(const GrBackendTexture& backendTexture);
 *
 *     void destroyBackendTexture();
 *
 *     sk_sp<GrPromiseImageTexture> fulfill() {
 *         ++fTotalFulfills;
 *         return fPromiseImageTexture;
 *     }
 *
 *     void release() {
 *         ++fDoneCnt;
 *         SkASSERT(fDoneCnt <= fNumImages);
 *     }
 *
 *     void wasAddedToImage() { fNumImages++; }
 *
 *     const GrPromiseImageTexture* promiseImageTexture() const {
 *         return fPromiseImageTexture.get();
 *     }
 *
 *     static sk_sp<GrPromiseImageTexture> PromiseImageFulfillProc(void* textureContext) {
 *         auto callbackContext = static_cast<PromiseImageCallbackContext*>(textureContext);
 *         return callbackContext->fulfill();
 *     }
 *
 *     static void PromiseImageReleaseProc(void* textureContext) {
 *         auto callbackContext = static_cast<PromiseImageCallbackContext*>(textureContext);
 *         callbackContext->release();
 *         callbackContext->unref();
 *     }
 *
 * private:
 *     GrDirectContext*             fContext;
 *     GrBackendFormat              fBackendFormat;
 *     sk_sp<GrPromiseImageTexture> fPromiseImageTexture;
 *     int                          fNumImages = 0;
 *     int                          fTotalFulfills = 0;
 *     int                          fDoneCnt = 0;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class PromiseImageCallbackContext public constructor(
  direct: GrDirectContext?,
  backendFormat: GrBackendFormat,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * GrDirectContext*             fContext
   * ```
   */
  private var fContext: GrDirectContext? = TODO("Initialize fContext")

  /**
   * C++ original:
   * ```cpp
   * GrBackendFormat              fBackendFormat
   * ```
   */
  private var fBackendFormat: Int = TODO("Initialize fBackendFormat")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrPromiseImageTexture> fPromiseImageTexture
   * ```
   */
  private var fPromiseImageTexture: Int = TODO("Initialize fPromiseImageTexture")

  /**
   * C++ original:
   * ```cpp
   * int                          fNumImages = 0
   * ```
   */
  private var fNumImages: Int = TODO("Initialize fNumImages")

  /**
   * C++ original:
   * ```cpp
   * int                          fTotalFulfills = 0
   * ```
   */
  private var fTotalFulfills: Int = TODO("Initialize fTotalFulfills")

  /**
   * C++ original:
   * ```cpp
   * int                          fDoneCnt = 0
   * ```
   */
  private var fDoneCnt: Int = TODO("Initialize fDoneCnt")

  /**
   * C++ original:
   * ```cpp
   * const GrBackendFormat& backendFormat() const { return fBackendFormat; }
   * ```
   */
  public fun backendFormat(): Int {
    TODO("Implement backendFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBackendTexture(const GrBackendTexture& backendTexture)
   * ```
   */
  public fun setBackendTexture(backendTexture: GrBackendTexture) {
    TODO("Implement setBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void destroyBackendTexture()
   * ```
   */
  public fun destroyBackendTexture() {
    TODO("Implement destroyBackendTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrPromiseImageTexture> fulfill() {
   *         ++fTotalFulfills;
   *         return fPromiseImageTexture;
   *     }
   * ```
   */
  public fun fulfill(): Int {
    TODO("Implement fulfill")
  }

  /**
   * C++ original:
   * ```cpp
   * void release() {
   *         ++fDoneCnt;
   *         SkASSERT(fDoneCnt <= fNumImages);
   *     }
   * ```
   */
  public fun release() {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * void wasAddedToImage() { fNumImages++; }
   * ```
   */
  public fun wasAddedToImage() {
    TODO("Implement wasAddedToImage")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrPromiseImageTexture* promiseImageTexture() const {
   *         return fPromiseImageTexture.get();
   *     }
   * ```
   */
  public fun promiseImageTexture(): Int {
    TODO("Implement promiseImageTexture")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GrPromiseImageTexture> PromiseImageFulfillProc(void* textureContext) {
     *         auto callbackContext = static_cast<PromiseImageCallbackContext*>(textureContext);
     *         return callbackContext->fulfill();
     *     }
     * ```
     */
    public fun promiseImageFulfillProc(textureContext: Unit?): Int {
      TODO("Implement promiseImageFulfillProc")
    }

    /**
     * C++ original:
     * ```cpp
     * static void PromiseImageReleaseProc(void* textureContext) {
     *         auto callbackContext = static_cast<PromiseImageCallbackContext*>(textureContext);
     *         callbackContext->release();
     *         callbackContext->unref();
     *     }
     * ```
     */
    public fun promiseImageReleaseProc(textureContext: Unit?) {
      TODO("Implement promiseImageReleaseProc")
    }
  }
}
