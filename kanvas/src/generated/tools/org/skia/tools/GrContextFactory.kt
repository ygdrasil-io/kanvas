package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkNoncopyable
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.gpu.ganesh.GrDirectContext

/**
 * C++ original:
 * ```cpp
 * class GrContextFactory : SkNoncopyable {
 * public:
 *     using ContextType = skgpu::ContextType;
 *
 *     /**
 *      * Overrides for the initial GrContextOptions provided at construction time, and required
 *      * features that will cause context creation to fail if not present.
 *      */
 *     enum class ContextOverrides {
 *         kNone                          = 0x0,
 *         kAvoidStencilBuffers           = 0x1,
 *         kFakeGLESVersionAs2            = 0x2,
 *         kReducedShaders                = 0x4,
 *     };
 *
 *     explicit GrContextFactory(const GrContextOptions& opts);
 *     GrContextFactory();
 *
 *     ~GrContextFactory();
 *
 *     void destroyContexts();
 *     void abandonContexts();
 *     void releaseResourcesAndAbandonContexts();
 *
 *     /**
 *      * Get a context initialized with a type of GL context. It also makes the GL context current.
 *      */
 *     ContextInfo getContextInfo(ContextType type, ContextOverrides = ContextOverrides::kNone);
 *
 *     /**
 *      * Get a context in the same share group as the passed in GrContext, with the same type and
 *      * overrides. To get multiple contexts in a single share group, pass the same shareContext,
 *      * with different values for shareIndex.
 *      */
 *     ContextInfo getSharedContextInfo(GrDirectContext* shareContext, uint32_t shareIndex = 0);
 *
 *     /**
 *      * Get a GrContext initialized with a type of GL context. It also makes the GL context current.
 *      */
 *     GrDirectContext* get(ContextType type, ContextOverrides overrides = ContextOverrides::kNone);
 *     const GrContextOptions& getGlobalOptions() const { return fGlobalOptions; }
 *
 * private:
 *     ContextInfo getContextInfoInternal(ContextType type, ContextOverrides overrides,
 *                                        GrDirectContext* shareContext, uint32_t shareIndex);
 *
 *     struct Context {
 *         ContextType       fType;
 *         ContextOverrides  fOverrides;
 *         GrContextOptions  fOptions;
 *         GrBackendApi      fBackend;
 *         TestContext*      fTestContext;
 *         GrDirectContext*  fGrContext;
 *         GrDirectContext*  fShareContext;
 *         uint32_t          fShareIndex;
 *
 *         bool              fAbandoned;
 *     };
 *     skia_private::TArray<Context, true> fContexts;
 * #ifdef SK_GL
 *     std::unique_ptr<GLTestContext>      fSentinelGLContext;
 * #endif
 *
 *     const GrContextOptions fGlobalOptions;
 * }
 * ```
 */
public abstract class GrContextFactory public constructor(
  opts: GrContextOptions,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<Context, true> fContexts
   * ```
   */
  private var fContexts: Int = TODO("Initialize fContexts")

  /**
   * C++ original:
   * ```cpp
   * const GrContextOptions fGlobalOptions
   * ```
   */
  private val fGlobalOptions: Int = TODO("Initialize fGlobalOptions")

  /**
   * C++ original:
   * ```cpp
   * explicit GrContextFactory(const GrContextOptions& opts)
   * ```
   */
  public constructor() : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void destroyContexts()
   * ```
   */
  public fun destroyContexts() {
    TODO("Implement destroyContexts")
  }

  /**
   * C++ original:
   * ```cpp
   * void abandonContexts()
   * ```
   */
  public fun abandonContexts() {
    TODO("Implement abandonContexts")
  }

  /**
   * C++ original:
   * ```cpp
   * void releaseResourcesAndAbandonContexts()
   * ```
   */
  public fun releaseResourcesAndAbandonContexts() {
    TODO("Implement releaseResourcesAndAbandonContexts")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextInfo getContextInfo(ContextType type, ContextOverrides = ContextOverrides::kNone)
   * ```
   */
  public fun getContextInfo(type: ContextType, param1: ContextOverrides = TODO()): ContextInfo {
    TODO("Implement getContextInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextInfo getSharedContextInfo(GrDirectContext* shareContext, uint32_t shareIndex = 0)
   * ```
   */
  public abstract fun getSharedContextInfo(shareContext: GrDirectContext?, shareIndex: UInt = TODO()): ContextInfo

  /**
   * C++ original:
   * ```cpp
   * GrDirectContext* get(ContextType type, ContextOverrides overrides = ContextOverrides::kNone)
   * ```
   */
  public fun `get`(type: ContextType, overrides: ContextOverrides = TODO()): Int {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrContextOptions& getGlobalOptions() const { return fGlobalOptions; }
   * ```
   */
  public fun getGlobalOptions(): Int {
    TODO("Implement getGlobalOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextInfo getContextInfoInternal(ContextType type, ContextOverrides overrides,
   *                                        GrDirectContext* shareContext, uint32_t shareIndex)
   * ```
   */
  private fun getContextInfoInternal(
    type: ContextType,
    overrides: ContextOverrides,
    shareContext: GrDirectContext?,
    shareIndex: UInt,
  ): ContextInfo {
    TODO("Implement getContextInfoInternal")
  }

  public open class Context public constructor(
    public var fType: Int,
    public var fOverrides: undefined.ContextOverrides,
    public var fOptions: Int,
    public var fBackend: Int,
    public var fTestContext: Int?,
    public var fGrContext: Int?,
    public var fShareContext: Int?,
    public var fShareIndex: Int,
    public var fAbandoned: Boolean,
  )

  public enum class ContextOverrides {
    kNone,
    kAvoidStencilBuffers,
    kFakeGLESVersionAs2,
    kReducedShaders,
  }
}
