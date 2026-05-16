package org.skia.tools

import SkSerialReturnType
import kotlin.Int
import kotlin.Unit
import org.skia.core.SkPicture
import org.skia.core.TArray
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.SkAlphaType

/**
 * C++ original:
 * ```cpp
 * class SkCapture : public SkRefCnt {
 * public:
 *     struct Metadata {
 *         uint32_t version;
 *         uint32_t numPictures;
 *     };
 *
 *     static sk_sp<SkCapture> MakeFromData(sk_sp<const SkData>);
 *     // TODO: instead of a make from pictures factory, the CaptureManager might just need hooks into
 *     // the to build it over time. Move the SkPictures (fPictures) here and just maintain that in one
 *     // place.
 *     static sk_sp<SkCapture> MakeFromPictures(skia_private::TArray<sk_sp<SkPicture>>);
 *     sk_sp<SkData> serializeCapture();
 *
 *     // TODO: Pictures being grabbed by index is not intuitive and leave the capture disorganized.
 *     // This should be deleted once SkPictures are organized by Surface and grouped by Recording.
 *     sk_sp<SkPicture> getPicture(int i) const;
 *     Metadata getMetadata() const;
 *
 * private:
 *     // TODO: add more awareness of the image meta data to a SkCaptureContext object
 *     static SkSerialReturnType serializeImageProc(SkImage* img, void* ctx);
 *     static sk_sp<SkImage> deserializeImageProc(sk_sp<SkData>,
 *                                                std::optional<SkAlphaType>, void* ctx);
 *
 *     Metadata fMetadata;
 *     //TODO(b/412351769): Replace pictures with SkCapturePicture structs that also include
 *     // picture metadata
 *     skia_private::TArray<sk_sp<SkPicture>> fPictures;
 *
 *     static const uint32_t kVersion = 0; // Until this version is 1 or greater, active development
 *                                         // will make this unstable.
 * }
 * ```
 */
public open class SkCapture : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * Metadata fMetadata
   * ```
   */
  private var fMetadata: Metadata = TODO("Initialize fMetadata")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkPicture>> fPictures
   * ```
   */
  private var fPictures: Int = TODO("Initialize fPictures")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkCapture::serializeCapture() {
   *     SkDynamicMemoryWStream stream;
   *
   *     stream.write32(kMagic1);
   *     stream.write32(kMagic2);
   *     stream.write32(SkCapture::kVersion);
   *
   *     // Number of pictures
   *     stream.write32(fPictures.size());
   *
   *     // TODO (b/412351769): Write metadata on each picture and assosiated canvas. This will be needed
   *     // when we have multiple SkPictures per canvas and we want to track which ones are drawn into
   *     // each other.
   *
   *     for (const auto& picture : fPictures) {
   *         SkDynamicMemoryWStream pictureStream;
   *         SkSerialProcs procs;
   *         procs.fImageProc = SkCapture::serializeImageProc;
   *         picture->serialize(&pictureStream, &procs);
   *         sk_sp<SkData> pictureData = pictureStream.detachAsData();
   *
   *         // Write size and then data
   *         stream.write32(pictureData->size());
   *         stream.write(pictureData->data(), pictureData->size());
   *     }
   *
   *     auto data = stream.detachAsData();
   *     SkDebugf("Wrote %d pictures to SkData block.\n", fPictures.size());
   *     return data;
   * }
   * ```
   */
  public fun serializeCapture(): SkSp<SkData> {
    TODO("Implement serializeCapture")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> SkCapture::getPicture(int i) const {
   *     if (i >= 0 && i < fPictures.size()) {
   *         return fPictures[i];
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun getPicture(i: Int): SkSp<SkPicture> {
    TODO("Implement getPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * SkCapture::Metadata SkCapture::getMetadata() const {
   *     return fMetadata;
   * }
   * ```
   */
  public fun getMetadata(): Metadata {
    TODO("Implement getMetadata")
  }

  public data class Metadata public constructor(
    public var version: Int,
    public var numPictures: Int,
  )

  public companion object {
    private val kVersion: Int = TODO("Initialize kVersion")

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkCapture> SkCapture::MakeFromData(sk_sp<const SkData> data) {
     *     if (!data) {
     *         return nullptr;
     *     }
     *
     *     // 1. Setup Stream
     *     SkMemoryStream stream(data->data(), data->size());
     *
     *     // 2. Read and Validate Magic Number
     *     uint32_t magic1;
     *     uint32_t magic2;
     *     if (!stream.readU32(&magic1) || !stream.readU32(&magic2) ||
     *         magic1 != kMagic1 || magic2 != kMagic2) {
     *         SkDebugf("Invalid magic number for SkCapture.\n");
     *         return nullptr;
     *     }
     *
     *     // 3. Read and Validate Version
     *     uint32_t version;
     *     if (!stream.readU32(&version) || version != kVersion) {
     *         SkDebugf("Unsupported SkCapture version: %u.\n", version);
     *         return nullptr;
     *     }
     *
     *     // 4. Read Picture Count
     *     uint32_t pictureCount;
     *     if (!stream.readU32(&pictureCount)) {
     *         SkDebugf("Failed to read picture count.\n");
     *         return nullptr;
     *     }
     *
     *     auto capture = sk_make_sp<SkCapture>();
     *     capture->fMetadata = {version, pictureCount};
     *
     *     // 5. Loop and Deserialize Each Picture
     *     for (uint32_t i = 0; i < pictureCount; ++i) {
     *         uint32_t pictureDataSize;
     *         if (!stream.readU32(&pictureDataSize)) {
     *             SkDebugf("Failed to read picture data size for picture %u.\n", i);
     *             return nullptr;
     *         }
     *
     *         // Read the picture data into an SkData object
     *         sk_sp<SkData> pictureData = SkData::MakeUninitialized(pictureDataSize);
     *
     *         // Check if allocation failed *or* if the stream read failed.
     *         if (!pictureData || stream.read(pictureData->writable_data(),
     *                                         pictureDataSize) != pictureDataSize) {
     *             SkDebugf("Failed to read picture data for picture %u or allocation failed.\n", i);
     *             return nullptr;
     *         }
     *
     *         // Deserialize the SkPicture from its raw data
     *         SkDeserialProcs procs;
     *         procs.fImageDataProc = SkCapture::deserializeImageProc;
     *         sk_sp<SkPicture> picture = SkPicture::MakeFromData(pictureData.get(), &procs);
     *         if (!picture) {
     *             SkDebugf("Failed to deserialize SkPicture for picture %u.\n", i);
     *             return nullptr;
     *         }
     *
     *         // Add the deserialized picture to the SkCapture object
     *         capture->fPictures.emplace_back(std::move(picture));
     *     }
     *
     *     SkDebugf("Successfully read %d pictures into SkCapture.\n", capture->fPictures.size());
     *     return capture;
     * }
     * ```
     */
    public fun makeFromData(`data`: SkSp<SkData>): SkSp<SkCapture> {
      TODO("Implement makeFromData")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkCapture> SkCapture::MakeFromPictures(skia_private::TArray<sk_sp<SkPicture>> pictures) {
     *     auto capture = sk_make_sp<SkCapture>();
     *
     *     capture->fMetadata = {SkCapture::kVersion, static_cast<uint32_t>(pictures.size())};
     *     capture->fPictures = pictures;
     *     return capture;
     * }
     * ```
     */
    public fun makeFromPictures(pictures: TArray<SkSp<SkPicture>>): SkSp<SkCapture> {
      TODO("Implement makeFromPictures")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSerialReturnType SkCapture::serializeImageProc(SkImage* img, void* ctx) {
     *     const int contentID = -1; // TODO: replace with real content ID.
     *     return SkData::MakeWithCopy(&contentID, sizeof(int));
     * }
     * ```
     */
    private fun serializeImageProc(img: SkImage?, ctx: Unit?): SkSerialReturnType {
      TODO("Implement serializeImageProc")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImage> SkCapture::deserializeImageProc(sk_sp<SkData>, std::optional<SkAlphaType>, void*) {
     *     // TODO: set up the SkCapture context and inspect it to grab SkPictures and pass them as images.
     *     SkBitmap b;
     *     b.allocN32Pixels(5, 5);
     *     SkCanvas canvas(b);
     *     canvas.drawColor(SK_ColorMAGENTA);
     *     return b.asImage();
     * }
     * ```
     */
    private fun deserializeImageProc(
      param0: SkSp<SkData>,
      param1: SkAlphaType?,
      ctx: Unit?,
    ): SkSp<SkImage> {
      TODO("Implement deserializeImageProc")
    }
  }
}
