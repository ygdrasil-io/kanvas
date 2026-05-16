package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct SinkFlags {
 *     enum Type { kNull, kGPU, kVector, kRaster } type;
 *     enum Approach { kDirect, kIndirect } approach;
 *     enum Multisampled { kNotMultisampled, kMultisampled } multisampled;
 *     SinkFlags(Type t, Approach a, Multisampled ms = kNotMultisampled)
 *             : type(t), approach(a), multisampled(ms) {}
 * }
 * ```
 */
public data class SinkFlags public constructor(
  /**
   * C++ original:
   * ```cpp
   * enum Type { kNull, kGPU, kVector, kRaster } type
   * ```
   */
  public var type: Type,
  /**
   * C++ original:
   * ```cpp
   * enum Approach { kDirect, kIndirect } approach
   * ```
   */
  public var approach: Approach,
  /**
   * C++ original:
   * ```cpp
   * enum Multisampled { kNotMultisampled, kMultisampled } multisampled
   * ```
   */
  public var multisampled: Multisampled,
) {
  public enum class Type {
    kNull,
    kGPU,
    kVector,
    kRaster,
  }

  public enum class Approach {
    kDirect,
    kIndirect,
  }

  public enum class Multisampled {
    kNotMultisampled,
    kMultisampled,
  }
}
