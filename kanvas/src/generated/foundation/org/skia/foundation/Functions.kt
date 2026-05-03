package org.skia.foundation

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import org.skia.math.SkScalar
import undefined.SkMutex

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
 * static bool read_string(SkStream* stream, SkString* string) {
 *     size_t length;
 *     if (!stream->readPackedUInt(&length)) { return false; }
 *     if (length > 0) {
 *         if (SkStreamPriv::RemainingLengthIsBelow(stream, length)) {
 *             return false;
 *         }
 *         string->resize(length);
 *         if (stream->read(string->data(), length) != length) { return false; }
 *     }
 *     return true;
 * }
 * ```
 */
public fun readString(stream: SkStream?, string: String?): Boolean {
  TODO("Implement readString")
}

/**
 * C++ original:
 * ```cpp
 * static bool write_string(SkWStream* stream, const SkString& string, uint32_t id) {
 *     if (string.isEmpty()) { return true; }
 *     return stream->writePackedUInt(id) &&
 *            stream->writePackedUInt(string.size()) &&
 *            stream->write(string.c_str(), string.size());
 * }
 * ```
 */
public fun writeString(
  stream: SkWStream?,
  string: String,
  id: UInt,
): Boolean {
  TODO("Implement writeString")
}

/**
 * C++ original:
 * ```cpp
 * static bool write_uint(SkWStream* stream, size_t n, uint32_t id) {
 *     return stream->writePackedUInt(id) &&
 *            stream->writePackedUInt(n);
 * }
 * ```
 */
public fun writeUint(
  stream: SkWStream?,
  n: ULong,
  id: UInt,
): Boolean {
  TODO("Implement writeUint")
}

/**
 * C++ original:
 * ```cpp
 * static bool write_scalar(SkWStream* stream, SkScalar n, uint32_t id) {
 *     return stream->writePackedUInt(id) &&
 *            stream->writeScalar(n);
 * }
 * ```
 */
public fun writeScalar(
  stream: SkWStream?,
  n: SkScalar,
  id: UInt,
): Boolean {
  TODO("Implement writeScalar")
}

/**
 * C++ original:
 * ```cpp
 * static size_t read_id(SkStream* stream) {
 *     size_t i;
 *     if (!stream->readPackedUInt(&i)) { return kInvalid; }
 *     return i;
 * }
 * ```
 */
public fun readId(stream: SkStream?): ULong {
  TODO("Implement readId")
}

/**
 * C++ original:
 * ```cpp
 * static int32_t safeMul32(int32_t a, int32_t b) {
 *     int64_t size = sk_64_mul(a, b);
 *     if (size > 0 && SkTFitsIn<int32_t>(size)) {
 *         return size;
 *     }
 *     return 0;
 * }
 * ```
 */
public fun safeMul32(a: Int, b: Int): Int {
  TODO("Implement safeMul32")
}

/**
 * C++ original:
 * ```cpp
 * static int maskFormatToShift(SkMask::Format format) {
 *     SkASSERT((unsigned)format < std::size(gMaskFormatToShift));
 *     SkASSERT(SkMask::kBW_Format != format);
 *     return gMaskFormatToShift[format];
 * }
 * ```
 */
public fun maskFormatToShift(format: SkMask.Format): Int {
  TODO("Implement maskFormatToShift")
}

/**
 * C++ original:
 * ```cpp
 * static bool affects_alpha(const SkImageFilter* imf) {
 *     // TODO: check if we should allow imagefilters to broadcast that they don't affect alpha
 *     // ala colorfilters
 *     return imf != nullptr;
 * }
 * ```
 */
public fun affectsAlpha(imf: SkImageFilter?): Boolean {
  TODO("Implement affectsAlpha")
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
 * static SkMutex& mask_gamma_cache_mutex() {
 *     static SkMutex& mutex = *(new SkMutex);
 *     return mutex;
 * }
 * ```
 */
public fun maskGammaCacheMutex(): SkMutex {
  TODO("Implement maskGammaCacheMutex")
}

/**
 * C++ original:
 * ```cpp
 * static const SkMaskGamma& linear_gamma() {
 *     static const SkMaskGamma kLinear;
 *     return kLinear;
 * }
 * ```
 */
public fun linearGamma(): SkMaskGamma {
  TODO("Implement linearGamma")
}

/**
 * C++ original:
 * ```cpp
 * static constexpr D sk_saturate_cast(S s) {
 *     static_assert(std::is_integral_v<D>);
 *     s = s < std::numeric_limits<D>::max() ? s : std::numeric_limits<D>::max();
 *     s = s > std::numeric_limits<D>::min() ? s : std::numeric_limits<D>::min();
 *     return (D)s;
 * }
 * ```
 */
public fun <D, S> skSaturateCast(s: S): D {
  TODO("Implement skSaturateCast")
}

/**
 * C++ original:
 * ```cpp
 * static void applyLUTToA8Mask(SkMaskBuilder& mask, const uint8_t* lut) {
 *     uint8_t* SK_RESTRICT dst = mask.image();
 *     unsigned rowBytes = mask.fRowBytes;
 *
 *     for (int y = mask.fBounds.height() - 1; y >= 0; --y) {
 *         for (int x = mask.fBounds.width() - 1; x >= 0; --x) {
 *             dst[x] = lut[dst[x]];
 *         }
 *         dst += rowBytes;
 *     }
 * }
 * ```
 */
public fun applyLUTToA8Mask(mask: SkMaskBuilder, lut: UByte?) {
  TODO("Implement applyLUTToA8Mask")
}

/**
 * C++ original:
 * ```cpp
 * static inline int convert_8_to_1(unsigned byte) {
 *     SkASSERT(byte <= 0xFF);
 *     return byte >> 7;
 * }
 * ```
 */
public fun convert8To1(byte: UInt): Int {
  TODO("Implement convert8To1")
}

/**
 * C++ original:
 * ```cpp
 * static uint8_t pack_8_to_1(const uint8_t alpha[8]) {
 *     unsigned bits = 0;
 *     for (int i = 0; i < 8; ++i) {
 *         bits <<= 1;
 *         bits |= convert_8_to_1(alpha[i]);
 *     }
 *     return SkToU8(bits);
 * }
 * ```
 */
public fun pack8To1(alpha: Array<UByte>): UByte {
  TODO("Implement pack8To1")
}

/**
 * C++ original:
 * ```cpp
 * static void packA8ToA1(SkMaskBuilder& dstMask, const uint8_t* src, size_t srcRB) {
 *     const int height = dstMask.fBounds.height();
 *     const int width = dstMask.fBounds.width();
 *     const int octs = width >> 3;
 *     const int leftOverBits = width & 7;
 *
 *     uint8_t* dst = dstMask.image();
 *     const int dstPad = dstMask.fRowBytes - SkAlign8(width)/8;
 *     SkASSERT(dstPad >= 0);
 *
 *     SkASSERT(width >= 0);
 *     SkASSERT(srcRB >= (size_t)width);
 *     const size_t srcPad = srcRB - width;
 *
 *     for (int y = 0; y < height; ++y) {
 *         for (int i = 0; i < octs; ++i) {
 *             *dst++ = pack_8_to_1(src);
 *             src += 8;
 *         }
 *         if (leftOverBits > 0) {
 *             unsigned bits = 0;
 *             int shift = 7;
 *             for (int i = 0; i < leftOverBits; ++i, --shift) {
 *                 bits |= convert_8_to_1(*src++) << shift;
 *             }
 *             *dst++ = bits;
 *         }
 *         src += srcPad;
 *         dst += dstPad;
 *     }
 * }
 * ```
 */
public fun packA8ToA1(
  dstMask: SkMaskBuilder,
  src: UByte?,
  srcRB: ULong,
) {
  TODO("Implement packA8ToA1")
}

/**
 * C++ original:
 * ```cpp
 * static SkScalar sk_relax(SkScalar x) {
 *     SkScalar n = SkScalarRoundToScalar(x * 1024);
 *     return n / 1024.0f;
 * }
 * ```
 */
public fun skRelax(x: SkScalar): SkScalar {
  TODO("Implement skRelax")
}

/**
 * C++ original:
 * ```cpp
 * static SkMask::Format compute_mask_format(const SkFont& font) {
 *     switch (font.getEdging()) {
 *         case SkFont::Edging::kAlias:
 *             return SkMask::kBW_Format;
 *         case SkFont::Edging::kAntiAlias:
 *             return SkMask::kA8_Format;
 *         case SkFont::Edging::kSubpixelAntiAlias:
 *             return SkMask::kLCD16_Format;
 *     }
 *     SkASSERT(false);
 *     return SkMask::kA8_Format;
 * }
 * ```
 */
public fun computeMaskFormat(font: SkFont): SkMask.Format {
  TODO("Implement computeMaskFormat")
}

/**
 * C++ original:
 * ```cpp
 * static bool too_big_for_lcd(const SkScalerContextRec& rec, bool checkPost2x2) {
 *     if (checkPost2x2) {
 *         SkScalar area = rec.fPost2x2[0][0] * rec.fPost2x2[1][1] -
 *                         rec.fPost2x2[1][0] * rec.fPost2x2[0][1];
 *         area *= rec.fTextSize * rec.fTextSize;
 *         return area > gMaxSize2ForLCDText;
 *     } else {
 *         return rec.fTextSize > SK_MAX_SIZE_FOR_LCDTEXT;
 *     }
 * }
 * ```
 */
public fun tooBigForLcd(rec: SkScalerContextRec, checkPost2x2: Boolean): Boolean {
  TODO("Implement tooBigForLcd")
}

/**
 * C++ original:
 * ```cpp
 * static void generate_descriptor(const SkScalerContextRec& rec,
 *                                 const SkBinaryWriteBuffer& effectBuffer,
 *                                 SkDescriptor* desc) {
 *     desc->addEntry(kRec_SkDescriptorTag, sizeof(rec), &rec);
 *
 *     if (effectBuffer.bytesWritten() > 0) {
 *         effectBuffer.writeToMemory(desc->addEntry(kEffects_SkDescriptorTag,
 *                                                   effectBuffer.bytesWritten(),
 *                                                   nullptr));
 *     }
 *
 *     desc->computeChecksum();
 * }
 * ```
 */
public fun generateDescriptor(
  rec: SkScalerContextRec,
  effectBuffer: SkBinaryWriteBuffer,
  desc: SkDescriptor?,
) {
  TODO("Implement generateDescriptor")
}

/**
 * C++ original:
 * ```cpp
 * std::vector<DecoderProc>* decoders() {
 *         static SkNoDestructor<std::vector<DecoderProc>> decoders{{
 *             { SkEmptyTypeface::FactoryId, SkEmptyTypeface::MakeFromStream },
 *             { SkCustomTypefaceBuilder::FactoryId, SkCustomTypefaceBuilder::MakeFromStream },
 * #ifdef SK_TYPEFACE_FACTORY_CORETEXT
 *             { SkTypeface_Mac::FactoryId, SkTypeface_Mac::MakeFromStream },
 * #endif
 * #ifdef SK_TYPEFACE_FACTORY_DIRECTWRITE
 *             { DWriteFontTypeface::FactoryId, DWriteFontTypeface::MakeFromStream },
 * #endif
 * #ifdef SK_TYPEFACE_FACTORY_FREETYPE
 *             { SkTypeface_FreeType::FactoryId, SkTypeface_FreeType::MakeFromStream },
 * #endif
 * #ifdef SK_TYPEFACE_FACTORY_FONTATIONS
 *             { SkTypeface_Fontations::FactoryId, SkTypeface_Fontations::MakeFromStream },
 * #endif
 *         }};
 *         return decoders.get();
 *     }
 * ```
 */
public fun decoders(): Int {
  TODO("Implement decoders")
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

/**
 * C++ original:
 * ```cpp
 * static inline bool IsLeadingSurrogateUTF16(uint16_t c) { return ((c) & 0xFC00) == 0xD800; }
 * ```
 */
public fun isLeadingSurrogateUTF16(c: UShort): Boolean {
  TODO("Implement isLeadingSurrogateUTF16")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool IsTrailingSurrogateUTF16(uint16_t c) { return ((c) & 0xFC00) == 0xDC00; }
 * ```
 */
public fun isTrailingSurrogateUTF16(c: UShort): Boolean {
  TODO("Implement isTrailingSurrogateUTF16")
}
