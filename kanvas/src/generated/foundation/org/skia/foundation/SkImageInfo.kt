package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkColorSpace
import org.skia.core.SkColorType
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkImageInfo {
 * public:
 *
 *     /** Creates an empty SkImageInfo with kUnknown_SkColorType, kUnknown_SkAlphaType,
 *         a width and height of zero, and no SkColorSpace.
 *
 *         @return  empty SkImageInfo
 *     */
 *     SkImageInfo() = default;
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, SkColorType ct,
 *         SkAlphaType at, and optionally SkColorSpace cs.
 *
 *         If SkColorSpace cs is nullptr and SkImageInfo is part of drawing source: SkColorSpace
 *         defaults to sRGB, mapping into SkSurface SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @param cs      range of colors; may be nullptr
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo Make(int width, int height, SkColorType ct, SkAlphaType at);
 *     static SkImageInfo Make(int width, int height, SkColorType ct, SkAlphaType at,
 *                             sk_sp<SkColorSpace> cs);
 *     static SkImageInfo Make(SkISize dimensions, SkColorType ct, SkAlphaType at);
 *     static SkImageInfo Make(SkISize dimensions, SkColorType ct, SkAlphaType at,
 *                             sk_sp<SkColorSpace> cs);
 *
 *     /** Creates SkImageInfo from integral dimensions and SkColorInfo colorInfo,
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param dimensions   pixel column and row count; must be zeros or greater
 *         @param SkColorInfo  the pixel encoding consisting of SkColorType, SkAlphaType, and
 *                             SkColorSpace (which may be nullptr)
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo Make(SkISize dimensions, const SkColorInfo& colorInfo) {
 *         return SkImageInfo(dimensions, colorInfo);
 *     }
 *     static SkImageInfo Make(SkISize dimensions, SkColorInfo&& colorInfo) {
 *         return SkImageInfo(dimensions, std::move(colorInfo));
 *     }
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kN32_SkColorType,
 *         SkAlphaType at, and optionally SkColorSpace cs. kN32_SkColorType will equal either
 *         kBGRA_8888_SkColorType or kRGBA_8888_SkColorType, whichever is optimal.
 *
 *         If SkColorSpace cs is nullptr and SkImageInfo is part of drawing source: SkColorSpace
 *         defaults to sRGB, mapping into SkSurface SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @param cs      range of colors; may be nullptr
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo MakeN32(int width, int height, SkAlphaType at);
 *     static SkImageInfo MakeN32(int width, int height, SkAlphaType at, sk_sp<SkColorSpace> cs);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kN32_SkColorType,
 *         SkAlphaType at, with sRGB SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @return        created SkImageInfo
 *
 *         example: https://fiddle.skia.org/c/@ImageInfo_MakeS32
 *     */
 *     static SkImageInfo MakeS32(int width, int height, SkAlphaType at);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kN32_SkColorType,
 *         kPremul_SkAlphaType, with optional SkColorSpace.
 *
 *         If SkColorSpace cs is nullptr and SkImageInfo is part of drawing source: SkColorSpace
 *         defaults to sRGB, mapping into SkSurface SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @param cs      range of colors; may be nullptr
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo MakeN32Premul(int width, int height);
 *     static SkImageInfo MakeN32Premul(int width, int height, sk_sp<SkColorSpace> cs);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kN32_SkColorType,
 *         kPremul_SkAlphaType, with SkColorSpace set to nullptr.
 *
 *         If SkImageInfo is part of drawing source: SkColorSpace defaults to sRGB, mapping
 *         into SkSurface SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *
 *         @param dimensions  width and height, each must be zero or greater
 *         @param cs          range of colors; may be nullptr
 *         @return            created SkImageInfo
 *     */
 *     static SkImageInfo MakeN32Premul(SkISize dimensions);
 *     static SkImageInfo MakeN32Premul(SkISize dimensions, sk_sp<SkColorSpace> cs);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kAlpha_8_SkColorType,
 *         kPremul_SkAlphaType, with SkColorSpace set to nullptr.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo MakeA8(int width, int height);
 *     /** Creates SkImageInfo from integral dimensions, kAlpha_8_SkColorType,
 *         kPremul_SkAlphaType, with SkColorSpace set to nullptr.
 *
 *         @param dimensions   pixel row and column count; must be zero or greater
 *         @return             created SkImageInfo
 *     */
 *     static SkImageInfo MakeA8(SkISize dimensions);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height, kUnknown_SkColorType,
 *         kUnknown_SkAlphaType, with SkColorSpace set to nullptr.
 *
 *         Returned SkImageInfo as part of source does not draw, and as part of destination
 *         can not be drawn to.
 *
 *         @param width   pixel column count; must be zero or greater
 *         @param height  pixel row count; must be zero or greater
 *         @return        created SkImageInfo
 *     */
 *     static SkImageInfo MakeUnknown(int width, int height);
 *
 *     /** Creates SkImageInfo from integral dimensions width and height set to zero,
 *         kUnknown_SkColorType, kUnknown_SkAlphaType, with SkColorSpace set to nullptr.
 *
 *         Returned SkImageInfo as part of source does not draw, and as part of destination
 *         can not be drawn to.
 *
 *         @return  created SkImageInfo
 *     */
 *     static SkImageInfo MakeUnknown() {
 *         return MakeUnknown(0, 0);
 *     }
 *
 *     /** Returns pixel count in each row.
 *
 *         @return  pixel width
 *     */
 *     int width() const { return fDimensions.width(); }
 *
 *     /** Returns pixel row count.
 *
 *         @return  pixel height
 *     */
 *     int height() const { return fDimensions.height(); }
 *
 *     SkColorType colorType() const { return fColorInfo.colorType(); }
 *
 *     SkAlphaType alphaType() const { return fColorInfo.alphaType(); }
 *
 *     /** Returns SkColorSpace, the range of colors. The reference count of
 *         SkColorSpace is unchanged. The returned SkColorSpace is immutable.
 *
 *         @return  SkColorSpace, or nullptr
 *     */
 *     SkColorSpace* colorSpace() const;
 *
 *     /** Returns smart pointer to SkColorSpace, the range of colors. The smart pointer
 *         tracks the number of objects sharing this SkColorSpace reference so the memory
 *         is released when the owners destruct.
 *
 *         The returned SkColorSpace is immutable.
 *
 *         @return  SkColorSpace wrapped in a smart pointer
 *     */
 *     sk_sp<SkColorSpace> refColorSpace() const;
 *
 *     /** Returns if SkImageInfo describes an empty area of pixels by checking if either
 *         width or height is zero or smaller.
 *
 *         @return  true if either dimension is zero or smaller
 *     */
 *     bool isEmpty() const { return fDimensions.isEmpty(); }
 *
 *     /** Returns the dimensionless SkColorInfo that represents the same color type,
 *         alpha type, and color space as this SkImageInfo.
 *      */
 *     const SkColorInfo& colorInfo() const { return fColorInfo; }
 *
 *     /** Returns true if SkAlphaType is set to hint that all pixels are opaque; their
 *         alpha value is implicitly or explicitly 1.0. If true, and all pixels are
 *         not opaque, Skia may draw incorrectly.
 *
 *         Does not check if SkColorType allows alpha, or if any pixel value has
 *         transparency.
 *
 *         @return  true if SkAlphaType is kOpaque_SkAlphaType
 *     */
 *     bool isOpaque() const { return fColorInfo.isOpaque(); }
 *
 *     /** Returns SkISize { width(), height() }.
 *
 *         @return  integral size of width() and height()
 *     */
 *     SkISize dimensions() const { return fDimensions; }
 *
 *     /** Returns SkIRect { 0, 0, width(), height() }.
 *
 *         @return  integral rectangle from origin to width() and height()
 *     */
 *     SkIRect bounds() const { return SkIRect::MakeSize(fDimensions); }
 *
 *     /** Returns true if associated SkColorSpace is not nullptr, and SkColorSpace gamma
 *         is approximately the same as sRGB.
 *         This includes the
 *
 *         @return  true if SkColorSpace gamma is approximately the same as sRGB
 *     */
 *     bool gammaCloseToSRGB() const { return fColorInfo.gammaCloseToSRGB(); }
 *
 *     /** Creates SkImageInfo with the same SkColorType, SkColorSpace, and SkAlphaType,
 *         with dimensions set to width and height.
 *
 *         @param newWidth   pixel column count; must be zero or greater
 *         @param newHeight  pixel row count; must be zero or greater
 *         @return           created SkImageInfo
 *     */
 *     SkImageInfo makeWH(int newWidth, int newHeight) const {
 *         return Make({newWidth, newHeight}, fColorInfo);
 *     }
 *
 *     /** Creates SkImageInfo with the same SkColorType, SkColorSpace, and SkAlphaType,
 *         with dimensions set to newDimensions.
 *
 *         @param newSize   pixel column and row count; must be zero or greater
 *         @return          created SkImageInfo
 *     */
 *     SkImageInfo makeDimensions(SkISize newSize) const {
 *         return Make(newSize, fColorInfo);
 *     }
 *
 *     /** Creates SkImageInfo with same SkColorType, SkColorSpace, width, and height,
 *         with SkAlphaType set to newAlphaType.
 *
 *         Created SkImageInfo contains newAlphaType even if it is incompatible with
 *         SkColorType, in which case SkAlphaType in SkImageInfo is ignored.
 *
 *         @return              created SkImageInfo
 *     */
 *     SkImageInfo makeAlphaType(SkAlphaType newAlphaType) const {
 *         return Make(fDimensions, fColorInfo.makeAlphaType(newAlphaType));
 *     }
 *
 *     /** Creates SkImageInfo with same SkAlphaType, SkColorSpace, width, and height,
 *         with SkColorType set to newColorType.
 *
 *         @return              created SkImageInfo
 *     */
 *     SkImageInfo makeColorType(SkColorType newColorType) const {
 *         return Make(fDimensions, fColorInfo.makeColorType(newColorType));
 *     }
 *
 *     /** Creates SkImageInfo with same SkAlphaType, SkColorType, width, and height,
 *         with SkColorSpace set to cs.
 *
 *         @param cs  range of colors; may be nullptr
 *         @return    created SkImageInfo
 *     */
 *     SkImageInfo makeColorSpace(sk_sp<SkColorSpace> cs) const;
 *
 *     /** Returns number of bytes per pixel required by SkColorType.
 *         Returns zero if colorType( is kUnknown_SkColorType.
 *
 *         @return  bytes in pixel
 *     */
 *     int bytesPerPixel() const { return fColorInfo.bytesPerPixel(); }
 *
 *     /** Returns bit shift converting row bytes to row pixels.
 *         Returns zero for kUnknown_SkColorType.
 *
 *         @return  one of: 0, 1, 2, 3; left shift to convert pixels to bytes
 *     */
 *     int shiftPerPixel() const { return fColorInfo.shiftPerPixel(); }
 *
 *     /** Returns minimum bytes per row, computed from pixel width() and SkColorType, which
 *         specifies bytesPerPixel(). SkBitmap maximum value for row bytes must fit
 *         in 31 bits.
 *
 *         @return  width() times bytesPerPixel() as unsigned 64-bit integer
 *     */
 *     uint64_t minRowBytes64() const {
 *         return (uint64_t)sk_64_mul(this->width(), this->bytesPerPixel());
 *     }
 *
 *     /** Returns minimum bytes per row, computed from pixel width() and SkColorType, which
 *         specifies bytesPerPixel(). SkBitmap maximum value for row bytes must fit
 *         in 31 bits.
 *
 *         @return  width() times bytesPerPixel() as size_t
 *     */
 *     size_t minRowBytes() const {
 *         uint64_t minRowBytes = this->minRowBytes64();
 *         if (!SkTFitsIn<int32_t>(minRowBytes)) {
 *             return 0;
 *         }
 *         return (size_t)minRowBytes;
 *     }
 *
 *     /** Returns byte offset of pixel from pixel base address.
 *
 *         Asserts in debug build if x or y is outside of bounds. Does not assert if
 *         rowBytes is smaller than minRowBytes(), even though result may be incorrect.
 *
 *         @param x         column index, zero or greater, and less than width()
 *         @param y         row index, zero or greater, and less than height()
 *         @param rowBytes  size of pixel row or larger
 *         @return          offset within pixel array
 *
 *         example: https://fiddle.skia.org/c/@ImageInfo_computeOffset
 *     */
 *     size_t computeOffset(int x, int y, size_t rowBytes) const;
 *
 *     /** Compares SkImageInfo with other, and returns true if width, height, SkColorType,
 *         SkAlphaType, and SkColorSpace are equivalent.
 *
 *         @param other  SkImageInfo to compare
 *         @return       true if SkImageInfo equals other
 *     */
 *     bool operator==(const SkImageInfo& other) const {
 *         return fDimensions == other.fDimensions && fColorInfo == other.fColorInfo;
 *     }
 *
 *     /** Compares SkImageInfo with other, and returns true if width, height, SkColorType,
 *         SkAlphaType, and SkColorSpace are not equivalent.
 *
 *         @param other  SkImageInfo to compare
 *         @return       true if SkImageInfo is not equal to other
 *     */
 *     bool operator!=(const SkImageInfo& other) const {
 *         return !(*this == other);
 *     }
 *
 *     /** Returns storage required by pixel array, given SkImageInfo dimensions, SkColorType,
 *         and rowBytes. rowBytes is assumed to be at least as large as minRowBytes().
 *
 *         Returns zero if height is zero.
 *         Returns SIZE_MAX if answer exceeds the range of size_t.
 *
 *         @param rowBytes  size of pixel row or larger
 *         @return          memory required by pixel buffer
 *     */
 *     size_t computeByteSize(size_t rowBytes) const;
 *
 *     /** Returns storage required by pixel array, given SkImageInfo dimensions, and
 *         SkColorType. Uses minRowBytes() to compute bytes for pixel row.
 *
 *         Returns zero if height is zero.
 *         Returns SIZE_MAX if answer exceeds the range of size_t.
 *
 *         @return  least memory required by pixel buffer
 *     */
 *     size_t computeMinByteSize() const {
 *         return this->computeByteSize(this->minRowBytes());
 *     }
 *
 *     /** Returns true if byteSize equals SIZE_MAX. computeByteSize() and
 *         computeMinByteSize() return SIZE_MAX if size_t can not hold buffer size.
 *
 *         @param byteSize  result of computeByteSize() or computeMinByteSize()
 *         @return          true if computeByteSize() or computeMinByteSize() result exceeds size_t
 *     */
 *     static bool ByteSizeOverflowed(size_t byteSize) {
 *         return SIZE_MAX == byteSize;
 *     }
 *
 *     /** Returns true if rowBytes is valid for this SkImageInfo.
 *
 *         @param rowBytes  size of pixel row including padding
 *         @return          true if rowBytes is large enough to contain pixel row and is properly
 *                          aligned
 *     */
 *     bool validRowBytes(size_t rowBytes) const {
 *         if (rowBytes < this->minRowBytes64()) {
 *             return false;
 *         }
 *         int shift = this->shiftPerPixel();
 *         size_t alignedRowBytes = rowBytes >> shift << shift;
 *         return alignedRowBytes == rowBytes;
 *     }
 *
 *     /** Creates an empty SkImageInfo with kUnknown_SkColorType, kUnknown_SkAlphaType,
 *         a width and height of zero, and no SkColorSpace.
 *     */
 *     void reset() { *this = {}; }
 *
 *     /** Asserts if internal values are illegal or inconsistent. Only available if
 *         SK_DEBUG is defined at compile time.
 *     */
 *     SkDEBUGCODE(void validate() const;)
 *
 * private:
 *     SkColorInfo fColorInfo;
 *     SkISize fDimensions = {0, 0};
 *
 *     SkImageInfo(SkISize dimensions, const SkColorInfo& colorInfo)
 *             : fColorInfo(colorInfo), fDimensions(dimensions) {}
 *
 *     SkImageInfo(SkISize dimensions, SkColorInfo&& colorInfo)
 *             : fColorInfo(std::move(colorInfo)), fDimensions(dimensions) {}
 * }
 * ```
 */
public data class SkImageInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkISize fDimensions
   * ```
   */
  private var fDimensions: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int width() const { return fDimensions.width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fDimensions.height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fColorInfo.colorType(); }
   * ```
   */
  public fun colorType(): Int {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType alphaType() const { return fColorInfo.alphaType(); }
   * ```
   */
  public fun alphaType(): Int {
    TODO("Implement alphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* SkImageInfo::colorSpace() const { return fColorInfo.colorSpace(); }
   * ```
   */
  public fun colorSpace(): SkColorSpace {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkImageInfo::refColorSpace() const { return fColorInfo.refColorSpace(); }
   * ```
   */
  public fun refColorSpace(): Int {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fDimensions.isEmpty(); }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColorInfo& colorInfo() const { return fColorInfo; }
   * ```
   */
  public fun colorInfo(): SkColorInfo {
    TODO("Implement colorInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const { return fColorInfo.isOpaque(); }
   * ```
   */
  public fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fDimensions; }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect bounds() const { return SkIRect::MakeSize(fDimensions); }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool gammaCloseToSRGB() const { return fColorInfo.gammaCloseToSRGB(); }
   * ```
   */
  public fun gammaCloseToSRGB(): Boolean {
    TODO("Implement gammaCloseToSRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo makeWH(int newWidth, int newHeight) const {
   *         return Make({newWidth, newHeight}, fColorInfo);
   *     }
   * ```
   */
  public fun makeWH(newWidth: Int, newHeight: Int): SkImageInfo {
    TODO("Implement makeWH")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo makeDimensions(SkISize newSize) const {
   *         return Make(newSize, fColorInfo);
   *     }
   * ```
   */
  public fun makeDimensions(newSize: SkISize): SkImageInfo {
    TODO("Implement makeDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo makeAlphaType(SkAlphaType newAlphaType) const {
   *         return Make(fDimensions, fColorInfo.makeAlphaType(newAlphaType));
   *     }
   * ```
   */
  public fun makeAlphaType(newAlphaType: SkAlphaType): SkImageInfo {
    TODO("Implement makeAlphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo makeColorType(SkColorType newColorType) const {
   *         return Make(fDimensions, fColorInfo.makeColorType(newColorType));
   *     }
   * ```
   */
  public fun makeColorType(newColorType: SkColorType): SkImageInfo {
    TODO("Implement makeColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkImageInfo::makeColorSpace(sk_sp<SkColorSpace> cs) const {
   *     return Make(fDimensions, fColorInfo.makeColorSpace(std::move(cs)));
   * }
   * ```
   */
  public fun makeColorSpace(cs: SkSp<SkColorSpace>): SkImageInfo {
    TODO("Implement makeColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * int bytesPerPixel() const { return fColorInfo.bytesPerPixel(); }
   * ```
   */
  public fun bytesPerPixel(): Int {
    TODO("Implement bytesPerPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * int shiftPerPixel() const { return fColorInfo.shiftPerPixel(); }
   * ```
   */
  public fun shiftPerPixel(): Int {
    TODO("Implement shiftPerPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t minRowBytes64() const {
   *         return (uint64_t)sk_64_mul(this->width(), this->bytesPerPixel());
   *     }
   * ```
   */
  public fun minRowBytes64(): ULong {
    TODO("Implement minRowBytes64")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t minRowBytes() const {
   *         uint64_t minRowBytes = this->minRowBytes64();
   *         if (!SkTFitsIn<int32_t>(minRowBytes)) {
   *             return 0;
   *         }
   *         return (size_t)minRowBytes;
   *     }
   * ```
   */
  public fun minRowBytes(): ULong {
    TODO("Implement minRowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkImageInfo::computeOffset(int x, int y, size_t rowBytes) const {
   *     SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, this->width());
   *     SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, this->height());
   *     return SkColorTypeComputeOffset(this->colorType(), x, y, rowBytes);
   * }
   * ```
   */
  public fun computeOffset(
    x: Int,
    y: Int,
    rowBytes: ULong,
  ): ULong {
    TODO("Implement computeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkImageInfo& other) const {
   *         return fDimensions == other.fDimensions && fColorInfo == other.fColorInfo;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkImageInfo& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public fun computeByteSize(rowBytes: ULong): ULong {
    TODO("Implement computeByteSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkImageInfo::computeByteSize(size_t rowBytes) const {
   *     if (0 == this->height()) {
   *         return 0;
   *     }
   *     SkSafeMath safe;
   *     size_t bytes = safe.add(safe.mul(safe.addInt(this->height(), -1), rowBytes),
   *                             safe.mul(this->width(), this->bytesPerPixel()));
   *
   *     // The CPU backend implements some memory operations on images using instructions that take a
   *     // signed 32-bit offset from the base. If we ever make an image larger than that, overflow can
   *     // cause us to read/write memory that starts 2GB *before* the buffer. (crbug.com/1264705)
   *     constexpr size_t kMaxSigned32BitSize = SK_MaxS32;
   *     return (safe.ok() && (bytes <= kMaxSigned32BitSize)) ? bytes : SIZE_MAX;
   * }
   * ```
   */
  public fun computeMinByteSize(): ULong {
    TODO("Implement computeMinByteSize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t computeMinByteSize() const {
   *         return this->computeByteSize(this->minRowBytes());
   *     }
   * ```
   */
  public fun validRowBytes(rowBytes: ULong): Boolean {
    TODO("Implement validRowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * bool validRowBytes(size_t rowBytes) const {
   *         if (rowBytes < this->minRowBytes64()) {
   *             return false;
   *         }
   *         int shift = this->shiftPerPixel();
   *         size_t alignedRowBytes = rowBytes >> shift << shift;
   *         return alignedRowBytes == rowBytes;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() { *this = {}; }
   * ```
   */
  public fun skDEBUGCODE(param0: () -> Unit): Int {
    TODO("Implement skDEBUGCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(void validate() const;)
   * ```
   */
  public fun make(
    width: Int,
    height: Int,
    ct: SkColorType,
    at: SkAlphaType,
  ): SkImageInfo {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkImageInfo::Make(int width, int height, SkColorType ct, SkAlphaType at) {
   *     return Make(width, height, ct, at, nullptr);
   * }
   * ```
   */
  public fun make(
    dimensions: SkISize,
    ct: SkColorType,
    at: SkAlphaType,
    cs: SkSp<SkColorSpace>,
  ): SkImageInfo {
    TODO("Implement make")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkImageInfo::Make(SkISize dimensions, SkColorType ct, SkAlphaType at,
   *                         sk_sp<SkColorSpace> cs) {
   *     return SkImageInfo(dimensions, {ct, at, std::move(cs)});
   * }
   * ```
   */
  public fun makeN32Premul(dimensions: SkISize, cs: SkSp<SkColorSpace>): SkImageInfo {
    TODO("Implement makeN32Premul")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkImageInfo::MakeN32Premul(SkISize dimensions, sk_sp<SkColorSpace> cs) {
   *     return Make(dimensions, kN32_SkColorType, kPremul_SkAlphaType, std::move(cs));
   * }
   * ```
   */
  public fun validate() {
    TODO("Implement validate")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo Make(int width, int height, SkColorType ct, SkAlphaType at)
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      ct: SkColorType,
      at: SkAlphaType,
    ): SkImageInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::Make(int width, int height, SkColorType ct, SkAlphaType at,
     *                               sk_sp<SkColorSpace> cs) {
     *     return SkImageInfo({width, height}, {ct, at, std::move(cs)});
     * }
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      ct: SkColorType,
      at: SkAlphaType,
      cs: SkSp<SkColorSpace>,
    ): SkImageInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::Make(SkISize dimensions, SkColorType ct, SkAlphaType at) {
     *     return Make(dimensions, ct, at, nullptr);
     * }
     * ```
     */
    public fun make(
      dimensions: SkISize,
      ct: SkColorType,
      at: SkAlphaType,
    ): SkImageInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo Make(SkISize dimensions, SkColorType ct, SkAlphaType at,
     *                             sk_sp<SkColorSpace> cs)
     * ```
     */
    public fun make(
      dimensions: SkISize,
      ct: SkColorType,
      at: SkAlphaType,
      cs: SkSp<SkColorSpace>,
    ): SkImageInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo Make(SkISize dimensions, const SkColorInfo& colorInfo) {
     *         return SkImageInfo(dimensions, colorInfo);
     *     }
     * ```
     */
    public fun make(dimensions: SkISize, colorInfo: SkColorInfo): SkImageInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo Make(SkISize dimensions, SkColorInfo&& colorInfo) {
     *         return SkImageInfo(dimensions, std::move(colorInfo));
     *     }
     * ```
     */
    public fun makeN32(
      width: Int,
      height: Int,
      at: SkAlphaType,
    ): SkImageInfo {
      TODO("Implement makeN32")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeN32(int width, int height, SkAlphaType at) {
     *     return MakeN32(width, height, at, nullptr);
     * }
     * ```
     */
    public fun makeN32(
      width: Int,
      height: Int,
      at: SkAlphaType,
      cs: SkSp<SkColorSpace>,
    ): SkImageInfo {
      TODO("Implement makeN32")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeN32(int width, int height, SkAlphaType at, sk_sp<SkColorSpace> cs) {
     *     return Make({width, height}, kN32_SkColorType, at, std::move(cs));
     * }
     * ```
     */
    public fun makeS32(
      width: Int,
      height: Int,
      at: SkAlphaType,
    ): SkImageInfo {
      TODO("Implement makeS32")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeS32(int width, int height, SkAlphaType at) {
     *     return SkImageInfo({width, height}, {kN32_SkColorType, at, SkColorSpace::MakeSRGB()});
     * }
     * ```
     */
    public fun makeN32Premul(width: Int, height: Int): SkImageInfo {
      TODO("Implement makeN32Premul")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeN32Premul(int width, int height) {
     *     return MakeN32Premul(width, height, nullptr);
     * }
     * ```
     */
    public fun makeN32Premul(
      width: Int,
      height: Int,
      cs: SkSp<SkColorSpace>,
    ): SkImageInfo {
      TODO("Implement makeN32Premul")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeN32Premul(int width, int height, sk_sp<SkColorSpace> cs) {
     *     return Make({width, height}, kN32_SkColorType, kPremul_SkAlphaType, std::move(cs));
     * }
     * ```
     */
    public fun makeN32Premul(dimensions: SkISize): SkImageInfo {
      TODO("Implement makeN32Premul")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeN32Premul(SkISize dimensions) {
     *     return MakeN32Premul(dimensions, nullptr);
     * }
     * ```
     */
    public fun makeN32Premul(dimensions: SkISize, cs: SkSp<SkColorSpace>): SkImageInfo {
      TODO("Implement makeN32Premul")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo MakeN32Premul(SkISize dimensions, sk_sp<SkColorSpace> cs)
     * ```
     */
    public fun makeA8(width: Int, height: Int): SkImageInfo {
      TODO("Implement makeA8")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeA8(int width, int height) {
     *     return Make({width, height}, kAlpha_8_SkColorType, kPremul_SkAlphaType, nullptr);
     * }
     * ```
     */
    public fun makeA8(dimensions: SkISize): SkImageInfo {
      TODO("Implement makeA8")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeA8(SkISize dimensions) {
     *     return Make(dimensions, kAlpha_8_SkColorType, kPremul_SkAlphaType, nullptr);
     * }
     * ```
     */
    public fun makeUnknown(width: Int, height: Int): SkImageInfo {
      TODO("Implement makeUnknown")
    }

    /**
     * C++ original:
     * ```cpp
     * SkImageInfo SkImageInfo::MakeUnknown(int width, int height) {
     *     return Make({width, height}, kUnknown_SkColorType, kUnknown_SkAlphaType, nullptr);
     * }
     * ```
     */
    public fun makeUnknown(): SkImageInfo {
      TODO("Implement makeUnknown")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkImageInfo MakeUnknown() {
     *         return MakeUnknown(0, 0);
     *     }
     * ```
     */
    public fun byteSizeOverflowed(byteSize: ULong): Boolean {
      TODO("Implement byteSizeOverflowed")
    }
  }
}
