package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class InternalLineMetrics {
 * public:
 *
 *     InternalLineMetrics() {
 *         clean();
 *         fForceStrut = false;
 *     }
 *
 *     explicit InternalLineMetrics(bool forceStrut) {
 *         clean();
 *         fForceStrut = forceStrut;
 *     }
 *
 *     InternalLineMetrics(SkScalar a, SkScalar d, SkScalar l) {
 *         fAscent = a;
 *         fDescent = d;
 *         fLeading = l;
 *         fRawAscent = a;
 *         fRawDescent = d;
 *         fRawLeading = l;
 *         fForceStrut = false;
 *     }
 *
 *     InternalLineMetrics(SkScalar a, SkScalar d, SkScalar l, SkScalar ra, SkScalar rd, SkScalar rl) {
 *         fAscent = a;
 *         fDescent = d;
 *         fLeading = l;
 *         fRawAscent = ra;
 *         fRawDescent = rd;
 *         fRawLeading = rl;
 *         fForceStrut = false;
 *     }
 *
 *     InternalLineMetrics(const SkFont& font, bool forceStrut) {
 *         SkFontMetrics metrics;
 *         font.getMetrics(&metrics);
 *         fAscent = metrics.fAscent;
 *         fDescent = metrics.fDescent;
 *         fLeading = metrics.fLeading;
 *         fRawAscent = metrics.fAscent;
 *         fRawDescent = metrics.fDescent;
 *         fRawLeading = metrics.fLeading;
 *         fForceStrut = forceStrut;
 *     }
 *
 *     void add(Run* run) {
 *         if (fForceStrut) {
 *             return;
 *         }
 *         fAscent = std::min(fAscent, run->correctAscent());
 *         fDescent = std::max(fDescent, run->correctDescent());
 *         fLeading = std::max(fLeading, run->correctLeading());
 *
 *         fRawAscent = std::min(fRawAscent, run->ascent());
 *         fRawDescent = std::max(fRawDescent, run->descent());
 *         fRawLeading = std::max(fRawLeading, run->leading());
 *     }
 *
 *     void add(InternalLineMetrics other) {
 *         fAscent = std::min(fAscent, other.fAscent);
 *         fDescent = std::max(fDescent, other.fDescent);
 *         fLeading = std::max(fLeading, other.fLeading);
 *         fRawAscent = std::min(fRawAscent, other.fRawAscent);
 *         fRawDescent = std::max(fRawDescent, other.fRawDescent);
 *         fRawLeading = std::max(fRawLeading, other.fRawLeading);
 *     }
 *
 *     void clean() {
 *         fAscent = SK_ScalarMax;
 *         fDescent = SK_ScalarMin;
 *         fLeading = 0;
 *         fRawAscent = SK_ScalarMax;
 *         fRawDescent = SK_ScalarMin;
 *         fRawLeading = 0;
 *     }
 *
 *     bool isClean() {
 *         return (fAscent == SK_ScalarMax &&
 *                 fDescent == SK_ScalarMin &&
 *                 fLeading == 0 &&
 *                 fRawAscent == SK_ScalarMax &&
 *                 fRawDescent == SK_ScalarMin &&
 *                 fRawLeading == 0);
 *     }
 *
 *     SkScalar delta() const { return height() - ideographicBaseline(); }
 *
 *     void updateLineMetrics(InternalLineMetrics& metrics) {
 *         if (metrics.fForceStrut) {
 *             metrics.fAscent = fAscent;
 *             metrics.fDescent = fDescent;
 *             metrics.fLeading = fLeading;
 *             metrics.fRawAscent = fRawAscent;
 *             metrics.fRawDescent = fRawDescent;
 *             metrics.fRawLeading = fRawLeading;
 *         } else {
 *             // This is another of those flutter changes. To be removed...
 *             metrics.fAscent = std::min(metrics.fAscent, fAscent - fLeading / 2.0f);
 *             metrics.fDescent = std::max(metrics.fDescent, fDescent + fLeading / 2.0f);
 *             metrics.fRawAscent = std::min(metrics.fRawAscent, fRawAscent - fRawLeading / 2.0f);
 *             metrics.fRawDescent = std::max(metrics.fRawDescent, fRawDescent + fRawLeading / 2.0f);
 *         }
 *     }
 *
 *     SkScalar runTop(const Run* run, LineMetricStyle ascentStyle) const {
 *         return fLeading / 2 - fAscent +
 *           (ascentStyle == LineMetricStyle::Typographic ? run->ascent() : run->correctAscent()) + delta();
 *     }
 *
 *     SkScalar height() const {
 *         return ::round((double)fDescent - fAscent + fLeading);
 *     }
 *
 *     void update(SkScalar a, SkScalar d, SkScalar l) {
 *         fAscent = a;
 *         fDescent = d;
 *         fLeading = l;
 *     }
 *
 *     void updateRawData(SkScalar ra, SkScalar rd) {
 *         fRawAscent = ra;
 *         fRawDescent = rd;
 *     }
 *
 *     SkScalar alphabeticBaseline() const { return fLeading / 2 - fAscent; }
 *     SkScalar ideographicBaseline() const { return fDescent - fAscent + fLeading; }
 *     SkScalar deltaBaselines() const { return fLeading / 2 + fDescent; }
 *     SkScalar baseline() const { return fLeading / 2 - fAscent; }
 *     SkScalar ascent() const { return fAscent; }
 *     SkScalar descent() const { return fDescent; }
 *     SkScalar leading() const { return fLeading; }
 *     SkScalar rawAscent() const { return fRawAscent; }
 *     SkScalar rawDescent() const { return fRawDescent; }
 *     void setForceStrut(bool value) { fForceStrut = value; }
 *     bool getForceStrut() const { return fForceStrut; }
 *
 * private:
 *
 *     friend class ParagraphImpl;
 *     friend class TextWrapper;
 *     friend class TextLine;
 *
 *     SkScalar fAscent;
 *     SkScalar fDescent;
 *     SkScalar fLeading;
 *
 *     SkScalar fRawAscent;
 *     SkScalar fRawDescent;
 *     SkScalar fRawLeading;
 *
 *     bool fForceStrut;
 * }
 * ```
 */
public data class InternalLineMetrics public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fAscent
   * ```
   */
  private var fAscent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fDescent
   * ```
   */
  private var fDescent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fLeading
   * ```
   */
  private var fLeading: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRawAscent
   * ```
   */
  private var fRawAscent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRawDescent
   * ```
   */
  private var fRawDescent: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRawLeading
   * ```
   */
  private var fRawLeading: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fForceStrut
   * ```
   */
  private var fForceStrut: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * void add(Run* run) {
   *         if (fForceStrut) {
   *             return;
   *         }
   *         fAscent = std::min(fAscent, run->correctAscent());
   *         fDescent = std::max(fDescent, run->correctDescent());
   *         fLeading = std::max(fLeading, run->correctLeading());
   *
   *         fRawAscent = std::min(fRawAscent, run->ascent());
   *         fRawDescent = std::max(fRawDescent, run->descent());
   *         fRawLeading = std::max(fRawLeading, run->leading());
   *     }
   * ```
   */
  public fun add(run: Run?) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void add(InternalLineMetrics other) {
   *         fAscent = std::min(fAscent, other.fAscent);
   *         fDescent = std::max(fDescent, other.fDescent);
   *         fLeading = std::max(fLeading, other.fLeading);
   *         fRawAscent = std::min(fRawAscent, other.fRawAscent);
   *         fRawDescent = std::max(fRawDescent, other.fRawDescent);
   *         fRawLeading = std::max(fRawLeading, other.fRawLeading);
   *     }
   * ```
   */
  public fun add(other: InternalLineMetrics) {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * void clean() {
   *         fAscent = SK_ScalarMax;
   *         fDescent = SK_ScalarMin;
   *         fLeading = 0;
   *         fRawAscent = SK_ScalarMax;
   *         fRawDescent = SK_ScalarMin;
   *         fRawLeading = 0;
   *     }
   * ```
   */
  public fun clean() {
    TODO("Implement clean")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClean() {
   *         return (fAscent == SK_ScalarMax &&
   *                 fDescent == SK_ScalarMin &&
   *                 fLeading == 0 &&
   *                 fRawAscent == SK_ScalarMax &&
   *                 fRawDescent == SK_ScalarMin &&
   *                 fRawLeading == 0);
   *     }
   * ```
   */
  public fun isClean(): Boolean {
    TODO("Implement isClean")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar delta() const { return height() - ideographicBaseline(); }
   * ```
   */
  public fun delta(): Int {
    TODO("Implement delta")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateLineMetrics(InternalLineMetrics& metrics) {
   *         if (metrics.fForceStrut) {
   *             metrics.fAscent = fAscent;
   *             metrics.fDescent = fDescent;
   *             metrics.fLeading = fLeading;
   *             metrics.fRawAscent = fRawAscent;
   *             metrics.fRawDescent = fRawDescent;
   *             metrics.fRawLeading = fRawLeading;
   *         } else {
   *             // This is another of those flutter changes. To be removed...
   *             metrics.fAscent = std::min(metrics.fAscent, fAscent - fLeading / 2.0f);
   *             metrics.fDescent = std::max(metrics.fDescent, fDescent + fLeading / 2.0f);
   *             metrics.fRawAscent = std::min(metrics.fRawAscent, fRawAscent - fRawLeading / 2.0f);
   *             metrics.fRawDescent = std::max(metrics.fRawDescent, fRawDescent + fRawLeading / 2.0f);
   *         }
   *     }
   * ```
   */
  public fun updateLineMetrics(metrics: InternalLineMetrics) {
    TODO("Implement updateLineMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar runTop(const Run* run, LineMetricStyle ascentStyle) const {
   *         return fLeading / 2 - fAscent +
   *           (ascentStyle == LineMetricStyle::Typographic ? run->ascent() : run->correctAscent()) + delta();
   *     }
   * ```
   */
  public fun runTop(run: Run?, ascentStyle: LineMetricStyle): Int {
    TODO("Implement runTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() const {
   *         return ::round((double)fDescent - fAscent + fLeading);
   *     }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * void update(SkScalar a, SkScalar d, SkScalar l) {
   *         fAscent = a;
   *         fDescent = d;
   *         fLeading = l;
   *     }
   * ```
   */
  public fun update(
    a: SkScalar,
    d: SkScalar,
    l: SkScalar,
  ) {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateRawData(SkScalar ra, SkScalar rd) {
   *         fRawAscent = ra;
   *         fRawDescent = rd;
   *     }
   * ```
   */
  public fun updateRawData(ra: SkScalar, rd: SkScalar) {
    TODO("Implement updateRawData")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar alphabeticBaseline() const { return fLeading / 2 - fAscent; }
   * ```
   */
  public fun alphabeticBaseline(): Int {
    TODO("Implement alphabeticBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar ideographicBaseline() const { return fDescent - fAscent + fLeading; }
   * ```
   */
  public fun ideographicBaseline(): Int {
    TODO("Implement ideographicBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar deltaBaselines() const { return fLeading / 2 + fDescent; }
   * ```
   */
  public fun deltaBaselines(): Int {
    TODO("Implement deltaBaselines")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar baseline() const { return fLeading / 2 - fAscent; }
   * ```
   */
  public fun baseline(): Int {
    TODO("Implement baseline")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar ascent() const { return fAscent; }
   * ```
   */
  public fun ascent(): Int {
    TODO("Implement ascent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar descent() const { return fDescent; }
   * ```
   */
  public fun descent(): Int {
    TODO("Implement descent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar leading() const { return fLeading; }
   * ```
   */
  public fun leading(): Int {
    TODO("Implement leading")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar rawAscent() const { return fRawAscent; }
   * ```
   */
  public fun rawAscent(): Int {
    TODO("Implement rawAscent")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar rawDescent() const { return fRawDescent; }
   * ```
   */
  public fun rawDescent(): Int {
    TODO("Implement rawDescent")
  }

  /**
   * C++ original:
   * ```cpp
   * void setForceStrut(bool value) { fForceStrut = value; }
   * ```
   */
  public fun setForceStrut(`value`: Boolean) {
    TODO("Implement setForceStrut")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getForceStrut() const { return fForceStrut; }
   * ```
   */
  public fun getForceStrut(): Boolean {
    TODO("Implement getForceStrut")
  }
}
