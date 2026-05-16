package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * template <typename T> class SkTDArray;
 *
 * class SkOpSegment {
 * public:
 *     bool operator<(const SkOpSegment& rh) const {
 *         return fBounds.fTop < rh.fBounds.fTop;
 *     }
 *
 *     SkOpAngle* activeAngle(SkOpSpanBase* start, SkOpSpanBase** startPtr, SkOpSpanBase** endPtr,
 *                             bool* done);
 *     SkOpAngle* activeAngleInner(SkOpSpanBase* start, SkOpSpanBase** startPtr,
 *                                        SkOpSpanBase** endPtr, bool* done);
 *     SkOpAngle* activeAngleOther(SkOpSpanBase* start, SkOpSpanBase** startPtr,
 *                                        SkOpSpanBase** endPtr, bool* done);
 *     bool activeOp(SkOpSpanBase* start, SkOpSpanBase* end, int xorMiMask, int xorSuMask,
 *                   SkPathOp op);
 *     bool activeOp(int xorMiMask, int xorSuMask, SkOpSpanBase* start, SkOpSpanBase* end, SkPathOp op,
 *                   int* sumMiWinding, int* sumSuWinding);
 *
 *     bool activeWinding(SkOpSpanBase* start, SkOpSpanBase* end);
 *     bool activeWinding(SkOpSpanBase* start, SkOpSpanBase* end, int* sumWinding);
 *
 *     SkOpSegment* addConic(SkPoint pts[3], SkScalar weight, SkOpContour* parent) {
 *         init(pts, weight, parent, SkPath::kConic_Verb);
 *         SkDCurve curve;
 *         curve.fConic.set(pts, weight);
 *         curve.setConicBounds(pts, weight, 0, 1, &fBounds);
 *         return this;
 *     }
 *
 *     SkOpSegment* addCubic(SkPoint pts[4], SkOpContour* parent) {
 *         init(pts, 1, parent, SkPath::kCubic_Verb);
 *         SkDCurve curve;
 *         curve.fCubic.set(pts);
 *         curve.setCubicBounds(pts, 1, 0, 1, &fBounds);
 *         return this;
 *     }
 *
 *     bool addCurveTo(const SkOpSpanBase* start, const SkOpSpanBase* end, SkPathWriter* path) const;
 *
 *     SkOpAngle* addEndSpan() {
 *         SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
 *         angle->set(&fTail, fTail.prev());
 *         fTail.setFromAngle(angle);
 *         return angle;
 *     }
 *
 *     bool addExpanded(double newT, const SkOpSpanBase* test, bool* startOver);
 *
 *     SkOpSegment* addLine(SkPoint pts[2], SkOpContour* parent) {
 *         SkASSERT(pts[0] != pts[1]);
 *         init(pts, 1, parent, SkPath::kLine_Verb);
 *         fBounds.setBounds({pts, 2});
 *         return this;
 *     }
 *
 *     SkOpPtT* addMissing(double t, SkOpSegment* opp, bool* allExist);
 *
 *     SkOpAngle* addStartSpan() {
 *         SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
 *         angle->set(&fHead, fHead.next());
 *         fHead.setToAngle(angle);
 *         return angle;
 *     }
 *
 *     SkOpSegment* addQuad(SkPoint pts[3], SkOpContour* parent) {
 *         init(pts, 1, parent, SkPath::kQuad_Verb);
 *         SkDCurve curve;
 *         curve.fQuad.set(pts);
 *         curve.setQuadBounds(pts, 1, 0, 1, &fBounds);
 *         return this;
 *     }
 *
 *     SkOpPtT* addT(double t);
 *     SkOpPtT* addT(double t, const SkPoint& pt);
 *
 *     const SkPathOpsBounds& bounds() const {
 *         return fBounds;
 *     }
 *
 *     void bumpCount() {
 *         ++fCount;
 *     }
 *
 *     void calcAngles();
 *     SkOpSpanBase::Collapsed collapsed(double startT, double endT) const;
 *     static bool ComputeOneSum(const SkOpAngle* baseAngle, SkOpAngle* nextAngle,
 *                               SkOpAngle::IncludeType );
 *     static bool ComputeOneSumReverse(SkOpAngle* baseAngle, SkOpAngle* nextAngle,
 *                                      SkOpAngle::IncludeType );
 *     int computeSum(SkOpSpanBase* start, SkOpSpanBase* end, SkOpAngle::IncludeType includeType);
 *
 *     void clearAll();
 *     void clearOne(SkOpSpan* span);
 *     static void ClearVisited(SkOpSpanBase* span);
 *     bool contains(double t) const;
 *
 *     SkOpContour* contour() const {
 *         return fContour;
 *     }
 *
 *     int count() const {
 *         return fCount;
 *     }
 *
 *     void debugAddAngle(double startT, double endT);
 * #if DEBUG_COIN
 *     const SkOpPtT* debugAddT(double t, SkPathOpsDebug::GlitchLog* ) const;
 * #endif
 *     const SkOpAngle* debugAngle(int id) const;
 * #if DEBUG_ANGLE
 *     void debugCheckAngleCoin() const;
 * #endif
 * #if DEBUG_COIN
 *     void debugCheckHealth(SkPathOpsDebug::GlitchLog* ) const;
 *     void debugClearAll(SkPathOpsDebug::GlitchLog* glitches) const;
 *     void debugClearOne(const SkOpSpan* span, SkPathOpsDebug::GlitchLog* glitches) const;
 * #endif
 *     const SkOpCoincidence* debugCoincidence() const;
 *     SkOpContour* debugContour(int id) const;
 *
 *     int debugID() const {
 *         return SkDEBUGRELEASE(fID, -1);
 *     }
 *
 *     SkOpAngle* debugLastAngle();
 * #if DEBUG_COIN
 *     void debugMissingCoincidence(SkPathOpsDebug::GlitchLog* glitches) const;
 *     void debugMoveMultiples(SkPathOpsDebug::GlitchLog* glitches) const;
 *     void debugMoveNearby(SkPathOpsDebug::GlitchLog* glitches) const;
 * #endif
 *     const SkOpPtT* debugPtT(int id) const;
 *     void debugReset();
 *     const SkOpSegment* debugSegment(int id) const;
 *
 * #if DEBUG_ACTIVE_SPANS
 *     void debugShowActiveSpans(SkString* str) const;
 * #endif
 * #if DEBUG_MARK_DONE
 *     void debugShowNewWinding(const char* fun, const SkOpSpan* span, int winding);
 *     void debugShowNewWinding(const char* fun, const SkOpSpan* span, int winding, int oppWinding);
 * #endif
 *
 *     const SkOpSpanBase* debugSpan(int id) const;
 *     void debugValidate() const;
 *
 * #if DEBUG_COINCIDENCE_ORDER
 *     void debugResetCoinT() const;
 *     void debugSetCoinT(int, SkScalar ) const;
 * #endif
 *
 * #if DEBUG_COIN
 *     static void DebugClearVisited(const SkOpSpanBase* span);
 *
 *     bool debugVisited() const {
 *         if (!fDebugVisited) {
 *             fDebugVisited = true;
 *             return false;
 *         }
 *         return true;
 *     }
 * #endif
 *
 * #if DEBUG_ANGLE
 *     double distSq(double t, const SkOpAngle* opp) const;
 * #endif
 *
 *     bool done() const {
 *         SkOPASSERT(fDoneCount <= fCount);
 *         return fDoneCount == fCount;
 *     }
 *
 *     bool done(const SkOpAngle* angle) const {
 *         return angle->start()->starter(angle->end())->done();
 *     }
 *
 *     SkDPoint dPtAtT(double mid) const {
 *         return (*CurveDPointAtT[fVerb])(fPts, fWeight, mid);
 *     }
 *
 *     SkDVector dSlopeAtT(double mid) const {
 *         return (*CurveDSlopeAtT[fVerb])(fPts, fWeight, mid);
 *     }
 *
 *     void dump() const;
 *     void dumpAll() const;
 *     void dumpAngles() const;
 *     void dumpCoin() const;
 *     void dumpPts(const char* prefix = "seg") const;
 *     void dumpPtsInner(const char* prefix = "seg") const;
 *
 *     const SkOpPtT* existing(double t, const SkOpSegment* opp) const;
 *     SkOpSegment* findNextOp(SkTDArray<SkOpSpanBase*>* chase, SkOpSpanBase** nextStart,
 *                              SkOpSpanBase** nextEnd, bool* unsortable, bool* simple,
 *                              SkPathOp op, int xorMiMask, int xorSuMask);
 *     SkOpSegment* findNextWinding(SkTDArray<SkOpSpanBase*>* chase, SkOpSpanBase** nextStart,
 *                                   SkOpSpanBase** nextEnd, bool* unsortable);
 *     SkOpSegment* findNextXor(SkOpSpanBase** nextStart, SkOpSpanBase** nextEnd, bool* unsortable);
 *     SkOpSpan* findSortableTop(SkOpContour* );
 *     SkOpGlobalState* globalState() const;
 *
 *     const SkOpSpan* head() const {
 *         return &fHead;
 *     }
 *
 *     SkOpSpan* head() {
 *         return &fHead;
 *     }
 *
 *     void init(SkPoint pts[], SkScalar weight, SkOpContour* parent, SkPath::Verb verb);
 *
 *     SkOpSpan* insert(SkOpSpan* prev) {
 *         SkOpGlobalState* globalState = this->globalState();
 *         globalState->setAllocatedOpSpan();
 *         SkOpSpan* result = globalState->allocator()->make<SkOpSpan>();
 *         SkOpSpanBase* next = prev->next();
 *         result->setPrev(prev);
 *         prev->setNext(result);
 *         SkDEBUGCODE(result->ptT()->fT = 0);
 *         result->setNext(next);
 *         if (next) {
 *             next->setPrev(result);
 *         }
 *         return result;
 *     }
 *
 *     bool isClose(double t, const SkOpSegment* opp) const;
 *
 *     bool isHorizontal() const {
 *         return fBounds.fTop == fBounds.fBottom;
 *     }
 *
 *     SkOpSegment* isSimple(SkOpSpanBase** end, int* step) const {
 *         return nextChase(end, step, nullptr, nullptr);
 *     }
 *
 *     bool isVertical() const {
 *         return fBounds.fLeft == fBounds.fRight;
 *     }
 *
 *     bool isVertical(SkOpSpanBase* start, SkOpSpanBase* end) const {
 *         return (*CurveIsVertical[fVerb])(fPts, fWeight, start->t(), end->t());
 *     }
 *
 *     bool isXor() const;
 *
 *     void joinEnds(SkOpSegment* start) {
 *         fTail.ptT()->addOpp(start->fHead.ptT(), start->fHead.ptT());
 *     }
 *
 *     const SkPoint& lastPt() const {
 *         return fPts[SkPathOpsVerbToPoints(fVerb)];
 *     }
 *
 *     void markAllDone();
 *     bool markAndChaseDone(SkOpSpanBase* start, SkOpSpanBase* end, SkOpSpanBase** found);
 *     bool markAndChaseWinding(SkOpSpanBase* start, SkOpSpanBase* end, int winding,
 *             SkOpSpanBase** lastPtr);
 *     bool markAndChaseWinding(SkOpSpanBase* start, SkOpSpanBase* end, int winding,
 *             int oppWinding, SkOpSpanBase** lastPtr);
 *     bool markAngle(int maxWinding, int sumWinding, const SkOpAngle* angle, SkOpSpanBase** result);
 *     bool markAngle(int maxWinding, int sumWinding, int oppMaxWinding, int oppSumWinding,
 *                          const SkOpAngle* angle, SkOpSpanBase** result);
 *     void markDone(SkOpSpan* );
 *     bool markWinding(SkOpSpan* , int winding);
 *     bool markWinding(SkOpSpan* , int winding, int oppWinding);
 *     bool match(const SkOpPtT* span, const SkOpSegment* parent, double t, const SkPoint& pt) const;
 *     bool missingCoincidence();
 *     bool moveMultiples();
 *     bool moveNearby();
 *
 *     SkOpSegment* next() const {
 *         return fNext;
 *     }
 *
 *     SkOpSegment* nextChase(SkOpSpanBase** , int* step, SkOpSpan** , SkOpSpanBase** last) const;
 *     bool operand() const;
 *
 *     static int OppSign(const SkOpSpanBase* start, const SkOpSpanBase* end) {
 *         int result = start->t() < end->t() ? -start->upCast()->oppValue()
 *                 : end->upCast()->oppValue();
 *         return result;
 *     }
 *
 *     bool oppXor() const;
 *
 *     const SkOpSegment* prev() const {
 *         return fPrev;
 *     }
 *
 *     SkPoint ptAtT(double mid) const {
 *         return (*CurvePointAtT[fVerb])(fPts, fWeight, mid);
 *     }
 *
 *     const SkPoint* pts() const {
 *         return fPts;
 *     }
 *
 *     bool ptsDisjoint(const SkOpPtT& span, const SkOpPtT& test) const {
 *         SkASSERT(this == span.segment());
 *         SkASSERT(this == test.segment());
 *         return ptsDisjoint(span.fT, span.fPt, test.fT, test.fPt);
 *     }
 *
 *     bool ptsDisjoint(const SkOpPtT& span, double t, const SkPoint& pt) const {
 *         SkASSERT(this == span.segment());
 *         return ptsDisjoint(span.fT, span.fPt, t, pt);
 *     }
 *
 *     bool ptsDisjoint(double t1, const SkPoint& pt1, double t2, const SkPoint& pt2) const;
 *
 *     void rayCheck(const SkOpRayHit& base, SkOpRayDir dir, SkOpRayHit** hits, SkArenaAlloc*);
 *     void release(const SkOpSpan* );
 *
 * #if DEBUG_COIN
 *     void resetDebugVisited() const {
 *         fDebugVisited = false;
 *     }
 * #endif
 *
 *     void resetVisited() {
 *         fVisited = false;
 *     }
 *
 *     void setContour(SkOpContour* contour) {
 *         fContour = contour;
 *     }
 *
 *     void setNext(SkOpSegment* next) {
 *         fNext = next;
 *     }
 *
 *     void setPrev(SkOpSegment* prev) {
 *         fPrev = prev;
 *     }
 *
 *     void setUpWinding(SkOpSpanBase* start, SkOpSpanBase* end, int* maxWinding, int* sumWinding) {
 *         int deltaSum = SpanSign(start, end);
 *         *maxWinding = *sumWinding;
 *         if (*sumWinding == SK_MinS32) {
 *           return;
 *         }
 *         *sumWinding -= deltaSum;
 *     }
 *
 *     void setUpWindings(SkOpSpanBase* start, SkOpSpanBase* end, int* sumMiWinding,
 *                        int* maxWinding, int* sumWinding);
 *     void setUpWindings(SkOpSpanBase* start, SkOpSpanBase* end, int* sumMiWinding, int* sumSuWinding,
 *                        int* maxWinding, int* sumWinding, int* oppMaxWinding, int* oppSumWinding);
 *     bool sortAngles();
 *     bool spansNearby(const SkOpSpanBase* ref, const SkOpSpanBase* check, bool* found) const;
 *
 *     static int SpanSign(const SkOpSpanBase* start, const SkOpSpanBase* end) {
 *         int result = start->t() < end->t() ? -start->upCast()->windValue()
 *                 : end->upCast()->windValue();
 *         return result;
 *     }
 *
 *     SkOpAngle* spanToAngle(SkOpSpanBase* start, SkOpSpanBase* end) {
 *         SkASSERT(start != end);
 *         return start->t() < end->t() ? start->upCast()->toAngle() : start->fromAngle();
 *     }
 *
 *     bool subDivide(const SkOpSpanBase* start, const SkOpSpanBase* end, SkDCurve* result) const;
 *
 *     const SkOpSpanBase* tail() const {
 *         return &fTail;
 *     }
 *
 *     SkOpSpanBase* tail() {
 *         return &fTail;
 *     }
 *
 *     bool testForCoincidence(const SkOpPtT* priorPtT, const SkOpPtT* ptT, const SkOpSpanBase* prior,
 *             const SkOpSpanBase* spanBase, const SkOpSegment* opp) const;
 *
 *     SkOpSpan* undoneSpan();
 *     int updateOppWinding(const SkOpSpanBase* start, const SkOpSpanBase* end) const;
 *     int updateOppWinding(const SkOpAngle* angle) const;
 *     int updateOppWindingReverse(const SkOpAngle* angle) const;
 *     int updateWinding(SkOpSpanBase* start, SkOpSpanBase* end);
 *     int updateWinding(SkOpAngle* angle);
 *     int updateWindingReverse(const SkOpAngle* angle);
 *
 *     static bool UseInnerWinding(int outerWinding, int innerWinding);
 *
 *     SkPath::Verb verb() const {
 *         return fVerb;
 *     }
 *
 *     // look for two different spans that point to the same opposite segment
 *     bool visited() {
 *         if (!fVisited) {
 *             fVisited = true;
 *             return false;
 *         }
 *         return true;
 *     }
 *
 *     SkScalar weight() const {
 *         return fWeight;
 *     }
 *
 *     SkOpSpan* windingSpanAtT(double tHit);
 *     int windSum(const SkOpAngle* angle) const;
 *
 * private:
 *     SkOpSpan fHead;  // the head span always has its t set to zero
 *     SkOpSpanBase fTail;  // the tail span always has its t set to one
 *     SkOpContour* fContour;
 *     SkOpSegment* fNext;  // forward-only linked list used by contour to walk the segments
 *     const SkOpSegment* fPrev;
 *     SkPoint* fPts;  // pointer into array of points owned by edge builder that may be tweaked
 *     SkPathOpsBounds fBounds;  // tight bounds
 *     SkScalar fWeight;
 *     int fCount;  // number of spans (one for a non-intersecting segment)
 *     int fDoneCount;  // number of processed spans (zero initially)
 *     SkPath::Verb fVerb;
 *     bool fVisited;  // used by missing coincidence check
 * #if DEBUG_COIN
 *     mutable bool fDebugVisited;  // used by debug missing coincidence check
 * #endif
 * #if DEBUG_COINCIDENCE_ORDER
 *     mutable int fDebugBaseIndex;
 *     mutable SkScalar fDebugBaseMin;  // if > 0, the 1st t value in this seg vis-a-vis the ref seg
 *     mutable SkScalar fDebugBaseMax;
 *     mutable int fDebugLastIndex;
 *     mutable SkScalar fDebugLastMin;  // if > 0, the last t -- next t val - base has same sign
 *     mutable SkScalar fDebugLastMax;
 * #endif
 *     SkDEBUGCODE(int fID;)
 * }
 * ```
 */
public data class SkOpSegment public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkOpSpan fHead
   * ```
   */
  private var fHead: SkOpSpan,
  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase fTail
   * ```
   */
  private var fTail: SkOpSpanBase,
  /**
   * C++ original:
   * ```cpp
   * SkOpContour* fContour
   * ```
   */
  private var fContour: SkOpContour?,
  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* fNext
   * ```
   */
  private var fNext: SkOpSegment?,
  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* fPrev
   * ```
   */
  private val fPrev: SkOpSegment?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint* fPts
   * ```
   */
  private var fPts: SkPoint?,
  /**
   * C++ original:
   * ```cpp
   * SkPathOpsBounds fBounds
   * ```
   */
  private var fBounds: SkPathOpsBounds,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWeight
   * ```
   */
  private var fWeight: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * int fCount
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fDoneCount
   * ```
   */
  private var fDoneCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPath::Verb fVerb
   * ```
   */
  private var fVerb: SkPathVerb,
  /**
   * C++ original:
   * ```cpp
   * bool fVisited
   * ```
   */
  private var fVisited: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator<(const SkOpSegment& rh) const {
   *         return fBounds.fTop < rh.fBounds.fTop;
   *     }
   * ```
   */
  public operator fun compareTo(rh: SkOpSegment): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* SkOpSegment::activeAngle(SkOpSpanBase* start, SkOpSpanBase** startPtr,
   *         SkOpSpanBase** endPtr, bool* done) {
   *     if (SkOpAngle* result = activeAngleInner(start, startPtr, endPtr, done)) {
   *         return result;
   *     }
   *     if (SkOpAngle* result = activeAngleOther(start, startPtr, endPtr, done)) {
   *         return result;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun activeAngle(
    start: SkOpSpanBase?,
    startPtr: Int?,
    endPtr: Int?,
    done: Boolean?,
  ): SkOpAngle {
    TODO("Implement activeAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* SkOpSegment::activeAngleInner(SkOpSpanBase* start, SkOpSpanBase** startPtr,
   *         SkOpSpanBase** endPtr, bool* done) {
   *     SkOpSpan* upSpan = start->upCastable();
   *     if (upSpan) {
   *         if (upSpan->windValue() || upSpan->oppValue()) {
   *             SkOpSpanBase* next = upSpan->next();
   *             if (!*endPtr) {
   *                 *startPtr = start;
   *                 *endPtr = next;
   *             }
   *             if (!upSpan->done()) {
   *                 if (upSpan->windSum() != SK_MinS32) {
   *                     return spanToAngle(start, next);
   *                 }
   *                 *done = false;
   *             }
   *         } else {
   *             SkASSERT(upSpan->done());
   *         }
   *     }
   *     SkOpSpan* downSpan = start->prev();
   *     // edge leading into junction
   *     if (downSpan) {
   *         if (downSpan->windValue() || downSpan->oppValue()) {
   *             if (!*endPtr) {
   *                 *startPtr = start;
   *                 *endPtr = downSpan;
   *             }
   *             if (!downSpan->done()) {
   *                 if (downSpan->windSum() != SK_MinS32) {
   *                     return spanToAngle(start, downSpan);
   *                 }
   *                 *done = false;
   *             }
   *         } else {
   *             SkASSERT(downSpan->done());
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun activeAngleInner(
    start: SkOpSpanBase?,
    startPtr: Int?,
    endPtr: Int?,
    done: Boolean?,
  ): SkOpAngle {
    TODO("Implement activeAngleInner")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* SkOpSegment::activeAngleOther(SkOpSpanBase* start, SkOpSpanBase** startPtr,
   *         SkOpSpanBase** endPtr, bool* done) {
   *     SkOpPtT* oPtT = start->ptT()->next();
   *     SkOpSegment* other = oPtT->segment();
   *     SkOpSpanBase* oSpan = oPtT->span();
   *     return other->activeAngleInner(oSpan, startPtr, endPtr, done);
   * }
   * ```
   */
  public fun activeAngleOther(
    start: SkOpSpanBase?,
    startPtr: Int?,
    endPtr: Int?,
    done: Boolean?,
  ): SkOpAngle {
    TODO("Implement activeAngleOther")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::activeOp(SkOpSpanBase* start, SkOpSpanBase* end, int xorMiMask, int xorSuMask,
   *         SkPathOp op) {
   *     int sumMiWinding = this->updateWinding(end, start);
   *     int sumSuWinding = this->updateOppWinding(end, start);
   * #if DEBUG_LIMIT_WIND_SUM
   *     SkASSERT(abs(sumMiWinding) <= DEBUG_LIMIT_WIND_SUM);
   *     SkASSERT(abs(sumSuWinding) <= DEBUG_LIMIT_WIND_SUM);
   * #endif
   *     if (this->operand()) {
   *         using std::swap;
   *         swap(sumMiWinding, sumSuWinding);
   *     }
   *     return this->activeOp(xorMiMask, xorSuMask, start, end, op, &sumMiWinding, &sumSuWinding);
   * }
   * ```
   */
  public fun activeOp(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    xorMiMask: Int,
    xorSuMask: Int,
    op: SkPathOp,
  ): Boolean {
    TODO("Implement activeOp")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::activeOp(int xorMiMask, int xorSuMask, SkOpSpanBase* start, SkOpSpanBase* end,
   *         SkPathOp op, int* sumMiWinding, int* sumSuWinding) {
   *     int maxWinding, sumWinding, oppMaxWinding, oppSumWinding;
   *     this->setUpWindings(start, end, sumMiWinding, sumSuWinding,
   *             &maxWinding, &sumWinding, &oppMaxWinding, &oppSumWinding);
   *     bool miFrom;
   *     bool miTo;
   *     bool suFrom;
   *     bool suTo;
   *     if (operand()) {
   *         miFrom = (oppMaxWinding & xorMiMask) != 0;
   *         miTo = (oppSumWinding & xorMiMask) != 0;
   *         suFrom = (maxWinding & xorSuMask) != 0;
   *         suTo = (sumWinding & xorSuMask) != 0;
   *     } else {
   *         miFrom = (maxWinding & xorMiMask) != 0;
   *         miTo = (sumWinding & xorMiMask) != 0;
   *         suFrom = (oppMaxWinding & xorSuMask) != 0;
   *         suTo = (oppSumWinding & xorSuMask) != 0;
   *     }
   *     bool result = kActiveEdge[op][miFrom][miTo][suFrom][suTo];
   * #if DEBUG_ACTIVE_OP
   *     SkDebugf("%s id=%d t=%1.9g tEnd=%1.9g op=%s miFrom=%d miTo=%d suFrom=%d suTo=%d result=%d\n",
   *             __FUNCTION__, debugID(), start->t(), end->t(),
   *             SkPathOpsDebug::kPathOpStr[op], miFrom, miTo, suFrom, suTo, result);
   * #endif
   *     return result;
   * }
   * ```
   */
  public fun activeOp(
    xorMiMask: Int,
    xorSuMask: Int,
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    op: SkPathOp,
    sumMiWinding: Int?,
    sumSuWinding: Int?,
  ): Boolean {
    TODO("Implement activeOp")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::activeWinding(SkOpSpanBase* start, SkOpSpanBase* end) {
   *     int sumWinding = updateWinding(end, start);
   *     return activeWinding(start, end, &sumWinding);
   * }
   * ```
   */
  public fun activeWinding(start: SkOpSpanBase?, end: SkOpSpanBase?): Boolean {
    TODO("Implement activeWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::activeWinding(SkOpSpanBase* start, SkOpSpanBase* end, int* sumWinding) {
   *     int maxWinding;
   *     setUpWinding(start, end, &maxWinding, sumWinding);
   *     bool from = maxWinding != 0;
   *     bool to = *sumWinding  != 0;
   *     bool result = kUnaryActiveEdge[from][to];
   *     return result;
   * }
   * ```
   */
  public fun activeWinding(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    sumWinding: Int?,
  ): Boolean {
    TODO("Implement activeWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* addConic(SkPoint pts[3], SkScalar weight, SkOpContour* parent) {
   *         init(pts, weight, parent, SkPath::kConic_Verb);
   *         SkDCurve curve;
   *         curve.fConic.set(pts, weight);
   *         curve.setConicBounds(pts, weight, 0, 1, &fBounds);
   *         return this;
   *     }
   * ```
   */
  public fun addConic(
    pts: Array<SkPoint>,
    weight: SkScalar,
    parent: SkOpContour?,
  ): SkOpSegment {
    TODO("Implement addConic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* addCubic(SkPoint pts[4], SkOpContour* parent) {
   *         init(pts, 1, parent, SkPath::kCubic_Verb);
   *         SkDCurve curve;
   *         curve.fCubic.set(pts);
   *         curve.setCubicBounds(pts, 1, 0, 1, &fBounds);
   *         return this;
   *     }
   * ```
   */
  public fun addCubic(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
    TODO("Implement addCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::addCurveTo(const SkOpSpanBase* start, const SkOpSpanBase* end,
   *         SkPathWriter* path) const {
   *     const SkOpSpan* spanStart = start->starter(end);
   *     FAIL_IF(spanStart->alreadyAdded());
   *     const_cast<SkOpSpan*>(spanStart)->markAdded();
   *     SkDCurveSweep curvePart;
   *     start->segment()->subDivide(start, end, &curvePart.fCurve);
   *     curvePart.setCurveHullSweep(fVerb);
   *     SkPath::Verb verb = curvePart.isCurve() ? fVerb : SkPath::kLine_Verb;
   *     path->deferredMove(start->ptT());
   *     switch (verb) {
   *         case SkPath::kLine_Verb:
   *             FAIL_IF(!path->deferredLine(end->ptT()));
   *             break;
   *         case SkPath::kQuad_Verb:
   *             path->quadTo(curvePart.fCurve.fQuad[1].asSkPoint(), end->ptT());
   *             break;
   *         case SkPath::kConic_Verb:
   *             path->conicTo(curvePart.fCurve.fConic[1].asSkPoint(), end->ptT(),
   *                     curvePart.fCurve.fConic.fWeight);
   *             break;
   *         case SkPath::kCubic_Verb:
   *             path->cubicTo(curvePart.fCurve.fCubic[1].asSkPoint(),
   *                     curvePart.fCurve.fCubic[2].asSkPoint(), end->ptT());
   *             break;
   *         default:
   *             SkASSERT(0);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun addCurveTo(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    path: SkPathWriter?,
  ): Boolean {
    TODO("Implement addCurveTo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* addEndSpan() {
   *         SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
   *         angle->set(&fTail, fTail.prev());
   *         fTail.setFromAngle(angle);
   *         return angle;
   *     }
   * ```
   */
  public fun addEndSpan(): SkOpAngle {
    TODO("Implement addEndSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::addExpanded(double newT, const SkOpSpanBase* test, bool* startOver) {
   *     if (this->contains(newT)) {
   *         return true;
   *     }
   *     this->globalState()->resetAllocatedOpSpan();
   *     FAIL_IF(!between(0, newT, 1));
   *     SkOpPtT* newPtT = this->addT(newT);
   *     *startOver |= this->globalState()->allocatedOpSpan();
   *     if (!newPtT) {
   *         return false;
   *     }
   *     newPtT->fPt = this->ptAtT(newT);
   *     SkOpPtT* oppPrev = test->ptT()->oppPrev(newPtT);
   *     if (oppPrev) {
   *         // const cast away to change linked list; pt/t values stays unchanged
   *         SkOpSpanBase* writableTest = const_cast<SkOpSpanBase*>(test);
   *         writableTest->mergeMatches(newPtT->span());
   *         writableTest->ptT()->addOpp(newPtT, oppPrev);
   *         writableTest->checkForCollapsedCoincidence();
   *     }
   *     return true;
   * }
   * ```
   */
  public fun addExpanded(
    newT: Double,
    test: SkOpSpanBase?,
    startOver: Boolean?,
  ): Boolean {
    TODO("Implement addExpanded")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* addLine(SkPoint pts[2], SkOpContour* parent) {
   *         SkASSERT(pts[0] != pts[1]);
   *         init(pts, 1, parent, SkPath::kLine_Verb);
   *         fBounds.setBounds({pts, 2});
   *         return this;
   *     }
   * ```
   */
  public fun addLine(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* addMissing(double t, SkOpSegment* opp, bool* allExist)
   * ```
   */
  public fun addMissing(
    t: Double,
    opp: SkOpSegment?,
    allExist: Boolean?,
  ): SkOpPtT {
    TODO("Implement addMissing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* addStartSpan() {
   *         SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
   *         angle->set(&fHead, fHead.next());
   *         fHead.setToAngle(angle);
   *         return angle;
   *     }
   * ```
   */
  public fun addStartSpan(): SkOpAngle {
    TODO("Implement addStartSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* addQuad(SkPoint pts[3], SkOpContour* parent) {
   *         init(pts, 1, parent, SkPath::kQuad_Verb);
   *         SkDCurve curve;
   *         curve.fQuad.set(pts);
   *         curve.setQuadBounds(pts, 1, 0, 1, &fBounds);
   *         return this;
   *     }
   * ```
   */
  public fun addQuad(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
    TODO("Implement addQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* SkOpSegment::addT(double t) {
   *     return addT(t, this->ptAtT(t));
   * }
   * ```
   */
  public fun addT(t: Double): SkOpPtT {
    TODO("Implement addT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* SkOpSegment::addT(double t, const SkPoint& pt) {
   *     debugValidate();
   *     SkOpSpanBase* spanBase = &fHead;
   *     do {
   *         SkOpPtT* result = spanBase->ptT();
   *         if (t == result->fT || (!zero_or_one(t) && this->match(result, this, t, pt))) {
   *             spanBase->bumpSpanAdds();
   *             return result;
   *         }
   *         if (t < result->fT) {
   *             SkOpSpan* prev = result->span()->prev();
   *             FAIL_WITH_NULL_IF(!prev);
   *             // marks in global state that new op span has been allocated
   *             SkOpSpan* span = this->insert(prev);
   *             span->init(this, prev, t, pt);
   *             this->debugValidate();
   * #if DEBUG_ADD_T
   *             SkDebugf("%s insert t=%1.9g segID=%d spanID=%d\n", __FUNCTION__, t,
   *                     span->segment()->debugID(), span->debugID());
   * #endif
   *             span->bumpSpanAdds();
   *             return span->ptT();
   *         }
   *         FAIL_WITH_NULL_IF(spanBase == &fTail);
   *     } while ((spanBase = spanBase->upCast()->next()));
   *     SkASSERT(0);
   *     return nullptr;  // we never get here, but need this to satisfy compiler
   * }
   * ```
   */
  public fun addT(t: Double, pt: SkPoint): SkOpPtT {
    TODO("Implement addT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPathOpsBounds& bounds() const {
   *         return fBounds;
   *     }
   * ```
   */
  public fun bounds(): SkPathOpsBounds {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void bumpCount() {
   *         ++fCount;
   *     }
   * ```
   */
  public fun bumpCount() {
    TODO("Implement bumpCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::calcAngles() {
   *     bool activePrior = !fHead.isCanceled();
   *     if (activePrior && !fHead.simple()) {
   *         addStartSpan();
   *     }
   *     SkOpSpan* prior = &fHead;
   *     SkOpSpanBase* spanBase = fHead.next();
   *     while (spanBase != &fTail) {
   *         if (activePrior) {
   *             SkOpAngle* priorAngle = this->globalState()->allocator()->make<SkOpAngle>();
   *             priorAngle->set(spanBase, prior);
   *             spanBase->setFromAngle(priorAngle);
   *         }
   *         SkOpSpan* span = spanBase->upCast();
   *         bool active = !span->isCanceled();
   *         SkOpSpanBase* next = span->next();
   *         if (active) {
   *             SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
   *             angle->set(span, next);
   *             span->setToAngle(angle);
   *         }
   *         activePrior = active;
   *         prior = span;
   *         spanBase = next;
   *     }
   *     if (activePrior && !fTail.simple()) {
   *         addEndSpan();
   *     }
   * }
   * ```
   */
  public fun calcAngles() {
    TODO("Implement calcAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase::Collapsed SkOpSegment::collapsed(double s, double e) const {
   *     const SkOpSpanBase* span = &fHead;
   *     do {
   *         SkOpSpanBase::Collapsed result = span->collapsed(s, e);
   *         if (SkOpSpanBase::Collapsed::kNo != result) {
   *             return result;
   *         }
   *     } while (span->upCastable() && (span = span->upCast()->next()));
   *     return SkOpSpanBase::Collapsed::kNo;
   * }
   * ```
   */
  public fun collapsed(startT: Double, endT: Double): SkOpSpanBase.Collapsed {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::computeSum(SkOpSpanBase* start, SkOpSpanBase* end,
   *         SkOpAngle::IncludeType includeType) {
   *     SkASSERT(includeType != SkOpAngle::kUnaryXor);
   *     SkOpAngle* firstAngle = this->spanToAngle(end, start);
   *     if (nullptr == firstAngle || nullptr == firstAngle->next()) {
   *         return SK_NaN32;
   *     }
   *     // if all angles have a computed winding,
   *     //  or if no adjacent angles are orderable,
   *     //  or if adjacent orderable angles have no computed winding,
   *     //  there's nothing to do
   *     // if two orderable angles are adjacent, and both are next to orderable angles,
   *     //  and one has winding computed, transfer to the other
   *     SkOpAngle* baseAngle = nullptr;
   *     bool tryReverse = false;
   *     // look for counterclockwise transfers
   *     SkOpAngle* angle = firstAngle->previous();
   *     SkOpAngle* next = angle->next();
   *     firstAngle = next;
   *     do {
   *         SkOpAngle* prior = angle;
   *         angle = next;
   *         next = angle->next();
   *         SkASSERT(prior->next() == angle);
   *         SkASSERT(angle->next() == next);
   *         if (prior->unorderable() || angle->unorderable() || next->unorderable()) {
   *             baseAngle = nullptr;
   *             continue;
   *         }
   *         int testWinding = angle->starter()->windSum();
   *         if (SK_MinS32 != testWinding) {
   *             baseAngle = angle;
   *             tryReverse = true;
   *             continue;
   *         }
   *         if (baseAngle) {
   *             ComputeOneSum(baseAngle, angle, includeType);
   *             baseAngle = SK_MinS32 != angle->starter()->windSum() ? angle : nullptr;
   *         }
   *     } while (next != firstAngle);
   *     if (baseAngle && SK_MinS32 == firstAngle->starter()->windSum()) {
   *         firstAngle = baseAngle;
   *         tryReverse = true;
   *     }
   *     if (tryReverse) {
   *         baseAngle = nullptr;
   *         SkOpAngle* prior = firstAngle;
   *         do {
   *             angle = prior;
   *             prior = angle->previous();
   *             SkASSERT(prior->next() == angle);
   *             next = angle->next();
   *             if (prior->unorderable() || angle->unorderable() || next->unorderable()) {
   *                 baseAngle = nullptr;
   *                 continue;
   *             }
   *             int testWinding = angle->starter()->windSum();
   *             if (SK_MinS32 != testWinding) {
   *                 baseAngle = angle;
   *                 continue;
   *             }
   *             if (baseAngle) {
   *                 ComputeOneSumReverse(baseAngle, angle, includeType);
   *                 baseAngle = SK_MinS32 != angle->starter()->windSum() ? angle : nullptr;
   *             }
   *         } while (prior != firstAngle);
   *     }
   *     return start->starter(end)->windSum();
   * }
   * ```
   */
  public fun computeSum(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    includeType: SkOpAngle.IncludeType,
  ): Int {
    TODO("Implement computeSum")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::clearAll() {
   *     SkOpSpan* span = &fHead;
   *     do {
   *         this->clearOne(span);
   *     } while ((span = span->next()->upCastable()));
   *     this->globalState()->coincidence()->release(this);
   * }
   * ```
   */
  public fun clearAll() {
    TODO("Implement clearAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::clearOne(SkOpSpan* span) {
   *     span->setWindValue(0);
   *     span->setOppValue(0);
   *     this->markDone(span);
   * }
   * ```
   */
  public fun clearOne(span: SkOpSpan?) {
    TODO("Implement clearOne")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::contains(double newT) const {
   *     const SkOpSpanBase* spanBase = &fHead;
   *     do {
   *         if (spanBase->ptT()->contains(this, newT)) {
   *             return true;
   *         }
   *         if (spanBase == &fTail) {
   *             break;
   *         }
   *         spanBase = spanBase->upCast()->next();
   *     } while (true);
   *     return false;
   * }
   * ```
   */
  public fun contains(t: Double): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* contour() const {
   *         return fContour;
   *     }
   * ```
   */
  public fun contour(): SkOpContour {
    TODO("Implement contour")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const {
   *         return fCount;
   *     }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::debugAddAngle(double startT, double endT) {
   *     SkOpPtT* startPtT = startT == 0 ? fHead.ptT() : startT == 1 ? fTail.ptT()
   *             : this->addT(startT);
   *     SkOpPtT* endPtT = endT == 0 ? fHead.ptT() : endT == 1 ? fTail.ptT()
   *             : this->addT(endT);
   *     SkOpAngle* angle = this->globalState()->allocator()->make<SkOpAngle>();
   *     SkOpSpanBase* startSpan = &fHead;
   *     while (startSpan->ptT() != startPtT) {
   *         startSpan = startSpan->upCast()->next();
   *     }
   *     SkOpSpanBase* endSpan = &fHead;
   *     while (endSpan->ptT() != endPtT) {
   *         endSpan = endSpan->upCast()->next();
   *     }
   *     angle->set(startSpan, endSpan);
   *     if (startT < endT) {
   *         startSpan->upCast()->setToAngle(angle);
   *         endSpan->setFromAngle(angle);
   *     } else {
   *         endSpan->upCast()->setToAngle(angle);
   *         startSpan->setFromAngle(angle);
   *     }
   * }
   * ```
   */
  public fun debugAddAngle(startT: Double, endT: Double) {
    TODO("Implement debugAddAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpAngle* SkOpSegment::debugAngle(int id) const {
   *     return this->contour()->debugAngle(id);
   * }
   * ```
   */
  public fun debugAngle(id: Int): SkOpAngle {
    TODO("Implement debugAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpCoincidence* SkOpSegment::debugCoincidence() const {
   *     return this->contour()->debugCoincidence();
   * }
   * ```
   */
  public fun debugCoincidence(): SkOpCoincidence {
    TODO("Implement debugCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpSegment::debugContour(int id) const {
   *     return this->contour()->debugContour(id);
   * }
   * ```
   */
  public fun debugContour(id: Int): SkOpContour {
    TODO("Implement debugContour")
  }

  /**
   * C++ original:
   * ```cpp
   * int debugID() const {
   *         return SkDEBUGRELEASE(fID, -1);
   *     }
   * ```
   */
  public fun debugID(): Int {
    TODO("Implement debugID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* SkOpSegment::debugLastAngle() {
   *     SkOpAngle* result = nullptr;
   *     SkOpSpan* span = this->head();
   *     do {
   *         if (span->toAngle()) {
   *             SkASSERT(!result);
   *             result = span->toAngle();
   *         }
   *     } while ((span = span->next()->upCastable()));
   *     SkASSERT(result);
   *     return result;
   * }
   * ```
   */
  public fun debugLastAngle(): SkOpAngle {
    TODO("Implement debugLastAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpSegment::debugPtT(int id) const {
   *     return this->contour()->debugPtT(id);
   * }
   * ```
   */
  public fun debugPtT(id: Int): SkOpPtT {
    TODO("Implement debugPtT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::debugReset() {
   *     this->init(this->fPts, this->fWeight, this->contour(), this->verb());
   * }
   * ```
   */
  public fun debugReset() {
    TODO("Implement debugReset")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* SkOpSegment::debugSegment(int id) const {
   *     return this->contour()->debugSegment(id);
   * }
   * ```
   */
  public fun debugSegment(id: Int): SkOpSegment {
    TODO("Implement debugSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* SkOpSegment::debugSpan(int id) const {
   *     return this->contour()->debugSpan(id);
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkOpSpanBase {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::debugValidate() const {
   * #if DEBUG_COINCIDENCE_ORDER
   *     {
   *         const SkOpSpanBase* span = &fHead;
   *         do {
   *             span->debugResetCoinT();
   *         } while (!span->final() && (span = span->upCast()->next()));
   *         span = &fHead;
   *         int index = 0;
   *         do {
   *             span->debugSetCoinT(index++);
   *         } while (!span->final() && (span = span->upCast()->next()));
   *     }
   * #endif
   * #if DEBUG_COINCIDENCE
   *     if (this->globalState()->debugCheckHealth()) {
   *         return;
   *     }
   * #endif
   * #if DEBUG_VALIDATE
   *     const SkOpSpanBase* span = &fHead;
   *     double lastT = -1;
   *     const SkOpSpanBase* prev = nullptr;
   *     int count = 0;
   *     int done = 0;
   *     do {
   *         if (!span->final()) {
   *             ++count;
   *             done += span->upCast()->done() ? 1 : 0;
   *         }
   *         SkASSERT(span->segment() == this);
   *         SkASSERT(!prev || prev->upCast()->next() == span);
   *         SkASSERT(!prev || prev == span->prev());
   *         prev = span;
   *         double t = span->ptT()->fT;
   *         SkASSERT(lastT < t);
   *         lastT = t;
   *         span->debugValidate();
   *     } while (!span->final() && (span = span->upCast()->next()));
   *     SkASSERT(count == fCount);
   *     SkASSERT(done == fDoneCount);
   *     SkASSERT(count >= fDoneCount);
   *     SkASSERT(span->final());
   *     span->debugValidate();
   * #endif
   * }
   * ```
   */
  public fun debugValidate() {
    TODO("Implement debugValidate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         SkOPASSERT(fDoneCount <= fCount);
   *         return fDoneCount == fCount;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done(const SkOpAngle* angle) const {
   *         return angle->start()->starter(angle->end())->done();
   *     }
   * ```
   */
  public fun done(angle: SkOpAngle?): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDPoint dPtAtT(double mid) const {
   *         return (*CurveDPointAtT[fVerb])(fPts, fWeight, mid);
   *     }
   * ```
   */
  public fun dPtAtT(mid: Double): SkDPoint {
    TODO("Implement dPtAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector dSlopeAtT(double mid) const {
   *         return (*CurveDSlopeAtT[fVerb])(fPts, fWeight, mid);
   *     }
   * ```
   */
  public fun dSlopeAtT(mid: Double): SkDVector {
    TODO("Implement dSlopeAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dump() const {
   *     SkDebugf("%.*s", contour()->debugIndent(), "        ");
   *     this->dumpPts();
   *     const SkOpSpanBase* span = &fHead;
   *     contour()->indentDump();
   *     do {
   *         SkDebugf("%.*s span=%d ", contour()->debugIndent(), "        ", span->debugID());
   *         span->ptT()->dumpBase();
   *         span->dumpBase();
   *         SkDebugf("\n");
   *     } while (!span->final() && (span = span->upCast()->next()));
   *     contour()->outdentDump();
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dumpAll() const {
   *     SkDebugf("%.*s", contour()->debugIndent(), "        ");
   *     this->dumpPts();
   *     const SkOpSpanBase* span = &fHead;
   *     contour()->indentDump();
   *     do {
   *         span->dumpAll();
   *     } while (!span->final() && (span = span->upCast()->next()));
   *     contour()->outdentDump();
   * }
   * ```
   */
  public fun dumpAll() {
    TODO("Implement dumpAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dumpAngles() const {
   *     SkDebugf("seg=%d\n", debugID());
   *     const SkOpSpanBase* span = &fHead;
   *     do {
   *         const SkOpAngle* fAngle = span->fromAngle();
   *         const SkOpAngle* tAngle = span->final() ? nullptr : span->upCast()->toAngle();
   *         if (fAngle) {
   *             SkDebugf("  span=%d from=%d ", span->debugID(), fAngle->debugID());
   *             fAngle->dumpTo(this, tAngle);
   *         }
   *         if (tAngle) {
   *             SkDebugf("  span=%d to=%d   ", span->debugID(), tAngle->debugID());
   *             tAngle->dumpTo(this, fAngle);
   *         }
   *     } while (!span->final() && (span = span->upCast()->next()));
   * }
   * ```
   */
  public fun dumpAngles() {
    TODO("Implement dumpAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dumpCoin() const {
   *     const SkOpSpan* span = &fHead;
   *     do {
   *         span->dumpCoin();
   *     } while ((span = span->next()->upCastable()));
   * }
   * ```
   */
  public fun dumpCoin() {
    TODO("Implement dumpCoin")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dumpPts(const char* prefix) const {
   *     dumpPtsInner(prefix);
   *     SkDebugf("\n");
   * }
   * ```
   */
  public fun dumpPts(prefix: String? = TODO()) {
    TODO("Implement dumpPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::dumpPtsInner(const char* prefix) const {
   *     int last = SkPathOpsVerbToPoints(fVerb);
   *     SkDebugf("%s=%d {{", prefix, this->debugID());
   *     if (fVerb == SkPath::kConic_Verb) {
   *         SkDebugf("{");
   *     }
   *     int index = 0;
   *     do {
   *         SkDPoint::Dump(fPts[index]);
   *         SkDebugf(", ");
   *     } while (++index < last);
   *     SkDPoint::Dump(fPts[index]);
   *     SkDebugf("}}");
   *     if (fVerb == SkPath::kConic_Verb) {
   *         SkDebugf(", %1.9gf}", fWeight);
   *     }
   * }
   * ```
   */
  public fun dumpPtsInner(prefix: String? = TODO()) {
    TODO("Implement dumpPtsInner")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpSegment::existing(double t, const SkOpSegment* opp) const {
   *     const SkOpSpanBase* test = &fHead;
   *     const SkOpPtT* testPtT;
   *     SkPoint pt = this->ptAtT(t);
   *     do {
   *         testPtT = test->ptT();
   *         if (testPtT->fT == t) {
   *             break;
   *         }
   *         if (!this->match(testPtT, this, t, pt)) {
   *             if (t < testPtT->fT) {
   *                 return nullptr;
   *             }
   *             continue;
   *         }
   *         if (!opp) {
   *             return testPtT;
   *         }
   *         const SkOpPtT* loop = testPtT->next();
   *         while (loop != testPtT) {
   *             if (loop->segment() == this && loop->fT == t && loop->fPt == pt) {
   *                 goto foundMatch;
   *             }
   *             loop = loop->next();
   *         }
   *         return nullptr;
   *     } while ((test = test->upCast()->next()));
   * foundMatch:
   *     return opp && !test->contains(opp) ? nullptr : testPtT;
   * }
   * ```
   */
  public fun existing(t: Double, opp: SkOpSegment?): SkOpPtT {
    TODO("Implement existing")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* SkOpSegment::findNextOp(SkTDArray<SkOpSpanBase*>* chase, SkOpSpanBase** nextStart,
   *         SkOpSpanBase** nextEnd, bool* unsortable, bool* simple,
   *         SkPathOp op, int xorMiMask, int xorSuMask) {
   *     SkOpSpanBase* start = *nextStart;
   *     SkOpSpanBase* end = *nextEnd;
   *     SkASSERT(start != end);
   *     int step = start->step(end);
   *     SkOpSegment* other = this->isSimple(nextStart, &step);  // advances nextStart
   *     if ((*simple = other)) {
   *     // mark the smaller of startIndex, endIndex done, and all adjacent
   *     // spans with the same T value (but not 'other' spans)
   * #if DEBUG_WINDING
   *         SkDebugf("%s simple\n", __FUNCTION__);
   * #endif
   *         SkOpSpan* startSpan = start->starter(end);
   *         if (startSpan->done()) {
   *             return nullptr;
   *         }
   *         markDone(startSpan);
   *         *nextEnd = step > 0 ? (*nextStart)->upCast()->next() : (*nextStart)->prev();
   *         return other;
   *     }
   *     SkOpSpanBase* endNear = step > 0 ? (*nextStart)->upCast()->next() : (*nextStart)->prev();
   *     SkASSERT(endNear == end);  // is this ever not end?
   *     SkASSERT(endNear);
   *     SkASSERT(start != endNear);
   *     SkASSERT((start->t() < endNear->t()) ^ (step < 0));
   *     // more than one viable candidate -- measure angles to find best
   *     int calcWinding = computeSum(start, endNear, SkOpAngle::kBinaryOpp);
   *     bool sortable = calcWinding != SK_NaN32;
   *     if (!sortable) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   *     SkOpAngle* angle = this->spanToAngle(end, start);
   *     if (angle->unorderable()) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   * #if DEBUG_SORT
   *     SkDebugf("%s\n", __FUNCTION__);
   *     angle->debugLoop();
   * #endif
   *     int sumMiWinding = updateWinding(end, start);
   *     if (sumMiWinding == SK_MinS32) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   *     int sumSuWinding = updateOppWinding(end, start);
   *     if (operand()) {
   *         using std::swap;
   *         swap(sumMiWinding, sumSuWinding);
   *     }
   *     SkOpAngle* nextAngle = angle->next();
   *     const SkOpAngle* foundAngle = nullptr;
   *     bool foundDone = false;
   *     // iterate through the angle, and compute everyone's winding
   *     SkOpSegment* nextSegment;
   *     int activeCount = 0;
   *     do {
   *         nextSegment = nextAngle->segment();
   *         bool activeAngle = nextSegment->activeOp(xorMiMask, xorSuMask, nextAngle->start(),
   *                 nextAngle->end(), op, &sumMiWinding, &sumSuWinding);
   *         if (activeAngle) {
   *             ++activeCount;
   *             if (!foundAngle || (foundDone && activeCount & 1)) {
   *                 foundAngle = nextAngle;
   *                 foundDone = nextSegment->done(nextAngle);
   *             }
   *         }
   *         if (nextSegment->done()) {
   *             continue;
   *         }
   *         if (!activeAngle) {
   *             (void) nextSegment->markAndChaseDone(nextAngle->start(), nextAngle->end(), nullptr);
   *         }
   *         SkOpSpanBase* last = nextAngle->lastMarked();
   *         if (last) {
   *             SkASSERT(!SkPathOpsDebug::ChaseContains(*chase, last));
   *             *chase->append() = last;
   * #if DEBUG_WINDING
   *             SkDebugf("%s chase.append segment=%d span=%d", __FUNCTION__,
   *                     last->segment()->debugID(), last->debugID());
   *             if (!last->final()) {
   *                 SkDebugf(" windSum=%d", last->upCast()->windSum());
   *             }
   *             SkDebugf("\n");
   * #endif
   *         }
   *     } while ((nextAngle = nextAngle->next()) != angle);
   *     start->segment()->markDone(start->starter(end));
   *     if (!foundAngle) {
   *         return nullptr;
   *     }
   *     *nextStart = foundAngle->start();
   *     *nextEnd = foundAngle->end();
   *     nextSegment = foundAngle->segment();
   * #if DEBUG_WINDING
   *     SkDebugf("%s from:[%d] to:[%d] start=%p end=%p\n",
   *             __FUNCTION__, debugID(), nextSegment->debugID(), *nextStart, *nextEnd);
   *  #endif
   *     return nextSegment;
   * }
   * ```
   */
  public fun findNextOp(
    chase: SkTDArray<SkOpSpanBase?>?,
    nextStart: Int?,
    nextEnd: Int?,
    unsortable: Boolean?,
    simple: Boolean?,
    op: SkPathOp,
    xorMiMask: Int,
    xorSuMask: Int,
  ): SkOpSegment {
    TODO("Implement findNextOp")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* SkOpSegment::findNextWinding(SkTDArray<SkOpSpanBase*>* chase,
   *         SkOpSpanBase** nextStart, SkOpSpanBase** nextEnd, bool* unsortable) {
   *     SkOpSpanBase* start = *nextStart;
   *     SkOpSpanBase* end = *nextEnd;
   *     SkASSERT(start != end);
   *     int step = start->step(end);
   *     SkOpSegment* other = this->isSimple(nextStart, &step);  // advances nextStart
   *     if (other) {
   *     // mark the smaller of startIndex, endIndex done, and all adjacent
   *     // spans with the same T value (but not 'other' spans)
   * #if DEBUG_WINDING
   *         SkDebugf("%s simple\n", __FUNCTION__);
   * #endif
   *         SkOpSpan* startSpan = start->starter(end);
   *         if (startSpan->done()) {
   *             return nullptr;
   *         }
   *         markDone(startSpan);
   *         *nextEnd = step > 0 ? (*nextStart)->upCast()->next() : (*nextStart)->prev();
   *         return other;
   *     }
   *     SkOpSpanBase* endNear = step > 0 ? (*nextStart)->upCast()->next() : (*nextStart)->prev();
   *     SkASSERT(endNear == end);  // is this ever not end?
   *     SkASSERT(endNear);
   *     SkASSERT(start != endNear);
   *     SkASSERT((start->t() < endNear->t()) ^ (step < 0));
   *     // more than one viable candidate -- measure angles to find best
   *     int calcWinding = computeSum(start, endNear, SkOpAngle::kUnaryWinding);
   *     bool sortable = calcWinding != SK_NaN32;
   *     if (!sortable) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   *     SkOpAngle* angle = this->spanToAngle(end, start);
   *     if (angle->unorderable()) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   * #if DEBUG_SORT
   *     SkDebugf("%s\n", __FUNCTION__);
   *     angle->debugLoop();
   * #endif
   *     int sumWinding = updateWinding(end, start);
   *     SkOpAngle* nextAngle = angle->next();
   *     const SkOpAngle* foundAngle = nullptr;
   *     bool foundDone = false;
   *     // iterate through the angle, and compute everyone's winding
   *     SkOpSegment* nextSegment;
   *     int activeCount = 0;
   *     do {
   *         nextSegment = nextAngle->segment();
   *         bool activeAngle = nextSegment->activeWinding(nextAngle->start(), nextAngle->end(),
   *                 &sumWinding);
   *         if (activeAngle) {
   *             ++activeCount;
   *             if (!foundAngle || (foundDone && activeCount & 1)) {
   *                 foundAngle = nextAngle;
   *                 foundDone = nextSegment->done(nextAngle);
   *             }
   *         }
   *         if (nextSegment->done()) {
   *             continue;
   *         }
   *         if (!activeAngle) {
   *             (void) nextSegment->markAndChaseDone(nextAngle->start(), nextAngle->end(), nullptr);
   *         }
   *         SkOpSpanBase* last = nextAngle->lastMarked();
   *         if (last) {
   *             SkASSERT(!SkPathOpsDebug::ChaseContains(*chase, last));
   *             *chase->append() = last;
   * #if DEBUG_WINDING
   *             SkDebugf("%s chase.append segment=%d span=%d", __FUNCTION__,
   *                     last->segment()->debugID(), last->debugID());
   *             if (!last->final()) {
   *                 SkDebugf(" windSum=%d", last->upCast()->windSum());
   *             }
   *             SkDebugf("\n");
   * #endif
   *         }
   *     } while ((nextAngle = nextAngle->next()) != angle);
   *     start->segment()->markDone(start->starter(end));
   *     if (!foundAngle) {
   *         return nullptr;
   *     }
   *     *nextStart = foundAngle->start();
   *     *nextEnd = foundAngle->end();
   *     nextSegment = foundAngle->segment();
   * #if DEBUG_WINDING
   *     SkDebugf("%s from:[%d] to:[%d] start=%p end=%p\n",
   *             __FUNCTION__, debugID(), nextSegment->debugID(), *nextStart, *nextEnd);
   *  #endif
   *     return nextSegment;
   * }
   * ```
   */
  public fun findNextWinding(
    chase: SkTDArray<SkOpSpanBase?>?,
    nextStart: Int?,
    nextEnd: Int?,
    unsortable: Boolean?,
  ): SkOpSegment {
    TODO("Implement findNextWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* SkOpSegment::findNextXor(SkOpSpanBase** nextStart, SkOpSpanBase** nextEnd,
   *         bool* unsortable) {
   *     SkOpSpanBase* start = *nextStart;
   *     SkOpSpanBase* end = *nextEnd;
   *     SkASSERT(start != end);
   *     int step = start->step(end);
   *     SkOpSegment* other = this->isSimple(nextStart, &step);  // advances nextStart
   *     if (other) {
   *     // mark the smaller of startIndex, endIndex done, and all adjacent
   *     // spans with the same T value (but not 'other' spans)
   * #if DEBUG_WINDING
   *         SkDebugf("%s simple\n", __FUNCTION__);
   * #endif
   *         SkOpSpan* startSpan = start->starter(end);
   *         if (startSpan->done()) {
   *             return nullptr;
   *         }
   *         markDone(startSpan);
   *         *nextEnd = step > 0 ? (*nextStart)->upCast()->next() : (*nextStart)->prev();
   *         return other;
   *     }
   *     SkDEBUGCODE(SkOpSpanBase* endNear = step > 0 ? (*nextStart)->upCast()->next() \
   *             : (*nextStart)->prev());
   *     SkASSERT(endNear == end);  // is this ever not end?
   *     SkASSERT(endNear);
   *     SkASSERT(start != endNear);
   *     SkASSERT((start->t() < endNear->t()) ^ (step < 0));
   *     SkOpAngle* angle = this->spanToAngle(end, start);
   *     if (!angle || angle->unorderable()) {
   *         *unsortable = true;
   *         markDone(start->starter(end));
   *         return nullptr;
   *     }
   * #if DEBUG_SORT
   *     SkDebugf("%s\n", __FUNCTION__);
   *     angle->debugLoop();
   * #endif
   *     SkOpAngle* nextAngle = angle->next();
   *     const SkOpAngle* foundAngle = nullptr;
   *     bool foundDone = false;
   *     // iterate through the angle, and compute everyone's winding
   *     SkOpSegment* nextSegment;
   *     int activeCount = 0;
   *     do {
   *         if (!nextAngle) {
   *             return nullptr;
   *         }
   *         nextSegment = nextAngle->segment();
   *         ++activeCount;
   *         if (!foundAngle || (foundDone && activeCount & 1)) {
   *             foundAngle = nextAngle;
   *             if (!(foundDone = nextSegment->done(nextAngle))) {
   *                 break;
   *             }
   *         }
   *         nextAngle = nextAngle->next();
   *     } while (nextAngle != angle);
   *     start->segment()->markDone(start->starter(end));
   *     if (!foundAngle) {
   *         return nullptr;
   *     }
   *     *nextStart = foundAngle->start();
   *     *nextEnd = foundAngle->end();
   *     nextSegment = foundAngle->segment();
   * #if DEBUG_WINDING
   *     SkDebugf("%s from:[%d] to:[%d] start=%p end=%p\n",
   *             __FUNCTION__, debugID(), nextSegment->debugID(), *nextStart, *nextEnd);
   *  #endif
   *     return nextSegment;
   * }
   * ```
   */
  public fun findNextXor(
    nextStart: Int?,
    nextEnd: Int?,
    unsortable: Boolean?,
  ): SkOpSegment {
    TODO("Implement findNextXor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* SkOpSegment::findSortableTop(SkOpContour* contourHead) {
   *     SkOpSpan* span = &fHead;
   *     SkOpSpanBase* next;
   *     do {
   *         next = span->next();
   *         if (span->done()) {
   *             continue;
   *         }
   *         if (span->windSum() != SK_MinS32) {
   *             return span;
   *         }
   *         if (span->sortableTop(contourHead)) {
   *             return span;
   *         }
   *     } while (!next->final() && (span = next->upCast()));
   *     return nullptr;
   * }
   * ```
   */
  public fun findSortableTop(contourHead: SkOpContour?): SkOpSpan {
    TODO("Implement findSortableTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* SkOpSegment::globalState() const {
   *     return contour()->globalState();
   * }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpan* head() const {
   *         return &fHead;
   *     }
   * ```
   */
  public fun head(): SkOpSpan {
    TODO("Implement head")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* head() {
   *         return &fHead;
   *     }
   * ```
   */
  public fun `init`(
    pts: Array<SkPoint>,
    weight: SkScalar,
    parent: SkOpContour?,
    verb: SkPathVerb,
  ) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::init(SkPoint pts[], SkScalar weight, SkOpContour* contour, SkPath::Verb verb) {
   *     fContour = contour;
   *     fNext = nullptr;
   *     fPts = pts;
   *     fWeight = weight;
   *     fVerb = verb;
   *     fCount = 0;
   *     fDoneCount = 0;
   *     fVisited = false;
   *     SkOpSpan* zeroSpan = &fHead;
   *     zeroSpan->init(this, nullptr, 0, fPts[0]);
   *     SkOpSpanBase* oneSpan = &fTail;
   *     zeroSpan->setNext(oneSpan);
   *     oneSpan->initBase(this, zeroSpan, 1, fPts[SkPathOpsVerbToPoints(fVerb)]);
   *     SkDEBUGCODE(fID = globalState()->nextSegmentID());
   * }
   * ```
   */
  public fun insert(prev: SkOpSpan?): SkOpSpan {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* insert(SkOpSpan* prev) {
   *         SkOpGlobalState* globalState = this->globalState();
   *         globalState->setAllocatedOpSpan();
   *         SkOpSpan* result = globalState->allocator()->make<SkOpSpan>();
   *         SkOpSpanBase* next = prev->next();
   *         result->setPrev(prev);
   *         prev->setNext(result);
   *         SkDEBUGCODE(result->ptT()->fT = 0);
   *         result->setNext(next);
   *         if (next) {
   *             next->setPrev(result);
   *         }
   *         return result;
   *     }
   * ```
   */
  public fun isClose(t: Double, opp: SkOpSegment?): Boolean {
    TODO("Implement isClose")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::isClose(double t, const SkOpSegment* opp) const {
   *     SkDPoint cPt = this->dPtAtT(t);
   *     SkDVector dxdy = (*CurveDSlopeAtT[this->verb()])(this->pts(), this->weight(), t);
   *     SkDLine perp = {{ cPt, {cPt.fX + dxdy.fY, cPt.fY - dxdy.fX} }};
   *     SkIntersections i;
   *     (*CurveIntersectRay[opp->verb()])(opp->pts(), opp->weight(), perp, &i);
   *     int used = i.used();
   *     for (int index = 0; index < used; ++index) {
   *         if (cPt.roughlyEqual(i.pt(index))) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun isHorizontal(): Boolean {
    TODO("Implement isHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isHorizontal() const {
   *         return fBounds.fTop == fBounds.fBottom;
   *     }
   * ```
   */
  public fun isSimple(end: Int?, step: Int?): SkOpSegment {
    TODO("Implement isSimple")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* isSimple(SkOpSpanBase** end, int* step) const {
   *         return nextChase(end, step, nullptr, nullptr);
   *     }
   * ```
   */
  public fun isVertical(): Boolean {
    TODO("Implement isVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVertical() const {
   *         return fBounds.fLeft == fBounds.fRight;
   *     }
   * ```
   */
  public fun isVertical(start: SkOpSpanBase?, end: SkOpSpanBase?): Boolean {
    TODO("Implement isVertical")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isVertical(SkOpSpanBase* start, SkOpSpanBase* end) const {
   *         return (*CurveIsVertical[fVerb])(fPts, fWeight, start->t(), end->t());
   *     }
   * ```
   */
  public fun isXor(): Boolean {
    TODO("Implement isXor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::isXor() const {
   *     return fContour->isXor();
   * }
   * ```
   */
  public fun joinEnds(start: SkOpSegment?) {
    TODO("Implement joinEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * void joinEnds(SkOpSegment* start) {
   *         fTail.ptT()->addOpp(start->fHead.ptT(), start->fHead.ptT());
   *     }
   * ```
   */
  public fun lastPt(): SkPoint {
    TODO("Implement lastPt")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& lastPt() const {
   *         return fPts[SkPathOpsVerbToPoints(fVerb)];
   *     }
   * ```
   */
  public fun markAllDone() {
    TODO("Implement markAllDone")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::markAllDone() {
   *     SkOpSpan* span = this->head();
   *     do {
   *         this->markDone(span);
   *     } while ((span = span->next()->upCastable()));
   * }
   * ```
   */
  public fun markAndChaseDone(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    found: Int?,
  ): Boolean {
    TODO("Implement markAndChaseDone")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markAndChaseDone(SkOpSpanBase* start, SkOpSpanBase* end, SkOpSpanBase** found) {
   *     int step = start->step(end);
   *     SkOpSpan* minSpan = start->starter(end);
   *     markDone(minSpan);
   *     SkOpSpanBase* last = nullptr;
   *     SkOpSegment* other = this;
   *     SkOpSpan* priorDone = nullptr;
   *     SkOpSpan* lastDone = nullptr;
   *     int safetyNet = 1000;
   *     while ((other = other->nextChase(&start, &step, &minSpan, &last))) {
   *         if (!--safetyNet) {
   *             return false;
   *         }
   *         if (other->done()) {
   *             SkASSERT(!last);
   *             break;
   *         }
   *         if (lastDone == minSpan || priorDone == minSpan) {
   *             if (found) {
   *                 *found = nullptr;
   *             }
   *             return true;
   *         }
   *         other->markDone(minSpan);
   *         priorDone = lastDone;
   *         lastDone = minSpan;
   *     }
   *     if (found) {
   *         *found = last;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun markAndChaseWinding(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    winding: Int,
    lastPtr: Int?,
  ): Boolean {
    TODO("Implement markAndChaseWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markAndChaseWinding(SkOpSpanBase* start, SkOpSpanBase* end, int winding,
   *         SkOpSpanBase** lastPtr) {
   *     SkOpSpan* spanStart = start->starter(end);
   *     int step = start->step(end);
   *     bool success = markWinding(spanStart, winding);
   *     SkOpSpanBase* last = nullptr;
   *     SkOpSegment* other = this;
   *     int safetyNet = 1000;
   *     while ((other = other->nextChase(&start, &step, &spanStart, &last))) {
   *         if (!--safetyNet) {
   *             return false;
   *         }
   *         if (spanStart->windSum() != SK_MinS32) {
   * //            SkASSERT(spanStart->windSum() == winding);   // FIXME: is this assert too aggressive?
   *             SkASSERT(!last);
   *             break;
   *         }
   *         (void) other->markWinding(spanStart, winding);
   *     }
   *     if (lastPtr) {
   *         *lastPtr = last;
   *     }
   *     return success;
   * }
   * ```
   */
  public fun markAndChaseWinding(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    winding: Int,
    oppWinding: Int,
    lastPtr: Int?,
  ): Boolean {
    TODO("Implement markAndChaseWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markAndChaseWinding(SkOpSpanBase* start, SkOpSpanBase* end,
   *         int winding, int oppWinding, SkOpSpanBase** lastPtr) {
   *     SkOpSpan* spanStart = start->starter(end);
   *     int step = start->step(end);
   *     bool success = markWinding(spanStart, winding, oppWinding);
   *     SkOpSpanBase* last = nullptr;
   *     SkOpSegment* other = this;
   *     int safetyNet = 1000;
   *     while ((other = other->nextChase(&start, &step, &spanStart, &last))) {
   *         if (!--safetyNet) {
   *             return false;
   *         }
   *         if (spanStart->windSum() != SK_MinS32) {
   *             if (this->operand() == other->operand()) {
   *                 if (spanStart->windSum() != winding || spanStart->oppSum() != oppWinding) {
   *                     this->globalState()->setWindingFailed();
   *                     return true;  // ... but let it succeed anyway
   *                 }
   *             } else {
   *                 FAIL_IF(spanStart->windSum() != oppWinding);
   *                 FAIL_IF(spanStart->oppSum() != winding);
   *             }
   *             SkASSERT(!last);
   *             break;
   *         }
   *         if (this->operand() == other->operand()) {
   *             (void) other->markWinding(spanStart, winding, oppWinding);
   *         } else {
   *             (void) other->markWinding(spanStart, oppWinding, winding);
   *         }
   *     }
   *     if (lastPtr) {
   *         *lastPtr = last;
   *     }
   *     return success;
   * }
   * ```
   */
  public fun markAngle(
    maxWinding: Int,
    sumWinding: Int,
    angle: SkOpAngle?,
    result: Int?,
  ): Boolean {
    TODO("Implement markAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markAngle(int maxWinding, int sumWinding, const SkOpAngle* angle,
   *                             SkOpSpanBase** result) {
   *     SkASSERT(angle->segment() == this);
   *     if (UseInnerWinding(maxWinding, sumWinding)) {
   *         maxWinding = sumWinding;
   *     }
   *     if (!markAndChaseWinding(angle->start(), angle->end(), maxWinding, result)) {
   *         return false;
   *     }
   * #if DEBUG_WINDING
   *     SkOpSpanBase* last = *result;
   *     if (last) {
   *         SkDebugf("%s last seg=%d span=%d", __FUNCTION__,
   *                 last->segment()->debugID(), last->debugID());
   *         if (!last->final()) {
   *             SkDebugf(" windSum=");
   *             SkPathOpsDebug::WindingPrintf(last->upCast()->windSum());
   *         }
   *         SkDebugf("\n");
   *     }
   * #endif
   *     return true;
   * }
   * ```
   */
  public fun markAngle(
    maxWinding: Int,
    sumWinding: Int,
    oppMaxWinding: Int,
    oppSumWinding: Int,
    angle: SkOpAngle?,
    result: Int?,
  ): Boolean {
    TODO("Implement markAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markAngle(int maxWinding, int sumWinding, int oppMaxWinding,
   *                             int oppSumWinding, const SkOpAngle* angle, SkOpSpanBase** result) {
   *     SkASSERT(angle->segment() == this);
   *     if (UseInnerWinding(maxWinding, sumWinding)) {
   *         maxWinding = sumWinding;
   *     }
   *     if (oppMaxWinding != oppSumWinding && UseInnerWinding(oppMaxWinding, oppSumWinding)) {
   *         oppMaxWinding = oppSumWinding;
   *     }
   *     // caller doesn't require that this marks anything
   *     if (!markAndChaseWinding(angle->start(), angle->end(), maxWinding, oppMaxWinding, result)) {
   *         return false;
   *     }
   * #if DEBUG_WINDING
   *     if (result) {
   *         SkOpSpanBase* last = *result;
   *         if (last) {
   *             SkDebugf("%s last segment=%d span=%d", __FUNCTION__,
   *                     last->segment()->debugID(), last->debugID());
   *             if (!last->final()) {
   *                 SkDebugf(" windSum=");
   *                 SkPathOpsDebug::WindingPrintf(last->upCast()->windSum());
   *             }
   *             SkDebugf(" \n");
   *         }
   *     }
   * #endif
   *     return true;
   * }
   * ```
   */
  public fun markDone(span: SkOpSpan?) {
    TODO("Implement markDone")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::markDone(SkOpSpan* span) {
   *     SkASSERT(this == span->segment());
   *     if (span->done()) {
   *         return;
   *     }
   * #if DEBUG_MARK_DONE
   *     debugShowNewWinding(__FUNCTION__, span, span->windSum(), span->oppSum());
   * #endif
   *     span->setDone(true);
   *     ++fDoneCount;
   *     debugValidate();
   * }
   * ```
   */
  public fun markWinding(span: SkOpSpan?, winding: Int): Boolean {
    TODO("Implement markWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markWinding(SkOpSpan* span, int winding) {
   *     SkASSERT(this == span->segment());
   *     SkASSERT(winding);
   *     if (span->done()) {
   *         return false;
   *     }
   * #if DEBUG_MARK_DONE
   *     debugShowNewWinding(__FUNCTION__, span, winding);
   * #endif
   *     span->setWindSum(winding);
   *     debugValidate();
   *     return true;
   * }
   * ```
   */
  public fun markWinding(
    span: SkOpSpan?,
    winding: Int,
    oppWinding: Int,
  ): Boolean {
    TODO("Implement markWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::markWinding(SkOpSpan* span, int winding, int oppWinding) {
   *     SkASSERT(this == span->segment());
   *     SkASSERT(winding || oppWinding);
   *     if (span->done()) {
   *         return false;
   *     }
   * #if DEBUG_MARK_DONE
   *     debugShowNewWinding(__FUNCTION__, span, winding, oppWinding);
   * #endif
   *     span->setWindSum(winding);
   *     span->setOppSum(oppWinding);
   *     debugValidate();
   *     return true;
   * }
   * ```
   */
  public fun match(
    span: SkOpPtT?,
    parent: SkOpSegment?,
    t: Double,
    pt: SkPoint,
  ): Boolean {
    TODO("Implement match")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::match(const SkOpPtT* base, const SkOpSegment* testParent, double testT,
   *         const SkPoint& testPt) const {
   *     SkASSERT(this == base->segment());
   *     if (this == testParent) {
   *         if (precisely_equal(base->fT, testT)) {
   *             return true;
   *         }
   *     }
   *     if (!SkDPoint::ApproximatelyEqual(testPt, base->fPt)) {
   *         return false;
   *     }
   *     return this != testParent || !this->ptsDisjoint(base->fT, base->fPt, testT, testPt);
   * }
   * ```
   */
  public fun missingCoincidence(): Boolean {
    TODO("Implement missingCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::missingCoincidence() {
   *     if (this->done()) {
   *         return false;
   *     }
   *     SkOpSpan* prior = nullptr;
   *     SkOpSpanBase* spanBase = &fHead;
   *     bool result = false;
   *     int safetyNet = 1000;
   *     do {
   *         SkOpPtT* ptT = spanBase->ptT(), * spanStopPtT = ptT;
   *         SkOPASSERT(ptT->span() == spanBase);
   *         while ((ptT = ptT->next()) != spanStopPtT) {
   *             if (!--safetyNet) {
   *                 return false;
   *             }
   *             if (ptT->deleted()) {
   *                 continue;
   *             }
   *             SkOpSegment* opp = ptT->span()->segment();
   *             if (opp->done()) {
   *                 continue;
   *             }
   *             // when opp is encounted the 1st time, continue; on 2nd encounter, look for coincidence
   *             if (!opp->visited()) {
   *                 continue;
   *             }
   *             if (spanBase == &fHead) {
   *                 continue;
   *             }
   *             if (ptT->segment() == this) {
   *                 continue;
   *             }
   *             SkOpSpan* span = spanBase->upCastable();
   *             // FIXME?: this assumes that if the opposite segment is coincident then no more
   *             // coincidence needs to be detected. This may not be true.
   *             if (span && span->containsCoincidence(opp)) {
   *                 continue;
   *             }
   *             if (spanBase->containsCoinEnd(opp)) {
   *                 continue;
   *             }
   *             SkOpPtT* priorPtT = nullptr, * priorStopPtT;
   *             // find prior span containing opp segment
   *             SkOpSegment* priorOpp = nullptr;
   *             SkOpSpan* priorTest = spanBase->prev();
   *             while (!priorOpp && priorTest) {
   *                 priorStopPtT = priorPtT = priorTest->ptT();
   *                 while ((priorPtT = priorPtT->next()) != priorStopPtT) {
   *                     if (priorPtT->deleted()) {
   *                         continue;
   *                     }
   *                     SkOpSegment* segment = priorPtT->span()->segment();
   *                     if (segment == opp) {
   *                         prior = priorTest;
   *                         priorOpp = opp;
   *                         break;
   *                     }
   *                 }
   *                 priorTest = priorTest->prev();
   *             }
   *             if (!priorOpp) {
   *                 continue;
   *             }
   *             if (priorPtT == ptT) {
   *                 continue;
   *             }
   *             SkOpPtT* oppStart = prior->ptT();
   *             SkOpPtT* oppEnd = spanBase->ptT();
   *             bool swapped = priorPtT->fT > ptT->fT;
   *             if (swapped) {
   *                 using std::swap;
   *                 swap(priorPtT, ptT);
   *                 swap(oppStart, oppEnd);
   *             }
   *             SkOpCoincidence* coincidences = this->globalState()->coincidence();
   *             SkOpPtT* rootPriorPtT = priorPtT->span()->ptT();
   *             SkOpPtT* rootPtT = ptT->span()->ptT();
   *             SkOpPtT* rootOppStart = oppStart->span()->ptT();
   *             SkOpPtT* rootOppEnd = oppEnd->span()->ptT();
   *             if (coincidences->contains(rootPriorPtT, rootPtT, rootOppStart, rootOppEnd)) {
   *                 goto swapBack;
   *             }
   *             if (this->testForCoincidence(rootPriorPtT, rootPtT, prior, spanBase, opp)) {
   *             // mark coincidence
   * #if DEBUG_COINCIDENCE_VERBOSE
   *                 SkDebugf("%s coinSpan=%d endSpan=%d oppSpan=%d oppEndSpan=%d\n", __FUNCTION__,
   *                         rootPriorPtT->debugID(), rootPtT->debugID(), rootOppStart->debugID(),
   *                         rootOppEnd->debugID());
   * #endif
   *                 if (!coincidences->extend(rootPriorPtT, rootPtT, rootOppStart, rootOppEnd)) {
   *                     coincidences->add(rootPriorPtT, rootPtT, rootOppStart, rootOppEnd);
   *                 }
   * #if DEBUG_COINCIDENCE
   *                 SkASSERT(coincidences->contains(rootPriorPtT, rootPtT, rootOppStart, rootOppEnd));
   * #endif
   *                 result = true;
   *             }
   *     swapBack:
   *             if (swapped) {
   *                 using std::swap;
   *                 swap(priorPtT, ptT);
   *             }
   *         }
   *     } while ((spanBase = spanBase->final() ? nullptr : spanBase->upCast()->next()));
   *     ClearVisited(&fHead);
   *     return result;
   * }
   * ```
   */
  public fun moveMultiples(): Boolean {
    TODO("Implement moveMultiples")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::moveMultiples() {
   *     debugValidate();
   *     SkOpSpanBase* test = &fHead;
   *     do {
   *         int addCount = test->spanAddsCount();
   * //        FAIL_IF(addCount < 1);
   *         if (addCount <= 1) {
   *             continue;
   *         }
   *         SkOpPtT* startPtT = test->ptT();
   *         SkOpPtT* testPtT = startPtT;
   *         int safetyHatch = 1000;
   *         do {  // iterate through all spans associated with start
   *             if (!--safetyHatch) {
   *                 return false;
   *             }
   *             SkOpSpanBase* oppSpan = testPtT->span();
   *             if (oppSpan->spanAddsCount() == addCount) {
   *                 continue;
   *             }
   *             if (oppSpan->deleted()) {
   *                 continue;
   *             }
   *             SkOpSegment* oppSegment = oppSpan->segment();
   *             if (oppSegment == this) {
   *                 continue;
   *             }
   *             // find range of spans to consider merging
   *             SkOpSpanBase* oppPrev = oppSpan;
   *             SkOpSpanBase* oppFirst = oppSpan;
   *             while ((oppPrev = oppPrev->prev())) {
   *                 if (!roughly_equal(oppPrev->t(), oppSpan->t())) {
   *                     break;
   *                 }
   *                 if (oppPrev->spanAddsCount() == addCount) {
   *                     continue;
   *                 }
   *                 if (oppPrev->deleted()) {
   *                     continue;
   *                 }
   *                 oppFirst = oppPrev;
   *             }
   *             SkOpSpanBase* oppNext = oppSpan;
   *             SkOpSpanBase* oppLast = oppSpan;
   *             while ((oppNext = oppNext->final() ? nullptr : oppNext->upCast()->next())) {
   *                 if (!roughly_equal(oppNext->t(), oppSpan->t())) {
   *                     break;
   *                 }
   *                 if (oppNext->spanAddsCount() == addCount) {
   *                     continue;
   *                 }
   *                 if (oppNext->deleted()) {
   *                     continue;
   *                 }
   *                 oppLast = oppNext;
   *             }
   *             if (oppFirst == oppLast) {
   *                 continue;
   *             }
   *             SkOpSpanBase* oppTest = oppFirst;
   *             do {
   *                 if (oppTest == oppSpan) {
   *                     continue;
   *                 }
   *                 // check to see if the candidate meets specific criteria:
   *                 // it contains spans of segments in test's loop but not including 'this'
   *                 SkOpPtT* oppStartPtT = oppTest->ptT();
   *                 SkOpPtT* oppPtT = oppStartPtT;
   *                 while ((oppPtT = oppPtT->next()) != oppStartPtT) {
   *                     SkOpSegment* oppPtTSegment = oppPtT->segment();
   *                     if (oppPtTSegment == this) {
   *                         goto tryNextSpan;
   *                     }
   *                     SkOpPtT* matchPtT = startPtT;
   *                     do {
   *                         if (matchPtT->segment() == oppPtTSegment) {
   *                             goto foundMatch;
   *                         }
   *                     } while ((matchPtT = matchPtT->next()) != startPtT);
   *                     goto tryNextSpan;
   *             foundMatch:  // merge oppTest and oppSpan
   *                     oppSegment->debugValidate();
   *                     oppTest->mergeMatches(oppSpan);
   *                     oppTest->addOpp(oppSpan);
   *                     oppSegment->debugValidate();
   *                     goto checkNextSpan;
   *                 }
   *         tryNextSpan:
   *                 ;
   *             } while (oppTest != oppLast && (oppTest = oppTest->upCast()->next()));
   *         } while ((testPtT = testPtT->next()) != startPtT);
   * checkNextSpan:
   *         ;
   *     } while ((test = test->final() ? nullptr : test->upCast()->next()));
   *     debugValidate();
   *     return true;
   * }
   * ```
   */
  public fun moveNearby(): Boolean {
    TODO("Implement moveNearby")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::moveNearby() {
   *     debugValidate();
   *     // release undeleted spans pointing to this seg that are linked to the primary span
   *     SkOpSpanBase* spanBase = &fHead;
   *     int escapeHatch = 9999;  // the largest count for a regular test is 50; for a fuzzer, 500
   *     do {
   *         SkOpPtT* ptT = spanBase->ptT();
   *         const SkOpPtT* headPtT = ptT;
   *         while ((ptT = ptT->next()) != headPtT) {
   *             if (!--escapeHatch) {
   *                 return false;
   *             }
   *             SkOpSpanBase* test = ptT->span();
   *             if (ptT->segment() == this && !ptT->deleted() && test != spanBase
   *                     && test->ptT() == ptT) {
   *                 if (test->final()) {
   *                     if (spanBase == &fHead) {
   *                         this->clearAll();
   *                         return true;
   *                     }
   *                     spanBase->upCast()->release(ptT);
   *                 } else if (test->prev()) {
   *                     test->upCast()->release(headPtT);
   *                 }
   *                 break;
   *             }
   *         }
   *         spanBase = spanBase->upCast()->next();
   *     } while (!spanBase->final());
   *     // This loop looks for adjacent spans which are near by
   *     spanBase = &fHead;
   *     do {  // iterate through all spans associated with start
   *         SkOpSpanBase* test = spanBase->upCast()->next();
   *         bool found;
   *         if (!this->spansNearby(spanBase, test, &found)) {
   *             return false;
   *         }
   *         if (found) {
   *             if (test->final()) {
   *                 if (spanBase->prev()) {
   *                     test->merge(spanBase->upCast());
   *                 } else {
   *                     this->clearAll();
   *                     return true;
   *                 }
   *             } else {
   *                 spanBase->merge(test->upCast());
   *             }
   *         }
   *         spanBase = test;
   *     } while (!spanBase->final());
   *     debugValidate();
   *     return true;
   * }
   * ```
   */
  public fun next(): SkOpSegment {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* next() const {
   *         return fNext;
   *     }
   * ```
   */
  public fun nextChase(
    startPtr: Int?,
    step: Int?,
    minPtr: Int?,
    last: Int?,
  ): SkOpSegment {
    TODO("Implement nextChase")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* SkOpSegment::nextChase(SkOpSpanBase** startPtr, int* stepPtr, SkOpSpan** minPtr,
   *         SkOpSpanBase** last) const {
   *     SkOpSpanBase* origStart = *startPtr;
   *     int step = *stepPtr;
   *     SkOpSpanBase* endSpan = step > 0 ? origStart->upCast()->next() : origStart->prev();
   *     SkASSERT(endSpan);
   *     SkOpAngle* angle = step > 0 ? endSpan->fromAngle() : endSpan->upCast()->toAngle();
   *     SkOpSpanBase* foundSpan;
   *     SkOpSpanBase* otherEnd;
   *     SkOpSegment* other;
   *     if (angle == nullptr) {
   *         if (endSpan->t() != 0 && endSpan->t() != 1) {
   *             return nullptr;
   *         }
   *         SkOpPtT* otherPtT = endSpan->ptT()->next();
   *         other = otherPtT->segment();
   *         foundSpan = otherPtT->span();
   *         otherEnd = step > 0
   *                 ? foundSpan->upCastable() ? foundSpan->upCast()->next() : nullptr
   *                 : foundSpan->prev();
   *     } else {
   *         int loopCount = angle->loopCount();
   *         if (loopCount > 2) {
   *             return set_last(last, endSpan);
   *         }
   *         const SkOpAngle* next = angle->next();
   *         if (nullptr == next) {
   *             return nullptr;
   *         }
   * #if DEBUG_WINDING
   *         if (angle->debugSign() != next->debugSign() && !angle->segment()->contour()->isXor()
   *                 && !next->segment()->contour()->isXor()) {
   *             SkDebugf("%s mismatched signs\n", __FUNCTION__);
   *         }
   * #endif
   *         other = next->segment();
   *         foundSpan = endSpan = next->start();
   *         otherEnd = next->end();
   *     }
   *     if (!otherEnd) {
   *         return nullptr;
   *     }
   *     int foundStep = foundSpan->step(otherEnd);
   *     if (*stepPtr != foundStep) {
   *         return set_last(last, endSpan);
   *     }
   *     SkASSERT(*startPtr);
   * //    SkASSERT(otherEnd >= 0);
   *     SkOpSpan* origMin = step < 0 ? origStart->prev() : origStart->upCast();
   *     SkOpSpan* foundMin = foundSpan->starter(otherEnd);
   *     if (foundMin->windValue() != origMin->windValue()
   *             || foundMin->oppValue() != origMin->oppValue()) {
   *           return set_last(last, endSpan);
   *     }
   *     *startPtr = foundSpan;
   *     *stepPtr = foundStep;
   *     if (minPtr) {
   *         *minPtr = foundMin;
   *     }
   *     return other;
   * }
   * ```
   */
  public fun operand(): Boolean {
    TODO("Implement operand")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::operand() const {
   *     return fContour->operand();
   * }
   * ```
   */
  public fun oppXor(): Boolean {
    TODO("Implement oppXor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::oppXor() const {
   *     return fContour->oppXor();
   * }
   * ```
   */
  public fun prev(): SkOpSegment {
    TODO("Implement prev")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* prev() const {
   *         return fPrev;
   *     }
   * ```
   */
  public fun ptAtT(mid: Double): SkPoint {
    TODO("Implement ptAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint ptAtT(double mid) const {
   *         return (*CurvePointAtT[fVerb])(fPts, fWeight, mid);
   *     }
   * ```
   */
  public fun pts(): SkPoint {
    TODO("Implement pts")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* pts() const {
   *         return fPts;
   *     }
   * ```
   */
  public fun ptsDisjoint(span: SkOpPtT, test: SkOpPtT): Boolean {
    TODO("Implement ptsDisjoint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ptsDisjoint(const SkOpPtT& span, const SkOpPtT& test) const {
   *         SkASSERT(this == span.segment());
   *         SkASSERT(this == test.segment());
   *         return ptsDisjoint(span.fT, span.fPt, test.fT, test.fPt);
   *     }
   * ```
   */
  public fun ptsDisjoint(
    span: SkOpPtT,
    t: Double,
    pt: SkPoint,
  ): Boolean {
    TODO("Implement ptsDisjoint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ptsDisjoint(const SkOpPtT& span, double t, const SkPoint& pt) const {
   *         SkASSERT(this == span.segment());
   *         return ptsDisjoint(span.fT, span.fPt, t, pt);
   *     }
   * ```
   */
  public fun ptsDisjoint(
    t1: Double,
    pt1: SkPoint,
    t2: Double,
    pt2: SkPoint,
  ): Boolean {
    TODO("Implement ptsDisjoint")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::ptsDisjoint(double t1, const SkPoint& pt1, double t2, const SkPoint& pt2) const {
   *     if (fVerb == SkPath::kLine_Verb) {
   *         return false;
   *     }
   *     // quads (and cubics) can loop back to nearly a line so that an opposite curve
   *     // hits in two places with very different t values.
   *     // OPTIMIZATION: curves could be preflighted so that, for example, something like
   *     // 'controls contained by ends' could avoid this check for common curves
   *     // 'ends are extremes in x or y' is cheaper to compute and real-world common
   *     // on the other hand, the below check is relatively inexpensive
   *     double midT = (t1 + t2) / 2;
   *     SkPoint midPt = this->ptAtT(midT);
   *     double seDistSq = std::max(SkPointPriv::DistanceToSqd(pt1, pt2) * 2, FLT_EPSILON * 2);
   *     return SkPointPriv::DistanceToSqd(midPt, pt1) > seDistSq ||
   *            SkPointPriv::DistanceToSqd(midPt, pt2) > seDistSq;
   * }
   * ```
   */
  public fun rayCheck(
    base: SkOpRayHit,
    dir: SkOpRayDir,
    hits: Int?,
    allocator: SkArenaAlloc?,
  ) {
    TODO("Implement rayCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::rayCheck(const SkOpRayHit& base, SkOpRayDir dir, SkOpRayHit** hits,
   *                            SkArenaAlloc* allocator) {
   *     if (!sideways_overlap(fBounds, base.fPt, dir)) {
   *         return;
   *     }
   *     SkScalar baseXY = pt_xy(base.fPt, dir);
   *     SkScalar boundsXY = rect_side(fBounds, dir);
   *     bool checkLessThan = less_than(dir);
   *     if (!approximately_equal(baseXY, boundsXY) && (baseXY < boundsXY) == checkLessThan) {
   *         return;
   *     }
   *     double tVals[3];
   *     SkScalar baseYX = pt_yx(base.fPt, dir);
   *     int roots = (*CurveIntercept[fVerb * 2 + xy_index(dir)])(fPts, fWeight, baseYX, tVals);
   *     for (int index = 0; index < roots; ++index) {
   *         double t = tVals[index];
   *         if (base.fSpan->segment() == this && approximately_equal(base.fT, t)) {
   *             continue;
   *         }
   *         SkDVector slope;
   *         SkPoint pt;
   *         SkDEBUGCODE(sk_bzero(&slope, sizeof(slope)));
   *         bool valid = false;
   *         if (approximately_zero(t)) {
   *             pt = fPts[0];
   *         } else if (approximately_equal(t, 1)) {
   *             pt = fPts[SkPathOpsVerbToPoints(fVerb)];
   *         } else {
   *             SkASSERT(between(0, t, 1));
   *             pt = this->ptAtT(t);
   *             if (SkDPoint::ApproximatelyEqual(pt, base.fPt)) {
   *                 if (base.fSpan->segment() == this) {
   *                     continue;
   *                 }
   *             } else {
   *                 SkScalar ptXY = pt_xy(pt, dir);
   *                 if (!approximately_equal(baseXY, ptXY) && (baseXY < ptXY) == checkLessThan) {
   *                     continue;
   *                 }
   *                 slope = this->dSlopeAtT(t);
   *                 if (fVerb == SkPath::kCubic_Verb && base.fSpan->segment() == this
   *                         && roughly_equal(base.fT, t)
   *                         && SkDPoint::RoughlyEqual(pt, base.fPt)) {
   *     #if DEBUG_WINDING
   *                     SkDebugf("%s (rarely expect this)\n", __FUNCTION__);
   *     #endif
   *                     continue;
   *                 }
   *                 if (fabs(pt_dydx(slope, dir) * 10000) > fabs(pt_dxdy(slope, dir))) {
   *                     valid = true;
   *                 }
   *             }
   *         }
   *         SkOpSpan* span = this->windingSpanAtT(t);
   *         if (!span) {
   *             valid = false;
   *         } else if (!span->windValue() && !span->oppValue()) {
   *             continue;
   *         }
   *         SkOpRayHit* newHit = allocator->make<SkOpRayHit>();
   *         newHit->fNext = *hits;
   *         newHit->fPt = pt;
   *         newHit->fSlope = slope;
   *         newHit->fSpan = span;
   *         newHit->fT = t;
   *         newHit->fValid = valid;
   *         *hits = newHit;
   *     }
   * }
   * ```
   */
  public fun release(span: SkOpSpan?) {
    TODO("Implement release")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::release(const SkOpSpan* span) {
   *     if (span->done()) {
   *         --fDoneCount;
   *     }
   *     --fCount;
   *     SkOPASSERT(fCount >= fDoneCount);
   * }
   * ```
   */
  public fun resetVisited() {
    TODO("Implement resetVisited")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetVisited() {
   *         fVisited = false;
   *     }
   * ```
   */
  public fun setContour(contour: SkOpContour?) {
    TODO("Implement setContour")
  }

  /**
   * C++ original:
   * ```cpp
   * void setContour(SkOpContour* contour) {
   *         fContour = contour;
   *     }
   * ```
   */
  public fun setNext(next: SkOpSegment?) {
    TODO("Implement setNext")
  }

  /**
   * C++ original:
   * ```cpp
   * void setNext(SkOpSegment* next) {
   *         fNext = next;
   *     }
   * ```
   */
  public fun setPrev(prev: SkOpSegment?) {
    TODO("Implement setPrev")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPrev(SkOpSegment* prev) {
   *         fPrev = prev;
   *     }
   * ```
   */
  public fun setUpWinding(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    maxWinding: Int?,
    sumWinding: Int?,
  ) {
    TODO("Implement setUpWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * void setUpWinding(SkOpSpanBase* start, SkOpSpanBase* end, int* maxWinding, int* sumWinding) {
   *         int deltaSum = SpanSign(start, end);
   *         *maxWinding = *sumWinding;
   *         if (*sumWinding == SK_MinS32) {
   *           return;
   *         }
   *         *sumWinding -= deltaSum;
   *     }
   * ```
   */
  public fun setUpWindings(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    sumMiWinding: Int?,
    maxWinding: Int?,
    sumWinding: Int?,
  ) {
    TODO("Implement setUpWindings")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::setUpWindings(SkOpSpanBase* start, SkOpSpanBase* end, int* sumMiWinding,
   *         int* maxWinding, int* sumWinding) {
   *     int deltaSum = SpanSign(start, end);
   *     *maxWinding = *sumMiWinding;
   *     *sumWinding = *sumMiWinding -= deltaSum;
   *     SkASSERT(!DEBUG_LIMIT_WIND_SUM || SkTAbs(*sumWinding) <= DEBUG_LIMIT_WIND_SUM);
   * }
   * ```
   */
  public fun setUpWindings(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    sumMiWinding: Int?,
    sumSuWinding: Int?,
    maxWinding: Int?,
    sumWinding: Int?,
    oppMaxWinding: Int?,
    oppSumWinding: Int?,
  ) {
    TODO("Implement setUpWindings")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpSegment::setUpWindings(SkOpSpanBase* start, SkOpSpanBase* end, int* sumMiWinding,
   *         int* sumSuWinding, int* maxWinding, int* sumWinding, int* oppMaxWinding,
   *         int* oppSumWinding) {
   *     int deltaSum = SpanSign(start, end);
   *     int oppDeltaSum = OppSign(start, end);
   *     if (operand()) {
   *         *maxWinding = *sumSuWinding;
   *         *sumWinding = *sumSuWinding -= deltaSum;
   *         *oppMaxWinding = *sumMiWinding;
   *         *oppSumWinding = *sumMiWinding -= oppDeltaSum;
   *     } else {
   *         *maxWinding = *sumMiWinding;
   *         *sumWinding = *sumMiWinding -= deltaSum;
   *         *oppMaxWinding = *sumSuWinding;
   *         *oppSumWinding = *sumSuWinding -= oppDeltaSum;
   *     }
   *     SkASSERT(!DEBUG_LIMIT_WIND_SUM || SkTAbs(*sumWinding) <= DEBUG_LIMIT_WIND_SUM);
   *     SkASSERT(!DEBUG_LIMIT_WIND_SUM || SkTAbs(*oppSumWinding) <= DEBUG_LIMIT_WIND_SUM);
   * }
   * ```
   */
  public fun sortAngles(): Boolean {
    TODO("Implement sortAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::sortAngles() {
   *     SkOpSpanBase* span = &this->fHead;
   *     do {
   *         SkOpAngle* fromAngle = span->fromAngle();
   *         SkOpAngle* toAngle = span->final() ? nullptr : span->upCast()->toAngle();
   *         if (!fromAngle && !toAngle) {
   *             continue;
   *         }
   * #if DEBUG_ANGLE
   *         bool wroteAfterHeader = false;
   * #endif
   *         SkOpAngle* baseAngle = fromAngle;
   *         if (fromAngle && toAngle) {
   * #if DEBUG_ANGLE
   *             SkDebugf("%s [%d] tStart=%1.9g [%d]\n", __FUNCTION__, debugID(), span->t(),
   *                     span->debugID());
   *             wroteAfterHeader = true;
   * #endif
   *             FAIL_IF(!fromAngle->insert(toAngle));
   *         } else if (!fromAngle) {
   *             baseAngle = toAngle;
   *         }
   *         SkOpPtT* ptT = span->ptT(), * stopPtT = ptT;
   *         int safetyNet = 1000;
   *         do {
   *             if (!--safetyNet) {
   *                 return false;
   *             }
   *             SkOpSpanBase* oSpan = ptT->span();
   *             if (oSpan == span) {
   *                 continue;
   *             }
   *             SkOpAngle* oAngle = oSpan->fromAngle();
   *             if (oAngle) {
   * #if DEBUG_ANGLE
   *                 if (!wroteAfterHeader) {
   *                     SkDebugf("%s [%d] tStart=%1.9g [%d]\n", __FUNCTION__, debugID(),
   *                             span->t(), span->debugID());
   *                     wroteAfterHeader = true;
   *                 }
   * #endif
   *                 if (!oAngle->loopContains(baseAngle)) {
   *                     baseAngle->insert(oAngle);
   *                 }
   *             }
   *             if (!oSpan->final()) {
   *                 oAngle = oSpan->upCast()->toAngle();
   *                 if (oAngle) {
   * #if DEBUG_ANGLE
   *                     if (!wroteAfterHeader) {
   *                         SkDebugf("%s [%d] tStart=%1.9g [%d]\n", __FUNCTION__, debugID(),
   *                                 span->t(), span->debugID());
   *                         wroteAfterHeader = true;
   *                     }
   * #endif
   *                     if (!oAngle->loopContains(baseAngle)) {
   *                         baseAngle->insert(oAngle);
   *                     }
   *                 }
   *             }
   *         } while ((ptT = ptT->next()) != stopPtT);
   *         if (baseAngle->loopCount() == 1) {
   *             span->setFromAngle(nullptr);
   *             if (toAngle) {
   *                 span->upCast()->setToAngle(nullptr);
   *             }
   *             baseAngle = nullptr;
   *         }
   * #if DEBUG_SORT
   *         SkASSERT(!baseAngle || baseAngle->loopCount() > 1);
   * #endif
   *     } while (!span->final() && (span = span->upCast()->next()));
   *     return true;
   * }
   * ```
   */
  public fun spansNearby(
    ref: SkOpSpanBase?,
    check: SkOpSpanBase?,
    found: Boolean?,
  ): Boolean {
    TODO("Implement spansNearby")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::spansNearby(const SkOpSpanBase* refSpan, const SkOpSpanBase* checkSpan,
   *         bool* found) const {
   *     const SkOpPtT* refHead = refSpan->ptT();
   *     const SkOpPtT* checkHead = checkSpan->ptT();
   * // if the first pt pair from adjacent spans are far apart, assume that all are far enough apart
   *     if (!SkDPoint::WayRoughlyEqual(refHead->fPt, checkHead->fPt)) {
   * #if DEBUG_COINCIDENCE
   *         // verify that no combination of points are close
   *         const SkOpPtT* dBugRef = refHead;
   *         do {
   *             const SkOpPtT* dBugCheck = checkHead;
   *             do {
   *                 SkOPASSERT(!SkDPoint::ApproximatelyEqual(dBugRef->fPt, dBugCheck->fPt));
   *                 dBugCheck = dBugCheck->next();
   *             } while (dBugCheck != checkHead);
   *             dBugRef = dBugRef->next();
   *         } while (dBugRef != refHead);
   * #endif
   *         *found = false;
   *         return true;
   *     }
   *     // check only unique points
   *     SkScalar distSqBest = SK_ScalarMax;
   *     const SkOpPtT* refBest = nullptr;
   *     const SkOpPtT* checkBest = nullptr;
   *     const SkOpPtT* ref = refHead;
   *     do {
   *         if (ref->deleted()) {
   *             continue;
   *         }
   *         while (ref->ptAlreadySeen(refHead)) {
   *             ref = ref->next();
   *             if (ref == refHead) {
   *                 goto doneCheckingDistance;
   *             }
   *         }
   *         const SkOpPtT* check = checkHead;
   *         const SkOpSegment* refSeg = ref->segment();
   *         int escapeHatch = 100;  // defend against infinite loops
   *         do {
   *             if (check->deleted()) {
   *                 continue;
   *             }
   *             while (check->ptAlreadySeen(checkHead)) {
   *                 check = check->next();
   *                 if (check == checkHead) {
   *                     goto nextRef;
   *                 }
   *             }
   *             SkScalar distSq = SkPointPriv::DistanceToSqd(ref->fPt, check->fPt);
   *             if (distSqBest > distSq && (refSeg != check->segment()
   *                     || !refSeg->ptsDisjoint(*ref, *check))) {
   *                 distSqBest = distSq;
   *                 refBest = ref;
   *                 checkBest = check;
   *             }
   *             if (--escapeHatch <= 0) {
   *                 return false;
   *             }
   *         } while ((check = check->next()) != checkHead);
   *     nextRef:
   *         ;
   *    } while ((ref = ref->next()) != refHead);
   * doneCheckingDistance:
   *     *found = checkBest && refBest->segment()->match(refBest, checkBest->segment(), checkBest->fT,
   *             checkBest->fPt);
   *     return true;
   * }
   * ```
   */
  public fun spanToAngle(start: SkOpSpanBase?, end: SkOpSpanBase?): SkOpAngle {
    TODO("Implement spanToAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpAngle* spanToAngle(SkOpSpanBase* start, SkOpSpanBase* end) {
   *         SkASSERT(start != end);
   *         return start->t() < end->t() ? start->upCast()->toAngle() : start->fromAngle();
   *     }
   * ```
   */
  public fun subDivide(
    start: SkOpSpanBase?,
    end: SkOpSpanBase?,
    result: SkDCurve?,
  ): Boolean {
    TODO("Implement subDivide")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::subDivide(const SkOpSpanBase* start, const SkOpSpanBase* end,
   *         SkDCurve* edge) const {
   *     SkASSERT(start != end);
   *     const SkOpPtT& startPtT = *start->ptT();
   *     const SkOpPtT& endPtT = *end->ptT();
   *     SkDEBUGCODE(edge->fVerb = fVerb);
   *     edge->fCubic[0].set(startPtT.fPt);
   *     int points = SkPathOpsVerbToPoints(fVerb);
   *     edge->fCubic[points].set(endPtT.fPt);
   *     if (fVerb == SkPath::kLine_Verb) {
   *         return false;
   *     }
   *     double startT = startPtT.fT;
   *     double endT = endPtT.fT;
   *     if ((startT == 0 || endT == 0) && (startT == 1 || endT == 1)) {
   *         // don't compute midpoints if we already have them
   *         if (fVerb == SkPath::kQuad_Verb) {
   *             edge->fLine[1].set(fPts[1]);
   *             return false;
   *         }
   *         if (fVerb == SkPath::kConic_Verb) {
   *             edge->fConic[1].set(fPts[1]);
   *             edge->fConic.fWeight = fWeight;
   *             return false;
   *         }
   *         SkASSERT(fVerb == SkPath::kCubic_Verb);
   *         if (startT == 0) {
   *             edge->fCubic[1].set(fPts[1]);
   *             edge->fCubic[2].set(fPts[2]);
   *             return false;
   *         }
   *         edge->fCubic[1].set(fPts[2]);
   *         edge->fCubic[2].set(fPts[1]);
   *         return false;
   *     }
   *     if (fVerb == SkPath::kQuad_Verb) {
   *         edge->fQuad[1] = SkDQuad::SubDivide(fPts, edge->fQuad[0], edge->fQuad[2], startT, endT);
   *     } else if (fVerb == SkPath::kConic_Verb) {
   *         edge->fConic[1] = SkDConic::SubDivide(fPts, fWeight, edge->fQuad[0], edge->fQuad[2],
   *             startT, endT, &edge->fConic.fWeight);
   *     } else {
   *         SkASSERT(fVerb == SkPath::kCubic_Verb);
   *         SkDCubic::SubDivide(fPts, edge->fCubic[0], edge->fCubic[3], startT, endT, &edge->fCubic[1]);
   *     }
   *     return true;
   * }
   * ```
   */
  public fun tail(): SkOpSpanBase {
    TODO("Implement tail")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* tail() const {
   *         return &fTail;
   *     }
   * ```
   */
  public fun testForCoincidence(
    priorPtT: SkOpPtT?,
    ptT: SkOpPtT?,
    prior: SkOpSpanBase?,
    spanBase: SkOpSpanBase?,
    opp: SkOpSegment?,
  ): Boolean {
    TODO("Implement testForCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpanBase* tail() {
   *         return &fTail;
   *     }
   * ```
   */
  public fun undoneSpan(): SkOpSpan {
    TODO("Implement undoneSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkOpSegment::testForCoincidence(const SkOpPtT* priorPtT, const SkOpPtT* ptT,
   *         const SkOpSpanBase* prior, const SkOpSpanBase* spanBase, const SkOpSegment* opp) const {
   *     // average t, find mid pt
   *     double midT = (prior->t() + spanBase->t()) / 2;
   *     SkPoint midPt = this->ptAtT(midT);
   *     bool coincident = true;
   *     // if the mid pt is not near either end pt, project perpendicular through opp seg
   *     if (!SkDPoint::ApproximatelyEqual(priorPtT->fPt, midPt)
   *             && !SkDPoint::ApproximatelyEqual(ptT->fPt, midPt)) {
   *         if (priorPtT->span() == ptT->span()) {
   *           return false;
   *         }
   *         coincident = false;
   *         SkIntersections i;
   *         SkDCurve curvePart;
   *         this->subDivide(prior, spanBase, &curvePart);
   *         SkDVector dxdy = (*CurveDDSlopeAtT[fVerb])(curvePart, 0.5f);
   *         SkDPoint partMidPt = (*CurveDDPointAtT[fVerb])(curvePart, 0.5f);
   *         SkDLine ray = {{{midPt.fX, midPt.fY}, {partMidPt.fX + dxdy.fY, partMidPt.fY - dxdy.fX}}};
   *         SkDCurve oppPart;
   *         opp->subDivide(priorPtT->span(), ptT->span(), &oppPart);
   *         (*CurveDIntersectRay[opp->verb()])(oppPart, ray, &i);
   *         // measure distance and see if it's small enough to denote coincidence
   *         for (int index = 0; index < i.used(); ++index) {
   *             if (!between(0, i[0][index], 1)) {
   *                 continue;
   *             }
   *             SkDPoint oppPt = i.pt(index);
   *             if (oppPt.approximatelyDEqual(midPt)) {
   *                 // the coincidence can occur at almost any angle
   *                 coincident = true;
   *             }
   *         }
   *     }
   *     return coincident;
   * }
   * ```
   */
  public fun updateOppWinding(start: SkOpSpanBase?, end: SkOpSpanBase?): Int {
    TODO("Implement updateOppWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* SkOpSegment::undoneSpan() {
   *     SkOpSpan* span = &fHead;
   *     SkOpSpanBase* next;
   *     do {
   *         next = span->next();
   *         if (!span->done()) {
   *             return span;
   *         }
   *     } while (!next->final() && (span = next->upCast()));
   *     return nullptr;
   * }
   * ```
   */
  public fun updateOppWinding(angle: SkOpAngle?): Int {
    TODO("Implement updateOppWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateOppWinding(const SkOpSpanBase* start, const SkOpSpanBase* end) const {
   *     const SkOpSpan* lesser = start->starter(end);
   *     int oppWinding = lesser->oppSum();
   *     int oppSpanWinding = SkOpSegment::OppSign(start, end);
   *     if (oppSpanWinding && UseInnerWinding(oppWinding - oppSpanWinding, oppWinding)
   *             && oppWinding != SK_MaxS32) {
   *         oppWinding -= oppSpanWinding;
   *     }
   *     return oppWinding;
   * }
   * ```
   */
  public fun updateOppWindingReverse(angle: SkOpAngle?): Int {
    TODO("Implement updateOppWindingReverse")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateOppWinding(const SkOpAngle* angle) const {
   *     const SkOpSpanBase* startSpan = angle->start();
   *     const SkOpSpanBase* endSpan = angle->end();
   *     return updateOppWinding(endSpan, startSpan);
   * }
   * ```
   */
  public fun updateWinding(start: SkOpSpanBase?, end: SkOpSpanBase?): Int {
    TODO("Implement updateWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateOppWindingReverse(const SkOpAngle* angle) const {
   *     const SkOpSpanBase* startSpan = angle->start();
   *     const SkOpSpanBase* endSpan = angle->end();
   *     return updateOppWinding(startSpan, endSpan);
   * }
   * ```
   */
  public fun updateWinding(angle: SkOpAngle?): Int {
    TODO("Implement updateWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateWinding(SkOpSpanBase* start, SkOpSpanBase* end) {
   *     SkOpSpan* lesser = start->starter(end);
   *     int winding = lesser->windSum();
   *     if (winding == SK_MinS32) {
   *         winding = lesser->computeWindSum();
   *     }
   *     if (winding == SK_MinS32) {
   *         return winding;
   *     }
   *     int spanWinding = SkOpSegment::SpanSign(start, end);
   *     if (winding && UseInnerWinding(winding - spanWinding, winding)
   *             && winding != SK_MaxS32) {
   *         winding -= spanWinding;
   *     }
   *     return winding;
   * }
   * ```
   */
  public fun updateWindingReverse(angle: SkOpAngle?): Int {
    TODO("Implement updateWindingReverse")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateWinding(SkOpAngle* angle) {
   *     SkOpSpanBase* startSpan = angle->start();
   *     SkOpSpanBase* endSpan = angle->end();
   *     return updateWinding(endSpan, startSpan);
   * }
   * ```
   */
  public fun verb(): SkPathVerb {
    TODO("Implement verb")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkOpSegment::updateWindingReverse(const SkOpAngle* angle) {
   *     SkOpSpanBase* startSpan = angle->start();
   *     SkOpSpanBase* endSpan = angle->end();
   *     return updateWinding(startSpan, endSpan);
   * }
   * ```
   */
  public fun visited(): Boolean {
    TODO("Implement visited")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath::Verb verb() const {
   *         return fVerb;
   *     }
   * ```
   */
  public fun weight(): SkScalar {
    TODO("Implement weight")
  }

  /**
   * C++ original:
   * ```cpp
   * bool visited() {
   *         if (!fVisited) {
   *             fVisited = true;
   *             return false;
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun windingSpanAtT(tHit: Double): SkOpSpan {
    TODO("Implement windingSpanAtT")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar weight() const {
   *         return fWeight;
   *     }
   * ```
   */
  public fun windSum(angle: SkOpAngle?): Int {
    TODO("Implement windSum")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkOpSegment::ComputeOneSum(const SkOpAngle* baseAngle, SkOpAngle* nextAngle,
     *         SkOpAngle::IncludeType includeType) {
     *     SkOpSegment* baseSegment = baseAngle->segment();
     *     int sumMiWinding = baseSegment->updateWindingReverse(baseAngle);
     *     int sumSuWinding;
     *     bool binary = includeType >= SkOpAngle::kBinarySingle;
     *     if (binary) {
     *         sumSuWinding = baseSegment->updateOppWindingReverse(baseAngle);
     *         if (baseSegment->operand()) {
     *             using std::swap;
     *             swap(sumMiWinding, sumSuWinding);
     *         }
     *     }
     *     SkOpSegment* nextSegment = nextAngle->segment();
     *     int maxWinding, sumWinding;
     *     SkOpSpanBase* last = nullptr;
     *     if (binary) {
     *         int oppMaxWinding, oppSumWinding;
     *         nextSegment->setUpWindings(nextAngle->start(), nextAngle->end(), &sumMiWinding,
     *                 &sumSuWinding, &maxWinding, &sumWinding, &oppMaxWinding, &oppSumWinding);
     *         if (!nextSegment->markAngle(maxWinding, sumWinding, oppMaxWinding, oppSumWinding,
     *                 nextAngle, &last)) {
     *             return false;
     *         }
     *     } else {
     *         nextSegment->setUpWindings(nextAngle->start(), nextAngle->end(), &sumMiWinding,
     *                 &maxWinding, &sumWinding);
     *         if (!nextSegment->markAngle(maxWinding, sumWinding, nextAngle, &last)) {
     *             return false;
     *         }
     *     }
     *     nextAngle->setLastMarked(last);
     *     return true;
     * }
     * ```
     */
    public fun computeOneSum(
      baseAngle: SkOpAngle?,
      nextAngle: SkOpAngle?,
      includeType: SkOpAngle.IncludeType,
    ): Boolean {
      TODO("Implement computeOneSum")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkOpSegment::ComputeOneSumReverse(SkOpAngle* baseAngle, SkOpAngle* nextAngle,
     *         SkOpAngle::IncludeType includeType) {
     *     SkOpSegment* baseSegment = baseAngle->segment();
     *     int sumMiWinding = baseSegment->updateWinding(baseAngle);
     *     int sumSuWinding;
     *     bool binary = includeType >= SkOpAngle::kBinarySingle;
     *     if (binary) {
     *         sumSuWinding = baseSegment->updateOppWinding(baseAngle);
     *         if (baseSegment->operand()) {
     *             using std::swap;
     *             swap(sumMiWinding, sumSuWinding);
     *         }
     *     }
     *     SkOpSegment* nextSegment = nextAngle->segment();
     *     int maxWinding, sumWinding;
     *     SkOpSpanBase* last = nullptr;
     *     if (binary) {
     *         int oppMaxWinding, oppSumWinding;
     *         nextSegment->setUpWindings(nextAngle->end(), nextAngle->start(), &sumMiWinding,
     *                 &sumSuWinding, &maxWinding, &sumWinding, &oppMaxWinding, &oppSumWinding);
     *         if (!nextSegment->markAngle(maxWinding, sumWinding, oppMaxWinding, oppSumWinding,
     *                 nextAngle, &last)) {
     *             return false;
     *         }
     *     } else {
     *         nextSegment->setUpWindings(nextAngle->end(), nextAngle->start(), &sumMiWinding,
     *                 &maxWinding, &sumWinding);
     *         if (!nextSegment->markAngle(maxWinding, sumWinding, nextAngle, &last)) {
     *             return false;
     *         }
     *     }
     *     nextAngle->setLastMarked(last);
     *     return true;
     * }
     * ```
     */
    public fun computeOneSumReverse(
      baseAngle: SkOpAngle?,
      nextAngle: SkOpAngle?,
      includeType: SkOpAngle.IncludeType,
    ): Boolean {
      TODO("Implement computeOneSumReverse")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkOpSegment::ClearVisited(SkOpSpanBase* span) {
     *     // reset visited flag back to false
     *     do {
     *         SkOpPtT* ptT = span->ptT(), * stopPtT = ptT;
     *         while ((ptT = ptT->next()) != stopPtT) {
     *             SkOpSegment* opp = ptT->segment();
     *             opp->resetVisited();
     *         }
     *     } while (!span->final() && (span = span->upCast()->next()));
     * }
     * ```
     */
    public fun clearVisited(span: SkOpSpanBase?) {
      TODO("Implement clearVisited")
    }

    /**
     * C++ original:
     * ```cpp
     * static int OppSign(const SkOpSpanBase* start, const SkOpSpanBase* end) {
     *         int result = start->t() < end->t() ? -start->upCast()->oppValue()
     *                 : end->upCast()->oppValue();
     *         return result;
     *     }
     * ```
     */
    public fun oppSign(start: SkOpSpanBase?, end: SkOpSpanBase?): Int {
      TODO("Implement oppSign")
    }

    /**
     * C++ original:
     * ```cpp
     * static int SpanSign(const SkOpSpanBase* start, const SkOpSpanBase* end) {
     *         int result = start->t() < end->t() ? -start->upCast()->windValue()
     *                 : end->upCast()->windValue();
     *         return result;
     *     }
     * ```
     */
    public fun spanSign(start: SkOpSpanBase?, end: SkOpSpanBase?): Int {
      TODO("Implement spanSign")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkOpSegment::UseInnerWinding(int outerWinding, int innerWinding) {
     *     SkASSERT(outerWinding != SK_MaxS32);
     *     SkASSERT(innerWinding != SK_MaxS32);
     *     int absOut = SkTAbs(outerWinding);
     *     int absIn = SkTAbs(innerWinding);
     *     bool result = absOut == absIn ? outerWinding < 0 : absOut < absIn;
     *     return result;
     * }
     * ```
     */
    public fun useInnerWinding(outerWinding: Int, innerWinding: Int): Boolean {
      TODO("Implement useInnerWinding")
    }
  }
}
