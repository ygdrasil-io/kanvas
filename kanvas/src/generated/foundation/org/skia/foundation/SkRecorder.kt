package org.skia.foundation

import org.skia.core.Recorder
import org.skia.core.SkCanvas
import org.skia.core.SkSurface

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRecorder {
 * public:
 *     SkRecorder() = default;
 *     virtual ~SkRecorder() = default;
 *     SkRecorder(const SkRecorder&) = delete;
 *     SkRecorder(SkRecorder&&) = delete;
 *     SkRecorder& operator=(const SkRecorder&) = delete;
 *
 *     enum class Type {
 *         kCPU,
 *         kGanesh,
 *         kGraphite,
 *     };
 *
 *     virtual Type type() const = 0;
 *
 *     virtual skcpu::Recorder* cpuRecorder() = 0;
 *
 * private:
 *
 *     /**
 *     * Attempts to create and return an SkCaptureCanvas that wraps the provided base canvas.
 *     * Returns nullptr if capture is not enabled.
 *     */
 *     virtual SkCanvas* makeCaptureCanvas(SkCanvas*) = 0;
 *     virtual void createCaptureBreakpoint(SkSurface*) = 0;
 *
 *     friend class SkSurface_Base; // for capture functionality
 * }
 * ```
 */
public abstract class SkRecorder public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkRecorder() = default
   * ```
   */
  public constructor(param0: SkRecorder) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRecorder& operator=(const SkRecorder&) = delete
   * ```
   */
  public fun assign(param0: SkRecorder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Type type() const = 0
   * ```
   */
  public abstract fun type(): Type

  /**
   * C++ original:
   * ```cpp
   * virtual skcpu::Recorder* cpuRecorder() = 0
   * ```
   */
  public abstract fun cpuRecorder(): Recorder

  /**
   * C++ original:
   * ```cpp
   * virtual SkCanvas* makeCaptureCanvas(SkCanvas*) = 0
   * ```
   */
  private abstract fun makeCaptureCanvas(param0: SkCanvas?): SkCanvas

  /**
   * C++ original:
   * ```cpp
   * virtual void createCaptureBreakpoint(SkSurface*) = 0
   * ```
   */
  private abstract fun createCaptureBreakpoint(param0: SkSurface?)

  public enum class Type {
    kCPU,
    kGanesh,
    kGraphite,
  }
}
