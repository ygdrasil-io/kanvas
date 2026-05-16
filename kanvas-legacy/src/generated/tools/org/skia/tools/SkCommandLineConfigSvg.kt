package org.skia.tools

import kotlin.Int
import kotlin.String
import org.skia.core.TArray

/**
 * C++ original:
 * ```cpp
 * class SkCommandLineConfigSvg : public SkCommandLineConfig {
 * public:
 *     SkCommandLineConfigSvg(const SkString& tag, const skia_private::TArray<SkString>& viaParts, int pageIndex);
 *     const SkCommandLineConfigSvg* asConfigSvg() const override { return this; }
 *
 *     int getPageIndex() const { return fPageIndex; }
 *
 * private:
 *     int fPageIndex;
 * }
 * ```
 */
public open class SkCommandLineConfigSvg public constructor(
  tag: String,
  viaParts: TArray<String>,
  pageIndex: Int,
) : SkCommandLineConfig() {
  /**
   * C++ original:
   * ```cpp
   * int fPageIndex
   * ```
   */
  private var fPageIndex: Int = TODO("Initialize fPageIndex")

  /**
   * C++ original:
   * ```cpp
   * const SkCommandLineConfigSvg* asConfigSvg() const override { return this; }
   * ```
   */
  public override fun asConfigSvg(): SkCommandLineConfigSvg {
    TODO("Implement asConfigSvg")
  }

  /**
   * C++ original:
   * ```cpp
   * int getPageIndex() const { return fPageIndex; }
   * ```
   */
  public fun getPageIndex(): Int {
    TODO("Implement getPageIndex")
  }
}
