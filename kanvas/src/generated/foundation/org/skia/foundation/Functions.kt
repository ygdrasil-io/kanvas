package org.skia.foundation

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SkData
import org.skia.core.SkImage
import org.skia.core.SkMipmap
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * SkPMColor SkPreMultiplyARGB(U8CPU a, U8CPU r, U8CPU g, U8CPU b) {
 *     return SkPremultiplyARGBInline(a, r, g, b);
 * }
 * ```
 */
public fun skPreMultiplyARGB(
  a: U8CPU,
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
): SkPMColor {
  TODO("Implement skPreMultiplyARGB")
}

/**
 * C++ original:
 * ```cpp
 * SkPMColor SkPreMultiplyColor(SkColor c) {
 *     return SkPremultiplyARGBInline(SkColorGetA(c), SkColorGetR(c),
 *                                    SkColorGetG(c), SkColorGetB(c));
 * }
 * ```
 */
public fun skPreMultiplyColor(c: SkColor): SkPMColor {
  TODO("Implement skPreMultiplyColor")
}

/**
 * C++ original:
 * ```cpp
 * SkPMColor SkPMColorSetARGB(uint8_t a, uint8_t r, uint8_t g, uint8_t b) {
 *     return SkPackARGB32(a, r, g, b);
 * }
 * ```
 */
public fun skPMColorSetARGB(
  a: UByte,
  r: UByte,
  g: UByte,
  b: UByte,
): SkPMColor {
  TODO("Implement skPMColorSetARGB")
}

/**
 * C++ original:
 * ```cpp
 * SkAlpha SkPMColorGetA(SkPMColor c) {
 *     return SkGetPackedA32(c);
 * }
 * ```
 */
public fun skPMColorGetA(c: SkPMColor): SkAlpha {
  TODO("Implement skPMColorGetA")
}

/**
 * C++ original:
 * ```cpp
 * uint8_t SkPMColorGetR(SkPMColor c) {
 *     return SkGetPackedR32(c);
 * }
 * ```
 */
public fun skPMColorGetR(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetR")
}

/**
 * C++ original:
 * ```cpp
 * uint8_t SkPMColorGetG(SkPMColor c) {
 *     return SkGetPackedG32(c);
 * }
 * ```
 */
public fun skPMColorGetG(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetG")
}

/**
 * C++ original:
 * ```cpp
 * uint8_t SkPMColorGetB(SkPMColor c) {
 *     return SkGetPackedB32(c);
 * }
 * ```
 */
public fun skPMColorGetB(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetB")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar ByteToScalar(U8CPU x) {
 *     SkASSERT(x <= 255);
 *     return SkIntToScalar(x) / 255;
 * }
 * ```
 */
public fun byteToScalar(x: U8CPU): SkScalar {
  TODO("Implement byteToScalar")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkScalar ByteDivToScalar(int numer, U8CPU denom) {
 *     // cast to keep the answer signed
 *     return SkIntToScalar(numer) / (int)denom;
 * }
 * ```
 */
public fun byteDivToScalar(numer: Int, denom: U8CPU): SkScalar {
  TODO("Implement byteDivToScalar")
}

/**
 * C++ original:
 * ```cpp
 * void SkRGBToHSV(U8CPU r, U8CPU g, U8CPU b, SkScalar hsv[3]) {
 *     SkASSERT(hsv);
 *
 *     unsigned min = std::min(r, std::min(g, b));
 *     unsigned max = std::max(r, std::max(g, b));
 *     unsigned delta = max - min;
 *
 *     SkScalar v = ByteToScalar(max);
 *     SkASSERT(v >= 0 && v <= SK_Scalar1);
 *
 *     if (0 == delta) { // we're a shade of gray
 *         hsv[0] = 0;
 *         hsv[1] = 0;
 *         hsv[2] = v;
 *         return;
 *     }
 *
 *     SkScalar s = ByteDivToScalar(delta, max);
 *     SkASSERT(s >= 0 && s <= SK_Scalar1);
 *
 *     SkScalar h;
 *     if (r == max) {
 *         h = ByteDivToScalar(g - b, delta);
 *     } else if (g == max) {
 *         h = SkIntToScalar(2) + ByteDivToScalar(b - r, delta);
 *     } else { // b == max
 *         h = SkIntToScalar(4) + ByteDivToScalar(r - g, delta);
 *     }
 *
 *     h *= 60;
 *     if (h < 0) {
 *         h += SkIntToScalar(360);
 *     }
 *     SkASSERT(h >= 0 && h < SkIntToScalar(360));
 *
 *     hsv[0] = h;
 *     hsv[1] = s;
 *     hsv[2] = v;
 * }
 * ```
 */
public fun skRGBToHSV(
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
  hsv: Array<SkScalar>,
) {
  TODO("Implement skRGBToHSV")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkColor SkColor4f::toSkColor() const {
 *     return Sk4f_toL32(swizzle_rb(skvx::float4::Load(this->vec())));
 * }
 * ```
 */
public fun toSkColor() {
  TODO("Implement toSkColor")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * uint32_t SkColor4f::toBytes_RGBA() const {
 *     return Sk4f_toL32(skvx::float4::Load(this->vec()));
 * }
 * ```
 */
public fun toBytesRGBA() {
  TODO("Implement toBytesRGBA")
}

/**
 * C++ original:
 * ```cpp
 * template <>
 * SkColor4f SkColor4f::FromBytes_RGBA(uint32_t c) {
 *     SkColor4f color;
 *     Sk4f_fromL32(c).store(&color);
 *     return color;
 * }
 * ```
 */
public fun fromBytesRGBA(c: UInt) {
  TODO("Implement fromBytesRGBA")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> MakeEmptyImage(int width, int height) {
 *         return SkImages::DeferredFromGenerator(
 *                 std::make_unique<EmptyImageGenerator>(SkImageInfo::MakeN32Premul(width, height)));
 *     }
 * ```
 */
public fun makeEmptyImage(width: Int, height: Int): SkSp<SkImage> {
  TODO("Implement makeEmptyImage")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> deserialize_image(sk_sp<SkData> data, SkDeserialProcs dProcs,
 *                                         std::optional<SkAlphaType> alphaType) {
 *     sk_sp<SkImage> image;
 *     if (dProcs.fImageDataProc) {
 *         image = dProcs.fImageDataProc(data, alphaType, dProcs.fImageCtx);
 *     } else if (dProcs.fImageProc) {
 * #if !defined(SK_LEGACY_DESERIAL_IMAGE_PROC)
 *         image = dProcs.fImageProc(data->data(), data->size(), dProcs.fImageCtx);
 * #else
 *         image = dProcs.fImageProc(data->data(), data->size(), alphaType, dProcs.fImageCtx);
 * #endif
 *     }
 *     return image;
 * }
 * ```
 */
public fun deserializeImage(
  `data`: SkSp<SkData>,
  dProcs: SkDeserialProcs,
  alphaType: SkAlphaType?,
): SkSp<SkImage> {
  TODO("Implement deserializeImage")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImage> add_mipmaps(sk_sp<SkImage> img, sk_sp<SkData> data,
 *                                   SkDeserialProcs dProcs, std::optional<SkAlphaType> alphaType) {
 *     SkMipmapBuilder builder(img->imageInfo());
 *
 *     SkReadBuffer buffer(data->data(), data->size());
 *     int count = buffer.read32();
 *     if (builder.countLevels() != count) {
 *         return img;
 *     }
 *     for (int i = 0; i < count; ++i) {
 *         size_t size = buffer.read32();
 *         const void* ptr = buffer.skip(size);
 *         if (!ptr) {
 *             return img;
 *         }
 *         // This use of SkData::MakeWithoutCopy is safe because the image goes
 *         // out of scope after we read the pixels from it, so we are sure the
 *         // data (from buffer) outlives the image.
 *         sk_sp<SkImage> mip = deserialize_image(SkData::MakeWithoutCopy(ptr, size), dProcs,
 *                                                alphaType);
 *         if (!mip) {
 *             return img;
 *         }
 *
 *         SkPixmap pm = builder.level(i);
 *         if (mip->dimensions() != pm.dimensions()) {
 *             return img;
 *         }
 *         if (!mip->readPixels(nullptr, pm, 0, 0)) {
 *             return img;
 *         }
 *     }
 *     if (!buffer.isValid()) {
 *         return img;
 *     }
 *     sk_sp<SkImage> raster = img->makeRasterImage(nullptr);
 *     if (!raster) {
 *         return img;
 *     }
 *     sk_sp<SkImage> rasterWithMips = builder.attachTo(raster);
 *     SkASSERT(rasterWithMips); // attachTo should never return null
 *     return rasterWithMips;
 * }
 * ```
 */
public fun addMipmaps(
  img: SkSp<SkImage>,
  `data`: SkSp<SkData>,
  dProcs: SkDeserialProcs,
  alphaType: SkAlphaType?,
): SkSp<SkImage> {
  TODO("Implement addMipmaps")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<const SkData> serialize_image(const SkImage* image, SkSerialProcs procs) {
 *     sk_sp<const SkData> data;
 *     if (procs.fImageProc) {
 *         data = procs.fImageProc(const_cast<SkImage*>(image), procs.fImageCtx);
 *     }
 *     if (data) {
 *         return data;
 *     }
 *     // Check to see if the image's source was an encoded block of data.
 *     // If so, just use that.
 *     data = image->refEncodedData();
 *     if (data) {
 *         return data;
 *     }
 *     return nullptr;
 * }
 * ```
 */
public fun serializeImage(image: SkImage?, procs: SkSerialProcs): SkSp<SkData> {
  TODO("Implement serializeImage")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkData> serialize_mipmap(const SkMipmap* mipmap, SkSerialProcs procs) {
 *     /*  Format
 *         count_levels:32
 *         for each level, starting with the biggest (index 0 in our iterator)
 *             encoded_size:32
 *             encoded_data (padded)
 *     */
 *     const int count = mipmap->countLevels();
 *
 *     // This buffer does not need procs because it is just writing SkDatas
 *     SkBinaryWriteBuffer buffer({});
 *     buffer.write32(count);
 *     for (int i = 0; i < count; ++i) {
 *         SkMipmap::Level level;
 *         if (mipmap->getLevel(i, &level)) {
 *             sk_sp<SkImage> levelImage = SkImages::RasterFromPixmap(level.fPixmap, nullptr, nullptr);
 *             sk_sp<const SkData> levelData = serialize_image(levelImage.get(), procs);
 *             buffer.writeDataAsByteArray(levelData.get());
 *         } else {
 *             return nullptr;
 *         }
 *     }
 *     return buffer.snapshotAsData();
 * }
 * ```
 */
public fun serializeMipmap(mipmap: SkMipmap?, procs: SkSerialProcs): SkSp<SkData> {
  TODO("Implement serializeMipmap")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool SkAlphaTypeIsOpaque(SkAlphaType at) {
 *     return kOpaque_SkAlphaType == at;
 * }
 * ```
 */
public fun skAlphaTypeIsOpaque(at: SkAlphaType): Boolean {
  TODO("Implement skAlphaTypeIsOpaque")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr inline SkColor SkColorSetARGB(U8CPU a, U8CPU r, U8CPU g, U8CPU b) {
 *     return SkASSERT(a <= 255 && r <= 255 && g <= 255 && b <= 255),
 *            (a << 24) | (r << 16) | (g << 8) | (b << 0);
 * }
 * ```
 */
public fun skColorSetARGB(
  a: U8CPU,
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
): SkColor {
  TODO("Implement skColorSetARGB")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr inline SkColor SkColorSetA(SkColor c, U8CPU a) {
 *     return (c & 0x00FFFFFF) | (a << 24);
 * }
 * ```
 */
public fun skColorSetA(c: SkColor, a: U8CPU): SkColor {
  TODO("Implement skColorSetA")
}

/**
 * C++ original:
 * ```cpp
 * static inline void SkColorToHSV(SkColor color, SkScalar hsv[3]) {
 *     SkRGBToHSV(SkColorGetR(color), SkColorGetG(color), SkColorGetB(color), hsv);
 * }
 * ```
 */
public fun skColorToHSV(color: SkColor, hsv: Array<SkScalar>) {
  TODO("Implement skColorToHSV")
}

/**
 * C++ original:
 * ```cpp
 * static inline SkColor SkHSVToColor(const SkScalar hsv[3]) {
 *     return SkHSVToColor(0xFF, hsv);
 * }
 * ```
 */
public fun skHSVToColor(hsv: Array<SkScalar>): SkColor {
  TODO("Implement skHSVToColor")
}

/**
 * C++ original:
 * ```cpp
 * inline void sk_print_index_out_of_bounds(size_t i, size_t size) {
 *     SK_ABORT("Index (%zu) out of bounds for size %zu.\n", i, size);
 * }
 * ```
 */
public fun skPrintIndexOutOfBounds(i: ULong, size: ULong) {
  TODO("Implement skPrintIndexOutOfBounds")
}

/**
 * C++ original:
 * ```cpp
 * inline T sk_collection_check_bounds(T i, T size) {
 *     if (0 <= i && i < size) SK_LIKELY {
 *         return i;
 *     }
 *
 *     SK_UNLIKELY {
 *         #if defined(SK_DEBUG)
 *             sk_print_index_out_of_bounds(static_cast<size_t>(i), static_cast<size_t>(size));
 *         #else
 *             SkUNREACHABLE;
 *         #endif
 *     }
 * }
 * ```
 */
public fun <T> skCollectionCheckBounds(i: T, size: T): T {
  TODO("Implement skCollectionCheckBounds")
}

/**
 * C++ original:
 * ```cpp
 * inline void sk_print_length_too_big(size_t i, size_t size) {
 *     SK_ABORT("Length (%zu) is too big for size %zu.\n", i, size);
 * }
 * ```
 */
public fun skPrintLengthTooBig(i: ULong, size: ULong) {
  TODO("Implement skPrintLengthTooBig")
}

/**
 * C++ original:
 * ```cpp
 * inline T sk_collection_check_length(T i, T size) {
 *     if (0 <= i && i <= size) SK_LIKELY {
 *         return i;
 *     }
 *
 *     SK_UNLIKELY {
 *         #if defined(SK_DEBUG)
 *             sk_print_length_too_big(static_cast<size_t>(i), static_cast<size_t>(size));
 *         #else
 *             SkUNREACHABLE;
 *         #endif
 *     }
 * }
 * ```
 */
public fun <T> skCollectionCheckLength(i: T, size: T): T {
  TODO("Implement skCollectionCheckLength")
}

/**
 * C++ original:
 * ```cpp
 * inline void sk_collection_not_empty(bool empty) {
 *     if (empty) SK_UNLIKELY {
 *         #if defined(SK_DEBUG)
 *             SK_ABORT("Collection is empty.\n");
 *         #else
 *             SkUNREACHABLE;
 *         #endif
 *     }
 * }
 * ```
 */
public fun skCollectionNotEmpty(empty: Boolean) {
  TODO("Implement skCollectionNotEmpty")
}

/**
 * C++ original:
 * ```cpp
 * inline void sk_print_size_too_big(size_t size, size_t maxSize) {
 *     SK_ABORT("Size (%zu) can't be represented in bytes. Max size is %zu.\n", size, maxSize);
 * }
 * ```
 */
public fun skPrintSizeTooBig(size: ULong, maxSize: ULong) {
  TODO("Implement skPrintSizeTooBig")
}

/**
 * C++ original:
 * ```cpp
 * static inline T* SkRef(T* obj) {
 *     SkASSERT(obj);
 *     obj->ref();
 *     return obj;
 * }
 * ```
 */
public fun <T> skRef(obj: T?): T {
  TODO("Implement skRef")
}

/**
 * C++ original:
 * ```cpp
 * static inline T* SkSafeRef(T* obj) {
 *     if (obj) {
 *         obj->ref();
 *     }
 *     return obj;
 * }
 * ```
 */
public fun <T> skSafeRef(obj: T?): T {
  TODO("Implement skSafeRef")
}

/**
 * C++ original:
 * ```cpp
 * static inline void SkSafeUnref(T* obj) {
 *     if (obj) {
 *         obj->unref();
 *     }
 * }
 * ```
 */
public fun <T> skSafeUnref(obj: T?) {
  TODO("Implement skSafeUnref")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename... Args>
 * sk_sp<T> sk_make_sp(Args&&... args) {
 *     return sk_sp<T>(new T(std::forward<Args>(args)...));
 * }
 * ```
 */
public fun <T, Args> skMakeSp(args: Args): SkSp<T> {
  TODO("Implement skMakeSp")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<T> sk_ref_sp(const T* obj) {
 *     return sk_sp<T>(const_cast<T*>(SkSafeRef(obj)));
 * }
 * ```
 */
public fun <T> skRefSp(obj: T): SkSp<T> {
  TODO("Implement skRefSp")
}
