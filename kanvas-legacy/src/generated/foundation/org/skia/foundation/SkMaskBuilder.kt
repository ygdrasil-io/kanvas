package org.skia.foundation

import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.Format
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct SkMaskBuilder : public SkMask {
 *     SkMaskBuilder() : SkMask(nullptr, {0}, 0, SkMask::Format::kBW_Format) {}
 *     SkMaskBuilder(const SkMaskBuilder&) = delete;
 *     SkMaskBuilder(SkMaskBuilder&&) = default;
 *     SkMaskBuilder& operator=(const SkMaskBuilder&) = delete;
 *     SkMaskBuilder& operator=(SkMaskBuilder&& that) {
 *         this->image() = that.image();
 *         this->bounds() = that.bounds();
 *         this->rowBytes() = that.rowBytes();
 *         this->format() = that.format();
 *         that.image() = nullptr;
 *         return *this;
 *     }
 *
 *     SkMaskBuilder(uint8_t* img, const SkIRect& bounds, uint32_t rowBytes, Format format)
 *         : SkMask(img, bounds, rowBytes, format) {}
 *
 *     uint8_t*& image() { return *const_cast<uint8_t**>(&fImage); }
 *     SkIRect& bounds() { return *const_cast<SkIRect*>(&fBounds); }
 *     uint32_t& rowBytes() { return *const_cast<uint32_t*>(&fRowBytes); }
 *     Format& format() { return *const_cast<Format*>(&fFormat); }
 *
 *     /** Returns the address of the byte that holds the specified bit.
 *         Asserts that the mask is kBW_Format, and that x,y are in range.
 *         x,y are in the same coordiate space as fBounds.
 *     */
 *     uint8_t* getAddr1(int x, int y) {
 *         return const_cast<uint8_t*>(this->SkMask::getAddr1(x, y));
 *     }
 *
 *     /** Returns the address of the specified byte.
 *         Asserts that the mask is kA8_Format, and that x,y are in range.
 *         x,y are in the same coordiate space as fBounds.
 *     */
 *     uint8_t* getAddr8(int x, int y) {
 *         return const_cast<uint8_t*>(this->SkMask::getAddr8(x, y));
 *     }
 *
 *     /**
 *      *  Return the address of the specified 16bit mask. In the debug build,
 *      *  this asserts that the mask's format is kLCD16_Format, and that (x,y)
 *      *  are contained in the mask's fBounds.
 *      */
 *     uint16_t* getAddrLCD16(int x, int y) {
 *         return const_cast<uint16_t*>(this->SkMask::getAddrLCD16(x, y));
 *     }
 *
 *     /**
 *      *  Return the address of the specified 32bit mask. In the debug build,
 *      *  this asserts that the mask's format is 32bits, and that (x,y)
 *      *  are contained in the mask's fBounds.
 *      */
 *     uint32_t* getAddr32(int x, int y) {
 *         return const_cast<uint32_t*>(this->SkMask::getAddr32(x, y));
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
 *     void* getAddr(int x, int y) {
 *         return const_cast<void*>(this->SkMask::getAddr(x, y));
 *     }
 *
 *     enum AllocType {
 *         kUninit_Alloc,
 *         kZeroInit_Alloc,
 *     };
 *     static uint8_t* AllocImage(size_t bytes, AllocType = kUninit_Alloc);
 *     static void FreeImage(void* image);
 *
 *     enum CreateMode {
 *         kJustComputeBounds_CreateMode,      //!< compute bounds and return
 *         kJustRenderImage_CreateMode,        //!< render into preallocate mask
 *         kComputeBoundsAndRenderImage_CreateMode  //!< compute bounds, alloc image and render into it
 *     };
 *
 *     /**
 *      *  Returns initial destination mask data padded by radiusX and radiusY
 *      */
 *     static SkMaskBuilder PrepareDestination(int radiusX, int radiusY, const SkMask& src);
 * }
 * ```
 */
public open class SkMaskBuilder public constructor() : SkMask(TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkMaskBuilder() : SkMask(nullptr, {0}, 0, SkMask::Format::kBW_Format) {}
   * ```
   */
  public constructor(param0: SkMaskBuilder) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskBuilder(const SkMaskBuilder&) = delete
   * ```
   */
  public constructor(
    img: UByte?,
    bounds: SkIRect,
    rowBytes: UInt,
    format: Format,
  ) : this(TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskBuilder& operator=(const SkMaskBuilder&) = delete
   * ```
   */
  public fun assign(param0: SkMaskBuilder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMaskBuilder& operator=(SkMaskBuilder&& that) {
   *         this->image() = that.image();
   *         this->bounds() = that.bounds();
   *         this->rowBytes() = that.rowBytes();
   *         this->format() = that.format();
   *         that.image() = nullptr;
   *         return *this;
   *     }
   * ```
   */
  public fun image(): Int {
    TODO("Implement image")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t*& image() { return *const_cast<uint8_t**>(&fImage); }
   * ```
   */
  public fun bounds(): SkIRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIRect& bounds() { return *const_cast<SkIRect*>(&fBounds); }
   * ```
   */
  public fun rowBytes(): Int {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t& rowBytes() { return *const_cast<uint32_t*>(&fRowBytes); }
   * ```
   */
  public fun format(): Format {
    TODO("Implement format")
  }

  /**
   * C++ original:
   * ```cpp
   * Format& format() { return *const_cast<Format*>(&fFormat); }
   * ```
   */
  public override fun getAddr1(x: Int, y: Int): Int {
    TODO("Implement getAddr1")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t* getAddr1(int x, int y) {
   *         return const_cast<uint8_t*>(this->SkMask::getAddr1(x, y));
   *     }
   * ```
   */
  public override fun getAddr8(x: Int, y: Int): Int {
    TODO("Implement getAddr8")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t* getAddr8(int x, int y) {
   *         return const_cast<uint8_t*>(this->SkMask::getAddr8(x, y));
   *     }
   * ```
   */
  public override fun getAddrLCD16(x: Int, y: Int): Int {
    TODO("Implement getAddrLCD16")
  }

  /**
   * C++ original:
   * ```cpp
   * uint16_t* getAddrLCD16(int x, int y) {
   *         return const_cast<uint16_t*>(this->SkMask::getAddrLCD16(x, y));
   *     }
   * ```
   */
  public override fun getAddr32(x: Int, y: Int): Int {
    TODO("Implement getAddr32")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t* getAddr32(int x, int y) {
   *         return const_cast<uint32_t*>(this->SkMask::getAddr32(x, y));
   *     }
   * ```
   */
  public override fun getAddr(x: Int, y: Int) {
    TODO("Implement getAddr")
  }

  public enum class AllocType {
    kUninit_Alloc,
    kZeroInit_Alloc,
  }

  public enum class CreateMode {
    kJustComputeBounds_CreateMode,
    kJustRenderImage_CreateMode,
    kComputeBoundsAndRenderImage_CreateMode,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * uint8_t* SkMaskBuilder::AllocImage(size_t size, AllocType at) {
     *     size_t aligned_size = SkSafeMath::Align4(size);
     *     unsigned flags = SK_MALLOC_THROW;
     *     if (at == kZeroInit_Alloc) {
     *         flags |= SK_MALLOC_ZERO_INITIALIZE;
     *     }
     *     return static_cast<uint8_t*>(sk_malloc_flags(aligned_size, flags));
     * }
     * ```
     */
    public fun allocImage(bytes: ULong, at: AllocType = TODO()): Int {
      TODO("Implement allocImage")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkMaskBuilder::FreeImage(void* image) {
     *     sk_free(image);
     * }
     * ```
     */
    public fun freeImage(image: Unit?) {
      TODO("Implement freeImage")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMaskBuilder SkMaskBuilder::PrepareDestination(int radiusX, int radiusY, const SkMask& src) {
     *     SkSafeMath safe;
     *
     *     SkMaskBuilder dst;
     *     dst.image() = nullptr;
     *     dst.format() = SkMask::kA8_Format;
     *
     *     // dstW = srcW + 2 * radiusX;
     *     size_t dstW = safe.add(src.fBounds.width(), safe.add(radiusX, radiusX));
     *     // dstH = srcH + 2 * radiusY;
     *     size_t dstH = safe.add(src.fBounds.height(), safe.add(radiusY, radiusY));
     *
     *     size_t toAlloc = safe.mul(dstW, dstH);
     *
     *     // We can only deal with masks that fit in INT_MAX and sides that fit in int.
     *     if (!SkTFitsIn<int>(dstW) || !SkTFitsIn<int>(dstH) || toAlloc > INT_MAX || !safe) {
     *         dst.bounds().setEmpty();
     *         dst.rowBytes() = 0;
     *         return dst;
     *     }
     *
     *     dst.bounds().setWH(SkTo<int>(dstW), SkTo<int>(dstH));
     *     dst.bounds().offset(src.fBounds.x(), src.fBounds.y());
     *     dst.bounds().offset(-radiusX, -radiusY);
     *     dst.rowBytes() = SkTo<uint32_t>(dstW);
     *
     *     if (src.fImage != nullptr) {
     *         dst.image() = SkMaskBuilder::AllocImage(toAlloc);
     *     }
     *
     *     return dst;
     * }
     * ```
     */
    public fun prepareDestination(
      radiusX: Int,
      radiusY: Int,
      src: SkMask,
    ): SkMaskBuilder {
      TODO("Implement prepareDestination")
    }
  }
}
