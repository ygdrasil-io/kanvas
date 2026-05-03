package org.skia.core

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkYUVAPixmaps {
 * public:
 *     using DataType = SkYUVAPixmapInfo::DataType;
 *     static constexpr auto kMaxPlanes = SkYUVAPixmapInfo::kMaxPlanes;
 *
 *     static SkColorType RecommendedRGBAColorType(DataType);
 *
 *     /** Allocate space for pixmaps' pixels in the SkYUVAPixmaps. */
 *     static SkYUVAPixmaps Allocate(const SkYUVAPixmapInfo& yuvaPixmapInfo);
 *
 *     /**
 *      * Use storage in SkData as backing store for pixmaps' pixels. SkData is retained by the
 *      * SkYUVAPixmaps.
 *      */
 *     static SkYUVAPixmaps FromData(const SkYUVAPixmapInfo&, sk_sp<SkData>);
 *
 *     /**
 *      * Makes a deep copy of the src SkYUVAPixmaps. The returned SkYUVAPixmaps owns its planes'
 *      * backing stores.
 *      */
 *     static SkYUVAPixmaps MakeCopy(const SkYUVAPixmaps& src);
 *
 *     /**
 *      * Use passed in memory as backing store for pixmaps' pixels. Caller must ensure memory remains
 *      * allocated while pixmaps are in use. There must be at least
 *      * SkYUVAPixmapInfo::computeTotalBytes() allocated starting at memory.
 *      */
 *     static SkYUVAPixmaps FromExternalMemory(const SkYUVAPixmapInfo&, void* memory);
 *
 *     /**
 *      * Wraps existing SkPixmaps. The SkYUVAPixmaps will have no ownership of the SkPixmaps' pixel
 *      * memory so the caller must ensure it remains valid. Will return an invalid SkYUVAPixmaps if
 *      * the SkYUVAInfo isn't compatible with the SkPixmap array (number of planes, plane dimensions,
 *      * sufficient color channels in planes, ...).
 *      */
 *     static SkYUVAPixmaps FromExternalPixmaps(const SkYUVAInfo&, const SkPixmap[kMaxPlanes]);
 *
 *     /** Default SkYUVAPixmaps is invalid. */
 *     SkYUVAPixmaps() = default;
 *     ~SkYUVAPixmaps() = default;
 *
 *     SkYUVAPixmaps(SkYUVAPixmaps&& that) = default;
 *     SkYUVAPixmaps& operator=(SkYUVAPixmaps&& that) = default;
 *     SkYUVAPixmaps(const SkYUVAPixmaps&) = default;
 *     SkYUVAPixmaps& operator=(const SkYUVAPixmaps& that) = default;
 *
 *     /** Does have initialized pixmaps compatible with its SkYUVAInfo. */
 *     bool isValid() const { return !fYUVAInfo.dimensions().isEmpty(); }
 *
 *     const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
 *
 *     DataType dataType() const { return fDataType; }
 *
 *     SkYUVAPixmapInfo pixmapsInfo() const;
 *
 *     /** Number of pixmap planes or 0 if this SkYUVAPixmaps is invalid. */
 *     int numPlanes() const { return this->isValid() ? fYUVAInfo.numPlanes() : 0; }
 *
 *     /**
 *      * Access the SkPixmap planes. They are default initialized if this is not a valid
 *      * SkYUVAPixmaps.
 *      */
 *     const std::array<SkPixmap, kMaxPlanes>& planes() const { return fPlanes; }
 *
 *     /**
 *      * Get the ith SkPixmap plane. SkPixmap will be default initialized if i >= numPlanes or this
 *      * SkYUVAPixmaps is invalid.
 *      */
 *     const SkPixmap& plane(int i) const { return fPlanes[SkToSizeT(i)]; }
 *
 *     /**
 *      * Computes a YUVALocations representation of the planar layout. The result is guaranteed to be
 *      * valid if this->isValid().
 *      */
 *     SkYUVAInfo::YUVALocations toYUVALocations() const;
 *
 *     /** Does this SkPixmaps own the backing store of the planes? */
 *     bool ownsStorage() const { return SkToBool(fData); }
 *
 * private:
 *     SkYUVAPixmaps(const SkYUVAPixmapInfo&, sk_sp<SkData>);
 *     SkYUVAPixmaps(const SkYUVAInfo&, DataType, const SkPixmap[kMaxPlanes]);
 *
 *     std::array<SkPixmap, kMaxPlanes> fPlanes = {};
 *     sk_sp<SkData> fData;
 *     SkYUVAInfo fYUVAInfo;
 *     DataType fDataType;
 * }
 * ```
 */
public data class SkYUVAPixmaps public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr auto kMaxPlanes = SkYUVAPixmapInfo::kMaxPlanes
   * ```
   */
  private var fPlanes: Int,
  /**
   * C++ original:
   * ```cpp
   * std::array<SkPixmap, kMaxPlanes> fPlanes
   * ```
   */
  private var fData: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> fData
   * ```
   */
  private var fYUVAInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo fYUVAInfo
   * ```
   */
  private var fDataType: SkYUVAPixmapsDataType,
) {
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps& operator=(SkYUVAPixmaps&& that) = default
   * ```
   */
  public fun assign(that: SkYUVAPixmaps) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps& operator=(const SkYUVAPixmaps& that) = default
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return !fYUVAInfo.dimensions().isEmpty(); }
   * ```
   */
  public fun yuvaInfo(): Int {
    TODO("Implement yuvaInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkYUVAInfo& yuvaInfo() const { return fYUVAInfo; }
   * ```
   */
  public fun dataType(): SkYUVAPixmapsDataType {
    TODO("Implement dataType")
  }

  /**
   * C++ original:
   * ```cpp
   * DataType dataType() const { return fDataType; }
   * ```
   */
  public fun pixmapsInfo(): SkYUVAPixmapInfo {
    TODO("Implement pixmapsInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmapInfo SkYUVAPixmaps::pixmapsInfo() const {
   *     if (!this->isValid()) {
   *         return {};
   *     }
   *     SkColorType colorTypes[kMaxPlanes] = {};
   *     size_t rowBytes[kMaxPlanes] = {};
   *     int numPlanes = this->numPlanes();
   *     for (int i = 0; i < numPlanes; ++i) {
   *         colorTypes[i] = fPlanes[i].colorType();
   *         rowBytes[i] = fPlanes[i].rowBytes();
   *     }
   *     return {fYUVAInfo, colorTypes, rowBytes};
   * }
   * ```
   */
  public fun numPlanes(): Int {
    TODO("Implement numPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * int numPlanes() const { return this->isValid() ? fYUVAInfo.numPlanes() : 0; }
   * ```
   */
  public fun planes(): Int {
    TODO("Implement planes")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::array<SkPixmap, kMaxPlanes>& planes() const { return fPlanes; }
   * ```
   */
  public fun plane(i: Int): Int {
    TODO("Implement plane")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPixmap& plane(int i) const { return fPlanes[SkToSizeT(i)]; }
   * ```
   */
  public fun toYUVALocations(): Int {
    TODO("Implement toYUVALocations")
  }

  /**
   * C++ original:
   * ```cpp
   * SkYUVAInfo::YUVALocations SkYUVAPixmaps::toYUVALocations() const {
   *     uint32_t channelFlags[] = {SkColorTypeChannelFlags(fPlanes[0].colorType()),
   *                                SkColorTypeChannelFlags(fPlanes[1].colorType()),
   *                                SkColorTypeChannelFlags(fPlanes[2].colorType()),
   *                                SkColorTypeChannelFlags(fPlanes[3].colorType())};
   *     auto result = fYUVAInfo.toYUVALocations(channelFlags);
   *     SkDEBUGCODE(int numPlanes;)
   *     SkASSERT(SkYUVAInfo::YUVALocation::AreValidLocations(result, &numPlanes));
   *     SkASSERT(numPlanes == this->numPlanes());
   *     return result;
   * }
   * ```
   */
  public fun ownsStorage(): Boolean {
    TODO("Implement ownsStorage")
  }

  public companion object {
    public val kMaxPlanes: Any = TODO("Initialize kMaxPlanes")

    /**
     * C++ original:
     * ```cpp
     * SkColorType SkYUVAPixmaps::RecommendedRGBAColorType(DataType dataType) {
     *     switch (dataType) {
     *         case DataType::kUnorm8:         return kRGBA_8888_SkColorType;
     *         // F16 has better GPU support than 16 bit unorm. Often "16" bit unorm values are actually
     *         // lower precision.
     *         case DataType::kUnorm16:        return kRGBA_F16_SkColorType;
     *         case DataType::kFloat16:        return kRGBA_F16_SkColorType;
     *         case DataType::kUnorm10_Unorm2: return kRGBA_1010102_SkColorType;
     *     }
     *     SkUNREACHABLE;
     * }
     * ```
     */
    public fun recommendedRGBAColorType(dataType: SkYUVAPixmapsDataType): Int {
      TODO("Implement recommendedRGBAColorType")
    }

    /**
     * C++ original:
     * ```cpp
     * SkYUVAPixmaps SkYUVAPixmaps::Allocate(const SkYUVAPixmapInfo& yuvaPixmapInfo) {
     *     if (!yuvaPixmapInfo.isValid()) {
     *         return {};
     *     }
     *     return SkYUVAPixmaps(yuvaPixmapInfo,
     *                          SkData::MakeUninitialized(yuvaPixmapInfo.computeTotalBytes()));
     * }
     * ```
     */
    public fun allocate(yuvaPixmapInfo: SkYUVAPixmapInfo): SkYUVAPixmaps {
      TODO("Implement allocate")
    }

    /**
     * C++ original:
     * ```cpp
     * SkYUVAPixmaps SkYUVAPixmaps::FromData(const SkYUVAPixmapInfo& yuvaPixmapInfo, sk_sp<SkData> data) {
     *     if (!yuvaPixmapInfo.isValid()) {
     *         return {};
     *     }
     *     if (yuvaPixmapInfo.computeTotalBytes() > data->size()) {
     *         return {};
     *     }
     *     return SkYUVAPixmaps(yuvaPixmapInfo, std::move(data));
     * }
     * ```
     */
    public fun fromData(yuvaPixmapInfo: SkYUVAPixmapInfo, `data`: SkSp<SkData>): SkYUVAPixmaps {
      TODO("Implement fromData")
    }

    /**
     * C++ original:
     * ```cpp
     * SkYUVAPixmaps SkYUVAPixmaps::MakeCopy(const SkYUVAPixmaps& src) {
     *     if (!src.isValid()) {
     *         return {};
     *     }
     *     SkYUVAPixmaps result = Allocate(src.pixmapsInfo());
     *     int n = result.numPlanes();
     *     for (int i = 0; i < n; ++i) {
     *         // We use SkRectMemCpy rather than readPixels to ensure that we don't do any alpha type
     *         // conversion.
     *         const SkPixmap& s = src.plane(i);
     *         const SkPixmap& d = result.plane(i);
     *         SkRectMemcpy(d.writable_addr(),
     *                      d.rowBytes(),
     *                      s.addr(),
     *                      s.rowBytes(),
     *                      s.info().minRowBytes(),
     *                      s.height());
     *     }
     *     return result;
     * }
     * ```
     */
    public fun makeCopy(src: SkYUVAPixmaps): SkYUVAPixmaps {
      TODO("Implement makeCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * SkYUVAPixmaps SkYUVAPixmaps::FromExternalMemory(const SkYUVAPixmapInfo& yuvaPixmapInfo,
     *                                                 void* memory) {
     *     if (!yuvaPixmapInfo.isValid()) {
     *         return {};
     *     }
     *     SkPixmap pixmaps[kMaxPlanes];
     *     yuvaPixmapInfo.initPixmapsFromSingleAllocation(memory, pixmaps);
     *     return SkYUVAPixmaps(yuvaPixmapInfo.yuvaInfo(), yuvaPixmapInfo.dataType(), pixmaps);
     * }
     * ```
     */
    public fun fromExternalMemory(yuvaPixmapInfo: SkYUVAPixmapInfo, memory: Unit?): SkYUVAPixmaps {
      TODO("Implement fromExternalMemory")
    }

    /**
     * C++ original:
     * ```cpp
     * SkYUVAPixmaps SkYUVAPixmaps::FromExternalPixmaps(const SkYUVAInfo& yuvaInfo,
     *                                                  const SkPixmap pixmaps[kMaxPlanes]) {
     *     SkColorType colorTypes[kMaxPlanes] = {};
     *     size_t rowBytes[kMaxPlanes] = {};
     *     int numPlanes = yuvaInfo.numPlanes();
     *     for (int i = 0; i < numPlanes; ++i) {
     *         colorTypes[i] = pixmaps[i].colorType();
     *         rowBytes[i] = pixmaps[i].rowBytes();
     *     }
     *     SkYUVAPixmapInfo yuvaPixmapInfo(yuvaInfo, colorTypes, rowBytes);
     *     if (!yuvaPixmapInfo.isValid()) {
     *         return {};
     *     }
     *     return SkYUVAPixmaps(yuvaInfo, yuvaPixmapInfo.dataType(), pixmaps);
     * }
     * ```
     */
    public fun fromExternalPixmaps(yuvaInfo: SkYUVAInfo, pixmaps: Array<SkPixmap>): SkYUVAPixmaps {
      TODO("Implement fromExternalPixmaps")
    }
  }
}
