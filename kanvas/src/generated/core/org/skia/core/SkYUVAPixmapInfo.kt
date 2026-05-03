package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.Pair
import kotlin.ULong
import kotlin.Unit
import kotlin.bitset
import org.skia.foundation.SkPixmap
import undefined.PlaneConfig

/**
 * C++ original:
 * ```cpp
 * class SK_API SkYUVAPixmapInfo {
 * public:
 *     static constexpr auto kMaxPlanes = SkYUVAInfo::kMaxPlanes;
 *
 *     using PlaneConfig  = SkYUVAInfo::PlaneConfig;
 *     using Subsampling  = SkYUVAInfo::Subsampling;
 *
 *     /**
 *      * Data type for Y, U, V, and possibly A channels independent of how values are packed into
 *      * planes.
 *      **/
 *     enum class DataType {
 *         kUnorm8,          ///< 8 bit unsigned normalized
 *         kUnorm16,         ///< 16 bit unsigned normalized
 *         kFloat16,         ///< 16 bit (half) floating point
 *         kUnorm10_Unorm2,  ///< 10 bit unorm for Y, U, and V. 2 bit unorm for alpha (if present).
 *
 *         kLast = kUnorm10_Unorm2
 *     };
 *     static constexpr int kDataTypeCnt = static_cast<int>(DataType::kLast) + 1;
 *
 *     class SK_API SupportedDataTypes {
 *     public:
 *         /** Defaults to nothing supported. */
 *         constexpr SupportedDataTypes() = default;
 *
 *         /** All legal combinations of PlaneConfig and DataType are supported. */
 *         static constexpr SupportedDataTypes All();
 *
 *         /**
 *          * Checks whether there is a supported combination of color types for planes structured
 *          * as indicated by PlaneConfig with channel data types as indicated by DataType.
 *          */
 *         constexpr bool supported(PlaneConfig, DataType) const;
 *
 *         /**
 *          * Update to add support for pixmaps with numChannel channels where each channel is
 *          * represented as DataType.
 *          */
 *         void enableDataType(DataType, int numChannels);
 *
 *     private:
 *         // The bit for DataType dt with n channels is at index kDataTypeCnt*(n-1) + dt.
 *         std::bitset<kDataTypeCnt*4> fDataTypeSupport = {};
 *     };
 *
 *     /**
 *      * Gets the default SkColorType to use with numChannels channels, each represented as DataType.
 *      * Returns kUnknown_SkColorType if no such color type.
 *      */
 *     static constexpr SkColorType DefaultColorTypeForDataType(DataType dataType, int numChannels);
 *
 *     /**
 *      * If the SkColorType is supported for YUVA pixmaps this will return the number of YUVA channels
 *      * that can be stored in a plane of this color type and what the DataType is of those channels.
 *      * If the SkColorType is not supported as a YUVA plane the number of channels is reported as 0
 *      * and the DataType returned should be ignored.
 *      */
 *     static std::tuple<int, DataType> NumChannelsAndDataType(SkColorType);
 *
 *     /** Default SkYUVAPixmapInfo is invalid. */
 *     SkYUVAPixmapInfo() = default;
 *
 *     /**
 *      * Initializes the SkYUVAPixmapInfo from a SkYUVAInfo with per-plane color types and row bytes.
 *      * This will be invalid if the colorTypes aren't compatible with the SkYUVAInfo or if a
 *      * rowBytes entry is not valid for the plane dimensions and color type. Color type and
 *      * row byte values beyond the number of planes in SkYUVAInfo are ignored. All SkColorTypes
 *      * must have the same DataType or this will be invalid.
 *      *
 *      * If rowBytes is nullptr then bpp*width is assumed for each plane.
 *      */
 *     SkYUVAPixmapInfo(const SkYUVAInfo&,
 *                      const SkColorType[kMaxPlanes],
 *                      const size_t rowBytes[kMaxPlanes]);
 *     /**
 *      * Like above but uses DefaultColorTypeForDataType to determine each plane's SkColorType. If
 *      * rowBytes is nullptr then bpp*width is assumed for each plane.
 *      */
 *     SkYUVAPixmapInfo(const SkYUVAInfo&, DataType, const size_t rowBytes[kMaxPlanes]);
 *
 *     SkYUVAPixmapInfo(const SkYUVAPixmapInfo&) = default;
 *
 *     SkYUVAPixmapInfo& operator=(const SkYUVAPixmapInfo&) = default;
 *
 *     bool operator==(const SkYUVAPixmapInfo&) const;
 *     bool operator!=(const SkYUVAPixmapInfo& that) const { return !(*this == that); }
 *
 *     const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
 *
 *     SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
 *
 *     /** The number of SkPixmap planes, 0 if this SkYUVAPixmapInfo is invalid. */
 *     int numPlanes() const { return fYUVAInfo.numPlanes(); }
 *
 *     /** The per-YUV[A] channel data type. */
 *     DataType dataType() const { return fDataType; }
 *
 *     /**
 *      * Row bytes for the ith plane. Returns zero if i >= numPlanes() or this SkYUVAPixmapInfo is
 *      * invalid.
 *      */
 *     size_t rowBytes(int i) const { return fRowBytes[static_cast<size_t>(i)]; }
 *
 *     /** Image info for the ith plane, or default SkImageInfo if i >= numPlanes() */
 *     const SkImageInfo& planeInfo(int i) const { return fPlaneInfos[static_cast<size_t>(i)]; }
 *
 *     /**
 *      * Determine size to allocate for all planes. Optionally retrieves the per-plane sizes in
 *      * planeSizes if not null. If total size overflows will return SIZE_MAX and set all planeSizes
 *      * to SIZE_MAX. Returns 0 and fills planesSizes with 0 if this SkYUVAPixmapInfo is not valid.
 *      */
 *     size_t computeTotalBytes(size_t planeSizes[kMaxPlanes] = nullptr) const;
 *
 *     /**
 *      * Takes an allocation that is assumed to be at least computeTotalBytes() in size and configures
 *      * the first numPlanes() entries in pixmaps array to point into that memory. The remaining
 *      * entries of pixmaps are default initialized. Fails if this SkYUVAPixmapInfo not valid.
 *      */
 *     bool initPixmapsFromSingleAllocation(void* memory, SkPixmap pixmaps[kMaxPlanes]) const;
 *
 *     /**
 *      * Returns true if this has been configured with a non-empty dimensioned SkYUVAInfo with
 *      * compatible color types and row bytes.
 *      */
 *     bool isValid() const { return fYUVAInfo.isValid(); }
 *
 *     /** Is this valid and does it use color types allowed by the passed SupportedDataTypes? */
 *     bool isSupported(const SupportedDataTypes&) const;
 *
 * private:
 *     SkYUVAInfo fYUVAInfo;
 *     std::array<SkImageInfo, kMaxPlanes> fPlaneInfos = {};
 *     std::array<size_t, kMaxPlanes> fRowBytes = {};
 *     DataType fDataType = DataType::kUnorm8;
 *     static_assert(kUnknown_SkColorType == 0, "default init isn't kUnknown");
 * }
 * ```
 */
public data class SkYUVAPixmapInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr auto kMaxPlanes
   * ```
   */
  private var fYUVAInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kDataTypeCnt = static_cast<int>(DataType::kLast) + 1
   * ```
   */
  private var fPlaneInfos: Int,
  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo fYUVAInfo
   * ```
   */
  private var fRowBytes: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<SkImageInfo, kMaxPlanes> fPlaneInfos
   * ```
   */
  private var fDataType: DataType,
) {
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmapInfo& operator=(const SkYUVAPixmapInfo&) = default
   * ```
   */
  private fun assign(param0: SkYUVAPixmapInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkYUVAPixmapInfo::operator==(const SkYUVAPixmapInfo& that) const {
   *     bool result = fYUVAInfo   == that.fYUVAInfo   &&
   *                   fPlaneInfos == that.fPlaneInfos &&
   *                   fRowBytes   == that.fRowBytes;
   *     SkASSERT(!result || fDataType == that.fDataType);
   *     return result;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkYUVAPixmapInfo& that) const { return !(*this == that); }
   * ```
   */
  private fun yuvaInfo(): Int {
    TODO("Implement yuvaInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
   * ```
   */
  private fun yuvColorSpace(): Int {
    TODO("Implement yuvColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVColorSpace yuvColorSpace() const { return fYUVAInfo.yuvColorSpace(); }
   * ```
   */
  private fun numPlanes(): Int {
    TODO("Implement numPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * int numPlanes() const { return fYUVAInfo.numPlanes(); }
   * ```
   */
  private fun dataType(): DataType {
    TODO("Implement dataType")
  }

  /**
   * C++ original:
   * ```cpp
   * DataType dataType() const { return fDataType; }
   * ```
   */
  private fun rowBytes(i: Int): ULong {
    TODO("Implement rowBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t rowBytes(int i) const { return fRowBytes[static_cast<size_t>(i)]; }
   * ```
   */
  private fun planeInfo(i: Int): Int {
    TODO("Implement planeInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& planeInfo(int i) const { return fPlaneInfos[static_cast<size_t>(i)]; }
   * ```
   */
  private fun computeTotalBytes(planeSizes: Array<ULong> = null): ULong {
    TODO("Implement computeTotalBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkYUVAPixmapInfo::computeTotalBytes(size_t planeSizes[kMaxPlanes]) const {
   *     if (!this->isValid()) {
   *         if (planeSizes) {
   *             std::fill_n(planeSizes, kMaxPlanes, 0);
   *         }
   *         return 0;
   *     }
   *     return fYUVAInfo.computeTotalBytes(fRowBytes.data(), planeSizes);
   * }
   * ```
   */
  private fun initPixmapsFromSingleAllocation(memory: Unit?, pixmaps: Array<SkPixmap>): Boolean {
    TODO("Implement initPixmapsFromSingleAllocation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkYUVAPixmapInfo::initPixmapsFromSingleAllocation(void* memory,
   *                                                        SkPixmap pixmaps[kMaxPlanes]) const {
   *     if (!this->isValid()) {
   *         return false;
   *     }
   *     SkASSERT(pixmaps);
   *     char* addr = static_cast<char*>(memory);
   *     int n = this->numPlanes();
   *     for (int i = 0; i < n; ++i) {
   *         SkASSERT(fPlaneInfos[i].validRowBytes(fRowBytes[i]));
   *         pixmaps[i].reset(fPlaneInfos[i], addr, fRowBytes[i]);
   *         size_t planeSize = pixmaps[i].rowBytes()*pixmaps[i].height();
   *         SkASSERT(planeSize);
   *         addr += planeSize;
   *     }
   *     for (int i = n; i < kMaxPlanes; ++i) {
   *         pixmaps[i] = {};
   *     }
   *     return true;
   * }
   * ```
   */
  private fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fYUVAInfo.isValid(); }
   * ```
   */
  private fun isSupported(supportedDataTypes: SupportedDataTypes): Boolean {
    TODO("Implement isSupported")
  }

  public data class SupportedDataTypes public constructor(
    private var fDataTypeSupport: bitset<Int>,
  ) {
    public fun supported(config: PlaneConfig, type: undefined.DataType): Boolean {
      TODO("Implement supported")
    }

    public fun enableDataType(type: undefined.DataType, numChannels: Int) {
      TODO("Implement enableDataType")
    }

    public companion object {
      public fun all(): SupportedDataTypes {
        TODO("Implement all")
      }
    }
  }

  public enum class DataType {
    kUnorm8,
    kUnorm16,
    kFloat16,
    kUnorm10_Unorm2,
    kLast,
  }

  public companion object {
    public val kMaxPlanes: Any = TODO("Initialize kMaxPlanes")

    public val kDataTypeCnt: Int = TODO("Initialize kDataTypeCnt")

    /**
     * C++ original:
     * ```cpp
     * constexpr SkColorType SkYUVAPixmapInfo::DefaultColorTypeForDataType(DataType dataType,
     *                                                                     int numChannels) {
     *     switch (numChannels) {
     *         case 1:
     *             switch (dataType) {
     *                 case DataType::kUnorm8:         return kGray_8_SkColorType;
     *                 case DataType::kUnorm16:        return kA16_unorm_SkColorType;
     *                 case DataType::kFloat16:        return kA16_float_SkColorType;
     *                 case DataType::kUnorm10_Unorm2: return kUnknown_SkColorType;
     *             }
     *             break;
     *         case 2:
     *             switch (dataType) {
     *                 case DataType::kUnorm8:         return kR8G8_unorm_SkColorType;
     *                 case DataType::kUnorm16:        return kR16G16_unorm_SkColorType;
     *                 case DataType::kFloat16:        return kR16G16_float_SkColorType;
     *                 case DataType::kUnorm10_Unorm2: return kUnknown_SkColorType;
     *             }
     *             break;
     *         case 3:
     *             // None of these are tightly packed. The intended use case is for interleaved YUVA
     *             // planes where we're forcing opaqueness by ignoring the alpha values.
     *             // There are "x" rather than "A" variants for Unorm8 and Unorm10_Unorm2 but we don't
     *             // choose them because 1) there is no inherent advantage and 2) there is better support
     *             // in the GPU backend for the "A" versions.
     *             switch (dataType) {
     *                 case DataType::kUnorm8:         return kRGBA_8888_SkColorType;
     *                 case DataType::kUnorm16:        return kR16G16B16A16_unorm_SkColorType;
     *                 case DataType::kFloat16:        return kRGBA_F16_SkColorType;
     *                 case DataType::kUnorm10_Unorm2: return kRGBA_1010102_SkColorType;
     *             }
     *             break;
     *         case 4:
     *             switch (dataType) {
     *                 case DataType::kUnorm8:         return kRGBA_8888_SkColorType;
     *                 case DataType::kUnorm16:        return kR16G16B16A16_unorm_SkColorType;
     *                 case DataType::kFloat16:        return kRGBA_F16_SkColorType;
     *                 case DataType::kUnorm10_Unorm2: return kRGBA_1010102_SkColorType;
     *             }
     *             break;
     *     }
     *     return kUnknown_SkColorType;
     * }
     * ```
     */
    private fun defaultColorTypeForDataType(dataType: DataType, numChannels: Int): Int {
      TODO("Implement defaultColorTypeForDataType")
    }

    /**
     * C++ original:
     * ```cpp
     * std::tuple<int, SkYUVAPixmapInfo::DataType> SkYUVAPixmapInfo::NumChannelsAndDataType(
     *         SkColorType ct) {
     *     // We could allow BGR[A] color types, but then we'd have to decide whether B should be the 0th
     *     // or 2nd channel. Our docs currently say channel order is always R=0, G=1, B=2[, A=3].
     *     switch (ct) {
     *         case kAlpha_8_SkColorType:
     *         case kGray_8_SkColorType:    return {1, DataType::kUnorm8 };
     *         case kR16_unorm_SkColorType:
     *         case kA16_unorm_SkColorType: return {1, DataType::kUnorm16};
     *         case kA16_float_SkColorType: return {1, DataType::kFloat16};
     *
     *         case kR8G8_unorm_SkColorType:   return {2, DataType::kUnorm8  };
     *         case kR16G16_unorm_SkColorType: return {2, DataType::kUnorm16 };
     *         case kR16G16_float_SkColorType: return {2, DataType::kFloat16 };
     *
     *         case kRGB_888x_SkColorType:       return {3, DataType::kUnorm8          };
     *         case kRGB_101010x_SkColorType:    return {3, DataType::kUnorm10_Unorm2  };
     *         case kRGB_F16F16F16x_SkColorType: return {3, DataType::kFloat16         };
     *
     *         case kRGBA_8888_SkColorType:          return {4, DataType::kUnorm8  };
     *         case kR16G16B16A16_unorm_SkColorType: return {4, DataType::kUnorm16 };
     *         case kRGBA_F16_SkColorType:           return {4, DataType::kFloat16 };
     *         case kRGBA_F16Norm_SkColorType:       return {4, DataType::kFloat16 };
     *         case kRGBA_1010102_SkColorType:       return {4, DataType::kUnorm10_Unorm2 };
     *
     *         default: return {0, DataType::kUnorm8 };
     *     }
     * }
     * ```
     */
    private fun numChannelsAndDataType(ct: SkColorType): Pair<Int, DataType> {
      TODO("Implement numChannelsAndDataType")
    }
  }
}
