package org.skia.gpu

import kotlin.Int
import org.skia.core.SkSLType

/**
 * C++ original:
 * ```cpp
 * class UniformOffsetCalculator {
 * public:
 *     UniformOffsetCalculator() = default;
 *
 *     static UniformOffsetCalculator ForTopLevel(Layout layout, int offset = 0) {
 *         return UniformOffsetCalculator(layout, offset, /*reqAlignment=*/1);
 *     }
 *
 *     static UniformOffsetCalculator ForStruct(Layout layout) {
 *         const int reqAlignment = LayoutRules::AlignArraysAsVec4(layout) ? 16 : 1;
 *         return UniformOffsetCalculator(layout, /*offset=*/0, reqAlignment);
 *     }
 *
 *     Layout layout() const { return fLayout; }
 *
 *     // NOTE: The returned size represents the last consumed byte, if the recorded
 *     // uniforms are embedded within a struct, this will need to be rounded up to a multiple of
 *     // requiredAlignment().
 *     int size() const { return fOffset; }
 *     int requiredAlignment() const { return fReqAlignment; }
 *
 *     // Returns the correctly aligned offset to accommodate `count` instances of `type` and
 *     // advances the internal offset.
 *     //
 *     // After a call to this method, `size()` will return the offset to the end of `count` instances
 *     // of `type` (while the return value equals the aligned start offset). Subsequent calls will
 *     // calculate the new start offset starting at `size()`.
 *     int advanceOffset(SkSLType type, int count = Uniform::kNonArray);
 *
 *     // Returns the correctly aligned offset to accommodate `count` instances of a custom struct
 *     // type that has had its own fields passed into the `substruct` offset calculator.
 *     //
 *     // After a call to this method, `size()` will return the offset to the end of `count` instances
 *     // of the struct types (while the return value equals the aligned start offset). This includes
 *     // any required padding of the struct size per rule #9.
 *     int advanceStruct(const UniformOffsetCalculator& substruct, int count = Uniform::kNonArray);
 *
 * private:
 *     UniformOffsetCalculator(Layout layout, int offset, int reqAlignment)
 *             : fLayout(layout), fOffset(offset), fReqAlignment((reqAlignment)) {}
 *
 *     Layout fLayout    = Layout::kInvalid;
 *     int fOffset       = 0;
 *     int fReqAlignment = 1;
 * }
 * ```
 */
public abstract class UniformOffsetCalculator public constructor() {
  /**
   * C++ original:
   * ```cpp
   * Layout fLayout
   * ```
   */
  private var fLayout: Int = TODO("Initialize fLayout")

  /**
   * C++ original:
   * ```cpp
   * int fOffset       = 0
   * ```
   */
  private var fOffset: Int = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * int fReqAlignment = 1
   * ```
   */
  private var fReqAlignment: Int = TODO("Initialize fReqAlignment")

  /**
   * C++ original:
   * ```cpp
   * UniformOffsetCalculator() = default
   * ```
   */
  public constructor(
    layout: Layout,
    offset: Int,
    reqAlignment: Int,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Layout layout() const { return fLayout; }
   * ```
   */
  public fun layout(): Int {
    TODO("Implement layout")
  }

  /**
   * C++ original:
   * ```cpp
   * int size() const { return fOffset; }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * int requiredAlignment() const { return fReqAlignment; }
   * ```
   */
  public fun requiredAlignment(): Int {
    TODO("Implement requiredAlignment")
  }

  /**
   * C++ original:
   * ```cpp
   * int UniformOffsetCalculator::advanceOffset(SkSLType type, int count) {
   *     SkASSERT(SkSLTypeCanBeUniformValue(type));
   *
   *     int dimension = SkSLTypeMatrixSize(type);
   *     if (dimension > 0) {
   *         // All SkSL matrices are square and can be interpreted as an array of column vectors
   *         count = std::max(count, 1) * dimension;
   *     } else {
   *         dimension = SkSLTypeVecLength(type);
   *     }
   *     SkASSERT(1 <= dimension && dimension <= 4);
   *
   *     // Bump dimension up to 4 if the array or vec3 consumes 4 primitives per element
   *     // NOTE: This affects the size, alignment already rounds up to a power of 2 automatically.
   *     const bool isArray = count > Uniform::kNonArray;
   *     if ((isArray && LayoutRules::AlignArraysAsVec4(fLayout)) ||
   *         (dimension == 3 && (isArray || LayoutRules::PadVec3Size(fLayout)))) {
   *         dimension = 4;
   *     }
   *
   *     const int primitiveSize = LayoutRules::UseFullPrecision(fLayout) ||
   *                               SkSLTypeIsFullPrecisionNumericType(type) ? 4 : 2;
   *     const int align = SkNextPow2(dimension) * primitiveSize;
   *     const int alignedOffset = SkAlignTo(fOffset, align);
   *     fOffset = alignedOffset + dimension * primitiveSize * std::max(count, 1);
   *     fReqAlignment = std::max(fReqAlignment, align);
   *
   *     return alignedOffset;
   * }
   * ```
   */
  public fun advanceOffset(type: SkSLType, count: Int = TODO()): Int {
    TODO("Implement advanceOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * int UniformOffsetCalculator::advanceStruct(const UniformOffsetCalculator& substruct, int count) {
   *     SkASSERT(substruct.fLayout == fLayout); // Invalid if the layout rules used aren't consistent
   *
   *     // If array element strides are forced to 16-byte alignment, structs must also have their
   *     // base alignment rounded up to 16-byte alignment, which should have been accounted for in
   *     // 'substruct's constructor.
   *     const int baseAlignment = substruct.requiredAlignment();
   *     SkASSERT(!LayoutRules::AlignArraysAsVec4(fLayout) || SkIsAlign16(baseAlignment));
   *
   *     // Per layout rule #9, the struct size must be padded to its base alignment
   *     // (see https://registry.khronos.org/OpenGL/specs/gl/glspec45.core.pdf#page=159).
   *     const int alignedSize = SkAlignTo(substruct.size(), baseAlignment);
   *
   *     const int alignedOffset = SkAlignTo(fOffset, baseAlignment);
   *     fOffset = alignedOffset + alignedSize * std::max(count, 1);
   *     fReqAlignment = std::max(fReqAlignment, baseAlignment);
   *
   *     return alignedOffset;
   * }
   * ```
   */
  public fun advanceStruct(substruct: UniformOffsetCalculator, count: Int = TODO()): Int {
    TODO("Implement advanceStruct")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static UniformOffsetCalculator ForTopLevel(Layout layout, int offset = 0) {
     *         return UniformOffsetCalculator(layout, offset, /*reqAlignment=*/1);
     *     }
     * ```
     */
    public fun forTopLevel(layout: Layout, offset: Int = TODO()): UniformOffsetCalculator {
      TODO("Implement forTopLevel")
    }

    /**
     * C++ original:
     * ```cpp
     * static UniformOffsetCalculator ForStruct(Layout layout) {
     *         const int reqAlignment = LayoutRules::AlignArraysAsVec4(layout) ? 16 : 1;
     *         return UniformOffsetCalculator(layout, /*offset=*/0, reqAlignment);
     *     }
     * ```
     */
    public fun forStruct(layout: Layout): UniformOffsetCalculator {
      TODO("Implement forStruct")
    }
  }
}
