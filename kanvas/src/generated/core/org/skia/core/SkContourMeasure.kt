package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkRefCnt
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SK_API SkContourMeasure : public SkRefCnt {
 * public:
 *     /** Return the length of the contour.
 *      */
 *     SkScalar length() const { return fLength; }
 *
 *     /** Pins distance to 0 <= distance <= length(), and then computes the corresponding
 *      *  position and tangent.
 *      */
 *     [[nodiscard]] bool getPosTan(SkScalar distance, SkPoint* position, SkVector* tangent) const;
 *
 *     enum MatrixFlags {
 *         kGetPosition_MatrixFlag     = 0x01,
 *         kGetTangent_MatrixFlag      = 0x02,
 *         kGetPosAndTan_MatrixFlag    = kGetPosition_MatrixFlag | kGetTangent_MatrixFlag
 *     };
 *
 *     /** Pins distance to 0 <= distance <= getLength(), and then computes
 *      the corresponding matrix (by calling getPosTan).
 *      Returns false if there is no path, or a zero-length path was specified, in which case
 *      matrix is unchanged.
 *      */
 *     [[nodiscard]] bool getMatrix(SkScalar distance, SkMatrix* matrix,
 *                                  MatrixFlags flags = kGetPosAndTan_MatrixFlag) const;
 *
 *     /** Given a start and stop distance, return in dst the intervening segment(s).
 *      If the segment is zero-length, return false, else return true.
 *      startD and stopD are pinned to legal values (0..getLength()). If startD > stopD
 *      then return false (and leave dst untouched).
 *      Begin the segment with a moveTo if startWithMoveTo is true
 *      */
 *     [[nodiscard]] bool getSegment(SkScalar startD, SkScalar stopD, SkPathBuilder* dst,
 *                                   bool startWithMoveTo) const;
 * #ifdef SK_SUPPORT_MUTABLE_PATHEFFECT
 *     [[nodiscard]] bool getSegment(SkScalar startD, SkScalar stopD, SkPath* dst,
 *                                   bool startWithMoveTo) const;
 * #endif
 *
 *     /** Return true if the contour is closed()
 *      */
 *     bool isClosed() const { return fIsClosed; }
 *
 *     /** Measurement data for individual verbs.
 *      */
 *     struct VerbMeasure {
 *         SkScalar              fDistance; // Cumulative distance along the current contour.
 *         SkPathVerb            fVerb;     // Verb type.
 *         SkSpan<const SkPoint> fPts;      // Verb points.
 *     };
 *
 * private:
 *     struct Segment;
 *
 * public:
 *     /** Utility for iterating over the current contour verbs:
 *      *
 *      *   for (const auto verb_measure : contour_measure) {
 *      *     ...
 *      *   }
 *      */
 *     class ForwardVerbIterator final {
 *     public:
 *         VerbMeasure operator*() const;
 *
 *         ForwardVerbIterator& operator++() {
 *             SkASSERT(!fSegments.empty());
 *
 *             fSegments = LastSegForCurrentVerb(fSegments.subspan(1));
 *
 *             return *this;
 *         }
 *
 *         bool operator==(const ForwardVerbIterator& other) const {
 *             SkASSERT(fSegments.data() != other.fSegments.data() ||
 *                      fSegments.size() == other.fSegments.size());
 *             return fSegments.data() == other.fSegments.data();
 *         }
 *
 *         bool operator!=(const ForwardVerbIterator& other) const {
 *             return !((*this) == other);
 *         }
 *
 *     private:
 *         friend class SkContourMeasure;
 *
 *         ForwardVerbIterator(SkSpan<const Segment> segs, SkSpan<const SkPoint> pts)
 *             : fSegments(LastSegForCurrentVerb(segs))
 *             , fPts(pts) {}
 *
 *         static SkSpan<const Segment> LastSegForCurrentVerb(const SkSpan<const Segment>& segs) {
 *             size_t i = 1;
 *             while (i < segs.size() && segs[0].fPtIndex == segs[i].fPtIndex) {
 *                 ++i;
 *             }
 *
 *             return segs.subspan(i - 1);
 *         }
 *
 *         // Remaining segments for forward iteration. The first segment in the span is
 *         // adjusted to always point to the last segment of the current verb, such that its distance
 *         // corresponds to the verb distance.
 *         SkSpan<const Segment> fSegments;
 *
 *         // All path points (indexed in segments).
 *         SkSpan<const SkPoint> fPts;
 *     };
 *
 *     ForwardVerbIterator begin() const {
 *         return ForwardVerbIterator(fSegments, fPts);
 *     }
 *     ForwardVerbIterator end() const {
 *         return ForwardVerbIterator(SkSpan(fSegments.end(), 0), fPts);
 *     }
 *
 * private:
 *     struct Segment {
 *         SkScalar    fDistance;  // total distance up to this point
 *         unsigned    fPtIndex; // index into the fPts array
 *         unsigned    fTValue : 30;
 *         unsigned    fType : 2;  // actually the enum SkSegType
 *         // See SkPathMeasurePriv.h
 *
 *         SkScalar getScalarT() const;
 *
 *         static const Segment* Next(const Segment* seg) {
 *             unsigned ptIndex = seg->fPtIndex;
 *             do {
 *                 ++seg;
 *             } while (seg->fPtIndex == ptIndex);
 *             return seg;
 *         }
 *
 *     };
 *
 *     const SkTDArray<Segment>  fSegments;
 *     const SkTDArray<SkPoint>  fPts; // Points used to define the segments
 *
 *     const SkScalar fLength;
 *     const bool fIsClosed;
 *
 *     SkContourMeasure(SkTDArray<Segment>&& segs, SkTDArray<SkPoint>&& pts,
 *                      SkScalar length, bool isClosed);
 *     ~SkContourMeasure() override {}
 *
 *     const Segment* distanceToSegment(SkScalar distance, SkScalar* t) const;
 *
 *     friend class SkContourMeasureIter;
 *     friend class SkPathMeasurePriv;
 * }
 * ```
 */
public open class SkContourMeasure public constructor(
  segs: SkTDArray<org.skia.modules.Segment>,
  pts: SkTDArray<SkPoint>,
  length: SkScalar,
  isClosed: Boolean,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const SkTDArray<Segment>  fSegments
   * ```
   */
  private val fSegments: Int = TODO("Initialize fSegments")

  /**
   * C++ original:
   * ```cpp
   * const SkTDArray<SkPoint>  fPts
   * ```
   */
  private val fPts: Int = TODO("Initialize fPts")

  /**
   * C++ original:
   * ```cpp
   * const SkScalar fLength
   * ```
   */
  private val fLength: Int = TODO("Initialize fLength")

  /**
   * C++ original:
   * ```cpp
   * const bool fIsClosed
   * ```
   */
  private val fIsClosed: Boolean = TODO("Initialize fIsClosed")

  /**
   * C++ original:
   * ```cpp
   * SkContourMeasure(SkTDArray<Segment>&& segs, SkTDArray<SkPoint>&& pts,
   *                      SkScalar length, bool isClosed)
   * ```
   */
  private var skTDArray: SkContourMeasure = TODO("Initialize skTDArray")

  /**
   * C++ original:
   * ```cpp
   * SkScalar length() const { return fLength; }
   * ```
   */
  public fun length(): Int {
    TODO("Implement length")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkContourMeasure::getPosTan(SkScalar distance, SkPoint* pos, SkVector* tangent) const {
   *     if (SkIsNaN(distance)) {
   *         return false;
   *     }
   *
   *     const SkScalar length = this->length();
   *     SkASSERT(length > 0 && !fSegments.empty());
   *
   *     // pin the distance to a legal range
   *     if (distance < 0) {
   *         distance = 0;
   *     } else if (distance > length) {
   *         distance = length;
   *     }
   *
   *     SkScalar        t;
   *     const Segment*  seg = this->distanceToSegment(distance, &t);
   *     if (SkIsNaN(t)) {
   *         return false;
   *     }
   *
   *     SkASSERT((unsigned)seg->fPtIndex < (unsigned)fPts.size());
   *     compute_pos_tan(&fPts[seg->fPtIndex], seg->fType, t, pos, tangent);
   *     return true;
   * }
   * ```
   */
  public fun getPosTan(
    distance: SkScalar,
    position: SkPoint?,
    tangent: SkVector?,
  ): Boolean {
    TODO("Implement getPosTan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkContourMeasure::getMatrix(SkScalar distance, SkMatrix* matrix, MatrixFlags flags) const {
   *     SkPoint     position;
   *     SkVector    tangent;
   *
   *     if (this->getPosTan(distance, &position, &tangent)) {
   *         if (matrix) {
   *             if (flags & kGetTangent_MatrixFlag) {
   *                 matrix->setSinCos(tangent.fY, tangent.fX, 0, 0);
   *             } else {
   *                 matrix->reset();
   *             }
   *             if (flags & kGetPosition_MatrixFlag) {
   *                 matrix->postTranslate(position.fX, position.fY);
   *             }
   *         }
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun getMatrix(
    distance: SkScalar,
    matrix: SkMatrix?,
    flags: MatrixFlags = TODO(),
  ): Boolean {
    TODO("Implement getMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkContourMeasure::getSegment(SkScalar startD, SkScalar stopD, SkPath* dst,
   *                                   bool startWithMoveTo) const {
   *     SkPathBuilder builder;
   *     if (this->getSegment(startD, stopD, &builder, startWithMoveTo)) {
   *         *dst = builder.detach();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun getSegment(
    startD: SkScalar,
    stopD: SkScalar,
    dst: SkPathBuilder?,
    startWithMoveTo: Boolean,
  ): Boolean {
    TODO("Implement getSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isClosed() const { return fIsClosed; }
   * ```
   */
  public fun isClosed(): Boolean {
    TODO("Implement isClosed")
  }

  /**
   * C++ original:
   * ```cpp
   * ForwardVerbIterator begin() const {
   *         return ForwardVerbIterator(fSegments, fPts);
   *     }
   * ```
   */
  private fun begin(): ForwardVerbIterator {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * ForwardVerbIterator end() const {
   *         return ForwardVerbIterator(SkSpan(fSegments.end(), 0), fPts);
   *     }
   * ```
   */
  private fun end(): ForwardVerbIterator {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkContourMeasure::Segment* SkContourMeasure::distanceToSegment( SkScalar distance,
   *                                                                      SkScalar* t) const {
   *     SkDEBUGCODE(SkScalar length = ) this->length();
   *     SkASSERT(distance >= 0 && distance <= length);
   *
   *     const Segment*  seg = fSegments.begin();
   *     int             count = fSegments.size();
   *
   *     int index = SkTKSearch<Segment, SkScalar>(seg, count, distance);
   *     // don't care if we hit an exact match or not, so we xor index if it is negative
   *     index ^= (index >> 31);
   *     seg = &seg[index];
   *
   *     // now interpolate t-values with the prev segment (if possible)
   *     SkScalar    startT = 0, startD = 0;
   *     // check if the prev segment is legal, and references the same set of points
   *     if (index > 0) {
   *         startD = seg[-1].fDistance;
   *         if (seg[-1].fPtIndex == seg->fPtIndex) {
   *             SkASSERT(seg[-1].fType == seg->fType);
   *             startT = seg[-1].getScalarT();
   *         }
   *     }
   *
   *     SkASSERT(seg->getScalarT() > startT);
   *     SkASSERT(distance >= startD);
   *     SkASSERT(seg->fDistance > startD);
   *
   *     *t = startT + (seg->getScalarT() - startT) * (distance - startD) / (seg->fDistance - startD);
   *     return seg;
   * }
   * ```
   */
  private fun distanceToSegment(distance: SkScalar, t: SkScalar?): Segment {
    TODO("Implement distanceToSegment")
  }

  public data class VerbMeasure public constructor(
    public var fDistance: Int,
    public var fVerb: Int,
    public var fPts: Int,
  )

  public data class ForwardVerbIterator public constructor(
    private var skSpan: undefined.ForwardVerbIterator,
  ) {
    public operator fun inc(): undefined.ForwardVerbIterator {
      TODO("Implement inc")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    private fun fPts(param0: Int): undefined.ForwardVerbIterator {
      TODO("Implement fPts")
    }
  }

  public data class Segment public constructor(
    public var fDistance: Int,
    public var fPtIndex: UInt,
    public var fTValue: UInt,
    public var fType: UInt,
  ) {
    public fun getScalarT(): Int {
      TODO("Implement getScalarT")
    }

    public companion object {
      public fun next(seg: org.skia.modules.Segment?): org.skia.modules.Segment {
        TODO("Implement next")
      }
    }
  }

  public enum class MatrixFlags {
    kGetPosition_MatrixFlag,
    kGetTangent_MatrixFlag,
    kGetPosAndTan_MatrixFlag,
  }
}
