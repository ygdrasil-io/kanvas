package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class StrikeMutationMonitor {
 * public:
 *     StrikeMutationMonitor(StrikeForGPU* strike);
 *     ~StrikeMutationMonitor();
 *
 * private:
 *     StrikeForGPU* fStrike;
 * }
 * ```
 */
public data class StrikeMutationMonitor public constructor(
  /**
   * C++ original:
   * ```cpp
   * StrikeForGPU* fStrike
   * ```
   */
  private var fStrike: StrikeForGPU?,
)
