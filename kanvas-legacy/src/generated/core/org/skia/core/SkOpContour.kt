package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkOpContour {
 * public:
 *     SkOpContour() {
 *         reset();
 *     }
 *
 *     bool operator<(const SkOpContour& rh) const {
 *         return fBounds.fTop == rh.fBounds.fTop
 *             ? fBounds.fLeft < rh.fBounds.fLeft
 *             : fBounds.fTop < rh.fBounds.fTop;
 *     }
 *
 *     void addConic(SkPoint pts[3], SkScalar weight) {
 *         appendSegment().addConic(pts, weight, this);
 *     }
 *
 *     void addCubic(SkPoint pts[4]) {
 *         appendSegment().addCubic(pts, this);
 *     }
 *
 *     SkOpSegment* addLine(SkPoint pts[2]) {
 *         SkASSERT(pts[0] != pts[1]);
 *         return appendSegment().addLine(pts, this);
 *     }
 *
 *     void addQuad(SkPoint pts[3]) {
 *         appendSegment().addQuad(pts, this);
 *     }
 *
 *     SkOpSegment& appendSegment() {
 *         SkOpSegment* result = fCount++ ? this->globalState()->allocator()->make<SkOpSegment>()
 *                                        : &fHead;
 *         result->setPrev(fTail);
 *         if (fTail) {
 *             fTail->setNext(result);
 *         }
 *         fTail = result;
 *         return *result;
 *     }
 *
 *     const SkPathOpsBounds& bounds() const {
 *         return fBounds;
 *     }
 *
 *     void calcAngles() {
 *         SkASSERT(fCount > 0);
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             segment->calcAngles();
 *         } while ((segment = segment->next()));
 *     }
 *
 *     void complete() {
 *         setBounds();
 *     }
 *
 *     int count() const {
 *         return fCount;
 *     }
 *
 *     int debugID() const {
 *         return SkDEBUGRELEASE(fID, -1);
 *     }
 *
 *     int debugIndent() const {
 *         return SkDEBUGRELEASE(fDebugIndent, 0);
 *     }
 *
 *     const SkOpAngle* debugAngle(int id) const {
 *         return SkDEBUGRELEASE(this->globalState()->debugAngle(id), nullptr);
 *     }
 *
 *     const SkOpCoincidence* debugCoincidence() const {
 *         return this->globalState()->coincidence();
 *     }
 *
 * #if DEBUG_COIN
 *     void debugCheckHealth(SkPathOpsDebug::GlitchLog* ) const;
 * #endif
 *
 *     SkOpContour* debugContour(int id) const {
 *         return SkDEBUGRELEASE(this->globalState()->debugContour(id), nullptr);
 *     }
 *
 * #if DEBUG_COIN
 *     void debugMissingCoincidence(SkPathOpsDebug::GlitchLog* log) const;
 *     void debugMoveMultiples(SkPathOpsDebug::GlitchLog* ) const;
 *     void debugMoveNearby(SkPathOpsDebug::GlitchLog* log) const;
 * #endif
 *
 *     const SkOpPtT* debugPtT(int id) const {
 *         return SkDEBUGRELEASE(this->globalState()->debugPtT(id), nullptr);
 *     }
 *
 *     const SkOpSegment* debugSegment(int id) const {
 *         return SkDEBUGRELEASE(this->globalState()->debugSegment(id), nullptr);
 *     }
 *
 * #if DEBUG_ACTIVE_SPANS
 *     void debugShowActiveSpans(SkString* str) {
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             segment->debugShowActiveSpans(str);
 *         } while ((segment = segment->next()));
 *     }
 * #endif
 *
 *     const SkOpSpanBase* debugSpan(int id) const {
 *         return SkDEBUGRELEASE(this->globalState()->debugSpan(id), nullptr);
 *     }
 *
 *     SkOpGlobalState* globalState() const {
 *         return fState;
 *     }
 *
 *     void debugValidate() const {
 * #if DEBUG_VALIDATE
 *         const SkOpSegment* segment = &fHead;
 *         const SkOpSegment* prior = nullptr;
 *         do {
 *             segment->debugValidate();
 *             SkASSERT(segment->prev() == prior);
 *             prior = segment;
 *         } while ((segment = segment->next()));
 *         SkASSERT(prior == fTail);
 * #endif
 *     }
 *
 *     bool done() const {
 *         return fDone;
 *     }
 *
 *     void dump() const;
 *     void dumpAll() const;
 *     void dumpAngles() const;
 *     void dumpContours() const;
 *     void dumpContoursAll() const;
 *     void dumpContoursAngles() const;
 *     void dumpContoursPts() const;
 *     void dumpContoursPt(int segmentID) const;
 *     void dumpContoursSegment(int segmentID) const;
 *     void dumpContoursSpan(int segmentID) const;
 *     void dumpContoursSpans() const;
 *     void dumpPt(int ) const;
 *     void dumpPts(const char* prefix = "seg") const;
 *     void dumpPtsX(const char* prefix) const;
 *     void dumpSegment(int ) const;
 *     void dumpSegments(const char* prefix = "seg", SkPathOp op = (SkPathOp) -1) const;
 *     void dumpSpan(int ) const;
 *     void dumpSpans() const;
 *
 *     const SkPoint& end() const {
 *         return fTail->pts()[SkPathOpsVerbToPoints(fTail->verb())];
 *     }
 *
 *     SkOpSpan* findSortableTop(SkOpContour* );
 *
 *     SkOpSegment* first() {
 *         SkASSERT(fCount > 0);
 *         return &fHead;
 *     }
 *
 *     const SkOpSegment* first() const {
 *         SkASSERT(fCount > 0);
 *         return &fHead;
 *     }
 *
 *     void indentDump() const {
 *         SkDEBUGCODE(fDebugIndent += 2);
 *     }
 *
 *     void init(SkOpGlobalState* globalState, bool operand, bool isXor) {
 *         fState = globalState;
 *         fOperand = operand;
 *         fXor = isXor;
 *         SkDEBUGCODE(fID = globalState->nextContourID());
 *     }
 *
 *     int isCcw() const {
 *         return fCcw;
 *     }
 *
 *     bool isXor() const {
 *         return fXor;
 *     }
 *
 *     void joinSegments() {
 *         SkOpSegment* segment = &fHead;
 *         SkOpSegment* next;
 *         do {
 *             next = segment->next();
 *             segment->joinEnds(next ? next : &fHead);
 *         } while ((segment = next));
 *     }
 *
 *     void markAllDone() {
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             segment->markAllDone();
 *         } while ((segment = segment->next()));
 *     }
 *
 *     // Please keep this aligned with debugMissingCoincidence()
 *     bool missingCoincidence() {
 *         SkASSERT(fCount > 0);
 *         SkOpSegment* segment = &fHead;
 *         bool result = false;
 *         do {
 *             if (segment->missingCoincidence()) {
 *                 result = true;
 *             }
 *             segment = segment->next();
 *         } while (segment);
 *         return result;
 *     }
 *
 *     bool moveMultiples() {
 *         SkASSERT(fCount > 0);
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             if (!segment->moveMultiples()) {
 *                 return false;
 *             }
 *         } while ((segment = segment->next()));
 *         return true;
 *     }
 *
 *     bool moveNearby() {
 *         SkASSERT(fCount > 0);
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             if (!segment->moveNearby()) {
 *                 return false;
 *             }
 *         } while ((segment = segment->next()));
 *         return true;
 *     }
 *
 *     SkOpContour* next() {
 *         return fNext;
 *     }
 *
 *     const SkOpContour* next() const {
 *         return fNext;
 *     }
 *
 *     bool operand() const {
 *         return fOperand;
 *     }
 *
 *     bool oppXor() const {
 *         return fOppXor;
 *     }
 *
 *     void outdentDump() const {
 *         SkDEBUGCODE(fDebugIndent -= 2);
 *     }
 *
 *     void rayCheck(const SkOpRayHit& base, SkOpRayDir dir, SkOpRayHit** hits, SkArenaAlloc*);
 *
 *     void reset() {
 *         fTail = nullptr;
 *         fNext = nullptr;
 *         fCount = 0;
 *         fDone = false;
 *         SkDEBUGCODE(fBounds.setLTRB(SK_ScalarMax, SK_ScalarMax, SK_ScalarMin, SK_ScalarMin));
 *         SkDEBUGCODE(fFirstSorted = -1);
 *         SkDEBUGCODE(fDebugIndent = 0);
 *     }
 *
 *     void resetReverse() {
 *         SkOpContour* next = this;
 *         do {
 *             if (!next->count()) {
 *                 continue;
 *             }
 *             next->fCcw = -1;
 *             next->fReverse = false;
 *         } while ((next = next->next()));
 *     }
 *
 *     bool reversed() const {
 *         return fReverse;
 *     }
 *
 *     void setBounds() {
 *         SkASSERT(fCount > 0);
 *         const SkOpSegment* segment = &fHead;
 *         fBounds = segment->bounds();
 *         while ((segment = segment->next())) {
 *             fBounds.add(segment->bounds());
 *         }
 *     }
 *
 *     void setCcw(int ccw) {
 *         fCcw = ccw;
 *     }
 *
 *     void setGlobalState(SkOpGlobalState* state) {
 *         fState = state;
 *     }
 *
 *     void setNext(SkOpContour* contour) {
 * //        SkASSERT(!fNext == !!contour);
 *         fNext = contour;
 *     }
 *
 *     void setOperand(bool isOp) {
 *         fOperand = isOp;
 *     }
 *
 *     void setOppXor(bool isOppXor) {
 *         fOppXor = isOppXor;
 *     }
 *
 *     void setReverse() {
 *         fReverse = true;
 *     }
 *
 *     void setXor(bool isXor) {
 *         fXor = isXor;
 *     }
 *
 *     bool sortAngles() {
 *         SkASSERT(fCount > 0);
 *         SkOpSegment* segment = &fHead;
 *         do {
 *             FAIL_IF(!segment->sortAngles());
 *         } while ((segment = segment->next()));
 *         return true;
 *     }
 *
 *     const SkPoint& start() const {
 *         return fHead.pts()[0];
 *     }
 *
 *     void toPartialBackward(SkPathWriter* path) const {
 *         const SkOpSegment* segment = fTail;
 *         do {
 *             SkAssertResult(segment->addCurveTo(segment->tail(), segment->head(), path));
 *         } while ((segment = segment->prev()));
 *     }
 *
 *     void toPartialForward(SkPathWriter* path) const {
 *         const SkOpSegment* segment = &fHead;
 *         do {
 *             SkAssertResult(segment->addCurveTo(segment->head(), segment->tail(), path));
 *         } while ((segment = segment->next()));
 *     }
 *
 *     void toReversePath(SkPathWriter* path) const;
 *     void toPath(SkPathWriter* path) const;
 *     SkOpSpan* undoneSpan();
 *
 * protected:
 *     SkOpGlobalState* fState;
 *     SkOpSegment fHead;
 *     SkOpSegment* fTail;
 *     SkOpContour* fNext;
 *     SkPathOpsBounds fBounds;
 *     int fCcw;
 *     int fCount;
 *     int fFirstSorted;
 *     bool fDone;  // set by find top segment
 *     bool fOperand;  // true for the second argument to a binary operator
 *     bool fReverse;  // true if contour should be reverse written to path (used only by fix winding)
 *     bool fXor;  // set if original path had even-odd fill
 *     bool fOppXor;  // set if opposite path had even-odd fill
 *     SkDEBUGCODE(int fID;)
 *     SkDEBUGCODE(mutable int fDebugIndent;)
 * }
 * ```
 */
public open class SkOpContour public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* fState
   * ```
   */
  protected var fState: SkOpGlobalState? = TODO("Initialize fState")

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment fHead
   * ```
   */
  protected var fHead: SkOpSegment = TODO("Initialize fHead")

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* fTail
   * ```
   */
  protected var fTail: SkOpSegment? = TODO("Initialize fTail")

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* fNext
   * ```
   */
  protected var fNext: SkOpContour? = TODO("Initialize fNext")

  /**
   * C++ original:
   * ```cpp
   * SkPathOpsBounds fBounds
   * ```
   */
  protected var fBounds: SkPathOpsBounds = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * int fCcw
   * ```
   */
  protected var fCcw: Int = TODO("Initialize fCcw")

  /**
   * C++ original:
   * ```cpp
   * int fCount
   * ```
   */
  protected var fCount: Int = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * int fFirstSorted
   * ```
   */
  protected var fFirstSorted: Int = TODO("Initialize fFirstSorted")

  /**
   * C++ original:
   * ```cpp
   * bool fDone
   * ```
   */
  protected var fDone: Boolean = TODO("Initialize fDone")

  /**
   * C++ original:
   * ```cpp
   * bool fOperand
   * ```
   */
  protected var fOperand: Boolean = TODO("Initialize fOperand")

  /**
   * C++ original:
   * ```cpp
   * bool fReverse
   * ```
   */
  protected var fReverse: Boolean = TODO("Initialize fReverse")

  /**
   * C++ original:
   * ```cpp
   * bool fXor
   * ```
   */
  protected var fXor: Boolean = TODO("Initialize fXor")

  /**
   * C++ original:
   * ```cpp
   * bool fOppXor
   * ```
   */
  protected var fOppXor: Boolean = TODO("Initialize fOppXor")

  /**
   * C++ original:
   * ```cpp
   * bool operator<(const SkOpContour& rh) const {
   *         return fBounds.fTop == rh.fBounds.fTop
   *             ? fBounds.fLeft < rh.fBounds.fLeft
   *             : fBounds.fTop < rh.fBounds.fTop;
   *     }
   * ```
   */
  public operator fun compareTo(rh: SkOpContour): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * void addConic(SkPoint pts[3], SkScalar weight) {
   *         appendSegment().addConic(pts, weight, this);
   *     }
   * ```
   */
  public fun addConic(pts: Array<SkPoint>, weight: SkScalar) {
    TODO("Implement addConic")
  }

  /**
   * C++ original:
   * ```cpp
   * void addCubic(SkPoint pts[4]) {
   *         appendSegment().addCubic(pts, this);
   *     }
   * ```
   */
  public fun addCubic(pts: Array<SkPoint>) {
    TODO("Implement addCubic")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment* addLine(SkPoint pts[2]) {
   *         SkASSERT(pts[0] != pts[1]);
   *         return appendSegment().addLine(pts, this);
   *     }
   * ```
   */
  public fun addLine(pts: Array<SkPoint>): SkOpSegment {
    TODO("Implement addLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void addQuad(SkPoint pts[3]) {
   *         appendSegment().addQuad(pts, this);
   *     }
   * ```
   */
  public fun addQuad(pts: Array<SkPoint>) {
    TODO("Implement addQuad")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSegment& appendSegment() {
   *         SkOpSegment* result = fCount++ ? this->globalState()->allocator()->make<SkOpSegment>()
   *                                        : &fHead;
   *         result->setPrev(fTail);
   *         if (fTail) {
   *             fTail->setNext(result);
   *         }
   *         fTail = result;
   *         return *result;
   *     }
   * ```
   */
  public fun appendSegment(): SkOpSegment {
    TODO("Implement appendSegment")
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
   * void calcAngles() {
   *         SkASSERT(fCount > 0);
   *         SkOpSegment* segment = &fHead;
   *         do {
   *             segment->calcAngles();
   *         } while ((segment = segment->next()));
   *     }
   * ```
   */
  public fun calcAngles() {
    TODO("Implement calcAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * void complete() {
   *         setBounds();
   *     }
   * ```
   */
  public fun complete() {
    TODO("Implement complete")
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
   * int debugIndent() const {
   *         return SkDEBUGRELEASE(fDebugIndent, 0);
   *     }
   * ```
   */
  public fun debugIndent(): Int {
    TODO("Implement debugIndent")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpAngle* debugAngle(int id) const {
   *         return SkDEBUGRELEASE(this->globalState()->debugAngle(id), nullptr);
   *     }
   * ```
   */
  public fun debugAngle(id: Int): SkOpAngle {
    TODO("Implement debugAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpCoincidence* debugCoincidence() const {
   *         return this->globalState()->coincidence();
   *     }
   * ```
   */
  public fun debugCoincidence(): SkOpCoincidence {
    TODO("Implement debugCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* debugContour(int id) const {
   *         return SkDEBUGRELEASE(this->globalState()->debugContour(id), nullptr);
   *     }
   * ```
   */
  public fun debugContour(id: Int): SkOpContour {
    TODO("Implement debugContour")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* debugPtT(int id) const {
   *         return SkDEBUGRELEASE(this->globalState()->debugPtT(id), nullptr);
   *     }
   * ```
   */
  public fun debugPtT(id: Int): SkOpPtT {
    TODO("Implement debugPtT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* debugSegment(int id) const {
   *         return SkDEBUGRELEASE(this->globalState()->debugSegment(id), nullptr);
   *     }
   * ```
   */
  public fun debugSegment(id: Int): SkOpSegment {
    TODO("Implement debugSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* debugSpan(int id) const {
   *         return SkDEBUGRELEASE(this->globalState()->debugSpan(id), nullptr);
   *     }
   * ```
   */
  public fun debugSpan(id: Int): SkOpSpanBase {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpGlobalState* globalState() const {
   *         return fState;
   *     }
   * ```
   */
  public fun globalState(): SkOpGlobalState {
    TODO("Implement globalState")
  }

  /**
   * C++ original:
   * ```cpp
   * void debugValidate() const {
   * #if DEBUG_VALIDATE
   *         const SkOpSegment* segment = &fHead;
   *         const SkOpSegment* prior = nullptr;
   *         do {
   *             segment->debugValidate();
   *             SkASSERT(segment->prev() == prior);
   *             prior = segment;
   *         } while ((segment = segment->next()));
   *         SkASSERT(prior == fTail);
   * #endif
   *     }
   * ```
   */
  public fun debugValidate() {
    TODO("Implement debugValidate")
  }

  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         return fDone;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dump() const {
   *     SkDebugf("contour=%d count=%d op=%d xor=%d\n", this->debugID(), fCount, fOperand, fXor);
   *     if (!fCount) {
   *         return;
   *     }
   *     const SkOpSegment* segment = &fHead;
   *     SkDEBUGCODE(fDebugIndent = 0);
   *     this->indentDump();
   *     do {
   *         segment->dump();
   *     } while ((segment = segment->next()));
   *     this->outdentDump();
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpAll() const {
   *     SkDebugf("contour=%d count=%d op=%d xor=%d\n", this->debugID(), fCount, fOperand, fXor);
   *     if (!fCount) {
   *         return;
   *     }
   *     const SkOpSegment* segment = &fHead;
   *     SkDEBUGCODE(fDebugIndent = 0);
   *     this->indentDump();
   *     do {
   *         segment->dumpAll();
   *     } while ((segment = segment->next()));
   *     this->outdentDump();
   * }
   * ```
   */
  public fun dumpAll() {
    TODO("Implement dumpAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpAngles() const {
   *     SkDebugf("contour=%d\n", this->debugID());
   *     const SkOpSegment* segment = &fHead;
   *     do {
   *         SkDebugf("  seg=%d ", segment->debugID());
   *         segment->dumpAngles();
   *     } while ((segment = segment->next()));
   * }
   * ```
   */
  public fun dumpAngles() {
    TODO("Implement dumpAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContours() const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dump();
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContours() {
    TODO("Implement dumpContours")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursAll() const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpAll();
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursAll() {
    TODO("Implement dumpContoursAll")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursAngles() const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpAngles();
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursAngles() {
    TODO("Implement dumpContoursAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursPts() const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpPts();
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursPts() {
    TODO("Implement dumpContoursPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursPt(int segmentID) const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpPt(segmentID);
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursPt(segmentID: Int) {
    TODO("Implement dumpContoursPt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursSegment(int segmentID) const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpSegment(segmentID);
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursSegment(segmentID: Int) {
    TODO("Implement dumpContoursSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursSpan(int spanID) const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpSpan(spanID);
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursSpan(segmentID: Int) {
    TODO("Implement dumpContoursSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpContoursSpans() const {
   *     SkOpContour* contour = this->globalState()->contourHead();
   *     do {
   *         contour->dumpSpans();
   *     } while ((contour = contour->next()));
   * }
   * ```
   */
  public fun dumpContoursSpans() {
    TODO("Implement dumpContoursSpans")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpPt(int index) const {
   *     const SkOpSegment* segment = &fHead;
   *     do {
   *         if (segment->debugID() == index) {
   *             segment->dumpPts();
   *         }
   *     } while ((segment = segment->next()));
   * }
   * ```
   */
  public fun dumpPt(index: Int) {
    TODO("Implement dumpPt")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpPts(const char* prefix) const {
   *     SkDebugf("contour=%d\n", this->debugID());
   *     const SkOpSegment* segment = &fHead;
   *     do {
   *         SkDebugf("  %s=%d ", prefix, segment->debugID());
   *         segment->dumpPts(prefix);
   *     } while ((segment = segment->next()));
   * }
   * ```
   */
  public fun dumpPts(prefix: String? = TODO()) {
    TODO("Implement dumpPts")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpPtsX(const char* prefix) const {
   *     if (!this->fCount) {
   *         SkDebugf("<empty>\n");
   *         return;
   *     }
   *     const SkOpSegment* segment = &fHead;
   *     do {
   *         segment->dumpPts(prefix);
   *     } while ((segment = segment->next()));
   * }
   * ```
   */
  public fun dumpPtsX(prefix: String?) {
    TODO("Implement dumpPtsX")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpSegment(int index) const {
   *     debugSegment(index)->dump();
   * }
   * ```
   */
  public fun dumpSegment(index: Int) {
    TODO("Implement dumpSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpSegments(const char* prefix, SkPathOp op) const {
   *     bool firstOp = false;
   *     const SkOpContour* c = this;
   *     do {
   *         if (!firstOp && c->operand()) {
   * #if DEBUG_ACTIVE_OP
   *             SkDebugf("op %s\n", SkPathOpsDebug::kPathOpStr[op]);
   * #endif
   *             firstOp = true;
   *         }
   *         c->dumpPtsX(prefix);
   *     } while ((c = c->next()));
   * }
   * ```
   */
  public fun dumpSegments(prefix: String? = TODO(), op: SkPathOp = TODO()) {
    TODO("Implement dumpSegments")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpSpan(int index) const {
   *     debugSpan(index)->dump();
   * }
   * ```
   */
  public fun dumpSpan(index: Int) {
    TODO("Implement dumpSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::dumpSpans() const {
   *     SkDebugf("contour=%d\n", this->debugID());
   *     const SkOpSegment* segment = &fHead;
   *     do {
   *         SkDebugf("  seg=%d ", segment->debugID());
   *         segment->dump();
   *     } while ((segment = segment->next()));
   * }
   * ```
   */
  public fun dumpSpans() {
    TODO("Implement dumpSpans")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& end() const {
   *         return fTail->pts()[SkPathOpsVerbToPoints(fTail->verb())];
   *     }
   * ```
   */
  public fun end(): SkPoint {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpSpan* SkOpContour::findSortableTop(SkOpContour* contourHead) {
   *     bool allDone = true;
   *     if (fCount) {
   *         SkOpSegment* testSegment = &fHead;
   *         do {
   *             if (testSegment->done()) {
   *                 continue;
   *             }
   *             allDone = false;
   *             SkOpSpan* result = testSegment->findSortableTop(contourHead);
   *             if (result) {
   *                 return result;
   *             }
   *         } while ((testSegment = testSegment->next()));
   *     }
   *     if (allDone) {
   *       fDone = true;
   *     }
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
   * SkOpSegment* first() {
   *         SkASSERT(fCount > 0);
   *         return &fHead;
   *     }
   * ```
   */
  public fun first(): SkOpSegment {
    TODO("Implement first")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* first() const {
   *         SkASSERT(fCount > 0);
   *         return &fHead;
   *     }
   * ```
   */
  public fun indentDump() {
    TODO("Implement indentDump")
  }

  /**
   * C++ original:
   * ```cpp
   * void indentDump() const {
   *         SkDEBUGCODE(fDebugIndent += 2);
   *     }
   * ```
   */
  public fun `init`(
    globalState: SkOpGlobalState?,
    operand: Boolean,
    isXor: Boolean,
  ) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(SkOpGlobalState* globalState, bool operand, bool isXor) {
   *         fState = globalState;
   *         fOperand = operand;
   *         fXor = isXor;
   *         SkDEBUGCODE(fID = globalState->nextContourID());
   *     }
   * ```
   */
  public fun isCcw(): Int {
    TODO("Implement isCcw")
  }

  /**
   * C++ original:
   * ```cpp
   * int isCcw() const {
   *         return fCcw;
   *     }
   * ```
   */
  public fun isXor(): Boolean {
    TODO("Implement isXor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isXor() const {
   *         return fXor;
   *     }
   * ```
   */
  public fun joinSegments() {
    TODO("Implement joinSegments")
  }

  /**
   * C++ original:
   * ```cpp
   * void joinSegments() {
   *         SkOpSegment* segment = &fHead;
   *         SkOpSegment* next;
   *         do {
   *             next = segment->next();
   *             segment->joinEnds(next ? next : &fHead);
   *         } while ((segment = next));
   *     }
   * ```
   */
  public fun markAllDone() {
    TODO("Implement markAllDone")
  }

  /**
   * C++ original:
   * ```cpp
   * void markAllDone() {
   *         SkOpSegment* segment = &fHead;
   *         do {
   *             segment->markAllDone();
   *         } while ((segment = segment->next()));
   *     }
   * ```
   */
  public fun missingCoincidence(): Boolean {
    TODO("Implement missingCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * bool missingCoincidence() {
   *         SkASSERT(fCount > 0);
   *         SkOpSegment* segment = &fHead;
   *         bool result = false;
   *         do {
   *             if (segment->missingCoincidence()) {
   *                 result = true;
   *             }
   *             segment = segment->next();
   *         } while (segment);
   *         return result;
   *     }
   * ```
   */
  public fun moveMultiples(): Boolean {
    TODO("Implement moveMultiples")
  }

  /**
   * C++ original:
   * ```cpp
   * bool moveMultiples() {
   *         SkASSERT(fCount > 0);
   *         SkOpSegment* segment = &fHead;
   *         do {
   *             if (!segment->moveMultiples()) {
   *                 return false;
   *             }
   *         } while ((segment = segment->next()));
   *         return true;
   *     }
   * ```
   */
  public fun moveNearby(): Boolean {
    TODO("Implement moveNearby")
  }

  /**
   * C++ original:
   * ```cpp
   * bool moveNearby() {
   *         SkASSERT(fCount > 0);
   *         SkOpSegment* segment = &fHead;
   *         do {
   *             if (!segment->moveNearby()) {
   *                 return false;
   *             }
   *         } while ((segment = segment->next()));
   *         return true;
   *     }
   * ```
   */
  public fun next(): SkOpContour {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* next() {
   *         return fNext;
   *     }
   * ```
   */
  public fun operand(): Boolean {
    TODO("Implement operand")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpContour* next() const {
   *         return fNext;
   *     }
   * ```
   */
  public fun oppXor(): Boolean {
    TODO("Implement oppXor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operand() const {
   *         return fOperand;
   *     }
   * ```
   */
  public fun outdentDump() {
    TODO("Implement outdentDump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool oppXor() const {
   *         return fOppXor;
   *     }
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
   * void outdentDump() const {
   *         SkDEBUGCODE(fDebugIndent -= 2);
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::rayCheck(const SkOpRayHit& base, SkOpRayDir dir, SkOpRayHit** hits,
   *                            SkArenaAlloc* allocator) {
   *     // if the bounds extreme is outside the best, we're done
   *     SkScalar baseXY = pt_xy(base.fPt, dir);
   *     SkScalar boundsXY = rect_side(fBounds, dir);
   *     bool checkLessThan = less_than(dir);
   *     if (!approximately_equal(baseXY, boundsXY) && (baseXY < boundsXY) == checkLessThan) {
   *         return;
   *     }
   *     SkOpSegment* testSegment = &fHead;
   *     do {
   *         testSegment->rayCheck(base, dir, hits, allocator);
   *     } while ((testSegment = testSegment->next()));
   * }
   * ```
   */
  public fun resetReverse() {
    TODO("Implement resetReverse")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fTail = nullptr;
   *         fNext = nullptr;
   *         fCount = 0;
   *         fDone = false;
   *         SkDEBUGCODE(fBounds.setLTRB(SK_ScalarMax, SK_ScalarMax, SK_ScalarMin, SK_ScalarMin));
   *         SkDEBUGCODE(fFirstSorted = -1);
   *         SkDEBUGCODE(fDebugIndent = 0);
   *     }
   * ```
   */
  public fun reversed(): Boolean {
    TODO("Implement reversed")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetReverse() {
   *         SkOpContour* next = this;
   *         do {
   *             if (!next->count()) {
   *                 continue;
   *             }
   *             next->fCcw = -1;
   *             next->fReverse = false;
   *         } while ((next = next->next()));
   *     }
   * ```
   */
  public fun setBounds() {
    TODO("Implement setBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool reversed() const {
   *         return fReverse;
   *     }
   * ```
   */
  public fun setCcw(ccw: Int) {
    TODO("Implement setCcw")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBounds() {
   *         SkASSERT(fCount > 0);
   *         const SkOpSegment* segment = &fHead;
   *         fBounds = segment->bounds();
   *         while ((segment = segment->next())) {
   *             fBounds.add(segment->bounds());
   *         }
   *     }
   * ```
   */
  public fun setGlobalState(state: SkOpGlobalState?) {
    TODO("Implement setGlobalState")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCcw(int ccw) {
   *         fCcw = ccw;
   *     }
   * ```
   */
  public fun setNext(contour: SkOpContour?) {
    TODO("Implement setNext")
  }

  /**
   * C++ original:
   * ```cpp
   * void setGlobalState(SkOpGlobalState* state) {
   *         fState = state;
   *     }
   * ```
   */
  public fun setOperand(isOp: Boolean) {
    TODO("Implement setOperand")
  }

  /**
   * C++ original:
   * ```cpp
   * void setNext(SkOpContour* contour) {
   * //        SkASSERT(!fNext == !!contour);
   *         fNext = contour;
   *     }
   * ```
   */
  public fun setOppXor(isOppXor: Boolean) {
    TODO("Implement setOppXor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOperand(bool isOp) {
   *         fOperand = isOp;
   *     }
   * ```
   */
  public fun setReverse() {
    TODO("Implement setReverse")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOppXor(bool isOppXor) {
   *         fOppXor = isOppXor;
   *     }
   * ```
   */
  public fun setXor(isXor: Boolean) {
    TODO("Implement setXor")
  }

  /**
   * C++ original:
   * ```cpp
   * void setReverse() {
   *         fReverse = true;
   *     }
   * ```
   */
  public fun sortAngles(): Boolean {
    TODO("Implement sortAngles")
  }

  /**
   * C++ original:
   * ```cpp
   * void setXor(bool isXor) {
   *         fXor = isXor;
   *     }
   * ```
   */
  public fun start(): SkPoint {
    TODO("Implement start")
  }

  /**
   * C++ original:
   * ```cpp
   * bool sortAngles() {
   *         SkASSERT(fCount > 0);
   *         SkOpSegment* segment = &fHead;
   *         do {
   *             FAIL_IF(!segment->sortAngles());
   *         } while ((segment = segment->next()));
   *         return true;
   *     }
   * ```
   */
  public fun toPartialBackward(path: SkPathWriter?) {
    TODO("Implement toPartialBackward")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& start() const {
   *         return fHead.pts()[0];
   *     }
   * ```
   */
  public fun toPartialForward(path: SkPathWriter?) {
    TODO("Implement toPartialForward")
  }

  /**
   * C++ original:
   * ```cpp
   * void toPartialBackward(SkPathWriter* path) const {
   *         const SkOpSegment* segment = fTail;
   *         do {
   *             SkAssertResult(segment->addCurveTo(segment->tail(), segment->head(), path));
   *         } while ((segment = segment->prev()));
   *     }
   * ```
   */
  public fun toReversePath(path: SkPathWriter?) {
    TODO("Implement toReversePath")
  }

  /**
   * C++ original:
   * ```cpp
   * void toPartialForward(SkPathWriter* path) const {
   *         const SkOpSegment* segment = &fHead;
   *         do {
   *             SkAssertResult(segment->addCurveTo(segment->head(), segment->tail(), path));
   *         } while ((segment = segment->next()));
   *     }
   * ```
   */
  public fun toPath(path: SkPathWriter?) {
    TODO("Implement toPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkOpContour::toReversePath(SkPathWriter* path) const {
   *     const SkOpSegment* segment = fTail;
   *     do {
   *         SkAssertResult(segment->addCurveTo(segment->tail(), segment->head(), path));
   *     } while ((segment = segment->prev()));
   *     path->finishContour();
   *     path->assemble();
   * }
   * ```
   */
  public fun undoneSpan(): SkOpSpan {
    TODO("Implement undoneSpan")
  }
}
