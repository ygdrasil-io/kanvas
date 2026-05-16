package org.skia.tools

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ContextInfo {
 * public:
 *     ContextInfo() = default;
 *     ContextInfo(const ContextInfo&) = default;
 *     ContextInfo& operator=(const ContextInfo&) = default;
 *
 *     skgpu::ContextType type() const { return fType; }
 *     GrBackendApi backend() const { return skgpu::ganesh::ContextTypeBackend(fType); }
 *
 *     GrDirectContext* directContext() const { return fContext; }
 *     TestContext* testContext() const { return fTestContext; }
 *
 * #ifdef SK_GL
 *     GLTestContext* glContext() const {
 *         SkASSERT(GrBackendApi::kOpenGL == this->backend());
 *         return static_cast<GLTestContext*>(fTestContext);
 *     }
 * #endif
 *
 *     const GrContextOptions& options() const { return fOptions; }
 *
 * private:
 *     ContextInfo(skgpu::ContextType type,
 *                 TestContext* testContext,
 *                 GrDirectContext* context,
 *                 const GrContextOptions& options)
 *             : fType(type), fTestContext(testContext), fContext(context), fOptions(options) {}
 *
 *     skgpu::ContextType fType = skgpu::ContextType::kGL;
 *     // Valid until the factory destroys it via abandonContexts() or destroyContexts().
 *     TestContext* fTestContext = nullptr;
 *     GrDirectContext* fContext = nullptr;
 *     GrContextOptions fOptions;
 *
 *     friend class GrContextFactory;
 * }
 * ```
 */
public data class ContextInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType fType
   * ```
   */
  private var fType: Int,
  /**
   * C++ original:
   * ```cpp
   * TestContext* fTestContext
   * ```
   */
  private var fTestContext: Int?,
  /**
   * C++ original:
   * ```cpp
   * GrDirectContext* fContext
   * ```
   */
  private var fContext: Int?,
  /**
   * C++ original:
   * ```cpp
   * GrContextOptions fOptions
   * ```
   */
  private var fOptions: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * ContextInfo& operator=(const ContextInfo&) = default
   * ```
   */
  public fun assign(param0: ContextInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType type() const { return fType; }
   * ```
   */
  public fun type(): Int {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * GrBackendApi backend() const { return skgpu::ganesh::ContextTypeBackend(fType); }
   * ```
   */
  public fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * GrDirectContext* directContext() const { return fContext; }
   * ```
   */
  public fun directContext(): Int {
    TODO("Implement directContext")
  }

  /**
   * C++ original:
   * ```cpp
   * TestContext* testContext() const { return fTestContext; }
   * ```
   */
  public fun testContext(): Int {
    TODO("Implement testContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrContextOptions& options() const { return fOptions; }
   * ```
   */
  public fun options(): Int {
    TODO("Implement options")
  }
}
