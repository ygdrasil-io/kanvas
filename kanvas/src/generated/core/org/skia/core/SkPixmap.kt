package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.math.SkIRect
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPixmap {
 * public:
 *
 *     /** Creates an empty SkPixmap without pixels, with kUnknown_SkColorType, with
 *         kUnknown_SkAlphaType, and with a width and height of zero. Use
 *         reset() to associate pixels, SkColorType, SkAlphaType, width, and height
 *         after SkPixmap has been created.
 *
 *         @return  empty SkPixmap
 *     */
 *     SkPixmap()
 *         : fPixels(nullptr), fRowBytes(0), fInfo(SkImageInfo::MakeUnknown(0, 0))
 *     {}
 *
 *     /** Creates SkPixmap from info width, height, SkAlphaType, and SkColorType.
 *         addr points to pixels, or nullptr. rowBytes should be info.width() times
 *         info.bytesPerPixel(), or larger.
 *
 *         No parameter checking is performed; it is up to the caller to ensure that
 *         addr and rowBytes agree with info.
 *
 *         The memory lifetime of pixels is managed by the caller. When SkPixmap goes
 *         out of scope, addr is unaffected.
 *
 *         SkPixmap may be later modified by reset() to change its size, pixel type, or
 *         storage.
 *
 *         @param info      width, height, SkAlphaType, SkColorType of SkImageInfo
 *         @param addr      pointer to pixels allocated by caller; may be nullptr
 *         @param rowBytes  size of one row of addr; width times pixel size, or larger
 *         @return          initialized SkPixmap
 *     */
 *     SkPixmap(const SkImageInfo& info, const void* addr, size_t rowBytes)
 *         : fPixels(addr), fRowBytes(rowBytes), fInfo(info)
 *     {}
 *
 *     /** Sets width, height, row bytes to zero; pixel address to nullptr; SkColorType to
 *         kUnknown_SkColorType; and SkAlphaType to kUnknown_SkAlphaType.
 *
 *         The prior pixels are unaffected; it is up to the caller to release pixels
 *         memory if desired.
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_reset
 *     */
 *     void reset();
 *
 *     /** Sets width, height, SkAlphaType, and SkColorType from info.
 *         Sets pixel address from addr, which may be nullptr.
 *         Sets row bytes from rowBytes, which should be info.width() times
 *         info.bytesPerPixel(), or larger.
 *
 *         Does not check addr. Asserts if built with SK_DEBUG defined and if rowBytes is
 *         too small to hold one row of pixels.
 *
 *         The memory lifetime pixels are managed by the caller. When SkPixmap goes
 *         out of scope, addr is unaffected.
 *
 *         @param info      width, height, SkAlphaType, SkColorType of SkImageInfo
 *         @param addr      pointer to pixels allocated by caller; may be nullptr
 *         @param rowBytes  size of one row of addr; width times pixel size, or larger
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_reset_2
 *     */
 *     void reset(const SkImageInfo& info, const void* addr, size_t rowBytes);
 *
 *     /** Changes SkColorSpace in SkImageInfo; preserves width, height, SkAlphaType, and
 *         SkColorType in SkImage, and leaves pixel address and row bytes unchanged.
 *         SkColorSpace reference count is incremented.
 *
 *         @param colorSpace  SkColorSpace moved to SkImageInfo
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_setColorSpace
 *     */
 *     void setColorSpace(sk_sp<SkColorSpace> colorSpace);
 *
 *     /** Deprecated.
 *     */
 *     [[nodiscard]] bool reset(const SkMask& mask);
 *
 *     /** Sets subset width, height, pixel address to intersection of SkPixmap with area,
 *         if intersection is not empty; and return true. Otherwise, leave subset unchanged
 *         and return false.
 *
 *         Failing to read the return value generates a compile time warning.
 *
 *         @param subset  storage for width, height, pixel address of intersection
 *         @param area    bounds to intersect with SkPixmap
 *         @return        true if intersection of SkPixmap and area is not empty
 *     */
 *     [[nodiscard]] bool extractSubset(SkPixmap* subset, const SkIRect& area) const;
 *
 *     /** Returns width, height, SkAlphaType, SkColorType, and SkColorSpace.
 *
 *         @return  reference to SkImageInfo
 *     */
 *     const SkImageInfo& info() const { return fInfo; }
 *
 *     /** Returns row bytes, the interval from one pixel row to the next. Row bytes
 *         is at least as large as: width() * info().bytesPerPixel().
 *
 *         Returns zero if colorType() is kUnknown_SkColorType.
 *         It is up to the SkBitmap creator to ensure that row bytes is a useful value.
 *
 *         @return  byte length of pixel row
 *     */
 *     size_t rowBytes() const { return fRowBytes; }
 *
 *     /** Returns pixel address, the base address corresponding to the pixel origin.
 *
 *         It is up to the SkPixmap creator to ensure that pixel address is a useful value.
 *
 *         @return  pixel address
 *     */
 *     const void* addr() const { return fPixels; }
 *
 *     /** Returns pixel count in each pixel row. Should be equal or less than:
 *         rowBytes() / info().bytesPerPixel().
 *
 *         @return  pixel width in SkImageInfo
 *     */
 *     int width() const { return fInfo.width(); }
 *
 *     /** Returns pixel row count.
 *
 *         @return  pixel height in SkImageInfo
 *     */
 *     int height() const { return fInfo.height(); }
 *
 *     /**
 *      *  Return the dimensions of the pixmap (from its ImageInfo)
 *      */
 *     SkISize dimensions() const { return fInfo.dimensions(); }
 *
 *     SkColorType colorType() const { return fInfo.colorType(); }
 *
 *     SkAlphaType alphaType() const { return fInfo.alphaType(); }
 *
 *     /** Returns SkColorSpace, the range of colors, associated with SkImageInfo. The
 *         reference count of SkColorSpace is unchanged. The returned SkColorSpace is
 *         immutable.
 *
 *         @return  SkColorSpace in SkImageInfo, or nullptr
 *     */
 *     SkColorSpace* colorSpace() const;
 *
 *     /** Returns smart pointer to SkColorSpace, the range of colors, associated with
 *         SkImageInfo. The smart pointer tracks the number of objects sharing this
 *         SkColorSpace reference so the memory is released when the owners destruct.
 *
 *         The returned SkColorSpace is immutable.
 *
 *         @return  SkColorSpace in SkImageInfo wrapped in a smart pointer
 *     */
 *     sk_sp<SkColorSpace> refColorSpace() const;
 *
 *     /** Returns true if SkAlphaType is kOpaque_SkAlphaType.
 *         Does not check if SkColorType allows alpha, or if any pixel value has
 *         transparency.
 *
 *         @return  true if SkImageInfo has opaque SkAlphaType
 *     */
 *     bool isOpaque() const { return fInfo.isOpaque(); }
 *
 *     /** Returns SkIRect { 0, 0, width(), height() }.
 *
 *         @return  integral rectangle from origin to width() and height()
 *     */
 *     SkIRect bounds() const { return SkIRect::MakeWH(this->width(), this->height()); }
 *
 *     /** Returns number of pixels that fit on row. Should be greater than or equal to
 *         width().
 *
 *         @return  maximum pixels per row
 *     */
 *     int rowBytesAsPixels() const { return int(fRowBytes >> this->shiftPerPixel()); }
 *
 *     /** Returns bit shift converting row bytes to row pixels.
 *         Returns zero for kUnknown_SkColorType.
 *
 *         @return  one of: 0, 1, 2, 3; left shift to convert pixels to bytes
 *     */
 *     int shiftPerPixel() const { return fInfo.shiftPerPixel(); }
 *
 *     /** Returns minimum memory required for pixel storage.
 *         Does not include unused memory on last row when rowBytesAsPixels() exceeds width().
 *         Returns SIZE_MAX if result does not fit in size_t.
 *         Returns zero if height() or width() is 0.
 *         Returns height() times rowBytes() if colorType() is kUnknown_SkColorType.
 *
 *         @return  size in bytes of image buffer
 *     */
 *     size_t computeByteSize() const { return fInfo.computeByteSize(fRowBytes); }
 *
 *     /** Returns true if all pixels are opaque. SkColorType determines how pixels
 *         are encoded, and whether pixel describes alpha. Returns true for SkColorType
 *         without alpha in each pixel; for other SkColorType, returns true if all
 *         pixels have alpha values equivalent to 1.0 or greater.
 *
 *         For SkColorType kRGB_565_SkColorType or kGray_8_SkColorType: always
 *         returns true. For SkColorType kAlpha_8_SkColorType, kBGRA_8888_SkColorType,
 *         kRGBA_8888_SkColorType: returns true if all pixel alpha values are 255.
 *         For SkColorType kARGB_4444_SkColorType: returns true if all pixel alpha values are 15.
 *         For kRGBA_F16_SkColorType: returns true if all pixel alpha values are 1.0 or
 *         greater.
 *
 *         Returns false for kUnknown_SkColorType.
 *
 *         @return  true if all pixels have opaque values or SkColorType is opaque
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_computeIsOpaque
 *     */
 *     bool computeIsOpaque() const;
 *
 *     /** Returns pixel at (x, y) as unpremultiplied color.
 *         Returns black with alpha if SkColorType is kAlpha_8_SkColorType.
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined; and returns undefined values or may crash if
 *         SK_RELEASE is defined. Fails if SkColorType is kUnknown_SkColorType or
 *         pixel address is nullptr.
 *
 *         SkColorSpace in SkImageInfo is ignored. Some color precision may be lost in the
 *         conversion to unpremultiplied color; original pixel data may have additional
 *         precision.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   pixel converted to unpremultiplied color
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_getColor
 *     */
 *     SkColor getColor(int x, int y) const;
 *
 *     /** Returns pixel at (x, y) as unpremultiplied color as an SkColor4f.
 *         Returns black with alpha if SkColorType is kAlpha_8_SkColorType.
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined; and returns undefined values or may crash if
 *         SK_RELEASE is defined. Fails if SkColorType is kUnknown_SkColorType or
 *         pixel address is nullptr.
 *
 *         SkColorSpace in SkImageInfo is ignored. Some color precision may be lost in the
 *         conversion to unpremultiplied color; original pixel data may have additional
 *         precision, though this is less likely than for getColor(). Rounding errors may
 *         occur if the underlying type has lower precision.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   pixel converted to unpremultiplied float color
 *     */
 *     SkColor4f getColor4f(int x, int y) const;
 *
 *     /** Look up the pixel at (x,y) and return its alpha component, normalized to [0..1].
 *         This is roughly equivalent to SkGetColorA(getColor()), but can be more efficent
 *         (and more precise if the pixels store more than 8 bits per component).
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   alpha converted to normalized float
 *      */
 *     float getAlphaf(int x, int y) const;
 *
 *     /** Returns readable pixel address at (x, y). Returns nullptr if SkPixelRef is nullptr.
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined. Returns nullptr if SkColorType is kUnknown_SkColorType.
 *
 *         Performs a lookup of pixel size; for better performance, call
 *         one of: addr8, addr16, addr32, addr64, or addrF16().
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable generic pointer to pixel
 *     */
 *     const void* addr(int x, int y) const {
 *         return (const char*)fPixels + fInfo.computeOffset(x, y, fRowBytes);
 *     }
 *
 *     /** Returns readable base pixel address. Result is addressable as unsigned 8-bit bytes.
 *         Will trigger an assert() if SkColorType is not kAlpha_8_SkColorType or
 *         kGray_8_SkColorType, and is built with SK_DEBUG defined.
 *
 *         One byte corresponds to one pixel.
 *
 *         @return  readable unsigned 8-bit pointer to pixels
 *     */
 *     const uint8_t* addr8() const {
 *         SkASSERT(1 == fInfo.bytesPerPixel());
 *         return reinterpret_cast<const uint8_t*>(fPixels);
 *     }
 *
 *     /** Returns readable base pixel address. Result is addressable as unsigned 16-bit words.
 *         Will trigger an assert() if SkColorType is not kRGB_565_SkColorType or
 *         kARGB_4444_SkColorType, and is built with SK_DEBUG defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @return  readable unsigned 16-bit pointer to pixels
 *     */
 *     const uint16_t* addr16() const {
 *         SkASSERT(2 == fInfo.bytesPerPixel());
 *         return reinterpret_cast<const uint16_t*>(fPixels);
 *     }
 *
 *     /** Returns readable base pixel address. Result is addressable as unsigned 32-bit words.
 *         Will trigger an assert() if SkColorType is not kRGBA_8888_SkColorType or
 *         kBGRA_8888_SkColorType, and is built with SK_DEBUG defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @return  readable unsigned 32-bit pointer to pixels
 *     */
 *     const uint32_t* addr32() const {
 *         SkASSERT(4 == fInfo.bytesPerPixel());
 *         return reinterpret_cast<const uint32_t*>(fPixels);
 *     }
 *
 *     /** Returns readable base pixel address. Result is addressable as unsigned 64-bit words.
 *         Will trigger an assert() if SkColorType is not kRGBA_F16_SkColorType and is built
 *         with SK_DEBUG defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @return  readable unsigned 64-bit pointer to pixels
 *     */
 *     const uint64_t* addr64() const {
 *         SkASSERT(8 == fInfo.bytesPerPixel());
 *         return reinterpret_cast<const uint64_t*>(fPixels);
 *     }
 *
 *     /** Returns readable base pixel address. Result is addressable as unsigned 16-bit words.
 *         Will trigger an assert() if SkColorType is not kRGBA_F16_SkColorType and is built
 *         with SK_DEBUG defined.
 *
 *         Each word represents one color component encoded as a half float.
 *         Four words correspond to one pixel.
 *
 *         @return  readable unsigned 16-bit pointer to first component of pixels
 *     */
 *     const uint16_t* addrF16() const {
 *         SkASSERT(8 == fInfo.bytesPerPixel());
 *         SkASSERT(kRGBA_F16_SkColorType     == fInfo.colorType() ||
 *                  kRGBA_F16Norm_SkColorType == fInfo.colorType());
 *         return reinterpret_cast<const uint16_t*>(fPixels);
 *     }
 *
 *     /** Returns readable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined.
 *
 *         Will trigger an assert() if SkColorType is not kAlpha_8_SkColorType or
 *         kGray_8_SkColorType, and is built with SK_DEBUG defined.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable unsigned 8-bit pointer to pixel at (x, y)
 *     */
 *     const uint8_t* addr8(int x, int y) const {
 *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
 *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
 *         return (const uint8_t*)((const char*)this->addr8() + (size_t)y * fRowBytes + (x << 0));
 *     }
 *
 *     /** Returns readable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined.
 *
 *         Will trigger an assert() if SkColorType is not kRGB_565_SkColorType or
 *         kARGB_4444_SkColorType, and is built with SK_DEBUG defined.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable unsigned 16-bit pointer to pixel at (x, y)
 *     */
 *     const uint16_t* addr16(int x, int y) const {
 *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
 *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
 *         return (const uint16_t*)((const char*)this->addr16() + (size_t)y * fRowBytes + (x << 1));
 *     }
 *
 *     /** Returns readable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined.
 *
 *         Will trigger an assert() if SkColorType is not kRGBA_8888_SkColorType or
 *         kBGRA_8888_SkColorType, and is built with SK_DEBUG defined.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable unsigned 32-bit pointer to pixel at (x, y)
 *     */
 *     const uint32_t* addr32(int x, int y) const {
 *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
 *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
 *         return (const uint32_t*)((const char*)this->addr32() + (size_t)y * fRowBytes + (x << 2));
 *     }
 *
 *     /** Returns readable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined.
 *
 *         Will trigger an assert() if SkColorType is not kRGBA_F16_SkColorType and is built
 *         with SK_DEBUG defined.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable unsigned 64-bit pointer to pixel at (x, y)
 *     */
 *     const uint64_t* addr64(int x, int y) const {
 *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
 *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
 *         return (const uint64_t*)((const char*)this->addr64() + (size_t)y * fRowBytes + (x << 3));
 *     }
 *
 *     /** Returns readable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined.
 *
 *         Will trigger an assert() if SkColorType is not kRGBA_F16_SkColorType and is built
 *         with SK_DEBUG defined.
 *
 *         Each unsigned 16-bit word represents one color component encoded as a half float.
 *         Four words correspond to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   readable unsigned 16-bit pointer to pixel component at (x, y)
 *     */
 *     const uint16_t* addrF16(int x, int y) const {
 *         SkASSERT(kRGBA_F16_SkColorType     == fInfo.colorType() ||
 *                  kRGBA_F16Norm_SkColorType == fInfo.colorType());
 *         return reinterpret_cast<const uint16_t*>(this->addr64(x, y));
 *     }
 *
 *     /** Returns writable base pixel address.
 *
 *         @return  writable generic base pointer to pixels
 *     */
 *     void* writable_addr() const { return const_cast<void*>(fPixels); }
 *
 *     /** Returns writable pixel address at (x, y).
 *
 *         Input is not validated: out of bounds values of x or y trigger an assert() if
 *         built with SK_DEBUG defined. Returns zero if SkColorType is kUnknown_SkColorType.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable generic pointer to pixel
 *     */
 *     void* writable_addr(int x, int y) const {
 *         return const_cast<void*>(this->addr(x, y));
 *     }
 *
 *     /** Returns writable pixel address at (x, y). Result is addressable as unsigned
 *         8-bit bytes. Will trigger an assert() if SkColorType is not kAlpha_8_SkColorType
 *         or kGray_8_SkColorType, and is built with SK_DEBUG defined.
 *
 *         One byte corresponds to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable unsigned 8-bit pointer to pixels
 *     */
 *     uint8_t* writable_addr8(int x, int y) const {
 *         return const_cast<uint8_t*>(this->addr8(x, y));
 *     }
 *
 *     /** Returns writable_addr pixel address at (x, y). Result is addressable as unsigned
 *         16-bit words. Will trigger an assert() if SkColorType is not kRGB_565_SkColorType
 *         or kARGB_4444_SkColorType, and is built with SK_DEBUG defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable unsigned 16-bit pointer to pixel
 *     */
 *     uint16_t* writable_addr16(int x, int y) const {
 *         return const_cast<uint16_t*>(this->addr16(x, y));
 *     }
 *
 *     /** Returns writable pixel address at (x, y). Result is addressable as unsigned
 *         32-bit words. Will trigger an assert() if SkColorType is not
 *         kRGBA_8888_SkColorType or kBGRA_8888_SkColorType, and is built with SK_DEBUG
 *         defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable unsigned 32-bit pointer to pixel
 *     */
 *     uint32_t* writable_addr32(int x, int y) const {
 *         return const_cast<uint32_t*>(this->addr32(x, y));
 *     }
 *
 *     /** Returns writable pixel address at (x, y). Result is addressable as unsigned
 *         64-bit words. Will trigger an assert() if SkColorType is not
 *         kRGBA_F16_SkColorType and is built with SK_DEBUG defined.
 *
 *         One word corresponds to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable unsigned 64-bit pointer to pixel
 *     */
 *     uint64_t* writable_addr64(int x, int y) const {
 *         return const_cast<uint64_t*>(this->addr64(x, y));
 *     }
 *
 *     /** Returns writable pixel address at (x, y). Result is addressable as unsigned
 *         16-bit words. Will trigger an assert() if SkColorType is not
 *         kRGBA_F16_SkColorType and is built with SK_DEBUG defined.
 *
 *         Each word represents one color component encoded as a half float.
 *         Four words correspond to one pixel.
 *
 *         @param x  column index, zero or greater, and less than width()
 *         @param y  row index, zero or greater, and less than height()
 *         @return   writable unsigned 16-bit pointer to first component of pixel
 *     */
 *     uint16_t* writable_addrF16(int x, int y) const {
 *         return reinterpret_cast<uint16_t*>(writable_addr64(x, y));
 *     }
 *
 *     /** Copies a SkRect of pixels to dstPixels. Copy starts at (0, 0), and does not
 *         exceed SkPixmap (width(), height()).
 *
 *         dstInfo specifies width, height, SkColorType, SkAlphaType, and
 *         SkColorSpace of destination. dstRowBytes specifics the gap from one destination
 *         row to the next. Returns true if pixels are copied. Returns false if
 *         dstInfo address equals nullptr, or dstRowBytes is less than dstInfo.minRowBytes().
 *
 *         Pixels are copied only if pixel conversion is possible. If SkPixmap colorType() is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dstInfo.colorType() must match.
 *         If SkPixmap colorType() is kGray_8_SkColorType, dstInfo.colorSpace() must match.
 *         If SkPixmap alphaType() is kOpaque_SkAlphaType, dstInfo.alphaType() must
 *         match. If SkPixmap colorSpace() is nullptr, dstInfo.colorSpace() must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         Returns false if SkPixmap width() or height() is zero or negative.
 *
 *         @param dstInfo      destination width, height, SkColorType, SkAlphaType, SkColorSpace
 *         @param dstPixels    destination pixel storage
 *         @param dstRowBytes  destination row length
 *         @return             true if pixels are copied to dstPixels
 *     */
 *     bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes) const {
 *         return this->readPixels(dstInfo, dstPixels, dstRowBytes, 0, 0);
 *     }
 *
 *     /** Copies a SkRect of pixels to dstPixels. Copy starts at (srcX, srcY), and does not
 *         exceed SkPixmap (width(), height()).
 *
 *         dstInfo specifies width, height, SkColorType, SkAlphaType, and
 *         SkColorSpace of destination. dstRowBytes specifics the gap from one destination
 *         row to the next. Returns true if pixels are copied. Returns false if
 *         dstInfo address equals nullptr, or dstRowBytes is less than dstInfo.minRowBytes().
 *
 *         Pixels are copied only if pixel conversion is possible. If SkPixmap colorType() is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dstInfo.colorType() must match.
 *         If SkPixmap colorType() is kGray_8_SkColorType, dstInfo.colorSpace() must match.
 *         If SkPixmap alphaType() is kOpaque_SkAlphaType, dstInfo.alphaType() must
 *         match. If SkPixmap colorSpace() is nullptr, dstInfo.colorSpace() must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         srcX and srcY may be negative to copy only top or left of source. Returns
 *         false if SkPixmap width() or height() is zero or negative. Returns false if:
 *         abs(srcX) >= Pixmap width(), or if abs(srcY) >= Pixmap height().
 *
 *         @param dstInfo      destination width, height, SkColorType, SkAlphaType, SkColorSpace
 *         @param dstPixels    destination pixel storage
 *         @param dstRowBytes  destination row length
 *         @param srcX         column index whose absolute value is less than width()
 *         @param srcY         row index whose absolute value is less than height()
 *         @return             true if pixels are copied to dstPixels
 *     */
 *     bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes, int srcX,
 *                     int srcY) const;
 *
 *     /** Copies a SkRect of pixels to dst. Copy starts at (srcX, srcY), and does not
 *         exceed SkPixmap (width(), height()). dst specifies width, height, SkColorType,
 *         SkAlphaType, and SkColorSpace of destination.  Returns true if pixels are copied.
 *         Returns false if dst address equals nullptr, or dst.rowBytes() is less than
 *         dst SkImageInfo::minRowBytes.
 *
 *         Pixels are copied only if pixel conversion is possible. If SkPixmap colorType() is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dst.info().colorType must match.
 *         If SkPixmap colorType() is kGray_8_SkColorType, dst.info().colorSpace must match.
 *         If SkPixmap alphaType() is kOpaque_SkAlphaType, dst.info().alphaType must
 *         match. If SkPixmap colorSpace() is nullptr, dst.info().colorSpace must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         srcX and srcY may be negative to copy only top or left of source. Returns
 *         false SkPixmap width() or height() is zero or negative. Returns false if:
 *         abs(srcX) >= Pixmap width(), or if abs(srcY) >= Pixmap height().
 *
 *         @param dst   SkImageInfo and pixel address to write to
 *         @param srcX  column index whose absolute value is less than width()
 *         @param srcY  row index whose absolute value is less than height()
 *         @return      true if pixels are copied to dst
 *     */
 *     bool readPixels(const SkPixmap& dst, int srcX, int srcY) const {
 *         return this->readPixels(dst.info(), dst.writable_addr(), dst.rowBytes(), srcX, srcY);
 *     }
 *
 *     /** Copies pixels inside bounds() to dst. dst specifies width, height, SkColorType,
 *         SkAlphaType, and SkColorSpace of destination.  Returns true if pixels are copied.
 *         Returns false if dst address equals nullptr, or dst.rowBytes() is less than
 *         dst SkImageInfo::minRowBytes.
 *
 *         Pixels are copied only if pixel conversion is possible. If SkPixmap colorType() is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dst SkColorType must match.
 *         If SkPixmap colorType() is kGray_8_SkColorType, dst SkColorSpace must match.
 *         If SkPixmap alphaType() is kOpaque_SkAlphaType, dst SkAlphaType must
 *         match. If SkPixmap colorSpace() is nullptr, dst SkColorSpace must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         Returns false if SkPixmap width() or height() is zero or negative.
 *
 *         @param dst  SkImageInfo and pixel address to write to
 *         @return     true if pixels are copied to dst
 *     */
 *     bool readPixels(const SkPixmap& dst) const {
 *         return this->readPixels(dst.info(), dst.writable_addr(), dst.rowBytes(), 0, 0);
 *     }
 *
 *     /** Copies SkBitmap to dst, scaling pixels to fit dst.width() and dst.height(), and
 *         converting pixels to match dst.colorType() and dst.alphaType(). Returns true if
 *         pixels are copied. Returns false if dst address is nullptr, or dst.rowBytes() is
 *         less than dst SkImageInfo::minRowBytes.
 *
 *         Pixels are copied only if pixel conversion is possible. If SkPixmap colorType() is
 *         kGray_8_SkColorType, or kAlpha_8_SkColorType; dst SkColorType must match.
 *         If SkPixmap colorType() is kGray_8_SkColorType, dst SkColorSpace must match.
 *         If SkPixmap alphaType() is kOpaque_SkAlphaType, dst SkAlphaType must
 *         match. If SkPixmap colorSpace() is nullptr, dst SkColorSpace must match. Returns
 *         false if pixel conversion is not possible.
 *
 *         Returns false if SkBitmap width() or height() is zero or negative.
 *
 *         @param dst            SkImageInfo and pixel address to write to
 *         @return               true if pixels are scaled to fit dst
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_scalePixels
 *     */
 *     bool scalePixels(const SkPixmap& dst, const SkSamplingOptions&) const;
 *
 *     /** Writes color to pixels bounded by subset; returns true on success.
 *         Returns false if colorType() is kUnknown_SkColorType, or if subset does
 *         not intersect bounds().
 *
 *         @param color   sRGB unpremultiplied color to write
 *         @param subset  bounding integer SkRect of written pixels
 *         @return        true if pixels are changed
 *
 *         example: https://fiddle.skia.org/c/@Pixmap_erase
 *     */
 *     bool erase(SkColor color, const SkIRect& subset) const;
 *
 *     /** Writes color to pixels inside bounds(); returns true on success.
 *         Returns false if colorType() is kUnknown_SkColorType, or if bounds()
 *         is empty.
 *
 *         @param color  sRGB unpremultiplied color to write
 *         @return       true if pixels are changed
 *     */
 *     bool erase(SkColor color) const { return this->erase(color, this->bounds()); }
 *
 *     /** Writes color to pixels bounded by subset; returns true on success.
 *         if subset is nullptr, writes colors pixels inside bounds(). Returns false if
 *         colorType() is kUnknown_SkColorType, if subset is not nullptr and does
 *         not intersect bounds(), or if subset is nullptr and bounds() is empty.
 *
 *         @param color   unpremultiplied color to write
 *         @param subset  bounding integer SkRect of pixels to write; may be nullptr
 *         @return        true if pixels are changed
 *     */
 *     bool erase(const SkColor4f& color, const SkIRect* subset = nullptr) const;
 *
 * private:
 *     const void*     fPixels;
 *     size_t          fRowBytes;
 *     SkImageInfo     fInfo;
 * }
 * ```
 */
public open class SkPixmap public constructor() {
  /**
   * C++ original:
   * ```cpp
   * const void*     fPixels
   * ```
   */
  private val fPixels: Unit? = TODO("Initialize fPixels")

  /**
   * C++ original:
   * ```cpp
   * size_t          fRowBytes
   * ```
   */
  private var fRowBytes: ULong = TODO("Initialize fRowBytes")

  /**
   * C++ original:
   * ```cpp
   * SkImageInfo     fInfo
   * ```
   */
  private var fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * SkPixmap()
   *         : fPixels(nullptr), fRowBytes(0), fInfo(SkImageInfo::MakeUnknown(0, 0))
   *     {}
   * ```
   */
  public constructor(
    info: SkImageInfo,
    addr: Unit?,
    rowBytes: ULong,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixmap::reset() {
   *     fPixels = nullptr;
   *     fRowBytes = 0;
   *     fInfo = SkImageInfo::MakeUnknown();
   * }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixmap::reset(const SkImageInfo& info, const void* addr, size_t rowBytes) {
   *     if (addr) {
   *         SkDEBUGCODE(info.validate());
   *         SkASSERT(info.validRowBytes(rowBytes));
   *     }
   *     fPixels = addr;
   *     fRowBytes = rowBytes;
   *     fInfo = info;
   * }
   * ```
   */
  public fun reset(
    info: SkImageInfo,
    addr: Unit?,
    rowBytes: ULong,
  ) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPixmap::setColorSpace(sk_sp<SkColorSpace> cs) {
   *     fInfo = fInfo.makeColorSpace(std::move(cs));
   * }
   * ```
   */
  public fun setColorSpace(colorSpace: SkSp<SkColorSpace>) {
    TODO("Implement setColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPixmap::reset(const SkMask& src) {
   *     if (SkMask::kA8_Format == src.fFormat) {
   *         this->reset(SkImageInfo::MakeA8(src.fBounds.width(), src.fBounds.height()),
   *                     src.fImage, src.fRowBytes);
   *         return true;
   *     }
   *     this->reset();
   *     return false;
   * }
   * ```
   */
  public fun reset(mask: SkMask): Boolean {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPixmap::extractSubset(SkPixmap* result, const SkIRect& subset) const {
   *     SkIRect srcRect, r;
   *     srcRect.setWH(this->width(), this->height());
   *     if (!r.intersect(srcRect, subset)) {
   *         return false;   // r is empty (i.e. no intersection)
   *     }
   *
   *     // If the upper left of the rectangle was outside the bounds of this SkBitmap, we should have
   *     // exited above.
   *     SkASSERT(static_cast<unsigned>(r.fLeft) < static_cast<unsigned>(this->width()));
   *     SkASSERT(static_cast<unsigned>(r.fTop) < static_cast<unsigned>(this->height()));
   *
   *     const void* pixels = nullptr;
   *     if (fPixels) {
   *         const size_t bpp = fInfo.bytesPerPixel();
   *         pixels = (const uint8_t*)fPixels + r.fTop * fRowBytes + r.fLeft * bpp;
   *     }
   *     result->reset(fInfo.makeDimensions(r.size()), pixels, fRowBytes);
   *     return true;
   * }
   * ```
   */
  public fun extractSubset(subset: SkPixmap?, area: SkIRect): Boolean {
    TODO("Implement extractSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& info() const { return fInfo; }
   * ```
   */
  public fun info(): Int {
    TODO("Implement info")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t rowBytes() const { return fRowBytes; }
   * ```
   */
  public fun rowBytes(): ULong {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* addr() const { return fPixels; }
   * ```
   */
  public fun addr() {
    TODO("Implement addr")
  }

  /**
   * C++ original:
   * ```cpp
   * int width() const { return fInfo.width(); }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fInfo.height(); }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fInfo.dimensions(); }
   * ```
   */
  public fun dimensions(): Int {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fInfo.colorType(); }
   * ```
   */
  public fun colorType(): Int {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType alphaType() const { return fInfo.alphaType(); }
   * ```
   */
  public fun alphaType(): SkAlphaType {
    TODO("Implement alphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* SkPixmap::colorSpace() const { return fInfo.colorSpace(); }
   * ```
   */
  public fun colorSpace(): SkColorSpace {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkPixmap::refColorSpace() const { return fInfo.refColorSpace(); }
   * ```
   */
  public fun refColorSpace(): Int {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const { return fInfo.isOpaque(); }
   * ```
   */
  public fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect bounds() const { return SkIRect::MakeWH(this->width(), this->height()); }
   * ```
   */
  public fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * int rowBytesAsPixels() const { return int(fRowBytes >> this->shiftPerPixel()); }
   * ```
   */
  public fun rowBytesAsPixels(): Int {
    TODO("Implement rowBytesAsPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * int shiftPerPixel() const { return fInfo.shiftPerPixel(); }
   * ```
   */
  public fun shiftPerPixel(): Int {
    TODO("Implement shiftPerPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t computeByteSize() const { return fInfo.computeByteSize(fRowBytes); }
   * ```
   */
  public fun computeByteSize(): ULong {
    TODO("Implement computeByteSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPixmap::computeIsOpaque() const {
   *     const int height = this->height();
   *     const int width = this->width();
   *
   *     switch (this->colorType()) {
   *         case kAlpha_8_SkColorType: {
   *             unsigned a = 0xFF;
   *             for (int y = 0; y < height; ++y) {
   *                 const uint8_t* row = this->addr8(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     a &= row[x];
   *                 }
   *                 if (0xFF != a) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kA16_unorm_SkColorType: {
   *             unsigned a = 0xFFFF;
   *             for (int y = 0; y < height; ++y) {
   *                 const uint16_t* row = this->addr16(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     a &= row[x];
   *                 }
   *                 if (0xFFFF != a) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kA16_float_SkColorType: {
   *             for (int y = 0; y < height; ++y) {
   *                 const SkHalf* row = this->addr16(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     if (row[x] < SK_Half1) {
   *                         return false;
   *                     }
   *                 }
   *             }
   *             return true;
   *         }
   *         case kRGB_565_SkColorType:
   *         case kGray_8_SkColorType:
   *         case kR8G8_unorm_SkColorType:
   *         case kR16_unorm_SkColorType:
   *         case kR16G16_unorm_SkColorType:
   *         case kR16G16_float_SkColorType:
   *         case kRGB_888x_SkColorType:
   *         case kRGB_101010x_SkColorType:
   *         case kBGR_101010x_SkColorType:
   *         case kRGB_F16F16F16x_SkColorType:
   *         case kBGR_101010x_XR_SkColorType:
   *         case kR8_unorm_SkColorType:
   *             return true;
   *         case kARGB_4444_SkColorType: {
   *             unsigned c = 0xFFFF;
   *             for (int y = 0; y < height; ++y) {
   *                 const SkPMColor16* row = this->addr16(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     c &= row[x];
   *                 }
   *                 if (0xF != SkGetPackedA4444(c)) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kBGRA_8888_SkColorType:
   *         case kRGBA_8888_SkColorType:
   *         case kSRGBA_8888_SkColorType: {
   *             SkPMColor c = (SkPMColor)~0;
   *             for (int y = 0; y < height; ++y) {
   *                 const SkPMColor* row = this->addr32(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     c &= row[x];
   *                 }
   *                 if (0xFF != SkGetPackedA32(c)) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType: {
   *             const SkHalf* row = (const SkHalf*)this->addr();
   *             for (int y = 0; y < height; ++y) {
   *                 for (int x = 0; x < width; ++x) {
   *                     if (row[4 * x + 3] < SK_Half1) {
   *                         return false;
   *                     }
   *                 }
   *                 row += this->rowBytes() >> 1;
   *             }
   *             return true;
   *         }
   *         case kRGBA_F32_SkColorType: {
   *             const float* row = (const float*)this->addr();
   *             for (int y = 0; y < height; ++y) {
   *                 for (int x = 0; x < width; ++x) {
   *                     if (row[4 * x + 3] < 1.0f) {
   *                         return false;
   *                     }
   *                 }
   *                 row += this->rowBytes() >> 2;
   *             }
   *             return true;
   *         }
   *         case kRGBA_1010102_SkColorType:
   *         case kBGRA_1010102_SkColorType: {
   *             uint32_t c = ~0;
   *             for (int y = 0; y < height; ++y) {
   *                 const uint32_t* row = this->addr32(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     c &= row[x];
   *                 }
   *                 if (0b11 != c >> 30) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kBGRA_10101010_XR_SkColorType:{
   *             static constexpr uint64_t kOne = 510 + 384;
   *             for (int y = 0; y < height; ++y) {
   *                 const uint64_t* row = this->addr64(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     if ((row[x] >> 54) < kOne) {
   *                         return false;
   *                     }
   *                 }
   *             }
   *             return true;
   *         }
   *         case kRGBA_10x6_SkColorType: {
   *             uint16_t acc = 0xFFC0;  // Ignore bottom six bits
   *             for (int y = 0; y < height; ++y) {
   *                 const uint64_t* row = this->addr64(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     acc &= (row[x] >> 48);
   *                 }
   *                 if (0xFFC0 != acc) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kR16G16B16A16_unorm_SkColorType: {
   *             uint16_t acc = 0xFFFF;
   *             for (int y = 0; y < height; ++y) {
   *                 const uint64_t* row = this->addr64(0, y);
   *                 for (int x = 0; x < width; ++x) {
   *                     acc &= (row[x] >> 48);
   *                 }
   *                 if (0xFFFF != acc) {
   *                     return false;
   *                 }
   *             }
   *             return true;
   *         }
   *         case kUnknown_SkColorType:
   *             SkDEBUGFAIL("");
   *             break;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun computeIsOpaque(): Boolean {
    TODO("Implement computeIsOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor SkPixmap::getColor(int x, int y) const {
   *     SkASSERT(this->addr());
   *     SkASSERT((unsigned)x < (unsigned)this->width());
   *     SkASSERT((unsigned)y < (unsigned)this->height());
   *
   *     const bool needsUnpremul = (kPremul_SkAlphaType == fInfo.alphaType());
   *     auto toColor = [needsUnpremul](uint32_t maybePremulColor) {
   *         return needsUnpremul ? SkUnPreMultiply::PMColorToColor(maybePremulColor)
   *                              : SkSwizzle_BGRA_to_PMColor(maybePremulColor);
   *     };
   *
   *     switch (this->colorType()) {
   *         case kGray_8_SkColorType: {
   *             uint8_t value = *this->addr8(x, y);
   *             return SkColorSetRGB(value, value, value);
   *         }
   *         case kR8_unorm_SkColorType: {
   *             return SkColorSetRGB(*this->addr8(x, y), 0, 0);
   *         }
   *         case kAlpha_8_SkColorType: {
   *             return SkColorSetA(0, *this->addr8(x, y));
   *         }
   *         case kA16_unorm_SkColorType: {
   *             return SkColorSetA(0, (*this->addr16(x, y)) * (255 / 65535.0f));
   *         }
   *         case kA16_float_SkColorType: {
   *             return SkColorSetA(0, 255 * SkHalfToFloat(*this->addr16(x, y)));
   *         }
   *         case kRGB_565_SkColorType: {
   *             return SkPixel16ToColor(*this->addr16(x, y));
   *         }
   *         case kARGB_4444_SkColorType: {
   *             SkPMColor c = SkPixel4444ToPixel32(*this->addr16(x, y));
   *             return toColor(c);
   *         }
   *         case kR8G8_unorm_SkColorType: {
   *             uint16_t value = *this->addr16(x, y);
   *             return SkColorSetRGB((uint8_t)(value & 0xffff), (uint8_t)((value >> 8) & 0xffff), 0);
   *         }
   *         case kR16_unorm_SkColorType: {
   *             uint16_t value = *this->addr16(x, y);
   *             return SkColorSetRGB(value * (255 / 65535.0f), 0, 0);
   *         }
   *         case kR16G16_unorm_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             return SkColorSetRGB((uint8_t)(value & 0xffff), (uint8_t)((value >> 16) & 0xffff), 0);
   *         }
   *         case kR16G16_float_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             float r = SkHalfToFloat((uint16_t)(value >>  0) & 0xffff),
   *                   g = SkHalfToFloat((uint16_t)(value >> 16) & 0xffff);
   *             return SkColorSetRGB((uint8_t)(255 * r), (uint8_t)(255 * g), 0);
   *         }
   *         case kRGB_888x_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             return SkSwizzle_RB(value | 0xff000000);
   *         }
   *         case kBGRA_8888_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             return toColor(SkSwizzle_BGRA_to_PMColor(value));
   *         }
   *         case kRGBA_8888_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             return toColor(SkSwizzle_RGBA_to_PMColor(value));
   *         }
   *         case kSRGBA_8888_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             float r = ((value >>  0) & 0xff) * (1/255.0f),
   *                   g = ((value >>  8) & 0xff) * (1/255.0f),
   *                   b = ((value >> 16) & 0xff) * (1/255.0f),
   *                   a = ((value >> 24) & 0xff) * (1/255.0f);
   *
   *             auto srgb_to_linear = [](float x) {
   *                 return (x <= 0.04045f) ? x * (1 / 12.92f)
   *                                        : std::pow(x * (1 / 1.055f) + (0.055f / 1.055f), 2.4f);
   *             };
   *             r = srgb_to_linear(r);
   *             g = srgb_to_linear(g);
   *             b = srgb_to_linear(b);
   *             if (a != 0 && needsUnpremul) {
   *                 r = SkTPin(r/a, 0.0f, 1.0f);
   *                 g = SkTPin(g/a, 0.0f, 1.0f);
   *                 b = SkTPin(b/a, 0.0f, 1.0f);
   *             }
   *             r *= 255.0f;
   *             g *= 255.0f;
   *             b *= 255.0f;
   *             a *= 255.0f;
   *             return SkColorSetARGB(r, g, b, a);
   *         }
   *         case kRGB_101010x_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             // Convert 10-bit rgb to 8-bit rgb
   *             return SkColorSetRGB(((value >>  0) & 0x3ff) * (255/1023.0f),
   *                                  ((value >> 10) & 0x3ff) * (255/1023.0f),
   *                                  ((value >> 20) & 0x3ff) * (255/1023.0f));
   *         }
   *         case kBGR_101010x_XR_SkColorType: {
   *             SkASSERT(false);
   *             return 0;
   *         }
   *         case kBGR_101010x_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             // Convert 10-bit bgr to 8-bit rgb
   *             return SkColorSetRGB(((value >> 20) & 0x3ff) * (255/1023.0f),
   *                                  ((value >> 10) & 0x3ff) * (255/1023.0f),
   *                                  ((value >>  0) & 0x3ff) * (255/1023.0f));
   *         }
   *         case kRGBA_1010102_SkColorType:
   *         case kBGRA_1010102_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             float b = ((value >>  0) & 0x3ff) * (1/1023.0f),
   *                   g = ((value >> 10) & 0x3ff) * (1/1023.0f),
   *                   r = ((value >> 20) & 0x3ff) * (1/1023.0f),
   *                   a = ((value >> 30) & 0x3  ) * (1/   3.0f);
   *             if (a != 0 && needsUnpremul) {
   *                 r = SkTPin(r/a, 0.0f, 1.0f);
   *                 g = SkTPin(g/a, 0.0f, 1.0f);
   *                 b = SkTPin(b/a, 0.0f, 1.0f);
   *             }
   *             b *= 255.0f;
   *             g *= 255.0f;
   *             r *= 255.0f;
   *             a *= 255.0f;
   *             return SkColorSetARGB(a, r, g, b);
   *         }
   *         case kBGRA_10101010_XR_SkColorType: {
   *             SkASSERT(false);
   *             return 0;
   *         }
   *         case kRGBA_10x6_SkColorType: {
   *             uint64_t value = *this->addr64(x, y);
   *             return SkColorSetARGB(((value >> 54) & 0x3ff) * (1/1023.0f),
   *                                   ((value >>  6) & 0x3ff) * (1/1023.0f),
   *                                   ((value >> 22) & 0x3ff) * (1/1023.0f),
   *                                   ((value >> 38) & 0x3ff) * (1/1023.0f));
   *         }
   *         case kR16G16B16A16_unorm_SkColorType: {
   *             uint64_t value = *this->addr64(x, y);
   *             float r = ((value >>  0) & 0xffff) * (1/65535.0f),
   *                   g = ((value >> 16) & 0xffff) * (1/65535.0f),
   *                   b = ((value >> 32) & 0xffff) * (1/65535.0f),
   *                   a = ((value >> 48) & 0xffff) * (1/65535.0f);
   *             if (a != 0 && needsUnpremul) {
   *                 r *= (1.0f/a);
   *                 g *= (1.0f/a);
   *                 b *= (1.0f/a);
   *             }
   *             r *= 255.0f;
   *             g *= 255.0f;
   *             b *= 255.0f;
   *             a *= 255.0f;
   *             return SkColorSetARGB(r, g, b, a);
   *         }
   *         case kRGB_F16F16F16x_SkColorType: {
   *             const uint64_t* addr =
   *                 (const uint64_t*)fPixels + y * (fRowBytes >> 3) + x;
   *             skvx::float4 p4 = from_half(skvx::half4::Load(addr));
   *             p4[3] = 1.0f;
   *             // p4 is RGBA, but we want BGRA, so we need to swap next
   *             return Sk4f_toL32(swizzle_rb(p4));
   *         }
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType: {
   *             const uint64_t* addr =
   *                 (const uint64_t*)fPixels + y * (fRowBytes >> 3) + x;
   *             skvx::float4 p4 = from_half(skvx::half4::Load(addr));
   *             if (p4[3] && needsUnpremul) {
   *                 float inva = 1 / p4[3];
   *                 p4 = p4 * skvx::float4(inva, inva, inva, 1);
   *             }
   *             // p4 is RGBA, but we want BGRA, so we need to swap next
   *             return Sk4f_toL32(swizzle_rb(p4));
   *         }
   *         case kRGBA_F32_SkColorType: {
   *             const float* rgba =
   *                 (const float*)fPixels + 4*y*(fRowBytes >> 4) + 4*x;
   *             skvx::float4 p4 = skvx::float4::Load(rgba);
   *             // From here on, just like F16:
   *             if (p4[3] && needsUnpremul) {
   *                 float inva = 1 / p4[3];
   *                 p4 = p4 * skvx::float4(inva, inva, inva, 1);
   *             }
   *             // p4 is RGBA, but we want BGRA, so we need to swap next
   *             return Sk4f_toL32(swizzle_rb(p4));
   *         }
   *         case kUnknown_SkColorType:
   *             break;
   *     }
   *     SkDEBUGFAIL("");
   *     return SkColorSetARGB(0, 0, 0, 0);
   * }
   * ```
   */
  public fun getColor(x: Int, y: Int): Int {
    TODO("Implement getColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor4f SkPixmap::getColor4f(int x, int y) const {
   *     SkASSERT(this->addr());
   *     SkASSERT((unsigned)x < (unsigned)this->width());
   *     SkASSERT((unsigned)y < (unsigned)this->height());
   *
   *     const bool needsUnpremul = (kPremul_SkAlphaType == fInfo.alphaType());
   *     auto toColor = [needsUnpremul](uint32_t maybePremulColor) {
   *         return needsUnpremul ? SkUnPreMultiply::PMColorToColor(maybePremulColor)
   *                              : SkSwizzle_BGRA_to_PMColor(maybePremulColor);
   *     };
   *
   *     switch (this->colorType()) {
   *         case kGray_8_SkColorType: {
   *             float value = *this->addr8(x, y) / 255.0f;
   *             return SkColor4f{value, value, value, 1.0f};
   *         }
   *         case kR8_unorm_SkColorType: {
   *             return SkColor4f{(*this->addr8(x, y) / 255.0f), 0.0f, 0.0f, 1.0f};
   *         }
   *         case kAlpha_8_SkColorType: {
   *             return SkColor4f{0.0f, 0.0f, 0.0f, (*this->addr8(x, y) / 255.0f)};
   *         }
   *         case kA16_unorm_SkColorType: {
   *             return SkColor4f{0.0f, 0.0f, 0.0f, (*this->addr16(x, y) / 65535.0f)};
   *         }
   *         case kA16_float_SkColorType: {
   *             return SkColor4f{0.0f, 0.0f, 0.0f, SkHalfToFloat(*this->addr16(x, y))};
   *         }
   *         case kRGB_565_SkColorType: {
   *             return SkColor4f::FromColor(SkPixel16ToColor(*this->addr16(x, y)));
   *         }
   *         case kARGB_4444_SkColorType: {
   *             SkPMColor c = SkPixel4444ToPixel32(*this->addr16(x, y));
   *             return SkColor4f::FromColor(toColor(c));
   *         }
   *         case kR8G8_unorm_SkColorType: {
   *             uint16_t value = *this->addr16(x, y);
   *             return SkColor4f::FromColor(SkColorSetRGB((uint8_t)(value), (uint8_t)(value >> 8), 0));
   *         }
   *         case kR16_unorm_SkColorType: {
   *             float value = *this->addr16(x, y) / 65535.0f;
   *             return SkColor4f{value, 0.0f, 0.0f, 1.0f};
   *         }
   *         case kR16G16_unorm_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             float r = ((value >>  0) & 0xffff) * (255 / 65535.0f),
   *                   g = ((value >> 16) & 0xffff) * (255 / 65535.0f);
   *             return SkColor4f{r, g, 0.0, 1.0};
   *         }
   *         case kR16G16_float_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             float r = SkHalfToFloat((value >> 0 ) & 0xffff);
   *             float g = SkHalfToFloat((value >> 16) & 0xffff);
   *             return SkColor4f{r, g, 0.0, 1.0};
   *         }
   *         case kRGB_888x_SkColorType: {
   *             SkColor c = SkSwizzle_RB(*this->addr32(x, y) | 0xff000000);
   *             return SkColor4f::FromColor(c);
   *         }
   *         case kBGRA_8888_SkColorType: {
   *             SkPMColor c = SkSwizzle_BGRA_to_PMColor(*this->addr32(x, y));
   *             return SkColor4f::FromColor(toColor(c));
   *         }
   *         case kRGBA_8888_SkColorType: {
   *             SkPMColor c = SkSwizzle_RGBA_to_PMColor(*this->addr32(x, y));
   *             return SkColor4f::FromColor(toColor(c));
   *         }
   *         case kSRGBA_8888_SkColorType: {
   *             auto srgb_to_linear = [](float x) {
   *                 return (x <= 0.04045f) ? x * (1 / 12.92f)
   *                                        : std::pow(x * (1 / 1.055f) + (0.055f / 1.055f), 2.4f);
   *             };
   *
   *             uint32_t value = *this->addr32(x, y);
   *             float r = ((value >>  0) & 0xff) * (1 / 255.0f),
   *                   g = ((value >>  8) & 0xff) * (1 / 255.0f),
   *                   b = ((value >> 16) & 0xff) * (1 / 255.0f),
   *                   a = ((value >> 24) & 0xff) * (1 / 255.0f);
   *             r = srgb_to_linear(r);
   *             g = srgb_to_linear(g);
   *             b = srgb_to_linear(b);
   *             if (a != 0 && needsUnpremul) {
   *                 r = SkTPin(r / a, 0.0f, 1.0f);
   *                 g = SkTPin(g / a, 0.0f, 1.0f);
   *                 b = SkTPin(b / a, 0.0f, 1.0f);
   *             }
   *             return SkColor4f{r, g, b, a};
   *         }
   *         case kBGR_101010x_XR_SkColorType: {
   *             SkASSERT(false);
   *             return {};
   *         }
   *         case kRGB_101010x_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             // Convert 10-bit RGB to floats
   *             return SkColor4f{((value >>  0) & 0x3ff) / (1023.0f),
   *                              ((value >> 10) & 0x3ff) / (1023.0f),
   *                              ((value >> 20) & 0x3ff) / (1023.0f),
   *                              1.0f};
   *         }
   *         case kBGR_101010x_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             // Convert 10-bit BGR color values to RGBA floats
   *             return SkColor4f{((value >> 20) & 0x3ff) / (1023.0f),
   *                              ((value >> 10) & 0x3ff) / (1023.0f),
   *                              ((value >>  0) & 0x3ff) / (1023.0f),
   *                              1.0f};
   *         }
   *         case kRGBA_1010102_SkColorType:
   *         case kBGRA_1010102_SkColorType: {
   *             uint32_t value = *this->addr32(x, y);
   *             // Convert 10-bit color values to floats
   *             float b = ((value >>  0) & 0x3ff) * (1 / 1023.0f),
   *                   g = ((value >> 10) & 0x3ff) * (1 / 1023.0f),
   *                   r = ((value >> 20) & 0x3ff) * (1 / 1023.0f),
   *                   a = ((value >> 30) & 0x3  ) * (1 / 3.0f   );
   *             if (a != 0 && needsUnpremul) {
   *                 r = SkTPin(r / a, 0.0f, 1.0f);
   *                 g = SkTPin(g / a, 0.0f, 1.0f);
   *                 b = SkTPin(b / a, 0.0f, 1.0f);
   *             }
   *             return SkColor4f{r, g, b, a};
   *         }
   *         case kBGRA_10101010_XR_SkColorType: {
   *             SkASSERT(false);
   *             return {};
   *         }
   *         case kRGBA_10x6_SkColorType: {
   *             uint64_t value = *this->addr64(x, y);
   *             return SkColor4f{((value >>  6) & 0x3ff) * (1/1023.0f),
   *                              ((value >> 22) & 0x3ff) * (1/1023.0f),
   *                              ((value >> 38) & 0x3ff) * (1/1023.0f),
   *                              ((value >> 54) & 0x3ff) * (1/1023.0f)};
   *         }
   *         case kR16G16B16A16_unorm_SkColorType: {
   *             uint64_t value = *this->addr64(x, y);
   *
   *             float r = ((value >>  0) & 0xffff) * (1 / 65535.0f),
   *                   g = ((value >> 16) & 0xffff) * (1 / 65535.0f),
   *                   b = ((value >> 32) & 0xffff) * (1 / 65535.0f),
   *                   a = ((value >> 48) & 0xffff) * (1 / 65535.0f);
   *             if (a != 0 && needsUnpremul) {
   *                 r *= (1.0f / a);
   *                 g *= (1.0f / a);
   *                 b *= (1.0f / a);
   *             }
   *             return SkColor4f{r, g, b, a};
   *         }
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType: {
   *             const uint64_t* addr = (const uint64_t*)fPixels + y * (fRowBytes >> 3) + x;
   *             skvx::float4 p4 = from_half(skvx::half4::Load(addr));
   *             if (p4[3] && needsUnpremul) {
   *                 float inva = 1 / p4[3];
   *                 p4 = p4 * skvx::float4(inva, inva, inva, 1);
   *             }
   *             return SkColor4f{p4[0], p4[1], p4[2], p4[3]};
   *         }
   *         case kRGB_F16F16F16x_SkColorType: {
   *             const uint64_t* addr = (const uint64_t*)fPixels + y * (fRowBytes >> 3) + x;
   *             skvx::float4 p4 = from_half(skvx::half4::Load(addr));
   *             p4[3] = 1.0f;
   *             return SkColor4f{p4[0], p4[1], p4[2], p4[3]};
   *         }
   *         case kRGBA_F32_SkColorType: {
   *             const float* rgba = (const float*)fPixels + 4 * y * (fRowBytes >> 4) + 4 * x;
   *             skvx::float4 p4 = skvx::float4::Load(rgba);
   *             // From here on, just like F16:
   *             if (p4[3] && needsUnpremul) {
   *                 float inva = 1 / p4[3];
   *                 p4 = p4 * skvx::float4(inva, inva, inva, 1);
   *             }
   *             return SkColor4f{p4[0], p4[1], p4[2], p4[3]};
   *         }
   *         case kUnknown_SkColorType:
   *             break;
   *     }
   *     SkDEBUGFAIL("");
   *     return SkColors::kTransparent;
   * }
   * ```
   */
  public fun getColor4f(x: Int, y: Int): Int {
    TODO("Implement getColor4f")
  }

  /**
   * C++ original:
   * ```cpp
   * float SkPixmap::getAlphaf(int x, int y) const {
   *     SkASSERT(this->addr());
   *     SkASSERT((unsigned)x < (unsigned)this->width());
   *     SkASSERT((unsigned)y < (unsigned)this->height());
   *
   *     float value = 0;
   *     const void* srcPtr = fast_getaddr(*this, x, y);
   *
   *     switch (this->colorType()) {
   *         case kUnknown_SkColorType:
   *             return 0;
   *         case kGray_8_SkColorType:
   *         case kR8G8_unorm_SkColorType:
   *         case kR16_unorm_SkColorType:
   *         case kR16G16_unorm_SkColorType:
   *         case kR16G16_float_SkColorType:
   *         case kRGB_565_SkColorType:
   *         case kRGB_888x_SkColorType:
   *         case kRGB_101010x_SkColorType:
   *         case kBGR_101010x_SkColorType:
   *         case kBGR_101010x_XR_SkColorType:
   *         case kRGB_F16F16F16x_SkColorType:
   *         case kR8_unorm_SkColorType:
   *             return 1;
   *         case kAlpha_8_SkColorType:
   *             value = static_cast<const uint8_t*>(srcPtr)[0] * (1.0f/255);
   *             break;
   *         case kA16_unorm_SkColorType:
   *             value = static_cast<const uint16_t*>(srcPtr)[0] * (1.0f/65535);
   *             break;
   *         case kA16_float_SkColorType: {
   *             SkHalf half = static_cast<const SkHalf*>(srcPtr)[0];
   *             value = SkHalfToFloat(half);
   *             break;
   *         }
   *         case kARGB_4444_SkColorType: {
   *             uint16_t u16 = static_cast<const uint16_t*>(srcPtr)[0];
   *             value = SkGetPackedA4444(u16) * (1.0f/15);
   *             break;
   *         }
   *         case kRGBA_8888_SkColorType:
   *         case kBGRA_8888_SkColorType:
   *         case kSRGBA_8888_SkColorType:
   *             value = static_cast<const uint8_t*>(srcPtr)[3] * (1.0f/255);
   *             break;
   *         case kRGBA_1010102_SkColorType:
   *         case kBGRA_1010102_SkColorType: {
   *             uint32_t u32 = static_cast<const uint32_t*>(srcPtr)[0];
   *             value = (u32 >> 30) * (1.0f/3);
   *             break;
   *         }
   *         case kBGRA_10101010_XR_SkColorType: {
   *             uint64_t u64 = static_cast<const uint64_t*>(srcPtr)[0];
   *             value = ((u64 >> 54) - 384) / 510.f;
   *             break;
   *         }
   *         case kRGBA_10x6_SkColorType: {
   *             uint64_t u64 = static_cast<const uint64_t*>(srcPtr)[0];
   *             value = (u64 >> 54) * (1.0f/1023);
   *             break;
   *         }
   *         case kR16G16B16A16_unorm_SkColorType: {
   *             uint64_t u64 = static_cast<const uint64_t*>(srcPtr)[0];
   *             value = (u64 >> 48) * (1.0f/65535);
   *             break;
   *         }
   *         case kRGBA_F16Norm_SkColorType:
   *         case kRGBA_F16_SkColorType: {
   *             value = from_half(skvx::half4::Load(srcPtr))[3];
   *             break;
   *         }
   *         case kRGBA_F32_SkColorType:
   *             value = static_cast<const float*>(srcPtr)[3];
   *             break;
   *     }
   *     return value;
   * }
   * ```
   */
  public fun getAlphaf(x: Int, y: Int): Float {
    TODO("Implement getAlphaf")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* addr(int x, int y) const {
   *         return (const char*)fPixels + fInfo.computeOffset(x, y, fRowBytes);
   *     }
   * ```
   */
  public fun addr(x: Int, y: Int) {
    TODO("Implement addr")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* addr8() const {
   *         SkASSERT(1 == fInfo.bytesPerPixel());
   *         return reinterpret_cast<const uint8_t*>(fPixels);
   *     }
   * ```
   */
  public fun addr8(): UByte {
    TODO("Implement addr8")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* addr16() const {
   *         SkASSERT(2 == fInfo.bytesPerPixel());
   *         return reinterpret_cast<const uint16_t*>(fPixels);
   *     }
   * ```
   */
  public fun addr16(): UShort {
    TODO("Implement addr16")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint32_t* addr32() const {
   *         SkASSERT(4 == fInfo.bytesPerPixel());
   *         return reinterpret_cast<const uint32_t*>(fPixels);
   *     }
   * ```
   */
  public fun addr32(): UInt {
    TODO("Implement addr32")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint64_t* addr64() const {
   *         SkASSERT(8 == fInfo.bytesPerPixel());
   *         return reinterpret_cast<const uint64_t*>(fPixels);
   *     }
   * ```
   */
  public fun addr64(): ULong {
    TODO("Implement addr64")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* addrF16() const {
   *         SkASSERT(8 == fInfo.bytesPerPixel());
   *         SkASSERT(kRGBA_F16_SkColorType     == fInfo.colorType() ||
   *                  kRGBA_F16Norm_SkColorType == fInfo.colorType());
   *         return reinterpret_cast<const uint16_t*>(fPixels);
   *     }
   * ```
   */
  public fun addrF16(): UShort {
    TODO("Implement addrF16")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* addr8(int x, int y) const {
   *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
   *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
   *         return (const uint8_t*)((const char*)this->addr8() + (size_t)y * fRowBytes + (x << 0));
   *     }
   * ```
   */
  public fun addr8(x: Int, y: Int): UByte {
    TODO("Implement addr8")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* addr16(int x, int y) const {
   *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
   *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
   *         return (const uint16_t*)((const char*)this->addr16() + (size_t)y * fRowBytes + (x << 1));
   *     }
   * ```
   */
  public fun addr16(x: Int, y: Int): UShort {
    TODO("Implement addr16")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint32_t* addr32(int x, int y) const {
   *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
   *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
   *         return (const uint32_t*)((const char*)this->addr32() + (size_t)y * fRowBytes + (x << 2));
   *     }
   * ```
   */
  public fun addr32(x: Int, y: Int): UInt {
    TODO("Implement addr32")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint64_t* addr64(int x, int y) const {
   *         SkASSERTF(x >= 0 && x < this->width(), "x=%d; width=%d\n", x, fInfo.width());
   *         SkASSERTF(y >= 0 && y < this->height(), "y=%d; height=%d\n", y, fInfo.height());
   *         return (const uint64_t*)((const char*)this->addr64() + (size_t)y * fRowBytes + (x << 3));
   *     }
   * ```
   */
  public fun addr64(x: Int, y: Int): ULong {
    TODO("Implement addr64")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* addrF16(int x, int y) const {
   *         SkASSERT(kRGBA_F16_SkColorType     == fInfo.colorType() ||
   *                  kRGBA_F16Norm_SkColorType == fInfo.colorType());
   *         return reinterpret_cast<const uint16_t*>(this->addr64(x, y));
   *     }
   * ```
   */
  public fun addrF16(x: Int, y: Int): UShort {
    TODO("Implement addrF16")
  }

  /**
   * C++ original:
   * ```cpp
   * void* writable_addr() const { return const_cast<void*>(fPixels); }
   * ```
   */
  public fun writableAddr() {
    TODO("Implement writableAddr")
  }

  /**
   * C++ original:
   * ```cpp
   * void* writable_addr(int x, int y) const {
   *         return const_cast<void*>(this->addr(x, y));
   *     }
   * ```
   */
  public fun writableAddr(x: Int, y: Int) {
    TODO("Implement writableAddr")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t* writable_addr8(int x, int y) const {
   *         return const_cast<uint8_t*>(this->addr8(x, y));
   *     }
   * ```
   */
  public fun writableAddr8(x: Int, y: Int): UByte {
    TODO("Implement writableAddr8")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t* writable_addr16(int x, int y) const {
   *         return const_cast<uint16_t*>(this->addr16(x, y));
   *     }
   * ```
   */
  public fun writableAddr16(x: Int, y: Int): UShort {
    TODO("Implement writableAddr16")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t* writable_addr32(int x, int y) const {
   *         return const_cast<uint32_t*>(this->addr32(x, y));
   *     }
   * ```
   */
  public fun writableAddr32(x: Int, y: Int): UInt {
    TODO("Implement writableAddr32")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t* writable_addr64(int x, int y) const {
   *         return const_cast<uint64_t*>(this->addr64(x, y));
   *     }
   * ```
   */
  public fun writableAddr64(x: Int, y: Int): ULong {
    TODO("Implement writableAddr64")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t* writable_addrF16(int x, int y) const {
   *         return reinterpret_cast<uint16_t*>(writable_addr64(x, y));
   *     }
   * ```
   */
  public fun writableAddrF16(x: Int, y: Int): UShort {
    TODO("Implement writableAddrF16")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRowBytes) const {
   *         return this->readPixels(dstInfo, dstPixels, dstRowBytes, 0, 0);
   *     }
   * ```
   */
  public fun readPixels(
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPixmap::readPixels(const SkImageInfo& dstInfo, void* dstPixels, size_t dstRB,
   *                           int x, int y) const {
   *     if (!SkImageInfoValidConversion(dstInfo, fInfo)) {
   *         return false;
   *     }
   *
   *     SkReadPixelsRec rec(dstInfo, dstPixels, dstRB, x, y);
   *     if (!rec.trim(fInfo.width(), fInfo.height())) {
   *         return false;
   *     }
   *
   *     const void* srcPixels = this->addr(rec.fX, rec.fY);
   *     const SkImageInfo srcInfo = fInfo.makeDimensions(rec.fInfo.dimensions());
   *     return SkConvertPixels(rec.fInfo, rec.fPixels, rec.fRowBytes, srcInfo, srcPixels,
   *                            this->rowBytes());
   * }
   * ```
   */
  public fun readPixels(
    dstInfo: SkImageInfo,
    dstPixels: Unit?,
    dstRowBytes: ULong,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPixels(const SkPixmap& dst, int srcX, int srcY) const {
   *         return this->readPixels(dst.info(), dst.writable_addr(), dst.rowBytes(), srcX, srcY);
   *     }
   * ```
   */
  public fun readPixels(
    dst: SkPixmap,
    srcX: Int,
    srcY: Int,
  ): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool readPixels(const SkPixmap& dst) const {
   *         return this->readPixels(dst.info(), dst.writable_addr(), dst.rowBytes(), 0, 0);
   *     }
   * ```
   */
  public fun readPixels(dst: SkPixmap): Boolean {
    TODO("Implement readPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPixmap::scalePixels(const SkPixmap& actualDst, const SkSamplingOptions& sampling) const {
   *     // We may need to tweak how we interpret these just a little below, so we make copies.
   *     SkPixmap src = *this,
   *              dst = actualDst;
   *
   *     // Can't do anthing with empty src or dst
   *     if (src.width() <= 0 || src.height() <= 0 ||
   *         dst.width() <= 0 || dst.height() <= 0) {
   *         return false;
   *     }
   *
   *     // no scaling involved?
   *     if (src.width() == dst.width() && src.height() == dst.height()) {
   *         return src.readPixels(dst);
   *     }
   *
   *     // If src and dst are both unpremul, we'll fake the source out to appear as if premul,
   *     // and mark the destination as opaque.  This odd combination allows us to scale unpremul
   *     // pixels without ever premultiplying them (perhaps losing information in the color channels).
   *     // This is an idiosyncratic feature of scalePixels(), and is tested by scalepixels_unpremul GM.
   *     bool clampAsIfUnpremul = false;
   *     if (src.alphaType() == kUnpremul_SkAlphaType &&
   *         dst.alphaType() == kUnpremul_SkAlphaType) {
   *         src.reset(src.info().makeAlphaType(kPremul_SkAlphaType), src.addr(), src.rowBytes());
   *         dst.reset(dst.info().makeAlphaType(kOpaque_SkAlphaType), dst.addr(), dst.rowBytes());
   *
   *         // We'll need to tell the image shader to clamp to [0,1] instead of the
   *         // usual [0,a] when using a bicubic scaling (kHigh_SkFilterQuality).
   *         clampAsIfUnpremul = true;
   *     }
   *
   *     SkBitmap bitmap;
   *     if (!bitmap.installPixels(src)) {
   *         return false;
   *     }
   *     bitmap.setImmutable();        // Don't copy when we create an image.
   *
   *     SkMatrix scale = SkMatrix::RectToRectOrIdentity(SkRect::Make(src.bounds()),
   *                                                     SkRect::Make(dst.bounds()));
   *
   *     sk_sp<SkShader> shader = SkImageShader::Make(bitmap.asImage(),
   *                                                  SkTileMode::kClamp,
   *                                                  SkTileMode::kClamp,
   *                                                  sampling,
   *                                                  &scale,
   *                                                  clampAsIfUnpremul);
   *
   *     sk_sp<SkSurface> surface =
   *             SkSurfaces::WrapPixels(dst.info(), dst.writable_addr(), dst.rowBytes());
   *     if (!shader || !surface) {
   *         return false;
   *     }
   *
   *     SkPaint paint;
   *     paint.setBlendMode(SkBlendMode::kSrc);
   *     paint.setShader(std::move(shader));
   *     surface->getCanvas()->drawPaint(paint);
   *     return true;
   * }
   * ```
   */
  public fun scalePixels(dst: SkPixmap, sampling: SkSamplingOptions): Boolean {
    TODO("Implement scalePixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool erase(SkColor color, const SkIRect& subset) const
   * ```
   */
  public fun erase(color: SkColor, subset: SkIRect): Boolean {
    TODO("Implement erase")
  }

  /**
   * C++ original:
   * ```cpp
   * bool erase(SkColor color) const { return this->erase(color, this->bounds()); }
   * ```
   */
  public fun erase(color: SkColor): Boolean {
    TODO("Implement erase")
  }

  /**
   * C++ original:
   * ```cpp
   * bool erase(const SkColor4f& color, const SkIRect* subset = nullptr) const
   * ```
   */
  public fun erase(color: SkColor4f, subset: SkIRect? = null): Boolean {
    TODO("Implement erase")
  }
}

public typealias SkAutoPixmapStorageINHERITED = SkPixmap
