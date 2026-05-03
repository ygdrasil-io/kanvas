package org.skia.gpu

import AlphaType
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.SkRasterPipeline
import org.skia.foundation.SkRGBA4f

/**
 * C++ original:
 * ```cpp
 * class Swizzle {
 * public:
 *     // Equivalent to "rgba", but Clang doesn't always manage to inline this
 *     // if we're too deep in the inlining already.
 *     constexpr Swizzle() : Swizzle(0x3210) {}
 *     explicit constexpr Swizzle(const char c[4]);
 *
 *     constexpr Swizzle(const Swizzle&) = default;
 *     constexpr Swizzle& operator=(const Swizzle& that) = default;
 *
 *     static constexpr Swizzle Concat(const Swizzle& a, const Swizzle& b);
 *
 *     constexpr bool operator==(const Swizzle& that) const { return fKey == that.fKey; }
 *     constexpr bool operator!=(const Swizzle& that) const { return !(*this == that); }
 *
 *     /** Compact representation of the swizzle suitable for a key. */
 *     constexpr uint16_t asKey() const { return fKey; }
 *
 *     /** 4 char null terminated string consisting only of chars 'r', 'g', 'b', 'a', '0', and '1'. */
 *     SkString asString() const;
 *
 *     constexpr char operator[](int i) const { return IToC(this->channelIndex(i)); }
 *
 *     // Returns a new swizzle that moves the swizzle component in index i to index 0 (e.g. "R") and
 *     // sets all other channels to 0. For a swizzle `s`, this is constructing "s[i]000".
 *     constexpr Swizzle selectChannelInR(int i) const;
 *
 *     /** Applies this swizzle to the input color and returns the swizzled color. */
 *     constexpr std::array<float, 4> applyTo(std::array<float, 4> color) const;
 *
 *     /** Convenience version for SkRGBA colors. */
 *     template <SkAlphaType AlphaType>
 *     constexpr SkRGBA4f<AlphaType> applyTo(SkRGBA4f<AlphaType> color) const {
 *         std::array<float, 4> result = this->applyTo(color.array());
 *         return {result[0], result[1], result[2], result[3]};
 *     }
 *
 *     void apply(SkRasterPipeline*) const;
 *
 *     static constexpr Swizzle RGBA() { return Swizzle("rgba"); }
 *     static constexpr Swizzle BGRA() { return Swizzle("bgra"); }
 *     static constexpr Swizzle RRRA() { return Swizzle("rrra"); }
 *     static constexpr Swizzle RGB1() { return Swizzle("rgb1"); }
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 *
 * private:
 *     friend class SwizzleCtorAccessor;
 *
 *     explicit constexpr Swizzle(uint16_t key) : fKey(key) {}
 *
 *     constexpr int channelIndex(int i) const {
 *         SkASSERT(i >= 0 && i < 4);
 *         return (fKey >> (4*i)) & 0xfU;
 *     }
 *
 *     static constexpr float ComponentIndexToFloat(std::array<float, 4>, size_t idx);
 *     static constexpr int CToI(char c);
 *     static constexpr char IToC(int idx);
 *
 *     uint16_t fKey;
 *
 *     static_assert(::sk_is_trivially_relocatable<decltype(fKey)>::value);
 * }
 * ```
 */
public data class Swizzle public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint16_t fKey
   * ```
   */
  private var fKey: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr Swizzle& operator=(const Swizzle& that) = default
   * ```
   */
  public fun assign(that: Swizzle) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator==(const Swizzle& that) const { return fKey == that.fKey; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator!=(const Swizzle& that) const { return !(*this == that); }
   * ```
   */
  public fun asKey(): Int {
    TODO("Implement asKey")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr uint16_t asKey() const { return fKey; }
   * ```
   */
  public fun asString(): String {
    TODO("Implement asString")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString Swizzle::asString() const {
   *     char swiz[5];
   *     uint16_t key = fKey;
   *     for (int i = 0; i < 4; ++i) {
   *         swiz[i] = IToC(key & 0xfU);
   *         key >>= 4;
   *     }
   *     swiz[4] = '\0';
   *     return SkString(swiz);
   * }
   * ```
   */
  public operator fun `get`(i: Int): Char {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr char operator[](int i) const { return IToC(this->channelIndex(i)); }
   * ```
   */
  public fun selectChannelInR(i: Int): Swizzle {
    TODO("Implement selectChannelInR")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr Swizzle Swizzle::selectChannelInR(int i) const {
   *     return Swizzle(static_cast<uint16_t>((this->channelIndex(i) << 0) | (CToI('0') << 4) |
   *                                          (CToI('0') << 8) | (CToI('0') << 12)));
   * }
   * ```
   */
  public fun applyTo(color: Array<Float>): Int {
    TODO("Implement applyTo")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr std::array<float, 4> applyTo(std::array<float, 4> color) const
   * ```
   */
  public fun <AlphaType> applyTo(color: SkRGBA4f<AlphaType>): SkRGBA4f<AlphaType> {
    TODO("Implement applyTo")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <SkAlphaType AlphaType>
   *     constexpr SkRGBA4f<AlphaType> applyTo(SkRGBA4f<AlphaType> color) const {
   *         std::array<float, 4> result = this->applyTo(color.array());
   *         return {result[0], result[1], result[2], result[3]};
   *     }
   * ```
   */
  public fun apply(pipeline: SkRasterPipeline?) {
    TODO("Implement apply")
  }

  /**
   * C++ original:
   * ```cpp
   * void Swizzle::apply(SkRasterPipeline* pipeline) const {
   *     SkASSERT(pipeline);
   *     switch (fKey) {
   *         case Swizzle("rgba").asKey():
   *             return;
   *         case Swizzle("bgra").asKey():
   *             pipeline->append(SkRasterPipelineOp::swap_rb);
   *             return;
   *         case Swizzle("aaa1").asKey():
   *             pipeline->append(SkRasterPipelineOp::alpha_to_gray);
   *             return;
   *         case Swizzle("rgb1").asKey():
   *             pipeline->append(SkRasterPipelineOp::force_opaque);
   *             return;
   *         case Swizzle("a001").asKey():
   *             pipeline->append(SkRasterPipelineOp::alpha_to_red);
   *             return;
   *         default: {
   *             static_assert(sizeof(uintptr_t) >= 4 * sizeof(char));
   *             // Rather than allocate the 4 control bytes on the heap somewhere, just jam them right
   *             // into a uintptr_t context.
   *             uintptr_t ctx = {};
   *             memcpy(&ctx, this->asString().c_str(), 4 * sizeof(char));
   *             pipeline->append(SkRasterPipelineOp::swizzle, ctx);
   *             return;
   *         }
   *     }
   * }
   * ```
   */
  private fun channelIndex(i: Int): Int {
    TODO("Implement channelIndex")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * constexpr Swizzle Swizzle::Concat(const Swizzle& a, const Swizzle& b) {
     *     uint16_t key = 0;
     *     for (unsigned i = 0; i < 4; ++i) {
     *         int idx = (b.fKey >> (4U * i)) & 0xfU;
     *         if (idx != CToI('0') && idx != CToI('1')) {
     *             SkASSERT(idx >= 0 && idx < 4);
     *             // Get the index value stored in a at location idx.
     *             idx = ((a.fKey >> (4 * idx)) & 0xfU);
     *         }
     *         key |= (idx << (4U * i));
     *     }
     *     return Swizzle(key);
     * }
     * ```
     */
    public fun concat(a: Swizzle, b: Swizzle): Swizzle {
      TODO("Implement concat")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr Swizzle RGBA() { return Swizzle("rgba"); }
     * ```
     */
    public fun rgba(): Swizzle {
      TODO("Implement rgba")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr Swizzle BGRA() { return Swizzle("bgra"); }
     * ```
     */
    public fun bgra(): Swizzle {
      TODO("Implement bgra")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr Swizzle RRRA() { return Swizzle("rrra"); }
     * ```
     */
    public fun rrra(): Swizzle {
      TODO("Implement rrra")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr Swizzle RGB1() { return Swizzle("rgb1"); }
     * ```
     */
    public fun rgb1(): Swizzle {
      TODO("Implement rgb1")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr float ComponentIndexToFloat(std::array<float, 4>, size_t idx)
     * ```
     */
    private fun componentIndexToFloat(param0: Array<Float>, idx: ULong): Float {
      TODO("Implement componentIndexToFloat")
    }

    /**
     * C++ original:
     * ```cpp
     * constexpr int Swizzle::CToI(char c) {
     *     switch (c) {
     *         // r...a must map to 0...3 because other methods use them as indices into fSwiz.
     *         case 'r': return 0;
     *         case 'g': return 1;
     *         case 'b': return 2;
     *         case 'a': return 3;
     *         case '0': return 4;
     *         case '1': return 5;
     *         default:  SkUNREACHABLE;
     *     }
     * }
     * ```
     */
    private fun cToI(c: Char): Int {
      TODO("Implement cToI")
    }

    /**
     * C++ original:
     * ```cpp
     * constexpr char Swizzle::IToC(int idx) {
     *     switch (idx) {
     *         case CToI('r'): return 'r';
     *         case CToI('g'): return 'g';
     *         case CToI('b'): return 'b';
     *         case CToI('a'): return 'a';
     *         case CToI('0'): return '0';
     *         case CToI('1'): return '1';
     *         default:        SkUNREACHABLE;
     *     }
     * }
     * ```
     */
    private fun iToC(idx: Int): Char {
      TODO("Implement iToC")
    }
  }
}
