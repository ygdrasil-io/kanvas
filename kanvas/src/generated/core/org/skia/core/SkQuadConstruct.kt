package org.skia.core

import kotlin.Array
import kotlin.Boolean
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SkQuadConstruct {    // The state of the quad stroke under construction.
 *     SkPoint fQuad[3];       // the stroked quad parallel to the original curve
 *     SkVector fTangentStart; // tangent vector at fQuad[0]
 *     SkVector fTangentEnd;   // tangent vector at fQuad[2]
 *     SkScalar fStartT;       // a segment of the original curve
 *     SkScalar fMidT;         //              "
 *     SkScalar fEndT;         //              "
 *     bool fStartSet;         // state to share common points across structs
 *     bool fEndSet;           //                     "
 *     bool fOppositeTangents; // set if coincident tangents have opposite directions
 *
 *     // return false if start and end are too close to have a unique middle
 *     bool init(SkScalar start, SkScalar end) {
 *         fStartT = start;
 *         fMidT = (start + end) * SK_ScalarHalf;
 *         fEndT = end;
 *         fStartSet = fEndSet = false;
 *         return fStartT < fMidT && fMidT < fEndT;
 *     }
 *
 *     bool initWithStart(SkQuadConstruct* parent) {
 *         if (!init(parent->fStartT, parent->fMidT)) {
 *             return false;
 *         }
 *         fQuad[0] = parent->fQuad[0];
 *         fTangentStart = parent->fTangentStart;
 *         fStartSet = true;
 *         return true;
 *     }
 *
 *     bool initWithEnd(SkQuadConstruct* parent) {
 *         if (!init(parent->fMidT, parent->fEndT)) {
 *             return false;
 *         }
 *         fQuad[2] = parent->fQuad[2];
 *         fTangentEnd = parent->fTangentEnd;
 *         fEndSet = true;
 *         return true;
 *    }
 * }
 * ```
 */
public data class SkQuadConstruct public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fQuad[3]
   * ```
   */
  public var fQuad: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * SkVector fTangentStart
   * ```
   */
  public var fTangentStart: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector fTangentEnd
   * ```
   */
  public var fTangentEnd: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fStartT
   * ```
   */
  public var fStartT: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fMidT
   * ```
   */
  public var fMidT: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fEndT
   * ```
   */
  public var fEndT: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * bool fStartSet
   * ```
   */
  public var fStartSet: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fEndSet
   * ```
   */
  public var fEndSet: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fOppositeTangents
   * ```
   */
  public var fOppositeTangents: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool init(SkScalar start, SkScalar end) {
   *         fStartT = start;
   *         fMidT = (start + end) * SK_ScalarHalf;
   *         fEndT = end;
   *         fStartSet = fEndSet = false;
   *         return fStartT < fMidT && fMidT < fEndT;
   *     }
   * ```
   */
  public fun `init`(start: SkScalar, end: SkScalar): Boolean {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * bool initWithStart(SkQuadConstruct* parent) {
   *         if (!init(parent->fStartT, parent->fMidT)) {
   *             return false;
   *         }
   *         fQuad[0] = parent->fQuad[0];
   *         fTangentStart = parent->fTangentStart;
   *         fStartSet = true;
   *         return true;
   *     }
   * ```
   */
  public fun initWithStart(parent: SkQuadConstruct?): Boolean {
    TODO("Implement initWithStart")
  }

  /**
   * C++ original:
   * ```cpp
   * bool initWithEnd(SkQuadConstruct* parent) {
   *         if (!init(parent->fMidT, parent->fEndT)) {
   *             return false;
   *         }
   *         fQuad[2] = parent->fQuad[2];
   *         fTangentEnd = parent->fTangentEnd;
   *         fEndSet = true;
   *         return true;
   *    }
   * ```
   */
  public fun initWithEnd(parent: SkQuadConstruct?): Boolean {
    TODO("Implement initWithEnd")
  }
}
