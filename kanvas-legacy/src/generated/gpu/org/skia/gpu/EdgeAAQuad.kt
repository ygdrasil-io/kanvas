package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class EdgeAAQuad {
 * public:
 *     // SkEnumBitMask<Flags> is a typesafe equivalent to SkCanvas::QuadAAFlags.
 *     enum class Flags : uint8_t {
 *         kLeft   = 0b0001,
 *         kTop    = 0b0010,
 *         kRight  = 0b0100,
 *         kBottom = 0b1000,
 *
 *         kNone   = 0b0000,
 *         kAll    = 0b1111,
 *     };
 *
 *     EdgeAAQuad() = delete;
 *
 *     EdgeAAQuad(const SkRect& rect, SkEnumBitMask<Flags> edgeFlags)
 *             : fXs{rect.fLeft, rect.fRight, rect.fRight, rect.fLeft}
 *             , fYs{rect.fTop, rect.fTop, rect.fBottom, rect.fBottom}
 *             , fEdgeFlags(edgeFlags)
 *             , fIsRect(true) {}
 *     EdgeAAQuad(const Rect& rect, SkEnumBitMask<Flags> edgeFlags)
 *             : fXs{skvx::shuffle<0,2,2,0>(rect.ltrb())}
 *             , fYs{skvx::shuffle<1,1,3,3>(rect.ltrb())}
 *             , fEdgeFlags(edgeFlags)
 *             , fIsRect(true) {}
 *     EdgeAAQuad(const SkPoint points[4], SkEnumBitMask<Flags> edgeFlags)
 *             : fXs{points[0].fX, points[1].fX, points[2].fX, points[3].fX}
 *             , fYs{points[0].fY, points[1].fY, points[2].fY, points[3].fY}
 *             , fEdgeFlags(edgeFlags)
 *             , fIsRect(false) {}
 *     EdgeAAQuad(const skvx::float4& xs, const skvx::float4& ys, SkEnumBitMask<Flags> edgeFlags)
 *             : fXs(xs)
 *             , fYs(ys)
 *             , fEdgeFlags(edgeFlags)
 *             , fIsRect(false) {}
 *
 *     // The bounding box of the quadrilateral (not counting any outsetting for anti-aliasing).
 *     Rect bounds() const {
 *         if (fIsRect) {
 *             return Rect({fXs[0], fYs[0]}, {fXs[2], fYs[2]});
 *         }
 *
 *         Rect p0p1 = Rect::LTRB(skvx::shuffle<0,2,1,3>(skvx::float4(fXs.lo, fYs.lo))).makeSorted();
 *         Rect p2p3 = Rect::LTRB(skvx::shuffle<0,2,1,3>(skvx::float4(fXs.hi, fYs.hi))).makeSorted();
 *         return p0p1.makeJoin(p2p3);
 *     }
 *
 *     // Access the individual elements of the quad data.
 *     const skvx::float4& xs() const { return fXs; }
 *     const skvx::float4& ys() const { return fYs; }
 *     SkEnumBitMask<Flags> edgeFlags() const { return fEdgeFlags; }
 *
 *     bool isRect() const { return fIsRect; }
 *
 * private:
 *     skvx::float4 fXs;
 *     skvx::float4 fYs;
 *     SkEnumBitMask<Flags> fEdgeFlags;
 *     bool fIsRect;
 * }
 * ```
 */
public data class EdgeAAQuad public constructor(
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fXs
   * ```
   */
  private var fXs: Int,
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fYs
   * ```
   */
  private var fYs: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<Flags> fEdgeFlags
   * ```
   */
  private var fEdgeFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fIsRect
   * ```
   */
  private var fIsRect: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * Rect bounds() const {
   *         if (fIsRect) {
   *             return Rect({fXs[0], fYs[0]}, {fXs[2], fYs[2]});
   *         }
   *
   *         Rect p0p1 = Rect::LTRB(skvx::shuffle<0,2,1,3>(skvx::float4(fXs.lo, fYs.lo))).makeSorted();
   *         Rect p2p3 = Rect::LTRB(skvx::shuffle<0,2,1,3>(skvx::float4(fXs.hi, fYs.hi))).makeSorted();
   *         return p0p1.makeJoin(p2p3);
   *     }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const skvx::float4& xs() const { return fXs; }
   * ```
   */
  public fun xs(): Int {
    TODO("Implement xs")
  }

  /**
   * C++ original:
   * ```cpp
   * const skvx::float4& ys() const { return fYs; }
   * ```
   */
  public fun ys(): Int {
    TODO("Implement ys")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<Flags> edgeFlags() const { return fEdgeFlags; }
   * ```
   */
  public fun edgeFlags(): Int {
    TODO("Implement edgeFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRect() const { return fIsRect; }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  public enum class Flags {
    kLeft,
    kTop,
    kRight,
    kBottom,
    kNone,
    kAll,
  }
}
