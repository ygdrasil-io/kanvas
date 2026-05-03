package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class PipelineLabelInfoCollector {
 * public:
 *     typedef bool (*SkipFunc)(const char*);
 *
 *     explicit PipelineLabelInfoCollector(SkSpan<const PipelineLabel> cases, SkipFunc);
 *
 *     int processLabel(const std::string& precompiledLabel, int precompileCase);
 *
 *     void finalReport();
 *
 * private:
 *     // PipelineLabelInfo captures the information for a single Pipeline label. It stores which
 *     // entry in 'kCases' it represents and which entry in 'kPrecompileCases' fulfilled it.
 *     class PipelineLabelInfo {
 *     public:
 *         PipelineLabelInfo(int casesIndex, int val = kUninit)
 *                 : fCasesIndex(casesIndex)
 *                 , fPrecompileCase(val) {}
 *
 *         // Index of this Pipeline label in 'kCases'.
 *         const int fCasesIndex;
 *
 *         static constexpr int kSkipped = -2;
 *         static constexpr int kUninit  = -1;
 *         // >= 0 -> covered by the 'fPrecompileCase' case in 'kPrecompileCases'
 *         int fPrecompileCase = kUninit;
 *     };
 *
 *     struct comparator {
 *         bool operator()(const char* a, const char* b) const {
 *             return strcmp(a, b) < 0;
 *         }
 *     };
 *
 *     int fNumLabelsProcessed = 0;
 *     std::map<const char*, PipelineLabelInfo, comparator> fMap;
 *
 *     struct OverGenInfo {
 *         OverGenInfo(int originatingSetting) : fOriginatingSetting(originatingSetting) {}
 *
 *         int fOriginatingSetting;
 *     };
 *
 *     std::map<std::string, OverGenInfo> fOverGenerated;
 * }
 * ```
 */
public data class PipelineLabelInfoCollector public constructor(
  /**
   * C++ original:
   * ```cpp
   * explicit PipelineLabelInfoCollector(SkSpan<const PipelineLabel> cases, SkipFunc)
   * ```
   */
  public var skSpan: PipelineLabelInfoCollector,
  /**
   * C++ original:
   * ```cpp
   * int fNumLabelsProcessed = 0
   * ```
   */
  public var fNumLabelsProcessed: Int,
  /**
   * C++ original:
   * ```cpp
   * std::map<const char*, PipelineLabelInfo, comparator> fMap
   * ```
   */
  public var fMap: Int,
  /**
   * C++ original:
   * ```cpp
   * std::map<std::string, OverGenInfo> fOverGenerated
   * ```
   */
  public var fOverGenerated: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int PipelineLabelInfoCollector::processLabel(const std::string& precompiledLabel,
   *                                              int precompileCase) {
   *     ++fNumLabelsProcessed;
   *
   *     auto result = fMap.find(precompiledLabel.c_str());
   *     if (result == fMap.end()) {
   *         SkDEBUGCODE(auto prior = fOverGenerated.find(precompiledLabel);)
   *         SkASSERTF(prior == fOverGenerated.end(),
   *                   "duplicate (unused) Pipeline found for cases %d %d:\n%s\n",
   *                   prior->second.fOriginatingSetting,
   *                   precompileCase,
   *                   precompiledLabel.c_str());
   *         fOverGenerated.insert({ precompiledLabel, OverGenInfo(precompileCase) });
   *         return -1;
   *     }
   *
   *     // We expect each PrecompileSettings case to handle disjoint sets of labels. If this
   *     // assert fires some pair of PrecompileSettings are handling the same case.
   *     SkASSERTF(result->second.fPrecompileCase == PipelineLabelInfo::kUninit,
   *               "cases %d and %d cover the same label",
   *               result->second.fPrecompileCase, precompileCase);
   *     result->second.fPrecompileCase = precompileCase;
   *     return result->second.fCasesIndex;
   * }
   * ```
   */
  public fun processLabel(precompiledLabel: String, precompileCase: Int): Int {
    TODO("Implement processLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * void PipelineLabelInfoCollector::finalReport() {
   *     std::vector<int> skipped, missed;
   *     int numCovered = 0, numIntentionallySkipped = 0, numMissed = 0;
   *
   *     for (const auto& iter : fMap) {
   *         if (iter.second.fPrecompileCase == PipelineLabelInfo::kSkipped) {
   *             ++numIntentionallySkipped;
   *             skipped.push_back(iter.second.fCasesIndex);
   *         } else if (iter.second.fPrecompileCase == PipelineLabelInfo::kUninit) {
   *             ++numMissed;
   *             missed.push_back(iter.second.fCasesIndex);
   *         } else {
   *             SkASSERT(iter.second.fPrecompileCase >= 0);
   *             ++numCovered;
   *         }
   *     }
   *
   *     SkASSERT(numMissed == (int) missed.size());
   *     SkASSERT(numIntentionallySkipped == (int) skipped.size());
   *
   *     SkDebugf("-----------------------\n");
   *     sort(missed.begin(), missed.end());
   *     SkDebugf("not covered: ");
   *     for (int i : missed) {
   *         SkDebugf("%d, ", i);
   *     }
   *     SkDebugf("\n");
   *
   *     sort(skipped.begin(), skipped.end());
   *     SkDebugf("skipped: ");
   *     for (int i : skipped) {
   *         SkDebugf("%d, ", i);
   *     }
   *     SkDebugf("\n");
   *
   *     SkASSERT(numCovered + static_cast<int>(fOverGenerated.size()) == fNumLabelsProcessed);
   *
   *     SkDebugf("covered %d notCovered %d skipped %d total %zu\n",
   *              numCovered,
   *              numMissed,
   *              numIntentionallySkipped,
   *              fMap.size());
   *     SkDebugf("%d Pipelines were generated\n", fNumLabelsProcessed);
   *     SkDebugf("of that %zu Pipelines were over-generated:\n", fOverGenerated.size());
   * #if 0 // enable to print out a list of the over-generated Pipeline labels
   *     for (const auto& s : fOverGenerated) {
   *         SkDebugf("from %d: %s\n", s.second.fOriginatingSetting, s.first.c_str());
   *     }
   * #endif
   * }
   * ```
   */
  public fun finalReport() {
    TODO("Implement finalReport")
  }

  public data class PipelineLabelInfo public constructor(
    public val fCasesIndex: Int,
    public var fPrecompileCase: Int,
  ) {
    public companion object {
      public val kSkipped: Int = TODO("Initialize kSkipped")

      public val kUninit: Int = TODO("Initialize kUninit")
    }
  }

  public open class Comparator {
    public operator fun invoke(a: String?, b: String?): Boolean {
      TODO("Implement invoke")
    }
  }

  public data class OverGenInfo public constructor(
    public var fOriginatingSetting: Int,
  )
}
