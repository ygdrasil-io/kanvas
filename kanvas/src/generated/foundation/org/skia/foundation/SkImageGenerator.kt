package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkRecorder
import org.skia.core.SkYUVAPixmapInfo
import org.skia.core.SkYUVAPixmaps

/**
 * C++ original:
 * ```cpp
 * class SK_API SkImageGenerator {
 * public:
 *     /**
 *      *  The PixelRef which takes ownership of this SkImageGenerator
 *      *  will call the image generator's destructor.
 *      */
 *     virtual ~SkImageGenerator() { }
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     /**
 *      *  Return a ref to the encoded (i.e. compressed) representation
 *      *  of this data.
 *      *
 *      *  If non-NULL is returned, the caller is responsible for calling
 *      *  unref() on the data when it is finished.
 *      */
 *     sk_sp<const SkData> refEncodedData() { return this->onRefEncodedData(); }
 *
 *     /**
 *      *  Return the ImageInfo associated with this generator.
 *      */
 *     const SkImageInfo& getInfo() const { return fInfo; }
 *
 *     /**
 *      *  Can this generator be used to produce images that will be drawable to the specified context
 *      *  (or to CPU, if context is nullptr)?
 *      */
 *     bool isValid(SkRecorder* recorder) const { return this->onIsValid(recorder); }
 *
 *     /**
 *      *  Will this generator produce protected content
 *      */
 *     bool isProtected() const {
 *         return this->onIsProtected();
 *     }
 *
 *     /**
 *      *  Decode into the given pixels, a block of memory of size at
 *      *  least (info.fHeight - 1) * rowBytes + (info.fWidth *
 *      *  bytesPerPixel)
 *      *
 *      *  Repeated calls to this function should give the same results,
 *      *  allowing the PixelRef to be immutable.
 *      *
 *      *  @param info A description of the format
 *      *         expected by the caller.  This can simply be identical
 *      *         to the info returned by getInfo().
 *      *
 *      *         This contract also allows the caller to specify
 *      *         different output-configs, which the implementation can
 *      *         decide to support or not.
 *      *
 *      *         A size that does not match getInfo() implies a request
 *      *         to scale. If the generator cannot perform this scale,
 *      *         it will return false.
 *      *
 *      *  @return true on success.
 *      */
 *     bool getPixels(const SkImageInfo& info, void* pixels, size_t rowBytes);
 *
 *     bool getPixels(const SkPixmap& pm) {
 *         return this->getPixels(pm.info(), pm.writable_addr(), pm.rowBytes());
 *     }
 *
 *     /**
 *      *  If decoding to YUV is supported, this returns true. Otherwise, this
 *      *  returns false and the caller will ignore output parameter yuvaPixmapInfo.
 *      *
 *      * @param  supportedDataTypes Indicates the data type/planar config combinations that are
 *      *                            supported by the caller. If the generator supports decoding to
 *      *                            YUV(A), but not as a type in supportedDataTypes, this method
 *      *                            returns false.
 *      *  @param yuvaPixmapInfo Output parameter that specifies the planar configuration, subsampling,
 *      *                        orientation, chroma siting, plane color types, and row bytes.
 *      */
 *     bool queryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes& supportedDataTypes,
 *                        SkYUVAPixmapInfo* yuvaPixmapInfo) const;
 *
 *     /**
 *      *  Returns true on success and false on failure.
 *      *  This always attempts to perform a full decode. To get the planar
 *      *  configuration without decoding use queryYUVAInfo().
 *      *
 *      *  @param yuvaPixmaps  Contains preallocated pixmaps configured according to a successful call
 *      *                      to queryYUVAInfo().
 *      */
 *     bool getYUVAPlanes(const SkYUVAPixmaps& yuvaPixmaps);
 *
 *     virtual bool isTextureGenerator() const { return false; }
 *
 * protected:
 *     static constexpr int kNeedNewImageUniqueID = 0;
 *
 *     SkImageGenerator(const SkImageInfo& info, uint32_t uniqueId = kNeedNewImageUniqueID);
 *
 * #if defined(SK_DISABLE_LEGACY_NONCONST_ENCODED_IMAGE_DATA)
 *     virtual sk_sp<const SkData> onRefEncodedData() { return nullptr; }
 * #else
 *     virtual sk_sp<SkData> onRefEncodedData() { return nullptr; }
 * #endif
 *     struct Options {};
 *     virtual bool onGetPixels(const SkImageInfo&, void*, size_t, const Options&) { return false; }
 *     virtual bool onIsValid(SkRecorder*) const { return true; }
 *     virtual bool onIsProtected() const { return false; }
 *     virtual bool onQueryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes&,
 *                                  SkYUVAPixmapInfo*) const { return false; }
 *     virtual bool onGetYUVAPlanes(const SkYUVAPixmaps&) { return false; }
 *
 *     const SkImageInfo fInfo;
 *
 * private:
 *     const uint32_t fUniqueID;
 *
 *     SkImageGenerator(SkImageGenerator&&) = delete;
 *     SkImageGenerator(const SkImageGenerator&) = delete;
 *     SkImageGenerator& operator=(SkImageGenerator&&) = delete;
 *     SkImageGenerator& operator=(const SkImageGenerator&) = delete;
 * }
 * ```
 */
public open class SkImageGenerator public constructor(
  info: SkImageInfo,
  uniqueId: UInt = TODO(),
) {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNeedNewImageUniqueID = 0
   * ```
   */
  protected val fInfo: Int = TODO("Initialize fInfo")

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo fInfo
   * ```
   */
  private val fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * SkImageGenerator(const SkImageInfo& info, uint32_t uniqueId = kNeedNewImageUniqueID)
   * ```
   */
  public constructor(param0: SkImageGenerator) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> refEncodedData() { return this->onRefEncodedData(); }
   * ```
   */
  public fun refEncodedData(): Int {
    TODO("Implement refEncodedData")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageInfo& getInfo() const { return fInfo; }
   * ```
   */
  public fun getInfo(): Int {
    TODO("Implement getInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid(SkRecorder* recorder) const { return this->onIsValid(recorder); }
   * ```
   */
  public fun isValid(recorder: SkRecorder?): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isProtected() const {
   *         return this->onIsProtected();
   *     }
   * ```
   */
  public fun isProtected(): Boolean {
    TODO("Implement isProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageGenerator::getPixels(const SkImageInfo& info, void* pixels, size_t rowBytes) {
   *     if (kUnknown_SkColorType == info.colorType()) {
   *         return false;
   *     }
   *     if (nullptr == pixels) {
   *         return false;
   *     }
   *     if (rowBytes < info.minRowBytes()) {
   *         return false;
   *     }
   *
   *     Options defaultOpts;
   *     return this->onGetPixels(info, pixels, rowBytes, defaultOpts);
   * }
   * ```
   */
  public fun getPixels(
    info: SkImageInfo,
    pixels: Unit?,
    rowBytes: ULong,
  ): Boolean {
    TODO("Implement getPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getPixels(const SkPixmap& pm) {
   *         return this->getPixels(pm.info(), pm.writable_addr(), pm.rowBytes());
   *     }
   * ```
   */
  public fun getPixels(pm: SkPixmap): Boolean {
    TODO("Implement getPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageGenerator::queryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes& supportedDataTypes,
   *                                      SkYUVAPixmapInfo* yuvaPixmapInfo) const {
   *     SkASSERT(yuvaPixmapInfo);
   *
   *     return this->onQueryYUVAInfo(supportedDataTypes, yuvaPixmapInfo) &&
   *            yuvaPixmapInfo->isSupported(supportedDataTypes);
   * }
   * ```
   */
  public fun queryYUVAInfo(supportedDataTypes: SkYUVAPixmapInfo.SupportedDataTypes, yuvaPixmapInfo: SkYUVAPixmapInfo?): Boolean {
    TODO("Implement queryYUVAInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageGenerator::getYUVAPlanes(const SkYUVAPixmaps& yuvaPixmaps) {
   *     return this->onGetYUVAPlanes(yuvaPixmaps);
   * }
   * ```
   */
  public fun getYUVAPlanes(yuvaPixmaps: SkYUVAPixmaps): Boolean {
    TODO("Implement getYUVAPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isTextureGenerator() const { return false; }
   * ```
   */
  public open fun isTextureGenerator(): Boolean {
    TODO("Implement isTextureGenerator")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkData> onRefEncodedData() { return nullptr; }
   * ```
   */
  protected open fun onRefEncodedData(): Int {
    TODO("Implement onRefEncodedData")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onGetPixels(const SkImageInfo&, void*, size_t, const Options&) { return false; }
   * ```
   */
  protected open fun onGetPixels(
    param0: SkImageInfo,
    param1: Unit?,
    param2: ULong,
    param3: Options,
  ): Boolean {
    TODO("Implement onGetPixels")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsValid(SkRecorder*) const { return true; }
   * ```
   */
  protected open fun onIsValid(param0: SkRecorder?): Boolean {
    TODO("Implement onIsValid")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsProtected() const { return false; }
   * ```
   */
  protected open fun onIsProtected(): Boolean {
    TODO("Implement onIsProtected")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onQueryYUVAInfo(const SkYUVAPixmapInfo::SupportedDataTypes&,
   *                                  SkYUVAPixmapInfo*) const { return false; }
   * ```
   */
  protected open fun onQueryYUVAInfo(param0: SkYUVAPixmapInfo.SupportedDataTypes, param1: SkYUVAPixmapInfo?): Boolean {
    TODO("Implement onQueryYUVAInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onGetYUVAPlanes(const SkYUVAPixmaps&) { return false; }
   * ```
   */
  protected open fun onGetYUVAPlanes(param0: SkYUVAPixmaps): Boolean {
    TODO("Implement onGetYUVAPlanes")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageGenerator& operator=(SkImageGenerator&&) = delete
   * ```
   */
  private fun assign(param0: SkImageGenerator) {
    TODO("Implement assign")
  }

  public open class Options

  public companion object {
    protected val kNeedNewImageUniqueID: Int = TODO("Initialize kNeedNewImageUniqueID")
  }
}
