package org.skia.gpu

import `Args&`
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * struct VertexWriter : private BufferWriter {
 *     inline constexpr static uint32_t kIEEE_32_infinity = 0x7f800000;
 *
 *     // DEPRECATED: Prefer specifying the size of the buffer being written to as well
 *     explicit VertexWriter(void* ptr) : BufferWriter(ptr, Mark()) {}
 *
 *     BUFFER_WRITER_OVERLOADS(VertexWriter)
 *
 *     using BufferWriter::mark;
 *     using BufferWriter::zeroBytes;
 *
 *     VertexWriter makeOffset(size_t offsetInBytes) const {
 *         return this->BufferWriter::makeOffset<VertexWriter>(offsetInBytes);
 *     }
 *
 *     template <typename T>
 *     struct Conditional {
 *         bool fCondition;
 *         T fValue;
 *     };
 *
 *     template <typename T>
 *     static Conditional<T> If(bool condition, const T& value) {
 *         return {condition, value};
 *     }
 *
 *     template <typename T>
 *     struct Skip {};
 *
 *     template<typename T>
 *     struct ArrayDesc {
 *         const T* fArray;
 *         int fCount;
 *     };
 *
 *     template <typename T>
 *     static ArrayDesc<T> Array(const T* array, int count) {
 *         return {array, count};
 *     }
 *
 *     template<int kCount, typename T>
 *     struct RepeatDesc {
 *         const T& fVal;
 *     };
 *
 *     template <int kCount, typename T>
 *     static RepeatDesc<kCount, T> Repeat(const T& val) {
 *         return {val};
 *     }
 *
 *     /**
 *      * Specialized utilities for writing a four-vertices, with some data being replicated at each
 *      * vertex, and other data being the appropriate 2-components from an SkRect to construct a
 *      * triangle strip.
 *      *
 *      * - Four sets of data will be written
 *      *
 *      * - For any arguments where is_quad<Type>::value is true, a unique point will be written at
 *      *   each vertex. To make a custom type be emitted as a quad, declare:
 *      *
 *      *       template<> struct VertexWriter::is_quad<MyQuadClass> : std::true_type {};
 *      *
 *      *   and define:
 *      *
 *      *       MyQuadClass::writeVertex(int cornerIdx, VertexWriter&) const { ... }
 *      *
 *      * - For any arguments where is_quad<Type>::value is false, its value will be replicated at each
 *      *   vertex.
 *      */
 *     template <typename T>
 *     struct is_quad : std::false_type {};
 *
 *     template <typename T>
 *     struct TriStrip {
 *         void writeVertex(int cornerIdx, VertexWriter& w) const {
 *             switch (cornerIdx) {
 *                 case 0: w << l << t; return;
 *                 case 1: w << l << b; return;
 *                 case 2: w << r << t; return;
 *                 case 3: w << r << b; return;
 *             }
 *             SkUNREACHABLE;
 *         }
 *         T l, t, r, b;
 *     };
 *
 *     static TriStrip<float> TriStripFromRect(const SkRect& r) {
 *         return { r.fLeft, r.fTop, r.fRight, r.fBottom };
 *     }
 *
 *     static TriStrip<uint16_t> TriStripFromUVs(const std::array<uint16_t, 4>& rect) {
 *         return { rect[0], rect[1], rect[2], rect[3] };
 *     }
 *
 *     template <typename T>
 *     struct TriFan {
 *         void writeVertex(int cornerIdx, VertexWriter& w) const {
 *             switch (cornerIdx) {
 *                 case 0: w << l << t; return;
 *                 case 1: w << l << b; return;
 *                 case 2: w << r << b; return;
 *                 case 3: w << r << t; return;
 *             }
 *             SkUNREACHABLE;
 *         }
 *         T l, t, r, b;
 *     };
 *
 *     static TriFan<float> TriFanFromRect(const SkRect& r) {
 *         return { r.fLeft, r.fTop, r.fRight, r.fBottom };
 *     }
 *
 *     template <typename... Args>
 *     void writeQuad(const Args&... remainder) {
 *         this->writeQuadVertex<0>(remainder...);
 *         this->writeQuadVertex<1>(remainder...);
 *         this->writeQuadVertex<2>(remainder...);
 *         this->writeQuadVertex<3>(remainder...);
 *     }
 *
 * private:
 *     template <int kCornerIdx, typename T, typename... Args>
 *     std::enable_if_t<!is_quad<T>::value, void> writeQuadVertex(const T& val,
 *                                                                const Args&... remainder) {
 *         *this << val;  // Non-quads duplicate their value.
 *         this->writeQuadVertex<kCornerIdx>(remainder...);
 *     }
 *
 *     template <int kCornerIdx, typename Q, typename... Args>
 *     std::enable_if_t<is_quad<Q>::value, void> writeQuadVertex(const Q& quad,
 *                                                               const Args&... remainder) {
 *         quad.writeVertex(kCornerIdx, *this);  // Quads emit a different corner each time.
 *         this->writeQuadVertex<kCornerIdx>(remainder...);
 *     }
 *
 *     template <int kCornerIdx>
 *     void writeQuadVertex() {}
 *
 *     template <typename T>
 *     friend VertexWriter& operator<<(VertexWriter&, const T&);
 *
 *     template <typename T>
 *     friend VertexWriter& operator<<(VertexWriter&, const ArrayDesc<T>&);
 * }
 * ```
 */
public open class VertexWriter public constructor(
  ptr: Unit?,
) : BufferWriter(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * VertexWriter makeOffset(size_t offsetInBytes) const {
   *         return this->BufferWriter::makeOffset<VertexWriter>(offsetInBytes);
   *     }
   * ```
   */
  public override fun makeOffset(offsetInBytes: ULong): VertexWriter {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename... Args>
   *     void writeQuad(const Args&... remainder) {
   *         this->writeQuadVertex<0>(remainder...);
   *         this->writeQuadVertex<1>(remainder...);
   *         this->writeQuadVertex<2>(remainder...);
   *         this->writeQuadVertex<3>(remainder...);
   *     }
   * ```
   */
  public fun <Args> writeQuad(remainder: `Args&`) {
    TODO("Implement writeQuad")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <int kCornerIdx, typename T, typename... Args>
   *     std::enable_if_t<!is_quad<T>::value, void> writeQuadVertex(const T& val,
   *                                                                const Args&... remainder) {
   *         *this << val;  // Non-quads duplicate their value.
   *         this->writeQuadVertex<kCornerIdx>(remainder...);
   *     }
   * ```
   */
  private fun <kCornerIdx, T, Args> writeQuadVertex(`val`: T, remainder: `Args&`): Int {
    TODO("Implement writeQuadVertex")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <int kCornerIdx, typename Q, typename... Args>
   *     std::enable_if_t<is_quad<Q>::value, void> writeQuadVertex(const Q& quad,
   *                                                               const Args&... remainder) {
   *         quad.writeVertex(kCornerIdx, *this);  // Quads emit a different corner each time.
   *         this->writeQuadVertex<kCornerIdx>(remainder...);
   *     }
   * ```
   */
  private fun <kCornerIdx, Q, Args> writeQuadVertex(quad: Q, remainder: `Args&`): Int {
    TODO("Implement writeQuadVertex")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <int kCornerIdx>
   *     void writeQuadVertex() {}
   * ```
   */
  private fun <kCornerIdx> writeQuadVertex() {
    TODO("Implement writeQuadVertex")
  }

  public data class Conditional<T> public constructor(
    private var fCondition: Boolean,
    private var fValue: T,
  )

  public open class Skip<T>

  public data class ArrayDesc<T> public constructor(
    private val fArray: T,
    private var fCount: Int,
  )

  public data class RepeatDesc<T> public constructor(
    private val fVal: T,
  )

  public open class IsQuad<T> : Boolean()

  public data class TriStrip<T> public constructor(
    private var l: T,
    private var t: T,
    private var r: T,
    private var b: T,
  ) {
    private fun writeVertex(cornerIdx: Int, w: VertexWriter) {
      TODO("Implement writeVertex")
    }
  }

  public data class TriFan<T> public constructor(
    private var l: T,
    private var t: T,
    private var r: T,
    private var b: T,
  ) {
    private fun writeVertex(cornerIdx: Int, w: VertexWriter) {
      TODO("Implement writeVertex")
    }
  }

  public companion object {
    public val kIEEE32Infinity: Int = TODO("Initialize kIEEE32Infinity")

    /**
     * C++ original:
     * ```cpp
     *     template <typename T>
     *     static Conditional<T> If(bool condition, const T& value) {
     *         return {condition, value};
     *     }
     * ```
     */
    public fun <T> `if`(condition: Boolean, `value`: T): Conditional {
      TODO("Implement if")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename T>
     *     static ArrayDesc<T> Array(const T* array, int count) {
     *         return {array, count};
     *     }
     * ```
     */
    public fun <T> array(array: T?, count: Int): ArrayDesc {
      TODO("Implement array")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <int kCount, typename T>
     *     static RepeatDesc<kCount, T> Repeat(const T& val) {
     *         return {val};
     *     }
     * ```
     */
    public fun <kCount, T> repeat(`val`: T): RepeatDesc {
      TODO("Implement repeat")
    }

    /**
     * C++ original:
     * ```cpp
     * static TriStrip<float> TriStripFromRect(const SkRect& r) {
     *         return { r.fLeft, r.fTop, r.fRight, r.fBottom };
     *     }
     * ```
     */
    public fun triStripFromRect(r: SkRect): TriStrip {
      TODO("Implement triStripFromRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static TriStrip<uint16_t> TriStripFromUVs(const std::array<uint16_t, 4>& rect) {
     *         return { rect[0], rect[1], rect[2], rect[3] };
     *     }
     * ```
     */
    public fun triStripFromUVs(rect: Array<UShort>): Int {
      TODO("Implement triStripFromUVs")
    }

    /**
     * C++ original:
     * ```cpp
     * static TriFan<float> TriFanFromRect(const SkRect& r) {
     *         return { r.fLeft, r.fTop, r.fRight, r.fBottom };
     *     }
     * ```
     */
    public fun triFanFromRect(r: SkRect): TriFan {
      TODO("Implement triFanFromRect")
    }
  }
}
