package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class CoverageProcessor {
 * public:
 *     CoverageProcessor(const TextAnimator::DomainMaps& maps,
 *                       RangeSelector::Domain domain,
 *                       RangeSelector::Mode mode,
 *                       TextAnimator::ModulatorBuffer& dst)
 *         : fDst(dst)
 *         , fDomainSize(dst.size()) {
 *
 *         SkASSERT(mode == RangeSelector::Mode::kAdd);
 *         fProc = &CoverageProcessor::add_proc;
 *
 *         switch (domain) {
 *         case RangeSelector::Domain::kChars:
 *             // Direct (1-to-1) index mapping.
 *             break;
 *         case RangeSelector::Domain::kCharsExcludingSpaces:
 *             fMap = &maps.fNonWhitespaceMap;
 *             break;
 *         case RangeSelector::Domain::kWords:
 *             fMap = &maps.fWordsMap;
 *             break;
 *         case RangeSelector::Domain::kLines:
 *             fMap = &maps.fLinesMap;
 *             break;
 *         }
 *
 *         // When no domain map is active, fProc points directly to the mode proc.
 *         // Otherwise, we punt through a domain mapper proxy.
 *         if (fMap) {
 *             fMappedProc = fProc;
 *             fProc = &CoverageProcessor::domain_map_proc;
 *             fDomainSize = fMap->size();
 *         }
 *     }
 *
 *     size_t size() const { return fDomainSize; }
 *
 *     void operator()(float amount, size_t offset, size_t count) const {
 *         (this->*fProc)(amount, offset, count);
 *     }
 *
 * private:
 *     // mode: kAdd
 *     void add_proc(float amount, size_t offset, size_t count) const {
 *         if (!amount || !count) return;
 *
 *         for (auto* dst = fDst.data() + offset; dst < fDst.data() + offset + count; ++dst) {
 *             dst->coverage = SkTPin<float>(dst->coverage + amount, -1, 1);
 *         }
 *     }
 *
 *     // A proxy for mapping domain indices to the target buffer.
 *     void domain_map_proc(float amount, size_t offset, size_t count) const {
 *         SkASSERT(fMap);
 *         SkASSERT(fMappedProc);
 *
 *         for (auto i = offset; i < offset + count; ++i) {
 *             const auto& span = (*fMap)[i];
 *             (this->*fMappedProc)(amount, span.fOffset, span.fCount);
 *         }
 *     }
 *
 *     using ProcT = void(CoverageProcessor::*)(float amount, size_t offset, size_t count) const;
 *
 *     TextAnimator::ModulatorBuffer& fDst;
 *     ProcT                          fProc,
 *                                    fMappedProc = nullptr;
 *     const TextAnimator::DomainMap* fMap = nullptr;
 *     size_t                         fDomainSize;
 * }
 * ```
 */
public data class CoverageProcessor public constructor(
  /**
   * C++ original:
   * ```cpp
   * TextAnimator::ModulatorBuffer& fDst
   * ```
   */
  private var fDst: Int,
  /**
   * C++ original:
   * ```cpp
   * ProcT                          fProc
   * ```
   */
  private var fProc: CoverageProcessorProcT,
  /**
   * C++ original:
   * ```cpp
   * ProcT                          fProc,
   *                                    fMappedProc = nullptr
   * ```
   */
  private var fMappedProc: CoverageProcessorProcT,
  /**
   * C++ original:
   * ```cpp
   * const TextAnimator::DomainMap* fMap
   * ```
   */
  private val fMap: Int?,
  /**
   * C++ original:
   * ```cpp
   * size_t                         fDomainSize
   * ```
   */
  private var fDomainSize: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fDomainSize; }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator()(float amount, size_t offset, size_t count) const {
   *         (this->*fProc)(amount, offset, count);
   *     }
   * ```
   */
  public operator fun invoke(
    amount: Float,
    offset: ULong,
    count: ULong,
  ) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * void add_proc(float amount, size_t offset, size_t count) const {
   *         if (!amount || !count) return;
   *
   *         for (auto* dst = fDst.data() + offset; dst < fDst.data() + offset + count; ++dst) {
   *             dst->coverage = SkTPin<float>(dst->coverage + amount, -1, 1);
   *         }
   *     }
   * ```
   */
  private fun addProc(
    amount: Float,
    offset: ULong,
    count: ULong,
  ) {
    TODO("Implement addProc")
  }

  /**
   * C++ original:
   * ```cpp
   * void domain_map_proc(float amount, size_t offset, size_t count) const {
   *         SkASSERT(fMap);
   *         SkASSERT(fMappedProc);
   *
   *         for (auto i = offset; i < offset + count; ++i) {
   *             const auto& span = (*fMap)[i];
   *             (this->*fMappedProc)(amount, span.fOffset, span.fCount);
   *         }
   *     }
   * ```
   */
  private fun domainMapProc(
    amount: Float,
    offset: ULong,
    count: ULong,
  ) {
    TODO("Implement domainMapProc")
  }
}
