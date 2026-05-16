package org.skia.tools

import kotlin.Int
import kotlin.String
import org.skia.core.TArray
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import undefined.SkCommandLineConfigGpu

/**
 * C++ original:
 * ```cpp
 * class SkCommandLineConfig {
 * public:
 *     SkCommandLineConfig(const SkString&           tag,
 *                         const SkString&           backend,
 *                         const skia_private::TArray<SkString>& viaParts);
 *     virtual ~SkCommandLineConfig();
 *     virtual const SkCommandLineConfigGpu* asConfigGpu() const { return nullptr; }
 *     virtual const SkCommandLineConfigGraphite* asConfigGraphite() const { return nullptr; }
 *     virtual const SkCommandLineConfigSvg* asConfigSvg() const { return nullptr; }
 *     const SkString&                       getTag() const { return fTag; }
 *     const SkString&                       getBackend() const { return fBackend; }
 *     sk_sp<SkColorSpace>                   refColorSpace() const { return fColorSpace; }
 *     const skia_private::TArray<SkString>& getViaParts() const { return fViaParts; }
 *
 * private:
 *     SkString                       fTag;
 *     SkString                       fBackend;
 *     sk_sp<SkColorSpace>            fColorSpace;
 *     skia_private::TArray<SkString> fViaParts;
 * }
 * ```
 */
public open class SkCommandLineConfig public constructor(
  tag: String,
  backend: String,
  viaParts: TArray<String>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkString                       fTag
   * ```
   */
  private var fTag: String = TODO("Initialize fTag")

  /**
   * C++ original:
   * ```cpp
   * SkString                       fBackend
   * ```
   */
  private var fBackend: String = TODO("Initialize fBackend")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace>            fColorSpace
   * ```
   */
  private var fColorSpace: SkSp<SkColorSpace> = TODO("Initialize fColorSpace")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkString> fViaParts
   * ```
   */
  private var fViaParts: Int = TODO("Initialize fViaParts")

  /**
   * C++ original:
   * ```cpp
   * virtual const SkCommandLineConfigGpu* asConfigGpu() const { return nullptr; }
   * ```
   */
  public open fun asConfigGpu(): SkCommandLineConfigGpu {
    TODO("Implement asConfigGpu")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const SkCommandLineConfigGraphite* asConfigGraphite() const { return nullptr; }
   * ```
   */
  public open fun asConfigGraphite(): SkCommandLineConfigGraphite {
    TODO("Implement asConfigGraphite")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const SkCommandLineConfigSvg* asConfigSvg() const { return nullptr; }
   * ```
   */
  public open fun asConfigSvg(): SkCommandLineConfigSvg {
    TODO("Implement asConfigSvg")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString&                       getTag() const { return fTag; }
   * ```
   */
  public fun getTag(): String {
    TODO("Implement getTag")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString&                       getBackend() const { return fBackend; }
   * ```
   */
  public fun getBackend(): String {
    TODO("Implement getBackend")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace>                   refColorSpace() const { return fColorSpace; }
   * ```
   */
  public fun refColorSpace(): SkSp<SkColorSpace> {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * const skia_private::TArray<SkString>& getViaParts() const { return fViaParts; }
   * ```
   */
  public fun getViaParts(): TArray<String> {
    TODO("Implement getViaParts")
  }
}
