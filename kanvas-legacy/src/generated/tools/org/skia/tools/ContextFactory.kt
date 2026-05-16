package org.skia.tools

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ContextFactory {
 * public:
 *     explicit ContextFactory(const TestOptions&);
 *     ContextFactory() = default;
 *     ContextFactory(const ContextFactory&) = delete;
 *     ContextFactory& operator=(const ContextFactory&) = delete;
 *
 *     ~ContextFactory() = default;
 *
 *     ContextInfo getContextInfo(skgpu::ContextType);
 *
 * private:
 *     struct OwnedContextInfo {
 *         OwnedContextInfo();
 *         OwnedContextInfo(skgpu::ContextType,
 *                          std::unique_ptr<GraphiteTestContext>,
 *                          std::unique_ptr<skgpu::graphite::Context>);
 *
 *         ~OwnedContextInfo();
 *         OwnedContextInfo(OwnedContextInfo&&);
 *         OwnedContextInfo& operator=(OwnedContextInfo&&);
 *
 *         // This holds the same data as ContextInfo, but uses unique_ptr to maintain ownership.
 *         skgpu::ContextType fType = skgpu::ContextType::kMock;
 *         std::unique_ptr<GraphiteTestContext> fTestContext;
 *         std::unique_ptr<skgpu::graphite::Context> fContext;
 *     };
 *
 *     static ContextInfo AsContextInfo(const OwnedContextInfo& ctx);
 *
 *     skia_private::TArray<OwnedContextInfo> fContexts;
 *     const TestOptions fOptions = {};
 * }
 * ```
 */
public data class ContextFactory public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<OwnedContextInfo> fContexts
   * ```
   */
  private var fContexts: Int,
  /**
   * C++ original:
   * ```cpp
   * const TestOptions fOptions
   * ```
   */
  private val fOptions: TestOptions,
) {
  /**
   * C++ original:
   * ```cpp
   * ContextFactory& operator=(const ContextFactory&) = delete
   * ```
   */
  public fun assign(param0: ContextFactory) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * ContextInfo ContextFactory::getContextInfo(skgpu::ContextType type) {
   *     if (!skgpu::IsDawnBackend(type) && fOptions.hasDawnOptions()) {
   *         return {};
   *     }
   *
   *     // Look for an existing ContextInfo that we can re-use.
   *     for (const OwnedContextInfo& ctxInfo : fContexts) {
   *         if (ctxInfo.fType == type) {
   *             return AsContextInfo(ctxInfo);
   *         }
   *     }
   *
   *     // Create a new ContextInfo from this context type.
   *     std::unique_ptr<GraphiteTestContext> testCtx;
   *
   *     switch (type) {
   *         case skgpu::ContextType::kMetal: {
   * #ifdef SK_METAL
   *             testCtx = graphite::MtlTestContext::Make();
   * #endif
   *         } break;
   *         case skgpu::ContextType::kVulkan: {
   * #ifdef SK_VULKAN
   *             testCtx = graphite::VulkanTestContext::Make();
   * #endif
   *         } break;
   * #ifdef SK_DAWN
   *
   * #define CASE(TYPE)                                                          \
   *     case skgpu::ContextType::kDawn_##TYPE:                                  \
   *         testCtx = graphite::DawnTestContext::Make(wgpu::BackendType::TYPE); \
   *         break;
   * #else
   * #define CASE(TYPE)                         \
   *     case skgpu::ContextType::kDawn_##TYPE: \
   *         break;
   * #endif // SK_DAWN
   *         CASE(D3D11)
   *         CASE(D3D12)
   *         CASE(Metal)
   *         CASE(Vulkan)
   *         CASE(OpenGL)
   *         CASE(OpenGLES)
   * #undef CASE
   *
   *         default:
   *             break;
   *     }
   *
   *     if (!testCtx) {
   *         return ContextInfo{};
   *     }
   *
   *     std::unique_ptr<skgpu::graphite::Context> context = testCtx->makeContext(fOptions);
   *     if (!context) {
   *         return ContextInfo{};
   *     }
   *
   *     fContexts.push_back({type, std::move(testCtx), std::move(context)});
   *     return AsContextInfo(fContexts.back());
   * }
   * ```
   */
  public fun getContextInfo(type: ContextType): ContextInfo {
    TODO("Implement getContextInfo")
  }

  public data class OwnedContextInfo public constructor(
    public var fType: ContextType,
    public var fTestContext: Int,
    public var fContext: Int,
  ) {
    public fun assign(param0: undefined.OwnedContextInfo) {
      TODO("Implement assign")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * ContextInfo ContextFactory::AsContextInfo(const OwnedContextInfo& owned) {
     *     return ContextInfo{owned.fTestContext.get(), owned.fContext.get()};
     * }
     * ```
     */
    private fun asContextInfo(ctx: OwnedContextInfo): ContextInfo {
      TODO("Implement asContextInfo")
    }
  }
}
