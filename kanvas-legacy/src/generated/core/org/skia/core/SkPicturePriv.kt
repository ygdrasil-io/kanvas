package org.skia.core

import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkPicturePriv {
 * public:
 *     /**
 *      *  Recreate a picture that was serialized into a buffer. If the creation requires bitmap
 *      *  decoding, the decoder must be set on the SkReadBuffer parameter by calling
 *      *  SkReadBuffer::setBitmapDecoder() before calling SkPicture::MakeFromBuffer().
 *      *  @param buffer Serialized picture data.
 *      *  @return A new SkPicture representing the serialized data, or NULL if the buffer is
 *      *          invalid.
 *      */
 *     static sk_sp<SkPicture> MakeFromBuffer(SkReadBuffer& buffer);
 *
 *     /**
 *      *  Serialize to a buffer.
 *      */
 *     static void Flatten(const sk_sp<const SkPicture> , SkWriteBuffer& buffer);
 *
 *     // Returns NULL if this is not an SkBigPicture.
 *     static const SkBigPicture* AsSkBigPicture(const sk_sp<const SkPicture>& picture) {
 *         return picture->asSkBigPicture();
 *     }
 *
 *     static uint64_t MakeSharedID(uint32_t pictureID) {
 *         uint64_t sharedID = SkSetFourByteTag('p', 'i', 'c', 't');
 *         return (sharedID << 32) | pictureID;
 *     }
 *
 *     static void AddedToCache(const SkPicture* pic) {
 *         pic->fAddedToCache.store(true);
 *     }
 *
 *     // V35: Store SkRect (rather then width & height) in header
 *     // V36: Remove (obsolete) alphatype from SkColorTable
 *     // V37: Added shadow only option to SkDropShadowImageFilter (last version to record CLEAR)
 *     // V38: Added PictureResolution option to SkPictureImageFilter
 *     // V39: Added FilterLevel option to SkPictureImageFilter
 *     // V40: Remove UniqueID serialization from SkImageFilter.
 *     // V41: Added serialization of SkBitmapSource's filterQuality parameter
 *     // V42: Added a bool to SkPictureShader serialization to indicate did-we-serialize-a-picture?
 *     // V43: Added DRAW_IMAGE and DRAW_IMAGE_RECT opt codes to serialized data
 *     // V44: Move annotations from paint to drawAnnotation
 *     // V45: Add invNormRotation to SkLightingShader.
 *     // V46: Add drawTextRSXform
 *     // V47: Add occluder rect to SkBlurMaskFilter
 *     // V48: Read and write extended SkTextBlobs.
 *     // V49: Gradients serialized as SkColor4f + SkColorSpace
 *     // V50: SkXfermode -> SkBlendMode
 *     // V51: more SkXfermode -> SkBlendMode
 *     // V52: Remove SkTextBlob::fRunCount
 *     // V53: SaveLayerRec clip mask
 *     // V54: ComposeShader can use a Mode or a Lerp
 *     // V55: Drop blendmode[] from MergeImageFilter
 *     // V56: Add TileMode in SkBlurImageFilter.
 *     // V57: Sweep tiling info.
 *     // V58: No more 2pt conical flipping.
 *     // V59: No more LocalSpace option on PictureImageFilter
 *     // V60: Remove flags in picture header
 *     // V61: Change SkDrawPictureRec to take two colors rather than two alphas
 *     // V62: Don't negate size of custom encoded images (don't write origin x,y either)
 *     // V63: Store image bounds (including origin) instead of just width/height to support subsets
 *     // V64: Remove occluder feature from blur maskFilter
 *     // V65: Float4 paint color
 *     // V66: Add saveBehind
 *     // V67: Blobs serialize fonts instead of paints
 *     // V68: Paint doesn't serialize font-related stuff
 *     // V69: Clean up duplicated and redundant SkImageFilter related enums
 *     // V70: Image filters definitions hidden, registered names updated to include "Impl"
 *     // V71: Unify erode and dilate image filters
 *     // V72: SkColorFilter_Matrix domain (rgba vs. hsla)
 *     // V73: Use SkColor4f in per-edge AA quad API
 *     // V74: MorphologyImageFilter internal radius is SkScaler
 *     // V75: SkVertices switched from unsafe use of SkReader32 to SkReadBuffer (like everything else)
 *     // V76: Add filtering enum to ImageShader
 *     // V77: Explicit filtering options on imageshaders
 *     // V78: Serialize skmipmap data for images that have it
 *     // V79: Cubic Resampler option on imageshader
 *     // V80: Smapling options on imageshader
 *     // V81: sampling parameters on drawImage/drawImageRect/etc.
 *     // V82: Add filter param to picture-shader
 *     // V83: SkMatrixImageFilter now takes SkSamplingOptions instead of SkFilterQuality
 *     // V84: SkImageFilters::Image now takes SkSamplingOptions instead of SkFilterQuality
 *     // V85: Remove legacy support for inheriting sampling from the paint.
 *     // V86: Remove support for custom data inside SkVertices
 *     // V87: SkPaint now holds a user-defined blend function (SkBlender), no longer has DrawLooper
 *     // V88: Add blender to ComposeShader and BlendImageFilter
 *     // V89: Deprecated SkClipOps are no longer supported
 *     // V90: Private API for backdrop scale factor in SaveLayerRec
 *     // V91: Added raw image shaders
 *     // V92: Added anisotropic filtering to SkSamplingOptions
 *     // V94: Removed local matrices from SkShaderBase. Local matrices always use SkLocalMatrixShader.
 *     // V95: SkImageFilters::Shader only saves SkShader, not a full SkPaint
 *     // V96: SkImageFilters::Magnifier updated with more complete parameters
 *     // V97: SkImageFilters::RuntimeShader takes a sample radius
 *     // V98: Merged SkImageFilters::Blend and ::Arithmetic implementations
 *     // V99: Remove legacy Magnifier filter
 *     // V100: SkImageFilters::DropShadow does not have a dedicated implementation
 *     // V101: Crop image filter supports all SkTileModes instead of just kDecal
 *     // V102: Convolution image filter uses ::Crop to apply tile mode
 *     // V103: Remove deprecated per-image filter crop rect
 *     // v104: SaveLayer supports multiple image filters
 *     // v105: Unclamped matrix color filter
 *     // v106: SaveLayer supports custom backdrop tile modes
 *     // v107: Combine SkColorShader and SkColorShader4
 *     // v108: Serialize stable keys of runtime effects
 *     // v109: Extend SkWorkingColorSpaceShader to have alpha type + output control
 *
 *     enum Version {
 *         kPictureShaderFilterParam_Version   = 82,
 *         kMatrixImageFilterSampling_Version  = 83,
 *         kImageFilterImageSampling_Version   = 84,
 *         kNoFilterQualityShaders_Version     = 85,
 *         kVerticesRemoveCustomData_Version   = 86,
 *         kSkBlenderInSkPaint                 = 87,
 *         kBlenderInEffects                   = 88,
 *         kNoExpandingClipOps                 = 89,
 *         kBackdropScaleFactor                = 90,
 *         kRawImageShaders                    = 91,
 *         kAnisotropicFilter                  = 92,
 *         kBlend4fColorFilter                 = 93,
 *         kNoShaderLocalMatrix                = 94,
 *         kShaderImageFilterSerializeShader   = 95,
 *         kRevampMagnifierFilter              = 96,
 *         kRuntimeImageFilterSampleRadius     = 97,
 *         kCombineBlendArithmeticFilters      = 98,
 *         kRemoveLegacyMagnifierFilter        = 99,
 *         kDropShadowImageFilterComposition   = 100,
 *         kCropImageFilterSupportsTiling      = 101,
 *         kConvolutionImageFilterTilingUpdate = 102,
 *         kRemoveDeprecatedCropRect           = 103,
 *         kMultipleFiltersOnSaveLayer         = 104,
 *         kUnclampedMatrixColorFilter         = 105,
 *         kSaveLayerBackdropTileMode          = 106,
 *         kCombineColorShaders                = 107,
 *         kSerializeStableKeys                = 108,
 *         kWorkingColorSpaceOutput            = 109,
 *
 *         // Only SKPs within the min/current picture version range (inclusive) can be read.
 *         //
 *         // When updating kMin_Version also update oldestSupportedSkpVersion in
 *         // infra/bots/gen_tasks_logic/gen_tasks_logic.go
 *         //
 *         // Steps on how to find which oldestSupportedSkpVersion to use:
 *         // 1) Find the git hash when the desired kMin_Version was the kCurrent_Version from the
 *         //    git logs: https://skia.googlesource.com/skia/+log/main/src/core/SkPicturePriv.h
 *         //    Eg: https://skia.googlesource.com/skia/+/bfd330d081952424a93d51715653e4d1314d4822%5E%21/#F1
 *         //
 *         // 2) Use that git hash to find the SKP asset version number at that time here:
 *         //    https://skia.googlesource.com/skia/+/bfd330d081952424a93d51715653e4d1314d4822/infra/bots/assets/skp/VERSION
 *         //
 *         // 3) [Optional] Increment the SKP asset version number from step 3 and verify that it has
 *         //    the expected version number by downloading the asset and running skpinfo on it.
 *         //
 *         // 4) Use the incremented SKP asset version number as the oldestSupportedSkpVersion in
 *         //    infra/bots/gen_tasks_logic/gen_tasks_logic.go
 *         //
 *         // 5) Run `make -C infra/bots train`
 *         //
 *         // Contact the Infra Gardener if the above steps do not work for you.
 *         kMin_Version     = kPictureShaderFilterParam_Version,
 *         kCurrent_Version = kWorkingColorSpaceOutput
 *     };
 * }
 * ```
 */
public open class SkPicturePriv {
  public enum class Version {
    kPictureShaderFilterParam_Version,
    kMatrixImageFilterSampling_Version,
    kImageFilterImageSampling_Version,
    kNoFilterQualityShaders_Version,
    kVerticesRemoveCustomData_Version,
    kSkBlenderInSkPaint,
    kBlenderInEffects,
    kNoExpandingClipOps,
    kBackdropScaleFactor,
    kRawImageShaders,
    kAnisotropicFilter,
    kBlend4fColorFilter,
    kNoShaderLocalMatrix,
    kShaderImageFilterSerializeShader,
    kRevampMagnifierFilter,
    kRuntimeImageFilterSampleRadius,
    kCombineBlendArithmeticFilters,
    kRemoveLegacyMagnifierFilter,
    kDropShadowImageFilterComposition,
    kCropImageFilterSupportsTiling,
    kConvolutionImageFilterTilingUpdate,
    kRemoveDeprecatedCropRect,
    kMultipleFiltersOnSaveLayer,
    kUnclampedMatrixColorFilter,
    kSaveLayerBackdropTileMode,
    kCombineColorShaders,
    kSerializeStableKeys,
    kWorkingColorSpaceOutput,
    kMin_Version,
    kCurrent_Version,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicturePriv::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkPictInfo info;
     *     if (!SkPicture::BufferIsSKP(&buffer, &info)) {
     *         return nullptr;
     *     }
     *     // size should be 0, 1, or negative
     *     int32_t ssize = buffer.read32();
     *     if (ssize < 0) {
     *         const SkDeserialProcs& procs = buffer.getDeserialProcs();
     *         if (!procs.fPictureProc) {
     *             return nullptr;
     *         }
     *         size_t size = sk_negate_to_size_t(ssize);
     *         return procs.fPictureProc(buffer.skip(size), size, procs.fPictureCtx);
     *     }
     *     if (ssize != 1) {
     *         // 1 is the magic 'size' that means SkPictureData follows
     *         return nullptr;
     *     }
     *    std::unique_ptr<SkPictureData> data(SkPictureData::CreateFromBuffer(buffer, info));
     *     return SkPicture::Forwardport(info, data.get(), &buffer);
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): SkSp<SkPicture> {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPicturePriv::Flatten(const sk_sp<const SkPicture> picture, SkWriteBuffer& buffer) {
     *     SkPictInfo info = picture->createHeader();
     *     std::unique_ptr<SkPictureData> data(picture->backport());
     *
     *     buffer.writeByteArray(&info.fMagic, sizeof(info.fMagic));
     *     buffer.writeUInt(info.getVersion());
     *     buffer.writeRect(info.fCullRect);
     *
     *     if (auto custom = custom_serialize(picture.get(), buffer.serialProcs())) {
     *         int32_t size = SkToS32(custom->size());
     *         buffer.write32(-size);    // negative for custom format
     *         buffer.writePad32(custom->data(), size);
     *         return;
     *     }
     *
     *     if (data) {
     *         buffer.write32(1); // special size meaning SkPictureData
     *         data->flatten(buffer);
     *     } else {
     *         buffer.write32(0); // signal no content
     *     }
     * }
     * ```
     */
    public fun flatten(picture: SkSp<SkPicture>, buffer: SkWriteBuffer) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * static const SkBigPicture* AsSkBigPicture(const sk_sp<const SkPicture>& picture) {
     *         return picture->asSkBigPicture();
     *     }
     * ```
     */
    public fun asSkBigPicture(picture: SkSp<SkPicture>): SkBigPicture {
      TODO("Implement asSkBigPicture")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint64_t MakeSharedID(uint32_t pictureID) {
     *         uint64_t sharedID = SkSetFourByteTag('p', 'i', 'c', 't');
     *         return (sharedID << 32) | pictureID;
     *     }
     * ```
     */
    public fun makeSharedID(pictureID: UInt): ULong {
      TODO("Implement makeSharedID")
    }

    /**
     * C++ original:
     * ```cpp
     * static void AddedToCache(const SkPicture* pic) {
     *         pic->fAddedToCache.store(true);
     *     }
     * ```
     */
    public fun addedToCache(pic: SkPicture?) {
      TODO("Implement addedToCache")
    }
  }
}
