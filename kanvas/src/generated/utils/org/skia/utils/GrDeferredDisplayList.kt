package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.gpu.ganesh.GrRenderTargetProxy
import undefined.GrDeferredDisplayListPriv

/**
 * C++ original:
 * ```cpp
 * class GrDeferredDisplayList : public SkNVRefCnt<GrDeferredDisplayList> {
 * public:
 *     SK_API ~GrDeferredDisplayList();
 *
 *     SK_API const GrSurfaceCharacterization& characterization() const {
 *         return fCharacterization;
 *     }
 *     /**
 *      * Iterate through the programs required by the DDL.
 *      */
 *     class SK_API ProgramIterator {
 *     public:
 *         ProgramIterator(GrDirectContext*, GrDeferredDisplayList*);
 *         ~ProgramIterator();
 *
 *         // This returns true if any work was done. Getting a cache hit does not count as work.
 *         bool compile();
 *         bool done() const;
 *         void next();
 *
 *     private:
 *         GrDirectContext*                                 fDContext;
 *         const skia_private::TArray<GrRecordingContext::ProgramData>& fProgramData;
 *         int                                              fIndex;
 *     };
 *
 *     // Provides access to functions that aren't part of the public API.
 *     GrDeferredDisplayListPriv priv();
 *     const GrDeferredDisplayListPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * private:
 *     friend class GrDrawingManager; // for access to 'fRenderTasks', 'fLazyProxyData', 'fArenas'
 *     friend class GrDeferredDisplayListRecorder; // for access to 'fLazyProxyData'
 *     friend class GrDeferredDisplayListPriv;
 *
 *     // This object is the source from which the lazy proxy backing the DDL will pull its backing
 *     // texture when the DDL is replayed. It has to be separately ref counted bc the lazy proxy
 *     // can outlive the DDL.
 *     class LazyProxyData : public SkRefCnt {
 *     public:
 *         // Upon being replayed - this field will be filled in (by the DrawingManager) with the
 *         // proxy backing the destination SkSurface. Note that, since there is no good place to
 *         // clear it, it can become a dangling pointer. Additionally, since the renderTargetProxy
 *         // doesn't get a ref here, the SkSurface that owns it must remain alive until the DDL
 *         // is flushed.
 *         // TODO: the drawing manager could ref the renderTargetProxy for the DDL and then add
 *         // a renderingTask to unref it after the DDL's ops have been executed.
 *         GrRenderTargetProxy* fReplayDest = nullptr;
 *     };
 *
 *     SK_API GrDeferredDisplayList(const GrSurfaceCharacterization& characterization,
 *                                  sk_sp<GrRenderTargetProxy> fTargetProxy,
 *                                  sk_sp<LazyProxyData>);
 *
 *     const skia_private::TArray<GrRecordingContext::ProgramData>& programData() const {
 *         return fProgramData;
 *     }
 *
 *     const GrSurfaceCharacterization fCharacterization;
 *
 *     // These are ordered such that the destructor cleans op tasks up first (which may refer back
 *     // to the arena and memory pool in their destructors).
 *     GrRecordingContext::OwnedArenas fArenas;
 *     skia_private::TArray<sk_sp<GrRenderTask>>   fRenderTasks;
 *
 *     skia_private::TArray<GrRecordingContext::ProgramData> fProgramData;
 *     sk_sp<GrRenderTargetProxy>      fTargetProxy;
 *     sk_sp<LazyProxyData>            fLazyProxyData;
 * }
 * ```
 */
public open class GrDeferredDisplayList public constructor(
  characterization: GrSurfaceCharacterization,
  fTargetProxy: SkSp<GrRenderTargetProxy>,
  param2: SkSp<LazyProxyData>,
) : SkNVRefCnt(),
    GrDeferredDisplayList {
  /**
   * C++ original:
   * ```cpp
   * const GrSurfaceCharacterization fCharacterization
   * ```
   */
  public val fCharacterization: Int = TODO("Initialize fCharacterization")

  /**
   * C++ original:
   * ```cpp
   * GrRecordingContext::OwnedArenas fArenas
   * ```
   */
  public var fArenas: Int = TODO("Initialize fArenas")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<GrRenderTask>>   fRenderTasks
   * ```
   */
  public var fRenderTasks: Int = TODO("Initialize fRenderTasks")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<GrRecordingContext::ProgramData> fProgramData
   * ```
   */
  private var fProgramData: Int = TODO("Initialize fProgramData")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrRenderTargetProxy>      fTargetProxy
   * ```
   */
  public var fTargetProxy: Int = TODO("Initialize fTargetProxy")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<LazyProxyData>            fLazyProxyData
   * ```
   */
  public var fLazyProxyData: Int = TODO("Initialize fLazyProxyData")

  /**
   * C++ original:
   * ```cpp
   * const GrSurfaceCharacterization& characterization() const {
   *         return fCharacterization;
   *     }
   * ```
   */
  public override fun characterization(): Int {
    TODO("Implement characterization")
  }

  /**
   * C++ original:
   * ```cpp
   * GrDeferredDisplayListPriv priv()
   * ```
   */
  public override fun priv(): GrDeferredDisplayListPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrDeferredDisplayListPriv priv() const
   * ```
   */
  public override fun programData(): Int {
    TODO("Implement programData")
  }

  public data class ProgramIterator public constructor(
    private var fDContext: GrDirectContext?,
    private val fProgramData: Int,
    private var fIndex: Int,
  ) {
    public fun compile(): Boolean {
      TODO("Implement compile")
    }

    public fun done(): Boolean {
      TODO("Implement done")
    }

    public fun next() {
      TODO("Implement next")
    }
  }

  public open class LazyProxyData : SkRefCnt() {
    public var fReplayDest: GrRenderTargetProxy? = TODO("Initialize fReplayDest")
  }
}
