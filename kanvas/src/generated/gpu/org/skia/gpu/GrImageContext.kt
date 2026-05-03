package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrContextThreadSafeProxy
import org.skia.gpu.ganesh.GrImageContextPriv

/**
 * C++ original:
 * ```cpp
 * class GrImageContext : public GrContext_Base {
 * public:
 *     ~GrImageContext() override;
 *
 *     // Provides access to functions that aren't part of the public API.
 *     GrImageContextPriv priv();
 *     const GrImageContextPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * protected:
 *     friend class GrImageContextPriv; // for hidden functions
 *
 *     explicit GrImageContext(sk_sp<GrContextThreadSafeProxy>);
 *
 *     SK_API virtual void abandonContext();
 *     SK_API virtual bool abandoned();
 *
 *     /** This is only useful for debug purposes */
 *     skgpu::SingleOwner* singleOwner() const { return &fSingleOwner; }
 *
 *     GrImageContext* asImageContext() override { return this; }
 *
 * private:
 *     // When making promise images, we currently need a placeholder GrImageContext instance to give
 *     // to the SkImage that has no real power, just a wrapper around the ThreadSafeProxy.
 *     // TODO: De-power SkImage to ThreadSafeProxy or at least figure out a way to share one instance.
 *     static sk_sp<GrImageContext> MakeForPromiseImage(sk_sp<GrContextThreadSafeProxy>);
 *
 *     // In debug builds we guard against improper thread handling
 *     // This guard is passed to the GrDrawingManager and, from there to all the
 *     // GrSurfaceDrawContexts.  It is also passed to the GrResourceProvider and SkGpuDevice.
 *     // TODO: Move this down to GrRecordingContext.
 *     mutable skgpu::SingleOwner fSingleOwner;
 * }
 * ```
 */
public open class GrImageContext : GrContextBase() {
  /**
   * C++ original:
   * ```cpp
   * explicit GrImageContext(sk_sp<GrContextThreadSafeProxy>)
   * ```
   */
  protected var skSp: GrImageContext = TODO("Initialize skSp")

  /**
   * C++ original:
   * ```cpp
   * mutable skgpu::SingleOwner fSingleOwner
   * ```
   */
  private var fSingleOwner: Int = TODO("Initialize fSingleOwner")

  /**
   * C++ original:
   * ```cpp
   * GrImageContextPriv priv()
   * ```
   */
  public override fun priv(): GrImageContextPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrImageContextPriv priv() const
   * ```
   */
  protected open fun abandonContext() {
    TODO("Implement abandonContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void abandonContext()
   * ```
   */
  protected open fun abandoned(): Boolean {
    TODO("Implement abandoned")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool abandoned()
   * ```
   */
  protected fun singleOwner(): Int {
    TODO("Implement singleOwner")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::SingleOwner* singleOwner() const { return &fSingleOwner; }
   * ```
   */
  protected override fun asImageContext(): GrImageContext {
    TODO("Implement asImageContext")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<GrImageContext> MakeForPromiseImage(sk_sp<GrContextThreadSafeProxy>)
     * ```
     */
    private fun makeForPromiseImage(param0: SkSp<GrContextThreadSafeProxy>): Int {
      TODO("Implement makeForPromiseImage")
    }
  }
}
