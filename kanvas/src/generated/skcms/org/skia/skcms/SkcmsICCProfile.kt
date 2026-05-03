package org.skia.skcms

import kotlin.Array
import kotlin.Boolean
import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct skcms_ICCProfile {
 *     const uint8_t* buffer;
 *
 *     uint32_t size;
 *     uint32_t data_color_space;
 *     uint32_t pcs;
 *     uint32_t tag_count;
 *
 *     // skcms_Parse() will set commonly-used fields for you when possible:
 *
 *     // If we can parse red, green and blue transfer curves from the profile,
 *     // trc will be set to those three curves, and has_trc will be true.
 *     skcms_Curve            trc[3];
 *
 *     // If this profile's gamut can be represented by a 3x3 transform to XYZD50,
 *     // skcms_Parse() sets toXYZD50 to that transform and has_toXYZD50 to true.
 *     skcms_Matrix3x3        toXYZD50;
 *
 *     // If the profile has a valid A2B0 or A2B1 tag, skcms_Parse() sets A2B to
 *     // that data, and has_A2B to true.  skcms_ParseWithA2BPriority() does the
 *     // same following any user-provided prioritization of A2B0, A2B1, or A2B2.
 *     skcms_A2B              A2B;
 *
 *     // If the profile has a valid B2A0 or B2A1 tag, skcms_Parse() sets B2A to
 *     // that data, and has_B2A to true.  skcms_ParseWithA2BPriority() does the
 *     // same following any user-provided prioritization of B2A0, B2A1, or B2A2.
 *     skcms_B2A              B2A;
 *
 *     // If the profile has a valid CICP tag, skcms_Parse() sets CICP to that data,
 *     // and has_CICP to true.
 *     skcms_CICP             CICP;
 *
 *     bool                   has_trc;
 *     bool                   has_toXYZD50;
 *     bool                   has_A2B;
 *     bool                   has_B2A;
 *     bool                   has_CICP;
 * }
 * ```
 */
public data class SkcmsICCProfile public constructor(
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* buffer
   * ```
   */
  public val buffer: UByte?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t size
   * ```
   */
  public var size: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t data_color_space
   * ```
   */
  public var dataColorSpace: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t pcs
   * ```
   */
  public var pcs: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t tag_count
   * ```
   */
  public var tagCount: UInt,
  /**
   * C++ original:
   * ```cpp
   * skcms_Curve            trc[3]
   * ```
   */
  public var trc: Array<SkcmsCurve>,
  /**
   * C++ original:
   * ```cpp
   * skcms_Matrix3x3        toXYZD50
   * ```
   */
  public var toXYZD50: SkcmsMatrix3x3,
  /**
   * C++ original:
   * ```cpp
   * skcms_A2B              A2B
   * ```
   */
  public var a2b: SkcmsA2B,
  /**
   * C++ original:
   * ```cpp
   * skcms_B2A              B2A
   * ```
   */
  public var b2a: SkcmsB2A,
  /**
   * C++ original:
   * ```cpp
   * skcms_CICP             CICP
   * ```
   */
  public var cicp: SkcmsCICP,
  /**
   * C++ original:
   * ```cpp
   * bool                   has_trc
   * ```
   */
  public var hasTrc: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool                   has_toXYZD50
   * ```
   */
  public var hasToXYZD50: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool                   has_A2B
   * ```
   */
  public var hasA2B: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool                   has_B2A
   * ```
   */
  public var hasB2A: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool                   has_CICP
   * ```
   */
  public var hasCICP: Boolean,
)

public typealias SkcmsICCProfile = SkcmsICCProfile
