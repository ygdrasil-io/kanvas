package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkColorSpace
import org.skia.core.SkColorType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorInfo {
 * public:
 *     /** Creates an SkColorInfo with kUnknown_SkColorType, kUnknown_SkAlphaType,
 *         and no SkColorSpace.
 *
 *         @return  empty SkImageInfo
 *     */
 *     SkColorInfo();
 *     ~SkColorInfo();
 *
 *     /** Creates SkColorInfo from SkColorType ct, SkAlphaType at, and optionally SkColorSpace cs.
 *
 *         If SkColorSpace cs is nullptr and SkColorInfo is part of drawing source: SkColorSpace
 *         defaults to sRGB, mapping into SkSurface SkColorSpace.
 *
 *         Parameters are not validated to see if their values are legal, or that the
 *         combination is supported.
 *         @return        created SkColorInfo
 *     */
 *     SkColorInfo(SkColorType ct, SkAlphaType at, sk_sp<SkColorSpace> cs);
 *
 *     SkColorInfo(const SkColorInfo&);
 *     SkColorInfo(SkColorInfo&&);
 *
 *     SkColorInfo& operator=(const SkColorInfo&);
 *     SkColorInfo& operator=(SkColorInfo&&);
 *
 *     SkColorSpace* colorSpace() const;
 *     sk_sp<SkColorSpace> refColorSpace() const;
 *     SkColorType colorType() const { return fColorType; }
 *     SkAlphaType alphaType() const { return fAlphaType; }
 *
 *     bool isOpaque() const {
 *         return SkAlphaTypeIsOpaque(fAlphaType)
 *             || SkColorTypeIsAlwaysOpaque(fColorType);
 *     }
 *
 *     bool gammaCloseToSRGB() const;
 *
 *     /** Does other represent the same color type, alpha type, and color space? */
 *     bool operator==(const SkColorInfo& other) const;
 *
 *     /** Does other represent a different color type, alpha type, or color space? */
 *     bool operator!=(const SkColorInfo& other) const;
 *
 *     /** Creates SkColorInfo with same SkColorType, SkColorSpace, with SkAlphaType set
 *         to newAlphaType.
 *
 *         Created SkColorInfo contains newAlphaType even if it is incompatible with
 *         SkColorType, in which case SkAlphaType in SkColorInfo is ignored.
 *     */
 *     SkColorInfo makeAlphaType(SkAlphaType newAlphaType) const;
 *
 *     /** Creates new SkColorInfo with same SkAlphaType, SkColorSpace, with SkColorType
 *         set to newColorType.
 *     */
 *     SkColorInfo makeColorType(SkColorType newColorType) const;
 *
 *     /** Creates SkColorInfo with same SkAlphaType, SkColorType, with SkColorSpace
 *         set to cs. cs may be nullptr.
 *     */
 *     SkColorInfo makeColorSpace(sk_sp<SkColorSpace> cs) const;
 *
 *     /** Returns number of bytes per pixel required by SkColorType.
 *         Returns zero if colorType() is kUnknown_SkColorType.
 *
 *         @return  bytes in pixel
 *
 *         example: https://fiddle.skia.org/c/@ImageInfo_bytesPerPixel
 *     */
 *     int bytesPerPixel() const;
 *
 *     /** Returns bit shift converting row bytes to row pixels.
 *         Returns zero for kUnknown_SkColorType.
 *
 *         @return  one of: 0, 1, 2, 3, 4; left shift to convert pixels to bytes
 *
 *         example: https://fiddle.skia.org/c/@ImageInfo_shiftPerPixel
 *     */
 *     int shiftPerPixel() const;
 *
 * private:
 *     sk_sp<SkColorSpace> fColorSpace;
 *     SkColorType fColorType = kUnknown_SkColorType;
 *     SkAlphaType fAlphaType = kUnknown_SkAlphaType;
 * }
 * ```
 */
public data class SkColorInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fColorSpace
   * ```
   */
  private var fColorSpace: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColorType fColorType
   * ```
   */
  private var fColorType: Int,
  /**
   * C++ original:
   * ```cpp
   * SkAlphaType fAlphaType
   * ```
   */
  private var fAlphaType: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkColorInfo& SkColorInfo::operator=(const SkColorInfo&)
   * ```
   */
  public fun assign(param0: SkColorInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo& SkColorInfo::operator=(SkColorInfo&&)
   * ```
   */
  public fun colorSpace(): SkColorSpace {
    TODO("Implement colorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorSpace* SkColorInfo::colorSpace() const { return fColorSpace.get(); }
   * ```
   */
  public fun refColorSpace(): Int {
    TODO("Implement refColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkColorInfo::refColorSpace() const { return fColorSpace; }
   * ```
   */
  public fun colorType(): Int {
    TODO("Implement colorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorType colorType() const { return fColorType; }
   * ```
   */
  public fun alphaType(): Int {
    TODO("Implement alphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAlphaType alphaType() const { return fAlphaType; }
   * ```
   */
  public fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isOpaque() const {
   *         return SkAlphaTypeIsOpaque(fAlphaType)
   *             || SkColorTypeIsAlwaysOpaque(fColorType);
   *     }
   * ```
   */
  public fun gammaCloseToSRGB(): Boolean {
    TODO("Implement gammaCloseToSRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorInfo::gammaCloseToSRGB() const {
   *     return fColorSpace && fColorSpace->gammaCloseToSRGB();
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorInfo::operator==(const SkColorInfo& other) const {
   *     return fColorType == other.fColorType && fAlphaType == other.fAlphaType &&
   *            SkColorSpace::Equals(fColorSpace.get(), other.fColorSpace.get());
   * }
   * ```
   */
  public fun makeAlphaType(newAlphaType: SkAlphaType): SkColorInfo {
    TODO("Implement makeAlphaType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorInfo::operator!=(const SkColorInfo& other) const { return !(*this == other); }
   * ```
   */
  public fun makeColorType(newColorType: SkColorType): SkColorInfo {
    TODO("Implement makeColorType")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo SkColorInfo::makeAlphaType(SkAlphaType newAlphaType) const {
   *     return SkColorInfo(this->colorType(), newAlphaType, this->refColorSpace());
   * }
   * ```
   */
  public fun makeColorSpace(cs: SkSp<SkColorSpace>): SkColorInfo {
    TODO("Implement makeColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo SkColorInfo::makeColorType(SkColorType newColorType) const {
   *     return SkColorInfo(newColorType, this->alphaType(), this->refColorSpace());
   * }
   * ```
   */
  public fun bytesPerPixel(): Int {
    TODO("Implement bytesPerPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColorInfo SkColorInfo::makeColorSpace(sk_sp<SkColorSpace> cs) const {
   *     return SkColorInfo(this->colorType(), this->alphaType(), std::move(cs));
   * }
   * ```
   */
  public fun shiftPerPixel(): Int {
    TODO("Implement shiftPerPixel")
  }
}
