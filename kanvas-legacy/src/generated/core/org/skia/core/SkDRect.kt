package org.skia.core

import kotlin.Boolean
import kotlin.Double

/**
 * C++ original:
 * ```cpp
 * struct SkDRect {
 *     double fLeft, fTop, fRight, fBottom;
 *
 *     void add(const SkDPoint& pt) {
 *         fLeft = std::min(fLeft, pt.fX);
 *         fTop = std::min(fTop, pt.fY);
 *         fRight = std::max(fRight, pt.fX);
 *         fBottom = std::max(fBottom, pt.fY);
 *     }
 *
 *     bool contains(const SkDPoint& pt) const {
 *         return approximately_between(fLeft, pt.fX, fRight)
 *                 && approximately_between(fTop, pt.fY, fBottom);
 *     }
 *
 *     void debugInit();
 *
 *     bool intersects(const SkDRect& r) const {
 *         SkASSERT(fLeft <= fRight);
 *         SkASSERT(fTop <= fBottom);
 *         SkASSERT(r.fLeft <= r.fRight);
 *         SkASSERT(r.fTop <= r.fBottom);
 *         return r.fLeft <= fRight && fLeft <= r.fRight && r.fTop <= fBottom && fTop <= r.fBottom;
 *     }
 *
 *     void set(const SkDPoint& pt) {
 *         fLeft = fRight = pt.fX;
 *         fTop = fBottom = pt.fY;
 *     }
 *
 *     double width() const {
 *         return fRight - fLeft;
 *     }
 *
 *     double height() const {
 *         return fBottom - fTop;
 *     }
 *
 *     void setBounds(const SkDConic& curve) {
 *         setBounds(curve, curve, 0, 1);
 *     }
 *
 *     void setBounds(const SkDConic& curve, const SkDConic& sub, double tStart, double tEnd);
 *
 *     void setBounds(const SkDCubic& curve) {
 *         setBounds(curve, curve, 0, 1);
 *     }
 *
 *     void setBounds(const SkDCubic& curve, const SkDCubic& sub, double tStart, double tEnd);
 *
 *     void setBounds(const SkDQuad& curve) {
 *         setBounds(curve, curve, 0, 1);
 *     }
 *
 *     void setBounds(const SkDQuad& curve, const SkDQuad& sub, double tStart, double tEnd);
 *
 *     void setBounds(const SkTCurve& curve);
 *
 *     bool valid() const {
 *         return fLeft <= fRight && fTop <= fBottom;
 *     }
 * }
 * ```
 */
public data class SkDRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * double fLeft
   * ```
   */
  public var fLeft: Double,
  /**
   * C++ original:
   * ```cpp
   * double fLeft, fTop
   * ```
   */
  public var fTop: Double,
  /**
   * C++ original:
   * ```cpp
   * double fLeft, fTop, fRight
   * ```
   */
  public var fRight: Double,
  /**
   * C++ original:
   * ```cpp
   * double fLeft, fTop, fRight, fBottom
   * ```
   */
  public var fBottom: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * void add(const SkDPoint& pt) {
   *         fLeft = std::min(fLeft, pt.fX);
   *         fTop = std::min(fTop, pt.fY);
   *         fRight = std::max(fRight, pt.fX);
   *         fBottom = std::max(fBottom, pt.fY);
   *     }
   * ```
   */
  public fun add(pt: SkDPoint) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const SkDPoint& pt) const {
   *         return approximately_between(fLeft, pt.fX, fRight)
   *                 && approximately_between(fTop, pt.fY, fBottom);
   *     }
   * ```
   */
  public fun contains(pt: SkDPoint): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDRect::debugInit() {
   *     fLeft = fTop = fRight = fBottom = SK_ScalarNaN;
   * }
   * ```
   */
  public fun debugInit() {
    TODO("Implement debugInit")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersects(const SkDRect& r) const {
   *         SkASSERT(fLeft <= fRight);
   *         SkASSERT(fTop <= fBottom);
   *         SkASSERT(r.fLeft <= r.fRight);
   *         SkASSERT(r.fTop <= r.fBottom);
   *         return r.fLeft <= fRight && fLeft <= r.fRight && r.fTop <= fBottom && fTop <= r.fBottom;
   *     }
   * ```
   */
  public fun intersects(r: SkDRect): Boolean {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkDPoint& pt) {
   *         fLeft = fRight = pt.fX;
   *         fTop = fBottom = pt.fY;
   *     }
   * ```
   */
  public fun `set`(pt: SkDPoint) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * double width() const {
   *         return fRight - fLeft;
   *     }
   * ```
   */
  public fun width(): Double {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * double height() const {
   *         return fBottom - fTop;
   *     }
   * ```
   */
  public fun height(): Double {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBounds(const SkDConic& curve) {
   *         setBounds(curve, curve, 0, 1);
   *     }
   * ```
   */
  public fun setBounds(curve: SkDConic) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDRect::setBounds(const SkDConic& curve, const SkDConic& sub, double startT, double endT) {
   *     set(sub[0]);
   *     add(sub[2]);
   *     double tValues[2];
   *     int roots = 0;
   *     if (!sub.monotonicInX()) {
   *         roots = SkDConic::FindExtrema(&sub[0].fX, sub.fWeight, tValues);
   *     }
   *     if (!sub.monotonicInY()) {
   *         roots += SkDConic::FindExtrema(&sub[0].fY, sub.fWeight, &tValues[roots]);
   *     }
   *     for (int index = 0; index < roots; ++index) {
   *         double t = startT + (endT - startT) * tValues[index];
   *         add(curve.ptAtT(t));
   *     }
   * }
   * ```
   */
  public fun setBounds(
    curve: SkDConic,
    sub: SkDConic,
    tStart: Double,
    tEnd: Double,
  ) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBounds(const SkDCubic& curve) {
   *         setBounds(curve, curve, 0, 1);
   *     }
   * ```
   */
  public fun setBounds(curve: SkDCubic) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDRect::setBounds(const SkDCubic& curve, const SkDCubic& sub, double startT, double endT) {
   *     set(sub[0]);
   *     add(sub[3]);
   *     double tValues[4];
   *     int roots = 0;
   *     if (!sub.monotonicInX()) {
   *         roots = SkDCubic::FindExtrema(&sub[0].fX, tValues);
   *     }
   *     if (!sub.monotonicInY()) {
   *         roots += SkDCubic::FindExtrema(&sub[0].fY, &tValues[roots]);
   *     }
   *     for (int index = 0; index < roots; ++index) {
   *         double t = startT + (endT - startT) * tValues[index];
   *         add(curve.ptAtT(t));
   *     }
   * }
   * ```
   */
  public fun setBounds(
    curve: SkDCubic,
    sub: SkDCubic,
    tStart: Double,
    tEnd: Double,
  ) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBounds(const SkDQuad& curve) {
   *         setBounds(curve, curve, 0, 1);
   *     }
   * ```
   */
  public fun setBounds(curve: SkDQuad) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDRect::setBounds(const SkDQuad& curve, const SkDQuad& sub, double startT, double endT) {
   *     set(sub[0]);
   *     add(sub[2]);
   *     double tValues[2];
   *     int roots = 0;
   *     if (!sub.monotonicInX()) {
   *         roots = SkDQuad::FindExtrema(&sub[0].fX, tValues);
   *     }
   *     if (!sub.monotonicInY()) {
   *         roots += SkDQuad::FindExtrema(&sub[0].fY, &tValues[roots]);
   *     }
   *     for (int index = 0; index < roots; ++index) {
   *         double t = startT + (endT - startT) * tValues[index];
   *         add(curve.ptAtT(t));
   *     }
   * }
   * ```
   */
  public fun setBounds(
    curve: SkDQuad,
    sub: SkDQuad,
    tStart: Double,
    tEnd: Double,
  ) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDRect::setBounds(const SkTCurve& curve) {
   *     curve.setBounds(this);
   * }
   * ```
   */
  public fun setBounds(curve: SkTCurve) {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool valid() const {
   *         return fLeft <= fRight && fTop <= fBottom;
   *     }
   * ```
   */
  public fun valid(): Boolean {
    TODO("Implement valid")
  }
}
