package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkWStream

/**
 * C++ original:
 * ```cpp
 * class HashAndEncode {
 * public:
 *     explicit HashAndEncode(const SkBitmap&);
 *
 *     // Feed uncompressed pixel data into a hash function like MD5.
 *     void feedHash(SkWStream*) const;
 *
 *     // Encode pixels as a PNG in our standard format, with md5 and key/properties as metadata.
 *     bool encodePNG(SkWStream*,
 *                    const char* md5,
 *                    CommandLineFlags::StringArray key,
 *                    CommandLineFlags::StringArray properties) const;
 *
 * private:
 *     const SkISize               fSize;
 *     std::unique_ptr<uint64_t[]> fPixels;  // In our standard format mentioned above.
 * }
 * ```
 */
public data class HashAndEncode public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkISize               fSize
   * ```
   */
  private val fSize: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<uint64_t[]> fPixels
   * ```
   */
  private var fPixels: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void HashAndEncode::feedHash(SkWStream* st) const {
   *     st->write(&fSize, sizeof(fSize));
   *     if (const uint64_t* px = fPixels.get()) {
   *         st->write(px, sizeof(*px) * fSize.width() * fSize.height());
   *     }
   *
   *     // N.B. changing salt will change the hash of all images produced by DM,
   *     // and will cause tens of thousands of new images to be uploaded to Gold.
   *     int salt = 1;
   *     st->write(&salt, sizeof(salt));
   * }
   * ```
   */
  public fun feedHash(st: SkWStream?) {
    TODO("Implement feedHash")
  }

  /**
   * C++ original:
   * ```cpp
   * bool HashAndEncode::encodePNG(SkWStream* st,
   *                               const char* md5,
   *                               CommandLineFlags::StringArray key,
   *                               CommandLineFlags::StringArray properties) const {
   *     if (!fPixels) {
   *         return false;
   *     }
   *
   *     png_structp png = png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
   *     if (!png) {
   *         return false;
   *     }
   *
   *     png_infop info = png_create_info_struct(png);
   *     if (!info) {
   *         png_destroy_write_struct(&png, &info);
   *         return false;
   *     }
   *     auto write_to_stream = +[](png_structp png, png_bytep ptr, png_size_t len) {
   *         auto st = (SkWStream*)png_get_io_ptr(png);
   *         if (!st->write(ptr, len)) {
   *             png_error(png, "HashAndEncode::encodePNG() failed writing stream");
   *         }
   *     };
   *     png_set_write_fn(png, st, write_to_stream, nullptr);
   *
   *     SkString description;
   *     description.append("Key: ");
   *     for (int i = 0; i < key.size(); i++) {
   *         description.appendf("%s ", key[i]);
   *     }
   *     description.append("Properties: ");
   *     for (int i = 0; i < properties.size(); i++) {
   *         description.appendf("%s ", properties[i]);
   *     }
   *     description.appendf("MD5: %s", md5);
   *
   *     png_text text[2];
   *     text[0].key  = const_cast<png_charp>("Author");
   *     text[0].text = const_cast<png_charp>("DM unified Rec.2020");
   *     text[0].compression = PNG_TEXT_COMPRESSION_NONE;
   *     text[1].key  = const_cast<png_charp>("Description");
   *     text[1].text = const_cast<png_charp>(description.c_str());
   *     text[1].compression = PNG_TEXT_COMPRESSION_NONE;
   *     png_set_text(png, info, text, std::size(text));
   *
   *     png_set_IHDR(png, info, (png_uint_32)fSize.width()
   *                           , (png_uint_32)fSize.height()
   *                           , 16/*bits per channel*/
   *                           , PNG_COLOR_TYPE_RGB_ALPHA
   *                           , PNG_INTERLACE_NONE
   *                           , PNG_COMPRESSION_TYPE_DEFAULT
   *                           , PNG_FILTER_TYPE_DEFAULT);
   *
   *     // Fastest encoding and decoding, at slight file size cost is no filtering, compression 1.
   *     png_set_filter(png, PNG_FILTER_TYPE_BASE, PNG_FILTER_NONE);
   *     png_set_compression_level(png, 1);
   *
   *     static const sk_sp<SkData> profile =
   *         SkWriteICCProfile(SkNamedTransferFn::kRec2020, SkNamedGamut::kRec2020);
   *     png_set_iCCP(png, info,
   *                  "Rec.2020",
   *                  0/*compression type... no idea what options are available here*/,
   *                  (png_const_bytep)profile->data(),
   *                  (png_uint_32)    profile->size());
   *
   *     png_write_info(png, info);
   *     for (int y = 0; y < fSize.height(); y++) {
   *         png_write_row(png, (png_bytep)(fPixels.get() + y*fSize.width()));
   *     }
   *     png_write_end(png, info);
   *
   *     png_destroy_write_struct(&png, &info);
   *     return true;
   * }
   * ```
   */
  public fun encodePNG(
    st: SkWStream?,
    md5: String?,
    key: CommandLineFlags.StringArray,
    properties: CommandLineFlags.StringArray,
  ): Boolean {
    TODO("Implement encodePNG")
  }
}
