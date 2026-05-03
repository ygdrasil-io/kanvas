package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct SkMask {
 *     enum Format : uint8_t {
 *         kBW_Format, //!< 1bit per pixel mask (e.g. monochrome)
 *         kA8_Format, //!< 8bits per pixel mask (e.g. antialiasing)
 *         k3D_Format, //!< 3 8bit per pixl planes: alpha, mul, add
 *         kARGB32_Format,         //!< SkPMColor
 *         kLCD16_Format,          //!< 565 alpha for r/g/b
 *         kSDF_Format,            //!< 8bits representing signed distance field
 *     };
 *
 *     enum {
 *         kCountMaskFormats = kSDF_Format + 1
 *     };
 *
 *     SkMask(const uint8_t* img, const SkIRect& bounds, uint32_t rowBytes, Format format)
 *         : fImage(img), fBounds(bounds), fRowBytes(rowBytes), fFormat(format) {}
 *     uint8_t const * const fImage;
 *     const SkIRect fBounds;
 *     const uint32_t fRowBytes;
 *     const Format fFormat;
 *
 *     static bool IsValidFormat(uint8_t format) { return format < kCountMaskFormats; }
 *
 *     /** Returns true if the mask is empty: i.e. it has an empty bounds.
 *      */
 *     bool isEmpty() const { return fBounds.isEmpty(); }
 *
 *     /** Return the byte size of the mask, assuming only 1 plane.
 *         Does not account for k3D_Format. For that, use computeTotalImageSize().
 *         If there is an overflow of 32bits, then returns 0.
 *     */
 *     size_t computeImageSize() const;
 *
 *     /** Return the byte size of the mask, taking into account
 *         any extra planes (e.g. k3D_Format).
 *         If there is an overflow of 32bits, then returns 0.
 *     */
 *     size_t computeTotalImageSize() const;
 *
 *     /** Returns the address of the byte that holds the specified bit.
 *         Asserts that the mask is kBW_Format, and that x,y are in range.
 *         x,y are in the same coordiate space as fBounds.
 *     */
 *     const uint8_t* getAddr1(int x, int y) const {
 *         SkASSERT(kBW_Format == fFormat);
 *         SkASSERT(fBounds.contains(x, y));
 *         SkASSERT(fImage != nullptr);
 *         return fImage + ((x - fBounds.fLeft) >> 3) + (y - fBounds.fTop) * fRowBytes;
 *     }
 *
 *     /** Returns the address of the specified byte.
 *         Asserts that the mask is kA8_Format, and that x,y are in range.
 *         x,y are in the same coordiate space as fBounds.
 *     */
 *     const uint8_t* getAddr8(int x, int y) const {
 *         SkASSERT(kA8_Format == fFormat || kSDF_Format == fFormat);
 *         SkASSERT(fBounds.contains(x, y));
 *         SkASSERT(fImage != nullptr);
 *         return fImage + x - fBounds.fLeft + (y - fBounds.fTop) * fRowBytes;
 *     }
 *
 *     /**
 *      *  Return the address of the specified 16bit mask. In the debug build,
 *      *  this asserts that the mask's format is kLCD16_Format, and that (x,y)
 *      *  are contained in the mask's fBounds.
 *      */
 *     const uint16_t* getAddrLCD16(int x, int y) const {
 *         SkASSERT(kLCD16_Format == fFormat);
 *         SkASSERT(fBounds.contains(x, y));
 *         SkASSERT(fImage != nullptr);
 *         const uint16_t* row = (const uint16_t*)(fImage + (y - fBounds.fTop) * fRowBytes);
 *         return row + (x - fBounds.fLeft);
 *     }
 *
 *     /**
 *      *  Return the address of the specified 32bit mask. In the debug build,
 *      *  this asserts that the mask's format is 32bits, and that (x,y)
 *      *  are contained in the mask's fBounds.
 *      */
 *     const uint32_t* getAddr32(int x, int y) const {
 *         SkASSERT(kARGB32_Format == fFormat);
 *         SkASSERT(fBounds.contains(x, y));
 *         SkASSERT(fImage != nullptr);
 *         const uint32_t* row = (const uint32_t*)(fImage + (y - fBounds.fTop) * fRowBytes);
 *         return row + (x - fBounds.fLeft);
 *     }
 *
 *     /**
 *      *  Returns the address of the specified pixel, computing the pixel-size
 *      *  at runtime based on the mask format. This will be slightly slower than
 *      *  using one of the routines where the format is implied by the name
 *      *  e.g. getAddr8 or getAddr32.
 *      *
 *      *  x,y must be contained by the mask's bounds (this is asserted in the
 *      *  debug build, but not checked in the release build.)
 *      *
 *      *  This should not be called with kBW_Format, as it will give unspecified
 *      *  results (and assert in the debug build).
 *      */
 *     const void* getAddr(int x, int y) const;
 *
 *     /** Iterates over the coverage values along a scanline in a given SkMask::Format. Provides
 *      *  constructor, copy constructor for creating
 *      *  operator++, operator-- for iterating over the coverage values on a scanline
 *      *  operator>>= to add row bytes
 *      *  operator* to get the coverage value at the current location
 *      *  operator< to compare two iterators
 *      */
 *     template <Format F> struct AlphaIter;
 * }
 * ```
 */
public open class SkMask public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t const * const fImage
   * ```
   */
  public val fImage: Int?,
  /**
   * C++ original:
   * ```cpp
   * const SkIRect fBounds
   * ```
   */
  public val fBounds: SkIRect,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fRowBytes
   * ```
   */
  public val fRowBytes: Int,
  /**
   * C++ original:
   * ```cpp
   * const Format fFormat
   * ```
   */
  public val fFormat: Format,
) {
  /**
   * C++ original:
   * ```cpp
   * SkMask(const uint8_t* img, const SkIRect& bounds, uint32_t rowBytes, Format format)
   *         : fImage(img), fBounds(bounds), fRowBytes(rowBytes), fFormat(format) {}
   * ```
   */
  public constructor(
    img: UByte?,
    bounds: SkIRect,
    rowBytes: UInt,
    format: Format,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fBounds.isEmpty(); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMask::computeImageSize() const {
   *     return safeMul32(fBounds.height(), fRowBytes);
   * }
   * ```
   */
  public fun computeImageSize(): Int {
    TODO("Implement computeImageSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkMask::computeTotalImageSize() const {
   *     size_t size = this->computeImageSize();
   *     if (fFormat == SkMask::k3D_Format) {
   *         size = safeMul32(SkToS32(size), 3);
   *     }
   *     return size;
   * }
   * ```
   */
  public fun computeTotalImageSize(): Int {
    TODO("Implement computeTotalImageSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* getAddr1(int x, int y) const {
   *         SkASSERT(kBW_Format == fFormat);
   *         SkASSERT(fBounds.contains(x, y));
   *         SkASSERT(fImage != nullptr);
   *         return fImage + ((x - fBounds.fLeft) >> 3) + (y - fBounds.fTop) * fRowBytes;
   *     }
   * ```
   */
  public fun getAddr1(x: Int, y: Int): Int {
    TODO("Implement getAddr1")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* getAddr8(int x, int y) const {
   *         SkASSERT(kA8_Format == fFormat || kSDF_Format == fFormat);
   *         SkASSERT(fBounds.contains(x, y));
   *         SkASSERT(fImage != nullptr);
   *         return fImage + x - fBounds.fLeft + (y - fBounds.fTop) * fRowBytes;
   *     }
   * ```
   */
  public fun getAddr8(x: Int, y: Int): Int {
    TODO("Implement getAddr8")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* getAddrLCD16(int x, int y) const {
   *         SkASSERT(kLCD16_Format == fFormat);
   *         SkASSERT(fBounds.contains(x, y));
   *         SkASSERT(fImage != nullptr);
   *         const uint16_t* row = (const uint16_t*)(fImage + (y - fBounds.fTop) * fRowBytes);
   *         return row + (x - fBounds.fLeft);
   *     }
   * ```
   */
  public fun getAddrLCD16(x: Int, y: Int): Int {
    TODO("Implement getAddrLCD16")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint32_t* getAddr32(int x, int y) const {
   *         SkASSERT(kARGB32_Format == fFormat);
   *         SkASSERT(fBounds.contains(x, y));
   *         SkASSERT(fImage != nullptr);
   *         const uint32_t* row = (const uint32_t*)(fImage + (y - fBounds.fTop) * fRowBytes);
   *         return row + (x - fBounds.fLeft);
   *     }
   * ```
   */
  public fun getAddr32(x: Int, y: Int): Int {
    TODO("Implement getAddr32")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkMask::getAddr(int x, int y) const {
   *     SkASSERT(kBW_Format != fFormat);
   *     SkASSERT(fBounds.contains(x, y));
   *     SkASSERT(fImage);
   *
   *     const char* addr = (const char*)fImage;
   *     addr += (y - fBounds.fTop) * fRowBytes;
   *     addr += (x - fBounds.fLeft) << maskFormatToShift(fFormat);
   *     return addr;
   * }
   * ```
   */
  public fun getAddr(x: Int, y: Int) {
    TODO("Implement getAddr")
  }

  public enum class Format {
    kBW_Format,
    kA8_Format,
    k3D_Format,
    kARGB32_Format,
    kLCD16_Format,
    kSDF_Format,
  }

  public companion object {
    public val kCountMaskFormats: Int = TODO("Initialize kCountMaskFormats")

    /**
     * C++ original:
     * ```cpp
     * static bool IsValidFormat(uint8_t format) { return format < kCountMaskFormats; }
     * ```
     */
    public fun isValidFormat(format: UByte): Boolean {
      TODO("Implement isValidFormat")
    }
  }
}
