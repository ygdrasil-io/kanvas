package org.skia.tools

import kotlin.Int
import org.skia.gpu.MtlBackendContext

/**
 * C++ original:
 * ```cpp
 * class MtlTestContext : public GraphiteTestContext {
 * public:
 *     ~MtlTestContext() override {}
 *
 *     static std::unique_ptr<GraphiteTestContext> Make();
 *
 *     skgpu::BackendApi backend() override { return skgpu::BackendApi::kMetal; }
 *
 *     skgpu::ContextType contextType() override;
 *
 *     std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) override;
 *
 *     const skgpu::graphite::MtlBackendContext& getBackendContext() const {
 *         return fMtl;
 *     }
 *
 * protected:
 *     MtlTestContext(const skgpu::graphite::MtlBackendContext& mtl) : fMtl(mtl) {}
 *
 *     skgpu::graphite::MtlBackendContext fMtl;
 * }
 * ```
 */
public open class MtlTestContext public constructor(
  mtl: MtlBackendContext,
) : GraphiteTestContext() {
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::MtlBackendContext fMtl
   * ```
   */
  protected var fMtl: Int = TODO("Initialize fMtl")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi backend() override { return skgpu::BackendApi::kMetal; }
   * ```
   */
  public override fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType MtlTestContext::contextType() {
   *     return skgpu::ContextType::kMetal;
   * }
   * ```
   */
  public override fun contextType(): Int {
    TODO("Implement contextType")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skgpu::graphite::Context> MtlTestContext::makeContext(const TestOptions& options) {
   *     SkASSERT(!options.hasDawnOptions());
   *     skgpu::graphite::ContextOptions revisedContextOptions(options.fContextOptions);
   *     skgpu::graphite::ContextOptionsPriv contextOptionsPriv;
   *     if (!options.fContextOptions.fOptionsPriv) {
   *         revisedContextOptions.fOptionsPriv = &contextOptionsPriv;
   *     }
   *     // Needed to make synchronous readPixels work
   *     revisedContextOptions.fOptionsPriv->fStoreContextRefInRecorder = true;
   *
   *     return skgpu::graphite::ContextFactory::MakeMetal(fMtl, revisedContextOptions);
   * }
   * ```
   */
  public override fun makeContext(options: TestOptions): Int {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const skgpu::graphite::MtlBackendContext& getBackendContext() const {
   *         return fMtl;
   *     }
   * ```
   */
  public fun getBackendContext(): Int {
    TODO("Implement getBackendContext")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<GraphiteTestContext> MtlTestContext::Make() {
     *     sk_cfp<id<MTLDevice>> device;
     * #ifdef SK_BUILD_FOR_MAC
     *     sk_cfp<NSArray<id <MTLDevice>>*> availableDevices(MTLCopyAllDevices());
     *     // Choose the non-integrated CPU if available
     *     for (id<MTLDevice> dev in availableDevices.get()) {
     *         if (!dev.isLowPower) {
     *             // This retain is necessary because when the NSArray goes away it will delete the
     *             // device entry otherwise.
     *             device.retain(dev);
     *             break;
     *         }
     *         if (dev.isRemovable) {
     *             device.retain(dev);
     *             break;
     *         }
     *     }
     *     if (!device) {
     *         device.reset(MTLCreateSystemDefaultDevice());
     *     }
     * #else
     *     device.reset(MTLCreateSystemDefaultDevice());
     * #endif
     *
     *     skgpu::graphite::MtlBackendContext backendContext = {};
     *     backendContext.fDevice.retain(device.get());
     *     backendContext.fQueue.reset([*device newCommandQueue]);
     *
     *     return std::unique_ptr<GraphiteTestContext>(new MtlTestContext(backendContext));
     * }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
