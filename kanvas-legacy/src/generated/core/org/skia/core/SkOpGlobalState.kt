package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class SkOpGlobalState {
 * public:
 *     SkOpGlobalState(SkOpContourHead* head,
 *                     SkArenaAlloc* allocator SkDEBUGPARAMS(bool debugSkipAssert)
 *                     SkDEBUGPARAMS(const char* testName));
 *
 *     enum {
 *         kMaxWindingTries = 10
 *     };
 *
 *     bool allocatedOpSpan() const {
 *         return fAllocatedOpSpan;
 *     }
 *
 *     SkArenaAlloc* allocator() {
 *         return fAllocator;
 *     }
 *
 *     void bumpNested() {
 *         ++fNested;
 *     }
 *
 *     void clearNested() {
 *         fNested = 0;
 *     }
 *
 *     SkOpCoincidence* coincidence() {
 *         return fCoincidence;
 *     }
 *
 *     SkOpContourHead* contourHead() {
 *         return fContourHead;
 *     }
 *
 * #ifdef SK_DEBUG
 *     const class SkOpAngle* debugAngle(int id) const;
 *     const SkOpCoincidence* debugCoincidence() const;
 *     SkOpContour* debugContour(int id) const;
 *     const class SkOpPtT* debugPtT(int id) const;
 * #endif
 *
 *     static bool DebugRunFail();
 *
 * #ifdef SK_DEBUG
 *     const class SkOpSegment* debugSegment(int id) const;
 *     bool debugSkipAssert() const { return fDebugSkipAssert; }
 *     const class SkOpSpanBase* debugSpan(int id) const;
 *     const char* debugTestName() const { return fDebugTestName; }
 * #endif
 *
 * #if DEBUG_T_SECT_LOOP_COUNT
 *     void debugAddLoopCount(SkIntersections* , const SkIntersectionHelper& ,
 *         const SkIntersectionHelper& );
 *     void debugDoYourWorst(SkOpGlobalState* );
 *     void debugLoopReport();
 *     void debugResetLoopCounts();
 * #endif
 *
 * #if DEBUG_COINCIDENCE
 *     void debugSetCheckHealth(bool check) { fDebugCheckHealth = check; }
 *     bool debugCheckHealth() const { return fDebugCheckHealth; }
 * #endif
 *
 * #if DEBUG_VALIDATE || DEBUG_COIN
 *     void debugSetPhase(const char* funcName  DEBUG_COIN_DECLARE_PARAMS()) const;
 * #endif
 *
 * #if DEBUG_COIN
 *     void debugAddToCoinChangedDict();
 *     void debugAddToGlobalCoinDicts();
 *     SkPathOpsDebug::CoinDict* debugCoinChangedDict() { return &fCoinChangedDict; }
 *     const SkPathOpsDebug::CoinDictEntry& debugCoinDictEntry() const { return fCoinDictEntry; }
 *
 *     static void DumpCoinDict();
 * #endif
 *
 *
 *     int nested() const {
 *         return fNested;
 *     }
 *
 * #ifdef SK_DEBUG
 *     int nextAngleID() {
 *         return ++fAngleID;
 *     }
 *
 *     int nextCoinID() {
 *         return ++fCoinID;
 *     }
 *
 *     int nextContourID() {
 *         return ++fContourID;
 *     }
 *
 *     int nextPtTID() {
 *         return ++fPtTID;
 *     }
 *
 *     int nextSegmentID() {
 *         return ++fSegmentID;
 *     }
 *
 *     int nextSpanID() {
 *         return ++fSpanID;
 *     }
 * #endif
 *
 *     SkOpPhase phase() const {
 *         return fPhase;
 *     }
 *
 *     void resetAllocatedOpSpan() {
 *         fAllocatedOpSpan = false;
 *     }
 *
 *     void setAllocatedOpSpan() {
 *         fAllocatedOpSpan = true;
 *     }
 *
 *     void setCoincidence(SkOpCoincidence* coincidence) {
 *         fCoincidence = coincidence;
 *     }
 *
 *     void setContourHead(SkOpContourHead* contourHead) {
 *         fContourHead = contourHead;
 *     }
 *
 *     void setPhase(SkOpPhase phase) {
 *         if (SkOpPhase::kNoChange == phase) {
 *             return;
 *         }
 *         SkASSERT(fPhase != phase);
 *         fPhase = phase;
 *     }
 *
 *     // called in very rare cases where angles are sorted incorrectly -- signfies op will fail
 *     void setWindingFailed() {
 *         fWindingFailed = true;
 *     }
 *
 *     bool windingFailed() const {
 *         return fWindingFailed;
 *     }
 *
 * private:
 *     SkArenaAlloc* fAllocator;
 *     SkOpCoincidence* fCoincidence;
 *     SkOpContourHead* fContourHead;
 *     int fNested;
 *     bool fAllocatedOpSpan;
 *     bool fWindingFailed;
 *     SkOpPhase fPhase;
 * #ifdef SK_DEBUG
 *     const char* fDebugTestName;
 *     void* fDebugReporter;
 *     int fAngleID;
 *     int fCoinID;
 *     int fContourID;
 *     int fPtTID;
 *     int fSegmentID;
 *     int fSpanID;
 *     bool fDebugSkipAssert;
 * #endif
 * #if DEBUG_T_SECT_LOOP_COUNT
 *     int fDebugLoopCount[3];
 *     SkPath::Verb fDebugWorstVerb[6];
 *     SkPoint fDebugWorstPts[24];
 *     float fDebugWorstWeight[6];
 * #endif
 * #if DEBUG_COIN
 *     SkPathOpsDebug::CoinDict fCoinChangedDict;
 *     SkPathOpsDebug::CoinDict fCoinVisitedDict;
 *     SkPathOpsDebug::CoinDictEntry fCoinDictEntry;
 *     const char* fPreviousFuncName;
 * #endif
 * #if DEBUG_COINCIDENCE
 *     bool fDebugCheckHealth;
 * #endif
 * }
 * ```
 */
public data class SkOpGlobalState public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc* fAllocator
   * ```
   */
  private var fAllocator: SkArenaAlloc?,
  /**
   * C++ original:
   * ```cpp
   * SkOpCoincidence* fCoincidence
   * ```
   */
  private var fCoincidence: SkOpCoincidence?,
  /**
   * C++ original:
   * ```cpp
   * SkOpContourHead* fContourHead
   * ```
   */
  private var fContourHead: SkOpContourHead?,
  /**
   * C++ original:
   * ```cpp
   * int fNested
   * ```
   */
  private var fNested: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fAllocatedOpSpan
   * ```
   */
  private var fAllocatedOpSpan: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fWindingFailed
   * ```
   */
  private var fWindingFailed: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkOpPhase fPhase
   * ```
   */
  private var fPhase: SkOpPhase,
  /**
   * C++ original:
   * ```cpp
   * const char* fDebugTestName
   * ```
   */
  private val fDebugTestName: String?,
  /**
   * C++ original:
   * ```cpp
   * void* fDebugReporter
   * ```
   */
  private var fDebugReporter: Unit?,
  /**
   * C++ original:
   * ```cpp
   * int fAngleID
   * ```
   */
  private var fAngleID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fCoinID
   * ```
   */
  private var fCoinID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fContourID
   * ```
   */
  private var fContourID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPtTID
   * ```
   */
  private var fPtTID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fSegmentID
   * ```
   */
  private var fSegmentID: Int,
  /**
   * C++ original:
   * ```cpp
   * int fSpanID
   * ```
   */
  private var fSpanID: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fDebugSkipAssert
   * ```
   */
  private var fDebugSkipAssert: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool allocatedOpSpan() const {
   *         return fAllocatedOpSpan;
   *     }
   * ```
   */
  public fun allocatedOpSpan(): Boolean {
    TODO("Implement allocatedOpSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc* allocator() {
   *         return fAllocator;
   *     }
   * ```
   */
  public fun allocator(): SkArenaAlloc {
    TODO("Implement allocator")
  }

  /**
   * C++ original:
   * ```cpp
   * void bumpNested() {
   *         ++fNested;
   *     }
   * ```
   */
  public fun bumpNested() {
    TODO("Implement bumpNested")
  }

  /**
   * C++ original:
   * ```cpp
   * void clearNested() {
   *         fNested = 0;
   *     }
   * ```
   */
  public fun clearNested() {
    TODO("Implement clearNested")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpCoincidence* coincidence() {
   *         return fCoincidence;
   *     }
   * ```
   */
  public fun coincidence(): SkOpCoincidence {
    TODO("Implement coincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContourHead* contourHead() {
   *         return fContourHead;
   *     }
   * ```
   */
  public fun contourHead(): SkOpContourHead {
    TODO("Implement contourHead")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpAngle* SkOpGlobalState::debugAngle(int id) const {
   *     const SkOpContour* contour = fContourHead;
   *     do {
   *         const SkOpSegment* segment = contour->first();
   *         while (segment) {
   *             const SkOpSpan* span = segment->head();
   *             do {
   *                 SkOpAngle* angle = span->fromAngle();
   *                 if (angle && angle->debugID() == id) {
   *                     return angle;
   *                 }
   *                 angle = span->toAngle();
   *                 if (angle && angle->debugID() == id) {
   *                     return angle;
   *                 }
   *             } while ((span = span->next()->upCastable()));
   *             const SkOpSpanBase* tail = segment->tail();
   *             SkOpAngle* angle = tail->fromAngle();
   *             if (angle && angle->debugID() == id) {
   *                 return angle;
   *             }
   *             segment = segment->next();
   *         }
   *     } while ((contour = contour->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugAngle(id: Int): SkOpAngle {
    TODO("Implement debugAngle")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpCoincidence* debugCoincidence() const
   * ```
   */
  public fun debugCoincidence(): SkOpCoincidence {
    TODO("Implement debugCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpContour* SkOpGlobalState::debugContour(int id) const {
   *     SkOpContour* contour = fContourHead;
   *     do {
   *         if (contour->debugID() == id) {
   *             return contour;
   *         }
   *     } while ((contour = contour->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugContour(id: Int): SkOpContour {
    TODO("Implement debugContour")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkOpGlobalState::debugPtT(int id) const {
   *     const SkOpContour* contour = fContourHead;
   *     do {
   *         const SkOpSegment* segment = contour->first();
   *         while (segment) {
   *             const SkOpSpan* span = segment->head();
   *             do {
   *                 const SkOpPtT* ptT = span->ptT();
   *                 if (ptT->debugMatchID(id)) {
   *                     return ptT;
   *                 }
   *             } while ((span = span->next()->upCastable()));
   *             const SkOpSpanBase* tail = segment->tail();
   *             const SkOpPtT* ptT = tail->ptT();
   *             if (ptT->debugMatchID(id)) {
   *                 return ptT;
   *             }
   *             segment = segment->next();
   *         }
   *     } while ((contour = contour->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugPtT(id: Int): SkOpPtT {
    TODO("Implement debugPtT")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSegment* SkOpGlobalState::debugSegment(int id) const {
   *     const SkOpContour* contour = fContourHead;
   *     do {
   *         const SkOpSegment* segment = contour->first();
   *         while (segment) {
   *             if (segment->debugID() == id) {
   *                 return segment;
   *             }
   *             segment = segment->next();
   *         }
   *     } while ((contour = contour->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugSegment(id: Int): SkOpSegment {
    TODO("Implement debugSegment")
  }

  /**
   * C++ original:
   * ```cpp
   * bool debugSkipAssert() const { return fDebugSkipAssert; }
   * ```
   */
  public fun debugSkipAssert(): Boolean {
    TODO("Implement debugSkipAssert")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpSpanBase* SkOpGlobalState::debugSpan(int id) const {
   *     const SkOpContour* contour = fContourHead;
   *     do {
   *         const SkOpSegment* segment = contour->first();
   *         while (segment) {
   *             const SkOpSpan* span = segment->head();
   *             do {
   *                 if (span->debugID() == id) {
   *                     return span;
   *                 }
   *             } while ((span = span->next()->upCastable()));
   *             const SkOpSpanBase* tail = segment->tail();
   *             if (tail->debugID() == id) {
   *                 return tail;
   *             }
   *             segment = segment->next();
   *         }
   *     } while ((contour = contour->next()));
   *     return nullptr;
   * }
   * ```
   */
  public fun debugSpan(id: Int): SkOpSpanBase {
    TODO("Implement debugSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* debugTestName() const { return fDebugTestName; }
   * ```
   */
  public fun debugTestName(): Char {
    TODO("Implement debugTestName")
  }

  /**
   * C++ original:
   * ```cpp
   * int nested() const {
   *         return fNested;
   *     }
   * ```
   */
  public fun nested(): Int {
    TODO("Implement nested")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextAngleID() {
   *         return ++fAngleID;
   *     }
   * ```
   */
  public fun nextAngleID(): Int {
    TODO("Implement nextAngleID")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextCoinID() {
   *         return ++fCoinID;
   *     }
   * ```
   */
  public fun nextCoinID(): Int {
    TODO("Implement nextCoinID")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextContourID() {
   *         return ++fContourID;
   *     }
   * ```
   */
  public fun nextContourID(): Int {
    TODO("Implement nextContourID")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextPtTID() {
   *         return ++fPtTID;
   *     }
   * ```
   */
  public fun nextPtTID(): Int {
    TODO("Implement nextPtTID")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextSegmentID() {
   *         return ++fSegmentID;
   *     }
   * ```
   */
  public fun nextSegmentID(): Int {
    TODO("Implement nextSegmentID")
  }

  /**
   * C++ original:
   * ```cpp
   * int nextSpanID() {
   *         return ++fSpanID;
   *     }
   * ```
   */
  public fun nextSpanID(): Int {
    TODO("Implement nextSpanID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPhase phase() const {
   *         return fPhase;
   *     }
   * ```
   */
  public fun phase(): SkOpPhase {
    TODO("Implement phase")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetAllocatedOpSpan() {
   *         fAllocatedOpSpan = false;
   *     }
   * ```
   */
  public fun resetAllocatedOpSpan() {
    TODO("Implement resetAllocatedOpSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAllocatedOpSpan() {
   *         fAllocatedOpSpan = true;
   *     }
   * ```
   */
  public fun setAllocatedOpSpan() {
    TODO("Implement setAllocatedOpSpan")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCoincidence(SkOpCoincidence* coincidence) {
   *         fCoincidence = coincidence;
   *     }
   * ```
   */
  public fun setCoincidence(coincidence: SkOpCoincidence?) {
    TODO("Implement setCoincidence")
  }

  /**
   * C++ original:
   * ```cpp
   * void setContourHead(SkOpContourHead* contourHead) {
   *         fContourHead = contourHead;
   *     }
   * ```
   */
  public fun setContourHead(contourHead: SkOpContourHead?) {
    TODO("Implement setContourHead")
  }

  /**
   * C++ original:
   * ```cpp
   * void setPhase(SkOpPhase phase) {
   *         if (SkOpPhase::kNoChange == phase) {
   *             return;
   *         }
   *         SkASSERT(fPhase != phase);
   *         fPhase = phase;
   *     }
   * ```
   */
  public fun setPhase(phase: SkOpPhase) {
    TODO("Implement setPhase")
  }

  /**
   * C++ original:
   * ```cpp
   * void setWindingFailed() {
   *         fWindingFailed = true;
   *     }
   * ```
   */
  public fun setWindingFailed() {
    TODO("Implement setWindingFailed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool windingFailed() const {
   *         return fWindingFailed;
   *     }
   * ```
   */
  public fun windingFailed(): Boolean {
    TODO("Implement windingFailed")
  }

  public companion object {
    public val kMaxWindingTries: Int = TODO("Initialize kMaxWindingTries")

    /**
     * C++ original:
     * ```cpp
     * bool SkOpGlobalState::DebugRunFail() {
     *     return SkPathOpsDebug::gRunFail;
     * }
     * ```
     */
    public fun debugRunFail(): Boolean {
      TODO("Implement debugRunFail")
    }
  }
}
