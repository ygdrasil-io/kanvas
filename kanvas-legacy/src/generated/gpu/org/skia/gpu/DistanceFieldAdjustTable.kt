package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DistanceFieldAdjustTable {
 * public:
 *     static const DistanceFieldAdjustTable* Get();
 *
 *     ~DistanceFieldAdjustTable() {
 *         delete[] fTable;
 *         delete[] fGammaCorrectTable;
 *     }
 *
 *     SkScalar getAdjustment(int lum, bool useGammaCorrectTable) const {
 *         lum >>= kDistanceAdjustLumShift;
 *         return useGammaCorrectTable ? fGammaCorrectTable[lum] : fTable[lum];
 *     }
 *
 * private:
 *     DistanceFieldAdjustTable();
 *
 *     static constexpr int kDistanceAdjustLumShift = 5;
 *
 *     SkScalar* fTable;
 *     SkScalar* fGammaCorrectTable;
 *
 *     friend class SkNoDestructor<DistanceFieldAdjustTable>;
 * }
 * ```
 */
public data class DistanceFieldAdjustTable public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kDistanceAdjustLumShift = 5
   * ```
   */
  private var fTable: Int?,
  /**
   * C++ original:
   * ```cpp
   * SkScalar* fTable
   * ```
   */
  private var fGammaCorrectTable: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar getAdjustment(int lum, bool useGammaCorrectTable) const {
   *         lum >>= kDistanceAdjustLumShift;
   *         return useGammaCorrectTable ? fGammaCorrectTable[lum] : fTable[lum];
   *     }
   * ```
   */
  public fun getAdjustment(lum: Int, useGammaCorrectTable: Boolean): Int {
    TODO("Implement getAdjustment")
  }

  public companion object {
    private val kDistanceAdjustLumShift: Int = TODO("Initialize kDistanceAdjustLumShift")

    /**
     * C++ original:
     * ```cpp
     * const DistanceFieldAdjustTable* DistanceFieldAdjustTable::Get() {
     *     static const SkNoDestructor<DistanceFieldAdjustTable> dfat;
     *     return dfat.get();
     * }
     * ```
     */
    public fun `get`(): DistanceFieldAdjustTable {
      TODO("Implement get")
    }
  }
}
