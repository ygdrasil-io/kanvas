package org.skia.gpu

import kotlin.Int
import org.skia.`external`.Index

/**
 * C++ original:
 * ```cpp
 * class UniformDataCache {
 * public:
 *     // Tracks uniform data on the CPU and then its transition to storage in a GPU buffer (UBO or
 *     // SSBO).
 *     struct Entry {
 *         UniformDataBlock fCpuData;
 *         BindBufferInfo fBufferBinding;
 *
 *         // Can only be initialized with CPU data.
 *         Entry(UniformDataBlock cpuData) : fCpuData(cpuData) {}
 *     };
 *
 * private:
 *     struct UniformCopier {
 *         UniformDataBlock persist(UniformDataBlock data) {
 *             return UniformDataBlock::Make(data, &fArena);
 *         }
 *         SkArenaAlloc fArena{0};
 *     };
 *     using UniformDataMap =
 *             DenseBiMap<UniformDataBlock, Entry, UniformCopier, UniformDataBlock::Hash>;
 *
 *     UniformDataMap fUniforms;
 *
 * public:
 *     using Index = UniformDataMap::Index;
 *     static constexpr Index kInvalidIndex = UniformDataMap::kInvalidIndex;
 *
 *     UniformDataCache() = default;
 *
 *     void reset() { fUniforms.reset(); }
 *
 *     Index insert(UniformDataBlock dataBlock) { return fUniforms.insert(dataBlock); }
 *
 *     const Entry& lookup(Index index) const { return fUniforms.lookup(index); }
 *
 *     Entry& lookup(Index index) { return fUniforms.lookup(index); }
 *
 * #if defined(GPU_TEST_UTILS)
 *     int count() { return fUniforms.count(); }
 * #endif
 * }
 * ```
 */
public data class UniformDataCache public constructor(
  /**
   * C++ original:
   * ```cpp
   * UniformDataMap fUniforms
   * ```
   */
  private var fUniforms: UniformDataCacheUniformDataMap,
) {
  /**
   * C++ original:
   * ```cpp
   * void reset() { fUniforms.reset(); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * Index insert(UniformDataBlock dataBlock) { return fUniforms.insert(dataBlock); }
   * ```
   */
  public fun insert(dataBlock: UniformDataBlock): Int {
    TODO("Implement insert")
  }

  /**
   * C++ original:
   * ```cpp
   * const Entry& lookup(Index index) const { return fUniforms.lookup(index); }
   * ```
   */
  public fun lookup(index: Index): Entry {
    TODO("Implement lookup")
  }

  /**
   * C++ original:
   * ```cpp
   * Entry& lookup(Index index) { return fUniforms.lookup(index); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  public open class Entry public constructor(
    public var fCpuData: UniformDataBlock,
    public var fBufferBinding: Int,
  ) {
    public constructor(cpuData: UniformDataBlock) : this() {
      TODO("Implement constructor")
    }
  }

  public data class UniformCopier public constructor(
    public var fArena: Int,
  ) {
    public fun persist(`data`: UniformDataBlock): UniformDataBlock {
      TODO("Implement persist")
    }
  }

  public companion object {
    public val kInvalidIndex: Int = TODO("Initialize kInvalidIndex")
  }
}
