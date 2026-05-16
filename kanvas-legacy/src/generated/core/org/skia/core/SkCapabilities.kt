package org.skia.core

import kotlin.Int
import org.skia.foundation.SkRefCnt
import org.skia.sksl.ShaderCaps

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCapabilities : public SkRefCnt {
 * public:
 *     static sk_sp<const SkCapabilities> RasterBackend();
 *
 *     SkSL::Version skslVersion() const { return fSkSLVersion; }
 *
 * protected:
 *     friend class skgpu::graphite::Caps; // for ctor
 *
 *     SkCapabilities() = default;
 *
 *     void initSkCaps(const SkSL::ShaderCaps*);
 *
 *     SkSL::Version fSkSLVersion = SkSL::Version::k100;
 * }
 * ```
 */
public open class SkCapabilities public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkSL::Version fSkSLVersion
   * ```
   */
  protected var fSkSLVersion: Int = TODO("Initialize fSkSLVersion")

  /**
   * C++ original:
   * ```cpp
   * SkSL::Version skslVersion() const { return fSkSLVersion; }
   * ```
   */
  public fun skslVersion(): Int {
    TODO("Implement skslVersion")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCapabilities::initSkCaps(const SkSL::ShaderCaps* shaderCaps) {
   *     this->fSkSLVersion = shaderCaps->supportedSkSLVerion();
   * }
   * ```
   */
  protected fun initSkCaps(shaderCaps: ShaderCaps?) {
    TODO("Implement initSkCaps")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<const SkCapabilities> SkCapabilities::RasterBackend() {
     *     static SkCapabilities* sCaps = []() {
     *         SkCapabilities* caps = new SkCapabilities;
     *         caps->fSkSLVersion = SkSL::Version::k100;
     *         return caps;
     *     }();
     *
     *     return sk_ref_sp(sCaps);
     * }
     * ```
     */
    public fun rasterBackend(): Int {
      TODO("Implement rasterBackend")
    }
  }
}
