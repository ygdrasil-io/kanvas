package org.skia.tests

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct LinearEffect {
 *     ui::Dataspace inputDataspace = ui::Dataspace::SRGB;
 *     ui::Dataspace outputDataspace = ui::Dataspace::SRGB;
 *     bool undoPremultipliedAlpha = false;
 *     ui::Dataspace fakeOutputDataspace = ui::Dataspace::UNKNOWN;
 *
 *     enum SkSLType { Shader, ColorFilter };
 *     SkSLType type = Shader;
 * }
 * ```
 */
public data class LinearEffect public constructor(
  /**
   * C++ original:
   * ```cpp
   * ui::Dataspace inputDataspace = ui::Dataspace::SRGB
   * ```
   */
  public var inputDataspace: Dataspace,
  /**
   * C++ original:
   * ```cpp
   * ui::Dataspace outputDataspace = ui::Dataspace::SRGB
   * ```
   */
  public var outputDataspace: Dataspace,
  /**
   * C++ original:
   * ```cpp
   * bool undoPremultipliedAlpha = false
   * ```
   */
  public var undoPremultipliedAlpha: Boolean,
  /**
   * C++ original:
   * ```cpp
   * ui::Dataspace fakeOutputDataspace = ui::Dataspace::UNKNOWN
   * ```
   */
  public var fakeOutputDataspace: Dataspace,
  /**
   * C++ original:
   * ```cpp
   * SkSLType type = Shader
   * ```
   */
  public var type: SkSLType,
) {
  public enum class SkSLType {
    Shader,
    ColorFilter,
  }
}
