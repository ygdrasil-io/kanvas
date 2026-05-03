package org.skia.core

import kotlin.ULong
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkPictureBackedGlyphDrawable final : public SkDrawable {
 * public:
 *     static sk_sp<SkPictureBackedGlyphDrawable>MakeFromBuffer(SkReadBuffer& buffer);
 *     static void FlattenDrawable(SkWriteBuffer& buffer, SkDrawable* drawable);
 *     explicit SkPictureBackedGlyphDrawable(sk_sp<SkPicture> self);
 *
 * private:
 *     sk_sp<SkPicture> fPicture;
 *     SkRect onGetBounds() override;
 *     size_t onApproximateBytesUsed() override;
 *     void onDraw(SkCanvas* canvas) override;
 * }
 * ```
 */
public class SkPictureBackedGlyphDrawable public constructor(
  self: SkSp<SkPicture>,
) : SkDrawable() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkPictureBackedGlyphDrawable::onGetBounds() {
   *     return fPicture->cullRect();
   * }
   * ```
   */
  public override fun onGetBounds(): SkRect {
    TODO("Implement onGetBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkPictureBackedGlyphDrawable::onApproximateBytesUsed() {
   *     return sizeof(SkPictureBackedGlyphDrawable) + fPicture->approximateBytesUsed();
   * }
   * ```
   */
  public override fun onApproximateBytesUsed(): ULong {
    TODO("Implement onApproximateBytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureBackedGlyphDrawable::onDraw(SkCanvas* canvas) {
   *     canvas->drawPicture(fPicture);
   * }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPictureBackedGlyphDrawable>
     * SkPictureBackedGlyphDrawable::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkASSERT(buffer.isValid());
     *
     *     sk_sp<SkData> pictureData = buffer.readByteArrayAsData();
     *
     *     // Return nullptr if invalid or there an empty drawable, which is represented by nullptr.
     *     if (!buffer.isValid() || pictureData->empty()) {
     *         return nullptr;
     *     }
     *
     *     // Propagate the outer buffer's allow-SkSL setting to the picture decoder, using the flag on
     *     // the deserial procs.
     *     SkDeserialProcs procs;
     *     procs.fAllowSkSL = buffer.allowSkSL();
     *     sk_sp<SkPicture> picture = SkPicture::MakeFromData(pictureData.get(), &procs);
     *     if (!buffer.validate(picture != nullptr)) {
     *         return nullptr;
     *     }
     *
     *     return sk_make_sp<SkPictureBackedGlyphDrawable>(std::move(picture));
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): SkSp<SkPictureBackedGlyphDrawable> {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPictureBackedGlyphDrawable::FlattenDrawable(SkWriteBuffer& buffer, SkDrawable* drawable) {
     *     if (drawable == nullptr) {
     *         buffer.writeByteArray(nullptr, 0);
     *         return;
     *     }
     *
     *     sk_sp<SkPicture> picture = drawable->makePictureSnapshot();
     *     // These drawables should not have SkImages, SkTypefaces or SkPictures inside of them, so
     *     // the default SkSerialProcs are sufficient.
     *     sk_sp<SkData> data = picture->serialize();
     *
     *     // If the picture is too big, or there is no picture, then drop by sending an empty byte array.
     *     if (!SkTFitsIn<uint32_t>(data->size()) || data->empty()) {
     *         buffer.writeByteArray(nullptr, 0);
     *         return;
     *     }
     *
     *     buffer.writeByteArray(data->data(), data->size());
     * }
     * ```
     */
    public fun flattenDrawable(buffer: SkWriteBuffer, drawable: SkDrawable?) {
      TODO("Implement flattenDrawable")
    }
  }
}
