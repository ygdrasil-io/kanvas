package org.skia.modules

import kotlin.Any
import kotlin.Int
import kotlin.Pair

/**
 * C++ original:
 * ```cpp
 * template<> inline const myers::Point& get<1>(const myers::Segment& s) { return s.lower(); }
 *
 * // -- Crossing -------------------------------------------------------------------------------------
 * class Crossing {
 * public:
 *     Crossing(const Segment& s0, const Segment& s1) : Crossing{std::minmax(s0, s1)} {}
 *     friend bool operator<(const Crossing& c0, const Crossing& c1);
 *     friend bool operator==(const Crossing& c0, const Crossing& c1);
 *
 * private:
 *     Crossing(std::tuple<Segment, Segment> highLow)
 *             : fHigher{std::get<0>(highLow)}
 *             , fLower{std::get<1>(highLow)} {}
 *
 *     Segment fHigher;
 *     Segment fLower;
 * }
 * ```
 */
public open class Crossing public constructor(
  s0: Segment,
  s1: Segment,
) : Point(),
    Int,
    Any,
    Segment {
  /**
   * C++ original:
   * ```cpp
   * Segment fHigher
   * ```
   */
  private var fHigher: Segment = TODO("Initialize fHigher")

  /**
   * C++ original:
   * ```cpp
   * Segment fLower
   * ```
   */
  private var fLower: Segment = TODO("Initialize fLower")

  /**
   * C++ original:
   * ```cpp
   * Crossing(const Segment& s0, const Segment& s1) : Crossing{std::minmax(s0, s1)} {}
   * ```
   */
  public constructor(highLow: Pair<Segment, Segment>) : this() {
    TODO("Implement constructor")
  }
}
