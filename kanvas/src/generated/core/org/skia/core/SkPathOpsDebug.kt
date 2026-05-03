package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class SkPathOpsDebug {
 * public:
 * #if DEBUG_COIN
 *     struct GlitchLog;
 *
 *     enum GlitchType {
 *         kUninitialized_Glitch,
 *         kAddCorruptCoin_Glitch,
 *         kAddExpandedCoin_Glitch,
 *         kAddExpandedFail_Glitch,
 *         kAddIfCollapsed_Glitch,
 *         kAddIfMissingCoin_Glitch,
 *         kAddMissingCoin_Glitch,
 *         kAddMissingExtend_Glitch,
 *         kAddOrOverlap_Glitch,
 *         kCollapsedCoin_Glitch,
 *         kCollapsedDone_Glitch,
 *         kCollapsedOppValue_Glitch,
 *         kCollapsedSpan_Glitch,
 *         kCollapsedWindValue_Glitch,
 *         kCorrectEnd_Glitch,
 *         kDeletedCoin_Glitch,
 *         kExpandCoin_Glitch,
 *         kFail_Glitch,
 *         kMarkCoinEnd_Glitch,
 *         kMarkCoinInsert_Glitch,
 *         kMarkCoinMissing_Glitch,
 *         kMarkCoinStart_Glitch,
 *         kMergeMatches_Glitch,
 *         kMissingCoin_Glitch,
 *         kMissingDone_Glitch,
 *         kMissingIntersection_Glitch,
 *         kMoveMultiple_Glitch,
 *         kMoveNearbyClearAll_Glitch,
 *         kMoveNearbyClearAll2_Glitch,
 *         kMoveNearbyMerge_Glitch,
 *         kMoveNearbyMergeFinal_Glitch,
 *         kMoveNearbyRelease_Glitch,
 *         kMoveNearbyReleaseFinal_Glitch,
 *         kReleasedSpan_Glitch,
 *         kReturnFalse_Glitch,
 *         kUnaligned_Glitch,
 *         kUnalignedHead_Glitch,
 *         kUnalignedTail_Glitch,
 *     };
 *
 *     struct CoinDictEntry {
 *         int fIteration;
 *         int fLineNumber;
 *         GlitchType fGlitchType;
 *         const char* fFunctionName;
 *     };
 *
 *     struct CoinDict {
 *         void add(const CoinDictEntry& key);
 *         void add(const CoinDict& dict);
 *         void dump(const char* str, bool visitCheck) const;
 *         SkTDArray<CoinDictEntry> fDict;
 *     };
 *
 *     static CoinDict gCoinSumChangedDict;
 *     static CoinDict gCoinSumVisitedDict;
 *     static CoinDict gCoinVistedDict;
 * #endif
 *
 * #if defined(SK_DEBUG) || !FORCE_RELEASE
 *     static int gContourID;
 *     static int gSegmentID;
 * #endif
 *
 * #if DEBUG_SORT
 *     static int gSortCountDefault;
 *     static int gSortCount;
 * #endif
 *
 * #if DEBUG_ACTIVE_OP
 *     static const char* kPathOpStr[];
 * #endif
 *     static bool gRunFail;
 *     static bool gVeryVerbose;
 *
 * #if DEBUG_ACTIVE_SPANS
 *     static SkString gActiveSpans;
 * #endif
 * #if DEBUG_DUMP_VERIFY
 *     static bool gDumpOp;
 *     static bool gVerifyOp;
 * #endif
 *
 *     static const char* OpStr(SkPathOp );
 *     static void MathematicaIze(char* str, size_t bufferSize);
 *     static bool ValidWind(int winding);
 *     static void WindingPrintf(int winding);
 *
 *     static void ShowActiveSpans(SkOpContourHead* contourList);
 *     static void ShowOnePath(const SkPath& path, const char* name, bool includeDeclaration);
 *     static void ShowPath(const SkPath& one, const SkPath& two, SkPathOp op, const char* name);
 *
 *     static bool ChaseContains(const SkTDArray<SkOpSpanBase*>& , const SkOpSpanBase* );
 *
 *     static void CheckHealth(class SkOpContourHead* contourList);
 *
 * #if DEBUG_COIN
 *    static void DumpCoinDict();
 *    static void DumpGlitchType(GlitchType );
 * #endif
 *
 * }
 * ```
 */
public open class SkPathOpsDebug {
  public companion object {
    public var gContourID: Int = TODO("Initialize gContourID")

    public var gSegmentID: Int = TODO("Initialize gSegmentID")

    public var gRunFail: Boolean = TODO("Initialize gRunFail")

    public var gVeryVerbose: Boolean = TODO("Initialize gVeryVerbose")

    /**
     * C++ original:
     * ```cpp
     * const char* SkPathOpsDebug::OpStr(SkPathOp op) {
     *     return gOpStrs[op];
     * }
     * ```
     */
    public fun opStr(op: SkPathOp): Char {
      TODO("Implement opStr")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathOpsDebug::MathematicaIze(char* str, size_t bufferLen) {
     *     size_t len = strlen(str);
     *     bool num = false;
     *     for (size_t idx = 0; idx < len; ++idx) {
     *         if (num && str[idx] == 'e') {
     *             if (len + 2 >= bufferLen) {
     *                 return;
     *             }
     *             memmove(&str[idx + 2], &str[idx + 1], len - idx);
     *             str[idx] = '*';
     *             str[idx + 1] = '^';
     *             ++len;
     *         }
     *         num = str[idx] >= '0' && str[idx] <= '9';
     *     }
     * }
     * ```
     */
    public fun mathematicaIze(str: String?, bufferSize: ULong) {
      TODO("Implement mathematicaIze")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathOpsDebug::ValidWind(int wind) {
     *     return wind > SK_MinS32 + 0xFFFF && wind < SK_MaxS32 - 0xFFFF;
     * }
     * ```
     */
    public fun validWind(winding: Int): Boolean {
      TODO("Implement validWind")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathOpsDebug::WindingPrintf(int wind) {
     *     if (wind == SK_MinS32) {
     *         SkDebugf("?");
     *     } else {
     *         SkDebugf("%d", wind);
     *     }
     * }
     * ```
     */
    public fun windingPrintf(winding: Int) {
      TODO("Implement windingPrintf")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathOpsDebug::ShowActiveSpans(SkOpContourHead* contourList) {
     * #if DEBUG_ACTIVE_SPANS
     *     SkString str;
     *     SkOpContour* contour = contourList;
     *     do {
     *         contour->debugShowActiveSpans(&str);
     *     } while ((contour = contour->next()));
     *     if (!gActiveSpans.equals(str)) {
     *         const char* s = str.c_str();
     *         const char* end;
     *         while ((end = strchr(s, '\n'))) {
     *             SkDebugf("%.*s", (int) (end - s + 1), s);
     *             s = end + 1;
     *         }
     *         gActiveSpans.set(str);
     *     }
     * #endif
     * }
     * ```
     */
    public fun showActiveSpans(contourList: SkOpContourHead?) {
      TODO("Implement showActiveSpans")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathOpsDebug::ShowOnePath(const SkPath& path, const char* name, bool includeDeclaration) {
     * #define SUPPORT_RECT_CONTOUR_DETECTION 0
     * #if SUPPORT_RECT_CONTOUR_DETECTION
     *     int rectCount = path.isRectContours() ? path.rectContours(nullptr, nullptr) : 0;
     *     if (rectCount > 0) {
     *         SkTDArray<SkRect> rects;
     *         SkTDArray<SkPathDirection> directions;
     *         rects.setCount(rectCount);
     *         directions.setCount(rectCount);
     *         path.rectContours(rects.begin(), directions.begin());
     *         for (int contour = 0; contour < rectCount; ++contour) {
     *             const SkRect& rect = rects[contour];
     *             SkDebugf("path.addRect(%1.9g, %1.9g, %1.9g, %1.9g, %s);\n", rect.fLeft, rect.fTop,
     *                     rect.fRight, rect.fBottom, directions[contour] == SkPathDirection::kCCW
     *                     ? "SkPathDirection::kCCW" : "SkPathDirection::kCW");
     *         }
     *         return;
     *     }
     * #endif
     *     SkPathFillType fillType = path.getFillType();
     *     SkASSERT(fillType >= SkPathFillType::kWinding && fillType <= SkPathFillType::kInverseEvenOdd);
     *     if (includeDeclaration) {
     *         SkDebugf("    SkPath %s;\n", name);
     *     }
     *     SkDebugf("    %s.setFillType(SkPath::%s);\n", name, gFillTypeStr[(int)fillType]);
     *     showPathContours(path, name);
     * }
     * ```
     */
    public fun showOnePath(
      path: SkPath,
      name: String?,
      includeDeclaration: Boolean,
    ) {
      TODO("Implement showOnePath")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPathOpsDebug::ShowPath(const SkPath& a, const SkPath& b, SkPathOp shapeOp,
     *         const char* testName) {
     *     static SkMutex& mutex = *(new SkMutex);
     *
     *     SkAutoMutexExclusive ac(mutex);
     *     show_function_header(testName);
     *     ShowOnePath(a, "path", true);
     *     ShowOnePath(b, "pathB", true);
     *     show_op(shapeOp, "path", "pathB");
     * }
     * ```
     */
    public fun showPath(
      one: SkPath,
      two: SkPath,
      op: SkPathOp,
      name: String?,
    ) {
      TODO("Implement showPath")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPathOpsDebug::ChaseContains(const SkTDArray<SkOpSpanBase*>& chaseArray,
     *                                    const SkOpSpanBase* span) {
     *     for (int index = 0; index < chaseArray.size(); ++index) {
     *         const SkOpSpanBase* entry = chaseArray[index];
     *         if (entry == span) {
     *             return true;
     *         }
     *     }
     *     return false;
     * }
     * ```
     */
    public fun chaseContains(chaseArray: SkTDArray<SkOpSpanBase?>, span: SkOpSpanBase?): Boolean {
      TODO("Implement chaseContains")
    }

    /**
     * C++ original:
     * ```cpp
     * static void CheckHealth(class SkOpContourHead* contourList)
     * ```
     */
    public fun checkHealth(contourList: SkOpContourHead?) {
      TODO("Implement checkHealth")
    }
  }
}
