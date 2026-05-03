package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkPathContourIter {
 * public:
 *     struct Rec {
 *         SkSpan<const SkPoint>    fPoints;
 *         SkSpan<const SkPathVerb> fVerbs;
 *         SkSpan<const float>      fConics;
 *     };
 *
 *     SkPathContourIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs,
 *                       SkSpan<const float> cns)
 *         : fPoints(pts), fVerbs(vbs), fConics(cns)
 *     {}
 *
 *     std::optional<Rec> next();
 *
 * private:
 *     SkSpan<const SkPoint>    fPoints;
 *     SkSpan<const SkPathVerb> fVerbs;
 *     SkSpan<const float>      fConics;
 * }
 * ```
 */
public data class SkPathContourIter public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPathContourIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs,
   *                       SkSpan<const float> cns)
   * ```
   */
  public var fPoints: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkPathContourIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs,
   *                       SkSpan<const float> cns)
   *         : fPoints(pts), fVerbs(vbs)
   * ```
   */
  public fun fVerbs(param0: Int): SkPathContourIter {
    TODO("Implement fVerbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathContourIter(SkSpan<const SkPoint> pts, SkSpan<const SkPathVerb> vbs,
   *                       SkSpan<const float> cns)
   *         : fPoints(pts), fVerbs(vbs), fConics(cns)
   * ```
   */
  public fun fConics(param0: Int): SkPathContourIter {
    TODO("Implement fConics")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathContourIter::Rec> SkPathContourIter::next() {
   *     if (fVerbs.empty()) {
   *         return {};
   *     }
   *
   *     SkASSERT(fVerbs[0] == SkPathVerb::kMove);
   *     size_t npts = 1, nvbs = 1, nws = 0;
   *
   *     for (size_t i = 1; i < fVerbs.size(); ++i) {
   *         switch (fVerbs[i]) {
   *             case SkPathVerb::kMove: goto DONE;
   *             case SkPathVerb::kLine:  npts += 1; break;
   *             case SkPathVerb::kQuad:  npts += 2; break;
   *             case SkPathVerb::kConic: npts += 2; nws += 1; break;
   *             case SkPathVerb::kCubic: npts += 3; break;
   *             case SkPathVerb::kClose: nvbs += 1; goto DONE;
   *         }
   *         nvbs += 1;
   *     }
   * DONE:
   *     Rec rec = {
   *         fPoints.subspan(0, npts),
   *         fVerbs.subspan(0, nvbs),
   *         fConics.subspan(0, nws),
   *     };
   *     fPoints = fPoints.last(fPoints.size() - npts);
   *     fVerbs  = fVerbs.last( fVerbs.size()  - nvbs);
   *     fConics = fConics.last(fConics.size() - nws);
   *     return rec;
   * }
   * ```
   */
  public fun next(): Int {
    TODO("Implement next")
  }

  public open class Rec public constructor(
    public var fPoints: Int,
    public var fVerbs: Int,
    public var fConics: Int,
  )
}
