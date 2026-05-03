package org.skia.tools

import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SkSharingDeserialContext {
 *     // a list of unique images in the order they were encountered in the file
 *     // Subsequent occurrences of an image refer to it by it's index in this list.
 *     std::vector<sk_sp<SkImage>> fImages;
 *
 *     // A deserial proc that can interpret id's in place of images as references to previous images.
 *     // Can also deserialize a SKP where all images are inlined (it's backwards compatible)
 *     static sk_sp<SkImage> deserializeImage(const void* data, size_t length, void* ctx);
 * }
 * ```
 */
public data class SkSharingDeserialContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkImage>> fImages
   * ```
   */
  public var fImages: Int,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImage> SkSharingDeserialContext::deserializeImage(
     *   const void* data, size_t length, void* ctx) {
     *     if (!data || !length || !ctx) {
     *         SkDebugf("SkSharingDeserialContext::deserializeImage arguments invalid %p %zu %p.\n",
     *             data, length, ctx);
     *         // Return something so the rest of the debugger can proceed.
     *         SkBitmap bm;
     *         bm.allocPixels(SkImageInfo::MakeN32Premul(1, 1));
     *         return bm.asImage();
     *     }
     *     SkSharingDeserialContext* context = reinterpret_cast<SkSharingDeserialContext*>(ctx);
     *     uint32_t fid;
     *     // If the data is an image fid, look up an already deserialized image from our map
     *     if (length == sizeof(fid)) {
     *         memcpy(&fid, data, sizeof(fid));
     *         if (fid >= context->fImages.size()) {
     *             SkDebugf("Cannot deserialize using id, We do not have the data for image %u.\n", fid);
     *             return nullptr;
     *         }
     *         return context->fImages[fid];
     *     }
     *     // Otherwise, the data is an image, deserialise it, store it in our map at its fid.
     *     // TODO(nifong): make DeserialProcs accept sk_sp<SkData> so we don't have to copy this.
     *     sk_sp<SkData> dataView = SkData::MakeWithCopy(data, length);
     *     auto codec = SkPngDecoder::Decode(std::move(dataView), nullptr);
     *     if (!codec) {
     *         SkDebugf("Cannot deserialize image - might not be a PNG.\n");
     *         return nullptr;
     *     }
     *     SkImageInfo info = codec->getInfo().makeAlphaType(kPremul_SkAlphaType);
     *     auto [image, result] = codec->getImage(info);
     *     if (result != SkCodec::Result::kSuccess) {
     *         SkDebugf("Error decoding image %d.\n", result);
     *         // Might have partially decoded.
     *     }
     *     context->fImages.push_back(image);
     *     return image;
     * }
     * ```
     */
    public fun deserializeImage(
      `data`: Unit?,
      length: ULong,
      ctx: Unit?,
    ): Int {
      TODO("Implement deserializeImage")
    }
  }
}
