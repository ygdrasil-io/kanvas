package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct GeometryEffectRec {
 *     const skjson::ObjectValue& fJson;
 *     GeometryEffectAttacherT    fAttach;
 * }
 * ```
 */
public data class GeometryEffectRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const skjson::ObjectValue& fJson
   * ```
   */
  public val fJson: ObjectValue,
  /**
   * C++ original:
   * ```cpp
   * GeometryEffectAttacherT    fAttach
   * ```
   */
  public var fAttach: GeometryAttacherT,
)
