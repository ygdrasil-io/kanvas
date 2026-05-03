package org.skia.gpu

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class CullTest {
 * public:
 *     CullTest() = default;
 *
 *     CullTest(const SkRect& devCullBounds, const SkMatrix& m) {
 *         this->set(devCullBounds, m);
 *     }
 *
 *     void set(const SkRect& devCullBounds, const SkMatrix& m) {
 *         SkASSERT(!m.hasPerspective());
 *         // [fMatX, fMatY] maps path coordinates to the float4 [x, y, -x, -y] in device space.
 *         fMatX = {m.getScaleX(), m.getSkewY(), -m.getScaleX(), -m.getSkewY()};
 *         fMatY = {m.getSkewX(), m.getScaleY(), -m.getSkewX(), -m.getScaleY()};
 *         // Store the cull bounds as [l, t, -r, -b] for faster math.
 *         // Also subtract the matrix translate from the cull bounds ahead of time, rather than adding
 *         // it to every point every time we test.
 *         fCullBounds = {devCullBounds.fLeft - m.getTranslateX(),
 *                        devCullBounds.fTop - m.getTranslateY(),
 *                        m.getTranslateX() - devCullBounds.fRight,
 *                        m.getTranslateY() - devCullBounds.fBottom};
 *     }
 *
 *     // Returns whether M*p will be in the viewport.
 *     bool isVisible(SkPoint p) const {
 *         // devPt = [x, y, -x, -y] in device space.
 *         auto devPt = fMatX*p.fX + fMatY*p.fY;
 *         // i.e., l < x && t < y && r > x && b > y.
 *         return all(fCullBounds < devPt);
 *     }
 *
 *     // Returns whether any region of the bounding box of M * p0..2 will be in the viewport.
 *     bool areVisible3(const SkPoint p[3]) const {
 *         // Transform p0..2 to device space.
 *         auto val0 = fMatY * p[0].fY;
 *         auto val1 = fMatY * p[1].fY;
 *         auto val2 = fMatY * p[2].fY;
 *         val0 = fMatX*p[0].fX + val0;
 *         val1 = fMatX*p[1].fX + val1;
 *         val2 = fMatX*p[2].fX + val2;
 *         // At this point: valN = {xN, yN, -xN, -yN} in device space.
 *
 *         // Find the device-space bounding box of p0..2.
 *         val0 = max(val0, val1);
 *         val0 = max(val0, val2);
 *         // At this point: val0 = [r, b, -l, -t] of the device-space bounding box of p0..2.
 *
 *         // Does fCullBounds intersect the device-space bounding box of p0..2?
 *         // i.e., l0 < r1 && t0 < b1 && r0 > l1 && b0 > t1.
 *         return all(fCullBounds < val0);
 *     }
 *
 *     // Returns whether any region of the bounding box of M * p0..3 will be in the viewport.
 *     bool areVisible4(const SkPoint p[4]) const {
 *         // Transform p0..3 to device space.
 *         auto val0 = fMatY * p[0].fY;
 *         auto val1 = fMatY * p[1].fY;
 *         auto val2 = fMatY * p[2].fY;
 *         auto val3 = fMatY * p[3].fY;
 *         val0 = fMatX*p[0].fX + val0;
 *         val1 = fMatX*p[1].fX + val1;
 *         val2 = fMatX*p[2].fX + val2;
 *         val3 = fMatX*p[3].fX + val3;
 *         // At this point: valN = {xN, yN, -xN, -yN} in device space.
 *
 *         // Find the device-space bounding box of p0..3.
 *         val0 = max(val0, val1);
 *         val2 = max(val2, val3);
 *         val0 = max(val0, val2);
 *         // At this point: val0 = [r, b, -l, -t] of the device-space bounding box of p0..3.
 *
 *         // Does fCullBounds intersect the device-space bounding box of p0..3?
 *         // i.e., l0 < r1 && t0 < b1 && r0 > l1 && b0 > t1.
 *         return all(fCullBounds < val0);
 *     }
 *
 * private:
 *     // [fMatX, fMatY] maps path coordinates to the float4 [x, y, -x, -y] in device space.
 *     skvx::float4 fMatX;
 *     skvx::float4 fMatY;
 *     skvx::float4 fCullBounds;  // [l, t, -r, -b]
 * }
 * ```
 */
public data class CullTest public constructor(
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fMatX
   * ```
   */
  private var fMatX: Int,
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fMatY
   * ```
   */
  private var fMatY: Int,
  /**
   * C++ original:
   * ```cpp
   * skvx::float4 fCullBounds
   * ```
   */
  private var fCullBounds: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(const SkRect& devCullBounds, const SkMatrix& m) {
   *         SkASSERT(!m.hasPerspective());
   *         // [fMatX, fMatY] maps path coordinates to the float4 [x, y, -x, -y] in device space.
   *         fMatX = {m.getScaleX(), m.getSkewY(), -m.getScaleX(), -m.getSkewY()};
   *         fMatY = {m.getSkewX(), m.getScaleY(), -m.getSkewX(), -m.getScaleY()};
   *         // Store the cull bounds as [l, t, -r, -b] for faster math.
   *         // Also subtract the matrix translate from the cull bounds ahead of time, rather than adding
   *         // it to every point every time we test.
   *         fCullBounds = {devCullBounds.fLeft - m.getTranslateX(),
   *                        devCullBounds.fTop - m.getTranslateY(),
   *                        m.getTranslateX() - devCullBounds.fRight,
   *                        m.getTranslateY() - devCullBounds.fBottom};
   *     }
   * ```
   */
  public fun `set`(devCullBounds: SkRect, m: SkMatrix) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVisible(SkPoint p) const {
   *         // devPt = [x, y, -x, -y] in device space.
   *         auto devPt = fMatX*p.fX + fMatY*p.fY;
   *         // i.e., l < x && t < y && r > x && b > y.
   *         return all(fCullBounds < devPt);
   *     }
   * ```
   */
  public fun isVisible(p: SkPoint): Boolean {
    TODO("Implement isVisible")
  }

  /**
   * C++ original:
   * ```cpp
   * bool areVisible3(const SkPoint p[3]) const {
   *         // Transform p0..2 to device space.
   *         auto val0 = fMatY * p[0].fY;
   *         auto val1 = fMatY * p[1].fY;
   *         auto val2 = fMatY * p[2].fY;
   *         val0 = fMatX*p[0].fX + val0;
   *         val1 = fMatX*p[1].fX + val1;
   *         val2 = fMatX*p[2].fX + val2;
   *         // At this point: valN = {xN, yN, -xN, -yN} in device space.
   *
   *         // Find the device-space bounding box of p0..2.
   *         val0 = max(val0, val1);
   *         val0 = max(val0, val2);
   *         // At this point: val0 = [r, b, -l, -t] of the device-space bounding box of p0..2.
   *
   *         // Does fCullBounds intersect the device-space bounding box of p0..2?
   *         // i.e., l0 < r1 && t0 < b1 && r0 > l1 && b0 > t1.
   *         return all(fCullBounds < val0);
   *     }
   * ```
   */
  public fun areVisible3(p: Array<SkPoint>): Boolean {
    TODO("Implement areVisible3")
  }

  /**
   * C++ original:
   * ```cpp
   * bool areVisible4(const SkPoint p[4]) const {
   *         // Transform p0..3 to device space.
   *         auto val0 = fMatY * p[0].fY;
   *         auto val1 = fMatY * p[1].fY;
   *         auto val2 = fMatY * p[2].fY;
   *         auto val3 = fMatY * p[3].fY;
   *         val0 = fMatX*p[0].fX + val0;
   *         val1 = fMatX*p[1].fX + val1;
   *         val2 = fMatX*p[2].fX + val2;
   *         val3 = fMatX*p[3].fX + val3;
   *         // At this point: valN = {xN, yN, -xN, -yN} in device space.
   *
   *         // Find the device-space bounding box of p0..3.
   *         val0 = max(val0, val1);
   *         val2 = max(val2, val3);
   *         val0 = max(val0, val2);
   *         // At this point: val0 = [r, b, -l, -t] of the device-space bounding box of p0..3.
   *
   *         // Does fCullBounds intersect the device-space bounding box of p0..3?
   *         // i.e., l0 < r1 && t0 < b1 && r0 > l1 && b0 > t1.
   *         return all(fCullBounds < val0);
   *     }
   * ```
   */
  public fun areVisible4(p: Array<SkPoint>): Boolean {
    TODO("Implement areVisible4")
  }
}
