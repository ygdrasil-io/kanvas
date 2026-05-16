package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Edge {
 *     enum {
 *         kY0Link = 0x01,
 *         kY1Link = 0x02,
 *
 *         kCompleteLink = (kY0Link | kY1Link)
 *     };
 *
 *     SkRegionPriv::RunType fX;
 *     SkRegionPriv::RunType fY0, fY1;
 *     uint8_t fFlags;
 *     Edge*   fNext;
 *
 *     void set(int x, int y0, int y1) {
 *         SkASSERT(y0 != y1);
 *
 *         fX = (SkRegionPriv::RunType)(x);
 *         fY0 = (SkRegionPriv::RunType)(y0);
 *         fY1 = (SkRegionPriv::RunType)(y1);
 *         fFlags = 0;
 *         SkDEBUGCODE(fNext = nullptr;)
 *     }
 *
 *     int top() const {
 *         return std::min(fY0, fY1);
 *     }
 * }
 * ```
 */
public data class Edge public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType fX
   * ```
   */
  public var fX: SkRegionPrivRunType,
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType fY0
   * ```
   */
  public var fY0: SkRegionPrivRunType,
  /**
   * C++ original:
   * ```cpp
   * SkRegionPriv::RunType fY0, fY1
   * ```
   */
  public var fY1: SkRegionPrivRunType,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fFlags
   * ```
   */
  public var fFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * Edge*   fNext
   * ```
   */
  public var fNext: Edge?,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(int x, int y0, int y1) {
   *         SkASSERT(y0 != y1);
   *
   *         fX = (SkRegionPriv::RunType)(x);
   *         fY0 = (SkRegionPriv::RunType)(y0);
   *         fY1 = (SkRegionPriv::RunType)(y1);
   *         fFlags = 0;
   *         SkDEBUGCODE(fNext = nullptr;)
   *     }
   * ```
   */
  public fun `set`(
    x: Int,
    y0: Int,
    y1: Int,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * int top() const {
   *         return std::min(fY0, fY1);
   *     }
   * ```
   */
  public fun top(): Int {
    TODO("Implement top")
  }

  public companion object {
    public val kY0Link: Int = TODO("Initialize kY0Link")

    public val kY1Link: Int = TODO("Initialize kY1Link")

    public val kCompleteLink: Int = TODO("Initialize kCompleteLink")
  }
}
