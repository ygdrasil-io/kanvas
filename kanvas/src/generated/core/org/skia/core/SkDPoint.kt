package org.skia.core

import kotlin.Boolean
import kotlin.Double
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SkDPoint {
 *     double fX;
 *     double fY;
 *
 *     void set(const SkPoint& pt) {
 *         fX = pt.fX;
 *         fY = pt.fY;
 *     }
 *
 *     friend SkDVector operator-(const SkDPoint& a, const SkDPoint& b) {
 *         return { a.fX - b.fX, a.fY - b.fY };
 *     }
 *
 *     friend bool operator==(const SkDPoint& a, const SkDPoint& b) {
 *         return a.fX == b.fX && a.fY == b.fY;
 *     }
 *
 *     friend bool operator!=(const SkDPoint& a, const SkDPoint& b) {
 *         return a.fX != b.fX || a.fY != b.fY;
 *     }
 *
 *     void operator=(const SkPoint& pt) {
 *         fX = pt.fX;
 *         fY = pt.fY;
 *     }
 *
 *     // only used by testing
 *     void operator+=(const SkDVector& v) {
 *         fX += v.fX;
 *         fY += v.fY;
 *     }
 *
 *     // only used by testing
 *     void operator-=(const SkDVector& v) {
 *         fX -= v.fX;
 *         fY -= v.fY;
 *     }
 *
 *     // only used by testing
 *     SkDPoint operator+(const SkDVector& v) {
 *         SkDPoint result = *this;
 *         result += v;
 *         return result;
 *     }
 *
 *     // only used by testing
 *     SkDPoint operator-(const SkDVector& v) {
 *         SkDPoint result = *this;
 *         result -= v;
 *         return result;
 *     }
 *
 *     // note: this can not be implemented with
 *     // return approximately_equal(a.fY, fY) && approximately_equal(a.fX, fX);
 *     // because that will not take the magnitude of the values into account
 *     bool approximatelyDEqual(const SkDPoint& a) const {
 *         if (approximately_equal(fX, a.fX) && approximately_equal(fY, a.fY)) {
 *             return true;
 *         }
 *         if (!RoughlyEqualUlps(fX, a.fX) || !RoughlyEqualUlps(fY, a.fY)) {
 *             return false;
 *         }
 *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
 *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
 *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
 *         largest = std::max(largest, -tiniest);
 *         return AlmostDequalUlps(largest, largest + dist); // is the dist within ULPS tolerance?
 *     }
 *
 *     bool approximatelyDEqual(const SkPoint& a) const {
 *         SkDPoint dA;
 *         dA.set(a);
 *         return approximatelyDEqual(dA);
 *     }
 *
 *     bool approximatelyEqual(const SkDPoint& a) const {
 *         if (approximately_equal(fX, a.fX) && approximately_equal(fY, a.fY)) {
 *             return true;
 *         }
 *         if (!RoughlyEqualUlps(fX, a.fX) || !RoughlyEqualUlps(fY, a.fY)) {
 *             return false;
 *         }
 *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
 *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
 *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
 *         largest = std::max(largest, -tiniest);
 *         return AlmostPequalUlps(largest, largest + dist); // is the dist within ULPS tolerance?
 *     }
 *
 *     bool approximatelyEqual(const SkPoint& a) const {
 *         SkDPoint dA;
 *         dA.set(a);
 *         return approximatelyEqual(dA);
 *     }
 *
 *     static bool ApproximatelyEqual(const SkPoint& a, const SkPoint& b) {
 *         if (approximately_equal(a.fX, b.fX) && approximately_equal(a.fY, b.fY)) {
 *             return true;
 *         }
 *         if (!RoughlyEqualUlps(a.fX, b.fX) || !RoughlyEqualUlps(a.fY, b.fY)) {
 *             return false;
 *         }
 *         SkDPoint dA, dB;
 *         dA.set(a);
 *         dB.set(b);
 *         double dist = dA.distance(dB);  // OPTIMIZATION: can we compare against distSq instead ?
 *         float tiniest = std::min(std::min(std::min(a.fX, b.fX), a.fY), b.fY);
 *         float largest = std::max(std::max(std::max(a.fX, b.fX), a.fY), b.fY);
 *         largest = std::max(largest, -tiniest);
 *         return AlmostDequalUlps((double) largest, largest + dist); // is dist within ULPS tolerance?
 *     }
 *
 *     // only used by testing
 *     bool approximatelyZero() const {
 *         return approximately_zero(fX) && approximately_zero(fY);
 *     }
 *
 *     SkPoint asSkPoint() const {
 *         SkPoint pt = {SkDoubleToScalar(fX), SkDoubleToScalar(fY)};
 *         return pt;
 *     }
 *
 *     double distance(const SkDPoint& a) const {
 *         SkDVector temp = *this - a;
 *         return temp.length();
 *     }
 *
 *     double distanceSquared(const SkDPoint& a) const {
 *         SkDVector temp = *this - a;
 *         return temp.lengthSquared();
 *     }
 *
 *     static SkDPoint Mid(const SkDPoint& a, const SkDPoint& b) {
 *         SkDPoint result;
 *         result.fX = (a.fX + b.fX) / 2;
 *         result.fY = (a.fY + b.fY) / 2;
 *         return result;
 *     }
 *
 *     bool roughlyEqual(const SkDPoint& a) const {
 *         if (roughly_equal(fX, a.fX) && roughly_equal(fY, a.fY)) {
 *             return true;
 *         }
 *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
 *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
 *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
 *         largest = std::max(largest, -tiniest);
 *         return RoughlyEqualUlps(largest, largest + dist); // is the dist within ULPS tolerance?
 *     }
 *
 *     static bool RoughlyEqual(const SkPoint& a, const SkPoint& b) {
 *         if (!RoughlyEqualUlps(a.fX, b.fX) && !RoughlyEqualUlps(a.fY, b.fY)) {
 *             return false;
 *         }
 *         SkDPoint dA, dB;
 *         dA.set(a);
 *         dB.set(b);
 *         double dist = dA.distance(dB);  // OPTIMIZATION: can we compare against distSq instead ?
 *         float tiniest = std::min(std::min(std::min(a.fX, b.fX), a.fY), b.fY);
 *         float largest = std::max(std::max(std::max(a.fX, b.fX), a.fY), b.fY);
 *         largest = std::max(largest, -tiniest);
 *         return RoughlyEqualUlps((double) largest, largest + dist); // is dist within ULPS tolerance?
 *     }
 *
 *     // very light weight check, should only be used for inequality check
 *     static bool WayRoughlyEqual(const SkPoint& a, const SkPoint& b) {
 *         float largestNumber = std::max(SkTAbs(a.fX), std::max(SkTAbs(a.fY),
 *                 std::max(SkTAbs(b.fX), SkTAbs(b.fY))));
 *         SkVector diffs = a - b;
 *         float largestDiff = std::max(SkTAbs(diffs.fX), SkTAbs(diffs.fY));
 *         return roughly_zero_when_compared_to(largestDiff, largestNumber);
 *     }
 *
 *     // utilities callable by the user from the debugger when the implementation code is linked in
 *     void dump() const;
 *     static void Dump(const SkPoint& pt);
 *     static void DumpHex(const SkPoint& pt);
 * }
 * ```
 */
public data class SkDPoint public constructor(
  /**
   * C++ original:
   * ```cpp
   * double fX
   * ```
   */
  public var fX: Double,
  /**
   * C++ original:
   * ```cpp
   * double fY
   * ```
   */
  public var fY: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(const SkPoint& pt) {
   *         fX = pt.fX;
   *         fY = pt.fY;
   *     }
   * ```
   */
  public fun `set`(pt: SkPoint) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator=(const SkPoint& pt) {
   *         fX = pt.fX;
   *         fY = pt.fY;
   *     }
   * ```
   */
  public fun assign(pt: SkPoint) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(const SkDVector& v) {
   *         fX += v.fX;
   *         fY += v.fY;
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkDVector) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(const SkDVector& v) {
   *         fX -= v.fX;
   *         fY -= v.fY;
   *     }
   * ```
   */
  public operator fun minusAssign(v: SkDVector) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint operator+(const SkDVector& v) {
   *         SkDPoint result = *this;
   *         result += v;
   *         return result;
   *     }
   * ```
   */
  public operator fun plus(v: SkDVector): SkDPoint {
    TODO("Implement plus")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint operator-(const SkDVector& v) {
   *         SkDPoint result = *this;
   *         result -= v;
   *         return result;
   *     }
   * ```
   */
  public operator fun minus(v: SkDVector): SkDPoint {
    TODO("Implement minus")
  }

  /**
   * C++ original:
   * ```cpp
   * bool approximatelyDEqual(const SkDPoint& a) const {
   *         if (approximately_equal(fX, a.fX) && approximately_equal(fY, a.fY)) {
   *             return true;
   *         }
   *         if (!RoughlyEqualUlps(fX, a.fX) || !RoughlyEqualUlps(fY, a.fY)) {
   *             return false;
   *         }
   *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
   *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
   *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
   *         largest = std::max(largest, -tiniest);
   *         return AlmostDequalUlps(largest, largest + dist); // is the dist within ULPS tolerance?
   *     }
   * ```
   */
  public fun approximatelyDEqual(a: SkDPoint): Boolean {
    TODO("Implement approximatelyDEqual")
  }

  /**
   * C++ original:
   * ```cpp
   * bool approximatelyDEqual(const SkPoint& a) const {
   *         SkDPoint dA;
   *         dA.set(a);
   *         return approximatelyDEqual(dA);
   *     }
   * ```
   */
  public fun approximatelyDEqual(a: SkPoint): Boolean {
    TODO("Implement approximatelyDEqual")
  }

  /**
   * C++ original:
   * ```cpp
   * bool approximatelyEqual(const SkDPoint& a) const {
   *         if (approximately_equal(fX, a.fX) && approximately_equal(fY, a.fY)) {
   *             return true;
   *         }
   *         if (!RoughlyEqualUlps(fX, a.fX) || !RoughlyEqualUlps(fY, a.fY)) {
   *             return false;
   *         }
   *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
   *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
   *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
   *         largest = std::max(largest, -tiniest);
   *         return AlmostPequalUlps(largest, largest + dist); // is the dist within ULPS tolerance?
   *     }
   * ```
   */
  public fun approximatelyEqual(a: SkDPoint): Boolean {
    TODO("Implement approximatelyEqual")
  }

  /**
   * C++ original:
   * ```cpp
   * bool approximatelyEqual(const SkPoint& a) const {
   *         SkDPoint dA;
   *         dA.set(a);
   *         return approximatelyEqual(dA);
   *     }
   * ```
   */
  public fun approximatelyEqual(a: SkPoint): Boolean {
    TODO("Implement approximatelyEqual")
  }

  /**
   * C++ original:
   * ```cpp
   * bool approximatelyZero() const {
   *         return approximately_zero(fX) && approximately_zero(fY);
   *     }
   * ```
   */
  public fun approximatelyZero(): Boolean {
    TODO("Implement approximatelyZero")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint asSkPoint() const {
   *         SkPoint pt = {SkDoubleToScalar(fX), SkDoubleToScalar(fY)};
   *         return pt;
   *     }
   * ```
   */
  public fun asSkPoint(): SkPoint {
    TODO("Implement asSkPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * double distance(const SkDPoint& a) const {
   *         SkDVector temp = *this - a;
   *         return temp.length();
   *     }
   * ```
   */
  public fun distance(a: SkDPoint): Double {
    TODO("Implement distance")
  }

  /**
   * C++ original:
   * ```cpp
   * double distanceSquared(const SkDPoint& a) const {
   *         SkDVector temp = *this - a;
   *         return temp.lengthSquared();
   *     }
   * ```
   */
  public fun distanceSquared(a: SkDPoint): Double {
    TODO("Implement distanceSquared")
  }

  /**
   * C++ original:
   * ```cpp
   * bool roughlyEqual(const SkDPoint& a) const {
   *         if (roughly_equal(fX, a.fX) && roughly_equal(fY, a.fY)) {
   *             return true;
   *         }
   *         double dist = distance(a);  // OPTIMIZATION: can we compare against distSq instead ?
   *         double tiniest = std::min(std::min(std::min(fX, a.fX), fY), a.fY);
   *         double largest = std::max(std::max(std::max(fX, a.fX), fY), a.fY);
   *         largest = std::max(largest, -tiniest);
   *         return RoughlyEqualUlps(largest, largest + dist); // is the dist within ULPS tolerance?
   *     }
   * ```
   */
  public fun roughlyEqual(a: SkDPoint): Boolean {
    TODO("Implement roughlyEqual")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDPoint::dump() const {
   *     SkDebugf("{");
   *     DebugDumpDouble(fX);
   *     SkDebugf(", ");
   *     DebugDumpDouble(fY);
   *     SkDebugf("}");
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool ApproximatelyEqual(const SkPoint& a, const SkPoint& b) {
     *         if (approximately_equal(a.fX, b.fX) && approximately_equal(a.fY, b.fY)) {
     *             return true;
     *         }
     *         if (!RoughlyEqualUlps(a.fX, b.fX) || !RoughlyEqualUlps(a.fY, b.fY)) {
     *             return false;
     *         }
     *         SkDPoint dA, dB;
     *         dA.set(a);
     *         dB.set(b);
     *         double dist = dA.distance(dB);  // OPTIMIZATION: can we compare against distSq instead ?
     *         float tiniest = std::min(std::min(std::min(a.fX, b.fX), a.fY), b.fY);
     *         float largest = std::max(std::max(std::max(a.fX, b.fX), a.fY), b.fY);
     *         largest = std::max(largest, -tiniest);
     *         return AlmostDequalUlps((double) largest, largest + dist); // is dist within ULPS tolerance?
     *     }
     * ```
     */
    public fun approximatelyEqual(a: SkPoint, b: SkPoint): Boolean {
      TODO("Implement approximatelyEqual")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkDPoint Mid(const SkDPoint& a, const SkDPoint& b) {
     *         SkDPoint result;
     *         result.fX = (a.fX + b.fX) / 2;
     *         result.fY = (a.fY + b.fY) / 2;
     *         return result;
     *     }
     * ```
     */
    public fun mid(a: SkDPoint, b: SkDPoint): SkDPoint {
      TODO("Implement mid")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool RoughlyEqual(const SkPoint& a, const SkPoint& b) {
     *         if (!RoughlyEqualUlps(a.fX, b.fX) && !RoughlyEqualUlps(a.fY, b.fY)) {
     *             return false;
     *         }
     *         SkDPoint dA, dB;
     *         dA.set(a);
     *         dB.set(b);
     *         double dist = dA.distance(dB);  // OPTIMIZATION: can we compare against distSq instead ?
     *         float tiniest = std::min(std::min(std::min(a.fX, b.fX), a.fY), b.fY);
     *         float largest = std::max(std::max(std::max(a.fX, b.fX), a.fY), b.fY);
     *         largest = std::max(largest, -tiniest);
     *         return RoughlyEqualUlps((double) largest, largest + dist); // is dist within ULPS tolerance?
     *     }
     * ```
     */
    public fun roughlyEqual(a: SkPoint, b: SkPoint): Boolean {
      TODO("Implement roughlyEqual")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool WayRoughlyEqual(const SkPoint& a, const SkPoint& b) {
     *         float largestNumber = std::max(SkTAbs(a.fX), std::max(SkTAbs(a.fY),
     *                 std::max(SkTAbs(b.fX), SkTAbs(b.fY))));
     *         SkVector diffs = a - b;
     *         float largestDiff = std::max(SkTAbs(diffs.fX), SkTAbs(diffs.fY));
     *         return roughly_zero_when_compared_to(largestDiff, largestNumber);
     *     }
     * ```
     */
    public fun wayRoughlyEqual(a: SkPoint, b: SkPoint): Boolean {
      TODO("Implement wayRoughlyEqual")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkDPoint::Dump(const SkPoint& pt) {
     *     SkDebugf("{");
     *     DebugDumpFloat(pt.fX);
     *     SkDebugf(", ");
     *     DebugDumpFloat(pt.fY);
     *     SkDebugf("}");
     * }
     * ```
     */
    public fun dump(pt: SkPoint) {
      TODO("Implement dump")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkDPoint::DumpHex(const SkPoint& pt) {
     *     SkDebugf("{");
     *     DebugDumpHexFloat(pt.fX);
     *     SkDebugf(", ");
     *     DebugDumpHexFloat(pt.fY);
     *     SkDebugf("}");
     * }
     * ```
     */
    public fun dumpHex(pt: SkPoint) {
      TODO("Implement dumpHex")
    }
  }
}
