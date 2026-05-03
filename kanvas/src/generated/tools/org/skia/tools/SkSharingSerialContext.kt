package org.skia.tools

import kotlin.Int
import kotlin.Unit
import org.skia.core.SkPicture
import org.skia.foundation.SkImage
import org.skia.gpu.ganesh.GrDirectContext

/**
 * C++ original:
 * ```cpp
 * struct SkSharingSerialContext {
 *     // --- Data and and function for optional texture collection pass --- //
 *
 *     // A map from uniqueID of images referenced by commands to non-texture images
 *     // collected at the end of each frame.
 *     skia_private::THashMap<uint32_t, sk_sp<SkImage>> fNonTexMap;
 *     GrDirectContext* fDirectContext;
 *
 *     // Collects any non-texture images referenced by the picture and stores non-texture copies
 *     // in the fNonTexMap of the provided SkSharingContext
 *     static void collectNonTextureImagesFromPicture(
 *         const SkPicture* pic, SkSharingSerialContext* sharingCtx);
 *
 *     void setDirectContext(GrDirectContext* ctx);
 *
 *     // --- Data and serialization function for regular use --- //
 *
 *     // A map from the ids from SkImage::uniqueID() to ids used within the file
 *     // The keys are ids of original images, not of non-texture copies
 *     skia_private::THashMap<uint32_t, int> fImageMap;
 *
 *     // A serial proc that shares images between subpictures
 *     // To use this, create an instance of SkSerialProcs and populate it this way.
 *     // The client must retain ownership of the context.
 *     // auto ctx = std::make_unique<SkSharingSerialContext>()
 *     // SkSerialProcs procs;
 *     // procs.fImageProc = SkSharingSerialContext::serializeImage;
 *     // procs.fImageCtx = ctx.get();
 *     static SkSerialReturnType serializeImage(SkImage* img, void* ctx);
 * }
 * ```
 */
public data class SkSharingSerialContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * GrDirectContext* fDirectContext
   * ```
   */
  public var fDirectContext: Int?,
  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<uint32_t, int> fImageMap
   * ```
   */
  public var fImageMap: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkSharingSerialContext::setDirectContext(GrDirectContext* ctx) {
   *     fDirectContext = ctx;
   * }
   * ```
   */
  public fun setDirectContext(ctx: GrDirectContext?) {
    TODO("Implement setDirectContext")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void SkSharingSerialContext::collectNonTextureImagesFromPicture(
     *     const SkPicture* pic, SkSharingSerialContext* sharingCtx) {
     *     SkSerialProcs tempProc;
     *     tempProc.fImageCtx = sharingCtx;
     *     tempProc.fImageProc = collectNonTextureImagesProc;
     *     SkNullWStream ns;
     *     pic->serialize(&ns, &tempProc);
     * }
     * ```
     */
    public fun collectNonTextureImagesFromPicture(pic: SkPicture?, sharingCtx: SkSharingSerialContext?) {
      TODO("Implement collectNonTextureImagesFromPicture")
    }

    /**
     * C++ original:
     * ```cpp
     * SkSerialReturnType SkSharingSerialContext::serializeImage(SkImage* img, void* ctx) {
     *     SkSharingSerialContext* context = reinterpret_cast<SkSharingSerialContext*>(ctx);
     *     uint32_t id = img->uniqueID(); // get this process's id for the image. these are not hashes.
     *     // find out if we have already serialized this, and if so, what its in-file id is.
     *     int* fid = context->fImageMap.find(id);
     *     if (!fid) {
     *         // encode the image or it's non-texture replacement if one was collected
     *         sk_sp<SkImage>* replacementImage = context->fNonTexMap.find(id);
     *         if (replacementImage) {
     *             img = replacementImage->get();
     *         }
     *         auto data = SkPngEncoder::Encode(context->fDirectContext, img, {});
     *         if (!data) {
     *             // If encoding fails, we must return something. If we return null then SkWriteBuffer's
     *             // serialize_image which calls this proc will continue to try writing to the mskp file.
     *             SkBitmap bm;
     *             bm.allocPixels(SkImageInfo::Make(10, 10, kRGBA_8888_SkColorType, kPremul_SkAlphaType));
     *             SkCanvas canvas = SkCanvas(bm);
     *             canvas.clear(SK_ColorMAGENTA);
     *             data = SkPngEncoder::Encode(context->fDirectContext, bm.asImage().get(), {});
     *         }
     *         context->fImageMap[id] = context->fImageMap.count(); // Next in-file id
     *         return data;
     *     }
     *     // if present, return only the in-file id we registered the first time we serialized it.
     *     return SkData::MakeWithCopy(fid, sizeof(*fid));
     * }
     * ```
     */
    public fun serializeImage(img: SkImage?, ctx: Unit?): Int {
      TODO("Implement serializeImage")
    }
  }
}
