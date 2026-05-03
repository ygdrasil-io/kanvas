package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkSamplingOptions {
 *     const int              maxAniso = 0;
 *     const bool             useCubic = false;
 *     const SkCubicResampler cubic    = {0, 0};
 *     const SkFilterMode     filter   = SkFilterMode::kNearest;
 *     const SkMipmapMode     mipmap   = SkMipmapMode::kNone;
 *
 *     constexpr SkSamplingOptions() = default;
 *     SkSamplingOptions(const SkSamplingOptions&) = default;
 *     SkSamplingOptions& operator=(const SkSamplingOptions& that) {
 *         this->~SkSamplingOptions();   // A pedantic no-op.
 *         new (this) SkSamplingOptions(that);
 *         return *this;
 *     }
 *
 *     constexpr SkSamplingOptions(SkFilterMode fm, SkMipmapMode mm)
 *         : filter(fm)
 *         , mipmap(mm) {}
 *
 *     // These are intentionally implicit because the single parameter clearly conveys what the
 *     // implicitly created SkSamplingOptions will be.
 *     constexpr SkSamplingOptions(SkFilterMode fm)
 *         : filter(fm)
 *         , mipmap(SkMipmapMode::kNone) {}
 *
 *     constexpr SkSamplingOptions(const SkCubicResampler& c)
 *         : useCubic(true)
 *         , cubic(c) {}
 *
 *     static constexpr SkSamplingOptions Aniso(int maxAniso) {
 *         return SkSamplingOptions{std::max(maxAniso, 1)};
 *     }
 *
 *     bool operator==(const SkSamplingOptions& other) const {
 *         return maxAniso == other.maxAniso
 *             && useCubic == other.useCubic
 *             && cubic.B  == other.cubic.B
 *             && cubic.C  == other.cubic.C
 *             && filter   == other.filter
 *             && mipmap   == other.mipmap;
 *     }
 *     bool operator!=(const SkSamplingOptions& other) const { return !(*this == other); }
 *
 *     bool isAniso() const { return maxAniso != 0; }
 *
 * private:
 *     constexpr SkSamplingOptions(int maxAniso) : maxAniso(maxAniso) {}
 * }
 * ```
 */
public data class SkSamplingOptions public constructor(
  /**
   * C++ original:
   * ```cpp
   * const int              maxAniso = 0
   * ```
   */
  public val maxAniso: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool             useCubic = false
   * ```
   */
  public val useCubic: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const SkCubicResampler cubic    = {0, 0}
   * ```
   */
  public val cubic: SkCubicResampler,
  /**
   * C++ original:
   * ```cpp
   * const SkFilterMode     filter   = SkFilterMode::kNearest
   * ```
   */
  public val filter: SkFilterMode,
  /**
   * C++ original:
   * ```cpp
   * const SkMipmapMode     mipmap   = SkMipmapMode::kNone
   * ```
   */
  public val mipmap: SkMipmapMode,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions& operator=(const SkSamplingOptions& that) {
   *         this->~SkSamplingOptions();   // A pedantic no-op.
   *         new (this) SkSamplingOptions(that);
   *         return *this;
   *     }
   * ```
   */
  public fun assign(that: SkSamplingOptions) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSamplingOptions& other) const {
   *         return maxAniso == other.maxAniso
   *             && useCubic == other.useCubic
   *             && cubic.B  == other.cubic.B
   *             && cubic.C  == other.cubic.C
   *             && filter   == other.filter
   *             && mipmap   == other.mipmap;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSamplingOptions& other) const { return !(*this == other); }
   * ```
   */
  public fun isAniso(): Boolean {
    TODO("Implement isAniso")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkSamplingOptions Aniso(int maxAniso) {
     *         return SkSamplingOptions{std::max(maxAniso, 1)};
     *     }
     * ```
     */
    public fun aniso(maxAniso: Int): SkSamplingOptions {
      TODO("Implement aniso")
    }
  }
}
