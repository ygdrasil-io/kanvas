package org.skia.tools

import kotlin.Int
import org.skia.gpu.DawnBackendContext
import wgpu.BackendType

/**
 * C++ original:
 * ```cpp
 * class DawnTestContext : public GraphiteTestContext {
 * public:
 *     ~DawnTestContext() override;
 *
 *     static std::unique_ptr<GraphiteTestContext> Make(wgpu::BackendType backend);
 *
 *     skgpu::BackendApi backend() override { return skgpu::BackendApi::kDawn; }
 *
 *     skgpu::ContextType contextType() override;
 *
 *     std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) override;
 *
 *     const skgpu::graphite::DawnBackendContext& getBackendContext() const {
 *         return fBackendContext;
 *     }
 *
 *     void tick() override;
 *
 * protected:
 *     DawnTestContext(const skgpu::graphite::DawnBackendContext& backendContext)
 *             : fBackendContext(backendContext) {}
 *
 *     skgpu::graphite::DawnBackendContext fBackendContext;
 * }
 * ```
 */
public open class DawnTestContext public constructor(
  backendContext: DawnBackendContext,
) : GraphiteTestContext() {
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::DawnBackendContext fBackendContext
   * ```
   */
  protected var fBackendContext: Int = TODO("Initialize fBackendContext")

  /**
   * C++ original:
   * ```cpp
   * skgpu::BackendApi backend() override { return skgpu::BackendApi::kDawn; }
   * ```
   */
  public override fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::ContextType contextType() override
   * ```
   */
  public override fun contextType(): Int {
    TODO("Implement contextType")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skgpu::graphite::Context> makeContext(const TestOptions&) override
   * ```
   */
  public override fun makeContext(param0: TestOptions): Int {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * const skgpu::graphite::DawnBackendContext& getBackendContext() const {
   *         return fBackendContext;
   *     }
   * ```
   */
  public fun getBackendContext(): Int {
    TODO("Implement getBackendContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void tick() override
   * ```
   */
  public override fun tick() {
    TODO("Implement tick")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<GraphiteTestContext> Make(wgpu::BackendType backend)
     * ```
     */
    public fun make(backend: BackendType): Int {
      TODO("Implement make")
    }
  }
}
