package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.U8CPU

/**
 * C++ original:
 * ```cpp
 * class RowIter {
 * public:
 *     RowIter(const uint8_t* row, const SkIRect& bounds) {
 *         fRow = row;
 *         fLeft = bounds.fLeft;
 *         fBoundsRight = bounds.fRight;
 *         if (row) {
 *             fRight = bounds.fLeft + row[0];
 *             SkASSERT(fRight <= fBoundsRight);
 *             fAlpha = row[1];
 *             fDone = false;
 *         } else {
 *             fDone = true;
 *             fRight = kMaxInt32;
 *             fAlpha = 0;
 *         }
 *     }
 *
 *     bool done() const { return fDone; }
 *     int left() const { return fLeft; }
 *     int right() const { return fRight; }
 *     U8CPU alpha() const { return fAlpha; }
 *     void next() {
 *         if (!fDone) {
 *             fLeft = fRight;
 *             if (fRight == fBoundsRight) {
 *                 fDone = true;
 *                 fRight = kMaxInt32;
 *                 fAlpha = 0;
 *             } else {
 *                 fRow += 2;
 *                 fRight += fRow[0];
 *                 fAlpha = fRow[1];
 *                 SkASSERT(fRight <= fBoundsRight);
 *             }
 *         }
 *     }
 *
 * private:
 *     const uint8_t*  fRow;
 *     int             fLeft;
 *     int             fRight;
 *     int             fBoundsRight;
 *     bool            fDone;
 *     uint8_t         fAlpha;
 * }
 * ```
 */
public data class RowIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const uint8_t*  fRow
   * ```
   */
  private val fRow: Int?,
  /**
   * C++ original:
   * ```cpp
   * int             fLeft
   * ```
   */
  private var fLeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int             fRight
   * ```
   */
  private var fRight: Int,
  /**
   * C++ original:
   * ```cpp
   * int             fBoundsRight
   * ```
   */
  private var fBoundsRight: Int,
  /**
   * C++ original:
   * ```cpp
   * bool            fDone
   * ```
   */
  private var fDone: Boolean,
  /**
   * C++ original:
   * ```cpp
   * uint8_t         fAlpha
   * ```
   */
  private var fAlpha: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool done() const { return fDone; }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * int left() const { return fLeft; }
   * ```
   */
  public fun left(): Int {
    TODO("Implement left")
  }

  /**
   * C++ original:
   * ```cpp
   * int right() const { return fRight; }
   * ```
   */
  public fun right(): Int {
    TODO("Implement right")
  }

  /**
   * C++ original:
   * ```cpp
   * U8CPU alpha() const { return fAlpha; }
   * ```
   */
  public fun alpha(): U8CPU {
    TODO("Implement alpha")
  }

  /**
   * C++ original:
   * ```cpp
   * void next() {
   *         if (!fDone) {
   *             fLeft = fRight;
   *             if (fRight == fBoundsRight) {
   *                 fDone = true;
   *                 fRight = kMaxInt32;
   *                 fAlpha = 0;
   *             } else {
   *                 fRow += 2;
   *                 fRight += fRow[0];
   *                 fAlpha = fRow[1];
   *                 SkASSERT(fRight <= fBoundsRight);
   *             }
   *         }
   *     }
   * ```
   */
  public fun next() {
    TODO("Implement next")
  }
}
