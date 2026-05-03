package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.codec.Options

/**
 * C++ original:
 * ```cpp
 * class SK_API SkJpegGainmapEncoder {
 * public:
 *     /**
 *      *  Encode an UltraHDR image to |dst|.
 *      *
 *      *  The base image is specified by |base|, and |baseOptions| controls the encoding behavior for
 *      *  the base image.
 *      *
 *      *  The gainmap image is specified by |gainmap|, and |gainmapOptions| controls the encoding
 *      *  behavior for the gainmap image.
 *      *
 *      *  The rendering behavior of the gainmap image is provided in |gainmapInfo|.
 *      *
 *      *  If |baseOptions| or |gainmapOptions| specify XMP metadata, then that metadata will be
 *      *  overwritten.
 *      *
 *      *  Returns true on success.  Returns false on an invalid or unsupported |src|.
 *      */
 *     static bool EncodeHDRGM(SkWStream* dst,
 *                             const SkPixmap& base,
 *                             const SkJpegEncoder::Options& baseOptions,
 *                             const SkPixmap& gainmap,
 *                             const SkJpegEncoder::Options& gainmapOptions,
 *                             const SkGainmapInfo& gainmapInfo);
 *
 *     /**
 *      *  Write a Multi Picture Format containing the |imageCount| images specified by |images|.
 *      */
 *     static bool MakeMPF(SkWStream* dst, const SkData** images, size_t imageCount);
 * }
 * ```
 */
public open class SkJpegGainmapEncoder {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkJpegGainmapEncoder::EncodeHDRGM(SkWStream* dst,
     *                                        const SkPixmap& base,
     *                                        const SkJpegEncoder::Options& baseOptions,
     *                                        const SkPixmap& gainmap,
     *                                        const SkJpegEncoder::Options& gainmapOptions,
     *                                        const SkGainmapInfo& gainmapInfo) {
     *     bool includeUltraHDRv1 = gainmapInfo.isUltraHDRv1Compatible();
     *
     *     // All images will have the same minimial Exif metadata.
     *     auto exif_params = get_exif_params();
     *
     *     // Encode the gainmap image.
     *     sk_sp<SkData> gainmapData;
     *     {
     *         SkJpegMetadataEncoder::SegmentList metadataSegments;
     *
     *         // Start with Exif metadata.
     *         metadataSegments.emplace_back(kExifMarker, exif_params);
     *
     *         // MPF segment will be inserted after this.
     *
     *         // Add XMP metadata.
     *         if (includeUltraHDRv1) {
     *             SkJpegMetadataEncoder::AppendXMPStandard(
     *                     metadataSegments, get_gainmap_image_xmp_metadata(gainmapInfo).get());
     *         }
     *
     *         // Include the ICC profile of the alternate color space, if it is used.
     *         if (gainmapInfo.fGainmapMathColorSpace) {
     *             SkJpegMetadataEncoder::AppendICC(
     *                     metadataSegments, gainmapOptions, gainmapInfo.fGainmapMathColorSpace.get());
     *         }
     *
     *         // Add the ISO 21946-1 metadata.
     *         metadataSegments.emplace_back(kISOGainmapMarker,
     *                                       get_iso_gainmap_segment_params(gainmapInfo.serialize()));
     *
     *         // Encode the gainmap image.
     *         gainmapData = encode_to_data(gainmap, gainmapOptions, metadataSegments);
     *         if (!gainmapData) {
     *             SkCodecPrintf("Failed to encode gainmap image.\n");
     *             return false;
     *         }
     *     }
     *
     *     // Encode the base image.
     *     sk_sp<SkData> baseData;
     *     {
     *         SkJpegMetadataEncoder::SegmentList metadataSegments;
     *
     *         // Start with Exif metadata.
     *         metadataSegments.emplace_back(kExifMarker, exif_params);
     *
     *         // MPF segment will be inserted after this.
     *
     *         // Include XMP.
     *         if (includeUltraHDRv1) {
     *             // Add to the gainmap image size the size of the MPF segment for image 1 of a 2-image
     *             // file.
     *             SkJpegMultiPictureParameters mpParams(2);
     *             size_t gainmapImageSize = gainmapData->size() + get_mpf_segment(mpParams, 1)->size();
     *             SkJpegMetadataEncoder::AppendXMPStandard(
     *                     metadataSegments,
     *                     get_base_image_xmp_metadata(static_cast<int32_t>(gainmapImageSize)).get());
     *         }
     *
     *         // Include ICC profile metadata.
     *         SkJpegMetadataEncoder::AppendICC(metadataSegments, baseOptions, base.colorSpace());
     *
     *         // Include the ISO 21946-1 version metadata.
     *         metadataSegments.emplace_back(
     *                 kISOGainmapMarker,
     *                 get_iso_gainmap_segment_params(SkGainmapInfo::SerializeVersion()));
     *
     *         // Encode the base image.
     *         baseData = encode_to_data(base, baseOptions, metadataSegments);
     *         if (!baseData) {
     *             SkCodecPrintf("Failed to encode base image.\n");
     *             return false;
     *         }
     *     }
     *
     *     // Combine them into an MPF.
     *     const SkData* images[] = {
     *             baseData.get(),
     *             gainmapData.get(),
     *     };
     *     return MakeMPF(dst, images, 2);
     * }
     * ```
     */
    public fun encodeHDRGM(
      dst: SkWStream?,
      base: SkPixmap,
      baseOptions: Options,
      gainmap: SkPixmap,
      gainmapOptions: Options,
      gainmapInfo: SkGainmapInfo,
    ): Boolean {
      TODO("Implement encodeHDRGM")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkJpegGainmapEncoder::MakeMPF(SkWStream* dst, const SkData** images, size_t imageCount) {
     *     if (imageCount < 1) {
     *         return true;
     *     }
     *
     *     // The offset into each image at which the MP segment will be written.
     *     std::vector<size_t> mpSegmentOffsets(imageCount);
     *
     *     // Populate the MP parameters (image sizes and offsets).
     *     SkJpegMultiPictureParameters mpParams(imageCount);
     *     size_t cumulativeSize = 0;
     *     for (size_t i = 0; i < imageCount; ++i) {
     *         // Compute the offset into the each image where we will write the MP parameters.
     *         mpSegmentOffsets[i] = mp_segment_offset(images[i]);
     *         if (!mpSegmentOffsets[i]) {
     *             return false;
     *         }
     *
     *         // Add the size of the MPF segment to image size. Note that the contents of
     *         // get_mpf_segment() are incorrect (because we don't have the right offset values), but
     *         // the size is correct.
     *         const size_t imageSize = images[i]->size() + get_mpf_segment(mpParams, i)->size();
     *         mpParams.images[i].dataOffset = SkJpegMultiPictureParameters::GetImageDataOffset(
     *                 cumulativeSize, mpSegmentOffsets[0]);
     *         mpParams.images[i].size = static_cast<uint32_t>(imageSize);
     *         cumulativeSize += imageSize;
     *     }
     *
     *     // Write the images.
     *     for (size_t i = 0; i < imageCount; ++i) {
     *         // Write up to the MP segment.
     *         if (!dst->write(images[i]->bytes(), mpSegmentOffsets[i])) {
     *             SkCodecPrintf("Failed to write image header.\n");
     *             return false;
     *         }
     *
     *         // Write the MP segment.
     *         auto mpfSegment = get_mpf_segment(mpParams, i);
     *         if (!dst->write(mpfSegment->data(), mpfSegment->size())) {
     *             SkCodecPrintf("Failed to write MPF segment.\n");
     *             return false;
     *         }
     *
     *         // Write the rest of the image.
     *         if (!dst->write(images[i]->bytes() + mpSegmentOffsets[i],
     *                         images[i]->size() - mpSegmentOffsets[i])) {
     *             SkCodecPrintf("Failed to write image body.\n");
     *             return false;
     *         }
     *     }
     *
     *     return true;
     * }
     * ```
     */
    public fun makeMPF(
      dst: SkWStream?,
      images: Int?,
      imageCount: ULong,
    ): Boolean {
      TODO("Implement makeMPF")
    }
  }
}
