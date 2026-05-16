package org.skia.tools

import kotlin.Boolean
import org.skia.gpu.ContextOptions

/**
 * C++ original:
 * ```cpp
 * struct TestOptions {
 *     TestOptions() = default;
 *     TestOptions(const TestOptions&) = default;
 *     TestOptions(TestOptions&&) = default;
 *     TestOptions& operator=(const TestOptions&) = default;
 *     TestOptions& operator=(TestOptions&&) = default;
 *
 *     bool hasDawnOptions() const {
 * #if defined(SK_DAWN)
 *         return fDisableTintSymbolRenaming ||
 *                fNeverYieldToWebGPU ||
 *                fUseWGPUTextureView;
 * #else
 *         return false;
 * #endif
 *     }
 *
 *     skgpu::graphite::ContextOptions fContextOptions = {};
 *
 * #if defined(SK_DAWN)
 *     bool fDisableTintSymbolRenaming = false;
 *     bool fNeverYieldToWebGPU = false;
 *     bool fUseWGPUTextureView = false;
 * #endif
 * }
 * ```
 */
public data class TestOptions public constructor(
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::ContextOptions fContextOptions
   * ```
   */
  public var fContextOptions: ContextOptions,
) {
  /**
   * C++ original:
   * ```cpp
   * TestOptions& operator=(const TestOptions&) = default
   * ```
   */
  public fun assign(param0: TestOptions) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * TestOptions& operator=(TestOptions&&) = default
   * ```
   */
  public fun hasDawnOptions(): Boolean {
    TODO("Implement hasDawnOptions")
  }
}
