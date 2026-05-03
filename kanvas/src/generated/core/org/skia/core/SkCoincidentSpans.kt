package org.skia.core

import SkDEBUGCODE
import kotlin.Boolean
import kotlin.Char
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename T> class SkTDArray;
 *
 * class SkCoincidentSpans {
 * public:
 *     const SkOpPtT* coinPtTEnd() const;
 *     const SkOpPtT* coinPtTStart() const;
 *
 *     // These return non-const pointers so that, as copies, they can be added
 *     // to a new span pair
 *     SkOpPtT* coinPtTEndWritable() const { return const_cast<SkOpPtT*>(fCoinPtTEnd); }
 *     SkOpPtT* coinPtTStartWritable() const { return const_cast<SkOpPtT*>(fCoinPtTStart); }
 *
 *     bool collapsed(const SkOpPtT* ) const;
 *     bool contains(const SkOpPtT* s, const SkOpPtT* e) const;
 *     void correctEnds();
 *     void correctOneEnd(const SkOpPtT* (SkCoincidentSpans::* getEnd)() const,
 *                        void (SkCoincidentSpans::* setEnd)(const SkOpPtT* ptT) );
 *
 * #if DEBUG_COIN
 *     void debugCorrectEnds(SkPathOpsDebug::GlitchLog* log) const;
 *     void debugCorrectOneEnd(SkPathOpsDebug::GlitchLog* log,
 *                             const SkOpPtT* (SkCoincidentSpans::* getEnd)() const,
 *                             void (SkCoincidentSpans::* setEnd)(const SkOpPtT* ptT) const) const;
 *     bool debugExpand(SkPathOpsDebug::GlitchLog* log) const;
 * #endif
 *
 *     const char* debugID() const {
 * #if DEBUG_COIN
 *         return fGlobalState->debugCoinDictEntry().fFunctionName;
 * #else
 *         return nullptr;
 * #endif
 *     }
 *
 *     void debugShow() const;
 * #ifdef SK_DEBUG
 *     void debugStartCheck(const SkOpSpanBase* outer, const SkOpSpanBase* over,
 *             const SkOpGlobalState* debugState) const;
 * #endif
 *     void dump() const;
 *     bool expand();
 *     bool extend(const SkOpPtT* coinPtTStart, const SkOpPtT* coinPtTEnd,
 *                 const SkOpPtT* oppPtTStart, const SkOpPtT* oppPtTEnd);
 *     bool flipped() const { return fOppPtTStart->fT > fOppPtTEnd->fT; }
 *     SkDEBUGCODE(SkOpGlobalState* globalState() { return fGlobalState; })
 *
 *     void init(SkDEBUGCODE(SkOpGlobalState* globalState)) {
 *         sk_bzero(this, sizeof(*this));
 *         SkDEBUGCODE(fGlobalState = globalState);
 *     }
 *
 *     SkCoincidentSpans* next() { return fNext; }
 *     const SkCoincidentSpans* next() const { return fNext; }
 *     SkCoincidentSpans** nextPtr() { return &fNext; }
 *     const SkOpPtT* oppPtTStart() const;
 *     const SkOpPtT* oppPtTEnd() const;
 *     // These return non-const pointers so that, as copies, they can be added
 *     // to a new span pair
 *     SkOpPtT* oppPtTStartWritable() const { return const_cast<SkOpPtT*>(fOppPtTStart); }
 *     SkOpPtT* oppPtTEndWritable() const { return const_cast<SkOpPtT*>(fOppPtTEnd); }
 *     bool ordered(bool* result) const;
 *
 *     void set(SkCoincidentSpans* next, const SkOpPtT* coinPtTStart, const SkOpPtT* coinPtTEnd,
 *             const SkOpPtT* oppPtTStart, const SkOpPtT* oppPtTEnd);
 *
 *     void setCoinPtTEnd(const SkOpPtT* ptT) {
 *         SkOPASSERT(ptT == ptT->span()->ptT());
 *         SkOPASSERT(!fCoinPtTStart || ptT->fT != fCoinPtTStart->fT);
 *         SkASSERT(!fCoinPtTStart || fCoinPtTStart->segment() == ptT->segment());
 *         fCoinPtTEnd = ptT;
 *         ptT->setCoincident();
 *     }
 *
 *     void setCoinPtTStart(const SkOpPtT* ptT) {
 *         SkOPASSERT(ptT == ptT->span()->ptT());
 *         SkOPASSERT(!fCoinPtTEnd || ptT->fT != fCoinPtTEnd->fT);
 *         SkASSERT(!fCoinPtTEnd || fCoinPtTEnd->segment() == ptT->segment());
 *         fCoinPtTStart = ptT;
 *         ptT->setCoincident();
 *     }
 *
 *     void setEnds(const SkOpPtT* coinPtTEnd, const SkOpPtT* oppPtTEnd) {
 *         this->setCoinPtTEnd(coinPtTEnd);
 *         this->setOppPtTEnd(oppPtTEnd);
 *     }
 *
 *     void setOppPtTEnd(const SkOpPtT* ptT) {
 *         SkOPASSERT(ptT == ptT->span()->ptT());
 *         SkOPASSERT(!fOppPtTStart || ptT->fT != fOppPtTStart->fT);
 *         SkASSERT(!fOppPtTStart || fOppPtTStart->segment() == ptT->segment());
 *         fOppPtTEnd = ptT;
 *         ptT->setCoincident();
 *     }
 *
 *     void setOppPtTStart(const SkOpPtT* ptT) {
 *         SkOPASSERT(ptT == ptT->span()->ptT());
 *         SkOPASSERT(!fOppPtTEnd || ptT->fT != fOppPtTEnd->fT);
 *         SkASSERT(!fOppPtTEnd || fOppPtTEnd->segment() == ptT->segment());
 *         fOppPtTStart = ptT;
 *         ptT->setCoincident();
 *     }
 *
 *     void setStarts(const SkOpPtT* coinPtTStart, const SkOpPtT* oppPtTStart) {
 *         this->setCoinPtTStart(coinPtTStart);
 *         this->setOppPtTStart(oppPtTStart);
 *     }
 *
 *     void setNext(SkCoincidentSpans* next) { fNext = next; }
 *
 * private:
 *     SkCoincidentSpans* fNext;
 *     const SkOpPtT* fCoinPtTStart;
 *     const SkOpPtT* fCoinPtTEnd;
 *     const SkOpPtT* fOppPtTStart;
 *     const SkOpPtT* fOppPtTEnd;
 *     SkDEBUGCODE(SkOpGlobalState* fGlobalState;)
 * }
 * ```
 */
public data class SkCoincidentSpans public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkCoincidentSpans* fNext
   * ```
   */
  private var fNext: SkCoincidentSpans?,
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* fCoinPtTStart
   * ```
   */
  private val fCoinPtTStart: SkOpPtT?,
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* fCoinPtTEnd
   * ```
   */
  private val fCoinPtTEnd: SkOpPtT?,
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* fOppPtTStart
   * ```
   */
  private val fOppPtTStart: SkOpPtT?,
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* fOppPtTEnd
   * ```
   */
  private val fOppPtTEnd: SkOpPtT?,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkCoincidentSpans::coinPtTEnd() const {
   *     return fCoinPtTEnd;
   * }
   * ```
   */
  public fun coinPtTEnd(): SkOpPtT {
    TODO("Implement coinPtTEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkCoincidentSpans::coinPtTStart() const {
   *     return fCoinPtTStart;
   * }
   * ```
   */
  public fun coinPtTStart(): SkOpPtT {
    TODO("Implement coinPtTStart")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* coinPtTEndWritable() const { return const_cast<SkOpPtT*>(fCoinPtTEnd); }
   * ```
   */
  public fun coinPtTEndWritable(): SkOpPtT {
    TODO("Implement coinPtTEndWritable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* coinPtTStartWritable() const { return const_cast<SkOpPtT*>(fCoinPtTStart); }
   * ```
   */
  public fun coinPtTStartWritable(): SkOpPtT {
    TODO("Implement coinPtTStartWritable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoincidentSpans::collapsed(const SkOpPtT* test) const {
   *     return (fCoinPtTStart == test && fCoinPtTEnd->contains(test))
   *         || (fCoinPtTEnd == test && fCoinPtTStart->contains(test))
   *         || (fOppPtTStart == test && fOppPtTEnd->contains(test))
   *         || (fOppPtTEnd == test && fOppPtTStart->contains(test));
   * }
   * ```
   */
  public fun collapsed(test: SkOpPtT?): Boolean {
    TODO("Implement collapsed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoincidentSpans::contains(const SkOpPtT* s, const SkOpPtT* e) const {
   *     if (s->fT > e->fT) {
   *         using std::swap;
   *         swap(s, e);
   *     }
   *     if (s->segment() == fCoinPtTStart->segment()) {
   *         return fCoinPtTStart->fT <= s->fT && e->fT <= fCoinPtTEnd->fT;
   *     } else {
   *         SkASSERT(s->segment() == fOppPtTStart->segment());
   *         double oppTs = fOppPtTStart->fT;
   *         double oppTe = fOppPtTEnd->fT;
   *         if (oppTs > oppTe) {
   *             using std::swap;
   *             swap(oppTs, oppTe);
   *         }
   *         return oppTs <= s->fT && e->fT <= oppTe;
   *     }
   * }
   * ```
   */
  public fun contains(s: SkOpPtT?, e: SkOpPtT?): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::correctEnds() {
   *     this->correctOneEnd(&SkCoincidentSpans::coinPtTStart, &SkCoincidentSpans::setCoinPtTStart);
   *     this->correctOneEnd(&SkCoincidentSpans::coinPtTEnd, &SkCoincidentSpans::setCoinPtTEnd);
   *     this->correctOneEnd(&SkCoincidentSpans::oppPtTStart, &SkCoincidentSpans::setOppPtTStart);
   *     this->correctOneEnd(&SkCoincidentSpans::oppPtTEnd, &SkCoincidentSpans::setOppPtTEnd);
   * }
   * ```
   */
  public fun correctEnds() {
    TODO("Implement correctEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::correctOneEnd(
   *         const SkOpPtT* (SkCoincidentSpans::* getEnd)() const,
   *         void (SkCoincidentSpans::*setEnd)(const SkOpPtT* ptT) ) {
   *     const SkOpPtT* origPtT = (this->*getEnd)();
   *     const SkOpSpanBase* origSpan = origPtT->span();
   *     const SkOpSpan* prev = origSpan->prev();
   *     const SkOpPtT* testPtT = prev ? prev->next()->ptT()
   *             : origSpan->upCast()->next()->prev()->ptT();
   *     if (origPtT != testPtT) {
   *         (this->*setEnd)(testPtT);
   *     }
   * }
   * ```
   */
  public fun correctOneEnd() {
    TODO("Implement correctOneEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* debugID() const {
   * #if DEBUG_COIN
   *         return fGlobalState->debugCoinDictEntry().fFunctionName;
   * #else
   *         return nullptr;
   * #endif
   *     }
   * ```
   */
  public fun debugID(): Char {
    TODO("Implement debugID")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::debugShow() const {
   *     SkDebugf("coinSpan - id=%d t=%1.9g tEnd=%1.9g\n", coinPtTStart()->segment()->debugID(),
   *             coinPtTStart()->fT, coinPtTEnd()->fT);
   *     SkDebugf("coinSpan + id=%d t=%1.9g tEnd=%1.9g\n", oppPtTStart()->segment()->debugID(),
   *             oppPtTStart()->fT, oppPtTEnd()->fT);
   * }
   * ```
   */
  public fun debugShow() {
    TODO("Implement debugShow")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::debugStartCheck(const SkOpSpanBase* outer, const SkOpSpanBase* over,
   *         const SkOpGlobalState* debugState) const {
   *     SkASSERT(coinPtTEnd()->span() == over || !SkOpGlobalState::DebugRunFail());
   *     SkASSERT(oppPtTEnd()->span() == outer || !SkOpGlobalState::DebugRunFail());
   * }
   * ```
   */
  public fun debugStartCheck(
    outer: SkOpSpanBase?,
    over: SkOpSpanBase?,
    debugState: SkOpGlobalState?,
  ) {
    TODO("Implement debugStartCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::dump() const {
   *     SkDebugf("- seg=%d span=%d ptT=%d ", fCoinPtTStart->segment()->debugID(),
   *         fCoinPtTStart->span()->debugID(), fCoinPtTStart->debugID());
   *     fCoinPtTStart->dumpBase();
   *     SkDebugf(" span=%d ptT=%d ", fCoinPtTEnd->span()->debugID(), fCoinPtTEnd->debugID());
   *     fCoinPtTEnd->dumpBase();
   *     if (fCoinPtTStart->segment()->operand()) {
   *         SkDebugf(" operand");
   *     }
   *     if (fCoinPtTStart->segment()->isXor()) {
   *         SkDebugf(" xor");
   *     }
   *     SkDebugf("\n");
   *     SkDebugf("+ seg=%d span=%d ptT=%d ", fOppPtTStart->segment()->debugID(),
   *         fOppPtTStart->span()->debugID(), fOppPtTStart->debugID());
   *     fOppPtTStart->dumpBase();
   *     SkDebugf(" span=%d ptT=%d ", fOppPtTEnd->span()->debugID(), fOppPtTEnd->debugID());
   *     fOppPtTEnd->dumpBase();
   *     if (fOppPtTStart->segment()->operand()) {
   *         SkDebugf(" operand");
   *     }
   *     if (fOppPtTStart->segment()->isXor()) {
   *         SkDebugf(" xor");
   *     }
   *     SkDebugf("\n");
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoincidentSpans::expand() {
   *     bool expanded = false;
   *     const SkOpSegment* segment = coinPtTStart()->segment();
   *     const SkOpSegment* oppSegment = oppPtTStart()->segment();
   *     do {
   *         const SkOpSpan* start = coinPtTStart()->span()->upCast();
   *         const SkOpSpan* prev = start->prev();
   *         const SkOpPtT* oppPtT;
   *         if (!prev || !(oppPtT = prev->contains(oppSegment))) {
   *             break;
   *         }
   *         double midT = (prev->t() + start->t()) / 2;
   *         if (!segment->isClose(midT, oppSegment)) {
   *             break;
   *         }
   *         setStarts(prev->ptT(), oppPtT);
   *         expanded = true;
   *     } while (true);
   *     do {
   *         const SkOpSpanBase* end = coinPtTEnd()->span();
   *         SkOpSpanBase* next = end->final() ? nullptr : end->upCast()->next();
   *         if (next && next->deleted()) {
   *             break;
   *         }
   *         const SkOpPtT* oppPtT;
   *         if (!next || !(oppPtT = next->contains(oppSegment))) {
   *             break;
   *         }
   *         double midT = (end->t() + next->t()) / 2;
   *         if (!segment->isClose(midT, oppSegment)) {
   *             break;
   *         }
   *         setEnds(next->ptT(), oppPtT);
   *         expanded = true;
   *     } while (true);
   *     return expanded;
   * }
   * ```
   */
  public fun expand(): Boolean {
    TODO("Implement expand")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoincidentSpans::extend(const SkOpPtT* coinPtTStart, const SkOpPtT* coinPtTEnd,
   *         const SkOpPtT* oppPtTStart, const SkOpPtT* oppPtTEnd) {
   *     bool result = false;
   *     if (fCoinPtTStart->fT > coinPtTStart->fT || (this->flipped()
   *             ? fOppPtTStart->fT < oppPtTStart->fT : fOppPtTStart->fT > oppPtTStart->fT)) {
   *         this->setStarts(coinPtTStart, oppPtTStart);
   *         result = true;
   *     }
   *     if (fCoinPtTEnd->fT < coinPtTEnd->fT || (this->flipped()
   *             ? fOppPtTEnd->fT > oppPtTEnd->fT : fOppPtTEnd->fT < oppPtTEnd->fT)) {
   *         this->setEnds(coinPtTEnd, oppPtTEnd);
   *         result = true;
   *     }
   *     return result;
   * }
   * ```
   */
  public fun extend(
    coinPtTStart: SkOpPtT?,
    coinPtTEnd: SkOpPtT?,
    oppPtTStart: SkOpPtT?,
    oppPtTEnd: SkOpPtT?,
  ): Boolean {
    TODO("Implement extend")
  }

  /**
   * C++ original:
   * ```cpp
   * bool flipped() const { return fOppPtTStart->fT > fOppPtTEnd->fT; }
   * ```
   */
  public fun flipped(): Boolean {
    TODO("Implement flipped")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(SkDEBUGCODE(SkOpGlobalState* globalState)) {
   *         sk_bzero(this, sizeof(*this));
   *         SkDEBUGCODE(fGlobalState = globalState);
   *     }
   * ```
   */
  public fun `init`(param0: (Int) -> SkDEBUGCODE) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCoincidentSpans* next() { return fNext; }
   * ```
   */
  public fun next(): SkCoincidentSpans {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkCoincidentSpans* next() const { return fNext; }
   * ```
   */
  public fun nextPtr(): SkCoincidentSpans {
    TODO("Implement nextPtr")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCoincidentSpans** nextPtr() { return &fNext; }
   * ```
   */
  public fun oppPtTStart(): SkOpPtT {
    TODO("Implement oppPtTStart")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkCoincidentSpans::oppPtTStart() const {
   *     return fOppPtTStart;
   * }
   * ```
   */
  public fun oppPtTEnd(): SkOpPtT {
    TODO("Implement oppPtTEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkOpPtT* SkCoincidentSpans::oppPtTEnd() const {
   *     return fOppPtTEnd;
   * }
   * ```
   */
  public fun oppPtTStartWritable(): SkOpPtT {
    TODO("Implement oppPtTStartWritable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* oppPtTStartWritable() const { return const_cast<SkOpPtT*>(fOppPtTStart); }
   * ```
   */
  public fun oppPtTEndWritable(): SkOpPtT {
    TODO("Implement oppPtTEndWritable")
  }

  /**
   * C++ original:
   * ```cpp
   * SkOpPtT* oppPtTEndWritable() const { return const_cast<SkOpPtT*>(fOppPtTEnd); }
   * ```
   */
  public fun ordered(result: Boolean?): Boolean {
    TODO("Implement ordered")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkCoincidentSpans::ordered(bool* result) const {
   *     const SkOpSpanBase* start = this->coinPtTStart()->span();
   *     const SkOpSpanBase* end = this->coinPtTEnd()->span();
   *     const SkOpSpanBase* next = start->upCast()->next();
   *     if (next == end) {
   *         *result = true;
   *         return true;
   *     }
   *     bool flipped = this->flipped();
   *     const SkOpSegment* oppSeg = this->oppPtTStart()->segment();
   *     double oppLastT = fOppPtTStart->fT;
   *     do {
   *         const SkOpPtT* opp = next->contains(oppSeg);
   *         if (!opp) {
   * //            SkOPOBJASSERT(start, 0);  // may assert if coincident span isn't fully processed
   *             return false;
   *         }
   *         if ((oppLastT > opp->fT) != flipped) {
   *             *result = false;
   *             return true;
   *         }
   *         oppLastT = opp->fT;
   *         if (next == end) {
   *             break;
   *         }
   *         if (!next->upCastable()) {
   *             *result = false;
   *             return true;
   *         }
   *         next = next->upCast()->next();
   *     } while (true);
   *     *result = true;
   *     return true;
   * }
   * ```
   */
  public fun `set`(
    next: SkCoincidentSpans?,
    coinPtTStart: SkOpPtT?,
    coinPtTEnd: SkOpPtT?,
    oppPtTStart: SkOpPtT?,
    oppPtTEnd: SkOpPtT?,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkCoincidentSpans::set(SkCoincidentSpans* next, const SkOpPtT* coinPtTStart,
   *         const SkOpPtT* coinPtTEnd, const SkOpPtT* oppPtTStart, const SkOpPtT* oppPtTEnd) {
   *     SkASSERT(SkOpCoincidence::Ordered(coinPtTStart, oppPtTStart));
   *     fNext = next;
   *     this->setStarts(coinPtTStart, oppPtTStart);
   *     this->setEnds(coinPtTEnd, oppPtTEnd);
   * }
   * ```
   */
  public fun setCoinPtTEnd(ptT: SkOpPtT?) {
    TODO("Implement setCoinPtTEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCoinPtTEnd(const SkOpPtT* ptT) {
   *         SkOPASSERT(ptT == ptT->span()->ptT());
   *         SkOPASSERT(!fCoinPtTStart || ptT->fT != fCoinPtTStart->fT);
   *         SkASSERT(!fCoinPtTStart || fCoinPtTStart->segment() == ptT->segment());
   *         fCoinPtTEnd = ptT;
   *         ptT->setCoincident();
   *     }
   * ```
   */
  public fun setCoinPtTStart(ptT: SkOpPtT?) {
    TODO("Implement setCoinPtTStart")
  }

  /**
   * C++ original:
   * ```cpp
   * void setCoinPtTStart(const SkOpPtT* ptT) {
   *         SkOPASSERT(ptT == ptT->span()->ptT());
   *         SkOPASSERT(!fCoinPtTEnd || ptT->fT != fCoinPtTEnd->fT);
   *         SkASSERT(!fCoinPtTEnd || fCoinPtTEnd->segment() == ptT->segment());
   *         fCoinPtTStart = ptT;
   *         ptT->setCoincident();
   *     }
   * ```
   */
  public fun setEnds(coinPtTEnd: SkOpPtT?, oppPtTEnd: SkOpPtT?) {
    TODO("Implement setEnds")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEnds(const SkOpPtT* coinPtTEnd, const SkOpPtT* oppPtTEnd) {
   *         this->setCoinPtTEnd(coinPtTEnd);
   *         this->setOppPtTEnd(oppPtTEnd);
   *     }
   * ```
   */
  public fun setOppPtTEnd(ptT: SkOpPtT?) {
    TODO("Implement setOppPtTEnd")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOppPtTEnd(const SkOpPtT* ptT) {
   *         SkOPASSERT(ptT == ptT->span()->ptT());
   *         SkOPASSERT(!fOppPtTStart || ptT->fT != fOppPtTStart->fT);
   *         SkASSERT(!fOppPtTStart || fOppPtTStart->segment() == ptT->segment());
   *         fOppPtTEnd = ptT;
   *         ptT->setCoincident();
   *     }
   * ```
   */
  public fun setOppPtTStart(ptT: SkOpPtT?) {
    TODO("Implement setOppPtTStart")
  }

  /**
   * C++ original:
   * ```cpp
   * void setOppPtTStart(const SkOpPtT* ptT) {
   *         SkOPASSERT(ptT == ptT->span()->ptT());
   *         SkOPASSERT(!fOppPtTEnd || ptT->fT != fOppPtTEnd->fT);
   *         SkASSERT(!fOppPtTEnd || fOppPtTEnd->segment() == ptT->segment());
   *         fOppPtTStart = ptT;
   *         ptT->setCoincident();
   *     }
   * ```
   */
  public fun setStarts(coinPtTStart: SkOpPtT?, oppPtTStart: SkOpPtT?) {
    TODO("Implement setStarts")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStarts(const SkOpPtT* coinPtTStart, const SkOpPtT* oppPtTStart) {
   *         this->setCoinPtTStart(coinPtTStart);
   *         this->setOppPtTStart(oppPtTStart);
   *     }
   * ```
   */
  public fun setNext(next: SkCoincidentSpans?) {
    TODO("Implement setNext")
  }
}
