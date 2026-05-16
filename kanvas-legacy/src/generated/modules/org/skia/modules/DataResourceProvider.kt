package org.skia.modules

import kotlin.CharArray
import org.skia.foundation.SkData
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class DataResourceProvider final : public skresources::ResourceProvider {
 * public:
 *     static sk_sp<skresources::ResourceProvider> Make() {
 *         return sk_sp<skresources::ResourceProvider>(new DataResourceProvider());
 *     }
 *
 *     sk_sp<skresources::ImageAsset> loadImageAsset(const char rpath[],
 *                                                   const char rname[],
 *                                                   const char rid[]) const override {
 *         if (auto data = decode_datauri("data:image/", rname)) {
 *             std::unique_ptr<SkCodec> codec = nullptr;
 *             if (SkPngDecoder::IsPng(data->bytes(), data->size())) {
 *                 codec = SkPngDecoder::Decode(data, nullptr);
 *             } else if (SkJpegDecoder::IsJpeg(data->bytes(), data->size())) {
 *                 codec = SkJpegDecoder::Decode(data, nullptr);
 *             } else {
 *                 // The spec says only JPEG or PNG should be used to encode the embedded data.
 *                 // https://learn.microsoft.com/en-us/typography/opentype/spec/svg#svg-capability-requirements-and-restrictions
 *                 SkDEBUGFAIL("Unsupported codec");
 *                 return nullptr;
 *             }
 *             if (!codec) {
 *                 return nullptr;
 *             }
 *             return skresources::MultiFrameImageAsset::Make(std::move(codec));
 *         }
 *         return nullptr;
 *     }
 *
 * private:
 *     DataResourceProvider() = default;
 *
 *     static sk_sp<SkData> decode_datauri(const char prefix[], const char uri[]) {
 *         // We only handle B64 encoded image dataURIs: data:image/<type>;base64,<data>
 *         // (https://en.wikipedia.org/wiki/Data_URI_scheme)
 *         static constexpr char kDataURIEncodingStr[] = ";base64,";
 *
 *         const size_t prefixLen = strlen(prefix);
 *         if (strncmp(uri, prefix, prefixLen) != 0) {
 *             return nullptr;
 *         }
 *
 *         const char* encoding = strstr(uri + prefixLen, kDataURIEncodingStr);
 *         if (!encoding) {
 *             return nullptr;
 *         }
 *
 *         const char* b64Data = encoding + std::size(kDataURIEncodingStr) - 1;
 *         size_t b64DataLen = strlen(b64Data);
 *         size_t dataLen;
 *         if (SkBase64::Decode(b64Data, b64DataLen, nullptr, &dataLen) != SkBase64::kNoError) {
 *             return nullptr;
 *         }
 *
 *         sk_sp<SkData> data = SkData::MakeUninitialized(dataLen);
 *         void* rawData = data->writable_data();
 *         if (SkBase64::Decode(b64Data, b64DataLen, rawData, &dataLen) != SkBase64::kNoError) {
 *             return nullptr;
 *         }
 *
 *         return data;
 *     }
 *
 *     using INHERITED = ResourceProvider;
 * }
 * ```
 */
public class DataResourceProvider public constructor() : ResourceProvider() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<skresources::ImageAsset> loadImageAsset(const char rpath[],
   *                                                   const char rname[],
   *                                                   const char rid[]) const override {
   *         if (auto data = decode_datauri("data:image/", rname)) {
   *             std::unique_ptr<SkCodec> codec = nullptr;
   *             if (SkPngDecoder::IsPng(data->bytes(), data->size())) {
   *                 codec = SkPngDecoder::Decode(data, nullptr);
   *             } else if (SkJpegDecoder::IsJpeg(data->bytes(), data->size())) {
   *                 codec = SkJpegDecoder::Decode(data, nullptr);
   *             } else {
   *                 // The spec says only JPEG or PNG should be used to encode the embedded data.
   *                 // https://learn.microsoft.com/en-us/typography/opentype/spec/svg#svg-capability-requirements-and-restrictions
   *                 SkDEBUGFAIL("Unsupported codec");
   *                 return nullptr;
   *             }
   *             if (!codec) {
   *                 return nullptr;
   *             }
   *             return skresources::MultiFrameImageAsset::Make(std::move(codec));
   *         }
   *         return nullptr;
   *     }
   * ```
   */
  public override fun loadImageAsset(
    rpath: CharArray,
    rname: CharArray,
    rid: CharArray,
  ): SkSp<ImageAsset> {
    TODO("Implement loadImageAsset")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<skresources::ResourceProvider> Make() {
     *         return sk_sp<skresources::ResourceProvider>(new DataResourceProvider());
     *     }
     * ```
     */
    public fun make(): SkSp<ResourceProvider> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkData> decode_datauri(const char prefix[], const char uri[]) {
     *         // We only handle B64 encoded image dataURIs: data:image/<type>;base64,<data>
     *         // (https://en.wikipedia.org/wiki/Data_URI_scheme)
     *         static constexpr char kDataURIEncodingStr[] = ";base64,";
     *
     *         const size_t prefixLen = strlen(prefix);
     *         if (strncmp(uri, prefix, prefixLen) != 0) {
     *             return nullptr;
     *         }
     *
     *         const char* encoding = strstr(uri + prefixLen, kDataURIEncodingStr);
     *         if (!encoding) {
     *             return nullptr;
     *         }
     *
     *         const char* b64Data = encoding + std::size(kDataURIEncodingStr) - 1;
     *         size_t b64DataLen = strlen(b64Data);
     *         size_t dataLen;
     *         if (SkBase64::Decode(b64Data, b64DataLen, nullptr, &dataLen) != SkBase64::kNoError) {
     *             return nullptr;
     *         }
     *
     *         sk_sp<SkData> data = SkData::MakeUninitialized(dataLen);
     *         void* rawData = data->writable_data();
     *         if (SkBase64::Decode(b64Data, b64DataLen, rawData, &dataLen) != SkBase64::kNoError) {
     *             return nullptr;
     *         }
     *
     *         return data;
     *     }
     * ```
     */
    private fun decodeDatauri(prefix: CharArray, uri: CharArray): SkSp<SkData> {
      TODO("Implement decodeDatauri")
    }
  }
}
