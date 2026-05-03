package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct Member {
 *     StringValue fKey;
 *     Value       fValue;
 * }
 * ```
 */
public open class Member public constructor(
  /**
   * C++ original:
   * ```cpp
   * StringValue fKey
   * ```
   */
  public var fKey: StringValue,
  /**
   * C++ original:
   * ```cpp
   * Value       fValue
   * ```
   */
  public var fValue: Value,
)
