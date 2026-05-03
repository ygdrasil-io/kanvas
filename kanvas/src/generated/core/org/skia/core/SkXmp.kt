package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.ULong
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkXmp {
 * public:
 *     SkXmp() = default;
 *     virtual ~SkXmp() = default;
 *     // Make noncopyable
 *     SkXmp(const SkXmp&) = delete;
 *     SkXmp& operator= (const SkXmp&) = delete;
 *
 *     // Create from XMP data.
 *     static std::unique_ptr<SkXmp> Make(sk_sp<SkData> xmpData);
 *     // Create from standard XMP + extended XMP data, see XMP Specification Part 3: Storage in files,
 *     // Section 1.1.3.1: Extended XMP in JPEG
 *     static std::unique_ptr<SkXmp> Make(sk_sp<SkData> xmpStandard, sk_sp<SkData> xmpExtended);
 *
 *     // Extract HDRGM gainmap parameters.
 *     // TODO(b/338342146): Remove this once all callers are removed.
 *     bool getGainmapInfoHDRGM(SkGainmapInfo* info) const { return getGainmapInfoAdobe(info); }
 *
 *     // Extract gainmap parameters from http://ns.adobe.com/hdr-gain-map/1.0/.
 *     virtual bool getGainmapInfoAdobe(SkGainmapInfo* info) const = 0;
 *
 *     // If the image specifies http://ns.apple.com/pixeldatainfo/1.0/ AuxiliaryImageType of
 *     // urn:com:apple:photo:2020:aux:hdrgainmap, and includes a http://ns.apple.com/HDRGainMap/1.0/
 *     // HDRGainMapVersion, then populate |info| with gainmap parameters that will approximate the
 *     // math specified at [0] and return true.
 *     // [0] https://developer.apple.com/documentation/appkit/images_and_pdf/
 *     //     applying_apple_hdr_effect_to_your_photos
 *     virtual bool getGainmapInfoApple(float exifHdrHeadroom, SkGainmapInfo* info) const = 0;
 *
 *     // If this includes GContainer metadata and the GContainer contains an item with semantic
 *     // GainMap and Mime of image/jpeg, then return true, and populate |offset| and |size| with
 *     // that item's offset (from the end of the primary JPEG image's EndOfImage), and the size of
 *     // the gainmap.
 *     virtual bool getContainerGainmapLocation(size_t* offset, size_t* size) const = 0;
 *
 *     // Return the GUID of an Extended XMP if present, or null otherwise.
 *     virtual const char* getExtendedXmpGuid() const = 0;
 * }
 * ```
 */
public abstract class SkXmp public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkXmp() = default
   * ```
   */
  public constructor(param0: SkXmp) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkXmp& operator= (const SkXmp&) = delete
   * ```
   */
  public fun assign(param0: SkXmp) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getGainmapInfoHDRGM(SkGainmapInfo* info) const { return getGainmapInfoAdobe(info); }
   * ```
   */
  public fun getGainmapInfoHDRGM(info: SkGainmapInfo?): Boolean {
    TODO("Implement getGainmapInfoHDRGM")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool getGainmapInfoAdobe(SkGainmapInfo* info) const = 0
   * ```
   */
  public abstract fun getGainmapInfoAdobe(info: SkGainmapInfo?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool getGainmapInfoApple(float exifHdrHeadroom, SkGainmapInfo* info) const = 0
   * ```
   */
  public abstract fun getGainmapInfoApple(exifHdrHeadroom: Float, info: SkGainmapInfo?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool getContainerGainmapLocation(size_t* offset, size_t* size) const = 0
   * ```
   */
  public abstract fun getContainerGainmapLocation(offset: ULong?, size: ULong?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual const char* getExtendedXmpGuid() const = 0
   * ```
   */
  public abstract fun getExtendedXmpGuid(): Char

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkXmp> SkXmp::Make(sk_sp<SkData> xmpData) {
     *     std::unique_ptr<SkXmpImpl> xmp(new SkXmpImpl);
     *     if (!xmp->parseDom(std::move(xmpData), /*extended=*/false)) {
     *         return nullptr;
     *     }
     *     return xmp;
     * }
     * ```
     */
    public fun make(xmpData: SkSp<SkData>): SkXmp? {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkXmp> SkXmp::Make(sk_sp<SkData> xmpStandard, sk_sp<SkData> xmpExtended) {
     *     std::unique_ptr<SkXmpImpl> xmp(new SkXmpImpl);
     *     if (!xmp->parseDom(std::move(xmpStandard), /*extended=*/false)) {
     *         return nullptr;
     *     }
     *     // Try to parse extended xmp but ignore the return value: if parsing fails, we'll still return
     *     // the standard xmp.
     *     (void)xmp->parseDom(std::move(xmpExtended), /*extended=*/true);
     *     return xmp;
     * }
     * ```
     */
    public fun make(xmpStandard: SkSp<SkData>, xmpExtended: SkSp<SkData>): SkXmp? {
      TODO("Implement make")
    }
  }
}
