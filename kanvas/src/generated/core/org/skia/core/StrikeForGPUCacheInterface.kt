package org.skia.core

import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class StrikeForGPUCacheInterface {
 * public:
 *     virtual ~StrikeForGPUCacheInterface() = default;
 *     virtual sk_sp<StrikeForGPU> findOrCreateScopedStrike(const SkStrikeSpec& strikeSpec) = 0;
 * }
 * ```
 */
public abstract class StrikeForGPUCacheInterface {
  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<StrikeForGPU> findOrCreateScopedStrike(const SkStrikeSpec& strikeSpec) = 0
   * ```
   */
  public abstract fun findOrCreateScopedStrike(strikeSpec: SkStrikeSpec): SkSp<StrikeForGPU>
}
