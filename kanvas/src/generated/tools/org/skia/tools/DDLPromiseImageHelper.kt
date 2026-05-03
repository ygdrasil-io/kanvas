package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkPicture
import org.skia.core.SkTaskGroup
import org.skia.core.SkYUVAPixmaps
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmap
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrContextThreadSafeProxy
import org.skia.gpu.ganesh.GrDirectContext

/**
 * C++ original:
 * ```cpp
 * class DDLPromiseImageHelper {
 * public:
 *     explicit DDLPromiseImageHelper(
 *             const SkYUVAPixmapInfo::SupportedDataTypes& supportedYUVADataTypes)
 *             : fSupportedYUVADataTypes(supportedYUVADataTypes) {}
 *     ~DDLPromiseImageHelper() = default;
 *
 *     // Convert the input SkPicture into a new one which has promise images rather than live
 *     // images.
 *     sk_sp<SkPicture> recreateSKP(GrDirectContext*, SkPicture*);
 *
 *     void uploadAllToGPU(SkTaskGroup*, GrDirectContext*);
 *     void deleteAllFromGPU(SkTaskGroup*, GrDirectContext*);
 *
 *     // Remove this class' refs on the promise images and the PromiseImageCallbackContexts
 *     void reset() {
 *         fImageInfo.clear();
 *         fPromiseImages.clear();
 *     }
 *
 * private:
 *     void createCallbackContexts(GrDirectContext*);
 *     // reinflate a deflated SKP, replacing all the indices with promise images.
 *     sk_sp<SkPicture> reinflateSKP(sk_sp<GrContextThreadSafeProxy>, const SkData* deflatedSKP);
 *
 *     // This is the information extracted into this class from the parsing of the skp file.
 *     // Once it has all been uploaded to the GPU and distributed to the promise images, it
 *     // is all dropped via "reset".
 *     class PromiseImageInfo {
 *     public:
 *         PromiseImageInfo(int index, uint32_t originalUniqueID, const SkImageInfo& ii);
 *         PromiseImageInfo(PromiseImageInfo&& other);
 *         ~PromiseImageInfo();
 *
 *         int index() const { return fIndex; }
 *         uint32_t originalUniqueID() const { return fOriginalUniqueID; }
 *         bool isYUV() const { return fYUVAPixmaps.isValid(); }
 *
 *         SkISize overallDimensions() const { return fImageInfo.dimensions(); }
 *         SkColorType overallColorType() const { return fImageInfo.colorType(); }
 *         SkAlphaType overallAlphaType() const { return fImageInfo.alphaType(); }
 *         sk_sp<SkColorSpace> refOverallColorSpace() const { return fImageInfo.refColorSpace(); }
 *
 *         const SkYUVAInfo& yuvaInfo() const { return fYUVAPixmaps.yuvaInfo(); }
 *
 *         const SkPixmap& yuvPixmap(int index) const {
 *             SkASSERT(this->isYUV());
 *             return fYUVAPixmaps.planes()[index];
 *         }
 *
 *         const SkBitmap& baseLevel() const {
 *             SkASSERT(!this->isYUV());
 *             return fBaseLevel;
 *         }
 *         // This returns an array of all the available mipLevels - suitable for passing into
 *         // createBackendTexture.
 *         std::unique_ptr<SkPixmap[]> normalMipLevels() const;
 *         int numMipLevels() const;
 *
 *         void setCallbackContext(int index, sk_sp<PromiseImageCallbackContext> callbackContext) {
 *             SkASSERT(index >= 0 && index < (this->isYUV() ? SkYUVAInfo::kMaxPlanes : 1));
 *             fCallbackContexts[index] = callbackContext;
 *         }
 *         PromiseImageCallbackContext* callbackContext(int index) const {
 *             SkASSERT(index >= 0 && index < (this->isYUV() ? SkYUVAInfo::kMaxPlanes : 1));
 *             return fCallbackContexts[index].get();
 *         }
 *         sk_sp<PromiseImageCallbackContext> refCallbackContext(int index) const {
 *             SkASSERT(index >= 0 && index < (this->isYUV() ? SkYUVAInfo::kMaxPlanes : 1));
 *             return fCallbackContexts[index];
 *         }
 *
 *         skgpu::Mipmapped mipmapped(int index) const {
 *             if (this->isYUV()) {
 *                 return skgpu::Mipmapped::kNo;
 *             }
 *             return fMipLevels ? skgpu::Mipmapped::kYes : skgpu::Mipmapped::kNo;
 *         }
 *         const GrBackendFormat& backendFormat(int index) const {
 *             SkASSERT(index >= 0 && index < (this->isYUV() ? SkYUVAInfo::kMaxPlanes : 1));
 *             return fCallbackContexts[index]->backendFormat();
 *         }
 *         const GrPromiseImageTexture* promiseTexture(int index) const {
 *             SkASSERT(index >= 0 && index < (this->isYUV() ? SkYUVAInfo::kMaxPlanes : 1));
 *             return fCallbackContexts[index]->promiseImageTexture();
 *         }
 *
 *         void setMipLevels(const SkBitmap& baseLevel, std::unique_ptr<SkMipmap> mipLevels);
 *
 *         /** Takes ownership of the plane data. */
 *         void setYUVPlanes(SkYUVAPixmaps yuvaPixmaps) { fYUVAPixmaps = std::move(yuvaPixmaps); }
 *
 *     private:
 *         const int                          fIndex;                // index in the 'fImageInfo' array
 *         const uint32_t                     fOriginalUniqueID;     // original ID for deduping
 *
 *         const SkImageInfo                  fImageInfo;            // info for the overarching image
 *
 *         // CPU-side cache of a normal SkImage's mipmap levels
 *         SkBitmap                           fBaseLevel;
 *         std::unique_ptr<SkMipmap>          fMipLevels;
 *
 *         // CPU-side cache of a YUV SkImage's contents
 *         SkYUVAPixmaps                      fYUVAPixmaps;
 *
 *         // Up to SkYUVASizeInfo::kMaxCount for a YUVA image. Only one for a normal image.
 *         sk_sp<PromiseImageCallbackContext> fCallbackContexts[SkYUVAInfo::kMaxPlanes];
 *     };
 *
 *     struct DeserialImageProcContext {
 *         sk_sp<GrContextThreadSafeProxy> fThreadSafeProxy;
 *         DDLPromiseImageHelper*          fHelper;
 *     };
 *
 *     static void CreateBETexturesForPromiseImage(GrDirectContext*, PromiseImageInfo*);
 *     static void DeleteBETexturesForPromiseImage(PromiseImageInfo*);
 *
 *     static sk_sp<SkImage> CreatePromiseImages(const void* rawData, size_t length, void* ctxIn);
 *
 *     bool isValidID(int id) const { return id >= 0 && id < fImageInfo.size(); }
 *     const PromiseImageInfo& getInfo(int id) const { return fImageInfo[id]; }
 *     void uploadImage(GrDirectContext*, PromiseImageInfo*);
 *
 *     // returns -1 if not found
 *     int findImage(SkImage* image) const;
 *
 *     // returns -1 on failure
 *     int addImage(SkImage* image);
 *
 *     // returns -1 on failure
 *     int findOrDefineImage(SkImage* image);
 *
 *     SkYUVAPixmapInfo::SupportedDataTypes   fSupportedYUVADataTypes;
 *     skia_private::TArray<PromiseImageInfo> fImageInfo;
 *
 *     // TODO: review the use of 'fPromiseImages' - it doesn't seem useful/necessary
 *     skia_private::TArray<sk_sp<SkImage>>   fPromiseImages;    // All the promise images in the
 *                                                             // reconstituted picture
 * }
 * ```
 */
public data class DDLPromiseImageHelper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmapInfo::SupportedDataTypes   fSupportedYUVADataTypes
   * ```
   */
  private var fSupportedYUVADataTypes: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<PromiseImageInfo> fImageInfo
   * ```
   */
  private var fImageInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkImage>>   fPromiseImages
   * ```
   */
  private var fPromiseImages: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> recreateSKP(GrDirectContext*, SkPicture*)
   * ```
   */
  public fun recreateSKP(param0: GrDirectContext?, param1: SkPicture?): Int {
    TODO("Implement recreateSKP")
  }

  /**
   * C++ original:
   * ```cpp
   * void uploadAllToGPU(SkTaskGroup*, GrDirectContext*)
   * ```
   */
  public fun uploadAllToGPU(param0: SkTaskGroup?, param1: GrDirectContext?) {
    TODO("Implement uploadAllToGPU")
  }

  /**
   * C++ original:
   * ```cpp
   * void deleteAllFromGPU(SkTaskGroup*, GrDirectContext*)
   * ```
   */
  public fun deleteAllFromGPU(param0: SkTaskGroup?, param1: GrDirectContext?) {
    TODO("Implement deleteAllFromGPU")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fImageInfo.clear();
   *         fPromiseImages.clear();
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void createCallbackContexts(GrDirectContext*)
   * ```
   */
  private fun createCallbackContexts(param0: GrDirectContext?) {
    TODO("Implement createCallbackContexts")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> reinflateSKP(sk_sp<GrContextThreadSafeProxy>, const SkData* deflatedSKP)
   * ```
   */
  private fun reinflateSKP(param0: SkSp<GrContextThreadSafeProxy>, deflatedSKP: SkData?): Int {
    TODO("Implement reinflateSKP")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValidID(int id) const { return id >= 0 && id < fImageInfo.size(); }
   * ```
   */
  private fun isValidID(id: Int): Boolean {
    TODO("Implement isValidID")
  }

  /**
   * C++ original:
   * ```cpp
   * const PromiseImageInfo& getInfo(int id) const { return fImageInfo[id]; }
   * ```
   */
  private fun getInfo(id: Int): PromiseImageInfo {
    TODO("Implement getInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void uploadImage(GrDirectContext*, PromiseImageInfo*)
   * ```
   */
  private fun uploadImage(param0: GrDirectContext?, param1: PromiseImageInfo?) {
    TODO("Implement uploadImage")
  }

  /**
   * C++ original:
   * ```cpp
   * int findImage(SkImage* image) const
   * ```
   */
  private fun findImage(image: SkImage?): Int {
    TODO("Implement findImage")
  }

  /**
   * C++ original:
   * ```cpp
   * int addImage(SkImage* image)
   * ```
   */
  private fun addImage(image: SkImage?): Int {
    TODO("Implement addImage")
  }

  /**
   * C++ original:
   * ```cpp
   * int findOrDefineImage(SkImage* image)
   * ```
   */
  private fun findOrDefineImage(image: SkImage?): Int {
    TODO("Implement findOrDefineImage")
  }

  public data class PromiseImageInfo public constructor(
    private val fIndex: Int,
    private val fOriginalUniqueID: Int,
    private val fImageInfo: Int,
    private var fBaseLevel: Int,
    private var fMipLevels: Int,
    private var fYUVAPixmaps: Int,
    private var fCallbackContexts: Int,
  ) {
    public fun index(): Int {
      TODO("Implement index")
    }

    public fun originalUniqueID(): Int {
      TODO("Implement originalUniqueID")
    }

    public fun isYUV(): Boolean {
      TODO("Implement isYUV")
    }

    public fun overallDimensions(): Int {
      TODO("Implement overallDimensions")
    }

    public fun overallColorType(): Int {
      TODO("Implement overallColorType")
    }

    public fun overallAlphaType(): Int {
      TODO("Implement overallAlphaType")
    }

    public fun refOverallColorSpace(): Int {
      TODO("Implement refOverallColorSpace")
    }

    public fun yuvaInfo(): Int {
      TODO("Implement yuvaInfo")
    }

    public fun yuvPixmap(index: Int): Int {
      TODO("Implement yuvPixmap")
    }

    public fun baseLevel(): Int {
      TODO("Implement baseLevel")
    }

    public fun normalMipLevels(): Int {
      TODO("Implement normalMipLevels")
    }

    public fun numMipLevels(): Int {
      TODO("Implement numMipLevels")
    }

    public fun setCallbackContext(index: Int, callbackContext: SkSp<PromiseImageCallbackContext>) {
      TODO("Implement setCallbackContext")
    }

    public fun callbackContext(index: Int): PromiseImageCallbackContext {
      TODO("Implement callbackContext")
    }

    public fun refCallbackContext(index: Int): Int {
      TODO("Implement refCallbackContext")
    }

    public fun mipmapped(index: Int): Int {
      TODO("Implement mipmapped")
    }

    public fun backendFormat(index: Int): Int {
      TODO("Implement backendFormat")
    }

    public fun promiseTexture(index: Int): Int {
      TODO("Implement promiseTexture")
    }

    public fun setMipLevels(baseLevel: SkBitmap, mipLevels: SkMipmap?) {
      TODO("Implement setMipLevels")
    }

    public fun setYUVPlanes(yuvaPixmaps: SkYUVAPixmaps) {
      TODO("Implement setYUVPlanes")
    }
  }

  public data class DeserialImageProcContext public constructor(
    public var fThreadSafeProxy: Int,
    public var fHelper: DDLPromiseImageHelper?,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void CreateBETexturesForPromiseImage(GrDirectContext*, PromiseImageInfo*)
     * ```
     */
    private fun createBETexturesForPromiseImage(param0: GrDirectContext?, param1: PromiseImageInfo?) {
      TODO("Implement createBETexturesForPromiseImage")
    }

    /**
     * C++ original:
     * ```cpp
     * static void DeleteBETexturesForPromiseImage(PromiseImageInfo*)
     * ```
     */
    private fun deleteBETexturesForPromiseImage(param0: PromiseImageInfo?) {
      TODO("Implement deleteBETexturesForPromiseImage")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImage> CreatePromiseImages(const void* rawData, size_t length, void* ctxIn)
     * ```
     */
    private fun createPromiseImages(
      rawData: Unit?,
      length: ULong,
      ctxIn: Unit?,
    ): Int {
      TODO("Implement createPromiseImages")
    }
  }
}
