package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.foundation.SkGlyphID
import org.skia.math.SkFixed
import org.skia.math.SkIPoint
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkPackedGlyphID {
 *     inline static constexpr uint32_t kImpossibleID = ~0u;
 *     enum {
 *         // Lengths
 *         kGlyphIDLen     = 16u,
 *         kSubPixelPosLen = 2u,
 *
 *         // Bit positions
 *         kSubPixelX = 0u,
 *         kGlyphID   = kSubPixelPosLen,
 *         kSubPixelY = kGlyphIDLen + kSubPixelPosLen,
 *         kEndData   = kGlyphIDLen + 2 * kSubPixelPosLen,
 *
 *         // Masks
 *         kGlyphIDMask     = (1u << kGlyphIDLen) - 1,
 *         kSubPixelPosMask = (1u << kSubPixelPosLen) - 1,
 *         kMaskAll         = (1u << kEndData) - 1,
 *
 *         // Location of sub pixel info in a fixed pointer number.
 *         kFixedPointBinaryPointPos = 16u,
 *         kFixedPointSubPixelPosBits = kFixedPointBinaryPointPos - kSubPixelPosLen,
 *     };
 *
 *     inline static const constexpr SkScalar kSubpixelRound =
 *             1.f / (1u << (SkPackedGlyphID::kSubPixelPosLen + 1));
 *
 *     inline static const constexpr SkIPoint kXYFieldMask{kSubPixelPosMask << kSubPixelX,
 *                                                         kSubPixelPosMask << kSubPixelY};
 *
 *     struct Hash {
 *          uint32_t operator() (SkPackedGlyphID packedID) const {
 *             return packedID.hash();
 *         }
 *     };
 *
 *     constexpr explicit SkPackedGlyphID(SkGlyphID glyphID)
 *             : fID{(uint32_t)glyphID << kGlyphID} { }
 *
 *     constexpr SkPackedGlyphID(SkGlyphID glyphID, SkFixed x, SkFixed y)
 *             : fID {PackIDXY(glyphID, x, y)} { }
 *
 *     constexpr SkPackedGlyphID(SkGlyphID glyphID, uint32_t x, uint32_t y)
 *             : fID {PackIDSubXSubY(glyphID, x, y)} { }
 *
 *     SkPackedGlyphID(SkGlyphID glyphID, SkPoint pt, SkIPoint mask)
 *         : fID{PackIDSkPoint(glyphID, pt, mask)} { }
 *
 *     constexpr explicit SkPackedGlyphID(uint32_t v) : fID{v & kMaskAll} { }
 *     constexpr SkPackedGlyphID() : fID{kImpossibleID} {}
 *
 *     bool operator==(const SkPackedGlyphID& that) const {
 *         return fID == that.fID;
 *     }
 *     bool operator!=(const SkPackedGlyphID& that) const {
 *         return !(*this == that);
 *     }
 *     bool operator<(SkPackedGlyphID that) const {
 *         return this->fID < that.fID;
 *     }
 *
 *     SkGlyphID glyphID() const {
 *         return (fID >> kGlyphID) & kGlyphIDMask;
 *     }
 *
 *     uint32_t value() const {
 *         return fID;
 *     }
 *
 *     SkFixed getSubXFixed() const {
 *         return this->subToFixed(kSubPixelX);
 *     }
 *
 *     SkFixed getSubYFixed() const {
 *         return this->subToFixed(kSubPixelY);
 *     }
 *
 *     uint32_t hash() const {
 *         return SkChecksum::CheapMix(fID);
 *     }
 *
 *     SkString dump() const {
 *         SkString str;
 *         str.appendf("glyphID: %d, x: %d, y:%d", glyphID(), getSubXFixed(), getSubYFixed());
 *         return str;
 *     }
 *
 *     SkString shortDump() const {
 *         SkString str;
 *         str.appendf("0x%x|%1u|%1u", this->glyphID(),
 *                                     this->subPixelField(kSubPixelX),
 *                                     this->subPixelField(kSubPixelY));
 *         return str;
 *     }
 *
 * private:
 *     static constexpr uint32_t PackIDSubXSubY(SkGlyphID glyphID, uint32_t x, uint32_t y) {
 *         SkASSERT(x < (1u << kSubPixelPosLen));
 *         SkASSERT(y < (1u << kSubPixelPosLen));
 *
 *         return (x << kSubPixelX) | (y << kSubPixelY) | (glyphID << kGlyphID);
 *     }
 *
 *     // Assumptions: pt is properly rounded. mask is set for the x or y fields.
 *     //
 *     // A sub-pixel field is a number on the interval [2^kSubPixel, 2^(kSubPixel + kSubPixelPosLen)).
 *     // Where kSubPixel is either kSubPixelX or kSubPixelY. Given a number x on [0, 1) we can
 *     // generate a sub-pixel field using:
 *     //    sub-pixel-field = x * 2^(kSubPixel + kSubPixelPosLen)
 *     //
 *     // We can generate the integer sub-pixel field by &-ing the integer part of sub-filed with the
 *     // sub-pixel field mask.
 *     //    int-sub-pixel-field = int(sub-pixel-field) & (kSubPixelPosMask << kSubPixel)
 *     //
 *     // The last trick is to extend the range from [0, 1) to [0, 2). The extend range is
 *     // necessary because the modulo 1 calculation (pt - floor(pt)) generates numbers on [-1, 1).
 *     // This does not round (floor) properly when converting to integer. Adding one to the range
 *     // causes truncation and floor to be the same. Coincidentally, masking to produce the field also
 *     // removes the +1.
 *     static uint32_t PackIDSkPoint(SkGlyphID glyphID, SkPoint pt, SkIPoint mask) {
 *     #if 0
 *         // TODO: why does this code not work on GCC 8.3 x86 Debug builds?
 *         using namespace skvx;
 *         using XY = Vec<2, float>;
 *         using SubXY = Vec<2, int>;
 *
 *         const XY magic = {1.f * (1u << (kSubPixelPosLen + kSubPixelX)),
 *                           1.f * (1u << (kSubPixelPosLen + kSubPixelY))};
 *         XY pos{pt.x(), pt.y()};
 *         XY subPos = (pos - floor(pos)) + 1.0f;
 *         SubXY sub = cast<int>(subPos * magic) & SubXY{mask.x(), mask.y()};
 *     #else
 *         const float magicX = 1.f * (1u << (kSubPixelPosLen + kSubPixelX)),
 *                     magicY = 1.f * (1u << (kSubPixelPosLen + kSubPixelY));
 *
 *         float x = pt.x(),
 *               y = pt.y();
 *         x = (x - floorf(x)) + 1.0f;
 *         y = (y - floorf(y)) + 1.0f;
 *         int sub[] = {
 *             (int)(x * magicX) & mask.x(),
 *             (int)(y * magicY) & mask.y(),
 *         };
 *     #endif
 *
 *         SkASSERT(sub[0] / (1u << kSubPixelX) < (1u << kSubPixelPosLen));
 *         SkASSERT(sub[1] / (1u << kSubPixelY) < (1u << kSubPixelPosLen));
 *         return (glyphID << kGlyphID) | sub[0] | sub[1];
 *     }
 *
 *     static constexpr uint32_t PackIDXY(SkGlyphID glyphID, SkFixed x, SkFixed y) {
 *         return PackIDSubXSubY(glyphID, FixedToSub(x), FixedToSub(y));
 *     }
 *
 *     static constexpr uint32_t FixedToSub(SkFixed n) {
 *         return ((uint32_t)n >> kFixedPointSubPixelPosBits) & kSubPixelPosMask;
 *     }
 *
 *     constexpr uint32_t subPixelField(uint32_t subPixelPosBit) const {
 *         return (fID >> subPixelPosBit) & kSubPixelPosMask;
 *     }
 *
 *     constexpr SkFixed subToFixed(uint32_t subPixelPosBit) const {
 *         uint32_t subPixelPosition = this->subPixelField(subPixelPosBit);
 *         return subPixelPosition << kFixedPointSubPixelPosBits;
 *     }
 *
 *     uint32_t fID;
 * }
 * ```
 */
public data class SkPackedGlyphID public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr uint32_t kImpossibleID = ~0u
   * ```
   */
  private var fID: UInt,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkPackedGlyphID& that) const {
   *         return fID == that.fID;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkPackedGlyphID& that) const {
   *         return !(*this == that);
   *     }
   * ```
   */
  public operator fun compareTo(that: SkPackedGlyphID): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator<(SkPackedGlyphID that) const {
   *         return this->fID < that.fID;
   *     }
   * ```
   */
  public fun glyphID(): SkGlyphID {
    TODO("Implement glyphID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphID glyphID() const {
   *         return (fID >> kGlyphID) & kGlyphIDMask;
   *     }
   * ```
   */
  public fun `value`(): UInt {
    TODO("Implement value")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t value() const {
   *         return fID;
   *     }
   * ```
   */
  public fun getSubXFixed(): SkFixed {
    TODO("Implement getSubXFixed")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed getSubXFixed() const {
   *         return this->subToFixed(kSubPixelX);
   *     }
   * ```
   */
  public fun getSubYFixed(): SkFixed {
    TODO("Implement getSubYFixed")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFixed getSubYFixed() const {
   *         return this->subToFixed(kSubPixelY);
   *     }
   * ```
   */
  public fun hash(): UInt {
    TODO("Implement hash")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t hash() const {
   *         return SkChecksum::CheapMix(fID);
   *     }
   * ```
   */
  public fun dump(): String {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString dump() const {
   *         SkString str;
   *         str.appendf("glyphID: %d, x: %d, y:%d", glyphID(), getSubXFixed(), getSubYFixed());
   *         return str;
   *     }
   * ```
   */
  public fun shortDump(): String {
    TODO("Implement shortDump")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString shortDump() const {
   *         SkString str;
   *         str.appendf("0x%x|%1u|%1u", this->glyphID(),
   *                                     this->subPixelField(kSubPixelX),
   *                                     this->subPixelField(kSubPixelY));
   *         return str;
   *     }
   * ```
   */
  private fun subPixelField(subPixelPosBit: UInt): UInt {
    TODO("Implement subPixelField")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr uint32_t subPixelField(uint32_t subPixelPosBit) const {
   *         return (fID >> subPixelPosBit) & kSubPixelPosMask;
   *     }
   * ```
   */
  private fun subToFixed(subPixelPosBit: UInt): SkFixed {
    TODO("Implement subToFixed")
  }

  public open class Hash {
    public operator fun invoke(packedID: SkPackedGlyphID): UInt {
      TODO("Implement invoke")
    }
  }

  public companion object {
    public val kGlyphIDLen: Int = TODO("Initialize kGlyphIDLen")

    public val kSubPixelPosLen: Int = TODO("Initialize kSubPixelPosLen")

    public val kSubPixelX: Int = TODO("Initialize kSubPixelX")

    public val kGlyphID: Int = TODO("Initialize kGlyphID")

    public val kSubPixelY: Int = TODO("Initialize kSubPixelY")

    public val kEndData: Int = TODO("Initialize kEndData")

    public val kGlyphIDMask: Int = TODO("Initialize kGlyphIDMask")

    public val kSubPixelPosMask: Int = TODO("Initialize kSubPixelPosMask")

    public val kMaskAll: Int = TODO("Initialize kMaskAll")

    public val kFixedPointBinaryPointPos: Int = TODO("Initialize kFixedPointBinaryPointPos")

    public val kFixedPointSubPixelPosBits: Int = TODO("Initialize kFixedPointSubPixelPosBits")

    public val kImpossibleID: UInt = TODO("Initialize kImpossibleID")

    public val kSubpixelRound: SkScalar = TODO("Initialize kSubpixelRound")

    public val kXYFieldMask: SkIPoint = TODO("Initialize kXYFieldMask")

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint32_t PackIDSubXSubY(SkGlyphID glyphID, uint32_t x, uint32_t y) {
     *         SkASSERT(x < (1u << kSubPixelPosLen));
     *         SkASSERT(y < (1u << kSubPixelPosLen));
     *
     *         return (x << kSubPixelX) | (y << kSubPixelY) | (glyphID << kGlyphID);
     *     }
     * ```
     */
    private fun packIDSubXSubY(
      glyphID: SkGlyphID,
      x: UInt,
      y: UInt,
    ): UInt {
      TODO("Implement packIDSubXSubY")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t PackIDSkPoint(SkGlyphID glyphID, SkPoint pt, SkIPoint mask) {
     *     #if 0
     *         // TODO: why does this code not work on GCC 8.3 x86 Debug builds?
     *         using namespace skvx;
     *         using XY = Vec<2, float>;
     *         using SubXY = Vec<2, int>;
     *
     *         const XY magic = {1.f * (1u << (kSubPixelPosLen + kSubPixelX)),
     *                           1.f * (1u << (kSubPixelPosLen + kSubPixelY))};
     *         XY pos{pt.x(), pt.y()};
     *         XY subPos = (pos - floor(pos)) + 1.0f;
     *         SubXY sub = cast<int>(subPos * magic) & SubXY{mask.x(), mask.y()};
     *     #else
     *         const float magicX = 1.f * (1u << (kSubPixelPosLen + kSubPixelX)),
     *                     magicY = 1.f * (1u << (kSubPixelPosLen + kSubPixelY));
     *
     *         float x = pt.x(),
     *               y = pt.y();
     *         x = (x - floorf(x)) + 1.0f;
     *         y = (y - floorf(y)) + 1.0f;
     *         int sub[] = {
     *             (int)(x * magicX) & mask.x(),
     *             (int)(y * magicY) & mask.y(),
     *         };
     *     #endif
     *
     *         SkASSERT(sub[0] / (1u << kSubPixelX) < (1u << kSubPixelPosLen));
     *         SkASSERT(sub[1] / (1u << kSubPixelY) < (1u << kSubPixelPosLen));
     *         return (glyphID << kGlyphID) | sub[0] | sub[1];
     *     }
     * ```
     */
    private fun packIDSkPoint(
      glyphID: SkGlyphID,
      pt: SkPoint,
      mask: SkIPoint,
    ): UInt {
      TODO("Implement packIDSkPoint")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint32_t PackIDXY(SkGlyphID glyphID, SkFixed x, SkFixed y) {
     *         return PackIDSubXSubY(glyphID, FixedToSub(x), FixedToSub(y));
     *     }
     * ```
     */
    private fun packIDXY(
      glyphID: SkGlyphID,
      x: SkFixed,
      y: SkFixed,
    ): UInt {
      TODO("Implement packIDXY")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr uint32_t FixedToSub(SkFixed n) {
     *         return ((uint32_t)n >> kFixedPointSubPixelPosBits) & kSubPixelPosMask;
     *     }
     * ```
     */
    private fun fixedToSub(n: SkFixed): UInt {
      TODO("Implement fixedToSub")
    }
  }
}
