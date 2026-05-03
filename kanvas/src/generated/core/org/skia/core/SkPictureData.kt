package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.utils.Slug

/**
 * C++ original:
 * ```cpp
 * class SkPictureData {
 * public:
 *     SkPictureData(const SkPictureRecord& record, const SkPictInfo&);
 *     // Does not affect ownership of SkStream.
 *     static SkPictureData* CreateFromStream(SkStream*,
 *                                            const SkPictInfo&,
 *                                            const SkDeserialProcs&,
 *                                            SkTypefacePlayback*,
 *                                            int recursionLimit);
 *     static SkPictureData* CreateFromBuffer(SkReadBuffer&, const SkPictInfo&);
 *
 *     void serialize(SkWStream*, const SkSerialProcs&, SkRefCntSet*, bool textBlobsOnly=false) const;
 *     void flatten(SkWriteBuffer&) const;
 *
 *     const SkPictInfo& info() const { return fInfo; }
 *
 *     const sk_sp<SkData>& opData() const { return fOpData; }
 *
 * protected:
 *     explicit SkPictureData(const SkPictInfo& info);
 *
 *     // Does not affect ownership of SkStream.
 *     bool parseStream(SkStream*, const SkDeserialProcs&, SkTypefacePlayback*,
 *                      int recursionLimit);
 *     bool parseBuffer(SkReadBuffer& buffer);
 *
 * public:
 *     const SkImage* getImage(SkReadBuffer* reader) const {
 *         // images are written base-0, unlike paths, pictures, drawables, etc.
 *         const int index = reader->readInt();
 *         return reader->validateIndex(index, fImages.size()) ? fImages[index].get() : nullptr;
 *     }
 *
 *     const SkPath& getPath(SkReadBuffer* reader) const {
 *         int index = reader->readInt();
 *         return reader->validate(index > 0 && index <= fPaths.size()) ?
 *                 fPaths[index - 1] : fEmptyPath;
 *     }
 *
 *     const SkPicture* getPicture(SkReadBuffer* reader) const {
 *         return read_index_base_1_or_null(reader, fPictures);
 *     }
 *
 *     SkDrawable* getDrawable(SkReadBuffer* reader) const {
 *         return read_index_base_1_or_null(reader, fDrawables);
 *     }
 *
 *     // Return a paint if one was used for this op, or nullptr if none was used.
 *     const SkPaint* optionalPaint(SkReadBuffer* reader) const;
 *
 *     // Return the paint used for this op, invalidating the SkReadBuffer if there appears to be none.
 *     // The returned paint is always safe to use.
 *     const SkPaint& requiredPaint(SkReadBuffer* reader) const;
 *
 *     const SkTextBlob* getTextBlob(SkReadBuffer* reader) const {
 *         return read_index_base_1_or_null(reader, fTextBlobs);
 *     }
 *
 *     const sktext::gpu::Slug* getSlug(SkReadBuffer* reader) const {
 *         return read_index_base_1_or_null(reader, fSlugs);
 *     }
 *
 *     const SkVertices* getVertices(SkReadBuffer* reader) const {
 *         return read_index_base_1_or_null(reader, fVertices);
 *     }
 *
 * private:
 *     // these help us with reading/writing
 *     // Does not affect ownership of SkStream.
 *     bool parseStreamTag(SkStream*, uint32_t tag, uint32_t size,
 *                         const SkDeserialProcs&, SkTypefacePlayback*,
 *                         int recursionLimit);
 *     void parseBufferTag(SkReadBuffer&, uint32_t tag, uint32_t size);
 *     void flattenToBuffer(SkWriteBuffer&, bool textBlobsOnly) const;
 *
 *     skia_private::TArray<SkPaint> fPaints;
 *     skia_private::TArray<SkPath>  fPaths;
 *
 *     sk_sp<SkData>                 fOpData;    // opcodes and parameters
 *
 *     const SkPath                  fEmptyPath;
 *     const SkBitmap                fEmptyBitmap;
 *
 *     skia_private::TArray<sk_sp<const SkPicture>>   fPictures;
 *     skia_private::TArray<sk_sp<SkDrawable>>        fDrawables;
 *     skia_private::TArray<sk_sp<const SkTextBlob>>  fTextBlobs;
 *     skia_private::TArray<sk_sp<const SkVertices>>  fVertices;
 *     skia_private::TArray<sk_sp<const SkImage>>     fImages;
 *     skia_private::TArray<sk_sp<const sktext::gpu::Slug>> fSlugs;
 *
 *     SkTypefacePlayback                 fTFPlayback;
 *     std::unique_ptr<SkFactoryPlayback> fFactoryPlayback;
 *
 *     const SkPictInfo fInfo;
 *
 *     static void WriteFactories(SkWStream* stream, const SkFactorySet& rec);
 *     static void WriteTypefaces(SkWStream* stream, const SkRefCntSet& rec, const SkSerialProcs&);
 *
 *     void initForPlayback() const;
 * }
 * ```
 */
public data class SkPictureData public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkPaint> fPaints
   * ```
   */
  private var fPaints: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<SkPath>  fPaths
   * ```
   */
  private var fPaths: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData>                 fOpData
   * ```
   */
  private var fOpData: SkSp<SkData>,
  /**
   * C++ original:
   * ```cpp
   * const SkPath                  fEmptyPath
   * ```
   */
  private val fEmptyPath: SkPath,
  /**
   * C++ original:
   * ```cpp
   * const SkBitmap                fEmptyBitmap
   * ```
   */
  private val fEmptyBitmap: SkBitmap,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkPicture>>   fPictures
   * ```
   */
  private var fPictures: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<SkDrawable>>        fDrawables
   * ```
   */
  private var fDrawables: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkTextBlob>>  fTextBlobs
   * ```
   */
  private var fTextBlobs: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkVertices>>  fVertices
   * ```
   */
  private var fVertices: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const SkImage>>     fImages
   * ```
   */
  private var fImages: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<sk_sp<const sktext::gpu::Slug>> fSlugs
   * ```
   */
  private var fSlugs: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTypefacePlayback                 fTFPlayback
   * ```
   */
  private var fTFPlayback: SkTypefacePlayback,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkFactoryPlayback> fFactoryPlayback
   * ```
   */
  private var fFactoryPlayback: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkPictInfo fInfo
   * ```
   */
  private val fInfo: SkPictInfo,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkPictureData::serialize(SkWStream* stream, const SkSerialProcs& procs,
   *                               SkRefCntSet* topLevelTypeFaceSet, bool textBlobsOnly) const {
   *     // This can happen at pretty much any time, so might as well do it first.
   *     write_tag_size(stream, SK_PICT_READER_TAG, fOpData->size());
   *     stream->write(fOpData->bytes(), fOpData->size());
   *
   *     // We serialize all typefaces into the typeface section of the top-level picture.
   *     SkRefCntSet localTypefaceSet;
   *     SkRefCntSet* typefaceSet = topLevelTypeFaceSet ? topLevelTypeFaceSet : &localTypefaceSet;
   *
   *     // We delay serializing the bulk of our data until after we've serialized
   *     // factories and typefaces by first serializing to an in-memory write buffer.
   *     SkFactorySet factSet;  // buffer refs factSet, so factSet must come first.
   *     SkBinaryWriteBuffer buffer(skip_typeface_proc(procs));
   *     buffer.setFactoryRecorder(sk_ref_sp(&factSet));
   *     buffer.setTypefaceRecorder(sk_ref_sp(typefaceSet));
   *     this->flattenToBuffer(buffer, textBlobsOnly);
   *
   *     // Pretend to serialize our sub-pictures for the side effect of filling typefaceSet
   *     // with typefaces from sub-pictures.
   *     struct DevNull: public SkWStream {
   *         DevNull() : fBytesWritten(0) {}
   *         size_t fBytesWritten;
   *         bool write(const void*, size_t size) override { fBytesWritten += size; return true; }
   *         size_t bytesWritten() const override { return fBytesWritten; }
   *     } devnull;
   *     for (const auto& pic : fPictures) {
   *         pic->serialize(&devnull, nullptr, typefaceSet, /*textBlobsOnly=*/ true);
   *     }
   *     if (textBlobsOnly) { return; } // return early from fake serialize
   *
   *     // We need to write factories before we write the buffer.
   *     // We need to write typefaces before we write the buffer or any sub-picture.
   *     WriteFactories(stream, factSet);
   *     // Pass the original typefaceproc (if any) now that we're ready to actually serialize the
   *     // typefaces. We skipped this proc before, when we were serializing paints, so that the
   *     // paints would just write indices into our typeface set.
   *     WriteTypefaces(stream, *typefaceSet, procs);
   *
   *     // Write the buffer.
   *     write_tag_size(stream, SK_PICT_BUFFER_SIZE_TAG, buffer.bytesWritten());
   *     buffer.writeToStream(stream);
   *
   *     // Write sub-pictures by calling serialize again.
   *     if (!fPictures.empty()) {
   *         write_tag_size(stream, SK_PICT_PICTURE_TAG, fPictures.size());
   *         for (const auto& pic : fPictures) {
   *             pic->serialize(stream, &procs, typefaceSet, /*textBlobsOnly=*/ false);
   *         }
   *     }
   *
   *     stream->write32(SK_PICT_EOF_TAG);
   * }
   * ```
   */
  public fun serialize(
    stream: SkWStream?,
    procs: SkSerialProcs,
    topLevelTypeFaceSet: SkRefCntSet?,
    textBlobsOnly: Boolean = false,
  ) {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureData::flatten(SkWriteBuffer& buffer) const {
   *     write_tag_size(buffer, SK_PICT_READER_TAG, fOpData->size());
   *     buffer.writeByteArray(fOpData->bytes(), fOpData->size());
   *
   *     if (!fPictures.empty()) {
   *         write_tag_size(buffer, SK_PICT_PICTURE_TAG, fPictures.size());
   *         for (const auto& pic : fPictures) {
   *             SkPicturePriv::Flatten(pic, buffer);
   *         }
   *     }
   *
   *     if (!fDrawables.empty()) {
   *         write_tag_size(buffer, SK_PICT_DRAWABLE_TAG, fDrawables.size());
   *         for (const auto& draw : fDrawables) {
   *             buffer.writeFlattenable(draw.get());
   *         }
   *     }
   *
   *     // Write this picture playback's data into a writebuffer
   *     this->flattenToBuffer(buffer, false);
   *     buffer.write32(SK_PICT_EOF_TAG);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPictInfo& info() const { return fInfo; }
   * ```
   */
  public fun info(): SkPictInfo {
    TODO("Implement info")
  }

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkData>& opData() const { return fOpData; }
   * ```
   */
  public fun opData(): SkSp<SkData> {
    TODO("Implement opData")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPictureData::parseStream(SkStream* stream,
   *                                 const SkDeserialProcs& procs,
   *                                 SkTypefacePlayback* topLevelTFPlayback,
   *                                 int recursionLimit) {
   *     for (;;) {
   *         uint32_t tag;
   *         if (!stream->readU32(&tag)) { return false; }
   *         if (SK_PICT_EOF_TAG == tag) {
   *             break;
   *         }
   *
   *         uint32_t size;
   *         if (!stream->readU32(&size)) { return false; }
   *         if (!this->parseStreamTag(stream, tag, size, procs, topLevelTFPlayback, recursionLimit)) {
   *             return false; // we're invalid
   *         }
   *     }
   *     return true;
   * }
   * ```
   */
  protected fun parseStream(
    stream: SkStream?,
    procs: SkDeserialProcs,
    topLevelTFPlayback: SkTypefacePlayback?,
    recursionLimit: Int,
  ): Boolean {
    TODO("Implement parseStream")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPictureData::parseBuffer(SkReadBuffer& buffer) {
   *     while (buffer.isValid()) {
   *         uint32_t tag = buffer.readUInt();
   *         if (SK_PICT_EOF_TAG == tag) {
   *             break;
   *         }
   *         this->parseBufferTag(buffer, tag, buffer.readUInt());
   *     }
   *
   *     // Check that we encountered required tags
   *     if (!buffer.validate(this->opData() != nullptr)) {
   *         // If we didn't build any opData, we are invalid. Even an EmptyPicture allocates the
   *         // SkData for the ops (though its length may be zero).
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  protected fun parseBuffer(buffer: SkReadBuffer): Boolean {
    TODO("Implement parseBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImage* getImage(SkReadBuffer* reader) const {
   *         // images are written base-0, unlike paths, pictures, drawables, etc.
   *         const int index = reader->readInt();
   *         return reader->validateIndex(index, fImages.size()) ? fImages[index].get() : nullptr;
   *     }
   * ```
   */
  public fun getImage(reader: SkReadBuffer?): SkImage {
    TODO("Implement getImage")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPath& getPath(SkReadBuffer* reader) const {
   *         int index = reader->readInt();
   *         return reader->validate(index > 0 && index <= fPaths.size()) ?
   *                 fPaths[index - 1] : fEmptyPath;
   *     }
   * ```
   */
  public fun getPath(reader: SkReadBuffer?): SkPath {
    TODO("Implement getPath")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPicture* getPicture(SkReadBuffer* reader) const {
   *         return read_index_base_1_or_null(reader, fPictures);
   *     }
   * ```
   */
  public fun getPicture(reader: SkReadBuffer?): SkPicture {
    TODO("Implement getPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDrawable* getDrawable(SkReadBuffer* reader) const {
   *         return read_index_base_1_or_null(reader, fDrawables);
   *     }
   * ```
   */
  public fun getDrawable(reader: SkReadBuffer?): SkDrawable {
    TODO("Implement getDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPaint* SkPictureData::optionalPaint(SkReadBuffer* reader) const {
   *     int index = reader->readInt();
   *     if (index == 0) {
   *         return nullptr; // recorder wrote a zero for no paint (likely drawimage)
   *     }
   *     return reader->validate(index > 0 && index <= fPaints.size()) ?
   *         &fPaints[index - 1] : nullptr;
   * }
   * ```
   */
  public fun optionalPaint(reader: SkReadBuffer?): SkPaint {
    TODO("Implement optionalPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPaint& SkPictureData::requiredPaint(SkReadBuffer* reader) const {
   *     const SkPaint* paint = this->optionalPaint(reader);
   *     if (reader->validate(paint != nullptr)) {
   *         return *paint;
   *     }
   *     static const SkPaint& stub = *(new SkPaint);
   *     return stub;
   * }
   * ```
   */
  public fun requiredPaint(reader: SkReadBuffer?): SkPaint {
    TODO("Implement requiredPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlob* getTextBlob(SkReadBuffer* reader) const {
   *         return read_index_base_1_or_null(reader, fTextBlobs);
   *     }
   * ```
   */
  public fun getTextBlob(reader: SkReadBuffer?): SkTextBlob {
    TODO("Implement getTextBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * const sktext::gpu::Slug* getSlug(SkReadBuffer* reader) const {
   *         return read_index_base_1_or_null(reader, fSlugs);
   *     }
   * ```
   */
  public fun getSlug(reader: SkReadBuffer?): Slug {
    TODO("Implement getSlug")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkVertices* getVertices(SkReadBuffer* reader) const {
   *         return read_index_base_1_or_null(reader, fVertices);
   *     }
   * ```
   */
  public fun getVertices(reader: SkReadBuffer?): SkVertices {
    TODO("Implement getVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPictureData::parseStreamTag(SkStream* stream,
   *                                    uint32_t tag,
   *                                    uint32_t size,
   *                                    const SkDeserialProcs& procs,
   *                                    SkTypefacePlayback* topLevelTFPlayback,
   *                                    int recursionLimit) {
   *     switch (tag) {
   *         case SK_PICT_READER_TAG:
   *             SkASSERT(nullptr == fOpData);
   *             fOpData = SkData::MakeFromStream(stream, size);
   *             if (!fOpData) {
   *                 return false;
   *             }
   *             break;
   *         case SK_PICT_FACTORY_TAG: {
   *             if (!stream->readU32(&size)) { return false; }
   *             if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
   *                 return false;
   *             }
   *             fFactoryPlayback = std::make_unique<SkFactoryPlayback>(size);
   *             for (size_t i = 0; i < size; i++) {
   *                 SkString str;
   *                 size_t len;
   *                 if (!stream->readPackedUInt(&len)) { return false; }
   *                 if (SkStreamPriv::RemainingLengthIsBelow(stream, len)) {
   *                     return false;
   *                 }
   *                 str.resize(len);
   *                 if (stream->read(str.data(), len) != len) {
   *                     return false;
   *                 }
   *                 fFactoryPlayback->base()[i] = SkFlattenable::NameToFactory(str.c_str());
   *             }
   *         } break;
   *         case SK_PICT_TYPEFACE_TAG: {
   *             if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
   *                 return false;
   *             }
   *             fTFPlayback.setCount(size);
   *             for (uint32_t i = 0; i < size; ++i) {
   *                 if (stream->isAtEnd()) {
   *                     return false;
   *                 }
   *                 sk_sp<SkTypeface> tf;
   *                 if (procs.fTypefaceProc) {
   *                     tf = procs.fTypefaceProc(&stream, sizeof(stream), procs.fTypefaceCtx);
   *                 }
   *                 else {
   *                     tf = SkTypeface::MakeDeserialize(stream, nullptr);
   *                 }
   *                 if (!tf) {    // failed to deserialize
   *                     // fTFPlayback asserts it never has a null, so we plop in
   *                     // a default here.
   *                     tf = SkTypeface::MakeEmpty();
   *                 }
   *                 fTFPlayback[i] = std::move(tf);
   *             }
   *         } break;
   *         case SK_PICT_PICTURE_TAG: {
   *             SkASSERT(fPictures.empty());
   *             if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
   *                 return false;
   *             }
   *             fPictures.reserve_exact(SkToInt(size));
   *
   *             for (uint32_t i = 0; i < size; i++) {
   *                 auto pic = SkPicture::MakeFromStreamPriv(stream, &procs,
   *                                                          topLevelTFPlayback, recursionLimit - 1);
   *                 if (!pic) {
   *                     return false;
   *                 }
   *                 fPictures.push_back(std::move(pic));
   *             }
   *         } break;
   *         case SK_PICT_BUFFER_SIZE_TAG: {
   *             if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
   *                 return false;
   *             }
   *             SkAutoMalloc storage(size);
   *             if (stream->read(storage.get(), size) != size) {
   *                 return false;
   *             }
   *
   *             SkReadBuffer buffer(storage.get(), size);
   *             buffer.setVersion(fInfo.getVersion());
   *
   *             if (!fFactoryPlayback) {
   *                 return false;
   *             }
   *             fFactoryPlayback->setupBuffer(buffer);
   *             buffer.setDeserialProcs(procs);
   *
   *             if (fTFPlayback.count() > 0) {
   *                 // .skp files <= v43 have typefaces serialized with each sub picture.
   *                 fTFPlayback.setupBuffer(buffer);
   *             } else {
   *                 // Newer .skp files serialize all typefaces with the top picture.
   *                 topLevelTFPlayback->setupBuffer(buffer);
   *             }
   *
   *             while (!buffer.eof() && buffer.isValid()) {
   *                 tag = buffer.readUInt();
   *                 size = buffer.readUInt();
   *                 this->parseBufferTag(buffer, tag, size);
   *             }
   *             if (!buffer.isValid()) {
   *                 return false;
   *             }
   *         } break;
   *     }
   *     return true;    // success
   * }
   * ```
   */
  private fun parseStreamTag(
    stream: SkStream?,
    tag: UInt,
    size: UInt,
    procs: SkDeserialProcs,
    topLevelTFPlayback: SkTypefacePlayback?,
    recursionLimit: Int,
  ): Boolean {
    TODO("Implement parseStreamTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureData::parseBufferTag(SkReadBuffer& buffer, uint32_t tag, uint32_t size) {
   *     switch (tag) {
   *         case SK_PICT_PAINT_BUFFER_TAG: {
   *             if (!buffer.validate(SkTFitsIn<int>(size))) {
   *                 return;
   *             }
   *             const int count = SkToInt(size);
   *
   *             for (int i = 0; i < count; ++i) {
   *                 fPaints.push_back(buffer.readPaint());
   *                 if (!buffer.isValid()) {
   *                     return;
   *                 }
   *             }
   *         } break;
   *         case SK_PICT_PATH_BUFFER_TAG:
   *             if (size > 0) {
   *                 const int count = buffer.readInt();
   *                 if (!buffer.validate(count >= 0)) {
   *                     return;
   *                 }
   *                 for (int i = 0; i < count; i++) {
   *                     if (auto path = buffer.readPath()) {
   *                         fPaths.push_back(std::move(*path));
   *                     } else {
   *                         // readPath should invalidate the buffer if we didn't get a path back.
   *                         SkASSERT(!buffer.isValid());
   *                         return;
   *                     }
   *                 }
   *             } break;
   *         case SK_PICT_TEXTBLOB_BUFFER_TAG:
   *             new_array_from_buffer(buffer, size, fTextBlobs, SkTextBlobPriv::MakeFromBuffer);
   *             break;
   *         case SK_PICT_SLUG_BUFFER_TAG:
   *             new_array_from_buffer(buffer, size, fSlugs, sktext::gpu::Slug::MakeFromBuffer);
   *             break;
   *         case SK_PICT_VERTICES_BUFFER_TAG:
   *             new_array_from_buffer(buffer, size, fVertices, SkVerticesPriv::Decode);
   *             break;
   *         case SK_PICT_IMAGE_BUFFER_TAG:
   *             new_array_from_buffer(buffer, size, fImages, create_image_from_buffer);
   *             break;
   *         case SK_PICT_READER_TAG: {
   *             // Preflight check that we can initialize all data from the buffer
   *             // before allocating it.
   *             if (!buffer.validateCanReadN<uint8_t>(size)) {
   *                 return;
   *             }
   *             auto data(SkData::MakeUninitialized(size));
   *             if (!buffer.readByteArray(data->writable_data(), size) ||
   *                 !buffer.validate(nullptr == fOpData)) {
   *                 return;
   *             }
   *             SkASSERT(nullptr == fOpData);
   *             fOpData = std::move(data);
   *         } break;
   *         case SK_PICT_PICTURE_TAG:
   *             new_array_from_buffer(buffer, size, fPictures, SkPicturePriv::MakeFromBuffer);
   *             break;
   *         case SK_PICT_DRAWABLE_TAG:
   *             new_array_from_buffer(buffer, size, fDrawables, create_drawable_from_buffer);
   *             break;
   *         default:
   *             buffer.validate(false); // The tag was invalid.
   *             break;
   *     }
   * }
   * ```
   */
  private fun parseBufferTag(
    buffer: SkReadBuffer,
    tag: UInt,
    size: UInt,
  ) {
    TODO("Implement parseBufferTag")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureData::flattenToBuffer(SkWriteBuffer& buffer, bool textBlobsOnly) const {
   *     if (!textBlobsOnly) {
   *         int numPaints = fPaints.size();
   *         if (numPaints > 0) {
   *             write_tag_size(buffer, SK_PICT_PAINT_BUFFER_TAG, numPaints);
   *             for (const SkPaint& paint : fPaints) {
   *                 buffer.writePaint(paint);
   *             }
   *         }
   *
   *         int numPaths = fPaths.size();
   *         if (numPaths > 0) {
   *             write_tag_size(buffer, SK_PICT_PATH_BUFFER_TAG, numPaths);
   *             buffer.writeInt(numPaths);
   *             for (const SkPath& path : fPaths) {
   *                 buffer.writePath(path);
   *             }
   *         }
   *     }
   *
   *     if (!fTextBlobs.empty()) {
   *         write_tag_size(buffer, SK_PICT_TEXTBLOB_BUFFER_TAG, fTextBlobs.size());
   *         for (const auto& blob : fTextBlobs) {
   *             SkTextBlobPriv::Flatten(*blob, buffer);
   *         }
   *     }
   *
   *     if (!textBlobsOnly) {
   *         write_tag_size(buffer, SK_PICT_SLUG_BUFFER_TAG, fSlugs.size());
   *         for (const auto& slug : fSlugs) {
   *             slug->doFlatten(buffer);
   *         }
   *     }
   *
   *     if (!textBlobsOnly) {
   *         if (!fVertices.empty()) {
   *             write_tag_size(buffer, SK_PICT_VERTICES_BUFFER_TAG, fVertices.size());
   *             for (const auto& vert : fVertices) {
   *                 vert->priv().encode(buffer);
   *             }
   *         }
   *
   *         if (!fImages.empty()) {
   *             write_tag_size(buffer, SK_PICT_IMAGE_BUFFER_TAG, fImages.size());
   *             for (const auto& img : fImages) {
   *                 buffer.writeImage(img.get());
   *             }
   *         }
   *     }
   * }
   * ```
   */
  private fun flattenToBuffer(buffer: SkWriteBuffer, textBlobsOnly: Boolean) {
    TODO("Implement flattenToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureData::initForPlayback() const {
   *     // ensure that the paths bounds are pre-computed
   *     for (int i = 0; i < fPaths.size(); i++) {
   *         fPaths[i].updateBoundsCache();
   *     }
   * }
   * ```
   */
  private fun initForPlayback() {
    TODO("Implement initForPlayback")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkPictureData* SkPictureData::CreateFromStream(SkStream* stream,
     *                                                const SkPictInfo& info,
     *                                                const SkDeserialProcs& procs,
     *                                                SkTypefacePlayback* topLevelTFPlayback,
     *                                                int recursionLimit) {
     *     std::unique_ptr<SkPictureData> data(new SkPictureData(info));
     *     if (!topLevelTFPlayback) {
     *         topLevelTFPlayback = &data->fTFPlayback;
     *     }
     *
     *     if (!data->parseStream(stream, procs, topLevelTFPlayback, recursionLimit)) {
     *         return nullptr;
     *     }
     *     return data.release();
     * }
     * ```
     */
    public fun createFromStream(
      stream: SkStream?,
      info: SkPictInfo,
      procs: SkDeserialProcs,
      topLevelTFPlayback: SkTypefacePlayback?,
      recursionLimit: Int,
    ): SkPictureData {
      TODO("Implement createFromStream")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPictureData* SkPictureData::CreateFromBuffer(SkReadBuffer& buffer,
     *                                                const SkPictInfo& info) {
     *     std::unique_ptr<SkPictureData> data(new SkPictureData(info));
     *     buffer.setVersion(info.getVersion());
     *
     *     if (!data->parseBuffer(buffer)) {
     *         return nullptr;
     *     }
     *     return data.release();
     * }
     * ```
     */
    public fun createFromBuffer(buffer: SkReadBuffer, info: SkPictInfo): SkPictureData {
      TODO("Implement createFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPictureData::WriteFactories(SkWStream* stream, const SkFactorySet& rec) {
     *     int count = rec.count();
     *
     *     AutoSTMalloc<16, SkFlattenable::Factory> storage(count);
     *     SkFlattenable::Factory* array = (SkFlattenable::Factory*)storage.get();
     *     rec.copyToArray(array);
     *
     *     size_t size = compute_chunk_size(array, count);
     *
     *     // TODO: write_tag_size should really take a size_t
     *     write_tag_size(stream, SK_PICT_FACTORY_TAG, (uint32_t) size);
     *     SkDEBUGCODE(size_t start = stream->bytesWritten());
     *     stream->write32(count);
     *
     *     for (int i = 0; i < count; i++) {
     *         const char* name = SkFlattenable::FactoryToName(array[i]);
     *         if (nullptr == name || 0 == *name) {
     *             stream->writePackedUInt(0);
     *         } else {
     *             size_t len = strlen(name);
     *             stream->writePackedUInt(len);
     *             stream->write(name, len);
     *         }
     *     }
     *
     *     SkASSERT(size == (stream->bytesWritten() - start));
     * }
     * ```
     */
    private fun writeFactories(stream: SkWStream?, rec: SkFactorySet) {
      TODO("Implement writeFactories")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPictureData::WriteTypefaces(SkWStream* stream, const SkRefCntSet& rec,
     *                                    const SkSerialProcs& procs) {
     *     int count = rec.count();
     *
     *     write_tag_size(stream, SK_PICT_TYPEFACE_TAG, count);
     *
     *     AutoSTMalloc<16, SkTypeface*> storage(count);
     *     SkTypeface** array = (SkTypeface**)storage.get();
     *     rec.copyToArray((SkRefCnt**)array);
     *
     *     for (int i = 0; i < count; i++) {
     *         SkTypeface* tf = array[i];
     *         if (procs.fTypefaceProc) {
     *             auto data = procs.fTypefaceProc(tf, procs.fTypefaceCtx);
     *             if (data) {
     *                 stream->write(data->data(), data->size());
     *                 continue;
     *             }
     *         }
     *         // With the default serialization and deserialization behavior,
     *         // kIncludeDataIfLocal does not always work because there is no default
     *         // fontmgr to pass into SkTypeface::MakeDeserialize, so there is no
     *         // fontmgr to find a font given the descriptor only.
     *         tf->serialize(stream, SkTypeface::SerializeBehavior::kDoIncludeData);
     *     }
     * }
     * ```
     */
    private fun writeTypefaces(
      stream: SkWStream?,
      rec: SkRefCntSet,
      procs: SkSerialProcs,
    ) {
      TODO("Implement writeTypefaces")
    }
  }
}
