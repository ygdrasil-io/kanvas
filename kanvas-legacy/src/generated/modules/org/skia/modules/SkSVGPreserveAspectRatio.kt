package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkSVGPreserveAspectRatio {
 *     enum Align : uint8_t {
 *         // These values are chosen such that bits [0,1] encode X alignment, and
 *         // bits [2,3] encode Y alignment.
 *         kXMinYMin = 0x00,
 *         kXMidYMin = 0x01,
 *         kXMaxYMin = 0x02,
 *         kXMinYMid = 0x04,
 *         kXMidYMid = 0x05,
 *         kXMaxYMid = 0x06,
 *         kXMinYMax = 0x08,
 *         kXMidYMax = 0x09,
 *         kXMaxYMax = 0x0a,
 *
 *         kNone     = 0x10,
 *     };
 *
 *     enum Scale {
 *         kMeet,
 *         kSlice,
 *     };
 *
 *     Align fAlign = kXMidYMid;
 *     Scale fScale = kMeet;
 * }
 * ```
 */
public data class SkSVGPreserveAspectRatio public constructor(
  /**
   * C++ original:
   * ```cpp
   * Align fAlign = kXMidYMid
   * ```
   */
  public var fAlign: Align,
  /**
   * C++ original:
   * ```cpp
   * Scale fScale = kMeet
   * ```
   */
  public var fScale: Scale,
) {
  public enum class Align {
    kXMinYMin,
    kXMidYMin,
    kXMaxYMin,
    kXMinYMid,
    kXMidYMid,
    kXMaxYMid,
    kXMinYMax,
    kXMidYMax,
    kXMaxYMax,
    kNone,
  }

  public enum class Scale {
    kMeet,
    kSlice,
  }
}
