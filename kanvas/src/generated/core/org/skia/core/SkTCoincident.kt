package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Double

/**
 * C++ original:
 * ```cpp
 * class SkTCoincident {
 * public:
 *     SkTCoincident() {
 *         this->init();
 *     }
 *
 *     void debugInit() {
 * #ifdef SK_DEBUG
 *         this->fPerpPt.fX = this->fPerpPt.fY = SK_ScalarNaN;
 *         this->fPerpT = SK_ScalarNaN;
 *         this->fMatch = 0xFF;
 * #endif
 *     }
 *
 *     char dumpIsCoincidentStr() const;
 *     void dump() const;
 *
 *     bool isMatch() const {
 *         SkASSERT(!!fMatch == fMatch);
 *         return SkToBool(fMatch);
 *     }
 *
 *     void init() {
 *         fPerpT = -1;
 *         fMatch = false;
 *         fPerpPt.fX = fPerpPt.fY = SK_ScalarNaN;
 *     }
 *
 *     void markCoincident() {
 *         if (!fMatch) {
 *             fPerpT = -1;
 *         }
 *         fMatch = true;
 *     }
 *
 *     const SkDPoint& perpPt() const {
 *         return fPerpPt;
 *     }
 *
 *     double perpT() const {
 *         return fPerpT;
 *     }
 *
 *     void setPerp(const SkTCurve& c1, double t, const SkDPoint& cPt, const SkTCurve& );
 *
 * private:
 *     SkDPoint fPerpPt;
 *     double fPerpT;  // perpendicular intersection on opposite curve
 *     SkOpDebugBool fMatch;
 * }
 * ```
 */
public data class SkTCoincident public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDPoint fPerpPt
   * ```
   */
  private var fPerpPt: SkDPoint,
  /**
   * C++ original:
   * ```cpp
   * double fPerpT
   * ```
   */
  private var fPerpT: Double,
  /**
   * C++ original:
   * ```cpp
   * SkOpDebugBool fMatch
   * ```
   */
  private var fMatch: SkOpDebugBool,
) {
  /**
   * C++ original:
   * ```cpp
   * void debugInit() {
   * #ifdef SK_DEBUG
   *         this->fPerpPt.fX = this->fPerpPt.fY = SK_ScalarNaN;
   *         this->fPerpT = SK_ScalarNaN;
   *         this->fMatch = 0xFF;
   * #endif
   *     }
   * ```
   */
  public fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * char SkTCoincident::dumpIsCoincidentStr() const {
   *     if (!!fMatch != fMatch) {
   *         return '?';
   *     }
   *     return fMatch ? '*' : 0;
   * }
   * ```
   */
  public fun dumpIsCoincidentStr(): Char {
    TODO("Implement dumpIsCoincidentStr")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTCoincident::dump() const {
   *     SkDebugf("t=%1.9g pt=(%1.9g,%1.9g)%s\n", fPerpT, fPerpPt.fX, fPerpPt.fY,
   *             fMatch ? " match" : "");
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isMatch() const {
   *         SkASSERT(!!fMatch == fMatch);
   *         return SkToBool(fMatch);
   *     }
   * ```
   */
  public fun isMatch(): Boolean {
    TODO("Implement isMatch")
  }

  /**
   * C++ original:
   * ```cpp
   * void init() {
   *         fPerpT = -1;
   *         fMatch = false;
   *         fPerpPt.fX = fPerpPt.fY = SK_ScalarNaN;
   *     }
   * ```
   */
  public fun `init`() {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void markCoincident() {
   *         if (!fMatch) {
   *             fPerpT = -1;
   *         }
   *         fMatch = true;
   *     }
   * ```
   */
  public fun markCoincident() {
    TODO("Implement markCoincident")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDPoint& perpPt() const {
   *         return fPerpPt;
   *     }
   * ```
   */
  public fun perpPt(): SkDPoint {
    TODO("Implement perpPt")
  }

  /**
   * C++ original:
   * ```cpp
   * double perpT() const {
   *         return fPerpT;
   *     }
   * ```
   */
  public fun perpT(): Double {
    TODO("Implement perpT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTCoincident::setPerp(const SkTCurve& c1, double t,
   *         const SkDPoint& cPt, const SkTCurve& c2) {
   *     SkDVector dxdy = c1.dxdyAtT(t);
   *     SkDLine perp = {{ cPt, {cPt.fX + dxdy.fY, cPt.fY - dxdy.fX} }};
   *     SkIntersections i  SkDEBUGCODE((c1.globalState()));
   *     int used = i.intersectRay(c2, perp);
   *     // only keep closest
   *     if (used == 0 || used == 3) {
   *         this->init();
   *         return;
   *     }
   *     fPerpT = i[0][0];
   *     fPerpPt = i.pt(0);
   *     SkASSERT(used <= 2);
   *     if (used == 2) {
   *         double distSq = (fPerpPt - cPt).lengthSquared();
   *         double dist2Sq = (i.pt(1) - cPt).lengthSquared();
   *         if (dist2Sq < distSq) {
   *             fPerpT = i[0][1];
   *             fPerpPt = i.pt(1);
   *         }
   *     }
   * #if DEBUG_T_SECT
   *     SkDebugf("setPerp t=%1.9g cPt=(%1.9g,%1.9g) %s oppT=%1.9g fPerpPt=(%1.9g,%1.9g)\n",
   *             t, cPt.fX, cPt.fY,
   *             cPt.approximatelyEqual(fPerpPt) ? "==" : "!=", fPerpT, fPerpPt.fX, fPerpPt.fY);
   * #endif
   *     fMatch = cPt.approximatelyEqual(fPerpPt);
   * #if DEBUG_T_SECT
   *     if (fMatch) {
   *         SkDebugf("%s", "");  // allow setting breakpoint
   *     }
   * #endif
   * }
   * ```
   */
  public fun setPerp(
    c1: SkTCurve,
    t: Double,
    cPt: SkDPoint,
    c2: SkTCurve,
  ) {
    TODO("Implement setPerp")
  }
}
