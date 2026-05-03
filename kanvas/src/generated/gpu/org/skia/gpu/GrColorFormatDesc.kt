package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class GrColorFormatDesc {
 * public:
 *     static constexpr GrColorFormatDesc MakeRGBA(int rgba, GrColorTypeEncoding e) {
 *         return {rgba, rgba, rgba, rgba, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeRGBA(int rgb, int a, GrColorTypeEncoding e) {
 *         return {rgb, rgb, rgb, a, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeRGB(int rgb, GrColorTypeEncoding e) {
 *         return {rgb, rgb, rgb, 0, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeRGB(int r, int g, int b, GrColorTypeEncoding e) {
 *         return {r, g, b, 0, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeAlpha(int a, GrColorTypeEncoding e) {
 *         return {0, 0, 0, a, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeR(int r, GrColorTypeEncoding e) {
 *         return {r, 0, 0, 0, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeRG(int rg, GrColorTypeEncoding e) {
 *         return {rg, rg, 0, 0, 0, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeGray(int grayBits, GrColorTypeEncoding e) {
 *         return {0, 0, 0, 0, grayBits, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeGrayAlpha(int grayAlpha, GrColorTypeEncoding e) {
 *         return {0, 0, 0, 0, grayAlpha, e};
 *     }
 *
 *     static constexpr GrColorFormatDesc MakeInvalid() { return {}; }
 *
 *     constexpr int r() const { return fRBits; }
 *     constexpr int g() const { return fGBits; }
 *     constexpr int b() const { return fBBits; }
 *     constexpr int a() const { return fABits; }
 *     constexpr int operator[](int c) const {
 *         switch (c) {
 *             case 0: return this->r();
 *             case 1: return this->g();
 *             case 2: return this->b();
 *             case 3: return this->a();
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     constexpr int gray() const { return fGrayBits; }
 *
 *     constexpr GrColorTypeEncoding encoding() const { return fEncoding; }
 *
 * private:
 *     int fRBits = 0;
 *     int fGBits = 0;
 *     int fBBits = 0;
 *     int fABits = 0;
 *     int fGrayBits = 0;
 *     GrColorTypeEncoding fEncoding = GrColorTypeEncoding::kUnorm;
 *
 *     constexpr GrColorFormatDesc() = default;
 *
 *     constexpr GrColorFormatDesc(int r, int g, int b, int a, int gray, GrColorTypeEncoding encoding)
 *             : fRBits(r), fGBits(g), fBBits(b), fABits(a), fGrayBits(gray), fEncoding(encoding) {
 *         SkASSERT(r >= 0 && g >= 0 && b >= 0 && a >= 0 && gray >= 0);
 *         SkASSERT(!gray || (!r && !g && !b));
 *         SkASSERT(r || g || b || a || gray);
 *     }
 * }
 * ```
 */
public data class GrColorFormatDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fRBits = 0
   * ```
   */
  private var fRBits: Int,
  /**
   * C++ original:
   * ```cpp
   * int fGBits = 0
   * ```
   */
  private var fGBits: Int,
  /**
   * C++ original:
   * ```cpp
   * int fBBits = 0
   * ```
   */
  private var fBBits: Int,
  /**
   * C++ original:
   * ```cpp
   * int fABits = 0
   * ```
   */
  private var fABits: Int,
  /**
   * C++ original:
   * ```cpp
   * int fGrayBits = 0
   * ```
   */
  private var fGrayBits: Int,
  /**
   * C++ original:
   * ```cpp
   * GrColorTypeEncoding fEncoding = GrColorTypeEncoding::kUnorm
   * ```
   */
  private var fEncoding: GrColorTypeEncoding,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr int r() const { return fRBits; }
   * ```
   */
  public fun r(): Int {
    TODO("Implement r")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int g() const { return fGBits; }
   * ```
   */
  public fun g(): Int {
    TODO("Implement g")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int b() const { return fBBits; }
   * ```
   */
  public fun b(): Int {
    TODO("Implement b")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int a() const { return fABits; }
   * ```
   */
  public fun a(): Int {
    TODO("Implement a")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int operator[](int c) const {
   *         switch (c) {
   *             case 0: return this->r();
   *             case 1: return this->g();
   *             case 2: return this->b();
   *             case 3: return this->a();
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public operator fun `get`(c: Int): Int {
    TODO("Implement get")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int gray() const { return fGrayBits; }
   * ```
   */
  public fun gray(): Int {
    TODO("Implement gray")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr GrColorTypeEncoding encoding() const { return fEncoding; }
   * ```
   */
  public fun encoding(): GrColorTypeEncoding {
    TODO("Implement encoding")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeRGBA(int rgba, GrColorTypeEncoding e) {
     *         return {rgba, rgba, rgba, rgba, 0, e};
     *     }
     * ```
     */
    public fun makeRGBA(rgba: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeRGBA")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeRGBA(int rgb, int a, GrColorTypeEncoding e) {
     *         return {rgb, rgb, rgb, a, 0, e};
     *     }
     * ```
     */
    public fun makeRGBA(
      rgb: Int,
      a: Int,
      e: GrColorTypeEncoding,
    ): GrColorFormatDesc {
      TODO("Implement makeRGBA")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeRGB(int rgb, GrColorTypeEncoding e) {
     *         return {rgb, rgb, rgb, 0, 0, e};
     *     }
     * ```
     */
    public fun makeRGB(rgb: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeRGB")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeRGB(int r, int g, int b, GrColorTypeEncoding e) {
     *         return {r, g, b, 0, 0, e};
     *     }
     * ```
     */
    public fun makeRGB(
      r: Int,
      g: Int,
      b: Int,
      e: GrColorTypeEncoding,
    ): GrColorFormatDesc {
      TODO("Implement makeRGB")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeAlpha(int a, GrColorTypeEncoding e) {
     *         return {0, 0, 0, a, 0, e};
     *     }
     * ```
     */
    public fun makeAlpha(a: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeAlpha")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeR(int r, GrColorTypeEncoding e) {
     *         return {r, 0, 0, 0, 0, e};
     *     }
     * ```
     */
    public fun makeR(r: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeR")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeRG(int rg, GrColorTypeEncoding e) {
     *         return {rg, rg, 0, 0, 0, e};
     *     }
     * ```
     */
    public fun makeRG(rg: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeRG")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeGray(int grayBits, GrColorTypeEncoding e) {
     *         return {0, 0, 0, 0, grayBits, e};
     *     }
     * ```
     */
    public fun makeGray(grayBits: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeGray")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeGrayAlpha(int grayAlpha, GrColorTypeEncoding e) {
     *         return {0, 0, 0, 0, grayAlpha, e};
     *     }
     * ```
     */
    public fun makeGrayAlpha(grayAlpha: Int, e: GrColorTypeEncoding): GrColorFormatDesc {
      TODO("Implement makeGrayAlpha")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr GrColorFormatDesc MakeInvalid() { return {}; }
     * ```
     */
    public fun makeInvalid(): GrColorFormatDesc {
      TODO("Implement makeInvalid")
    }
  }
}
