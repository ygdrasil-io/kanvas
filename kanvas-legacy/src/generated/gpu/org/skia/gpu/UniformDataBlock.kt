package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * class UniformDataBlock {
 * public:
 *     constexpr UniformDataBlock(const UniformDataBlock&) = default;
 *     constexpr UniformDataBlock() = default;
 *
 *     static UniformDataBlock Make(UniformDataBlock toClone, SkArenaAlloc* arena) {
 *         const char* copy = arena->makeArrayCopy<char>(toClone.fData);
 *         return UniformDataBlock(SkSpan(copy, toClone.size()));
 *     }
 *
 *     // Wraps the finished accumulated uniform data within the manager's underlying storage.
 *     static UniformDataBlock Wrap(UniformManager* uniforms) {
 *         return UniformDataBlock(uniforms->finish());
 *     }
 *
 *     static UniformDataBlock WrapNonShading(UniformManager* uniforms) {
 *         return UniformDataBlock(uniforms->finishMarked());
 *     }
 *
 *     constexpr UniformDataBlock& operator=(const UniformDataBlock&) = default;
 *
 *     explicit operator bool() const { return !this->empty(); }
 *     bool empty() const { return fData.empty(); }
 *
 *     const char* data() const { return fData.data(); }
 *     size_t size() const { return fData.size(); }
 *
 *     bool operator==(UniformDataBlock that) const {
 *         return this->size() == that.size() &&
 *                (this->data() == that.data() || // Shortcuts the memcmp if the spans are the same
 *                 memcmp(this->data(), that.data(), this->size()) == 0);
 *     }
 *     bool operator!=(UniformDataBlock that) const { return !(*this == that); }
 *
 *     struct Hash {
 *         uint32_t operator()(UniformDataBlock block) const {
 *             return SkChecksum::Hash32(block.fData.data(), block.fData.size_bytes());
 *         }
 *     };
 *
 * private:
 *     // To ensure that the underlying data is actually aligned properly, UniformDataBlocks can
 *     // only be created publicly by copying an existing block or wrapping data accumulated by a
 *     // UniformManager (or transitively a PipelineDataGatherer).
 *     constexpr UniformDataBlock(SkSpan<const char> data) : fData(data) {}
 *
 *     SkSpan<const char> fData;
 * }
 * ```
 */
public data class UniformDataBlock public constructor(
  /**
   * C++ original:
   * ```cpp
   * constexpr UniformDataBlock(SkSpan<const char> data)
   * ```
   */
  private val skSpan: UniformDataBlock,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr UniformDataBlock& operator=(const UniformDataBlock&) = default
   * ```
   */
  public fun assign(param0: UniformDataBlock) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fData.empty(); }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* data() const { return fData.data(); }
   * ```
   */
  public fun `data`(): Char {
    TODO("Implement data")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fData.size(); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(UniformDataBlock that) const {
   *         return this->size() == that.size() &&
   *                (this->data() == that.data() || // Shortcuts the memcmp if the spans are the same
   *                 memcmp(this->data(), that.data(), this->size()) == 0);
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public open class Hash {
    public operator fun invoke(block: UniformDataBlock): Int {
      TODO("Implement invoke")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static UniformDataBlock Make(UniformDataBlock toClone, SkArenaAlloc* arena) {
     *         const char* copy = arena->makeArrayCopy<char>(toClone.fData);
     *         return UniformDataBlock(SkSpan(copy, toClone.size()));
     *     }
     * ```
     */
    public fun make(toClone: UniformDataBlock, arena: SkArenaAlloc?): UniformDataBlock {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static UniformDataBlock Wrap(UniformManager* uniforms) {
     *         return UniformDataBlock(uniforms->finish());
     *     }
     * ```
     */
    public fun wrap(uniforms: UniformManager?): UniformDataBlock {
      TODO("Implement wrap")
    }

    /**
     * C++ original:
     * ```cpp
     * static UniformDataBlock WrapNonShading(UniformManager* uniforms) {
     *         return UniformDataBlock(uniforms->finishMarked());
     *     }
     * ```
     */
    public fun wrapNonShading(uniforms: UniformManager?): UniformDataBlock {
      TODO("Implement wrapNonShading")
    }
  }
}
