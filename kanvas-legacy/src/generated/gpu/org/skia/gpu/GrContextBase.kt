package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkTextureCompressionType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrBackendApi
import org.skia.gpu.ganesh.GrBackendFormat
import org.skia.gpu.ganesh.GrBaseContextPriv
import org.skia.gpu.ganesh.GrCaps
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.gpu.ganesh.GrContextThreadSafeProxy
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.gpu.ganesh.GrRecordingContext
import undefined.GrRenderable

/**
 * C++ original:
 * ```cpp
 * class GrContext_Base : public SkRefCnt {
 * public:
 *     ~GrContext_Base() override;
 *
 *     /*
 *      * Safely downcast to a GrDirectContext.
 *      */
 *     virtual GrDirectContext* asDirectContext() { return nullptr; }
 *
 *     /*
 *      * The 3D API backing this context
 *      */
 *     SK_API GrBackendApi backend() const;
 *
 *     /*
 *      * Retrieve the default GrBackendFormat for a given SkColorType and renderability.
 *      * It is guaranteed that this backend format will be the one used by the GrContext
 *      * SkColorType and GrSurfaceCharacterization-based createBackendTexture methods.
 *      *
 *      * The caller should check that the returned format is valid.
 *      */
 *     SK_API GrBackendFormat defaultBackendFormat(SkColorType, GrRenderable) const;
 *
 *     SK_API GrBackendFormat compressedBackendFormat(SkTextureCompressionType) const;
 *
 *     /**
 *      * Gets the maximum supported sample count for a color type. 1 is returned if only non-MSAA
 *      * rendering is supported for the color type. 0 is returned if rendering to this color type
 *      * is not supported at all.
 *      */
 *     SK_API int maxSurfaceSampleCountForColorType(SkColorType colorType) const;
 *
 *     // TODO: When the public version is gone, rename to refThreadSafeProxy and add raw ptr ver.
 *     sk_sp<GrContextThreadSafeProxy> threadSafeProxy();
 *
 *     // Provides access to functions that aren't part of the public API.
 *     GrBaseContextPriv priv();
 *     const GrBaseContextPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * protected:
 *     friend class GrBaseContextPriv; // for hidden functions
 *
 *     explicit GrContext_Base(sk_sp<GrContextThreadSafeProxy>);
 *
 *     virtual bool init();
 *
 *     /**
 *      * An identifier for this context. The id is used by all compatible contexts. For example,
 *      * if SkImages are created on one thread using an image creation context, then fed into a
 *      * DDL Recorder on second thread (which has a recording context) and finally replayed on
 *      * a third thread with a direct context, then all three contexts will report the same id.
 *      * It is an error for an image to be used with contexts that report different ids.
 *      */
 *     uint32_t contextID() const;
 *
 *     bool matches(GrContext_Base* candidate) const {
 *         return candidate && candidate->contextID() == this->contextID();
 *     }
 *
 *     /*
 *      * The options in effect for this context
 *      */
 *     const GrContextOptions& options() const;
 *
 *     const GrCaps* caps() const;
 *     sk_sp<const GrCaps> refCaps() const;
 *
 *     virtual GrImageContext* asImageContext() { return nullptr; }
 *     virtual GrRecordingContext* asRecordingContext() { return nullptr; }
 *
 *     sk_sp<GrContextThreadSafeProxy>         fThreadSafeProxy;
 *
 * private:
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class GrContextBase public constructor(
  param0: SkSp<GrContextThreadSafeProxy>,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrContextThreadSafeProxy>         fThreadSafeProxy
   * ```
   */
  protected var fThreadSafeProxy: SkSp<GrContextThreadSafeProxy> =
      TODO("Initialize fThreadSafeProxy")

  /**
   * C++ original:
   * ```cpp
   * virtual GrDirectContext* asDirectContext() { return nullptr; }
   * ```
   */
  public open fun asDirectContext(): GrDirectContext {
    TODO("Implement asDirectContext")
  }

  /**
   * C++ original:
   * ```cpp
   * GrBackendApi backend() const
   * ```
   */
  public fun backend(): GrBackendApi {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * GrBackendFormat defaultBackendFormat(SkColorType, GrRenderable) const
   * ```
   */
  public fun defaultBackendFormat(param0: SkColorType, param1: GrRenderable): GrBackendFormat {
    TODO("Implement defaultBackendFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * GrBackendFormat compressedBackendFormat(SkTextureCompressionType) const
   * ```
   */
  public fun compressedBackendFormat(param0: SkTextureCompressionType): GrBackendFormat {
    TODO("Implement compressedBackendFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxSurfaceSampleCountForColorType(SkColorType colorType) const
   * ```
   */
  public fun maxSurfaceSampleCountForColorType(colorType: SkColorType): Int {
    TODO("Implement maxSurfaceSampleCountForColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrContextThreadSafeProxy> threadSafeProxy()
   * ```
   */
  public fun threadSafeProxy(): SkSp<GrContextThreadSafeProxy> {
    TODO("Implement threadSafeProxy")
  }

  /**
   * C++ original:
   * ```cpp
   * inline GrBaseContextPriv GrContext_Base::priv() { return GrBaseContextPriv(this); }
   * ```
   */
  public fun priv(): GrBaseContextPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * inline const GrBaseContextPriv GrContext_Base::priv () const {  // NOLINT(readability-const-return-type)
   *     return GrBaseContextPriv(const_cast<GrContext_Base*>(this));
   * }
   * ```
   */
  protected open fun `init`(): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool init()
   * ```
   */
  protected fun contextID(): Int {
    TODO("Implement contextID")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t contextID() const
   * ```
   */
  protected fun matches(candidate: GrContextBase?): Boolean {
    TODO("Implement matches")
  }

  /**
   * C++ original:
   * ```cpp
   * bool matches(GrContext_Base* candidate) const {
   *         return candidate && candidate->contextID() == this->contextID();
   *     }
   * ```
   */
  protected fun options(): GrContextOptions {
    TODO("Implement options")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrContextOptions& options() const
   * ```
   */
  protected fun caps(): GrCaps {
    TODO("Implement caps")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrCaps* caps() const
   * ```
   */
  protected fun refCaps(): SkSp<GrCaps> {
    TODO("Implement refCaps")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const GrCaps> refCaps() const
   * ```
   */
  protected open fun asImageContext(): GrImageContext {
    TODO("Implement asImageContext")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrImageContext* asImageContext() { return nullptr; }
   * ```
   */
  protected open fun asRecordingContext(): GrRecordingContext {
    TODO("Implement asRecordingContext")
  }
}
