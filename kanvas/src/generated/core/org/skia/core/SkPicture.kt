package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import kotlinx.atomicfu.AtomicBoolean
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSerialProcs
import org.skia.foundation.SkStream
import org.skia.foundation.SkWStream
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkPicture : public SkRefCnt {
 * public:
 *     ~SkPicture() override;
 *
 *     /** Recreates SkPicture that was serialized into a stream. Returns constructed SkPicture
 *         if successful; otherwise, returns nullptr. Fails if data does not permit
 *         constructing valid SkPicture.
 *
 *         procs->fPictureProc permits supplying a custom function to decode SkPicture.
 *         If procs->fPictureProc is nullptr, default decoding is used. procs->fPictureCtx
 *         may be used to provide user context to procs->fPictureProc; procs->fPictureProc
 *         is called with a pointer to data, data byte length, and user context.
 *
 *         @param stream  container for serial data
 *         @param procs   custom serial data decoders; may be nullptr
 *         @return        SkPicture constructed from stream data
 *     */
 *     static sk_sp<SkPicture> MakeFromStream(SkStream* stream,
 *                                            const SkDeserialProcs* procs = nullptr);
 *
 *     /** Recreates SkPicture that was serialized into data. Returns constructed SkPicture
 *         if successful; otherwise, returns nullptr. Fails if data does not permit
 *         constructing valid SkPicture.
 *
 *         procs->fPictureProc permits supplying a custom function to decode SkPicture.
 *         If procs->fPictureProc is nullptr, default decoding is used. procs->fPictureCtx
 *         may be used to provide user context to procs->fPictureProc; procs->fPictureProc
 *         is called with a pointer to data, data byte length, and user context.
 *
 *         @param data   container for serial data
 *         @param procs  custom serial data decoders; may be nullptr
 *         @return       SkPicture constructed from data
 *     */
 *     static sk_sp<SkPicture> MakeFromData(const SkData* data,
 *                                          const SkDeserialProcs* procs = nullptr);
 *
 *     /**
 *
 *         @param data   pointer to serial data
 *         @param size   size of data
 *         @param procs  custom serial data decoders; may be nullptr
 *         @return       SkPicture constructed from data
 *     */
 *     static sk_sp<SkPicture> MakeFromData(const void* data, size_t size,
 *                                          const SkDeserialProcs* procs = nullptr);
 *
 *     /** \class SkPicture::AbortCallback
 *         AbortCallback is an abstract class. An implementation of AbortCallback may
 *         passed as a parameter to SkPicture::playback, to stop it before all drawing
 *         commands have been processed.
 *
 *         If AbortCallback::abort returns true, SkPicture::playback is interrupted.
 *     */
 *     class SK_API AbortCallback {
 *     public:
 *         /** Has no effect.
 *         */
 *         virtual ~AbortCallback() = default;
 *
 *         /** Stops SkPicture playback when some condition is met. A subclass of
 *             AbortCallback provides an override for abort() that can stop SkPicture::playback.
 *
 *             The part of SkPicture drawn when aborted is undefined. SkPicture instantiations are
 *             free to stop drawing at different points during playback.
 *
 *             If the abort happens inside one or more calls to SkCanvas::save(), stack
 *             of SkCanvas matrix and SkCanvas clip values is restored to its state before
 *             SkPicture::playback was called.
 *
 *             @return  true to stop playback
 *
 *         example: https://fiddle.skia.org/c/@Picture_AbortCallback_abort
 *         */
 *         virtual bool abort() = 0;
 *
 *     protected:
 *         AbortCallback() = default;
 *         AbortCallback(const AbortCallback&) = delete;
 *         AbortCallback& operator=(const AbortCallback&) = delete;
 *     };
 *
 *     /** Replays the drawing commands on the specified canvas. In the case that the
 *         commands are recorded, each command in the SkPicture is sent separately to canvas.
 *
 *         To add a single command to draw SkPicture to recording canvas, call
 *         SkCanvas::drawPicture instead.
 *
 *         @param canvas    receiver of drawing commands
 *         @param callback  allows interruption of playback
 *
 *         example: https://fiddle.skia.org/c/@Picture_playback
 *     */
 *     virtual void playback(SkCanvas* canvas, AbortCallback* callback = nullptr) const = 0;
 *
 *     /** Returns cull SkRect for this picture, passed in when SkPicture was created.
 *         Returned SkRect does not specify clipping SkRect for SkPicture; cull is hint
 *         of SkPicture bounds.
 *
 *         SkPicture is free to discard recorded drawing commands that fall outside
 *         cull.
 *
 *         @return  bounds passed when SkPicture was created
 *
 *         example: https://fiddle.skia.org/c/@Picture_cullRect
 *     */
 *     virtual SkRect cullRect() const = 0;
 *
 *     /** Returns a non-zero value unique among SkPicture in Skia process.
 *
 *         @return  identifier for SkPicture
 *     */
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     /** Returns storage containing SkData describing SkPicture, using optional custom
 *         encoders.
 *
 *         procs->fPictureProc permits supplying a custom function to encode SkPicture.
 *         If procs->fPictureProc is nullptr, default encoding is used. procs->fPictureCtx
 *         may be used to provide user context to procs->fPictureProc; procs->fPictureProc
 *         is called with a pointer to SkPicture and user context.
 *
 *         The default behavior for serializing SkImages is to encode a nullptr. Should
 *         clients want to, for example, encode these SkImages as PNGs so they can be
 *         deserialized, they must provide SkSerialProcs with the fImageProc set to do so.
 *
 *         @param procs  custom serial data encoders; may be nullptr
 *         @return       storage containing serialized SkPicture
 *
 *         example: https://fiddle.skia.org/c/@Picture_serialize
 *     */
 *     sk_sp<SkData> serialize(const SkSerialProcs* procs = nullptr) const;
 *
 *     /** Writes picture to stream, using optional custom encoders.
 *
 *         procs->fPictureProc permits supplying a custom function to encode SkPicture.
 *         If procs->fPictureProc is nullptr, default encoding is used. procs->fPictureCtx
 *         may be used to provide user context to procs->fPictureProc; procs->fPictureProc
 *         is called with a pointer to SkPicture and user context.
 *
 *         The default behavior for serializing SkImages is to encode a nullptr. Should
 *         clients want to, for example, encode these SkImages as PNGs so they can be
 *         deserialized, they must provide SkSerialProcs with the fImageProc set to do so.
 *
 *         @param stream  writable serial data stream
 *         @param procs   custom serial data encoders; may be nullptr
 *
 *         example: https://fiddle.skia.org/c/@Picture_serialize_2
 *     */
 *     void serialize(SkWStream* stream, const SkSerialProcs* procs = nullptr) const;
 *
 *     /** Returns a placeholder SkPicture. Result does not draw, and contains only
 *         cull SkRect, a hint of its bounds. Result is immutable; it cannot be changed
 *         later. Result identifier is unique.
 *
 *         Returned placeholder can be intercepted during playback to insert other
 *         commands into SkCanvas draw stream.
 *
 *         @param cull  placeholder dimensions
 *         @return      placeholder with unique identifier
 *
 *         example: https://fiddle.skia.org/c/@Picture_MakePlaceholder
 *     */
 *     static sk_sp<SkPicture> MakePlaceholder(SkRect cull);
 *
 *     /** Returns the approximate number of operations in SkPicture. Returned value
 *         may be greater or less than the number of SkCanvas calls
 *         recorded: some calls may be recorded as more than one operation, other
 *         calls may be optimized away.
 *
 *         @param nested  if true, include the op-counts of nested pictures as well, else
 *                        just return count the ops in the top-level picture.
 *         @return  approximate operation count
 *
 *         example: https://fiddle.skia.org/c/@Picture_approximateOpCount
 *     */
 *     virtual int approximateOpCount(bool nested = false) const = 0;
 *
 *     /** Returns the approximate byte size of SkPicture. Does not include large objects
 *         referenced by SkPicture.
 *
 *         @return  approximate size
 *
 *         example: https://fiddle.skia.org/c/@Picture_approximateBytesUsed
 *     */
 *     virtual size_t approximateBytesUsed() const = 0;
 *
 *     /** Return a new shader that will draw with this picture.
 *      *
 *      *  @param tmx  The tiling mode to use when sampling in the x-direction.
 *      *  @param tmy  The tiling mode to use when sampling in the y-direction.
 *      *  @param mode How to filter the tiles
 *      *  @param localMatrix Optional matrix used when sampling
 *      *  @param tileRect The tile rectangle in picture coordinates: this represents the subset
 *      *                  (or superset) of the picture used when building a tile. It is not
 *      *                  affected by localMatrix and does not imply scaling (only translation
 *      *                  and cropping). If null, the tile rect is considered equal to the picture
 *      *                  bounds.
 *      *  @return     Returns a new shader object. Note: this function never returns null.
 *      */
 *     sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, SkFilterMode mode,
 *                                const SkMatrix* localMatrix, const SkRect* tileRect) const;
 *
 *     sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, SkFilterMode mode) const {
 *         return this->makeShader(tmx, tmy, mode, nullptr, nullptr);
 *     }
 *
 * private:
 *     // Allowed subclasses.
 *     SkPicture();
 *     friend class SkBigPicture;
 *     friend class SkEmptyPicture;
 *     friend class SkPicturePriv;
 *
 *     void serialize(SkWStream*, const SkSerialProcs*, class SkRefCntSet* typefaces,
 *         bool textBlobsOnly=false) const;
 *     static sk_sp<SkPicture> MakeFromStreamPriv(SkStream*, const SkDeserialProcs*,
 *                                                class SkTypefacePlayback*,
 *                                                int recursionLimit);
 *     friend class SkPictureData;
 *
 *     /** Return true if the SkStream/Buffer represents a serialized picture, and
 *      fills out SkPictInfo. After this function returns, the data source is not
 *      rewound so it will have to be manually reset before passing to
 *      MakeFromStream or MakeFromBuffer. Note, MakeFromStream and
 *      MakeFromBuffer perform this check internally so these entry points are
 *      intended for stand alone tools.
 *      If false is returned, SkPictInfo is unmodified.
 *      */
 *     static bool StreamIsSKP(SkStream*, struct SkPictInfo*);
 *     static bool BufferIsSKP(class SkReadBuffer*, struct SkPictInfo*);
 *     friend bool SkPicture_StreamIsSKP(SkStream*, struct SkPictInfo*);
 *
 *     // Returns NULL if this is not an SkBigPicture.
 *     virtual const class SkBigPicture* asSkBigPicture() const { return nullptr; }
 *
 *     static bool IsValidPictInfo(const struct SkPictInfo& info);
 *     static sk_sp<SkPicture> Forwardport(const struct SkPictInfo&,
 *                                         const class SkPictureData*,
 *                                         class SkReadBuffer* buffer);
 *
 *     struct SkPictInfo createHeader() const;
 *     class SkPictureData* backport() const;
 *
 *     uint32_t fUniqueID;
 *     mutable std::atomic<bool> fAddedToCache{false};
 * }
 * ```
 */
public abstract class SkPicture public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  private var fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<bool> fAddedToCache
   * ```
   */
  private val fAddedToCache: AtomicBoolean = TODO("Initialize fAddedToCache")

  /**
   * C++ original:
   * ```cpp
   * virtual void playback(SkCanvas* canvas, AbortCallback* callback = nullptr) const = 0
   * ```
   */
  protected abstract fun playback(canvas: SkCanvas?, callback: AbortCallback? = TODO())

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect cullRect() const = 0
   * ```
   */
  protected abstract fun cullRect(): Int

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  protected fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkPicture::serialize(const SkSerialProcs* procs) const {
   *     SkDynamicMemoryWStream stream;
   *     this->serialize(&stream, procs, nullptr);
   *     return stream.detachAsData();
   * }
   * ```
   */
  protected fun serialize(procs: SkSerialProcs? = TODO()): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPicture::serialize(SkWStream* stream, const SkSerialProcs* procs) const {
   *     this->serialize(stream, procs, nullptr);
   * }
   * ```
   */
  protected fun serialize(stream: SkWStream?, procs: SkSerialProcs? = TODO()) {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int approximateOpCount(bool nested = false) const = 0
   * ```
   */
  protected abstract fun approximateOpCount(nested: Boolean = TODO()): Int

  /**
   * C++ original:
   * ```cpp
   * virtual size_t approximateBytesUsed() const = 0
   * ```
   */
  protected abstract fun approximateBytesUsed(): ULong

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkPicture::makeShader(SkTileMode tmx, SkTileMode tmy, SkFilterMode filter,
   *                                       const SkMatrix* localMatrix, const SkRect* tile) const {
   *     if (localMatrix && !localMatrix->invert()) {
   *         return nullptr;
   *     }
   *     return SkPictureShader::Make(sk_ref_sp(this), tmx, tmy, filter, localMatrix, tile);
   * }
   * ```
   */
  protected fun makeShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    mode: SkFilterMode,
    localMatrix: SkMatrix?,
    tileRect: SkRect?,
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> makeShader(SkTileMode tmx, SkTileMode tmy, SkFilterMode mode) const {
   *         return this->makeShader(tmx, tmy, mode, nullptr, nullptr);
   *     }
   * ```
   */
  protected fun makeShader(
    tmx: SkTileMode,
    tmy: SkTileMode,
    mode: SkFilterMode,
  ): Int {
    TODO("Implement makeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPicture::serialize(SkWStream* stream, const SkSerialProcs* procsPtr,
   *                           SkRefCntSet* typefaceSet, bool textBlobsOnly) const {
   *     SkSerialProcs procs;
   *     if (procsPtr) {
   *         procs = *procsPtr;
   *     }
   *
   *     SkPictInfo info = this->createHeader();
   *     stream->write(&info, sizeof(info));
   *
   *     if (auto custom = custom_serialize(this, procs)) {
   *         int32_t size = SkToS32(custom->size());
   *         if (size == 0) {
   *             stream->write8(kFailure_TrailingStreamByteAfterPictInfo);
   *             return;
   *         }
   *         stream->write8(kCustom_TrailingStreamByteAfterPictInfo);
   *         stream->write32(-size);    // negative for custom format
   *         write_pad32(stream, custom->data(), size);
   *         return;
   *     }
   *
   *     std::unique_ptr<SkPictureData> data(this->backport());
   *     if (data) {
   *         stream->write8(kPictureData_TrailingStreamByteAfterPictInfo);
   *         data->serialize(stream, procs, typefaceSet, textBlobsOnly);
   *     } else {
   *         stream->write8(kFailure_TrailingStreamByteAfterPictInfo);
   *     }
   * }
   * ```
   */
  private fun serialize(
    stream: SkWStream?,
    procsPtr: SkSerialProcs?,
    typefaces: SkRefCntSet?,
    textBlobsOnly: Boolean = TODO(),
  ) {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const class SkBigPicture* asSkBigPicture() const { return nullptr; }
   * ```
   */
  public open fun asSkBigPicture(): SkBigPicture {
    TODO("Implement asSkBigPicture")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPictureData* SkPicture::backport() const {
   *     SkPictInfo info = this->createHeader();
   *     SkPictureRecord rec(info.fCullRect.roundOut(), 0/*flags*/);
   *     rec.beginRecording();
   *         this->playback(&rec);
   *     rec.endRecording();
   *     return new SkPictureData(rec, info);
   * }
   * ```
   */
  private fun backport(): SkPictureData {
    TODO("Implement backport")
  }

  public abstract class AbortCallback public constructor() {
    public constructor(param0: undefined.AbortCallback) : this() {
      TODO("Implement constructor")
    }

    public abstract fun abort(): Boolean

    protected fun assign(param0: undefined.AbortCallback) {
      TODO("Implement assign")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::MakeFromStream(SkStream* stream, const SkDeserialProcs* procs) {
     *     return MakeFromStreamPriv(stream, procs, nullptr, kNestedSKPLimit);
     * }
     * ```
     */
    public fun makeFromStream(stream: SkStream?, procs: SkDeserialProcs? = TODO()): Int {
      TODO("Implement makeFromStream")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::MakeFromData(const SkData* data, const SkDeserialProcs* procs) {
     *     if (!data) {
     *         return nullptr;
     *     }
     *     SkMemoryStream stream(data->data(), data->size());
     *     return MakeFromStreamPriv(&stream, procs, nullptr, kNestedSKPLimit);
     * }
     * ```
     */
    public fun makeFromData(`data`: SkData?, procs: SkDeserialProcs? = TODO()): Int {
      TODO("Implement makeFromData")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::MakeFromData(const void* data, size_t size,
     *                                          const SkDeserialProcs* procs) {
     *     if (!data) {
     *         return nullptr;
     *     }
     *     SkMemoryStream stream(data, size);
     *     return MakeFromStreamPriv(&stream, procs, nullptr, kNestedSKPLimit);
     * }
     * ```
     */
    public fun makeFromData(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement makeFromData")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::MakePlaceholder(SkRect cull) {
     *     struct Placeholder : public SkPicture {
     *           explicit Placeholder(SkRect cull) : fCull(cull) {}
     *
     *           void playback(SkCanvas*, AbortCallback*) const override { }
     *
     *           // approximateOpCount() needs to be greater than kMaxPictureOpsToUnrollInsteadOfRef
     *           // (SkCanvasPriv.h) to avoid unrolling this into a parent picture.
     *           int approximateOpCount(bool) const override {
     *               return kMaxPictureOpsToUnrollInsteadOfRef+1;
     *           }
     *           size_t approximateBytesUsed() const override { return sizeof(*this); }
     *           SkRect cullRect()             const override { return fCull; }
     *
     *           SkRect fCull;
     *     };
     *     return sk_make_sp<Placeholder>(cull);
     * }
     * ```
     */
    protected fun makePlaceholder(cull: SkRect): Int {
      TODO("Implement makePlaceholder")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::MakeFromStreamPriv(SkStream* stream, const SkDeserialProcs* procsPtr,
     *                                                SkTypefacePlayback* typefaces, int recursionLimit) {
     *     if (recursionLimit <= 0) {
     *         return nullptr;
     *     }
     *     SkPictInfo info;
     *     if (!StreamIsSKP(stream, &info)) {
     *         return nullptr;
     *     }
     *
     *     SkDeserialProcs procs;
     *     if (procsPtr) {
     *         procs = *procsPtr;
     *     }
     *
     *     uint8_t trailingStreamByteAfterPictInfo;
     *     if (!stream->readU8(&trailingStreamByteAfterPictInfo)) { return nullptr; }
     *     switch (trailingStreamByteAfterPictInfo) {
     *         case kPictureData_TrailingStreamByteAfterPictInfo: {
     *             std::unique_ptr<SkPictureData> data(
     *                     SkPictureData::CreateFromStream(stream, info, procs, typefaces,
     *                                                     recursionLimit));
     *             return Forwardport(info, data.get(), nullptr);
     *         }
     *         case kCustom_TrailingStreamByteAfterPictInfo: {
     *             int32_t ssize;
     *             if (!stream->readS32(&ssize) || ssize >= 0 || !procs.fPictureProc) {
     *                 return nullptr;
     *             }
     *             size_t size = sk_negate_to_size_t(ssize);
     *             if (SkStreamPriv::RemainingLengthIsBelow(stream, size)) {
     *                 return nullptr;
     *             }
     *             auto data = SkData::MakeUninitialized(size);
     *             if (stream->read(data->writable_data(), size) != size) {
     *                 return nullptr;
     *             }
     *             return procs.fPictureProc(data->data(), size, procs.fPictureCtx);
     *         }
     *         default:    // fall out to error return
     *             break;
     *     }
     *     return nullptr;
     * }
     * ```
     */
    private fun makeFromStreamPriv(
      stream: SkStream?,
      procsPtr: SkDeserialProcs?,
      typefaces: SkTypefacePlayback?,
      recursionLimit: Int,
    ): Int {
      TODO("Implement makeFromStreamPriv")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPicture::StreamIsSKP(SkStream* stream, SkPictInfo* pInfo) {
     *     if (!stream) {
     *         return false;
     *     }
     *
     *     SkPictInfo info;
     *     SkASSERT(sizeof(kMagic) == sizeof(info.fMagic));
     *     if (stream->read(&info.fMagic, sizeof(kMagic)) != sizeof(kMagic)) {
     *         return false;
     *     }
     *
     *     uint32_t version;
     *     if (!stream->readU32(&version)) { return false; }
     *     info.setVersion(version);
     *     if (!stream->readScalar(&info.fCullRect.fLeft  )) { return false; }
     *     if (!stream->readScalar(&info.fCullRect.fTop   )) { return false; }
     *     if (!stream->readScalar(&info.fCullRect.fRight )) { return false; }
     *     if (!stream->readScalar(&info.fCullRect.fBottom)) { return false; }
     *
     *     if (pInfo) {
     *         *pInfo = info;
     *     }
     *     return IsValidPictInfo(info);
     * }
     * ```
     */
    private fun streamIsSKP(stream: SkStream?, pInfo: SkPictInfo?): Boolean {
      TODO("Implement streamIsSKP")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPicture::BufferIsSKP(SkReadBuffer* buffer, SkPictInfo* pInfo) {
     *     SkPictInfo info;
     *     SkASSERT(sizeof(kMagic) == sizeof(info.fMagic));
     *     if (!buffer->readByteArray(&info.fMagic, sizeof(kMagic))) {
     *         return false;
     *     }
     *
     *     info.setVersion(buffer->readUInt());
     *     buffer->readRect(&info.fCullRect);
     *
     *     if (IsValidPictInfo(info)) {
     *         if (pInfo) { *pInfo = info; }
     *         return true;
     *     }
     *     return false;
     * }
     * ```
     */
    private fun bufferIsSKP(buffer: SkReadBuffer?, pInfo: SkPictInfo?): Boolean {
      TODO("Implement bufferIsSKP")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPicture::IsValidPictInfo(const SkPictInfo& info) {
     *     if (0 != memcmp(info.fMagic, kMagic, sizeof(kMagic))) {
     *         return false;
     *     }
     *     if (info.getVersion() < SkPicturePriv::kMin_Version ||
     *         info.getVersion() > SkPicturePriv::kCurrent_Version) {
     *         return false;
     *     }
     *     return true;
     * }
     * ```
     */
    private fun isValidPictInfo(info: SkPictInfo): Boolean {
      TODO("Implement isValidPictInfo")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPicture> SkPicture::Forwardport(const SkPictInfo& info,
     *                                         const SkPictureData* data,
     *                                         SkReadBuffer* buffer) {
     *     if (!data) {
     *         return nullptr;
     *     }
     *     if (!data->opData()) {
     *         return nullptr;
     *     }
     *     SkPicturePlayback playback(data);
     *     SkPictureRecorder r;
     *     playback.draw(r.beginRecording(info.fCullRect), nullptr/*no callback*/, buffer);
     *     return r.finishRecordingAsPicture();
     * }
     * ```
     */
    private fun forwardport(
      info: SkPictInfo,
      `data`: SkPictureData?,
      buffer: SkReadBuffer?,
    ): Int {
      TODO("Implement forwardport")
    }
  }
}
